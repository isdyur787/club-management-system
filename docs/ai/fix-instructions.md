# 集成问题修复指令

> 面向编码 LLM 的修复指令。本文档列出了浏览器测试和代码审查中发现的全部集成问题，逐条修复后确保系统可正常运行。

## 身份定义

你是全栈工程师（Java Spring Boot + Python FastAPI + Vue 3），负责修复以下集成问题。

## 核心规则

1. **逐条修复**：按编号顺序修复，每修复一个问题进行验证
2. **先读后改**：修改文件前先完整阅读目标文件
3. **保持一致**：遵循项目现有代码风格和命名规范
4. **最小改动**：只修复列出的问题，不做额外重构

---

## 问题总览

| # | 严重度 | 模块 | 问题描述 |
|---|--------|------|----------|
| 1 | 🔴 阻断 | 前端路由 | 7 个新页面未注册路由 |
| 2 | 🔴 阻断 | 前端导航 | 侧边栏缺少新功能入口 |
| 3 | 🔴 阻断 | 活动详情 | 详情页缺少打卡/汇编/报告入口 |
| 4 | 🟡 重要 | 前端组件 | 3 个页面组件错误使用 props 而非 route.params |
| 5 | 🟡 重要 | Python | 缺少 `/knowledge/ingest-report` 端点 |
| 6 | 🟡 重要 | Java→Python | 知识库上传双重写入 MySQL |
| 7 | 🟡 重要 | Java→Python | 知识库上传文件格式不匹配 |
| 8 | 🟡 重要 | Java→Python | 报告确认/打回路由参数语义不一致 |
| 9 | 🟢 次要 | Python | 缺少 `document_generator.py` |
| 10 | 🟢 次要 | 前端 | ChatView 社团选择硬编码 |

---

## 问题 1：前端路由缺失（🔴 阻断）

**文件**：`club-management-web/src/router/index.js`

**问题**：`routes` 数组的 `children` 中没有注册以下 7 个新页面的路由，导致所有新功能页面无法访问。

**修复**：在 `children` 数组中（`statistics` 路由之后）添加以下路由：

```js
// === 打卡相关 ===
{
  path: 'checkin/form/:activityId',
  name: 'CheckinForm',
  component: () => import('@/views/checkin/CheckinForm.vue'),
  meta: { title: '活动打卡' }
},
{
  path: 'checkin/manage/:activityId',
  name: 'CheckinManage',
  component: () => import('@/views/checkin/CheckinManage.vue'),
  meta: { title: '打卡管理' }
},
// === 积分 ===
{
  path: 'points',
  name: 'Points',
  component: () => import('@/views/points/PointsPage.vue'),
  meta: { title: '我的积分' }
},
// === AI 功能 ===
{
  path: 'ai/chat',
  name: 'AiChat',
  component: () => import('@/views/ai/ChatView.vue'),
  meta: { title: 'AI 知识助手' }
},
{
  path: 'ai/report/:activityId',
  name: 'AiReport',
  component: () => import('@/views/ai/ReportView.vue'),
  meta: { title: '成果报告' }
},
{
  path: 'ai/knowledge',
  name: 'AiKnowledge',
  component: () => import('@/views/ai/KnowledgeView.vue'),
  meta: { title: '知识库管理' }
},
{
  path: 'ai/compilation/:activityId',
  name: 'CompilationView',
  component: () => import('@/views/checkin/CompilationView.vue'),
  meta: { title: '汇编文档' }
}
```

**验证**：启动前端后，直接访问 `http://localhost:5173/ai/chat` 应能正常显示页面（不跳 404）。

---

## 问题 2：侧边栏缺少导航入口（🔴 阻断）

**文件**：`club-management-web/src/views/layout/index.vue`

**问题**：侧边栏菜单只有原有 8 项，缺少"积分""AI 助手""知识库"入口。

**修复**：在 `el-menu` 中（`statistics` 菜单项之后）添加：

```vue
<el-menu-item index="/points">
  <el-icon><Trophy /></el-icon>
  <span>积分中心</span>
</el-menu-item>
<el-menu-item index="/ai/chat">
  <el-icon><ChatDotRound /></el-icon>
  <span>AI 助手</span>
</el-menu-item>
<el-menu-item index="/ai/knowledge">
  <el-icon><Collection /></el-icon>
  <span>知识库</span>
</el-menu-item>
```

同时在 script 的 import 中添加对应图标：

```js
import {
  HomeFilled, OfficeBuilding, Calendar, Document, Box,
  Bell, DataAnalysis, Fold, Expand,
  Trophy, ChatDotRound, Collection
} from '@element-plus/icons-vue'
```

**验证**：侧边栏显示 3 个新菜单项，点击可跳转。

---

## 问题 3：活动详情页缺少功能入口（🔴 阻断）

**文件**：`club-management-web/src/views/activity/detail.vue`

**问题**：活动详情页只显示基本信息和签到码，没有"打卡管理""汇编文档""成果报告"的入口按钮。

**修复**：在签到管理 `el-card` 之后，添加以下功能区：

```vue
<!-- 打卡与汇编管理（团长可见） -->
<el-card style="margin-top: 20px" v-if="detail.status === 'COMPLETED' || detail.status === 'APPROVED' || detail.status === 'IN_PROGRESS'">
  <template #header><span style="font-weight: 600">打卡与成果管理</span></template>
  <el-space wrap>
    <el-button type="primary" @click="$router.push(`/checkin/manage/${detail.id}`)">
      打卡任务管理
    </el-button>
    <el-button @click="$router.push(`/checkin/form/${detail.id}`)">
      提交打卡
    </el-button>
    <el-button @click="$router.push(`/ai/report/${detail.id}`)">
      成果报告
    </el-button>
  </el-space>
</el-card>
```

**验证**：活动详情页底部出现 3 个按钮，点击跳转到对应页面。

---

## 问题 4：组件使用 props 而非 route.params（🟡 重要）

### 4a. CheckinForm.vue

**文件**：`club-management-web/src/views/checkin/CheckinForm.vue`

**问题**：使用 `defineProps({ activityId })` 接收 activityId，但作为独立页面应使用 `useRoute().params.activityId`。

**修复**：
1. 删除 `defineProps` 部分
2. 添加 `const route = useRoute()`
3. 将所有 `props.activityId` 替换为 `Number(route.params.activityId)`
4. `activityTitle` 改为从 API 获取或从路由 query 获取

```js
import { useRoute } from 'vue-router'

const route = useRoute()
const activityId = Number(route.params.activityId)
const activityTitle = ref('')

// 在 onMounted 中获取活动标题（可选，也可以直接显示 activityId）
```

### 4b. CheckinManage.vue

**文件**：`club-management-web/src/views/checkin/CheckinManage.vue`

**问题**：同上，使用 `defineProps({ activityId })`。

**修复**：同上，改为 `useRoute().params.activityId`。

### 4c. ReportView.vue

**文件**：`club-management-web/src/views/ai/ReportView.vue`

**问题**：同上，使用 `defineProps({ activityId })`。

**修复**：同上，改为 `useRoute().params.activityId`。

### 4d. PointsPage.vue

**文件**：`club-management-web/src/views/points/PointsPage.vue`

**问题**：使用 `defineProps({ clubId: { default: 1 } })`，但作为独立页面没有传入 clubId。

**修复**：改为从用户信息或 store 中获取当前用户所属社团 ID，或提供社团选择器。简化处理：默认使用 clubId=1，后续可从 store 获取。

```js
// 暂时硬编码，后续可从 store 或路由 query 获取
const clubId = ref(1)
```

删除 `defineProps`。

**验证**：各页面通过路由访问时不报 props 缺失错误。

---

## 问题 5：Python 缺少 `/knowledge/ingest-report` 端点（🟡 重要）

**文件**：`ai-service/routes/knowledge.py`

**问题**：Java `AiServiceImpl.confirmReport()` 调用 `POST /knowledge/ingest-report` 将已确认报告入库到 ChromaDB，但 Python 端没有此端点。

**修复**：在 `knowledge.py` 中添加：

```python
@router.post("/ingest-report")
async def ingest_report(request: dict):
    """将已确认的成果报告入库到 ChromaDB"""
    try:
        club_id = request["club_id"]
        activity_id = request["activity_id"]
        title = request["title"]
        content = request["content"]

        doc_id, chunk_count = add_document(
            club_id=club_id,
            doc_type="activity_report",
            title=title,
            content=content,
            source="system",
            metadata={"activity_id": activity_id}
        )

        return {
            "doc_id": doc_id,
            "chunk_count": chunk_count,
            "message": "报告已入库"
        }
    except Exception as e:
        logger.error(f"Ingest report error: {e}")
        raise HTTPException(status_code=500, detail=str(e))
```

同时需要在 `rag/knowledge_base.py` 的 `add_document` 函数中支持 `metadata` 参数（如果尚未支持）。

**验证**：`curl -X POST http://localhost:8000/knowledge/ingest-report -H "Content-Type: application/json" -d '{"club_id":1,"activity_id":1,"title":"test","content":"test content"}'`

---

## 问题 6：知识库上传双重写入 MySQL（🟡 重要）

**文件**：`src/main/java/com/club/management/ai/service/impl/AiServiceImpl.java`

**问题**：`uploadKnowledgeDocument()` 方法中，Java 端先调用 Python `POST /knowledge/documents`（Python 端已经写入 `ai_knowledge_document` 表），然后 Java 又自己 insert 了一条记录到 `ai_knowledge_document` 表。导致同一文档产生两条记录。

**修复方案**（二选一，推荐方案 A）：

**方案 A：Java 不再写入 MySQL（推荐）**

Python 端已经负责写入 MySQL，Java 端只需使用 Python 返回的 `id` 构造 VO 返回给前端。

修改 `AiServiceImpl.uploadKnowledgeDocument()`：
- 删除 `KnowledgeDocument doc = new KnowledgeDocument(); ... knowledgeDocMapper.insert(doc);` 部分
- 直接从 Python 响应中构建 `KnowledgeDocVO` 返回

```java
// 删除这些代码：
// KnowledgeDocument doc = new KnowledgeDocument();
// doc.setClubId(clubId);
// ...
// knowledgeDocMapper.insert(doc);
// return toKnowledgeVO(doc);

// 替换为：
return new KnowledgeDocVO(
    (Long) data.get("id"),
    clubId,
    docType,
    title,
    "upload",
    (Integer) data.get("chunk_count"),
    (String) data.get("created_at")
);
```

**方案 B：Python 不再写入 MySQL**

删除 `routes/knowledge.py` 中 `upload_document()` 的 MySQL 写入部分，让 Java 端作为唯一数据源。

**验证**：上传一个文档后，`SELECT * FROM ai_knowledge_document` 只有一条记录。

---

## 问题 7：知识库上传文件格式不匹配（🟡 重要）

**文件**：
- Java: `AiServiceImpl.uploadKnowledgeDocument()`
- Python: `routes/knowledge.py` → `upload_document()`

**问题**：
- Java 端调用 Python 时发送的是 JSON body（`body.add("file_path", filePath)`）
- Python 端期望的是 `multipart/form-data`（`file: UploadFile = File(...)`）

两端格式完全不匹配，调用必然失败。

**修复方案**：统一为"Java 上传文件后传路径给 Python"模式。

修改 Python `routes/knowledge.py` 的 `upload_document` 端点，增加一个接受 JSON 的变体：

```python
@router.post("/documents/ingest")
async def ingest_document(request: dict):
    """Java 端已上传文件，传入文件路径进行解析和入库"""
    try:
        club_id = request["club_id"]
        doc_type = request["doc_type"]
        title = request["title"]
        file_path = request["file_path"]

        if not os.path.exists(file_path):
            raise HTTPException(status_code=400, detail="文件不存在")

        text_content = parse_document(file_path)
        doc_id, chunk_count = add_document(
            club_id=club_id,
            doc_type=doc_type,
            title=title,
            content=text_content,
            source="upload"
        )

        # 保存元数据到 MySQL
        reader = DBReader()
        try:
            with reader.conn.cursor() as cursor:
                cursor.execute("""
                    INSERT INTO ai_knowledge_document
                    (club_id, doc_type, title, source_type, file_path, chunk_count, status)
                    VALUES (%s, %s, %s, 'upload', %s, %s, 1)
                """, (club_id, doc_type, title, file_path, chunk_count))
                reader.conn.commit()
                doc_db_id = cursor.lastrowid
        finally:
            reader.close()

        return {
            "id": doc_db_id,
            "chunk_count": chunk_count,
            "created_at": __import__('datetime').datetime.now().isoformat()
        }
    except Exception as e:
        logger.error(f"Ingest document error: {e}")
        raise HTTPException(status_code=500, detail=str(e))
```

然后修改 Java `AiServiceImpl.uploadKnowledgeDocument()` 中的调用 URL：
```java
// 从：
String url = aiConfig.getBaseUrl() + "/knowledge/documents";
// 改为：
String url = aiConfig.getBaseUrl() + "/knowledge/documents/ingest";
```

并将 body 改为 JSON（不再用 MultiValueMap）：
```java
Map<String, Object> body = new HashMap<>();
body.put("club_id", clubId);
body.put("doc_type", docType);
body.put("title", title);
body.put("file_path", filePath);

ResponseEntity<Map> response = restTemplate.postForEntity(url, body, Map.class);
```

**验证**：通过前端上传一个 .txt 文档，确认 ChromaDB 和 MySQL 都正确写入。

---

## 问题 8：报告确认/打回路由参数语义不一致（🟡 重要）

**文件**：
- Java: `AiController.java` 第 50-65 行
- Python: `routes/report.py` 第 86-120 行

**问题**：
- Java 端 `confirmReport(@PathVariable Long id)` 中 `id` 是 **报告 ID**（`ai_activity_report.id`）
- Python 端 `confirm_report(activity_id: int)` 中参数是 **活动 ID**（`activity_id`）
- Java 端调用 Python 时发送的是 `POST /report/{activity_id}/confirm`，但 Java Controller 接收的是 report id

**修复**：统一使用 `activity_id`。

修改 Java `AiController.java`：
```java
// confirmReport 和 rejectReport 的参数改为 activityId
@PostMapping("/report/{activityId}/confirm")
public R<Void> confirmReport(@PathVariable Long activityId) {
    aiService.confirmReportByActivityId(activityId);
    return R.ok();
}

@PostMapping("/report/{activityId}/reject")
public R<Void> rejectReport(@PathVariable Long activityId,
                             @RequestBody ReportReviewDTO dto) {
    aiService.rejectReportByActivityId(activityId, dto.getReason());
    return R.ok();
}
```

同时修改 `AiServiceImpl` 中对应方法的查询逻辑，改为按 `activityId` 查询和更新。

前端 `ai.js` 中 `confirmReport` 和 `rejectReport` 调用也需要传 `activityId` 而非 `report.id`：
```js
// ReportView.vue 中
await confirmReport(props.activityId)  // 而非 report.value.id
await rejectReport(props.activityId, { reason: rejectReason.value })
```

**验证**：在报告审核页点击"确认入库"，Java 成功调用 Python 更新报告状态。

---

## 问题 9：Python 缺少 `document_generator.py`（🟢 次要）

**文件**：`ai-service/tools/document_generator.py`

**问题**：设计文档中定义了 `tools/document_generator.py`（Word 文档生成），但实际未创建此文件。成果报告和汇编文档的 Word 导出功能不可用。

**修复**：创建文件：

```python
# ai-service/tools/document_generator.py
from docx import Document
from docx.shared import Pt
import os
from config import REPORT_OUTPUT_DIR

def generate_word_document(title: str, content: str, filename_prefix: str = "report") -> str:
    """
    生成 Word 文档
    返回文件路径
    """
    os.makedirs(REPORT_OUTPUT_DIR, exist_ok=True)
    
    doc = Document()
    doc.add_heading(title, level=1)
    
    # 按段落分割内容（Markdown 简易转换）
    paragraphs = content.split('\n\n')
    for para in paragraphs:
        para = para.strip()
        if para.startswith('# '):
            doc.add_heading(para[2:], level=2)
        elif para.startswith('## '):
            doc.add_heading(para[3:], level=3)
        elif para.startswith('- '):
            for line in para.split('\n'):
                if line.startswith('- '):
                    doc.add_paragraph(line[2:], style='List Bullet')
        else:
            doc.add_paragraph(para)
    
    import time
    filename = f"{filename_prefix}_{int(time.time())}.docx"
    file_path = os.path.join(REPORT_OUTPUT_DIR, filename)
    doc.save(file_path)
    return file_path
```

同时在 `requirements.txt` 中添加 `python-docx`。

**验证**：`from tools.document_generator import generate_word_document` 不报错。

---

## 问题 10：ChatView 社团选择硬编码（🟢 次要）

**文件**：`club-management-web/src/views/ai/ChatView.vue`

**问题**：社团选择下拉框硬编码了 `el-option :key="1" :value="1" label="默认社团"`，无法选择其他社团。

**修复**：从 API 获取社团列表动态渲染：

```vue
<el-select v-model="currentClubId" placeholder="选择社团" size="small" style="width: 150px">
  <el-option v-for="club in clubList" :key="club.id" :value="club.id" :label="club.name" />
</el-select>
```

```js
import { getClubList } from '@/api/club'  // 需要确认此 API 存在

const clubList = ref([])

onMounted(async () => {
  try {
    const res = await getClubList({ pageNum: 1, pageSize: 100 })
    clubList.value = res.data.list || []
  } catch (e) { /* 使用默认值 */ }
})
```

如果 `getClubList` API 不存在或暂不需要，可保留硬编码但添加 TODO 注释。

---

## 额外注意：CompilationView.vue 缺失

**问题**：路由中需要 `CompilationView.vue` 页面，但当前只有 `CheckinManage.vue` 中内嵌了汇编查看对话框。

**修复**：创建 `club-management-web/src/views/checkin/CompilationView.vue`：

```vue
<template>
  <div>
    <div class="page-header">
      <h2>汇编文档</h2>
      <el-button @click="$router.back()">返回</el-button>
    </div>
    <el-card v-if="compilation" v-loading="loading">
      <template #header>
        <div class="header">
          <span>{{ compilation.activityId }} 号活动汇编</span>
          <el-tag :type="compilation.isIngested ? 'success' : 'info'">
            {{ compilation.isIngested ? '已入库' : '未入库' }}
          </el-tag>
        </div>
      </template>
      <el-descriptions :column="2" border style="margin-bottom: 20px">
        <el-descriptions-item label="参与人数">{{ compilation.memberCount }}</el-descriptions-item>
        <el-descriptions-item label="平均评分">{{ compilation.avgRating }}/5</el-descriptions-item>
        <el-descriptions-item label="问题汇总">{{ compilation.problemSummary || '无' }}</el-descriptions-item>
        <el-descriptions-item label="建议汇总">{{ compilation.suggestionSummary || '无' }}</el-descriptions-item>
      </el-descriptions>
      <div class="content" style="white-space: pre-wrap; background: #f5f7fa; padding: 20px; border-radius: 4px;">
        {{ compilation.content }}
      </div>
    </el-card>
    <el-empty v-else-if="!loading" description="暂无汇编文档" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getCompilation } from '@/api/ai'

const route = useRoute()
const compilation = ref(null)
const loading = ref(true)

onMounted(async () => {
  try {
    const res = await getCompilation(Number(route.params.activityId))
    compilation.value = res.data
  } catch (e) {
    compilation.value = null
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
```

---

## 修复顺序建议

1. **问题 1**（路由）→ 所有页面可访问
2. **问题 4**（props → route.params）→ 页面不报错
3. **问题 2**（侧边栏）→ 导航可用
4. **问题 3**（详情页入口）→ 流程打通
5. **问题 6 + 7**（知识库上传）→ 后端联通
6. **问题 5**（ingest-report）→ 报告入库可用
7. **问题 8**（报告确认路由）→ 审核流程可用
8. **问题 9**（Word 生成）→ 导出可用
9. **问题 10**（社团选择）→ 体验优化
10. **CompilationView**（缺失文件）→ 汇编页面可用

## 验证清单

- [ ] 前端启动无报错
- [ ] 侧边栏显示所有菜单项
- [ ] 活动详情页有打卡/报告入口
- [ ] 打卡表单页可正常提交
- [ ] 打卡管理页可创建任务、生成汇编
- [ ] AI 对话页可发送消息（需 Python 服务运行）
- [ ] 知识库上传文档成功
- [ ] 报告审核页可确认/打回
