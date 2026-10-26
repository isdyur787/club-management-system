# AI 模块详细设计文档

## 1. Python 服务模块设计

### 1.1 入口与路由

#### main.py

```python
from fastapi import FastAPI
from routes import chat, report, experience, knowledge

app = FastAPI(title="Club AI Service", version="1.0.0")

app.include_router(chat.router, prefix="/chat", tags=["对话"])
app.include_router(report.router, prefix="/report", tags=["成果报告"])
app.include_router(experience.router, prefix="/experience", tags=["经验参考"])
app.include_router(knowledge.router, prefix="/knowledge", tags=["知识库"])

@app.on_event("startup")
async def startup():
    # 初始化 Embedding 模型（全局单例）
    from rag.ingestion import get_embedding_model
    get_embedding_model()
    # 初始化 ChromaDB 客户端
    from store.chroma_store import get_chroma_client
    get_chroma_client()
```

---

### 1.2 向量存储模块

#### store/vector_store.py（抽象接口）

```python
from abc import ABC, abstractmethod
from typing import List, Dict, Optional
from dataclasses import dataclass

@dataclass
class SearchResult:
    id: str
    content: str
    metadata: Dict
    score: float

class VectorStoreRepository(ABC):
    @abstractmethod
    def add_documents(self, collection: str, documents: List[str],
                      metadatas: List[Dict], ids: List[str]) -> None:
        pass

    @abstractmethod
    def search(self, collection: str, query_embedding: List[float],
               top_k: int = 20, where: Optional[Dict] = None) -> List[SearchResult]:
        pass

    @abstractmethod
    def delete_documents(self, collection: str, ids: List[str]) -> None:
        pass

    @abstractmethod
    def delete_collection(self, collection: str) -> None:
        pass

    @abstractmethod
    def count(self, collection: str) -> int:
        pass
```

#### store/chroma_store.py（ChromaDB 实现）

```python
import chromadb
from chromadb.config import Settings
from config import CHROMA_PERSIST_DIR

_client = None

def get_chroma_client():
    global _client
    if _client is None:
        _client = chromadb.PersistentClient(path=CHROMA_PERSIST_DIR)
    return _client

class ChromaVectorStore(VectorStoreRepository):
    def __init__(self):
        self.client = get_chroma_client()

    def _get_collection(self, collection: str):
        return self.client.get_or_create_collection(
            name=collection,
            metadata={"hnsw:space": "cosine"}
        )

    def add_documents(self, collection: str, documents: List[str],
                      metadatas: List[Dict], ids: List[str]) -> None:
        col = self._get_collection(collection)
        # 使用 Embedding 模型生成向量
        from rag.ingestion import get_embedding_model
        model = get_embedding_model()
        embeddings = model.encode(documents).tolist()
        col.add(documents=documents, embeddings=embeddings,
                metadatas=metadatas, ids=ids)

    def search(self, collection: str, query_embedding: List[float],
               top_k: int = 20, where: Optional[Dict] = None) -> List[SearchResult]:
        col = self._get_collection(collection)
        kwargs = {"query_embeddings": [query_embedding], "n_results": top_k}
        if where:
            kwargs["where"] = where
        results = col.query(**kwargs)
        # 转换为 SearchResult 列表
        ...

    def delete_documents(self, collection: str, ids: List[str]) -> None:
        col = self._get_collection(collection)
        col.delete(ids=ids)

    def delete_collection(self, collection: str) -> None:
        self.client.delete_collection(collection)

    def count(self, collection: str) -> int:
        col = self._get_collection(collection)
        return col.count()
```

---

### 1.3 RAG 引擎模块

#### rag/ingestion.py（文档入库 + 切块策略）

```python
from langchain_text_splitters import RecursiveCharacterTextSplitter
from sentence_transformers import SentenceTransformer
from config import EMBEDDING_MODEL, EMBEDDING_DEVICE, CHUNK_SIZE, CHUNK_OVERLAP
from store.chroma_store import ChromaVectorStore
from tools.document_parser import parse_document
import re

_embedding_model = None

def get_embedding_model():
    global _embedding_model
    if _embedding_model is None:
        _embedding_model = SentenceTransformer(EMBEDDING_MODEL, device=EMBEDDING_DEVICE)
    return _embedding_model

def chunk_by_doc_type(doc_type: str, content: str) -> List[Dict]:
    """
    根据文档类型选择切块策略
    返回 [{"text": str, "metadata_addition": dict}]
    """
    if doc_type == "checkin_compilation":
        return chunk_compilation_by_section(content)
    elif doc_type == "faq":
        return chunk_faq_by_qa(content)
    elif doc_type in ("charter", "activity_report"):
        return chunk_by_markdown_sections(content)
    else:
        return chunk_by_fixed_size(content)

def chunk_by_fixed_size(content: str) -> List[Dict]:
    """固定大小切块（512 token, 重叠 64）"""
    splitter = RecursiveCharacterTextSplitter(
        chunk_size=CHUNK_SIZE,
        chunk_overlap=CHUNK_OVERLAP,
        separators=["\n\n", "\n", "。", "！", "？", ".", " "]
    )
    chunks = splitter.split_text(content)
    return [{"text": c, "metadata_addition": {}} for c in chunks]

def chunk_by_markdown_sections(content: str) -> List[Dict]:
    """按 Markdown 标题切块，保留章节上下文"""
    sections = re.split(r'(?=^## )', content, flags=re.MULTILINE)
    result = []
    for section in sections:
        section = section.strip()
        if not section:
            continue
        if len(section) > 1500:  # 超长章节再按固定大小切
            for chunk in chunk_by_fixed_size(section):
                result.append(chunk)
        else:
            result.append({"text": section, "metadata_addition": {}})
    return result

def chunk_faq_by_qa(content: str) -> List[Dict]:
    """FAQ 按 Q&A 对切块"""
    pairs = re.split(r'(?=^Q[\d]*[\u3001.．\s])', content, flags=re.MULTILINE)
    result = []
    for pair in pairs:
        pair = pair.strip()
        if pair:
            result.append({"text": pair, "metadata_addition": {}})
    return result

def chunk_compilation_by_section(content: str) -> List[Dict]:
    """
    打卡汇编文档按维度切块
    将“所有成员的收获”“所有成员的问题”“所有成员的建议”分别作为独立 chunk
    每个 chunk 带 section_type 元数据，支持精准检索
    """
    result = []
    
    # 提取各维度内容
    reflections = extract_section(content, "收获")
    problems = extract_section(content, "问题")
    suggestions = extract_section(content, "建议")
    general = extract_general_info(content)  # 基本信息 + 统计汇总
    
    if reflections:
        result.append({"text": reflections, "metadata_addition": {"section_type": "reflections"}})
    if problems:
        result.append({"text": problems, "metadata_addition": {"section_type": "problems"}})
    if suggestions:
        result.append({"text": suggestions, "metadata_addition": {"section_type": "suggestions"}})
    if general:
        result.append({"text": general, "metadata_addition": {"section_type": "general"}})
    
    return result

def extract_section(content: str, dimension: str) -> str:
    """从汇编文档中提取特定维度的内容"""
    # 匹配“成员反馈明细”中每个成员的该维度内容
    pattern = rf'[\u3010{dimension}】](.*?)(?=[\u3010]|\n\n\d+\.\d+|$)'
    matches = re.findall(pattern, content, re.DOTALL)
    if matches:
        return f"【{dimension}汇总】\n" + "\n".join(f"- {m.strip()}" for m in matches if m.strip())
    return ""

def extract_general_info(content: str) -> str:
    """提取汇编文档的基本信息和统计汇总部分"""
    sections = []
    # 提取“一、基本信息”
    match = re.search(r'一、基本信息\n(.*?)(?=二、)', content, re.DOTALL)
    if match:
        sections.append("基本信息\n" + match.group(1).strip())
    # 提取“三、问题汇总”和“四、建议汇总”
    for title in ["问题汇总", "建议汇总"]:
        match = re.search(rf'{title}\n(.*?)(?=\n[四五六]、|$)', content, re.DOTALL)
        if match:
            sections.append(f"{title}\n" + match.group(1).strip())
    return "\n\n".join(sections) if sections else ""

def ingest_document(collection: str, doc_id: str, content: str,
                    metadata: Dict) -> int:
    """
    入库单个文档，根据 doc_type 自动选择切块策略
    返回分块数量
    """
    doc_type = metadata.get("doc_type", "")
    chunk_results = chunk_by_doc_type(doc_type, content)
    
    if not chunk_results:
        return 0
    
    chunks = [r["text"] for r in chunk_results]
    chunk_ids = [f"{doc_id}_chunk_{i}" for i in range(len(chunks))]
    chunk_metadatas = [{**metadata, **r["metadata_addition"], "chunk_index": i}
                       for i, r in enumerate(chunk_results)]
    
    store = ChromaVectorStore()
    store.add_documents(collection, chunks, chunk_metadatas, chunk_ids)
    
    return len(chunks)

def ingest_from_file(collection: str, doc_id: str, file_path: str,
                     metadata: Dict) -> int:
    """从文件入库（先解析再入库）"""
    content = parse_document(file_path)
    return ingest_document(collection, doc_id, content, metadata)
```

#### rag/retrieval.py（混合检索 + 维度过滤）

```python
from rank_bm25 import BM25Okapi
import numpy as np
from typing import Optional
from config import RRF_K, VECTOR_SEARCH_TOP_K, BM25_SEARCH_TOP_K, RETRIEVAL_TOP_K
from store.chroma_store import ChromaVectorStore
from rag.ingestion import get_embedding_model

def detect_section_type(query: str) -> Optional[str]:
    """
    通过关键词匹配判断用户提问涉及哪个维度
    返回 section_type 或 None（不过滤）
    """
    problem_keywords = ["问题", "不足", "困难", "体验不好", "缺点", "失误", "踩坑"]
    suggestion_keywords = ["改进", "建议", "下次", "优化", "提升", "怎么办", "怎么改"]
    reflection_keywords = ["收获", "学到", "心得", "启发", "成长", "理解", "感悟"]
    
    if any(kw in query for kw in problem_keywords):
        return "problems"
    elif any(kw in query for kw in suggestion_keywords):
        return "suggestions"
    elif any(kw in query for kw in reflection_keywords):
        return "reflections"
    return None

def hybrid_search(collection: str, query: str,
                  where: Dict = None) -> List[Dict]:
    """
    混合检索：维度识别 + 向量检索 + BM25 + RRF 融合
    返回 Top-K 结果
    """
    # 1. 维度识别：自动添加 section_type 过滤
    effective_where = dict(where) if where else {}
    section_type = detect_section_type(query)
    if section_type:
        effective_where["section_type"] = section_type
    
    # 2. 向量检索
    model = get_embedding_model()
    query_embedding = model.encode([query])[0].tolist()
    store = ChromaVectorStore()
    vector_results = store.search(collection, query_embedding,
                                  top_k=VECTOR_SEARCH_TOP_K, where=effective_where)

    # 3. BM25 检索
    bm25_results = bm25_search(collection, query, top_k=BM25_SEARCH_TOP_K,
                                where=effective_where)

    # 4. RRF 融合
    fused = rrf_fusion(vector_results, bm25_results, k=RRF_K)

    # 5. 取 Top-K
    return fused[:RETRIEVAL_TOP_K]

def bm25_search(collection: str, query: str,
                top_k: int = 20, where: Dict = None) -> List[SearchResult]:
    """BM25 关键词检索"""
    # 从 ChromaDB 获取全部文档（带元数据过滤）
    col = ChromaVectorStore()._get_collection(collection)
    all_docs = col.get(where=where, include=["documents", "metadatas"])

    if not all_docs["documents"]:
        return []

    # 分词
    tokenized_docs = [list(doc) for doc in all_docs["documents"]]
    tokenized_query = list(query)

    # BM25 计算
    bm25 = BM25Okapi(tokenized_docs)
    scores = bm25.get_scores(tokenized_query)

    # 取 Top-K
    top_indices = np.argsort(scores)[::-1][:top_k]
    results = []
    for idx in top_indices:
        if scores[idx] > 0:
            results.append(SearchResult(
                id=all_docs["ids"][idx],
                content=all_docs["documents"][idx],
                metadata=all_docs["metadatas"][idx],
                score=float(scores[idx])
            ))
    return results

def rrf_fusion(vector_results: List[SearchResult],
               bm25_results: List[SearchResult],
               k: int = 60) -> List[Dict]:
    """
    Reciprocal Rank Fusion
    score = Σ 1/(k + rank_i)
    """
    doc_scores = {}  # id -> {score, content, metadata}

    for rank, result in enumerate(vector_results):
        if result.id not in doc_scores:
            doc_scores[result.id] = {
                "score": 0, "content": result.content,
                "metadata": result.metadata
            }
        doc_scores[result.id]["score"] += 1.0 / (k + rank + 1)

    for rank, result in enumerate(bm25_results):
        if result.id not in doc_scores:
            doc_scores[result.id] = {
                "score": 0, "content": result.content,
                "metadata": result.metadata
            }
        doc_scores[result.id]["score"] += 1.0 / (k + rank + 1)

    # 按分数排序
    sorted_docs = sorted(doc_scores.items(), key=lambda x: x[1]["score"],
                         reverse=True)
    return [{"id": id, **data} for id, data in sorted_docs]
```

#### rag/generator.py（LLM 生成）

```python
from openai import OpenAI
from config import LLM_API_KEY, LLM_BASE_URL, LLM_MODEL, LLM_TEMPERATURE, LLM_TIMEOUT

_client = None

def get_llm_client():
    global _client
    if _client is None:
        _client = OpenAI(api_key=LLM_API_KEY, base_url=LLM_BASE_URL)
    return _client

def generate_answer(query: str, context_docs: List[Dict],
                    chat_history: List[Dict] = None) -> Dict:
    """
    基于检索结果生成回答
    返回 {answer, sources}
    """
    # 1. 组装上下文
    context_text = ""
    for i, doc in enumerate(context_docs):
        title = doc["metadata"].get("title", "未知来源")
        context_text += f"\n[来源{i+1}] {title}\n{doc['content']}\n"

    # 2. 组装 Prompt
    system_prompt = f"""你是一个社团知识助手。基于以下参考资料回答用户的问题。

规则：
1. 只基于提供的参考资料回答，不要编造信息
2. 如果参考资料中没有相关信息，明确告知用户
3. 回答中标注引用来源，格式为 [来源1]、[来源2]

参考资料：
{context_text}"""

    # 3. 构建消息列表
    messages = [{"role": "system", "content": system_prompt}]
    if chat_history:
        messages.extend(chat_history[-10:])  # 最近 5 轮
    messages.append({"role": "user", "content": query})

    # 4. 调用 LLM
    client = get_llm_client()
    response = client.chat.completions.create(
        model=LLM_MODEL,
        messages=messages,
        temperature=LLM_TEMPERATURE,
        timeout=LLM_TIMEOUT
    )

    answer = response.choices[0].message.content

    # 5. 构建来源列表
    sources = []
    for i, doc in enumerate(context_docs):
        sources.append({
            "doc_id": doc["id"],
            "title": doc["metadata"].get("title", ""),
            "snippet": doc["content"][:200] + "...",
            "doc_type": doc["metadata"].get("doc_type", "")
        })

    return {"answer": answer, "sources": sources}
```

#### rag/knowledge_base.py（知识库管理）

```python
from store.chroma_store import ChromaVectorStore
from rag.ingestion import ingest_document, ingest_from_file
import uuid

def get_collection_name(club_id: int) -> str:
    return f"club_{club_id}"

def add_document(club_id: int, doc_type: str, title: str,
                 content: str, source: str, source_id: int = None) -> str:
    """添加文档到知识库，返回文档 ID"""
    collection = get_collection_name(club_id)
    doc_id = str(uuid.uuid4())
    metadata = {
        "club_id": club_id,
        "doc_type": doc_type,
        "title": title,
        "source": source,
    }
    if source_id:
        metadata["source_id"] = source_id

    chunk_count = ingest_document(collection, doc_id, content, metadata)
    return doc_id, chunk_count

def delete_document(club_id: int, chroma_ids: List[str]) -> None:
    """从知识库删除文档"""
    collection = get_collection_name(club_id)
    store = ChromaVectorStore()
    store.delete_documents(collection, chroma_ids)

def count_documents(club_id: int) -> int:
    """统计知识库文档数"""
    collection = get_collection_name(club_id)
    store = ChromaVectorStore()
    return store.count(collection)
```

---

### 1.4 Agent 模块

#### agents/report_agent.py（成果撰写 Agent）

```python
from langgraph.graph import StateGraph, END
from langgraph.checkpoint.memory import MemorySaver
from typing import TypedDict, List, Dict, Optional, Annotated
from tools.db_reader import DBReader
from rag.generator import generate_answer
from tools.document_generator import generate_word_document

class ReportState(TypedDict):
    activity_id: int
    club_id: int
    compilation_content: str          # 汇编文档内容（而非原始打卡记录）
    classified_materials: Dict[str, List]
    draft_content: str
    file_path: Optional[str]
    review_action: Optional[str]     # "approved" or "rejected"
    review_feedback: Optional[str]
    revision_count: int

def collect_compilation(state: ReportState) -> ReportState:
    """从 MySQL 读取该活动的汇编文档（而非原始打卡记录）"""
    reader = DBReader()
    compilation = reader.get_compilation_by_activity(state["activity_id"])
    activity = reader.get_activity(state["activity_id"])
    if not compilation:
        raise ValueError(f"活动 {state['activity_id']} 尚未生成汇编文档，请先等待打卡截止并自动生成汇编")
    return {**state, "compilation_content": compilation["content"], "activity_info": activity}

def classify_materials(state: ReportState) -> ReportState:
    """LLM 基于汇编文档按维度分类整理"""
    compilation = state["compilation_content"]
    # 构建分类 Prompt
    prompt = f"""请将以下汇编文档中的成员反馈按维度分类整理：
    - reflections: 主要收获（摘要合并）
    - problems: 遇到的问题（归类合并）
    - suggestions: 改进建议（归类合并）
    - ratings: 评分统计

    汇编文档内容：
    {compilation}

    返回 JSON 格式。"""

    # 调用 LLM 分类
    from rag.generator import get_llm_client
    client = get_llm_client()
    response = client.chat.completions.create(
        model=LLM_MODEL,
        messages=[{"role": "user", "content": prompt}],
        temperature=0.2
    )
    classified = parse_json(response.choices[0].message.content)
    return {**state, "classified_materials": classified}

def generate_draft(state: ReportState) -> ReportState:
    """LLM 生成结构化报告草稿"""
    activity = state["activity_info"]
    materials = state["classified_materials"]

    prompt = f"""基于以下信息生成活动成果报告：

活动信息：
- 标题：{activity['title']}
- 时间：{activity['start_time']} ~ {activity['end_time']}
- 地点：{activity['location']}
- 参与人数：{activity['current_participants']}
- 预算：{activity['budget']}

成员反馈：
{materials}

请生成结构化报告，包含：
一、活动基本信息
二、成员反馈汇总（含评分统计、收获摘要、建议归类）
三、经验教训
四、附件清单

使用 Markdown 格式。"""

    from rag.generator import get_llm_client
    client = get_llm_client()
    response = client.chat.completions.create(
        model=LLM_MODEL,
        messages=[{"role": "user", "content": prompt}],
        temperature=0.3
    )
    draft = response.choices[0].message.content
    return {**state, "draft_content": draft}

def generate_document_node(state: ReportState) -> ReportState:
    """将草稿转为 Word 文档"""
    file_path = generate_word_document(
        title=state["activity_info"]["title"] + "-成果报告",
        content=state["draft_content"],
        club_id=state["club_id"],
        activity_id=state["activity_id"]
    )
    return {**state, "file_path": file_path}

def human_review_node(state: ReportState) -> ReportState:
    """LangGraph interrupt，等待团长审核"""
    # 这里通过 LangGraph 的 interrupt 机制暂停
    # 等待外部调用 resume 传入审核结果
    from langgraph.types import interrupt
    review_result = interrupt({
        "draft": state["draft_content"],
        "file_path": state["file_path"],
        "message": "请审核报告草稿"
    })
    return {
        **state,
        "review_action": review_result["action"],
        "review_feedback": review_result.get("feedback")
    }

def revise_draft(state: ReportState) -> ReportState:
    """根据团长反馈修改草稿"""
    feedback = state["review_feedback"]
    prompt = f"""请根据以下反馈修改报告：

原报告：
{state['draft_content']}

修改意见：
{feedback}

请输出修改后的完整报告。"""

    from rag.generator import get_llm_client
    client = get_llm_client()
    response = client.chat.completions.create(
        model=LLM_MODEL,
        messages=[{"role": "user", "content": prompt}],
        temperature=0.3
    )
    return {
        **state,
        "draft_content": response.choices[0].message.content,
        "revision_count": state["revision_count"] + 1
    }

def save_report_node(state: ReportState) -> ReportState:
    """保存报告到数据库"""
    # 调用 Java 后端 API 保存
    ...
    return state

def ingest_node(state: ReportState) -> ReportState:
    """报告入库知识库"""
    from rag.knowledge_base import add_document
    add_document(
        club_id=state["club_id"],
        doc_type="activity_report",
        title=state["activity_info"]["title"] + "-成果报告",
        content=state["draft_content"],
        source="system",
        source_id=state["activity_id"]
    )
    return state

def should_revise(state: ReportState) -> str:
    """条件边：审核通过还是打回"""
    if state["review_action"] == "approved":
        return "save"
    else:
        return "revise"

# 构建 LangGraph
def build_report_graph():
    graph = StateGraph(ReportState)

    graph.add_node("collect_materials", collect_materials)
    graph.add_node("classify_materials", classify_materials)
    graph.add_node("generate_draft", generate_draft)
    graph.add_node("generate_document", generate_document_node)
    graph.add_node("human_review", human_review_node)
    graph.add_node("revise_draft", revise_draft)
    graph.add_node("save_report", save_report_node)
    graph.add_node("ingest", ingest_node)

    graph.set_entry_point("collect_materials")
    graph.add_edge("collect_materials", "classify_materials")
    graph.add_edge("classify_materials", "generate_draft")
    graph.add_edge("generate_draft", "generate_document")
    graph.add_edge("generate_document", "human_review")
    graph.add_conditional_edges("human_review", should_revise, {
        "save": "save_report",
        "revise": "revise_draft"
    })
    graph.add_edge("revise_draft", "generate_document")  # 修改后重新生成文档
    graph.add_edge("save_report", "ingest")
    graph.add_edge("ingest", END)

    return graph.compile(checkpointer=MemorySaver())
```

#### agents/experience_agent.py（经验参考 Agent）

```python
from langgraph.graph import StateGraph, END
from typing import TypedDict, List, Dict, Optional
from rag.retrieval import hybrid_search
from rag.generator import generate_answer

class ExperienceState(TypedDict):
    club_id: int
    activity_type: str
    title: str
    location: str
    max_participants: int
    budget: float
    search_results: List[Dict]
    has_sufficient_data: bool
    suggestions: List[str]
    risk_warnings: List[str]
    template: Optional[str]

def retrieve_experience(state: ExperienceState) -> ExperienceState:
    """RAG 检索该社团历史成果"""
    collection = f"club_{state['club_id']}"
    query = f"{state['title']} {state['activity_type']} 经验 建议 问题"
    results = hybrid_search(collection, query,
                            where={"doc_type": "activity_report"})
    return {**state, "search_results": results}

def check_sufficiency(state: ExperienceState) -> ExperienceState:
    """判断检索结果是否充分"""
    has_data = len(state["search_results"]) >= 2
    return {**state, "has_sufficient_data": has_data}

def generate_suggestions(state: ExperienceState) -> ExperienceState:
    """有历史数据时，生成经验建议"""
    prompt = f"""基于以下历史活动成果，为新活动提供策划建议：

新活动信息：
- 类型：{state['activity_type']}
- 标题：{state['title']}
- 地点：{state['location']}
- 预计人数：{state['max_participants']}
- 预算：{state['budget']}

历史活动经验：
{state['search_results']}

请输出：
1. experience_summary: 经验总结（1-2段）
2. suggestions: 具体建议列表
3. risk_warnings: 风险提醒列表

返回 JSON 格式。"""

    from rag.generator import get_llm_client
    client = get_llm_client()
    response = client.chat.completions.create(
        model=LLM_MODEL,
        messages=[{"role": "user", "content": prompt}],
        temperature=0.3
    )
    result = parse_json(response.choices[0].message.content)
    return {
        **state,
        "suggestions": result.get("suggestions", []),
        "risk_warnings": result.get("risk_warnings", []),
        "experience_summary": result.get("experience_summary", "")
    }

def generate_template(state: ExperienceState) -> ExperienceState:
    """无历史数据时，返回通用模板"""
    template = {
        "experience_summary": "该社团暂无历史活动数据，以下为通用策划建议。",
        "suggestions": [
            "提前两周发布活动通知，确保足够的报名时间",
            "活动前确认场地预约成功",
            "准备备用方案应对突发情况",
            "安排专人拍照记录活动过程",
            "活动结束后及时收集成员反馈"
        ],
        "risk_warnings": [
            "注意控制预算，预留10%-15%的缓冲",
            "关注天气预报，户外活动需准备应急预案"
        ]
    }
    return {
        **state,
        "suggestions": template["suggestions"],
        "risk_warnings": template["risk_warnings"],
        "experience_summary": template["experience_summary"],
        "template": template
    }

def should_use_template(state: ExperienceState) -> str:
    if state["has_sufficient_data"]:
        return "generate"
    return "template"

def build_experience_graph():
    graph = StateGraph(ExperienceState)

    graph.add_node("retrieve", retrieve_experience)
    graph.add_node("check", check_sufficiency)
    graph.add_node("generate", generate_suggestions)
    graph.add_node("template", generate_template)

    graph.set_entry_point("retrieve")
    graph.add_edge("retrieve", "check")
    graph.add_conditional_edges("check", should_use_template, {
        "generate": "generate",
        "template": "template"
    })
    graph.add_edge("generate", END)
    graph.add_edge("template", END)

    return graph.compile()
```

---

### 1.5 工具模块

#### tools/db_reader.py（MySQL 数据读取）

```python
import pymysql
from config import MYSQL_HOST, MYSQL_PORT, MYSQL_USER, MYSQL_PASSWORD, MYSQL_DATABASE

class DBReader:
    def __init__(self):
        self.conn = pymysql.connect(
            host=MYSQL_HOST, port=MYSQL_PORT,
            user=MYSQL_USER, password=MYSQL_PASSWORD,
            database=MYSQL_DATABASE, charset="utf8mb4"
        )

    def get_checkin_records(self, activity_id: int) -> List[Dict]:
        """获取活动下所有打卡记录"""
        with self.conn.cursor(pymysql.cursors.DictCursor) as cursor:
            cursor.execute("""
                SELECT cr.*, u.real_name, u.email
                FROM act_checkin_record cr
                JOIN sys_user u ON cr.user_id = u.id
                WHERE cr.activity_id = %s AND cr.deleted = 0
                ORDER BY cr.created_at
            """, (activity_id,))
            return cursor.fetchall()

    def get_activity(self, activity_id: int) -> Dict:
        """获取活动信息"""
        with self.conn.cursor(pymysql.cursors.DictCursor) as cursor:
            cursor.execute("""
                SELECT a.*, c.club_name
                FROM act_activity a
                JOIN club_info c ON a.club_id = c.id
                WHERE a.id = %s AND a.deleted = 0
            """, (activity_id,))
            return cursor.fetchone()

    def get_activity_reports(self, club_id: int, limit: int = 10) -> List[Dict]:
        """获取社团历史成果报告"""
        with self.conn.cursor(pymysql.cursors.DictCursor) as cursor:
            cursor.execute("""
                SELECT * FROM ai_activity_report
                WHERE club_id = %s AND status = 'CONFIRMED' AND deleted = 0
                ORDER BY created_at DESC LIMIT %s
            """, (club_id, limit))
            return cursor.fetchall()

    def get_compilation_by_activity(self, activity_id: int) -> Optional[Dict]:
        """获取活动的汇编文档"""
        with self.conn.cursor(pymysql.cursors.DictCursor) as cursor:
            cursor.execute("""
                SELECT cc.*, a.title as activity_title
                FROM act_checkin_compilation cc
                JOIN act_activity a ON cc.activity_id = a.id
                WHERE cc.activity_id = %s AND cc.deleted = 0
            """, (activity_id,))
            return cursor.fetchone()

    def get_checkin_records_for_compilation(self, activity_id: int) -> List[Dict]:
        """获取活动下所有打卡记录（含用户信息，用于汇编生成）"""
        with self.conn.cursor(pymysql.cursors.DictCursor) as cursor:
            cursor.execute("""
                SELECT cr.id, cr.rating, cr.reflection, cr.problem, cr.suggestion,
                       cr.photo_urls, cr.image_descriptions, cr.file_urls,
                       u.real_name, u.email
                FROM act_checkin_record cr
                JOIN sys_user u ON cr.user_id = u.id
                WHERE cr.activity_id = %s AND cr.deleted = 0
                ORDER BY cr.created_at
            """, (activity_id,))
            return cursor.fetchall()

    def close(self):
        self.conn.close()
```

#### tools/document_parser.py（文档解析）

```python
from docx import Document

def parse_document(file_path: str) -> str:
    """根据文件类型解析文本内容"""
    if file_path.endswith('.txt') or file_path.endswith('.md'):
        with open(file_path, 'r', encoding='utf-8') as f:
            return f.read()
    elif file_path.endswith('.docx'):
        doc = Document(file_path)
        paragraphs = [p.text for p in doc.paragraphs if p.text.strip()]
        return "\n\n".join(paragraphs)
    else:
        raise ValueError(f"不支持的文件格式: {file_path}")
```

#### tools/document_generator.py（Word 文档生成）

```python
from docx import Document
from docx.shared import Pt, Inches
from docx.enum.text import WD_ALIGN_PARAGRAPH
import os
import re
from config import REPORT_OUTPUT_DIR

def generate_word_document(title: str, content: str,
                           club_id: int, activity_id: int) -> str:
    """
    将 Markdown 内容转为 Word 文档
    返回文件路径
    """
    doc = Document()

    # 标题
    heading = doc.add_heading(title, level=1)
    heading.alignment = WD_ALIGN_PARAGRAPH.CENTER

    # 解析 Markdown 内容
    lines = content.split('\n')
    for line in lines:
        line = line.strip()
        if not line:
            continue

        if line.startswith('## '):
            doc.add_heading(line[3:], level=2)
        elif line.startswith('### '):
            doc.add_heading(line[4:], level=3)
        elif line.startswith('- '):
            doc.add_paragraph(line[2:], style='List Bullet')
        elif line.startswith('1. ') or line.startswith('2. ') or line.startswith('3. '):
            doc.add_paragraph(line[3:], style='List Number')
        else:
            doc.add_paragraph(line)

    # 保存文件
    dir_path = os.path.join(REPORT_OUTPUT_DIR, str(club_id), str(activity_id))
    os.makedirs(dir_path, exist_ok=True)
    file_name = f"{title}.docx"
    file_path = os.path.join(dir_path, file_name)
    doc.save(file_path)

    return file_path
```

---

#### tools/compilation_generator.py（汇编文档生成）

```python
import json
from datetime import datetime
from tools.db_reader import DBReader
from tools.document_generator import generate_word_document

def generate_compilation(activity_id: int, activity_info: dict) -> dict:
    """
    根据活动下所有打卡记录，自动生成汇编文档
    返回 {"content": str, "member_count": int, "avg_rating": float,
           "problem_summary": str, "suggestion_summary": str}
    注意：此函数不调用 LLM，纯模板拼接
    """
    reader = DBReader()
    records = reader.get_checkin_records_for_compilation(activity_id)
    
    if not records:
        return None
    
    # 计算统计
    member_count = len(records)
    ratings = [r["rating"] for r in records if r["rating"]]
    avg_rating = round(sum(ratings) / len(ratings), 1) if ratings else 0
    
    # 构建汇编文档
    lines = []
    lines.append(f"《{activity_info['title']}》成员反馈汇编\n")
    
    # 一、基本信息
    lines.append("一、基本信息")
    lines.append(f"- 活动时间：{activity_info.get('start_time', '未设置')} ~ {activity_info.get('end_time', '未设置')}")
    lines.append(f"- 活动地点：{activity_info.get('location', '未设置')}")
    lines.append(f"- 打卡提交数：{member_count}")
    lines.append(f"- 平均评分：{avg_rating}/5\n")
    
    # 二、成员反馈明细
    lines.append("二、成员反馈明细\n")
    for i, record in enumerate(records, 1):
        name = record.get("real_name", "匿名")
        rating = record.get("rating", "未评分")
        lines.append(f"{i}. {name}（评分：{rating}/5）")
        
        if record.get("reflection"):
            lines.append(f"   【收获】{record['reflection']}")
        if record.get("problem"):
            lines.append(f"   【问题】{record['problem']}")
        if record.get("suggestion"):
            lines.append(f"   【建议】{record['suggestion']}")
        
        # 照片及图片描述
        photo_urls = json.loads(record.get("photo_urls") or "[]")
        image_descriptions = json.loads(record.get("image_descriptions") or "[]")
        if photo_urls:
            lines.append(f"   【附件】照片 {len(photo_urls)} 张")
            for j, desc in enumerate(image_descriptions):
                if desc:
                    lines.append(f"     - 照片{j+1}描述：{desc}")
        lines.append("")
    
    # 三、问题汇总（简单统计）
    problems = [r["problem"] for r in records if r.get("problem")]
    problem_summary = ""
    if problems:
        problem_summary = f"共 {len(problems)} 位成员提出问题"
        lines.append("三、问题汇总")
        for p in problems:
            lines.append(f"- {p}")
        lines.append("")
    else:
        lines.append("三、问题汇总\n本次无成员提出问题。\n")
    
    # 四、建议汇总
    suggestions = [r["suggestion"] for r in records if r.get("suggestion")]
    suggestion_summary = ""
    if suggestions:
        suggestion_summary = f"共 {len(suggestions)} 位成员提出建议"
        lines.append("四、建议汇总")
        for s in suggestions:
            lines.append(f"- {s}")
        lines.append("")
    else:
        lines.append("四、建议汇总\n本次无成员提出建议。\n")
    
    # 五、附件清单
    lines.append("五、附件清单")
    total_photos = sum(len(json.loads(r.get("photo_urls") or "[]")) for r in records)
    lines.append(f"- 照片共 {total_photos} 张\n")
    
    content = "\n".join(lines)
    
    return {
        "content": content,
        "member_count": member_count,
        "avg_rating": avg_rating,
        "problem_summary": problem_summary,
        "suggestion_summary": suggestion_summary
    }
```

---

#### tools/image_processor.py（图片多模态处理）

```python
import json
import logging
from typing import List, Optional
from config import MULTIMODAL_MODEL, MULTIMODAL_API_KEY, MULTIMODAL_BASE_URL

logger = logging.getLogger(__name__)

def describe_images(image_urls: List[str]) -> List[Optional[str]]:
    """
    批量生成图片描述文本
    返回与 image_urls 一一对应的描述列表，失败时对应位置为 None
    """
    descriptions = []
    for url in image_urls:
        try:
            desc = describe_single_image(url)
            descriptions.append(desc)
        except Exception as e:
            logger.warning(f"图片描述生成失败: {url}, 错误: {e}")
            descriptions.append(None)  # 降级：跳过
    return descriptions

def describe_single_image(image_url: str) -> str:
    """
    调用多模态 LLM 生成单张图片的描述
    """
    from openai import OpenAI
    client = OpenAI(api_key=MULTIMODAL_API_KEY, base_url=MULTIMODAL_BASE_URL)
    
    response = client.chat.completions.create(
        model=MULTIMODAL_MODEL,
        messages=[{
            "role": "user",
            "content": [
                {"type": "text", "text": "请简要描述这张图片的内容，重点关注：场景、人物活动、关键细节。50字以内。"},
                {"type": "image_url", "image_url": {"url": image_url}}
            ]
        }],
        max_tokens=100
    )
    return response.choices[0].message.content.strip()
```

---

### 1.6 数据模型

#### models/schemas.py

```python
from pydantic import BaseModel
from typing import List, Dict, Optional

# === 对话 ===
class ChatRequest(BaseModel):
    club_id: int
    session_id: Optional[str] = None
    message: str

class ChatResponse(BaseModel):
    session_id: str
    answer: str
    sources: List[Dict]

# === 成果报告 ===
class ReportGenerateRequest(BaseModel):
    activity_id: int

class ReportReviewRequest(BaseModel):
    action: str  # "approved" or "rejected"
    feedback: Optional[str] = None

class ReportResponse(BaseModel):
    report_id: int
    activity_id: int
    status: str
    title: Optional[str] = None
    content: Optional[str] = None
    file_path: Optional[str] = None

# === 经验参考 ===
class ExperienceRequest(BaseModel):
    club_id: int
    activity_type: str
    title: str
    location: Optional[str] = None
    max_participants: Optional[int] = None
    budget: Optional[float] = None

class ExperienceResponse(BaseModel):
    has_history: bool
    experience_summary: str
    suggestions: List[str]
    risk_warnings: List[str]
    template: Optional[Dict] = None

# === 知识库 ===
class KnowledgeDocResponse(BaseModel):
    id: str
    club_id: int
    doc_type: str
    title: str
    source_type: str
    chunk_count: int
    created_at: str
```

---

## 2. Java 后端新增模块设计

### 2.1 AiController

```java
@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    @Autowired
    private AiService aiService;

    // === 对话 ===
    @PostMapping("/chat")
    @RequirePermission("ai:chat")
    @OperationLog(module = "ai", operation = "AI对话")
    public R<ChatResponseVO> chat(@RequestBody @Valid ChatRequestDTO dto) {
        return R.ok(aiService.chat(dto));
    }

    // === 成果报告 ===
    @PostMapping("/report/generate")
    @RequirePermission("ai:report:manage")
    @OperationLog(module = "ai", operation = "生成成果报告")
    public R<ReportDraftVO> generateReport(@RequestBody @Valid ReportGenerateDTO dto) {
        return R.ok(aiService.generateReport(dto));
    }

    @GetMapping("/report/{activityId}")
    @RequirePermission("ai:report:manage")
    public R<ReportDraftVO> getReport(@PathVariable Long activityId) {
        return R.ok(aiService.getReport(activityId));
    }

    @PostMapping("/report/{id}/confirm")
    @RequirePermission("ai:report:manage")
    @OperationLog(module = "ai", operation = "确认报告")
    public R<Void> confirmReport(@PathVariable Long id) {
        aiService.confirmReport(id);
        return R.ok();
    }

    @PostMapping("/report/{id}/reject")
    @RequirePermission("ai:report:manage")
    @OperationLog(module = "ai", operation = "打回报告")
    public R<Void> rejectReport(@PathVariable Long id,
                                 @RequestBody ReportReviewDTO dto) {
        aiService.rejectReport(id, dto.getReason());
        return R.ok();
    }

    // === 经验参考 ===
    @PostMapping("/experience")
    @RequirePermission("ai:chat")
    public R<ExperienceSuggestionVO> getExperience(
            @RequestBody @Valid ExperienceRequestDTO dto) {
        return R.ok(aiService.getExperience(dto));
    }

    // === 知识库 ===
    @PostMapping("/knowledge/documents")
    @RequirePermission("ai:knowledge:manage")
    @OperationLog(module = "ai", operation = "上传知识库文档")
    public R<KnowledgeDocVO> uploadDocument(
            @RequestParam MultipartFile file,
            @RequestParam String docType,
            @RequestParam String title) {
        return R.ok(aiService.uploadKnowledgeDocument(file, docType, title));
    }

    @GetMapping("/knowledge/documents")
    @RequirePermission("ai:knowledge:manage")
    public R<PageResult<KnowledgeDocVO>> listDocuments(
            @RequestParam Long clubId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(aiService.listKnowledgeDocuments(clubId, pageNum, pageSize));
    }

    @DeleteMapping("/knowledge/documents/{id}")
    @RequirePermission("ai:knowledge:manage")
    @OperationLog(module = "ai", operation = "删除知识库文档")
    public R<Void> deleteDocument(@PathVariable Long id) {
        aiService.deleteKnowledgeDocument(id);
        return R.ok();
    }
}
```

### 2.2 AiService 接口

```java
public interface AiService {
    ChatResponseVO chat(ChatRequestDTO dto);
    ReportDraftVO generateReport(ReportGenerateDTO dto);
    ReportDraftVO getReport(Long activityId);
    void confirmReport(Long id);
    void rejectReport(Long id, String reason);
    ExperienceSuggestionVO getExperience(ExperienceRequestDTO dto);
    KnowledgeDocVO uploadKnowledgeDocument(MultipartFile file, String docType, String title);
    PageResult<KnowledgeDocVO> listKnowledgeDocuments(Long clubId, int pageNum, int pageSize);
    void deleteKnowledgeDocument(Long id);
}
```

### 2.3 AiServiceImpl 核心逻辑

```java
@Service
public class AiServiceImpl implements AiService {

    @Autowired
    private AiConfig aiConfig;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ActivityReportMapper reportMapper;

    @Autowired
    private KnowledgeDocumentMapper knowledgeDocMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public ChatResponseVO chat(ChatRequestDTO dto) {
        // 1. 转发到 Python 服务
        String url = aiConfig.getBaseUrl() + "/chat";
        Map<String, Object> body = Map.of(
            "club_id", dto.getClubId(),
            "session_id", dto.getSessionId(),
            "message", dto.getMessage()
        );
        // 2. HTTP 调用，处理超时和异常
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, body, Map.class);
            // 3. 转换为 VO 返回
            return convertToChatVO(response.getBody());
        } catch (ResourceAccessException e) {
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        }
    }

    @Override
    public ReportDraftVO generateReport(ReportGenerateDTO dto) {
        // 1. 校验活动状态
        Activity activity = activityMapper.selectById(dto.getActivityId());
        if (!"COMPLETED".equals(activity.getStatus())) {
            throw new BusinessException(ErrorCode.ACTIVITY_NOT_COMPLETED);
        }
        // 2. 校验打卡数据
        int checkinCount = checkinRecordMapper.countByActivityId(dto.getActivityId());
        if (checkinCount == 0) {
            throw new BusinessException(ErrorCode.NO_CHECKIN_DATA);
        }
        // 3. 调用 Python 服务
        String url = aiConfig.getBaseUrl() + "/report/generate";
        // 4. 创建报告记录（状态 DRAFT）
        // 5. 返回
        ...
    }

    @Override
    public void confirmReport(Long id) {
        AiActivityReport report = reportMapper.selectById(id);
        if (report == null) {
            throw new NotFoundException(ErrorCode.AI_REPORT_NOT_FOUND);
        }
        if ("CONFIRMED".equals(report.getStatus())) {
            throw new BusinessException(ErrorCode.AI_REPORT_ALREADY_CONFIRMED);
        }
        // 更新状态
        report.setStatus("CONFIRMED");
        report.setConfirmedBy(SecurityUtil.getCurrentUserId());
        report.setConfirmedAt(LocalDateTime.now());
        reportMapper.updateById(report);

        // 异步触发知识库入库
        ingestReportToKnowledgeBase(report);
    }

    @Async
    protected void ingestReportToKnowledgeBase(AiActivityReport report) {
        // 调用 Python 服务将报告内容入库 ChromaDB
        String url = aiConfig.getBaseUrl() + "/knowledge/ingest-report";
        Map<String, Object> body = Map.of(
            "club_id", report.getClubId(),
            "activity_id", report.getActivityId(),
            "title", report.getTitle(),
            "content", report.getContent()
        );
        restTemplate.postForEntity(url, body, Map.class);

        // 更新入库状态
        report.setIsIngested(1);
        report.setIngestedAt(LocalDateTime.now());
        reportMapper.updateById(report);
    }
}
```

---

## 3. 前端模块设计

### 3.1 打卡表单（成员端）

嵌入在活动详情页，成员签到后才能看到并提交。

```vue
<template>
  <div class="checkin-form">
    <el-card>
      <template #header>
        <span>活动打卡 - {{ activityInfo.title }}</span>
        <el-tag v-if="taskInfo.deadline" type="warning">
          截止：{{ taskInfo.deadline }}
        </el-tag>
      </template>
      <el-form :model="form" :rules="rules" ref="formRef">
        <!-- 活动评分 -->
        <el-form-item label="活动评分" prop="rating">
          <el-rate v-model="form.rating" :max="5" show-text
                   :texts="['很差', '较差', '一般', '较好', '很好']" />
        </el-form-item>

        <!-- 我的收获（必填/选填根据任务配置） -->
        <el-form-item label="我的收获" prop="reflection">
          <el-input v-model="form.reflection" type="textarea"
                    :rows="4" maxlength="2000" show-word-limit
                    placeholder="分享你在这次活动中的收获和感悟..." />
        </el-form-item>

        <!-- 遇到的问题（选填） -->
        <el-form-item label="遇到的问题">
          <el-input v-model="form.problem" type="textarea"
                    :rows="3" maxlength="1000" show-word-limit
                    placeholder="活动中体验不好的地方、遇到的困难等（选填）" />
        </el-form-item>

        <!-- 改进建议（根据任务配置决定是否必填） -->
        <el-form-item label="改进建议" prop="suggestion">
          <el-input v-model="form.suggestion" type="textarea"
                    :rows="3" maxlength="1000" show-word-limit
                    placeholder="对下次活动的建议，怎么办得更好..." />
        </el-form-item>

        <!-- 照片上传（根据任务配置决定是否必填） -->
        <el-form-item label="照片上传">
          <el-upload
            :action="uploadUrl"
            :headers="uploadHeaders"
            :data="{ type: 'photo' }"
            :on-success="handlePhotoSuccess"
            :file-list="photoList"
            list-type="picture-card"
            :limit="3"
            accept="image/*"
          >
            <el-icon><Plus /></el-icon>
            <template #tip>
              <div class="el-upload__tip">最多 3 张，单张不超过 5MB</div>
            </template>
          </el-upload>
        </el-form-item>

        <!-- 提交按钮 -->
        <el-form-item>
          <el-button type="primary" @click="submitCheckin" :loading="submitting">
            提交打卡
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
// Props: activityId, taskInfo (包含 requireReflection/requirePhoto/requireSuggestion)
// 调用 POST /api/v1/checkin/records
// 提交成功后显示“已打卡”状态，可查看自己的打卡记录
</script>
```

### 3.2 打卡任务管理页（团长端）

团长查看活动打卡情况，可生成汇编文档。

```vue
<template>
  <div class="checkin-manage">
    <!-- 打卡任务配置 -->
    <el-card v-if="!task">
      <el-button type="primary" @click="showCreateDialog = true">
        创建打卡任务
      </el-button>
    </el-card>

    <!-- 打卡统计 -->
    <el-card v-else>
      <template #header>
        <div class="header">
          <span>打卡任务管理</span>
          <el-tag type="info">截止：{{ task.deadline }}</el-tag>
        </div>
      </template>
      <el-row :gutter="20">
        <el-col :span="6">
          <el-statistic title="应打卡人数" :value="task.requireCount" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="已提交" :value="task.submittedCount" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="平均评分" :value="task.avgRating" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="汇编状态">
            <template #default>
              <el-tag :type="compilation ? 'success' : 'info'">
                {{ compilation ? '已生成' : '未生成' }}
              </el-tag>
            </template>
          </el-statistic>
        </el-col>
      </el-row>

      <!-- 操作区 -->
      <div class="actions" style="margin-top: 20px">
        <el-button @click="fetchRecords">查看打卡列表</el-button>
        <el-button v-if="!compilation && isDeadlinePassed"
                   type="primary" @click="generateCompilation">
          生成汇编文档
        </el-button>
        <el-button v-if="compilation"
                   type="success" @click="viewCompilation">
          查看汇编文档
        </el-button>
        <el-button v-if="compilation"
                   @click="downloadCompilation">
          下载 Word
        </el-button>
      </div>
    </el-card>

    <!-- 打卡记录列表 -->
    <el-card v-if="records.length" style="margin-top: 20px">
      <el-table :data="records">
        <el-table-column prop="realName" label="成员" />
        <el-table-column prop="rating" label="评分" width="80" />
        <el-table-column prop="reflection" label="收获" show-overflow-tooltip />
        <el-table-column prop="problem" label="问题" show-overflow-tooltip />
        <el-table-column prop="suggestion" label="建议" show-overflow-tooltip />
        <el-table-column label="照片" width="80">
          <template #default="{ row }">
            {{ row.photoUrls?.length || 0 }}张
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button v-if="row.suggestion && !row.isAdopted"
                       size="small" @click="adoptSuggestion(row.id)">
              采纳
            </el-button>
            <el-tag v-if="row.isAdopted" type="success" size="small">已采纳</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 创建打卡任务对话框 -->
    <el-dialog v-model="showCreateDialog" title="创建打卡任务">
      <el-form :model="createForm">
        <el-form-item label="截止时间">
          <el-date-picker v-model="createForm.deadline" type="datetime" />
        </el-form-item>
        <el-form-item label="必填项">
          <el-checkbox v-model="createForm.requireReflection">心得感悟</el-checkbox>
          <el-checkbox v-model="createForm.requirePhoto">照片上传</el-checkbox>
          <el-checkbox v-model="createForm.requireSuggestion">改进建议</el-checkbox>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="createTask">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
// Props: activityId
// 调用 POST /api/v1/checkin/tasks (创建任务)
// 调用 GET /api/v1/checkin/records/{activityId} (查看打卡列表)
// 调用 POST /api/v1/checkin/compilation/generate/{activityId} (生成汇编)
// 调用 GET /api/v1/checkin/compilation/{activityId} (查看汇编)
// 调用 PATCH /api/v1/checkin/records/{id}/adopt (采纳建议)
</script>
```

### 3.3 汇编文档查看页

查看系统自动汇编的成员反馈文档。

```vue
<template>
  <div class="compilation-view" v-if="compilation">
    <el-card>
      <template #header>
        <div class="header">
          <span>成员反馈汇编</span>
          <div>
            <el-tag type="info">{{ compilation.memberCount }} 人参与</el-tag>
            <el-tag type="warning">平均评分 {{ compilation.avgRating }}/5</el-tag>
          </div>
        </div>
      </template>

      <!-- 汇编内容（Markdown 渲染） -->
      <div class="content" v-html="renderedContent"></div>

      <!-- 汇总信息 -->
      <el-divider />
      <el-descriptions :column="2" border>
        <el-descriptions-item label="问题汇总">
          {{ compilation.problemSummary || '无' }}
        </el-descriptions-item>
        <el-descriptions-item label="建议汇总">
          {{ compilation.suggestionSummary || '无' }}
        </el-descriptions-item>
        <el-descriptions-item label="入库状态">
          <el-tag :type="compilation.isIngested ? 'success' : 'info'">
            {{ compilation.isIngested ? '已入库' : '未入库' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="生成时间">
          {{ compilation.createdAt }}
        </el-descriptions-item>
      </el-descriptions>

      <!-- 操作 -->
      <div class="actions" style="margin-top: 20px">
        <el-button @click="downloadFile">下载 Word</el-button>
        <el-button type="primary" @click="goToReport" v-if="isManager">
          基于汇编生成报告
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
// Props: activityId
// 调用 GET /api/v1/checkin/compilation/{activityId}
// 下载：GET /api/v1/checkin/compilation/{activityId}/download
// 跳转报告页：router.push({ path: '/ai/report', query: { activityId } })
</script>
```

### 3.4 积分流水页

```vue
<template>
  <div class="points-page">
    <!-- 积分汇总 -->
    <el-row :gutter="20" style="margin-bottom: 20px">
      <el-col :span="8">
        <el-card>
          <el-statistic title="我的总积分" :value="summary.totalPoints" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card>
          <el-statistic title="我的排名" :value="summary.ranking" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card>
          <el-statistic title="本月获得" :value="summary.monthPoints" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 积分流水列表 -->
    <el-card>
      <el-table :data="logs">
        <el-table-column prop="description" label="说明" />
        <el-table-column prop="points" label="积分" width="80">
          <template #default="{ row }">
            <span :style="{ color: row.points > 0 ? '#67c23a' : '#f56c6c' }">
              {{ row.points > 0 ? '+' : '' }}{{ row.points }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="reason" label="类型" width="120">
          <template #default="{ row }">
            <el-tag size="small">{{ reasonMap[row.reason] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="时间" width="180" />
      </el-table>
      <el-pagination v-model:current-page="pageNum" :total="total"
                     @current-change="fetchLogs" />
    </el-card>

    <!-- 积分排行榜 -->
    <el-card style="margin-top: 20px">
      <template #header>社团积分排行榜</template>
      <el-table :data="rankingList">
        <el-table-column type="index" label="排名" width="60" />
        <el-table-column prop="realName" label="姓名" />
        <el-table-column prop="totalPoints" label="总积分" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
// Props: clubId
// 调用 GET /api/v1/points/summary (汇总)
// 调用 GET /api/v1/points/my (流水)
// 调用 GET /api/v1/points/ranking (排行榜)
</script>
```

### 3.5 对话窗口组件 (chat.vue)

```vue
<template>
  <div class="chat-container">
    <!-- 消息列表 -->
    <div class="message-list" ref="messageListRef">
      <div v-for="msg in messages" :key="msg.id" :class="['message', msg.role]">
        <div class="bubble">
          <div class="content" v-html="msg.content"></div>
          <!-- 引用来源 -->
          <div v-if="msg.sources?.length" class="sources">
            <el-collapse>
              <el-collapse-item title="参考来源">
                <div v-for="src in msg.sources" :key="src.doc_id" class="source-item">
                  <span class="source-title">{{ src.title }}</span>
                  <span class="source-snippet">{{ src.snippet }}</span>
                </div>
              </el-collapse-item>
            </el-collapse>
          </div>
        </div>
      </div>
    </div>
    <!-- 输入区域 -->
    <div class="input-area">
      <el-input v-model="inputMessage" placeholder="输入问题..."
                @keyup.enter="sendMessage" :disabled="loading" />
      <el-button type="primary" @click="sendMessage" :loading="loading">
        发送
      </el-button>
    </div>
  </div>
</template>

<script setup>
// Props: clubId
// 管理 sessionId、messages 列表
// 调用 POST /api/v1/ai/chat
</script>
```

### 3.6 成果报告审核页 (report.vue)

```vue
<template>
  <div class="report-review" v-if="report">
    <el-card>
      <template #header>
        <div class="header">
          <span>{{ report.title }}</span>
          <el-tag :type="statusType">{{ statusText }}</el-tag>
        </div>
      </template>
      <!-- 报告内容（Markdown 渲染） -->
      <div class="content" v-html="renderedContent"></div>
      <!-- 统计信息 -->
      <div class="stats">
        <el-statistic title="平均评分" :value="report.avgRating" />
        <el-statistic title="参与人数" :value="report.participantCount" />
        <el-statistic title="打卡数" :value="report.checkinCount" />
      </div>
      <!-- 操作按钮 -->
      <div class="actions" v-if="report.status === 'DRAFT'">
        <el-button @click="downloadFile">下载 Word</el-button>
        <el-button type="primary" @click="confirmReport">确认入库</el-button>
        <el-button type="danger" @click="showRejectDialog = true">打回修改</el-button>
      </div>
    </el-card>
    <!-- 打回对话框 -->
    <el-dialog v-model="showRejectDialog" title="打回修改">
      <el-input v-model="rejectReason" type="textarea" placeholder="请输入修改意见" />
      <template #footer>
        <el-button @click="showRejectDialog = false">取消</el-button>
        <el-button type="primary" @click="submitReject">确认打回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
// Props: activityId
// 调用 GET /api/v1/ai/report/{activityId}
// 确认：POST /api/v1/ai/report/{id}/confirm
// 打回：POST /api/v1/ai/report/{id}/reject
</script>
```

### 3.7 知识库管理页 (knowledge.vue)

```vue
<template>
  <div class="knowledge-management">
    <!-- 上传区域 -->
    <el-card class="upload-card">
      <el-upload
        :action="uploadUrl"
        :headers="uploadHeaders"
        :data="{ docType: uploadForm.docType, title: uploadForm.title }"
        :on-success="handleUploadSuccess"
        accept=".txt,.md,.docx"
        :limit="1"
      >
        <el-button type="primary">上传文档</el-button>
      </el-upload>
      <el-form :inline="true" style="margin-top: 10px">
        <el-form-item label="文档类型">
          <el-select v-model="uploadForm.docType">
            <el-option label="社团章程" value="charter" />
            <el-option label="招新说明" value="recruitment" />
            <el-option label="常见问答" value="faq" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="uploadForm.title" />
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 已入库文档列表 -->
    <el-card style="margin-top: 20px">
      <el-table :data="documents">
        <el-table-column prop="title" label="标题" />
        <el-table-column prop="docType" label="类型">
          <template #default="{ row }">
            <el-tag>{{ docTypeMap[row.docType] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sourceType" label="来源" />
        <el-table-column prop="chunkCount" label="分块数" />
        <el-table-column prop="createdAt" label="入库时间" />
        <el-table-column label="操作">
          <template #default="{ row }">
            <el-button type="danger" size="small" @click="deleteDoc(row.id)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="pageNum" :total="total"
                     @current-change="fetchDocuments" />
    </el-card>
  </div>
</template>

<script setup>
// Props: clubId
// 调用 GET /api/v1/ai/knowledge/documents
// 上传：POST /api/v1/ai/knowledge/documents (multipart)
// 删除：DELETE /api/v1/ai/knowledge/documents/{id}
</script>
```
