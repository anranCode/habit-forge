package com.habitforge.service;

import com.habitforge.modules.study.service.Sm2Scheduler;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * SM-2 简化算法全矩阵：4 档评分 × 新卡/rep1/rep2/成熟卡 + EF 1.30/3.00 边界
 */
class Sm2SchedulerTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 13);

    private Sm2Scheduler.State state(String ef, int interval, int rep) {
        return new Sm2Scheduler.State(new BigDecimal(ef), interval, rep, 0, TODAY);
    }

    /** 新卡/rep1/rep2/成熟卡 四种起点, EF 统一 2.50 */
    private Sm2Scheduler.State fresh() {
        return state("2.50", 0, 0);
    }

    private Sm2Scheduler.State rep1() {
        return state("2.50", 1, 1);
    }

    private Sm2Scheduler.State rep2() {
        return state("2.50", 3, 2);
    }

    private Sm2Scheduler.State mature() {
        return state("2.50", 8, 3);
    }

    private void assertEf(String expected, Sm2Scheduler.State s) {
        assertEquals(0, s.easeFactor().compareTo(new BigDecimal(expected)),
                "EF 应为 " + expected + " 实为 " + s.easeFactor());
    }

    // ============ rating=1 忘记：全起点同构, 当日重现 ============

    @Test
    void rating1_fromAllStates_resetsAndReappearsToday() {
        for (Sm2Scheduler.State s : new Sm2Scheduler.State[]{fresh(), rep1(), rep2(), mature()}) {
            Sm2Scheduler.State r = Sm2Scheduler.next(1, s, TODAY);
            assertEquals(0, r.repetition());
            assertEquals(0, r.intervalDays());
            assertEquals(TODAY, r.dueDate());           // 当日重现
            assertEquals(s.lapses() + 1, r.lapses());  // lapses+1
            assertEf("2.30", r);                        // 2.50-0.20
        }
    }

    // ============ rating=2 模糊 ============

    @Test
    void rating2_matrix() {
        Sm2Scheduler.State r = Sm2Scheduler.next(2, fresh(), TODAY);
        assertEquals(1, r.repetition());
        assertEquals(1, r.intervalDays());   // max(1, round(0*1.2)=0)
        assertEf("2.35", r);
        assertEquals(TODAY.plusDays(1), r.dueDate());

        r = Sm2Scheduler.next(2, rep1(), TODAY);
        assertEquals(2, r.repetition());
        assertEquals(1, r.intervalDays());   // round(1*1.2=1.2)=1
        assertEf("2.35", r);

        r = Sm2Scheduler.next(2, rep2(), TODAY);
        assertEquals(3, r.repetition());
        assertEquals(4, r.intervalDays());   // round(3*1.2=3.6)=4

        r = Sm2Scheduler.next(2, mature(), TODAY);
        assertEquals(4, r.repetition());
        assertEquals(10, r.intervalDays());  // round(8*1.2=9.6)=10
    }

    // ============ rating=3 记得 ============

    @Test
    void rating3_matrix() {
        Sm2Scheduler.State r = Sm2Scheduler.next(3, fresh(), TODAY);
        assertEquals(1, r.repetition());
        assertEquals(1, r.intervalDays());   // 首评 rep 后=1 → 1 天
        assertEf("2.50", r);                 // EF 不变
        assertEquals(TODAY.plusDays(1), r.dueDate());

        r = Sm2Scheduler.next(3, rep1(), TODAY);
        assertEquals(2, r.repetition());
        assertEquals(3, r.intervalDays());   // rep 后=2 → 3 天

        r = Sm2Scheduler.next(3, rep2(), TODAY);
        assertEquals(3, r.repetition());
        assertEquals(8, r.intervalDays());   // round(3*2.5)=8

        r = Sm2Scheduler.next(3, mature(), TODAY);
        assertEquals(4, r.repetition());
        assertEquals(20, r.intervalDays());  // round(8*2.5)=20
        assertEf("2.50", r);
    }

    // ============ rating=4 轻松 ============

    @Test
    void rating4_matrix() {
        Sm2Scheduler.State r = Sm2Scheduler.next(4, fresh(), TODAY);
        assertEquals(1, r.repetition());
        assertEquals(2, r.intervalDays());   // 首评 rep 后=1 → 2 天
        assertEf("2.65", r);                 // 2.50+0.15
        assertEquals(TODAY.plusDays(2), r.dueDate());

        r = Sm2Scheduler.next(4, rep1(), TODAY);
        assertEquals(2, r.repetition());
        assertEquals(4, r.intervalDays());   // rep 后=2 → 4 天

        r = Sm2Scheduler.next(4, rep2(), TODAY);
        assertEquals(3, r.repetition());
        assertEquals(10, r.intervalDays());  // round(3*2.5*1.3=9.75)=10 HALF_UP

        r = Sm2Scheduler.next(4, mature(), TODAY);
        assertEquals(4, r.repetition());
        assertEquals(26, r.intervalDays());  // round(8*2.5*1.3=26)=26
    }

    // ============ EF 边界 ============

    @Test
    void easeFactor_boundaries() {
        // 下限 1.30: 1.30-0.20 → 夹底
        Sm2Scheduler.State r = Sm2Scheduler.next(1, state("1.30", 5, 2), TODAY);
        assertEf("1.30", r);

        // 下限: 1.40-0.15=1.25 → 夹底
        r = Sm2Scheduler.next(2, state("1.40", 5, 2), TODAY);
        assertEf("1.30", r);

        // 下限不夹合法减法: 1.50-0.20=1.30 恰为下限
        r = Sm2Scheduler.next(1, state("1.50", 5, 2), TODAY);
        assertEf("1.30", r);

        // 上限 3.00: 2.90+0.15=3.05 → 夹顶
        r = Sm2Scheduler.next(4, state("2.90", 5, 2), TODAY);
        assertEf("3.00", r);

        // 已达上限再评 4 保持 3.00
        r = Sm2Scheduler.next(4, state("3.00", 5, 2), TODAY);
        assertEf("3.00", r);

        // 上限附近不夹: 2.80+0.15=2.95
        r = Sm2Scheduler.next(4, state("2.80", 5, 2), TODAY);
        assertEf("2.95", r);
    }

    @Test
    void invalidRating_throws() {
        assertThrows(IllegalArgumentException.class, () -> Sm2Scheduler.next(0, fresh(), TODAY));
        assertThrows(IllegalArgumentException.class, () -> Sm2Scheduler.next(5, fresh(), TODAY));
    }
}
