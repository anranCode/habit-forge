package com.habitforge.modules.study.service.support;

import com.habitforge.common.exception.BusinessException;
import com.habitforge.common.exception.ErrorCode;

import java.util.Objects;
import java.util.TreeSet;

/**
 * 客观题服务端判分 —— 纯函数，无依赖。
 * <p>
 * 存储契约：SINGLE "A"；MULTI "A,C" 升序逗号；JUDGE "T"/"F"（SHORT 走 AI，不从这里过）。
 * MULTI 两侧都做归一化（拆字母→大写→去重→排序），因为存量题的正确答案可能是手输乱序
 * （"C,A"），只归一化用户侧会误判 —— 这是计划风险 1 的必做项。
 */
public final class AnswerMatcher {

    private AnswerMatcher() {
    }

    /**
     * @param questionType SINGLE / MULTI / JUDGE（其余题型抛 BAD_REQUEST，SHORT 另有 AI 通道）
     * @return 是否答对；空作答由调用方（grade 服务）先行拒绝
     */
    public static boolean match(String questionType, String storedAnswer, String userAnswer) {
        Objects.requireNonNull(userAnswer, "空作答应由调用方先挡");
        return switch (questionType) {
            case "SINGLE", "JUDGE" -> normLetterRun(storedAnswer).equals(normLetterRun(userAnswer));
            case "MULTI" -> normLetters(storedAnswer).equals(normLetters(userAnswer));
            default -> throw new BusinessException(ErrorCode.BAD_REQUEST, "该题型不走服务端判分: " + questionType);
        };
    }

    /** SINGLE/JUDGE：整串大写比较（"a "/"A" 等价；用户多敲就等值失败，判错而非 400——刷题误点不该炸接口） */
    private static String normLetterRun(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase();
    }

    /** MULTI：只取 A-Z 字母，去重排序后拼接 —— "C,A"/"a c"/"ACC" 全部归一成 "AC" */
    private static String normLetters(String raw) {
        if (raw == null) {
            return "";
        }
        TreeSet<Character> letters = new TreeSet<>();
        for (char c : raw.toUpperCase().toCharArray()) {
            if (c >= 'A' && c <= 'Z') {
                letters.add(c);
            }
        }
        StringBuilder sb = new StringBuilder(letters.size());
        letters.forEach(sb::append);
        return sb.toString();
    }
}
