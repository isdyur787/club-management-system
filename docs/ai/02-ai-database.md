# AI 数据库扩展设计

## 1. 新增表总览

| 表名 | 模块 | 说明 |
|---|---|---|
| act_checkin_task | 活动 | 打卡任务表（团长发起活动时创建） |
| act_checkin_record | 活动 | 打卡记录表（成员提交的打卡内容） |
| act_checkin_compilation | 活动 | 打卡汇编文档表（打卡截止后自动生成） |
| act_sign_out | 活动 | 签退记录表 |
| ai_activity_report | AI | 活动成果报告表 |
| ai_chat_session | AI | 对话会话表 |
| ai_knowledge_document | AI | 知识库文档元数据表 |
| member_points_log | 成员 | 积分流水表 |

---

## 2. 表结构详细设计

### 2.1 act_checkin_task（打卡任务表）

团长发起活动时设定打卡要求，一个活动对应一个打卡任务。

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| activity_id | BIGINT | NOT NULL, UNIQUE | 关联活动 ID |
| club_id | BIGINT | NOT NULL | 所属社团 ID |
| title | VARCHAR(100) | NOT NULL | 打卡任务标题 |
| description | TEXT | | 打卡说明 |
| require_reflection | TINYINT | NOT NULL, DEFAULT 1 | 是否要求提交心得：0=否, 1=是 |
| require_photo | TINYINT | NOT NULL, DEFAULT 0 | 是否要求提交照片：0=否, 1=是 |
| require_suggestion | TINYINT | NOT NULL, DEFAULT 0 | 是否要求提交建议：0=否, 1=是 |
| deadline | DATETIME | NOT NULL | 打卡截止时间（活动结束后 N 小时） |
| status | TINYINT | NOT NULL, DEFAULT 0 | 状态：0=未开始, 1=进行中, 2=已截止, 3=已结算 |
| created_by | BIGINT | | 创建人 ID |
| created_at | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_by | BIGINT | | 最后修改人 ID |
| updated_at | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP ON UPDATE | 修改时间 |
| deleted | TINYINT | NOT NULL, DEFAULT 0 | 逻辑删除 |

**索引：**
- `uk_activity_id` UNIQUE (`activity_id`)
- `idx_club_id` (`club_id`)
- `idx_status` (`status`)
- `idx_deadline` (`deadline`)

---

### 2.2 act_checkin_record（打卡记录表）

成员在活动中提交的打卡内容，按活动+成员维度归档。

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| task_id | BIGINT | NOT NULL | 关联打卡任务 ID |
| activity_id | BIGINT | NOT NULL | 关联活动 ID（冗余，便于查询） |
| club_id | BIGINT | NOT NULL | 所属社团 ID |
| user_id | BIGINT | NOT NULL | 提交人 ID |
| rating | TINYINT | | 活动评分（1-5） |
| reflection | TEXT | | 心得/感悟（我的收获） |
| problem | TEXT | | 遇到的问题（活动中体验不好的地方） |
| suggestion | TEXT | | 改进建议（下次怎么办得更好） |
| photo_urls | TEXT | | 照片 URL 列表（JSON 数组） |
| image_descriptions | TEXT | | 图片描述（JSON 数组，多模态 LLM 生成，与 photo_urls 一一对应） |
| file_urls | TEXT | | 附件 URL 列表（JSON 数组，成员上传的文档） |
| is_adopted | TINYINT | NOT NULL, DEFAULT 0 | 建议是否被团长采纳：0=未采纳, 1=已采纳 |
| status | TINYINT | NOT NULL, DEFAULT 1 | 状态：0=已删除, 1=正常 |
| created_by | BIGINT | | 创建人 ID |
| created_at | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_by | BIGINT | | 最后修改人 ID |
| updated_at | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP ON UPDATE | 修改时间 |
| deleted | TINYINT | NOT NULL, DEFAULT 0 | 逻辑删除 |

**索引：**
- `uk_task_user` UNIQUE (`task_id`, `user_id`) — 每人每个打卡任务只能提交一次
- `idx_activity_id` (`activity_id`)
- `idx_club_id` (`club_id`)
- `idx_user_id` (`user_id`)

---

### 2.3 act_checkin_compilation（打卡汇编文档表）

打卡截止后，系统自动将该活动下所有成员的打卡内容汇编为一份结构化文档。此表存储汇编结果。

汇编文档是 Agent 成果撰写的输入源，同时也作为独立文档入库 RAG（按维度切块），供后续活动经验检索。

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| task_id | BIGINT | NOT NULL, UNIQUE | 关联打卡任务 ID |
| activity_id | BIGINT | NOT NULL | 关联活动 ID |
| club_id | BIGINT | NOT NULL | 所属社团 ID |
| content | TEXT | NOT NULL | 汇编文档正文（Markdown 格式） |
| file_path | VARCHAR(500) | | Word 文件存储路径 |
| member_count | INT | NOT NULL | 参与汇编的成员数 |
| avg_rating | DECIMAL(2,1) | | 平均评分 |
| problem_summary | TEXT | | 问题汇总（从所有成员问题中提取的高频问题） |
| suggestion_summary | TEXT | | 建议汇总（从所有成员建议中归类的高频建议） |
| chroma_doc_ids | TEXT | | 入库 ChromaDB 后的文档 ID 列表（JSON 数组，用于删除/重建索引） |
| is_ingested | TINYINT | NOT NULL, DEFAULT 0 | 是否已入库知识库：0=未入库, 1=已入库 |
| ingested_at | DATETIME | | 入库时间 |
| created_by | BIGINT | | 创建人 ID（系统自动创建为 NULL） |
| created_at | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_by | BIGINT | | 最后修改人 ID |
| updated_at | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP ON UPDATE | 修改时间 |
| deleted | TINYINT | NOT NULL, DEFAULT 0 | 逻辑删除 |

**索引：**
- `uk_task_id` UNIQUE (`task_id`)
- `idx_activity_id` (`activity_id`)
- `idx_club_id` (`club_id`)
- `idx_is_ingested` (`is_ingested`)

**汇编文档结构：**

```
《{活动标题}》成员反馈汇编

一、基本信息
   活动时间/地点/参与人数/打卡提交数/平均评分

二、成员反馈明细
   2.1 张三（评分：5/5）
   【收获】...
   【问题】...
   【建议】...
   【附件】照片 X 张

   2.2 李四（评分：4/5）
   ...

三、问题汇总
   高频问题：时间不足(3次)、设备问题(2次)...

四、建议汇总
   高频建议：增加实操(4次)、提供资料(3次)...

五、附件清单
   照片文件列表
```

---

### 2.4 act_sign_out（签退记录表）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| activity_id | BIGINT | NOT NULL | 活动 ID |
| user_id | BIGINT | NOT NULL | 签退人 ID |
| signed_out_at | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 签退时间 |
| created_by | BIGINT | | 创建人 ID |
| created_at | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_by | BIGINT | | 最后修改人 ID |
| updated_at | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP ON UPDATE | 修改时间 |
| deleted | TINYINT | NOT NULL, DEFAULT 0 | 逻辑删除 |

**索引：**
- `uk_activity_user` UNIQUE (`activity_id`, `user_id`) — 防重复签退
- `idx_user_id` (`user_id`)

---

### 2.5 ai_activity_report（活动成果报告表）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| activity_id | BIGINT | NOT NULL, UNIQUE | 关联活动 ID |
| club_id | BIGINT | NOT NULL | 所属社团 ID |
| title | VARCHAR(200) | NOT NULL | 报告标题 |
| content | TEXT | NOT NULL | 报告正文（Markdown 格式） |
| file_path | VARCHAR(500) | | Word 文件存储路径 |
| avg_rating | DECIMAL(2,1) | | 平均评分（从打卡记录聚合） |
| participant_count | INT | | 参与人数（从打卡记录统计） |
| checkin_count | INT | | 打卡提交数 |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'DRAFT' | 状态：DRAFT(草稿), CONFIRMED(已确认), REJECTED(已打回) |
| reject_reason | TEXT | | 打回原因（团长填写） |
| revision_count | INT | NOT NULL, DEFAULT 0 | 修改次数 |
| confirmed_by | BIGINT | | 确认人 ID（团长） |
| confirmed_at | DATETIME | | 确认时间 |
| is_ingested | TINYINT | NOT NULL, DEFAULT 0 | 是否已入库知识库：0=未入库, 1=已入库 |
| ingested_at | DATETIME | | 入库时间 |
| created_by | BIGINT | | 创建人 ID |
| created_at | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_by | BIGINT | | 最后修改人 ID |
| updated_at | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP ON UPDATE | 修改时间 |
| deleted | TINYINT | NOT NULL, DEFAULT 0 | 逻辑删除 |

**索引：**
- `uk_activity_id` UNIQUE (`activity_id`)
- `idx_club_id` (`club_id`)
- `idx_status` (`status`)
- `idx_is_ingested` (`is_ingested`)

---

### 2.6 ai_chat_session（对话会话表）

记录用户的对话历史，用于多轮对话上下文。实际对话内容存储在 Redis 中。

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| session_id | VARCHAR(50) | NOT NULL, UNIQUE | 会话唯一标识（UUID） |
| club_id | BIGINT | NOT NULL | 关联社团 ID |
| user_id | BIGINT | NOT NULL | 用户 ID |
| title | VARCHAR(100) | | 会话标题（自动取第一条消息摘要） |
| message_count | INT | NOT NULL, DEFAULT 0 | 消息数量 |
| last_active_at | DATETIME | NOT NULL | 最后活跃时间 |
| status | TINYINT | NOT NULL, DEFAULT 1 | 状态：0=已关闭, 1=活跃 |
| created_at | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP ON UPDATE | 修改时间 |

**索引：**
- `uk_session_id` UNIQUE (`session_id`)
- `idx_user_club` (`user_id`, `club_id`)
- `idx_last_active` (`last_active_at`)

---

### 2.7 ai_knowledge_document（知识库文档元数据表）

记录已入库到 ChromaDB 的文档元数据，便于管理和追溯。

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| club_id | BIGINT | NOT NULL | 所属社团 ID |
| doc_type | VARCHAR(30) | NOT NULL | 文档类型：charter/recruitment/faq/checkin_compilation/activity_report |
| title | VARCHAR(200) | NOT NULL | 文档标题 |
| source_type | VARCHAR(20) | NOT NULL | 来源类型：upload(上传) / system(系统生成) |
| source_id | BIGINT | | 来源 ID（activity_report 对应 report_id） |
| file_path | VARCHAR(500) | | 原始文件路径（上传文件） |
| chunk_count | INT | NOT NULL, DEFAULT 0 | 分块数量 |
| chroma_ids | TEXT | | ChromaDB 中的文档 ID 列表（JSON 数组，用于删除） |
| status | TINYINT | NOT NULL, DEFAULT 1 | 状态：0=已删除, 1=正常 |
| created_by | BIGINT | | 创建人 ID |
| created_at | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_by | BIGINT | | 最后修改人 ID |
| updated_at | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP ON UPDATE | 修改时间 |
| deleted | TINYINT | NOT NULL, DEFAULT 0 | 逻辑删除 |

**索引：**
- `idx_club_type` (`club_id`, `doc_type`)
- `idx_source` (`source_type`, `source_id`)
- `idx_status` (`status`)

---

### 2.8 member_points_log（积分流水表）

记录成员的积分变动，只记流水不做消费（消费出口后续迭代）。

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| club_id | BIGINT | NOT NULL | 所属社团 ID |
| user_id | BIGINT | NOT NULL | 用户 ID |
| points | INT | NOT NULL | 积分变动值（正数=获得，负数=扣除） |
| reason | VARCHAR(50) | NOT NULL | 变动原因编码：SIGN_IN/CHECKIN_REFLECTION/CHECKIN_PHOTO/ADOPTED_SUGGESTION |
| description | VARCHAR(200) | NOT NULL | 变动描述（人类可读） |
| related_activity_id | BIGINT | | 关联活动 ID |
| related_checkin_id | BIGINT | | 关联打卡记录 ID |
| created_at | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 创建时间 |

**索引：**
- `idx_club_user` (`club_id`, `user_id`)
- `idx_user_id` (`user_id`)
- `idx_reason` (`reason`)
- `idx_activity_id` (`related_activity_id`)
- `idx_created_at` (`created_at`)

---

## 3. ER 关系扩展

```
act_activity ──1:1── act_checkin_task ──1:N── act_checkin_record
act_checkin_task ──1:1── act_checkin_compilation
act_activity ──1:N── act_sign_out
act_activity ──1:1── ai_activity_report

club_info ──1:N── ai_chat_session
sys_user ──1:N── ai_chat_session

club_info ──1:N── ai_knowledge_document

club_member ──1:N── member_points_log
act_checkin_record ──1:N── member_points_log
```

---

## 4. 积分规则定义

| reason 编码 | 积分值 | 触发条件 |
|---|---|---|
| SIGN_IN | +1 | 签到成功 |
| SIGN_OUT | +1 | 签退成功 |
| CHECKIN_REFLECTION | +2 | 提交打卡心得 |
| CHECKIN_PHOTO | +1 | 打卡含照片（与 REFLECTION 叠加） |
| CHECKIN_SUGGESTION | +1 | 提交打卡建议 |
| ADOPTED_SUGGESTION | +5 | 建议被团长采纳 |

**积分计算时机：** 打卡截止后由系统统一结算，批量写入积分流水。

---

## 5. SQL 扩展脚本

### 5.1 V3__ai_tables.sql

```sql
USE club_management;

-- ========== 打卡任务表 ==========

CREATE TABLE act_checkin_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    activity_id BIGINT NOT NULL,
    club_id BIGINT NOT NULL,
    title VARCHAR(100) NOT NULL,
    description TEXT,
    require_reflection TINYINT NOT NULL DEFAULT 1 COMMENT '0=否,1=是',
    require_photo TINYINT NOT NULL DEFAULT 0 COMMENT '0=否,1=是',
    require_suggestion TINYINT NOT NULL DEFAULT 0 COMMENT '0=否,1=是',
    deadline DATETIME NOT NULL,
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0=未开始,1=进行中,2=已截止,3=已结算',
    created_by BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_activity_id (activity_id),
    INDEX idx_club_id (club_id),
    INDEX idx_status (status),
    INDEX idx_deadline (deadline)
) ENGINE=InnoDB COMMENT='打卡任务表';

-- ========== 打卡记录表 ==========

CREATE TABLE act_checkin_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    activity_id BIGINT NOT NULL,
    club_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    rating TINYINT COMMENT '1-5分',
    reflection TEXT COMMENT '心得/感悟（我的收获）',
    problem TEXT COMMENT '遇到的问题（活动中体验不好的地方）',
    suggestion TEXT COMMENT '改进建议（下次怎么办得更好）',
    photo_urls TEXT COMMENT '照片URL列表JSON数组',
    image_descriptions TEXT COMMENT '图片描述JSON数组（多模态LLM生成，与photo_urls一一对应）',
    file_urls TEXT COMMENT '附件URL列表JSON数组',
    is_adopted TINYINT NOT NULL DEFAULT 0 COMMENT '0=未采纳,1=已采纳',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '0=已删除,1=正常',
    created_by BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_task_user (task_id, user_id),
    INDEX idx_activity_id (activity_id),
    INDEX idx_club_id (club_id),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB COMMENT='打卡记录表';

-- ========== 打卡汇编文档表 ==========

CREATE TABLE act_checkin_compilation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    activity_id BIGINT NOT NULL,
    club_id BIGINT NOT NULL,
    content TEXT NOT NULL COMMENT '汇编文档正文Markdown',
    file_path VARCHAR(500) COMMENT 'Word文件存储路径',
    member_count INT NOT NULL COMMENT '参与汇编的成员数',
    avg_rating DECIMAL(2,1) COMMENT '平均评分',
    problem_summary TEXT COMMENT '问题汇总',
    suggestion_summary TEXT COMMENT '建议汇总',
    chroma_doc_ids TEXT COMMENT 'ChromaDB文档ID列表JSON数组',
    is_ingested TINYINT NOT NULL DEFAULT 0 COMMENT '0=未入库,1=已入库',
    ingested_at DATETIME,
    created_by BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_task_id (task_id),
    INDEX idx_activity_id (activity_id),
    INDEX idx_club_id (club_id),
    INDEX idx_is_ingested (is_ingested)
) ENGINE=InnoDB COMMENT='打卡汇编文档表';

-- ========== 签退记录表 ==========

CREATE TABLE act_sign_out (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    activity_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    signed_out_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_activity_user (activity_id, user_id),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB COMMENT='签退记录表';

-- ========== AI 成果报告表 ==========

CREATE TABLE ai_activity_report (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    activity_id BIGINT NOT NULL,
    club_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    file_path VARCHAR(500),
    avg_rating DECIMAL(2,1),
    participant_count INT,
    checkin_count INT,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/CONFIRMED/REJECTED',
    reject_reason TEXT,
    revision_count INT NOT NULL DEFAULT 0,
    confirmed_by BIGINT,
    confirmed_at DATETIME,
    is_ingested TINYINT NOT NULL DEFAULT 0 COMMENT '0=未入库,1=已入库',
    ingested_at DATETIME,
    created_by BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_activity_id (activity_id),
    INDEX idx_club_id (club_id),
    INDEX idx_status (status),
    INDEX idx_is_ingested (is_ingested)
) ENGINE=InnoDB COMMENT='活动成果报告表';

-- ========== AI 对话会话表 ==========

CREATE TABLE ai_chat_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(50) NOT NULL,
    club_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    title VARCHAR(100),
    message_count INT NOT NULL DEFAULT 0,
    last_active_at DATETIME NOT NULL,
    status TINYINT NOT NULL DEFAULT 1 COMMENT '0=已关闭,1=活跃',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_session_id (session_id),
    INDEX idx_user_club (user_id, club_id),
    INDEX idx_last_active (last_active_at)
) ENGINE=InnoDB COMMENT='AI对话会话表';

-- ========== AI 知识库文档元数据表 ==========

CREATE TABLE ai_knowledge_document (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    club_id BIGINT NOT NULL,
    doc_type VARCHAR(30) NOT NULL COMMENT 'charter/recruitment/faq/activity_report',
    title VARCHAR(200) NOT NULL,
    source_type VARCHAR(20) NOT NULL COMMENT 'upload/system',
    source_id BIGINT,
    file_path VARCHAR(500),
    chunk_count INT NOT NULL DEFAULT 0,
    chroma_ids TEXT COMMENT 'JSON数组',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '0=已删除,1=正常',
    created_by BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    INDEX idx_club_type (club_id, doc_type),
    INDEX idx_source (source_type, source_id),
    INDEX idx_status (status)
) ENGINE=InnoDB COMMENT='知识库文档元数据表';

-- ========== 积分流水表 ==========

CREATE TABLE member_points_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    club_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    points INT NOT NULL COMMENT '正数=获得,负数=扣除',
    reason VARCHAR(50) NOT NULL COMMENT 'SIGN_IN/SIGN_OUT/CHECKIN_REFLECTION/CHECKIN_PHOTO/CHECKIN_SUGGESTION/ADOPTED_SUGGESTION',
    description VARCHAR(200) NOT NULL,
    related_activity_id BIGINT,
    related_checkin_id BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_club_user (club_id, user_id),
    INDEX idx_user_id (user_id),
    INDEX idx_reason (reason),
    INDEX idx_activity_id (related_activity_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB COMMENT='积分流水表';
```

### 5.2 V4__ai_permissions.sql

```sql
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
```

---

## 6. 文件存储设计

### 6.1 本地文件存储目录

```
data/
├── uploads/                    # 用户上传文件
│   ├── photos/                 # 打卡照片
│   │   └── {club_id}/
│   │       └── {activity_id}/
│   │           └── {timestamp}_{uuid}.jpg
│   ├── files/                  # 成员上传的文档附件
│   │   └── {club_id}/
│   │       └── {user_id}/
│   │           └── {timestamp}_{uuid}.docx
│   └── knowledge/              # 社长上传的知识库文档
│       └── {club_id}/
│           └── {timestamp}_{uuid}.docx
│
├── reports/                    # Agent 生成的成果报告
│   └── {club_id}/
│       └── {activity_id}/
│           └── {title}.docx
│
└── chroma/                     # ChromaDB 持久化（见架构文档）
```

### 6.2 文件存储策略接口

```python
# 抽象接口，后续可切换为对象存储实现

class FileStorageStrategy(ABC):
    @abstractmethod
    def save(self, file_content: bytes, relative_path: str) -> str:
        """保存文件，返回访问路径"""
        pass

    @abstractmethod
    def delete(self, relative_path: str) -> None:
        """删除文件"""
        pass

    @abstractmethod
    def get_url(self, relative_path: str) -> str:
        """获取文件访问 URL"""
        pass

class LocalFileStorage(FileStorageStrategy):
    """本地磁盘存储实现"""
    pass

# 未来扩展
# class OssFileStorage(FileStorageStrategy):
#     """阿里云 OSS 实现"""
#     pass
```

### 6.3 文件访问

Java 后端提供文件访问接口：

```
GET /api/v1/files/{relativePath}   # 文件下载/预览
POST /api/v1/files/upload          # 文件上传
```

文件 URL 格式：`/api/v1/files/photos/1/2/1719000000_abc.jpg`
