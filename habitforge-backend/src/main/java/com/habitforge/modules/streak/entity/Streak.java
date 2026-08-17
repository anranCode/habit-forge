package com.habitforge.modules.streak.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("streaks")
public class Streak {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String habitId;

    private Integer currentStreak;

    private Integer longestStreak;

    private LocalDate lastCheckDate;

    private LocalDateTime updatedAt;
}
