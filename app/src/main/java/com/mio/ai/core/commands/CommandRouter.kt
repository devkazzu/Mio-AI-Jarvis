package com.mio.ai.core.commands

import com.mio.ai.core.actions.Action
import com.mio.ai.core.actions.Plan
import com.mio.ai.core.ai.ChatMessage
import com.mio.ai.core.ai.LlmPlanner

/**
 * Two-stage command router:
 *  1. Instant offline rule parser ([CommandParser]) — deterministic, no network.
 *  2. Cloud LLM planner ([LlmPlanner]) for everything else — validated, fail-closed.
 *
 * Safety policy is enforced here (not in the UI): sensitive steps and
 * multi-step automation of other apps ALWAYS require confirmation.
 */
class CommandRouter(val planner: LlmPlanner?) {

    sealed interface Decision {
        data class DoPlan(val plan: Plan) : Decision
        /** Final reply text already generated (by the LLM). Just speak it. */
        data class Say(val text: String) : Decision
        /** Nothing matched anywhere — let the offline brain answer. */
        data class ConverseLocal(val text: String) : Decision
        data object ConfirmYes : Decision
        data object ConfirmNo : Decision
        data object Cancel : Decision
        data class SetNickname(val name: String) : Decision
    }

    suspend fun route(rawText: String, history: List<ChatMessage> = emptyList()): Decision {
        return when (val r = CommandParser.parse(rawText)) {
            is CommandParser.RouteResult.DoPlan -> Decision.DoPlan(enforcePolicy(r.plan))
            is CommandParser.RouteResult.ConfirmYes -> Decision.ConfirmYes
            is CommandParser.RouteResult.ConfirmNo -> Decision.ConfirmNo
            is CommandParser.RouteResult.Cancel -> Decision.Cancel
            is CommandParser.RouteResult.SetNickname -> Decision.SetNickname(r.name)
            is CommandParser.RouteResult.Converse -> routeConversational(r.text, history)
        }
    }

    private suspend fun routeConversational(text: String, history: List<ChatMessage>): Decision {
        val p = planner ?: return Decision.ConverseLocal(text)
        return when (val d = p.decide(text, history)) {
            is LlmPlanner.Decision.DoPlan -> Decision.DoPlan(enforcePolicy(d.plan))
            is LlmPlanner.Decision.Say -> Decision.Say(d.text)
            null -> Decision.ConverseLocal(text)
        }
    }

    /**
     * Central safety gate:
     * - any sensitive step → confirmation required
     * - multi-step plans that drive another app's UI → confirmation required
     * - single read-only queries and plain app launches → run immediately
     */
    private fun enforcePolicy(plan: Plan): Plan {
        val touchesForeignUi = plan.steps.size > 1 && plan.steps.any {
            it is Action.TapText || it is Action.TapFirstResult ||
                it is Action.TypeText || it is Action.Scroll || it is Action.WaitForText
        }
        val mustConfirm = plan.requiresConfirmation || plan.hasSensitiveStep || touchesForeignUi
        if (!mustConfirm) return plan
        return plan.copy(
            requiresConfirmation = true,
            confirmationPrompt = plan.confirmationPrompt
                ?: "I'll ${plan.summary.replaceFirstChar { it.lowercase() }}. Go ahead?",
        )
    }
}
