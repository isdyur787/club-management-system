#!/usr/bin/env python3
"""本地 AI 服务：setup / start / stop / status / index，不重启 IntelliJ 后端。"""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time
import urllib.request

import dev

AI = dev.ROOT / "ai-service"
PYTHON = AI / ".venv" / ("Scripts/python.exe" if os.name == "nt" else "bin/python")


def setup(env):
    if not PYTHON.exists():
        candidate = shutil.which("python3.12") or shutil.which("python3.11")
        if not candidate:
            candidate = "/opt/homebrew/opt/python@3.12/bin/python3.12"
        subprocess.run([candidate, "-m", "venv", str(AI / ".venv")], check=True)
    subprocess.run([str(PYTHON), "-m", "pip", "install", "-r", str(AI / "requirements.txt")], check=True)
    subprocess.run([str(PYTHON), "-c", "from rag.ingestion import get_embedding_model; from rag.reranker import get_reranker; get_embedding_model().encode(['社团']); get_reranker().compute_score([['社团','社团管理']])"],
                   cwd=AI, env=env, check=True)


def request(path, env, payload=None):
    req = urllib.request.Request("http://127.0.0.1:8000" + path,
        data=json.dumps(payload).encode() if payload is not None else None,
        headers={"X-Internal-Key": env.get("AI_INTERNAL_API_KEY", ""), "Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=300) as response:
        return json.load(response)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=["setup", "start", "stop", "status", "index"])
    args = parser.parse_args()
    env = dev.environment()
    dev.RUNTIME.mkdir(exist_ok=True)
    if args.command == "setup":
        setup(env)
    elif args.command == "start":
        for name in ("LLM_API_KEY", "AI_INTERNAL_API_KEY", "MYSQL_PASSWORD"):
            if not env.get(name):
                raise RuntimeError(f"请在根目录 .env 配置 {name}")
        if not PYTHON.exists():
            raise RuntimeError("请先执行 python3 scripts/ai.py setup")
        dev.launch("ai", [PYTHON, "-m", "uvicorn", "main:app", "--host", "127.0.0.1", "--port", "8000"],
                   8000, env, cwd=AI)
        print("AI health:", request("/health", env))
    elif args.command == "stop":
        info = dev.process_info("ai")
        if info:
            import signal
            os.kill(info["pid"], signal.SIGTERM)
            deadline = time.monotonic() + 30
            while dev.process_info("ai") and time.monotonic() < deadline:
                time.sleep(.2)
            if dev.process_info("ai"):
                raise RuntimeError("AI 服务尚未退出，请查看 .runtime/ai.log")
            (dev.RUNTIME / "ai.process.json").unlink(missing_ok=True)
            print("已停止 AI 服务")
        else:
            print("没有脚本启动的 AI 进程")
    elif args.command == "status":
        print(json.dumps(request("/health", env), ensure_ascii=False))
        print(json.dumps(request("/chat/models", env), ensure_ascii=False))
    else:
        result = request("/knowledge/admin/ingest-all", env, {})
        print(json.dumps(result, ensure_ascii=False, indent=2))
        if any(row["status"] == "error" for row in result["results"]):
            raise RuntimeError("部分文档索引失败，请查看 .runtime/ai.log")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(f"错误：{error}", file=sys.stderr)
        sys.exit(1)
