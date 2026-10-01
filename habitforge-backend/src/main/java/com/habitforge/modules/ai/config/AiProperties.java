package com.habitforge.modules.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 今日安排 — 业务护栏参数（非敏感; 开关/凭证走环境变量, 仿 MinioProperties 两件套）
 */
@Data
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    /** 总开关: false 时生成接口返 6001, 不影响其他功能 */
    private boolean enabled = false;

    /** 每人每日生成次数上限 */
    private int dailyGenerateLimit = 5;

    /** 上下文取近 N 天日记 */
    private int journalDays = 3;

    /** 每篇日记截断字数 */
    private int journalCharsPerDay = 600;

    /** 心得条数上限 */
    private int reflectionLimit = 10;

    /** 心得每字段截断字数 */
    private int reflectionChars = 200;

    /** 有效块少于该数视为解析失败(6005) */
    private int minBlocks = 3;

    /** 有效块超出该数截断 */
    private int maxBlocks = 12;

    // ================= P0 每周复盘报告 =================

    /** 每人每周生成次数上限（含重新生成; 真值在 Redis, 键按周期起始日隔离） */
    private int weeklyReportLimit = 2;

    /** 周报上下文取近 N 天日记（一个自然周 = 7） */
    private int reportJournalDays = 7;

    /** 周报上下文心得条数上限 */
    private int reportReflectionLimit = 15;

    /** 每条日记/心得字段截断字数 */
    private int reportCharsPerItem = 400;

    /** AI 建议条数少于该数视为解析失败（返 6005, 不退额度） */
    private int reportMinSuggestions = 2;
}
