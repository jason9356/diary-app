package com.personaldiary.android.ai

import com.personaldiary.android.data.DiaryEntry

/**
 * Reserved AI hooks — no model wired yet.
 * Enable in settings; calls currently return stub messages.
 */
interface AiHooks {
    val enabled: Boolean
    fun summarizeCards(cards: List<DiaryEntry>, rangeLabel: String): String
    fun dailyDigest(date: String, cards: List<DiaryEntry>): String
    fun suggestTags(card: DiaryEntry): List<String>
}

class NoOpAiHooks(
    override val enabled: Boolean = false,
) : AiHooks {
    override fun summarizeCards(cards: List<DiaryEntry>, rangeLabel: String): String =
        if (!enabled) "AI 未开启"
        else "（预留）将总结 ${cards.size} 张卡片 · $rangeLabel"

    override fun dailyDigest(date: String, cards: List<DiaryEntry>): String =
        if (!enabled) "AI 未开启"
        else "（预留）$date 日报 · ${cards.size} 条灵感"

    override fun suggestTags(card: DiaryEntry): List<String> =
        if (!enabled) emptyList()
        else card.tags
}
