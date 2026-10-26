# AI 接口规范文档

## 1. 接口总览

### 1.1 打卡与签退接口（Java 后端）

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | /api/v1/checkin/tasks | checkin:manage | 创建打卡任务 |
| GET | /api/v1/checkin/tasks/{activityId} | checkin:manage | 获取打卡任务详情 |
| POST | /api/v1/checkin/records | — | 成员提交打卡 |
| GET | /api/v1/checkin/records/{activityId} | checkin:manage | 查看活动打卡列表（团长） |
| GET | /api/v1/checkin/my/{activityId} | — | 查看我的打卡记录 |
| PATCH | /api/v1/checkin/records/{id}/adopt | checkin:manage | 采纳建议 |
| POST | /api/v1/checkin/settle/{activityId} | checkin:manage | 积分结算 |
| POST | /api/v1/checkin/compilation/generate/{activityId} | checkin:manage | 生成汇编文档 |
| GET | /api/v1/checkin/compilation/{activityId} | checkin:view | 查询汇编文档 |
| POST | /api/v1/signout | — | 签退 |

### 1.2 积分接口（Java 后端）

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | /api/v1/points/my | points:view | 查看我的积分流水 |
| GET | /api/v1/points/ranking | points:view | 社团积分排行榜 |
| GET | /api/v1/points/summary | points:view | 我的积分汇总 |

### 1.3 AI 对话接口（Java 代理 → Python）

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | /api/v1/ai/chat | ai:chat | 发送对话消息 |
| POST | /api/v1/ai/chat/sessions | ai:chat | 创建对话会话 |
| GET | /api/v1/ai/chat/sessions | ai:chat | 获取会话列表 |
| DELETE | /api/v1/ai/chat/sessions/{sessionId} | ai:chat | 关闭会话 |

### 1.4 AI 成果报告接口（Java 代理 → Python）

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | /api/v1/ai/report/generate | ai:report:manage | 触发成果报告生成 |
| GET | /api/v1/ai/report/{activityId} | ai:report:manage | 获取报告详情 |
| POST | /api/v1/ai/report/{id}/confirm | ai:report:manage | 确认报告入库 |
| POST | /api/v1/ai/report/{id}/reject | ai:report:manage | 打回报告 |
| GET | /api/v1/ai/report/{id}/download | ai:report:manage | 下载 Word 文件 |

### 1.5 AI 经验参考接口（Java 代理 → Python）

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | /api/v1/ai/experience | ai:chat | 获取活动经验参考 |

### 1.6 知识库管理接口（Java 代理 → Python）

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | /api/v1/ai/knowledge/documents | ai:knowledge:manage | 上传文档入库 |
| GET | /api/v1/ai/knowledge/documents | ai:knowledge:manage | 已入库文档列表 |
| DELETE | /api/v1/ai/knowledge/documents/{id} | ai:knowledge:manage | 删除文档（含向量） |
| POST | /api/v1/ai/knowledge/documents/{id}/reindex | ai:knowledge:manage | 重新入库 |

---

## 2. 打卡与签退接口详细定义

### 2.1 创建打卡任务

**POST** `/api/v1/checkin/tasks`  
**权限：** checkin:manage（团长）

**请求体：**
```json
{
    "activityId": 15,
    "title": "AI技术分享会打卡",
    "description": "请分享你的学习心得和建议",
    "requireReflection": true,
    "requirePhoto": false,
    "requireSuggestion": true,
    "deadlineHours": 24
}
```

**校验规则：**
- activityId: 必填，活动必须存在且属于当前用户的社团
- title: 必填，1-100 字符
- deadlineHours: 必填，1-168（1小时到7天）

**成功响应：**
```json
{
    "code": 200,
    "message": "success",
    "data": {
        "id": 1,
        "activityId": 15,
        "title": "AI技术分享会打卡",
        "deadline": "2026-07-16T17:00:00",
        "status": 1
    }
}
```

**错误码：**
- 40300: 活动不存在
- 40301: 无权限（非该社团团长）
- 40350: 打卡任务已存在

---

### 2.2 提交打卡

**POST** `/api/v1/checkin/records`  
**权限：** 登录用户（社团成员）

**请求体：**
```json
{
    "activityId": 15,
    "rating": 4,
    "reflection": "这次分享会对 Transformer 的讲解很清晰，特别是注意力机制的部分...",
    "problem": "实操环节太少，没有足够时间动手尝试",
    "suggestion": "建议下次增加实操环节，让大家动手写代码",
    "photoUrls": ["/api/v1/files/photos/1/15/1719000000_abc.jpg"],
    "fileUrls": []
}
```

**校验规则：**
- activityId: 必填
- rating: 必填，1-5
- reflection: 根据打卡任务 requireReflection 决定是否必填，50-2000 字符
- problem: 选填，10-1000 字符
- suggestion: 根据 requireSuggestion 决定是否必填，10-1000 字符
- 如果 requirePhoto=true，photoUrls 不能为空

**成功响应：**
```json
{
    "code": 200,
    "message": "success",
    "data": {
        "id": 1,
        "activityId": 15,
        "userId": 3,
        "rating": 4,
        "reflection": "这次分享会对 Transformer 的讲解很清晰...",
        "createdAt": "2026-07-15T16:30:00"
    }
}
```

**错误码：**
- 40350: 打卡任务不存在
- 40351: 打卡已截止
- 40352: 已提交过打卡（幂等校验）
- 40353: 非该社团成员
- 40354: 未签到，无法打卡

---

### 2.3 采纳建议

**PATCH** `/api/v1/checkin/records/{id}/adopt`  
**权限：** checkin:manage（团长）

**成功响应：**
```json
{
    "code": 200,
    "message": "success",
    "data": {
        "id": 1,
        "isAdopted": true
    }
}
```

---

### 2.4 积分结算

**POST** `/api/v1/checkin/settle/{activityId}`  
**权限：** checkin:manage（团长）

**说明：** 打卡截止后，团长触发积分结算。系统根据该活动下所有打卡记录批量计算积分。

**成功响应：**
```json
{
    "code": 200,
    "message": "success",
    "data": {
        "activityId": 15,
        "settledCount": 12,
        "totalPoints": 48
    }
}
```

**错误码：**
- 40355: 打卡尚未截止，不可结算
- 40356: 已结算过

---

### 2.5 签退

**POST** `/api/v1/signout`  
**权限：** 登录用户

**请求体：**
```json
{
    "activityId": 15
}
```

**成功响应：**
```json
{
    "code": 200,
    "message": "success",
    "data": {
        "activityId": 15,
        "signedOutAt": "2026-07-15T17:00:00"
    }
}
```

**错误码：**
- 40357: 未签到，无法签退
- 40358: 已签退过

---

### 2.6 生成汇编文档

**POST** `/api/v1/checkin/compilation/generate/{activityId}`  
**权限：** checkin:manage（团长）

打卡截止后，团长手动触发（或系统自动触发）。系统读取该活动全部打卡记录，生成汇编文档，并自动入库 RAG。

**成功响应：**
```json
{
    "code": 200,
    "message": "success",
    "data": {
        "id": 1,
        "activityId": 15,
        "memberCount": 8,
        "avgRating": 4.2,
        "problemSummary": "共 5 位成员提出问题",
        "suggestionSummary": "共 6 位成员提出建议",
        "isIngested": true,
        "createdAt": "2026-07-16T10:00:00"
    }
}
```

**错误码：**
- 40360: 打卡任务不存在
- 40361: 打卡尚未截止，不能生成汇编
- 40362: 汇编文档已存在（幂等校验）

---

### 2.7 查询汇编文档

**GET** `/api/v1/checkin/compilation/{activityId}`  
**权限：** checkin:view（团长/成员）

**成功响应：**
```json
{
    "code": 200,
    "message": "success",
    "data": {
        "id": 1,
        "activityId": 15,
        "content": "《AI技术分享会》成员反馈汇编\n\n一、基本信息\n...",
        "filePath": "/data/compilations/1/15/汇编文档.docx",
        "memberCount": 8,
        "avgRating": 4.2,
        "problemSummary": "共 5 位成员提出问题",
        "suggestionSummary": "共 6 位成员提出建议",
        "isIngested": true,
        "createdAt": "2026-07-16T10:00:00"
    }
}
```

**错误码：**
- 40363: 汇编文档不存在

---

## 3. 积分接口详细定义

### 3.1 查看我的积分流水

**GET** `/api/v1/points/my?clubId=1&pageNum=1&pageSize=20`  
**权限：** points:view

**成功响应：**
```json
{
    "code": 200,
    "message": "success",
    "data": {
        "records": [
            {
                "id": 1,
                "points": 2,
                "reason": "CHECKIN_REFLECTION",
                "description": "提交活动打卡心得",
                "relatedActivityId": 15,
                "createdAt": "2026-07-15T18:00:00"
            }
        ],
        "total": 25,
        "pageNum": 1,
        "pageSize": 20
    }
}
```

### 3.2 社团积分排行榜

**GET** `/api/v1/points/ranking?clubId=1&top=20`  
**权限：** points:view

**成功响应：**
```json
{
    "code": 200,
    "message": "success",
    "data": [
        {
            "rank": 1,
            "userId": 3,
            "realName": "张三",
            "totalPoints": 48,
            "avatar": "/api/v1/files/avatars/3.jpg"
        }
    ]
}
```

### 3.3 积分汇总

**GET** `/api/v1/points/summary?clubId=1`  
**权限：** points:view

**成功响应：**
```json
{
    "code": 200,
    "message": "success",
    "data": {
        "totalPoints": 48,
        "rank": 3,
        "memberCount": 25
    }
}
```

---

## 4. AI 对话接口详细定义

### 4.1 发送对话消息

**POST** `/api/v1/ai/chat`  
**权限：** ai:chat

**请求体：**
```json
{
    "clubId": 1,
    "sessionId": "uuid-xxx",
    "message": "编程社每周活动几次？"
}
```

**校验规则：**
- clubId: 必填
- message: 必填，1-2000 字符
- sessionId: 可选，不传则创建新会话

**成功响应：**
```json
{
    "code": 200,
    "message": "success",
    "data": {
        "sessionId": "uuid-xxx",
        "answer": "根据编程社的招新说明，社团每周六下午 2:00-5:00 固定活动，特殊情况会提前在社团群通知。[来源1]",
        "sources": [
            {
                "docId": "doc_001",
                "title": "编程社招新说明",
                "snippet": "每周六下午2:00-5:00固定活动...",
                "docType": "recruitment"
            }
        ]
    }
}
```

**错误码：**
- 40700: AI 服务不可用
- 40701: LLM 生成失败
- 40702: 知识库中无相关内容

---

### 4.2 创建对话会话

**POST** `/api/v1/ai/chat/sessions`  
**权限：** ai:chat

**请求体：**
```json
{
    "clubId": 1
}
```

**成功响应：**
```json
{
    "code": 200,
    "message": "success",
    "data": {
        "sessionId": "uuid-xxx",
        "clubId": 1,
        "createdAt": "2026-07-15T14:00:00"
    }
}
```

---

## 5. AI 成果报告接口详细定义

### 5.1 触发成果报告生成

**POST** `/api/v1/ai/report/generate`  
**权限：** ai:report:manage（团长）

**请求体：**
```json
{
    "activityId": 15
}
```

**成功响应：**
```json
{
    "code": 200,
    "message": "success",
    "data": {
        "reportId": 1,
        "activityId": 15,
        "status": "DRAFT",
        "message": "报告生成中，请稍后查看"
    }
}
```

**错误码：**
- 40300: 活动不存在
- 40359: 活动尚未结束，不可生成报告
- 40360: 打卡数据为空，无法生成报告
- 40361: 报告已存在

---

### 5.2 获取报告详情

**GET** `/api/v1/ai/report/{activityId}`  
**权限：** ai:report:manage

**成功响应：**
```json
{
    "code": 200,
    "message": "success",
    "data": {
        "id": 1,
        "activityId": 15,
        "title": "AI技术分享会-成果报告",
        "content": "# AI技术分享会成果报告\n\n## 一、活动基本信息\n...",
        "filePath": "/api/v1/ai/report/1/download",
        "avgRating": 4.3,
        "participantCount": 12,
        "checkinCount": 10,
        "status": "DRAFT",
        "revisionCount": 0,
        "createdAt": "2026-07-15T18:00:00"
    }
}
```

---

### 5.3 确认报告入库

**POST** `/api/v1/ai/report/{id}/confirm`  
**权限：** ai:report:manage（团长）

**成功响应：**
```json
{
    "code": 200,
    "message": "success",
    "data": {
        "id": 1,
        "status": "CONFIRMED",
        "isIngested": true,
        "message": "报告已确认并入库知识库"
    }
}
```

---

### 5.4 打回报告

**POST** `/api/v1/ai/report/{id}/reject`  
**权限：** ai:report:manage（团长）

**请求体：**
```json
{
    "reason": "经验教训部分过于笼统，请结合具体数据重新生成"
}
```

**成功响应：**
```json
{
    "code": 200,
    "message": "success",
    "data": {
        "id": 1,
        "status": "REJECTED",
        "revisionCount": 1,
        "message": "已打回，Agent 将重新生成"
    }
}
```

---

## 6. AI 经验参考接口详细定义

### 6.1 获取活动经验参考

**POST** `/api/v1/ai/experience`  
**权限：** ai:chat

**请求体：**
```json
{
    "clubId": 1,
    "activityType": "CAMPUS",
    "title": "新学期技术分享会",
    "location": "教学楼A301",
    "maxParticipants": 50,
    "budget": 500
}
```

**成功响应：**
```json
{
    "code": 200,
    "message": "success",
    "data": {
        "hasHistory": true,
        "experienceSummary": "根据贵社团过去3次技术分享会的经验，活动整体评分在4.0-4.5之间，成员对实操环节评价较高。",
        "suggestions": [
            "建议控制活动时长在2-3小时内，历史数据显示超过3小时的活动评分下降",
            "提前一周预约场地，避免场地冲突",
            "实操环节预留至少40分钟，成员反馈时间不足"
        ],
        "riskWarnings": [
            "上次类似活动预算超支15%，建议预留缓冲",
            "冬季户外活动参与率较低"
        ],
        "template": null
    }
}
```

---

## 7. 知识库管理接口详细定义

### 7.1 上传文档入库

**POST** `/api/v1/ai/knowledge/documents`  
**权限：** ai:knowledge:manage（社长）  
**Content-Type:** multipart/form-data

**请求参数：**
- file: 文件（txt/md/docx）
- docType: 文档类型（charter/recruitment/faq）
- title: 文档标题

**成功响应：**
```json
{
    "code": 200,
    "message": "success",
    "data": {
        "id": 1,
        "clubId": 1,
        "docType": "recruitment",
        "title": "2026年秋季招新说明",
        "chunkCount": 3,
        "status": 1,
        "createdAt": "2026-07-15T14:00:00"
    }
}
```

**错误码：**
- 40705: 文件格式不支持
- 40706: 文件大小超限（限制 10MB）

---

### 7.2 已入库文档列表

**GET** `/api/v1/ai/knowledge/documents?clubId=1&pageNum=1&pageSize=20`  
**权限：** ai:knowledge:manage

**成功响应：**
```json
{
    "code": 200,
    "message": "success",
    "data": {
        "records": [
            {
                "id": 1,
                "docType": "recruitment",
                "title": "2026年秋季招新说明",
                "sourceType": "upload",
                "chunkCount": 3,
                "createdAt": "2026-07-15T14:00:00"
            },
            {
                "id": 2,
                "docType": "activity_report",
                "title": "AI技术分享会-成果报告",
                "sourceType": "system",
                "chunkCount": 5,
                "createdAt": "2026-07-15T18:30:00"
            }
        ],
        "total": 2,
        "pageNum": 1,
        "pageSize": 20
    }
}
```

---

### 7.3 删除文档

**DELETE** `/api/v1/ai/knowledge/documents/{id}`  
**权限：** ai:knowledge:manage（社长）

**成功响应：**
```json
{
    "code": 200,
    "message": "success",
    "data": null
}
```

---

## 8. 文件上传接口

### 8.1 通用文件上传

**POST** `/api/v1/files/upload`  
**权限：** 登录用户  
**Content-Type:** multipart/form-data

**请求参数：**
- file: 文件
- type: 文件类型（photo/document）

**成功响应：**
```json
{
    "code": 200,
    "message": "success",
    "data": {
        "url": "/api/v1/files/photos/1/15/1719000000_abc.jpg",
        "fileName": "photo.jpg",
        "fileSize": 102400
    }
}
```

**限制：**
- 照片：最大 5MB，支持 jpg/png/webp
- 文档：最大 10MB，支持 txt/md/docx

---

## 9. Python 服务内部接口

Java 后端代理转发到 Python 服务的接口，路径与 Java 端保持一致但去掉 `/api/v1` 前缀：

| Java 路径 | Python 路径 |
|---|---|
| POST /api/v1/ai/chat | POST /chat |
| POST /api/v1/ai/chat/sessions | POST /chat/sessions |
| POST /api/v1/ai/report/generate | POST /report/generate |
| GET /api/v1/ai/report/{activityId} | GET /report/{activityId} |
| POST /api/v1/ai/report/{id}/confirm | POST /report/{id}/confirm |
| POST /api/v1/ai/report/{id}/reject | POST /report/{id}/reject |
| POST /api/v1/ai/experience | POST /experience |
| POST /api/v1/ai/knowledge/documents | POST /knowledge/documents |
| GET /api/v1/ai/knowledge/documents | GET /knowledge/documents |
| DELETE /api/v1/ai/knowledge/documents/{id} | DELETE /knowledge/documents/{id} |
