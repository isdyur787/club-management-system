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
    reflection TEXT COMMENT '心得/感悟',
    problem TEXT COMMENT '遇到的问题',
    suggestion TEXT COMMENT '改进建议',
    photo_urls TEXT COMMENT '照片URL列表JSON数组',
    image_descriptions TEXT COMMENT '图片描述JSON数组',
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
