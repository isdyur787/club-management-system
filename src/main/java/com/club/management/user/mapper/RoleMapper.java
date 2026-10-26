package com.club.management.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.club.management.user.entity.Role;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RoleMapper extends BaseMapper<Role> {

    @Select("""
            SELECT r.role_code FROM sys_role r
            JOIN sys_user_role ur ON r.id = ur.role_id
            WHERE ur.user_id = #{userId}
            """)
    List<String> findRoleCodesByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM sys_role WHERE role_code = #{roleCode}")
    Role findByRoleCode(@Param("roleCode") String roleCode);

    @Select("""
            SELECT r.* FROM sys_role r
            JOIN sys_user_role ur ON r.id = ur.role_id
            WHERE ur.user_id = #{userId}
            """)
    List<Role> findRolesByUserId(@Param("userId") Long userId);

    /**
     * 检查用户是否拥有指定权限
     */
    @Select("""
            SELECT COUNT(*) FROM sys_user_role ur
            JOIN sys_role_permission rp ON ur.role_id = rp.role_id
            JOIN sys_permission p ON rp.permission_id = p.id
            WHERE ur.user_id = #{userId} AND p.permission_code = #{permissionCode}
            """)
    int countUserPermission(@Param("userId") Long userId, @Param("permissionCode") String permissionCode);

    /**
     * 检查用户是否为管理员角色
     */
    @Select("""
            SELECT COUNT(*) FROM sys_user_role ur
            JOIN sys_role r ON ur.role_id = r.id
            WHERE ur.user_id = #{userId} AND r.role_code = 'ADMIN'
            """)
    int countAdminRole(@Param("userId") Long userId);
}
