package com.habitforge.modules.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.habitforge.common.exception.BusinessException;
import com.habitforge.common.exception.ErrorCode;
import com.habitforge.common.util.JwtUtil;
import com.habitforge.common.util.RedisUtil;
import com.habitforge.modules.auth.dto.LoginRequest;
import com.habitforge.modules.auth.dto.LoginResponse;
import com.habitforge.modules.auth.dto.RegisterRequest;
import com.habitforge.modules.auth.service.AuthService;
import com.habitforge.modules.user.dto.UserProfileDTO;
import com.habitforge.modules.user.entity.User;
import com.habitforge.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RedisUtil redisUtil;

    @Override
    public LoginResponse register(RegisterRequest request) {
        // 唯一性校验
        if (userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername())) > 0) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }
        if (userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, request.getEmail())) > 0) {
            throw new BusinessException(ErrorCode.EMAIL_EXISTS);
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setIdentityGoal(request.getIdentityGoal());
        user.setPoints(0);
        user.setLevel(1);
        user.setIsActive(1);
        userMapper.insert(user);
        log.info("新用户注册: {}", user.getUsername());

        return buildLoginResponse(user);
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername()));
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.PASSWORD_WRONG);
        }
        if (user.getIsActive() != null && user.getIsActive() == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已被停用");
        }

        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);
        log.info("用户登录: {}", user.getUsername());

        return buildLoginResponse(user);
    }

    @Override
    public void logout(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (token == null || token.isBlank()) {
            return;
        }
        try {
            long remaining = jwtUtil.getRemainingMillis(token);
            redisUtil.addToBlacklist(token, remaining);
        } catch (Exception e) {
            log.debug("登出时解析 token 失败（可能已过期）: {}", e.getMessage());
        }
    }

    @Override
    public UserProfileDTO getCurrentUser(String userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return UserProfileDTO.from(user);
    }

    private LoginResponse buildLoginResponse(User user) {
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        return new LoginResponse(token, UserProfileDTO.from(user));
    }
}
