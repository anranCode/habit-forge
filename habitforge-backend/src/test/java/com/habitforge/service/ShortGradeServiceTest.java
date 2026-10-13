package com.habitforge.service;

import com.habitforge.common.exception.BusinessException;
import com.habitforge.common.exception.ErrorCode;
import com.habitforge.common.util.RedisUtil;
import com.habitforge.modules.ai.client.PlanAiClient;
import com.habitforge.modules.ai.client.ShortGradeContextAssembler;
import com.habitforge.modules.ai.client.ShortGradePromptTemplate;
import com.habitforge.modules.ai.config.AiProperties;
import com.habitforge.modules.ai.dto.ShortGradeOutcome;
import com.habitforge.modules.ai.entity.PlanGeneration;
import com.habitforge.modules.ai.mapper.PlanGenerationMapper;
import com.habitforge.modules.ai.service.PlanGenerationWriter;
import com.habitforge.modules.ai.service.ShortGradeService;
import com.habitforge.modules.ai.service.impl.ShortGradeServiceImpl;
import com.habitforge.modules.study.entity.Question;
import com.habitforge.modules.study.entity.QuestionGrade;
import com.habitforge.modules.study.mapper.QuestionGradeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 主观题判分编排：成功留痕 / 阈值边界(1/2 恰过半为对, 1/3 为错) /
 * 上游失败退额度 / 解析失败不退 / 限流 7014 / 锁 6003 / 开关 / 空作答前置拒绝 / 6001 透出。
 * 真实 PromptTemplate + 真实 ContextAssembler + 真实 PlanGenerationWriter(包 mock mapper)，
 * PlanAiClient 直接 mock（ChatResult 形状可控, 无需扮到 ChatModel 层）。
 */
@ExtendWith(MockitoExtension.class)
class ShortGradeServiceTest {

    private static final String USER = "user-a";
    private static final String QID = "question-1";
    private static final String LIMIT_KEY = ShortGradeService.quotaKey(USER, LocalDate.now());
    private static final String LOCK_KEY = "habitforge:ai:lock:grade:" + USER;

    private AiProperties props;
    private PlanAiClient aiClient;
    private ShortGradeServiceImpl service;

    @Mock
    private RedisUtil redisUtil;
    @Mock
    private QuestionGradeMapper questionGradeMapper;
    @Mock
    private PlanGenerationMapper generationMapper;

    @BeforeEach
    void setUp() {
        props = new AiProperties();
        props.setEnabled(true);
        aiClient = org.mockito.Mockito.mock(PlanAiClient.class);
        service = new ShortGradeServiceImpl(props, redisUtil, aiClient,
                new ShortGradePromptTemplate(), new ShortGradeContextAssembler(),
                questionGradeMapper, new PlanGenerationWriter(generationMapper));
    }

    // ================= 公共桩 =================

    private Question question() {
        Question q = new Question();
        q.setId(QID);
        q.setUserId(USER);
        q.setQuestionType("SHORT");
        q.setStem("简述中值定理");
        q.setAnswer("参考答案全文");
        return q;
    }

    private void stubUntilBeforeLlm() {
        when(redisUtil.tryAcquire(eq(LIMIT_KEY), anyInt(), any(Duration.class))).thenReturn(true);
        when(redisUtil.tryLock(eq(LOCK_KEY), anyString(), anyLong())).thenReturn(true);
    }

    private void stubChat(String json) {
        when(aiClient.chat(anyString(), anyString()))
                .thenReturn(new PlanAiClient.ChatResult(json, 100L, 50L, "deepseek-v4-flash"));
    }

    private static String pointsJson(boolean a, boolean b, boolean c) {
        return "{\"points\":[" +
                "{\"point\":\"定理条件\",\"hit\":" + a + "}," +
                "{\"point\":\"结论\",\"hit\":" + b + "}," +
                "{\"point\":\"几何意义\",\"hit\":" + c + "}]}";
    }

    private int codeOf(Runnable r) {
        BusinessException e = assertThrows(BusinessException.class, r::run);
        return e.getCode();
    }

    // ================= 用例 =================

    @Test
    void success_twoOfThree_passesThresholdAndLogsGradeRow() {
        stubUntilBeforeLlm();
        stubChat(pointsJson(true, true, false));

        ShortGradeOutcome out = service.grade(USER, question(), "我的作答");

        assertTrue(out.correct()); // 2/3 ≥ 50%
        assertEquals(2, out.covered());
        assertEquals(3, out.total());
        assertEquals(0.67, out.ratio(), 0.001);

        ArgumentCaptor<QuestionGrade> rowCaptor = ArgumentCaptor.forClass(QuestionGrade.class);
        verify(questionGradeMapper).insert(rowCaptor.capture());
        QuestionGrade row = rowCaptor.getValue();
        assertEquals(USER, row.getUserId());
        assertEquals(QID, row.getQuestionId());
        assertTrue(row.getCorrect());
        assertEquals(2, row.getCovered());
        assertEquals(3, row.getTotal());
        assertTrue(row.getPoints().contains("定理条件"));

        ArgumentCaptor<PlanGeneration> logCaptor = ArgumentCaptor.forClass(PlanGeneration.class);
        verify(generationMapper).insert(logCaptor.capture());
        PlanGeneration log = logCaptor.getValue();
        assertEquals(PlanGeneration.KIND_GRADE, log.getKind());
        assertEquals(QID, log.getRefId()); // 流水带题目引用
        assertTrue(log.getSuccess());
        assertEquals(150, log.getTotalTokens());

        verify(redisUtil, never()).release(anyString());
        verify(redisUtil).unlock(eq(LOCK_KEY), anyString());
    }

    /** 计划验证行写"1/2→false", 但决策 6 公式 covered*2>=total 与用户"满足 50% 即为正确"同向: 恰好半覆盖=对 */
    @Test
    void boundary_oneOfTwo_exactlyHalf_passes() {
        stubUntilBeforeLlm();
        stubChat("{\"points\":[{\"point\":\"甲\",\"hit\":true},{\"point\":\"乙\",\"hit\":false}]}");

        assertTrue(service.grade(USER, question(), "答了一点").correct());
    }

    @Test
    void boundary_oneOfThree_fails() {
        stubUntilBeforeLlm();
        stubChat(pointsJson(true, false, false));

        assertFalse(service.grade(USER, question(), "只答了条件").correct());
    }

    @Test
    void disabled_returns6001_neverTouchesLlmOrQuota() {
        props.setEnabled(false);
        assertEquals(6001, codeOf(() -> service.grade(USER, question(), "作答")));
        verifyNoInteractions(redisUtil, aiClient, questionGradeMapper, generationMapper);
    }

    /** 空作答是"省 token 前置拒绝": 400、不触 AI、不占额度(对照 AI_FREE_SLOT_REQUIRED 6006 语义) */
    @Test
    void blankAnswer_returns400_neverLlmNeverQuota() {
        assertEquals(400, codeOf(() -> service.grade(USER, question(), "   ")));
        verifyNoInteractions(redisUtil, aiClient, questionGradeMapper);
    }

    @Test
    void limited_returns7014_neverCallsLlm() {
        when(redisUtil.tryAcquire(eq(LIMIT_KEY), anyInt(), any(Duration.class))).thenReturn(false);

        assertEquals(7014, codeOf(() -> service.grade(USER, question(), "作答")));

        verifyNoInteractions(aiClient);
        verify(redisUtil, never()).tryLock(anyString(), anyString(), anyLong());
    }

    @Test
    void lockConflict_returns6003AndRefundsQuota() {
        when(redisUtil.tryAcquire(eq(LIMIT_KEY), anyInt(), any(Duration.class))).thenReturn(true);
        when(redisUtil.tryLock(eq(LOCK_KEY), anyString(), anyLong())).thenReturn(false);

        assertEquals(6003, codeOf(() -> service.grade(USER, question(), "作答")));

        verify(redisUtil).release(LIMIT_KEY);
        verifyNoInteractions(aiClient);
        verify(redisUtil, never()).unlock(anyString(), anyString());
    }

    @Test
    void upstreamFailure_refundsQuotaAndLogsFailureRow() {
        stubUntilBeforeLlm();
        when(aiClient.chat(anyString(), anyString())).thenThrow(new RuntimeException("connect timed out"));

        assertEquals(6004, codeOf(() -> service.grade(USER, question(), "作答")));

        verify(redisUtil).release(LIMIT_KEY);
        ArgumentCaptor<PlanGeneration> logCaptor = ArgumentCaptor.forClass(PlanGeneration.class);
        verify(generationMapper).insert(logCaptor.capture());
        assertFalse(logCaptor.getValue().getSuccess());
        assertTrue(logCaptor.getValue().getError().contains("connect timed out"));
        assertEquals(QID, logCaptor.getValue().getRefId());
        verify(questionGradeMapper, never()).insert(any(QuestionGrade.class));
        verify(redisUtil).unlock(eq(LOCK_KEY), anyString());
    }

    @Test
    void parseFailure_keepsQuotaLogsRawAndThrows6005() {
        stubUntilBeforeLlm();
        stubChat("同学你的答案很精彩，继续加油！"); // 完全不是 JSON

        assertEquals(6005, codeOf(() -> service.grade(USER, question(), "作答")));

        verify(redisUtil, never()).release(anyString()); // token 已耗, 不退
        ArgumentCaptor<PlanGeneration> logCaptor = ArgumentCaptor.forClass(PlanGeneration.class);
        verify(generationMapper).insert(logCaptor.capture());
        assertFalse(logCaptor.getValue().getSuccess());
        assertEquals("同学你的答案很精彩，继续加油！", logCaptor.getValue().getRawOutput());
        verify(questionGradeMapper, never()).insert(any(QuestionGrade.class));
    }

    /** 判分留痕写不进库 → 6004 且流水已留（不留"有消耗无流水"缺口） */
    @Test
    void gradeRowPersistFailure_logsAndThrows6004() {
        stubUntilBeforeLlm();
        stubChat(pointsJson(true, true, true));
        when(questionGradeMapper.insert(any(QuestionGrade.class))).thenThrow(new RuntimeException("db down"));

        assertEquals(6004, codeOf(() -> service.grade(USER, question(), "作答")));

        ArgumentCaptor<PlanGeneration> logCaptor = ArgumentCaptor.forClass(PlanGeneration.class);
        verify(generationMapper).insert(logCaptor.capture());
        assertFalse(logCaptor.getValue().getSuccess());
        assertTrue(logCaptor.getValue().getError().contains("db down"));
        verify(redisUtil, never()).release(anyString()); // token 已耗
    }

    @Test
    void aiNotConfigured_propagates6001AndRefunds_noLogRow() {
        stubUntilBeforeLlm();
        when(aiClient.chat(anyString(), anyString()))
                .thenThrow(new BusinessException(ErrorCode.AI_NOT_CONFIGURED));

        assertEquals(6001, codeOf(() -> service.grade(USER, question(), "作答")));

        verify(redisUtil).release(LIMIT_KEY);
        verify(generationMapper, never()).insert(any(PlanGeneration.class));
        verify(redisUtil).unlock(eq(LOCK_KEY), anyString());
    }
}
