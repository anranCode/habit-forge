package com.habitforge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.habitforge.common.exception.BusinessException;
import com.habitforge.modules.checkin.entity.Checkin;
import com.habitforge.modules.checkin.service.CheckinService;
import com.habitforge.modules.habit.entity.Habit;
import com.habitforge.modules.habit.mapper.HabitMapper;
import com.habitforge.modules.journal.entity.Journal;
import com.habitforge.modules.journal.entity.JournalHabit;
import com.habitforge.modules.journal.mapper.JournalHabitMapper;
import com.habitforge.modules.journal.mapper.JournalMapper;
import com.habitforge.modules.journal.service.JournalService;
import com.habitforge.modules.reflection.dto.ReflectionRequest;
import com.habitforge.modules.reflection.dto.ReflectionResponse;
import com.habitforge.modules.reflection.entity.HabitReflection;
import com.habitforge.modules.reflection.mapper.HabitReflectionMapper;
import com.habitforge.modules.reflection.service.impl.ReflectionServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 习惯心得服务关键路径测试（创建/关联/重复/失败心得/跨用户隔离）
 */
@ExtendWith(MockitoExtension.class)
class ReflectionServiceImplTest {

    @Mock
    private HabitReflectionMapper reflectionMapper;
    @Mock
    private JournalMapper journalMapper;
    @Mock
    private JournalHabitMapper journalHabitMapper;
    @Mock
    private HabitMapper habitMapper;
    @Mock
    private JournalService journalService;
    @Mock
    private CheckinService checkinService;

    @InjectMocks
    private ReflectionServiceImpl reflectionService;

    private static final String USER_ID = "user-a";
    private static final String JOURNAL_ID = "journal-1";
    private static final String HABIT_ID = "habit-1";
    private static final String CHECKIN_ID = "checkin-1";

    private Journal journal() {
        Journal j = new Journal();
        j.setId(JOURNAL_ID);
        j.setUserId(USER_ID);
        j.setJournalDate(LocalDate.now());
        return j;
    }

    private Habit habit(String userId) {
        Habit h = new Habit();
        h.setId(HABIT_ID);
        h.setUserId(userId);
        h.setName("早起");
        return h;
    }

    private ReflectionRequest request(Integer result) {
        ReflectionRequest r = new ReflectionRequest();
        r.setJournalId(JOURNAL_ID);
        r.setHabitId(HABIT_ID);
        r.setResult(result);
        return r;
    }

    @Test
    void create_completed_linksCheckin() {
        when(journalService.getOwned(USER_ID, JOURNAL_ID)).thenReturn(journal());
        when(habitMapper.selectById(HABIT_ID)).thenReturn(habit(USER_ID));
        when(journalHabitMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(journalHabitMapper.insert(any(JournalHabit.class))).thenReturn(1);
        Checkin checkin = new Checkin();
        checkin.setId(CHECKIN_ID);
        when(checkinService.getByHabitAndDate(any(), any())).thenReturn(checkin);
        when(reflectionMapper.insert(any(HabitReflection.class))).thenReturn(1);

        ReflectionResponse resp = reflectionService.create(USER_ID, request(1));

        assertEquals(CHECKIN_ID, resp.getCheckinId());
        assertEquals(1, resp.getResult());
        assertEquals("早起", resp.getHabitName());
    }

    @Test
    void create_notCompleted_checkinNull() {
        // 失败记录同样允许：无打卡 -> checkinId 为 null
        when(journalService.getOwned(USER_ID, JOURNAL_ID)).thenReturn(journal());
        when(habitMapper.selectById(HABIT_ID)).thenReturn(habit(USER_ID));
        when(journalHabitMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        when(checkinService.getByHabitAndDate(any(), any())).thenReturn(null);
        when(reflectionMapper.insert(any(HabitReflection.class))).thenReturn(1);

        ReflectionResponse resp = reflectionService.create(USER_ID, request(0));

        assertNull(resp.getCheckinId());
        assertEquals(0, resp.getResult());
    }

    @Test
    void create_crossUserHabit_returns2001() {
        when(journalService.getOwned(USER_ID, JOURNAL_ID)).thenReturn(journal());
        when(habitMapper.selectById(HABIT_ID)).thenReturn(habit("user-b"));

        BusinessException e = assertThrows(BusinessException.class,
                () -> reflectionService.create(USER_ID, request(1)));
        assertEquals(2001, e.getCode());
    }

    @Test
    void create_duplicate_returns5002() {
        when(journalService.getOwned(USER_ID, JOURNAL_ID)).thenReturn(journal());
        when(habitMapper.selectById(HABIT_ID)).thenReturn(habit(USER_ID));
        when(journalHabitMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        when(checkinService.getByHabitAndDate(any(), any())).thenReturn(null);
        when(reflectionMapper.insert(any(HabitReflection.class))).thenThrow(new DuplicateKeyException("uk_journal_habit"));

        BusinessException e = assertThrows(BusinessException.class,
                () -> reflectionService.create(USER_ID, request(1)));
        assertEquals(5002, e.getCode());
    }

    @Test
    void listByHabit_returnsWithJournalDate_descOrder() {
        when(habitMapper.selectById(HABIT_ID)).thenReturn(habit(USER_ID));

        HabitReflection r1 = new HabitReflection();
        r1.setId("ref-1");
        r1.setJournalId("journal-old");
        r1.setHabitId(HABIT_ID);
        r1.setResult(1);
        r1.setCreatedAt(LocalDateTime.now().minusDays(2));
        HabitReflection r2 = new HabitReflection();
        r2.setId("ref-2");
        r2.setJournalId("journal-new");
        r2.setHabitId(HABIT_ID);
        r2.setResult(0);
        r2.setCreatedAt(LocalDateTime.now());
        when(reflectionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(r2, r1));

        Journal jOld = new Journal();
        jOld.setId("journal-old");
        jOld.setJournalDate(LocalDate.now().minusDays(2));
        Journal jNew = new Journal();
        jNew.setId("journal-new");
        jNew.setJournalDate(LocalDate.now());
        when(journalMapper.selectBatchIds(any())).thenReturn(List.of(jOld, jNew));

        List<ReflectionResponse> list = reflectionService.listByHabit(USER_ID, HABIT_ID);

        assertEquals(2, list.size());
        assertEquals("journal-new", list.get(0).getJournalId());
        assertEquals(LocalDate.now(), list.get(0).getJournalDate());
        assertEquals("journal-old", list.get(1).getJournalId());
    }
}
