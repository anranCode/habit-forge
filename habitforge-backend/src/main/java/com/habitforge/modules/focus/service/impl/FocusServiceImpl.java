package com.habitforge.modules.focus.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.habitforge.common.constant.AppConstant;
import com.habitforge.common.exception.BusinessException;
import com.habitforge.common.exception.ErrorCode;
import com.habitforge.common.util.RedisUtil;
import com.habitforge.modules.focus.dto.FocusDayPoint;
import com.habitforge.modules.focus.dto.FocusLogRequest;
import com.habitforge.modules.focus.dto.FocusLogResponse;
import com.habitforge.modules.focus.dto.FocusTrendResponse;
import com.habitforge.modules.focus.dto.UrgeRequest;
import com.habitforge.modules.focus.entity.FocusLog;
import com.habitforge.modules.focus.mapper.FocusLogMapper;
import com.habitforge.modules.focus.service.FocusService;
import com.habitforge.modules.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FocusServiceImpl implements FocusService {

    private static final int URGE_BATCH_MAX = 20;

    private final FocusLogMapper focusLogMapper;
    private final UserService userService;
    private final RedisUtil redisUtil;

    // ================= 查询 =================

    @Override
    public FocusLogResponse today(String userId) {
        LocalDate date = LocalDate.now();
        return responseOf(find(userId, date), date, resolveLimit(userId));
    }

    @Override
    public int resolveLimit(String userId) {
        Integer configured = userService.getFocusLimit(userId);
        int value = configured == null ? AppConstant.FOCUS_DEFAULT_LIMIT_MINUTES : configured;
        return Math.max(0, Math.min(AppConstant.FOCUS_ENTERTAINMENT_MAX_MINUTES, value));
    }

    @Override
    public FocusTrendResponse trend(String userId, LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请提供 from/to 参数");
        }
        if (from.isAfter(to)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "开始日期不能晚于结束日期");
        }
        if (ChronoUnit.DAYS.between(from, to) > AppConstant.FOCUS_MAX_RANGE_DAYS) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "查询区间最长 " + AppConstant.FOCUS_MAX_RANGE_DAYS + " 天");
        }
        int limit = resolveLimit(userId);
        List<FocusDayPoint> days = buildDays(userId, from, to, limit);

        int recorded = 0;
        int compliant = 0;
        int totalMinutes = 0;
        int urgeTotal = 0;
        int urgeResisted = 0;
        for (FocusDayPoint d : days) {
            if (d.getEntertainmentMinutes() != null) {
                recorded++;
                totalMinutes += d.getEntertainmentMinutes();
                if (Boolean.TRUE.equals(d.getCompliant())) {
                    compliant++;
                }
            }
            urgeTotal += d.getUrgeTotal() == null ? 0 : d.getUrgeTotal();
            urgeResisted += d.getUrgeResisted() == null ? 0 : d.getUrgeResisted();
        }

        return FocusTrendResponse.builder()
                .from(from)
                .to(to)
                .limitMinutes(limit)
                .totalDays(days.size())
                .recordedDays(recorded)
                .compliantDays(compliant)
                .avgEntertainmentMinutes(recorded == 0 ? null : totalMinutes / recorded)
                .totalEntertainmentMinutes(totalMinutes)
                .urgeTotal(urgeTotal)
                .urgeResisted(urgeResisted)
                .resistRatePercent(urgeTotal == 0 ? null : urgeResisted * 100 / urgeTotal)
                .days(days)
                .build();
    }

    @Override
    public List<FocusDayPoint> rangeForReport(String userId, LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) {
            return List.of();
        }
        return buildDays(userId, from, to, resolveLimit(userId));
    }

    // ================= 写入 =================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FocusLogResponse saveLog(String userId, FocusLogRequest request) {
        rateLimit(userId);
        LocalDate date = request.getLogDate() != null ? request.getLogDate() : LocalDate.now();
        if (date.isAfter(LocalDate.now())) {
            throw new BusinessException(ErrorCode.FOCUS_DATE_INVALID);
        }
        Integer minutes = request.getEntertainmentMinutes();
        if (minutes == null || minutes < 0 || minutes > AppConstant.FOCUS_ENTERTAINMENT_MAX_MINUTES) {
            throw new BusinessException(ErrorCode.FOCUS_VALUE_INVALID,
                    "娱乐时长需在 0-" + AppConstant.FOCUS_ENTERTAINMENT_MAX_MINUTES + " 分钟之间");
        }
        Integer pickups = request.getPickups();
        if (pickups != null && (pickups < 0 || pickups > AppConstant.FOCUS_PICKUPS_MAX)) {
            throw new BusinessException(ErrorCode.FOCUS_VALUE_INVALID,
                    "拿起次数需在 0-" + AppConstant.FOCUS_PICKUPS_MAX + " 之间");
        }

        int limit = resolveLimit(userId);
        FocusLog existing = find(userId, date);
        boolean before = existing != null && existing.compliant(limit);

        FocusLog target = existing != null ? existing : new FocusLog();
        target.setUserId(userId);
        target.setLogDate(date);
        target.setEntertainmentMinutes(minutes);
        target.setPickups(pickups);
        target.setNote(request.getNote());
        if (existing == null) {
            target.setUrgeTotal(0);
            target.setUrgeResisted(0);
            focusLogMapper.insert(target);
        } else {
            focusLogMapper.updateById(target);
        }

        // 达标状态差量结算（沿 CheckinServiceImpl 的撤销扣分范式）
        boolean after = target.compliant(limit);
        if (!before && after) {
            userService.addPoints(userId, AppConstant.POINTS_PER_FOCUS_COMPLIANT);
            log.info("节制达标 +{} 积分 user={}, date={}, minutes={}", AppConstant.POINTS_PER_FOCUS_COMPLIANT, userId, date, minutes);
        } else if (before && !after) {
            userService.addPoints(userId, -AppConstant.POINTS_PER_FOCUS_COMPLIANT);
            log.info("节制达标撤销 -{} 积分 user={}, date={}, minutes={}", AppConstant.POINTS_PER_FOCUS_COMPLIANT, userId, date, minutes);
        }
        return responseOf(target, date, limit);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FocusLogResponse recordUrge(String userId, UrgeRequest request) {
        rateLimit(userId);
        int count = request.getCount() == null ? 1 : request.getCount();
        if (count < 1 || count > URGE_BATCH_MAX) {
            throw new BusinessException(ErrorCode.FOCUS_VALUE_INVALID, "单次记录 1-" + URGE_BATCH_MAX + " 笔");
        }
        LocalDate date = LocalDate.now();
        int limit = resolveLimit(userId);

        FocusLog target = find(userId, date);
        if (target == null) {
            // 只建壳: entertainmentMinutes 保持 null, 因此不会因为"点了冲动"而白拿达标积分
            target = new FocusLog();
            target.setUserId(userId);
            target.setLogDate(date);
            target.setUrgeTotal(0);
            target.setUrgeResisted(0);
        }
        target.setUrgeTotal((target.getUrgeTotal() == null ? 0 : target.getUrgeTotal()) + count);
        if (Boolean.TRUE.equals(request.getResisted())) {
            target.setUrgeResisted((target.getUrgeResisted() == null ? 0 : target.getUrgeResisted()) + count);
        }
        if (target.getId() == null) {
            focusLogMapper.insert(target);
        } else {
            focusLogMapper.updateById(target);
        }
        return responseOf(target, date, limit);
    }

    @Override
    public FocusLogResponse updateLimit(String userId, Integer limitMinutes) {
        if (limitMinutes == null || limitMinutes < 0
                || limitMinutes > AppConstant.FOCUS_ENTERTAINMENT_MAX_MINUTES) {
            throw new BusinessException(ErrorCode.FOCUS_VALUE_INVALID,
                    "上限需在 0-" + AppConstant.FOCUS_ENTERTAINMENT_MAX_MINUTES + " 分钟之间");
        }
        // 只改设置, 不追溯调整当日已结算的积分（避免"改个设置就把分扣了"的惊吓）
        userService.updateFocusLimit(userId, limitMinutes);
        log.info("每日娱乐上限更新 user={}, limit={}", userId, limitMinutes);
        return today(userId);
    }

    // ================= 内部 =================

    private FocusLog find(String userId, LocalDate date) {
        return focusLogMapper.selectOne(new LambdaQueryWrapper<FocusLog>()
                .eq(FocusLog::getUserId, userId)
                .eq(FocusLog::getLogDate, date)
                .last("LIMIT 1"));
    }

    /** 区间内每一天都出一个点, 没记录的日子 minutes 为 null（前端画断点, 不画 0） */
    private List<FocusDayPoint> buildDays(String userId, LocalDate from, LocalDate to, int limit) {
        List<FocusLog> logs = focusLogMapper.selectList(new LambdaQueryWrapper<FocusLog>()
                .eq(FocusLog::getUserId, userId)
                .between(FocusLog::getLogDate, from, to));
        Map<LocalDate, FocusLog> byDate = new HashMap<>();
        for (FocusLog l : logs) {
            byDate.put(l.getLogDate(), l);
        }
        List<FocusDayPoint> days = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            FocusLog l = byDate.get(d);
            days.add(FocusDayPoint.builder()
                    .date(d)
                    .entertainmentMinutes(l == null ? null : l.getEntertainmentMinutes())
                    .compliant(l != null && l.compliant(limit))
                    .urgeTotal(l == null ? 0 : (l.getUrgeTotal() == null ? 0 : l.getUrgeTotal()))
                    .urgeResisted(l == null ? 0 : (l.getUrgeResisted() == null ? 0 : l.getUrgeResisted()))
                    .build());
        }
        return days;
    }

    private FocusLogResponse responseOf(FocusLog log, LocalDate date, int limit) {
        if (log == null) {
            FocusLog empty = new FocusLog();
            empty.setLogDate(date);
            empty.setUrgeTotal(0);
            empty.setUrgeResisted(0);
            log = empty;
        }
        return FocusLogResponse.of(log, limit);
    }

    /** 写入限流（Redis 故障 fail-open, 沿项目惯例） */
    private void rateLimit(String userId) {
        try {
            if (!redisUtil.tryAcquire("focus:" + userId, AppConstant.FOCUS_RATE_LIMIT, Duration.ofMinutes(1))) {
                throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("限流组件不可用, 放行: {}", e.getMessage());
        }
    }
}
