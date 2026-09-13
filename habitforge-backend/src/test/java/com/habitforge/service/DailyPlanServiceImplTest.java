package com.habitforge.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.habitforge.common.exception.BusinessException;
import com.habitforge.modules.ai.config.AiProperties;
import com.habitforge.modules.ai.dto.BlockCreateRequest;
import com.habitforge.modules.ai.dto.BlockUpdateRequest;
import com.habitforge.modules.ai.dto.FreeSlotSaveRequest;
import com.habitforge.modules.ai.dto.PlanBlockResponse;
import com.habitforge.modules.ai.dto.PlanResponse;
import com.habitforge.modules.ai.entity.DailyPlan;
import com.habitforge.modules.ai.entity.PlanBlock;
import com.habitforge.modules.ai.entity.PlanFreeSlot;
import com.habitforge.modules.ai.entity.PlanGeneration;
import com.habitforge.modules.ai.mapper.DailyPlanMapper;
import com.habitforge.modules.ai.mapper.PlanBlockMapper;
import com.habitforge.modules.ai.mapper.PlanFreeSlotMapper;
import com.habitforge.modules.ai.mapper.PlanGenerationMapper;
import com.habitforge.modules.ai.service.impl.DailyPlanServiceImpl;
import com.habitforge.modules.checkin.entity.Checkin;
import com.habitforge.modules.checkin.service.CheckinService;
import com.habitforge.modules.habit.dto.HabitResponseDTO;
import com.habitforge.modules.habit.service.HabitService;
import com.habitforge.modules.study.dto.OverviewResponse;
import com.habitforge.modules.study.dto.SubjectResponse;
import com.habitforge.modules.study.service.StudyOverviewService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 计划域：状态机逐转移 / 空闲时段校验 / 重新生成只删 PROPOSED / 完成打卡幂等 / 用量
 */
@ExtendWith(MockitoExtension.class)
class DailyPlanServiceImplTest {

    private static final String USER = "user-a";
    private static final String PLAN_ID = "plan-1";
    private static final String BLOCK_ID = "block-1";
    private static final LocalDate TODAY = LocalDate.now();

    @Mock
    private DailyPlanMapper planMapper;
    @Mock
    private PlanBlockMapper blockMapper;
    @Mock
    private PlanFreeSlotMapper freeSlotMapper;
    @Mock
    private PlanGenerationMapper generationMapper;
    @Mock
    private HabitService habitService;
    @Mock
    private CheckinService checkinService;
    @Mock
    private StudyOverviewService studyOverviewService;
    /** 联动打卡走 REQUIRES_NEW: mock 的 getTransaction 返回 null, TransactionTemplate 直接执行回调 */
    @Mock
    private PlatformTransactionManager transactionManager;

    private AiProperties props;
    private DailyPlanServiceImpl service;

    /** 纯 Mockito 环境下初始化 TableInfo, Lambda wrapper 渲染列名需要 */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), DailyPlan.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), PlanBlock.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), PlanFreeSlot.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), PlanGeneration.class);
    }

    @BeforeEach
    void setUp() {
        props = new AiProperties();
        service = new DailyPlanServiceImpl(planMapper, blockMapper, freeSlotMapper, generationMapper,
                habitService, checkinService, studyOverviewService, props, transactionManager);
        // 响应富化公共桩（各用例按需覆盖）
        lenient().when(habitService.listMine(eq(USER), anyBoolean())).thenReturn(
                List.of(HabitResponseDTO.builder().id("h1").name("晨跑").build()));
        lenient().when(studyOverviewService.overview(USER)).thenReturn(OverviewResponse.builder()
                .subjects(List.of(SubjectResponse.builder().id("s1").name("高等数学").build())).build());
        lenient().when(checkinService.getByHabitAndDate(anyString(), any())).thenReturn(null);
    }

    // ================= 夹具 =================

    private DailyPlan plan() {
        DailyPlan p = new DailyPlan();
        p.setId(PLAN_ID);
        p.setUserId(USER);
        p.setPlanDate(TODAY);
        p.setGenCount(1);
        return p;
    }

    private DailyPlan plan(LocalDate date) {
        DailyPlan p = plan();
        p.setPlanDate(date);
        return p;
    }

    private PlanBlock block(String status) {
        PlanBlock b = new PlanBlock();
        b.setId(BLOCK_ID);
        b.setPlanId(PLAN_ID);
        b.setBlockType("HABIT");
        b.setTitle("晨跑");
        b.setStartTime(LocalTime.parse("09:00"));
        b.setEndTime(LocalTime.parse("10:00"));
        b.setSortOrder(540);
        b.setStatus(status);
        b.setSource("AI");
        b.setHabitId("h1");
        return b;
    }

    private PlanBlock stubOwnedBlock(String status) {
        PlanBlock b = block(status);
        when(blockMapper.selectById(BLOCK_ID)).thenReturn(b);
        when(planMapper.selectById(PLAN_ID)).thenReturn(plan());
        return b;
    }

    private FreeSlotSaveRequest slotsOf(String... hhmmPairs) {
        FreeSlotSaveRequest req = new FreeSlotSaveRequest();
        req.setDate(TODAY);
        req.setSlots(java.util.Arrays.stream(hhmmPairs).map(pair -> {
            String[] ps = pair.split("-");
            FreeSlotSaveRequest.SlotItem item = new FreeSlotSaveRequest.SlotItem();
            item.setStartTime(ps[0]);
            item.setEndTime(ps[1]);
            item.setLabel("测试");
            return item;
        }).toList());
        return req;
    }

    private int codeOf(Runnable r) {
        BusinessException e = assertThrows(BusinessException.class, r::run);
        return e.getCode();
    }

    // ================= 状态机 =================

    @Test
    void complete_fromProposed_setsDoneWithCheckin() {
        stubOwnedBlock("PROPOSED");
        PlanBlockResponse resp = service.completeBlock(USER, BLOCK_ID, true);
        assertEquals("DONE", resp.getStatus());
        assertNotNull(resp.getCompletedAt());
        ArgumentCaptor<PlanBlock> captor = ArgumentCaptor.forClass(PlanBlock.class);
        verify(blockMapper).updateById(captor.capture());
        assertEquals("DONE", captor.getValue().getStatus());
        assertNotNull(captor.getValue().getCompletedAt());
        // 联动打卡走完整 CheckinService, 日期 = 计划日期
        ArgumentCaptor<com.habitforge.modules.checkin.dto.CheckinRequest> checkinCaptor =
                ArgumentCaptor.forClass(com.habitforge.modules.checkin.dto.CheckinRequest.class);
        verify(checkinService).checkin(eq(USER), checkinCaptor.capture());
        assertEquals("h1", checkinCaptor.getValue().getHabitId());
        assertEquals(TODAY, checkinCaptor.getValue().getCheckDate());
    }

    /** 当天已打卡: 预检命中, 不再调用 checkin, 块幂等完成 */
    @Test
    void complete_alreadyCheckedToday_skipsCheckinAndSucceeds() {
        stubOwnedBlock("ADOPTED");
        when(checkinService.isCheckedOn("h1", TODAY)).thenReturn(true);
        PlanBlockResponse resp = service.completeBlock(USER, BLOCK_ID, true);
        assertEquals("DONE", resp.getStatus());
        assertNotNull(resp.getCompletedAt());
        verify(checkinService, never()).checkin(anyString(), any());
    }

    /** 未来日期的计划块: 打卡会命中 3003, 故跳过打卡仅置 DONE(块仍可完成) */
    @Test
    void complete_futurePlanDate_skipsCheckinAndStillDone() {
        PlanBlock b = block("ADOPTED");
        when(blockMapper.selectById(BLOCK_ID)).thenReturn(b);
        when(planMapper.selectById(PLAN_ID)).thenReturn(plan(TODAY.plusDays(1)));

        PlanBlockResponse resp = service.completeBlock(USER, BLOCK_ID, true);

        assertEquals("DONE", resp.getStatus());
        assertNotNull(resp.getCompletedAt());
        verify(checkinService, never()).isCheckedOn(anyString(), any());
        verify(checkinService, never()).checkin(anyString(), any());
    }

    /** B10: ADOPTED 不勾打卡直接完成 → DONE(不打已打卡标记) */
    @Test
    void complete_fromAdoptedWithoutCheckinFlag_setsDone() {
        stubOwnedBlock("ADOPTED");
        PlanBlockResponse resp = service.completeBlock(USER, BLOCK_ID, false);
        assertEquals("DONE", resp.getStatus());
        assertNotNull(resp.getCompletedAt());
        verify(checkinService, never()).checkin(anyString(), any());
    }

    /** 预检后竞态: checkin 抛 CHECKIN_DUPLICATE 仍幂等成功, 块照常 DONE */
    @Test
    void complete_duplicateCheckin_isIdempotentSuccess() {
        stubOwnedBlock("ADOPTED");
        when(checkinService.checkin(eq(USER), any())).thenThrow(
                new BusinessException(com.habitforge.common.exception.ErrorCode.CHECKIN_DUPLICATE));
        PlanBlockResponse resp = service.completeBlock(USER, BLOCK_ID, true);
        assertEquals("DONE", resp.getStatus()); // 重复打卡不阻塞完成
    }

    @Test
    void complete_otherCheckinError_propagates() {
        stubOwnedBlock("ADOPTED");
        when(checkinService.checkin(eq(USER), any())).thenThrow(
                new BusinessException(com.habitforge.common.exception.ErrorCode.HABIT_ARCHIVED));
        assertEquals(2002, codeOf(() -> service.completeBlock(USER, BLOCK_ID, true)));
    }

    @Test
    void complete_fromDoneOrSkipped_throws6013() {
        stubOwnedBlock("DONE");
        assertEquals(6013, codeOf(() -> service.completeBlock(USER, BLOCK_ID, false)));
        org.mockito.Mockito.reset(blockMapper, planMapper);
        stubOwnedBlock("SKIPPED");
        assertEquals(6013, codeOf(() -> service.completeBlock(USER, BLOCK_ID, false)));
        verify(blockMapper, never()).updateById(any(PlanBlock.class));
    }

    @Test
    void complete_withoutFlag_neverCallsCheckin() {
        stubOwnedBlock("PROPOSED");
        service.completeBlock(USER, BLOCK_ID, false);
        verify(checkinService, never()).checkin(anyString(), any());
    }

    @Test
    void skip_fromProposedOrAdopted_setsSkipped() {
        stubOwnedBlock("PROPOSED");
        assertEquals("SKIPPED", service.skipBlock(USER, BLOCK_ID).getStatus());
        verify(blockMapper).updateById(any(PlanBlock.class));
    }

    /** B10: ADOPTED → skip → SKIPPED */
    @Test
    void skip_fromAdopted_setsSkipped() {
        stubOwnedBlock("ADOPTED");
        assertEquals("SKIPPED", service.skipBlock(USER, BLOCK_ID).getStatus());
        verify(blockMapper).updateById(any(PlanBlock.class));
    }

    @Test
    void skip_fromDone_throws6013() {
        stubOwnedBlock("DONE");
        assertEquals(6013, codeOf(() -> service.skipBlock(USER, BLOCK_ID)));
    }

    @Test
    void reopen_fromDone_adoptsAndClearsCompletedAt() {
        stubOwnedBlock("DONE");
        when(blockMapper.update(isNull(), any())).thenReturn(1);
        PlanBlockResponse resp = service.reopenBlock(USER, BLOCK_ID);
        assertEquals("ADOPTED", resp.getStatus());
        assertNull(resp.getCompletedAt());
        // 清 null 字段必须走 UpdateWrapper(updateById 的 NOT_NULL 策略写不进 null); SET 子句在 getSqlSet()
        ArgumentCaptor<Wrapper<PlanBlock>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(blockMapper).update(isNull(), wrapperCaptor.capture());
        var updateWrapper = (com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<PlanBlock>)
                wrapperCaptor.getValue();
        assertTrue(updateWrapper.getSqlSet().contains("completed_at"), updateWrapper.getSqlSet());
        assertTrue(updateWrapper.getSqlSegment().contains("id"), updateWrapper.getSqlSegment());
    }

    @Test
    void reopen_fromAdopted_throws6013() {
        stubOwnedBlock("ADOPTED");
        assertEquals(6013, codeOf(() -> service.reopenBlock(USER, BLOCK_ID)));
    }

    /** 前端对 SKIPPED 渲染「恢复」按钮, reopen 必须接受 SKIPPED → ADOPTED */
    @Test
    void reopen_fromSkipped_adoptsAndClearsCompletedAt() {
        stubOwnedBlock("SKIPPED");
        when(blockMapper.update(isNull(), any())).thenReturn(1);

        PlanBlockResponse resp = service.reopenBlock(USER, BLOCK_ID);

        assertEquals("ADOPTED", resp.getStatus());
        assertNull(resp.getCompletedAt());
        verify(blockMapper).update(isNull(), any());
    }

    @Test
    void blockOfOtherUser_throws6012() {
        PlanBlock b = block("ADOPTED");
        when(blockMapper.selectById(BLOCK_ID)).thenReturn(b);
        DailyPlan foreign = plan();
        foreign.setUserId("other-user");
        when(planMapper.selectById(PLAN_ID)).thenReturn(foreign);
        assertEquals(6012, codeOf(() -> service.skipBlock(USER, BLOCK_ID)));
    }

    // ================= 空闲时段 =================

    @Test
    void saveFreeSlots_valid_overwritesWithSortOrder() {
        when(planMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(plan());
        service.saveFreeSlots(USER, TODAY, slotsOf("10:00-11:00", "08:00-09:00"));
        verify(freeSlotMapper).delete(any());
        ArgumentCaptor<PlanFreeSlot> captor = ArgumentCaptor.forClass(PlanFreeSlot.class);
        verify(freeSlotMapper, times(2)).insert(captor.capture());
        // 按时间排序落库, sortOrder 重编
        assertEquals(LocalTime.parse("08:00"), captor.getAllValues().get(0).getStartTime());
        assertEquals(0, captor.getAllValues().get(0).getSortOrder());
        assertEquals(LocalTime.parse("10:00"), captor.getAllValues().get(1).getStartTime());
        assertEquals(1, captor.getAllValues().get(1).getSortOrder());
    }

    @Test
    void saveFreeSlots_overlap_throws6014() {
        assertEquals(6014, codeOf(() -> service.saveFreeSlots(USER, TODAY, slotsOf("09:00-10:30", "10:00-11:00"))));
    }

    @Test
    void saveFreeSlots_touchingEndpoints_allowed() {
        when(planMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(plan());
        service.saveFreeSlots(USER, TODAY, slotsOf("09:00-10:00", "10:00-11:00"));
        verify(freeSlotMapper, times(2)).insert(any(PlanFreeSlot.class));
    }

    @Test
    void saveFreeSlots_startNotBeforeEnd_throws6014() {
        assertEquals(6014, codeOf(() -> service.saveFreeSlots(USER, TODAY, slotsOf("10:00-10:00"))));
        assertEquals(6014, codeOf(() -> service.saveFreeSlots(USER, TODAY, slotsOf("11:00-10:00"))));
    }

    @Test
    void saveFreeSlots_moreThanEight_throws6014() {
        assertEquals(6014, codeOf(() -> service.saveFreeSlots(USER, TODAY, slotsOf(
                "01:00-01:30", "02:00-02:30", "03:00-03:30", "04:00-04:30",
                "05:00-05:30", "06:00-06:30", "07:00-07:30", "08:00-08:30", "09:00-09:30"))));
    }

    /** {"slots":[null]} → 6014(此前会 NPE 落 500) */
    @Test
    void saveFreeSlots_nullElement_throws6014() {
        FreeSlotSaveRequest req = new FreeSlotSaveRequest();
        req.setDate(TODAY);
        req.setSlots(java.util.Collections.singletonList(null));
        assertEquals(6014, codeOf(() -> service.saveFreeSlots(USER, TODAY, req)));
        verify(freeSlotMapper, never()).insert(any(PlanFreeSlot.class));
    }

    // ================= 采纳 / 手动块 / 重新生成 =================

    @Test
    void adoptAll_flipsOnlyProposed_returnsCount() {
        when(planMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(plan());
        when(blockMapper.update(isNull(), any())).thenReturn(4);
        assertEquals(4, service.adoptAll(USER, TODAY));
    }

    @Test
    void adoptAll_noPlan_throws6011() {
        when(planMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        assertEquals(6011, codeOf(() -> service.adoptAll(USER, TODAY)));
    }

    @Test
    void createBlock_manual_isAdoptedAndSortedByStart() {
        when(planMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(plan());
        BlockCreateRequest req = new BlockCreateRequest();
        req.setDate(TODAY);
        req.setBlockType("STUDY");
        req.setTitle("晚上复习");
        req.setStartTime("19:30");
        req.setEndTime("20:30");
        req.setSubjectId("s1");
        PlanBlockResponse resp = service.createBlock(USER, req);
        assertEquals("MANUAL", resp.getSource());
        assertEquals("ADOPTED", resp.getStatus());
        assertEquals(19 * 60 + 30, resp.getSortOrder());
        assertEquals("高等数学", resp.getSubjectName());
    }

    @Test
    void createBlock_invalidTypeOrTime_throwsBadRequest() {
        BlockCreateRequest req = new BlockCreateRequest();
        req.setDate(TODAY);
        req.setBlockType("SWIM");
        req.setTitle("x");
        req.setStartTime("09:00");
        req.setEndTime("10:00");
        assertEquals(400, codeOf(() -> service.createBlock(USER, req)));

        req.setBlockType("HABIT");
        req.setStartTime("10:00");
        req.setEndTime("09:00");
        assertEquals(400, codeOf(() -> service.createBlock(USER, req)));
    }

    @Test
    void updateBlock_partialFields_keepsRestAndResorts() {
        stubOwnedBlock("ADOPTED");
        BlockUpdateRequest req = new BlockUpdateRequest();
        req.setTitle("改个名");
        req.setHabitId(""); // 空串=清除关联
        PlanBlockResponse resp = service.updateBlock(USER, BLOCK_ID, req);
        assertEquals("改个名", resp.getTitle());
        assertEquals("09:00", resp.getStartTime());
        assertNull(resp.getHabitId());
        assertEquals(540, resp.getSortOrder());
    }

    @Test
    void replaceProposedWithGenerated_deletesOnlyProposedAndBumpsCount() {
        DailyPlan p = plan();
        PlanBlock b1 = new PlanBlock();
        b1.setStartTime(LocalTime.parse("08:00"));
        b1.setStatus("PROPOSED");
        PlanBlock b2 = new PlanBlock();
        b2.setStartTime(LocalTime.parse("09:00"));
        b2.setStatus("PROPOSED");
        when(blockMapper.delete(any())).thenReturn(3);

        service.replaceProposedWithGenerated(p, List.of(b1, b2), "deepseek-v4-flash");

        ArgumentCaptor<Wrapper<PlanBlock>> delCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(blockMapper).delete(delCaptor.capture());
        String sql = delCaptor.getValue().getSqlSegment();
        assertTrue(sql.contains("plan_id"), sql);
        assertTrue(sql.contains("status"), sql); // 只删 PROPOSED
        assertEquals(PLAN_ID, b1.getPlanId());
        verify(blockMapper, times(2)).insert(any(PlanBlock.class));
        ArgumentCaptor<DailyPlan> planCaptor = ArgumentCaptor.forClass(DailyPlan.class);
        verify(planMapper).updateById(planCaptor.capture());
        assertEquals(2, planCaptor.getValue().getGenCount());
        assertEquals("deepseek-v4-flash", planCaptor.getValue().getLastModel());
    }

    // ================= 查询富化 / 用量 =================

    @Test
    void getPlanResponse_enrichesNamesAndCheckedToday() {
        when(planMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(plan());
        when(blockMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(block("PROPOSED")));
        when(freeSlotMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(checkinService.getByHabitAndDate("h1", TODAY)).thenReturn(new Checkin());

        PlanResponse resp = service.getPlanResponse(USER, TODAY);
        assertNotNull(resp);
        assertEquals("晨跑", resp.getBlocks().get(0).getHabitName());
        assertTrue(resp.getBlocks().get(0).getHabitCheckedToday());
    }

    @Test
    void getPlanResponse_missing_returnsNull() {
        when(planMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        assertNull(service.getPlanResponse(USER, TODAY));
    }

    @Test
    void getUsage_sumsTodayTokensAndComputesRemaining() {
        when(planMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(plan()); // genCount=1
        PlanGeneration g1 = new PlanGeneration();
        g1.setTotalTokens(100);
        PlanGeneration g2 = new PlanGeneration();
        g2.setTotalTokens(200);
        when(generationMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(g1, g2));

        var usage = service.getUsage(USER);
        assertEquals(1, usage.getUsed());
        assertEquals(4, usage.getRemaining());
        assertEquals(300L, usage.getTodayTokens());
    }

    @Test
    void getUsage_withoutPlan_zeroUsedFullRemaining() {
        when(planMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(generationMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        var usage = service.getUsage(USER);
        assertEquals(0, usage.getUsed());
        assertEquals(props.getDailyGenerateLimit(), usage.getRemaining());
        assertEquals(0L, usage.getTodayTokens());
    }
}
