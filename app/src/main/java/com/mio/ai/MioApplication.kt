package com.mio.ai

import android.app.Application
import com.mio.ai.core.ai.AiClient
import com.mio.ai.core.ai.ConversationStore
import com.mio.ai.core.ai.LlmPlanner
import com.mio.ai.core.ai.OpenAiCompatibleClient
import com.mio.ai.core.commands.CommandRouter
import com.mio.ai.core.engine.ActionEngine
import com.mio.ai.data.MioSettings
import com.mio.ai.data.SecureKeyStore
import com.mio.ai.data.SettingsRepository
import java.io.File

/** Resolved AI stack for the current settings snapshot. */
data class AiRuntime(
    val client: AiClient,
    val planner: LlmPlanner?,
    val router: CommandRouter,
)

/**
 * Composition root. API keys resolve as: encrypted in-app override first,
 * BuildConfig (local.properties) fallback — never hard-coded.
 */
class MioApplication : Application() {

    val settingsRepo by lazy { SettingsRepository(this) }
    val secureKeys by lazy { SecureKeyStore(this) }
    val conversationStore by lazy {
        ConversationStore(File(filesDir, "conversation.json"))
    }
    val engine by lazy { ActionEngine(this) }

    fun resolveAi(settings: MioSettings): AiRuntime {
        val baseUrl = settings.aiBaseUrlOverride.ifBlank { BuildConfig.MIO_AI_BASE_URL }
        val model = settings.aiModelOverride.ifBlank { BuildConfig.MIO_AI_MODEL }
        val apiKey = secureKeys.getApiKey().ifBlank { BuildConfig.MIO_AI_API_KEY }
        val client = OpenAiCompatibleClient(baseUrl, apiKey, model)
        val planner = if (settings.useCloudAi && client.isConfigured) LlmPlanner(client) else null
        return AiRuntime(client, planner, CommandRouter(planner))
    }
}
