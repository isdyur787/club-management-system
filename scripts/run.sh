#!/usr/bin/env bash
set -e
cd "$(dirname "$0")/.."
export PATH="$PATH:/opt/homebrew/bin:/usr/local/bin:/Library/Frameworks/Python.framework/Versions/Current/bin"
export PYTHONUTF8=1
export PYTHONIOENCODING=utf-8

for python in python3 python /opt/homebrew/bin/python3 /usr/local/bin/python3 /Library/Frameworks/Python.framework/Versions/Current/bin/python3; do
  if command -v "$python" >/dev/null 2>&1 && "$python" -c 'import sys; sys.exit(sys.version_info < (3, 10))' >/dev/null 2>&1; then
    exec "$python" scripts/dev.py "$@"
  fi
done

printf '%s\n' '未找到 Python 3.10+，请先安装 Python 并加入 PATH。' >&2
exit 1
