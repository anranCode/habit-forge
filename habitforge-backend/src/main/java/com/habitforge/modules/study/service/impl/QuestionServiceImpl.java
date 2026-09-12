package com.habitforge.modules.study.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.habitforge.common.exception.BusinessException;
import com.habitforge.common.exception.ErrorCode;
import com.habitforge.modules.study.dto.QuestionCreateRequest;
import com.habitforge.modules.study.dto.QuestionOptionDTO;
import com.habitforge.modules.study.dto.QuestionResponse;
import com.habitforge.modules.study.dto.QuestionUpdateRequest;
import com.habitforge.modules.study.entity.Chapter;
import com.habitforge.modules.study.entity.Question;
import com.habitforge.modules.study.entity.Subject;
import com.habitforge.modules.study.mapper.ChapterMapper;
import com.habitforge.modules.study.mapper.QuestionMapper;
import com.habitforge.modules.study.mapper.SubjectMapper;
import com.habitforge.modules.study.service.QuestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionServiceImpl implements QuestionService {

    private static final Set<String> QUESTION_TYPES = Set.of("SINGLE", "MULTI", "JUDGE", "SHORT");
    /** 来源类型白名单(与 questions.source_type 注释、前端 types 一致) */
    private static final Set<String> SOURCE_TYPES = Set.of("PAST_EXAM", "TEXTBOOK", "CUSTOM", "AI");
    /** 选择题必须有选项, 判断/简答可空 */
    private static final Set<String> OPTION_REQUIRED_TYPES = Set.of("SINGLE", "MULTI");

    private final QuestionMapper questionMapper;
    private final SubjectMapper subjectMapper;
    private final ChapterMapper chapterMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuestionResponse create(String userId, QuestionCreateRequest request) {
        requireOwnedSubject(userId, request.getSubjectId());
        requireChapterInSubject(request.getChapterId(), request.getSubjectId());
        String type = request.getQuestionType() == null ? "SINGLE" : request.getQuestionType();
        validateTypeAndOptions(type, request.getOptions());
        validateSourceType(request.getSourceType());

        Question question = new Question();
        question.setUserId(userId);
        question.setSubjectId(request.getSubjectId());
        question.setChapterId(request.getChapterId());
        question.setQuestionType(type);
        question.setStem(request.getStem());
        question.setOptions(toStoredJson(request.getOptions()));
        question.setAnswer(request.getAnswer());
        question.setAnalysis(request.getAnalysis());
        question.setSourceType(request.getSourceType() == null ? "CUSTOM" : request.getSourceType());
        question.setSourceDetail(request.getSourceDetail());
        question.setDifficulty(request.getDifficulty());
        questionMapper.insert(question);
        log.info("创建题目 user={}, subject={}, type={}", userId, request.getSubjectId(), type);
        return toResponse(question);
    }

    @Override
    public Page<QuestionResponse> page(String userId, String subjectId, String chapterId, String questionType,
                                       String sourceType, Integer difficulty, String keyword, long page, long size) {
        long current = page < 1 ? 1 : page;
        long limit = size < 1 ? 20 : Math.min(size, 100);
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<Question>()
                .eq(Question::getUserId, userId)
                .eq(subjectId != null, Question::getSubjectId, subjectId)
                .eq(chapterId != null, Question::getChapterId, chapterId)
                .eq(StrUtil.isNotBlank(questionType), Question::getQuestionType, questionType)
                .eq(sourceType != null && !sourceType.isBlank(), Question::getSourceType, sourceType)
                .eq(difficulty != null, Question::getDifficulty, difficulty)
                .like(StrUtil.isNotBlank(keyword), Question::getStem, keyword == null ? null : keyword.trim())
                .orderByDesc(Question::getCreatedAt);
        Page<Question> result = questionMapper.selectPage(new Page<>(current, limit), wrapper);

        Page<QuestionResponse> mapped = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        mapped.setRecords(result.getRecords().stream().map(this::toResponse).toList());
        return mapped;
    }

    @Override
    public QuestionResponse getDetail(String userId, String id) {
        return toResponse(loadOwned(userId, id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuestionResponse update(String userId, String id, QuestionUpdateRequest request) {
        Question question = loadOwned(userId, id);
        if (request.getChapterId() != null) {
            requireChapterInSubject(request.getChapterId(), question.getSubjectId());
            question.setChapterId(request.getChapterId());
        }
        if (request.getQuestionType() != null) {
            question.setQuestionType(request.getQuestionType());
        }
        if (request.getStem() != null && !request.getStem().isBlank()) {
            question.setStem(request.getStem());
        }
        if (request.getOptions() != null) {
            validateOptions(request.getOptions());
            question.setOptions(toStoredJson(request.getOptions()));
        }
        if (request.getAnswer() != null && !request.getAnswer().isBlank()) {
            question.setAnswer(request.getAnswer());
        }
        if (request.getAnalysis() != null) {
            question.setAnalysis(request.getAnalysis());
        }
        if (request.getSourceType() != null) {
            validateSourceType(request.getSourceType());
            question.setSourceType(request.getSourceType());
        }
        if (request.getSourceDetail() != null) {
            question.setSourceDetail(request.getSourceDetail());
        }
        if (request.getDifficulty() != null) {
            question.setDifficulty(request.getDifficulty());
        }
        // 类型与选项最终一致性(可能只改了 questionType)
        validateTypeAndOptions(question.getQuestionType(), parseOptions(question.getOptions()));
        questionMapper.updateById(question);
        return toResponse(question);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String userId, String id) {
        Question question = loadOwned(userId, id);
        // 物理删除; wrong_questions FK ON DELETE CASCADE 连带删错题行
        questionMapper.deleteById(question.getId());
        log.info("删除题目 user={}, questionId={}", userId, id);
    }

    // ================= 私有方法 =================

    /** 归属校验(不存在或非本人 → 7007) */
    private Question loadOwned(String userId, String questionId) {
        Question question = questionMapper.selectById(questionId);
        if (question == null || !question.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.QUESTION_NOT_FOUND);
        }
        return question;
    }

    private void validateTypeAndOptions(String type, List<QuestionOptionDTO> options) {
        if (!QUESTION_TYPES.contains(type)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "题型不合法(SINGLE/MULTI/JUDGE/SHORT)");
        }
        if (OPTION_REQUIRED_TYPES.contains(type) && (options == null || options.isEmpty())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "选择题必须提供选项");
        }
        if (options != null) {
            validateOptions(options);
        }
    }

    /** 来源校验: null 走默认 CUSTOM, 非空必须命中白名单 */
    private void validateSourceType(String sourceType) {
        if (sourceType != null && !SOURCE_TYPES.contains(sourceType)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "来源类型不合法(PAST_EXAM/TEXTBOOK/CUSTOM/AI)");
        }
    }

    /** 写入校验: key/text 非空且 key 不重复 */
    private void validateOptions(List<QuestionOptionDTO> options) {
        Set<String> keys = new HashSet<>();
        for (QuestionOptionDTO option : options) {
            if (option == null || StrUtil.isBlank(option.getKey()) || StrUtil.isBlank(option.getText())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "选项的 key 与 text 不能为空");
            }
            if (!keys.add(option.getKey().trim())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "选项 key 重复: " + option.getKey());
            }
        }
    }

    private String toStoredJson(List<QuestionOptionDTO> options) {
        return options == null || options.isEmpty() ? null : JSONUtil.toJsonStr(options);
    }

    private List<QuestionOptionDTO> parseOptions(String storedJson) {
        if (StrUtil.isBlank(storedJson)) {
            return null;
        }
        return JSONUtil.toList(storedJson, QuestionOptionDTO.class);
    }

    private QuestionResponse toResponse(Question question) {
        return QuestionResponse.from(question, parseOptions(question.getOptions()));
    }

    private Subject requireOwnedSubject(String userId, String subjectId) {
        Subject subject = subjectMapper.selectById(subjectId);
        if (subject == null || !subject.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.SUBJECT_NOT_FOUND);
        }
        return subject;
    }

    private void requireChapterInSubject(String chapterId, String subjectId) {
        if (chapterId == null) {
            return;
        }
        Chapter chapter = chapterMapper.selectById(chapterId);
        if (chapter == null || !chapter.getSubjectId().equals(subjectId)) {
            throw new BusinessException(ErrorCode.CHAPTER_NOT_FOUND);
        }
    }
}
