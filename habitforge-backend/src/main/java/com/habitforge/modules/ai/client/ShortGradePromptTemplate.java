package com.habitforge.modules.ai.client;

import com.habitforge.modules.ai.dto.ShortGradeDraft;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;

/**
 * 主观题判分 Prompt 模板（沿 PlanPromptTemplate 的 BeanOutputConverter 套路）。
 * 核心口径（计划决策 6）: 模型只拆要点+逐点判 hit, **不输出对错** ——
 * 对错由服务端数 hit 按阈值换算, schema 里根本没有 correct 字段可让它填。
 */
@Component
public class ShortGradePromptTemplate {

    /** 结构化输出转换器（prompt-based JSON: getFormat() 生成 schema 指令, convert() 提取+反序列化） */
    private final BeanOutputConverter<ShortGradeDraft> converter = new BeanOutputConverter<>(ShortGradeDraft.class);

    public String systemPrompt() {
        return SYSTEM;
    }

    /** 追加到 user prompt 末尾的格式指令（含 JSON Schema） */
    public String formatInstructions() {
        return converter.getFormat();
    }

    /** 模型原始输出 → ShortGradeDraft（提取/反序列化失败抛异常, 调用方翻译 6005） */
    public ShortGradeDraft convert(String rawOutput) {
        return converter.convert(rawOutput);
    }

    private static final String SYSTEM = """
            你是 HabitForge 的阅卷助手。用户会给出是一道主观题的题干、参考答案与学生的作答，\
            你需要把参考答案拆解成要点，并逐条判断学生作答是否覆盖了该要点。

            【输出要求】只输出一个合法的 JSON 对象本身，禁止输出 Markdown 代码围栏、解释或任何多余文字。
            JSON 结构: {"points":[{"point":"要点表述","hit":true}]}
            - point: 从参考答案拆出的一个独立得分要点，用简体中文表述，不超过 50 个字。
            - hit: 布尔值, true = 学生作答覆盖了该要点, false = 未覆盖。

            【拆解规则】
            1. 要点数量 1–8 个, 按参考答案的语义单元拆分, 一个要点只说一件事, 不要把整句答案原样照抄成"要点"。
            2. 参考答案只有笼统一句话时, 拆成 1 个要点即可, 不要硬凑。

            【判定规则】
            3. 同义表述、等价说法、更具体但方向正确的表述都算覆盖（如答案"生产力决定生产关系", 作答"生产力的发展会推动生产关系变化"算 hit）。
            4. 沾边但空泛的复述、答非所问、只有结论没有理由(当参考答案要求理由时)、与答案矛盾的表述都不算 hit。
            5. 学生作答完全没涉及某要点时 hit=false, 不要因为"沾边"而放水。

            【禁止事项】
            6. 不要输出对错结论、分数、评语、改进建议 —— 你唯一的任务是把答案拆成要点并逐条判 hit, 对错由系统按阈值换算。
            """;
}
