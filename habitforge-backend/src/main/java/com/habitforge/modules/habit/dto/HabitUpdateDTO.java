package com.habitforge.modules.habit.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新习惯（字段均可选，传哪个改哪个）
 */
@Data
public class HabitUpdateDTO {

    @Size(max = 100, message = "习惯名称最长 100 字")
    private String name;

    @Size(max = 100, message = "身份标签最长 100 字")
    private String identityTag;

    @Pattern(regexp = "^(HEALTH|LEARNING|WORK|LIFE|OTHER)?$", message = "分类取值不合法")
    private String category;

    @Pattern(regexp = "^(DAILY|WEEKLY_DAYS|WEEKLY_COUNT)?$", message = "频率类型不合法")
    private String frequencyType;

    @Pattern(regexp = "^[1-7](,[1-7])*$", message = "频率星期格式应为 1-7 的逗号分隔列表")
    private String frequencyDays;

    private Integer frequencyTarget;

    @Size(max = 200, message = "两分钟版本最长 200 字")
    private String twoMinuteVersion;

    @Size(max = 50, message = "执行时间格式过长")
    private String execTime;

    @Size(max = 100, message = "执行地点最长 100 字")
    private String execPlace;

    @Size(max = 100, message = "习惯叠加描述最长 100 字")
    private String stackAfter;

    /** 是否进行中（归档切换） */
    private Integer isActive;

    private Integer priority;
}
