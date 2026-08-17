package com.habitforge.common.result;

import lombok.Data;

import java.time.Instant;

/**
 * 统一响应格式
 */
@Data
public class Result<T> {

    private int code;
    private String message;
    private T data;
    private String timestamp;

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> success(T data) {
        Result<T> r = new Result<>();
        r.setCode(200);
        r.setMessage("success");
        r.setData(data);
        r.setTimestamp(Instant.now().toString());
        return r;
    }

    public static <T> Result<T> success(T data, String message) {
        Result<T> r = success(data);
        r.setMessage(message);
        return r;
    }

    public static <T> Result<T> error(int code, String message) {
        Result<T> r = new Result<>();
        r.setCode(code);
        r.setMessage(message);
        r.setTimestamp(Instant.now().toString());
        return r;
    }
}
