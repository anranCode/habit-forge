package com.habitforge.modules.ai.client;

import com.habitforge.modules.ai.dto.QuestionPointsDraft;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;

/**
 * 考点识别 Prompt 模板（沿 PlanPromptTemplate 的 BeanOutputConverter 套路）。
 * 口径与手工录入/存量数据一致: ≤8 个、每个 ≤20 字的中文短语（AiProperties points-max-per-question /
 * point-max-chars 是阈值事实源, SYSTEM 文案与之同步）。
 */
@Component
public class QuestionPointsPromptTemplate {

    private final BeanOutputConverter<QuestionPointsDraft> converter = new BeanOutputConverter<>(QuestionPointsDraft.class);

    public String systemPrompt() {
        return SYSTEM;
    }

    /** 追加到 user prompt 末尾的格式指令（含 JSON Schema） */
    public String formatInstructions() {
        return converter.getFormat();
    }

    /** 模型原始输出 → QuestionPointsDraft（失败抛异常, 调用方翻译 6005） */
    public QuestionPointsDraft convert(String rawOutput) {
        return converter.convert(rawOutput);
    }

    private static final String SYSTEM = """
            你是 HabitForge 题库的考点分析助手。用户会给出一道题目（题型、题干、选项、参考答案、解析等分节信息），\
            你需要识别这道题考查的知识点（考点）。

            【输出要求】只输出一个合法的 JSON 对象本身，禁止输出 Markdown 代码围栏、解释或任何多余文字。
            JSON 结构: {"points":["考点一","考点二"]}

            【考点选取规则】
            1. 数量 3–8 个; 题目考查面确实窄时 1–2 个也可以, 宁缺毋滥, 严禁硬凑。
            2. 每个考点是不超过 20 个字的中文短语, 直接可作为复习标签（如"中值定理""复合函数求导""辛亥革命意义"）。
            3. 考点要落在"这道题实际考了什么", 优先取解析中点名的概念; 没有解析时依据题干与参考答案推断。
            4. 禁止输出学科大类名（如"数学""计算机"）或空泛标签（如"基础知识""重点内容"）。
            5. 同一知识单元不要拆成多个近义考点（如"导数定义"与"导数的定义"算一个）。
            """;
}
