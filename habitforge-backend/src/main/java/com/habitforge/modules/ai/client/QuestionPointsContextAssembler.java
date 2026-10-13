package com.habitforge.modules.ai.client;

import com.habitforge.modules.study.entity.Chapter;
import com.habitforge.modules.study.entity.Question;
import com.habitforge.modules.study.entity.Subject;
import com.habitforge.modules.study.mapper.ChapterMapper;
import com.habitforge.modules.study.mapper.SubjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 考点识别上下文拼装: 科目/章节/题型/题干/选项/参考答案/解析分节。
 * 总预算 5000 字（计划口径）, 超预算先砍解析 —— 解析是提示性材料,
 * 考点判断的主依据是题干+选项+参考答案; 解析被砍后识别质量略降但远好于不识别。
 */
@Component
@RequiredArgsConstructor
public class QuestionPointsContextAssembler {

    private static final int TOTAL_BUDGET_CHARS = 5000;
    private static final int STEM_MAX_CHARS = 1200;
    private static final int OPTIONS_MAX_CHARS = 1200;
    private static final int ANSWER_MAX_CHARS = 1000;
    private static final int ANALYSIS_MAX_CHARS = 1200;

    private final SubjectMapper subjectMapper;
    private final ChapterMapper chapterMapper;

    public String assemble(Question question) {
        StringBuilder base = new StringBuilder();
        base.append("【科目】").append(nameOfSubject(question.getSubjectId())).append('\n');
        base.append("【章节】").append(nameOfChapter(question.getChapterId())).append('\n');
        base.append("【题型】").append(question.getQuestionType()).append('\n');
        base.append("【题干】\n").append(truncate(question.getStem(), STEM_MAX_CHARS)).append('\n');
        String options = question.getOptions();
        if (options != null && !options.isBlank()) {
            // 存的是 JSON 列, 原样给出即可 —— 模型读得懂, 转 DTO 反倒在 ai 域多引一份 study 解析逻辑
            base.append("【选项】\n").append(truncate(options, OPTIONS_MAX_CHARS)).append('\n');
        }
        base.append("【参考答案】\n").append(truncate(question.getAnswer(), ANSWER_MAX_CHARS));

        String analysis = truncate(question.getAnalysis(), ANALYSIS_MAX_CHARS);
        if (!analysis.isEmpty() && base.length() + analysis.length() + 8 <= TOTAL_BUDGET_CHARS) {
            base.append("\n【解析】\n").append(analysis);
        }
        return base.toString();
    }

    private String nameOfSubject(String subjectId) {
        if (subjectId == null || subjectId.isBlank()) {
            return "（未分类）";
        }
        Subject s = subjectMapper.selectById(subjectId);
        return s == null ? "（未知科目）" : s.getName();
    }

    private String nameOfChapter(String chapterId) {
        if (chapterId == null || chapterId.isBlank()) {
            return "（未分章节）";
        }
        Chapter c = chapterMapper.selectById(chapterId);
        return c == null ? "（未知章节）" : c.getName();
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        String t = text.strip();
        return t.length() <= max ? t : t.substring(0, max);
    }
}
