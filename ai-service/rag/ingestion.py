import re
import logging
import threading
from typing import List, Dict
from langchain_text_splitters import RecursiveCharacterTextSplitter
from config import CHUNK_SIZE, CHUNK_OVERLAP

logger = logging.getLogger(__name__)

_embedding_model = None
_embedding_model_lock = threading.Lock()

def get_embedding_model():
    global _embedding_model
    if _embedding_model is None:
        with _embedding_model_lock:
            if _embedding_model is None:  # 双重检查锁定
                import os
                # 必须在导入 sentence_transformers 之前设置，否则无效
                from sentence_transformers import SentenceTransformer
                from config import EMBEDDING_MODEL, EMBEDDING_DEVICE
                logger.info(f"Loading embedding model: {EMBEDDING_MODEL}")
                _embedding_model = SentenceTransformer(EMBEDDING_MODEL, device=EMBEDDING_DEVICE)
    return _embedding_model

def chunk_by_doc_type(doc_type: str, content: str) -> List[Dict]:
    if doc_type == "checkin_compilation":
        return chunk_compilation_by_section(content)
    elif doc_type == "faq":
        return chunk_faq_by_qa(content)
    elif doc_type in ("charter", "activity_report"):
        return chunk_by_markdown_sections(content)
    else:
        return chunk_by_fixed_size(content)

def chunk_by_fixed_size(content: str) -> List[Dict]:
    splitter = RecursiveCharacterTextSplitter(
        chunk_size=CHUNK_SIZE,
        chunk_overlap=CHUNK_OVERLAP,
        separators=["\n\n", "\n", "。", "！", "？", ".", " "]
    )
    chunks = splitter.split_text(content)
    return [{"text": c, "metadata_addition": {}} for c in chunks]

def chunk_by_markdown_sections(content: str) -> List[Dict]:
    sections = re.split(r'(?=^## )', content, flags=re.MULTILINE)
    result = []
    for section in sections:
        section = section.strip()
        if not section:
            continue
        if len(section) > 1500:
            for chunk in chunk_by_fixed_size(section):
                result.append(chunk)
        else:
            result.append({"text": section, "metadata_addition": {}})
    return result

def chunk_faq_by_qa(content: str) -> List[Dict]:
    pairs = re.split(r'(?=^Q[\d]*[\u3001.．\s])', content, flags=re.MULTILINE)
    result = []
    for pair in pairs:
        pair = pair.strip()
        if pair:
            result.append({"text": pair, "metadata_addition": {}})
    return result

def chunk_compilation_by_section(content: str) -> List[Dict]:
    result = []
    reflections = extract_section(content, "收获")
    problems = extract_section(content, "问题")
    suggestions = extract_section(content, "建议")
    general = extract_general_info(content)

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
    pattern = rf'【{re.escape(dimension)}】(.*?)(?=【|\n\n\d+\.\d+|\Z)'
    matches = re.findall(pattern, content, re.DOTALL)
    if matches:
        return f"【{dimension}汇总】\n" + "\n".join(f"- {m.strip()}" for m in matches if m.strip())
    return ""

def extract_general_info(content: str) -> str:
    sections = []
    match = re.search(r'一、基本信息\n(.*?)(?=二、)', content, re.DOTALL)
    if match:
        sections.append("基本信息\n" + match.group(1).strip())
    for title in ["问题汇总", "建议汇总"]:
        match = re.search(rf'{title}\n(.*?)(?=\n[四五六]、|$)', content, re.DOTALL)
        if match:
            sections.append(f"{title}\n" + match.group(1).strip())
    return "\n\n".join(sections) if sections else ""

def ingest_document(collection: str, doc_id: str, content: str,
                    metadata: Dict) -> int:
    doc_type = metadata.get("doc_type", "")
    chunk_results = chunk_by_doc_type(doc_type, content)
    if not chunk_results:
        return 0

    chunks = [r["text"] for r in chunk_results]
    chunk_ids = [f"{doc_id}_chunk_{i}" for i in range(len(chunks))]
    chunk_metadatas = [{**metadata, **r["metadata_addition"], "chunk_index": i}
                       for i, r in enumerate(chunk_results)]

    from store.chroma_store import ChromaVectorStore
    store = ChromaVectorStore()
    store.add_documents(collection, chunks, chunk_metadatas, chunk_ids)
    return len(chunks)
