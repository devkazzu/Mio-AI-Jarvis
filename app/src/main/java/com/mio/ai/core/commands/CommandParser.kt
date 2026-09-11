package com.mio.ai.core.commands

import com.mio.ai.core.actions.Action
import com.mio.ai.core.actions.Plan
import com.mio.ai.core.actions.ScrollDirection
import com.mio.ai.core.actions.Switch
import com.mio.ai.core.actions.VolumeDirection

/**
 * Rule-based natural-language → [Plan] parser.
 *
 * Fast, offline, deterministic. Anything it cannot confidently map falls
 * through to [RouteResult.Converse], which the [CommandRouter] sends to the
 * cloud LLM planner (when configured) or the offline [LocalBrain].
 *
 * Pure Kotlin — covered by JVM unit tests (see CommandParserTest).
 */
object CommandParser {

    sealed interface RouteResult {
        data class DoPlan(val plan: Plan) : RouteResult
        /** Not a command — needs a conversational reply. */
        data class Converse(val text: String) : RouteResult
        data object ConfirmYes : RouteResult
        data object ConfirmNo : RouteResult
        data object Cancel : RouteResult
        data class SetNickname(val name: String) : RouteResult
    }

    // ------------------------------------------------------------------ entry

    fun parse(rawInput: String): RouteResult {
        val cleaned = preprocess(rawInput)
        if (cleaned.isBlank()) return RouteResult.Converse(rawInput)

        // 1. Confirmations / cancellation (highest priority — short utterances).
        matchConfirmation(cleaned)?.let { return it }

        // 2. Nickname teaching: "call me Neo", "my name is Neo".
        matchNickname(cleaned)?.let { return it }

        // 3. Whole-utterance multi-step patterns (must run before clause splitting).
        matchSearchSequence(cleaned)?.let { return RouteResult.DoPlan(it) }
        matchCommunication(cleaned)?.let { return RouteResult.DoPlan(it) }
        matchDistributiveToggle(cleaned)?.let { return RouteResult.DoPlan(it) }

        // 4. Compound commands: "open clock then set an alarm..." / "..., ..." / "... and ...".
        val clauses = splitClauses(cleaned)
        if (clauses.size > 1) {
            val actions = clauses.mapNotNull { parseSingle(it) }
            if (actions.size == clauses.size) {
                return RouteResult.DoPlan(
                    Plan(
                        steps = actions,
                        summary = summarize(actions),
                        requiresConfirmation = actions.any { it.sensitive },
                    )
                )
            }
            // Fall through: treat the whole utterance as one command/conversation.
        }

        // 5. Single command.
        parseSingle(cleaned)?.let { action ->
            return RouteResult.DoPlan(
                Plan(
                    steps = listOf(action),
                    summary = action.label,
                    requiresConfirmation = action.sensitive,
                    confirmationPrompt = if (action.sensitive) {
                        "Should I go ahead and ${action.label.lowercase()}?"
                    } else {
                        null
                    },
                )
            )
        }

        // 6. Conversational fallback.
        return RouteResult.Converse(rawInput)
    }

    // -------------------------------------------------------------- preprocess

    private val wakePrefixes = listOf("hey mio", "hi mio", "ok mio", "okay mio", "mio")

    fun preprocess(input: String): String {
        var s = input.lowercase().trim()
        for (p in wakePrefixes) {
            if (s.startsWith(p)) {
                s = s.removePrefix(p).trimStart(',', ' ', '.', '!', '?')
                break
            }
        }
        s = s.removePrefix("please ").removeSuffix(" please").trim()
        s = s.replace(Regex("\\s+"), " ")
        return s.trimEnd('.', '!', '?')
    }

    private fun digits(s: String): String = NumberWords.toDigits(s)

    // ----------------------------------------------------------- confirmations

    private val yesPattern =
        Regex("^(yes|yeah|yep|yup|sure|ok|okay|do it|go ahead|proceed|confirm|confirmed|go on|please do|sounds good|absolutely|why not)[.! ]*$")
    private val noPattern =
        Regex("^(no|nope|nah|don't|cancel|never ?mind|stop|abort|forget it|not now)[.! ]*$")

    private fun matchConfirmation(cleaned: String): RouteResult? {
        if (yesPattern.matches(cleaned)) return RouteResult.ConfirmYes
        if (noPattern.matches(cleaned)) return RouteResult.ConfirmNo
        if (cleaned in setOf("stop listening", "stop talking", "be quiet", "shut up", "quiet")) {
            return RouteResult.Cancel
        }
        return null
    }

    private fun matchNickname(cleaned: String): RouteResult.SetNickname? {
        val m = Regex("^(?:call me|my name is|address me as)\\s+(.+)$").find(cleaned) ?: return null
        val name = m.groupValues[1].trim().split(' ').firstOrNull()
            ?.replaceFirstChar { it.uppercase() }?.takeIf { it.length in 1..24 } ?: return null
        if (name in setOf("Please", "Mio", "Sir", "Maam")) return null
        return RouteResult.SetNickname(name)
    }

    // ------------------------------------------------------- multi-step search
    // "open Instagram, search for cats, and open the first result"
    // "search for lo-fi beats on YouTube" / "play Believer on Spotify"

    private val searchSequencePatterns = listOf(
        // open <app>[,] search for <q> [and open the first result]
        Regex("^open\\s+(.+?)\\s*,?\\s+search\\s+(?:for\\s+)?(.+?)(?:\\s*,?\\s+and\\s+(?:open|tap|click)(?:\\s+on)?\\s+the\\s+first\\s+result)?$"),
        Regex("^launch\\s+(.+?)\\s*,?\\s+search\\s+(?:for\\s+)?(.+?)(?:\\s*,?\\s+and\\s+(?:open|tap|click)(?:\\s+on)?\\s+the\\s+first\\s+result)?$"),
        // search for <q> on|in <app>
        Regex("^(?:search|look\\s+up|look)\\s+(?:for\\s+)?(.+?)\\s+(?:on|in)\\s+(.+?)$"),
        // play <q> on <app> (music/video apps)
        Regex("^play\\s+(.+?)\\s+on\\s+(.+?)$"),
    )

    private fun matchSearchSequence(cleaned: String): Plan? {
        for ((index, pattern) in searchSequencePatterns.withIndex()) {
            val m = pattern.find(cleaned) ?: continue
            val (appQuery, query) = if (index < 2) {
                m.groupValues[1] to m.groupValues[2]
            } else {
                m.groupValues[2] to m.groupValues[1]
            }
            val wantsFirstResult = cleaned.contains("first result")
            val appName = appQuery.trim().trimEnd('.', ',', ' ', '!', '?')
            val searchQuery = query.trim().trim('"', '\'', ' ').trimEnd('.', ',', ' ', '!', '?')
            if (appName.isBlank() || searchQuery.isBlank()) continue
            // Guard: "search the web for X" is a web search, not an app sequence.
            if (appName in setOf("web", "google", "internet")) {
                return Plan(
                    steps = listOf(Action.WebSearch(searchQuery)),
                    summary = "Searching the web for $searchQuery",
                )
            }
            val steps = mutableListOf<Action>(
                Action.OpenApp(humanizeApp(appName)),
                Action.Pause(1_400),
                Action.TapText("Search"),
                Action.Pause(600),
                Action.TypeText(searchQuery, submit = true),
                Action.Pause(1_800),
            )
            if (wantsFirstResult || index == 3) steps += Action.TapFirstResult(searchQuery)
            return Plan(
                steps = steps,
                summary = "Opening $appName and searching for $searchQuery",
                requiresConfirmation = true,
                confirmationPrompt = "I'll open $appName and search for “$searchQuery”. Go ahead?",
            )
        }
        // Bare "search for X" → web search (no permission needed).
        Regex("^(?:search|google|look\\s+up)(?:\\s+for|\\s+up)?\\s+(.+)$").find(cleaned)?.let { m ->
            val q = m.groupValues[1].trim().trimEnd('.', ',', ' ', '!', '?')
            if (q.isNotBlank() && !q.startsWith("on ") && !q.startsWith("in ")) {
                return Plan(
                    steps = listOf(Action.WebSearch(q)),
                    summary = "Searching the web for $q",
                )
            }
        }
        return null
    }

    // ------------------------------------------------------------ communication

    private val smsWithBody =
        Regex("^(?:send\\s+)?(?:a\\s+)?(?:text|message|sms)(?:\\s+message)?\\s+to\\s+(.+?)\\s+(?:saying|that\\s+says|:|,)\\s+(.+)$")
    private val smsBare = Regex("^(?:send\\s+)?(?:a\\s+)?(?:text|message|sms)(?:\\s+message)?\\s+(?:to\\s+)?(.+)$")
    private val callPattern = Regex("^(?:phone|call|ring|dial)\\s+(.+)$")

    private fun matchCommunication(cleaned: String): Plan? {
        smsWithBody.find(cleaned)?.let { m ->
            val to = humanize(m.groupValues[1])
            val body = m.groupValues[2].trim().trimEnd('.', '!', '?')
            if (to.isBlank()) return null
            val action = Action.ComposeSms(to, body.ifBlank { null })
            return Plan(
                listOf(action), action.label, true,
                "I'll open a message to $to${if (body.isNotBlank()) " saying “$body”" else ""}. Go ahead?",
            )
        }
        // "call mom" must come before bare sms ("message mom" vs "call mom" distinct verbs — safe order).
        callPattern.find(cleaned)?.let { m ->
            val to = humanize(m.groupValues[1])
            if (to.isBlank() || to == "me") return null
            val action = Action.Dial(to)
            return Plan(
                listOf(action), action.label, true,
                "Should I call $to?",
            )
        }
        smsBare.find(cleaned)?.let { m ->
            val to = humanize(m.groupValues[1])
            if (to.isBlank()) return null
            val action = Action.ComposeSms(to, null)
            return Plan(
                listOf(action), action.label, true,
                "Should I open a message to $to?",
            )
        }
        return null
    }

    // ---------------------------------------------------- distributive toggles
    // "turn on Wi-Fi and Bluetooth" → two actions.

    private val distributiveToggle =
        Regex("^(turn|switch|enable|disable)\\s+(.+?)\\s+(on|off)\\s*$|^\\s*(turn|switch)\\s+(on|off)\\s+(.+)$")

    private fun matchDistributiveToggle(cleaned: String): Plan? {
        val m = distributiveToggle.find(cleaned) ?: return null
        val g = m.groupValues
        val (verb, state, rest) = if (g[1].isNotBlank()) {
            Triple(g[1], g[3], g[2])
        } else {
            Triple(g[4], g[5], g[6])
        }
        val switch = when (state) {
            "on" -> Switch.ON
            "off" -> Switch.OFF
            else -> return null
        }
        if (verb == "enable" && switch == Switch.OFF) return null
        if (verb == "disable" && switch == Switch.ON) return null
        val parts = rest.split(Regex("\\s+and\\s+|,\\s*|\\s+&\\s+"))
        if (parts.size < 2) return null
        val actions = parts.mapNotNull { deviceWordToAction(it.trim(), switch) }
        if (actions.size != parts.size) return null
        return Plan(
            steps = actions,
            summary = summarize(actions),
            requiresConfirmation = actions.any { it.sensitive },
        )
    }

    private fun deviceWordToAction(word: String, switch: Switch): Action? {
        val w = word.trim()
        return when {
            w matches Regex(".*(flash ?lights?|torch|flash).*") -> Action.Torch(switch)
            w matches Regex(".*(wi[- ]?fi|internet|network).*") -> Action.Wifi(switch)
            w matches Regex(".*(bluetooth|blue ?tooth|bt).*") -> Action.Bluetooth(switch)
            w matches Regex(".*(do not disturb|dnd|silent( mode)?|mute all).*") ->
                Action.DoNotDisturb(switch)

            else -> null
        }
    }

    // ------------------------------------------------------- clause splitting

    private val clauseSeparators = Regex("\\s+and then\\s+|\\s*,\\s*then\\s+|\\s*,\\s*|\\s*;\\s*")

    fun splitClauses(cleaned: String): List<String> {
        val primary = cleaned.split(clauseSeparators).map { it.trim() }.filter { it.isNotBlank() }
        if (primary.size > 1) return primary
        // Try " and " only if every part parses independently — protects
        // queries like "search for cats and dogs".
        val andParts = cleaned.split(Regex("\\s+and\\s+")).map { it.trim() }.filter { it.isNotBlank() }
        if (andParts.size > 1 && andParts.all { parseSingle(it) != null }) return andParts
        return listOf(cleaned)
    }

    // ------------------------------------------------------------- single verbs

    fun parseSingle(raw: String): Action? {
        val s = digits(raw.trim())
        if (s.isBlank()) return null
        parseDevice(s)?.let { return it }
        parseMedia(s)?.let { return it }
        parseAlarms(s)?.let { return it }
        parseAppsNav(s)?.let { return it }
        parseA11y(s)?.let { return it }
        parseInfo(s)?.let { return it }
        parseNavigate(s)?.let { return it }
        return null
    }

    // -- Devices ---------------------------------------------------------------

    private fun parseDevice(s: String): Action? {
        // Flashlight.
        if (s matches Regex(".*(flash ?light|torch).*")) {
            switchOf(s)?.let { return Action.Torch(it) }
            if (s.contains("toggle")) return Action.Torch(Switch.TOGGLE)
        }
        // Wi-Fi.
        if (s matches Regex(".*wi[- ]?fi.*")) {
            switchOf(s)?.let { return Action.Wifi(it) }
            if (s.contains("toggle")) return Action.Wifi(Switch.TOGGLE)
        }
        // Bluetooth.
        if (s matches Regex(".*(bluetooth|blue ?tooth).*") && !s.contains("pair")) {
            switchOf(s)?.let { return Action.Bluetooth(it) }
            if (s.contains("toggle")) return Action.Bluetooth(Switch.TOGGLE)
        }
        // Do Not Disturb.
        if (s matches Regex(".*(do not disturb|dnd).*") ||
            s.matches(Regex("^(turn on|enable|activate) silent( mode)?$")) ||
            s.matches(Regex("^(turn off|disable|deactivate) silent( mode)?$"))
        ) {
            switchOf(s)?.let { return Action.DoNotDisturb(it) }
            if (s.contains("toggle")) return Action.DoNotDisturb(Switch.TOGGLE)
        }
        // Brightness.
        if (s.contains("brightness") || s.contains("bright ") || s == "brighter" || s == "dimmer" ||
            s == "dim the screen" || s == "brighten the screen"
        ) {
            percentOf(s)?.let { return Action.Brightness(percent = it.coerceIn(1, 100)) }
            if (s.contains("up") || s.contains("brighter") || s.contains("brighten") ||
                s.contains("increase") || s.contains("higher") || s.contains("max")
            ) {
                return if (s.contains("max")) Action.Brightness(percent = 100)
                else Action.Brightness(up = true)
            }
            if (s.contains("down") || s.contains("dim") || s.contains("decrease") ||
                s.contains("lower") || s.contains("min")
            ) {
                return if (s.contains("min")) Action.Brightness(percent = 5)
                else Action.Brightness(up = false)
            }
        }
        return null
    }

    private fun switchOf(s: String): Switch? = when {
        Regex("\\b(turn|switch|set|put)\\b.*\\bon\\b").containsMatchIn(s) ||
            Regex("\\b(enable|activate|start)\\b").containsMatchIn(s) -> Switch.ON
        Regex("\\b(turn|switch|set|put)\\b.*\\boff\\b").containsMatchIn(s) ||
            Regex("\\b(disable|deactivate)\\b").containsMatchIn(s) -> Switch.OFF
        s.startsWith("on ") || s == "on" -> Switch.ON
        s.startsWith("off ") || s == "off" -> Switch.OFF
        else -> null
    }

    // -- Volume/media ----------------------------------------------------------

    private fun parseMedia(s: String): Action? {
        // NOTE: unmute is checked first — "unmute" also contains "mute".
        if (s.matches(Regex("^unmute( (the )?(volume|sound|audio|phone|media))?$")) ||
            s.matches(Regex("^turn (the )?(volume|sound|audio) on$")) ||
            s == "unmute it" || s == "sound on"
        ) {
            return Action.Volume(VolumeDirection.UNMUTE)
        }
        if (s.matches(Regex("^mute( (the )?(volume|sound|audio|phone|media))?$")) ||
            s == "mute it" || s == "silence" ||
            s.matches(Regex("^turn (the )?(volume|sound|audio) off$"))
        ) {
            return Action.Volume(VolumeDirection.MUTE)
        }
        if (s.contains("volume") || s.contains("louder") || s.contains("quieter") ||
            s == "turn it up" || s == "turn it down" || s == "up" || s == "down" ||
            s.contains("sound ")
        ) {
            percentOf(s)?.let { return Action.Volume(VolumeDirection.UP, percent = it.coerceIn(0, 100)) }
            if (s.contains("max") || s.contains("full") || s.contains("100")) {
                return Action.Volume(VolumeDirection.UP, percent = 100)
            }
            if (s.contains("up") || s.contains("louder") || s.contains("higher") ||
                s.contains("increase") || s == "turn it up"
            ) {
                return Action.Volume(VolumeDirection.UP)
            }
            if (s.contains("down") || s.contains("quieter") || s.contains("lower") ||
                s.contains("decrease") || s == "turn it down"
            ) {
                return Action.Volume(VolumeDirection.DOWN)
            }
        }
        return null
    }

    // -- Alarms / timers -------------------------------------------------------

    private fun parseAlarms(s: String): Action? {
        if (s.matches(Regex("^(show|open|list|display)( my)? alarms?$")) ||
            s == "my alarms" || s == "open the clock"
        ) {
            return Action.ShowAlarms
        }
        if (s.contains("alarm") || s.contains("wake me") || s.contains("wake up")) {
            parseAlarmTime(s)?.let { (h, m) ->
                val label = Regex("(?:alarm|up)(?: for| to)?\\s+(.+?)\\s+at\\s+").find("$s ")?.groupValues?.get(1)
                    ?.takeIf { it.isNotBlank() && !it.contains("wake") }
                return Action.SetAlarm(h, m, label?.let(::humanize))
            }
            // "set an alarm" with no time → open the clock app's alarms.
            if (s.matches(Regex("^(set|create|add)( an?)? alarm$"))) return Action.ShowAlarms
            return null
        }
        if (s.contains("timer") || s.contains("countdown") || s.contains("stopwatch")) {
            if (s.contains("stopwatch")) return Action.ShowAlarms
            parseDurationSeconds(s)?.let { secs ->
                if (secs in 1..86_400) return Action.SetTimer(secs)
            }
            return null
        }
        // "remind me in 20 minutes" → timer fallback (no reminder provider needed).
        if (s.startsWith("remind me in ")) {
            parseDurationSeconds(s)?.let { secs ->
                if (secs in 1..86_400) return Action.SetTimer(secs, "Reminder")
            }
        }
        return null
    }

    // -- Apps & navigation -----------------------------------------------------

    private fun parseAppsNav(s: String): Action? {
        if (s.matches(Regex("^(go |press |hit )?back$")) || s == "go back" ||
            s == "press the back button" || s == "previous screen"
        ) {
            return Action.PressBack
        }
        if (s.matches(Regex("^(go |take me |navigate )?(home|to home|home screen)$")) ||
            s == "go to the home screen" || s == "home screen"
        ) {
            return Action.GoHome
        }
        if (s.contains("recent apps") || s == "open recents" || s == "show recent apps") {
            return Action.OpenRecents
        }
        if (s in setOf("show my apps", "show all apps", "all apps", "app list", "my apps", "list apps", "list my apps", "installed apps")) {
            return Action.AppList
        }
        Regex("^close( (this|the|current|my))? app$").matches(s) ||
            s.let { it == "close it" || it == "close this" } ||
            Regex("^close (.+)$").find(s)?.let { m ->
                // "close Instagram" → close current app (works when it is foreground).
                return Action.CloseCurrentApp
            } != null
        if (s.matches(Regex("^close( (this|the|current|my))? app$")) ||
            s == "close it" || s == "close this"
        ) {
            return Action.CloseCurrentApp
        }
        // Camera.
        if (s.matches(Regex("^(open|launch|start)( the)? camera( app)?$")) ||
            s.matches(Regex("^take a (photo|picture|selfie|pic)$")) ||
            s == "open camera" || s == "selfie"
        ) {
            return Action.OpenCamera
        }
        // Open app.
        Regex("^(open|launch|start|go to)\\s+(.+)$").find(s)?.let { m ->
            var app = m.groupValues[2].trim().trimEnd('.', '!', '?')
            if (app.isBlank()) return null
            // "open the camera" handled above; guard settings-ish nouns.
            if (app in setOf("the camera", "camera")) return Action.OpenCamera
            if (app in setOf("settings", "the settings", "phone settings", "system settings")) {
                return Action.OpenApp("Settings")
            }
            app = app.removePrefix("the ").removePrefix("my ")
            if (app.isBlank()) return null
            return Action.OpenApp(humanizeApp(app))
        }
        return null
    }

    // -- Accessibility primitives ----------------------------------------------

    private fun parseA11y(s: String): Action? {
        Regex("^(tap|click|press)( on)?\\s+(.+)$").find(s)?.let { m ->
            val target = m.groupValues[3].trim().trim('"', '\'', '“', '”').trimEnd('.', '!', '?')
            if (target == "back") return Action.PressBack
            if (target in setOf("home", "home button")) return Action.GoHome
            if (target.isBlank()) return null
            return Action.TapText(humanize(target))
        }
        if (s.contains("first result")) return Action.TapFirstResult()
        Regex("^type\\s+(.+)$").find(s)?.let { m ->
            val text = m.groupValues[1].trim().trim('"', '\'', '“', '”')
            if (text.isBlank()) return null
            return Action.TypeText(text, submit = false)
        }
        Regex("^(scroll|swipe)( (up|down|left|right))?( (\\d+) times?)?$").find(s)?.let { m ->
            val dir = when (m.groupValues[3]) {
                "up" -> ScrollDirection.UP
                "left" -> ScrollDirection.LEFT
                "right" -> ScrollDirection.RIGHT
                else -> ScrollDirection.DOWN
            }
            val times = m.groupValues[5].toIntOrNull()?.coerceIn(1, 10) ?: 1
            return Action.Scroll(dir, times)
        }
        if (s == "scroll" || s == "scroll down") return Action.Scroll(ScrollDirection.DOWN)
        if (s == "scroll up") return Action.Scroll(ScrollDirection.UP)
        return null
    }

    // -- Info queries ----------------------------------------------------------

    private fun parseInfo(s: String): Action? {
        if (s.matches(Regex(".*what('| i)?s the time.*")) || s == "time" ||
            s.matches(Regex("^(tell me )?the time( please)?$")) ||
            s == "what time is it" || s == "current time"
        ) {
            return Action.QueryTime
        }
        if (s.matches(Regex(".*(what('| i)?s|tell me)( today'?s?)? date.*")) ||
            s == "date" || s == "what day is it" || s == "what day is today" ||
            s == "today's date" || s == "todays date"
        ) {
            return Action.QueryDate
        }
        if (s.contains("battery") || s.contains("charge left") || s == "battery level" ||
            s == "how much charge"
        ) {
            return Action.QueryBattery
        }
        if (s.matches(Regex(".*(is|are) (the )?(wi[- ]?fi|bluetooth|flash ?light|torch|dnd|do not disturb) (on|off|enabled|connected).*")) ||
            s == "device status" || s == "system status" || s == "status" ||
            s == "how is my phone"
        ) {
            return Action.QueryDeviceStatus
        }
        return null
    }

    // -- Navigation ------------------------------------------------------------

    private fun parseNavigate(s: String): Action? {
        Regex("^(navigate to|take me to|directions to|drive to|show me directions to)\\s+(.+)$")
            .find(s)?.let { m ->
                val dest = m.groupValues[2].trim().trimEnd('.', '!', '?')
                if (dest.isNotBlank()) return Action.Navigate(humanize(dest))
            }
        return null
    }

    // ------------------------------------------------------------------ helpers

    private fun summarize(actions: List<Action>): String =
        actions.joinToString(" · ") { it.label }.take(140)

    private fun humanize(s: String): String =
        s.trim().split(Regex("\\s+")).joinToString(" ") { word ->
            word.replaceFirstChar { it.uppercase() }
        }.take(80)

    private fun humanizeApp(s: String): String {
        val cleaned = s.removeSuffix(" app").trim()
        // Prefer the catalog's canonical display name when we know it.
        return AppCatalog.find(cleaned)?.displayName ?: humanize(cleaned)
    }

    private fun percentOf(s: String): Int? {
        Regex("(\\d{1,3})\\s*(percent|%)").find(s)?.let { return it.groupValues[1].toIntOrNull() }
        if (s.contains("half")) return 50
        return null
    }

    // ------------------------------------------------------------- time parsing

    /** Parse alarm clock times → (hour24, minute). */
    fun parseAlarmTime(raw: String): Pair<Int, Int>? {
        var s = digits(raw.lowercase())
            .replace("o'clock", "").replace("o clock", "")
            .replace(".", "").replace("-", " ")
        s = s.replace(Regex("\\s+"), " ").trim()

        if (s.contains("midnight")) return 0 to 0
        if (s.contains("noon") || s.contains("midday")) return 12 to 0

        Regex("half past (\\d{1,2})").find(s)?.let {
            return it.groupValues[1].toInt() % 24 to 30
        }
        Regex("quarter past (\\d{1,2})").find(s)?.let {
            return it.groupValues[1].toInt() % 24 to 15
        }
        Regex("quarter to (\\d{1,2})").find(s)?.let {
            val h = it.groupValues[1].toInt()
            return (h + 23) % 24 to 45
        }
        // "7:30 pm" / "7 30 pm" / "19:30"
        Regex("(\\d{1,2})(?::|\\s)(\\d{2})\\s*(am|pm)?").find(s)?.let { m ->
            var h = m.groupValues[1].toInt()
            val min = m.groupValues[2].toInt()
            if (h > 23 || min > 59) return null
            h = applyMeridiem(h, m.groupValues[3]) ?: return null
            return h to min
        }
        // "7 pm" / "7" (bare hour after alarm verbs — caller already filtered context)
        Regex("(\\d{1,2})\\s*(am|pm)\\b").find(s)?.let { m ->
            val h = applyMeridiem(m.groupValues[1].toInt(), m.groupValues[2]) ?: return null
            return h to 0
        }
        Regex("(?:alarm|at|for|up)\\s+(\\d{1,2})(?!\\s*\\d)").find("$s ")?.let { m ->
            val h = m.groupValues[1].toInt()
            if (h in 0..23) return h to 0
        }
        return null
    }

    private fun applyMeridiem(h: Int, meridiem: String): Int? {
        if (h !in 0..23) return null
        return when (meridiem) {
            "am" -> if (h == 12) 0 else h % 12
            "pm" -> if (h == 12) 12 else (h % 12) + 12
            else -> if (h in 0..23) h else null
        }
    }

    /** Parse durations ("1 hour 20 minutes", "90 seconds") → total seconds. */
    fun parseDurationSeconds(raw: String): Int? {
        var s = digits(raw.lowercase())
            .replace("half an hour", "30 minutes")
            .replace("half hour", "30 minutes")
            .replace("an hour", "1 hour")
            .replace("a minute", "1 minute")
            .replace("a second", "1 second")
        var total = 0
        var found = false
        Regex("(\\d+)\\s*(hours?|hrs?|hr\\b|h\\b)").findAll(s).forEach {
            total += it.groupValues[1].toInt() * 3600
            found = true
        }
        Regex("(\\d+)\\s*(minutes?|mins?|min\\b|m\\b)").findAll(s).forEach {
            total += it.groupValues[1].toInt() * 60
            found = true
        }
        Regex("(\\d+)\\s*(seconds?|secs?|sec\\b|s\\b)").findAll(s).forEach {
            total += it.groupValues[1].toInt()
            found = true
        }
        return if (found) total else null
    }

    // -------------------------------------------------------------- number words

    /**
     * Convert English number words (0–100) to digits so downstream regexes
     * only deal with numerals. "seven thirty" → "7 30".
     */
    object NumberWords {
        private val ones = mapOf(
            "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4,
            "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9,
            "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13,
            "fourteen" to 14, "fifteen" to 15, "sixteen" to 16, "seventeen" to 17,
            "eighteen" to 18, "nineteen" to 19, "a" to 1, "an" to 1,
        )
        private val tens = mapOf(
            "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50,
            "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90,
        )

        fun toDigits(s: String): String {
            val tokens = s.split(' ')
            val out = ArrayList<String>(tokens.size)
            var i = 0
            while (i < tokens.size) {
                val t = tokens[i].lowercase().trim(',', '.', '-')
                val ten = tens[t]
                if (ten != null) {
                    val next = tokens.getOrNull(i + 1)?.lowercase()?.trim(',', '.', '-')
                    val one = next?.let { ones[it] }
                    if (one != null && one in 1..9) {
                        out += (ten + one).toString()
                        i += 2
                    } else {
                        out += ten.toString()
                        i++
                    }
                    continue
                }
                val one = ones[t]
                if (one != null) {
                    // "one hundred" → 100.
                    val next = tokens.getOrNull(i + 1)?.lowercase()
                    if (one == 1 && next == "hundred") {
                        out += "100"
                        i += 2
                        continue
                    }
                    // Don't rewrite lone "a"/"an" articles mid-sentence.
                    if ((t == "a" || t == "an") && next !in setOf(
                            "hundred", "hour", "minute", "second",
                        )
                    ) {
                        out += tokens[i]
                        i++
                        continue
                    }
                    out += one.toString()
                    i++
                    continue
                }
                out += tokens[i]
                i++
            }
            return out.joinToString(" ")
        }
    }
}
