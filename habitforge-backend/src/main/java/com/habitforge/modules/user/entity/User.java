package com.habitforge.modules.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("users")
public class User {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String username;

    private String email;

    private String passwordHash;

    /** 身份设定: 我想成为... */
    private String identityGoal;

    private String avatarUrl;

    private Integer points;

    private Integer level;

    private Integer isActive;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime lastLoginAt;
}
