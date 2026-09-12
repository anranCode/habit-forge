package com.habitforge.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.habitforge.common.exception.BusinessException;
import com.habitforge.common.util.RedisUtil;
import com.habitforge.modules.study.dto.FlashcardCreateRequest;
import com.habitforge.modules.study.dto.FlashcardQueueResponse;
import com.habitforge.modules.study.dto.FlashcardReviewResultResponse;
import com.habitforge.modules.study.entity.CardReviewLog;
import com.habitforge.modules.study.entity.Chapter;
import com.habitforge.modules.study.entity.Flashcard;
import com.habitforge.modules.study.entity.Subject;
import com.habitforge.modules.study.mapper.CardReviewLogMapper;
import com.habitforge.modules.study.mapper.ChapterMapper;
import com.habitforge.modules.study.mapper.FlashcardMapper;
import com.habitforge.modules.study.mapper.SubjectMapper;
import com.habitforge.modules.study.service.Sm2Scheduler;
import com.habitforge.modules.study.service.impl.FlashcardServiceImpl;
import com.habitforge.modules.user.service.UserService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 闪卡服务关键路径测试(队列/评分入队重现/奖励只发一次)
 */
@ExtendWith(MockitoExtension.class)
class FlashcardServiceImplTest {

    @Mock
    private FlashcardMapper flashcardMapper;
    @Mock
    private CardReviewLogMapper cardReviewLogMapper;
    @Mock
    private SubjectMapper subjectMapper;
    @Mock
    private ChapterMapper chapterMapper;
    @Mock
    private UserService userService;
    @Mock
    private RedisUtil redisUtil;

    @InjectMocks
    private FlashcardServiceImpl flashcardService;

    private static final String USER_ID = "user-a";
    private static final String SUBJECT_ID = "subject-1";
    private static final String CHAPTER_ID = "chapter-1";
    private static final String CARD_ID = "card-1";

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), Flashcard.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), CardReviewLog.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), Subject.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), Chapter.class);
    }

    private Flashcard card(String userId) {
        Flashcard c = new Flashcard();
        c.setId(CARD_ID);
        c.setUserId(userId);
        c.setSubjectId(SUBJECT_ID);
        c.setFront("面");
        c.setBack("背");
        c.setEaseFactor(new BigDecimal("2.50"));
        c.setIntervalDays(0);
        c.setRepetition(0);
        c.setLapses(0);
        c.setDueDate(LocalDate.now());
        c.setStatus(Flashcard.STATUS_ACTIVE);
        return c;
    }

    // ================= 队列 =================

    @Test
    void queue_returnsCardsAndTotals() {
        when(flashcardMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(card(USER_ID)));
        // 第一次 dueTotal, 第二次 newTotal
        when(flashcardMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(7L, 2L);

        FlashcardQueueResponse resp = flashcardService.queue(USER_ID, null, null);

        assertEquals(1, resp.getCards().size());
        assertEquals(7, resp.getDueTotal());
        assertEquals(2, resp.getNewTotal());
    }

    @Test
    void queue_emptyCards_noRows() {
        when(flashcardMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(flashcardMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L, 0L);

        FlashcardQueueResponse resp = flashcardService.queue(USER_ID, SUBJECT_ID, 10);

        assertEquals(0, resp.getCards().size());
        assertEquals(0, resp.getDueTotal());
    }

    // ================= 创建校验 =================

    @Test
    void create_foreignSubject_returns7001() {
        Subject other = new Subject();
        other.setId(SUBJECT_ID);
        other.setUserId("user-b");
        when(subjectMapper.selectById(SUBJECT_ID)).thenReturn(other);

        FlashcardCreateRequest req = new FlashcardCreateRequest();
        req.setSubjectId(SUBJECT_ID);
        req.setFront("f");
        req.setBack("b");
        BusinessException e = assertThrows(BusinessException.class, () -> flashcardService.create(USER_ID, req));
        assertEquals(7001, e.getCode());
    }

    @Test
    void create_chapterOfOtherSubject_returns7002() {
        when(subjectMapper.selectById(SUBJECT_ID)).thenReturn(subject(USER_ID));
        Chapter chapter = new Chapter();
        chapter.setId(CHAPTER_ID);
        chapter.setSubjectId("subject-other");
        when(chapterMapper.selectById(CHAPTER_ID)).thenReturn(chapter);

        FlashcardCreateRequest req = new FlashcardCreateRequest();
        req.setSubjectId(SUBJECT_ID);
        req.setChapterId(CHAPTER_ID);
        req.setFront("f");
        req.setBack("b");
        BusinessException e = assertThrows(BusinessException.class, () -> flashcardService.create(USER_ID, req));
        assertEquals(7002, e.getCode());
    }

    private Subject subject(String userId) {
        Subject s = new Subject();
        s.setId(SUBJECT_ID);
        s.setUserId(userId);
        return s;
    }

    // ================= 评分 =================

    @Test
    void review_invalidRating_returns7005() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> flashcardService.review(USER_ID, CARD_ID, 5));
        assertEquals(7005, e.getCode());
        assertThrows(BusinessException.class, () -> flashcardService.review(USER_ID, CARD_ID, null));
        verify(flashcardMapper, never()).updateById(any(Flashcard.class));
    }

    @Test
    void review_crossUser_returns7004() {
        when(flashcardMapper.selectById(CARD_ID)).thenReturn(card("user-b"));
        BusinessException e = assertThrows(BusinessException.class,
                () -> flashcardService.review(USER_ID, CARD_ID, 3));
        assertEquals(7004, e.getCode());
    }

    @Test
    void review_rating1_reappearsToday_withLogSnapshot() {
        when(flashcardMapper.selectById(CARD_ID)).thenReturn(card(USER_ID));
        when(cardReviewLogMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(3L);

        FlashcardReviewResultResponse resp = flashcardService.review(USER_ID, CARD_ID, 1);

        // 当日重现: due=today, interval=0, EF 2.50-0.20=2.30
        assertEquals(LocalDate.now(), resp.getDueDate());
        assertEquals(0, resp.getIntervalDays());
        assertEquals(0, resp.getEaseFactor().compareTo(new BigDecimal("2.30")));
        assertEquals(3, resp.getReviewedToday());
        assertEquals(0, resp.getRewardPoints());
        verify(userService, never()).addPoints(anyString(), anyInt());

        ArgumentCaptor<Flashcard> cardCaptor = ArgumentCaptor.forClass(Flashcard.class);
        verify(flashcardMapper).updateById(cardCaptor.capture());
        assertEquals(LocalDate.now(), cardCaptor.getValue().getDueDate());
        assertEquals(1, cardCaptor.getValue().getLapses());

        // 日志快照 before/after
        ArgumentCaptor<CardReviewLog> logCaptor = ArgumentCaptor.forClass(CardReviewLog.class);
        verify(cardReviewLogMapper).insert(logCaptor.capture());
        CardReviewLog log = logCaptor.getValue();
        assertEquals(0, log.getIntervalBefore());
        assertEquals(0, log.getIntervalAfter());
        assertEquals(0, log.getEaseBefore().compareTo(new BigDecimal("2.50")));
        assertEquals(0, log.getEaseAfter().compareTo(new BigDecimal("2.30")));
        assertEquals(1, log.getRating());
    }

    @Test
    void review_rating3_newCard_schedulesTomorrow() {
        when(flashcardMapper.selectById(CARD_ID)).thenReturn(card(USER_ID));
        when(cardReviewLogMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        FlashcardReviewResultResponse resp = flashcardService.review(USER_ID, CARD_ID, 3);

        assertEquals(LocalDate.now().plusDays(1), resp.getDueDate());
        assertEquals(1, resp.getIntervalDays());
        assertEquals(0, resp.getEaseFactor().compareTo(Sm2Scheduler.EF_DEFAULT));
    }

    @Test
    void review_reachesThreshold_firstTime_grantsRewardOnce() {
        when(flashcardMapper.selectById(CARD_ID)).thenReturn(card(USER_ID));
        when(cardReviewLogMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(5L);
        when(redisUtil.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        FlashcardReviewResultResponse resp = flashcardService.review(USER_ID, CARD_ID, 3);

        assertEquals(10, resp.getRewardPoints());
        verify(userService).addPoints(eq(USER_ID), eq(10));
    }

    @Test
    void review_reachesThreshold_alreadyRewarded_grantsNothing() {
        when(flashcardMapper.selectById(CARD_ID)).thenReturn(card(USER_ID));
        when(cardReviewLogMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(6L);
        when(redisUtil.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        FlashcardReviewResultResponse resp = flashcardService.review(USER_ID, CARD_ID, 3);

        assertEquals(0, resp.getRewardPoints());
        verify(userService, never()).addPoints(anyString(), anyInt());
    }

    @Test
    void review_redisFailure_failClosedNoReward() {
        when(flashcardMapper.selectById(CARD_ID)).thenReturn(card(USER_ID));
        when(cardReviewLogMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(5L);
        when(redisUtil.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RuntimeException("redis down"));

        FlashcardReviewResultResponse resp = flashcardService.review(USER_ID, CARD_ID, 3);

        // Redis 读不到标记时不发分(防当日重复发放), 评分本身仍成功
        assertEquals(0, resp.getRewardPoints());
        verify(userService, never()).addPoints(anyString(), anyInt());
    }

    @Test
    void review_belowThreshold_noRewardAttempt() {
        when(flashcardMapper.selectById(CARD_ID)).thenReturn(card(USER_ID));
        when(cardReviewLogMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(4L);

        FlashcardReviewResultResponse resp = flashcardService.review(USER_ID, CARD_ID, 3);

        assertEquals(0, resp.getRewardPoints());
        verify(redisUtil, never()).setIfAbsent(anyString(), anyString(), any(Duration.class));
    }
}
