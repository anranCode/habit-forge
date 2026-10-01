package com.habitforge.modules.study.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 开始学习计时（科目/章节都可选）
 */
@Data
public class StudySessionStartRequest {

    @Size(max = 36, message = "科目ID非法")
    private String subjectId;

    @Size(max = 36, message = "章节ID非法")
    private String chapterId;
}
