package com.habitforge.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.habitforge.common.exception.BusinessException;
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
import com.habitforge.modules.study.service.impl.SubjectServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 科目服务关键路径测试（归属校验/逻辑删除/列表实时汇总/倒计时）
 */
@ExtendWith(MockitoExtension.class)
class SubjectServiceImplTest {

    @Mock
    private SubjectMapper subjectMapper;
    @Mock
    private ChapterMapper chapterMapper;
    // P1: 科目列表附到期卡/错题聚合(Mockito 默认返回空 List → 计数 0)
    @Mock
    private FlashcardMapper flashcardMapper;
    @Mock
    private QuestionMapper questionMapper;
    @Mock
    private WrongQuestionMapper wrongQuestionMapper;

    @InjectMocks
    private SubjectServiceImpl subjectService;

    private static final String USER_ID = "user-a";
    private static final String SUBJECT_1 = "subject-1";
    private static final String SUBJECT_2 = "subject-2";
    private static final String SUBJECT_3 = "subject-3";

    /** 纯 Mockito 环境下初始化 TableInfo，保证 Lambda 条件列名解析可用 */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), Subject.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), Chapter.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), Flashcard.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), Question.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), WrongQuestion.class);
    }

    private Subject subject(String id, String userId, LocalDate examDate) {
        Subject s = new Subject();
        s.setId(id);
        s.setUserId(userId);
        s.setName("科目" + id);
        s.setExamDate(examDate);
        s.setSortOrder(0);
        s.setIsActive(1);
        s.setDeleted(0);
        return s;
    }

    private Map<String, Object> row(String subjectId, long cnt) {
        Map<String, Object> m = new HashMap<>();
        m.put("subjectId", subjectId);
        m.put("cnt", cnt);
        return m;
    }

    // ================= 归属校验 =================

    @Test
    void getDetail_missing_returns7001() {
        when(subjectMapper.selectById(SUBJECT_1)).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class,
                () -> subjectService.getDetail(USER_ID, SUBJECT_1));
        assertEquals(7001, e.getCode());
    }

    @Test
    void getDetail_crossUser_returns7001() {
        when(subjectMapper.selectById(SUBJECT_1)).thenReturn(subject(SUBJECT_1, "user-b", null));

        BusinessException e = assertThrows(BusinessException.class,
                () -> subjectService.getDetail(USER_ID, SUBJECT_1));
        assertEquals(7001, e.getCode());
    }

    // ================= 逻辑删除 =================

    @Test
    void delete_owned_usesLogicDelete() {
        when(subjectMapper.selectById(SUBJECT_1)).thenReturn(subject(SUBJECT_1, USER_ID, null));

        subjectService.delete(USER_ID, SUBJECT_1);

        // @TableLogic: deleteById 即 UPDATE deleted=1, 章节数据保留在库
        verify(subjectMapper).deleteById(SUBJECT_1);
    }

    // ================= 创建 =================

    @Test
    void create_fillsDefaults_andDaysLeft() {
        when(subjectMapper.insert(any(Subject.class))).thenAnswer(inv -> {
            inv.getArgument(0, Subject.class).setId(SUBJECT_1);
            return 1;
        });
        LocalDate exam = LocalDate.now().plusDays(30);

        SubjectCreateRequest req = new SubjectCreateRequest();
        req.setName("高等数学(一)");
        req.setExamDate(exam);
        SubjectResponse resp = subjectService.create(USER_ID, req);

        assertEquals(SUBJECT_1, resp.getId());
        assertEquals(0, resp.getChapterTotal());
        assertEquals(0, resp.getChapterDone());
        assertEquals(30, resp.getDaysLeft());
        assertEquals(0, resp.getDueCards());
        assertEquals(0, resp.getWrongCount());
        // 新建科目章节必为 0, 不回查聚合
        verify(chapterMapper, never()).selectMaps(any(QueryWrapper.class));
    }

    // ================= 更新 =================

    @Test
    void update_nullFields_keepExisting() {
        when(subjectMapper.selectById(SUBJECT_1)).thenReturn(subject(SUBJECT_1, USER_ID, null));
        when(chapterMapper.selectMaps(any(QueryWrapper.class))).thenReturn(List.of());

        SubjectUpdateRequest req = new SubjectUpdateRequest();
        req.setName("新名字"); // 其余 null = 不改
        SubjectResponse resp = subjectService.update(USER_ID, SUBJECT_1, req);

        assertEquals("新名字", resp.getName());
        assertNull(resp.getExamDate());
        assertNull(resp.getDaysLeft());
        verify(subjectMapper).updateById(any(Subject.class));
    }

    // ================= 列表汇总 =================

    @Test
    void listMine_realtimeAggregatesAndDaysLeft() {
        Subject s1 = subject(SUBJECT_1, USER_ID, LocalDate.now().plusDays(10));
        Subject s2 = subject(SUBJECT_2, USER_ID, LocalDate.now().minusDays(5)); // 过期按 0
        Subject s3 = subject(SUBJECT_3, USER_ID, null);                          // 无考期 null
        when(subjectMapper.selectList(any())).thenReturn(List.of(s1, s2, s3));
        // 两次 GROUP BY: 第一次总数, 第二次 DONE 数
        when(chapterMapper.selectMaps(any(QueryWrapper.class))).thenAnswer(
                new org.mockito.stubbing.Answer<List<Map<String, Object>>>() {
                    private int call = 0;
                    @Override
                    public List<Map<String, Object>> answer(org.mockito.invocation.InvocationOnMock inv) {
                        return call++ == 0
                                ? List.of(row(SUBJECT_1, 3L), row(SUBJECT_2, 2L))
                                : List.of(row(SUBJECT_1, 2L));
                    }
                });

        List<SubjectResponse> list = subjectService.listMine(USER_ID);

        assertEquals(3, list.size());
        SubjectResponse r1 = list.get(0);
        assertEquals(3, r1.getChapterTotal());
        assertEquals(2, r1.getChapterDone());
        assertEquals(10, r1.getDaysLeft());
        SubjectResponse r2 = list.get(1);
        assertEquals(2, r2.getChapterTotal());
        assertEquals(0, r2.getChapterDone());
        assertEquals(0, r2.getDaysLeft());
        SubjectResponse r3 = list.get(2);
        assertEquals(0, r3.getChapterTotal());
        assertEquals(0, r3.getChapterDone());
        assertNull(r3.getDaysLeft());
        // 汇总固定两条聚合查询, 不随科目数 N+1
        verify(chapterMapper, times(2)).selectMaps(any(QueryWrapper.class));
    }

    @Test
    void listMine_empty_skipsAggregation() {
        when(subjectMapper.selectList(any())).thenReturn(List.of());

        List<SubjectResponse> list = subjectService.listMine(USER_ID);

        assertEquals(0, list.size());
        verify(chapterMapper, never()).selectMaps(any(QueryWrapper.class));
    }
}
