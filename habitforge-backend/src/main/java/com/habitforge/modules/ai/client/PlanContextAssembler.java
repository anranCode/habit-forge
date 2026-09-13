package com.habitforge.modules.ai.client;

import com.habitforge.common.constant.AppConstant;
import com.habitforge.modules.ai.config.AiProperties;
import com.habitforge.modules.ai.dto.PlanFreeSlotResponse;
import com.habitforge.modules.ai.entity.PlanFreeSlot;
import com.habitforge.modules.habit.dto.HabitResponseDTO;
import com.habitforge.modules.habit.service.HabitService;
import com.habitforge.modules.journal.entity.Journal;
import com.habitforge.modules.journal.service.JournalService;
import com.habitforge.modules.reflection.dto.ReflectionResponse;
import com.habitforge.modules.reflection.service.ReflectionService;
import com.habitforge.modules.study.dto.ChapterResponse;
import com.habitforge.modules.study.dto.OverviewResponse;
import com.habitforge.modules.study.dto.SubjectResponse;
import com.habitforge.modules.study.service.ChapterService;
import com.habitforge.modules.study.service.StudyOverviewService;
import com.habitforge.modules.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * user prompt 分节拼装（无数据的节整节省略; 总长超预算时日记节按 N→N-1→…→1 天降档）
 * 候选 ID 集合 = 写进清单的 habit/subject/chapter，供 PlanJsonParser 防幻觉白名单
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlanContextAssembler {

    private final AiProperties aiProperties;
    private final UserService userService;
    private final HabitService habitService;
    private final StudyOverviewService studyOverviewService;
    private final ChapterService chapterService;
    private final JournalService journalService;
    private final ReflectionService reflectionService;

    /** 拼装结果：user prompt 正文 + 三类候选 ID（白名单） */
    public record Context(String userPrompt, Set<String> habitIds, Set<String> subjectIds, Set<String> chapterIds) {
    }

    public Context assemble(String userId, LocalDate date, List<PlanFreeSlot> slots) {
        Set<String> habitIds = new LinkedHashSet<>();
        Set<String> subjectIds = new LinkedHashSet<>();
        Set<String> chapterIds = new LinkedHashSet<>();

        String header = buildHeader(userId, date, slots);
        String habits = buildHabits(userId, habitIds);
        String study = buildStudy(userId, subjectIds, chapterIds);
        String reflections = buildReflections(userId);

        // 日记是唯一可降档的节: 从配置天数逐降到 1, 仍超预算则接受最后(最小)版本
        String journals = "";
        for (int days = aiProperties.getJournalDays(); days >= 1; days--) {
            journals = buildJournals(userId, days);
            String prompt = join(header, habits, study, journals, reflections);
            if (prompt.length() <= AppConstant.AI_CONTEXT_CHAR_BUDGET) {
                return new Context(prompt, habitIds, subjectIds, chapterIds);
            }
        }
        return new Context(join(header, habits, study, journals, reflections), habitIds, subjectIds, chapterIds);
    }

    // ================= 各节拼装 =================

    private String buildHeader(String userId, LocalDate date, List<PlanFreeSlot> slots) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 今天\n").append(date).append(" ")
                .append(date.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.CHINA)).append("\n");
        String goal = userService.getIdentityGoal(userId);
        if (goal != null && !goal.isBlank()) {
            sb.append("身份目标: 我想成为 ").append(goal.trim()).append("\n");
        }
        sb.append("\n## 今日空闲时段（时间块只能排在以下区间内）\n");
        for (PlanFreeSlot s : slots) {
            sb.append("- ").append(PlanFreeSlotResponse.fmt(s.getStartTime())).append("-")
                    .append(PlanFreeSlotResponse.fmt(s.getEndTime()));
            if (s.getLabel() != null && !s.getLabel().isBlank()) {
                sb.append("（").append(s.getLabel()).append("）");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /** 直接复用 HabitService.listToday 的 HabitResponseDTO（已含 execTime/currentStreak/checkedToday, 零新查询） */
    private String buildHabits(String userId, Set<String> habitIds) {
        List<HabitResponseDTO> habits = habitService.listToday(userId);
        if (habits.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\n## 今日待打卡习惯（habitId 只能从这里挑选）\n");
        for (HabitResponseDTO h : habits) {
            habitIds.add(h.getId());
            sb.append("- habitId=").append(h.getId()).append(" | ").append(h.getName());
            if (h.getExecTime() != null && !h.getExecTime().isBlank()) {
                sb.append(" | 惯常执行时间=").append(h.getExecTime());
            }
            if (h.getCategory() != null && !h.getCategory().isBlank()) {
                sb.append(" | 分类=").append(h.getCategory());
            }
            if (h.getCurrentStreak() != null && h.getCurrentStreak() > 0) {
                sb.append(" | 已连续").append(h.getCurrentStreak()).append("天");
            }
            if (Boolean.TRUE.equals(h.getCheckedToday())) {
                sb.append(" | 今日已打卡");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /** StudyOverviewService 总览 + 每科目未完成章节清单（chapterId 白名单来源） */
    private String buildStudy(String userId, Set<String> subjectIds, Set<String> chapterIds) {
        OverviewResponse overview = studyOverviewService.overview(userId);
        if (overview == null || overview.getSubjects() == null || overview.getSubjects().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\n## 学习任务（subjectId/chapterId 只能从这里挑选）\n");
        for (SubjectResponse s : overview.getSubjects()) {
            subjectIds.add(s.getId());
            sb.append("- subjectId=").append(s.getId()).append(" | ").append(s.getName());
            if (s.getDaysLeft() != null) {
                sb.append(" | 距考试").append(s.getDaysLeft()).append("天");
            }
            if (s.getChapterTotal() != null && s.getChapterTotal() > 0) {
                sb.append(" | 章节进度 ").append(s.getChapterDone()).append("/").append(s.getChapterTotal());
            }
            if (s.getDueCards() != null && s.getDueCards() > 0) {
                sb.append(" | 到期闪卡").append(s.getDueCards()).append("张");
            }
            if (s.getWrongCount() != null && s.getWrongCount() > 0) {
                sb.append(" | 待复习错题").append(s.getWrongCount()).append("道");
            }
            sb.append("\n");
            List<ChapterResponse> chapters = chapterService.listBySubject(userId, s.getId());
            chapters.stream()
                    .filter(c -> !"DONE".equals(c.getStatus()))
                    .limit(MAX_CHAPTERS_PER_SUBJECT)
                    .forEach(c -> {
                        chapterIds.add(c.getId());
                        sb.append("    章节 chapterId=").append(c.getId()).append(" | ").append(c.getName())
                                .append(" | ").append(statusText(c.getStatus())).append("\n");
                    });
        }
        return sb.toString();
    }

    private String buildJournals(String userId, int days) {
        List<Journal> journals = journalService.listRecentWithContent(userId, days);
        if (journals.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\n## 近期日记（心情与状态的信号）\n");
        for (Journal j : journals) {
            sb.append("- ").append(j.getJournalDate());
            if (j.getMood() != null) {
                sb.append(" 心情=").append(moodText(j.getMood()));
            }
            String content = truncate(j.getContent(), aiProperties.getJournalCharsPerDay());
            if (!content.isEmpty()) {
                sb.append("：").append(content);
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String buildReflections(String userId) {
        List<ReflectionResponse> reflections = reflectionService.listRecentByUser(userId, aiProperties.getReflectionLimit());
        if (reflections.isEmpty()) {
            return "";
        }
        int chars = aiProperties.getReflectionChars();
        StringBuilder sb = new StringBuilder("\n## 最近习惯心得\n");
        for (ReflectionResponse r : reflections) {
            sb.append("- ").append(r.getHabitName() == null ? "习惯" : r.getHabitName());
            if (r.getResult() != null) {
                sb.append(" | ").append(r.getResult() == 1 ? "完成" : "未完成");
            }
            appendIfPresent(sb, "原因", truncate(r.getReason(), chars));
            appendIfPresent(sb, "困难", truncate(r.getObstacle(), chars));
            appendIfPresent(sb, "学到", truncate(r.getLearning(), chars));
            appendIfPresent(sb, "调整", truncate(r.getAdjustment(), chars));
            sb.append("\n");
        }
        return sb.toString();
    }

    // ================= 小工具 =================

    private static final int MAX_CHAPTERS_PER_SUBJECT = 20;

    private String join(String... sections) {
        return java.util.Arrays.stream(sections)
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining("\n"));
    }

    private void appendIfPresent(StringBuilder sb, String label, String value) {
        if (!value.isEmpty()) {
            sb.append(" | ").append(label).append("=").append(value);
        }
    }

    /** null 安全截断, 超长截到 max 字 */
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

    private String statusText(String status) {
        if (status == null) {
            return "";
        }
        return Map.of("NOT_STARTED", "未开始", "IN_PROGRESS", "进行中").getOrDefault(status, status);
    }

}
