package com.habitforge.modules.journal.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

/**
 * 日记摘要（列表用）
 */
@Data
@Builder
public class JournalSummaryResponse {

    private String id;
    private LocalDate journalDate;
    private String title;
    private Integer mood;
    /** 是否有正文 */
    private Boolean hasContent;
    /** 图片数量 */
    private Integer imageCount;
    /** 关联习惯数量 */
    private Integer habitCount;
}
