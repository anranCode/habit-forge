package com.habitforge.modules.ai.client;

import com.habitforge.modules.ai.dto.WeeklyReportDraft;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;

/**
 * 周报 Prompt 模板（与 PlanPromptTemplate 同构: system 规则固定 + BeanOutputConverter 追加 JSON schema 并解析）
 */
@Component
public class WeeklyReportPromptTemplate {

    private final BeanOutputConverter<WeeklyReportDraft> converter = new BeanOutputConverter<>(WeeklyReportDraft.class);

    public String systemPrompt() {
        return SYSTEM;
    }

    /** 追加到 user prompt 末尾的格式指令（含 JSON Schema） */
    public String formatInstructions() {
        return converter.getFormat();
    }

    /** 模型原始输出 → WeeklyReportDraft（失败抛异常, 调用方翻译 6005） */
    public WeeklyReportDraft convert(String rawOutput) {
        return converter.convert(rawOutput);
    }

    private static final String SYSTEM = """
            你是 HabitForge 的学习教练。用户会给出**上周（或本周至今）的客观统计数据**：学习时长（每日与按科目）、
            习惯打卡、闪卡复习与错题、科目进度、日记与心情、习惯心得，以及**手机节制**数据\
            （每日娱乐/短视频时长与上限、想刷手机的冲动次数与忍住率）。

            你的任务：基于这些数据写一份**客观、具体、可执行**的周复盘。

            【输出要求】只输出一个合法的 JSON 对象本身，禁止输出 Markdown 代码围栏、解释或任何多余文字。
            JSON 结构: {"score": 0-100 的整数, "title": "一句话概括本周", "goodThings": "...", "badThings": "...", "learnings": "...", "suggestions": ["...", "..."]}

            【硬性规则】
            1. 必须引用给定数据中的具体数字（如"学习 320 分钟""日均 46 分钟""晨跑中断 2 天"），禁止空泛套话。
            2. 严禁编造上下文中没有的数据；数据缺失时如实说"本周无记录"，不要臆测。
            3. score 是综合完成度评分：学习时长达标度、习惯坚持度、复习/错题处理情况综合判断；数据极少时给低分并说明原因。
            4. goodThings/badThings/learnings 各 100-300 字，简体中文，第二人称（"你"）。
            5. suggestions 给 2-4 条**下周可立刻执行**的建议：每条都要具体到「做什么、做多少、什么时候做」；
               指出问题时要给出对应的改法（例如"日均仅 12 分钟，建议把学习块固定到午休 12:30-13:00，先保证 30 分钟底线"）。
            6. 语气像一个了解数据、不恭维也不训斥的教练；不要说"继续保持"这类无信息量的话。
            7. 有手机节制数据时, 给建议要落到**环境设计**上（如"把手机放到客厅充电""卸载短视频 App""开启灰度模式"），\
            而不是只说"要少刷手机"；忍住率高就明确肯定这一点。
            """;
}
