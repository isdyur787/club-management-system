USE club_management;

-- AI 相关权限
INSERT INTO sys_permission (permission_code, permission_name, module) VALUES
('ai:chat', 'AI对话', 'ai'),
('ai:report:manage', '成果报告管理', 'ai'),
('ai:knowledge:manage', '知识库管理', 'ai'),
('checkin:manage', '打卡管理', 'activity'),
('points:view', '查看积分', 'club');

-- 社团负责人增加 AI 和打卡权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 2, id FROM sys_permission WHERE permission_code IN (
    'ai:chat', 'ai:report:manage', 'ai:knowledge:manage',
    'checkin:manage', 'points:view'
);

-- 学生增加 AI 对话和积分查看权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 1, id FROM sys_permission WHERE permission_code IN (
    'ai:chat', 'points:view'
);

-- 指导老师增加 AI 相关权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 3, id FROM sys_permission WHERE permission_code IN (
    'ai:chat', 'ai:report:manage', 'points:view'
);

-- 管理员增加全部 AI 权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 4, id FROM sys_permission WHERE permission_code LIKE 'ai:%';

-- 管理员补充遗漏权限（checkin:manage, points:view 不以 ai: 开头）
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 4, id FROM sys_permission
WHERE permission_code IN ('checkin:manage', 'points:view');
