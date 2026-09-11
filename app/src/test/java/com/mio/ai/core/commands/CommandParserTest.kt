package com.mio.ai.core.commands

import com.mio.ai.core.actions.Action
import com.mio.ai.core.actions.ScrollDirection
import com.mio.ai.core.actions.Switch
import com.mio.ai.core.actions.VolumeDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandParserTest {

    private fun planOf(text: String) =
        CommandParser.parse(text) as CommandParser.RouteResult.DoPlan

    // ------------------------------------------------------------ devices

    @Test
    fun `torch on and off`() {
        val on = planOf("turn on the flashlight").plan
        assertEquals(listOf(Action.Torch(Switch.ON)), on.steps)
        assertFalse(on.requiresConfirmation)

        val off = planOf("switch off torch").plan
        assertEquals(listOf(Action.Torch(Switch.OFF)), off.steps)
    }

    @Test
    fun `distributive toggle fans out`() {
        val plan = planOf("turn on Wi-Fi and Bluetooth").plan
        assertEquals(
            listOf(Action.Wifi(Switch.ON), Action.Bluetooth(Switch.ON)),
            plan.steps,
        )
        assertTrue(plan.requiresConfirmation)
    }

    @Test
    fun `volume directions and percents`() {
        assertEquals(
            Action.Volume(VolumeDirection.UP),
            planOf("turn the volume up").plan.steps.single(),
        )
        assertEquals(
            Action.Volume(VolumeDirection.MUTE),
            planOf("mute").plan.steps.single(),
        )
        assertEquals(
            Action.Volume(VolumeDirection.UNMUTE),
            planOf("unmute").plan.steps.single(),
        )
        assertEquals(
            Action.Volume(VolumeDirection.UP, percent = 40),
            planOf("set volume to 40").plan.steps.single(),
        )
        assertEquals(
            Action.Volume(VolumeDirection.UP, percent = 50),
            planOf("set volume to fifty percent").plan.steps.single(),
        )
    }

    @Test
    fun `brightness percent and steps`() {
        assertEquals(
            Action.Brightness(percent = 70),
            planOf("set brightness to 70").plan.steps.single(),
        )
        assertEquals(
            Action.Brightness(up = true),
            planOf("brighter").plan.steps.single(),
        )
    }

    @Test
    fun `do not disturb`() {
        assertEquals(
            Action.DoNotDisturb(Switch.ON),
            planOf("turn on do not disturb").plan.steps.single(),
        )
    }

    // ------------------------------------------------------ alarms & timers

    @Test
    fun `alarm times parse`() {
        assertEquals(Action.SetAlarm(7, 0, null), planOf("set an alarm for 7 am").plan.steps.single())
        assertEquals(Action.SetAlarm(19, 30, null), planOf("wake me up at 7:30 pm").plan.steps.single())
        assertEquals(Action.SetAlarm(6, 30, null), planOf("set an alarm for half past 6").plan.steps.single())
        assertEquals(Action.SetAlarm(7, 45, null), planOf("set alarm for quarter to 8").plan.steps.single())
        assertEquals(Action.SetAlarm(7, 30, null), planOf("set an alarm for seven thirty").plan.steps.single())
    }

    @Test
    fun `timers parse to seconds`() {
        assertEquals(Action.SetTimer(300, null), planOf("set a timer for 5 minutes").plan.steps.single())
        assertEquals(Action.SetTimer(4_800, null), planOf("set a timer for 1 hour 20 minutes").plan.steps.single())
        assertEquals(Action.SetTimer(90, null), planOf("timer ninety seconds").plan.steps.single())
    }

    // ----------------------------------------------------------- apps & web

    @Test
    fun `open app resolves catalog name`() {
        val step = planOf("open instagram").plan.steps.single() as Action.OpenApp
        assertEquals("Instagram", step.query)
    }

    @Test
    fun `multi-step search sequence builds confirmed plan`() {
        val plan = planOf("open Instagram, search for cats, and open the first result").plan
        assertEquals(7, plan.steps.size)
        assertTrue(plan.steps[0] is Action.OpenApp)
        assertTrue(plan.steps[4] is Action.TypeText)
        assertTrue(plan.steps[6] is Action.TapFirstResult)
        assertTrue(plan.requiresConfirmation)
    }

    @Test
    fun `search on app pattern`() {
        val plan = planOf("search for lo-fi beats on YouTube").plan
        assertTrue(plan.steps[0] is Action.OpenApp)
        val type = plan.steps.filterIsInstance<Action.TypeText>().single()
        assertEquals("lo-fi beats", type.text)
        assertTrue(type.submit)
    }

    @Test
    fun `bare search becomes web search`() {
        val step = planOf("search for today's weather").plan.steps.single() as Action.WebSearch
        assertEquals("today's weather", step.query)
    }

    @Test
    fun `close app and navigation`() {
        assertTrue(planOf("close this app").plan.steps.single() is Action.CloseCurrentApp)
        assertTrue(planOf("go back").plan.steps.single() is Action.PressBack)
        assertTrue(planOf("go home").plan.steps.single() is Action.GoHome)
        assertTrue(planOf("show my apps").plan.steps.single() is Action.AppList)
    }

    // ------------------------------------------------------- communication

    @Test
    fun `call requires confirmation`() {
        val plan = planOf("call mom").plan
        assertEquals(Action.Dial("Mom"), plan.steps.single())
        assertTrue(plan.requiresConfirmation)
    }

    @Test
    fun `sms with body`() {
        val plan = planOf("send a text to mom saying I'll be late").plan
        assertEquals(Action.ComposeSms("Mom", "i'll be late"), plan.steps.single())
        assertTrue(plan.requiresConfirmation)
    }

    // ------------------------------------------------------------- a11y & co

    @Test
    fun `a11y primitives`() {
        assertEquals(Action.TapText("Search"), planOf("tap search").plan.steps.single())
        assertEquals(Action.TypeText("hello world"), planOf("type hello world").plan.steps.single())
        assertEquals(
            Action.Scroll(ScrollDirection.DOWN, 2),
            planOf("scroll down 2 times").plan.steps.single(),
        )
    }

    @Test
    fun `info queries`() {
        assertTrue(planOf("what time is it").plan.steps.single() is Action.QueryTime)
        assertTrue(planOf("what day is today").plan.steps.single() is Action.QueryDate)
        assertTrue(planOf("how much battery is left").plan.steps.single() is Action.QueryBattery)
        assertTrue(planOf("is the Wi-Fi on").plan.steps.single() is Action.QueryDeviceStatus)
    }

    @Test
    fun `navigation`() {
        assertEquals(
            Action.Navigate("Mumbai Airport"),
            planOf("navigate to Mumbai airport").plan.steps.single(),
        )
    }

    // -------------------------------------------------------------- compounds

    @Test
    fun `compound commands combine`() {
        val plan = planOf("turn on the flashlight and set volume to 50").plan
        assertEquals(2, plan.steps.size)
        assertTrue(plan.steps[0] is Action.Torch)
        assertTrue(plan.steps[1] is Action.Volume)
    }

    @Test
    fun `and inside a query does not split`() {
        val result = CommandParser.parse("search for cats and dogs on YouTube")
        val plan = (result as CommandParser.RouteResult.DoPlan).plan
        val type = plan.steps.filterIsInstance<Action.TypeText>().single()
        assertEquals("cats and dogs", type.text)
    }

    // ------------------------------------------------------------ meta intents

    @Test
    fun `confirmations and nickname`() {
        assertTrue(CommandParser.parse("yes") is CommandParser.RouteResult.ConfirmYes)
        assertTrue(CommandParser.parse("go ahead") is CommandParser.RouteResult.ConfirmYes)
        assertTrue(CommandParser.parse("no") is CommandParser.RouteResult.ConfirmNo)
        assertTrue(CommandParser.parse("never mind") is CommandParser.RouteResult.ConfirmNo)
        val nick = CommandParser.parse("call me Neo") as CommandParser.RouteResult.SetNickname
        assertEquals("Neo", nick.name)
    }

    @Test
    fun `small talk falls through to conversation`() {
        assertTrue(CommandParser.parse("hello there") is CommandParser.RouteResult.Converse)
        assertTrue(CommandParser.parse("tell me a joke") is CommandParser.RouteResult.Converse)
    }

    @Test
    fun `wake prefix is stripped`() {
        val plan = planOf("hey Mio, turn on the flashlight").plan
        assertEquals(listOf(Action.Torch(Switch.ON)), plan.steps)
    }
}
