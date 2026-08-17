package com.habitforge.modules.auth.service;

import com.habitforge.modules.auth.dto.LoginRequest;
import com.habitforge.modules.auth.dto.LoginResponse;
import com.habitforge.modules.auth.dto.RegisterRequest;
import com.habitforge.modules.user.dto.UserProfileDTO;

public interface AuthService {

    LoginResponse register(RegisterRequest request);

    LoginResponse login(LoginRequest request);

    /** 登出：将 token 加入黑名单 */
    void logout(String token);

    UserProfileDTO getCurrentUser(String userId);
}
