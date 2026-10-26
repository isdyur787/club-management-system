USE club_management;

-- ========== V7: 补充复合索引 ==========
-- 基于代码审查发现的查询热点补充

-- 打卡记录表：按任务聚合统计（getTaskByActivityId 高频查询）
ALTER TABLE act_checkin_record ADD INDEX idx_task_rating (task_id, rating);

-- 打卡任务表：按活动查询（多个服务通过 activityId 查 task）
ALTER TABLE act_checkin_task ADD INDEX idx_activity_id (activity_id);
