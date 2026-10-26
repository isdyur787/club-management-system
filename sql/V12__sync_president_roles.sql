USE club_management;

-- 原种子数据设置了社团 president_id，但遗漏对应的 RBAC 角色，导致负责人
-- 登录后仍只有 STUDENT 权限。按已通过审批的社团补齐关联，保留已有角色。
INSERT INTO sys_user_role (user_id, role_id, club_id)
SELECT c.president_id, r.id, c.id
FROM club_info c
JOIN sys_role r ON r.role_code = 'CLUB_PRESIDENT'
WHERE c.deleted = 0 AND c.approval_status = 1
  AND NOT EXISTS (
      SELECT 1 FROM sys_user_role ur
      WHERE ur.user_id = c.president_id AND ur.role_id = r.id AND ur.club_id = c.id
  );
