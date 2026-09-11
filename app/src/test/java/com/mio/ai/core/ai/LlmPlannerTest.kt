package com.mio.ai.core.ai

import com.mio.ai.core.actions.Action
import com.mio.ai.core.actions.Switch
import com.mio.ai.data.ResponseStyle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmPlannerTest {

    private class FakeClient(
        override val isConfigured: Boolean = true,
        private val reply: String = "",
        private val failure: AiException? = null,
    ) : AiClient {
        var lastSystem = ""
        var lastMaxTokens = 0
        override val describe: String = "fake"
        override suspend fun chat(messages: List<ChatMessage>, systemPrompt: String, maxTokens: Int): String {
            lastSystem = systemPrompt
            lastMaxTokens = maxTokens
            failure?.let { throw it }
            return reply
        }

        override suspend fun ping(): PingResult = PingResult.Ok("ok")
    }

    @Test
    fun `chat json becomes Say`() = runBlocking {
        val p = LlmPlanner(FakeClient(reply = """{"mode":"chat","reply":"Hello there!"}"""))
        val d = p.decide("hi", emptyList()) as LlmPlanner.Decision.Say
        assertEquals("Hello there!", d.text)
        assertNull(p.lastFailure)
    }

    @Test
    fun `valid plan json becomes DoPlan`() = runBlocking {
        val p = LlmPlanner(
            FakeClient(
                reply = """{"mode":"plan","summary":"Torch on","confirm":false,
                    "steps":[{"type":"torch","params":{"state":"on"}}],"reply":"Done"}""",
            ),
        )
        val d = p.decide("torch on", emptyList()) as LlmPlanner.Decision.DoPlan
        assertEquals(listOf(Action.Torch(Switch.ON)), d.plan.steps)
        assertNull(p.lastFailure)
    }

    @Test
    fun `unknown action type fails closed to Say`() = runBlocking {
        val p = LlmPlanner(
            FakeClient(
                reply = """{"mode":"plan","summary":"x","confirm":false,
                    "steps":[{"type":"launch_missiles","params":{}}],"reply":"Can't do that safely."}""",
            ),
        )
        val d = p.decide("do evil", emptyList()) as LlmPlanner.Decision.Say
        assertEquals("Can't do that safely.", d.text)
    }

    @Test
    fun `non-json becomes Say with raw text`() = runBlocking {
        val p = LlmPlanner(FakeClient(reply = "Just a plain answer."))
        val d = p.decide("hi", emptyList()) as LlmPlanner.Decision.Say
        assertEquals("Just a plain answer.", d.text)
    }

    @Test
    fun `unconfigured client returns null without failure`() = runBlocking {
        val p = LlmPlanner(FakeClient(isConfigured = false))
        assertNull(p.decide("hi", emptyList()))
        assertNull(p.lastFailure)
    }

    @Test
    fun `AiException returns null and records failure, success clears it`() = runBlocking {
        val failing = LlmPlanner(FakeClient(failure = AiException("Invalid API key (401)")))
        assertNull(failing.decide("hi", emptyList()))
        assertEquals("Invalid API key (401)", failing.lastFailure)

        val succeeding = LlmPlanner(FakeClient(reply = """{"mode":"chat","reply":"ok"}"""))
        succeeding.decide("hi", emptyList())
        assertNull(succeeding.lastFailure)
    }

    @Test
    fun `response style drives tokens and system prompt`() = runBlocking {
        val fake = FakeClient(reply = """{"mode":"chat","reply":"ok"}""")
        LlmPlanner(fake, ResponseStyle.CONCISE).decide("hi", emptyList())
        assertEquals(140, fake.lastMaxTokens)
        assertTrue(fake.lastSystem.contains("ONE short sentence"))
    }
}
