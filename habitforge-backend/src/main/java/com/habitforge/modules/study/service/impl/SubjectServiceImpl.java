package com.habitforge.modules.study.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.habitforge.common.exception.BusinessException;
import com.habitforge.common.exception.ErrorCode;
import com.habitforge.modules.study.dto.SubjectCreateRequest;
import com.habitforge.modules.study.dto.SubjectResponse;
import com.habitforge.modules.study.dto.SubjectUpdateRequest;
import com.habitforge.modules.study.entity.Chapter;
import com.habitforge.modules.study.entity.Flashcard;
import com.habitforge.modules.study.entity.Question;
import com.habitforge.modules.study.entity.Subject;
import com.habitforge.modules.study.entity.WrongQuestion;
import com.habitforge.modules.study.mapper.ChapterMapper;
import com.habitforge.modules.study.mapper.FlashcardMapper;
import com.habitforge.modules.study.mapper.QuestionMapper;
import com.habitforge.modules.study.mapper.SubjectMapper;
import com.habitforge.modules.study.mapper.WrongQuestionMapper;
import com.habitforge.modules.study.service.SubjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubjectServiceImpl implements SubjectService {

    private static final String STATUS_DONE = "DONE";

    private final SubjectMapper subjectMapper;
    private final ChapterMapper chapterMapper;
    private final FlashcardMapper flashcardMapper;
    private final QuestionMapper questionMapper;
    private final WrongQuestionMapper wrongQuestionMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SubjectResponse create(String userId, SubjectCreateRequest request) {
        Subject subject = new Subject();
        subject.setUserId(userId);
        subject.setName(request.getName());
        subject.setExamDate(request.getExamDate());
        subject.setExamSession(request.getExamSession());
        subject.setDescription(request.getDescription());
        subject.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        subject.setIsActive(1);
        subject.setDeleted(0);
        subjectMapper.insert(subject);
        log.info("创建科目 user={}, name={}", userId, subject.getName());
        // 新建科目章节数必为 0, 免于回查
        return SubjectResponse.from(subject);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SubjectResponse update(String userId, String subjectId, SubjectUpdateRequest request) {
        Subject subject = loadOwned(userId, subjectId);
        if (request.getName() != null && !request.getName().isBlank()) {
            subject.setName(request.getName());
        }
        if (request.getExamDate() != null) {
            subject.setExamDate(request.getExamDate());
        }
        if (request.getExamSession() != null) {
            subject.setExamSession(request.getExamSession());
        }
        if (request.getDescription() != null) {
            subject.setDescription(request.getDescription());
        }
        if (request.getSortOrder() != null) {
            subject.setSortOrder(request.getSortOrder());
        }
        subjectMapper.updateById(subject);
        SubjectResponse resp = buildResponse(subject, countChapters(List.of(subjectId), false), countChapters(List.of(subjectId), true));
        fillStudyCounts(userId, List.of(resp));
        return resp;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String userId, String subjectId) {
        Subject subject = loadOwned(userId, subjectId);
        subjectMapper.deleteById(subject.getId()); // @TableLogic 逻辑删除
        log.info("删除科目 user={}, subject={}", userId, subject.getName());
    }

    @Override
    public SubjectResponse getDetail(String userId, String subjectId) {
        Subject subject = loadOwned(userId, subjectId);
        SubjectResponse resp = buildResponse(subject, countChapters(List.of(subjectId), false), countChapters(List.of(subjectId), true));
        fillStudyCounts(userId, List.of(resp));
        return resp;
    }

    @Override
    public List<SubjectResponse> listMine(String userId) {
        List<Subject> subjects = subjectMapper.selectList(new LambdaQueryWrapper<Subject>()
                .eq(Subject::getUserId, userId)
                .orderByAsc(Subject::getSortOrder)
                .orderByAsc(Subject::getCreatedAt));
        if (subjects.isEmpty()) {
            return List.of();
        }
        List<String> ids = subjects.stream().map(Subject::getId).toList();
        // 两次 GROUP BY 聚合(subject_id IN + status='DONE'), 避免逐科目 N+1
        Map<String, Long> totals = countChapters(ids, false);
        Map<String, Long> dones = countChapters(ids, true);
        List<SubjectResponse> responses = subjects.stream()
                .map(s -> buildResponse(s, totals, dones))
                .toList();
        fillStudyCounts(userId, responses);
        return responses;
    }

    // ================= 私有方法 =================

    /**
     * 填充到期闪卡数/待复习错题数(P1, 固定三条查询不随科目数 N+1):
     * 1) flashcards 按 subject_id GROUP BY; 2) questions 取 id→subject 映射; 3) wrong_questions 按 question GROUP BY 后内存归并到科目
     */
    private void fillStudyCounts(String userId, List<SubjectResponse> responses) {
        if (responses.isEmpty()) {
            return;
        }
        List<String> subjectIds = responses.stream().map(SubjectResponse::getId).toList();
        LocalDate today = LocalDate.now();

        Map<String, Long> dueCards = flashcardMapper.selectMaps(new QueryWrapper<Flashcard>()
                        .select("subject_id AS subjectId", "COUNT(*) AS cnt")
                        .eq("user_id", userId)
                        .eq("status", Flashcard.STATUS_ACTIVE)
                        .le("due_date", today)
                        .in("subject_id", subjectIds)
                        .groupBy("subject_id"))
                .stream()
                .collect(Collectors.toMap(
                        m -> String.valueOf(m.get("subjectId")),
                        m -> ((Number) m.get("cnt")).longValue()));

        Map<String, String> subjectOfQuestion = new HashMap<>();
        questionMapper.selectMaps(new QueryWrapper<Question>()
                        .select("id AS qid", "subject_id AS subjectId")
                        .eq("user_id", userId)
                        .in("subject_id", subjectIds))
                .forEach(m -> subjectOfQuestion.put(String.valueOf(m.get("qid")), String.valueOf(m.get("subjectId"))));
        Map<String, Long> wrongs = new HashMap<>();
        for (Map<String, Object> m : wrongQuestionMapper.selectMaps(new QueryWrapper<WrongQuestion>()
                .select("question_id AS qid", "COUNT(*) AS cnt")
                .eq("user_id", userId)
                .eq("mastered", 0)
                .groupBy("question_id"))) {
            String subjectId = subjectOfQuestion.get(String.valueOf(m.get("qid")));
            if (subjectId != null) {
                wrongs.merge(subjectId, ((Number) m.get("cnt")).longValue(), Long::sum);
            }
        }

        for (SubjectResponse resp : responses) {
            resp.setDueCards(dueCards.getOrDefault(resp.getId(), 0L).intValue());
            resp.setWrongCount(wrongs.getOrDefault(resp.getId(), 0L).intValue());
        }
    }

    private Subject loadOwned(String userId, String subjectId) {
        Subject subject = subjectMapper.selectById(subjectId);
        if (subject == null || !subject.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.SUBJECT_NOT_FOUND);
        }
        return subject;
    }

    /** 按科目 GROUP BY 计数: doneOnly=true 时只统计 DONE 章节 */
    private Map<String, Long> countChapters(List<String> subjectIds, boolean doneOnly) {
        if (subjectIds.isEmpty()) {
            return Map.of();
        }
        QueryWrapper<Chapter> qw = new QueryWrapper<Chapter>()
                .select("subject_id AS subjectId", "COUNT(*) AS cnt")
                .in("subject_id", subjectIds)
                .groupBy("subject_id");
        if (doneOnly) {
            qw.eq("status", STATUS_DONE);
        }
        return chapterMapper.selectMaps(qw).stream()
                .collect(Collectors.toMap(
                        m -> String.valueOf(m.get("subjectId")),
                        m -> ((Number) m.get("cnt")).longValue()));
    }

    private SubjectResponse buildResponse(Subject subject, Map<String, Long> totals, Map<String, Long> dones) {
        SubjectResponse resp = SubjectResponse.from(subject);
        resp.setChapterTotal(totals.getOrDefault(subject.getId(), 0L).intValue());
        resp.setChapterDone(dones.getOrDefault(subject.getId(), 0L).intValue());
        return resp;
    }
}
