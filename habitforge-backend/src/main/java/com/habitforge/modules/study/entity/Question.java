package com.habitforge.modules.study.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 题库题目（options 存 JSON 数组字符串, 服务层 hutool JSONUtil 序列化/校验, 不用 TypeHandler）
 */
@Data
@TableName("questions")
public class Question {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String userId;

    private String subjectId;

    /** 所属章节(null=不挂章节) */
    private String chapterId;

    /** SINGLE单选 / MULTI多选 / JUDGE判断 / SHORT简答 */
    private String questionType;

    /** 题干(可含 Markdown) */
    private String stem;

    /** 选项 JSON 数组字符串(简答/判断可空) */
    private String options;

    private String answer;

    private String analysis;

    /** 来源: PAST_EXAM真题 / TEXTBOOK教材 / CUSTOM自编 / AI生成(服务层不强校验枚举, 前端契约为准) */
    private String sourceType;

    /** 来源详情, 如 2025年10月真题 */
    private String sourceDetail;

    /** 难度 1-5 */
    private Integer difficulty;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
