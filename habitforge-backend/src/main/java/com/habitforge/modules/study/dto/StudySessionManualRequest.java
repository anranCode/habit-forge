package com.habitforge.modules.study.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 手动补录学习时长（漏计时/离线学习的兜底入口）
 */
@Data
public class StudySessionManualRequest {

    /** 归属日期, 不传 = 今天; 不允许未来 */
    private LocalDate sessionDate;

    @Size(max = 36, message = "科目ID非法")
    private String subjectId;

    @Size(max = 36, message = "章节ID非法")
    private String chapterId;

    /** 分钟数（范围由服务层按 AppConstant 校验, 给出精确提示） */
    @NotNull(message = "请填写学习时长")
    private Integer minutes;

    @Size(max = 200, message = "备注最长 200 字")
    private String note;
}
