package com.habitforge.common.constant;

/**
 * 全局常量
 */
public final class AppConstant {

    private AppConstant() {
    }

    /** 每次打卡获得积分 */
    public static final int POINTS_PER_CHECKIN = 10;

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

    /** 单张图片大小上限（5MB） */
    public static final long IMAGE_MAX_SIZE = 5L * 1024 * 1024;

    /** 允许上传的图片 MIME 类型 */
    public static final java.util.Set<String> IMAGE_CONTENT_TYPES =
            java.util.Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
}
