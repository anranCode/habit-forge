package com.habitforge.modules.study.dto;

import com.habitforge.modules.study.entity.NoteImage;
import lombok.Builder;
import lombok.Data;

/**
 * 笔记图片信息（访问 URL = /images/{objectKey}, 走既有代理）
 */
@Data
@Builder
public class NoteImageResponse {

    private String id;
    private String objectKey;
    private String originalName;
    private String contentType;
    private Integer fileSize;
    private Integer width;
    private Integer height;
    private Integer sortOrder;

    public static NoteImageResponse from(NoteImage image) {
        return NoteImageResponse.builder()
                .id(image.getId())
                .objectKey(image.getObjectKey())
                .originalName(image.getOriginalName())
                .contentType(image.getContentType())
                .fileSize(image.getFileSize())
                .width(image.getWidth())
                .height(image.getHeight())
                .sortOrder(image.getSortOrder())
                .build();
    }
}
