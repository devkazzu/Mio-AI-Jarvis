package com.mio.ai

import android.app.Application
import com.mio.ai.core.ai.AiClient
import com.mio.ai.core.ai.CloudBrainConfig
import com.mio.ai.core.ai.ConversationStore
import com.mio.ai.core.ai.LlmPlanner
import com.mio.ai.core.ai.OpenAiCompatibleClient
import com.mio.ai.assistant.AssistantCore
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

    /**
     * The one assistant pipeline (mic → router → engine → TTS), shared by the
     * activity UI and the background service so there is never a second
     * recognizer/TTS fighting for the microphone or audio focus.
     */
    val assistant by lazy { AssistantCore(this) }

    fun resolveAi(settings: MioSettings): AiRuntime {
        // Effective config: user overrides win, build defaults fill blanks.
        // The API key comes ONLY from encrypted storage — never build config.
        val eff = CloudBrainConfig.effective(
            settings.aiBaseUrlOverride, settings.aiModelOverride,
            BuildConfig.MIO_AI_BASE_URL, BuildConfig.MIO_AI_MODEL,
        )
        val client = OpenAiCompatibleClient(eff.baseUrl, secureKeys.getApiKey(), eff.model)
        val planner = if (CloudBrainConfig.isReady(settings.useCloudAi, eff, secureKeys.hasApiKey())) {
            LlmPlanner(client, settings.responseStyle)
        } else {
            null
        }
        return AiRuntime(client, planner, CommandRouter(planner))
    }
}
