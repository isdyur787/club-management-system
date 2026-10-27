import importlib.util
import os
from pathlib import Path
import socket
import tempfile
import unittest
from unittest.mock import patch


SPEC = importlib.util.spec_from_file_location("club_dev", Path(__file__).parents[1] / "dev.py")
dev = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(dev)


class LauncherTests(unittest.TestCase):
    def test_configured_tool_directory_takes_priority_over_path(self):
        with tempfile.TemporaryDirectory() as directory:
            binary = Path(directory) / "mysql"
            binary.touch()
            with patch.object(dev.shutil, "which", return_value="/other/mysql"):
                self.assertEqual(dev.executable("mysql", env={"MYSQL_BIN": directory}), str(binary))

    def test_java_home_is_used_instead_of_another_java_on_path(self):
        with tempfile.TemporaryDirectory() as directory:
            binary = Path(directory) / "bin" / "java"
            binary.parent.mkdir()
            binary.touch()
            with patch.object(dev.shutil, "which", return_value="/other/java"):
                self.assertEqual(dev.executable("java", env={"JAVA_HOME": directory}), str(binary))

    def test_explicit_mysql_directory_supports_spaces_and_chinese(self):
        with tempfile.TemporaryDirectory(prefix="club launch ") as directory:
            binary = Path(directory) / "数据库 MySQL" / "bin" / "mysql.exe"
            binary.parent.mkdir(parents=True)
            binary.touch()
            env = {"PATH": "", "MYSQL_BIN": str(binary.parent)}
            with patch.object(dev, "WINDOWS", True, create=True):
                self.assertEqual(dev.executable("mysql", env=env), str(binary))

    def test_windows_redis_can_use_memurai_binaries(self):
        with tempfile.TemporaryDirectory() as directory:
            server = Path(directory) / "memurai.exe"
            client = Path(directory) / "memurai-cli.exe"
            server.touch()
            client.touch()
            env = {"PATH": "", "REDIS_BIN": directory}
            with patch.object(dev, "WINDOWS", True, create=True):
                self.assertEqual(dev.executable("redis-server", env=env), str(server))
                self.assertEqual(dev.executable("redis-cli", env=env), str(client))

    def test_memurai_is_discovered_on_path_outside_default_install_location(self):
        with tempfile.TemporaryDirectory(prefix="custom redis ") as directory:
            server = Path(directory) / "memurai.exe"
            client = Path(directory) / "memurai-cli.exe"
            for binary in (server, client):
                binary.touch()
                binary.chmod(0o755)
            with patch.object(dev, "WINDOWS", True, create=True):
                self.assertEqual(dev.executable("redis-server", env={"PATH": directory}), str(server))
                self.assertEqual(dev.executable("redis-cli", env={"PATH": directory}), str(client))

    def test_windows_mysql_uses_console_without_unix_socket(self):
        with patch.object(dev, "WINDOWS", True, create=True):
            command = dev.mysql_command("mysqld.exe", {"MYSQL_PORT": "3317"})
        self.assertIn("--console", command)
        self.assertIn("--port=3317", command)
        self.assertFalse(any(arg.startswith("--socket=") for arg in command))
        self.assertIn(f"--datadir={dev.RUNTIME / 'mysql'}", command)

    def test_existing_utf8_settings_are_not_overwritten(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            original = "MYSQL_PASSWORD=keep-this\n# 社团配置\n"
            (root / ".env").write_text(original, encoding="utf-8")
            with patch.object(dev, "ROOT", root), patch.object(dev.sys, "platform", "linux"):
                env = dev.environment(create=True)
            self.assertEqual(env["MYSQL_PASSWORD"], "keep-this")
            self.assertEqual((root / ".env").read_text(encoding="utf-8"), original)

    def test_backend_rebuilds_only_when_inputs_are_newer(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            jar = root / "target" / "app.jar"
            source = root / "src" / "main" / "java" / "Example.java"
            jar.parent.mkdir()
            source.parent.mkdir(parents=True)
            jar.touch()
            source.touch()
            for parent in (source.parent, source.parent.parent, source.parent.parent.parent, root / "src"):
                os.utime(parent, (100, 100))
            os.utime(source, (100, 100))
            os.utime(jar, (200, 200))
            with patch.object(dev, "ROOT", root), patch.object(dev, "JAR", jar):
                self.assertFalse(dev.backend_needs_build())
                os.utime(source, (300, 300))
                self.assertTrue(dev.backend_needs_build())

    def test_deleted_backend_source_requires_rebuild(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "src/main/java/Removed.java"
            source.parent.mkdir(parents=True)
            source.touch()
            jar = root / "app.jar"
            jar.touch()
            os.utime(jar, (200, 200))
            os.utime(source, (100, 100))
            source.unlink()
            with patch.object(dev, "ROOT", root), patch.object(dev, "JAR", jar):
                self.assertTrue(dev.backend_needs_build())

    def test_unmanaged_port_is_rejected_without_process_record(self):
        with tempfile.TemporaryDirectory() as directory, socket.socket() as occupied:
            occupied.bind(("127.0.0.1", 0))
            occupied.listen()
            port = occupied.getsockname()[1]
            with patch.object(dev, "RUNTIME", Path(directory)):
                with self.assertRaisesRegex(RuntimeError, str(port)):
                    dev.launch("frontend", ["never-run"], port, {})
                self.assertFalse((Path(directory) / "frontend.process.json").exists())

    def test_existing_database_auth_failure_never_resets_data_or_password(self):
        with tempfile.TemporaryDirectory() as directory:
            runtime = Path(directory)
            data = runtime / "mysql"
            data.mkdir()
            marker = data / "auto.cnf"
            marker.write_text("existing instance", encoding="utf-8")

            def denied(env, statement, **kwargs):
                self.assertEqual(statement, "SELECT 1;")
                return dev.subprocess.CompletedProcess([], 1, stdout="", stderr="access denied")

            with patch.object(dev, "RUNTIME", runtime), patch.object(dev, "executable", return_value="mysqld"), \
                    patch.object(dev, "launch"), patch.object(dev, "sql", side_effect=denied):
                with self.assertRaisesRegex(RuntimeError, "未重置"):
                    dev.start_mysql({"MYSQL_PASSWORD": "wrong"})
            self.assertEqual(marker.read_text(encoding="utf-8"), "existing instance")

    def test_redis_shutdown_without_password_does_not_send_auth(self):
        def shutdown(command, **kwargs):
            self.assertNotIn("REDISCLI_AUTH", kwargs["env"])
            self.assertEqual(command[-1], "shutdown")

        with tempfile.TemporaryDirectory() as directory:
            with patch.object(dev, "RUNTIME", Path(directory)), \
                    patch.object(dev, "environment", return_value={"REDIS_PASSWORD": "", "REDISCLI_AUTH": "other-instance"}), \
                    patch.object(dev, "process_info", side_effect=[{"pid": 123, "port": 6379}, None, None]), \
                    patch.object(dev, "executable", return_value="redis-cli"), \
                    patch.object(dev.subprocess, "run", side_effect=shutdown):
                dev.stop(("redis",))


if __name__ == "__main__":
    unittest.main()
