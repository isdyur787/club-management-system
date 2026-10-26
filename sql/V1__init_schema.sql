USE club_management;

-- ========== 系统表 ==========

CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    password VARCHAR(255) NOT NULL,
    real_name VARCHAR(50) NOT NULL,
    email VARCHAR(100),
    phone VARCHAR(20),
    avatar VARCHAR(255),
    status TINYINT NOT NULL DEFAULT 1,
    created_by BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_username (username),
    UNIQUE KEY uk_email (email),
    INDEX idx_phone (phone)
) ENGINE=InnoDB COMMENT='用户表';

CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_name VARCHAR(50) NOT NULL,
    role_code VARCHAR(50) NOT NULL,
    description VARCHAR(200),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_role_code (role_code)
) ENGINE=InnoDB COMMENT='角色表';

CREATE TABLE IF NOT EXISTS sys_user_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    club_id BIGINT COMMENT '关联社团ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_role_club (user_id, role_id, club_id),
    INDEX idx_role_id (role_id)
) ENGINE=InnoDB COMMENT='用户角色关联表';

CREATE TABLE IF NOT EXISTS sys_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    permission_code VARCHAR(100) NOT NULL,
    permission_name VARCHAR(100) NOT NULL,
    module VARCHAR(50) NOT NULL,
    description VARCHAR(200),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_permission_code (permission_code),
    INDEX idx_module (module)
) ENGINE=InnoDB COMMENT='权限表';

CREATE TABLE IF NOT EXISTS sys_role_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_role_permission (role_id, permission_id)
) ENGINE=InnoDB COMMENT='角色权限关联表';

-- ========== 社团表 ==========

CREATE TABLE IF NOT EXISTS club_info (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    club_name VARCHAR(100) NOT NULL,
    category VARCHAR(50) NOT NULL COMMENT '学术/体育/文艺/志愿等',
    description TEXT,
    charter TEXT,
    cover_image VARCHAR(255),
    president_id BIGINT NOT NULL,
    approval_status TINYINT NOT NULL DEFAULT 0 COMMENT '0=待审批,1=通过,2=驳回',
    reject_reason VARCHAR(500),
    approved_by BIGINT,
    approved_at DATETIME,
    member_count INT NOT NULL DEFAULT 0,
    created_by BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_club_name (club_name),
    INDEX idx_category (category),
    INDEX idx_president_id (president_id),
    INDEX idx_approval_status (approval_status)
) ENGINE=InnoDB COMMENT='社团信息表';

CREATE TABLE IF NOT EXISTS club_member (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    club_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'MEMBER' COMMENT 'PRESIDENT/VICE_PRESIDENT/SECRETARY/MEMBER',
    joined_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status TINYINT NOT NULL DEFAULT 1 COMMENT '0=已退出,1=正常',
    created_by BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_club_user (club_id, user_id),
    INDEX idx_user_id (user_id),
    INDEX idx_status (status)
) ENGINE=InnoDB COMMENT='社团成员表';

-- ========== 活动表 ==========

CREATE TABLE IF NOT EXISTS act_activity (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    club_id BIGINT NOT NULL,
    title VARCHAR(100) NOT NULL,
    description TEXT NOT NULL,
    type VARCHAR(20) NOT NULL COMMENT 'CAMPUS/OFF_CAMPUS/LARGE',
    location VARCHAR(200) NOT NULL,
    start_time DATETIME NOT NULL,
    end_time DATETIME NOT NULL,
    registration_deadline DATETIME,
    max_participants INT NOT NULL,
    current_participants INT NOT NULL DEFAULT 0,
    budget DECIMAL(10,2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PENDING/APPROVED/REJECTED/IN_PROGRESS/COMPLETED/ARCHIVED',
    version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    summary TEXT,
    cover_image VARCHAR(255),
    need_registration TINYINT NOT NULL DEFAULT 1,
    need_approval TINYINT NOT NULL DEFAULT 1,
    created_by BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    INDEX idx_club_id (club_id),
    INDEX idx_status (status),
    INDEX idx_start_time (start_time),
    INDEX idx_type (type),
    INDEX idx_club_status (club_id, status)
) ENGINE=InnoDB COMMENT='活动表';

CREATE TABLE IF NOT EXISTS act_approval (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    activity_id BIGINT NOT NULL,
    approver_id BIGINT NOT NULL,
    approval_level INT NOT NULL COMMENT '1=社长,2=指导老师,3=团委',
    result TINYINT NOT NULL COMMENT '1=通过,2=驳回',
    comment VARCHAR(500),
    approved_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    INDEX idx_activity_id (activity_id),
    INDEX idx_approver_id (approver_id)
) ENGINE=InnoDB COMMENT='审批记录表';

-- ========== 报名签到表 ==========

CREATE TABLE IF NOT EXISTS reg_registration (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    activity_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    status TINYINT NOT NULL DEFAULT 1 COMMENT '0=已取消,1=已报名,2=审核通过,3=审核驳回',
    registered_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_activity_user (activity_id, user_id),
    INDEX idx_user_id (user_id),
    INDEX idx_status (status)
) ENGINE=InnoDB COMMENT='报名记录表';

CREATE TABLE IF NOT EXISTS reg_sign_in (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    activity_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    sign_in_code VARCHAR(20),
    signed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_activity_user (activity_id, user_id),
    INDEX idx_sign_in_code (sign_in_code)
) ENGINE=InnoDB COMMENT='签到记录表';

-- ========== 资源表 ==========

CREATE TABLE IF NOT EXISTS res_venue (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    venue_name VARCHAR(100) NOT NULL,
    location VARCHAR(200),
    capacity INT NOT NULL,
    facilities VARCHAR(500),
    status TINYINT NOT NULL DEFAULT 1 COMMENT '0=不可用,1=可用',
    created_by BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    INDEX idx_status (status)
) ENGINE=InnoDB COMMENT='场地表';

CREATE TABLE IF NOT EXISTS res_equipment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    equipment_name VARCHAR(100) NOT NULL,
    equipment_type VARCHAR(50) NOT NULL,
    total_count INT NOT NULL DEFAULT 1,
    available_count INT NOT NULL DEFAULT 1,
    status TINYINT NOT NULL DEFAULT 1 COMMENT '0=不可用,1=可用',
    created_by BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    INDEX idx_type (equipment_type),
    INDEX idx_status (status)
) ENGINE=InnoDB COMMENT='设备表';

CREATE TABLE IF NOT EXISTS res_venue_booking (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    venue_id BIGINT NOT NULL,
    activity_id BIGINT NOT NULL,
    club_id BIGINT NOT NULL,
    booking_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    status TINYINT NOT NULL DEFAULT 1 COMMENT '0=已取消,1=已预约,2=已完成',
    created_by BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_venue_time (venue_id, booking_date, start_time),
    INDEX idx_activity_id (activity_id),
    INDEX idx_club_id (club_id),
    INDEX idx_booking_date (booking_date)
) ENGINE=InnoDB COMMENT='场地预约表';

CREATE TABLE IF NOT EXISTS res_equipment_borrow (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    equipment_id BIGINT NOT NULL,
    activity_id BIGINT,
    club_id BIGINT NOT NULL,
    borrower_id BIGINT NOT NULL,
    borrow_count INT NOT NULL DEFAULT 1,
    borrow_time DATETIME NOT NULL,
    return_time DATETIME,
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0=借用中,1=已归还',
    created_by BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    INDEX idx_equipment_id (equipment_id),
    INDEX idx_activity_id (activity_id),
    INDEX idx_status (status)
) ENGINE=InnoDB COMMENT='设备借用表';

-- ========== 通知表 ==========

CREATE TABLE IF NOT EXISTS noti_notification (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(100) NOT NULL,
    content TEXT NOT NULL,
    type VARCHAR(20) NOT NULL COMMENT 'SYSTEM/ACTIVITY/CLUB/APPROVAL',
    related_id BIGINT,
    is_read TINYINT NOT NULL DEFAULT 0,
    read_at DATETIME,
    created_by BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    INDEX idx_user_read (user_id, is_read),
    INDEX idx_type (type),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB COMMENT='通知表';

-- ========== 操作日志表 ==========

CREATE TABLE IF NOT EXISTS operation_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    trace_id VARCHAR(50) NOT NULL,
    user_id BIGINT,
    username VARCHAR(50),
    module VARCHAR(50) NOT NULL,
    operation VARCHAR(100) NOT NULL,
    method VARCHAR(200),
    request_params TEXT,
    response_data TEXT,
    status TINYINT NOT NULL COMMENT '0=失败,1=成功',
    error_msg TEXT,
    cost_time BIGINT,
    ip VARCHAR(50),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_trace_id (trace_id),
    INDEX idx_user_id (user_id),
    INDEX idx_module (module),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB COMMENT='操作日志表';
