package com.jarvis.assistant

/** Recent incoming messages, kept in memory only (never written to disk). */
object Inbox {
    private val items = ArrayList<Pair<String, String>>()

    @Synchronized
    fun add(who: String, text: String) {
        if (items.lastOrNull() == Pair(who, text)) return
        items.add(Pair(who, text))
        while (items.size > 10) items.removeAt(0)
    }

    @Synchronized
    fun drain(): List<Pair<String, String>> {
        val copy = items.toList()
        items.clear()
        return copy
    }
}
