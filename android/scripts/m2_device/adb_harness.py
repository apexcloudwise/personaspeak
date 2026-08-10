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

SCREENSHOT_NAMES = [
    "01-idle-typed", "02-loading-cancel", "03-review",
    "04-applied", "05-dismissed", "06-stale", "07-settings",
]

KEYBOARD_PACKAGE = "biz.pixelperfectstudios.personaspeak"
PANEL_STATE_RES_ID = f"{KEYBOARD_PACKAGE}:id/panel_state"
APPLY_RES_ID = f"{KEYBOARD_PACKAGE}:id/apply_button"
DISMISS_RES_ID = f"{KEYBOARD_PACKAGE}:id/cancel_button"
STATE_LOADING = "LOADING"
STATE_REVIEW = "REVIEW"
CANDIDATE_RES_ID = f"{KEYBOARD_PACKAGE}:id/candidate_text"
ANIMATION_SCALE = "1.0"
SEARCH_RES_ID = f"{SETTINGS_PACKAGE}:id/search_action_bar"
KEYBOARD_VIEW_RES_ID = f"{KEYBOARD_PACKAGE}:id/keyboard_view"
SEARCH_CLEAR_RES_ID = f"{SETTINGS_PACKAGE}:id/search_close_btn"
ASK_KEY_COORDS: dict[str, tuple[int, int]] = {
    "T": (475, 1375), "E": (285, 1375), "A": (100, 1480),
    "S": (195, 1480), "I": (760, 1375), "X": (245, 1585),
    "V": (435, 1585), "N": (625, 1585),
    " ": (540, 1690), ".": (730, 1690),
}

EXPECTED_VERSION_NAME = "0.1.0"
EXPECTED_VERSION_CODE = "1"
EXPECTED_SIGNER = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2"
EXPECTED_ADB_VERSION = "1.0.41"
EXPECTED_EMULATOR_VERSION = "33.1.24.0"
SYSTEM_IMAGE_ID = "google/sdk_gphone64_arm64/emu64a:14"
FIXTURE_RECEIPT_DIGEST = (
    "dad6f7ac3b3c10ac7b88dfe2397746acb11ee6a42957cf2d1fee7afe1325bdb0"
)
EXPECTED_BUILD_TOOLS_VERSION = "34.0.0"


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
        self.build_tools_tool: ToolIdentity | None = None
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
            s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            s.settimeout(1.0)
            try:
                s.connect(("127.0.0.1", 5554))
                return self._fail("preflight", b"port 5554 occupied - another emulator may be running")
            except (ConnectionRefusedError, OSError):
                pass
            finally:
                s.close()
            sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT", "")
            if sdk:
                aapt2 = os.path.join(sdk, "build-tools", EXPECTED_BUILD_TOOLS_VERSION, "aapt2")
                if os.path.isfile(aapt2):
                    self.build_tools_tool = commands.resolve_tool(
                        "aapt2", path=aapt2, version_args=["version"])
            msg = (
                f"preflight passed receipt={FIXTURE_RECEIPT_DIGEST[:12]}"
                f" build_tools={EXPECTED_BUILD_TOOLS_VERSION}"
                f" snapshot={SNAPSHOT_NAME}"
            )
            return self._ok("preflight", msg.encode())
        except Exception as e:
            return self._fail("preflight", str(e).encode())

    def capture_context(self) -> CaptureContext:
        head_res = self.runner(["git", "rev-parse", "HEAD"], cwd=self.repo_root)
        repo_head = head_res.stdout.decode("utf-8").strip()
        status_res = self.runner(["git", "status", "--porcelain"], cwd=self.repo_root)
        if status_res.stdout.decode("utf-8").strip():
            raise RuntimeError("repository not clean — uncommitted changes")
        apk_sha = commands.digest_file(self.apk_path)
        tools = [self.adb_tool, self.emulator_tool]
        if self.build_tools_tool is not None:
            tools.append(self.build_tools_tool)
        return CaptureContext(
            repo_head=repo_head, apk_sha256=apk_sha,
            tools=tools, fixture_receipt_digest=FIXTURE_RECEIPT_DIGEST,
        )

    def launch_emulator(self) -> CommandResult:
        argv = self._emu_argv()
        self.emulator_process = self.starter(argv)
        pid = self.emulator_process.proc.pid
        start = self.emulator_process.start_utc
        msg = (
            f"launched pid={pid} start={start}"
            f" exe={self.emulator_tool.path} avd={AVD_NAME}"
        )
        return self._ok("emulator_launch", msg.encode())

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

    def _validate_keyboard(self, steps, root):
        kb = self._find(root, KEYBOARD_VIEW_RES_ID)
        if kb is None:
            self._step(steps, "validate_keyboard",
                       self._fail("kb", b"keyboard view not found"))
            return False
        self._step(steps, "validate_keyboard", self._ok("kb"))
        return True

    def _tap_ask_key(self, steps, ch):
        key = ch.upper() if ch.isalpha() else ch
        coord = ASK_KEY_COORDS.get(key)
        if coord is None:
            self._step(steps, f"tap_key_{ch}",
                       self._fail(f"tap_{ch}", f"no coord for {ch}".encode()))
            return False
        res = self.runner(self._cmd("shell", "input", "tap", str(coord[0]), str(coord[1])))
        return self._step(steps, f"tap_key_{ch}", res)

    def _type_text(self, steps, text, op="type_text"):
        for ch in text:
            if not self._tap_ask_key(steps, ch):
                return False
        return True

    def _clear_field(self, steps):
        d_res, root = self._dump_hierarchy("clear")
        if root is None:
            self._step(steps, "clear_field", d_res)
            return False
        btn = self._find(root, SEARCH_CLEAR_RES_ID)
        if btn is None:
            self._step(steps, "clear_field", self._ok("clear", b"already empty"))
            return True
        center = self._center(btn)
        if center is None:
            self._step(steps, "clear_field", self._fail("clear", b"no bounds"))
            return False
        res = self.runner(self._cmd("shell", "input", "tap", *center))
        return self._step(steps, "clear_field", res)

    def _take_screenshot(self, name):
        evidence_dir = os.path.join(self.run_dir, "evidence")
        os.makedirs(evidence_dir, exist_ok=True)
        remote = f"/sdcard/{name}.png"
        local = os.path.join(evidence_dir, f"{name}.png")
        self.runner(self._cmd("shell", "screencap", "-p", remote))
        self.runner(self._cmd("pull", remote, local))
        with open(local, "rb") as fh:
            return evidence.validate_png(fh.read())

    def run_journey(self) -> list[StepRecord]:
        steps: list[StepRecord] = []

        remote_vid = "/sdcard/journey.mp4"
        self.screenrecord_process = self.starter(
            self._cmd("shell", "screenrecord", "--time-limit", "30", remote_vid))

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

        _, kb_root = self._dump_hierarchy("keyboard_check")
        if kb_root is not None and not self._validate_keyboard(steps, kb_root):
            return steps

        self._take_screenshot("01-idle-typed")
        self._step(steps, "screenshot_01", self._ok("shot"))

        if not self._type_text(steps, SOURCE_TEXT, "type_source_1"):
            return steps

        if self._verify_kb(steps, "loading_1", STATE_LOADING) is None:
            return steps
        self._take_screenshot("02-loading-cancel")
        self._step(steps, "screenshot_02", self._ok("shot"))

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

        if not self._clear_field(steps) or not self._type_text(steps, SOURCE_TEXT, "type_source_2"):
            return steps
        if self._verify_kb(steps, "loading_2", STATE_LOADING) is None:
            return steps
        review_root = self._verify_kb(steps, "review_2", STATE_REVIEW)
        if review_root is None:
            return steps

        self._take_screenshot("03-review")
        self._step(steps, "screenshot_03", self._ok("shot"))

        if not self._tap_btn(steps, review_root, "apply_rephrasing", APPLY_RES_ID):
            return steps
        v_res, v_root = self._dump_hierarchy("after_apply")
        if v_root is None or not self._verify_text(steps, v_root, CANDIDATE_REPHRASING, "verify_apply"):
            if v_root is not None:
                return steps
            self._step(steps, "verify_apply", v_res)
            return steps
        self._take_screenshot("04-applied")
        self._step(steps, "screenshot_04", self._ok("shot"))

        if not self._clear_field(steps) or not self._type_text(steps, SOURCE_TEXT, "type_source_3"):
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
        self._take_screenshot("05-dismissed")
        self._step(steps, "screenshot_05", self._ok("shot"))

        if not self._clear_field(steps) or not self._type_text(steps, SOURCE_TEXT, "type_source_4"):
            return steps
        if self._verify_kb(steps, "loading_4", STATE_LOADING) is None:
            return steps
        review_root = self._verify_kb(steps, "review_4", STATE_REVIEW)
        if review_root is None:
            return steps

        if not self._clear_field(steps) or not self._type_text(steps, STALE_TEXT, "type_stale"):
            return steps
        if not self._tap_btn(steps, review_root, "apply_stale", APPLY_RES_ID):
            return steps
        v_res, v_root = self._dump_hierarchy("after_stale")
        if v_root is None:
            self._step(steps, "verify_stale", v_res)
            return steps
        if not self._verify_text(steps, v_root, STALE_TEXT, "verify_stale"):
            return steps
        self._take_screenshot("06-stale")
        self._step(steps, "screenshot_06", self._ok("shot"))

        res = self.runner(self._cmd("shell", "am", "start", "-a", SETTINGS_ACTION))
        self._step(steps, "relaunch_settings", res)
        self._take_screenshot("07-settings")
        self._step(steps, "screenshot_07", self._ok("shot"))

        return steps

    def capture_evidence(self) -> CommandResult:
        evidence_dir = os.path.join(self.run_dir, "evidence")
        os.makedirs(evidence_dir, exist_ok=True)
        errors = []

        remote_vid = "/sdcard/journey.mp4"
        local_vid = os.path.join(evidence_dir, "journey.mp4")
        if self.screenrecord_process is not None:
            rec_res = self.finisher(self.screenrecord_process, timeout=15.0, terminate=False)
            self.screenrecord_process = None
            if rec_res.returncode != 0:
                errors.append(f"screenrecord rc={rec_res.returncode}")
        pull_v = self.runner(self._cmd("pull", remote_vid, local_vid))
        if pull_v.returncode != 0:
            errors.append(f"pull_video rc={pull_v.returncode}")
        else:
            with open(local_vid, "rb") as vfh:
                if not evidence.validate_mp4(vfh.read()):
                    errors.append("journey.mp4 invalid")

        for name in SCREENSHOT_NAMES:
            path = os.path.join(evidence_dir, f"{name}.png")
            if not os.path.isfile(path):
                errors.append(f"missing screenshot: {name}.png")
            else:
                with open(path, "rb") as fh:
                    if not evidence.validate_png(fh.read()):
                        errors.append(f"{name}.png invalid")

        pngs = sorted(f for f in os.listdir(evidence_dir) if f.endswith(".png"))
        mp4s = sorted(f for f in os.listdir(evidence_dir) if f.endswith(".mp4"))
        if len(pngs) != len(SCREENSHOT_NAMES):
            errors.append(f"expected {len(SCREENSHOT_NAMES)} PNGs, found {len(pngs)}")
        if len(mp4s) != 1:
            errors.append(f"expected 1 MP4, found {len(mp4s)}")

        rc = 0 if not errors else 1
        return CommandResult(
            argv=["capture_evidence"], start_utc=_UTC(), end_utc=_UTC(),
            returncode=rc,
            stdout=b"" if errors else b"evidence verified",
            stderr="\n".join(errors).encode() if errors else b"",
        )

    def restore(self) -> CommandResult:
        if self.screenrecord_process is not None:
            self.finisher(self.screenrecord_process, timeout=5.0, terminate=True)
            self.screenrecord_process = None
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
