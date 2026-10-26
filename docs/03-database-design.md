# 数据库设计文档

## 1. 表总览

| 表名 | 模块 | 说明 |
|---|---|---|
| sys_user | 系统 | 用户表 |
| sys_role | 系统 | 角色表 |
| sys_user_role | 系统 | 用户-角色关联表 |
| sys_permission | 系统 | 权限表 |
| sys_role_permission | 系统 | 角色-权限关联表 |
| club_info | 社团 | 社团信息表 |
| club_member | 社团 | 社团成员表 |
| act_activity | 活动 | 活动表 |
| act_approval | 活动 | 审批记录表 |
| reg_registration | 报名 | 报名记录表 |
| reg_sign_in | 报名 | 签到记录表 |
| res_venue | 资源 | 场地表 |
| res_equipment | 资源 | 设备表 |
| res_venue_booking | 资源 | 场地预约表 |
| res_equipment_borrow | 资源 | 设备借用表 |
| noti_notification | 通知 | 通知表 |
| operation_log | 系统 | 操作日志表 |

---

## 2. 表结构详细设计

### 2.1 sys_user（用户表）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| username | VARCHAR(50) | UNIQUE, NOT NULL | 用户名 |
| password | VARCHAR(255) | NOT NULL | 密码（BCrypt 加密） |
| real_name | VARCHAR(50) | NOT NULL | 真实姓名 |
| email | VARCHAR(100) | UNIQUE | 邮箱 |
| phone | VARCHAR(20) | | 手机号 |
| avatar | VARCHAR(255) | | 头像 URL |
| status | TINYINT | NOT NULL, DEFAULT 1 | 状态：0=禁用, 1=启用 |
| created_by | BIGINT | | 创建人 ID |
| created_at | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_by | BIGINT | | 最后修改人 ID |
| updated_at | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP ON UPDATE | 修改时间 |
| deleted | TINYINT | NOT NULL, DEFAULT 0 | 逻辑删除：0=未删除, 1=已删除 |

**索引：**
- `uk_username` UNIQUE (`username`)
- `uk_email` UNIQUE (`email`)
- `idx_phone` (`phone`)

---

### 2.2 sys_role（角色表）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| role_name | VARCHAR(50) | NOT NULL | 角色名称 |
| role_code | VARCHAR(50) | UNIQUE, NOT NULL | 角色编码（STUDENT, CLUB_PRESIDENT, TEACHER, ADMIN） |
| description | VARCHAR(200) | | 角色描述 |
| created_at | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP ON UPDATE | 修改时间 |

**索引：**
- `uk_role_code` UNIQUE (`role_code`)

---

### 2.3 sys_user_role（用户-角色关联表）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| user_id | BIGINT | NOT NULL | 用户 ID |
| role_id | BIGINT | NOT NULL | 角色 ID |
| club_id | BIGINT | | 关联社团 ID（社团内角色时使用） |
| created_at | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 创建时间 |

**索引：**
- `uk_user_role_club` UNIQUE (`user_id`, `role_id`, `club_id`)
- `idx_role_id` (`role_id`)

---

### 2.4 sys_permission（权限表）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| permission_code | VARCHAR(100) | UNIQUE, NOT NULL | 权限编码（如 `activity:create`, `activity:approve`） |
| permission_name | VARCHAR(100) | NOT NULL | 权限名称 |
| module | VARCHAR(50) | NOT NULL | 所属模块 |
| description | VARCHAR(200) | | 描述 |
| created_at | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 创建时间 |

**索引：**
- `uk_permission_code` UNIQUE (`permission_code`)
- `idx_module` (`module`)

---

### 2.5 sys_role_permission（角色-权限关联表）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| role_id | BIGINT | NOT NULL | 角色 ID |
| permission_id | BIGINT | NOT NULL | 权限 ID |
| created_at | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 创建时间 |

**索引：**
- `uk_role_permission` UNIQUE (`role_id`, `permission_id`)

---

### 2.6 club_info（社团信息表）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| club_name | VARCHAR(100) | UNIQUE, NOT NULL | 社团名称 |
| category | VARCHAR(50) | NOT NULL | 社团类别（学术/体育/文艺/志愿等） |
| description | TEXT | | 社团简介 |
| charter | TEXT | | 社团章程 |
| cover_image | VARCHAR(255) | | 封面图 URL |
| president_id | BIGINT | NOT NULL | 社长用户 ID |
| approval_status | TINYINT | NOT NULL, DEFAULT 0 | 审批状态：0=待审批, 1=已通过, 2=已驳回 |
| reject_reason | VARCHAR(500) | | 驳回原因 |
| approved_by | BIGINT | | 审批人 ID |
| approved_at | DATETIME | | 审批时间 |
| member_count | INT | NOT NULL, DEFAULT 0 | 成员数量（冗余字段，用于快速查询） |
| created_by | BIGINT | | 创建人 ID |
| created_at | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_by | BIGINT | | 最后修改人 ID |
| updated_at | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP ON UPDATE | 修改时间 |
| deleted | TINYINT | NOT NULL, DEFAULT 0 | 逻辑删除 |

**索引：**
- `uk_club_name` UNIQUE (`club_name`)
- `idx_category` (`category`)
- `idx_president_id` (`president_id`)
- `idx_approval_status` (`approval_status`)

---

### 2.7 club_member（社团成员表）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| club_id | BIGINT | NOT NULL | 社团 ID |
| user_id | BIGINT | NOT NULL | 用户 ID |
| role | VARCHAR(20) | NOT NULL, DEFAULT 'MEMBER' | 社团内角色：PRESIDENT, VICE_PRESIDENT, SECRETARY, MEMBER |
| joined_at | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 加入时间 |
| status | TINYINT | NOT NULL, DEFAULT 1 | 状态：0=已退出, 1=正常 |
| created_by | BIGINT | | 创建人 ID |
| created_at | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_by | BIGINT | | 最后修改人 ID |
| updated_at | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP ON UPDATE | 修改时间 |
| deleted | TINYINT | NOT NULL, DEFAULT 0 | 逻辑删除 |

**索引：**
- `uk_club_user` UNIQUE (`club_id`, `user_id`)
- `idx_user_id` (`user_id`)
- `idx_status` (`status`)

---

### 2.8 act_activity（活动表）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| club_id | BIGINT | NOT NULL | 所属社团 ID |
| title | VARCHAR(100) | NOT NULL | 活动标题 |
| description | TEXT | NOT NULL | 活动描述 |
| type | VARCHAR(20) | NOT NULL | 活动类型：CAMPUS(校内), OFF_CAMPUS(校外), LARGE(大型) |
| location | VARCHAR(200) | NOT NULL | 活动地点 |
| start_time | DATETIME | NOT NULL | 开始时间 |
| end_time | DATETIME | NOT NULL | 结束时间 |
| registration_deadline | DATETIME | | 报名截止时间 |
| max_participants | INT | NOT NULL | 人数上限 |
| current_participants | INT | NOT NULL, DEFAULT 0 | 当前报名人数（冗余字段） |
| budget | DECIMAL(10,2) | NOT NULL | 预算（元） |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'DRAFT' | 状态：DRAFT, PENDING, APPROVED, REJECTED, IN_PROGRESS, COMPLETED, ARCHIVED |
| version | INT | NOT NULL, DEFAULT 1 | 乐观锁版本号 |
| summary | TEXT | | 活动总结 |
| cover_image | VARCHAR(255) | | 封面图 URL |
| need_registration | TINYINT | NOT NULL, DEFAULT 1 | 是否需要报名：0=否, 1=是 |
| need_approval | TINYINT | NOT NULL, DEFAULT 1 | 是否需要审批：0=否, 1=是 |
| created_by | BIGINT | | 创建人 ID |
| created_at | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_by | BIGINT | | 最后修改人 ID |
| updated_at | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP ON UPDATE | 修改时间 |
| deleted | TINYINT | NOT NULL, DEFAULT 0 | 逻辑删除 |

**索引：**
- `idx_club_id` (`club_id`)
- `idx_status` (`status`)
- `idx_start_time` (`start_time`)
- `idx_type` (`type`)
- `idx_club_status` (`club_id`, `status`)

---

### 2.9 act_approval（审批记录表）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| activity_id | BIGINT | NOT NULL | 活动 ID |
| approver_id | BIGINT | NOT NULL | 审批人 ID |
| approval_level | INT | NOT NULL | 审批级别：1=社长, 2=指导老师, 3=团委 |
| result | TINYINT | NOT NULL | 审批结果：1=通过, 2=驳回 |
| comment | VARCHAR(500) | | 审批意见 |
| approved_at | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 审批时间 |
| created_by | BIGINT | | 创建人 ID |
| created_at | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_by | BIGINT | | 最后修改人 ID |
| updated_at | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP ON UPDATE | 修改时间 |
| deleted | TINYINT | NOT NULL, DEFAULT 0 | 逻辑删除 |

**索引：**
- `idx_activity_id` (`activity_id`)
- `idx_approver_id` (`approver_id`)

---

### 2.10 reg_registration（报名记录表）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| activity_id | BIGINT | NOT NULL | 活动 ID |
| user_id | BIGINT | NOT NULL | 报名用户 ID |
| status | TINYINT | NOT NULL, DEFAULT 1 | 状态：0=已取消, 1=已报名, 2=审核通过, 3=审核驳回 |
| registered_at | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 报名时间 |
| created_by | BIGINT | | 创建人 ID |
| created_at | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_by | BIGINT | | 最后修改人 ID |
| updated_at | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP ON UPDATE | 修改时间 |
| deleted | TINYINT | NOT NULL, DEFAULT 0 | 逻辑删除 |

**索引：**
- `uk_activity_user` UNIQUE (`activity_id`, `user_id`) — 幂等性保障
- `idx_user_id` (`user_id`)
- `idx_status` (`status`)

---

### 2.11 reg_sign_in（签到记录表）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| activity_id | BIGINT | NOT NULL | 活动 ID |
| user_id | BIGINT | NOT NULL | 签到用户 ID |
| sign_in_code | VARCHAR(20) | | 签到码 |
| signed_at | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 签到时间 |
| created_by | BIGINT | | 创建人 ID |
| created_at | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_by | BIGINT | | 最后修改人 ID |
| updated_at | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP ON UPDATE | 修改时间 |
| deleted | TINYINT | NOT NULL, DEFAULT 0 | 逻辑删除 |

**索引：**
- `uk_activity_user` UNIQUE (`activity_id`, `user_id`) — 防重复签到
- `idx_sign_in_code` (`sign_in_code`)

---

### 2.12 res_venue（场地表）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| venue_name | VARCHAR(100) | NOT NULL | 场地名称 |
| location | VARCHAR(200) | | 位置描述 |
| capacity | INT | NOT NULL | 容纳人数 |
| facilities | VARCHAR(500) | | 设施描述（JSON 或逗号分隔） |
| status | TINYINT | NOT NULL, DEFAULT 1 | 状态：0=不可用, 1=可用 |
| created_by | BIGINT | | 创建人 ID |
| created_at | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_by | BIGINT | | 最后修改人 ID |
| updated_at | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP ON UPDATE | 修改时间 |
| deleted | TINYINT | NOT NULL, DEFAULT 0 | 逻辑删除 |

**索引：**
- `idx_status` (`status`)

---

### 2.13 res_equipment（设备表）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| equipment_name | VARCHAR(100) | NOT NULL | 设备名称 |
| equipment_type | VARCHAR(50) | NOT NULL | 设备类型（投影仪/音响/电脑等） |
| total_count | INT | NOT NULL, DEFAULT 1 | 总数量 |
| available_count | INT | NOT NULL, DEFAULT 1 | 当前可用数量 |
| status | TINYINT | NOT NULL, DEFAULT 1 | 状态：0=不可用, 1=可用 |
| created_by | BIGINT | | 创建人 ID |
| created_at | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_by | BIGINT | | 最后修改人 ID |
| updated_at | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP ON UPDATE | 修改时间 |
| deleted | TINYINT | NOT NULL, DEFAULT 0 | 逻辑删除 |

**索引：**
- `idx_type` (`equipment_type`)
- `idx_status` (`status`)

---

### 2.14 res_venue_booking（场地预约表）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| venue_id | BIGINT | NOT NULL | 场地 ID |
| activity_id | BIGINT | NOT NULL | 关联活动 ID |
| club_id | BIGINT | NOT NULL | 预约社团 ID |
| booking_date | DATE | NOT NULL | 预约日期 |
| start_time | TIME | NOT NULL | 开始时间 |
| end_time | TIME | NOT NULL | 结束时间 |
| status | TINYINT | NOT NULL, DEFAULT 1 | 状态：0=已取消, 1=已预约, 2=已完成 |
| created_by | BIGINT | | 创建人 ID |
| created_at | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_by | BIGINT | | 最后修改人 ID |
| updated_at | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP ON UPDATE | 修改时间 |
| deleted | TINYINT | NOT NULL, DEFAULT 0 | 逻辑删除 |

**索引：**
- `uk_venue_time` UNIQUE (`venue_id`, `booking_date`, `start_time`) — 防场地时间冲突
- `idx_activity_id` (`activity_id`)
- `idx_club_id` (`club_id`)
- `idx_booking_date` (`booking_date`)

---

### 2.15 res_equipment_borrow（设备借用表）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| equipment_id | BIGINT | NOT NULL | 设备 ID |
| activity_id | BIGINT | | 关联活动 ID |
| club_id | BIGINT | NOT NULL | 借用社团 ID |
| borrower_id | BIGINT | NOT NULL | 借用人 ID |
| borrow_count | INT | NOT NULL, DEFAULT 1 | 借用数量 |
| borrow_time | DATETIME | NOT NULL | 借用时间 |
| return_time | DATETIME | | 归还时间 |
| status | TINYINT | NOT NULL, DEFAULT 0 | 状态：0=借用中, 1=已归还 |
| created_by | BIGINT | | 创建人 ID |
| created_at | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_by | BIGINT | | 最后修改人 ID |
| updated_at | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP ON UPDATE | 修改时间 |
| deleted | TINYINT | NOT NULL, DEFAULT 0 | 逻辑删除 |

**索引：**
- `idx_equipment_id` (`equipment_id`)
- `idx_activity_id` (`activity_id`)
- `idx_status` (`status`)

---

### 2.16 noti_notification（通知表）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| user_id | BIGINT | NOT NULL | 接收人 ID |
| title | VARCHAR(100) | NOT NULL | 通知标题 |
| content | TEXT | NOT NULL | 通知内容 |
| type | VARCHAR(20) | NOT NULL | 通知类型：SYSTEM(系统), ACTIVITY(活动), CLUB(社团), APPROVAL(审批) |
| related_id | BIGINT | | 关联业务 ID（活动 ID 或社团 ID） |
| is_read | TINYINT | NOT NULL, DEFAULT 0 | 是否已读：0=未读, 1=已读 |
| read_at | DATETIME | | 阅读时间 |
| created_by | BIGINT | | 创建人 ID |
| created_at | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_by | BIGINT | | 最后修改人 ID |
| updated_at | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP ON UPDATE | 修改时间 |
| deleted | TINYINT | NOT NULL, DEFAULT 0 | 逻辑删除 |

**索引：**
- `idx_user_read` (`user_id`, `is_read`)
- `idx_type` (`type`)
- `idx_created_at` (`created_at`)

---

### 2.17 operation_log（操作日志表）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| trace_id | VARCHAR(50) | NOT NULL | 链路追踪 ID |
| user_id | BIGINT | | 操作人 ID |
| username | VARCHAR(50) | | 操作人用户名 |
| module | VARCHAR(50) | NOT NULL | 模块名 |
| operation | VARCHAR(100) | NOT NULL | 操作描述 |
| method | VARCHAR(200) | | 请求方法 |
| request_params | TEXT | | 请求参数（JSON） |
| response_data | TEXT | | 响应数据（JSON） |
| status | TINYINT | NOT NULL | 状态：0=失败, 1=成功 |
| error_msg | TEXT | | 错误信息 |
| cost_time | BIGINT | | 耗时（毫秒） |
| ip | VARCHAR(50) | | 请求 IP |
| created_at | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 创建时间 |

**索引：**
- `idx_trace_id` (`trace_id`)
- `idx_user_id` (`user_id`)
- `idx_module` (`module`)
- `idx_created_at` (`created_at`)

---

## 3. ER 关系图

```
sys_user ──1:N── sys_user_role ──N:1── sys_role
                                        │
                                       1:N
                                        │
                              sys_role_permission ──N:1── sys_permission

sys_user ──1:N── club_member ──N:1── club_info

club_info ──1:N── act_activity
act_activity ──1:N── act_approval
act_activity ──1:N── reg_registration ──N:1── sys_user
act_activity ──1:N── reg_sign_in ──N:1── sys_user

act_activity ──1:1── res_venue_booking ──N:1── res_venue
act_activity ──1:N── res_equipment_borrow ──N:1── res_equipment

sys_user ──1:N── noti_notification
```

---

## 4. SQL 初始化脚本

### 4.1 V1__init_schema.sql

```sql
-- 数据库创建
CREATE DATABASE IF NOT EXISTS club_management DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE club_management;

-- ========== 系统表 ==========

CREATE TABLE sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    password VARCHAR(255) NOT NULL,
    real_name VARCHAR(50) NOT NULL,
    email VARCHAR(100),
    phone VARCHAR(20),
    avatar VARCHAR(255),
    status TINYINT NOT NULL DEFAULT 1 COMMENT '0=禁用,1=启用',
    created_by BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_username (username),
    UNIQUE KEY uk_email (email),
    INDEX idx_phone (phone)
) ENGINE=InnoDB COMMENT='用户表';

CREATE TABLE sys_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_name VARCHAR(50) NOT NULL,
    role_code VARCHAR(50) NOT NULL,
    description VARCHAR(200),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_role_code (role_code)
) ENGINE=InnoDB COMMENT='角色表';

CREATE TABLE sys_user_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    club_id BIGINT COMMENT '关联社团ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_role_club (user_id, role_id, club_id),
    INDEX idx_role_id (role_id)
) ENGINE=InnoDB COMMENT='用户角色关联表';

CREATE TABLE sys_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    permission_code VARCHAR(100) NOT NULL,
    permission_name VARCHAR(100) NOT NULL,
    module VARCHAR(50) NOT NULL,
    description VARCHAR(200),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_permission_code (permission_code),
    INDEX idx_module (module)
) ENGINE=InnoDB COMMENT='权限表';

CREATE TABLE sys_role_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_role_permission (role_id, permission_id)
) ENGINE=InnoDB COMMENT='角色权限关联表';

-- ========== 社团表 ==========

CREATE TABLE club_info (
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

CREATE TABLE club_member (
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

CREATE TABLE act_activity (
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

CREATE TABLE act_approval (
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

CREATE TABLE reg_registration (
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

CREATE TABLE reg_sign_in (
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

CREATE TABLE res_venue (
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

CREATE TABLE res_equipment (
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

CREATE TABLE res_venue_booking (
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

CREATE TABLE res_equipment_borrow (
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

CREATE TABLE noti_notification (
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

CREATE TABLE operation_log (
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
```

### 4.2 V2__init_data.sql

```sql
USE club_management;

-- ========== 初始化角色 ==========
INSERT INTO sys_role (role_name, role_code, description) VALUES
('学生', 'STUDENT', '普通学生角色'),
('社团负责人', 'CLUB_PRESIDENT', '社团社长'),
('指导老师', 'TEACHER', '社团指导老师'),
('系统管理员', 'ADMIN', '系统管理员');

-- ========== 初始化权限 ==========
INSERT INTO sys_permission (permission_code, permission_name, module) VALUES
-- 社团权限
('club:create', '创建社团', 'club'),
('club:edit', '编辑社团', 'club'),
('club:delete', '删除社团', 'club'),
('club:approve', '审批社团', 'club'),
('club:member:manage', '管理成员', 'club'),
-- 活动权限
('activity:create', '创建活动', 'activity'),
('activity:edit', '编辑活动', 'activity'),
('activity:delete', '删除活动', 'activity'),
('activity:approve', '审批活动', 'activity'),
('activity:view', '查看活动', 'activity'),
-- 报名权限
('registration:create', '报名活动', 'registration'),
('registration:manage', '管理报名', 'registration'),
('signin:manage', '管理签到', 'registration'),
-- 资源权限
('resource:manage', '管理资源', 'resource'),
('resource:book', '预约资源', 'resource'),
-- 通知权限
('notification:view', '查看通知', 'notification'),
('notification:send', '发送通知', 'notification'),
-- 统计权限
('statistics:view', '查看统计', 'statistics'),
-- 系统权限
('system:user:manage', '用户管理', 'system'),
('system:role:manage', '角色管理', 'system'),
('system:log:view', '查看日志', 'system');

-- ========== 角色-权限关联 ==========
-- 学生权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 1, id FROM sys_permission WHERE permission_code IN (
    'activity:view', 'registration:create', 'resource:book', 'notification:view'
);

-- 社团负责人权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 2, id FROM sys_permission WHERE permission_code IN (
    'club:edit', 'club:member:manage',
    'activity:create', 'activity:edit', 'activity:view',
    'registration:manage', 'signin:manage',
    'resource:book', 'notification:view', 'notification:send'
);

-- 指导老师权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 3, id FROM sys_permission WHERE permission_code IN (
    'activity:approve', 'activity:view',
    'registration:manage', 'club:member:manage',
    'notification:view', 'notification:send', 'statistics:view'
);

-- 管理员权限（全部）
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 4, id FROM sys_permission;

-- ========== 初始化管理员账号 ==========
-- 密码为 BCrypt 加密后的 "admin123"
INSERT INTO sys_user (username, password, real_name, email, status) VALUES
('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '系统管理员', 'admin@club.com', 1);

-- 管理员关联角色
INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 4);
```
