package com.habitforge.modules.environment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 环境设计（《掌控习惯》第一定律「让它难以发生 / 让它显而易见」的可勾选清单）
 *
 * <p>表在最初的 schema 里就预留了，P2 开始真正使用；本次新增 category 用于把
 * 「手机节制清单」与「习惯环境设计」分开（见 upgrade_2026-12_focus.sql）。
 */
@Data
@TableName("environment_settings")
public class EnvironmentSetting {

    /** 让它显而易见（提示） */
    public static final String TYPE_PROMPT = "PROMPT";
    /** 让它难以发生（阻力） */
    public static final String TYPE_RESISTANCE = "RESISTANCE";
    /** 承诺机制 */
    public static final String TYPE_COMMITMENT = "COMMITMENT";
    public static final Set<String> TYPES = Set.of(TYPE_PROMPT, TYPE_RESISTANCE, TYPE_COMMITMENT);

    /** 手机节制 */
    public static final String CATEGORY_PHONE = "PHONE";
    /** 习惯环境 */
    public static final String CATEGORY_HABIT = "HABIT";
    /** 其他 */
    public static final String CATEGORY_OTHER = "OTHER";
    public static final Set<String> CATEGORIES = Set.of(CATEGORY_PHONE, CATEGORY_HABIT, CATEGORY_OTHER);

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String userId;

    /** PROMPT / RESISTANCE / COMMITMENT */
    private String type;

    /** PHONE / HABIT / OTHER */
    private String category;

    /** 设置描述（如：手机放在客厅充电） */
    private String description;

    /** 关联的习惯（可选） */
    private String targetHabitId;

    /** 是否生效（清单的勾选状态） */
    private Integer isActive;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
