package com.habitforge.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.habitforge.common.exception.BusinessException;
import com.habitforge.modules.study.dto.GradeRequest;
import com.habitforge.modules.study.dto.GradeResponse;
import com.habitforge.modules.study.entity.Question;
import com.habitforge.modules.study.entity.WrongQuestion;
import com.habitforge.modules.study.mapper.QuestionMapper;
import com.habitforge.modules.study.mapper.WrongQuestionMapper;
import com.habitforge.modules.study.service.WrongQuestionService;
import com.habitforge.modules.study.service.impl.QuestionGradeServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 判分编排的副作用矩阵：答错入本 / 答对且在本未摘走 practice / 答对且不在本零副作用。
 * （AI 拆点、限流、流水属 P4 的 ShortGradeService，不在本测试射程）
 */
@ExtendWith(MockitoExtension.class)
class QuestionGradeServiceTest {

    @Mock
    private QuestionMapper questionMapper;
    @Mock
    private WrongQuestionMapper wrongQuestionMapper;
    @Mock
    private WrongQuestionService wrongQuestionService;

    @InjectMocks
    private QuestionGradeServiceImpl gradeService;

    private static final String USER_ID = "user-a";
    private static final String QID = "question-1";

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), WrongQuestion.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), Question.class);
    }

    private Question question(String type, String answer) {
        Question q = new Question();
        q.setId(QID);
        q.setUserId(USER_ID);
        q.setQuestionType(type);
        q.setStem("题干");
        q.setAnswer(answer);
        q.setAnalysis("解析");
        return q;
    }

    private WrongQuestion row(int wrongCount, int streak, int mastered) {
        WrongQuestion w = new WrongQuestion();
        w.setUserId(USER_ID);
        w.setQuestionId(QID);
        w.setWrongCount(wrongCount);
        w.setCorrectStreak(streak);
        w.setMastered(mastered);
        w.setLastWrongAt(LocalDateTime.now());
        return w;
    }

    private GradeRequest request(String answer) {
        GradeRequest r = new GradeRequest();
        r.setQuestionId(QID);
        r.setAnswer(answer);
        return r;
    }

    @Test
    void correct_notInBook_noSideEffectAndNullWrongBook() {
        when(questionMapper.selectById(QID)).thenReturn(question("SINGLE", "A"));
        when(wrongQuestionMapper.selectOne(any())).thenReturn(null);

        GradeResponse resp = gradeService.grade(USER_ID, request("a"));

        assertTrue(resp.isCorrect());
        assertNull(resp.getWrongBook());
        assertEquals("A", resp.getStandardAnswer());
        verify(wrongQuestionService, never()).add(anyString(), anyString());
        verify(wrongQuestionService, never()).practice(anyString(), anyString(), anyBoolean());
    }

    @Test
    void wrong_addsToBook_andEchoesState() {
        when(questionMapper.selectById(QID)).thenReturn(question("JUDGE", "T"));
        when(wrongQuestionMapper.selectOne(any())).thenReturn(row(1, 0, 0));

        GradeResponse resp = gradeService.grade(USER_ID, request("F"));

        assertFalse(resp.isCorrect());
        verify(wrongQuestionService).add(USER_ID, QID);
        assertNotNull(resp.getWrongBook());
        assertTrue(resp.getWrongBook().isInBook());
        assertFalse(resp.getWrongBook().isMastered());
    }

    @Test
    void multi_outOfOrderStoredAnswer_stillMatches() {
        // 存量答案手输乱序(风险备忘 1)：库里 "C,A"、用户 "A,C" 应判对
        when(questionMapper.selectById(QID)).thenReturn(question("MULTI", "C,A"));
        when(wrongQuestionMapper.selectOne(any())).thenReturn(null);

        assertTrue(gradeService.grade(USER_ID, request("A,C")).isCorrect());
    }

    @Test
    void correct_inBookNotMastered_practicesAndCanReportMastered() {
        when(questionMapper.selectById(QID)).thenReturn(question("SINGLE", "A"));
        // 副作用前查到未摘行, practice(mock 不真改)后回显同一行
        when(wrongQuestionMapper.selectOne(any())).thenReturn(row(3, 1, 0));

        GradeResponse resp = gradeService.grade(USER_ID, request("A"));

        assertTrue(resp.isCorrect());
        verify(wrongQuestionService).practice(USER_ID, QID, true);
        assertEquals(3, resp.getWrongBook().getWrongCount());
    }

    @Test
    void correct_alreadyMastered_skipsPractice() {
        when(questionMapper.selectById(QID)).thenReturn(question("SINGLE", "A"));
        when(wrongQuestionMapper.selectOne(any())).thenReturn(row(2, 2, 1));

        GradeResponse resp = gradeService.grade(USER_ID, request("A"));

        verify(wrongQuestionService, never()).practice(anyString(), anyString(), anyBoolean());
        assertTrue(resp.getWrongBook().isMastered());
    }

    @Test
    void short_rejectedUntilP4_andNoSideEffect() {
        when(questionMapper.selectById(QID)).thenReturn(question("SHORT", "参考答案"));

        assertThrows(BusinessException.class, () -> gradeService.grade(USER_ID, request("我的作答")));
        verify(wrongQuestionService, never()).add(anyString(), anyString());
    }

    @Test
    void othersQuestion_notFound() {
        when(questionMapper.selectById(QID)).thenReturn(question("SINGLE", "A"));
        // 换到别人的 user
        assertThrows(BusinessException.class, () -> gradeService.grade("user-b", request("A")));
    }
}
