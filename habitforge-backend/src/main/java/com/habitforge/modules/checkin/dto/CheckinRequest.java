package com.habitforge.modules.checkin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CheckinRequest {

    @NotBlank(message = "习惯ID不能为空")
    private String habitId;

    /** 打卡日期，缺省为今天（由前端传以避免时区问题） */
    private LocalDate checkDate;

    @Size(max = 500, message = "备注最长 500 字")
    private String note;
}
