import os
import json
import logging
import uuid
import datetime
import pymysql
from fastapi import APIRouter, HTTPException, UploadFile, File, Form
from typing import Optional
from tools.document_parser import parse_document, resolve_document_path
from rag.knowledge_base import add_document, delete_document, count_documents, get_collection_name
from tools.db_reader import DBReader
from config import UPLOAD_DIR

logger = logging.getLogger(__name__)
router = APIRouter()

@router.post("/documents")
async def upload_document(
    file: UploadFile = File(...),
    doc_type: str = Form(...),
    title: str = Form(...),
    club_id: int = Form(1)
):
    try:
        # 校验文件类型
        if not file.filename.endswith(('.txt', '.md', '.docx')):
            raise HTTPException(status_code=400, detail="仅支持 txt/md/docx 格式")

        # 安全化文件名（移除路径分隔符和特殊字符
        safe_filename = os.path.basename(file.filename).replace(" ", "_")

        # 保存文件
        file_id = str(uuid.uuid4())
        file_path = os.path.join(UPLOAD_DIR, f"{file_id}_{safe_filename}")
        os.makedirs(os.path.dirname(file_path), exist_ok=True)

        content = await file.read()
        with open(file_path, "wb") as f:
            f.write(content)

        # 解析文件
        text_content = parse_document(file_path)

        # 入库 ChromaDB
        doc_id, chunk_count = add_document(
            club_id=club_id,
            doc_type=doc_type,
            title=title,
            content=text_content,
            source="upload"
        )

        # 保存元数据到 MySQL
        with DBReader() as reader:
            with reader.conn.cursor() as cursor:
                cursor.execute("""
                    INSERT INTO ai_knowledge_document
                    (club_id, doc_type, title, source_type, file_path, chunk_count, chroma_ids, status)
                    VALUES (%s, %s, %s, 'upload', %s, %s, %s, 1)
                """, (club_id, doc_type, title, file_path, chunk_count,
                      json.dumps([f"{doc_id}_chunk_{i}" for i in range(chunk_count)])))
                reader.conn.commit()
                doc_db_id = cursor.lastrowid

        return {
            "id": doc_db_id,
            "club_id": club_id,
            "doc_type": doc_type,
            "title": title,
            "source_type": "upload",
            "chunk_count": chunk_count,
            "created_at": datetime.datetime.now().isoformat()
        }
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Upload document error: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail="服务内部错误，请稍后重试")

@router.get("/documents")
async def list_documents(club_id: int = 1, page_num: int = 1, page_size: int = 20):
    try:
        with DBReader() as reader:
            with reader.conn.cursor(pymysql.cursors.DictCursor) as cursor:
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
                total = cursor.fetchone()["total"]

                return {
                    "records": [
                        {
                            "id": d["id"],
                            "doc_type": d["doc_type"],
                            "title": d["title"],
                            "source_type": d["source_type"],
                            "chunk_count": d["chunk_count"],
                            "created_at": str(d["created_at"])
                        } for d in docs
                    ],
                    "total": total,
                    "page_num": page_num,
                    "page_size": page_size
                }
    except Exception as e:
        logger.error(f"List documents error: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail="服务内部错误，请稍后重试")

@router.delete("/documents/{doc_id}")
async def delete_document_endpoint(doc_id: int):
    try:
        with DBReader() as reader:
            with reader.conn.cursor() as cursor:
                cursor.execute("SELECT * FROM ai_knowledge_document WHERE id = %s AND deleted = 0", (doc_id,))
                doc = cursor.fetchone()
                if not doc:
                    raise HTTPException(status_code=404, detail="文档不存在")
                ids = json.loads(doc.get("chroma_ids") or "[]")
                delete_document(doc["club_id"], ids)
                cursor.execute("""
                    UPDATE ai_knowledge_document
                    SET status = 0
                    WHERE id = %s
                """, (doc_id,))
                reader.conn.commit()
            return {"message": "deleted"}
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Delete document error: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail="服务内部错误，请稍后重试")

@router.post("/ingest-report")
async def ingest_report(request: dict):
    """将已确认的成果报告入库到 ChromaDB 和 MySQL"""
    try:
        club_id = request["club_id"]
        activity_id = request["activity_id"]
        title = request["title"]
        content = request["content"]

        # 写入 ChromaDB
        doc_id, chunk_count = add_document(
            club_id=club_id,
            doc_type="activity_report",
            title=title,
            content=content,
            source="system",
            source_id=activity_id
        )

        # 写入 MySQL
        with DBReader() as reader:
            with reader.conn.cursor() as cursor:
                cursor.execute("""
                    INSERT INTO ai_knowledge_document 
                    (club_id, doc_type, title, source_type, source_id, chunk_count, chroma_ids, status)
                    VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
                """, (
                    club_id,
                    "activity_report",
                    title,
                    "system",
                    activity_id,
                    chunk_count,
                    json.dumps([f"{doc_id}_chunk_{i}" for i in range(chunk_count)]),
                    1
                ))
                reader.conn.commit()
                mysql_doc_id = cursor.lastrowid

        return {
            "doc_id": doc_id,
            "mysql_id": mysql_doc_id,
            "chunk_count": chunk_count,
            "message": "报告已入库"
        }
    except Exception as e:
        logger.error(f"Ingest report error: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail="服务内部错误，请稍后重试")

@router.post("/documents/ingest")
async def ingest_document(request: dict):
    """Java 端已上传文件，传入文件路径进行解析和入库"""
    try:
        club_id = request["club_id"]
        doc_type = request["doc_type"]
        title = request["title"]
        file_path = request["file_path"]

        # file_path 可能是 URL 格式，转为本地路径
        if file_path.startswith("/api/v1/files/"):
            # URL 格式: /api/v1/files/knowledge/1/xxx.txt
            relative = file_path.replace("/api/v1/files/", "")
            # 路径安全：禁止路径遍历
            if ".." in relative or relative.startswith("/"):
                raise HTTPException(status_code=400, detail="非法文件路径")
            # 限制只允许 knowledge 子目录
            if not relative.startswith("knowledge/"):
                raise HTTPException(status_code=400, detail="仅允许访问 knowledge 目录")
            project_root = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
            local_path = os.path.normpath(os.path.join(project_root, "data", "uploads", relative))
            # 二次验证：确保解析后仍在允许目录内
            allowed_base = os.path.normpath(os.path.join(project_root, "data", "uploads", "knowledge"))
            if not local_path.startswith(allowed_base):
                raise HTTPException(status_code=400, detail="非法文件路径")
            if os.path.exists(local_path):
                file_path = local_path
            else:
                local_path2 = os.path.normpath(os.path.join(os.path.dirname(__file__), "..", "data", "uploads", relative))
                if not local_path2.startswith(allowed_base):
                    raise HTTPException(status_code=400, detail="非法文件路径")
                if os.path.exists(local_path2):
                    file_path = local_path2

        if not os.path.exists(file_path):
            raise HTTPException(status_code=400, detail=f"文件不存在")

        # 文件大小限制（防止恶意大文件解析）
        if os.path.getsize(file_path) > 20 * 1024 * 1024:
            raise HTTPException(status_code=400, detail="文件过大（超过20MB限制）")

        text_content = parse_document(file_path)
        doc_id, chunk_count = add_document(
            club_id=club_id,
            doc_type=doc_type,
            title=title,
            content=text_content,
            source="upload"
        )

        # 保存元数据到 MySQL
        with DBReader() as reader:
            with reader.conn.cursor() as cursor:
                cursor.execute("""
                    INSERT INTO ai_knowledge_document
                    (club_id, doc_type, title, source_type, file_path, chunk_count, chroma_ids, status)
                    VALUES (%s, %s, %s, 'upload', %s, %s, %s, 1)
                """, (club_id, doc_type, title, file_path, chunk_count,
                      json.dumps([f"{doc_id}_chunk_{i}" for i in range(chunk_count)])))
                reader.conn.commit()
                doc_db_id = cursor.lastrowid

        return {
            "id": doc_db_id,
            "chunk_count": chunk_count,
            "created_at": datetime.datetime.now().isoformat()
        }
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Ingest document error: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail="服务内部错误，请稍后重试")


# ========== 管理端点 ==========

@router.post("/admin/cleanup")
async def cleanup_collections():
    """清理所有 ChromaDB 集合（用于数据重建）"""
    from store.chroma_store import ChromaVectorStore
    store = ChromaVectorStore()
    deleted = []
    for club_id in range(1, 11):
        collection = get_collection_name(club_id)
        try:
            store.delete_collection(collection)
            deleted.append(collection)
        except:
            pass
    try:
        store.delete_collection("semantic_cache")
        deleted.append("semantic_cache")
    except:
        pass
    return {"deleted": deleted, "message": f"已清理 {len(deleted)} 个集合"}


@router.post("/admin/ingest-all")
async def ingest_all_documents():
    """将 MySQL 中所有 ai_knowledge_document 记录向量化入库"""
    from rag.ingestion import ingest_document
    from store.chroma_store import ChromaVectorStore
    store = ChromaVectorStore()
    with DBReader() as reader:
        with reader.conn.cursor(pymysql.cursors.DictCursor) as cursor:
            cursor.execute("""
                SELECT * FROM ai_knowledge_document
                WHERE status = 1 AND deleted = 0
                ORDER BY club_id, doc_type
            """)
            docs = cursor.fetchall()

        results = []
        for doc in docs:
            club_id = doc["club_id"]
            doc_type = doc["doc_type"]
            title = doc["title"]
            source_id = doc.get("source_id")
            doc_id = f"doc_{doc['id']}"
            collection = get_collection_name(club_id)

            # 获取内容
            content = None
            file_path = doc.get("file_path")
            if file_path and resolve_document_path(file_path).is_file():
                content = parse_document(file_path)
            if not content and doc_type == "activity_report" and source_id:
                with reader.conn.cursor(pymysql.cursors.DictCursor) as cursor:
                    cursor.execute("SELECT content FROM ai_activity_report WHERE activity_id = %s AND deleted = 0", (source_id,))
                    row = cursor.fetchone()
                    content = row["content"] if row else None
            elif not content and doc_type == "checkin_compilation" and source_id:
                with reader.conn.cursor(pymysql.cursors.DictCursor) as cursor:
                    cursor.execute("""
                        SELECT cc.content FROM act_checkin_compilation cc
                        JOIN act_checkin_task ct ON cc.task_id = ct.id
                        WHERE ct.activity_id = %s AND cc.deleted = 0
                    """, (source_id,))
                    row = cursor.fetchone()
                    if row:
                        content = row["content"]
                    else:
                        cursor.execute("SELECT content FROM act_checkin_compilation WHERE task_id = %s AND deleted = 0", (source_id,))
                        row = cursor.fetchone()
                        content = row["content"] if row else None

            # 演示库缺少原始附件时，只使用数据库事实与已实现的操作说明。
            if not content and doc_type == "charter":
                club = reader.get_club_detail(club_id)
                content = (f"# {title}（本地演示资料）\n\n"
                           f"## 社团基本信息\n{json.dumps(club, ensure_ascii=False, default=str)}\n\n"
                           "## 操作说明\n社团资料、负责人和成员数量以社团详情页为准。"
                           "本演示资料不包含学校正式章程，具体制度请上传正式文件。")
            elif not content and doc_type == "faq":
                content = (f"# {title}（本地系统操作说明）\n\n"
                           "Q1、如何加入社团？\n登录系统，进入社团详情，点击加入社团；不能重复加入。\n\n"
                           "Q2、如何参加活动？\n在活动管理中查看活动，选择开放报名的活动提交报名。"
                           "报名状态、截止时间和剩余名额以活动详情页为准。\n\n"
                           "Q3、打卡有什么内容？\n提交活动评分、心得、问题、建议及相关附件。\n\n"
                           "Q4、如何查看积分？\n在积分页面查看个人记录和社团排行榜。\n\n"
                           "Q5、成果报告如何生成？\n活动完成并有打卡数据和汇编后，负责人生成报告草稿，"
                           "审核确认后可作为历史经验检索；打回后可重新生成。")
            if not content:
                results.append({"title": title, "status": "skipped", "reason": "no source content"})
                continue

            metadata = {
                "club_id": club_id, "doc_type": doc_type,
                "title": title, "source": doc["source_type"],
            }
            if source_id:
                metadata["source_id"] = source_id

            try:
                old_ids = json.loads(doc.get("chroma_ids") or "[]")
                if old_ids:
                    store.delete_documents(collection, old_ids)
                chunk_count = ingest_document(collection, doc_id, content, metadata)
                ids = [f"{doc_id}_chunk_{i}" for i in range(chunk_count)]
                with reader.conn.cursor() as cursor:
                    cursor.execute("UPDATE ai_knowledge_document SET chunk_count = %s, chroma_ids = %s WHERE id = %s",
                                   (chunk_count, json.dumps(ids), doc["id"]))
                    reader.conn.commit()
                results.append({"title": title, "status": "ok", "chunks": chunk_count, "collection": collection})
            except Exception as e:
                results.append({"title": title, "status": "error", "error": str(e)})

        # 统计
        collection_counts = {}
        from rag.knowledge_base import invalidate_knowledge_cache
        for club_id in sorted({d["club_id"] for d in docs}):
            invalidate_knowledge_cache(club_id)
            cname = get_collection_name(club_id)
            collection_counts[cname] = store.count(cname)

        return {
            "total_docs": len(docs),
            "results": results,
            "collection_counts": collection_counts
        }
