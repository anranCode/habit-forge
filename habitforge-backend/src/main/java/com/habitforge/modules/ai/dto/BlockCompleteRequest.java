package com.habitforge.modules.ai.dto;

import lombok.Data;

/**
 * 完成块（POST /plans/blocks/{id}/complete; checkinHabit=true 且有 habitId 时联动完整打卡链路）
 */
@Data
public class BlockCompleteRequest {

    /** 是否同时给关联习惯打卡（前端「完成并打卡」默认勾选可取消） */
    private Boolean checkinHabit;
}
