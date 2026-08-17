package com.habitforge.common.enums;

import lombok.Getter;

/**
 * 习惯分类
 */
@Getter
public enum CategoryEnum {

    HEALTH("健康"),
    LEARNING("学习"),
    WORK("工作"),
    LIFE("生活"),
    OTHER("其他");

    private final String label;

    CategoryEnum(String label) {
        this.label = label;
    }

    public static boolean isValid(String name) {
        for (CategoryEnum c : values()) {
            if (c.name().equals(name)) {
                return true;
            }
        }
        return false;
    }
}
