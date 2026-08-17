package com.habitforge.common.enums;

import lombok.Getter;

/**
 * 习惯类型（BAD 为预留：坏习惯戒断模式）
 */
@Getter
public enum HabitTypeEnum {

    GOOD("好习惯"),
    BAD("坏习惯");

    private final String label;

    HabitTypeEnum(String label) {
        this.label = label;
    }
}
