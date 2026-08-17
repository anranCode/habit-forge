package com.habitforge.modules.user.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新个人资料（身份设定 / 头像）
 */
@Data
public class IdentityUpdateDTO {

    @Size(max = 500, message = "身份设定最长 500 字")
    private String identityGoal;

    @Size(max = 500, message = "头像URL过长")
    private String avatarUrl;
}
