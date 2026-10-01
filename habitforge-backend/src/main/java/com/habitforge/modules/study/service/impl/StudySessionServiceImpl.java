package com.habitforge.modules.study.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.habitforge.common.constant.AppConstant;
import com.habitforge.common.exception.BusinessException;
import com.habitforge.common.exception.ErrorCode;
import com.habitforge.common.util.RedisUtil;
import com.habitforge.modules.study.dto.DailyStudyTimeResponse;
import com.habitforge.modules.study.dto.StudySessionManualRequest;
import com.habitforge.modules.study.dto.StudySessionResponse;
import com.habitforge.modules.study.dto.StudySessionStartRequest;
import com.habitforge.modules.study.dto.StudyTimeSummaryResponse;
import com.habitforge.modules.study.entity.Chapter;
import com.habitforge.modules.study.entity.StudySession;
import com.habitforge.modules.study.entity.Subject;
import com.habitforge.modules.study.mapper.ChapterMapper;
import com.habitforge.modules.study.mapper.StudySessionMapper;
import com.habitforge.modules.study.mapper.SubjectMapper;
import com.habitforge.modules.study.service.StudySessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudySessionServiceImpl implements StudySessionService {

    private final StudySessionMapper sessionMapper;
    private final SubjectMapper subjectMapper;
    private final ChapterMapper chapterMapper;
    private final RedisUtil redisUtil;

    // ================= 计时 =================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StudySessionResponse start(String userId, StudySessionStartRequest request) {
        rateLimit(userId);
        if (findRunning(userId) != null) {
            throw new BusinessException(ErrorCode.STUDY_SESSION_RUNNING);
        }
        validateRefs(userId, request.getSubjectId(), request.getChapterId());

        LocalDateTime now = LocalDateTime.now();
        StudySession session = new StudySession();
        session.setUserId(userId);
        session.setSubjectId(blankToNull(request.getSubjectId()));
        session.setChapterId(blankToNull(request.getChapterId()));
        session.setSessionDate(now.toLocalDate());
        session.setStartedAt(now);
        session.setSource(StudySession.SOURCE_TIMER);
        sessionMapper.insert(session);

        log.info("学习计时开始 user={}, sessionId={}", userId, session.getId());
        return toResponses(List.of(session)).get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StudySessionResponse end(String userId, String sessionId) {
        rateLimit(userId);
        StudySession session = getOwned(userId, sessionId);
        if (!session.isRunning()) {
            throw new BusinessException(ErrorCode.STUDY_SESSION_NOT_RUNNING);
        }

        LocalDateTime now = LocalDateTime.now();
        long seconds = Duration.between(session.getStartedAt(), now).getSeconds();
        int minutes = (int) Math.max(1L, Math.round(seconds / 60.0));
        if (minutes > AppConstant.STUDY_SESSION_MAX_MINUTES) {
            throw new BusinessException(ErrorCode.STUDY_SESSION_TIME_INVALID,
                    "本次计时已超过 " + (AppConstant.STUDY_SESSION_MAX_MINUTES / 60)
                            + " 小时，请删除该计时并用手动补录填写真实时长");
        }

        session.setEndedAt(now);
        session.setMinutes(minutes);
        sessionMapper.updateById(session);

        log.info("学习计时结束 user={}, sessionId={}, minutes={}", userId, sessionId, minutes);
        return toResponses(List.of(session)).get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StudySessionResponse manual(String userId, StudySessionManualRequest request) {
        rateLimit(userId);
        LocalDate date = request.getSessionDate() != null ? request.getSessionDate() : LocalDate.now();
        if (date.isAfter(LocalDate.now())) {
            throw new BusinessException(ErrorCode.STUDY_SESSION_TIME_INVALID, "不能补录未来的学习时长");
        }
        Integer minutes = request.getMinutes();
        if (minutes == null || minutes < 1 || minutes > AppConstant.STUDY_SESSION_MAX_MINUTES) {
            throw new BusinessException(ErrorCode.STUDY_SESSION_TIME_INVALID,
                    "单次时长需在 1-" + AppConstant.STUDY_SESSION_MAX_MINUTES + " 分钟之间");
        }
        validateRefs(userId, request.getSubjectId(), request.getChapterId());

        // 补录无真实时刻: startedAt 取当天 00:00 占位, 语义只有 sessionDate + minutes
        StudySession session = new StudySession();
        session.setUserId(userId);
        session.setSubjectId(blankToNull(request.getSubjectId()));
        session.setChapterId(blankToNull(request.getChapterId()));
        session.setSessionDate(date);
        session.setStartedAt(date.atStartOfDay());
        session.setEndedAt(date.atStartOfDay().plusMinutes(minutes));
        session.setMinutes(minutes);
        session.setSource(StudySession.SOURCE_MANUAL);
        session.setNote(request.getNote());
        sessionMapper.insert(session);

        log.info("学习时长补录 user={}, date={}, minutes={}", userId, date, minutes);
        return toResponses(List.of(session)).get(0);
    }

    @Override
    public StudySessionResponse active(String userId) {
        StudySession running = findRunning(userId);
        return running == null ? null : toResponses(List.of(running)).get(0);
    }

    @Override
    public List<StudySessionResponse> listByDate(String userId, LocalDate date) {
        LocalDate target = date != null ? date : LocalDate.now();
        List<StudySession> sessions = sessionMapper.selectList(new LambdaQueryWrapper<StudySession>()
                .eq(StudySession::getUserId, userId)
                .eq(StudySession::getSessionDate, target)
                .orderByAsc(StudySession::getStartedAt));
        return toResponses(sessions);
    }

    @Override
    public void delete(String userId, String sessionId) {
        StudySession session = getOwned(userId, sessionId);
        sessionMapper.deleteById(session.getId());
        log.info("学习记录删除 user={}, sessionId={}", userId, sessionId);
    }

    // ================= 聚合 =================

    @Override
    public StudyTimeSummaryResponse summary(String userId, LocalDate date) {
        LocalDate target = date != null ? date : LocalDate.now();
        List<StudySession> sessions = finished(userId, target, target);
        return StudyTimeSummaryResponse.builder()
                .date(target)
                .minutes(sumMinutes(sessions))
                .sessionCount(sessions.size())
                .build();
    }

    @Override
    public List<DailyStudyTimeResponse> daily(String userId, LocalDate from, LocalDate to) {
        validateRange(from, to);
        return dailyInternal(userId, from, to);
    }

    @Override
    public List<DailyStudyTimeResponse> dailyForReport(String userId, LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) {
            return List.of();
        }
        return dailyInternal(userId, from, to);
    }

    @Override
    public List<StudySessionResponse> listByRange(String userId, LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) {
            return List.of();
        }
        return toResponses(finished(userId, from, to));
    }

    // ================= 内部 =================

    private List<DailyStudyTimeResponse> dailyInternal(String userId, LocalDate from, LocalDate to) {
        Map<LocalDate, List<StudySession>> grouped = finished(userId, from, to).stream()
                .collect(Collectors.groupingBy(StudySession::getSessionDate));
        List<DailyStudyTimeResponse> result = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            List<StudySession> day = grouped.getOrDefault(d, List.of());
            result.add(DailyStudyTimeResponse.builder()
                    .date(d)
                    .minutes(sumMinutes(day))
                    .sessionCount(day.size())
                    .build());
        }
        return result;
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请提供 from/to 参数");
        }
        if (from.isAfter(to)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "开始日期不能晚于结束日期");
        }
        if (ChronoUnit.DAYS.between(from, to) > AppConstant.STUDY_TIME_MAX_RANGE_DAYS) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "查询区间最长 " + AppConstant.STUDY_TIME_MAX_RANGE_DAYS + " 天");
        }
    }

    /** 区间内已结束的记录（进行中的不计入时长, 避免统计跳变） */
    private List<StudySession> finished(String userId, LocalDate from, LocalDate to) {
        return sessionMapper.selectList(new LambdaQueryWrapper<StudySession>()
                .eq(StudySession::getUserId, userId)
                .between(StudySession::getSessionDate, from, to)
                .isNotNull(StudySession::getEndedAt)
                .orderByAsc(StudySession::getStartedAt));
    }

    private StudySession findRunning(String userId) {
        return sessionMapper.selectOne(new LambdaQueryWrapper<StudySession>()
                .eq(StudySession::getUserId, userId)
                .isNull(StudySession::getEndedAt)
                .orderByDesc(StudySession::getStartedAt)
                .last("LIMIT 1"));
    }

    private StudySession getOwned(String userId, String sessionId) {
        StudySession session = sessionMapper.selectById(sessionId);
        if (session == null || !session.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.STUDY_SESSION_NOT_FOUND);
        }
        return session;
    }

    private void validateRefs(String userId, String subjectId, String chapterId) {
        if (StrUtil.isNotBlank(subjectId)) {
            Subject subject = subjectMapper.selectById(subjectId);
            if (subject == null || !subject.getUserId().equals(userId)) {
                throw new BusinessException(ErrorCode.SUBJECT_NOT_FOUND);
            }
        }
        if (StrUtil.isNotBlank(chapterId)) {
            Chapter chapter = chapterMapper.selectById(chapterId);
            // 章节无 userId, 归属沿科目校验
            Subject owner = chapter == null ? null : subjectMapper.selectById(chapter.getSubjectId());
            if (owner == null || !owner.getUserId().equals(userId)) {
                throw new BusinessException(ErrorCode.CHAPTER_NOT_FOUND);
            }
        }
    }

    /** 批量补名字, 避免 N+1 */
    private List<StudySessionResponse> toResponses(List<StudySession> sessions) {
        if (sessions.isEmpty()) {
            return List.of();
        }
        Set<String> subjectIds = sessions.stream().map(StudySession::getSubjectId)
                .filter(StrUtil::isNotBlank).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> chapterIds = sessions.stream().map(StudySession::getChapterId)
                .filter(StrUtil::isNotBlank).collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, String> subjectNames = subjectIds.isEmpty() ? Map.of()
                : subjectMapper.selectBatchIds(subjectIds).stream()
                .collect(Collectors.toMap(Subject::getId, Subject::getName, (a, b) -> a));
        Map<String, String> chapterNames = chapterIds.isEmpty() ? Map.of()
                : chapterMapper.selectBatchIds(chapterIds).stream()
                .collect(Collectors.toMap(Chapter::getId, Chapter::getName, (a, b) -> a));

        return sessions.stream()
                .map(s -> StudySessionResponse.of(s,
                        nameOf(subjectNames, s.getSubjectId()),
                        nameOf(chapterNames, s.getChapterId())))
                .toList();
    }

    private static String nameOf(Map<String, String> names, String id) {
        return id == null ? null : names.get(id);
    }

    private static int sumMinutes(List<StudySession> sessions) {
        return sessions.stream().mapToInt(s -> s.getMinutes() == null ? 0 : s.getMinutes()).sum();
    }

    private static String blankToNull(String value) {
        return StrUtil.isBlank(value) ? null : value;
    }

    /** 写入限流（Redis 故障 fail-open, 沿项目惯例） */
    private void rateLimit(String userId) {
        try {
            if (!redisUtil.tryAcquire("study:" + userId, AppConstant.STUDY_SESSION_RATE_LIMIT, Duration.ofMinutes(1))) {
                throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("限流组件不可用, 放行: {}", e.getMessage());
        }
    }
}
