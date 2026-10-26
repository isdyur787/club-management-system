package com.club.management.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.club.management.common.config.SecurityProperties;
import com.club.management.common.enums.RoleType;
import com.club.management.common.enums.UserStatus;
import com.club.management.common.exception.AuthException;
import com.club.management.common.exception.BusinessException;
import com.club.management.common.exception.ConflictException;
import com.club.management.common.exception.NotFoundException;
import com.club.management.common.util.JwtUtil;
import com.club.management.user.dto.LoginDTO;
import com.club.management.user.dto.PasswordUpdateDTO;
import com.club.management.user.dto.RegisterDTO;
import com.club.management.user.entity.Role;
import com.club.management.user.entity.User;
import com.club.management.user.manager.AuthManager;
import com.club.management.user.mapper.RoleMapper;
import com.club.management.user.mapper.UserMapper;
import com.club.management.user.mapper.UserRoleMapper;
import com.club.management.user.service.impl.UserServiceImpl;
import com.club.management.user.vo.LoginVO;
import com.club.management.user.vo.UserVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserServiceImpl 单元测试")
class UserServiceImplTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private RoleMapper roleMapper;
    @Mock
    private UserRoleMapper userRoleMapper;
    @Mock
    private AuthManager authManager;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private UserServiceImpl userService;

    private SecurityProperties securityProperties;

    @BeforeEach
    void setUp() {
        securityProperties = new SecurityProperties();
        // 使用反射注入 SecurityProperties（@RequiredArgsConstructor 只注入 final 字段）
        // SecurityProperties 不是 final 字段，需要手动设置
        try {
            var field = UserServiceImpl.class.getDeclaredField("securityProperties");
            field.setAccessible(true);
            field.set(userService, securityProperties);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ======================== 注册测试 ========================

    @Nested
    @DisplayName("register 方法")
    class RegisterTests {

        private RegisterDTO buildRegisterDTO() {
            RegisterDTO dto = new RegisterDTO();
            dto.setUsername("testuser");
            dto.setPassword("Password1");
            dto.setRealName("测试用户");
            dto.setEmail("test@example.com");
            dto.setPhone("13800138000");
            return dto;
        }

        @Test
        @DisplayName("register_success — 正常注册")
        void register_success() {
            RegisterDTO dto = buildRegisterDTO();

            // Redis 分布式锁成功
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                    .thenReturn(true);

            // 用户名不存在
            when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

            // 密码编码
            when(authManager.encodePassword("Password1")).thenReturn("encoded_password");

            // 模拟 insert 后设置 ID
            when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
                User user = invocation.getArgument(0);
                user.setId(1L);
                return 1;
            });

            // 默认角色
            Role studentRole = new Role();
            studentRole.setId(10L);
            studentRole.setRoleCode(RoleType.STUDENT.name());
            studentRole.setRoleName("学生");
            when(roleMapper.findByRoleCode(RoleType.STUDENT.name())).thenReturn(studentRole);
            when(userRoleMapper.insert(any())).thenReturn(1);

            // 角色查询
            when(roleMapper.findRolesByUserId(1L)).thenReturn(List.of(studentRole));

            // 执行
            UserVO result = userService.register(dto);

            // 断言
            assertThat(result).isNotNull();
            assertThat(result.getUsername()).isEqualTo("testuser");
            assertThat(result.getRealName()).isEqualTo("测试用户");
            assertThat(result.getRoles()).hasSize(1);
            assertThat(result.getRoles().get(0).getRoleCode()).isEqualTo("STUDENT");

            // 验证 Redis 锁被释放
            verify(redisTemplate).delete(anyString());
            // 验证用户被插入
            verify(userMapper).insert(any(User.class));
            // 验证角色关联被创建
            verify(userRoleMapper).insert(any());
        }

        @Test
        @DisplayName("register_duplicate_username — 重复用户名抛出 ConflictException")
        void register_duplicate_username() {
            RegisterDTO dto = buildRegisterDTO();

            // Redis 分布式锁成功
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                    .thenReturn(true);

            // 用户名已存在
            when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

            assertThatThrownBy(() -> userService.register(dto))
                    .isInstanceOf(ConflictException.class)
                    .satisfies(ex -> {
                        ConflictException ce = (ConflictException) ex;
                        assertThat(ce.getErrorCode().getCode()).isEqualTo(40102);
                    });

            // 不应该插入用户
            verify(userMapper, never()).insert(any(User.class));
        }

        @Test
        @DisplayName("register_default_role — 默认分配 STUDENT 角色")
        void register_default_role() {
            RegisterDTO dto = buildRegisterDTO();

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                    .thenReturn(true);
            when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            when(authManager.encodePassword(anyString())).thenReturn("encoded");
            when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
                User user = invocation.getArgument(0);
                user.setId(42L);
                return 1;
            });

            Role studentRole = new Role();
            studentRole.setId(10L);
            studentRole.setRoleCode(RoleType.STUDENT.name());
            studentRole.setRoleName("学生");
            when(roleMapper.findByRoleCode(RoleType.STUDENT.name())).thenReturn(studentRole);
            when(userRoleMapper.insert(any())).thenReturn(1);
            when(roleMapper.findRolesByUserId(42L)).thenReturn(List.of(studentRole));

            UserVO result = userService.register(dto);

            // 验证 findByRoleCode 被调用且传入 STUDENT
            verify(roleMapper).findByRoleCode(RoleType.STUDENT.name());

            // 验证 UserRole 的 roleId 为 STUDENT 角色的 ID
            ArgumentCaptor<com.club.management.user.entity.UserRole> captor =
                    ArgumentCaptor.forClass(com.club.management.user.entity.UserRole.class);
            verify(userRoleMapper).insert(captor.capture());
            assertThat(captor.getValue().getRoleId()).isEqualTo(10L);
            assertThat(captor.getValue().getUserId()).isEqualTo(42L);

            assertThat(result.getRoles()).anySatisfy(roleVO ->
                    assertThat(roleVO.getRoleCode()).isEqualTo("STUDENT"));
        }
    }

    // ======================== 登录测试 ========================

    @Nested
    @DisplayName("login 方法")
    class LoginTests {

        private LoginDTO buildLoginDTO() {
            LoginDTO dto = new LoginDTO();
            dto.setUsername("testuser");
            dto.setPassword("Password1");
            return dto;
        }

        private User buildUser() {
            User user = new User();
            user.setId(1L);
            user.setUsername("testuser");
            user.setPassword("encoded_password");
            user.setRealName("测试用户");
            user.setStatus(UserStatus.ACTIVE.getCode());
            return user;
        }

        @Test
        @DisplayName("login_success — 正常登录")
        void login_success() {
            LoginDTO dto = buildLoginDTO();
            User user = buildUser();

            // Redis 失败计数为 0
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("login_fail:testuser")).thenReturn(null);

            // 用户存在
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);

            // 密码正确
            when(authManager.validatePassword("Password1", "encoded_password")).thenReturn(true);

            // 角色查询
            when(roleMapper.findRoleCodesByUserId(1L)).thenReturn(List.of("STUDENT"));

            // Token 生成
            when(authManager.generateToken(eq(user), anyList())).thenReturn("jwt_token_abc");

            LoginVO result = userService.login(dto);

            assertThat(result).isNotNull();
            assertThat(result.getToken()).isEqualTo("jwt_token_abc");
            assertThat(result.getUserId()).isEqualTo(1L);
            assertThat(result.getUsername()).isEqualTo("testuser");
            assertThat(result.getRealName()).isEqualTo("测试用户");
            assertThat(result.getRoles()).containsExactly("STUDENT");

            // 验证失败计数被清除
            verify(redisTemplate).delete("login_fail:testuser");
        }

        @Test
        @DisplayName("login_wrong_password — 密码错误抛出 BusinessException")
        void login_wrong_password() {
            LoginDTO dto = buildLoginDTO();
            User user = buildUser();

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("login_fail:testuser")).thenReturn(null);
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
            when(authManager.validatePassword("Password1", "encoded_password")).thenReturn(false);

            assertThatThrownBy(() -> userService.login(dto))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException bex = (BusinessException) ex;
                        assertThat(bex.getErrorCode().getCode()).isEqualTo(40103);
                    });

            // 验证失败计数被递增
            verify(valueOperations).increment("login_fail:testuser");
        }

        @Test
        @DisplayName("login_account_locked — 账号锁定抛出 AuthException")
        void login_account_locked() {
            LoginDTO dto = buildLoginDTO();

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            // 失败次数已达上限
            when(valueOperations.get("login_fail:testuser"))
                    .thenReturn(String.valueOf(securityProperties.getLogin().getMaxFailCount()));

            assertThatThrownBy(() -> userService.login(dto))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> {
                        AuthException aex = (AuthException) ex;
                        assertThat(aex.getErrorCode().getCode()).isEqualTo(40006);
                    });
        }

        @Test
        @DisplayName("login_disabled_user — 禁用用户抛出 AuthException")
        void login_disabled_user() {
            LoginDTO dto = buildLoginDTO();
            User user = buildUser();
            user.setStatus(UserStatus.DISABLED.getCode());

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("login_fail:testuser")).thenReturn(null);
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
            when(authManager.validatePassword("Password1", "encoded_password")).thenReturn(true);

            assertThatThrownBy(() -> userService.login(dto))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> {
                        AuthException aex = (AuthException) ex;
                        assertThat(aex.getErrorCode().getCode()).isEqualTo(40002);
                    });
        }
    }

    // ======================== 获取当前用户测试 ========================

    @Nested
    @DisplayName("getCurrentUser 方法")
    class GetCurrentUserTests {

        @Test
        @DisplayName("getCurrentUser_success — 获取当前用户")
        void getCurrentUser_success() {
            User user = new User();
            user.setId(1L);
            user.setUsername("testuser");
            user.setRealName("测试用户");
            user.setEmail("test@example.com");
            user.setPhone("13800138000");
            user.setAvatar("avatar.png");

            Role role = new Role();
            role.setRoleCode("STUDENT");
            role.setRoleName("学生");

            when(userMapper.selectById(1L)).thenReturn(user);
            when(roleMapper.findRolesByUserId(1L)).thenReturn(List.of(role));

            UserVO result = userService.getCurrentUser(1L);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getUsername()).isEqualTo("testuser");
            assertThat(result.getRealName()).isEqualTo("测试用户");
            assertThat(result.getEmail()).isEqualTo("test@example.com");
            assertThat(result.getPhone()).isEqualTo("13800138000");
            assertThat(result.getAvatar()).isEqualTo("avatar.png");
            assertThat(result.getRoles()).hasSize(1);
            assertThat(result.getRoles().get(0).getRoleCode()).isEqualTo("STUDENT");
        }

        @Test
        @DisplayName("getCurrentUser_user_not_found — 用户不存在抛出 NotFoundException")
        void getCurrentUser_user_not_found() {
            when(userMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> userService.getCurrentUser(999L))
                    .isInstanceOf(NotFoundException.class)
                    .satisfies(ex -> {
                        NotFoundException nex = (NotFoundException) ex;
                        assertThat(nex.getErrorCode().getCode()).isEqualTo(40101);
                    });
        }
    }

    // ======================== 修改密码测试 ========================

    @Nested
    @DisplayName("changePassword 方法")
    class ChangePasswordTests {

        private PasswordUpdateDTO buildPasswordDTO() {
            PasswordUpdateDTO dto = new PasswordUpdateDTO();
            dto.setOldPassword("OldPassword1");
            dto.setNewPassword("NewPassword1");
            return dto;
        }

        @Test
        @DisplayName("changePassword_success — 修改密码成功")
        void changePassword_success() {
            PasswordUpdateDTO dto = buildPasswordDTO();
            User user = new User();
            user.setId(1L);
            user.setPassword("encoded_old_password");

            when(userMapper.selectById(1L)).thenReturn(user);
            when(authManager.validatePassword("OldPassword1", "encoded_old_password")).thenReturn(true);
            when(authManager.encodePassword("NewPassword1")).thenReturn("encoded_new_password");
            when(userMapper.updateById(any(User.class))).thenReturn(1);

            userService.changePassword(1L, dto);

            // 验证密码被更新
            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userMapper).updateById(captor.capture());
            assertThat(captor.getValue().getPassword()).isEqualTo("encoded_new_password");
        }

        @Test
        @DisplayName("changePassword_wrong_old_password — 旧密码错误抛出 BusinessException")
        void changePassword_wrong_old_password() {
            PasswordUpdateDTO dto = buildPasswordDTO();
            User user = new User();
            user.setId(1L);
            user.setPassword("encoded_old_password");

            when(userMapper.selectById(1L)).thenReturn(user);
            when(authManager.validatePassword("OldPassword1", "encoded_old_password")).thenReturn(false);

            assertThatThrownBy(() -> userService.changePassword(1L, dto))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException bex = (BusinessException) ex;
                        assertThat(bex.getErrorCode().getCode()).isEqualTo(40103);
                    });

            // 密码不应该被更新
            verify(userMapper, never()).updateById(any());
        }
    }
}
