"""
RAG 问答评估器

使用方式：
    cd ai-service
    python -m evaluation.evaluator

输出：
    - 每个测试用例的检索命中、忠实度、拒答情况
    - 汇总指标：检索命中率 / Recall@K / MRR / 忠实度 / 拒答率 / 回答相关性
"""
import json
import logging
import time
from typing import List, Dict
from rag.retrieval import hybrid_search
from rag.reranker import rerank
from rag.generator import generate_answer, self_check_answer, get_llm_client
from rag.knowledge_base import get_collection_name
from config import LLM_MODEL

logging.basicConfig(level=logging.INFO, format='%(asctime)s %(levelname)s %(message)s')
logger = logging.getLogger(__name__)


def load_test_dataset(path: str = "evaluation/test_dataset.json") -> List[Dict]:
    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f)
    return data["cases"]


def evaluate_retrieval(case: Dict, club_id: int = 1) -> Dict:
    """评估检索质量：包含 Recall@K 和 MRR 计算"""
    question = case["question"]
    collection = get_collection_name(club_id)
    expected_type = case.get("expected_doc_type", "")
    
    start = time.time()
    # 粗排
    coarse = hybrid_search(collection, question, top_k=10)
    # 精排
    fine = rerank(question, coarse, top_k=3) if coarse else []
    elapsed = time.time() - start
    
    # 检查是否命中预期文档类型
    hit = False
    hit_rank = 0  # 命中的最高排名
    if expected_type:
        for i, doc in enumerate(fine):
            if doc.get("metadata", {}).get("doc_type") == expected_type:
                hit = True
                if hit_rank == 0:
                    hit_rank = i + 1
                break
    
    # Recall@K: 精排结果中包含预期类型的比例
    recall_at_k = 1.0 if hit else 0.0
    
    # MRR (Mean Reciprocal Rank)
    mrr = (1.0 / hit_rank) if hit_rank > 0 else 0.0
    
    return {
        "case_id": case["id"],
        "question": question,
        "coarse_count": len(coarse),
        "fine_count": len(fine),
        "retrieval_hit": hit,
        "recall_at_k": recall_at_k,
        "mrr": mrr,
        "elapsed_seconds": round(elapsed, 2),
        "top_doc_type": fine[0].get("metadata", {}).get("doc_type", "") if fine else ""
    }


def evaluate_answer_relevance(answer: str, question: str) -> float:
    """使用 LLM 评估回答与问题的相关性（0-1 分）"""
    prompt = f"""请评估以下回答与问题的相关性，返回一个 0 到 1 之间的数字。

问题：{question}

回答：{answer[:500]}

只返回一个数字（如 0.8），不要其他内容。"""
    try:
        client = get_llm_client()
        response = client.chat.completions.create(
            model=LLM_MODEL,
            messages=[{"role": "user", "content": prompt}],
            temperature=0.1,
            max_tokens=10
        )
        score = float(response.choices[0].message.content.strip())
        return max(0.0, min(1.0, score))
    except Exception as e:
        logger.warning(f"Answer relevance scoring failed: {e}")
        return 0.5


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
        
        # 回答相关性评分
        relevance = evaluate_answer_relevance(answer, question)
        
        return {
            "case_id": case["id"],
            "question": question,
            "answer_preview": answer[:100],
            "keywords_found": keywords_found,
            "keyword_hit_rate": len(keywords_found) / max(len(case.get("expected_keywords", [])), 1),
            "faithful": check["passed"],
            "confidence": check["confidence"],
            "answer_relevance": round(relevance, 3)
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
    
    # Recall@K 平均值
    avg_recall = sum(r.get("recall_at_k", 0) for r in retrieval_results) / max(len(retrieval_results), 1)
    
    # MRR 平均值
    avg_mrr = sum(r.get("mrr", 0) for r in retrieval_results) / max(len(retrieval_results), 1)
    
    # 拒答率
    oos_cases = [c for c in cases if c.get("expected_behavior") == "reject"]
    oos_results = [r for r in generation_results if r.get("correct_reject") is not None]
    reject_rate = sum(1 for r in oos_results if r["correct_reject"]) / max(len(oos_cases), 1)
    
    # 忠实度
    normal_gen = [r for r in generation_results if r.get("faithful") is not None and "correct_reject" not in r]
    faithful_rate = sum(1 for r in normal_gen if r["faithful"]) / max(len(normal_gen), 1)
    avg_confidence = sum(r.get("confidence", 0) for r in normal_gen) / max(len(normal_gen), 1)
    
    # 回答相关性
    avg_relevance = sum(r.get("answer_relevance", 0) for r in normal_gen) / max(len(normal_gen), 1)
    
    logger.info("\n=== 评估汇总 ===")
    logger.info(f"  检索命中率:  {retrieval_hit_rate:.1%} ({len(fact_retrieval)}/{len(fact_cases)})")
    logger.info(f"  Recall@K:    {avg_recall:.1%}")
    logger.info(f"  MRR:         {avg_mrr:.3f}")
    logger.info(f"  忠实度:      {faithful_rate:.1%} ({sum(1 for r in normal_gen if r['faithful'])}/{len(normal_gen)})")
    logger.info(f"  平均置信度:  {avg_confidence:.2f}")
    logger.info(f"  回答相关性:  {avg_relevance:.3f}")
    logger.info(f"  拒答率:      {reject_rate:.1%} ({sum(1 for r in oos_results if r['correct_reject'])}/{len(oos_cases)})")
    
    # 4. 保存结果
    output = {
        "timestamp": time.strftime("%Y-%m-%d %H:%M:%S"),
        "summary": {
            "total_cases": total,
            "retrieval_hit_rate": round(retrieval_hit_rate, 3),
            "recall_at_k": round(avg_recall, 3),
            "mrr": round(avg_mrr, 3),
            "faithful_rate": round(faithful_rate, 3),
            "avg_confidence": round(avg_confidence, 3),
            "answer_relevance": round(avg_relevance, 3),
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
