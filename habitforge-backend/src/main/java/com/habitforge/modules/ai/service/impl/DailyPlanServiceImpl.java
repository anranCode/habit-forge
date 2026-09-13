package com.habitforge.modules.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.habitforge.common.constant.AppConstant;
import com.habitforge.common.exception.BusinessException;
import com.habitforge.common.exception.ErrorCode;
import com.habitforge.modules.ai.config.AiProperties;
import com.habitforge.modules.ai.dto.BlockCreateRequest;
import com.habitforge.modules.ai.dto.BlockUpdateRequest;
import com.habitforge.modules.ai.dto.FreeSlotSaveRequest;
import com.habitforge.modules.ai.dto.PlanBlockResponse;
import com.habitforge.modules.ai.dto.PlanFreeSlotResponse;
import com.habitforge.modules.ai.dto.PlanResponse;
import com.habitforge.modules.ai.dto.PlanUsageResponse;
import com.habitforge.modules.ai.entity.DailyPlan;
import com.habitforge.modules.ai.entity.PlanBlock;
import com.habitforge.modules.ai.entity.PlanFreeSlot;
import com.habitforge.modules.ai.entity.PlanGeneration;
import com.habitforge.modules.ai.mapper.DailyPlanMapper;
import com.habitforge.modules.ai.mapper.PlanBlockMapper;
import com.habitforge.modules.ai.mapper.PlanFreeSlotMapper;
import com.habitforge.modules.ai.mapper.PlanGenerationMapper;
import com.habitforge.modules.ai.service.DailyPlanService;
import com.habitforge.modules.checkin.dto.CheckinRequest;
import com.habitforge.modules.checkin.service.CheckinService;
import com.habitforge.modules.habit.dto.HabitResponseDTO;
import com.habitforge.modules.habit.service.HabitService;
import com.habitforge.modules.study.dto.OverviewResponse;
import com.habitforge.modules.study.service.StudyOverviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailyPlanServiceImpl implements DailyPlanService {

    static final String STATUS_PROPOSED = "PROPOSED";
    static final String STATUS_ADOPTED = "ADOPTED";
    static final String STATUS_DONE = "DONE";
    static final String STATUS_SKIPPED = "SKIPPED";

    private static final Set<String> BLOCK_TYPES = Set.of("HABIT", "STUDY", "REST", "OTHER");

    private final DailyPlanMapper planMapper;
    private final PlanBlockMapper blockMapper;
    private final PlanFreeSlotMapper freeSlotMapper;
    private final PlanGenerationMapper generationMapper;
    private final HabitService habitService;
    private final CheckinService checkinService;
    private final StudyOverviewService studyOverviewService;
    private final AiProperties aiProperties;
    private final PlatformTransactionManager transactionManager;

    // ================= 计划行（懒创建） =================

    @Override
    public DailyPlan findPlan(String userId, LocalDate date) {
        return planMapper.selectOne(new LambdaQueryWrapper<DailyPlan>()
                .eq(DailyPlan::getUserId, userId)
                .eq(DailyPlan::getPlanDate, date)
                .last("LIMIT 1"));
    }

    @Override
    public DailyPlan getOrCreatePlan(String userId, LocalDate date) {
        DailyPlan existing = findPlan(userId, date);
        if (existing != null) {
            return existing;
        }
        DailyPlan plan = new DailyPlan();
        plan.setUserId(userId);
        plan.setPlanDate(date);
        plan.setGenCount(0);
        try {
            planMapper.insert(plan);
            return plan;
        } catch (DuplicateKeyException e) {
            // 并发下另一请求已建行（uk_user_date）
            return findPlan(userId, date);
        }
    }

    @Override
    public PlanResponse getPlanResponse(String userId, LocalDate date) {
        DailyPlan plan = findPlan(userId, date);
        return plan == null ? null : buildFullResponse(userId, plan);
    }

    // ================= 空闲时段 =================

    /** 校验后的时段（服务内中间结构） */
    private record ParsedSlot(LocalTime start, LocalTime end, String label) {
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<PlanFreeSlotResponse> saveFreeSlots(String userId, LocalDate date, FreeSlotSaveRequest request) {
        List<FreeSlotSaveRequest.SlotItem> items =
                request.getSlots() == null ? List.of() : request.getSlots();
        if (items.size() > AppConstant.PLAN_FREE_SLOT_MAX) {
            throw new BusinessException(ErrorCode.FREE_SLOT_INVALID, "空闲时段最多 " + AppConstant.PLAN_FREE_SLOT_MAX + " 条");
        }
        List<ParsedSlot> parsed = new ArrayList<>();
        for (FreeSlotSaveRequest.SlotItem item : items) {
            // 元素级兜底: {"slots":[null]} 之类的空元素直接 6014, 不落到 NPE(500)
            if (item == null) {
                throw new BusinessException(ErrorCode.FREE_SLOT_INVALID, "空闲时段不能为空");
            }
            LocalTime start = parseTime(item.getStartTime());
            LocalTime end = parseTime(item.getEndTime());
            if (start == null || end == null || !start.isBefore(end)) {
                throw new BusinessException(ErrorCode.FREE_SLOT_INVALID);
            }
            parsed.add(new ParsedSlot(start, end, item.getLabel()));
        }
        parsed.sort(Comparator.comparing(ParsedSlot::start).thenComparing(ParsedSlot::end));
        for (int i = 1; i < parsed.size(); i++) {
            if (parsed.get(i - 1).end().isAfter(parsed.get(i).start())) {
                throw new BusinessException(ErrorCode.FREE_SLOT_INVALID, "空闲时段之间不得重叠");
            }
        }

        DailyPlan plan = getOrCreatePlan(userId, date);
        freeSlotMapper.delete(new LambdaQueryWrapper<PlanFreeSlot>().eq(PlanFreeSlot::getPlanId, plan.getId()));
        List<PlanFreeSlot> saved = new ArrayList<>();
        for (int i = 0; i < parsed.size(); i++) {
            ParsedSlot p = parsed.get(i);
            PlanFreeSlot slot = new PlanFreeSlot();
            slot.setPlanId(plan.getId());
            slot.setStartTime(p.start());
            slot.setEndTime(p.end());
            slot.setLabel(p.label());
            slot.setSortOrder(i);
            freeSlotMapper.insert(slot);
            saved.add(slot);
        }
        log.info("保存空闲时段 user={}, date={}, count={}", userId, date, saved.size());
        return saved.stream().map(PlanFreeSlotResponse::from).toList();
    }

    // ================= 采纳 / 块 CRUD =================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int adoptAll(String userId, LocalDate date) {
        DailyPlan plan = findPlan(userId, date);
        if (plan == null) {
            throw new BusinessException(ErrorCode.PLAN_NOT_FOUND);
        }
        int rows = blockMapper.update(null, new LambdaUpdateWrapper<PlanBlock>()
                .eq(PlanBlock::getPlanId, plan.getId())
                .eq(PlanBlock::getStatus, STATUS_PROPOSED)
                .set(PlanBlock::getStatus, STATUS_ADOPTED));
        log.info("一键采纳 user={}, date={}, adopted={}", userId, date, rows);
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlanBlockResponse createBlock(String userId, BlockCreateRequest request) {
        if (!BLOCK_TYPES.contains(request.getBlockType())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "块类型须为 HABIT/STUDY/REST/OTHER");
        }
        LocalTime start = requireTime(request.getStartTime(), "开始时间");
        LocalTime end = requireTime(request.getEndTime(), "结束时间");
        if (!start.isBefore(end)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "开始时间必须早于结束时间");
        }
        DailyPlan plan = getOrCreatePlan(userId, request.getDate());
        PlanBlock block = new PlanBlock();
        block.setPlanId(plan.getId());
        block.setBlockType(request.getBlockType());
        block.setTitle(request.getTitle().strip());
        block.setStartTime(start);
        block.setEndTime(end);
        block.setSortOrder(start.toSecondOfDay() / 60);
        block.setStatus(STATUS_ADOPTED); // MANUAL 块创建即已采纳
        block.setSource("MANUAL");
        block.setHabitId(blankToNull(request.getHabitId()));
        block.setSubjectId(blankToNull(request.getSubjectId()));
        block.setChapterId(blankToNull(request.getChapterId()));
        blockMapper.insert(block);
        return toResponse(userId, block);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlanBlockResponse updateBlock(String userId, String blockId, BlockUpdateRequest request) {
        PlanBlock block = loadOwnedBlock(userId, blockId);
        if (request.getBlockType() != null) {
            if (!BLOCK_TYPES.contains(request.getBlockType())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "块类型须为 HABIT/STUDY/REST/OTHER");
            }
            block.setBlockType(request.getBlockType());
        }
        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            block.setTitle(request.getTitle().strip());
        }
        if (request.getStartTime() != null) {
            block.setStartTime(requireTime(request.getStartTime(), "开始时间"));
        }
        if (request.getEndTime() != null) {
            block.setEndTime(requireTime(request.getEndTime(), "结束时间"));
        }
        if (!block.getStartTime().isBefore(block.getEndTime())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "开始时间必须早于结束时间");
        }
        if (request.getHabitId() != null) {
            block.setHabitId(blankToNull(request.getHabitId()));
        }
        if (request.getSubjectId() != null) {
            block.setSubjectId(blankToNull(request.getSubjectId()));
        }
        if (request.getChapterId() != null) {
            block.setChapterId(blankToNull(request.getChapterId()));
        }
        block.setSortOrder(block.getStartTime().toSecondOfDay() / 60);
        blockMapper.updateById(block);
        return toResponse(userId, block);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBlock(String userId, String blockId) {
        PlanBlock block = loadOwnedBlock(userId, blockId);
        blockMapper.deleteById(block.getId());
        log.info("删除计划块 user={}, block={}", userId, blockId);
    }

    // ================= 块状态机 =================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlanBlockResponse completeBlock(String userId, String blockId, boolean checkinHabit) {
        PlanBlock block = loadOwnedBlock(userId, blockId);
        // 仅 DONE 再 complete → 6013; SKIPPED 亦不可直接完成
        if (STATUS_DONE.equals(block.getStatus()) || STATUS_SKIPPED.equals(block.getStatus())) {
            throw new BusinessException(ErrorCode.PLAN_BLOCK_STATUS_INVALID);
        }
        // PROPOSED 隐式采纳后完成, ADOPTED 直接完成
        block.setStatus(STATUS_DONE);
        block.setCompletedAt(LocalDateTime.now());
        blockMapper.updateById(block);

        if (checkinHabit && block.getHabitId() != null && !block.getHabitId().isBlank()) {
            DailyPlan plan = planMapper.selectById(block.getPlanId());
            LocalDate checkDate = plan == null ? LocalDate.now() : plan.getPlanDate();
            if (checkDate.isAfter(LocalDate.now())) {
                // 未来日期的计划块: 打卡会命中 CHECKIN_DATE_INVALID(3003) 导致块完成失败
                // → 跳过打卡仅置 DONE, 块自身仍可正常完成
                log.info("计划日期 {} 晚于今天, 跳过联动打卡(仅置 DONE) user={}, block={}", checkDate, userId, blockId);
            } else if (!checkinService.isCheckedOn(block.getHabitId(), checkDate)) {
                // 幂等前置判定: 当天已打卡则直接完成块, 不再调用 checkin
                // (若仍调用, checkin 抛 CHECKIN_DUPLICATE 会把共享物理事务标记 rollback-only, 见 checkinInNewTransaction 注释)
                CheckinRequest checkinRequest = new CheckinRequest();
                checkinRequest.setHabitId(block.getHabitId());
                checkinRequest.setCheckDate(checkDate);
                checkinInNewTransaction(userId, checkinRequest);
            }
        }
        return toResponse(userId, block);
    }

    /**
     * 完成联动打卡：完整复用 CheckinService（频率校验/链重算/积分/成就），绝不绕过。
     * <p>
     * 打卡必须跑在独立事务（REQUIRES_NEW）中：checkin 是 @Transactional 默认 REQUIRED，
     * 当天已有打卡记录时由 DuplicateKeyException 转抛的 BusinessException(CHECKIN_DUPLICATE) 会穿过
     * 内层事务代理，使内外共享的物理事务被标记 rollback-only，随后外层 completeBlock 提交时抛
     * UnexpectedRollbackException（HTTP 500），块置 DONE 也会一并回滚 —— 与「重复打卡幂等成功」相反。
     * 独立事务下内层回滚只影响自身，重复打卡可安全吞掉视为幂等成功。
     */
    private void checkinInNewTransaction(String userId, CheckinRequest request) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            template.execute(status -> checkinService.checkin(userId, request));
        } catch (BusinessException e) {
            if (e.getCode() != ErrorCode.CHECKIN_DUPLICATE.getCode()) {
                throw e;
            }
            // 预检与调用之间的竞态: 他人已抢先打卡, 幂等成功, 块照常置 DONE
            log.info("块完成联动打卡时当天已有打卡记录, 幂等成功 user={}, habit={}", userId, request.getHabitId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlanBlockResponse skipBlock(String userId, String blockId) {
        PlanBlock block = loadOwnedBlock(userId, blockId);
        if (!STATUS_PROPOSED.equals(block.getStatus()) && !STATUS_ADOPTED.equals(block.getStatus())) {
            throw new BusinessException(ErrorCode.PLAN_BLOCK_STATUS_INVALID);
        }
        block.setStatus(STATUS_SKIPPED);
        blockMapper.updateById(block);
        return toResponse(userId, block);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlanBlockResponse reopenBlock(String userId, String blockId) {
        PlanBlock block = loadOwnedBlock(userId, blockId);
        // 前端对 DONE(撤销) 与 SKIPPED(恢复) 都渲染 reopen 按钮, 两者均回退到 ADOPTED
        if (!STATUS_DONE.equals(block.getStatus()) && !STATUS_SKIPPED.equals(block.getStatus())) {
            throw new BusinessException(ErrorCode.PLAN_BLOCK_STATUS_INVALID);
        }
        // updateById 的 NOT_NULL 字段策略不会写 null, 清 completed_at 必须走 UpdateWrapper
        blockMapper.update(null, new LambdaUpdateWrapper<PlanBlock>()
                .eq(PlanBlock::getId, block.getId())
                .set(PlanBlock::getStatus, STATUS_ADOPTED)
                .set(PlanBlock::getCompletedAt, null));
        block.setStatus(STATUS_ADOPTED);
        block.setCompletedAt(null);
        return toResponse(userId, block);
    }

    // ================= 用量 =================

    @Override
    public PlanUsageResponse getUsage(String userId) {
        LocalDate today = LocalDate.now();
        DailyPlan plan = findPlan(userId, today);
        int used = plan == null || plan.getGenCount() == null ? 0 : plan.getGenCount();
        int remaining = Math.max(0, aiProperties.getDailyGenerateLimit() - used);
        List<PlanGeneration> todayGens = generationMapper.selectList(new LambdaQueryWrapper<PlanGeneration>()
                .eq(PlanGeneration::getUserId, userId)
                .ge(PlanGeneration::getCreatedAt, today.atStartOfDay())
                .lt(PlanGeneration::getCreatedAt, today.plusDays(1).atStartOfDay()));
        long tokens = todayGens.stream()
                .mapToLong(g -> g.getTotalTokens() == null ? 0L : g.getTotalTokens())
                .sum();
        return PlanUsageResponse.builder()
                .used(used)
                .remaining(remaining)
                .todayTokens(tokens)
                .build();
    }

    // ================= 生成落块 =================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replaceProposedWithGenerated(DailyPlan plan, List<PlanBlock> blocks, String model) {
        // 重新生成只替换未采纳的 PROPOSED, 保护 ADOPTED/DONE/SKIPPED
        blockMapper.delete(new LambdaQueryWrapper<PlanBlock>()
                .eq(PlanBlock::getPlanId, plan.getId())
                .eq(PlanBlock::getStatus, STATUS_PROPOSED));
        for (PlanBlock block : blocks) {
            block.setPlanId(plan.getId());
            blockMapper.insert(block);
        }
        plan.setGenCount(plan.getGenCount() == null ? 1 : plan.getGenCount() + 1);
        plan.setLastModel(model);
        planMapper.updateById(plan);
        log.info("AI 生成落块 plan={}, date={}, blocks={}, model={}", plan.getId(), plan.getPlanDate(), blocks.size(), model);
    }

    // ================= 内部方法 =================

    /** 归属校验：块存在且其计划属于当前用户, 违者 6012 */
    private PlanBlock loadOwnedBlock(String userId, String blockId) {
        PlanBlock block = blockMapper.selectById(blockId);
        if (block == null) {
            throw new BusinessException(ErrorCode.PLAN_BLOCK_NOT_FOUND);
        }
        DailyPlan plan = planMapper.selectById(block.getPlanId());
        if (plan == null || !plan.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.PLAN_BLOCK_NOT_FOUND);
        }
        return block;
    }

    private PlanResponse buildFullResponse(String userId, DailyPlan plan) {
        List<PlanBlock> blocks = blockMapper.selectList(new LambdaQueryWrapper<PlanBlock>()
                .eq(PlanBlock::getPlanId, plan.getId())
                .orderByAsc(PlanBlock::getSortOrder)
                .orderByAsc(PlanBlock::getStartTime));
        List<PlanFreeSlot> slots = freeSlotMapper.selectList(new LambdaQueryWrapper<PlanFreeSlot>()
                .eq(PlanFreeSlot::getPlanId, plan.getId())
                .orderByAsc(PlanFreeSlot::getSortOrder)
                .orderByAsc(PlanFreeSlot::getStartTime));
        Map<String, String> habitNames = habitNameMap(userId);
        Map<String, String> subjectNames = subjectNameMap(userId);
        List<PlanBlockResponse> blockResponses = blocks.stream()
                .map(b -> toResponse(b, plan.getPlanDate(), habitNames, subjectNames))
                .toList();
        return PlanResponse.baseFrom(plan)
                .blocks(blockResponses)
                .freeSlots(slots.stream().map(PlanFreeSlotResponse::from).toList())
                .build();
    }

    /** 单块响应（自取富化上下文） */
    private PlanBlockResponse toResponse(String userId, PlanBlock block) {
        DailyPlan plan = planMapper.selectById(block.getPlanId());
        return toResponse(block, plan == null ? LocalDate.now() : plan.getPlanDate(),
                habitNameMap(userId), subjectNameMap(userId));
    }

    /** 富化：habitName/subjectName 批量查; habitCheckedToday 反向标记（getByHabitAndDate） */
    private PlanBlockResponse toResponse(PlanBlock block, LocalDate planDate,
                                         Map<String, String> habitNames, Map<String, String> subjectNames) {
        boolean checkedToday = block.getHabitId() != null
                && checkinService.getByHabitAndDate(block.getHabitId(), planDate) != null;
        return PlanBlockResponse.baseFrom(block)
                .habitName(block.getHabitId() == null ? null : habitNames.get(block.getHabitId()))
                .subjectName(block.getSubjectId() == null ? null : subjectNames.get(block.getSubjectId()))
                .habitCheckedToday(checkedToday)
                .build();
    }

    private Map<String, String> habitNameMap(String userId) {
        return habitService.listMine(userId, false).stream()
                .filter(h -> h.getId() != null && h.getName() != null)
                .collect(Collectors.toMap(HabitResponseDTO::getId, HabitResponseDTO::getName, (a, b) -> a));
    }

    private Map<String, String> subjectNameMap(String userId) {
        OverviewResponse overview = studyOverviewService.overview(userId);
        if (overview == null || overview.getSubjects() == null) {
            return Map.of();
        }
        return overview.getSubjects().stream()
                .filter(s -> s.getId() != null && s.getName() != null)
                .collect(Collectors.toMap(s -> s.getId(), s -> s.getName(), (a, b) -> a));
    }

    private static LocalTime parseTime(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(text.strip());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static LocalTime requireTime(String text, String label) {
        LocalTime t = parseTime(text);
        if (t == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, label + "格式须为 HH:mm");
        }
        return t;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
