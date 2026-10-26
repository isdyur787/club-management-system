SET NAMES utf8mb4;
USE club_management;

-- ========== 1. 修复管理员密码（统一为 123456） ==========
UPDATE sys_user SET password = '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi' WHERE id = 1;

-- ========== 2. 修复管理员权限遗漏（checkin:manage, points:view） ==========
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT 4, id FROM sys_permission WHERE permission_code IN ('checkin:manage', 'points:view');

-- ========== 3. 修复活动日期（2025 → 2026） ==========
UPDATE act_activity SET
    start_time = DATE_ADD(start_time, INTERVAL 1 YEAR),
    end_time = DATE_ADD(end_time, INTERVAL 1 YEAR),
    registration_deadline = DATE_ADD(registration_deadline, INTERVAL 1 YEAR)
WHERE YEAR(start_time) = 2025;

-- ========== 4. 修复审批记录日期 ==========
UPDATE act_approval SET approved_at = DATE_ADD(approved_at, INTERVAL 1 YEAR) WHERE YEAR(approved_at) = 2025;

-- ========== 5. 修复签到记录日期 ==========
UPDATE reg_sign_in SET signed_at = DATE_ADD(signed_at, INTERVAL 1 YEAR) WHERE YEAR(signed_at) = 2025;

-- ========== 6. 修复场地预约日期 ==========
UPDATE res_venue_booking SET booking_date = DATE_ADD(booking_date, INTERVAL 1 YEAR) WHERE YEAR(booking_date) = 2025;

-- ========== 7. 修复设备借用日期 ==========
UPDATE res_equipment_borrow SET
    borrow_time = DATE_ADD(borrow_time, INTERVAL 1 YEAR),
    return_time = IF(return_time IS NOT NULL, DATE_ADD(return_time, INTERVAL 1 YEAR), NULL)
WHERE YEAR(borrow_time) = 2025;

-- ========== 8. 修复通知日期 ==========
UPDATE noti_notification SET read_at = DATE_ADD(read_at, INTERVAL 1 YEAR) WHERE read_at IS NOT NULL AND YEAR(read_at) = 2025;

-- ========== 9. 修复操作日志日期（cost_time 不变） ==========
-- operation_log 无日期列需更新（created_at 由基类自动填充）

-- ========== 10. 修复打卡任务截止日期 ==========
UPDATE act_checkin_task SET deadline = DATE_ADD(deadline, INTERVAL 1 YEAR) WHERE YEAR(deadline) = 2025;

-- ========== 11. 修复打卡汇编入库时间 ==========
UPDATE act_checkin_compilation SET ingested_at = DATE_ADD(ingested_at, INTERVAL 1 YEAR) WHERE ingested_at IS NOT NULL AND YEAR(ingested_at) = 2025;

-- ========== 12. 修复签退日期 ==========
UPDATE act_sign_out SET signed_out_at = DATE_ADD(signed_out_at, INTERVAL 1 YEAR) WHERE YEAR(signed_out_at) = 2025;

-- ========== 13. 修复 AI 报告确认时间 ==========
UPDATE ai_activity_report SET
    confirmed_at = IF(confirmed_at IS NOT NULL AND YEAR(confirmed_at) = 2025, DATE_ADD(confirmed_at, INTERVAL 1 YEAR), confirmed_at),
    ingested_at = IF(ingested_at IS NOT NULL AND YEAR(ingested_at) = 2025, DATE_ADD(ingested_at, INTERVAL 1 YEAR), ingested_at);

-- ========== 14. 修复 AI 会话日期 ==========
UPDATE ai_chat_session SET last_active_at = DATE_ADD(last_active_at, INTERVAL 1 YEAR) WHERE YEAR(last_active_at) = 2025;

-- ========== 15. 为 admin 补充通知数据 ==========
INSERT INTO noti_notification (user_id, title, content, type, related_id, is_read, read_at) VALUES
(1, '系统初始化完成',     '社团管理系统已初始化完成，所有功能已就绪',             'SYSTEM',   NULL, 1, '2026-09-01 10:00:00'),
(1, '新社团待审批',       '「编程技术社」提交了社团成立申请，请及时审批',         'CLUB',     1,    1, '2026-09-01 10:30:00'),
(1, '新活动待审批',       '编程技术社提交了「Python 入门 Workshop」活动申请',     'APPROVAL', 1,    1, '2026-10-10 09:00:00'),
(1, '新活动待审批',       '篮球协会提交了「新生篮球赛」活动申请',                 'APPROVAL', 4,    0, NULL),
(1, '系统维护通知',       '系统将于本周六凌晨 2:00-4:00 进行例行维护',           'SYSTEM',   NULL, 0, NULL);
