# AI 层编码指令（面向编码 LLM）

## 你的身份

你是一个 Python 后端开发工程师 + Java 后端开发工程师，负责实现「社团活动管理系统」的 AI 层代码。包含：
1. Python FastAPI 服务（RAG 引擎 + Agent + 知识库管理）
2. Java 后端新增模块（AI 代理层 + 打卡/签退/积分业务）
3. 前端新增页面（对话窗口、报告审核、知识库管理）

---

## 核心规则

1. **你不需要做任何架构决策。** 所有设计已确定，你严格按照文档实现。
2. **先读文档，再写代码。** 在写任何代码之前，必须先阅读相关文档。
3. **文档是唯一的标准。** 类名、方法签名、字段定义必须完全遵循文档。
4. **逐阶段实现。** 每完成一个阶段确认能运行后再进入下一阶段。

---

## 文档阅读顺序

### Python 服务开发前必读：
1. `docs/ai/01-ai-architecture.md` — AI 整体架构、通信规范、RAG 引擎设计、Agent 设计
2. `docs/ai/02-ai-database.md` — 新增表结构、积分规则、文件存储设计
3. `docs/ai/03-ai-api-specification.md` — 全部接口定义（含 Python 内部接口）
4. `docs/ai/04-ai-module-design.md` — Python 模块详细设计、类/方法实现代码

### Java 后端扩展前必读：
5. `docs/01-architecture.md` — 原有架构（分层、目录结构、设计模式）
6. `docs/02-development-standards.md` — 原有编码规范（统一响应、异常、错误码）
7. `docs/ai/04-ai-module-design.md` 第 2 节 — Java 新增模块设计

### 前端开发前必读：
8. `docs/ai/04-ai-module-design.md` 第 3 节 — 前端新增页面设计
9. `docs/ai/03-ai-api-specification.md` — 接口定义

---

## 前置条件

在开始编码之前，确认以下环境就绪：
- Python 3.10+ 已安装
- Node.js 18+ 已安装
- MySQL 8 已运行（root/root，数据库 club_management 已存在）
- Redis 已运行（localhost:6379）
- Java 后端项目已能正常编译运行（端口 8081）
- 前端项目已能正常启动（端口 5174）

---

## 实现顺序

### 阶段 A：数据库扩展（Java 端）

1. 执行 `docs/ai/02-ai-database.md` 中的 `V3__ai_tables.sql` 创建新表
2. 执行 `V4__ai_permissions.sql` 添加新权限
3. 验证：`SHOW TABLES` 确认 8 张新表已创建（含 act_checkin_compilation）

### 阶段 B：Java 后端 — 打卡/签退/积分/汇编业务

1. 在 `com.club.management` 下新增包：
   - `checkin/` — 打卡任务 + 打卡记录 + 汇编文档（controller/service/mapper/entity/dto/vo）
   - `signout/` — 签退（controller/service/mapper/entity）
   - `points/` — 积分流水（controller/service/mapper/entity/vo）
2. 实现 Entity 类（继承 BaseEntity），注意打卡记录新增 `problem` 和 `image_descriptions` 字段
3. 实现 Mapper 接口
4. 实现 DTO/VO（打卡提交请求体包含 problem 字段）
5. 实现 Service + Controller（含汇编文档生成/查询接口）
6. 汇编文档生成逻辑：打卡截止后，读取全部打卡记录，按模板拼接生成汇编文档（纯 Java 实现，不调 LLM）
7. 按 `docs/ai/03-ai-api-specification.md` 第 2 节测试接口
8. 确认：创建打卡任务 → 提交打卡（含 problem 字段）→ 签退 → 积分结算 → 生成汇编文档 → 查看汇编，全链路正常

### 阶段 C：Java 后端 — 文件上传

1. 新增 `file/` 包（controller/service）
2. 实现本地文件存储（策略模式接口 + LocalFileStorage 实现）
3. 实现文件上传/下载接口
4. 配置静态资源映射或文件下载接口
5. 确认：上传照片 → 返回 URL → 通过 URL 可访问

### 阶段 D：Java 后端 — AI 代理层

1. 新增 `ai/` 包（controller/service/config/dto/vo）
2. 实现 `AiConfig.java`（Python 服务地址配置）
3. 实现 `AiService` 接口和 `AiServiceImpl`（HTTP 转发到 Python）
4. 实现 `AiController`（按 `docs/ai/03-ai-api-specification.md` 第 4-7 节）
5. 新增错误码 40700-40706 到 `ErrorCode.java`
6. 此阶段 Python 服务尚未实现，AI 接口会返回 40700（AI 服务不可用），这是预期的

### 阶段 E：Python 服务 — 项目骨架

1. 在项目根目录创建 `ai-service/` 目录
2. 创建 `requirements.txt`：
   ```
   fastapi>=0.110.0
   uvicorn>=0.29.0
   chromadb>=0.5.0
   sentence-transformers>=2.6.0
   openai>=1.0.0
   langgraph>=0.2.0
   langchain-text-splitters>=0.2.0
   rank-bm25>=0.2.2
   python-docx>=1.1.0
   pymysql>=1.1.0
   redis>=5.0.0
   pydantic>=2.0.0
   python-multipart>=0.0.9
   ```
3. 执行 `pip install -r requirements.txt`
4. 创建 `config.py`，按 `docs/ai/01-ai-architecture.md` 第 11 节配置
5. 创建 `main.py`（FastAPI 入口）
6. 创建目录结构：`rag/`, `agents/`, `tools/`, `store/`, `models/`, `routes/`
7. 确认：`uvicorn main:app --port 8000` 能启动

### 阶段 F：Python 服务 — 向量存储与 RAG 引擎

1. 实现 `store/vector_store.py`（抽象接口）
2. 实现 `store/chroma_store.py`（ChromaDB 实现）
3. 实现 `rag/ingestion.py`（按 doc_type 选择切块策略：固定大小/章节/Q&A/维度切块）
4. 实现 `rag/retrieval.py`（维度识别 + 向量检索 + BM25 + RRF 融合）
5. 实现 `rag/generator.py`（Prompt 组装 + DeepSeek 调用）
6. 实现 `rag/knowledge_base.py`（知识库管理）
7. 实现 `tools/document_parser.py`（txt/md/docx 解析）
8. 实现 `tools/compilation_generator.py`（汇编文档生成，纯模板拼接）
9. 实现 `tools/image_processor.py`（多模态 LLM 图片描述生成）
10. 测试：手动入库一段文本 → 检索 → 确认返回正确结果
11. 测试：汇编文档入库后，按维度检索能精准命中

### 阶段 G：Python 服务 — 对话路由（招新/知识助手）

1. 实现 `routes/chat.py`（对话路由）
2. 实现 Redis 对话历史管理
3. 实现 `models/schemas.py`（Pydantic 模型）
4. 测试：POST /chat → 得到基于知识库的回答

### 阶段 H：Python 服务 — Agent

1. 实现 `tools/db_reader.py`（MySQL 数据读取，含 `get_compilation_by_activity` 方法）
2. 实现 `tools/document_generator.py`（Word 文档生成）
3. 实现 `agents/report_agent.py`（成果撰写 Agent，LangGraph）
   - 注意：Agent 读取汇编文档（而非原始打卡记录）作为输入
   - 如果汇编文档不存在，应抛出明确错误
4. 实现 `agents/experience_agent.py`（经验参考 Agent，LangGraph）
5. 实现 `routes/report.py`（报告路由）
6. 实现 `routes/experience.py`（经验参考路由）
7. 测试：
   - 先在 MySQL 中插入测试打卡数据 + 生成汇编文档
   - 调用 /report/generate → 确认基于汇编文档生成草稿
   - 调用 /experience → 确认返回建议

### 阶段 I：Python 服务 — 知识库管理

1. 实现 `routes/knowledge.py`（知识库管理路由）
2. 实现文件上传入库（multipart/form-data）
3. 测试：上传 txt 文件 → 查询列表 → 检索到内容 → 删除

### 阶段 J：前后端联调

1. 前端新增 `src/api/ai.js`（AI 接口封装）
2. 前端新增 `src/views/checkin/CheckinForm.vue`（打卡表单，成员端）
3. 前端新增 `src/views/checkin/CheckinManage.vue`（打卡任务管理，团长端）
4. 前端新增 `src/views/checkin/CompilationView.vue`（汇编文档查看）
5. 前端新增 `src/views/points/PointsPage.vue`（积分流水页）
6. 前端新增 `src/views/ai/chat.vue`（对话窗口）
7. 前端新增 `src/views/ai/report.vue`（报告审核）
8. 前端新增 `src/views/ai/knowledge.vue`（知识库管理）
9. 前端路由配置新增页面路由
10. 联调测试：
   - 活动详情页 → 成员提交打卡（含 problem 字段）→ 团长查看打卡列表
   - 打卡截止 → 团长生成汇编文档 → 查看汇编内容 → 下载 Word
   - 社团详情页 → 打开对话窗口 → 提问 → 得到回答
   - 活动详情页 → 团长触发报告生成 → 查看草稿 → 确认入库
   - 知识库管理页 → 上传文档 → 列表显示 → 对话中能检索到
   - 积分页 → 查看流水 → 查看排行榜

### 阶段 K：全链路测试

1. 完整流程测试：
   - 创建活动 → 创建打卡任务 → 成员签到 → 成员打卡（含问题反馈）→ 签退
   - 打卡截止 → 生成汇编文档 → 积分结算 → 触发报告生成 → 团长确认 → 入库
   - 创建新活动 → 获取经验参考 → 基于历史建议策划
   - 上传知识库文档 → 对话中检索到
2. 异常场景测试：
   - Python 服务关闭 → Java 调用 AI 接口 → 返回 40700
   - 知识库为空 → 对话 → 返回合理提示
   - 无历史数据 → 经验参考 → 返回通用模板
   - 未生成汇编 → 触发报告生成 → 返回明确错误

---

## 编码规范提醒

### Python 端：
- 所有接口使用 Pydantic 模型做入参校验
- 异常使用 HTTPException 抛出，状态码与 Java 端错误码对应
- 日志使用 logging 模块，格式与 Java 端保持一致（时间 + 级别 + 模块 + 消息）
- ChromaDB collection 命名统一为 `club_{club_id}`
- Embedding 模型全局单例，不要每次请求都加载

### Java 端：
- 遵循原有分层规范（Controller → Service → Mapper）
- 新增 Entity 继承 BaseEntity
- AI 代理层使用 RestTemplate 调用 Python 服务
- 异常统一包装为 BusinessException
- 新增错误码按文档定义（40700-40706）

### 前端：
- 遵循原有 Vue 3 + Element Plus 风格
- API 调用统一走 `src/api/ai.js`
- 对话窗口使用 SSE 或轮询（当前阶段先用普通请求，后续可优化为流式）

---

## 遇到问题时

- 文档中有明确定义 → 按文档实现
- 文档中未覆盖的细节 → 选择最简单的实现方式
- Python 依赖安装失败 → 检查 Python 版本（需 3.10+），使用虚拟环境
- ChromaDB 报错 → 检查持久化目录是否存在且有写权限
- DeepSeek API 调用失败 → 检查 API Key 和网络
- LangGraph interrupt 不生效 → 检查 checkpointer 是否正确配置

---

## 完成标准

- Python 服务所有接口可按 `docs/ai/03-ai-api-specification.md` 正常响应
- Java 端 AI 代理层正确转发请求到 Python 服务
- 打卡/签退/积分业务接口全部可用
- 成果撰写 Agent 能基于打卡数据生成报告草稿
- 经验参考 Agent 能基于历史数据返回建议
- 对话助手能基于知识库回答问题并标注来源
- 知识库管理（上传/查询/删除）功能正常
- 前端三个新页面可正常使用
- 全链路流程可走通

---

## 关键配置提醒

### Python config.py 必填项：
```python
LLM_API_KEY = "你的 DeepSeek API Key"
LLM_BASE_URL = "https://api.deepseek.com"
LLM_MODEL = "deepseek-chat"
EMBEDDING_MODEL = "BAAI/bge-large-zh-v1.5"
CHROMA_PERSIST_DIR = "./data/chroma"
REDIS_URL = "redis://localhost:6379/1"
MYSQL_HOST = "localhost"
MYSQL_USER = "root"
MYSQL_PASSWORD = "root"
MYSQL_DATABASE = "club_management"
# 多模态 LLM（图片描述生成）
MULTIMODAL_API_KEY = "你的 DeepSeek API Key"  # 可与 LLM_API_KEY 相同
MULTIMODAL_BASE_URL = "https://api.deepseek.com"
MULTIMODAL_MODEL = "deepseek-vl2-small"  # 多模态版本
```

### Java application.yml 新增：
```yaml
ai:
  service:
    base-url: http://localhost:8000
    timeout: 60000
```

### 启动命令：
- Python 服务：`cd ai-service && uvicorn main:app --port 8000 --reload`
- Java 后端：`mvn spring-boot:run`（已有）
- 前端：`cd club-management-web && npx vite`（已有）
