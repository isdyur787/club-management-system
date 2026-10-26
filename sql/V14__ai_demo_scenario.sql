-- 本地 AI 演示场景：保留原有活动和报告，提供可完整生成与审核的新活动。
USE club_management;

INSERT INTO act_activity
    (club_id, title, description, type, location, start_time, end_time,
     registration_deadline, max_participants, current_participants, budget, status,
     summary, created_by)
SELECT 1, 'AI功能演示：编程交流与复盘',
       '用于演示打卡汇编、AI成果报告、审核和经验检索。成员反馈为演示数据。',
       'CAMPUS', '计算机实验室', DATE_SUB(NOW(), INTERVAL 3 DAY),
       DATE_ADD(DATE_SUB(NOW(), INTERVAL 3 DAY), INTERVAL 2 HOUR),
       DATE_SUB(NOW(), INTERVAL 4 DAY), 30, 3, 300, 'COMPLETED',
       '完成三个小组的项目演示和问题讨论。', 1
WHERE NOT EXISTS (SELECT 1 FROM act_activity WHERE title = 'AI功能演示：编程交流与复盘' AND deleted = 0);

SET @ai_demo_activity = (SELECT id FROM act_activity WHERE title = 'AI功能演示：编程交流与复盘' AND deleted = 0 LIMIT 1);

INSERT INTO act_checkin_task (activity_id, club_id, title, description, deadline, status, created_by)
SELECT @ai_demo_activity, 1, '编程交流复盘反馈（演示）', '请提交评分、收获和改进建议。',
       DATE_SUB(NOW(), INTERVAL 1 DAY), 2, 1
WHERE NOT EXISTS (SELECT 1 FROM act_checkin_task WHERE activity_id = @ai_demo_activity);

SET @ai_demo_task = (SELECT id FROM act_checkin_task WHERE activity_id = @ai_demo_activity);

INSERT INTO reg_registration (activity_id, user_id, status, registered_at)
SELECT @ai_demo_activity, u.id, 2, DATE_SUB(NOW(), INTERVAL 5 DAY)
FROM sys_user u WHERE u.id IN (2, 3, 4)
AND NOT EXISTS (SELECT 1 FROM reg_registration r WHERE r.activity_id = @ai_demo_activity AND r.user_id = u.id);

INSERT INTO reg_sign_in (activity_id, user_id, signed_at)
SELECT @ai_demo_activity, u.id, DATE_SUB(NOW(), INTERVAL 3 DAY)
FROM sys_user u WHERE u.id IN (2, 3, 4)
AND NOT EXISTS (SELECT 1 FROM reg_sign_in r WHERE r.activity_id = @ai_demo_activity AND r.user_id = u.id);

INSERT INTO act_sign_out (activity_id, user_id, signed_out_at)
SELECT @ai_demo_activity, u.id, DATE_ADD(DATE_SUB(NOW(), INTERVAL 3 DAY), INTERVAL 2 HOUR)
FROM sys_user u WHERE u.id IN (2, 3, 4)
AND NOT EXISTS (SELECT 1 FROM act_sign_out r WHERE r.activity_id = @ai_demo_activity AND r.user_id = u.id);

INSERT INTO act_checkin_record
    (task_id, activity_id, club_id, user_id, rating, reflection, problem, suggestion)
SELECT @ai_demo_task, @ai_demo_activity, 1, 2, 5,
       '演示反馈：学会了项目分工和接口联调，完成了一个可运行的小项目。',
       '演示反馈：环境配置占用时间较长。', '演示反馈：提前发放环境检查清单。'
WHERE NOT EXISTS (SELECT 1 FROM act_checkin_record WHERE task_id = @ai_demo_task AND user_id = 2);

INSERT INTO act_checkin_record
    (task_id, activity_id, club_id, user_id, rating, reflection, problem, suggestion)
SELECT @ai_demo_task, @ai_demo_activity, 1, 3, 4,
       '演示反馈：掌握了前后端请求流程，小组讨论很有帮助。',
       '演示反馈：后排看不清投影。', '演示反馈：分享代码仓库和课后资料。'
WHERE NOT EXISTS (SELECT 1 FROM act_checkin_record WHERE task_id = @ai_demo_task AND user_id = 3);

INSERT INTO act_checkin_record
    (task_id, activity_id, club_id, user_id, rating, reflection, problem, suggestion)
SELECT @ai_demo_task, @ai_demo_activity, 1, 4, 4,
       '演示反馈：了解了代码评审方法，希望继续参加实践活动。',
       '演示反馈：练习时间不足。', '演示反馈：下次增加半小时动手练习。'
WHERE NOT EXISTS (SELECT 1 FROM act_checkin_record WHERE task_id = @ai_demo_task AND user_id = 4);
