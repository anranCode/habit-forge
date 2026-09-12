package com.habitforge.modules.study.service;

import com.habitforge.modules.study.dto.OverviewResponse;

public interface StudyOverviewService {

    /** 学习总览: 科目列表(含章节进度/倒计时) + 到期卡/错题汇总(P0 恒 0) */
    OverviewResponse overview(String userId);
}
