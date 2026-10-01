package com.habitforge.modules.focus.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 录入/修改某日的注意力数据（覆盖式：这三个字段以本次提交为准；冲动计数不在此接口）
 */
@Data
public class FocusLogRequest {

    /** 不传 = 今天；允许补录过去，拒绝未来 */
    private LocalDate logDate;

    /** 娱乐/短视频时长（分钟） */
    @NotNull(message = "请填写娱乐时长")
    private Integer entertainmentMinutes;

    /** 拿起手机次数（可选，不传 = 清空） */
    private Integer pickups;

    @Size(max = 200, message = "备注最长 200 字")
    private String note;
}
