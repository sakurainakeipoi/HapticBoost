"""Exercise production preview/apply shell commands against temporary fake nodes.

Run tools/test-app.ps1 first to compile the Java command producer. No device writes.
"""
from pathlib import Path
import importlib.util
import os
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("ringer_tests", ROOT / "tools/test-ringer-mode.py")
helpers = importlib.util.module_from_spec(spec)
spec.loader.exec_module(helpers)


class PreviewTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="haptic-preview-")
        self.root = Path(self.temp.name)
        self.mod = self.root / "module"
        self.nodes = self.root / "nodes"
        self.runtime = self.root / "runtime"
        for path in (self.mod, self.nodes, self.runtime):
            path.mkdir()
        self.config = (ROOT / "module/config.sh").read_text(encoding="utf-8")
        (self.mod / "config.sh").write_text(self.config, encoding="utf-8")
        for node, value in zip(("vmax", "fifo_vmax", "cl_vmax"), (5500, 4800, 1280)):
            (self.nodes / node).write_text(str(value))
        for name in ("apply.sh", "restore.sh"):
            (self.mod / name).write_text(self.translate((ROOT / "module" / name).read_text(encoding="utf-8")), encoding="utf-8", newline="\n")
        java_home = Path(os.environ.get("JAVA_HOME", ROOT / ".tools/jdk/jdk-17.0.20.1+1"))
        self.java = java_home / "bin" / ("java.exe" if os.name == "nt" else "java")

    def tearDown(self):
        self.temp.cleanup()

    def translate(self, script):
        return (script.replace("/data/adb/modules/haptic_boost_qcom", helpers.shell_path(self.mod))
                .replace("/sys/class/qcom-haptics", helpers.shell_path(self.nodes))
                .replace("/data/local/tmp", helpers.shell_path(self.runtime)))

    def execute(self, script):
        return subprocess.run([helpers.bash_path(), "-c", self.translate(script)], capture_output=True, text=True, timeout=10)

    def command(self, kind):
        return subprocess.check_output([str(self.java), "-cp", str(ROOT / ".tools/app-tests"),
                                       "io.github.sakurainakeipoi.hapticboost.AppProtocolTest", "preview-command", kind], text=True)

    def values(self):
        return [int((self.nodes / node).read_text().strip()) for node in ("vmax", "fifo_vmax", "cl_vmax")]

    def test_preview_and_restore_preserve_config(self):
        self.assertEqual(self.execute(self.command("prepare")).returncode, 0)
        self.assertEqual(self.values(), [3500, 4500, 1280])
        self.assertEqual(self.execute(self.command("restore")).returncode, 0)
        self.assertEqual(self.values(), [5500, 4800, 1280])
        self.assertEqual((self.mod / "config.sh").read_text(encoding="utf-8"), self.config)

    def test_active_call_blocks_preview(self):
        (self.runtime / "vib_ring_active").write_text("1")
        result = self.execute(self.command("prepare"))
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("HB_CALL_BUSY", result.stdout)
        self.assertEqual(self.values(), [5500, 4800, 1280])

    def test_active_call_blocks_save_without_overwriting_config(self):
        (self.runtime / "vib_ring_active").write_text("1")
        command = subprocess.check_output([str(self.java), "-cp", str(ROOT / ".tools/app-tests"),
                "io.github.sakurainakeipoi.hapticboost.AppProtocolTest", "save-command"], text=True, cwd=ROOT)
        result = self.execute(command)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("HB_CALL_BUSY", result.stdout)
        self.assertEqual((self.mod / "config.sh").read_text(encoding="utf-8"), self.config)
        self.assertEqual(self.values(), [5500, 4800, 1280])

    def test_call_started_during_preview_restores_call_profile(self):
        self.assertEqual(self.execute(self.command("prepare")).returncode, 0)
        (self.runtime / "vib_ring_active").write_text("1")
        self.assertEqual(self.execute(self.command("restore")).returncode, 0)
        self.assertEqual(self.values(), [9500, 9500, 9500])

    def test_missing_config_does_not_change_nodes(self):
        (self.mod / "config.sh").unlink()
        self.assertNotEqual(self.execute(self.command("prepare")).returncode, 0)
        self.assertEqual(self.values(), [5500, 4800, 1280])

    def test_write_failure_is_not_success(self):
        (self.nodes / "fifo_vmax").unlink()
        (self.nodes / "fifo_vmax").mkdir()
        self.assertNotEqual(self.execute(self.command("prepare")).returncode, 0)
        self.assertNotEqual(self.execute('sh /data/adb/modules/haptic_boost_qcom/apply.sh normal').returncode, 0)

    def test_restore_failure_is_not_success(self):
        (self.mod / "apply.sh").write_text("#!/bin/sh\necho pretend-success\n", encoding="utf-8")
        self.assertEqual(self.execute(self.command("prepare")).returncode, 0)
        result = self.execute(self.command("restore"))
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("HB_RESTORE_FAILED", result.stdout)

    def test_invalid_config_rejected_before_writing(self):
        (self.mod / "config.sh").write_text(self.config.replace("NORMAL_VMAX=5500", "NORMAL_VMAX=9501"), encoding="utf-8")
        self.assertNotEqual(self.execute('sh /data/adb/modules/haptic_boost_qcom/apply.sh normal').returncode, 0)
        self.assertEqual(self.values(), [5500, 4800, 1280])

    def test_factory_values_written_after_daemons_stop(self):
        # Simulate the last daemon write during cancellation, without starting processes.
        script = (self.mod / "restore.sh").read_text(encoding="utf-8")
        mock = "pgrep() { return 1; }; sleep() { :; }; cmd() { echo 9000 > /sys/class/qcom-haptics/vmax; };\n"
        self.assertEqual(self.execute(mock + script).returncode, 0)
        self.assertEqual(self.values(), [1280, 7700, 1280])


if __name__ == "__main__":
    unittest.main(verbosity=2)
