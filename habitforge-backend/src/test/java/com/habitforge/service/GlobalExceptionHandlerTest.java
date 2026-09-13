package com.habitforge.service;

import com.habitforge.common.exception.GlobalExceptionHandler;
import com.habitforge.common.result.Result;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 路径/查询参数类型转换失败(如 GET /plans/foo) → 400 + 明确中文消息, 不再落兜底 500
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void typeMismatch_localDate_returns400WithDateFormatHint() {
        MethodArgumentTypeMismatchException e =
                new MethodArgumentTypeMismatchException("foo", LocalDate.class, "date", null, null);

        ResponseEntity<Result<Void>> resp = handler.handleTypeMismatch(e);

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertEquals(400, resp.getBody().getCode());
        assertEquals("参数 date 格式不正确，须为 yyyy-MM-dd", resp.getBody().getMessage());
    }

    @Test
    void typeMismatch_otherType_returnsGeneric400() {
        MethodArgumentTypeMismatchException e =
                new MethodArgumentTypeMismatchException("abc", Integer.class, "page", null, null);

        ResponseEntity<Result<Void>> resp = handler.handleTypeMismatch(e);

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertEquals(400, resp.getBody().getCode());
        assertEquals("参数 page 格式不正确", resp.getBody().getMessage());
    }
}
