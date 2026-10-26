USE club_management;

-- ========== BUG-2: res_equipment 缺 version 列（@Version 乐观锁需要） ==========
ALTER TABLE res_equipment ADD COLUMN version INT NOT NULL DEFAULT 0;

-- ========== BUG-4: 知识库权限不全（STUDENT 和 TEACHER 缺少 ai:knowledge:manage） ==========
-- 为指导老师添加知识库管理权限
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT 3, id FROM sys_permission WHERE permission_code = 'ai:knowledge:manage';

-- 为学生添加知识库管理权限（查看知识库页面）
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT 1, id FROM sys_permission WHERE permission_code = 'ai:knowledge:manage';

-- ========== CONC-1: 签到防重 — UNIQUE 索引兜底 ==========
ALTER TABLE reg_sign_in ADD UNIQUE INDEX uk_sign_in_activity_user (activity_id, user_id);
