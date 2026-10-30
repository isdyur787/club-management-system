#!/usr/bin/env python3
"""本地开发：MySQL / Redis、Java 后端、Vue 前端，配置密钥后自动启动 AI。"""
# 文件职责：调度项目服务、迁移、构建与进程登记；根目录启动/停止入口最终调用本脚本。
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import signal
import socket
import subprocess
import sys
import time
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parent.parent
WEB = ROOT / "club-management-web"
RUNTIME = ROOT / ".runtime"
JAR = ROOT / "target/club-management-1.0.0-SNAPSHOT.jar"
WINDOWS = os.name == "nt"


def read_env(path):
    values = {}
    if path.exists():
        for line in path.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                key, value = line.split("=", 1)
                values[key.strip()] = value.strip().strip('"\'')
    return values


def environment(create=False):
    # 环境变量优先于 .env；首次运行才生成开发凭证，已有配置原样保留。
    path = ROOT / ".env"
    if create and not path.exists():
        password, jwt = secrets.token_hex(24), secrets.token_hex(32)
        path.write_text(
            "# scripts/dev.py 自动生成；只用于本机开发，不提交版本控制。\n"
            "SPRING_PROFILES_ACTIVE=dev\nSERVER_PORT=8081\n"
            "MYSQL_HOST=127.0.0.1\nMYSQL_PORT=3307\n"
            f"MYSQL_USER=root\nMYSQL_PASSWORD={password}\nMYSQL_DATABASE=club_management\n"
            "REDIS_HOST=127.0.0.1\nREDIS_PORT=6379\nREDIS_DB=0\nREDIS_PASSWORD=\n"
            f"JWT_SECRET={jwt}\n"
            "CORS_ORIGINS=http://localhost:5173,http://127.0.0.1:5173\n"
            "AI_SERVICE_URL=http://127.0.0.1:8000\n"
            "REDIS_URL=redis://127.0.0.1:6379/1\n",
            encoding="utf-8",
        )
        path.chmod(0o600)
    env = {**read_env(path), **os.environ}
    if sys.platform == "darwin":
        java = subprocess.run(["/usr/libexec/java_home", "-v", "17"], capture_output=True, text=True)
        if java.returncode == 0:
            env["JAVA_HOME"] = java.stdout.strip()
    return env


def executable(name, *candidates, env=None):
    # 显式指定的工具目录优先，其次 PATH，最后才尝试常见安装位置。
    env = os.environ if env is None else env
    names = [name + ".exe", name + ".cmd"] if WINDOWS else [name]
    if WINDOWS and name in ("redis-server", "redis-cli"):
        names.append("memurai.exe" if name == "redis-server" else "memurai-cli.exe")
    key = ("MYSQL_BIN" if name.startswith("mysql") else "REDIS_BIN" if name.startswith("redis")
           else "JAVA_HOME" if name == "java" else "MAVEN_HOME" if name == "mvn" else "")
    configured = env.get(key) or (env.get("M2_HOME") if name == "mvn" else None)
    if configured:
        directory = Path(configured) / "bin" if name in ("java", "mvn") else Path(configured)
        for filename in names:
            candidate = directory / filename
            if candidate.is_file():
                return str(candidate)
        raise RuntimeError(f"{key} 指定的目录中未找到 {name}：{directory}")
    for filename in [name] + names:
        found = shutil.which(filename, path=env.get("PATH", ""))
        if found:
            return found
    directories = []
    if name.startswith("mysql"):
        if not WINDOWS:
            directories += ["/usr/local/mysql/bin", "/opt/homebrew/bin"]
        if WINDOWS:
            program_files = Path(env.get("PROGRAMFILES", env.get("ProgramFiles", r"C:\Program Files")))
            directories += sorted((program_files / "MySQL").glob("MySQL Server */bin"), reverse=True)
    elif name.startswith("redis"):
        if not WINDOWS:
            directories += ["/opt/homebrew/bin", "/usr/local/bin"]
        directories += sorted((RUNTIME / "tools").glob("redis-*/src"), reverse=True)
        if WINDOWS:
            directories += [Path(env.get("PROGRAMFILES", env.get("ProgramFiles", r"C:\Program Files"))) / "Memurai"]
    elif name == "mvn":
        if not WINDOWS:
            directories += ["/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin"]
        if WINDOWS:
            jetbrains = Path(env.get("PROGRAMFILES", env.get("ProgramFiles", r"C:\Program Files"))) / "JetBrains"
            directories += sorted(jetbrains.glob("IntelliJ IDEA*/plugins/maven/lib/maven3/bin"), reverse=True)
    for directory in directories:
        if directory:
            candidates += tuple(Path(directory) / filename for filename in names)
    for candidate in candidates:
        if Path(candidate).is_file():
            return str(candidate)
    raise RuntimeError(f"未找到 {name}，请安装并加入 PATH，或在 .env 配置 MYSQL_BIN / REDIS_BIN / JAVA_HOME / MAVEN_HOME。")


def run_tool(command, **kwargs):
    command = [str(arg) for arg in command]
    # PowerShell 的字面量参数支持带空格、中文和单引号的 .cmd 工具路径。
    if WINDOWS and Path(command[0]).suffix.lower() in (".cmd", ".bat"):
        quoted = " ".join("'" + arg.replace("'", "''") + "'" for arg in command)
        script = "$ErrorActionPreference='Stop'; & " + quoted + "; exit $LASTEXITCODE"
        encoded = base64.b64encode(script.encode("utf-16-le")).decode("ascii")
        command = ["powershell", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded]
    return subprocess.run(command, **kwargs)


def backend_needs_build():
    if not JAR.is_file():
        return True
    source = ROOT / "src/main"
    # 目录时间同时覆盖删除文件的情况；清理构建避免旧 class 或资源残留。
    inputs = [ROOT / "pom.xml", ROOT / "src", source] + list(source.rglob("*"))
    return any(path.exists() and path.stat().st_mtime > JAR.stat().st_mtime for path in inputs)


def check_tools(env):
    for name in ("mysql", "mysqld", "mysqladmin", "redis-server", "redis-cli", "java", "node", "npm", "mvn"):
        executable(name, env=env)
    for name, minimum, option in (("java", 17, "-version"), ("node", 18, "--version")):
        result = run_tool([executable(name, env=env), option], capture_output=True, text=True, env=env, check=True)
        match = re.search(r'(?:version\s+"|^v)(\d+)', result.stdout + result.stderr)
        if not match or int(match.group(1)) < minimum:
            raise RuntimeError(f"需要 {name} {minimum}+，请检查 JAVA_HOME 和 PATH。")


def process_stamp(pid):
    if WINDOWS:
        command = (
            f"$p = Get-Process -Id {int(pid)} -ErrorAction SilentlyContinue; "
            "if ($p) { $p.StartTime.ToUniversalTime().ToString('o') }"
        )
        result = subprocess.run(
            ["powershell", "-NoProfile", "-Command", command],
            capture_output=True,
            text=True,
        )
    else:
        result = subprocess.run(["ps", "-p", str(pid), "-o", "lstart="], capture_output=True, text=True)
    return result.stdout.strip() if result.returncode == 0 else ""


def process_info(name):
    # PID 会被系统复用；同时核对启动时间，避免 stop 误伤后来占用相同 PID 的进程。
    path = RUNTIME / f"{name}.process.json"
    if path.exists():
        info = json.loads(path.read_text(encoding="utf-8"))
        if info["started"] and process_stamp(info["pid"]) == info["started"]:
            return info
    return None


def port_open(port):
    with socket.socket() as client:
        client.settimeout(0.3)
        return client.connect_ex(("127.0.0.1", port)) == 0


def launch(name, command, port, env, cwd=ROOT):
    # 只有登记过的进程才复用；端口被陌生进程占用时直接报错，不强行结束对方。
    if process_info(name):
        if not port_open(port):
            raise RuntimeError(f"{name} 进程存在但端口未就绪，请查看 .runtime/{name}.log。")
        return
    if port_open(port):
        raise RuntimeError(f"端口 {port} 已被其他进程占用，未启动或停止该进程。")
    with (RUNTIME / f"{name}.log").open("a") as log:
        options = ({"creationflags": subprocess.CREATE_NEW_PROCESS_GROUP | subprocess.DETACHED_PROCESS}
                   if WINDOWS else {"start_new_session": True})
        child = subprocess.Popen([str(arg) for arg in command], cwd=cwd, env=env,
                                 stdin=subprocess.DEVNULL, stdout=log, stderr=log,
                                 **options)
    record = {"pid": child.pid, "started": process_stamp(child.pid), "port": port}
    (RUNTIME / f"{name}.process.json").write_text(json.dumps(record), encoding="utf-8")
    deadline = time.monotonic() + 90
    while time.monotonic() < deadline:
        if child.poll() is not None:
            raise RuntimeError(f"{name} 启动失败，请查看 .runtime/{name}.log。")
        # 端口可连接仅代表进程开始监听，具体业务是否可用仍要检查接口和日志。
        if port_open(port):
            print(f"{name} 已启动：127.0.0.1:{port}", flush=True)
            return
        time.sleep(0.3)
    raise RuntimeError(f"{name} 启动超时，请查看 .runtime/{name}.log。")


def sql(env, statement, database=None, password=None, check=True):
    # 密码通过子进程环境传给客户端，SQL 从标准输入发送，避免出现在命令行参数中。
    client = executable("mysql", env=env)
    command = [client, "--no-defaults", "--protocol=TCP", "-h", "127.0.0.1",
               "-P", env.get("MYSQL_PORT", "3307"), "-u", "root",
               "--default-character-set=utf8mb4", "--batch", "--skip-column-names"]
    if database:
        command.append(database)
    result = subprocess.run(
        command,
        input=statement,
        text=True,
        encoding="utf-8",
        capture_output=True,
        env={**env, "MYSQL_PWD": env["MYSQL_PASSWORD"] if password is None else password},
    )
    if check and result.returncode:
        raise RuntimeError(f"数据库操作失败：{result.stderr.strip()}")
    return result


def mysql_command(server, env):
    command = [server, "--no-defaults", f"--datadir={RUNTIME / 'mysql'}", "--bind-address=127.0.0.1",
               f"--port={env.get('MYSQL_PORT', '3307')}", "--mysqlx=0", f"--pid-file={RUNTIME / 'mysql.pid'}"]
    if WINDOWS:
        command.append("--console")
    else:
        command.append(f"--socket=/tmp/club-{hashlib.sha256(str(ROOT).encode()).hexdigest()[:12]}.sock")
    return command


def start_mysql(env):
    if env.get("MYSQL_HOST", "127.0.0.1") not in ("127.0.0.1", "localhost") or env.get("MYSQL_USER", "root") != "root":
        raise RuntimeError("本脚本只管理项目自带的本地 MySQL；使用外部数据库时，请自行启动 MySQL、Redis、Java 后端和 Vue 前端。")
    if env.get("SPRING_PROFILES_ACTIVE", "dev") != "dev":
        raise RuntimeError("本地脚本只使用 dev 配置；生产环境请独立启动 Java 服务。")
    if env.get("MYSQL_URL"):
        url = urlsplit(env["MYSQL_URL"].removeprefix("jdbc:"))
        if (url.scheme != "mysql" or url.hostname not in ("127.0.0.1", "localhost")
                or (url.port or 3306) != int(env.get("MYSQL_PORT", "3307"))
                or url.path.lstrip("/") != env.get("MYSQL_DATABASE", "club_management")):
            raise RuntimeError("MYSQL_URL 与本地 MYSQL_HOST/PORT/DATABASE 不一致，未操作数据库。"
                               "请统一数据库配置，或自行启动数据库、Java 后端和 Vue 前端。")
    server = executable("mysqld", env=env)
    data = RUNTIME / "mysql"
    initialized = not (data / "auto.cnf").exists()
    if initialized:
        with (RUNTIME / "mysql-initialize.log").open("a") as log:
            subprocess.run([server, "--no-defaults", "--initialize-insecure", f"--datadir={data}"],
                           stdout=log, stderr=log, check=True)
    port = int(env.get("MYSQL_PORT", "3307"))
    launch("mysql", mysql_command(server, env), port, env)
    # 仅对本脚本新建的私有实例设置 root 密码，不访问系统 MySQL。
    if sql(env, "SELECT 1;", check=False).returncode:
        if not initialized:
            raise RuntimeError("已有项目数据库认证失败，请核对 .env 中的 MYSQL_PASSWORD；未重置密码或数据库。")
        password = env["MYSQL_PASSWORD"].replace("\\", "\\\\").replace("'", "''")
        sql(env, f"ALTER USER 'root'@'localhost' IDENTIFIED BY '{password}';", password="")
    database = env.get("MYSQL_DATABASE", "club_management")
    if not re.fullmatch(r"[a-zA-Z0-9_]+", database):
        raise RuntimeError("MYSQL_DATABASE 只能使用字母、数字和下划线。")
    sql(env, f"CREATE DATABASE IF NOT EXISTS `{database}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;")
    sql(env, "CREATE TABLE IF NOT EXISTS _local_schema_history "
             "(version INT PRIMARY KEY, filename VARCHAR(255), checksum CHAR(64), "
             "applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);", database)
    for path in sorted((ROOT / "sql").glob("V*__*.sql"), key=lambda p: int(p.name.split("__")[0][1:])):
        # 按数字版本排序（V2 早于 V10）；已执行文件的注释也计入哈希，不可原位改写。
        version = int(path.name.split("__")[0][1:])
        checksum = hashlib.sha256(path.read_bytes()).hexdigest()
        applied = sql(env, f"SELECT checksum FROM _local_schema_history WHERE version={version};", database).stdout.strip()
        if applied:
            if applied != checksum:
                raise RuntimeError(f"已执行过的 {path.name} 内容发生变化，请新增迁移脚本。")
            continue
        content = re.sub(
            r"(?im)^\s*USE\s+club_management\s*;",
            "",
            path.read_text(encoding="utf-8"),
        )
        # 种子数据和版本记录同一事务；遇错关闭连接会回滚 DML，避免半份数据。
        # MySQL 的 DDL 会隐式提交，DDL 失败仍需人工检查，禁止忽略错误继续导入。
        sql(env, "START TRANSACTION;\n" + content + "\n"
                 f"INSERT INTO _local_schema_history(version,filename,checksum) "
                 f"VALUES({version},'{path.name}','{checksum}');\nCOMMIT;", database)
        print(f"已导入 {path.name}", flush=True)


def start_redis(env):
    if env.get("REDIS_HOST", "127.0.0.1") not in ("127.0.0.1", "localhost"):
        raise RuntimeError("本脚本只管理本机 Redis；外部 Redis 请手动启动两端。")
    server = executable("redis-server", env=env)
    directory = RUNTIME / "redis"
    directory.mkdir(exist_ok=True)
    port = int(env.get("REDIS_PORT", "6379"))
    config = RUNTIME / "redis.conf"
    config.write_text(f'bind 127.0.0.1\nport {port}\nprotected-mode yes\ndir "{directory.as_posix()}"\n'
                      'appendonly yes\ndaemonize no\n' +
                      (f'requirepass "{env["REDIS_PASSWORD"]}"\n' if env.get("REDIS_PASSWORD") else ""), encoding="utf-8")
    config.chmod(0o600)
    launch("redis", [server, config], port, env)


def build_backend(env):
    maven = executable("mvn", env=env)
    run_tool([maven, "-B", "clean", "package"], cwd=ROOT, env=env, check=True)


def install_frontend(env):
    if not (WEB / "node_modules/vite/bin/vite.js").exists():
        run_tool([executable("npm", env=env), "ci", "--no-audit", "--no-fund"], cwd=WEB, env=env, check=True)


def start(env, setup_only=False):
    # 顺序有依赖：数据库和缓存先就绪，再构建/启动 Java，随后启动 Vue 和可选 AI。
    check_tools(env)
    RUNTIME.mkdir(exist_ok=True)
    start_mysql(env)
    start_redis(env)
    install_frontend(env)
    if setup_only or backend_needs_build():
        if process_info("backend"):
            stop(("backend",))
        build_backend(env)
    if setup_only:
        run_tool([executable("npm", env=env), "run", "build"], cwd=WEB, env=env, check=True)
        return
    java = executable("java", env=env)
    launch("backend", [java, "-jar", JAR], int(env.get("SERVER_PORT", "8081")), env)
    # 使用 Vite 自己的加载器，保持与 npm run dev 完全一致的文件优先级及变量展开。
    front_config = subprocess.run(
        [executable("node", env=env), "--input-type=module", "-e",
         "import { loadEnv } from 'vite'; process.stdout.write(JSON.stringify(loadEnv('development', process.cwd(), 'VITE_')));"],
        cwd=WEB, env=env, capture_output=True, text=True, check=True)
    front_env = {**env, **json.loads(front_config.stdout)}
    launch("frontend", [executable("node", env=env), WEB / "node_modules/vite/bin/vite.js"],
           int(front_env.get("VITE_PORT", "5173")), front_env, WEB)
    ai_enabled = bool(env.get("LLM_API_KEY") and not env["LLM_API_KEY"].startswith("your-") and env.get("AI_INTERNAL_API_KEY"))
    if ai_enabled:
        subprocess.run([sys.executable, str(ROOT / "scripts/ai.py"), "start"], cwd=ROOT, env=env, check=True)
    print(f"\n前端：http://localhost:{front_env.get('VITE_PORT', '5173')}/\n"
          f"接口文档：http://localhost:{env.get('SERVER_PORT', '8081')}/swagger-ui/index.html\n"
          "测试账号：admin / 123456\n" + ("AI：http://127.0.0.1:8000/health" if ai_enabled else "AI 未配置，未启动 Python 服务。"), flush=True)


def stop(names=("ai", "frontend", "backend", "redis", "mysql")):
    # 先停业务、后停基础服务；MySQL/Redis 走各自的正常关闭命令以保留持久数据。
    env = environment()
    for name in names:
        info = process_info(name)
        if info:
            if name == "mysql":
                subprocess.run([executable("mysqladmin", env=env), "--no-defaults", "--protocol=TCP",
                                "-h", "127.0.0.1", "-P", str(info["port"]), "-u", "root", "shutdown"],
                               env={**env, "MYSQL_PWD": env["MYSQL_PASSWORD"]}, check=True)
            elif name == "redis":
                redis_env = {key: value for key, value in env.items() if key != "REDISCLI_AUTH"}
                if env.get("REDIS_PASSWORD"):
                    redis_env["REDISCLI_AUTH"] = env["REDIS_PASSWORD"]
                subprocess.run([executable("redis-cli", env=env), "-h", "127.0.0.1",
                                "-p", str(info["port"]), "shutdown"],
                               env=redis_env, check=True)
            else:
                os.kill(info["pid"], signal.SIGTERM)
            deadline = time.monotonic() + 30
            while process_info(name) and time.monotonic() < deadline:
                time.sleep(0.3)
            if process_info(name):
                raise RuntimeError(f"{name} 尚未退出，请查看日志；未强制终止。")
            print(f"{name} 已停止（数据保留）")
        (RUNTIME / f"{name}.process.json").unlink(missing_ok=True)


def status():
    for name in ("mysql", "redis", "backend", "frontend", "ai"):
        info = process_info(name)
        ready = info and port_open(info["port"])
        print(f"{name}: " + (f"运行中，端口 {info['port']}，PID {info['pid']}" if ready else "未运行/未就绪"))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("setup", "start", "stop", "stop-backend", "status"))
    args = parser.parse_args()
    try:
        if args.command in ("setup", "start"):
            start(environment(create=True), setup_only=args.command == "setup")
        elif args.command == "stop":
            stop()
        elif args.command == "stop-backend":
            stop(("backend",))
        else:
            status()
    except (RuntimeError, subprocess.CalledProcessError, OSError, ValueError) as error:
        print(f"错误：{error}", file=sys.stderr)
        sys.exit(1)
