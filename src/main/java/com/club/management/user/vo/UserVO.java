package com.club.management.user.vo;

import lombok.Data;

import java.util.List;

@Data
public class UserVO {

    private Long id;
    private String username;
    private String realName;
    private String email;
    private String phone;
    private String avatar;
    private List<RoleVO> roles;

    @Data
    public static class RoleVO {
        private String roleCode;
        private String roleName;
    }
}
