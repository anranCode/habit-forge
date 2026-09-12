package com.habitforge.modules.study.dto;

import com.habitforge.modules.study.entity.Chapter;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 章节响应（平铺返回, 前端按 parentId 组树）
 */
@Data
@Builder
public class ChapterResponse {

    private String id;
    private String subjectId;
    private String parentId;
    private String name;
    private Integer sortOrder;
    /** NOT_STARTED / IN_PROGRESS / DONE */
    private String status;
    private LocalDateTime doneAt;

    public static ChapterResponse from(Chapter chapter) {
        return ChapterResponse.builder()
                .id(chapter.getId())
                .subjectId(chapter.getSubjectId())
                .parentId(chapter.getParentId())
                .name(chapter.getName())
                .sortOrder(chapter.getSortOrder())
                .status(chapter.getStatus())
                .doneAt(chapter.getDoneAt())
                .build();
    }
}
