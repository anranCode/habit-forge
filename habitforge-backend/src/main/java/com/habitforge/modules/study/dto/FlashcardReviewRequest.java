package com.habitforge.modules.study.dto;

import lombok.Data;

/**
 * 复习评分请求（1忘记/2模糊/3记得/4轻松; 白名单在 service 校验 → 7005）
 */
@Data
public class FlashcardReviewRequest {

    private Integer rating;
}
