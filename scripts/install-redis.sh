#!/bin/sh
# Homebrew 不支持当前 macOS 时，把 Redis 编译到项目私有目录。
set -eu
project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
tools_dir="$project_dir/.runtime/tools"
version=7.4.11
mkdir -p "$tools_dir"
curl -fL --retry 2 "https://download.redis.io/releases/redis-$version.tar.gz" -o "$tools_dir/redis.tar.gz"
tar -xzf "$tools_dir/redis.tar.gz" -C "$tools_dir"
make -C "$tools_dir/redis-$version" -j4
