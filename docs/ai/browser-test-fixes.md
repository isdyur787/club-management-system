# 浏览器测试问题修复指令

> 面向编码 LLM 的修复文档。本文档记录浏览器全链路测试中发现的所有问题及修复方案。

## 身份定义

你是全栈工程师（Java Spring Boot + Python FastAPI + Vue 3），负责修复浏览器测试中发现的问题。

## 核心规则

1. **按优先级修复**：先修复阻断性问题，再处理次要问题
2. **验证后再提交**：每个修复后需重新编译、启动、浏览器验证
3. **日志观察**：修复后观察后端/Python 日志确认无异常

---

## 问题清单

###  问题 1：知识库前端字段不匹配（已修复）

**文件路径**：`club-management-web/src/views/ai/KnowledgeView.vue`（第 89 行）

**问题描述**：
- 前端期望 API 返回 `res.data.records`
- 实际后端返回 `res.data.list`（`PageResult<T>` 使用 `list` 字段）
- 导致知识库列表显示为空

**修复状态**：✅ 已修复

**修复代码**：
```javascript
// 修改前
documents.value = res.data.records || []

// 修改后
documents.value = res.data.list || []
```

**验证方法**：
1. 刷新知识库页面 `/ai/knowledge`
2. 确认列表显示数据库中的文档（当前有 1 条 TestClub2 章程）
3. 检查控制台无错误

---

### 🟡 问题 2：数据库标题乱码

**现象**：
- 知识库列表中 "TestClub2 社团章程" 显示为 "TestClub2 ³"
- 截图可见中文部分全部显示为问号菱形

**原因分析**：
- 之前上传文档时文件编码或数据库连接编码不一致
- MySQL 表 `ai_knowledge_document.title` 字段存储了乱码数据

**影响范围**：
- 仅影响该条记录的显示，不影响功能
- 新上传的文档不会出现此问题（假设配置正确）

**修复方案（可选）**：

#### 方案 A：手动修正数据库（推荐）
```sql
UPDATE ai_knowledge_document 
SET title = 'TestClub2 社团章程' 
WHERE id = 1;
```

#### 方案 B：检查并修复编码配置
1. 确认 MySQL 连接 URL 包含 `?useUnicode=true&characterEncoding=utf8mb4`
2. 确认数据库和表字符集为 `utf8mb4`
3. 重新上传该文档测试

**验证方法**：
1. 刷新知识库页面
2. 确认标题正常显示中文

**优先级**：🟢 低（不影响功能，可延后处理）

---

### 🟡 问题 3：Python `/knowledge/documents` 端点报错

**文件路径**：`ai-service/routes/knowledge.py`（第 77-120 行）

**问题描述**：
调用 `GET /knowledge/documents?club_id=1&page_num=1&page_size=20` 返回：
```json
{
  "detail": "tuple indices must be integers or slices, not str"
}
```

**原因分析**：
这是典型的 Python 字典/元组访问错误。可能原因：
1. `cursor.fetchone()` 返回的是 tuple 而非 dict，但代码用 `["total"]` 访问
2. `cursor.fetchall()` 返回的 rows 是 tuple 列表，但代码用字典方式访问字段

**定位步骤**：
查看 `routes/knowledge.py` 第 77-120 行的 `list_documents` 函数：

```python
@router.get("/documents")
async def list_documents(club_id: int = 1, page_num: int = 1, page_size: int = 20):
    try:
        reader = DBReader()
        try:
            with reader.conn.cursor() as cursor:
                offset = (page_num - 1) * page_size
                cursor.execute("""
                    SELECT * FROM ai_knowledge_document
                    WHERE club_id = %s AND status = 1 AND deleted = 0
                    ORDER BY created_at DESC
                    LIMIT %s OFFSET %s
                """, (club_id, page_size, offset))
                docs = cursor.fetchall()

                cursor.execute("""
                    SELECT COUNT(*) as total FROM ai_knowledge_document
                    WHERE club_id = %s AND status = 1 AND deleted = 0
                """, (club_id,))
                total = cursor.fetchone()["total"]  # ← 这里可能报错

                return {
                    "records": [
                        {
                            "id": doc[0],  # ← 或用 doc["id"]，取决于 cursor 类型
                            "clubId": doc[1],
                            "docType": doc[2],
                            "title": doc[3],
                            "sourceType": doc[4],
                            "chunkCount": doc[7],
                            "createdAt": doc[11].isoformat() if doc[11] else None
                        }
                        for doc in docs
                    ],
                    "total": total
                }
```

**修复代码**：

需要确认 `DBReader` 使用的 cursor 类型。如果是 `pymysql.cursors.DictCursor`，则用字典访问；否则用索引访问。

#### 修复方案 1：确保使用 DictCursor

在 `rag/db_reader.py` 中确认：

```python
import pymysql
from pymysql.cursors import DictCursor

class DBReader:
    def __init__(self):
        self.conn = pymysql.connect(
            host=config.MYSQL_HOST,
            port=config.MYSQL_PORT,
            user=config.MYSQL_USER,
            password=config.MYSQL_PASSWORD,
            database=config.MYSQL_DB,
            charset='utf8mb4',
            cursorclass=DictCursor  # ← 确保使用 DictCursor
        )
```

#### 修复方案 2：如果无法使用 DictCursor，改用索引访问

```python
@router.get("/documents")
async def list_documents(club_id: int = 1, page_num: int = 1, page_size: int = 20):
    try:
        reader = DBReader()
        try:
            with reader.conn.cursor() as cursor:
                offset = (page_num - 1) * page_size
                cursor.execute("""
                    SELECT id, club_id, doc_type, title, source_type, source_id, 
                           file_path, chunk_count, chroma_ids, status, 
                           created_by, created_at, updated_by, updated_at, deleted
                    FROM ai_knowledge_document
                    WHERE club_id = %s AND status = 1 AND deleted = 0
                    ORDER BY created_at DESC
                    LIMIT %s OFFSET %s
                """, (club_id, page_size, offset))
                docs = cursor.fetchall()

                cursor.execute("""
                    SELECT COUNT(*) as total FROM ai_knowledge_document
                    WHERE club_id = %s AND status = 1 AND deleted = 0
                """, (club_id,))
                total_row = cursor.fetchone()
                total = total_row[0] if isinstance(total_row, tuple) else total_row["total"]

                return {
                    "records": [
                        {
                            "id": doc[0],
                            "clubId": doc[1],
                            "docType": doc[2],
                            "title": doc[3],
                            "sourceType": doc[4],
                            "chunkCount": doc[7],
                            "createdAt": doc[11].isoformat() if doc[11] else None
                        }
                        for doc in docs
                    ],
                    "total": total
                }
        finally:
            reader.close()
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
```

**验证方法**：
1. 重启 Python 服务：`cd ai-service && python main.py`
2. 测试端点：
   ```bash
   curl "http://localhost:8000/knowledge/documents?club_id=1&page_num=1&page_size=20"
   ```
3. 确认返回 JSON 结构：
   ```json
   {
     "records": [...],
     "total": 1
   }
   ```
4. 浏览器访问 `/ai/knowledge` 确认列表正常加载

---

### 🟢 问题 4：ChromaDB 向量入库未验证

**问题描述**：
- 报告确认入库（`POST /api/v1/ai/report/4/confirm`）返回 200
- MySQL 中 `ai_activity_report.status` 变为 `CONFIRMED`
- 但无法通过 Python 端点验证 ChromaDB 是否成功入库

**原因**：
- Python `/knowledge/documents` 端点报错（问题 3），无法查询入库结果
- Python 服务日志未完全捕获，无法确认入库过程

**修复依赖**：
需先修复问题 3，然后才能验证 ChromaDB 入库。

**验证方法（修复问题 3 后）**：
1. 确认报告入库：
   ```bash
   curl "http://localhost:8000/knowledge/documents?club_id=1&page_num=1&page_size=20"
   ```
   应返回包含 `doc_type="activity_report"` 的记录

2. 测试 RAG 检索是否能检索到该报告：
   ```bash
   curl -X POST http://localhost:8000/chat \
     -H "Content-Type: application/json" \
     -d '{
       "club_id": 1,
       "message": "Web Workshop 活动的成果是什么？",
       "session_id": null
     }'
   ```
   响应中 `sources` 应包含该报告作为参考来源

---

### 🟢 问题 5：知识库文档上传未测试

**问题描述**：
- 前端上传对话框存在
- 但未实际测试完整上传流程（选择文件 → 上传 → 入库 → 显示）

**测试步骤**：
1. 准备测试文件 `test_charter.txt`（内容见下方）
2. 浏览器访问 `/ai/knowledge`
3. 点击"上传文档"
4. 填写标题、选择类型、选择文件
5. 点击"上传"
6. 验证：
   - 前端提示"上传成功"
   - 列表刷新显示新文档
   - MySQL 中 `ai_knowledge_document` 新增记录
   - Python 日志显示 ChromaDB 入库成功

**测试文件内容**（`test_charter.txt`）：
```
# TestClub2 社团章程

## 第一章 总则

第一条 本社团名称为 TestClub2，是由在校学生自愿组成的非营利性学生组织。

第二条 本社团宗旨：促进技术交流，提升实践能力，培养创新精神。

## 第二章 组织架构

第三条 本社团设会长 1 名，副会长 2 名，各部门部长若干。

第四条 会长职责：统筹社团工作，代表社团对外交流。

## 第三章 活动管理

第五条 社团每周举办一次技术分享会。

第六条 每月组织一次实战 Workshop。

第七条 每学期举办一次编程竞赛。

## 第四章 成员权利与义务

第八条 成员有权参加社团组织的各项活动。

第九条 成员应遵守社团章程，积极参与社团建设。
```

**预期结果**：
- Java 接收文件并保存到本地/MinIO
- Java 调用 Python `/knowledge/documents/ingest`
- Python 解析文件、切块、Embedding、存入 ChromaDB
- Python 写入 MySQL `ai_knowledge_document`
- 前端列表刷新显示新文档

---

## 修复顺序建议

### 第一阶段：立即修复（阻断后续测试）
1. ✅ ~~问题 1：知识库前端字段不匹配~~（已完成）
2. 问题 3：Python `/knowledge/documents` 端点报错

### 第二阶段：验证核心链路
3. 问题 4：ChromaDB 向量入库验证（依赖问题 3 修复）

### 第三阶段：完善测试覆盖
4. 问题 5：知识库文档上传测试
5. 问题 2：数据库标题乱码（可选，不影响功能）

---

## 验证清单

完成所有修复后，执行以下验证：

- [ ] 知识库列表页正常显示文档（含新上传的文档）
- [ ] Python `/knowledge/documents` 端点返回正确 JSON
- [ ] 报告确认后能在知识库中查询到（`doc_type="activity_report"`）
- [ ] AI 对话能检索到已确认的报告作为参考来源
- [ ] 上传新文档后，列表自动刷新显示
- [ ] 控制台无 JavaScript 错误
- [ ] 后端/Python 日志无异常堆栈

---

## 附录：相关代码位置

### 前端
- 知识库页面：`club-management-web/src/views/ai/KnowledgeView.vue`
- API 调用：`club-management-web/src/api/ai.js`（`getKnowledgeDocuments`）

### 后端（Java）
- Controller：`src/main/java/com/club/management/ai/controller/AiController.java`
- Service：`src/main/java/com/club/management/ai/service/impl/AiServiceImpl.java`
- 实体：`src/main/java/com/club/management/ai/entity/KnowledgeDocument.java`

### Python 服务
- 路由：`ai-service/routes/knowledge.py`
- 数据库读取器：`ai-service/rag/db_reader.py`
- 向量入库：`ai-service/rag/ingestion.py`
- 主应用：`ai-service/main.py`

### 数据库
- 表：`ai_knowledge_document`
- 关键字段：`id`, `club_id`, `doc_type`, `title`, `source_type`, `chunk_count`, `chroma_ids`, `status`

---

## 注意事项

1. **Python 服务重启**：每次修改 Python 代码后需重启服务
   ```bash
   # 停止旧进程
   Get-Process | Where-Object {$_.ProcessName -like "*python*"} | Stop-Process
   
   # 重新启动
   cd c:\Users\lrs\Desktop\gie\ai-service
   python main.py
   ```

2. **端口占用检查**：
   ```powershell
   Get-NetTCPConnection -LocalPort 8000 -ErrorAction SilentlyContinue
   ```

3. **日志观察**：
   - Python 启动日志应包含：`Uvicorn running on http://0.0.0.0:8000`
   - 确认无 `DeprecationWarning` 以外的错误

4. **MySQL 连接**：
   - 用户名：`root`
   - 密码：`root`
   - 数据库：`club_management`
   - 字符集：`utf8mb4`
