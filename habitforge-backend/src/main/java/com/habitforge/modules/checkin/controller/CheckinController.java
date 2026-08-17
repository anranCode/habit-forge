package com.habitforge.modules.checkin.controller;

import com.habitforge.common.result.Result;
import com.habitforge.common.util.SecurityUtils;
import com.habitforge.modules.checkin.dto.CheckinRequest;
import com.habitforge.modules.checkin.dto.CheckinResponse;
import com.habitforge.modules.checkin.entity.Checkin;
import com.habitforge.modules.checkin.service.CheckinService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/checkins")
@RequiredArgsConstructor
public class CheckinController {

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final CheckinService checkinService;

    @PostMapping
    public Result<CheckinResponse> checkin(@Valid @RequestBody CheckinRequest request) {
        CheckinResponse resp = checkinService.checkin(SecurityUtils.getCurrentUserId(), request);
        return Result.success(resp, resp.getNewAchievements().isEmpty()
                ? "打卡成功！继续加油！"
                : "打卡成功！解锁新成就 🎉");
    }

    @GetMapping("/habit/{habitId}")
    public Result<List<Checkin>> listByHabit(@PathVariable String habitId) {
        return Result.success(checkinService.listByHabit(SecurityUtils.getCurrentUserId(), habitId));
    }

    /**
     * 月度打卡数据（热力图）
     * @param month 格式 yyyy-MM，缺省当月；返回日期列表 + 每日打卡次数
     */
    @GetMapping("/month")
    public Result<Map<String, Object>> month(@RequestParam(required = false) String month) {
        YearMonth ym = (month == null || month.isBlank())
                ? YearMonth.now()
                : YearMonth.parse(month, MONTH_FMT);
        String userId = SecurityUtils.getCurrentUserId();
        List<LocalDate> dates = checkinService.monthDates(userId, ym);
        return Result.success(Map.of(
                "month", ym.toString(),
                "dates", dates,
                "total", dates.size()
        ));
    }

    /** 撤销打卡 */
    @DeleteMapping("/{id}")
    public Result<Void> cancel(@PathVariable String id) {
        checkinService.cancelCheckin(SecurityUtils.getCurrentUserId(), id);
        return Result.success(null, "已撤销打卡");
    }
}
