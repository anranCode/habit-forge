package com.habitforge.service;

import com.habitforge.common.exception.BusinessException;
import com.habitforge.common.exception.ErrorCode;
import com.habitforge.common.util.RedisUtil;
import com.habitforge.modules.ai.client.PlanAiClient;
import com.habitforge.modules.ai.client.PlanContextAssembler;
import com.habitforge.modules.ai.client.PlanPromptTemplate;
import com.habitforge.modules.ai.config.AiProperties;
import com.habitforge.modules.ai.dto.PlanResponse;
import com.habitforge.modules.ai.entity.DailyPlan;
import com.habitforge.modules.ai.entity.PlanBlock;
import com.habitforge.modules.ai.entity.PlanFreeSlot;
import com.habitforge.modules.ai.entity.PlanGeneration;
import com.habitforge.modules.ai.mapper.PlanFreeSlotMapper;
import com.habitforge.modules.ai.mapper.PlanGenerationMapper;
import com.habitforge.modules.ai.service.DailyPlanService;
import com.habitforge.modules.ai.service.impl.AiScheduleServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicConnectionProperties;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * AI 生成编排：成功落块 / 上游失败退额度 / 解析失败不退 / 限流 / 并发锁 / 开关 / 无时段
 */
@ExtendWith(MockitoExtension.class)
class AiScheduleServiceTest {

    private static final String USER = "user-a";
    private static final String PLAN_ID = "plan-1";
    private static final LocalDate TODAY = LocalDate.now();
    private static final String LIMIT_KEY = "ai:plan:" + USER + ":" + TODAY;
    private static final String LOCK_KEY = "habitforge:ai:lock:" + USER;

    private static final String VALID_JSON = """
            {"blocks":[
             {"start":"07:00","end":"08:00","title":"背单词","type":"STUDY","habitId":null,"subjectId":"s1","chapterId":"c1"},
             {"start":"08:30","end":"09:00","title":"晨跑","type":"HABIT","habitId":"h1","subjectId":null,"chapterId":null},
             {"start":"09:00","end":"09:10","title":"休息","type":"REST","habitId":null,"subjectId":null,"chapterId":null}]}
            """;

    private AiProperties props;
    private AiScheduleServiceImpl service;

    @Mock
    private RedisUtil redisUtil;
    @Mock
    private PlanContextAssembler contextAssembler;
    @Mock
    private DailyPlanService dailyPlanService;
    @Mock
    private PlanFreeSlotMapper freeSlotMapper;
    @Mock
    private PlanGenerationMapper generationMapper;
    @Mock
    private ChatModel chatModel;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        props = new AiProperties();
        props.setEnabled(true);
        ObjectProvider<ChatModel> provider = mock(ObjectProvider.class);
        lenient().when(provider.getIfAvailable()).thenReturn(chatModel);
        // 凭证属性留空(null) => 凭证护栏放行, 判断权交给 ChatModel, 与本测试的关注点(限流/锁/翻译)无关
        ObjectProvider<AnthropicConnectionProperties> connProvider = mock(ObjectProvider.class);
        PlanAiClient aiClient = new PlanAiClient(provider, connProvider);
        service = new AiScheduleServiceImpl(props, redisUtil, aiClient, new PlanPromptTemplate(),
                contextAssembler, dailyPlanService, freeSlotMapper, generationMapper);
    }

    /** 用替身 PlanAiClient 装配服务（验证 catch 对 PlanAiClient 异常的处理, 绕开 ChatClient 包装） */
    private AiScheduleServiceImpl serviceWith(PlanAiClient aiClient) {
        return new AiScheduleServiceImpl(props, redisUtil, aiClient, new PlanPromptTemplate(),
                contextAssembler, dailyPlanService, freeSlotMapper, generationMapper);
    }

    // ================= 公共桩 =================

    private DailyPlan plan() {
        DailyPlan p = new DailyPlan();
        p.setId(PLAN_ID);
        p.setUserId(USER);
        p.setPlanDate(TODAY);
        p.setGenCount(1);
        return p;
    }

    private PlanFreeSlot slot() {
        PlanFreeSlot s = new PlanFreeSlot();
        s.setPlanId(PLAN_ID);
        s.setSortOrder(0);
        return s;
    }

    private PlanContextAssembler.Context context() {
        return new PlanContextAssembler.Context("## 今天", Set.of("h1"), Set.of("s1"), Set.of("c1"));
    }

    private void stubUntilBeforeLlm(DailyPlan plan) {
        when(dailyPlanService.findPlan(USER, TODAY)).thenReturn(plan);
        when(freeSlotMapper.selectList(any())).thenReturn(List.of(slot()));
        when(redisUtil.tryAcquire(eq(LIMIT_KEY), anyInt(), any(Duration.class))).thenReturn(true);
        when(redisUtil.tryLock(eq(LOCK_KEY), anyString(), anyLong())).thenReturn(true);
    }

    private void stubLlmResponse(String text) {
        Usage usage = mock(Usage.class);
        lenient().when(usage.getPromptTokens()).thenReturn(100);
        lenient().when(usage.getCompletionTokens()).thenReturn(50);
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .model("deepseek-v4-flash")
                .usage(usage)
                .build();
        ChatResponse response = new ChatResponse(
                List.of(new Generation(new AssistantMessage(text))), metadata);
        when(chatModel.call(any(Prompt.class))).thenReturn(response);
    }

    private int codeOf(Runnable r) {
        BusinessException e = assertThrows(BusinessException.class, r::run);
        return e.getCode();
    }

    // ================= 用例 =================

    @Test
    void success_replacesProposedAndLogs() {
        DailyPlan plan = plan();
        stubUntilBeforeLlm(plan);
        when(contextAssembler.assemble(eq(USER), eq(TODAY), anyList())).thenReturn(context());
        stubLlmResponse("```json\n" + VALID_JSON + "\n```"); // 带围栏也应被 converter 提取
        PlanResponse expected = PlanResponse.builder().id(PLAN_ID).build();
        when(dailyPlanService.getPlanResponse(USER, TODAY)).thenReturn(expected);

        PlanResponse actual = service.generate(USER, TODAY);
        assertEquals(expected, actual);

        ArgumentCaptor<List<PlanBlock>> blocksCaptor = ArgumentCaptor.forClass(List.class);
        verify(dailyPlanService).replaceProposedWithGenerated(eq(plan), blocksCaptor.capture(), eq("deepseek-v4-flash"));
        assertEquals(3, blocksCaptor.getValue().size());

        ArgumentCaptor<PlanGeneration> logCaptor = ArgumentCaptor.forClass(PlanGeneration.class);
        verify(generationMapper).insert(logCaptor.capture());
        PlanGeneration log = logCaptor.getValue();
        assertTrue(log.getSuccess());
        assertEquals(100, log.getPromptTokens());
        assertEquals(50, log.getCompletionTokens());
        assertEquals(150, log.getTotalTokens());
        assertTrue(log.getRawOutput().contains("背单词"));

        verify(redisUtil, never()).release(anyString());
        verify(redisUtil).unlock(eq(LOCK_KEY), anyString());
    }

    @Test
    void upstreamFailure_refundsQuotaAndLogs6004() {
        DailyPlan plan = plan();
        stubUntilBeforeLlm(plan);
        when(contextAssembler.assemble(eq(USER), eq(TODAY), anyList())).thenReturn(context());
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("connect timed out"));

        assertEquals(6004, codeOf(() -> service.generate(USER, TODAY)));

        verify(redisUtil).release(LIMIT_KEY);
        ArgumentCaptor<PlanGeneration> logCaptor = ArgumentCaptor.forClass(PlanGeneration.class);
        verify(generationMapper).insert(logCaptor.capture());
        assertFalse(logCaptor.getValue().getSuccess());
        assertTrue(logCaptor.getValue().getError().contains("connect timed out"));
        verify(dailyPlanService, never()).replaceProposedWithGenerated(any(), any(), any());
        verify(redisUtil).unlock(eq(LOCK_KEY), anyString());
    }

    @Test
    void parseFailure_keepsQuotaAndLogs6005() {
        DailyPlan plan = plan();
        stubUntilBeforeLlm(plan);
        when(contextAssembler.assemble(eq(USER), eq(TODAY), anyList())).thenReturn(context());
        stubLlmResponse("今天天气不错，建议您劳逸结合。"); // 完全不是 JSON

        assertEquals(6005, codeOf(() -> service.generate(USER, TODAY)));

        verify(redisUtil, never()).release(anyString()); // token 已耗, 不退额度
        ArgumentCaptor<PlanGeneration> logCaptor = ArgumentCaptor.forClass(PlanGeneration.class);
        verify(generationMapper).insert(logCaptor.capture());
        assertFalse(logCaptor.getValue().getSuccess());
        assertEquals("今天天气不错，建议您劳逸结合。", logCaptor.getValue().getRawOutput());
        verify(dailyPlanService, never()).replaceProposedWithGenerated(any(), any(), any());
        verify(redisUtil).unlock(eq(LOCK_KEY), anyString());
    }

    @Test
    void limited_returns6002AndNeverCallsLlm() {
        when(dailyPlanService.findPlan(USER, TODAY)).thenReturn(plan());
        when(freeSlotMapper.selectList(any())).thenReturn(List.of(slot()));
        when(redisUtil.tryAcquire(eq(LIMIT_KEY), anyInt(), any(Duration.class))).thenReturn(false);

        assertEquals(6002, codeOf(() -> service.generate(USER, TODAY)));

        verifyNoInteractions(chatModel);
        verify(redisUtil, never()).tryLock(anyString(), anyString(), anyLong());
        verify(generationMapper, never()).insert(any(PlanGeneration.class));
    }

    @Test
    void lockConflict_returns6003AndRefundsQuota() {
        when(dailyPlanService.findPlan(USER, TODAY)).thenReturn(plan());
        when(freeSlotMapper.selectList(any())).thenReturn(List.of(slot()));
        when(redisUtil.tryAcquire(eq(LIMIT_KEY), anyInt(), any(Duration.class))).thenReturn(true);
        when(redisUtil.tryLock(eq(LOCK_KEY), anyString(), anyLong())).thenReturn(false);

        assertEquals(6003, codeOf(() -> service.generate(USER, TODAY)));

        verify(redisUtil).release(LIMIT_KEY); // 未耗 token, 退额度防双击扣次
        verifyNoInteractions(chatModel);
        verify(redisUtil, never()).unlock(anyString(), anyString());
    }

    @Test
    void disabled_returns6001() {
        props.setEnabled(false);
        assertEquals(6001, codeOf(() -> service.generate(USER, TODAY)));
        verifyNoInteractions(redisUtil, chatModel, dailyPlanService, freeSlotMapper, generationMapper);
    }

    @Test
    void noPlanYet_returns6006WithoutTouchingQuota() {
        when(dailyPlanService.findPlan(USER, TODAY)).thenReturn(null);
        assertEquals(6006, codeOf(() -> service.generate(USER, TODAY)));
        verifyNoInteractions(redisUtil, chatModel, generationMapper);
    }

    @Test
    void planWithoutSlots_returns6006() {
        when(dailyPlanService.findPlan(USER, TODAY)).thenReturn(plan());
        when(freeSlotMapper.selectList(any())).thenReturn(List.of());
        assertEquals(6006, codeOf(() -> service.generate(USER, TODAY)));
        verify(redisUtil, never()).tryAcquire(anyString(), anyInt(), any(Duration.class));
        verifyNoInteractions(chatModel);
    }

    @Test
    void tooFewValidBlocks_mapsTo6005() {
        DailyPlan plan = plan();
        stubUntilBeforeLlm(plan);
        when(contextAssembler.assemble(eq(USER), eq(TODAY), anyList())).thenReturn(context());
        stubLlmResponse("{\"blocks\":[{\"start\":\"07:00\",\"end\":\"08:00\",\"title\":\"块\",\"type\":\"OTHER\",\"habitId\":null,\"subjectId\":null,\"chapterId\":null}]}");

        assertEquals(6005, codeOf(() -> service.generate(USER, TODAY))); // minBlocks=3
        verify(redisUtil, never()).release(anyString());
        verify(dailyPlanService, never()).replaceProposedWithGenerated(any(), any(), any());
    }

    /** AI 未配置(ChatModel/凭证缺失, PlanAiClient 抛 6001) → 原样透出 6001, 不得被吞成 6004 */
    @Test
    void aiNotConfigured_propagates6001Not6004() {
        DailyPlan plan = plan();
        stubUntilBeforeLlm(plan);
        when(contextAssembler.assemble(eq(USER), eq(TODAY), anyList())).thenReturn(context());
        PlanAiClient client = mock(PlanAiClient.class);
        when(client.chat(anyString(), anyString())).thenThrow(new BusinessException(ErrorCode.AI_NOT_CONFIGURED));
        AiScheduleServiceImpl svc = serviceWith(client);

        assertEquals(6001, codeOf(() -> svc.generate(USER, TODAY)));

        verify(redisUtil).release(LIMIT_KEY); // 未触达 LLM, 未耗 token 退额度
        verify(generationMapper, never()).insert(any(PlanGeneration.class)); // 非生成尝试, 不留流水
        verify(redisUtil).unlock(eq(LOCK_KEY), anyString());
    }

    /** 落块失败(token 已耗) → 仍写失败流水(带原始输出) 再抛 6004, 不留「有消耗无流水」缺口 */
    @Test
    void blockPersistFailure_logsAndThrows6004() {
        DailyPlan plan = plan();
        stubUntilBeforeLlm(plan);
        when(contextAssembler.assemble(eq(USER), eq(TODAY), anyList())).thenReturn(context());
        stubLlmResponse(VALID_JSON);
        doThrow(new RuntimeException("db down"))
                .when(dailyPlanService).replaceProposedWithGenerated(eq(plan), anyList(), eq("deepseek-v4-flash"));

        assertEquals(6004, codeOf(() -> service.generate(USER, TODAY)));

        ArgumentCaptor<PlanGeneration> logCaptor = ArgumentCaptor.forClass(PlanGeneration.class);
        verify(generationMapper).insert(logCaptor.capture());
        PlanGeneration log = logCaptor.getValue();
        assertFalse(log.getSuccess());
        assertTrue(log.getError().contains("db down"));
        assertEquals(150, log.getTotalTokens());
        assertTrue(log.getRawOutput().contains("背单词"));
        verify(redisUtil, never()).release(anyString()); // token 已耗, 不退额度
        verify(redisUtil).unlock(eq(LOCK_KEY), anyString());
    }
}
