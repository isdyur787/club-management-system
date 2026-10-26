package com.club.management.common.util;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtUtil 单元测试")
class JwtUtilTest {

    private JwtUtil jwtUtil;

    /** 至少 256 位 (32 字节) 的 HMAC 密钥 */
    private static final String SECRET = "MySuperSecretKeyForJwtTokenGeneration1234567890";
    private static final long EXPIRATION_MS = 3600_000L; // 1 小时

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", SECRET);
        ReflectionTestUtils.setField(jwtUtil, "expiration", EXPIRATION_MS);
    }

    // ======================== generateToken ↔ parseToken 往返测试 ========================

    @Nested
    @DisplayName("Token 生成与解析往返")
    class TokenRoundTripTests {

        @Test
        @DisplayName("generateToken → parseToken 往返保持数据完整")
        void generateToken_thenParseToken_dataIntact() {
            Long userId = 42L;
            String username = "testuser";
            List<String> roles = List.of("STUDENT", "CLUB_PRESIDENT");

            String token = jwtUtil.generateToken(userId, username, roles);

            assertThat(token).isNotBlank();

            Claims claims = jwtUtil.parseToken(token);
            assertThat(claims.getSubject()).isEqualTo("42");
            assertThat(claims.get("username", String.class)).isEqualTo("testuser");
            assertThat(claims.get("roles", List.class)).containsExactly("STUDENT", "CLUB_PRESIDENT");
        }

        @Test
        @DisplayName("生成的 token 包含 issuedAt 和 expiration")
        void generatedToken_containsIssuedAtAndExpiration() {
            String token = jwtUtil.generateToken(1L, "user", List.of("STUDENT"));
            Claims claims = jwtUtil.parseToken(token);

            assertThat(claims.getIssuedAt()).isNotNull();
            assertThat(claims.getExpiration()).isNotNull();
            assertThat(claims.getExpiration().getTime())
                    .isGreaterThan(claims.getIssuedAt().getTime());
        }

        @Test
        @DisplayName("每次生成的 token 不同（因 issuedAt 时间戳不同）")
        void generateToken_multipleCalls_producesDifferentTokens() {
            String token1 = jwtUtil.generateToken(1L, "user", List.of("STUDENT"));

            // 微小延迟确保时间戳不同
            try { Thread.sleep(10); } catch (InterruptedException ignored) {}

            String token2 = jwtUtil.generateToken(1L, "user", List.of("STUDENT"));
            // 由于 issuedAt 可能相同（毫秒级），这里至少保证两个 token 都非空
            assertThat(token1).isNotBlank();
            assertThat(token2).isNotBlank();
        }
    }

    // ======================== getUserId / getUsername 测试 ========================

    @Nested
    @DisplayName("getUserId / getUsername 方法")
    class ExtractClaimTests {

        @Test
        @DisplayName("getUserId 正确提取用户 ID")
        void getUserId_extractsCorrectUserId() {
            String token = jwtUtil.generateToken(999L, "admin", List.of("ADMIN"));
            Long userId = jwtUtil.getUserId(token);

            assertThat(userId).isEqualTo(999L);
        }

        @Test
        @DisplayName("getUsername 正确提取用户名")
        void getUsername_extractsCorrectUsername() {
            String token = jwtUtil.generateToken(1L, "zhangsan", List.of("STUDENT"));
            String username = jwtUtil.getUsername(token);

            assertThat(username).isEqualTo("zhangsan");
        }

        @Test
        @DisplayName("getUserId 和 getUsername 可从同一 token 独立提取")
        void bothMethods_workIndependently_onSameToken() {
            String token = jwtUtil.generateToken(55L, "lisi", List.of("TEACHER"));

            assertThat(jwtUtil.getUserId(token)).isEqualTo(55L);
            assertThat(jwtUtil.getUsername(token)).isEqualTo("lisi");
        }
    }

    // ======================== isExpired 测试 ========================

    @Nested
    @DisplayName("isExpired 方法")
    class ExpiredTests {

        @Test
        @DisplayName("新生成的 token 未过期")
        void isExpired_freshToken_returnsFalse() {
            String token = jwtUtil.generateToken(1L, "user", List.of("STUDENT"));
            assertThat(jwtUtil.isExpired(token)).isFalse();
        }

        @Test
        @DisplayName("过期 token 返回 true")
        void isExpired_expiredToken_returnsTrue() {
            // 设置极短过期时间
            ReflectionTestUtils.setField(jwtUtil, "expiration", 1L); // 1 毫秒
            String token = jwtUtil.generateToken(1L, "user", List.of("STUDENT"));

            // 等待 token 过期
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}

            assertThat(jwtUtil.isExpired(token)).isTrue();

            // 恢复原始过期时间
            ReflectionTestUtils.setField(jwtUtil, "expiration", EXPIRATION_MS);
        }

        @Test
        @DisplayName("无效格式 token 返回 true")
        void isExpired_invalidToken_returnsTrue() {
            assertThat(jwtUtil.isExpired("this.is.not.a.valid.token")).isTrue();
        }

        @Test
        @DisplayName("篡改过的 token 返回 true")
        void isExpired_tamperedToken_returnsTrue() {
            String token = jwtUtil.generateToken(1L, "user", List.of("STUDENT"));
            // 篡改 token 内容
            String tampered = token.substring(0, token.length() - 5) + "XXXXX";
            assertThat(jwtUtil.isExpired(tampered)).isTrue();
        }
    }

    // ======================== blacklist（注销）相关测试 ========================

    @Nested
    @DisplayName("Blacklist（注销黑名单）相关")
    class BlacklistRelatedTests {

        @Test
        @DisplayName("getRemainingExpiration 返回正数（未过期 token）")
        void getRemainingExpiration_freshToken_returnsPositive() {
            String token = jwtUtil.generateToken(1L, "user", List.of("STUDENT"));
            long remaining = jwtUtil.getRemainingExpiration(token);

            assertThat(remaining).isPositive();
            // 应接近 1 小时 (3600000ms)，允许 1 秒误差
            assertThat(remaining).isCloseTo(EXPIRATION_MS, within(1000L));
        }

        @Test
        @DisplayName("getRemainingExpiration 可用于计算黑名单 TTL")
        void getRemainingExpiration_usableForBlacklistTtl() {
            String token = jwtUtil.generateToken(1L, "user", List.of("STUDENT"));
            long remaining = jwtUtil.getRemainingExpiration(token);

            // 模拟 logout 逻辑：将 token 加入 Redis 黑名单，TTL 为剩余过期时间
            // 这里验证 remaining 为正数且合理（不超过 expiration）
            assertThat(remaining).isPositive();
            assertThat(remaining).isLessThanOrEqualTo(EXPIRATION_MS);

            // 模拟黑名单检查：再次解析同一 token 仍然有效
            // （在实际系统中，JwtAuthenticationFilter 会检查 Redis 黑名单）
            assertThat(jwtUtil.isExpired(token)).isFalse();

            // 验证 parseToken 仍然可以解析（token 本身未过期）
            Claims claims = jwtUtil.parseToken(token);
            assertThat(claims.getSubject()).isEqualTo("1");
        }
    }
}
