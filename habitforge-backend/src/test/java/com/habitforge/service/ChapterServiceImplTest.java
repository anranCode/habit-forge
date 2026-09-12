package com.habitforge.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.habitforge.common.constant.AppConstant;
import com.habitforge.common.exception.BusinessException;
import com.habitforge.modules.study.dto.ChapterCreateRequest;
import com.habitforge.modules.study.dto.ChapterResponse;
import com.habitforge.modules.study.dto.ChapterUpdateRequest;
import com.habitforge.modules.study.entity.Chapter;
import com.habitforge.modules.study.entity.Subject;
import com.habitforge.modules.study.mapper.ChapterMapper;
import com.habitforge.modules.study.mapper.SubjectMapper;
import com.habitforge.modules.study.service.impl.ChapterServiceImpl;
import com.habitforge.modules.user.service.UserService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 章节服务关键路径测试（防环/DONE 级联/首次完成加分/重开不回收积分/删除）
 */
@ExtendWith(MockitoExtension.class)
class ChapterServiceImplTest {

    @Mock
    private ChapterMapper chapterMapper;
    @Mock
    private SubjectMapper subjectMapper;
    @Mock
    private UserService userService;

    @InjectMocks
    private ChapterServiceImpl chapterService;

    private static final String USER_ID = "user-a";
    private static final String SUBJECT_ID = "subject-1";
    private static final String CHAPTER_A = "chap-a";
    private static final String CHAPTER_B = "chap-b";

    /** 纯 Mockito 环境下初始化 TableInfo，保证 Lambda 条件列名解析可用 */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), Subject.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), Chapter.class);
    }

    private Subject subjectOf(String userId) {
        Subject s = new Subject();
        s.setId(SUBJECT_ID);
        s.setUserId(userId);
        s.setName("高等数学(一)");
        return s;
    }

    private Chapter chapter(String id, String parentId, String status) {
        Chapter c = new Chapter();
        c.setId(id);
        c.setSubjectId(SUBJECT_ID);
        c.setParentId(parentId);
        c.setName("章节" + id);
        c.setSortOrder(0);
        c.setStatus(status);
        return c;
    }

    private void mockOwnedChapter(Chapter chapter) {
        when(chapterMapper.selectById(chapter.getId())).thenReturn(chapter);
        when(subjectMapper.selectById(SUBJECT_ID)).thenReturn(subjectOf(USER_ID));
    }

    // ================= 防环 =================

    @Test
    void update_parentIsOwnDescendant_returns7003() {
        // a(顶层) ← b(子), 把 a 的父设为 b 会成环
        Chapter a = chapter(CHAPTER_A, null, "NOT_STARTED");
        Chapter b = chapter(CHAPTER_B, CHAPTER_A, "NOT_STARTED");
        mockOwnedChapter(a);
        when(chapterMapper.selectById(CHAPTER_B)).thenReturn(b);

        ChapterUpdateRequest req = new ChapterUpdateRequest();
        req.setParentId(CHAPTER_B);
        BusinessException e = assertThrows(BusinessException.class,
                () -> chapterService.update(USER_ID, CHAPTER_A, req));
        assertEquals(7003, e.getCode());
        verify(chapterMapper, never()).updateById(any(Chapter.class));
    }

    @Test
    void update_parentSelf_returns7003() {
        Chapter a = chapter(CHAPTER_A, null, "NOT_STARTED");
        mockOwnedChapter(a);

        ChapterUpdateRequest req = new ChapterUpdateRequest();
        req.setParentId(CHAPTER_A);
        BusinessException e = assertThrows(BusinessException.class,
                () -> chapterService.update(USER_ID, CHAPTER_A, req));
        assertEquals(7003, e.getCode());
        verify(chapterMapper, never()).updateById(any(Chapter.class));
    }

    @Test
    void update_parentOfOtherSubject_returns7003() {
        Chapter a = chapter(CHAPTER_A, null, "NOT_STARTED");
        mockOwnedChapter(a);
        Chapter foreign = chapter("chap-x", null, "NOT_STARTED");
        foreign.setSubjectId("subject-other");
        when(chapterMapper.selectById("chap-x")).thenReturn(foreign);

        ChapterUpdateRequest req = new ChapterUpdateRequest();
        req.setParentId("chap-x");
        BusinessException e = assertThrows(BusinessException.class,
                () -> chapterService.update(USER_ID, CHAPTER_A, req));
        assertEquals(7003, e.getCode());
    }

    // ================= 状态变更: 加分/级联/重开 =================

    @Test
    void updateStatus_firstDone_awardsPointsOnceAndCascadesChildren() {
        Chapter a = chapter(CHAPTER_A, null, "NOT_STARTED");
        Chapter b = chapter(CHAPTER_B, CHAPTER_A, "NOT_STARTED");
        mockOwnedChapter(a);
        when(chapterMapper.selectList(any())).thenReturn(List.of(a, b));

        ChapterResponse resp = chapterService.updateStatus(USER_ID, CHAPTER_A, "DONE");

        assertEquals("DONE", resp.getStatus());
        assertNotNull(resp.getDoneAt());
        // 本章节首次 DONE: 只给本章节加一次分, 级联的子孙不加分
        verify(userService, times(1)).addPoints(USER_ID, AppConstant.POINTS_PER_CHAPTER_DONE);

        ArgumentCaptor<Chapter> captor = ArgumentCaptor.forClass(Chapter.class);
        verify(chapterMapper, times(2)).updateById(captor.capture()); // a + b
        Chapter updatedB = captor.getAllValues().stream()
                .filter(c -> CHAPTER_B.equals(c.getId())).findFirst().orElseThrow();
        assertEquals("DONE", updatedB.getStatus());
        assertNotNull(updatedB.getDoneAt());

        // 重复置 DONE: doneAt 保留, 不再加分
        chapterService.updateStatus(USER_ID, CHAPTER_A, "DONE");
        verify(userService, times(1)).addPoints(anyString(), anyInt());
        assertNotNull(a.getDoneAt());
    }

    @Test
    void updateStatus_reopenFromDone_clearsDoneAt_butKeepsPoints() {
        Chapter a = chapter(CHAPTER_A, null, "DONE");
        a.setDoneAt(LocalDateTime.now().minusDays(1));
        mockOwnedChapter(a);

        ChapterResponse resp = chapterService.updateStatus(USER_ID, CHAPTER_A, "IN_PROGRESS");

        assertEquals("IN_PROGRESS", resp.getStatus());
        assertNull(resp.getDoneAt());
        // 已发积分不回收
        verify(userService, never()).addPoints(anyString(), anyInt());
        verify(chapterMapper).updateById(a);
    }

    // ================= 归属校验 =================

    @Test
    void updateStatus_chapterMissing_returns7002() {
        when(chapterMapper.selectById(CHAPTER_A)).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class,
                () -> chapterService.updateStatus(USER_ID, CHAPTER_A, "DONE"));
        assertEquals(7002, e.getCode());
    }

    @Test
    void updateStatus_crossUserSubject_returns7001() {
        Chapter a = chapter(CHAPTER_A, null, "NOT_STARTED");
        when(chapterMapper.selectById(CHAPTER_A)).thenReturn(a);
        when(subjectMapper.selectById(SUBJECT_ID)).thenReturn(subjectOf("user-b"));

        BusinessException e = assertThrows(BusinessException.class,
                () -> chapterService.updateStatus(USER_ID, CHAPTER_A, "DONE"));
        assertEquals(7001, e.getCode());
        verify(userService, never()).addPoints(anyString(), anyInt());
    }

    @Test
    void create_crossUserSubject_returns7001() {
        when(subjectMapper.selectById(SUBJECT_ID)).thenReturn(subjectOf("user-b"));

        ChapterCreateRequest req = new ChapterCreateRequest();
        req.setSubjectId(SUBJECT_ID);
        req.setName("第一章");
        BusinessException e = assertThrows(BusinessException.class,
                () -> chapterService.create(USER_ID, req));
        assertEquals(7001, e.getCode());
        verify(chapterMapper, never()).insert(any(Chapter.class));
    }

    // ================= 删除 =================

    @Test
    void delete_owned_delegatesToMapper_physicalDelete() {
        Chapter a = chapter(CHAPTER_A, null, "NOT_STARTED");
        mockOwnedChapter(a);

        chapterService.delete(USER_ID, CHAPTER_A);

        // 物理删除, 子树由 DB FK CASCADE 处理
        verify(chapterMapper).deleteById(CHAPTER_A);
    }

    @Test
    void listBySubject_crossUser_returns7001() {
        when(subjectMapper.selectById(SUBJECT_ID)).thenReturn(subjectOf("user-b"));

        BusinessException e = assertThrows(BusinessException.class,
                () -> chapterService.listBySubject(USER_ID, SUBJECT_ID));
        assertEquals(7001, e.getCode());
    }
}
