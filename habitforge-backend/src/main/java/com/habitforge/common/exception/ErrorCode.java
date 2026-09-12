package com.habitforge.common.exception;

import lombok.Getter;

/**
 * 业务错误码
 */
@Getter
public enum ErrorCode {

    // 通用
    SUCCESS(200, "success"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权访问该资源"),
    NOT_FOUND(404, "资源不存在"),
    TOO_MANY_REQUESTS(429, "操作过于频繁，请稍后再试"),
    INTERNAL_ERROR(500, "服务器内部错误"),

    // 认证 1xxx
    USER_NOT_FOUND(1001, "用户不存在"),
    PASSWORD_WRONG(1002, "用户名或密码错误"),
    USERNAME_EXISTS(1003, "用户名已被占用"),
    EMAIL_EXISTS(1004, "邮箱已被注册"),
    TOKEN_INVALID(1005, "登录已过期，请重新登录"),

    // 习惯 2xxx
    HABIT_NOT_FOUND(2001, "习惯不存在"),
    HABIT_ARCHIVED(2002, "习惯已归档，无法操作"),

    // 打卡 3xxx
    CHECKIN_DUPLICATE(3001, "该习惯今天已经打过卡啦"),
    CHECKIN_NOT_FOUND(3002, "打卡记录不存在"),
    CHECKIN_DATE_INVALID(3003, "打卡日期无效（不能是未来的日子）"),

    // 日记 4xxx
    JOURNAL_NOT_FOUND(4001, "日记不存在"),
    JOURNAL_DUPLICATE(4002, "这一天已经写过日记啦"),
    JOURNAL_DATE_INVALID(4003, "日记日期无效（不能是未来的日子）"),
    IMAGE_NOT_FOUND(4004, "图片不存在"),
    IMAGE_TYPE_INVALID(4005, "仅支持 jpeg/png/webp/gif 图片"),
    IMAGE_SIZE_EXCEEDED(4006, "图片大小不能超过 5MB"),
    STORAGE_ERROR(4007, "图片存储失败，请稍后再试"),

    // 习惯心得 5xxx
    REFLECTION_NOT_FOUND(5001, "心得不存在"),
    REFLECTION_DUPLICATE(5002, "这条心得已经记录过啦"),

    // 学习模块 7xxx（其余 7xxx 由 P1 补齐）
    SUBJECT_NOT_FOUND(7001, "科目不存在"),
    CHAPTER_NOT_FOUND(7002, "章节不存在"),
    CHAPTER_PARENT_INVALID(7003, "父章节无效（不能是自己或自己的子孙，会形成环）"),

    // 学习模块 P1 7xxx（闪卡/笔记/题库/错题本）
    FLASHCARD_NOT_FOUND(7004, "闪卡不存在"),
    REVIEW_RATING_INVALID(7005, "复习评分无效（1-4）"),
    NOTE_NOT_FOUND(7006, "笔记不存在"),
    QUESTION_NOT_FOUND(7007, "题目不存在"),
    WRONG_NOT_FOUND(7008, "错题记录不存在");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
