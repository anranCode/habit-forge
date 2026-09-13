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
}
