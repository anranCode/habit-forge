package com.habitforge.modules.study.controller;

import com.habitforge.common.result.Result;
import com.habitforge.common.util.SecurityUtils;
import com.habitforge.modules.study.dto.OverviewResponse;
import com.habitforge.modules.study.service.StudyOverviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/study")
@RequiredArgsConstructor
public class StudyController {

    private final StudyOverviewService studyOverviewService;

    /** 学习总览(Home 卡片角标数据源) */
    @GetMapping("/overview")
    public Result<OverviewResponse> overview() {
        return Result.success(studyOverviewService.overview(SecurityUtils.getCurrentUserId()));
    }
}
