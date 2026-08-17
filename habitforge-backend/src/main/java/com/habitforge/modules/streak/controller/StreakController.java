package com.habitforge.modules.streak.controller;

import com.habitforge.common.result.Result;
import com.habitforge.common.util.SecurityUtils;
import com.habitforge.modules.streak.entity.Streak;
import com.habitforge.modules.streak.service.StreakService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/streaks")
@RequiredArgsConstructor
public class StreakController {

    private final StreakService streakService;

    @GetMapping("/habit/{habitId}")
    public Result<Streak> byHabit(@PathVariable String habitId) {
        return Result.success(streakService.getByHabitId(habitId));
    }

    /** 习惯链排行（按当前链长） */
    @GetMapping("/top")
    public Result<List<Map<String, Object>>> top(@RequestParam(defaultValue = "10") int limit) {
        return Result.success(streakService.topStreaks(SecurityUtils.getCurrentUserId(), limit));
    }
}
