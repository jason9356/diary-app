package com.personaldiary.android.data

import java.time.LocalDate
import java.util.UUID

/** One-shot sample cards / todos so the UI has something to read. */
object DemoSeed {
    fun ensure(repo: DiaryRepository, todos: NativeTodoStore, prefs: AppPrefs) {
        if (prefs.demoSeeded) return
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val todayStr = today.toString()
        val yesterdayStr = yesterday.toString()
        val now = DiaryDates.nowIso()

        repo.writeDayContext(
            DayContext(
                date = todayStr,
                location = "杭州 · 西湖",
                weather = "多云",
                tempC = 28.0,
                device = DeviceLabels.currentPhone(),
                contextSource = "phone",
                contextUpdatedAt = now,
                updatedAt = now,
            ),
        )
        repo.writeDayContext(
            DayContext(
                date = yesterdayStr,
                location = "上海 · 静安",
                weather = "小雨",
                tempC = 24.0,
                device = "Windows PC",
                contextSource = "desktop",
                contextUpdatedAt = now,
                updatedAt = now,
            ),
        )

        fun saveNote(date: String, body: String, createdOffsetMin: Long = 0) {
            val id = UUID.randomUUID().toString()
            val created = java.time.OffsetDateTime.now().minusMinutes(createdOffsetMin).toString()
            repo.save(
                DiaryEntry(
                    id = id,
                    entryDate = date,
                    title = date,
                    body = body.trimIndent(),
                    createdAt = created,
                    updatedAt = created,
                ),
            )
        }

        saveNote(
            todayStr,
            """
            傍晚路过湖边，风把柳条掀起来。

            记下三件小事：
            1. 把灵感匣编辑页收成**一整块纸面**
            2. 阅读元信息改成地点 / 设备 / 天气三行
            3. 主题恢复四套：青笺、苔墨、匣光、素昼

            #产品 #今日
            """.trimIndent(),
            40,
        )
        saveNote(
            todayStr,
            """
            ## 一段带 Markdown 的草稿

            有时会写 *斜体*，有时 **加粗强调**。

            - 购物：牛奶
            - 回邮件：待办卡片间距

            也可以夹一句链接文案 [灵感匣](https://example.com)，阅读时只留文字。

            #草稿 #markdown
            """.trimIndent(),
            20,
        )
        saveNote(
            yesterdayStr,
            """
            昨天在电脑前写完一页，窗外忽然下雨。

            > 先把结构搭对，细节会自己长出来。

            #随笔 #桌面
            """.trimIndent(),
            60 * 20,
        )
        saveNote(
            yesterdayStr,
            """
            短记：不要再把标签框、工具栏、正文拆成三块了。

            正文里写 `#标签` 就够。

            #设计
            """.trimIndent(),
            60 * 18,
        )

        listOf(
            "整理灵感匣阅读页元信息",
            "试一遍四套主题切换",
            "写两篇带 #标签 的测试卡片",
        ).forEach { todos.add(it) }

        prefs.demoSeeded = true
    }
}
