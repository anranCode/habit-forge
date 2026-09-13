package com.habitforge.modules.user.service;

import com.habitforge.modules.user.dto.IdentityUpdateDTO;
import com.habitforge.modules.user.dto.UserProfileDTO;

public interface UserService {

    UserProfileDTO updateProfile(String userId, IdentityUpdateDTO dto);

    /** 加积分并同步等级（等级 = 每满 100 分升 1 级） */
    void addPoints(String userId, int delta);

    /** 读取身份目标"我想成为..."（AI 上下文只读; 用户不存在返回 null） */
    String getIdentityGoal(String userId);
}
