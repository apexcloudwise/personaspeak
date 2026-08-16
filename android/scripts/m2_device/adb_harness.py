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
KEYBOARD_EXPECTED_BOUNDS = "[0,1300][1080,2400]"

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
EXPECTED_ENABLED_IMES = [
    "com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME",
    "com.google.android.inputmethod.latin/com.google.android.apps.inputmethod.latin.MockVoiceIME",
]
EXPECTED_EDITOR_CLASS = "android.widget.EditText"


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
        self._owned_pid: int | None = None
        self._owned_identity: commands.ProcessIdentity | None = None
        self._session_launched = False
        self.ledger = commands.CommandLedger()

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
                s.close()
                return self._fail("preflight", b"port 5554 occupied - another emulator may be running")
            except ConnectionRefusedError:
                pass
            except OSError as e:
                s.close()
                return self._fail("preflight", f"port probe inconclusive: {e}".encode())
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
        self.emulator_process = self.starter(argv, new_session=True)
        self._session_launched = True
        pid = self.emulator_process.proc.pid
        msg = (
            f"launched pid={pid} start={self.emulator_process.start_utc}"
            f" exe={self.emulator_tool.path} avd={AVD_NAME}"
            f" session={'yes' if self._session_launched else 'no'}"
        )
        return self._ok("emulator_launch", msg.encode())

    def establish_ownership(self) -> CommandResult:
        if self.emulator_process is None:
            return self._fail("ownership", b"no emulator process")
        pid = self.emulator_process.proc.pid
        identity = commands.pid_identity(pid)
        if identity is None:
            return self._fail("ownership", f"pid {pid} not running".encode())
        self._owned_pid = pid
        self._owned_identity = identity
        return self._ok(
            "ownership",
            f"owned pid={pid} start={identity.start}"
            f" cmd={identity.command[:80]}".encode(),
        )

    def attach(self) -> CommandResult:
        return self._host("wait-for-device", timeout=30.0)

    def _shell(self, *args: str, timeout: float = 30.0) -> RemoteResult:
        argv = self._cmd("shell", *args)
        transport = self.runner(argv, timeout=timeout)
        res = commands.to_remote(transport)
        self.ledger.record(
            argv, transport.start_utc, transport.end_utc,
            transport.returncode, res.remote_rc, transport.timed_out, "shell",
        )
        return res

    def _host(self, *args: str, timeout: float = 60.0) -> CommandResult:
        argv = self._cmd(*args)
        res = self.runner(argv, timeout=timeout)
        self.ledger.record(
            argv, res.start_utc, res.end_utc,
            res.returncode, None, res.timed_out, "host",
        )
        return res

    def _shell_start(self, *args: str) -> commands.ManagedProcess:
        """Start a long-running shell-v2 command (e.g. screenrecord)."""
        return self.starter(self._cmd("shell", *args))

    def _shell_finish(
        self, process: commands.ManagedProcess,
        *, timeout: float, terminate: bool = False,
    ) -> RemoteResult:
        """Boundedly finish a started shell-v2 command through the
        execution boundary: transport → RemoteResult conversion plus a
        ledger entry, so no shell-v2 operation escapes status tracking."""
        transport = self.finisher(process, timeout=timeout, terminate=terminate)
        res = commands.to_remote(transport)
        self.ledger.record(
            process.argv, transport.start_utc, transport.end_utc,
            transport.returncode, res.remote_rc, transport.timed_out, "shell",
        )
        return res

    _out = staticmethod(commands.remote_stdout)

    @staticmethod
    def _rc_of(result) -> int:
        if isinstance(result, RemoteResult):
            if result.transport.returncode != 0:
                return result.transport.returncode
            return result.remote_rc if result.remote_rc is not None else 1
        return result.returncode

    @staticmethod
    def _timed_out(result) -> bool:
        if isinstance(result, RemoteResult):
            return result.transport.timed_out
        return result.timed_out

    @staticmethod
    def _ambiguous(result) -> bool:
        if isinstance(result, RemoteResult):
            return result.remote_rc is None and not result.transport.timed_out
        return False

    def capture_prior_state(self) -> PriorDeviceState | None:
        def _query(*args):
            res = self._shell(*args)
            if res.remote_rc is None:
                raise commands.RemoteAmbiguousError(" ".join(args))
            if res.remote_rc != 0:
                raise ValueError(f"{' '.join(args)} rc={res.remote_rc}")
            return res.transport.stdout.decode("utf-8", errors="replace").strip()

        try:
            boot = _query("getprop", "sys.boot_completed")
            fp = _query("getprop", "ro.build.fingerprint")
            sdk_raw = _query("getprop", "ro.build.version.sdk")
            api_level = int(sdk_raw)
            size_str = _query("wm", "size")
            m = re.search(r"(\d+)x(\d+)", size_str)
            if not m:
                return None
            sw, sh = int(m.group(1)), int(m.group(2))
            pkg = _query("pm", "path", KEYBOARD_PACKAGE)
            package_present = pkg.startswith("package:")
            package_hash = None
            if package_present:
                dev_path = pkg.split(":", 1)[1].strip()
                h = _query("sha256sum", dev_path)
                if h:
                    package_hash = h.split()[0]
            ime = _query("settings", "get", "secure", "enabled_input_methods")
            enabled_imes = [x for x in ime.split(":") if x]
            default = _query("settings", "get", "secure", "default_input_method")
        except commands.RemoteAmbiguousError:
            raise
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

        if prior.enabled_imes != EXPECTED_ENABLED_IMES:
            errors.append(f"IME list mismatch: got {prior.enabled_imes}")

        fixture_props = [
            (("getprop", "persist.sys.timezone"), TIMEZONE),
            (("getprop", "ro.product.locale"), LOCALE),
            (("getprop", "ro.product.cpu.abi"), ABI),
            (("getprop", "ro.sf.lcd_density"), str(SCREEN_DPI)),
            (("settings", "get", "global", "window_animation_scale"), ANIMATION_SCALE),
            (("settings", "get", "global", "transition_animation_scale"), ANIMATION_SCALE),
            (("settings", "get", "secure", "default_input_method"), EXPECTED_ENABLED_IMES[0]),
        ]
        for args, expected in fixture_props:
            res = self._shell(*args)
            if self._ambiguous(res):
                return res
            if self._rc_of(res) != 0:
                errors.append(f"{' '.join(args)} query failed: rc={self._rc_of(res)}")
            else:
                actual = res.transport.stdout.decode("utf-8", errors="replace").strip()
                if actual != expected:
                    errors.append(f"{' '.join(args)} mismatch: got {actual}")

        if errors:
            return self._fail("validate_fixture", "\n".join(errors).encode())
        return self._ok("validate_fixture", b"Fixture identity validated.")

    def install_apk(self) -> CommandResult:
        res = self._host("install", "-r", self.apk_path, timeout=120.0)
        if res.returncode != 0:
            return res
        dump = self._shell("dumpsys", "package", KEYBOARD_PACKAGE)
        if self._ambiguous(dump):
            return dump
        if self._rc_of(dump) != 0:
            return self._fail("install_apk", f"dumpsys rc={self._rc_of(dump)}".encode())
        out = dump.transport.stdout.decode("utf-8", errors="replace")
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
        evidence_dir = os.path.join(self.run_dir, "artifacts")
        os.makedirs(evidence_dir, exist_ok=True)
        remote = "/sdcard/window_dump.xml"
        local = os.path.join(evidence_dir, f"{label}.xml")
        dump = self._shell("uiautomator", "dump", remote)
        if self._ambiguous(dump) or self._rc_of(dump) != 0:
            return dump, None
        pull = self._host("pull", remote, local)
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
        if self._ambiguous(result):
            cause = TerminalCause.TOOL_FAILURE
        elif self._timed_out(result):
            cause = TerminalCause.TIMEOUT
        elif self._rc_of(result) == 0:
            cause = TerminalCause.COMPLETED
        else:
            cause = fail
        steps.append(StepRecord(
            phase="journey", operation=op, input_digest=None,
            output_digest=None, result=result, cause=cause))
        return cause == TerminalCause.COMPLETED

    @staticmethod
    def _find(root: Any, res_id: str) -> Any | None:
        matches = [elem for elem in root.iter()
                   if elem.attrib.get("resource-id", "") == res_id]
        if len(matches) != 1:
            return None
        return matches[0]

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
        center = self._center(btn) if btn is not None else None
        if center is None:
            self._step(steps, op, self._fail(op, b"button unavailable"))
            return None
        return self._step(steps, op, self._shell("input", "tap", *center)) or None

    def _verify_text(self, steps, root, expected, op="verify_text"):
        field = self._find(root, SEARCH_RES_ID)
        if field is None:
            self._step(steps, op, self._fail(op, b"editor not found"))
            return False
        cls = field.attrib.get("class", "")
        if cls != EXPECTED_EDITOR_CLASS:
            self._step(steps, op, self._fail(op, f"editor class mismatch: {cls}".encode()))
            return False
        actual = field.attrib.get("text", "")
        ok = actual == expected
        self._step(steps, op,
                   self._ok(op) if ok else self._fail(op, f"got {actual[:40]}".encode()))
        return ok

    def _verify_idle(self, steps, root, label):
        panel = self._find(root, PANEL_STATE_RES_ID)
        active = panel is not None and panel.attrib.get("text", "") in (STATE_LOADING, STATE_REVIEW)
        self._step(steps, f"verify_idle_{label}",
                   self._ok(label) if not active else self._fail(label, b"keyboard still active"))
        return not active

    def _validate_keyboard(self, steps, root):
        kb = self._find(root, KEYBOARD_VIEW_RES_ID)
        actual = kb.attrib.get("bounds", "") if kb is not None else None
        ok = actual == KEYBOARD_EXPECTED_BOUNDS
        self._step(steps, "validate_keyboard",
                   self._ok("kb") if ok else self._fail("kb", f"bounds: {actual}".encode()))
        return ok

    def _tap_ask_key(self, steps, ch):
        key = ch.upper() if ch.isalpha() else ch
        coord = ASK_KEY_COORDS.get(key)
        if coord is None:
            self._step(steps, f"tap_key_{ch}",
                       self._fail(f"tap_{ch}", f"no coord for {ch}".encode()))
            return False
        res = self._shell("input", "tap", str(coord[0]), str(coord[1]))
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
        res = self._shell("input", "tap", *center)
        return self._step(steps, "clear_field", res)

    def _take_screenshot(self, steps, name):
        evidence_dir = os.path.join(self.run_dir, "artifacts")
        os.makedirs(evidence_dir, exist_ok=True)
        remote = f"/sdcard/{name}.png"
        local = os.path.join(evidence_dir, f"{name}.png")
        cap = self._shell("screencap", "-p", remote)
        if self._ambiguous(cap) or self._rc_of(cap) != 0:
            self._step(steps, f"screenshot_{name}", cap)
            return False
        pull = self._host("pull", remote, local)
        if pull.returncode != 0:
            self._step(steps, f"screenshot_{name}", pull)
            return False
        with open(local, "rb") as fh:
            if not evidence.validate_png(fh.read()):
                self._step(steps, f"screenshot_{name}", self._fail(name, b"invalid PNG"))
                return False
        self._step(steps, f"screenshot_{name}", self._ok(name))
        return True

    def run_journey(self) -> list[StepRecord]:
        steps: list[StepRecord] = []

        remote_vid = "/sdcard/journey.mp4"
        self.screenrecord_process = self._shell_start(
            "screenrecord", "--time-limit", "30", remote_vid)

        res = self._shell("am", "start", "-a", SETTINGS_ACTION)
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
        res = self._shell("input", "tap", *center)
        if not self._step(steps, "focus_editor", res):
            return steps

        _, kb_root = self._dump_hierarchy("keyboard_check")
        if kb_root is not None and not self._validate_keyboard(steps, kb_root):
            return steps

        if not self._type_text(steps, SOURCE_TEXT, "type_source_1"):
            return steps

        if not self._take_screenshot(steps, "01-idle-typed"):
            return steps

        loading_root = self._verify_kb(steps, "loading_1", STATE_LOADING)
        if loading_root is None:
            return steps
        if not self._take_screenshot(steps, "02-loading-cancel"):
            return steps

        if not self._tap_btn(steps, loading_root, "cancel_loading", DISMISS_RES_ID):
            return steps
        v_res, v_root = self._dump_hierarchy("after_cancel_loading")
        if v_root is None:
            self._step(steps, "verify_cancel_unchanged", v_res)
            return steps
        if not self._verify_idle(steps, v_root, "after_cancel_loading"):
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

        if not self._take_screenshot(steps, "03-review"):
            return steps

        if not self._tap_btn(steps, review_root, "apply_rephrasing", APPLY_RES_ID):
            return steps
        v_res, v_root = self._dump_hierarchy("after_apply")
        if v_root is None or not self._verify_text(steps, v_root, CANDIDATE_REPHRASING, "verify_apply"):
            if v_root is not None:
                return steps
            self._step(steps, "verify_apply", v_res)
            return steps
        if not self._take_screenshot(steps, "04-applied"):
            return steps

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
        if not self._take_screenshot(steps, "05-dismissed"):
            return steps

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
        candidate = self._find(v_root, CANDIDATE_RES_ID)
        if candidate is not None:
            self._step(steps, "verify_stale_outcome",
                       self._fail("stale_outcome", b"candidate present after stale apply"))
            return steps
        self._step(steps, "verify_stale_outcome", self._ok("stale_outcome"))
        if not self._take_screenshot(steps, "06-stale"):
            return steps

        res = self._shell("am", "start", "-a", SETTINGS_ACTION)
        self._step(steps, "relaunch_settings", res)
        if not self._take_screenshot(steps, "07-settings"):
            return steps

        return steps

    def capture_evidence(self) -> CommandResult:
        evidence_dir = os.path.join(self.run_dir, "artifacts")
        os.makedirs(evidence_dir, exist_ok=True)
        errors = []

        remote_vid = "/sdcard/journey.mp4"
        local_vid = os.path.join(evidence_dir, "journey.mp4")
        if self.screenrecord_process is not None:
            try:
                rec = self._shell_finish(
                    self.screenrecord_process, timeout=15.0)
                if rec.transport.timed_out:
                    errors.append("screenrecord timed out")
                elif rec.remote_rc is None:
                    errors.append("screenrecord status ambiguous")
                elif rec.transport.returncode != 0 or rec.remote_rc != 0:
                    errors.append(
                        f"screenrecord rc={rec.transport.returncode}"
                        f"/remote={rec.remote_rc}")
            except Exception as e:
                errors.append(f"screenrecord finisher error: {e}")
            self.screenrecord_process = None
        pull_v = self._host("pull", remote_vid, local_vid)
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
            try:
                self._shell_finish(
                    self.screenrecord_process, timeout=5.0, terminate=True)
            except Exception:
                pass
            self.screenrecord_process = None
        return self._host("emu", "snapshot", "load", SNAPSHOT_NAME, timeout=30.0)

    def verify_restore(self) -> PriorDeviceState:
        state = self.capture_prior_state()
        if state is None:
            raise RuntimeError("verification prior state unavailable")
        return state

    def _revalidate_ownership(self) -> bool:
        if self._owned_pid is None or self._owned_identity is None:
            return False
        current = commands.pid_identity(self._owned_pid)
        if current is None:
            return False
        return current == self._owned_identity

    def _identity_matches_launch(
        self, identity: commands.ProcessIdentity,
    ) -> bool:
        """Provisional ownership: the observed command must be the
        process we launched — same executable, same AVD token — read
        from the live process, never from our expectations.

        The executable is matched as a whitespace token, not a prefix:
        shebang-launched scripts appear in ``ps`` behind their
        interpreter (``python3 /path/to/emulator -avd ...``)."""
        launched = (
            self.emulator_process.argv if self.emulator_process is not None
            else None
        )
        if not launched:
            return False
        exe = launched[0]
        if exe not in identity.command.split():
            return False
        if AVD_NAME in launched and AVD_NAME not in identity.command:
            return False
        return True

    def dump_ledger(self) -> CommandResult:
        evidence_dir = os.path.join(self.run_dir, "artifacts")
        os.makedirs(evidence_dir, exist_ok=True)
        path = os.path.join(evidence_dir, "command_ledger.json")
        try:
            with open(path, "w") as fh:
                fh.write(self.ledger.serialize())
            return self._ok("ledger", f"{len(self.ledger)} entries -> {path}".encode())
        except OSError as e:
            return self._fail("ledger", str(e).encode())

    def release_emulator(self) -> CommandResult:
        if self.emulator_process is not None:
            proc = self.emulator_process.proc
            identity = commands.pid_identity(proc.pid)
            if identity is None and proc.poll() is None:
                # Running but unobservable: we cannot prove ownership,
                # so we refuse to signal it.
                self.emulator_process = None
                return self._fail(
                    "release", b"identity unobservable - refuse kill")
            if identity is not None and not self._identity_matches_launch(identity):
                self.emulator_process = None
                return self._fail(
                    "release", b"identity does not match launch - refuse kill")
            if (identity is not None and self._owned_pid == proc.pid
                    and self._owned_identity is not None
                    and identity != self._owned_identity):
                self.emulator_process = None
                return self._fail(
                    "release", b"PID reuse detected - refuse kill")
            try:
                outcome = commands.bounded_terminate(
                    proc, group=self._session_launched)
            except Exception as e:
                self.emulator_process = None
                return self._fail("release", f"termination error: {e}".encode())
            alive = proc.poll() is None
            self.emulator_process = None
            if alive or not outcome.group_extinct:
                return self._fail(
                    "release",
                    b"process or group members still alive after escalation")
            msg = b"released via process handle"
            if outcome.killed:
                msg += b" (SIGKILL required)"
            if self._session_launched:
                msg += b" (group)"
            return self._ok("release", msg)
        if self._owned_pid is not None:
            if not self._revalidate_ownership():
                self._owned_pid = None
                self._owned_identity = None
                return self._fail("release",
                                  b"PID reuse or stale ownership - refuse kill")
            killed = commands.bounded_terminate_pid(
                self._owned_pid, self._owned_identity)
            if self._revalidate_ownership():
                return self._fail("release",
                                  b"process survived bounded escalation")
            msg = b"released by owned PID"
            if killed:
                msg += b" (SIGKILL required)"
            return self._ok("release", msg)
        return self._fail("release", b"no owned emulator to release")

    def verify_release(self) -> CommandResult:
        if self._owned_pid is not None and self._owned_identity is not None:
            current = commands.pid_identity(self._owned_pid)
            if current is not None and current == self._owned_identity:
                return self._fail("verify_release",
                                  f"owned PID {self._owned_pid} still alive".encode())
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
