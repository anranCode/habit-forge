package com.habitforge.common.util;

import com.habitforge.common.exception.BusinessException;
import com.habitforge.common.exception.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 从 SecurityContext 获取当前登录用户 ID
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static String getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null || !(auth.getPrincipal() instanceof String)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return (String) auth.getPrincipal();
    }
}
