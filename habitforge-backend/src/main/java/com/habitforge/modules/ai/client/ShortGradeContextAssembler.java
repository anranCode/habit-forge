package com.habitforge.modules.ai.client;

import com.habitforge.common.constant.AppConstant;
import com.habitforge.modules.study.entity.Question;
import org.springframework.stereotype.Component;

/**
 * 主观题判分上下文拼装（纯字符串活, 不查库 —— 题已由判分入口加载并校验归属）。
 * 上下文极小是同步判分成立的前提（计划决策 3）: 题干截 500、参考答案全文、作答截 2000。
 */
@Component
public class ShortGradeContextAssembler {

    /** 题干超过这个长度就只是背景信息(判分真正依据是参考答案与作答), 截断省 token */
    private static final int STEM_MAX_CHARS = 500;

    public String assemble(Question question, String userAnswer) {
        StringBuilder sb = new StringBuilder();
        sb.append("【题干】\n").append(truncate(question.getStem(), STEM_MAX_CHARS)).append("\n\n");
        sb.append("【参考答案】\n").append(nullToDash(question.getAnswer())).append("\n\n");
        sb.append("【学生作答】\n").append(truncate(userAnswer, AppConstant.SHORT_GRADE_ANSWER_MAX_CHARS));
        return sb.toString();
    }

    private static String nullToDash(String text) {
        return text == null || text.isBlank() ? "（无参考答案）" : text.strip();
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        String t = text.strip();
        return t.length() <= max ? t : t.substring(0, max);
    }
}
