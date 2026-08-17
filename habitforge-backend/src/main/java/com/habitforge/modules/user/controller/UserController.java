package com.habitforge.modules.user.controller;

import com.habitforge.common.result.Result;
import com.habitforge.common.util.SecurityUtils;
import com.habitforge.modules.user.dto.IdentityUpdateDTO;
import com.habitforge.modules.user.dto.UserProfileDTO;
import com.habitforge.modules.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** 更新个人资料（身份设定/头像） */
    @PutMapping("/profile")
    public Result<UserProfileDTO> updateProfile(@Valid @RequestBody IdentityUpdateDTO dto) {
        return Result.success(userService.updateProfile(SecurityUtils.getCurrentUserId(), dto), "更新成功");
    }
}
