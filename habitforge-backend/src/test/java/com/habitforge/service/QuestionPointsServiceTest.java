package com.habitforge.service;

import com.habitforge.common.exception.BusinessException;
import com.habitforge.common.exception.ErrorCode;
import com.habitforge.common.util.RedisUtil;
import com.habitforge.modules.ai.client.PlanAiClient;
import com.habitforge.modules.ai.client.QuestionPointsContextAssembler;
import com.habitforge.modules.ai.client.QuestionPointsPromptTemplate;
import com.habitforge.modules.ai.config.AiProperties;
import com.habitforge.modules.ai.entity.PlanGeneration;
import com.habitforge.modules.ai.mapper.PlanGenerationMapper;
import com.habitforge.modules.ai.service.PlanGenerationWriter;
import com.habitforge.modules.ai.service.QuestionPointsService;
import com.habitforge.modules.ai.service.impl.QuestionPointsServiceImpl;
import com.habitforge.modules.study.entity.Question;
import com.habitforge.modules.study.mapper.ChapterMapper;
import com.habitforge.modules.study.mapper.QuestionMapper;
import com.habitforge.modules.study.mapper.SubjectMapper;
import com.habitforge.modules.study.service.support.PointsCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

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
 * 考点识别编排：成功覆盖写+流水 / 归属校验 7007 不触 Redis / 上游失败退额度 /
 * 解析失败不退且流水带 raw / 限流 7013 / 锁 6003 退额度 / 开关 6001 / 落库失败 6004。
 * 真实 PromptTemplate + 真实 ContextAssembler(题面无 subject/chapter, mapper 不触) +
 * 真实 PlanGenerationWriter(包 mock mapper)，PlanAiClient 直接 mock —— 骨架对照 ShortGradeServiceTest。
 */
@ExtendWith(MockitoExtension.class)
class QuestionPointsServiceTest {

    private static final String USER = "user-a";
    private static final String QID = "question-1";
    private static final String LIMIT_KEY = QuestionPointsService.quotaKey(USER, LocalDate.now());
    private static final String LOCK_KEY = "habitforge:ai:lock:points:" + USER;

    private AiProperties props;
    private PlanAiClient aiClient;
    private QuestionPointsServiceImpl service;

    @Mock
    private RedisUtil redisUtil;
    @Mock
    private QuestionMapper questionMapper;
    @Mock
    private SubjectMapper subjectMapper;
    @Mock
    private ChapterMapper chapterMapper;
    @Mock
    private PlanGenerationMapper generationMapper;

    @BeforeEach
    void setUp() {
        props = new AiProperties();
        props.setEnabled(true);
        aiClient = org.mockito.Mockito.mock(PlanAiClient.class);
        service = new QuestionPointsServiceImpl(props, redisUtil, aiClient,
                new QuestionPointsPromptTemplate(),
                new QuestionPointsContextAssembler(subjectMapper, chapterMapper),
                questionMapper, new PlanGenerationWriter(generationMapper));
    }

    // ================= 公共桩 =================

    private Question question() {
        Question q = new Question();
        q.setId(QID);
        q.setUserId(USER);
        q.setQuestionType("SINGLE");
        q.setStem("设 f 在 [a,b] 连续…考查的是");
        q.setAnswer("B");
        return q;
    }

    private void stubOwnedQuestion() {
        when(questionMapper.selectById(QID)).thenReturn(question());
    }

    private void stubUntilBeforeLlm() {
        when(redisUtil.tryAcquire(eq(LIMIT_KEY), anyInt(), any(Duration.class))).thenReturn(true);
        when(redisUtil.tryLock(eq(LOCK_KEY), anyString(), anyLong())).thenReturn(true);
    }

    private void stubChat(String json) {
        when(aiClient.chat(anyString(), anyString()))
                .thenReturn(new PlanAiClient.ChatResult(json, 100L, 50L, "deepseek-v4-flash"));
    }

    private int codeOf(Runnable r) {
        BusinessException e = assertThrows(BusinessException.class, r::run);
        return e.getCode();
    }

    // ================= 用例 =================

    @Test
    void success_overwritesKnowledgePointsAndLogsFlow() {
        stubOwnedQuestion();
        stubUntilBeforeLlm();
        stubChat("{\"points\":[\" 中值定理 \",\"中值定理\",\"复合函数求导\",\"极限计算\"]}");

        service.recognize(USER, QID);

        ArgumentCaptor<Question> qCaptor = ArgumentCaptor.forClass(Question.class);
        verify(questionMapper).updateById(qCaptor.capture());
        List<String> stored = PointsCodec.parse(qCaptor.getValue().getKnowledgePoints());
        // trim + 去重: "中值定理"只留一个, 顺序保持
        assertEquals(List.of("中值定理", "复合函数求导", "极限计算"), stored);

        ArgumentCaptor<PlanGeneration> logCaptor = ArgumentCaptor.forClass(PlanGeneration.class);
        verify(generationMapper).insert(logCaptor.capture());
        PlanGeneration log = logCaptor.getValue();
        assertEquals(PlanGeneration.KIND_POINTS, log.getKind());
        assertEquals(QID, log.getRefId());
        assertTrue(log.getSuccess());
        assertEquals(150, log.getTotalTokens());

        verify(redisUtil, never()).release(anyString());
        verify(redisUtil).unlock(eq(LOCK_KEY), anyString());
    }

    /** 归属校验在限流/锁之前: 别人的题连 Redis 都不该碰, 更不烧 token */
    @Test
    void foreignOrMissingQuestion_returns7007_neverTouchesQuotaOrLlm() {
        when(questionMapper.selectById(QID)).thenReturn(null);
        assertEquals(7007, codeOf(() -> service.recognize(USER, QID)));

        Question foreign = question();
        foreign.setUserId("user-b");
        when(questionMapper.selectById("other-q")).thenReturn(foreign);
        assertEquals(7007, codeOf(() -> service.recognize(USER, "other-q")));

        verifyNoInteractions(redisUtil, aiClient, generationMapper);
        verify(questionMapper, never()).updateById(any(Question.class));
    }

    @Test
    void disabled_returns6001_neverTouchesAnything() {
        props.setEnabled(false);
        assertEquals(6001, codeOf(() -> service.recognize(USER, QID)));
        verifyNoInteractions(redisUtil, aiClient, questionMapper, generationMapper);
    }

    @Test
    void limited_returns7013_neverCallsLlm() {
        stubOwnedQuestion();
        when(redisUtil.tryAcquire(eq(LIMIT_KEY), anyInt(), any(Duration.class))).thenReturn(false);

        assertEquals(7013, codeOf(() -> service.recognize(USER, QID)));

        verifyNoInteractions(aiClient);
        verify(redisUtil, never()).tryLock(anyString(), anyString(), anyLong());
    }

    @Test
    void lockConflict_returns6003AndRefundsQuota() {
        stubOwnedQuestion();
        when(redisUtil.tryAcquire(eq(LIMIT_KEY), anyInt(), any(Duration.class))).thenReturn(true);
        when(redisUtil.tryLock(eq(LOCK_KEY), anyString(), anyLong())).thenReturn(false);

        assertEquals(6003, codeOf(() -> service.recognize(USER, QID)));

        verify(redisUtil).release(LIMIT_KEY);
        verifyNoInteractions(aiClient);
        verify(redisUtil, never()).unlock(anyString(), anyString());
    }

    @Test
    void upstreamFailure_refundsQuotaAndLogsFailureRow() {
        stubOwnedQuestion();
        stubUntilBeforeLlm();
        when(aiClient.chat(anyString(), anyString())).thenThrow(new RuntimeException("connect timed out"));

        assertEquals(6004, codeOf(() -> service.recognize(USER, QID)));

        verify(redisUtil).release(LIMIT_KEY);
        ArgumentCaptor<PlanGeneration> logCaptor = ArgumentCaptor.forClass(PlanGeneration.class);
        verify(generationMapper).insert(logCaptor.capture());
        assertEquals(PlanGeneration.KIND_POINTS, logCaptor.getValue().getKind());
        assertFalse(logCaptor.getValue().getSuccess());
        assertTrue(logCaptor.getValue().getError().contains("connect timed out"));
        verify(questionMapper, never()).updateById(any(Question.class));
        verify(redisUtil).unlock(eq(LOCK_KEY), anyString());
    }

    @Test
    void parseFailure_keepsQuotaLogsRawAndThrows6005() {
        stubOwnedQuestion();
        stubUntilBeforeLlm();
        stubChat("这道题考查的内容非常丰富，此处不宜展开。"); // 完全不是 JSON

        assertEquals(6005, codeOf(() -> service.recognize(USER, QID)));

        verify(redisUtil, never()).release(anyString()); // token 已耗, 不退
        ArgumentCaptor<PlanGeneration> logCaptor = ArgumentCaptor.forClass(PlanGeneration.class);
        verify(generationMapper).insert(logCaptor.capture());
        assertFalse(logCaptor.getValue().getSuccess());
        assertEquals("这道题考查的内容非常丰富，此处不宜展开。", logCaptor.getValue().getRawOutput());
        verify(questionMapper, never()).updateById(any(Question.class));
    }

    /** 考点写不进库 → 6004 且流水已留（不留"有消耗无流水"缺口） */
    @Test
    void persistFailure_logsAndThrows6004() {
        stubOwnedQuestion();
        stubUntilBeforeLlm();
        stubChat("{\"points\":[\"中值定理\"]}");
        when(questionMapper.updateById(any(Question.class))).thenThrow(new RuntimeException("db down"));

        assertEquals(6004, codeOf(() -> service.recognize(USER, QID)));

        ArgumentCaptor<PlanGeneration> logCaptor = ArgumentCaptor.forClass(PlanGeneration.class);
        verify(generationMapper).insert(logCaptor.capture());
        assertFalse(logCaptor.getValue().getSuccess());
        assertTrue(logCaptor.getValue().getError().contains("db down"));
        verify(redisUtil, never()).release(anyString()); // token 已耗
    }

    @Test
    void aiNotConfigured_propagates6001AndRefunds_noLogRow() {
        stubOwnedQuestion();
        stubUntilBeforeLlm();
        when(aiClient.chat(anyString(), anyString()))
                .thenThrow(new BusinessException(ErrorCode.AI_NOT_CONFIGURED));

        assertEquals(6001, codeOf(() -> service.recognize(USER, QID)));

        verify(redisUtil).release(LIMIT_KEY);
        verify(generationMapper, never()).insert(any(PlanGeneration.class));
        verify(redisUtil).unlock(eq(LOCK_KEY), anyString());
    }
}
