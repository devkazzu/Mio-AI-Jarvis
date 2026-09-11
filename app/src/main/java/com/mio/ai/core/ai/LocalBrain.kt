package com.mio.ai.core.ai

import com.mio.ai.data.ResponseStyle
import kotlin.random.Random

/**
 * Offline personality brain: concise, calm, confident, natural with a light
 * futuristic tone. Handles greetings, small talk, help and jokes with zero
 * network, and points at real capabilities otherwise.
 *
 * Pure Kotlin — unit-testable.
 */
object LocalBrain {

    data class Context(
        val nickname: String?,
        val hourOfDay: Int,
        val cloudConfigured: Boolean,
        val style: String = ResponseStyle.BALANCED,
    )

    fun reply(rawText: String, ctx: Context): String {
        val s = rawText.lowercase().trim()
            .removePrefix("hey mio").removePrefix("hi mio").removePrefix("ok mio").removePrefix("mio")
            .trim(',', ' ', '.', '!', '?')
        val concise = ctx.style == ResponseStyle.CONCISE
        val name = ctx.nickname?.let { ", $it" }.orEmpty()

        fun greeting(): String {
            if (concise) return "Hello$name. What do you need?"
            val part = when (ctx.hourOfDay) {
                in 5..11 -> "Good morning"
                in 12..16 -> "Good afternoon"
                in 17..21 -> "Good evening"
                else -> "Still up"
            }
            return "$part$name. I'm ready — what can I do for you?"
        }

        return when {
            s.isBlank() || matches(s, "^(hi|hey|hello|yo|greetings|good (morning|afternoon|evening)|namaste)[.! ]*$") ->
                greeting()

            matches(s, "how are you", "how('s| is) it going", "how do you feel", "are you (ok|okay|good|well)") ->
                if (concise) "All good. How can I help?" else pick(
                    "Running well, thank you. What do you need?",
                    "All systems normal. How can I help?",
                    "Ready when you are. What's first?",
                )

            matches(s, "your name", "who are you", "what are you", "introduce yourself", "about yourself") ->
                if (concise) "I'm Mio, your phone assistant."
                else "I'm Mio — your personal AI assistant. I control this phone with your voice: " +
                    "apps, settings, calls, alarms, and full on-screen automation when you enable it."

            matches(s, "who (made|created|built|developed) you", "your (creator|maker|developer|boss)") ->
                "I was built to be genuinely useful — an assistant that does things, not just talks."

            matches(s, "what can you do", "help", "commands", "features", "abilities", "how do i use you", "demo") ->
                if (concise) {
                    "I run your phone by voice: apps, flashlight, alarms, calls, and on-screen taps. Try “turn on the flashlight”."
                } else {
                    "I run your phone by voice. Try “turn on the flashlight”, “open YouTube”, “call mom”, " +
                        "“set an alarm for 7 AM”, or “open Instagram, search for cats, and open the first result”. " +
                        "Enable UI Control in Permissions and I can tap, scroll and type inside apps."
                }

            matches(s, "thank", "thanks", "thankyou", "good job", "well done", "nice work", "awesome", "great job") ->
                pick("Anytime$name.", "Glad to help.", "Of course.")

            matches(s, "^(bye|goodbye|good ?night|see you|later|sleep)") ->
                if (concise) "Goodbye$name." else "I'll be here when you need me$name."

            matches(s, "joke", "funny", "make me laugh") -> joke()

            matches(s, "i love you", "love you", "marry me") ->
                "I'm flattered$name. I'll stick to being useful."

            matches(s, "are you (human|real|alive|a robot|an ai|ai)", "are you (siri|alexa|google|jarvis)") ->
                "I'm Mio — an AI that lives on your phone and operates it for you."

            matches(s, "weather") ->
                "I don't have live weather yet. Say “search the web for today's weather” and I'll open it."

            matches(s, "meaning of life", "philosophy") ->
                "A charged battery and zero unread notifications. The rest is commentary."

            matches(s, "open sesame", "hack", "crack") ->
                "I only act on your commands — and within the rules."

            matches(s, "sorry", "my bad", "apolog") ->
                "No problem. What would you like to do?"

            matches(s, "who am i", "my name", "do you know me") ->
                if (ctx.nickname != null) "You're $ctx.nickname."
                else "You haven't told me yet — say “call me…” followed by your name."

            else -> fallback(ctx)
        }
    }

    private fun fallback(ctx: Context): String {
        if (ctx.style == ResponseStyle.CONCISE) {
            return if (ctx.cloudConfigured) "I couldn't map that to an action. Try rephrasing it."
            else "Offline: I only know direct commands. Say “help”."
        }
        return if (ctx.cloudConfigured) {
            pick(
                "I couldn't map that to an action. Try something like “turn on Wi-Fi” or “open Spotify”.",
                "Not sure what you mean. Ask me to open an app or change a setting — or say “help”.",
            )
        } else {
            pick(
                "I'm offline right now, so I only know direct commands. Say “help” to hear them — " +
                    "or add an AI key in Settings for full conversation.",
                "That's beyond my offline brain. Try “help” for what I can do without a connection.",
            )
        }
    }

    private fun joke(): String = pick(
        "Why do programmers prefer dark mode? Light attracts bugs.",
        "I told a UDP joke once. Not sure it arrived.",
        "Why did the phone go to therapy? Too many unresolved notifications.",
        "There are only 10 kinds of people: those who understand binary and those who don't.",
        "My last gig was comedy for alarm clocks. Tough crowd — they always woke up.",
    )

    private fun matches(s: String, vararg patterns: String): Boolean =
        patterns.any { Regex(it).containsMatchIn(s) }

    private fun pick(vararg options: String): String = options[Random.nextInt(options.size)]
}
