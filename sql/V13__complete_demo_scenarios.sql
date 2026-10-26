USE club_management;

-- 追加缺失的演示场景。通过名称/关联记录判断是否存在，重复执行不会重复插入。
-- 1. 两个待审批社团、两个已驳回社团，保留原有已通过社团。
INSERT INTO club_info
    (club_name, category, description, charter, president_id, approval_status,
     reject_reason, approved_by, approved_at, member_count, created_by)
SELECT demo.club_name, demo.category, demo.description, '遵守学校社团管理规定，定期公开活动计划与经费使用情况。',
       demo.president_id, demo.approval_status, demo.reject_reason,
       IF(demo.approval_status = 2, 1, NULL), IF(demo.approval_status = 2, NOW(), NULL),
       1, demo.president_id
FROM (
    SELECT '机器人创新社' AS club_name, '学术' AS category,
           '开展机器人设计、编程和竞赛交流，申请成立后拟举办入门体验活动。' AS description,
           12 AS president_id, 0 AS approval_status, NULL AS reject_reason
    UNION ALL
    SELECT '校园定向越野社', '体育', '组织校园定向与户外体能训练，已提交成立申请及安全预案。', 13, 0, NULL
    UNION ALL
    SELECT '校园戏剧社', '文艺', '开展剧本阅读、舞台表演及校园戏剧排练，完善申请资料后可重新提交。', 14, 2,
           '章程尚未明确经费管理和财务公示制度，请补充后重新申请。'
    UNION ALL
    SELECT '绿色公益研习社', '志愿', '围绕环境保护开展校园志愿服务，拟完善活动规划后重新申请。', 15, 2,
           '活动计划与现有社团重复，请进一步说明特色项目并补充指导老师信息。'
) demo
JOIN sys_user u ON u.id = demo.president_id AND u.deleted = 0 AND u.status = 1
WHERE NOT EXISTS (SELECT 1 FROM club_info c WHERE c.club_name = demo.club_name);

-- 待审批社团也保留申请人的成员关系，不提前授予已审批社团负责人权限。
INSERT INTO club_member (club_id, user_id, role, joined_at, status, created_by)
SELECT c.id, c.president_id, 'PRESIDENT', NOW(), 1, c.president_id
FROM club_info c
WHERE c.club_name IN ('机器人创新社', '校园定向越野社', '校园戏剧社', '绿色公益研习社')
  AND c.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM club_member m WHERE m.club_id = c.id AND m.user_id = c.president_id);

-- 2. 三个大型活动：两个已通过、一个待审批；日期以导入当日为基准。
INSERT INTO act_activity
    (club_id, title, description, type, location, start_time, end_time,
     registration_deadline, max_participants, current_participants, budget,
     status, version, need_registration, need_approval, created_by)
SELECT c.id, demo.title, demo.description, 'LARGE', demo.location,
       TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL demo.days_ahead DAY), '09:00:00'),
       TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL demo.days_ahead DAY), '18:00:00'),
       TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL (demo.days_ahead - 4) DAY), '23:59:59'),
       demo.capacity, 0, demo.budget, demo.status, 1, 1, 1, c.president_id
FROM (
    SELECT '编程技术社' AS club_name, '校园科技创新嘉年华' AS title,
           '面向全校展示机器人、人工智能与软件作品，设置项目路演、互动体验和成果交流展区。' AS description,
           '大学生活动中心及科技广场' AS location, 14 AS days_ahead, 400 AS capacity,
           12000.00 AS budget, 'APPROVED' AS status
    UNION ALL
    SELECT '篮球协会', '校园运动嘉年华', '联合举办趣味篮球、团体接力和运动体验，面向全校开放报名。',
           '学校体育馆及田径场', 21, 600, 18000.00, 'APPROVED'
    UNION ALL
    SELECT '志愿服务社', '校园公益文化节', '通过公益市集、志愿项目展示和环保实践，集中展示校园志愿服务成果。',
           '校园文化广场', 28, 350, 8000.00, 'PENDING'
) demo
JOIN club_info c ON c.club_name = demo.club_name AND c.deleted = 0 AND c.approval_status = 1
WHERE NOT EXISTS (SELECT 1 FROM act_activity a WHERE a.club_id = c.id AND a.title = demo.title);

-- 大型活动依次需要社长、指导老师、团委三级审批。
INSERT INTO act_approval (activity_id, approver_id, approval_level, result, comment, approved_at, created_by)
SELECT a.id, IF(steps.approval_level = 1, c.president_id, steps.approver_id),
       steps.approval_level, 1, steps.comment, NOW(),
       IF(steps.approval_level = 1, c.president_id, steps.approver_id)
FROM act_activity a
JOIN club_info c ON c.id = a.club_id
JOIN (
    SELECT 1 AS approval_level, NULL AS approver_id, '社长确认活动计划及组织分工。' AS comment
    UNION ALL SELECT 2, 10, '指导老师审核活动方案及安全预案，同意开展。'
    UNION ALL SELECT 3, 1, '团委审核大型活动安排及经费预算，同意开展。'
) steps ON 1 = 1
WHERE a.title IN ('校园科技创新嘉年华', '校园运动嘉年华')
  AND a.type = 'LARGE' AND a.status = 'APPROVED' AND a.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM act_approval p WHERE p.activity_id = a.id AND p.approval_level = steps.approval_level
  );

INSERT INTO act_approval (activity_id, approver_id, approval_level, result, comment, approved_at, created_by)
SELECT a.id, c.president_id, 1, 1, '社长已确认活动计划，等待指导老师审批。', NOW(), c.president_id
FROM act_activity a JOIN club_info c ON c.id = a.club_id
WHERE a.title = '校园公益文化节' AND a.type = 'LARGE' AND a.status = 'PENDING' AND a.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM act_approval p WHERE p.activity_id = a.id AND p.approval_level = 1);

-- 3. 管理员、老师、负责人、学生测试账号均可筛选到活动通知，覆盖已读/未读。
INSERT INTO noti_notification (user_id, title, content, type, related_id, is_read, read_at, created_by)
SELECT u.id, demo.title,
       CONCAT(demo.content, ' 活动时间：', DATE_FORMAT(a.start_time, '%m月%d日 %H:%i'), '，地点：', a.location, '。'),
       'ACTIVITY', a.id, demo.is_read, IF(demo.is_read = 1, NOW(), NULL), 1
FROM sys_user u
JOIN (
    SELECT '校园科技创新嘉年华' AS activity_title, '校园科技创新嘉年华报名开放' AS title,
           '大型科技活动已通过审批，欢迎报名参与作品展示与互动体验。' AS content, 0 AS is_read
    UNION ALL
    SELECT '校园运动嘉年华', '校园运动嘉年华活动提醒', '活动安排已确认，请关注报名截止时间并按时参加。', 0
    UNION ALL
    SELECT '校园科技创新嘉年华', '校园科技创新嘉年华参与指南', '请提前查看活动详情，准备展示材料并遵守现场秩序。', 1
) demo ON 1 = 1
JOIN act_activity a ON a.title = demo.activity_title AND a.type = 'LARGE'
                       AND a.status = 'APPROVED' AND a.deleted = 0
WHERE u.username IN ('admin', 'teacher_li', 'zhangsan', 'lisi') AND u.deleted = 0 AND u.status = 1
  AND NOT EXISTS (
      SELECT 1 FROM noti_notification n
      WHERE n.user_id = u.id AND n.title = demo.title AND n.type = 'ACTIVITY' AND n.related_id = a.id
  );
