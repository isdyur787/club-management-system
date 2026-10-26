package com.club.management.user.service;

import com.club.management.user.dto.LoginDTO;
import com.club.management.user.dto.PasswordUpdateDTO;
import com.club.management.user.dto.RegisterDTO;
import com.club.management.user.dto.UserUpdateDTO;
import com.club.management.user.vo.LoginVO;
import com.club.management.user.vo.UserVO;

public interface UserService {

    UserVO register(RegisterDTO dto);

    LoginVO login(LoginDTO dto);

    UserVO getCurrentUser(Long userId);

    void updateProfile(Long userId, UserUpdateDTO dto);

    void changePassword(Long userId, PasswordUpdateDTO dto);

    void logout(String token);
}
