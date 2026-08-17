package com.habitforge.modules.checkin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("checkins")
public class Checkin {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String habitId;

    private LocalDate checkDate;

    private Integer isCompleted;

    private String note;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
