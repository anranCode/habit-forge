package com.habitforge.modules.study.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.habitforge.common.constant.AppConstant;
import com.habitforge.common.exception.BusinessException;
import com.habitforge.common.exception.ErrorCode;
import com.habitforge.modules.study.dto.QuestionOptionDTO;
import com.habitforge.modules.study.dto.QuestionResponse;
import com.habitforge.modules.study.dto.WrongQuestionResponse;
import com.habitforge.modules.study.entity.Question;
import com.habitforge.modules.study.entity.WrongQuestion;
import com.habitforge.modules.study.mapper.QuestionMapper;
import com.habitforge.modules.study.mapper.WrongQuestionMapper;
import com.habitforge.modules.study.service.WrongQuestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WrongQuestionServiceImpl implements WrongQuestionService {

    /** 错题列表默认上限 */
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final WrongQuestionMapper wrongQuestionMapper;
    private final QuestionMapper questionMapper;

    // ================= 加入错题本(upsert) =================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WrongQuestionResponse add(String userId, String questionId) {
        Question question = loadOwnedQuestion(userId, questionId);

        LocalDateTime now = LocalDateTime.now();
        WrongQuestion wrong = new WrongQuestion();
        wrong.setUserId(userId);
        wrong.setQuestionId(questionId);
        wrong.setWrongCount(1);
        wrong.setCorrectStreak(0);
        wrong.setMastered(0);
        wrong.setLastWrongAt(now);
        try {
            wrongQuestionMapper.insert(wrong);
        } catch (DuplicateKeyException e) {
            // uk_user_question 冲突 → 累加而不是报错
            wrong = loadRow(userId, questionId);
            wrong.setWrongCount(wrong.getWrongCount() + 1);
            wrong.setCorrectStreak(0);
            wrong.setMastered(0);
            wrong.setLastWrongAt(now);
            wrongQuestionMapper.updateById(wrong);
            log.info("错题重复加入, 累加 user={}, question={}, wrongCount={}", userId, questionId, wrong.getWrongCount());
        }
        return buildResponse(wrong, question);
    }

    // ================= 列表 =================

    @Override
    public List<WrongQuestionResponse> list(String userId, String subjectId, Integer limit) {
        int size = (limit == null || limit <= 0)
                ? DEFAULT_LIMIT
                : Math.min(limit, MAX_LIMIT);

        List<String> questionIdFilter = null;
        if (subjectId != null && !subjectId.isBlank()) {
            // 科目过滤: 先取该科目题目 id(单查询), 错题表无 subject_id 不做 join
            questionIdFilter = questionMapper.selectMaps(new QueryWrapper<Question>()
                            .select("id")
                            .eq("user_id", userId)
                            .eq("subject_id", subjectId))
                    .stream().map(m -> String.valueOf(m.get("id"))).toList();
            if (questionIdFilter.isEmpty()) {
                return List.of();
            }
        }

        List<WrongQuestion> wrongs = wrongQuestionMapper.selectList(new LambdaQueryWrapper<WrongQuestion>()
                .eq(WrongQuestion::getUserId, userId)
                .eq(WrongQuestion::getMastered, 0)
                .in(questionIdFilter != null, WrongQuestion::getQuestionId,
                        questionIdFilter == null ? List.of() : questionIdFilter)
                .orderByDesc(WrongQuestion::getWrongCount)
                .orderByAsc(WrongQuestion::getLastWrongAt)
                .last("LIMIT " + size));
        if (wrongs.isEmpty()) {
            return List.of();
        }

        // 题目详情两次 IN 查询组装, 防 N+1
        List<String> ids = wrongs.stream().map(WrongQuestion::getQuestionId).toList();
        Map<String, Question> questionById = questionMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));
        return wrongs.stream()
                .filter(w -> questionById.containsKey(w.getQuestionId()))
                .map(w -> buildResponse(w, questionById.get(w.getQuestionId())))
                .toList();
    }

    // ================= 重练 / 手动摘除 =================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WrongQuestionResponse practice(String userId, String questionId, boolean correct) {
        WrongQuestion wrong = loadRow(userId, questionId);
        Question question = loadOwnedQuestion(userId, questionId);

        LocalDateTime now = LocalDateTime.now();
        wrong.setLastPracticedAt(now);
        if (correct) {
            int streak = wrong.getCorrectStreak() + 1;
            wrong.setCorrectStreak(streak);
            // 连对达阈值自动摘除(不删行)
            if (streak >= AppConstant.WRONG_MASTER_STREAK) {
                wrong.setMastered(1);
            }
        } else {
            wrong.setWrongCount(wrong.getWrongCount() + 1);
            wrong.setCorrectStreak(0);
            wrong.setMastered(0);
            wrong.setLastWrongAt(now);
        }
        wrongQuestionMapper.updateById(wrong);
        return buildResponse(wrong, question);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WrongQuestionResponse setMastered(String userId, String questionId, boolean mastered) {
        WrongQuestion wrong = loadRow(userId, questionId);
        wrong.setMastered(mastered ? 1 : 0);
        wrongQuestionMapper.updateById(wrong);
        return buildResponse(wrong, loadOwnedQuestion(userId, questionId));
    }

    // ================= 私有方法 =================

    /** 错题行归属校验(不存在 → 7008) */
    private WrongQuestion loadRow(String userId, String questionId) {
        WrongQuestion wrong = wrongQuestionMapper.selectOne(new LambdaQueryWrapper<WrongQuestion>()
                .eq(WrongQuestion::getUserId, userId)
                .eq(WrongQuestion::getQuestionId, questionId)
                .last("LIMIT 1"));
        if (wrong == null) {
            throw new BusinessException(ErrorCode.WRONG_NOT_FOUND);
        }
        return wrong;
    }

    private Question loadOwnedQuestion(String userId, String questionId) {
        Question question = questionMapper.selectById(questionId);
        if (question == null || !question.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.QUESTION_NOT_FOUND);
        }
        return question;
    }

    private WrongQuestionResponse buildResponse(WrongQuestion wrong, Question question) {
        List<QuestionOptionDTO> options = StrUtil.isBlank(question.getOptions())
                ? null
                : JSONUtil.toList(question.getOptions(), QuestionOptionDTO.class);
        return WrongQuestionResponse.from(wrong, QuestionResponse.from(question, options));
    }
}
