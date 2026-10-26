# RAG 问答系统优化指令

> 面向编码 LLM 的精进文档。将 RAG 问答从线性流程升级为 LangGraph 状态图，加入 Reranker 精排、自检机制和降级策略，并建立评估框架。

## 身份定义

你是 Python 后端工程师（FastAPI + LangGraph），负责优化 AI 服务中的 RAG 问答模块。Java 端和前端无需修改。

## 核心规则

1. **仅修改 Python 端**：所有改动在 `ai-service/` 目录内，Java 端和前端不变
2. **对外接口不变**：`POST /chat` 的请求/响应格式保持 `ChatRequest` → `ChatResponse` 不变
3. **模型下载使用国内镜像**：所有 HuggingFace 模型下载必须设置 `HF_ENDPOINT=https://hf-mirror.com`，避免连接 huggingface.co 超时
4. **离线模式优先**：如果本地已有缓存，使用 `HF_HUB_OFFLINE=1`

---

## 当前架构（改造前）

```
用户提问 → hybrid_search（向量+BM25+RRF）→ generate_answer（LLM）→ 返回
```

文件：`ai-service/routes/chat.py`（69 行）
- 线性流程，无质量判断
- 检索结果直接送 LLM，不判断相关性
- LLM 生成回答后直接返回，不校验忠实度
- 异常时直接 500，无降级

---

## 目标架构（改造后）

```
用户提问
  → 问题分类（事实型/汇总型/超范围）
  → 粗排检索（向量+BM25+RRF，取 Top-10）
  → Reranker 精排（取 Top-3）
  → 相关性判断（检索结果是否够用）
    → 不够 → 改写查询重新检索（最多 1 次）
  → LLM 生成回答
  → 自检（忠实度校验）
    → 不通过 → 标注"本回答可能不完全准确"
  → 返回
```

---

## 阶段零：数据清理与紧急修复（先于 LangGraph 改造执行）

> 以下问题在测试中发现，必须优先修复，否则后续优化无意义。

### 问题 0.1：ChromaDB 中存在大量乱码 chunk

**现象**：`club_1` 集合共 13 个 chunk，其中 7 个是乱码（标题为 `Web Workshop-????`，内容含 `???` 和 ``）。这些乱码 chunk 参与检索，严重拉低质量。

**原因**：之前通过 Python `/knowledge/ingest-report` 入库时，PowerShell 传递中文内容时编码异常，导致写入乱码数据。

**修复方式**：创建清理脚本 `ai-service/cleanup_garbled.py`，执行后删除。

```python
"""清理 ChromaDB 中的乱码 chunk"""
import os
os.environ['HF_HUB_OFFLINE'] = '1'

from store.chroma_store import ChromaVectorStore

def cleanup():
    s = ChromaVectorStore()
    col = s._get_collection('club_1')
    data = col.get(include=['documents', 'metadatas'])
    
    garbled_ids = []
    for i, doc in enumerate(data['documents']):
        # 乱码特征：连续问号 或  字符
        if '???' in doc or '' in doc:
            garbled_ids.append(data['ids'][i])
    
    if garbled_ids:
        col.delete(ids=garbled_ids)
        print(f"已删除 {len(garbled_ids)} 个乱码 chunk")
    else:
        print("无乱码 chunk")
    
    # 验证剩余数据
    remaining = col.get(include=['metadatas'])
    print(f"剩余 chunk 数: {len(remaining['ids'])}")
    for i, m in enumerate(remaining['metadatas']):
        title = m.get('title', '?')
        doc_type = m.get('doc_type', '?')
        print(f"  [{doc_type}] {title}")

if __name__ == '__main__':
    cleanup()
```

**执行命令**：
```powershell
cd c:\Users\lrs\Desktop\gie\ai-service
$env:HF_HUB_OFFLINE="1"
python cleanup_garbled.py
```

**验证**：剩余 chunk 应全部为正常中文内容，无 `???` 或 ``。

---

### 问题 0.2：section_type 过滤导致检索返回 0 结果

**文件路径**：`ai-service/rag/retrieval.py`（第 25-42 行）

**现象**：当用户问"成员对活动有什么建议？"时，`detect_section_type` 检测到"建议"关键词，添加 `where={"section_type": "suggestions"}` 过滤。但 ChromaDB 中 `checkin_compilation` 数据不存在（section_type 字段只存在于汇编文档的 chunk 中），导致检索返回 0 结果。

**问题代码**（第 25-42 行）：
```python
def hybrid_search(collection: str, query: str,
                  where: Dict = None) -> List[Dict]:
    effective_where = dict(where) if where else {}
    section_type = detect_section_type(query)
    if section_type:
        effective_where["section_type"] = section_type
    # ... 检索逻辑 ...
    fused = rrf_fusion(vector_results, bm25_results, k=RRF_K)
    return fused[:RETRIEVAL_TOP_K]
```

**修复方案**：当带 section_type 过滤返回 0 结果时，自动去掉 section_type 过滤重新检索。

**修改后的完整函数**：
```python
def hybrid_search(collection: str, query: str,
                  where: Dict = None,
                  top_k: int = None) -> List[Dict]:
    """
    混合检索（向量+BM25+RRF）
    
    参数：
        collection: ChromaDB 集合名
        query: 用户查询
        where: 额外的元数据过滤条件
        top_k: 返回数量。None 时使用 COARSE_TOP_K（粗排，给 Reranker 用）
    """
    if top_k is None:
        from config import COARSE_TOP_K
        top_k = COARSE_TOP_K
    
    effective_where = dict(where) if where else {}
    section_type = detect_section_type(query)
    if section_type:
        effective_where["section_type"] = section_type

    model = get_embedding_model()
    query_embedding = model.encode([query])[0].tolist()
    store = ChromaVectorStore()
    vector_results = store.search(collection, query_embedding,
                                  top_k=VECTOR_SEARCH_TOP_K, where=effective_where)

    bm25_results = bm25_search(collection, query, top_k=BM25_SEARCH_TOP_K,
                                where=effective_where)

    fused = rrf_fusion(vector_results, bm25_results, k=RRF_K)
    results = fused[:top_k]
    
    # 降级：如果 section_type 过滤导致无结果，去掉 section_type 重新检索
    if not results and section_type:
        fallback_where = dict(where) if where else {}
        vector_results = store.search(collection, query_embedding,
                                      top_k=VECTOR_SEARCH_TOP_K, where=fallback_where)
        bm25_results = bm25_search(collection, query, top_k=BM25_SEARCH_TOP_K,
                                    where=fallback_where)
        fused = rrf_fusion(vector_results, bm25_results, k=RRF_K)
        results = fused[:top_k]
        logger.info(f"section_type='{section_type}' 过滤无结果，已降级为不带过滤检索")
    
    return results
```

**验证**：
```powershell
cd c:\Users\lrs\Desktop\gie\ai-service
$env:HF_HUB_OFFLINE="1"
python -c "from rag.retrieval import hybrid_search; r=hybrid_search('club_1', '成员对活动有什么建议'); print(f'Results: {len(r)}')"
```
预期：返回 > 0 个结果（降级到不带 section_type 过滤的检索）。

---

### 问题 0.3：Embedding 模型启动时尝试联网

**文件路径**：`ai-service/rag/ingestion.py`（第 11-21 行）

**现象**：新进程加载模型时尝试连接 huggingface.co，超时后才降级。在无法访问外网的环境下会卡死 30+ 秒。

**修复**：在 `get_embedding_model` 函数中，在导入 `SentenceTransformer` 之前就设置离线环境变量。

**当前代码**（第 11-21 行）：
```python
def get_embedding_model():
    global _embedding_model
    if _embedding_model is None:
        import os
        from sentence_transformers import SentenceTransformer
        from config import EMBEDDING_MODEL, EMBEDDING_DEVICE
        # 设置离线模式，避免联网下载
        os.environ['HF_HUB_OFFLINE'] = '1'
        logger.info(f"Loading embedding model: {EMBEDDING_MODEL}")
        _embedding_model = SentenceTransformer(EMBEDDING_MODEL, device=EMBEDDING_DEVICE)
    return _embedding_model
```

**修改后**：
```python
def get_embedding_model():
    global _embedding_model
    if _embedding_model is None:
        import os
        # 必须在导入 sentence_transformers 之前设置，否则无效
        os.environ['HF_HUB_OFFLINE'] = '1'
        os.environ['TRANSFORMERS_OFFLINE'] = '1'
        from sentence_transformers import SentenceTransformer
        from config import EMBEDDING_MODEL, EMBEDDING_DEVICE
        logger.info(f"Loading embedding model: {EMBEDDING_MODEL}")
        _embedding_model = SentenceTransformer(EMBEDDING_MODEL, device=EMBEDDING_DEVICE)
    return _embedding_model
```

**验证**：
```powershell
cd c:\Users\lrs\Desktop\gie\ai-service
python -c "from rag.ingestion import get_embedding_model; import time; s=time.time(); m=get_embedding_model(); print(f'Loaded in {time.time()-s:.1f}s')"
```
预期：加载时间 < 5 秒（离线模式），无 huggingface.co 连接尝试。

---

## 任务 1：新增 Reranker 模型配置

### 1.1 修改 `ai-service/config.py`

在现有配置末尾（第 43 行 `UPLOAD_DIR` 之后）新增：

```python
# Reranker 模型
RERANKER_MODEL = "BAAI/bge-reranker-v2-m3"
RERANKER_DEVICE = "cpu"
RERANKER_TOP_K = 3

# 检索粗排数量（给 Reranker 精排用）
COARSE_TOP_K = 10

# 自检置信度阈值
SELF_CHECK_THRESHOLD = 0.6
```

### 1.2 修改 `ai-service/requirements.txt`

在末尾新增：

```
FlagEmbedding>=1.2.0
langgraph>=0.2.0
```

> 注意：`langgraph` 已在 requirements.txt 中，只需确认版本 >= 0.2.0。`FlagEmbedding` 是 Reranker 所需的库。

### 1.3 安装依赖

```bash
cd ai-service
pip install FlagEmbedding>=1.2.0 -i https://pypi.tuna.tsinghua.edu.cn/simple
```

### 1.4 下载 Reranker 模型（使用国内镜像）

```bash
# 方式一：使用 huggingface 国内镜像
set HF_ENDPOINT=https://hf-mirror.com
python -c "from FlagEmbedding import FlagReranker; m = FlagReranker('BAAI/bge-reranker-v2-m3'); print('Reranker downloaded')"

# 方式二：如果方式一仍然超时，使用 modelscope
pip install modelscope -i https://pypi.tuna.tsinghua.edu.cn/simple
python -c "from modelscope import snapshot_download; snapshot_download('BAAI/bge-reranker-v2-m3'); print('Downloaded via modelscope')"
```

验证：下载完成后设置 `HF_HUB_OFFLINE=1` 再测试加载：

```bash
set HF_HUB_OFFLINE=1
python -c "from FlagEmbedding import FlagReranker; m = FlagReranker('BAAI/bge-reranker-v2-m3'); print('Offline load OK')"
```

---

## 任务 2：新增 Reranker 模块

### 2.1 创建 `ai-service/rag/reranker.py`

```python
import logging
from typing import List, Dict

logger = logging.getLogger(__name__)

_reranker = None

def get_reranker():
    """懒加载 Reranker 模型，首次加载后缓存"""
    global _reranker
    if _reranker is None:
        import os
        from FlagEmbedding import FlagReranker
        from config import RERANKER_MODEL, RERANKER_DEVICE
        os.environ['HF_HUB_OFFLINE'] = '1'
        logger.info(f"Loading reranker model: {RERANKER_MODEL}")
        _reranker = FlagReranker(RERANKER_MODEL, device=RERANKER_DEVICE, use_fp16=True)
    return _reranker

def rerank(query: str, candidates: List[Dict], top_k: int = 3) -> List[Dict]:
    """
    对候选文档进行重排序
    
    参数：
        query: 用户查询
        candidates: 候选文档列表，每个元素包含 id/content/metadata/score
        top_k: 精排后保留的数量
    
    返回：
        重排序后的文档列表（取 Top-K）
    """
    if not candidates:
        return []
    
    try:
        reranker = get_reranker()
        # 构造 [query, document] 对
        pairs = [[query, doc["content"]] for doc in candidates]
        scores = reranker.compute_score(pairs)
        
        # 如果只返回一个分数，包装为列表
        if isinstance(scores, (int, float)):
            scores = [scores]
        
        # 将 reranker 分数附加到文档
        for doc, score in zip(candidates, scores):
            doc["rerank_score"] = float(score)
        
        # 按 rerank_score 降序排序，取 top_k
        candidates.sort(key=lambda x: x["rerank_score"], reverse=True)
        return candidates[:top_k]
        
    except Exception as e:
        logger.warning(f"Reranker failed, falling back to original order: {e}")
        # 降级：Reranker 失败时返回原始顺序的前 top_k 个
        return candidates[:top_k]
```

---

## 任务 3：修改检索模块，支持粗排 Top-K 配置

### 3.1 修改 `ai-service/rag/retrieval.py`

**修改 `hybrid_search` 函数签名和逻辑**：

当前代码（第 25-42 行）：
```python
def hybrid_search(collection: str, query: str,
                  where: Dict = None) -> List[Dict]:
    effective_where = dict(where) if where else {}
    section_type = detect_section_type(query)
    if section_type:
        effective_where["section_type"] = section_type

    model = get_embedding_model()
    query_embedding = model.encode([query])[0].tolist()
    store = ChromaVectorStore()
    vector_results = store.search(collection, query_embedding,
                                  top_k=VECTOR_SEARCH_TOP_K, where=effective_where)

    bm25_results = bm25_search(collection, query, top_k=BM25_SEARCH_TOP_K,
                                where=effective_where)

    fused = rrf_fusion(vector_results, bm25_results, k=RRF_K)
    return fused[:RETRIEVAL_TOP_K]
```

改为：
```python
def hybrid_search(collection: str, query: str,
                  where: Dict = None,
                  top_k: int = None) -> List[Dict]:
    """
    混合检索
    
    参数：
        top_k: 返回数量。None 时使用 COARSE_TOP_K（粗排，给 Reranker 用）
    """
    if top_k is None:
        from config import COARSE_TOP_K
        top_k = COARSE_TOP_K
    
    effective_where = dict(where) if where else {}
    section_type = detect_section_type(query)
    if section_type:
        effective_where["section_type"] = section_type

    model = get_embedding_model()
    query_embedding = model.encode([query])[0].tolist()
    store = ChromaVectorStore()
    vector_results = store.search(collection, query_embedding,
                                  top_k=VECTOR_SEARCH_TOP_K, where=effective_where)

    bm25_results = bm25_search(collection, query, top_k=BM25_SEARCH_TOP_K,
                                where=effective_where)

    fused = rrf_fusion(vector_results, bm25_results, k=RRF_K)
    return fused[:top_k]
```

---

## 任务 4：修改生成模块，增加自检能力

### 4.1 修改 `ai-service/rag/generator.py`

**保留现有 `generate_answer` 函数**，在其后新增自检函数：

在文件末尾（第 57 行 `return {"answer": answer, "sources": sources}` 之后）新增：

```python
def self_check_answer(answer: str, context_docs: List[Dict]) -> Dict:
    """
    自检：判断回答中的关键事实是否能在检索内容中找到依据
    
    返回：
        {"passed": bool, "confidence": float, "reason": str}
    """
    context_text = ""
    for i, doc in enumerate(context_docs):
        context_text += f"\n[参考{i+1}] {doc['content'][:300]}\n"
    
    prompt = f"""请判断以下回答是否忠实于参考资料。

判断规则：
1. 回答中的关键事实和数据是否都能在参考资料中找到依据
2. 是否有编造、臆测的内容
3. 是否过度推断

参考资料：
{context_text}

回答：
{answer}

请以 JSON 格式返回判断结果：
{{"passed": true/false, "confidence": 0.0-1.0, "reason": "简要说明"}}
只返回 JSON，不要其他内容。"""

    try:
        client = get_llm_client()
        response = client.chat.completions.create(
            model=LLM_MODEL,
            messages=[{"role": "user", "content": prompt}],
            temperature=0.1
        )
        text = response.choices[0].message.content
        if "```json" in text:
            text = text.split("```json")[1].split("```")[0]
        elif "```" in text:
            text = text.split("```")[1].split("```")[0]
        import json
        result = json.loads(text)
        return {
            "passed": result.get("passed", True),
            "confidence": float(result.get("confidence", 0.5)),
            "reason": result.get("reason", "")
        }
    except Exception as e:
        logger.warning(f"Self-check failed: {e}")
        # 自检失败不阻断，默认通过
        return {"passed": True, "confidence": 0.5, "reason": "自检异常，默认通过"}
```

**新增降级回答生成函数**（在 `self_check_answer` 之后）：

```python
def generate_fallback_answer(query: str, context_docs: List[Dict]) -> str:
    """
    降级回答：当 LLM 生成失败时，返回检索结果摘要
    """
    if not context_docs:
        return "抱歉，知识库中未找到相关信息，且 AI 生成服务暂不可用。"
    
    summary = "以下为知识库中的相关内容（AI 生成暂不可用，仅供参考）：\n\n"
    for i, doc in enumerate(context_docs[:3]):
        title = doc.get("metadata", {}).get("title", "未知来源")
        snippet = doc["content"][:200]
        summary += f"[{i+1}] {title}：{snippet}...\n\n"
    return summary
```

---

## 任务 5：用 LangGraph 重构 `routes/chat.py`

### 5.1 完整替换 `ai-service/routes/chat.py`

当前文件 69 行，需要完整替换为以下代码：

```python
import logging
import redis
import json
import uuid
from typing import TypedDict, Optional, List, Dict, Annotated
from fastapi import APIRouter, HTTPException
from models.schemas import ChatRequest, ChatResponse
from rag.retrieval import hybrid_search
from rag.generator import generate_answer, self_check_answer, generate_fallback_answer
from rag.reranker import rerank
from rag.knowledge_base import get_collection_name
from config import REDIS_URL, RERANKER_TOP_K, SELF_CHECK_THRESHOLD, COARSE_TOP_K

logger = logging.getLogger(__name__)
router = APIRouter()

redis_client = redis.from_url(REDIS_URL, decode_responses=True)


# ========== LangGraph State 定义 ==========

class ChatState(TypedDict):
    """RAG 问答状态图"""
    question: str                    # 用户原始问题
    club_id: int                     # 社团 ID
    session_id: str                  # 会话 ID
    history: List[Dict]              # 对话历史
    coarse_results: List[Dict]       # 粗排检索结果
    fine_results: List[Dict]         # 精排检索结果（Reranker 后）
    answer: str                      # LLM 生成的回答
    sources: List[Dict]              # 参考来源
    check_passed: bool               # 自检是否通过
    check_confidence: float          # 自检置信度
    retry_count: int                 # 重试次数
    error: Optional[str]             # 错误信息


# ========== 图节点函数 ==========

def retrieve_node(state: ChatState) -> dict:
    """节点1：粗排检索"""
    try:
        collection = get_collection_name(state["club_id"])
        results = hybrid_search(collection, state["question"], top_k=COARSE_TOP_K)
        return {"coarse_results": results or []}
    except Exception as e:
        logger.error(f"Retrieval failed: {e}")
        return {"coarse_results": [], "error": f"检索失败: {e}"}


def rerank_node(state: ChatState) -> dict:
    """节点2：Reranker 精排"""
    candidates = state["coarse_results"]
    if not candidates:
        return {"fine_results": []}
    
    try:
        fine = rerank(state["question"], candidates, top_k=RERANKER_TOP_K)
        return {"fine_results": fine}
    except Exception as e:
        logger.warning(f"Rerank failed: {e}")
        return {"fine_results": candidates[:RERANKER_TOP_K]}


def relevance_check_node(state: ChatState) -> dict:
    """节点3：相关性判断 — 检索结果是否够用"""
    fine_results = state["fine_results"]
    if not fine_results:
        return {"answer": "抱歉，知识库中暂无相关内容。请先上传文档或等待知识库更新。",
                "sources": [], "check_passed": True}
    
    # 简单判断：最高 rerank_score 是否超过阈值
    max_score = max(doc.get("rerank_score", 0) for doc in fine_results)
    if max_score < 1.0 and state["retry_count"] == 0:
        # 相关性不足，标记需要重试
        return {"retry_count": 1}
    
    return {"retry_count": state["retry_count"]}


def rewrite_query_node(state: ChatState) -> dict:
    """节点4：查询改写（当相关性不足时）"""
    # 简单改写策略：去掉疑问词，保留核心关键词
    question = state["question"]
    stop_words = ["请问", "什么", "怎么", "如何", "为什么", "哪些", "多少", "吗", "呢", "？", "?"]
    rewritten = question
    for sw in stop_words:
        rewritten = rewritten.replace(sw, "")
    rewritten = rewritten.strip()
    
    if rewritten == question or not rewritten:
        # 无法有效改写，跳过
        return {"retry_count": state["retry_count"]}
    
    try:
        collection = get_collection_name(state["club_id"])
        results = hybrid_search(collection, rewritten, top_k=COARSE_TOP_K)
        fine = rerank(rewritten, results, top_k=RERANKER_TOP_K)
        
        # 如果改写后结果更好，替换
        old_max = max((doc.get("rerank_score", 0) for doc in state["fine_results"]), default=0)
        new_max = max((doc.get("rerank_score", 0) for doc in fine), default=0)
        if new_max > old_max:
            return {"fine_results": fine, "retry_count": state["retry_count"] + 1}
    except Exception as e:
        logger.warning(f"Query rewrite failed: {e}")
    
    return {"retry_count": state["retry_count"] + 1}


def generate_node(state: ChatState) -> dict:
    """节点5：LLM 生成回答"""
    fine_results = state["fine_results"]
    if not fine_results:
        return {"answer": "抱歉，知识库中暂无相关内容。请先上传文档或等待知识库更新。",
                "sources": []}
    
    try:
        gen_result = generate_answer(state["question"], fine_results, state["history"])
        answer = gen_result["answer"]
        sources = gen_result["sources"]
        return {"answer": answer, "sources": sources}
    except Exception as e:
        logger.error(f"Generation failed: {e}")
        # 降级：返回检索结果摘要
        fallback = generate_fallback_answer(state["question"], fine_results)
        return {"answer": fallback, "sources": [], "error": str(e)}


def self_check_node(state: ChatState) -> dict:
    """节点6：自检 — 忠实度校验"""
    answer = state.get("answer", "")
    fine_results = state.get("fine_results", [])
    
    # 如果是降级回答或空回答，跳过自检
    if not fine_results or "AI 生成暂不可用" in answer:
        return {"check_passed": True, "check_confidence": 1.0}
    
    try:
        result = self_check_answer(answer, fine_results)
        passed = result["passed"] and result["confidence"] >= SELF_CHECK_THRESHOLD
        
        final_answer = answer
        if not passed:
            final_answer = f"⚠️ 以下回答可能不完全准确，仅供参考：\n\n{answer}"
        
        return {
            "answer": final_answer,
            "check_passed": passed,
            "check_confidence": result["confidence"]
        }
    except Exception as e:
        logger.warning(f"Self-check node failed: {e}")
        return {"check_passed": True, "check_confidence": 0.5}


# ========== 构建 LangGraph 状态图 ==========

def build_chat_graph():
    """构建 RAG 问答状态图"""
    from langgraph.graph import StateGraph, START, END
    
    graph = StateGraph(ChatState)
    
    # 添加节点
    graph.add_node("retrieve", retrieve_node)
    graph.add_node("rerank", rerank_node)
    graph.add_node("relevance_check", relevance_check_node)
    graph.add_node("rewrite_query", rewrite_query_node)
    graph.add_node("generate", generate_node)
    graph.add_node("self_check", self_check_node)
    
    # 主流程
    graph.add_edge(START, "retrieve")
    graph.add_edge("retrieve", "rerank")
    graph.add_edge("rerank", "relevance_check")
    
    # 条件边：相关性判断
    def should_rewrite(state: ChatState) -> str:
        """相关性不足且未重试过 → 改写查询"""
        if state.get("retry_count", 0) == 0 and state.get("fine_results"):
            max_score = max(doc.get("rerank_score", 0) for doc in state["fine_results"])
            if max_score < 1.0:
                return "rewrite"
        return "generate"
    
    graph.add_conditional_edges(
        "relevance_check",
        should_rewrite,
        {"rewrite": "rewrite_query", "generate": "generate"}
    )
    
    graph.add_edge("rewrite_query", "generate")
    graph.add_edge("generate", "self_check")
    graph.add_edge("self_check", END)
    
    return graph.compile()


# 全局图实例（懒加载）
_chat_graph = None

def get_chat_graph():
    global _chat_graph
    if _chat_graph is None:
        _chat_graph = build_chat_graph()
    return _chat_graph


# ========== API 端点 ==========

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

        # 执行 LangGraph 状态图
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
        
        final_state = graph.invoke(initial_state)
        
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
            redis_client.expire(history_key, 86400)  # 24h
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


@router.post("/sessions")
async def create_session(request: dict):
    session_id = str(uuid.uuid4())
    return {
        "session_id": session_id,
        "club_id": request.get("club_id"),
        "created_at": __import__('datetime').datetime.now().isoformat()
    }
```

---

## 任务 6：创建评估框架

### 6.1 创建目录 `ai-service/evaluation/`

### 6.2 创建 `ai-service/evaluation/__init__.py`

空文件。

### 6.3 创建 `ai-service/evaluation/test_dataset.json`

```json
{
  "description": "RAG 问答评估测试集",
  "cases": [
    {
      "id": 1,
      "category": "fact",
      "question": "Web Workshop 活动的参与人数是多少？",
      "expected_keywords": ["2", "参与人数"],
      "expected_doc_type": "activity_report"
    },
    {
      "id": 2,
      "category": "fact",
      "question": "Web Workshop 活动的平均评分是多少？",
      "expected_keywords": ["4", "评分"],
      "expected_doc_type": "activity_report"
    },
    {
      "id": 3,
      "category": "summary",
      "question": "成员对 Web Workshop 活动有什么建议？",
      "expected_keywords": ["时间", "实操"],
      "expected_doc_type": "activity_report"
    },
    {
      "id": 4,
      "category": "summary",
      "question": "成员在活动中主要收获了什么？",
      "expected_keywords": ["Vue", "Composition"],
      "expected_doc_type": "checkin_compilation"
    },
    {
      "id": 5,
      "category": "fact",
      "question": "社团的管理制度是什么？",
      "expected_keywords": ["章程", "管理"],
      "expected_doc_type": "charter"
    },
    {
      "id": 6,
      "category": "out_of_scope",
      "question": "学校食堂怎么样？",
      "expected_keywords": [],
      "expected_doc_type": null,
      "expected_behavior": "reject"
    },
    {
      "id": 7,
      "category": "out_of_scope",
      "question": "今天天气如何？",
      "expected_keywords": [],
      "expected_doc_type": null,
      "expected_behavior": "reject"
    },
    {
      "id": 8,
      "category": "fact",
      "question": "活动的打卡截止时间是什么时候？",
      "expected_keywords": ["截止"],
      "expected_doc_type": "charter"
    },
    {
      "id": 9,
      "category": "summary",
      "question": "成员反映活动中存在什么问题？",
      "expected_keywords": ["问题", "不足"],
      "expected_doc_type": "checkin_compilation"
    },
    {
      "id": 10,
      "category": "fact",
      "question": "Web Workshop 活动在哪个地点举办？",
      "expected_keywords": ["Room", "201"],
      "expected_doc_type": "activity_report"
    }
  ]
}
```

> 注意：以上测试集基于现有知识库数据（Web Workshop 活动报告 + 社团章程）。如果知识库内容不同，需要根据实际内容调整测试用例。

### 6.4 创建 `ai-service/evaluation/evaluator.py`

```python
"""
RAG 问答评估器

使用方式：
    cd ai-service
    python -m evaluation.evaluator

输出：
    - 每个测试用例的检索命中、忠实度、拒答情况
    - 汇总指标：检索命中率、忠实度、拒答率
"""
import json
import logging
import time
from typing import List, Dict
from rag.retrieval import hybrid_search
from rag.reranker import rerank
from rag.generator import generate_answer, self_check_answer
from rag.knowledge_base import get_collection_name

logging.basicConfig(level=logging.INFO, format='%(asctime)s %(levelname)s %(message)s')
logger = logging.getLogger(__name__)


def load_test_dataset(path: str = "evaluation/test_dataset.json") -> List[Dict]:
    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f)
    return data["cases"]


def evaluate_retrieval(case: Dict, club_id: int = 1) -> Dict:
    """评估检索质量"""
    question = case["question"]
    collection = get_collection_name(club_id)
    
    start = time.time()
    # 粗排
    coarse = hybrid_search(collection, question, top_k=10)
    # 精排
    fine = rerank(question, coarse, top_k=3) if coarse else []
    elapsed = time.time() - start
    
    # 检查是否命中预期文档类型
    hit = False
    if case.get("expected_doc_type"):
        for doc in fine:
            if doc.get("metadata", {}).get("doc_type") == case["expected_doc_type"]:
                hit = True
                break
    
    return {
        "case_id": case["id"],
        "question": question,
        "coarse_count": len(coarse),
        "fine_count": len(fine),
        "retrieval_hit": hit,
        "elapsed_seconds": round(elapsed, 2),
        "top_doc_type": fine[0].get("metadata", {}).get("doc_type", "") if fine else ""
    }


def evaluate_generation(case: Dict, club_id: int = 1) -> Dict:
    """评估生成质量"""
    question = case["question"]
    collection = get_collection_name(club_id)
    
    coarse = hybrid_search(collection, question, top_k=10)
    fine = rerank(question, coarse, top_k=3) if coarse else []
    
    # 超范围问题：检查是否正确拒答
    if case.get("expected_behavior") == "reject":
        if not fine:
            return {"case_id": case["id"], "rejected": True, "correct_reject": True}
        # 有检索结果时，让 LLM 判断
        try:
            result = generate_answer(question, fine, [])
            answer = result["answer"]
            reject_keywords = ["暂无", "未找到", "没有相关", "无法回答", "知识库中"]
            is_rejected = any(kw in answer for kw in reject_keywords)
            return {"case_id": case["id"], "rejected": is_rejected, "correct_reject": is_rejected}
        except:
            return {"case_id": case["id"], "rejected": True, "correct_reject": True}
    
    # 正常问题
    if not fine:
        return {"case_id": case["id"], "rejected": False, "keywords_found": [], "faithful": False}
    
    try:
        result = generate_answer(question, fine, [])
        answer = result["answer"]
        
        # 检查关键词命中
        keywords_found = []
        for kw in case.get("expected_keywords", []):
            if kw.lower() in answer.lower():
                keywords_found.append(kw)
        
        # 自检忠实度
        check = self_check_answer(answer, fine)
        
        return {
            "case_id": case["id"],
            "question": question,
            "answer_preview": answer[:100],
            "keywords_found": keywords_found,
            "keyword_hit_rate": len(keywords_found) / max(len(case.get("expected_keywords", [])), 1),
            "faithful": check["passed"],
            "confidence": check["confidence"]
        }
    except Exception as e:
        return {"case_id": case["id"], "error": str(e)}


def run_evaluation(club_id: int = 1):
    """运行完整评估"""
    cases = load_test_dataset()
    
    logger.info(f"=== RAG 评估开始 | 测试用例: {len(cases)} | club_id: {club_id} ===")
    
    # 1. 检索评估
    logger.info("\n--- 检索评估 ---")
    retrieval_results = []
    for case in cases:
        result = evaluate_retrieval(case, club_id)
        retrieval_results.append(result)
        logger.info(f"  Case {result['case_id']}: hit={result['retrieval_hit']} | "
                    f"coarse={result['coarse_count']} fine={result['fine_count']} | "
                    f"{result['elapsed_seconds']}s")
    
    # 2. 生成评估
    logger.info("\n--- 生成评估 ---")
    generation_results = []
    for case in cases:
        result = evaluate_generation(case, club_id)
        generation_results.append(result)
        if "error" in result:
            logger.info(f"  Case {result['case_id']}: ERROR - {result['error']}")
        elif result.get("correct_reject") is not None:
            logger.info(f"  Case {result['case_id']}: reject={result['correct_reject']}")
        else:
            logger.info(f"  Case {result['case_id']}: keywords={result.get('keywords_found', [])} | "
                       f"faithful={result.get('faithful', False)} | "
                       f"confidence={result.get('confidence', 0):.2f}")
    
    # 3. 汇总
    total = len(cases)
    
    # 检索命中率
    fact_cases = [c for c in cases if c["category"] in ("fact", "summary")]
    fact_retrieval = [r for r in retrieval_results if r["retrieval_hit"]]
    retrieval_hit_rate = len(fact_retrieval) / max(len(fact_cases), 1)
    
    # 拒答率
    oos_cases = [c for c in cases if c.get("expected_behavior") == "reject"]
    oos_results = [r for r in generation_results if r.get("correct_reject") is not None]
    reject_rate = sum(1 for r in oos_results if r["correct_reject"]) / max(len(oos_cases), 1)
    
    # 忠实度
    normal_gen = [r for r in generation_results if r.get("faithful") is not None and "correct_reject" not in r]
    faithful_rate = sum(1 for r in normal_gen if r["faithful"]) / max(len(normal_gen), 1)
    avg_confidence = sum(r.get("confidence", 0) for r in normal_gen) / max(len(normal_gen), 1)
    
    logger.info("\n=== 评估汇总 ===")
    logger.info(f"  检索命中率: {retrieval_hit_rate:.1%} ({len(fact_retrieval)}/{len(fact_cases)})")
    logger.info(f"  忠实度:     {faithful_rate:.1%} ({sum(1 for r in normal_gen if r['faithful'])}/{len(normal_gen)})")
    logger.info(f"  平均置信度: {avg_confidence:.2f}")
    logger.info(f"  拒答率:     {reject_rate:.1%} ({sum(1 for r in oos_results if r['correct_reject'])}/{len(oos_cases)})")
    
    # 4. 保存结果
    output = {
        "timestamp": time.strftime("%Y-%m-%d %H:%M:%S"),
        "summary": {
            "total_cases": total,
            "retrieval_hit_rate": round(retrieval_hit_rate, 3),
            "faithful_rate": round(faithful_rate, 3),
            "avg_confidence": round(avg_confidence, 3),
            "reject_rate": round(reject_rate, 3)
        },
        "retrieval_results": retrieval_results,
        "generation_results": generation_results
    }
    
    output_path = "evaluation/eval_result.json"
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(output, f, ensure_ascii=False, indent=2)
    logger.info(f"\n评估结果已保存到 {output_path}")
    
    return output


if __name__ == "__main__":
    run_evaluation()
```

---

## 任务 7：修改 `ai-service/requirements.txt`

确保包含以下依赖（在现有基础上新增）：

```
FlagEmbedding>=1.2.0
```

> `langgraph>=0.2.0` 已存在，无需重复添加。

---

## 执行顺序

### 阶段零：数据清理与紧急修复（必须先执行）
1. **问题 0.1**：清理 ChromaDB 乱码 chunk（执行 `cleanup_garbled.py`）
2. **问题 0.2**：修复 `rag/retrieval.py` 的 section_type 过滤降级
3. **问题 0.3**：修复 `rag/ingestion.py` 的离线模式环境变量顺序

### 阶段一：LangGraph 改造
4. **任务 1**：配置 + 依赖 + Reranker 模型下载
5. **任务 2**：创建 `rag/reranker.py`
6. **任务 3**：修改 `rag/retrieval.py`（top_k 参数，与问题 0.2 的修改合并）
7. **任务 4**：修改 `rag/generator.py`（新增自检 + 降级函数）
8. **任务 5**：重构 `routes/chat.py`（LangGraph 状态图）

### 阶段二：评估与验证
9. **任务 6**：创建评估框架
10. **任务 7**：确认 requirements.txt

## 验证步骤

### 7.1 语法检查

```bash
cd ai-service
python -m py_compile rag/reranker.py
python -m py_compile rag/retrieval.py
python -m py_compile rag/generator.py
python -m py_compile routes/chat.py
python -m py_compile evaluation/evaluator.py
```

### 7.2 启动服务

```bash
cd ai-service
set HF_HUB_OFFLINE=1
python main.py
```

预期日志：
```
Loading reranker model: BAAI/bge-reranker-v2-m3
AI Service started
```

### 7.3 健康检查

```powershell
Invoke-RestMethod -Uri "http://localhost:8000/health"
```

预期返回：`{"status": "ok"}`

### 7.4 对话测试

```powershell
$body = @{club_id=1; message="Web Workshop 活动的参与人数是多少？"; session_id=$null}
Invoke-RestMethod -Uri "http://localhost:8000/chat" -Method POST -ContentType "application/json" -Body ($body | ConvertTo-Json) | ConvertTo-Json -Depth 5
```

预期：返回包含答案 + sources 列表。

### 7.5 运行评估

```bash
cd ai-service
python -m evaluation.evaluator
```

预期：输出检索命中率、忠实度、拒答率，结果保存到 `evaluation/eval_result.json`。

### 7.6 降级测试

临时关闭 ChromaDB（或删除 data/chroma 目录），测试降级行为：
- 检索失败 → 应返回"知识库中暂无相关内容"
- LLM 超时 → 应返回检索结果摘要（含"AI 生成暂不可用"提示）

---

## 文件清单

| 文件路径 | 操作 | 阶段 | 说明 |
|---------|------|------|------|
| `ai-service/cleanup_garbled.py` | **新建→执行→删除** | 0 | 乱码数据清理脚本 |
| `ai-service/rag/retrieval.py` | 修改 | 0+1 | section_type 降级 + top_k 参数 |
| `ai-service/rag/ingestion.py` | 修改 | 0 | 离线模式环境变量顺序修复 |
| `ai-service/config.py` | 修改 | 1 | 新增 Reranker、粗排数量、自检阈值配置 |
| `ai-service/requirements.txt` | 修改 | 1 | 新增 FlagEmbedding |
| `ai-service/rag/reranker.py` | **新建** | 1 | Reranker 模型封装 |
| `ai-service/rag/generator.py` | 修改 | 1 | 新增 self_check_answer + generate_fallback_answer |
| `ai-service/routes/chat.py` | **重写** | 1 | LangGraph StateGraph 实现 |
| `ai-service/evaluation/__init__.py` | **新建** | 2 | 空文件 |
| `ai-service/evaluation/test_dataset.json` | **新建** | 2 | 测试用例集 |
| `ai-service/evaluation/evaluator.py` | **新建** | 2 | 评估脚本 |

## 注意事项

1. **模型下载必须使用国内镜像**：
   - pip 安装用 `-i https://pypi.tuna.tsinghua.edu.cn/simple`
   - HuggingFace 模型用 `set HF_ENDPOINT=https://hf-mirror.com`
   - 已有缓存用 `set HF_HUB_OFFLINE=1`

2. **Reranker 首次加载较慢**（约 10-30 秒），后续请求正常。可在 main.py startup 中预热（可选）。

3. **LangGraph 图是全局单例**（`_chat_graph`），避免每次请求重复构建。

4. **自检会额外消耗一次 LLM 调用**，响应时间会增加。如果延迟不可接受，可通过配置开关跳过自检。

5. **Java 端无需修改**：`POST /chat` 的请求/响应格式未变，Java 的 `AiServiceImpl.chat()` 调用 Python `/chat` 的接口兼容。
