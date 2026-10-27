#!/bin/bash
bash "$(dirname "$0")/start.sh" "$@"
result=$?
if [ -t 0 ] && [ -z "${CLUB_NO_PAUSE:-}" ]; then
  printf '\n按回车关闭窗口，已启动的服务会继续运行。\n'
  read -r
fi
exit "$result"
