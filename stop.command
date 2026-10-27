#!/bin/bash
bash "$(dirname "$0")/stop.sh" "$@"
result=$?
if [ -t 0 ] && [ -z "${CLUB_NO_PAUSE:-}" ]; then
  printf '\n按回车关闭窗口。\n'
  read -r
fi
exit "$result"
