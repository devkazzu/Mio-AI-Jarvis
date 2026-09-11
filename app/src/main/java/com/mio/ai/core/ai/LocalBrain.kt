package com.mio.ai.core.ai

import kotlin.random.Random

/**
 * Offline personality brain. Handles greetings, small talk, help and jokes
 * with zero network — and gives graceful "here's what I *can* do" replies for
 * everything else when the cloud brain isn't configured.
 *
 * Pure Kotlin — unit-testable.
 */
object LocalBrain {

    data class Context(
        val nickname: String?,
        val hourOfDay: Int,
        val cloudConfigured: Boolean,
    )

    fun reply(rawText: String, ctx: Context): String {
        val s = rawText.lowercase().trim()
            .removePrefix("hey mio").removePrefix("hi mio").removePrefix("ok mio").removePrefix("mio")
            .trim(',', ' ', '.', '!', '?')
        val name = ctx.nickname?.let { ", $it" }.orEmpty()

        fun greeting(): String {
            val part = when (ctx.hourOfDay) {
                in 5..11 -> "Good morning"
                in 12..16 -> "Good afternoon"
                in 17..21 -> "Good evening"
                else -> "Working late"
            }
            return "$part$name. Systems online — what can I do for you?"
        }

        return when {
            s.isBlank() || matches(s, "^(hi|hey|hello|yo|greetings|good (morning|afternoon|evening)|namaste)[.! ]*$") ->
                greeting()

            matches(s, "how are you", "how('s| is) it going", "how do you feel", "are you (ok|okay|good|well)") ->
                pick(
                    "Running at full power$name. How can I help?",
                    "All systems nominal. What do you need?",
                    "Better now that you're talking to me. What's the mission?",
                )

            matches(s, "your name", "who are you", "what are you", "introduce yourself", "about yourself") ->
                "I'm Mio — your on-device AI assistant. I run your phone with your voice: apps, calls, " +
                    "flashlight, alarms, and full UI automation when you grant me access."

            matches(s, "who (made|created|built|developed) you", "your (creator|maker|developer|boss)") ->
                "I was built to be your personal JARVIS — an AI that actually does things, not just talks about them."

            matches(s, "what can you do", "help", "commands", "features", "abilities", "how do i use you", "demo") ->
                "Try me: “turn on the flashlight”, “open YouTube”, “call mom”, “set an alarm for 7 AM”, " +
                    "or “open Instagram, search for cats, and open the first result”. " +
                    "I can tap, scroll and type inside apps once my UI Control access is on."

            matches(s, "thank", "thanks", "thankyou", "good job", "well done", "nice work", "awesome", "great job") ->
                pick(
                    "Always a pleasure$name.",
                    "At your service.",
                    "Happy to help. Anything else?",
                )

            matches(s, "^(bye|goodbye|good ?night|see you|later|sleep)") ->
                "Powering down my ears$name — tap the mic any time you need me."

            matches(s, "joke", "funny", "make me laugh") -> joke()

            matches(s, "i love you", "love you", "marry me") ->
                "Careful$name — I'm already married to your battery percentage."

            matches(s, "are you (human|real|alive|a robot|an ai|ai)", "are you (siri|alexa|google|jarvis)") ->
                "I'm Mio — an AI living in your phone. Less famous than JARVIS, better at actually tapping buttons."

            matches(s, "weather") ->
                if (ctx.cloudConfigured) {
                    "I don't have a live weather feed wired up yet — but say “search the web for today's weather” " +
                        "and I'll pull it up for you."
                } else {
                    "My sensors don't reach the clouds yet$name. Say “search the web for today's weather” and I'll open it."
                }

            matches(s, "meaning of life", "philosophy") ->
                "42 — but around here, the meaning of life is a fully charged battery and zero unread notifications."

            matches(s, "open sesame", "hack", "crack") ->
                "Nice try$name. I only break into action when you ask nicely — and legally."

            matches(s, "sorry", "my bad", "apolog") ->
                "No harm done. What should we do instead?"

            matches(s, "who am i", "my name", "do you know me") ->
                if (ctx.nickname != null) "You're $ctx.nickname — my favorite human. Probably."
                else "You haven't told me your name yet — say “call me…” followed by your name."

            else -> fallback(ctx)
        }
    }

    private fun fallback(ctx: Context): String {
        val name = ctx.nickname?.let { ", $it" }.orEmpty()
        return if (ctx.cloudConfigured) {
            pick(
                "Hmm$name — I couldn't quite map that to an action. Try rephrasing, like “turn on Wi-Fi” or “open Spotify”.",
                "My circuits drew a blank on that one. Ask me to open an app, change a setting, or say “help”.",
            )
        } else {
            pick(
                "I'm running offline$name, so I only know direct commands right now. Say “help” to hear them — " +
                    "or add an AI key in Settings to unlock full conversation.",
                "Offline mode: I understood the words, but that one's beyond my local brain. Try “help” for what I can do.",
            )
        }
    }

    private fun joke(): String = pick(
        "Why do programmers prefer dark mode? Because light attracts bugs.",
        "I told my phone a joke about UDP… I'm not sure it got it.",
        "Why did the smartphone go to therapy? Too many unresolved notifications.",
        "My last job was a stand-up comedian for alarm clocks. Tough crowd — they always woke up.",
        "There are only 10 kinds of people: those who understand binary and those who don't.",
        "Why don't robots ever panic? Because they always keep their capacitors together.",
    )

    private fun matches(s: String, vararg patterns: String): Boolean =
        patterns.any { Regex(it).containsMatchIn(s) }

    private fun pick(vararg options: String): String = options[Random.nextInt(options.size)]
}
