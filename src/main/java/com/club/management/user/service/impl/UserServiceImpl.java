package com.club.management.user.service.impl;

import com.club.management.common.config.RedisKeyConstants;
import com.club.management.common.config.SecurityProperties;
import com.club.management.common.enums.RoleType;
import com.club.management.common.enums.UserStatus;
import com.club.management.common.exception.*;
import com.club.management.common.util.JwtUtil;
import com.club.management.user.dto.*;
import com.club.management.user.entity.Role;
import com.club.management.user.entity.User;
import com.club.management.user.entity.UserRole;
import com.club.management.user.manager.AuthManager;
import com.club.management.user.mapper.RoleMapper;
import com.club.management.user.mapper.UserMapper;
import com.club.management.user.mapper.UserRoleMapper;
import com.club.management.user.service.UserService;
import com.club.management.user.vo.LoginVO;
import com.club.management.user.vo.UserVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final AuthManager authManager;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;
    private final SecurityProperties securityProperties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO register(RegisterDTO dto) {
        // 分布式锁防止并发注册同一用户名（TOCTOU 竞态修复）
        String lockKey = RedisKeyConstants.REGISTER_LOCK + dto.getUsername();
        Boolean locked = false;
        try {
            locked = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, "1", 30, TimeUnit.SECONDS);
        } catch (Exception e) {
            // Redis 不可用时降级为无锁，依赖数据库唯一约束
            log.warn("Redis operation failed (register lock): {}", e.getMessage());
        }
        if (Boolean.FALSE.equals(locked)) {
            throw new ConflictException(ErrorCode.USER_ALREADY_EXISTS, "用户名（正在被注册）");
        }

        try {
            return doRegister(dto);
        } finally {
            try {
                if (Boolean.TRUE.equals(locked)) {
                    redisTemplate.delete(lockKey);
                }
            } catch (Exception e) {
                // Redis 不可用时忽略
                log.warn("Redis operation failed (register unlock): {}", e.getMessage());
            }
        }
    }

    private UserVO doRegister(RegisterDTO dto) {
        // 检查用户名是否已存在
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, dto.getUsername());
        if (userMapper.selectCount(wrapper) > 0) {
            throw new ConflictException(ErrorCode.USER_ALREADY_EXISTS, "用户名");
        }

        // 检查邮箱是否已存在
        if (dto.getEmail() != null) {
            LambdaQueryWrapper<User> emailWrapper = new LambdaQueryWrapper<>();
            emailWrapper.eq(User::getEmail, dto.getEmail());
            if (userMapper.selectCount(emailWrapper) > 0) {
                throw new ConflictException(ErrorCode.USER_ALREADY_EXISTS, "邮箱");
            }
        }

        // 检查手机号是否已存在
        if (dto.getPhone() != null) {
            LambdaQueryWrapper<User> phoneWrapper = new LambdaQueryWrapper<>();
            phoneWrapper.eq(User::getPhone, dto.getPhone());
            if (userMapper.selectCount(phoneWrapper) > 0) {
                throw new ConflictException(ErrorCode.USER_ALREADY_EXISTS, "手机号");
            }
        }

        // 创建用户
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(authManager.encodePassword(dto.getPassword()));
        user.setRealName(dto.getRealName());
        user.setEmail(dto.getEmail());
        user.setPhone(dto.getPhone());
        user.setStatus(UserStatus.ACTIVE.getCode());
        userMapper.insert(user);

        // 分配默认角色 STUDENT
        Role studentRole = roleMapper.findByRoleCode(RoleType.STUDENT.name());
        if (studentRole == null) {
            throw new NotFoundException(ErrorCode.ROLE_NOT_FOUND);
        }

        UserRole userRole = new UserRole();
        userRole.setUserId(user.getId());
        userRole.setRoleId(studentRole.getId());
        userRoleMapper.insert(userRole);

        // 返回 UserVO（从数据库查询角色信息，避免硬编码）
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        List<Role> roles = roleMapper.findRolesByUserId(user.getId());
        vo.setRoles(roles.stream().map(role -> {
            UserVO.RoleVO roleVO = new UserVO.RoleVO();
            roleVO.setRoleCode(role.getRoleCode());
            roleVO.setRoleName(role.getRoleName());
            return roleVO;
        }).collect(Collectors.toList()));
        return vo;
    }

    @Override
    public LoginVO login(LoginDTO dto) {
        // 查询用户
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, dto.getUsername());
        User user = userMapper.selectOne(wrapper);

        // 统一错误消息（防止用户枚举）
        String loginFailMsg = "用户名或密码错误";

        // 先检查锁定状态（避免对锁定账户执行 bcrypt CPU 密集计算）
        String failKey = RedisKeyConstants.LOGIN_FAIL + dto.getUsername();
        try {
            String failCountStr = redisTemplate.opsForValue().get(failKey);
            int failCount = failCountStr != null ? Integer.parseInt(failCountStr) : 0;
            if (failCount >= securityProperties.getLogin().getMaxFailCount()) {
                throw new AuthException(ErrorCode.ACCOUNT_LOCKED);
            }
        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
            // Redis 不可用时降级：不锁定，继续验证
            log.warn("Redis operation failed (login fail check): {}", e.getMessage());
        }

        // 用户不存在
        if (user == null) {
            throw new BusinessException(ErrorCode.PASSWORD_INCORRECT, loginFailMsg);
        }

        // 校验密码
        if (!authManager.validatePassword(dto.getPassword(), user.getPassword())) {
            // 记录失败次数
            try {
                redisTemplate.opsForValue().increment(failKey);
                redisTemplate.expire(failKey, securityProperties.getLogin().getLockMinutes(), TimeUnit.MINUTES);
            } catch (Exception e) {
                // Redis 不可用时降级：不锁定
                log.warn("Redis operation failed (login fail increment): {}", e.getMessage());
            }
            throw new BusinessException(ErrorCode.PASSWORD_INCORRECT, loginFailMsg);
        }

        // 检查用户状态
        if (user.getStatus() == UserStatus.DISABLED.getCode()) {
            throw new AuthException(ErrorCode.UNAUTHORIZED, "账号已禁用");
        }

        // 清除登录失败计数
        try {
            redisTemplate.delete(failKey);
        } catch (Exception e) {
            // Redis 不可用时忽略
            log.warn("Redis operation failed (clear login fail count): {}", e.getMessage());
        }

        // 查询用户角色
        List<String> roles = roleMapper.findRoleCodesByUserId(user.getId());

        // 生成 Token（将角色列表存入 JWT claims）
        String token = authManager.generateToken(user, roles);

        // 返回 LoginVO
        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setRoles(roles);
        return vo;
    }

    @Override
    public UserVO getCurrentUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new NotFoundException(ErrorCode.USER_NOT_FOUND);
        }

        // 一次性 JOIN 查询获取所有角色，避免 N+1
        List<Role> roles = roleMapper.findRolesByUserId(userId);
        List<UserVO.RoleVO> roleVOs = roles.stream().map(role -> {
            UserVO.RoleVO roleVO = new UserVO.RoleVO();
            roleVO.setRoleCode(role.getRoleCode());
            roleVO.setRoleName(role.getRoleName());
            return roleVO;
        }).collect(Collectors.toList());

        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        vo.setAvatar(user.getAvatar());
        vo.setRoles(roleVOs);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProfile(Long userId, UserUpdateDTO dto) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new NotFoundException(ErrorCode.USER_NOT_FOUND);
        }

        if (dto.getRealName() != null) user.setRealName(dto.getRealName());
        if (dto.getEmail() != null) {
            // 检查新邮箱是否已被其他用户使用
            LambdaQueryWrapper<User> emailWrapper = new LambdaQueryWrapper<>();
            emailWrapper.eq(User::getEmail, dto.getEmail())
                    .ne(User::getId, userId);
            if (userMapper.selectCount(emailWrapper) > 0) {
                throw new ConflictException(ErrorCode.USER_ALREADY_EXISTS, "邮箱");
            }
            user.setEmail(dto.getEmail());
        }
        if (dto.getPhone() != null) {
            // 检查新手机号是否已被其他用户使用
            LambdaQueryWrapper<User> phoneWrapper = new LambdaQueryWrapper<>();
            phoneWrapper.eq(User::getPhone, dto.getPhone())
                    .ne(User::getId, userId);
            if (userMapper.selectCount(phoneWrapper) > 0) {
                throw new ConflictException(ErrorCode.USER_ALREADY_EXISTS, "手机号");
            }
            user.setPhone(dto.getPhone());
        }
        if (dto.getAvatar() != null) user.setAvatar(dto.getAvatar());

        userMapper.updateById(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(Long userId, PasswordUpdateDTO dto) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new NotFoundException(ErrorCode.USER_NOT_FOUND);
        }

        if (!authManager.validatePassword(dto.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_INCORRECT);
        }

        // 新密码不能与旧密码相同
        if (dto.getOldPassword().equals(dto.getNewPassword())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "新密码不能与旧密码相同");
        }

        user.setPassword(authManager.encodePassword(dto.getNewPassword()));
        userMapper.updateById(user);
    }

    @Override
    public void logout(String token) {
        long remaining = jwtUtil.getRemainingExpiration(token);
        if (remaining > 0) {
            redisTemplate.opsForValue().set(
                    RedisKeyConstants.JWT_BLACKLIST + token, "1", remaining, java.util.concurrent.TimeUnit.MILLISECONDS);
        }
    }
}
