package com.habitforge.modules.auth.controller;

import com.habitforge.common.result.Result;
import com.habitforge.common.util.SecurityUtils;
import com.habitforge.modules.auth.dto.LoginRequest;
import com.habitforge.modules.auth.dto.LoginResponse;
import com.habitforge.modules.auth.dto.RegisterRequest;
import com.habitforge.modules.auth.service.AuthService;
import com.habitforge.modules.user.dto.UserProfileDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public Result<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success(authService.register(request), "注册成功");
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request), "登录成功");
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = "Authorization", required = false) String token) {
        authService.logout(token);
        return Result.success(null, "已登出");
    }

    @GetMapping("/me")
    public Result<UserProfileDTO> me() {
        return Result.success(authService.getCurrentUser(SecurityUtils.getCurrentUserId()));
    }
}
