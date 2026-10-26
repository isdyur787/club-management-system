package com.club.management.user.controller;

import com.club.management.common.annotation.OperationLog;
import com.club.management.common.response.R;
import com.club.management.common.util.SecurityUtil;
import com.club.management.user.dto.*;
import com.club.management.user.service.UserService;
import com.club.management.user.vo.LoginVO;
import com.club.management.user.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class UserController {

    @Autowired
    private UserService userService;

    @PostMapping("/auth/register")
    @OperationLog(module = "用户管理", operation = "用户注册")
    public R<UserVO> register(@Valid @RequestBody RegisterDTO dto) {
        UserVO userVO = userService.register(dto);
        return R.ok(userVO);
    }

    @PostMapping("/auth/login")
    @OperationLog(module = "用户管理", operation = "用户登录", saveRequestData = false)
    public R<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        LoginVO loginVO = userService.login(dto);
        return R.ok(loginVO);
    }

    @PostMapping("/auth/logout")
    @OperationLog(module = "用户管理", operation = "用户注销")
    public R<Void> logout(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            userService.logout(token);
        }
        return R.ok();
    }

    @GetMapping("/auth/me")
    public R<UserVO> getCurrentUser() {
        Long userId = SecurityUtil.getCurrentUserId();
        UserVO userVO = userService.getCurrentUser(userId);
        return R.ok(userVO);
    }

    @PutMapping("/users/me")
    @OperationLog(module = "用户管理", operation = "修改个人信息")
    public R<Void> updateProfile(@Valid @RequestBody UserUpdateDTO dto) {
        Long userId = SecurityUtil.getCurrentUserId();
        userService.updateProfile(userId, dto);
        return R.ok();
    }

    @PutMapping("/users/me/password")
    @OperationLog(module = "用户管理", operation = "修改密码")
    public R<Void> changePassword(@Valid @RequestBody PasswordUpdateDTO dto) {
        Long userId = SecurityUtil.getCurrentUserId();
        userService.changePassword(userId, dto);
        return R.ok();
    }
}
