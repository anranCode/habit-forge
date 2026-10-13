package com.habitforge.modules.study.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.habitforge.common.exception.BusinessException;
import com.habitforge.common.exception.ErrorCode;
import com.habitforge.modules.ai.dto.ShortGradeOutcome;
import com.habitforge.modules.ai.service.ShortGradeService;
import com.habitforge.modules.study.dto.GradeRequest;
import com.habitforge.modules.study.dto.GradeResponse;
import com.habitforge.modules.study.entity.Question;
import com.habitforge.modules.study.entity.WrongQuestion;
import com.habitforge.modules.study.mapper.QuestionMapper;
import com.habitforge.modules.study.mapper.WrongQuestionMapper;
import com.habitforge.modules.study.service.QuestionGradeService;
import com.habitforge.modules.study.service.WrongQuestionService;
import com.habitforge.modules.study.service.support.AnswerMatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 判分编排：分流 → 判 → 副作用 → 回显。
 * <p>
 * 不套大事务：副作用各自是 add()/practice() 的单行原子操作，
 * 中间失败最坏多计一次数（重答重判的既有语义），不值得为它把回显也捆进去。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionGradeServiceImpl implements QuestionGradeService {

    private final QuestionMapper questionMapper;
    private final WrongQuestionMapper wrongQuestionMapper;
    private final WrongQuestionService wrongQuestionService;
    private final ShortGradeService shortGradeService;

    @Override
    public GradeResponse grade(String userId, GradeRequest request) {
        Question question = loadOwned(userId, request.getQuestionId());
        String answer = request.getAnswer().trim();

        boolean correct;
        GradeResponse.GradeResponseBuilder builder = GradeResponse.builder()
                .questionId(question.getId())
                .questionType(question.getQuestionType())
                .standardAnswer(question.getAnswer())
                .analysis(question.getAnalysis());

        GradeResponse.AiGrade aiGrade = null;
        String gradeId = null;
        if ("SHORT".equals(question.getQuestionType())) {
            // AI 拆要点判 hit + question_grades 留痕; 对错在服务端按阈值换算。
            // 抛 6001/6003/6004/6005/7014 时执行流在副作用之前中断 —— "未判分≠答错", 错题本绝不沾。
            ShortGradeOutcome outcome = shortGradeService.grade(userId, question, answer);
            correct = outcome.correct();
            aiGrade = GradeResponse.AiGrade.builder()
                    .points(outcome.points().stream()
                            .map(p -> GradeResponse.PointHit.builder().point(p.point()).hit(p.hit()).build())
                            .toList())
                    .covered(outcome.covered())
                    .total(outcome.total())
                    .ratio(outcome.ratio())
                    .build();
            gradeId = outcome.gradeId();
        } else {
            correct = AnswerMatcher.match(question.getQuestionType(), question.getAnswer(), answer);
        }

        applyWrongBookSideEffect(userId, question.getId(), correct);
        GradeResponse.WrongBook wrongBook = readWrongBook(userId, question.getId());

        log.info("判分 user={}, question={}, type={}, correct={}", userId, question.getId(),
                question.getQuestionType(), correct);
        return builder.correct(correct).aiGrade(aiGrade).gradeId(gradeId).wrongBook(wrongBook).build();
    }

    /** 答错入本(重复答错 count+1 由 upsert 承担); 答对且在本未摘才走 practice, 不在本则无副作用 */
    private void applyWrongBookSideEffect(String userId, String questionId, boolean correct) {
        if (!correct) {
            wrongQuestionService.add(userId, questionId);
            return;
        }
        WrongQuestion row = findRow(userId, questionId);
        if (row != null && row.getMastered() == 0) {
            wrongQuestionService.practice(userId, questionId, true);
        }
    }

    /** 副作用落定后的回显状态；无行 → null(答对且不在本) */
    private GradeResponse.WrongBook readWrongBook(String userId, String questionId) {
        WrongQuestion row = findRow(userId, questionId);
        if (row == null) {
            return null;
        }
        return GradeResponse.WrongBook.builder()
                .inBook(true)
                .wrongCount(row.getWrongCount())
                .correctStreak(row.getCorrectStreak())
                .mastered(row.getMastered() == 1)
                .build();
    }

    private WrongQuestion findRow(String userId, String questionId) {
        return wrongQuestionMapper.selectOne(new LambdaQueryWrapper<WrongQuestion>()
                .eq(WrongQuestion::getUserId, userId)
                .eq(WrongQuestion::getQuestionId, questionId)
                .last("LIMIT 1"));
    }

    /** 归属校验(与 QuestionServiceImpl.loadOwned 同语义, 7007) */
    private Question loadOwned(String userId, String questionId) {
        Question question = questionMapper.selectById(questionId);
        if (question == null || !question.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.QUESTION_NOT_FOUND);
        }
        return question;
    }
}
