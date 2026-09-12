package com.habitforge.modules.study.service.impl;

import com.habitforge.modules.study.dto.OverviewResponse;
import com.habitforge.modules.study.dto.SubjectResponse;
import com.habitforge.modules.study.service.StudyOverviewService;
import com.habitforge.modules.study.service.SubjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StudyOverviewServiceImpl implements StudyOverviewService {

    private final SubjectService subjectService;

    @Override
    public OverviewResponse overview(String userId) {
        List<SubjectResponse> subjects = subjectService.listMine(userId);
        return OverviewResponse.builder()
                .subjects(subjects)
                .dueCardsTotal(0)   // P1 闪卡模块补齐
                .wrongsTotal(0)     // P1 错题本补齐
                .reviewedToday(0)   // P1 复习日志补齐
                .build();
    }
}
