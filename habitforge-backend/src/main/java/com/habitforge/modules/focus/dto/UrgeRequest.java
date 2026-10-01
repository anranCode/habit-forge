package com.habitforge.modules.focus.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 记录一次「想刷手机」的冲动（只能记今天 —— 冲动是即时事件）
 */
@Data
public class UrgeRequest {

    /** true = 忍住了没刷；false = 没忍住 */
    @NotNull(message = "请指明是否忍住了")
    private Boolean resisted;

    /** 一次记多笔（默认 1，上限 20） */
    private Integer count;
}
