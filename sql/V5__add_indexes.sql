USE club_management;

-- ========== V5: 性能索引优化 ==========
-- 面试话术：基于慢查询分析，针对高频查询路径补充复合索引

-- V1 已包含活动的 idx_club_status / idx_start_time、报名的 idx_user_id、
-- 通知的 idx_user_read / idx_type 和社团成员的 idx_user_id，无需重复创建。

-- 报名表：按活动+状态查询（报名审核列表）
ALTER TABLE reg_registration ADD INDEX idx_activity_status (activity_id, status);

-- 社团成员表：按社团+角色查询（成员管理页）
ALTER TABLE club_member ADD INDEX idx_club_role (club_id, role);

-- 积分流水表：按社团+用户+时间查询（积分明细页）
ALTER TABLE member_points_log ADD INDEX idx_club_user_time (club_id, user_id, created_at);

-- 签退表：按活动查询（签退统计）
ALTER TABLE act_sign_out ADD INDEX idx_activity_id (activity_id);
