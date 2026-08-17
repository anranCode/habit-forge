package com.habitforge.modules.reflection.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 习惯心得响应（附带习惯名/日记日期，供双向跳转展示）
 */
@Data
@Builder
public class ReflectionResponse {

    private String id;
    private String journalId;
    private String habitId;
    private String checkinId;
    private Integer result;
    private Integer feeling;
    private Integer difficulty;
    private String reason;
    private String obstacle;
    private String learning;
    private String adjustment;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 附带信息：习惯侧展示 */
    private String habitName;
    /** 附带信息：日记日期（心得所属日记的日期，时间倒序列表用） */
    private LocalDate journalDate;
    /** 附带信息：日记标题 */
    private String journalTitle;
}
