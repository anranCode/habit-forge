package com.habitforge.modules.journal.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 创建日记请求
 */
@Data
public class JournalCreateRequest {

    /** 日记日期，默认今天；允许补写过去，拒绝未来 */
    private LocalDate journalDate;

    @Size(max = 200, message = "标题最长 200 字")
    private String title;

    /** 心情: 1好 2一般 3疲惫（可不填） */
    @Min(value = 1, message = "心情取值 1-3")
    @Max(value = 3, message = "心情取值 1-3")
    private Integer mood;

    @Size(max = 10000, message = "正文最长 10000 字")
    private String content;

    /** 关联习惯ID列表；null = 自动关联当日已打卡习惯，空列表 = 不关联 */
    private List<String> habitIds;
}
