package com.sparkbox.android.ai

import com.sparkbox.android.data.SparkEntry

/**
 * Reserved AI hooks — no model wired yet.
 * Enable in settings; calls currently return stub messages.
 */
interface AiHooks {
    val enabled: Boolean
    fun summarizeCards(cards: List<SparkEntry>, rangeLabel: String): String
    fun dailyDigest(date: String, cards: List<SparkEntry>): String
    fun suggestTags(card: SparkEntry): List<String>
}

class NoOpAiHooks(
    override val enabled: Boolean = false,
) : AiHooks {
    override fun summarizeCards(cards: List<SparkEntry>, rangeLabel: String): String =
        if (!enabled) "AI 未开启"
        else "（预留）将总结 ${cards.size} 张卡片 · $rangeLabel"

    override fun dailyDigest(date: String, cards: List<SparkEntry>): String =
        if (!enabled) "AI 未开启"
        else "（预留）$date 日报 · ${cards.size} 条灵感"

    override fun suggestTags(card: SparkEntry): List<String> =
        if (!enabled) emptyList()
        else card.tags
}
