package com.habitforge.modules.user.dto;

import com.habitforge.modules.user.entity.User;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户信息（不含密码哈希）
 */
@Data
@Builder
public class UserProfileDTO {

    private String id;
    private String username;
    private String email;
    private String identityGoal;
    private String avatarUrl;
    private Integer points;
    private Integer level;
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;

    public static UserProfileDTO from(User user) {
        return UserProfileDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .identityGoal(user.getIdentityGoal())
                .avatarUrl(user.getAvatarUrl())
                .points(user.getPoints())
                .level(user.getLevel())
                .createdAt(user.getCreatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .build();
    }
}
