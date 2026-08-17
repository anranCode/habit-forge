package com.habitforge.modules.achievement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("achievements")
public class Achievement {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String userId;

    private String name;

    private String icon;

    private String description;

    private LocalDateTime achievedAt;
}
