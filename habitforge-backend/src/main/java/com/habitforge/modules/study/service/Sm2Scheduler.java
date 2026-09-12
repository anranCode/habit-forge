package com.habitforge.modules.study.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * SM-2 简化算法（静态纯函数, 集中可调）。
 *
 * <p>4 档评分：1=忘记 2=模糊 3=记得 4=轻松
 *
 * <table border="1">
 *   <tr><th>rating</th><th>repetition</th><th>interval</th><th>ease_factor</th><th>due</th></tr>
 *   <tr><td>1</td><td>→0</td><td>→0</td><td>max(1.30, EF−0.20), lapses+1</td><td>今天(当日重现)</td></tr>
 *   <tr><td>2</td><td>+1</td><td>max(1, round(prev×1.2))</td><td>max(1.30, EF−0.15)</td><td>today+interval</td></tr>
 *   <tr><td>3</td><td>+1</td><td>新rep=1→1; rep=2→3; 否则 round(prev×EF)</td><td>不变</td><td>today+interval</td></tr>
 *   <tr><td>4</td><td>+1</td><td>新rep=1→2; rep=2→4; 否则 round(prev×EF×1.3)</td><td>min(3.00, EF+0.15)</td><td>today+interval</td></tr>
 * </table>
 *
 * <p>表中 rep 指评分后的 repetition(即原 rep+1): 新卡原 rep=0 首评后 rep=1 → 走 1/2 天分支。
 */
public final class Sm2Scheduler {

    /** EF 下限 */
    public static final BigDecimal EF_MIN = new BigDecimal("1.30");
    /** EF 上限 */
    public static final BigDecimal EF_MAX = new BigDecimal("3.00");
    /** EF 初始值 */
    public static final BigDecimal EF_DEFAULT = new BigDecimal("2.50");

    private Sm2Scheduler() {
    }

    /** 卡片当前调度状态(纯值对象) */
    public record State(BigDecimal easeFactor, int intervalDays, int repetition, int lapses, LocalDate dueDate) {
    }

    /**
     * 计算一次评分后的新状态。
     *
     * @param rating   1-4(白名单由调用侧校验)
     * @param current  评分前状态
     * @param today    服务端当日(Asia/Shanghai 由容器时区决定)
     */
    public static State next(int rating, State current, LocalDate today) {
        BigDecimal ef = current.easeFactor();
        int prevInterval = current.intervalDays();
        int newRepetition;
        int newInterval;
        int lapses = current.lapses();
        BigDecimal newEf;

        switch (rating) {
            case 1 -> {
                newRepetition = 0;
                newInterval = 0;
                newEf = ef.subtract(new BigDecimal("0.20"));
                lapses += 1;
            }
            case 2 -> {
                newRepetition = current.repetition() + 1;
                newInterval = Math.max(1, multiply(prevInterval, BigDecimal.ONE, new BigDecimal("1.2")));
                newEf = ef.subtract(new BigDecimal("0.15"));
            }
            case 3 -> {
                newRepetition = current.repetition() + 1;
                newInterval = switch (newRepetition) {
                    case 1 -> 1;
                    case 2 -> 3;
                    default -> multiply(prevInterval, ef, BigDecimal.ONE);
                };
                newEf = ef;
            }
            case 4 -> {
                newRepetition = current.repetition() + 1;
                newInterval = switch (newRepetition) {
                    case 1 -> 2;
                    case 2 -> 4;
                    default -> multiply(prevInterval, ef, new BigDecimal("1.3"));
                };
                newEf = ef.add(new BigDecimal("0.15"));
            }
            default -> throw new IllegalArgumentException("rating 必须在 1-4: " + rating);
        }

        newEf = clamp(newEf);
        LocalDate due = newInterval <= 0 ? today : today.plusDays(newInterval);
        return new State(newEf, newInterval, newRepetition, lapses, due);
    }

    /** round(prev × ef × factor), HALF_UP */
    private static int multiply(int prev, BigDecimal ef, BigDecimal factor) {
        return BigDecimal.valueOf(prev)
                .multiply(ef)
                .multiply(factor)
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();
    }

    /** EF 夹到 [1.30, 3.00] */
    private static BigDecimal clamp(BigDecimal ef) {
        if (ef.compareTo(EF_MIN) < 0) {
            return EF_MIN;
        }
        if (ef.compareTo(EF_MAX) > 0) {
            return EF_MAX;
        }
        return ef.setScale(2, RoundingMode.HALF_UP);
    }
}
