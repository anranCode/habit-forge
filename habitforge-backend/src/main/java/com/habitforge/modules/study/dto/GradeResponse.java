package com.habitforge.modules.study.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 判分结果：判完才回显标准答案与解析（作答阶段题目里根本没有它们）。
 */
@Data
@Builder
public class GradeResponse {

    private String questionId;
    private String questionType;
    private boolean correct;
    /** 标准答案（SHORT 为参考答案全文） */
    private String standardAnswer;
    private String analysis;
    /** 仅 SHORT 非 null */
    private AiGrade aiGrade;
    /** 仅 SHORT 非 null（question_grades 行 id, 留痕回查用） */
    private String gradeId;
    /** 判分副作用后的错题本状态; null = 不在错题本(答对且本无记录) */
    private WrongBook wrongBook;

    /** SHORT 的 AI 拆点结果; 判分口径: correct = covered*2 >= total（阈值只在服务端一处, 模型不输出对错） */
    @Data
    @Builder
    public static class AiGrade {
        private List<PointHit> points;
        private int covered;
        private int total;
        private double ratio;
    }

    @Data
    @Builder
    public static class PointHit {
        private String point;
        private boolean hit;
    }

    @Data
    @Builder
    public static class WrongBook {
        private boolean inBook;
        private int wrongCount;
        private int correctStreak;
        private boolean mastered;
    }
}
