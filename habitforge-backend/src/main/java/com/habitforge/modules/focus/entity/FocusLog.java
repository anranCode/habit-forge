package com.habitforge.modules.focus.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 注意力日志（P2 手机节制: user × day 一天一行）
 *
 * <p>关键语义：{@code entertainmentMinutes} 为 null 表示**当日尚未录入**，不算达标 ——
 * 只有「冲动次数」的日子里没有时长数据，不能白拿达标积分。
 */
@Data
@TableName("focus_logs")
public class FocusLog {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String userId;

    /** 日志日期（不允许未来） */
    private LocalDate logDate;

    /** 娱乐/短视频时长（分钟；null = 尚未录入） */
    private Integer entertainmentMinutes;

    /** 拿起手机次数（可选） */
    private Integer pickups;

    /** 当日冲动次数（想刷的瞬间） */
    private Integer urgeTotal;

    /** 其中忍住没刷的次数 */
    private Integer urgeResisted;

    private String note;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /** 已录入且不超过上限才算「节制达标」 */
    public boolean compliant(Integer limitMinutes) {
        return entertainmentMinutes != null && limitMinutes != null && entertainmentMinutes <= limitMinutes;
    }
}
