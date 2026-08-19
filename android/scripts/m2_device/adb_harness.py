"""Production harness implementation conforming to JourneyHarness protocol."""

from __future__ import annotations

import os
import re
import socket
import time
import xml.etree.ElementTree as ET
from datetime import datetime, timezone
from typing import Any

from android.scripts.m2_device import commands, evidence
from android.scripts.m2_device.evidence import CANONICAL_PNG_NAMES
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

SCREENSHOT_NAMES = list(CANONICAL_PNG_NAMES)

KEYBOARD_PACKAGE = "biz.pixelperfectstudios.personaspeak"
ANIMATION_SCALE = "1.0"
# The IME component the APK registers. Android will not show an
# installed-but-disabled IME: enablement and selection are explicit
# journey steps (proven on the fixture 2026-08-19, issue #79).
IME_COMPONENT = (
    f"{KEYBOARD_PACKAGE}/com.menny.android.anysoftkeyboard.SoftKeyboard")
# Host-side facts, dump-visible on API 34: the Settings homepage search
# bar is the entry control; the Settings-intelligence search screen's
# editor is the behavioral bridge for typed and applied text. Its text
# attribute is the hint while empty (observed in the 20260819T123124Z
# run archive), so "empty" and "typed" are both exact string facts.
SEARCH_BAR_RES_ID = f"{SETTINGS_PACKAGE}:id/search_action_bar"
EDITOR_RES_ID = (
    "com.google.android.settings.intelligence:id/open_search_view_edit_text")
EDITOR_HINT = "Search settings"
# ASK key geometry recalibrated against the real layout (2026-08-19
# debug-session screenshot; the utility row sits ABOVE the letter rows,
# so the old y-band ~1375-1690 hit app content and drifted into Google
# Assistant settings). Letter keys sit on a 108px grid at 420dpi.
ASK_KEY_COORDS: dict[str, tuple[int, int]] = {
    "T": (486, 1794), "E": (270, 1794), "A": (108, 1932),
    "S": (216, 1932), "I": (810, 1794), "X": (215, 2072),
    "V": (431, 2072), "N": (647, 2072),
    " ": (566, 2210), ".": (755, 2210),
}
# PersonaSpeak's panel row. No dump channel can observe it (uiautomator
# is structurally blind to the IME window on API 34, issue #79): these
# taps are pinned against the real layout and every one is verified
# through the editor-text bridge before the journey trusts it. The row
# is bottom-anchored, so the y survives the panel's Review expansion.
REWRITE_TAP = (116, 1452)
CANCEL_TAP = (180, 1452)
APPLY_TAP = (105, 1452)
DISMISS_TAP = (328, 1452)
# InputMethod window geometry (dumpsys window): the compact row's
# touchable region tops out at y=1378; Review expands it upward past
# y=1330. A region top below the compact line means the panel grew —
# the machine-visible half of the review-ready signal; the candidate
# surface itself is screenshot-bound. (API 34 publishes this only as the
# touchable region; the window frame itself is fill-parent in both
# states — probe 2026-08-20.)
IME_COMPACT_TOP = 1378
IME_EXPANDED_MAX_TOP = 1330
# FakeProvider's fixture latency is 400ms; the host sleeps past it
# before asking for the expanded frame, keeping the ledger deterministic.
REVIEW_SETTLE_SECONDS = 0.6
KEYEVENT_BACK = "4"
KEYEVENT_DEL = "67"

# Package identity as the device reports it (2026-08-19 capability probe on
# the pinned fixture; versionName is the vendored keyboard's own numbering).
EXPECTED_VERSION_NAME = "1.13.1"
EXPECTED_VERSION_CODE = "1"
# On-device PackageSignatures digest of the canonical APK's signing
# cert, as dumpsys prints it. Corroboration only: this is a 32-bit
# Signature.hashCode, not a cryptographic digest, so it can never be
# the signer gate — EXPECTED_SIGNER_CERT_SHA256 below is that gate.
EXPECTED_SIGNER = "847f3baa"
# The signer gate: SHA-256 of the canonical APK's signing certificate,
# compared as an exact line of `apksigner verify --print-certs` output.
EXPECTED_SIGNER_CERT_SHA256 = (
    "0f62f45e45adda3af137ac4d9cb48f642975d9c1a35c52e61f8df41188cfc807"
)
# build-tools 34.0.0's apksigner self-reports this version string.
EXPECTED_APKSIGNER_VERSION = "0.9"
EXPECTED_ADB_VERSION = "1.0.41"
# Local instrument, probe-proven to load the pinned m2_pristine snapshot
# under the software renderer (prep ran 34.2.16; that version is no longer
# installable, and the pin must name the tool that actually runs).
EXPECTED_EMULATOR_VERSION = "36.6.11"
SYSTEM_IMAGE_ID = "google/sdk_gphone64_arm64/emu64a:14"
FIXTURE_RECEIPT_DIGEST = (
    "dad6f7ac3b3c10ac7b88dfe2397746acb11ee6a42957cf2d1fee7afe1325bdb0"
)
EXPECTED_BUILD_TOOLS_VERSION = "34.0.0"
# IME baseline exactly as the accepted #56 receipt and the live fixture
# both record it: Gboard LatinIME plus the Google TTS voice service.
EXPECTED_ENABLED_IMES = [
    "com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME",
    "com.google.android.tts/com.google.android.apps.speech.tts.googletts.settings.asr.voiceime.VoiceInputMethodService",
]
EXPECTED_EDITOR_CLASS = "android.widget.EditText"
ANIMATOR_SCALE = "null"  # fixture pins animator unset

# Fixture transaction: the snapshot files whose exact bytes the
# accepted #56 receipt pins. Defaults are the pinned digests; fake-only
# runs inject digests computed from their own fixture files. hardware.ini
# is a snapshot content file (receipt: snapshot/files/hardware.ini), not
# an AVD-top-level file — the real fixture has it only under the snapshot.
FIXTURE_FILES = {
    "M2_Qual_Fixture.avd/snapshots/m2_pristine/hardware.ini": HARDWARE_INI_HASH,
    "M2_Qual_Fixture.avd/snapshots/m2_pristine/ram.bin": RAM_BIN_HASH,
    "M2_Qual_Fixture.avd/snapshots/m2_pristine/textures.bin": TEXTURES_BIN_HASH,
}


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
        fixture_root: str = "",
        fixture_digests: dict[str, str] | None = None,
    ):
        self.run_dir = run_dir
        self.apk_path = apk_path
        self.serial = serial
        self.runner = runner or commands.run
        self.starter = starter or commands.start
        self.finisher = finisher or commands.finish
        self.repo_root = repo_root or os.getcwd()
        self.fixture_root = fixture_root or os.path.join(
            os.path.expanduser("~"), ".android", "avd")
        self.fixture_digests = dict(FIXTURE_FILES)
        self._injected_fixture_digests = bool(fixture_digests)
        if fixture_digests:
            # Deliberately merges rather than replaces: a typo'd key in
            # override JSON adds an entry while the pinned one stays in
            # force, so a mistyped override fails closed on drift.
            self.fixture_digests.update(fixture_digests)
        self.adb_tool: ToolIdentity | None = None
        self.emulator_tool: ToolIdentity | None = None
        self.apksigner_tool: ToolIdentity | None = None
        self.build_tools_tool: ToolIdentity | None = None
        self.emulator_process: commands.ManagedProcess | None = None
        self.screenrecord_process: commands.ManagedProcess | None = None
        self._owned_pid: int | None = None
        self._owned_identity: commands.ProcessIdentity | None = None
        self._launch_identity: commands.ProcessIdentity | None = None
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
            # The pinned snapshot was saved under the software renderer;
            # under the 36.x default (gfxstream) the load is refused and
            # the emulator cold-boots instead, failing the run's
            # preconditions. The renderer must match the saved one.
            "-gpu", "swiftshader_indirect",
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
            self.apksigner_tool = commands.resolve_tool("apksigner")
            if EXPECTED_ADB_VERSION not in self.adb_tool.version:
                return self._fail("preflight", f"adb version mismatch: {self.adb_tool.version}".encode())
            if EXPECTED_EMULATOR_VERSION not in self.emulator_tool.version:
                return self._fail("preflight", f"emulator version mismatch: {self.emulator_tool.version}".encode())
            if EXPECTED_APKSIGNER_VERSION not in self.apksigner_tool.version:
                return self._fail("preflight", f"apksigner version mismatch: {self.apksigner_tool.version}".encode())
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
        # apksigner is mandatory after preflight; its identity rides in
        # the recorded tool set because it carries the signer gate.
        tools = [self.adb_tool, self.emulator_tool, self.apksigner_tool]
        if self.build_tools_tool is not None:
            tools.append(self.build_tools_tool)
        # A run over injected (fake-only) digests is mechanically barred
        # from claiming the accepted fixture receipt: the recorded
        # digest is blanked, so no capture over arbitrary snapshot bytes
        # can present itself as an accepted-fixture qualification.
        # Defense-in-depth only — the authoritative boundary is the
        # verdict serialized into the validate_fixture step's stdout
        # (this context field is not carried into CaptureRecord).
        receipt = "" if self._injected_fixture_digests else FIXTURE_RECEIPT_DIGEST
        return CaptureContext(
            repo_head=repo_head, apk_sha256=apk_sha,
            tools=tools, fixture_receipt_digest=receipt,
        )

    def launch_emulator(self) -> CommandResult:
        argv = self._emu_argv()
        self.emulator_process = self.starter(argv, new_session=True)
        self._session_launched = True
        pid = self.emulator_process.proc.pid
        # Observed from the process itself immediately after launch —
        # never copied from expectations. The start time survives a
        # launcher exec/title rewrite; the command line may not.
        self._launch_identity = commands.pid_identity(pid)
        self.ledger.record(
            argv, self.emulator_process.start_utc, _UTC(),
            0, None, False, "launch",
        )
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
        if (self._launch_identity is not None
                and identity.start != self._launch_identity.start):
            return self._fail(
                "ownership",
                b"start-time discontinuity - pid reuse suspected")
        exe = self.emulator_process.argv[0]
        if not (self._token_in(exe, identity.command)
                or AVD_NAME in identity.command):
            # Neither the launched executable nor the pinned AVD appears
            # in the observed command (a zombie reports "<defunct>"):
            # refuse to own what we cannot recognize.
            return self._fail(
                "ownership",
                b"observed command matches neither executable nor AVD")
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
            # pm path for an absent package exits 1 with empty output —
            # that is the pristine fixture's REQUIRED state, not an
            # error (the fake toolkit used to exit 0 here, hiding the
            # difference). rc=1 with output stays unknown state, which
            # must stop the run rather than guess.
            pkg_res = self._shell("pm", "path", KEYBOARD_PACKAGE)
            if pkg_res.remote_rc is None:
                raise commands.RemoteAmbiguousError("pm path")
            pkg_out = pkg_res.transport.stdout.decode(
                "utf-8", errors="replace").strip()
            pkg_err = pkg_res.transport.stderr.decode(
                "utf-8", errors="replace").strip()
            if pkg_res.remote_rc == 0:
                package_present = pkg_out.startswith("package:")
            elif pkg_res.remote_rc == 1 and not pkg_out and not pkg_err:
                package_present = False
            else:
                return None
            package_hash = None
            if package_present:
                dev_path = pkg_out.split(":", 1)[1].strip()
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
            (("getprop", "qemu.sf.lcd_density"), str(SCREEN_DPI)),
            (("settings", "get", "global", "window_animation_scale"), ANIMATION_SCALE),
            (("settings", "get", "global", "transition_animation_scale"), ANIMATION_SCALE),
            (("settings", "get", "global", "animator_duration_scale"), ANIMATOR_SCALE),
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

        # Fixture transaction: the pinned snapshot bytes are verified
        # before any mutation. Missing or drifted files fail closed.
        for rel, expected_digest in sorted(self.fixture_digests.items()):
            path = os.path.join(self.fixture_root, *rel.split("/"))
            if not os.path.isfile(path):
                errors.append(f"fixture file missing: {rel}")
                continue
            try:
                actual_digest = commands.digest_file(path)
            except OSError as e:
                errors.append(f"fixture file unreadable: {rel}: {e}")
                continue
            if actual_digest != expected_digest:
                errors.append(
                    f"fixture digest drift: {rel}: "
                    f"expected {expected_digest[:12]} got {actual_digest[:12]}")

        if errors:
            return self._fail("validate_fixture", "\n".join(errors).encode())
        # The pinned-versus-injected verdict is carried in the step's own
        # serialized stdout, so the persisted CaptureRecord mechanically
        # distinguishes an accepted-fixture qualification from a fake-only
        # run over injected digests — the boundary survives serialization.
        if self._injected_fixture_digests:
            return self._ok(
                "validate_fixture",
                b"fake-only fixture transaction: injected digests verified"
                b" - not an accepted-fixture qualification")
        # The pinned-success branch is only reachable on the real
        # fixture (#55): no fake can forge the pinned digests, so its
        # stdout is device-only coverage by design.
        return self._ok(
            "validate_fixture",
            f"Fixture identity validated against pinned receipt"
            f" {FIXTURE_RECEIPT_DIGEST[:12]}.".encode())

    def install_apk(self) -> CommandResult:
        # The signer gate runs before any device mutation: a certificate
        # mismatch must stop the install, not follow it.
        if self.apksigner_tool is None:
            return self._fail("install_apk", b"apksigner tool unresolved")
        certs = self.runner(
            [self.apksigner_tool.path, "verify", "--print-certs",
             self.apk_path], timeout=60.0)
        if certs.timed_out:
            return self._fail("install_apk", b"apksigner verify timed out")
        if certs.returncode != 0:
            return self._fail(
                "install_apk", f"apksigner rc={certs.returncode}".encode())
        expected_line = (
            "Signer #1 certificate SHA-256 digest: "
            f"{EXPECTED_SIGNER_CERT_SHA256}")
        if expected_line not in certs.stdout.decode(
                "utf-8", errors="replace").splitlines():
            return self._fail(
                "install_apk",
                b"signer certificate mismatch: apksigner SHA-256 digest "
                b"does not exactly match the pinned certificate")
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
        if label not in evidence.CANONICAL_HIERARCHY_LABELS:
            # The code cannot express a non-canonical hierarchy name.
            raise RuntimeError(
                f"hierarchy label {label!r} is not in the canonical set")
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
        except (ET.ParseError, OSError):
            if pull.timed_out:
                return pull, None
            # The transport claims success but the artifact is missing
            # or unparsable: fail closed as a journey failure. A clean
            # wrapper rc never certifies facts that could not be read.
            # OSError is deliberately wide, not just FileNotFoundError:
            # permission, disk-full, any artifact I/O failure is an
            # unreadable artifact, which is exactly the fail-closed verdict.
            return self._fail(
                label, b"hierarchy missing or unparsable"), None

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

    def _enable_ime(self, steps) -> bool:
        res = self._shell("ime", "enable", IME_COMPONENT)
        return self._step(steps, "enable_ime", res)

    def _set_ime(self, steps) -> bool:
        res = self._shell("ime", "set", IME_COMPONENT)
        return self._step(steps, "set_ime", res)

    def _verify_binding(self, steps, tag: str) -> bool:
        """dumpsys input_method: our component must be the bound,
        connected, visible method at the moment the journey needs the
        keyboard up. One semantic step: the transport verdict if it
        failed, otherwise the parsed binding verdict."""
        res = self._shell("dumpsys", "input_method")
        if self._ambiguous(res) or self._rc_of(res) != 0:
            self._step(steps, f"verify_ime_binding_{tag}", res)
            return False
        out = res.transport.stdout.decode("utf-8", errors="replace")
        m = re.search(r"mCurMethodId=(\S+)", out)
        errors = []
        if m is None or m.group(1) != IME_COMPONENT:
            errors.append(f"mCurMethodId: {m.group(1) if m else 'absent'}")
        for flag in ("mHaveConnection", "mBoundToMethod", "mVisibleBound"):
            if f"{flag}=true" not in out:
                errors.append(f"{flag} not true")
        if errors:
            self._step(steps, f"verify_ime_binding_{tag}",
                       self._fail("binding", "; ".join(errors).encode()))
            return False
        self._step(steps, f"verify_ime_binding_{tag}",
                   self._ok("binding"))
        return True

    def _ime_window_frame(self, steps, tag: str) -> tuple[int, int] | None:
        """dumpsys window: the InputMethod window owned by our package,
        shown and drawn. Returns (top, bottom) of its touchable region —
        the visible keyboard area — or None on any refusal, never a
        guessed frame."""
        res = self._shell("dumpsys", "window", "windows")
        if self._ambiguous(res) or self._rc_of(res) != 0:
            self._step(steps, f"verify_ime_window_{tag}", res)
            return None
        out = res.transport.stdout.decode("utf-8", errors="replace")
        for block in out.split("Window #")[1:]:
            if "InputMethod}" not in block.split("\n", 1)[0]:
                continue
            errors = []
            if f"package={KEYBOARD_PACKAGE}" not in block:
                errors.append("window not owned by personaspeak package")
            if "HAS_DRAWN" not in block:
                errors.append("window not drawn")
            if not any(token in block for token in (
                    "mViewVisibility=0x0", "isReadyForDisplay()=true",
                    "shown=true")):
                errors.append("window not shown")
            # The real block (API 34, emulator 36.6.11 — probe
            # 2026-08-20) carries no mFrame line: the window frame is
            # fill-parent in both panel states. The visible keyboard
            # geometry lives in the touchable region, which moves with
            # the panel — that region is the review signal's source.
            fm = re.search(
                r"touchable region=SkRegion\("
                r"\((-?\d+),(-?\d+),(-?\d+),(-?\d+)\)\)", block)
            if fm is None:
                errors.append("touchable region absent")
            if errors:
                # The raw block rides in the failure record so a format
                # drift is diagnosable from the record alone.
                self._step(
                    steps, f"verify_ime_window_{tag}",
                    self._fail(
                        "window",
                        ("; ".join(errors) + "\n" + block[:8192]).encode()))
                return None
            return int(fm.group(2)), int(fm.group(4))
        self._step(
            steps, f"verify_ime_window_{tag}",
            self._fail(
                "window",
                b"no InputMethod window\n"
                + out[:8192].encode("utf-8", "replace")))
        return None

    def _verify_window_state(self, steps, tag: str, expanded: bool) -> bool:
        """The panel row's presence class, read from window geometry:
        the compact row tops at IME_COMPACT_TOP; Review grows the window
        upward past IME_EXPANDED_MAX_TOP. The candidate surface itself
        stays screenshot-bound; this only pins the container fact."""
        frame = self._ime_window_frame(steps, tag)
        if frame is None:
            return False
        top = frame[0]
        if expanded:
            ok = top <= IME_EXPANDED_MAX_TOP
            detail = f"frame top {top} never rose above {IME_EXPANDED_MAX_TOP}"
        else:
            ok = top == IME_COMPACT_TOP
            detail = f"frame top {top} != compact {IME_COMPACT_TOP}"
        if not ok:
            self._step(steps, f"verify_ime_window_{tag}",
                       self._fail("window", detail.encode()))
            return False
        self._step(steps, f"verify_ime_window_{tag}", self._ok("window"))
        return True

    @staticmethod
    def _editor_of(root: Any) -> Any | None:
        field = AdbHarness._find(root, EDITOR_RES_ID)
        if field is None:
            return None
        if field.attrib.get("class", "") != EXPECTED_EDITOR_CLASS:
            return None
        return field

    def _verify_editor(
        self, steps, root, label: str, expected: str,
    ) -> bool:
        """The behavioral bridge: the host editor node's text attribute
        proves typed keys and applied rewrites. A tap that did not land
        is a text that did not change — fail closed."""
        field = self._editor_of(root)
        if field is None:
            self._step(steps, f"verify_{label}",
                       self._fail(label, b"editor not found"))
            return False
        actual = field.attrib.get("text", "")
        ok = actual == expected
        self._step(steps, f"verify_{label}",
                   self._ok(label) if ok
                   else self._fail(label, f"got {actual[:40]!r}".encode()))
        return ok

    def _verify_editor_by_dump(self, steps, label: str, expected: str) -> bool:
        res, root = self._dump_hierarchy(label)
        if root is None:
            self._step(steps, f"verify_{label}", res)
            return False
        return self._verify_editor(steps, root, label, expected)

    def _open_search_session(self, steps, n: int) -> bool:
        """One editor session: open Settings, locate the search bar in
        the dump (its bounds are read at runtime, never pinned), tap it,
        then pin the pristine editor facts — hint text, focused, and the
        IME bound and drawn per the dumpsys channels."""
        res = self._shell("am", "start", "-a", SETTINGS_ACTION)
        if not self._step(steps, f"open_settings_{n}", res):
            return False

        res, root = self._dump_hierarchy(f"home_{n}")
        if root is None:
            self._step(steps, f"dump_home_{n}", res)
            return False
        self._step(steps, f"dump_home_{n}", res)
        bar = self._find(root, SEARCH_BAR_RES_ID)
        center = self._center(bar) if bar is not None else None
        if center is None:
            self._step(steps, f"focus_editor_{n}",
                       self._fail("locate", b"search bar not found"))
            return False
        res = self._shell("input", "tap", *center)
        if not self._step(steps, f"focus_editor_{n}", res):
            return False

        res, root = self._dump_hierarchy(f"focus_{n}")
        if root is None:
            self._step(steps, f"verify_editor_pristine_{n}", res)
            return False
        field = self._editor_of(root)
        errors = []
        if field is None:
            errors.append("editor not found")
        else:
            if field.attrib.get("text", "") != EDITOR_HINT:
                errors.append(
                    f"editor not empty: {field.attrib.get('text', '')[:40]!r}")
            if field.attrib.get("focused", "") != "true":
                errors.append("editor not focused")
        if errors:
            self._step(steps, f"verify_editor_pristine_{n}",
                       self._fail("pristine", "\n".join(errors).encode()))
            return False
        self._step(steps, f"verify_editor_pristine_{n}", self._ok("pristine"))
        return (self._verify_binding(steps, f"s{n}")
                and self._verify_window_state(steps, f"s{n}", expanded=False))

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

    def _clear_text(self, steps, current: str) -> bool:
        """Delete exactly len(current) characters through the editor's
        own input pipeline. Host-injected by design: the journey's proof
        obligations are ASK key taps (insertion) and the panel actions;
        deletion between legs is setup, like the BACK navigation."""
        for _ in current:
            res = self._shell("input", "keyevent", KEYEVENT_DEL)
            if not self._step(steps, "clear_key", res):
                return False
        return True

    def _exit_session(self, steps, n: int) -> bool:
        res = self._shell("input", "keyevent", KEYEVENT_BACK)
        return self._step(steps, f"exit_session_{n}", res)

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

        if not self._enable_ime(steps):
            return steps
        if not self._set_ime(steps):
            return steps

        # Session 1 — Idle, Loading/cancel: type through real ASK keys,
        # trigger a rewrite, cancel it while loading. Zero mutations.
        if not self._open_search_session(steps, 1):
            return steps
        if not self._type_text(steps, SOURCE_TEXT):
            return steps
        if not self._verify_editor_by_dump(steps, "typed_1", SOURCE_TEXT):
            return steps
        if not self._take_screenshot(steps, "01-idle-typed"):
            return steps
        # One shell: the cancel tap must land inside the fixture
        # provider's 400ms loading window, so both taps ride a single
        # transport with no screenshot or dump between them.
        res = self._shell(
            "input", "tap", str(REWRITE_TAP[0]), str(REWRITE_TAP[1]),
            ";", "input", "tap", str(CANCEL_TAP[0]), str(CANCEL_TAP[1]))
        if not self._step(steps, "rewrite_and_cancel", res):
            return steps
        if not self._verify_editor_by_dump(
                steps, "after_cancel", SOURCE_TEXT):
            return steps
        if not self._verify_window_state(steps, "after_cancel", expanded=False):
            return steps
        if not self._take_screenshot(steps, "02-loading-cancel"):
            return steps
        if not self._exit_session(steps, 1):
            return steps

        # Session 2 — Review, Applied: rewrite, wait out the provider
        # latency, apply, and prove the exactly-one mutation.
        if not self._open_search_session(steps, 2):
            return steps
        if not self._type_text(steps, SOURCE_TEXT):
            return steps
        if not self._verify_editor_by_dump(steps, "typed_2", SOURCE_TEXT):
            return steps
        res = self._shell(
            "input", "tap", str(REWRITE_TAP[0]), str(REWRITE_TAP[1]))
        if not self._step(steps, "request_rewrite_2", res):
            return steps
        time.sleep(REVIEW_SETTLE_SECONDS)
        if not self._verify_window_state(steps, "review_2", expanded=True):
            return steps
        if not self._take_screenshot(steps, "03-review"):
            return steps
        res = self._shell(
            "input", "tap", str(APPLY_TAP[0]), str(APPLY_TAP[1]))
        if not self._step(steps, "apply_rephrasing", res):
            return steps
        if not self._verify_editor_by_dump(
                steps, "after_apply", CANDIDATE_REPHRASING):
            return steps
        if not self._take_screenshot(steps, "04-applied"):
            return steps
        if not self._exit_session(steps, 2):
            return steps

        # Session 3 — Dismiss: zero mutations, panel back to idle.
        if not self._open_search_session(steps, 3):
            return steps
        if not self._type_text(steps, SOURCE_TEXT):
            return steps
        if not self._verify_editor_by_dump(steps, "typed_3", SOURCE_TEXT):
            return steps
        res = self._shell(
            "input", "tap", str(REWRITE_TAP[0]), str(REWRITE_TAP[1]))
        if not self._step(steps, "request_rewrite_3", res):
            return steps
        time.sleep(REVIEW_SETTLE_SECONDS)
        if not self._verify_window_state(steps, "review_3", expanded=True):
            return steps
        res = self._shell(
            "input", "tap", str(DISMISS_TAP[0]), str(DISMISS_TAP[1]))
        if not self._step(steps, "dismiss_rephrasing", res):
            return steps
        if not self._verify_editor_by_dump(
                steps, "after_dismiss", SOURCE_TEXT):
            return steps
        if not self._verify_window_state(steps, "after_dismiss", expanded=False):
            return steps
        if not self._take_screenshot(steps, "05-dismissed"):
            return steps
        if not self._exit_session(steps, 3):
            return steps

        # Session 4 — Stale: change the source under a pending candidate;
        # the apply must make zero mutations and retain the candidate.
        if not self._open_search_session(steps, 4):
            return steps
        if not self._type_text(steps, SOURCE_TEXT):
            return steps
        if not self._verify_editor_by_dump(steps, "typed_4", SOURCE_TEXT):
            return steps
        res = self._shell(
            "input", "tap", str(REWRITE_TAP[0]), str(REWRITE_TAP[1]))
        if not self._step(steps, "request_rewrite_4", res):
            return steps
        time.sleep(REVIEW_SETTLE_SECONDS)
        if not self._verify_window_state(steps, "review_4", expanded=True):
            return steps
        if not self._clear_text(steps, SOURCE_TEXT):
            return steps
        if not self._type_text(steps, STALE_TEXT, "type_stale"):
            return steps
        if not self._verify_editor_by_dump(steps, "typed_stale", STALE_TEXT):
            return steps
        res = self._shell(
            "input", "tap", str(APPLY_TAP[0]), str(APPLY_TAP[1]))
        if not self._step(steps, "apply_stale", res):
            return steps
        if not self._verify_editor_by_dump(
                steps, "after_stale", STALE_TEXT):
            return steps
        if not self._take_screenshot(steps, "06-stale"):
            return steps
        if not self._exit_session(steps, 4):
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
                # We stop the recorder ourselves: a journey shorter than
                # --time-limit would otherwise outlive the finish window
                # and be classified as a timeout. A wrapper rc of -15/143
                # is our SIGTERM, not a failure — the file finalizes on
                # the device when the shell connection drops.
                rec = self._shell_finish(
                    self.screenrecord_process, timeout=15.0, terminate=True)
                if rec.transport.timed_out:
                    errors.append("screenrecord timed out")
                elif rec.transport.returncode in (-15, 143):
                    pass
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
        # The 36.x console nests the snapshot family under `avd` (probe
        # 2026-08-20): the bare top-level form draws "KO: unknown
        # command". Console KOs arrive with returncode 0, so stdout —
        # not rc — is the verdict.
        res = self._host("emu", "avd", "snapshot", "load", SNAPSHOT_NAME,
                         timeout=30.0)
        out = res.stdout.decode("utf-8", errors="replace")
        if res.returncode == 0 and "KO:" in out:
            return CommandResult(
                argv=res.argv, start_utc=res.start_utc,
                end_utc=res.end_utc, returncode=1, stdout=res.stdout,
                stderr=("console rejected restore: "
                        + out.strip()).encode(),
            )
        return res

    def verify_restore(self) -> PriorDeviceState:
        state = self.capture_prior_state()
        if state is None:
            raise RuntimeError("verification prior state unavailable")
        # Pristine-state assertion, not a journey-time one: after the
        # snapshot restore the Settings search screen does not exist —
        # the fixture boots to its home screen. The journey-time editor
        # must be gone; identity, IME baseline, and package absence are
        # compared against the captured prior state by the caller.
        res, root = self._dump_hierarchy("verify_restore")
        if root is None:
            raise RuntimeError(
                "restoration mismatch: hierarchy unavailable for pristine facts")
        if self._find(root, EDITOR_RES_ID) is not None:
            raise RuntimeError(
                "restoration mismatch: search editor still present after restore")
        return state

    def _revalidate_ownership(self) -> bool:
        if self._owned_pid is None or self._owned_identity is None:
            return False
        current = commands.pid_identity(self._owned_pid)
        if current is None:
            return False
        return current == self._owned_identity

    @staticmethod
    def _token_in(token: str, command: str) -> bool:
        """Whitespace-token containment that tolerates spaces inside
        *token* (SDK paths often contain them)."""
        return f" {token} " in f" {command} "

    def _identity_matches_launch(
        self, identity: commands.ProcessIdentity,
    ) -> bool:
        """Fallback provisional-ownership check for the case where no
        identity was observed at launch: the observed command must carry
        the launched executable and AVD tokens — read from the live
        process. Shebang-launched scripts appear in ``ps`` behind their
        interpreter, hence token matching rather than a prefix."""
        launched = (
            self.emulator_process.argv if self.emulator_process is not None
            else None
        )
        if not launched:
            return False
        if not self._token_in(launched[0], identity.command):
            return False
        if AVD_NAME in launched and AVD_NAME not in identity.command:
            return False
        return True

    def dump_ledger(self) -> CommandResult:
        evidence_dir = os.path.join(self.run_dir, "artifacts")
        os.makedirs(evidence_dir, exist_ok=True)
        path = os.path.join(evidence_dir, "command_ledger.json")
        try:
            evidence.write_private_atomic(
                path, self.ledger.serialize().encode())
            return self._ok("ledger", f"{len(self.ledger)} entries -> {path}".encode())
        except OSError as e:
            return self._fail("ledger", str(e).encode())

    def release_emulator(self) -> CommandResult:
        if self.emulator_process is not None:
            proc = self.emulator_process.proc
            if proc.poll() is not None:
                # The leader already exited — crashed emulator, failed
                # boot, the most common non-nominal path. Reap it and
                # report a clean release. Never group-terminate after
                # the reap: the pid is free and a pgid fallback could
                # target an unrelated recycled group.
                try:
                    proc.communicate(timeout=5.0)
                except Exception:
                    pass
                self.emulator_process = None
                return self._ok(
                    "release", b"released (process already exited)")
            identity = commands.pid_identity(proc.pid)
            if identity is None:
                # Running but unobservable: we cannot prove ownership,
                # so we refuse to signal it.
                self.emulator_process = None
                return self._fail(
                    "release", b"identity unobservable - refuse kill")
            retained = self._owned_identity or self._launch_identity
            if retained is not None:
                # Start-time continuity against an identity observed
                # from the process itself. The command line may change
                # legitimately (launcher exec of the engine, process
                # title rewrites); a different start time means the
                # original is gone and this pid may be reused — refuse.
                if identity.start != retained.start:
                    self.emulator_process = None
                    return self._fail(
                        "release",
                        b"identity changed (pid reuse) - refuse kill")
            elif not self._identity_matches_launch(identity):
                # No launch observation was retained: fall back to
                # launch-argv tokens as the only available evidence.
                self.emulator_process = None
                return self._fail(
                    "release",
                    b"identity does not match launch - refuse kill")
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
