#!/usr/bin/env python3
"""Offline behavior checks for silent-mode gating; no device or root access.

Run: python tools/test-ringer-mode.py
Requires Bash (Git Bash on Windows). All sysfs/runtime paths and Android
commands are redirected to temporary fixtures, including the ring worker.
"""

import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import time
import unittest


REPO = Path(__file__).resolve().parents[1]


def bash_path():
    if os.name == "nt":
        candidate = Path(os.environ.get("ProgramFiles", "C:/Program Files")) / "Git/bin/bash.exe"
        if candidate.is_file():
            return str(candidate)
    return shutil.which("bash") or "bash"


def shell_path(path):
    value = path.resolve().as_posix()
    return "/" + value[0].lower() + value[2:] if os.name == "nt" else value


def telecom(mode="MODE_NORMAL", ringing=False):
    return (f"CallAudioModeStateMachine\n Mode {mode}\nPending Msg\n"
            " Ringing calls:\n" + ("  Call id=1\n" if ringing else "") + " Holding calls:\n")


def stage(mode="NORMAL", *, external=None, audio_mode="MODE_NORMAL", source="phone", fail=False, hint=False):
    audio = f" - Actual mode = {audio_mode}\n"
    if mode is not None:
        audio += f" - mode (internal) = {mode}\n - mode (external) = {external or mode}\n"
    return {
        "audio": audio,
        "audio_exit": "124" if fail else "0",
        "telecom": telecom("MODE_RINGTONE", True) if source == "phone" else telecom(),
        "hint": "1" if hint else "0",
    }


class Sandbox:
    def __init__(self, stages, *, voip=False):
        self.temp = tempfile.TemporaryDirectory(prefix="hapticboost-test-")
        self.root = Path(self.temp.name)
        self.stages = stages
        for name in ("module", "bin", "runtime", "sysfs", "stages"):
            (self.root / name).mkdir()
        for name in ("watch.sh", "ring_play.sh", "ringer_mode.sh"):
            content = (REPO / "module" / name).read_text(encoding="utf-8")
            content = content.replace("/data/local/tmp", shell_path(self.root / "runtime"))
            content = content.replace("/sys/class/qcom-haptics", shell_path(self.root / "sysfs"))
            if name == "watch.sh":
                # Bound only the fixture's otherwise infinite polling loop.
                content = content.replace("while true; do", 'while [ "$(cat "$HB_TEST_ROOT/index")" -lt "$HB_TEST_TOTAL" ]; do')
            assert "/data/local/tmp" not in content and "/sys/class/qcom-haptics" not in content
            self.write("module/" + name, content)
        self.write("module/config.sh", (REPO / "module/config.sh").read_text(encoding="utf-8"))
        self.write("index", "0\n")
        self.write("events", "")
        self.write("snapshots", "")
        for index, fixture in enumerate(stages):
            (self.root / "stages" / str(index)).mkdir()
            for name, content in fixture.items():
                self.write(f"stages/{index}/{name}", str(content))
        if voip:
            self.write("runtime/vib_voip_session", str(int(time.time())))
            self.write("runtime/vib_call_hint", str(int(time.time())))

        common = '''#!/bin/sh
INDEX=$(cat "$HB_TEST_ROOT/index")
FIXTURE="$HB_TEST_ROOT/stages/$INDEX"
'''
        self.mock("dumpsys", common + '''
printf 'dumpsys|%s|%s\n' "$INDEX" "$1" >> "$HB_TEST_ROOT/events"
cat "$FIXTURE/$1"
[ "$1" != audio ] || exit "$(cat "$FIXTURE/audio_exit")"
''')
        self.mock("timeout", '#!/bin/sh\nshift\nexec "$@"\n')
        for command in ("cmd", "setsid", "pkill"):
            self.mock(command, common + f'''printf '{command}|%s|%s\\n' "$INDEX" "$*" >> "$HB_TEST_ROOT/events"\n''')
        self.mock("sleep", common + '''
read_node() { cat "$HB_TEST_ROOT/sysfs/$1" 2>/dev/null; }
present() { if [ -f "$HB_TEST_ROOT/runtime/$1" ]; then printf 1; else printf 0; fi; }
printf '%s|%s|%s|%s|%s|%s|%s\n' "$INDEX" \
  "$(read_node vmax)" "$(read_node fifo_vmax)" "$(read_node cl_vmax)" \
  "$(present vib_ring_active)" "$(present vib_voip_connected)" "$(present vib_call_hint)" \
  >> "$HB_TEST_ROOT/snapshots"
NEXT=$((INDEX + 1))
printf '%s\n' "$NEXT" > "$HB_TEST_ROOT/index"
if [ "$NEXT" -ge "$HB_TEST_TOTAL" ]; then
  exit 0
fi
if [ "$(cat "$HB_TEST_ROOT/stages/$NEXT/hint")" = 1 ]; then
  date +%s > "$HB_TEST_ROOT/runtime/vib_call_hint"
fi
''')

    def write(self, relative, content):
        # Only disposable fixtures are generated; repository edits use apply_patch.
        (self.root / relative).write_text(content, encoding="utf-8", newline="\n")

    def mock(self, name, content):
        self.write("bin/" + name, content)
        (self.root / "bin" / name).chmod(0o755)

    def run(self, script):
        env = dict(os.environ, HB_TEST_ROOT=shell_path(self.root), HB_TEST_TOTAL=str(len(self.stages)))
        if script == "ring_play.sh":
            self.write("runtime/vib_ring_active", str(int(time.time())))
        result = subprocess.run(
            [bash_path(), "--noprofile", "--norc", "-c",
             'export PATH="$HB_TEST_ROOT/bin:/usr/bin:/bin"; exec bash "$HB_TEST_ROOT/module/' + script + '"'],
            env=env, capture_output=True, text=True, timeout=30,
        )
        if result.returncode != 0:
            raise AssertionError(f"{script} exited {result.returncode}: {result.stderr}")
        return (self.root / "events").read_text().splitlines()

    def snapshots(self):
        return [line.split("|") for line in (self.root / "snapshots").read_text().splitlines()]

    def close(self):
        self.temp.cleanup()


class RingerModeTests(unittest.TestCase):
    def sandbox(self, stages, **kwargs):
        sandbox = Sandbox(stages, **kwargs)
        self.addCleanup(sandbox.close)
        return sandbox

    def assert_profiles(self, sandbox, expected):
        snapshots = sandbox.snapshots()
        self.assertEqual(len(snapshots), len(expected))
        for row, allowed in zip(snapshots, expected):
            with self.subTest(stage=row[0]):
                self.assertEqual(row[1:4], ["9500"] * 3 if allowed else ["5500", "4800", "1280"])
                self.assertEqual(row[4], "1" if allowed else "0")

    def test_phone_and_voip_respect_live_ringer_mode(self):
        for source in ("phone", "voip"):
            with self.subTest(source=source):
                states = [stage(mode, source=source) for mode in ("SILENT", "NORMAL", "VIBRATE", None)]
                states += [stage(source=source, fail=True),
                           stage(source=source, external="SILENT"),
                           stage("SILENT", source=source, external="NORMAL")]
                box = self.sandbox(states, voip=source == "voip")
                events = box.run("watch.sh")
                self.assert_profiles(box, [False, True, True, False, False, False, False])
                # Worker launch is asynchronous; assert its total and verify
                # each stage's actual profile/token instead of launch timing.
                self.assertEqual(sum(event.startswith("setsid|") for event in events), 2)
                self.assertFalse(any("synced oneshot" in event for event in events))
                self.assertTrue(any(event.startswith("cmd|3|vibrator_manager cancel") for event in events))

    def test_silent_answer_latches_voip_and_prevents_late_hint(self):
        states = [stage(source="voip"), stage("SILENT", source="voip"),
                  stage("SILENT", source="voip", audio_mode="MODE_IN_COMMUNICATION"),
                  stage(source="voip", audio_mode="MODE_IN_COMMUNICATION", hint=True)]
        # AudioService must override a stale telecom ringtone / ringing list.
        states[2]["telecom"] = telecom("MODE_RINGTONE", ringing=True)
        states[3]["telecom"] = telecom("MODE_RINGTONE", ringing=True)
        box = self.sandbox(states, voip=True)
        events = box.run("watch.sh")
        self.assert_profiles(box, [True, False, False, False])
        rows = box.snapshots()
        self.assertEqual(rows[2][5:7], ["1", "0"])
        self.assertEqual(rows[3][5:7], ["1", "0"])
        self.assertTrue(any(event.startswith("cmd|1|vibrator_manager cancel") for event in events))
        self.assertEqual(sum(event.startswith("setsid|") for event in events), 1)

    def test_answered_system_call_overrides_stale_ringing_list(self):
        states = [stage(), stage("SILENT"), stage("SILENT", audio_mode="MODE_IN_CALL"),
                  stage(audio_mode="MODE_IN_CALL")]
        for fixture in states[2:]:
            fixture["telecom"] = telecom("MODE_IN_CALL", ringing=True)
        box = self.sandbox(states)
        events = box.run("watch.sh")
        self.assert_profiles(box, [True, False, False, False])
        self.assertEqual(sum(event.startswith("setsid|") for event in events), 1)
        self.assertTrue(any(event.startswith("cmd|1|vibrator_manager cancel") for event in events))

    def test_ring_worker_checks_mode_before_each_pulse(self):
        scenarios = [([stage(), stage("VIBRATE"), stage("SILENT")], 2),
                     ([stage("2"), stage("1"), stage("0")], 2),
                     ([stage("SILENT")], 0),
                     ([stage(), stage(None)], 1),
                     ([stage(), stage(fail=True)], 1),
                     ([stage(), stage(external="SILENT")], 1)]
        for states, count in scenarios:
            with self.subTest(modes=[state["audio"] for state in states], timeout=states[-1]["audio_exit"]):
                box = self.sandbox(states)
                events = box.run("ring_play.sh")
                pulses = [event for event in events if "vibrator_manager synced oneshot" in event]
                self.assertEqual(len(pulses), count)
                self.assertEqual([int(event.split("|")[1]) for event in pulses], list(range(count)))
                for event in pulses:
                    index = events.index(event)
                    self.assertEqual(events[index - 1], f"dumpsys|{event.split('|')[1]}|audio")
                self.assertTrue(events[-1].endswith("vibrator_manager cancel"))
                self.assertFalse((box.root / "runtime/vib_ring_play.pid").exists())


if __name__ == "__main__":
    unittest.main(verbosity=2)
