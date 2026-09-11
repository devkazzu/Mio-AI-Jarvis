package com.mio.ai.core.engine

import android.content.Context
import android.content.Intent
import com.mio.ai.accessibility.AccessibilityController
import com.mio.ai.core.actions.Action
import com.mio.ai.core.actions.Plan
import com.mio.ai.core.actions.PlanResult
import com.mio.ai.core.actions.StepOutcome
import com.mio.ai.core.actions.Switch
import com.mio.ai.system.AppLauncher
import com.mio.ai.system.OpResult
import com.mio.ai.system.SystemActions
import kotlinx.coroutines.delay

/**
 * Action Engine: runs a validated [Plan] step-by-step, streaming progress to
 * the UI ("what Mio is doing right now") and stopping the chain on the first
 * failure with a plain-language explanation.
 */
class ActionEngine(context: Context) {

    private val appContext = context.applicationContext
    private val system = SystemActions(appContext)
    private val apps = AppLauncher(appContext)

    enum class StepState { RUNNING, DONE }

    suspend fun execute(
        plan: Plan,
        onStep: suspend (index: Int, state: StepState, outcome: StepOutcome?) -> Unit,
    ): PlanResult {
        val outcomes = ArrayList<StepOutcome>()
        for ((index, action) in plan.steps.withIndex()) {
            onStep(index, StepState.RUNNING, null)
            val op = try {
                runAction(action)
            } catch (e: Exception) {
                OpResult.fail("Something went wrong (${e.message}).", "UNKNOWN")
            }
            val step = StepOutcome(
                action = action,
                success = op.success,
                detail = op.detail,
                failureReason = op.failureReason,
                fixDestination = op.fixDestination,
            )
            outcomes += step
            onStep(index, StepState.DONE, step)
            if (!op.success) break // chains depend on order — stop at the break.
            delay(220)
        }
        val ranAll = outcomes.size == plan.steps.size
        val allOk = ranAll && outcomes.all { it.success }
        return PlanResult(plan, outcomes, allOk, spokenSummary(plan, outcomes, allOk))
    }

    // ------------------------------------------------------------------ runner

    private suspend fun runAction(action: Action): OpResult = when (action) {
        is Action.Torch -> system.setTorch(resolveSwitch(action.switch) { system.isTorchOn() })
        is Action.Wifi -> {
            if (action.switch == Switch.TOGGLE) {
                val on = system.isWifiOn()
                if (on == null) system.setWifi(true) else system.setWifi(!on)
            } else {
                system.setWifi(action.switch == Switch.ON)
            }
        }
        is Action.Bluetooth -> {
            if (action.switch == Switch.TOGGLE) {
                val on = system.isBluetoothOn()
                if (on == null) system.setBluetooth(true) else system.setBluetooth(!on)
            } else {
                system.setBluetooth(action.switch == Switch.ON)
            }
        }
        is Action.Volume -> system.adjustVolume(action.direction, action.percent)
        is Action.DoNotDisturb -> {
            if (action.switch == Switch.TOGGLE) {
                val on = system.isDndOn()
                if (on == null) system.setDnd(true) else system.setDnd(!on)
            } else {
                system.setDnd(action.switch == Switch.ON)
            }
        }
        is Action.Brightness -> system.setBrightness(action.percent, action.up)

        is Action.OpenApp -> apps.launch(action.query, action.packageName)
        is Action.CloseCurrentApp -> apps.closeCurrentApp()
        is Action.GoHome -> goHome()
        is Action.PressBack -> a11y("go back") { AccessibilityController.pressBack() }
        is Action.OpenRecents -> a11y("open recent apps") { AccessibilityController.openRecents() }

        is Action.SetAlarm -> system.setAlarm(action.hour, action.minute, action.labelText)
        is Action.ShowAlarms -> system.showAlarms()
        is Action.SetTimer -> system.setTimer(action.seconds, action.labelText)
        is Action.OpenCamera -> system.openCamera()

        is Action.Dial -> system.dial(action.recipient)
        is Action.ComposeSms -> system.composeSms(action.recipient, action.body)

        is Action.TapText -> a11y("tap “${action.text}”") {
            AccessibilityController.tapText(action.text, action.exact)
        }
        is Action.TapFirstResult -> a11y("open the first result") {
            AccessibilityController.tapFirstResult()
        }
        is Action.TypeText -> a11y("type text") {
            AccessibilityController.typeText(action.text, action.fieldHint, action.submit)
        }
        is Action.Scroll -> a11y("scroll") {
            AccessibilityController.scroll(action.direction, action.times)
        }
        is Action.WaitForText -> a11y("wait for “${action.text}”") {
            AccessibilityController.waitForText(action.text, action.timeoutMs)
        }
        is Action.Pause -> {
            delay(action.millis)
            OpResult.ok("…")
        }

        is Action.QueryTime -> system.queryTime()
        is Action.QueryDate -> system.queryDate()
        is Action.QueryBattery -> system.queryBattery()
        is Action.QueryDeviceStatus -> system.queryDeviceStatus()

        is Action.WebSearch -> system.webSearch(action.query)
        is Action.Navigate -> system.navigate(action.destination)
    }

    private fun resolveSwitch(switch: Switch, isOn: () -> Boolean): Boolean = when (switch) {
        Switch.ON -> true
        Switch.OFF -> false
        Switch.TOGGLE -> !isOn()
    }

    /** Home works even without accessibility (plain HOME intent). */
    private fun goHome(): OpResult {
        if (AccessibilityController.isConnected.value && AccessibilityController.goHome()) {
            return OpResult.ok("Home.")
        }
        return try {
            val home = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(home)
            OpResult.ok("Home.")
        } catch (e: Exception) {
            OpResult.fail("Couldn't reach Home (${e.message}).", "UNKNOWN")
        }
    }

    private suspend fun a11y(label: String, block: suspend () -> Boolean): OpResult {
        if (!AccessibilityController.isConnected.value) {
            return OpResult.fail(
                "Turn on Mio's UI Control to $label.",
                "SERVICE_DISABLED", "accessibility",
            )
        }
        return try {
            if (block()) OpResult.ok("Done — $label.")
            else OpResult.fail(
                "I couldn't $label on this screen. It may have changed — try again.",
                "ELEMENT_NOT_FOUND",
            )
        } catch (e: Exception) {
            OpResult.fail("UI control hit a snag (${e.message}).", "UNKNOWN")
        }
    }

    // ------------------------------------------------------------------ speech

    private fun spokenSummary(plan: Plan, outcomes: List<StepOutcome>, allOk: Boolean): String {
        val meaningful = outcomes.filter { it.action !is Action.Pause }
        if (meaningful.isEmpty()) return "Done."
        if (meaningful.size == 1) return meaningful.first().detail
        if (allOk) {
            val last = meaningful.last().detail
            return "${plan.summary}. Done — ${last.replaceFirstChar { it.lowercase() }}"
        }
        val failed = meaningful.first { !it.success }
        val doneCount = meaningful.count { it.success }
        return if (doneCount == 0) {
            "I got stuck: ${failed.detail}"
        } else {
            "I completed $doneCount of ${plan.steps.size} steps, then got stuck: ${failed.detail}"
        }
    }
}
