package com.habitforge.modules.ai.client;

import com.habitforge.modules.ai.dto.PlanDraft;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;

/**
 * 排程 Prompt 模板：system 规则固定 + BeanOutputConverter 自动追加 JSON 格式指令并解析
 * （不依赖 provider 原生 JSON mode, 靠 prompt 约束 + PlanJsonParser 解析容错）
 */
@Component
public class PlanPromptTemplate {

    /** 结构化输出转换器（默认 prompt-based: getFormat() 生成 schema 指令, convert() 提取+反序列化） */
    private final BeanOutputConverter<PlanDraft> converter = new BeanOutputConverter<>(PlanDraft.class);

    public String systemPrompt() {
        return SYSTEM;
    }

    /** 追加到 user prompt 末尾的格式指令（含 JSON Schema） */
    public String formatInstructions() {
        return converter.getFormat();
    }

    /** 模型原始输出 → PlanDraft（提取/反序列化失败抛异常, 调用方翻译 6005） */
    public PlanDraft convert(String rawOutput) {
        return converter.convert(rawOutput);
    }

    private static final String SYSTEM = """
            你是 HabitForge 的每日时间规划助手。用户会给出当天的空闲时段、待打卡习惯、学习任务、近期日记与心得，\
            你需要为用户安排一天的时间块（blocks）。

            【输出要求】只输出一个合法的 JSON 对象本身，禁止输出 Markdown 代码围栏、解释或任何多余文字。
            JSON 结构: {"blocks":[{"start":"HH:mm","end":"HH:mm","title":"...","type":"HABIT|STUDY|REST|OTHER","habitId":"...","subjectId":"...","chapterId":"..."}]}
            - start/end 为 24 小时制 HH:mm；不需要关联的 id 字段填 null。

            【硬性规则】
            1. 所有时间块必须完全落在用户给出的空闲时段之内，块与块之间不得重叠。
            2. 单个块时长不超过 120 分钟。
            3. habitId / subjectId / chapterId 只能从上下文清单里挑选，严禁编造 ID。
            4. 安排优先级：有执行时间(exec_time)的习惯 > 临考或到期复习卡多的学习任务 > 其他习惯 > 休息。
            5. 连续的学习/专注块之间要穿插 REST 休息块（type=REST，如"休息 10 分钟"）。
            6. title 用简体中文，不超过 30 个字，动词开头、具体可执行（如"晨跑 30 分钟""复习高数第3章"）。
            7. 结合近期日记与心得调整安排：疲惫时减量、注明重点复习薄弱环节。
            """;
}
