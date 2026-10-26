# 浏览器测试问题修复指令

> 面向编码 LLM 的修复文档。基于浏览器测试中发现的实际问题进行编写。

## 身份定义

你是 Python 后端工程师（FastAPI），负责修复 AI 服务中浏览器测试发现的问题。

## 核心规则

1. **仅修改 Python 端**：所有改动在 `ai-service/` 目录内
2. **模型下载使用国内镜像**：`set HF_ENDPOINT=https://hf-mirror.com`
3. **离线模式**：已有缓存时设置 `set HF_HUB_OFFLINE=1`

---

## 问题总览

| # | 问题 | 严重度 | 文件 |
|---|------|--------|------|
| 1 | ChromaDB 乱码 chunk 污染检索结果 | 🔴 阻断 | `store/chroma_store.py`（数据问题） |
| 2 | 检索结果含乱码时 LLM 生成乱码回答 | 🔴 阻断 | `routes/chat.py` |
| 3 | Python 服务处理异常请求后无响应 | 🟡 重要 | `routes/chat.py` |

---

## 问题 1：ChromaDB 乱码 chunk 污染检索结果

### 现象

`club_1` 集合共 13 个 chunk，其中 7 个是乱码。

乱码 chunk 特征：
- `id` 前缀：`701c6ea5-*` 和 `a3df0d27-*`
- `title`：`Web Workshop-????`
- `content`：包含 `???` 和 `` 字符
- `doc_type`：`activity_report`

正常 chunk（6 个）：
- `3f8da5da-*`：5 个 chunk，活动报告（正常中文）
- `b4777159-*`：1 个 chunk，社团章程（正常中文）

### 原因

之前通过 PowerShell 调用 `/knowledge/ingest-report` 端点时，PowerShell 的编码（GBK）与 Python FastAPI 期望的 UTF-8 不匹配，导致中文内容以乱码形式写入 ChromaDB。同一报告被入库两次：一次乱码、一次正常。

### 修复方案

创建一次性清理脚本，删除乱码 chunk。

### 修复代码

创建文件 `ai-service/cleanup_garbled.py`：

```python
"""
一次性脚本：清理 ChromaDB 中的乱码 chunk
执行后验证剩余数据，确认无乱码后删除此文件
"""
import os
os.environ['HF_HUB_OFFLINE'] = '1'

from store.chroma_store import ChromaVectorStore


def is_garbled(text: str) -> bool:
    """判断文本是否为乱码"""
    # 乱码特征1：连续问号（3个以上）
    if '???' in text:
        return True
    # 乱码特征2：包含  字符（Unicode 替换字符）
    if '' in text:
        return True
    # 乱码特征3：中文占比极低（正常中文文档中文应占 30% 以上）
    chinese_chars = sum(1 for c in text if '\u4e00' <= c <= '\u9fff')
    if len(text) > 50 and chinese_chars / len(text) < 0.1:
        return True
    return False


def cleanup_collection(collection_name: str):
    """清理单个集合中的乱码 chunk"""
    s = ChromaVectorStore()
    col = s._get_collection(collection_name)
    data = col.get(include=['documents', 'metadatas'])
    
    total = len(data['ids'])
    garbled_ids = []
    
    for i, doc in enumerate(data['documents']):
        if is_garbled(doc):
            garbled_ids.append(data['ids'][i])
    
    if garbled_ids:
        col.delete(ids=garbled_ids)
        print(f"[{collection_name}] 删除 {len(garbled_ids)}/{total} 个乱码 chunk")
        for gid in garbled_ids:
            print(f"  删除: {gid}")
    else:
        print(f"[{collection_name}] 无乱码 chunk（共 {total} 个）")
    
    return len(garbled_ids)


def verify_collection(collection_name: str):
    """验证集合中是否还有乱码"""
    s = ChromaVectorStore()
    col = s._get_collection(collection_name)
    data = col.get(include=['documents', 'metadatas'])
    
    print(f"\n[{collection_name}] 验证：剩余 {len(data['ids'])} 个 chunk")
    for i, doc in enumerate(data['documents']):
        title = data['metadatas'][i].get('title', '?')
        doc_type = data['metadatas'][i].get('doc_type', '?')
        preview = doc[:50].replace('\n', ' ')
        garbled_flag = " [乱码!]" if is_garbled(doc) else ""
        print(f"  [{doc_type}] {title}: {preview}...{garbled_flag}")
    
    remaining_garbled = sum(1 for doc in data['documents'] if is_garbled(doc))
    if remaining_garbled > 0:
        print(f"\n  警告：仍有 {remaining_garbled} 个乱码 chunk！")
    else:
        print(f"\n  验证通过：所有 chunk 均为正常内容")


if __name__ == '__main__':
    print("=== ChromaDB 乱码清理工具 ===\n")
    
    # 清理 club_1 集合
    deleted = cleanup_collection('club_1')
    
    # 验证
    verify_collection('club_1')
    
    print(f"\n=== 清理完成，共删除 {deleted} 个乱码 chunk ===")
```

### 执行命令

```powershell
cd c:\Users\lrs\Desktop\gie\ai-service
$env:HF_HUB_OFFLINE="1"
python cleanup_garbled.py
```

### 预期输出

```
=== ChromaDB 乱码清理工具 ===

[club_1] 删除 7/13 个乱码 chunk
  删除: 701c6ea5-8159-46ad-9c07-3b85d21b6811_chunk_0
  删除: 701c6ea5-8159-46ad-9c07-3b85d21b6811_chunk_1
  删除: 701c6ea5-8159-46ad-9c07-3b85d21b6811_chunk_2
  删除: 701c6ea5-8159-46ad-9c07-3b85d21b6811_chunk_3
  删除: 701c6ea5-8159-46ad-9c07-3b85d21b6811_chunk_4
  删除: a3df0d27-f1e1-42f8-bf6e-073b39a1c0a6_chunk_0
  删除: a3df0d27-f1e1-42f8-bf6e-073b39a1c0a6_chunk_1

[club_1] 验证：剩余 6 个 chunk
  [charter] TestClub2 社团章程: TestClub2 社团章程  第一章 总则...
  [activity_report] Web Workshop-成果报告: # 《Web Workshop》活动成果报告...
  [activity_report] Web Workshop-成果报告: ## 一、活动基本信息...
  [activity_report] Web Workshop-成果报告: ## 二、成员反馈汇总...
  [activity_report] Web Workshop-成果报告: ## 三、经验教训...
  [activity_report] Web Workshop-成果报告: ## 四、附件清单...

  验证通过：所有 chunk 均为正常内容

=== 清理完成，共删除 7 个乱码 chunk ===
```

### 验证

执行后确认：
1. 剩余 chunk 数为 6
2. 所有 chunk 的 `is_garbled` 检查为 False
3. 无 `???` 或 `` 字符

---

## 问题 2：检索结果含乱码时 LLM 生成乱码回答

### 现象

提问"成员对活动有什么建议？"，检索到乱码 chunk 后送给 LLM，LLM 基于乱码内容生成了乱码回答。

浏览器显示：
```
??o?o?????????????????????...
```

### 原因

即使问题 1 清理了当前乱码数据，未来仍可能因编码问题入库乱码内容。需要在检索和生成环节增加防御。

### 修复方案

在 `routes/chat.py` 的 `generate_node` 中，送给 LLM 之前过滤掉乱码内容。

### 修复代码

**文件**：`ai-service/routes/chat.py`

在文件顶部（第 1 行 `import logging` 之后）新增辅助函数：

```python
def _is_garbled(text: str) -> bool:
    """检测文本是否为乱码"""
    if '???' in text or '' in text:
        return True
    chinese_chars = sum(1 for c in text if '\u4e00' <= c <= '\u9fff')
    if len(text) > 50 and chinese_chars / len(text) < 0.1:
        return True
    return False


def _filter_garbled_results(results: List[Dict]) -> List[Dict]:
    """过滤掉乱码检索结果"""
    filtered = [r for r in results if not _is_garbled(r.get('content', ''))]
    if len(filtered) < len(results):
        logger.warning(f"过滤了 {len(results) - len(filtered)} 个乱码 chunk")
    return filtered
```

**修改 `generate_node` 函数**（当前在第 111-127 行）：

当前代码：
```python
def generate_node(state: ChatState) -> dict:
    """节点5：LLM 生成回答"""
    fine_results = state["fine_results"]
    if not fine_results:
        return {"answer": "抱歉，知识库中暂无相关内容。请先上传文档或等待知识库更新。",
                "sources": []}
    
    try:
        gen_result = generate_answer(state["question"], fine_results, state["history"])
```

修改为：
```python
def generate_node(state: ChatState) -> dict:
    """节点5：LLM 生成回答"""
    fine_results = state["fine_results"]
    
    # 过滤乱码内容
    fine_results = _filter_garbled_results(fine_results)
    
    if not fine_results:
        return {"answer": "抱歉，知识库中暂无相关内容。请先上传文档或等待知识库更新。",
                "sources": []}
    
    try:
        gen_result = generate_answer(state["question"], fine_results, state["history"])
```

同样**修改 `rerank_node` 函数**（当前在第 51-62 行），在 Reranker 排序前也过滤：

当前代码：
```python
def rerank_node(state: ChatState) -> dict:
    """节点2：Reranker 精排"""
    candidates = state["coarse_results"]
    if not candidates:
        return {"fine_results": []}
    
    try:
        fine = rerank(state["question"], candidates, top_k=RERANKER_TOP_K)
```

修改为：
```python
def rerank_node(state: ChatState) -> dict:
    """节点2：Reranker 精排"""
    candidates = state["coarse_results"]
    
    # 过滤乱码内容，避免乱码进入精排和生成环节
    candidates = _filter_garbled_results(candidates)
    
    if not candidates:
        return {"fine_results": []}
    
    try:
        fine = rerank(state["question"], candidates, top_k=RERANKER_TOP_K)
```

### 验证

清理乱码后（问题 1 修复），再次测试：

```powershell
$body = @{club_id=1; message="成员对活动有什么建议？"; session_id=$null}
$resp = Invoke-RestMethod -Uri "http://localhost:8000/chat" -Method POST -ContentType "application/json" -Body ($body | ConvertTo-Json -Depth 3) -TimeoutSec 60
Write-Host $resp.answer
```

预期：返回正常中文回答（可能提示"暂无相关内容"，因为 checkin_compilation 数据不存在，但不应出现乱码）。

---

## 问题 3：Python 服务处理异常请求后无响应

### 现象

1. 发送"成员对活动有什么建议？"请求
2. 请求超时（60 秒无响应）
3. 之后所有请求（包括 `/health`）都无响应
4. 必须重启 Python 服务才能恢复

### 原因

`routes/chat.py` 中的 LangGraph 状态图在执行 `graph.invoke(initial_state)` 时，如果某个节点（如 LLM 调用）长时间阻塞，整个请求处理线程被占用。由于 uvicorn 默认单 worker，后续所有请求都被阻塞。

此外，`generate_answer` 函数调用 LLM API 时，如果传入的 context 包含乱码内容，LLM API 可能返回异常或超时，而代码中没有对 LLM 调用设置合理的超时保护。

### 修复方案

1. 在 `rag/generator.py` 的 `generate_answer` 中确保 LLM 调用有超时保护
2. 在 `routes/chat.py` 的 `chat` 端点中添加整体超时保护

### 修复代码

**文件 1**：`ai-service/rag/generator.py`

检查 `generate_answer` 函数中 LLM 调用是否使用了带超时的 client。

当前 `get_llm_client` 函数（文件第 11-15 行）：
```python
def get_llm_client():
    global _client
    if _client is None:
        _client = OpenAI(api_key=LLM_API_KEY, base_url=LLM_BASE_URL, timeout=LLM_TIMEOUT)
    return _client
```

已有 `timeout=LLM_TIMEOUT`（config.py 中设置为 60 秒）。但 `self_check_answer` 函数中的 LLM 调用也应确认使用同一 client。

检查 `self_check_answer` 函数，确认它调用的是 `get_llm_client()` 而非新建 client。

**文件 2**：`ai-service/routes/chat.py`

在 `chat` 端点中添加超时保护。修改 `chat` 函数（当前第 212-273 行）：

当前代码：
```python
@router.post("", response_model=ChatResponse)
async def chat(request: ChatRequest):
    try:
        session_id = request.session_id or str(uuid.uuid4())
        # ...
        final_state = graph.invoke(initial_state)
```

修改为：
```python
import asyncio
from concurrent.futures import ThreadPoolExecutor

_executor = ThreadPoolExecutor(max_workers=2)

@router.post("", response_model=ChatResponse)
async def chat(request: ChatRequest):
    try:
        session_id = request.session_id or str(uuid.uuid4())

        # 获取对话历史
        history_key = f"chat:{session_id}:history"
        history = []
        try:
            raw = redis_client.lrange(history_key, 0, -1)
            history = [json.loads(m) for m in raw]
        except:
            pass

        # 在线程池中执行 LangGraph 状态图，避免阻塞事件循环
        graph = get_chat_graph()
        initial_state = {
            "question": request.message,
            "club_id": request.club_id,
            "session_id": session_id,
            "history": history,
            "coarse_results": [],
            "fine_results": [],
            "answer": "",
            "sources": [],
            "check_passed": False,
            "check_confidence": 0.0,
            "retry_count": 0,
            "error": None
        }
        
        try:
            # 使用 asyncio.wait_for 设置 90 秒超时
            loop = asyncio.get_event_loop()
            final_state = await asyncio.wait_for(
                loop.run_in_executor(_executor, graph.invoke, initial_state),
                timeout=90.0
            )
        except asyncio.TimeoutError:
            logger.error(f"Chat graph execution timed out for session={session_id}")
            return ChatResponse(
                session_id=session_id,
                answer="抱歉，处理超时，请稍后重试。",
                sources=[]
            )
        
        answer = final_state.get("answer", "")
        sources = final_state.get("sources", [])
        
        # 记录执行日志
        logger.info(
            f"Chat completed | session={session_id} | "
            f"coarse={len(final_state.get('coarse_results', []))} | "
            f"fine={len(final_state.get('fine_results', []))} | "
            f"retry={final_state.get('retry_count', 0)} | "
            f"check_passed={final_state.get('check_passed', False)} | "
            f"confidence={final_state.get('check_confidence', 0):.2f}"
        )

        # 保存对话历史
        try:
            redis_client.rpush(history_key, json.dumps({"role": "user", "content": request.message}))
            redis_client.rpush(history_key, json.dumps({"role": "assistant", "content": answer}))
            redis_client.expire(history_key, 86400)
        except:
            pass

        return ChatResponse(
            session_id=session_id,
            answer=answer,
            sources=sources
        )
    except Exception as e:
        logger.error(f"Chat error: {e}")
        raise HTTPException(status_code=500, detail=str(e))
```

关键改动：
1. 使用 `ThreadPoolExecutor` 在线程池中执行 `graph.invoke`，避免阻塞 uvicorn 事件循环
2. 使用 `asyncio.wait_for` 设置 90 秒超时，超时后返回友好错误而非无限等待
3. 超时不会导致服务崩溃，只影响当前请求

### 验证

1. 启动服务后发送正常请求，确认正常返回
2. 模拟超时场景（如断开 LLM API），确认 90 秒后返回超时提示而非服务卡死
3. 超时后再次发送请求，确认服务仍可响应

---

## 执行顺序

1. **问题 1**：执行 `cleanup_garbled.py` 清理乱码数据
2. **问题 2**：修改 `routes/chat.py` 添加乱码过滤
3. **问题 3**：修改 `routes/chat.py` 添加超时保护

## 验证步骤

### 步骤 1：清理乱码

```powershell
cd c:\Users\lrs\Desktop\gie\ai-service
$env:HF_HUB_OFFLINE="1"
python cleanup_garbled.py
```

确认输出显示"删除 7 个乱码 chunk"且"验证通过"。

### 步骤 2：重启 Python 服务

```powershell
# 停止旧进程
Get-Process | Where-Object {$_.ProcessName -like "*python*"} | Stop-Process -Force
Start-Sleep -Seconds 2

# 启动新进程
cd c:\Users\lrs\Desktop\gie\ai-service
$env:HF_HUB_OFFLINE="1"
python main.py
```

### 步骤 3：测试正常问题

```powershell
$body = @{club_id=1; message="Web Workshop 活动的参与人数是多少？"; session_id=$null}
$resp = Invoke-RestMethod -Uri "http://localhost:8000/chat" -Method POST -ContentType "application/json" -Body ($body | ConvertTo-Json -Depth 3) -TimeoutSec 90
Write-Host $resp.answer
```

预期：回答包含"2 人"，无乱码。

### 步骤 4：测试建议类问题

```powershell
$body = @{club_id=1; message="成员对活动有什么建议？"; session_id=$null}
$resp = Invoke-RestMethod -Uri "http://localhost:8000/chat" -Method POST -ContentType "application/json" -Body ($body | ConvertTo-Json -Depth 3) -TimeoutSec 90
Write-Host $resp.answer
```

预期：正常中文回答（可能提示暂无相关内容），无乱码。

### 步骤 5：测试超范围问题

```powershell
$body = @{club_id=1; message="学校食堂怎么样？"; session_id=$null}
$resp = Invoke-RestMethod -Uri "http://localhost:8000/chat" -Method POST -ContentType "application/json" -Body ($body | ConvertTo-Json -Depth 3) -TimeoutSec 90
Write-Host $resp.answer
```

预期：回答提示"知识库中暂无相关内容"或类似拒答。

### 步骤 6：浏览器验证

1. 打开 `http://localhost:5173/ai/chat`
2. 发送"Web Workshop 活动的参与人数是多少？"→ 确认正常回答
3. 发送"成员对活动有什么建议？"→ 确认无乱码
4. 检查控制台无错误

---

## 文件清单

| 文件路径 | 操作 | 说明 |
|---------|------|------|
| `ai-service/cleanup_garbled.py` | **新建→执行→删除** | 乱码清理脚本 |
| `ai-service/routes/chat.py` | 修改 | 添加乱码过滤 + 超时保护 |

## 注意事项

1. `cleanup_garbled.py` 是一次性脚本，执行成功并验证后应删除
2. 乱码过滤函数 `_is_garbled` 同时出现在 `cleanup_garbled.py` 和 `routes/chat.py` 中，逻辑一致
3. 超时时间设为 90 秒，因为 RAG 流程涉及：Embedding 编码 + 向量检索 + BM25 + Reranker + LLM 生成 + 自检，总耗时可能较长
4. `ThreadPoolExecutor(max_workers=2)` 允许同时处理 2 个请求，避免单请求阻塞整个服务

