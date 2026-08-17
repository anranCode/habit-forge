package com.habitforge.common.enums;

import lombok.Getter;

/**
 * 打卡频率类型
 */
@Getter
public enum FrequencyTypeEnum {

    /** 每天打卡 */
    DAILY("每日"),
    /** 每周指定几天，如周一三五 */
    WEEKLY_DAYS("每周指定几天"),
    /** 每周任意 N 次 */
    WEEKLY_COUNT("每周N次");

    private final String label;

    FrequencyTypeEnum(String label) {
        this.label = label;
    }

    public static FrequencyTypeEnum fromName(String name) {
        for (FrequencyTypeEnum f : values()) {
            if (f.name().equals(name)) {
                return f;
            }
        }
        return DAILY;
    }
}
