-- 其他社团的可编辑演示文档；仓库相对路径由 AI 服务解析。
USE club_management;

INSERT INTO ai_knowledge_document (club_id, doc_type, title, source_type, file_path, status)
SELECT d.club_id, d.doc_type, d.title, 'manual', d.file_path, 1
FROM (
    SELECT 6 club_id, 'recruitment' doc_type, '街舞社训练与招新指南（演示）' title, 'docs/knowledge/street-dance-recruitment.md' file_path
    UNION ALL SELECT 6, 'faq', '街舞社常见问题（演示）', 'docs/knowledge/street-dance-faq.md'
    UNION ALL SELECT 7, 'recruitment', '英语角口语活动与招新指南（演示）', 'docs/knowledge/english-corner-recruitment.md'
    UNION ALL SELECT 7, 'faq', '英语角常见问题（演示）', 'docs/knowledge/english-corner-faq.md'
    UNION ALL SELECT 8, 'recruitment', '电竞社参赛与训练指南（演示）', 'docs/knowledge/esports-recruitment.md'
    UNION ALL SELECT 8, 'faq', '电竞社常见问题（演示）', 'docs/knowledge/esports-faq.md'
    UNION ALL SELECT 9, 'recruitment', '书法协会入门与招新指南（演示）', 'docs/knowledge/calligraphy-recruitment.md'
    UNION ALL SELECT 9, 'faq', '书法协会常见问题（演示）', 'docs/knowledge/calligraphy-faq.md'
    UNION ALL SELECT 10, 'recruitment', '环保社志愿活动与招新指南（演示）', 'docs/knowledge/environment-recruitment.md'
    UNION ALL SELECT 10, 'faq', '环保社常见问题（演示）', 'docs/knowledge/environment-faq.md'
    UNION ALL SELECT 11, 'recruitment', '机器人创新社申请与筹备说明（演示）', 'docs/knowledge/robotics-preparation.md'
    UNION ALL SELECT 12, 'recruitment', '校园定向越野社申请与筹备说明（演示）', 'docs/knowledge/orienteering-preparation.md'
    UNION ALL SELECT 13, 'recruitment', '校园戏剧社申请完善指引（演示）', 'docs/knowledge/theater-preparation.md'
    UNION ALL SELECT 14, 'recruitment', '绿色公益研习社申请完善指引（演示）', 'docs/knowledge/green-study-preparation.md'
) d
JOIN club_info c ON c.id = d.club_id AND c.deleted = 0
WHERE NOT EXISTS (
    SELECT 1 FROM ai_knowledge_document existing
    WHERE existing.club_id = d.club_id AND existing.file_path = d.file_path AND existing.deleted = 0
);

-- 已审批社团的活跃成员积分演示，不给管理员或未审批社团虚构积分。
-- 每个社团最多选8名已有成员，保留所有旧流水；描述标记保证重复执行不重复加分。
INSERT INTO member_points_log (club_id, user_id, points, reason, description, created_at)
SELECT members.club_id, members.user_id, awards.points, awards.reason, awards.description,
       DATE_SUB(NOW(), INTERVAL (awards.days_ago + MOD(members.user_id, 3)) DAY)
FROM (
    SELECT cm.club_id, cm.user_id,
           ROW_NUMBER() OVER (PARTITION BY cm.club_id ORDER BY cm.user_id) member_order
    FROM club_member cm
    JOIN club_info c ON c.id = cm.club_id AND c.approval_status = 1 AND c.deleted = 0
    JOIN sys_user u ON u.id = cm.user_id AND u.deleted = 0 AND u.status = 1
    WHERE cm.status = 1 AND cm.deleted = 0
) members
CROSS JOIN (
    SELECT 1 points, 'SIGN_IN' reason, '演示积分：社团活动签到' description, 8 days_ago, 8 member_limit
    UNION ALL SELECT 2, 'CHECKIN_REFLECTION', '演示积分：提交活动心得', 5, 8
    UNION ALL SELECT 1, 'CHECKIN_SUGGESTION', '演示积分：提交改进建议', 3, 8
    UNION ALL SELECT 1, 'SIGN_OUT', '演示积分：完成活动签退', 1, 8
    UNION ALL SELECT 1, 'CHECKIN_PHOTO', '演示积分：上传活动照片', 4, 5
    UNION ALL SELECT 5, 'ADOPTED_SUGGESTION', '演示积分：建议被采纳', 0, 3
) awards
WHERE members.member_order <= awards.member_limit
AND NOT EXISTS (
    SELECT 1 FROM member_points_log existing
    WHERE existing.club_id = members.club_id AND existing.user_id = members.user_id
      AND existing.reason = awards.reason AND existing.description = awards.description
);
