package com.habitforge.modules.user.service.impl;

import com.habitforge.common.constant.AppConstant;
import com.habitforge.common.exception.BusinessException;
import com.habitforge.common.exception.ErrorCode;
import com.habitforge.modules.user.dto.IdentityUpdateDTO;
import com.habitforge.modules.user.dto.UserProfileDTO;
import com.habitforge.modules.user.entity.User;
import com.habitforge.modules.user.mapper.UserMapper;
import com.habitforge.modules.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    @Override
    public UserProfileDTO updateProfile(String userId, IdentityUpdateDTO dto) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (dto.getIdentityGoal() != null) {
            user.setIdentityGoal(dto.getIdentityGoal());
        }
        if (dto.getAvatarUrl() != null) {
            user.setAvatarUrl(dto.getAvatarUrl());
        }
        userMapper.updateById(user);
        return UserProfileDTO.from(user);
    }

    @Override
    public void addPoints(String userId, int delta) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return;
        }
        int points = Math.max(0, (user.getPoints() == null ? 0 : user.getPoints()) + delta);
        user.setPoints(points);
        user.setLevel(points / AppConstant.POINTS_PER_LEVEL + 1);
        userMapper.updateById(user);
    }
}
