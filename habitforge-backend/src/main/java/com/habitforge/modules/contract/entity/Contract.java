package com.habitforge.modules.contract.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 习惯契约（《掌控习惯》第四定律「让它令人满足」的问责机制）
 *
 * <p>表在最初的 schema 里就预留了，P2 开始使用。单用户场景下问责伙伴只记姓名，不建账号。
 */
@Data
@TableName("contracts")
public class Contract {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_BROKEN = "BROKEN";
    public static final Set<String> STATUSES = Set.of(STATUS_ACTIVE, STATUS_COMPLETED, STATUS_BROKEN);

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String userId;

    /** 关联习惯（NOT NULL: 契约必须挂在一个具体习惯上） */
    private String habitId;

    /** 问责伙伴姓名（手填） */
    private String partnerName;

    /** 违约代价描述 */
    private String penalty;

    private Integer isPublic;

    /** ACTIVE / COMPLETED / BROKEN */
    private String status;

    private LocalDateTime signedAt;

    private LocalDateTime updatedAt;
}
