package com.habitforge.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.habitforge.common.exception.BusinessException;
import com.habitforge.common.util.RedisUtil;
import com.habitforge.modules.checkin.service.CheckinService;
import com.habitforge.modules.habit.entity.Habit;
import com.habitforge.modules.habit.mapper.HabitMapper;
import com.habitforge.modules.journal.dto.JournalCreateRequest;
import com.habitforge.modules.journal.dto.JournalDetailResponse;
import com.habitforge.modules.journal.entity.Journal;
import com.habitforge.modules.journal.entity.JournalHabit;
import com.habitforge.modules.journal.entity.JournalImage;
import com.habitforge.modules.journal.mapper.JournalHabitMapper;
import com.habitforge.modules.journal.mapper.JournalImageMapper;
import com.habitforge.modules.journal.mapper.JournalMapper;
import com.habitforge.modules.journal.service.impl.JournalServiceImpl;
import com.habitforge.modules.storage.service.StorageService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 日记服务关键路径测试（创建/重复/未来日期/跨用户隔离）
 */
@ExtendWith(MockitoExtension.class)
class JournalServiceImplTest {

    @Mock
    private JournalMapper journalMapper;
    @Mock
    private JournalHabitMapper journalHabitMapper;
    @Mock
    private JournalImageMapper journalImageMapper;
    @Mock
    private HabitMapper habitMapper;
    @Mock
    private CheckinService checkinService;
    @Mock
    private StorageService storageService;
    @Mock
    private RedisUtil redisUtil;

    @InjectMocks
    private JournalServiceImpl journalService;

    private static final String USER_ID = "user-a";
    private static final String JOURNAL_ID = "journal-1";
    private static final String HABIT_ID = "habit-1";

    /** 纯 Mockito 环境下初始化 TableInfo，LambdaQueryWrapper.select() 需要完整列缓存 */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), Journal.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), JournalHabit.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), JournalImage.class);
    }

    private Journal journalOf(String userId) {
        Journal j = new Journal();
        j.setId(JOURNAL_ID);
        j.setUserId(userId);
        j.setJournalDate(LocalDate.now());
        return j;
    }

    private Habit habitOf(String userId) {
        Habit h = new Habit();
        h.setId(HABIT_ID);
        h.setUserId(userId);
        h.setName("早起");
        return h;
    }

    private void mockRateLimitOk() {
        when(redisUtil.tryAcquire(anyString(), anyInt(), any(Duration.class))).thenReturn(true);
    }

    /** 模拟 MyBatis-Plus insert 后回填主键 */
    private void mockInsertFillsId() {
        when(journalMapper.insert(any(Journal.class))).thenAnswer(invocation -> {
            Journal j = invocation.getArgument(0);
            j.setId(JOURNAL_ID);
            return 1;
        });
    }

    @Test
    void create_success_withExplicitHabit() {
        mockRateLimitOk();
        mockInsertFillsId();
        when(habitMapper.selectById(HABIT_ID)).thenReturn(habitOf(USER_ID));
        when(journalHabitMapper.insert(any(JournalHabit.class))).thenReturn(1);
        // getDetail 回读
        when(journalMapper.selectById(JOURNAL_ID)).thenReturn(journalOf(USER_ID));
        JournalHabit link = new JournalHabit();
        link.setJournalId(JOURNAL_ID);
        link.setHabitId(HABIT_ID);
        when(journalHabitMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(link));
        when(habitMapper.selectBatchIds(any())).thenReturn(List.of(habitOf(USER_ID)));
        when(journalImageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        JournalCreateRequest req = new JournalCreateRequest();
        req.setTitle("今天不错");
        req.setHabitIds(List.of(HABIT_ID));
        JournalDetailResponse resp = journalService.create(USER_ID, req);

        assertEquals(JOURNAL_ID, resp.getId());
        assertEquals(1, resp.getHabits().size());
        assertEquals("早起", resp.getHabits().get(0).getName());
    }

    @Test
    void create_autoSeedFromCheckins() {
        mockRateLimitOk();
        mockInsertFillsId();
        when(checkinService.listCheckedHabitIds(anyString(), any(LocalDate.class))).thenReturn(List.of(HABIT_ID));
        when(habitMapper.selectById(HABIT_ID)).thenReturn(habitOf(USER_ID));
        when(journalHabitMapper.insert(any(JournalHabit.class))).thenReturn(1);
        when(journalMapper.selectById(JOURNAL_ID)).thenReturn(journalOf(USER_ID));
        when(journalHabitMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(journalImageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        JournalCreateRequest req = new JournalCreateRequest();
        req.setHabitIds(null); // null = 自动播种
        journalService.create(USER_ID, req);

        verify(checkinService).listCheckedHabitIds(anyString(), any(LocalDate.class));
    }

    @Test
    void create_duplicate_returns4002() {
        mockRateLimitOk();
        when(journalMapper.insert(any(Journal.class))).thenThrow(new DuplicateKeyException("uk_user_date"));

        JournalCreateRequest req = new JournalCreateRequest();
        req.setHabitIds(List.of());
        BusinessException e = assertThrows(BusinessException.class, () -> journalService.create(USER_ID, req));
        assertEquals(4002, e.getCode());
    }

    @Test
    void create_futureDate_returns4003() {
        mockRateLimitOk();

        JournalCreateRequest req = new JournalCreateRequest();
        req.setJournalDate(LocalDate.now().plusDays(1));
        BusinessException e = assertThrows(BusinessException.class, () -> journalService.create(USER_ID, req));
        assertEquals(4003, e.getCode());
        verify(journalMapper, never()).insert(any(Journal.class));
    }

    @Test
    void getDetail_crossUser_returns4001() {
        when(journalMapper.selectById(JOURNAL_ID)).thenReturn(journalOf("user-b"));

        BusinessException e = assertThrows(BusinessException.class,
                () -> journalService.getDetail(USER_ID, JOURNAL_ID));
        assertEquals(4001, e.getCode());
    }

    @Test
    void addHabit_crossUserHabit_returns2001() {
        when(journalMapper.selectById(JOURNAL_ID)).thenReturn(journalOf(USER_ID));
        when(habitMapper.selectById(HABIT_ID)).thenReturn(habitOf("user-b"));

        BusinessException e = assertThrows(BusinessException.class,
                () -> journalService.addHabit(USER_ID, JOURNAL_ID, HABIT_ID));
        assertEquals(2001, e.getCode());
    }
}
