package com.habitforge.modules.study.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.habitforge.common.constant.AppConstant;
import com.habitforge.common.exception.BusinessException;
import com.habitforge.common.exception.ErrorCode;
import com.habitforge.common.util.RedisUtil;
import com.habitforge.modules.study.dto.FlashcardCreateRequest;
import com.habitforge.modules.study.dto.FlashcardQueueResponse;
import com.habitforge.modules.study.dto.FlashcardResponse;
import com.habitforge.modules.study.dto.FlashcardReviewResultResponse;
import com.habitforge.modules.study.dto.FlashcardStatsResponse;
import com.habitforge.modules.study.dto.FlashcardUpdateRequest;
import com.habitforge.modules.study.entity.CardReviewLog;
import com.habitforge.modules.study.entity.Chapter;
import com.habitforge.modules.study.entity.Flashcard;
import com.habitforge.modules.study.entity.Subject;
import com.habitforge.modules.study.mapper.CardReviewLogMapper;
import com.habitforge.modules.study.mapper.ChapterMapper;
import com.habitforge.modules.study.mapper.FlashcardMapper;
import com.habitforge.modules.study.mapper.SubjectMapper;
import com.habitforge.modules.study.service.FlashcardService;
import com.habitforge.modules.study.service.Sm2Scheduler;
import com.habitforge.modules.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FlashcardServiceImpl implements FlashcardService {

    /** 4 档评分白名单 */
    private static final Set<Integer> VALID_RATINGS = Set.of(1, 2, 3, 4);
    /** 队列单次拉取硬上限(防超大 limit 打满内存) */
    private static final int QUEUE_MAX_LIMIT = 200;

    private final FlashcardMapper flashcardMapper;
    private final CardReviewLogMapper cardReviewLogMapper;
    private final SubjectMapper subjectMapper;
    private final ChapterMapper chapterMapper;
    private final UserService userService;
    private final RedisUtil redisUtil;

    // ================= 创建 / 更新 / 删除 =================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlashcardResponse create(String userId, FlashcardCreateRequest request) {
        requireOwnedSubject(userId, request.getSubjectId());
        requireChapterInSubject(request.getChapterId(), request.getSubjectId());

        LocalDate today = LocalDate.now();
        Flashcard card = new Flashcard();
        card.setUserId(userId);
        card.setSubjectId(request.getSubjectId());
        card.setChapterId(request.getChapterId());
        card.setFront(request.getFront());
        card.setBack(request.getBack());
        card.setEaseFactor(Sm2Scheduler.EF_DEFAULT);
        card.setIntervalDays(0);
        card.setRepetition(0);
        card.setLapses(0);
        card.setDueDate(today);
        card.setStatus(Flashcard.STATUS_ACTIVE);
        flashcardMapper.insert(card);
        log.info("创建闪卡 user={}, subject={}", userId, request.getSubjectId());
        return FlashcardResponse.from(card);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlashcardResponse update(String userId, String cardId, FlashcardUpdateRequest request) {
        Flashcard card = loadOwned(userId, cardId);
        if (request.getSubjectId() != null && !request.getSubjectId().equals(card.getSubjectId())) {
            requireOwnedSubject(userId, request.getSubjectId());
            card.setSubjectId(request.getSubjectId());
            // 换科目后原章节不再适用, 要求一并传新 chapterId(不传则挂空)
            card.setChapterId(null);
        }
        if (request.getChapterId() != null) {
            requireChapterInSubject(request.getChapterId(), card.getSubjectId());
            card.setChapterId(request.getChapterId());
        }
        if (request.getFront() != null && !request.getFront().isBlank()) {
            card.setFront(request.getFront());
        }
        if (request.getBack() != null && !request.getBack().isBlank()) {
            card.setBack(request.getBack());
        }
        flashcardMapper.updateById(card);
        return FlashcardResponse.from(card);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String userId, String cardId) {
        Flashcard card = loadOwned(userId, cardId);
        // 物理删除; card_review_logs FK ON DELETE CASCADE 连带删日志
        flashcardMapper.deleteById(card.getId());
        log.info("删除闪卡 user={}, card={}", userId, cardId);
    }

    // ================= 复习队列 =================

    @Override
    public FlashcardQueueResponse queue(String userId, String subjectId, Integer limit) {
        int size = (limit == null || limit <= 0)
                ? AppConstant.REVIEW_QUEUE_DEFAULT_LIMIT
                : Math.min(limit, QUEUE_MAX_LIMIT);
        LocalDate today = LocalDate.now();

        List<Flashcard> cards = flashcardMapper.selectList(new LambdaQueryWrapper<Flashcard>()
                .eq(Flashcard::getUserId, userId)
                .eq(Flashcard::getStatus, Flashcard.STATUS_ACTIVE)
                .eq(subjectId != null, Flashcard::getSubjectId, subjectId)
                .le(Flashcard::getDueDate, today)
                .orderByAsc(Flashcard::getDueDate)
                .orderByAsc(Flashcard::getCreatedAt)
                .last("LIMIT " + size));

        Long dueTotal = flashcardMapper.selectCount(dueScope(userId, subjectId, today));
        Long newTotal = flashcardMapper.selectCount(dueScope(userId, subjectId, today)
                .eq(Flashcard::getRepetition, 0));

        return FlashcardQueueResponse.builder()
                .cards(cards.stream().map(FlashcardResponse::from).toList())
                .dueTotal(dueTotal.intValue())
                .newTotal(newTotal.intValue())
                .build();
    }

    private LambdaQueryWrapper<Flashcard> dueScope(String userId, String subjectId, LocalDate today) {
        return new LambdaQueryWrapper<Flashcard>()
                .eq(Flashcard::getUserId, userId)
                .eq(Flashcard::getStatus, Flashcard.STATUS_ACTIVE)
                .eq(subjectId != null, Flashcard::getSubjectId, subjectId)
                .le(Flashcard::getDueDate, today);
    }

    // ================= 评分 =================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlashcardReviewResultResponse review(String userId, String cardId, Integer rating) {
        if (rating == null || !VALID_RATINGS.contains(rating)) {
            throw new BusinessException(ErrorCode.REVIEW_RATING_INVALID);
        }
        Flashcard card = loadOwned(userId, cardId);

        LocalDate today = LocalDate.now();
        BigDecimal easeBefore = card.getEaseFactor();
        int intervalBefore = card.getIntervalDays() == null ? 0 : card.getIntervalDays();

        Sm2Scheduler.State after = Sm2Scheduler.next(rating,
                new Sm2Scheduler.State(easeBefore, intervalBefore,
                        card.getRepetition() == null ? 0 : card.getRepetition(),
                        card.getLapses() == null ? 0 : card.getLapses(),
                        card.getDueDate()),
                today);

        card.setEaseFactor(after.easeFactor());
        card.setIntervalDays(after.intervalDays());
        card.setRepetition(after.repetition());
        card.setLapses(after.lapses());
        card.setDueDate(after.dueDate());
        card.setLastReviewedAt(LocalDateTime.now());
        flashcardMapper.updateById(card);

        CardReviewLog reviewLog = new CardReviewLog();
        reviewLog.setCardId(card.getId());
        reviewLog.setUserId(userId);
        reviewLog.setRating(rating);
        reviewLog.setIntervalBefore(intervalBefore);
        reviewLog.setIntervalAfter(after.intervalDays());
        reviewLog.setEaseBefore(easeBefore);
        reviewLog.setEaseAfter(after.easeFactor());
        cardReviewLogMapper.insert(reviewLog);

        Long reviewedTodayCount = cardReviewLogMapper.selectCount(todayLogScope(userId, today));
        int reviewedToday = reviewedTodayCount == null ? 0 : reviewedTodayCount.intValue();
        int rewardPoints = grantDailyRewardIfNeeded(userId, today, reviewedToday);

        return FlashcardReviewResultResponse.builder()
                .intervalDays(after.intervalDays())
                .easeFactor(after.easeFactor())
                .dueDate(after.dueDate())
                .reviewedToday(reviewedToday)
                .rewardPoints(rewardPoints)
                .build();
    }

    /**
     * 当日累计复习 ≥ REVIEW_REWARD_MIN_CARDS 且 Redis 标记首次设置成功 → 发一次 +10。
     *
     * <p>与限流相反这里对 Redis 异常 fail-closed(不发分): 标记读不到时发分可能当日重复发放。
     */
    private int grantDailyRewardIfNeeded(String userId, LocalDate today, int reviewedToday) {
        if (reviewedToday < AppConstant.REVIEW_REWARD_MIN_CARDS) {
            return 0;
        }
        String key = "habitforge:study:review:reward:" + userId + ":" + today;
        try {
            boolean firstTime = redisUtil.setIfAbsent(key, "1", Duration.ofHours(48));
            if (!firstTime) {
                return 0;
            }
            userService.addPoints(userId, AppConstant.POINTS_PER_DAILY_REVIEW);
            log.info("每日复习达标奖励 user={}, reviewedToday={}, points={}",
                    userId, reviewedToday, AppConstant.POINTS_PER_DAILY_REVIEW);
            return AppConstant.POINTS_PER_DAILY_REVIEW;
        } catch (Exception e) {
            log.warn("复习奖励标记 Redis 异常, 本次不发分: {}", e.getMessage());
            return 0;
        }
    }

    /** 当日已复习张数(含本次, 事务内已插入日志) */
    private LambdaQueryWrapper<CardReviewLog> todayLogScope(String userId, LocalDate today) {
        return new LambdaQueryWrapper<CardReviewLog>()
                .eq(CardReviewLog::getUserId, userId)
                .ge(CardReviewLog::getReviewedAt, today.atStartOfDay());
    }

    // ================= 统计 =================

    @Override
    public FlashcardStatsResponse stats(String userId) {
        LocalDate today = LocalDate.now();
        Long dueToday = flashcardMapper.selectCount(new LambdaQueryWrapper<Flashcard>()
                .eq(Flashcard::getUserId, userId)
                .eq(Flashcard::getStatus, Flashcard.STATUS_ACTIVE)
                .le(Flashcard::getDueDate, today));
        Long total = flashcardMapper.selectCount(new LambdaQueryWrapper<Flashcard>()
                .eq(Flashcard::getUserId, userId)
                .eq(Flashcard::getStatus, Flashcard.STATUS_ACTIVE));
        Long reviewed = cardReviewLogMapper.selectCount(todayLogScope(userId, today));

        // 未来 7 天一次 GROUP BY, 缺日补 0
        LocalDate from = today.plusDays(1);
        LocalDate to = today.plusDays(7);
        Map<LocalDate, Long> byDate = flashcardMapper.selectMaps(new QueryWrapper<Flashcard>()
                        .select("due_date AS dueDate", "COUNT(*) AS cnt")
                        .eq("user_id", userId)
                        .eq("status", Flashcard.STATUS_ACTIVE)
                        .between("due_date", from, to)
                        .groupBy("due_date"))
                .stream()
                .collect(Collectors.toMap(
                        m -> toLocalDate(m.get("dueDate")),
                        m -> ((Number) m.get("cnt")).longValue()));
        List<FlashcardStatsResponse.DayCount> next7Days = java.util.stream.IntStream.rangeClosed(1, 7)
                .mapToObj(i -> {
                    LocalDate d = today.plusDays(i);
                    return FlashcardStatsResponse.DayCount.builder()
                            .date(d)
                            .count(byDate.getOrDefault(d, 0L).intValue())
                            .build();
                })
                .toList();

        return FlashcardStatsResponse.builder()
                .dueToday(dueToday.intValue())
                .reviewedToday(reviewed.intValue())
                .total(total.intValue())
                .next7Days(next7Days)
                .build();
    }

    // ================= 私有方法 =================

    /** selectMaps 的 DATE 列驱动返回类型不一(java.sql.Date/LocalDate/String), 统一转 LocalDate */
    static LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate ld) {
            return ld;
        }
        if (value instanceof java.sql.Date sd) {
            return sd.toLocalDate();
        }
        return LocalDate.parse(String.valueOf(value));
    }

    /** 闪卡归属校验(不存在或非本人 → 7004) */
    private Flashcard loadOwned(String userId, String cardId) {
        Flashcard card = flashcardMapper.selectById(cardId);
        if (card == null || !card.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FLASHCARD_NOT_FOUND);
        }
        return card;
    }

    private Subject requireOwnedSubject(String userId, String subjectId) {
        Subject subject = subjectMapper.selectById(subjectId);
        if (subject == null || !subject.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.SUBJECT_NOT_FOUND);
        }
        return subject;
    }

    /** 章节可选; 传了则必须存在且属于该科目(否则 7002) */
    private void requireChapterInSubject(String chapterId, String subjectId) {
        if (chapterId == null) {
            return;
        }
        Chapter chapter = chapterMapper.selectById(chapterId);
        if (chapter == null || !chapter.getSubjectId().equals(subjectId)) {
            throw new BusinessException(ErrorCode.CHAPTER_NOT_FOUND);
        }
    }
}
