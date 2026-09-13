package com.habitforge.modules.ai.service;

import com.habitforge.modules.ai.dto.BlockCreateRequest;
import com.habitforge.modules.ai.dto.BlockUpdateRequest;
import com.habitforge.modules.ai.dto.FreeSlotSaveRequest;
import com.habitforge.modules.ai.dto.PlanBlockResponse;
import com.habitforge.modules.ai.dto.PlanFreeSlotResponse;
import com.habitforge.modules.ai.dto.PlanResponse;
import com.habitforge.modules.ai.dto.PlanUsageResponse;
import com.habitforge.modules.ai.entity.DailyPlan;
import com.habitforge.modules.ai.entity.PlanBlock;

import java.time.LocalDate;
import java.util.List;

/**
 * 计划域读写（懒创建; 块状态机; 空闲时段整体覆盖; 打卡联动）
 */
public interface DailyPlanService {

    /** 查某日计划（不建行; 不存在返回 null） */
    DailyPlan findPlan(String userId, LocalDate date);

    /** 懒创建：不存在则 insert 一行（存时段/生成/手动加块时调用） */
    DailyPlan getOrCreatePlan(String userId, LocalDate date);

    /** 完整响应（blocks+freeSlots+富化; 未生成返回 null, 供 GET /today） */
    PlanResponse getPlanResponse(String userId, LocalDate date);

    /** 空闲时段整体覆盖保存（事务内先删后插; 违者 6014） */
    List<PlanFreeSlotResponse> saveFreeSlots(String userId, LocalDate date, FreeSlotSaveRequest request);

    /** 一键采纳：全部 PROPOSED→ADOPTED, 返回条数（计划不存在 6011） */
    int adoptAll(String userId, LocalDate date);

    /** 手动加块（MANUAL 创建即 ADOPTED） */
    PlanBlockResponse createBlock(String userId, BlockCreateRequest request);

    /** 编辑块（null 字段不改） */
    PlanBlockResponse updateBlock(String userId, String blockId, BlockUpdateRequest request);

    void deleteBlock(String userId, String blockId);

    /** 完成块：PROPOSED/ADOPTED→DONE（PROPOSED 隐式采纳）; checkinHabit 时走完整 CheckinService, 重复打卡幂等 */
    PlanBlockResponse completeBlock(String userId, String blockId, boolean checkinHabit);

    /** 跳过：PROPOSED/ADOPTED→SKIPPED */
    PlanBlockResponse skipBlock(String userId, String blockId);

    /** 重开：DONE→ADOPTED（清 completedAt） */
    PlanBlockResponse reopenBlock(String userId, String blockId);

    /** 今日用量 {used, remaining, todayTokens} */
    PlanUsageResponse getUsage(String userId);

    /** 生成成功后落块：只删 PROPOSED 旧块再插新块; gen_count+1、last_model 更新 */
    void replaceProposedWithGenerated(DailyPlan plan, List<PlanBlock> blocks, String model);
}
