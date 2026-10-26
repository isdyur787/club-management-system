# 接口文档

所有接口前缀：`/api/v1`  
认证方式：JWT Bearer Token（除登录接口外，所有接口需携带 `Authorization: Bearer <token>`）

---

## 1. 用户与认证模块

### 1.1 用户注册

```
POST /api/v1/auth/register
认证：不需要
```

**请求体：**
```json
{
    "username": "zhangsan",
    "password": "Abc123456",
    "realName": "张三",
    "email": "zhangsan@example.com",
    "phone": "13800138000"
}
```

**校验规则：**
- username: 必填，4-50 字符，字母开头
- password: 必填，8-20 字符，包含大小写字母和数字
- realName: 必填，2-50 字符
- email: 选填，邮箱格式
- phone: 选填，11 位手机号

**成功响应：**
```json
{
    "code": 200,
    "message": "操作成功",
    "data": {
        "id": 1,
        "username": "zhangsan",
        "realName": "张三"
    },
    "timestamp": 1719000000000
}
```

**错误码：** 40102（用户已存在）、40001（参数校验失败）

---

### 1.2 用户登录

```
POST /api/v1/auth/login
认证：不需要
```

**请求体：**
```json
{
    "username": "zhangsan",
    "password": "Abc123456"
}
```

**成功响应：**
```json
{
    "code": 200,
    "message": "操作成功",
    "data": {
        "token": "eyJhbGciOiJIUzI1NiJ9...",
        "userId": 1,
        "username": "zhangsan",
        "realName": "张三",
        "roles": ["STUDENT"]
    },
    "timestamp": 1719000000000
}
```

**错误码：** 40101（用户不存在）、40103（密码错误）

---

### 1.3 获取当前用户信息

```
GET /api/v1/auth/me
认证：需要
```

**成功响应：**
```json
{
    "code": 200,
    "message": "操作成功",
    "data": {
        "id": 1,
        "username": "zhangsan",
        "realName": "张三",
        "email": "zhangsan@example.com",
        "phone": "13800138000",
        "avatar": null,
        "roles": [
            {
                "roleCode": "STUDENT",
                "roleName": "学生"
            }
        ]
    },
    "timestamp": 1719000000000
}
```

---

### 1.4 修改个人信息

```
PUT /api/v1/users/me
认证：需要
```

**请求体：**
```json
{
    "realName": "张三",
    "email": "zhangsan@new.com",
    "phone": "13900139000",
    "avatar": "https://xxx.com/avatar.jpg"
}
```

**成功响应：** 标准 R<Void> 成功响应

---

### 1.5 修改密码

```
PUT /api/v1/users/me/password
认证：需要
```

**请求体：**
```json
{
    "oldPassword": "Abc123456",
    "newPassword": "NewAbc123"
}
```

**错误码：** 40103（原密码错误）

---

## 2. 社团管理模块

### 2.1 创建社团

```
POST /api/v1/clubs
认证：需要
权限：无限制（所有登录用户可创建）
```

**请求体：**
```json
{
    "clubName": "编程社",
    "category": "学术",
    "description": "这是一个编程爱好者社团",
    "charter": "社团章程内容..."
}
```

**成功响应：**
```json
{
    "code": 200,
    "message": "操作成功",
    "data": {
        "id": 1,
        "clubName": "编程社",
        "category": "学术",
        "approvalStatus": 0
    },
    "timestamp": 1719000000000
}
```

**错误码：** 40202（社团名称已存在）、40001（参数校验失败）

---

### 2.2 查询社团列表

```
GET /api/v1/clubs
认证：需要
```

**查询参数：**
| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| pageNum | Integer | 否 | 页码，默认 1 |
| pageSize | Integer | 否 | 每页条数，默认 10 |
| clubName | String | 否 | 社团名称（模糊查询） |
| category | String | 否 | 社团类别 |
| approvalStatus | Integer | 否 | 审批状态 |

**成功响应：**
```json
{
    "code": 200,
    "message": "操作成功",
    "data": {
        "pageNum": 1,
        "pageSize": 10,
        "total": 5,
        "list": [
            {
                "id": 1,
                "clubName": "编程社",
                "category": "学术",
                "description": "编程爱好者社团",
                "presidentName": "张三",
                "memberCount": 30,
                "approvalStatus": 1,
                "createdAt": "2024-01-01T10:00:00"
            }
        ]
    },
    "timestamp": 1719000000000
}
```

---

### 2.3 查询社团详情

```
GET /api/v1/clubs/{id}
认证：需要
```

**成功响应：**
```json
{
    "code": 200,
    "message": "操作成功",
    "data": {
        "id": 1,
        "clubName": "编程社",
        "category": "学术",
        "description": "编程爱好者社团",
        "charter": "社团章程...",
        "coverImage": "https://xxx.com/cover.jpg",
        "president": {
            "id": 1,
            "realName": "张三"
        },
        "memberCount": 30,
        "approvalStatus": 1,
        "createdAt": "2024-01-01T10:00:00"
    },
    "timestamp": 1719000000000
}
```

**错误码：** 40201（社团不存在）

---

### 2.4 修改社团信息

```
PUT /api/v1/clubs/{id}
认证：需要
权限：club:edit（仅社团负责人）
```

**请求体：**（所有字段选填）
```json
{
    "description": "新的简介",
    "coverImage": "https://xxx.com/new-cover.jpg"
}
```

**错误码：** 40201（社团不存在）、40003（无权限）

---

### 2.5 审批社团

```
PUT /api/v1/clubs/{id}/approve
认证：需要
权限：club:approve（仅管理员）
```

**请求体：**
```json
{
    "approved": true,
    "rejectReason": ""
}
```

**错误码：** 40201（社团不存在）、40203（社团已通过审批）

---

### 2.6 加入社团

```
POST /api/v1/clubs/{id}/members
认证：需要
```

**请求体：**
```json
{
    "userId": 2
}
```

**错误码：** 40201（社团不存在）、40204（已是成员）、40203（社团未通过审批）

---

### 2.7 退出社团

```
DELETE /api/v1/clubs/{clubId}/members/me
认证：需要
```

**错误码：** 40205（成员不存在）、40206（社长不能直接退出）

---

### 2.8 查询社团成员列表

```
GET /api/v1/clubs/{id}/members
认证：需要
```

**查询参数：**
| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| pageNum | Integer | 否 | 页码 |
| pageSize | Integer | 否 | 每页条数 |
| role | String | 否 | 社团内角色筛选 |

**成功响应：**
```json
{
    "code": 200,
    "data": {
        "pageNum": 1,
        "pageSize": 10,
        "total": 30,
        "list": [
            {
                "userId": 1,
                "realName": "张三",
                "role": "PRESIDENT",
                "joinedAt": "2024-01-01T10:00:00"
            }
        ]
    }
}
```

---

### 2.9 移除成员

```
DELETE /api/v1/clubs/{clubId}/members/{userId}
认证：需要
权限：club:member:manage
```

**错误码：** 40205（成员不存在）、40003（无权限）

---

## 3. 活动管理模块

### 3.1 创建活动

```
POST /api/v1/activities
认证：需要
权限：activity:create
```

**请求体：**
```json
{
    "title": "AI 技术分享会",
    "description": "分享 AI 领域最新技术动态",
    "type": "CAMPUS",
    "location": "教学楼 A301",
    "startTime": "2024-06-15T14:00:00",
    "endTime": "2024-06-15T17:00:00",
    "registrationDeadline": "2024-06-14T23:59:59",
    "maxParticipants": 50,
    "budget": 500.00,
    "needRegistration": true,
    "coverImage": "https://xxx.com/activity.jpg"
}
```

**成功响应：**
```json
{
    "code": 200,
    "message": "操作成功",
    "data": {
        "id": 1,
        "title": "AI 技术分享会",
        "status": "DRAFT"
    },
    "timestamp": 1719000000000
}
```

**错误码：** 40001（参数校验失败）、40306（时间冲突）

---

### 3.2 查询活动列表

```
GET /api/v1/activities
认证：需要
```

**查询参数：**
| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| pageNum | Integer | 否 | 页码 |
| pageSize | Integer | 否 | 每页条数 |
| clubId | Long | 否 | 社团 ID |
| status | String | 否 | 活动状态 |
| type | String | 否 | 活动类型 |
| keyword | String | 否 | 标题关键词 |
| startTimeFrom | String | 否 | 开始时间范围起（yyyy-MM-dd） |
| startTimeTo | String | 否 | 开始时间范围止（yyyy-MM-dd） |

**成功响应：**
```json
{
    "code": 200,
    "data": {
        "pageNum": 1,
        "pageSize": 10,
        "total": 20,
        "list": [
            {
                "id": 1,
                "title": "AI 技术分享会",
                "clubId": 1,
                "clubName": "编程社",
                "type": "CAMPUS",
                "location": "教学楼 A301",
                "startTime": "2024-06-15T14:00:00",
                "endTime": "2024-06-15T17:00:00",
                "maxParticipants": 50,
                "currentParticipants": 30,
                "status": "APPROVED",
                "coverImage": "https://xxx.com/activity.jpg"
            }
        ]
    }
}
```

---

### 3.3 查询活动详情

```
GET /api/v1/activities/{id}
认证：需要
```

**成功响应：**
```json
{
    "code": 200,
    "data": {
        "id": 1,
        "title": "AI 技术分享会",
        "description": "分享 AI 领域最新技术动态",
        "clubId": 1,
        "clubName": "编程社",
        "type": "CAMPUS",
        "location": "教学楼 A301",
        "startTime": "2024-06-15T14:00:00",
        "endTime": "2024-06-15T17:00:00",
        "registrationDeadline": "2024-06-14T23:59:59",
        "maxParticipants": 50,
        "currentParticipants": 30,
        "budget": 500.00,
        "status": "APPROVED",
        "summary": null,
        "coverImage": "https://xxx.com/activity.jpg",
        "needRegistration": true,
        "creator": {
            "id": 1,
            "realName": "张三"
        },
        "createdAt": "2024-06-01T10:00:00",
        "approvalRecords": [
            {
                "approverName": "李老师",
                "approvalLevel": 2,
                "result": 1,
                "comment": "同意举办",
                "approvedAt": "2024-06-02T09:00:00"
            }
        ]
    }
}
```

**错误码：** 40301（活动不存在）

---

### 3.4 修改活动

```
PUT /api/v1/activities/{id}
认证：需要
权限：activity:edit（仅活动创建者或社团负责人）
```

**请求体：**（选填，仅修改提供的字段）
```json
{
    "title": "AI 技术分享会（修改版）",
    "maxParticipants": 80
}
```

**约束：** 仅 DRAFT 和 REJECTED 状态的活动可修改

**错误码：** 40301（活动不存在）、40302（状态不允许修改）、40003（无权限）

---

### 3.5 删除活动

```
DELETE /api/v1/activities/{id}
认证：需要
权限：activity:delete
```

**约束：** 仅 DRAFT 状态的活动可删除（逻辑删除）

**错误码：** 40301（活动不存在）、40302（状态不允许删除）

---

### 3.6 提交活动审批

```
POST /api/v1/activities/{id}/submit
认证：需要
```

**说明：** 将活动从 DRAFT 状态提交审批，状态变为 PENDING

**错误码：** 40301（活动不存在）、40302（仅 DRAFT 状态可提交）

---

### 3.7 审批活动

```
POST /api/v1/activities/{id}/approve
认证：需要
权限：activity:approve
```

**请求体：**
```json
{
    "approved": true,
    "comment": "同意举办，注意安全"
}
```

**错误码：** 40301（活动不存在）、40304（已审批）、40302（状态不允许审批）

---

### 3.8 更新活动状态为进行中

```
PUT /api/v1/activities/{id}/status/in-progress
认证：需要
```

**约束：** 仅 APPROVED 状态可操作

**错误码：** 40302（状态不允许）

---

### 3.9 结束活动并提交总结

```
PUT /api/v1/activities/{id}/complete
认证：需要
```

**请求体：**
```json
{
    "summary": "本次活动共有 45 人参加，反响良好..."
}
```

**约束：** 仅 IN_PROGRESS 状态可操作

**错误码：** 40302（状态不允许）

---

## 4. 报名与签到模块

### 4.1 报名活动

```
POST /api/v1/registrations
认证：需要
幂等：@Idempotent(key = "#dto.activityId + ':' + T(com.club.management.common.util.SecurityUtil).getCurrentUserId()")
```

**请求体：**
```json
{
    "activityId": 1
}
```

**成功响应：**
```json
{
    "code": 200,
    "message": "操作成功",
    "data": {
        "registrationId": 1,
        "status": 1
    },
    "timestamp": 1719000000000
}
```

**错误码：** 40401（人数已满）、40402（重复报名）、40403（报名已截止）、40301（活动不存在）

---

### 4.2 取消报名

```
DELETE /api/v1/registrations/{activityId}
认证：需要
```

**约束：** 仅状态为"已报名"且活动未开始时取消

**错误码：** 40404（报名记录不存在）、40302（活动已开始，不可取消）

---

### 4.3 查询我的报名列表

```
GET /api/v1/registrations/me
认证：需要
```

**查询参数：**
| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| pageNum | Integer | 否 | 页码 |
| pageSize | Integer | 否 | 每页条数 |
| status | Integer | 否 | 报名状态 |

**成功响应：**
```json
{
    "code": 200,
    "data": {
        "pageNum": 1,
        "pageSize": 10,
        "total": 5,
        "list": [
            {
                "registrationId": 1,
                "activityId": 1,
                "activityTitle": "AI 技术分享会",
                "clubName": "编程社",
                "startTime": "2024-06-15T14:00:00",
                "location": "教学楼 A301",
                "status": 1,
                "registeredAt": "2024-06-01T10:00:00"
            }
        ]
    }
}
```

---

### 4.4 查询活动报名列表（管理员视角）

```
GET /api/v1/activities/{activityId}/registrations
认证：需要
权限：registration:manage
```

**查询参数：** pageNum, pageSize, status

---

### 4.5 生成签到码

```
POST /api/v1/activities/{id}/sign-in/code
认证：需要
权限：signin:manage
```

**成功响应：**
```json
{
    "code": 200,
    "data": {
        "signInCode": "ABC123",
        "expireAt": "2024-06-15T14:30:00"
    }
}
```

---

### 4.6 签到

```
POST /api/v1/sign-in
认证：需要
幂等：Redis SET 去重
```

**请求体：**
```json
{
    "activityId": 1,
    "signInCode": "ABC123"
}
```

**错误码：** 40405（重复签到）、40406（签到码无效）、40407（签到时间已过）

---

### 4.7 查询签到统计

```
GET /api/v1/activities/{id}/sign-in/statistics
认证：需要
权限：signin:manage
```

**成功响应：**
```json
{
    "code": 200,
    "data": {
        "totalRegistered": 45,
        "totalSignedIn": 40,
        "signInRate": 0.89
    }
}
```

---

## 5. 资源管理模块

### 5.1 查询场地列表

```
GET /api/v1/venues
认证：需要
```

**查询参数：** pageNum, pageSize, status, capacity(最小容量)

---

### 5.2 查询场地可用时段

```
GET /api/v1/venues/{id}/availability
认证：需要
```

**查询参数：**
| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| date | String | 是 | 查询日期（yyyy-MM-dd） |

**成功响应：**
```json
{
    "code": 200,
    "data": {
        "venueId": 1,
        "venueName": "教学楼 A301",
        "date": "2024-06-15",
        "bookedSlots": [
            { "startTime": "08:00", "endTime": "10:00", "activityTitle": "晨读会" },
            { "startTime": "14:00", "endTime": "17:00", "activityTitle": "AI 分享会" }
        ],
        "availableSlots": [
            { "startTime": "10:00", "endTime": "14:00" },
            { "startTime": "17:00", "endTime": "22:00" }
        ]
    }
}
```

---

### 5.3 预约场地

```
POST /api/v1/venue-bookings
认证：需要
权限：resource:book
```

**请求体：**
```json
{
    "venueId": 1,
    "activityId": 1,
    "bookingDate": "2024-06-15",
    "startTime": "14:00",
    "endTime": "17:00"
}
```

**错误码：** 40502（场地时间冲突）、40503（场地不存在）

---

### 5.4 取消场地预约

```
DELETE /api/v1/venue-bookings/{id}
认证：需要
```

---

### 5.5 查询设备列表

```
GET /api/v1/equipment
认证：需要
```

**查询参数：** pageNum, pageSize, equipmentType, status

---

### 5.6 借用设备

```
POST /api/v1/equipment-borrows
认证：需要
权限：resource:book
```

**请求体：**
```json
{
    "equipmentId": 1,
    "activityId": 1,
    "borrowCount": 1
}
```

**错误码：** 40504（设备已被借出/数量不足）

---

### 5.7 归还设备

```
PUT /api/v1/equipment-borrows/{id}/return
认证：需要
```

---

## 6. 通知模块

### 6.1 查询我的通知列表

```
GET /api/v1/notifications/me
认证：需要
```

**查询参数：**
| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| pageNum | Integer | 否 | 页码 |
| pageSize | Integer | 否 | 每页条数 |
| type | String | 否 | 通知类型 |
| isRead | Integer | 否 | 0=未读, 1=已读 |

**成功响应：**
```json
{
    "code": 200,
    "data": {
        "pageNum": 1,
        "pageSize": 10,
        "total": 15,
        "list": [
            {
                "id": 1,
                "title": "活动审批通过",
                "content": "您创建的活动「AI 技术分享会」已通过审批",
                "type": "APPROVAL",
                "relatedId": 1,
                "isRead": 0,
                "createdAt": "2024-06-02T09:00:00"
            }
        ]
    }
}
```

---

### 6.2 标记通知为已读

```
PUT /api/v1/notifications/{id}/read
认证：需要
```

---

### 6.3 批量标记已读

```
PUT /api/v1/notifications/read-all
认证：需要
```

---

### 6.4 查询未读通知数量

```
GET /api/v1/notifications/me/unread-count
认证：需要
```

**成功响应：**
```json
{
    "code": 200,
    "data": {
        "count": 5
    }
}
```

---

### 6.5 发送通知（内部接口/管理员使用）

```
POST /api/v1/notifications
认证：需要
权限：notification:send
```

**请求体：**
```json
{
    "userId": 2,
    "title": "系统通知",
    "content": "您的社团审批已通过",
    "type": "SYSTEM",
    "relatedId": null
}
```

---

## 7. 统计模块

### 7.1 社团活动统计

```
GET /api/v1/statistics/clubs/{clubId}/activities
认证：需要
权限：statistics:view
```

**查询参数：**
| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| year | Integer | 否 | 年份，默认当前年 |

**成功响应：**
```json
{
    "code": 200,
    "data": {
        "clubId": 1,
        "clubName": "编程社",
        "year": 2024,
        "totalActivities": 15,
        "monthlyStats": [
            { "month": 1, "activityCount": 2, "participantCount": 60 },
            { "month": 2, "activityCount": 1, "participantCount": 30 }
        ],
        "totalParticipants": 450,
        "averageParticipants": 30
    }
}
```

---

### 7.2 社团考核评分

```
GET /api/v1/statistics/clubs/score
认证：需要
权限：statistics:view
```

**查询参数：** year

**成功响应：**
```json
{
    "code": 200,
    "data": [
        {
            "clubId": 1,
            "clubName": "编程社",
            "activityScore": 85,
            "participationScore": 90,
            "totalScore": 87.5,
            "rank": 1
        }
    ]
}
```

---

### 7.3 系统总览统计

```
GET /api/v1/statistics/overview
认证：需要
权限：statistics:view
```

**成功响应：**
```json
{
    "code": 200,
    "data": {
        "totalUsers": 500,
        "totalClubs": 20,
        "totalActivities": 150,
        "totalParticipants": 3000,
        "recentActivities": [
            {
                "id": 10,
                "title": "最新活动",
                "clubName": "某某社",
                "status": "APPROVED",
                "startTime": "2024-06-20T14:00:00"
            }
        ]
    }
}
```

---

## 8. 预留 AI 接口（暂不实现）

```
POST /api/v1/ai/approval/evaluate      # 智能审批评估
POST /api/v1/ai/activity/plan          # 活动规划
GET  /api/v1/ai/knowledge/query        # 制度问答
POST /api/v1/ai/resource/schedule      # 资源调度
```
