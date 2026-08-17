package com.habitforge.modules.journal.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 日记详情（含关联习惯 + 图片列表）
 */
@Data
@Builder
public class JournalDetailResponse {

    private String id;
    private LocalDate journalDate;
    private String title;
    private Integer mood;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 关联的习惯 */
    private List<HabitBrief> habits;

    /** 图片列表（按 sortOrder 升序） */
    private List<ImageInfo> images;

    @Data
    @Builder
    public static class HabitBrief {
        private String id;
        private String name;
        private String category;
        private String identityTag;
    }

    @Data
    @Builder
    public static class ImageInfo {
        private String id;
        private String objectKey;
        private String originalName;
        private String contentType;
        private Integer fileSize;
        private Integer width;
        private Integer height;
        private Integer sortOrder;
    }
}
