# AI 服务架构文档

## 1. 概述

本文档定义社团管理系统的 AI 层架构设计。AI 层作为独立 Python 服务部署，Java 后端通过 HTTP 代理转发，前端不直接访问 AI 服务。

**核心能力：**
- RAG 知识问答（招新助手 + 社团内部知识助手）
- 成果撰写 Agent（汇编打卡内容为结构化报告）
- 经验参考 Agent（基于历史成果辅助活动策划）

---

## 2. 技术选型

| 组件 | 技术 | 版本 | 说明 |
|---|---|---|---|
| AI 服务框架 | FastAPI | 0.110+ | Python Web 框架 |
| 向量数据库 | ChromaDB | 0.5+ | 嵌入式向量数据库，支持元数据过滤和自动持久化 |
| Embedding 模型 | BAAI/bge-large-zh-v1.5 | - | 本地模型，sentence-transformers 加载，CPU 可运行 |
| LLM | DeepSeek V4 | - | 远程 API，兼容 OpenAI 格式 |
| LLM SDK | openai | 1.x | Python SDK，切换 base_url 指向 DeepSeek |
| Agent 框架 | LangGraph | 0.2+ | 多步骤 Agent 编排 |
| 文档处理 | python-docx | - | 解析 docx 文件 |
| BM25 检索 | rank_bm25 | - | 关键词检索 |
| 文档分块 | langchain-text-splitters | - | 文本分块工具 |
| 数据库连接 | pymysql + aiomysql | - | 读取 MySQL 中的业务数据 |
| 对话缓存 | redis | - | 多轮对话历史存储 |

---

## 3. 系统架构

### 3.1 整体架构图

```
┌─────────────────────────────────────────────────┐
│              Vue 3 前端 (5174)                    │
└──────────────────────┬──────────────────────────┘
                       │ HTTP
┌──────────────────────▼──────────────────────────┐
│              Java 后端 Spring Boot (8081)         │
│                                                   │
│  ┌─────────────────────────────────────────────┐ │
│  │  业务模块（已有）                              │ │
│  │  用户 | 社团 | 活动 | 报名签到 | 资源 | 通知  │ │
│  └─────────────────────────────────────────────┘ │
│  ┌─────────────────────────────────────────────┐ │
│  │  AI 代理层（新增）                            │ │
│  │  AiController → AiService → HTTP 转发        │ │
│  └──────────────────────┬──────────────────────┘ │
└─────────────────────────┼────────────────────────┘
                          │ HTTP (localhost:8000)
┌─────────────────────────▼────────────────────────┐
│              Python AI 服务 (FastAPI 8000)         │
│                                                    │
│  ┌──────────────────────────────────────────────┐ │
│  │  RAG 引擎                                    │ │
│  │  ingestion | retrieval | generator            │ │
│  └──────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────┐ │
│  │  Agent 层（LangGraph）                        │ │
│  │  report_agent | experience_agent              │ │
│  └──────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────┐ │
│  │  知识问答（简单 RAG）                         │ │
│  │  recruitment_agent（招新/社团知识助手）        │ │
│  └──────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────┐ │
│  │  基础设施                                    │ │
│  │  ChromaDB | Embedding | BM25 | Redis          │ │
│  └──────────────────────────────────────────────┘ │
└──────────┬──────────────────────┬─────────────────┘
           │                      │
    ┌──────▼──────┐        ┌──────▼──────┐
    │   MySQL 8   │        │    Redis     │
    │  (共享)     │        │  (对话缓存)  │
    └─────────────┘        └─────────────┘
```

### 3.2 通信规范

| 调用方 | 被调用方 | 协议 | 说明 |
|---|---|---|---|
| 前端 | Java 后端 | HTTP REST | 唯一入口，携带 JWT |
| Java 后端 | Python 服务 | HTTP REST | 内部通信，不暴露给前端 |
| Python 服务 | MySQL | JDBC/SQL | 读取活动、打卡、成员等业务数据 |
| Python 服务 | Redis | TCP | 存储对话历史 |
| Python 服务 | ChromaDB | 本地文件 | 向量存储与检索 |
| Python 服务 | DeepSeek API | HTTPS | LLM 推理调用 |

### 3.3 请求流转示例

```
用户提问"编程社每周活动几次？"
    │
    ▼
前端 POST /api/v1/ai/chat {clubId: 1, message: "..."}
    │ 携带 JWT
    ▼
Java AiController → 校验 JWT → 提取 clubId → 转发
    │
    ▼
Python POST /chat {club_id: 1, message: "...", session_id: "xxx"}
    │
    ▼
Redis 加载对话历史 → RAG 检索（ChromaDB club_1 集合）
    │
    ▼
组装 Prompt → DeepSeek API → 解析回答
    │
    ▼
返回 {answer: "...", sources: [{doc_id, title, snippet}]}
    │
    ▼
Java 接收 → 包装为 R<T> → 返回前端
```

---

## 4. RAG 引擎设计

### 4.1 知识库结构

每个社团一个独立的 ChromaDB Collection，实现知识隔离：

```
data/chroma/
├── club_1/              # 编程社的 ChromaDB collection
├── club_2/              # 摄影社的 ChromaDB collection
└── ...
```

### 4.2 文档元数据

每条入库文档附带以下元数据：

```python
{
    "club_id": 1,                    # 所属社团
    "doc_type": "activity_report",   # 文档类型：charter/recruitment/faq/checkin_compilation/activity_report
    "activity_id": 15,               # 关联活动 ID（仅 activity_report/checkin_compilation 有值）
    "section_type": "problems",      # 切块所属维度（仅 checkin_compilation 使用）：reflections/problems/suggestions/general
    "title": "AI技术分享会-成果报告", # 文档标题
    "source": "system",              # 来源：system(系统自动生成) / upload(用户上传)
    "created_at": "2026-06-20"       # 入库时间
}
```

### 4.3 文档类型定义

| doc_type | 内容来源 | 入库触发时机 |
|---|---|---|
| charter | 社团章程（club_info.charter） | 社团创建/章程更新时 |
| recruitment | 招新说明（社长上传） | 社长在知识库管理页上传时 |
| faq | 常见问答（社长编辑） | 社长在知识库管理页编辑时 |
| checkin_compilation | 打卡汇编文档（系统自动汇编成员反馈） | 打卡截止后自动生成并入库 |
| activity_report | 活动成果报告（Agent 生成 + 团长确认） | 团长确认报告后自动触发 |

### 4.4 入库流程

```
文档输入（文本/文件）
    │
    ▼
文本提取（txt/md 直接读取，docx 用 python-docx 解析）
    │
    ▼
根据 doc_type 选择切块策略（见 4.5 节）
    │
    ▼
Embedding（bge-large-zh-v1.5 本地模型）
    │
    ▼
写入 ChromaDB Collection（文档 + 元数据 + 向量）
    │
    ▼
同步更新 BM25 索引（用于关键词检索）
```

### 4.5 切块策略

不同文档类型使用不同的切块策略：

| doc_type | 切块策略 | 说明 |
|---|---|---|
| charter | 按章节切块（按 ## 标题分割） | 保留章节标题作为上下文 |
| recruitment | 固定大小切块（512 token, 重叠 64） | 信息密集，固定切块即可 |
| faq | 按 Q&A 对切块 | 每个问答对作为一个独立 chunk |
| checkin_compilation | 按维度切块（见下文） | 核心优化点，提升召回率 |
| activity_report | 按章节切块（按 ## 标题分割） | 结构化报告，按章节保留上下文 |

**打卡汇编文档的按维度切块：**

汇编文档包含多个成员的反馈，每个成员的反馈包含收获、问题、建议三个维度。按维度切块而非按成员切块，使得检索“上次活动有什么问题”时能精准命中问题部分，不会被收获内容稀释。

```
原始汇编文档：
  张三的收获 + 张三的问题 + 张三的建议
  李四的收获 + 李四的问题 + 李四的建议
  ...

切块后：
  chunk_1: [所有成员的收获] → metadata: section_type=reflections
  chunk_2: [所有成员的问题] → metadata: section_type=problems
  chunk_3: [所有成员的建议] → metadata: section_type=suggestions
  chunk_4: [基本信息 + 统计汇总] → metadata: section_type=general
```

每个 chunk 附带元数据：
```python
{
    "doc_type": "checkin_compilation",
    "activity_id": 15,
    "section_type": "problems",  # reflections / problems / suggestions / general
    "contributor_ids": [3, 5, 7]  # 该维度下有内容的成员 ID 列表
}
```

**检索时的维度过滤：**

当用户提问涉及特定维度时，可通过 section_type 过滤提升召回率：
- “上次活动有什么问题” → where section_type = "problems"
- “下次怎么改进” → where section_type = "suggestions"
- “成员学到了什么” → where section_type = "reflections"
- 通用问题 → 不过滤，检索所有维度

维度识别通过简单的关键词匹配实现：
```python
def detect_section_type(query: str) -> Optional[str]:
    problem_keywords = ["问题", "不足", "困难", "体验不好", "缺点", "失误"]
    suggestion_keywords = ["改进", "建议", "下次", "优化", "提升", "怎么改"]
    reflection_keywords = ["收获", "学到", "心得", "启发", "成长", "理解"]
    
    if any(kw in query for kw in problem_keywords):
        return "problems"
    elif any(kw in query for kw in suggestion_keywords):
        return "suggestions"
    elif any(kw in query for kw in reflection_keywords):
        return "reflections"
    return None  # 不过滤
```

### 4.6 检索流程与召回率优化

```
用户 Query
    │
    ├─→ 维度识别（关键词匹配，判断是否需要 section_type 过滤）
    │
    ├─→ Embedding → ChromaDB 向量检索 Top-20（带元数据过滤）
    │
    ├─→ 分词 → BM25 关键词检索 Top-20
    │
    ▼
RRF（Reciprocal Rank Fusion）融合排序
    score = Σ 1/(k + rank_i), k=60
    │
    ▼
取 Top-5 文档片段
    │
    ▼
组装 Prompt → DeepSeek V4 生成回答
```

**召回率优化策略：**

1. **维度过滤**：通过 section_type 元数据过滤，将检索范围缩小到相关维度，提升精准度
2. **Query 改写**（可选）：用 LLM 将用户口语化提问扩展为检索关键词
   ```
   用户问：“上次办活动踩了什么坑”
   改写为：“活动 问题 不足 困难 反馈 失误”
   ```
3. **Rerank 重排序**（可选加分项）：RRF 融合后取 Top-10，用 Cross-Encoder（bge-reranker-v2-m3）重排序，取 Top-3

**召回率评估：**

构建简单评估集，定期测试：

| Query | 期望召回的文档类型 | 期望命中的 section_type |
|---|---|---|
| “编程社每周活动几次” | recruitment | — |
| “上次活动有什么问题” | checkin_compilation | problems |
| “加入社团要缴费吗” | charter / faq | — |
| “上次成员有什么建议” | checkin_compilation | suggestions |
| “技术分享会的经验” | activity_report | — |

计算 Recall@K = 召回的正确文档数 / 应该召回的文档总数

### 4.7 Prompt 模板

```
你是一个社团知识助手。基于以下参考资料回答用户的问题。

规则：
1. 只基于提供的参考资料回答，不要编造信息
2. 如果参考资料中没有相关信息，明确告知用户
3. 回答中标注引用来源，格式为 [来源1]、[来源2]

参考资料：
{context}

用户问题：{question}
```

---

## 5. Agent 设计

### 5.1 完整数据流（打卡 → 汇编 → 报告）

```
[活动进行中]
  成员签到 → 参加活动 → 打卡提交（收获/问题/建议/照片）→ 签退
      ↓
[打卡截止]
  系统自动触发汇编：
    1. 查询该活动全部打卡记录
    2. 对照片调用多模态 LLM 生成描述文本
    3. 按模板拼接汇编文档（不需要 LLM）
    4. 生成 Word 文件归档
    5. 汇编文档入库 RAG（按维度切块）
    6. 触发积分结算
      ↓
[团长触发]
  成果撰写 Agent：
    1. 读取汇编文档（而非原始打卡记录）
    2. LLM 分类整理 → 生成报告草稿 → 转 Word
    3. 团长审核 → 确认/打回
    4. 报告入库 RAG
```

### 5.2 图片处理流程

成员打卡时上传的照片，通过多模态 LLM 生成描述文本，使图片内容可被 RAG 检索。

```
成员上传照片 → 保存到本地磁盘
    │
    ▼
异步调用多模态 LLM（DeepSeek VL 系列）
    │
    ▼
生成图片描述文本（重点关注：场景、人物活动、关键细节）
    │
    ▼
描述文本存入 act_checkin_record.image_descriptions（JSON 数组，与 photo_urls 一一对应）
    │
    ▼
汇编文档生成时，将图片描述插入对应成员的反馈中
```

**多模态 LLM 调用：**
```python
def describe_image(image_url: str) -> str:
    """调用多模态 LLM 生成图片描述"""
    response = multimodal_client.chat.completions.create(
        model="deepseek-vl2-small",  # 多模态版本
        messages=[{
            "role": "user",
            "content": [
                {"type": "text", "text": "请简要描述这张图片的内容，重点关注：场景、人物活动、关键细节。50字以内。"},
                {"type": "image_url", "image_url": {"url": image_url}}
            ]
        }]
    )
    return response.choices[0].message.content
```

**降级策略：** 如果多模态 LLM 调用失败（网络超时、API 错误），跳过图片描述生成，图片仅作为附件展示，不参与 RAG 检索。

### 5.3 Agent 1：成果撰写 Agent

**触发时机：** 活动结束 + 打卡截止后，由团长手动触发或系统自动触发

**LangGraph 图结构：**

```
START
  → collect_compilation   # 从 MySQL 读取该活动的汇编文档
  → classify_materials   # LLM 按维度分类（收获/建议/评价/问题）
  → generate_draft       # LLM 基于分类结果 + 活动信息生成报告草稿
  → generate_document    # 将草稿转为 Word 文档（python-docx）
  → human_review         # LangGraph interrupt，等待团长审核
    → approved → save_report → ingest_to_knowledge_base → END
    → rejected → revise_draft（携带修改意见）→ generate_draft（循环）
```

**节点详细说明：**

| 节点 | 功能 | 输入 | 输出 |
|---|---|---|---|
| collect_materials | 查询 MySQL 获取打卡数据 | activity_id | List[CheckInRecord] |
| classify_materials | LLM 分类打卡内容 | 打卡内容列表 | Dict[维度, List[内容]] |
| generate_draft | LLM 生成结构化报告 | 分类结果 + 活动基本信息 | ReportDraft |
| generate_document | 生成 Word 文件 | ReportDraft | 文件路径 |
| human_review | 等待团长操作 | 草稿 | approved/rejected + 修改意见 |
| revise_draft | LLM 根据反馈修改 | 原草稿 + 修改意见 | 新 ReportDraft |
| save_report | 保存到 MySQL | ReportDraft + 文件路径 | ai_activity_report 记录 |
| ingest_to_knowledge_base | 文档入库 | 报告文本 + 元数据 | ChromaDB 写入 |

**成果报告结构：**

```
一、活动基本信息
   标题、时间、地点、类型、参与人数、预算（从 act_activity 表取）

二、成员反馈汇总
   2.1 整体评价：平均评分 X/5
   2.2 主要收获：（Agent 摘要 + 原文引用）
   2.3 改进建议：（Agent 归类合并）

三、经验教训
   （Agent 基于评分和建议提炼）

四、附件清单
   （成员上传的照片/文件列表）
```

### 5.4 Agent 2：经验参考 Agent

**触发时机：** 团长创建新活动时调用

**LangGraph 图结构：**

```
START
  → parse_activity_info    # 解析新活动信息（类型/规模/场地）
  → retrieve_experience    # RAG 检索该社团历史成果
  → check_sufficiency      # 判断检索结果是否充分
    → 充分 → generate_suggestions  # LLM 生成经验总结 + 策划建议
    → 不充分 → generate_template   # 返回通用策划模板
  → END
```

**输出结构：**

```json
{
  "has_history": true,
  "experience_summary": "根据历史活动经验...",
  "suggestions": [
    "建议控制活动时长在2-3小时内",
    "提前一周预约场地",
    "实操环节预留充足时间"
  ],
  "risk_warnings": [
    "上次类似活动反馈时间不足",
    "预算超支率较高"
  ],
  "template": null
}
```

### 5.5 招新/知识助手（非 Agent，纯 RAG 问答）

**触发时机：** 用户在社团详情页打开对话窗口

**流程：**

```
用户提问 → 加载对话历史（Redis）→ RAG 检索 → LLM 生成回答 → 保存对话历史 → 返回
```

**对话历史管理：**
- Redis Key: `ai:chat:{club_id}:{session_id}`
- 存储格式: JSON 数组 [{role: "user", content: "..."}, {role: "assistant", content: "..."}]
- TTL: 30 分钟（自动过期）
- 最多保留最近 10 轮对话

---

## 6. Python 服务目录结构

```
ai-service/
├── main.py                      # FastAPI 入口，注册路由
├── config.py                    # 配置：LLM API、ChromaDB 路径、Redis 连接
├── requirements.txt             # Python 依赖
│
├── rag/
│   ├── __init__.py
│   ├── ingestion.py             # 文档入库：文本提取 → 分块 → Embedding → 写入 ChromaDB
│   ├── retrieval.py             # 混合检索：向量检索 + BM25 + RRF 融合
│   ├── generator.py             # 生成：Prompt 组装 + DeepSeek 调用
│   └── knowledge_base.py        # 知识库管理：ChromaDB Collection 的 CRUD 操作
│
├── agents/
│   ├── __init__.py
│   ├── report_agent.py          # 成果撰写 Agent（LangGraph）
│   └── experience_agent.py      # 经验参考 Agent（LangGraph）
│
├── tools/
│   ├── __init__.py
│   ├── db_reader.py             # MySQL 数据读取（活动、打卡、成员）
│   ├── document_parser.py       # 文档解析（txt/md/docx）
│   └── document_generator.py    # Word 文档生成（python-docx）
│
├── store/
│   ├── __init__.py
│   ├── vector_store.py          # 向量存储抽象接口
│   └── chroma_store.py          # ChromaDB 实现
│
├── models/
│   ├── __init__.py
│   └── schemas.py               # Pydantic 请求/响应模型
│
├── routes/
│   ├── __init__.py
│   ├── chat.py                  # 对话路由（招新/知识助手）
│   ├── report.py                # 成果撰写 Agent 路由
│   ├── experience.py            # 经验参考 Agent 路由
│   └── knowledge.py             # 知识库管理路由（入库/查询/删除）
│
└── data/
    └── chroma/                  # ChromaDB 持久化目录
        ├── club_1/
        ├── club_2/
        └── ...
```

---

## 7. Java 后端新增内容

### 7.1 新增文件

```
com.club.management.ai/
├── controller/
│   └── AiController.java            # AI 接口入口
├── service/
│   ├── AiService.java               # 接口
│   └── impl/
│       └── AiServiceImpl.java       # 实现：HTTP 调用 Python 服务
├── dto/
│   ├── ChatRequestDTO.java          # 对话请求
│   ├── ReportGenerateDTO.java       # 触发报告生成
│   └── ReportReviewDTO.java         # 报告审核
├── vo/
│   ├── ChatResponseVO.java          # 对话回答
│   ├── ReportDraftVO.java           # 报告草稿
│   └── ExperienceSuggestionVO.java  # 经验建议
└── config/
    └── AiConfig.java                # Python 服务地址配置
```

### 7.2 新增权限

```
ai:chat          # AI 对话
ai:report:manage # 成果报告管理
```

### 7.3 新增错误码

| 错误码 | 常量名 | 说明 |
|---|---|---|
| 40700 | AI_SERVICE_UNAVAILABLE | AI 服务不可用 |
| 40701 | AI_GENERATION_FAILED | LLM 生成失败 |
| 40702 | AI_KNOWLEDGE_NOT_FOUND | 知识库中无相关内容 |
| 40703 | AI_REPORT_NOT_FOUND | 报告不存在 |
| 40704 | AI_REPORT_ALREADY_CONFIRMED | 报告已确认，不可修改 |

---

## 8. 前端新增内容

### 8.1 新增页面/组件

```
src/views/ai/
├── chat.vue              # 对话窗口组件（嵌入社团详情页）
├── report.vue            # 成果报告审核页（嵌入活动详情页，团长可见）
└── knowledge.vue         # 知识库管理页（社长使用：上传文档、查看已入库列表）

src/api/
└── ai.js                 # AI 相关 API 封装
```

### 8.2 对话窗口组件（chat.vue）

- 嵌入社团详情页，作为 Tab 或浮动按钮
- 支持多轮对话，消息气泡展示
- 显示引用来源（可展开查看原文片段）
- 输入框 + 发送按钮

### 8.3 成果报告审核页（report.vue）

- 仅团长可见
- 展示 Agent 生成的报告草稿（Markdown 渲染）
- 提供 Word 文件下载预览
- 「确认入库」和「打回修改」按钮
- 打回时弹出修改意见输入框

### 8.4 知识库管理页（knowledge.vue）

- 仅社长可见
- 文档上传（支持 txt/md/docx）
- 已入库文档列表（标题、类型、入库时间、操作）
- 文档删除（同步删除 ChromaDB 中的向量）
- 文档重新入库

---

## 9. 向量存储抽象设计

为支持后续从 ChromaDB 迁移到 pgvector，定义抽象接口：

```python
# store/vector_store.py

from abc import ABC, abstractmethod
from typing import List, Dict, Optional

class VectorStoreRepository(ABC):
    """向量存储抽象接口，支持后续切换实现"""

    @abstractmethod
    def add_documents(self, collection: str, documents: List[str],
                      metadatas: List[Dict], ids: List[str]) -> None:
        """批量添加文档"""
        pass

    @abstractmethod
    def search(self, collection: str, query_embedding: List[float],
               top_k: int = 20, where: Optional[Dict] = None) -> List[Dict]:
        """向量检索，返回文档+分数+元数据"""
        pass

    @abstractmethod
    def delete_documents(self, collection: str, ids: List[str]) -> None:
        """按 ID 删除文档"""
        pass

    @abstractmethod
    def delete_collection(self, collection: str) -> None:
        """删除整个集合"""
        pass

    @abstractmethod
    def count(self, collection: str) -> int:
        """返回集合中文档数量"""
        pass
```

```python
# store/chroma_store.py

class ChromaVectorStore(VectorStoreRepository):
    """ChromaDB 实现"""
    # 具体实现见模块设计文档
```

---

## 10. 错误处理与降级

| 场景 | 处理方式 |
|---|---|
| Python 服务不可用 | Java 端捕获连接异常，返回 40700 AI_SERVICE_UNAVAILABLE |
| DeepSeek API 超时 | 重试 1 次（timeout=30s），仍失败返回 40701 |
| DeepSeek API 返回异常内容 | 不做内容过滤，直接返回给用户（社团内部场景风险低） |
| ChromaDB 集合不存在 | 自动创建空集合，返回"知识库暂无内容"提示 |
| 打卡数据为空 | Agent 返回提示"该活动暂无打卡数据，无法生成报告" |
| Embedding 模型加载失败 | 启动时校验，失败则服务拒绝启动（启动日志明确报错） |

---

## 11. 配置管理

### 11.1 Python 服务配置 (config.py)

```python
# LLM 配置
LLM_API_KEY = "sk-xxx"                    # DeepSeek API Key
LLM_BASE_URL = "https://api.deepseek.com" # DeepSeek API 地址
LLM_MODEL = "deepseek-chat"              # 模型名称
LLM_TEMPERATURE = 0.3                    # 低温度保证事实性
LLM_TIMEOUT = 30                         # 超时秒数

# Embedding 配置
EMBEDDING_MODEL = "BAAI/bge-large-zh-v1.5"  # 本地模型路径或 HuggingFace ID
EMBEDDING_DEVICE = "cpu"                     # cpu 或 cuda

# ChromaDB 配置
CHROMA_PERSIST_DIR = "./data/chroma"         # 持久化目录

# Redis 配置
REDIS_URL = "redis://localhost:6379/1"       # 使用 db=1，与 Java 的 db=0 隔离

# MySQL 配置（只读连接，读取业务数据）
MYSQL_HOST = "localhost"
MYSQL_PORT = 3306
MYSQL_USER = "root"
MYSQL_PASSWORD = "root"
MYSQL_DATABASE = "club_management"

# RAG 参数
CHUNK_SIZE = 512
CHUNK_OVERLAP = 64
RETRIEVAL_TOP_K = 5
RRF_K = 60
VECTOR_SEARCH_TOP_K = 20
BM25_SEARCH_TOP_K = 20
```

### 11.2 Java 端配置 (application.yml 新增)

```yaml
ai:
  service:
    base-url: http://localhost:8000
    timeout: 60000          # HTTP 超时（毫秒）
    retry-count: 1          # 重试次数
```
