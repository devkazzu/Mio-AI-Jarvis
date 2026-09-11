package com.mio.ai.core.ai

import com.mio.ai.core.actions.Action
import com.mio.ai.core.actions.Plan
import com.mio.ai.core.actions.ScrollDirection
import com.mio.ai.core.actions.Switch
import com.mio.ai.core.actions.VolumeDirection
import com.mio.ai.core.util.MiniJson
import com.mio.ai.data.ResponseStyle

/**
 * Cloud planner: converts free-form utterances the rule parser couldn't map
 * into validated [Plan]s — or a conversational reply. The LLM proposes,
 * [LlmPlanParser] disposes: unknown action types or bad params fail closed
 * to a normal chat reply, never to a guessed action.
 */
class LlmPlanner(private val client: AiClient, private val style: String = ResponseStyle.BALANCED) {

    sealed interface Decision {
        data class DoPlan(val plan: Plan) : Decision
        data class Say(val text: String) : Decision
    }

    suspend fun decide(userText: String, history: List<ChatMessage>): Decision? {
        if (!client.isConfigured) return null
        val raw = try {
            client.chat(
                history.takeLast(8) + ChatMessage(ChatMessage.Role.USER, userText),
                systemPrompt(),
                ResponseStyle.maxTokens(style),
            )
        } catch (_: AiException) {
            return null
        }
        val json = MiniJson.extractFirstJson(raw) ?: return Decision.Say(raw.trim().take(600))
        return try {
            val root = MiniJson.asMap(MiniJson.parse(json))
            when (MiniJson.asString(root["mode"])) {
                "plan" -> {
                    val plan = LlmPlanParser.toPlan(root) ?: return Decision.Say(
                        MiniJson.asString(root["reply"])
                            ?.takeIf { it.isNotBlank() }
                            ?.take(600)
                            ?: "I couldn't turn that into a safe action. Could you rephrase it?",
                    )
                    Decision.DoPlan(plan)
                }
                else -> Decision.Say(
                    MiniJson.asString(root["reply"])?.takeIf { it.isNotBlank() }?.take(600)
                        ?: raw.trim().take(600),
                )
            }
        } catch (_: Exception) {
            Decision.Say(raw.trim().take(600).ifBlank { "I didn't quite catch that." })
        }
    }

    private fun systemPrompt(): String = "$SYSTEM\n${ResponseStyle.instruction(style)}"

    companion object {
        /**
         * Strict contract prompt. Kept short so it works with small local models too.
         * The allowlist mirrors the types accepted in [LlmPlanParser.toAction].
         */
        const val SYSTEM = """
You are Mio, a phone assistant. Answer with ONLY one JSON object, no markdown, no extra text.
{"mode":"plan","summary":"short label","confirm":true|false,"confirm_prompt":"question or null","steps":[{"type":"...","params":{...}}],"reply":"one-line fallback"}
or {"mode":"chat","reply":"conversational answer, max 2 sentences"}.
Use mode plan ONLY when the user clearly wants a phone action below; otherwise chat.
Allowed step types and params:
torch{state:on|off|toggle} wifi{state} bluetooth{state} dnd{state} brightness{percent:1-100}
volume{dir:up|down|mute|unmute,percent?:0-100} open_app{query} close_app{} home{} back{} recents{}
set_alarm{hour:0-23,minute:0-59,label?} show_alarms{} set_timer{seconds:1-86400,label?} open_camera{}
dial{to} sms{to,body?} tap_text{text} tap_first{} type_text{text,submit?:bool} scroll{dir:up|down|left|right,times?:1-5}
wait_text{text} pause{ms:300-5000} time{} date{} battery{} status{} web_search{query} navigate{to}
Rules: confirm=true for dial, sms, wifi, bluetooth, dnd, and any multi-step plan touching another app.
Only use params listed. Never invent types. Keep reply short and speakable.
"""
    }
}

/** Validates raw LLM JSON into a [Plan]. Returns null when anything is off — fail closed. */
object LlmPlanParser {

    fun toPlan(root: Map<String, Any?>): Plan? {
        val rawSteps = MiniJson.asList(root["steps"])
        if (rawSteps.isEmpty() || rawSteps.size > 12) return null
        val steps = rawSteps.map { toAction(MiniJson.asMap(it)) ?: return null }
        val summary = MiniJson.asString(root["summary"])?.takeIf { it.isNotBlank() }?.take(140)
            ?: steps.joinToString(" · ") { it.label }.take(140)
        var confirm = MiniJson.asBoolean(root["confirm"]) ?: false
        if (steps.any { it.sensitive }) confirm = true
        val prompt = MiniJson.asString(root["confirm_prompt"])?.takeIf { it.isNotBlank() }?.take(200)
        return Plan(steps, summary, confirm, prompt)
    }

    private fun toAction(step: Map<String, Any?>): Action? {
        val type = MiniJson.asString(step["type"]) ?: return null
        val p = MiniJson.asMap(step["params"])
        fun str(key: String) = MiniJson.asString(p[key])?.trim()?.takeIf { it.isNotEmpty() }
        fun switch(): Switch? = when (str("state")?.lowercase()) {
            "on" -> Switch.ON
            "off" -> Switch.OFF
            "toggle" -> Switch.TOGGLE
            else -> null
        }
        return when (type) {
            "torch" -> (switch() ?: return null).let { Action.Torch(it) }
            "wifi" -> (switch() ?: return null).let { Action.Wifi(it) }
            "bluetooth" -> (switch() ?: return null).let { Action.Bluetooth(it) }
            "dnd" -> (switch() ?: return null).let { Action.DoNotDisturb(it) }
            "brightness" -> {
                val pct = MiniJson.asInt(p["percent"])?.coerceIn(1, 100) ?: return null
                Action.Brightness(percent = pct)
            }
            "volume" -> {
                val dir = when (str("dir")?.lowercase()) {
                    "up" -> VolumeDirection.UP
                    "down" -> VolumeDirection.DOWN
                    "mute" -> VolumeDirection.MUTE
                    "unmute" -> VolumeDirection.UNMUTE
                    else -> return null
                }
                Action.Volume(dir, MiniJson.asInt(p["percent"])?.coerceIn(0, 100))
            }
            "open_app" -> Action.OpenApp(str("query")?.take(60) ?: return null)
            "close_app" -> Action.CloseCurrentApp
            "home" -> Action.GoHome
            "back" -> Action.PressBack
            "recents" -> Action.OpenRecents
            "app_list" -> Action.AppList
            "set_alarm" -> {
                val h = MiniJson.asInt(p["hour"]) ?: return null
                val m = MiniJson.asInt(p["minute"]) ?: return null
                if (h !in 0..23 || m !in 0..59) return null
                Action.SetAlarm(h, m, str("label")?.take(60))
            }
            "show_alarms" -> Action.ShowAlarms
            "set_timer" -> {
                val s = MiniJson.asInt(p["seconds"]) ?: return null
                if (s !in 1..86_400) return null
                Action.SetTimer(s, str("label")?.take(60))
            }
            "open_camera" -> Action.OpenCamera
            "dial" -> Action.Dial(str("to")?.take(60) ?: return null)
            "sms" -> Action.ComposeSms(str("to")?.take(60) ?: return null, str("body")?.take(500))
            "tap_text" -> Action.TapText(str("text")?.take(80) ?: return null)
            "tap_first" -> Action.TapFirstResult(str("hint")?.take(80))
            "type_text" -> Action.TypeText(
                str("text")?.take(500) ?: return null,
                submit = MiniJson.asBoolean(p["submit"]) ?: false,
            )
            "scroll" -> {
                val dir = when (str("dir")?.lowercase()) {
                    "up" -> ScrollDirection.UP
                    "down" -> ScrollDirection.DOWN
                    "left" -> ScrollDirection.LEFT
                    "right" -> ScrollDirection.RIGHT
                    else -> return null
                }
                Action.Scroll(dir, (MiniJson.asInt(p["times"]) ?: 1).coerceIn(1, 5))
            }
            "wait_text" -> Action.WaitForText(str("text")?.take(80) ?: return null)
            "pause" -> Action.Pause((MiniJson.asLong(p["ms"]) ?: 800L).coerceIn(300L, 5_000L))
            "time" -> Action.QueryTime
            "date" -> Action.QueryDate
            "battery" -> Action.QueryBattery
            "status" -> Action.QueryDeviceStatus
            "web_search" -> Action.WebSearch(str("query")?.take(200) ?: return null)
            "navigate" -> Action.Navigate(str("to")?.take(200) ?: return null)
            else -> null // Unknown type → fail closed.
        }
    }
}
