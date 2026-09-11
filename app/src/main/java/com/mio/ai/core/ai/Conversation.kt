package com.mio.ai.core.ai

import com.mio.ai.core.util.MiniJson
import java.io.File

/** One conversation turn kept for context. */
data class Turn(val role: Role, val text: String, val atMillis: Long = System.currentTimeMillis()) {
    enum class Role { USER, ASSISTANT }
}

/**
 * Short-term conversation memory: last N turns, persisted as JSON so context
 * survives process restarts. Backed by a plain file so it stays unit-testable
 * (the app passes `File(filesDir, "conversation.json")`).
 */
class ConversationStore(private val file: File, private val maxTurns: Int = 40) {

    private val lock = Any()
    private val turns = ArrayList<Turn>()

    init {
        load()
    }

    fun addUser(text: String) = add(Turn(Turn.Role.USER, text))

    fun addAssistant(text: String) = add(Turn(Turn.Role.ASSISTANT, text))

    private fun add(turn: Turn) {
        synchronized(lock) {
            turns += turn
            while (turns.size > maxTurns) turns.removeAt(0)
        }
        save()
    }

    fun recent(n: Int = 10): List<Turn> = synchronized(lock) { turns.takeLast(n).toList() }

    fun all(): List<Turn> = synchronized(lock) { turns.toList() }

    fun clear() {
        synchronized(lock) { turns.clear() }
        save()
    }

    /** Last assistant question — used to resolve follow-ups like "the second one". */
    fun lastAssistantText(): String? =
        synchronized(lock) { turns.lastOrNull { it.role == Turn.Role.ASSISTANT }?.text }

    fun toChatMessages(n: Int = 10): List<ChatMessage> = recent(n).map {
        ChatMessage(
            role = if (it.role == Turn.Role.USER) ChatMessage.Role.USER else ChatMessage.Role.ASSISTANT,
            content = it.text,
        )
    }

    private fun load() {
        try {
            if (!file.exists()) return
            val list = MiniJson.asList(MiniJson.parse(file.readText()))
            synchronized(lock) {
                turns.clear()
                list.mapNotNull { item ->
                    val m = MiniJson.asMap(item)
                    val role = MiniJson.asString(m["role"])?.let {
                        runCatching { Turn.Role.valueOf(it) }.getOrNull()
                    } ?: return@mapNotNull null
                    val text = MiniJson.asString(m["text"]) ?: return@mapNotNull null
                    Turn(role, text, MiniJson.asLong(m["at"]) ?: System.currentTimeMillis())
                }.takeLast(maxTurns).forEach { turns += it }
            }
        } catch (_: Exception) {
            // Corrupt history must never crash startup — start fresh.
            synchronized(lock) { turns.clear() }
        }
    }

    private fun save() {
        try {
            val snapshot = synchronized(lock) { turns.toList() }
            val payload = snapshot.map {
                mapOf("role" to it.role.name, "text" to it.text, "at" to it.atMillis)
            }
            file.parentFile?.mkdirs()
            file.writeText(MiniJson.stringify(payload))
        } catch (_: Exception) {
            // Persistence is best-effort.
        }
    }
}
