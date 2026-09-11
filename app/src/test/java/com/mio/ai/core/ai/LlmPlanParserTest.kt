package com.mio.ai.core.ai

import com.mio.ai.core.actions.Action
import com.mio.ai.core.actions.Switch
import com.mio.ai.core.util.MiniJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmPlanParserTest {

    @Test
    fun `valid plan parses`() {
        val json = """
            {"mode":"plan","summary":"Torch on","confirm":false,
             "steps":[{"type":"torch","params":{"state":"on"}}]}
        """.trimIndent()
        val plan = LlmPlanParser.toPlan(MiniJson.asMap(MiniJson.parse(json)))!!
        assertEquals(listOf(Action.Torch(Switch.ON)), plan.steps)
    }

    @Test
    fun `sensitive steps force confirmation`() {
        val json = """
            {"mode":"plan","summary":"Call","confirm":false,
             "steps":[{"type":"dial","params":{"to":"Mom"}}]}
        """.trimIndent()
        val plan = LlmPlanParser.toPlan(MiniJson.asMap(MiniJson.parse(json)))!!
        assertTrue(plan.requiresConfirmation)
    }

    @Test
    fun `unknown type fails closed`() {
        val json = """
            {"mode":"plan","summary":"Evil","confirm":false,
             "steps":[{"type":"self_destruct","params":{}}]}
        """.trimIndent()
        assertNull(LlmPlanParser.toPlan(MiniJson.asMap(MiniJson.parse(json))))
    }

    @Test
    fun `bad params fail closed`() {
        val json = """
            {"mode":"plan","summary":"Bad alarm","confirm":false,
             "steps":[{"type":"set_alarm","params":{"hour":99,"minute":0}}]}
        """.trimIndent()
        assertNull(LlmPlanParser.toPlan(MiniJson.asMap(MiniJson.parse(json))))
    }

    @Test
    fun `empty steps fail closed`() {
        val json = """{"mode":"plan","summary":"Nothing","steps":[]}"""
        assertNull(LlmPlanParser.toPlan(MiniJson.asMap(MiniJson.parse(json))))
    }

    @Test
    fun `app list parses`() {
        val json = """
            {"mode":"plan","summary":"Apps","confirm":false,
             "steps":[{"type":"app_list","params":{}}]}
        """.trimIndent()
        val plan = LlmPlanParser.toPlan(MiniJson.asMap(MiniJson.parse(json)))!!
        assertTrue(plan.steps.single() is Action.AppList)
    }

    @Test
    fun `multi-step automation plan parses`() {
        val json = """
            {"mode":"plan","summary":"IG search","confirm":true,"confirm_prompt":"Go?",
             "steps":[
               {"type":"open_app","params":{"query":"Instagram"}},
               {"type":"pause","params":{"ms":1200}},
               {"type":"tap_text","params":{"text":"Search"}},
               {"type":"type_text","params":{"text":"cats","submit":true}}
             ]}
        """.trimIndent()
        val plan = LlmPlanParser.toPlan(MiniJson.asMap(MiniJson.parse(json)))!!
        assertEquals(4, plan.steps.size)
        assertTrue(plan.requiresConfirmation)
        assertEquals("Go?", plan.confirmationPrompt)
    }
}
