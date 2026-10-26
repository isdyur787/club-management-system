USE club_management;

-- ========== 初始化角色 ==========
INSERT INTO sys_role (role_name, role_code, description) VALUES
('学生', 'STUDENT', '普通学生角色'),
('社团负责人', 'CLUB_PRESIDENT', '社团社长'),
('指导老师', 'TEACHER', '社团指导老师'),
('系统管理员', 'ADMIN', '系统管理员');

-- ========== 初始化权限 ==========
INSERT INTO sys_permission (permission_code, permission_name, module) VALUES
-- 社团权限
('club:create', '创建社团', 'club'),
('club:edit', '编辑社团', 'club'),
('club:delete', '删除社团', 'club'),
('club:approve', '审批社团', 'club'),
('club:member:manage', '管理成员', 'club'),
-- 活动权限
('activity:create', '创建活动', 'activity'),
('activity:edit', '编辑活动', 'activity'),
('activity:delete', '删除活动', 'activity'),
('activity:approve', '审批活动', 'activity'),
('activity:view', '查看活动', 'activity'),
-- 报名权限
('registration:create', '报名活动', 'registration'),
('registration:manage', '管理报名', 'registration'),
('signin:manage', '管理签到', 'registration'),
-- 资源权限
('resource:manage', '管理资源', 'resource'),
('resource:book', '预约资源', 'resource'),
-- 通知权限
('notification:view', '查看通知', 'notification'),
('notification:send', '发送通知', 'notification'),
-- 统计权限
('statistics:view', '查看统计', 'statistics'),
-- 系统权限
('system:user:manage', '用户管理', 'system'),
('system:role:manage', '角色管理', 'system'),
('system:log:view', '查看日志', 'system');

-- ========== 角色-权限关联 ==========
-- 学生权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 1, id FROM sys_permission WHERE permission_code IN (
    'activity:view', 'registration:create', 'resource:book', 'notification:view'
);

-- 社团负责人权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 2, id FROM sys_permission WHERE permission_code IN (
    'club:edit', 'club:member:manage',
    'activity:create', 'activity:edit', 'activity:view',
    'registration:manage', 'signin:manage',
    'resource:book', 'notification:view', 'notification:send'
);

-- 指导老师权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 3, id FROM sys_permission WHERE permission_code IN (
    'activity:approve', 'activity:view',
    'registration:manage', 'club:member:manage',
    'notification:view', 'notification:send', 'statistics:view'
);

-- 管理员权限（全部）
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 4, id FROM sys_permission;

-- ========== 初始化管理员账号 ==========
-- 密码为 BCrypt 加密后的 "admin123"
INSERT INTO sys_user (username, password, real_name, email, status) VALUES
('admin', '$2a$10$1yDSW8S1DVZaC9RDgfy59uUzo4mzP7NG/QcYRrDNHXc8Bvs5YbJlu', '系统管理员', 'admin@club.com', 1);

-- 管理员关联角色
INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 4);
