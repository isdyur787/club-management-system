"""
将 MySQL ai_knowledge_document 中的记录向量化入库 ChromaDB

使用方式：
    cd ai-service
    python -m scripts.ingest_test_data

说明：
    1. 清理旧的 ChromaDB 数据（club_1 ~ club_10）
    2. 从 MySQL 读取 ai_knowledge_document 元数据
    3. 获取实际内容（activity_report 从 ai_activity_report，compilation 从 act_checkin_compilation）
    4. 对 charter/faq 类型生成合成内容
    5. 向量化入库 ChromaDB
"""
import sys
import os
import logging

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import pymysql
from config import (MYSQL_HOST, MYSQL_PORT, MYSQL_USER, MYSQL_PASSWORD,
                    MYSQL_DATABASE, CHROMA_PERSIST_DIR)
from rag.ingestion import ingest_document, get_embedding_model
from rag.knowledge_base import get_collection_name
from store.chroma_store import ChromaVectorStore

logging.basicConfig(level=logging.INFO, format='%(asctime)s %(levelname)s %(message)s', force=True)
logger = logging.getLogger(__name__)


def get_connection():
    return pymysql.connect(
        host=MYSQL_HOST, port=MYSQL_PORT,
        user=MYSQL_USER, password=MYSQL_PASSWORD,
        database=MYSQL_DATABASE, charset="utf8mb4",
        cursorclass=pymysql.cursors.DictCursor
    )


def get_knowledge_documents(conn):
    """获取所有待入库的知识文档"""
    with conn.cursor() as cursor:
        cursor.execute("""
            SELECT * FROM ai_knowledge_document
            WHERE status = 1 AND deleted = 0
            ORDER BY club_id, doc_type
        """)
        return cursor.fetchall()


def get_activity_report_content(conn, source_id):
    """从 ai_activity_report 表获取成果报告内容"""
    if not source_id:
        return None
    with conn.cursor() as cursor:
        cursor.execute("""
            SELECT content FROM ai_activity_report
            WHERE activity_id = %s AND deleted = 0
        """, (source_id,))
        row = cursor.fetchone()
        return row["content"] if row else None


def get_compilation_content(conn, source_id):
    """从 act_checkin_compilation 表获取打卡汇编内容"""
    if not source_id:
        return None
    with conn.cursor() as cursor:
        cursor.execute("""
            SELECT content FROM act_checkin_compilation
            WHERE task_id = %s AND deleted = 0
        """, (source_id,))
        row = cursor.fetchone()
        return row["content"] if row else None


def generate_charter_content(title: str, club_id: int) -> str:
    """为章程类文档生成合成内容"""
    club_names = {
        1: "编程技术社", 2: "篮球协会", 3: "摄影协会",
        4: "志愿者协会", 5: "读书会", 6: "街舞社",
        7: "音乐社", 8: "书画社", 9: "天文社", 10: "电竞社"
    }
    name = club_names.get(club_id, f"社团{club_id}")
    return f"""# {title}

## 一、社团简介
{name}是经学校批准成立的正式学生社团，致力于为广大同学提供学习交流和展示才华的平台。

## 二、社团宗旨
丰富校园文化生活，提升成员综合素质，促进同学之间的交流与合作。

## 三、组织架构
- 社长：负责社团整体规划和管理
- 副社长：协助社长处理日常事务
- 组织部：负责活动策划和执行
- 宣传部：负责活动宣传和媒体运营
- 财务部：负责经费管理和预算审批

## 四、成员管理
1. 加入条件：本校在读学生，对{club_names.get(club_id, '本社团').replace('社', '').replace('协会', '')}相关领域有兴趣
2. 退出机制：成员可申请退出，需提前两周通知社长
3. 权利义务：成员有权参加社团活动、使用社团资源，有义务遵守社团章程

## 五、活动制度
1. 每学期至少组织 2 次大型活动
2. 活动需提前两周提交策划书，经审批后执行
3. 活动结束后一周内提交成果报告
4. 打卡任务截止时间以活动通知为准

## 六、经费管理
1. 经费来源：学校拨款、社团自筹
2. 重大支出需提前申请，经社长审批
3. 每学期末进行财务公示

## 七、附则
本章程自发布之日起生效，解释权归{name}所有。
"""


def generate_faq_content(title: str) -> str:
    """为 FAQ 类文档生成合成内容"""
    return f"""# {title}

Q1、如何加入社团？
通过社团管理系统提交报名申请，等待审核通过即可成为正式成员。也可以在线下社团招新时直接报名。

Q2、社团活动需要收费吗？
大部分活动免费，少数特殊活动（如外出比赛、购买材料）可能需要少量费用，会提前通知。

Q3、活动时间一般是什么时候？
活动一般安排在周末或工作日下午，具体时间以活动通知为准。

Q4、如何查看活动信息？
登录社团管理系统，在"活动管理"页面可以查看所有已发布和进行中的活动。

Q5、活动报名后可以取消吗？
可以，在活动开始前 24 小时内可以取消报名。超过截止时间则无法取消。

Q6、打卡任务是什么？
活动结束后需要提交打卡记录，包括心得体会、评分和建议，用于活动总结和积分统计。

Q7、积分有什么用？
积分可以兑换社团福利，也是评优评先的参考依据。签到、打卡心得、提出建议等都可以获得积分。

Q8、如何联系社团负责人？
在社团详情页可以看到社长和负责人的联系方式，也可以通过系统内的通知功能发送消息。
"""


def ingest_all():
    """执行完整的数据入库流程"""
    # 预加载 embedding 模型
    logger.info("加载 embedding 模型...")
    model = get_embedding_model()
    logger.info(f"模型加载完成: {model}")

    conn = get_connection()
    store = ChromaVectorStore()

    # 1. 清理旧数据
    logger.info("\n=== 阶段 1：清理旧 ChromaDB 数据 ===")
    for club_id in range(1, 11):
        collection = get_collection_name(club_id)
        try:
            store.delete_collection(collection)
            logger.info(f"  已删除 collection: {collection}")
        except Exception as e:
            logger.warning(f"  删除 {collection} 失败（可能不存在）: {e}")

    # 也清理缓存 collection
    try:
        store.delete_collection("__semantic_cache__")
        logger.info("  已删除 collection: __semantic_cache__")
    except:
        pass

    # 2. 读取文档元数据
    logger.info("\n=== 阶段 2：读取 MySQL 文档元数据 ===")
    docs = get_knowledge_documents(conn)
    logger.info(f"  共读取 {len(docs)} 条文档记录")

    # 3. 逐条入库
    logger.info("\n=== 阶段 3：向量化入库 ===")
    total_chunks = 0
    success_count = 0

    for doc in docs:
        club_id = doc["club_id"]
        doc_type = doc["doc_type"]
        title = doc["title"]
        source_id = doc.get("source_id")
        doc_id = f"doc_{doc['id']}"
        collection = get_collection_name(club_id)

        # 获取实际内容
        content = None
        if doc_type == "activity_report":
            content = get_activity_report_content(conn, source_id)
        elif doc_type == "checkin_compilation":
            # compilation 的 source_id 对应 task_id
            content = get_compilation_content(conn, source_id)
            if not content:
                # 尝试通过 activity_id 查找
                with conn.cursor() as cursor:
                    cursor.execute("""
                        SELECT cc.content FROM act_checkin_compilation cc
                        JOIN act_checkin_task ct ON cc.task_id = ct.id
                        WHERE ct.activity_id = %s AND cc.deleted = 0
                    """, (source_id,))
                    row = cursor.fetchone()
                    content = row["content"] if row else None

        # 对没有实际内容的文档，生成合成内容
        if not content:
            if doc_type == "charter":
                content = generate_charter_content(title, club_id)
            elif doc_type == "faq":
                content = generate_faq_content(title)
            else:
                logger.warning(f"  跳过: {title} (doc_type={doc_type}, 无内容)")
                continue

        # 入库
        try:
            metadata = {
                "club_id": club_id,
                "doc_type": doc_type,
                "title": title,
                "source": doc["source_type"],
            }
            if source_id:
                metadata["source_id"] = source_id

            chunk_count = ingest_document(collection, doc_id, content, metadata)
            total_chunks += chunk_count
            success_count += 1
            logger.info(f"  [OK] {title} ({doc_type}) → {chunk_count} chunks → {collection}")
        except Exception as e:
            logger.error(f"  [FAIL] {title}: {e}")

    # 4. 统计
    logger.info("\n=== 入库完成 ===")
    logger.info(f"  成功: {success_count}/{len(docs)} 条文档")
    logger.info(f"  总 chunks: {total_chunks}")

    for club_id in range(1, 6):
        collection = get_collection_name(club_id)
        count = store.count(collection)
        logger.info(f"  {collection}: {count} 条向量")

    conn.close()


if __name__ == "__main__":
    print("=== 开始执行数据入库 ===")
    ingest_all()
    print("=== 入库完成 ===")
