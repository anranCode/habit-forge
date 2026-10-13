package com.habitforge.modules.ai.dto;

import java.util.List;

/**
 * 考点识别 LLM 输出草稿（BeanOutputConverter 目标类型）。
 * 形状即 {"points":["中值定理","极限计算"]} —— 与存储列 knowledge_points 的 JSON 字符串数组同构，
 * 解析层清洗（去空/去重/截断）后直接落库。
 */
public record QuestionPointsDraft(List<String> points) {
}
