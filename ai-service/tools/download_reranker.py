"""
Reranker 模型下载工具

使用 ModelScope（国内直连）下载 BAAI/bge-reranker-v2-m3。
下载完成后验证离线加载。

用法：
    cd ai-service
    python tools/download_reranker.py
"""
import os
import sys
import shutil

MODEL_NAME = "BAAI/bge-reranker-v2-m3"
# 下载到项目本地目录，不依赖 ~/.cache
LOCAL_MODEL_DIR = os.path.join(os.path.dirname(__file__), "..", "models", "bge-reranker-v2-m3")


def download():
    print(f"[1/3] 使用 ModelScope 下载模型: {MODEL_NAME}")
    print(f"      目标路径: {os.path.abspath(LOCAL_MODEL_DIR)}")
    print()

    from modelscope import snapshot_download

    # ModelScope 下载到 ~/.cache/modelscope/，再拷贝到项目目录
    cache_path = snapshot_download(MODEL_NAME)
    print(f"      ModelScope 缓存: {cache_path}")

    # 拷贝到项目本地目录
    if os.path.exists(LOCAL_MODEL_DIR):
        shutil.rmtree(LOCAL_MODEL_DIR)
    shutil.copytree(cache_path, LOCAL_MODEL_DIR)
    print(f"      已拷贝到: {os.path.abspath(LOCAL_MODEL_DIR)}")


def verify():
    print()
    print("[3/3] 验证离线加载...")

    os.environ["HF_HUB_OFFLINE"] = "1"

    from FlagEmbedding import FlagReranker

    model = FlagReranker(os.path.abspath(LOCAL_MODEL_DIR), device="cpu", use_fp16=True)

    scores = model.compute_score([["测试查询", "测试文档"]])
    print(f"      离线加载成功，测试分数: {scores}")
    print()
    print("✓ 全部完成！")
    print(f"  模型路径: {os.path.abspath(LOCAL_MODEL_DIR)}")


if __name__ == "__main__":
    try:
        download()
        verify()
    except Exception as e:
        print(f"\n✗ 失败: {e}", file=sys.stderr)
        sys.exit(1)
