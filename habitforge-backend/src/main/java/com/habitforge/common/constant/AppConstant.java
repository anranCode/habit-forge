package com.habitforge.common.constant;

/**
 * 全局常量
 */
public final class AppConstant {

    private AppConstant() {
    }

    /** 每次打卡获得积分 */
    public static final int POINTS_PER_CHECKIN = 10;

    /** 章节首次完成（非DONE→DONE）获得积分 */
    public static final int POINTS_PER_CHAPTER_DONE = 20;

    /** 每日复习达标获得积分（当日累计复习满 REVIEW_REWARD_MIN_CARDS 张奖励一次） */
    public static final int POINTS_PER_DAILY_REVIEW = 10;

    /** 触发每日复习积分奖励的最小当日复习张数 */
    public static final int REVIEW_REWARD_MIN_CARDS = 5;

    /** 错题连对该次数自动标记掌握（mastered=1） */
    public static final int WRONG_MASTER_STREAK = 2;

    /** 到期复习队列默认上限 */
    public static final int REVIEW_QUEUE_DEFAULT_LIMIT = 50;

    /** 等级阈值：每满 100 分升 1 级 */
    public static final int POINTS_PER_LEVEL = 100;

    /** 里程碑天数 */
    public static final int[] MILESTONE_DAYS = {7, 30, 100};

    /** 里程碑额外积分（与 MILESTONE_DAYS 一一对应） */
    public static final int[] MILESTONE_POINTS = {50, 200, 500};

    /** 里程碑成就名称模板 */
    public static final String MILESTONE_ACHIEVEMENT_NAME = "连续打卡%d天";

    /** 打卡限流：同一用户每秒最多 5 次（允许多习惯连打，防脚本刷数据） */
    public static final int CHECKIN_RATE_LIMIT = 5;

    /** 日记创建限流：同一用户每秒最多 5 次 */
    public static final int JOURNAL_RATE_LIMIT = 5;

    /** 图片上传限流：同一用户每分钟最多 10 张 */
    public static final int UPLOAD_RATE_LIMIT = 10;

    /** 每日计划空闲时段条数上限 */
    public static final int PLAN_FREE_SLOT_MAX = 8;

    /** 计划块标题最大长度（AI prompt 中文约束与后端截断同值） */
    public static final int PLAN_TITLE_MAX_CHARS = 30;

    /** AI 生成并发锁 TTL（秒, 覆盖最坏 45s×2 重试再加解析耗时） */
    public static final long AI_LOCK_TTL_SECONDS = 120L;

    /** AI 每日限流窗口（小时, 25h 让"当日"窗口跨过自然日边界冗余） */
    public static final long AI_RATE_WINDOW_HOURS = 25L;

    /** AI user prompt 字符预算（超预算日记节按 3→2→1 天降档, 目标单次 ≤3k token） */
    public static final int AI_CONTEXT_CHAR_BUDGET = 5000;

    /** 单张图片大小上限（5MB） */
    public static final long IMAGE_MAX_SIZE = 5L * 1024 * 1024;

    /** 允许上传的图片 MIME 类型 */
    public static final java.util.Set<String> IMAGE_CONTENT_TYPES =
            java.util.Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
}
