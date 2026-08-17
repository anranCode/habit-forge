package com.habitforge.modules.auth.dto;

import com.habitforge.modules.user.dto.UserProfileDTO;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResponse {

    private String token;

    private UserProfileDTO user;
}
