package com.mio.ai.core.commands

/**
 * Curated catalog of popular apps for fast, reliable voice launching.
 *
 * Why a catalog instead of scanning packages? On Android 11+ an app can only
 * see packages declared in `<queries>` — a curated package map plus the
 * generic LAUNCHER query (see AndroidManifest) covers the popular cases, and
 * [com.mio.ai.system.AppLauncher] falls back to a launcher-wide fuzzy search
 * for everything else.
 *
 * Pure Kotlin — unit-testable.
 */
object AppCatalog {

    data class Entry(
        val packageName: String,
        val displayName: String,
        val aliases: Set<String> = emptySet(),
    )

    private val ENTRIES: List<Entry> = listOf(
        Entry("com.instagram.android", "Instagram", setOf("insta", "ig")),
        Entry("com.whatsapp", "WhatsApp", setOf("whatsapp", "whats app")),
        Entry("com.facebook.katana", "Facebook", setOf("fb")),
        Entry("com.facebook.orca", "Messenger", setOf("fb messenger")),
        Entry("com.zhiliaoapp.musically", "TikTok", setOf("tik tok", "ticktock")),
        Entry("com.snapchat.android", "Snapchat", setOf("snap chat")),
        Entry("com.twitter.android", "X", setOf("twitter", "x app")),
        Entry("com.reddit.frontpage", "Reddit"),
        Entry("com.linkedin.android", "LinkedIn", setOf("linked in")),
        Entry("com.pinterest", "Pinterest"),
        Entry("com.google.android.youtube", "YouTube", setOf("you tube", "yt")),
        Entry("com.google.android.apps.youtube.music", "YouTube Music", setOf("yt music")),
        Entry("com.spotify.music", "Spotify"),
        Entry("com.apple.android.music", "Apple Music"),
        Entry("com.soundcloud.android", "SoundCloud"),
        Entry("com.netflix.mediaclient", "Netflix"),
        Entry("com.disney.disneyplus", "Disney+", setOf("disney plus", "disney")),
        Entry("com.amazon.avod.thirdpartyclient", "Prime Video", setOf("amazon prime", "prime video")),
        Entry("com.hbo.hbonow", "Max", setOf("hbo", "hbo max")),
        Entry("com.google.android.apps.nbu.files", "Files", setOf("file manager", "my files")),
        Entry("com.google.android.gm", "Gmail", setOf("g mail", "email", "mail")),
        Entry("com.google.android.apps.maps", "Google Maps", setOf("maps")),
        Entry("com.waze", "Waze"),
        Entry("com.uber.request", "Uber"),
        Entry("com.google.android.apps.photos", "Google Photos", setOf("photos", "gallery")),
        Entry("com.sec.android.gallery3d", "Gallery", setOf("samsung gallery")),
        Entry("com.google.android.dialer", "Phone", setOf("dialer", "phone app")),
        Entry("com.samsung.android.dialer", "Phone", setOf("samsung phone")),
        Entry("com.google.android.apps.messaging", "Messages", setOf("sms", "text messages", "messaging")),
        Entry("com.samsung.android.messaging", "Messages", setOf("samsung messages")),
        Entry("com.telegram.messenger", "Telegram"),
        Entry("com.discord", "Discord"),
        Entry("com.google.android.chrome", "Chrome", setOf("google chrome", "browser")),
        Entry("com.sec.android.app.sbrowser", "Samsung Internet", setOf("samsung browser", "internet")),
        Entry("org.mozilla.firefox", "Firefox"),
        Entry("com.opera.browser", "Opera"),
        Entry("com.brave.browser", "Brave"),
        Entry("com.duolingo", "Duolingo"),
        Entry("com.google.android.calendar", "Calendar", setOf("google calendar")),
        Entry("com.google.android.deskclock", "Clock", setOf("alarm", "clock app")),
        Entry("com.sec.android.app.clockpackage", "Clock", setOf("samsung clock")),
        Entry("com.google.android.calculator", "Calculator"),
        Entry("com.sec.android.app.popupcalculator", "Calculator", setOf("samsung calculator")),
        Entry("com.google.android.keep", "Keep Notes", setOf("keep", "notes", "google keep")),
        Entry("com.samsung.android.app.notes", "Samsung Notes"),
        Entry("com.evernote", "Evernote"),
        Entry("com.notion.id", "Notion"),
        Entry("com.microsoft.teams", "Teams", setOf("microsoft teams")),
        Entry("com.microsoft.office.outlook", "Outlook"),
        Entry("com.zoom.videomeetings", "Zoom"),
        Entry("com.google.android.apps.meetings", "Google Meet", setOf("meet")),
        Entry("com.skype.raider", "Skype"),
        Entry("com.google.android.apps.docs", "Google Docs", setOf("docs")),
        Entry("com.google.android.apps.docs.editors.sheets", "Google Sheets", setOf("sheets")),
        Entry("com.google.android.apps.docs.editors.slides", "Google Slides", setOf("slides")),
        Entry("com.google.android.apps.drive", "Google Drive", setOf("drive")),
        Entry("com.amazon.mShop.android.shopping", "Amazon", setOf("amazon shopping")),
        Entry("com.flipkart.android", "Flipkart"),
        Entry("com.ebay.mobile", "eBay", setOf("ebay")),
        Entry("com.alibaba.aliexpresshd", "AliExpress", setOf("ali express")),
        Entry("com.shopee.app", "Shopee"),
        Entry("com.zzkko", "SHEIN", setOf("shein", "she in")),
        Entry("com.google.android.apps.walletnfcrel", "Google Wallet", setOf("wallet", "gpay", "google pay")),
        Entry("com.paypal.android.p2pmobile", "PayPal"),
        Entry("com.phonepe.app", "PhonePe", setOf("phone pe")),
        Entry("net.one97.paytm", "Paytm", setOf("pay tm")),
        Entry("com.swiggy.android", "Swiggy"),
        Entry("in.swiggy.android", "Swiggy"),
        Entry("com.application.zomato", "Zomato"),
        Entry("com.google.android.play.games", "Play Games", setOf("games")),
        Entry("com.supercell.clashofclans", "Clash of Clans", setOf("coc")),
        Entry("com.supercell.clashroyale", "Clash Royale"),
        Entry("com.king.candycrushsaga", "Candy Crush", setOf("candy crush saga")),
        Entry("com.miniclip.eightballpool", "8 Ball Pool", setOf("eight ball pool")),
        Entry("us.zoom.videomeetings", "Zoom"),
        Entry("com.google.android.settings.intelligence", "Settings", setOf("phone settings", "system settings")),
    )

    /** Normalize user speech for matching: lowercase, drop punctuation/filler. */
    fun normalize(raw: String): String =
        raw.lowercase()
            .replace(Regex("[^a-z0-9+ ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    /**
     * Best catalog match for a spoken app name, or null.
     * Scoring: exact display/alias (100) > starts-with (80) > token overlap (50+) > contains (40).
     */
    fun find(spoken: String): Entry? {
        val q = normalize(spoken)
        if (q.isBlank()) return null
        var best: Entry? = null
        var bestScore = 0
        for (e in ENTRIES) {
            val names = listOf(e.displayName) + e.aliases
            for (n in names) {
                val score = score(q, normalize(n))
                if (score > bestScore) {
                    bestScore = score
                    best = e
                }
            }
        }
        return if (bestScore >= 40) best else null
    }

    private fun score(query: String, name: String): Int {
        if (query == name) return 100
        if (name.startsWith(query) || query.startsWith(name)) return 80
        val qTokens = query.split(' ').filter { it.length > 1 }.toSet()
        val nTokens = name.split(' ').filter { it.length > 1 }.toSet()
        if (qTokens.isNotEmpty() && nTokens.isNotEmpty()) {
            val overlap = (qTokens intersect nTokens).size
            if (overlap == qTokens.size && overlap == nTokens.size) return 95
            if (overlap > 0) return 50 + 10 * overlap
        }
        // Contains fallback needs a 4+ char overlap — short aliases ("ig",
        // "yt") would otherwise false-match inside long nonsense queries.
        if ((name.contains(query) || query.contains(name)) && minOf(name.length, query.length) >= 4) {
            return 40
        }
        return 0
    }

    /** All display names (for "what apps can you open" + disambiguation). */
    fun displayNames(): List<String> = ENTRIES.map { it.displayName }.distinct().sorted()
}
