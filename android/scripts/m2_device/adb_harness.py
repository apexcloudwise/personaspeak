"""Production harness implementation conforming to JourneyHarness protocol."""

from __future__ import annotations

import os
import re
import socket
import xml.etree.ElementTree as ET
from datetime import datetime, timezone
from typing import Any

from android.scripts.m2_device import commands, evidence
from android.scripts.m2_device.orchestrator import CaptureContext
from android.scripts.m2_device.records import (
    CommandResult,
    PriorDeviceState,
    RemoteResult,
    StepRecord,
    TerminalCause,
    ToolIdentity,
)

_UTC = lambda: datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

AVD_NAME = "M2_Qual_Fixture"
SNAPSHOT_NAME = "m2_pristine"
SYSTEM_IMAGE = "system-images;android-34;google_apis;arm64-v8a"
API_LEVEL = 34
ABI = "arm64-v8a"
FINGERPRINT = (
    "google/sdk_gphone64_arm64/emu64a:14/UE1A.230829.050/12077443:userdebug/dev-keys"
)
LOCALE = "en-US"
TIMEZONE = "Asia/Kolkata"
SCREEN_WIDTH = 1080
SCREEN_HEIGHT = 2400
SCREEN_DPI = 420

RAM_BIN_HASH = "a46053dddc85a1bfc2be298a955bce07a14fb6dbe183bff6052ee727fcfee6f1"
TEXTURES_BIN_HASH = (
    "23661254fc0982e69795a9486e8c23bc85802ff57faf118f22d11937f489e68d"
)
HARDWARE_INI_HASH = (
    "076562d6c8733c97b2818c51c0e571d2052962d8dff30b9905c2ecf4d049a3a3"
)

SETTINGS_PACKAGE = "com.android.settings"
SETTINGS_ACTION = "android.settings.SETTINGS"
SOURCE_TEXT = "Tea at six."
STALE_TEXT = "Tea at seven."
CANDIDATE_REPHRASING = (
    "I have taken the liberty, sir, of rephrasing your words: "
    "\u201cTea at six.\u201d \u2014 though I must confess the genuine article is still en route."
)

KEYBOARD_PACKAGE = "biz.pixelperfectstudios.personaspeak"
PANEL_STATE_RES_ID = f"{KEYBOARD_PACKAGE}:id/panel_state"
APPLY_RES_ID = f"{KEYBOARD_PACKAGE}:id/apply_button"
DISMISS_RES_ID = f"{KEYBOARD_PACKAGE}:id/cancel_button"
STATE_LOADING = "LOADING"
STATE_REVIEW = "REVIEW"
CANDIDATE_RES_ID = f"{KEYBOARD_PACKAGE}:id/candidate_text"
ANIMATION_SCALE = "1.0"
SEARCH_RES_ID = f"{SETTINGS_PACKAGE}:id/search_action_bar"

EXPECTED_VERSION_NAME = "0.1.0"
EXPECTED_VERSION_CODE = "1"
EXPECTED_SIGNER = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2"
EXPECTED_ADB_VERSION = "1.0.41"
EXPECTED_EMULATOR_VERSION = "33.1.24.0"
SYSTEM_IMAGE_ID = "google/sdk_gphone64_arm64/emu64a:14"


class AdbHarness:

    def __init__(
        self,
        run_dir: str,
        apk_path: str,
        serial: str = "emulator-5554",
        runner: Any = None,
        starter: Any = None,
        finisher: Any = None,
        repo_root: str = "",
    ):
        self.run_dir = run_dir
        self.apk_path = apk_path
        self.serial = serial
        self.runner = runner or commands.run
        self.starter = starter or commands.start
        self.finisher = finisher or commands.finish
        self.repo_root = repo_root or os.getcwd()
        self.adb_tool: ToolIdentity | None = None
        self.emulator_tool: ToolIdentity | None = None
        self.emulator_process: commands.ManagedProcess | None = None
        self.screenrecord_process: commands.ManagedProcess | None = None

    @property
    def _adb(self) -> str:
        if self.adb_tool is None:
            raise RuntimeError("adb not resolved")
        return self.adb_tool.path

    def _cmd(self, *args: str) -> list[str]:
        return [self._adb, "-s", self.serial, *args]

    def _emu_argv(self) -> list[str]:
        if self.emulator_tool is None:
            raise RuntimeError("emulator not resolved")
        return [
            self.emulator_tool.path, "-avd", AVD_NAME,
            "-snapshot", SNAPSHOT_NAME, "-no-snapshot-save", "-port", "5554",
        ]

    @staticmethod
    def _ok(op: str, stdout: bytes = b"") -> CommandResult:
        return CommandResult(
            argv=[op], start_utc=_UTC(), end_utc=_UTC(),
            returncode=0, stdout=stdout, stderr=b"",
        )

    @staticmethod
    def _fail(op: str, stderr: bytes = b"") -> CommandResult:
        return CommandResult(
            argv=[op], start_utc=_UTC(), end_utc=_UTC(),
            returncode=1, stdout=b"", stderr=stderr,
        )

    def preflight(self) -> CommandResult:
        try:
            self.adb_tool = commands.resolve_tool("adb")
            self.emulator_tool = commands.resolve_tool("emulator")
            if EXPECTED_ADB_VERSION not in self.adb_tool.version:
                return self._fail("preflight", f"adb version mismatch: {self.adb_tool.version}".encode())
            if EXPECTED_EMULATOR_VERSION not in self.emulator_tool.version:
                return self._fail("preflight", f"emulator version mismatch: {self.emulator_tool.version}".encode())
            if self.adb_tool.digest is None or self.emulator_tool.digest is None:
                return self._fail("preflight", b"tool digest unavailable")
            avds = self.runner([self.emulator_tool.path, "-list-avds"], timeout=10)
            if AVD_NAME not in avds.stdout.decode("utf-8", errors="replace"):
                return self._fail("preflight", f"AVD {AVD_NAME} not found".encode())
            return self._ok("preflight", b"Preflight check passed.")
        except Exception as e:
            return self._fail("preflight", str(e).encode())

    def capture_context(self) -> CaptureContext:
        head_res = self.runner(["git", "rev-parse", "HEAD"], cwd=self.repo_root)
        repo_head = head_res.stdout.decode("utf-8").strip()
        status_res = self.runner(["git", "status", "--porcelain"], cwd=self.repo_root)
        if status_res.stdout.decode("utf-8").strip():
            raise RuntimeError("repository not clean — uncommitted changes")
        apk_sha = commands.digest_file(self.apk_path)
        return CaptureContext(
            repo_head=repo_head, apk_sha256=apk_sha,
            tools=[self.adb_tool, self.emulator_tool],
        )

    def launch_emulator(self) -> CommandResult:
        argv = self._emu_argv()
        self.emulator_process = self.starter(argv)
        return self._ok("emulator_launch", b"Emulator launch initiated.")

    def attach(self) -> CommandResult:
        return self.runner(self._cmd("wait-for-device"), timeout=30.0)

    @staticmethod
    def _out(res: CommandResult) -> str:
        if res.returncode != 0:
            raise ValueError(f"command rc={res.returncode}")
        return res.stdout.decode("utf-8").strip()

    def capture_prior_state(self) -> PriorDeviceState | None:
        def _run(*args):
            return self.runner(self._cmd(*args))

        try:
            boot = self._out(_run("shell", "getprop", "sys.boot_completed"))
            fp = self._out(_run("shell", "getprop", "ro.build.fingerprint"))
            sdk_raw = self._out(_run("shell", "getprop", "ro.build.version.sdk"))
            api_level = int(sdk_raw)
            size_str = self._out(_run("shell", "wm", "size"))
            m = re.search(r"(\d+)x(\d+)", size_str)
            if not m:
                return None
            sw, sh = int(m.group(1)), int(m.group(2))
            pkg = self._out(_run("shell", "pm", "path", KEYBOARD_PACKAGE))
            package_present = pkg.startswith("package:")
            package_hash = None
            if package_present:
                dev_path = pkg.split(":", 1)[1].strip()
                h = self._out(_run("shell", "sha256sum", dev_path))
                if h:
                    package_hash = h.split()[0]
            ime = self._out(_run("shell", "settings", "get", "secure", "enabled_input_methods"))
            enabled_imes = [x for x in ime.split(":") if x]
            default = self._out(_run("shell", "settings", "get", "secure", "default_input_method"))
        except (ValueError, UnicodeDecodeError):
            return None

        return PriorDeviceState(
            serial=self.serial,
            emulator_state="booted" if boot == "1" else "unknown",
            fingerprint=fp, api_level=api_level,
            screen_width=sw, screen_height=sh,
            package_present=package_present, package_hash=package_hash,
            enabled_imes=enabled_imes, default_ime=default,
        )

    def validate_fixture(self, prior: PriorDeviceState) -> CommandResult:
        errors = []
        if prior.fingerprint != FINGERPRINT:
            errors.append(f"fingerprint mismatch: got {prior.fingerprint}")
        if not prior.fingerprint.startswith(SYSTEM_IMAGE_ID):
            errors.append(f"system image mismatch: {prior.fingerprint}")
        if prior.api_level != API_LEVEL:
            errors.append(f"api_level mismatch: got {prior.api_level}")
        if prior.screen_width != SCREEN_WIDTH or prior.screen_height != SCREEN_HEIGHT:
            errors.append(f"screen size mismatch: got {prior.screen_width}x{prior.screen_height}")
        if prior.package_present:
            errors.append(f"{KEYBOARD_PACKAGE} present before test")

        tz = self.runner(self._cmd("shell", "getprop", "persist.sys.timezone")).stdout.decode().strip()
        if tz != TIMEZONE:
            errors.append(f"timezone mismatch: got {tz}")
        loc = self.runner(self._cmd("shell", "getprop", "ro.product.locale")).stdout.decode().strip()
        if loc != LOCALE:
            errors.append(f"locale mismatch: got {loc}")

        gboard = "com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME"
        if gboard not in prior.enabled_imes:
            errors.append("Gboard IME not enabled")

        try:
            abi = self._out(self.runner(self._cmd("shell", "getprop", "ro.product.cpu.abi")))
            if abi != ABI:
                errors.append(f"ABI mismatch: got {abi}")
            density = self._out(self.runner(self._cmd("shell", "getprop", "ro.sf.lcd_density")))
            if density != str(SCREEN_DPI):
                errors.append(f"density mismatch: got {density}")
            anim = self._out(self.runner(
                self._cmd("shell", "settings", "get", "global", "window_animation_scale")))
            if anim != ANIMATION_SCALE:
                errors.append(f"animation scale mismatch: got {anim}")
        except ValueError:
            errors.append("fixture property query failed")

        if errors:
            return self._fail("validate_fixture", "\n".join(errors).encode())
        return self._ok("validate_fixture", b"Fixture identity validated.")

    def install_apk(self) -> CommandResult:
        res = self.runner(self._cmd("install", "-r", self.apk_path))
        if res.returncode != 0:
            return res
        dump = self.runner(self._cmd("shell", "dumpsys", "package", KEYBOARD_PACKAGE))
        out = dump.stdout.decode("utf-8", errors="replace")
        errors = []
        if f"versionName={EXPECTED_VERSION_NAME}" not in out:
            errors.append(f"versionName mismatch: expected {EXPECTED_VERSION_NAME}")
        if f"versionCode={EXPECTED_VERSION_CODE}" not in out:
            errors.append(f"versionCode mismatch: expected {EXPECTED_VERSION_CODE}")
        if EXPECTED_SIGNER not in out:
            errors.append("signer certificate mismatch")
        if errors:
            return self._fail("install_apk", "\n".join(errors).encode())
        return self._ok("install_apk", b"APK installed and identity verified.")

    def _dump_hierarchy(self, label: str) -> tuple[CommandResult, Any]:
        remote = "/sdcard/window_dump.xml"
        local = os.path.join(self.run_dir, f"{label}.xml")
        dump = self.runner(self._cmd("shell", "uiautomator", "dump", remote))
        if dump.returncode != 0:
            return dump, None
        pull = self.runner(self._cmd("pull", remote, local))
        if pull.returncode != 0:
            return pull, None
        try:
            return pull, ET.parse(local).getroot()
        except ET.ParseError:
            return pull, None

    def _step(
        self, steps: list[StepRecord], op: str, result: CommandResult,
        fail: TerminalCause = TerminalCause.JOURNEY_FAILED,
    ) -> bool:
        ok = result.returncode == 0
        steps.append(StepRecord(
            phase="journey", operation=op, input_digest=None,
            output_digest=None, result=result,
            cause=TerminalCause.COMPLETED if ok else fail))
        return ok

    @staticmethod
    def _find(root: Any, res_id: str) -> Any | None:
        for elem in root.iter():
            if elem.attrib.get("resource-id", "") == res_id:
                return elem
        return None

    @staticmethod
    def _center(elem: Any) -> tuple[str, str] | None:
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", elem.attrib.get("bounds", ""))
        if not m:
            return None
        x = (int(m.group(1)) + int(m.group(3))) // 2
        y = (int(m.group(2)) + int(m.group(4))) // 2
        return str(x), str(y)

    def _verify_kb(self, steps, label, expected):
        res, root = self._dump_hierarchy(label)
        if root is None:
            self._step(steps, f"verify_{label}", res)
            return None
        panel = self._find(root, PANEL_STATE_RES_ID)
        ok = panel is not None and panel.attrib.get("text", "") == expected
        self._step(steps, f"verify_{label}",
                   self._ok(label) if ok else self._fail(label, f"expected {expected}".encode()))
        return root if ok else None

    def _tap_btn(self, steps, root, op, res_id):
        btn = self._find(root, res_id)
        if btn is None:
            self._step(steps, op, self._fail(op, f"{res_id} not found".encode()))
            return None
        center = self._center(btn)
        if center is None:
            self._step(steps, op, self._fail(op, b"bounds missing"))
            return None
        res = self.runner(self._cmd("shell", "input", "tap", *center))
        if not self._step(steps, op, res):
            return None
        return True

    def _verify_text(self, steps, root, expected, op="verify_text"):
        field = self._find(root, SEARCH_RES_ID)
        if field is None:
            self._step(steps, op, self._fail(op, b"editor not found"))
            return False
        actual = field.attrib.get("text", "")
        ok = actual == expected
        self._step(steps, op,
                   self._ok(op) if ok else self._fail(op, f"got {actual[:40]}".encode()))
        return ok

    def _clear_field(self, steps):
        for _ in range(len(CANDIDATE_REPHRASING) + len(SOURCE_TEXT) + 10):
            del_res = self.runner(self._cmd("shell", "input", "keyevent", "67"))
            if del_res.returncode != 0:
                self._step(steps, "clear_field", del_res)
                return False
        self._step(steps, "clear_field", self._ok("clear"))
        return True

    def _type_source(self, steps):
        res = self.runner(self._cmd("shell", "input", "text", SOURCE_TEXT.replace(" ", "%s")))
        return self._step(steps, "type_source_text", res)

    def _verify_idle(self, steps, root, label):
        panel = self._find(root, PANEL_STATE_RES_ID)
        active = panel is not None and panel.attrib.get("text", "") in (STATE_LOADING, STATE_REVIEW)
        self._step(steps, f"verify_idle_{label}",
                   self._ok(label) if not active else self._fail(label, b"keyboard still active"))
        return not active

    def run_journey(self) -> list[StepRecord]:
        steps: list[StepRecord] = []

        res = self.runner(self._cmd("shell", "am", "start", "-a", SETTINGS_ACTION))
        if not self._step(steps, "launch_editor", res):
            return steps

        d_res, root = self._dump_hierarchy("journey")
        if root is None:
            self._step(steps, "dump_hierarchy", d_res)
            return steps
        self._step(steps, "dump_hierarchy", d_res)

        field = self._find(root, SEARCH_RES_ID)
        if field is None:
            self._step(steps, "locate_editor", self._fail("locate", b"not found"))
            return steps
        center = self._center(field)
        if center is None:
            self._step(steps, "locate_editor", self._fail("locate", b"bounds"))
            return steps
        res = self.runner(self._cmd("shell", "input", "tap", *center))
        if not self._step(steps, "focus_editor", res):
            return steps

        res = self.runner(self._cmd("shell", "input", "text", STALE_TEXT.replace(" ", "%s")))
        if not self._step(steps, "type_stale_text", res):
            return steps
        if not self._clear_field(steps):
            return steps

        if not self._type_source(steps):
            return steps
        if self._verify_kb(steps, "loading_1", STATE_LOADING) is None:
            return steps
        review_root = self._verify_kb(steps, "review_1", STATE_REVIEW)
        if review_root is None:
            return steps

        if not self._tap_btn(steps, review_root, "cancel_rephrasing", DISMISS_RES_ID):
            return steps
        v_res, v_root = self._dump_hierarchy("after_cancel")
        if v_root is None:
            self._step(steps, "verify_cancel_unchanged", v_res)
            return steps
        if not self._verify_idle(steps, v_root, "after_cancel"):
            return steps
        if not self._verify_text(steps, v_root, SOURCE_TEXT, "verify_cancel_unchanged"):
            return steps

        if not self._clear_field(steps) or not self._type_source(steps):
            return steps
        if self._verify_kb(steps, "loading_2", STATE_LOADING) is None:
            return steps
        review_root = self._verify_kb(steps, "review_2", STATE_REVIEW)
        if review_root is None:
            return steps

        if not self._tap_btn(steps, review_root, "apply_rephrasing", APPLY_RES_ID):
            return steps
        v_res, v_root = self._dump_hierarchy("after_apply")
        if v_root is None or not self._verify_text(steps, v_root, CANDIDATE_REPHRASING, "verify_apply"):
            if v_root is not None:
                return steps
            self._step(steps, "verify_apply", v_res)
            return steps

        if not self._clear_field(steps) or not self._type_source(steps):
            return steps
        if self._verify_kb(steps, "loading_3", STATE_LOADING) is None:
            return steps
        review_root = self._verify_kb(steps, "review_3", STATE_REVIEW)
        if review_root is None:
            return steps

        if not self._tap_btn(steps, review_root, "dismiss_rephrasing", DISMISS_RES_ID):
            return steps
        v_res, v_root = self._dump_hierarchy("after_dismiss")
        if v_root is None:
            self._step(steps, "verify_dismiss_unchanged", v_res)
            return steps
        if not self._verify_idle(steps, v_root, "after_dismiss"):
            return steps
        if not self._verify_text(steps, v_root, SOURCE_TEXT, "verify_dismiss_unchanged"):
            return steps

        return steps

    def capture_evidence(self) -> CommandResult:
        evidence_dir = os.path.join(self.run_dir, "evidence")
        os.makedirs(evidence_dir, exist_ok=True)
        errors = []

        for i in range(7):
            remote = f"/sdcard/shot_{i}.png"
            local = os.path.join(evidence_dir, f"shot_{i}.png")
            cap = self.runner(self._cmd("shell", "screencap", "-p", remote))
            if cap.returncode != 0:
                errors.append(f"screencap_{i} rc={cap.returncode}")
                continue
            pull = self.runner(self._cmd("pull", remote, local))
            if pull.returncode != 0:
                errors.append(f"pull_shot_{i} rc={pull.returncode}")
                continue
            with open(local, "rb") as fh:
                if not evidence.validate_png(fh.read()):
                    errors.append(f"shot_{i}.png invalid")

        remote_vid = "/sdcard/journey.mp4"
        local_vid = os.path.join(evidence_dir, "journey.mp4")
        rec = self.starter(self._cmd("shell", "screenrecord", "--time-limit", "10", remote_vid))
        rec_res = self.finisher(rec, timeout=15.0, terminate=False)
        if rec_res.returncode != 0:
            errors.append(f"screenrecord rc={rec_res.returncode}")
        else:
            pull_v = self.runner(self._cmd("pull", remote_vid, local_vid))
            if pull_v.returncode != 0:
                errors.append(f"pull_video rc={pull_v.returncode}")
            else:
                with open(local_vid, "rb") as vfh:
                    if not evidence.validate_mp4(vfh.read()):
                        errors.append("journey.mp4 invalid")

        pngs = sorted(f for f in os.listdir(evidence_dir) if f.endswith(".png"))
        mp4s = sorted(f for f in os.listdir(evidence_dir) if f.endswith(".mp4"))
        if len(pngs) != 7:
            errors.append(f"expected 7 PNGs, found {len(pngs)}")
        if len(mp4s) != 1:
            errors.append(f"expected 1 MP4, found {len(mp4s)}")

        rc = 0 if not errors else 1
        return CommandResult(
            argv=["capture_evidence"], start_utc=_UTC(), end_utc=_UTC(),
            returncode=rc,
            stdout=b"" if errors else b"7 screenshots + 1 video captured",
            stderr="\n".join(errors).encode() if errors else b"",
        )

    def restore(self) -> CommandResult:
        return self.runner(self._cmd("emu", "snapshot", "load", SNAPSHOT_NAME))

    def verify_restore(self) -> PriorDeviceState:
        state = self.capture_prior_state()
        if state is None:
            raise RuntimeError("verification prior state unavailable")
        return state

    def release_emulator(self) -> CommandResult:
        if self.emulator_process is not None:
            res = self.finisher(self.emulator_process, terminate=True)
            self.emulator_process = None
            return res
        return self.runner(self._cmd("emu", "kill"))

    def verify_release(self) -> CommandResult:
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.settimeout(1.0)
        try:
            s.connect(("127.0.0.1", 5554))
        except ConnectionRefusedError:
            return self._ok("verify_release", b"release verified (port closed)")
        except OSError:
            return self._fail("verify_release", b"release inconclusive (socket error)")
        else:
            return self._fail("verify_release", b"emulator still running")
        finally:
            s.close()
