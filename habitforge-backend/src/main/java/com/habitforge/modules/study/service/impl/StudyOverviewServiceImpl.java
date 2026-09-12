package com.habitforge.modules.study.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.habitforge.modules.study.dto.OverviewResponse;
import com.habitforge.modules.study.dto.SubjectResponse;
import com.habitforge.modules.study.entity.CardReviewLog;
import com.habitforge.modules.study.entity.Flashcard;
import com.habitforge.modules.study.entity.WrongQuestion;
import com.habitforge.modules.study.mapper.CardReviewLogMapper;
import com.habitforge.modules.study.mapper.FlashcardMapper;
import com.habitforge.modules.study.mapper.WrongQuestionMapper;
import com.habitforge.modules.study.service.StudyOverviewService;
import com.habitforge.modules.study.service.SubjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StudyOverviewServiceImpl implements StudyOverviewService {

    private final SubjectService subjectService;
    private final FlashcardMapper flashcardMapper;
    private final WrongQuestionMapper wrongQuestionMapper;
    private final CardReviewLogMapper cardReviewLogMapper;

    @Override
    public OverviewResponse overview(String userId) {
        // subjects 列表内 dueCards/wrongCount 由 SubjectServiceImpl 聚合填充
        List<SubjectResponse> subjects = subjectService.listMine(userId);
        LocalDate today = LocalDate.now();

        // 全局口径直查(含挂在逻辑删科目下的卡, 与科目内聚合允许微小差异)
        Long dueCardsTotal = flashcardMapper.selectCount(new LambdaQueryWrapper<Flashcard>()
                .eq(Flashcard::getUserId, userId)
                .eq(Flashcard::getStatus, Flashcard.STATUS_ACTIVE)
                .le(Flashcard::getDueDate, today));
        Long wrongsTotal = wrongQuestionMapper.selectCount(new LambdaQueryWrapper<WrongQuestion>()
                .eq(WrongQuestion::getUserId, userId)
                .eq(WrongQuestion::getMastered, 0));
        Long reviewedToday = cardReviewLogMapper.selectCount(new LambdaQueryWrapper<CardReviewLog>()
                .eq(CardReviewLog::getUserId, userId)
                .ge(CardReviewLog::getReviewedAt, today.atStartOfDay()));

        return OverviewResponse.builder()
                .subjects(subjects)
                .dueCardsTotal(dueCardsTotal.intValue())
                .wrongsTotal(wrongsTotal.intValue())
                .reviewedToday(reviewedToday.intValue())
                .build();
    }
}
