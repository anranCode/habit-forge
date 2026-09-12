package com.habitforge.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.habitforge.common.exception.BusinessException;
import com.habitforge.modules.study.dto.WrongQuestionResponse;
import com.habitforge.modules.study.entity.Question;
import com.habitforge.modules.study.entity.WrongQuestion;
import com.habitforge.modules.study.mapper.QuestionMapper;
import com.habitforge.modules.study.mapper.WrongQuestionMapper;
import com.habitforge.modules.study.service.impl.WrongQuestionServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 错题本关键路径测试(upsert 累加/连对 2 次摘除/答错重置)
 */
@ExtendWith(MockitoExtension.class)
class WrongQuestionServiceImplTest {

    @Mock
    private WrongQuestionMapper wrongQuestionMapper;
    @Mock
    private QuestionMapper questionMapper;

    @InjectMocks
    private WrongQuestionServiceImpl wrongQuestionService;

    private static final String USER_ID = "user-a";
    private static final String QUESTION_ID = "question-1";

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), WrongQuestion.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), Question.class);
    }

    private Question question(String userId) {
        Question q = new Question();
        q.setId(QUESTION_ID);
        q.setUserId(userId);
        q.setSubjectId("subject-1");
        q.setQuestionType("SINGLE");
        q.setStem("题干");
        q.setAnswer("A");
        return q;
    }

    private WrongQuestion row(int wrongCount, int streak, int mastered) {
        WrongQuestion w = new WrongQuestion();
        w.setId("wrong-1");
        w.setUserId(USER_ID);
        w.setQuestionId(QUESTION_ID);
        w.setWrongCount(wrongCount);
        w.setCorrectStreak(streak);
        w.setMastered(mastered);
        w.setLastWrongAt(LocalDateTime.now());
        return w;
    }

    // ================= 加入错题本(upsert) =================

    @Test
    void add_firstTime_insertsRow() {
        when(questionMapper.selectById(QUESTION_ID)).thenReturn(question(USER_ID));
        when(wrongQuestionMapper.insert(any(WrongQuestion.class))).thenReturn(1);

        WrongQuestionResponse resp = wrongQuestionService.add(USER_ID, QUESTION_ID);

        assertEquals(1, resp.getWrongCount());
        assertEquals(0, resp.getCorrectStreak());
        assertFalse(resp.getMastered());
        assertEquals("题干", resp.getQuestion().getStem());
    }

    @Test
    void add_duplicate_accumulatesAndResets() {
        when(questionMapper.selectById(QUESTION_ID)).thenReturn(question(USER_ID));
        when(wrongQuestionMapper.insert(any(WrongQuestion.class))).thenThrow(new DuplicateKeyException("uk"));
        when(wrongQuestionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(row(3, 1, 1));

        WrongQuestionResponse resp = wrongQuestionService.add(USER_ID, QUESTION_ID);

        assertEquals(4, resp.getWrongCount());     // 3+1
        assertEquals(0, resp.getCorrectStreak());  // 清零
        assertFalse(resp.getMastered());           // 摘除状态复位
        ArgumentCaptor<WrongQuestion> captor = ArgumentCaptor.forClass(WrongQuestion.class);
        verify(wrongQuestionMapper).updateById(captor.capture());
        assertEquals(4, captor.getValue().getWrongCount());
        assertEquals(0, captor.getValue().getMastered());
    }

    @Test
    void add_foreignQuestion_returns7007() {
        when(questionMapper.selectById(QUESTION_ID)).thenReturn(question("user-b"));
        BusinessException e = assertThrows(BusinessException.class,
                () -> wrongQuestionService.add(USER_ID, QUESTION_ID));
        assertEquals(7007, e.getCode());
    }

    // ================= 重练 =================

    @Test
    void practice_correctReachesStreak2_autoMasters() {
        when(wrongQuestionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(row(2, 1, 0));
        when(questionMapper.selectById(QUESTION_ID)).thenReturn(question(USER_ID));

        WrongQuestionResponse resp = wrongQuestionService.practice(USER_ID, QUESTION_ID, true);

        assertEquals(2, resp.getCorrectStreak());
        assertTrue(resp.getMastered());            // 连对 2 次自动摘除
        assertEquals(2, resp.getWrongCount());     // 摘除不删行, 计数保留
    }

    @Test
    void practice_correctBelowStreak_keepsUnmastered() {
        when(wrongQuestionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(row(2, 0, 0));
        when(questionMapper.selectById(QUESTION_ID)).thenReturn(question(USER_ID));

        WrongQuestionResponse resp = wrongQuestionService.practice(USER_ID, QUESTION_ID, true);

        assertEquals(1, resp.getCorrectStreak());
        assertFalse(resp.getMastered());
    }

    @Test
    void practice_wrong_resetsStreakAndUnmastered() {
        when(wrongQuestionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(row(4, 1, 1));
        when(questionMapper.selectById(QUESTION_ID)).thenReturn(question(USER_ID));

        WrongQuestionResponse resp = wrongQuestionService.practice(USER_ID, QUESTION_ID, false);

        assertEquals(5, resp.getWrongCount());     // +1
        assertEquals(0, resp.getCorrectStreak());  // 重置
        assertFalse(resp.getMastered());           // 答错回到未摘除
    }

    @Test
    void practice_missingRow_returns7008() {
        when(wrongQuestionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        BusinessException e = assertThrows(BusinessException.class,
                () -> wrongQuestionService.practice(USER_ID, QUESTION_ID, true));
        assertEquals(7008, e.getCode());
    }

    // ================= 手动摘除 =================

    @Test
    void setMastered_manualTogglesFlag() {
        when(wrongQuestionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(row(2, 0, 0));
        when(questionMapper.selectById(QUESTION_ID)).thenReturn(question(USER_ID));

        WrongQuestionResponse resp = wrongQuestionService.setMastered(USER_ID, QUESTION_ID, true);

        assertTrue(resp.getMastered());
        verify(wrongQuestionMapper).updateById(any(WrongQuestion.class));
    }
}
