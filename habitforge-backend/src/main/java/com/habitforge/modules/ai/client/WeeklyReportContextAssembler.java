package com.habitforge.modules.ai.client;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.habitforge.common.constant.AppConstant;
import com.habitforge.modules.ai.config.AiProperties;
import com.habitforge.modules.ai.dto.WeeklyStatsSnapshot;
import com.habitforge.modules.checkin.entity.Checkin;
import com.habitforge.modules.focus.dto.FocusDayPoint;
import com.habitforge.modules.focus.service.FocusService;
import com.habitforge.modules.checkin.mapper.CheckinMapper;
import com.habitforge.modules.habit.dto.HabitResponseDTO;
import com.habitforge.modules.habit.service.HabitService;
import com.habitforge.modules.journal.entity.Journal;
import com.habitforge.modules.journal.service.JournalService;
import com.habitforge.modules.reflection.dto.ReflectionResponse;
import com.habitforge.modules.reflection.service.ReflectionService;
import com.habitforge.modules.study.dto.DailyStudyTimeResponse;
import com.habitforge.modules.study.dto.OverviewResponse;
import com.habitforge.modules.study.dto.StudySessionResponse;
import com.habitforge.modules.study.dto.SubjectResponse;
import com.habitforge.modules.study.entity.CardReviewLog;
import com.habitforge.modules.study.entity.Flashcard;
import com.habitforge.modules.study.entity.WrongQuestion;
import com.habitforge.modules.study.mapper.CardReviewLogMapper;
import com.habitforge.modules.study.mapper.FlashcardMapper;
import com.habitforge.modules.study.mapper.WrongQuestionMapper;
import com.habitforge.modules.study.service.StudyOverviewService;
import com.habitforge.modules.study.service.StudySessionService;
import com.habitforge.modules.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 周报客观数据组装：一份快照, 两处使用（拼 prompt + 存 reviews.stats_snapshot）
 *
 * <p>原则：AI 只能看到这里给出的数字, 因此快照必须自洽、可复现；
 * prompt 超预算时按「日记正文 → 心得」顺序降档（数字部分永不裁, 它是评价的依据）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeeklyReportContextAssembler {

    /** 心得单字段截断（比日记更短, 心得是补充信号而非主证据） */
    private static final int REFLECTION_FIELD_CHARS = 200;

    private final AiProperties aiProperties;
    private final UserService userService;
    private final HabitService habitService;
    private final CheckinMapper checkinMapper;
    private final StudySessionService studySessionService;
    private final StudyOverviewService studyOverviewService;
    private final CardReviewLogMapper cardReviewLogMapper;
    private final FlashcardMapper flashcardMapper;
    private final WrongQuestionMapper wrongQuestionMapper;
    private final JournalService journalService;
    private final ReflectionService reflectionService;
    private final FocusService focusService;

    /** userPrompt = 客观数据正文; snapshotJson = 落库快照（两者数字同源） */
    public record Context(String userPrompt, String snapshotJson) {
    }

    public Context assemble(String userId, LocalDate periodStart, LocalDate periodEnd) {
        WeeklyStatsSnapshot snapshot = buildSnapshot(userId, periodStart, periodEnd);
        List<Journal> journals = journalService.listByRangeWithContent(userId, periodStart, periodEnd);
        List<ReflectionResponse> reflections = recentReflections(userId, periodStart, periodEnd);
        return new Context(buildPrompt(userId, snapshot, journals, reflections), JSONUtil.toJsonStr(snapshot));
    }

    // ================= 快照 =================

    private WeeklyStatsSnapshot buildSnapshot(String userId, LocalDate from, LocalDate to) {
        return WeeklyStatsSnapshot.builder()
                .periodStart(from.toString())
                .periodEnd(to.toString())
                .study(buildStudy(userId, from, to))
                .habits(buildHabits(userId, from, to))
                .cards(buildCards(userId, from, to))
                .wrongs(buildWrongs(userId, from, to))
                .subjects(buildSubjects(userId, from, to))
                .journals(buildJournals(userId, from, to))
                .reflections(buildReflections(userId, from, to))
                .focus(buildFocus(userId, from, to))
                .build();
    }

    private WeeklyStatsSnapshot.StudyStats buildStudy(String userId, LocalDate from, LocalDate to) {
        List<DailyStudyTimeResponse> daily = studySessionService.dailyForReport(userId, from, to);
        int total = daily.stream().mapToInt(d -> d.getMinutes() == null ? 0 : d.getMinutes()).sum();
        int days = (int) (to.toEpochDay() - from.toEpochDay() + 1);
        int activeDays = (int) daily.stream().filter(d -> d.getMinutes() != null && d.getMinutes() > 0).count();
        int sessionCount = daily.stream().mapToInt(d -> d.getSessionCount() == null ? 0 : d.getSessionCount()).sum();

        Map<String, Integer> bySubject = new LinkedHashMap<>();
        for (StudySessionResponse s : studySessionService.listByRange(userId, from, to)) {
            String name = s.getSubjectName() == null || s.getSubjectName().isBlank() ? "未归类" : s.getSubjectName();
            bySubject.merge(name, s.getMinutes() == null ? 0 : s.getMinutes(), Integer::sum);
        }

        return WeeklyStatsSnapshot.StudyStats.builder()
                .totalMinutes(total)
                .avgMinutesPerDay(days == 0 ? 0 : total / days)
                .activeDays(activeDays)
                .sessionCount(sessionCount)
                .daily(daily.stream()
                        .map(d -> WeeklyStatsSnapshot.DailyPoint.builder()
                                .date(d.getDate().toString())
                                .minutes(d.getMinutes())
                                .build())
                        .toList())
                .bySubject(bySubject.entrySet().stream()
                        .map(e -> WeeklyStatsSnapshot.NameMinutes.builder().name(e.getKey()).minutes(e.getValue()).build())
                        .toList())
                .build();
    }

    private WeeklyStatsSnapshot.HabitStats buildHabits(String userId, LocalDate from, LocalDate to) {
        List<HabitResponseDTO> habits = habitService.listMine(userId, true);
        if (habits.isEmpty()) {
            return WeeklyStatsSnapshot.HabitStats.builder()
                    .activeHabitCount(0).checkinTotal(0).habitHitRatePercent(0).items(List.of())
                    .build();
        }
        List<String> habitIds = habits.stream().map(HabitResponseDTO::getId).toList();
        // 空集合会生成 IN (), 已在上面提前返回
        List<Checkin> checkins = checkinMapper.selectList(new LambdaQueryWrapper<Checkin>()
                .in(Checkin::getHabitId, habitIds)
                .between(Checkin::getCheckDate, from, to));
        Map<String, Set<LocalDate>> checkedDates = checkins.stream()
                .collect(Collectors.groupingBy(Checkin::getHabitId,
                        Collectors.mapping(Checkin::getCheckDate, Collectors.toSet())));

        List<WeeklyStatsSnapshot.HabitItem> items = new ArrayList<>();
        int hit = 0;
        for (HabitResponseDTO h : habits) {
            int weekDays = checkedDates.getOrDefault(h.getId(), Set.of()).size();
            if (weekDays > 0) {
                hit++;
            }
            items.add(WeeklyStatsSnapshot.HabitItem.builder()
                    .name(h.getName())
                    .category(h.getCategory())
                    .habitType(h.getHabitType())
                    .weekCheckedDays(weekDays)
                    .currentStreak(h.getCurrentStreak())
                    .longestStreak(h.getLongestStreak())
                    .missedYesterday(h.getMissedYesterday())
                    .build());
        }
        return WeeklyStatsSnapshot.HabitStats.builder()
                .activeHabitCount(habits.size())
                .checkinTotal(checkins.size())
                .habitHitRatePercent(hit * 100 / habits.size())
                .items(items)
                .build();
    }

    private WeeklyStatsSnapshot.CardStats buildCards(String userId, LocalDate from, LocalDate to) {
        List<CardReviewLog> logs = cardReviewLogMapper.selectList(new LambdaQueryWrapper<CardReviewLog>()
                .eq(CardReviewLog::getUserId, userId)
                .ge(CardReviewLog::getReviewedAt, from.atStartOfDay())
                .lt(CardReviewLog::getReviewedAt, to.plusDays(1).atStartOfDay()));
        long remembered = logs.stream().filter(l -> l.getRating() != null && l.getRating() >= 3).count();
        long reviewedDays = logs.stream()
                .filter(l -> l.getReviewedAt() != null)
                .map(l -> l.getReviewedAt().toLocalDate())
                .distinct()
                .count();
        Long dueNow = flashcardMapper.selectCount(new LambdaQueryWrapper<Flashcard>()
                .eq(Flashcard::getUserId, userId)
                .eq(Flashcard::getStatus, Flashcard.STATUS_ACTIVE)
                .le(Flashcard::getDueDate, LocalDate.now()));
        return WeeklyStatsSnapshot.CardStats.builder()
                .reviewedTotal(logs.size())
                .reviewedDays((int) reviewedDays)
                .rememberRatePercent(logs.isEmpty() ? 0 : (int) (remembered * 100 / logs.size()))
                .dueNow(dueNow == null ? 0 : dueNow.intValue())
                .build();
    }

    private WeeklyStatsSnapshot.WrongStats buildWrongs(String userId, LocalDate from, LocalDate to) {
        Long pending = wrongQuestionMapper.selectCount(new LambdaQueryWrapper<WrongQuestion>()
                .eq(WrongQuestion::getUserId, userId)
                .eq(WrongQuestion::getMastered, 0));
        Long mastered = wrongQuestionMapper.selectCount(new LambdaQueryWrapper<WrongQuestion>()
                .eq(WrongQuestion::getUserId, userId)
                .eq(WrongQuestion::getMastered, 1)
                .ge(WrongQuestion::getUpdatedAt, from.atStartOfDay())
                .lt(WrongQuestion::getUpdatedAt, to.plusDays(1).atStartOfDay()));
        return WeeklyStatsSnapshot.WrongStats.builder()
                .pendingNow(pending == null ? 0 : pending.intValue())
                .masteredThisWeek(mastered == null ? 0 : mastered.intValue())
                .build();
    }

    private List<WeeklyStatsSnapshot.SubjectProgress> buildSubjects(String userId, LocalDate from, LocalDate to) {
        OverviewResponse overview = studyOverviewService.overview(userId);
        if (overview == null || overview.getSubjects() == null) {
            return List.of();
        }
        Map<String, Integer> weekBySubject = studySessionService.listByRange(userId, from, to).stream()
                .filter(s -> s.getSubjectName() != null)
                .collect(Collectors.groupingBy(StudySessionResponse::getSubjectName,
                        Collectors.summingInt(s -> s.getMinutes() == null ? 0 : s.getMinutes())));
        List<WeeklyStatsSnapshot.SubjectProgress> result = new ArrayList<>();
        for (SubjectResponse s : overview.getSubjects()) {
            result.add(WeeklyStatsSnapshot.SubjectProgress.builder()
                    .name(s.getName())
                    .chapterDone(s.getChapterDone())
                    .chapterTotal(s.getChapterTotal())
                    .daysLeft(s.getDaysLeft())
                    .weekMinutes(weekBySubject.getOrDefault(s.getName(), 0))
                    .build());
        }
        return result;
    }

    private WeeklyStatsSnapshot.JournalStats buildJournals(String userId, LocalDate from, LocalDate to) {
        List<Journal> journals = journalService.listByRangeWithContent(userId, from, to);
        int good = 0;
        int normal = 0;
        int tired = 0;
        for (Journal j : journals) {
            if (j.getMood() == null) {
                continue;
            }
            switch (j.getMood()) {
                case 1 -> good++;
                case 2 -> normal++;
                case 3 -> tired++;
                default -> { }
            }
        }
        return WeeklyStatsSnapshot.JournalStats.builder()
                .count(journals.size()).moodGood(good).moodNormal(normal).moodTired(tired)
                .build();
    }

    private WeeklyStatsSnapshot.ReflectionStats buildReflections(String userId, LocalDate from, LocalDate to) {
        List<ReflectionResponse> reflections = recentReflections(userId, from, to);
        int done = (int) reflections.stream().filter(r -> r.getResult() != null && r.getResult() == 1).count();
        return WeeklyStatsSnapshot.ReflectionStats.builder()
                .count(reflections.size())
                .doneCount(done)
                .undoneCount(reflections.size() - done)
                .build();
    }

    /** 手机节制: 整周既没录时长也没点过冲动时返回 null（该节整体省略） */
    private WeeklyStatsSnapshot.FocusStats buildFocus(String userId, LocalDate from, LocalDate to) {
        List<FocusDayPoint> days = focusService.rangeForReport(userId, from, to);
        int recorded = 0;
        int compliant = 0;
        int total = 0;
        int urgeTotal = 0;
        int urgeResisted = 0;
        for (FocusDayPoint d : days) {
            if (d.getEntertainmentMinutes() != null) {
                recorded++;
                total += d.getEntertainmentMinutes();
                if (Boolean.TRUE.equals(d.getCompliant())) {
                    compliant++;
                }
            }
            urgeTotal += d.getUrgeTotal() == null ? 0 : d.getUrgeTotal();
            urgeResisted += d.getUrgeResisted() == null ? 0 : d.getUrgeResisted();
        }
        if (recorded == 0 && urgeTotal == 0) {
            return null;
        }
        return WeeklyStatsSnapshot.FocusStats.builder()
                .limitMinutes(focusService.resolveLimit(userId))
                .recordedDays(recorded)
                .compliantDays(compliant)
                .avgEntertainmentMinutes(recorded == 0 ? null : total / recorded)
                .totalEntertainmentMinutes(total)
                .urgeTotal(urgeTotal)
                .urgeResisted(urgeResisted)
                .resistRatePercent(urgeTotal == 0 ? null : urgeResisted * 100 / urgeTotal)
                .build();
    }

    /** listRecentByUser 无时间窗, 这里按 createdAt 过滤到统计区间 */
    private List<ReflectionResponse> recentReflections(String userId, LocalDate from, LocalDate to) {
        return reflectionService.listRecentByUser(userId, aiProperties.getReportReflectionLimit()).stream()
                .filter(r -> r.getCreatedAt() != null)
                .filter(r -> {
                    LocalDate d = r.getCreatedAt().toLocalDate();
                    return !d.isBefore(from) && !d.isAfter(to);
                })
                .toList();
    }

    // ================= prompt =================

    private String buildPrompt(String userId, WeeklyStatsSnapshot snap,
                               List<Journal> journals, List<ReflectionResponse> reflections) {
        String header = buildHeader(userId, snap);
        String study = sectionStudy(snap.getStudy());
        String habits = sectionHabits(snap.getHabits());
        String studyModule = sectionStudyModule(snap);
        String focus = sectionFocus(snap.getFocus());
        String reflectionsSec = sectionReflections(reflections);

        String full = join(header, study, habits, studyModule, focus,
                sectionJournals(journals, true), reflectionsSec);
        if (full.length() <= AppConstant.AI_CONTEXT_CHAR_BUDGET) {
            return full;
        }
        // 降档 1: 日记只留日期与心情
        String brief = join(header, study, habits, studyModule, focus,
                sectionJournals(journals, false), reflectionsSec);
        if (brief.length() <= AppConstant.AI_CONTEXT_CHAR_BUDGET) {
            log.info("周报上下文超预算, 日记正文已降档 user={}", userId);
            return brief;
        }
        // 降档 2: 再去掉心得（数字骨架必须保留, 它是 AI 评价的唯一依据）
        log.info("周报上下文超预算, 日记与心得均已降档 user={}", userId);
        return join(header, study, habits, studyModule, focus);
    }

    private String buildHeader(String userId, WeeklyStatsSnapshot snap) {
        LocalDate start = LocalDate.parse(snap.getPeriodStart());
        LocalDate end = LocalDate.parse(snap.getPeriodEnd());
        StringBuilder sb = new StringBuilder("## 统计周期\n");
        sb.append(start).append("(").append(start.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.CHINA))
                .append(") ~ ").append(end).append("(")
                .append(end.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.CHINA)).append(")\n");
        String goal = userService.getIdentityGoal(userId);
        if (goal != null && !goal.isBlank()) {
            sb.append("身份目标: 我想成为 ").append(goal.trim()).append("\n");
        }
        return sb.toString();
    }

    private String sectionStudy(WeeklyStatsSnapshot.StudyStats study) {
        if (study == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\n## 学习时长（客观数据, 单位分钟）\n");
        sb.append("- 合计 ").append(study.getTotalMinutes()).append(" 分钟")
                .append(" | 日均 ").append(study.getAvgMinutesPerDay()).append(" 分钟")
                .append(" | 有记录 ").append(study.getActiveDays()).append(" 天")
                .append(" | 共 ").append(study.getSessionCount()).append(" 段\n");
        sb.append("- 每日: ");
        sb.append(study.getDaily() == null ? "无" : study.getDaily().stream()
                .map(d -> d.getDate().substring(5) + " " + d.getMinutes())
                .collect(Collectors.joining(" / ")));
        sb.append("\n- 按科目: ");
        sb.append(study.getBySubject() == null || study.getBySubject().isEmpty() ? "无记录"
                : study.getBySubject().stream()
                .map(s -> s.getName() + " " + s.getMinutes() + " 分钟")
                .collect(Collectors.joining(" / ")));
        sb.append("\n");
        return sb.toString();
    }

    private String sectionHabits(WeeklyStatsSnapshot.HabitStats habits) {
        if (habits == null || habits.getActiveHabitCount() == null || habits.getActiveHabitCount() == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\n## 习惯打卡\n");
        sb.append("- 活跃习惯 ").append(habits.getActiveHabitCount()).append(" 个")
                .append(" | 本周打卡 ").append(habits.getCheckinTotal()).append(" 次")
                .append(" | 覆盖率 ").append(habits.getHabitHitRatePercent()).append("%\n");
        for (WeeklyStatsSnapshot.HabitItem h : habits.getItems()) {
            sb.append("- ").append(h.getName())
                    .append(" | ").append(h.getCategory())
                    .append(" | 本周 ").append(h.getWeekCheckedDays()).append(" 天")
                    .append(" | 当前链 ").append(h.getCurrentStreak()).append(" 天")
                    .append(" | 最长 ").append(h.getLongestStreak()).append(" 天");
            if (Boolean.TRUE.equals(h.getMissedYesterday())) {
                sb.append(" | ⚠️ 昨日漏卡");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String sectionStudyModule(WeeklyStatsSnapshot snap) {
        StringBuilder sb = new StringBuilder("\n## 学习模块\n");
        WeeklyStatsSnapshot.CardStats c = snap.getCards();
        if (c != null) {
            sb.append("- 闪卡: 复习 ").append(c.getReviewedTotal()).append(" 张")
                    .append(" | 覆盖 ").append(c.getReviewedDays()).append(" 天")
                    .append(" | 记得率 ").append(c.getRememberRatePercent()).append("%")
                    .append(" | 当前到期 ").append(c.getDueNow()).append(" 张\n");
        }
        WeeklyStatsSnapshot.WrongStats w = snap.getWrongs();
        if (w != null) {
            sb.append("- 错题: 待复习 ").append(w.getPendingNow()).append(" 道")
                    .append(" | 本周摘除 ").append(w.getMasteredThisWeek()).append(" 道\n");
        }
        if (snap.getSubjects() != null && !snap.getSubjects().isEmpty()) {
            sb.append("- 科目进度:\n");
            for (WeeklyStatsSnapshot.SubjectProgress s : snap.getSubjects()) {
                sb.append("    ").append(s.getName())
                        .append(" | 章节 ").append(s.getChapterDone()).append("/").append(s.getChapterTotal())
                        .append(" | 本周投入 ").append(s.getWeekMinutes()).append(" 分钟");
                if (s.getDaysLeft() != null) {
                    sb.append(" | 距考试 ").append(s.getDaysLeft()).append(" 天");
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    /** 手机节制: 没有数据时整节省略（AI 不该对"没记录"编故事） */
    private String sectionFocus(WeeklyStatsSnapshot.FocusStats f) {
        if (f == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\n## 手机节制（注意力）\n");
        sb.append("- 娱乐/短视频上限 ").append(f.getLimitMinutes()).append(" 分钟/天");
        if (f.getRecordedDays() != null && f.getRecordedDays() > 0) {
            sb.append(" | 日均 ").append(f.getAvgEntertainmentMinutes()).append(" 分钟")
                    .append(" | 达标 ").append(f.getCompliantDays()).append("/").append(f.getRecordedDays()).append(" 天")
                    .append(" | 合计 ").append(f.getTotalEntertainmentMinutes()).append(" 分钟");
        } else {
            sb.append(" | 本周未录入娱乐时长");
        }
        sb.append("\n");
        if (f.getUrgeTotal() != null && f.getUrgeTotal() > 0) {
            sb.append("- 冲动: 想刷 ").append(f.getUrgeTotal()).append(" 次")
                    .append(" | 忍住 ").append(f.getUrgeResisted()).append(" 次")
                    .append(" | 忍住率 ").append(f.getResistRatePercent()).append("%\n");
        }
        return sb.toString();
    }

    private String sectionJournals(List<Journal> journals, boolean withContent) {
        if (journals == null || journals.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\n## 本周日记\n");
        for (Journal j : journals) {
            sb.append("- ").append(j.getJournalDate());
            if (j.getMood() != null) {
                sb.append(" 心情=").append(moodText(j.getMood()));
            }
            if (withContent) {
                String content = truncate(j.getContent(), aiProperties.getReportCharsPerItem());
                if (!content.isEmpty()) {
                    sb.append("：").append(content);
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String sectionReflections(List<ReflectionResponse> reflections) {
        if (reflections == null || reflections.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\n## 本周习惯心得\n");
        for (ReflectionResponse r : reflections) {
            sb.append("- ").append(r.getHabitName() == null ? "习惯" : r.getHabitName());
            if (r.getResult() != null) {
                sb.append(" | ").append(r.getResult() == 1 ? "完成" : "未完成");
            }
            appendIfPresent(sb, "原因", truncate(r.getReason(), REFLECTION_FIELD_CHARS));
            appendIfPresent(sb, "困难", truncate(r.getObstacle(), REFLECTION_FIELD_CHARS));
            appendIfPresent(sb, "学到", truncate(r.getLearning(), REFLECTION_FIELD_CHARS));
            appendIfPresent(sb, "调整", truncate(r.getAdjustment(), REFLECTION_FIELD_CHARS));
            sb.append("\n");
        }
        return sb.toString();
    }

    // ================= 小工具 =================

    private String join(String... sections) {
        return java.util.Arrays.stream(sections)
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining("\n"));
    }

    private void appendIfPresent(StringBuilder sb, String label, String value) {
        if (value != null && !value.isEmpty()) {
            sb.append(" | ").append(label).append("=").append(value);
        }
    }

    static String truncate(String text, int max) {
        if (text == null || text.isBlank() || max <= 0) {
            return "";
        }
        String t = text.strip();
        return t.length() <= max ? t : t.substring(0, max);
    }

    private String moodText(Integer mood) {
        if (mood == null) {
            return "";
        }
        return Map.of(1, "好", 2, "一般", 3, "疲惫").getOrDefault(mood, "");
    }
}
