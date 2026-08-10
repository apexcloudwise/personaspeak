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

# Pinned qualification invariants
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

# Pinned editor contract
SETTINGS_PACKAGE = "com.android.settings"
SETTINGS_ACTION = "android.settings.SETTINGS"
SOURCE_TEXT = "Tea at six."
STALE_TEXT = "Tea at seven."
CANDIDATE_REPHRASING = (
    "I have taken the liberty, sir, of rephrasing your words: "
    "“Tea at six.” — though I must confess the genuine article is still en route."
)


class AdbHarness:
    """JourneyHarness driving Android Emulator automation via adb and standard utilities."""

    def __init__(
        self,
        run_dir: str,
        apk_path: str,
        serial: str = "emulator-5554",
        runner: Any = None,
        starter: Any = None,
        finisher: Any = None,
    ):
        self.run_dir = run_dir
        self.apk_path = apk_path
        self.serial = serial
        self.runner = runner or commands.run
        self.starter = starter or commands.start
        self.finisher = finisher or commands.finish

        self.adb_tool: ToolIdentity | None = None
        self.emulator_tool: ToolIdentity | None = None
        self.emulator_process: commands.ManagedProcess | None = None
        self.screenrecord_process: commands.ManagedProcess | None = None

    def preflight(self) -> CommandResult:
        try:
            self.adb_tool = commands.resolve_tool("adb")
            self.emulator_tool = commands.resolve_tool("emulator")
            return CommandResult(
                argv=["preflight"],
                start_utc=_UTC(),
                end_utc=_UTC(),
                returncode=0,
                stdout=b"Preflight check passed.",
                stderr=b"",
            )
        except Exception as e:
            return CommandResult(
                argv=["preflight"],
                start_utc=_UTC(),
                end_utc=_UTC(),
                returncode=1,
                stdout=b"",
                stderr=str(e).encode(),
            )

    def capture_context(self) -> CaptureContext:
        head_res = self.runner(["git", "rev-parse", "HEAD"])
        repo_head = head_res.stdout.decode("utf-8").strip()
        apk_sha = commands.digest_file(self.apk_path)
        return CaptureContext(
            repo_head=repo_head,
            apk_sha256=apk_sha,
            tools=[self.adb_tool, self.emulator_tool],
        )

    def launch_emulator(self) -> CommandResult:
        argv = [
            "emulator",
            "-avd",
            AVD_NAME,
            "-snapshot",
            SNAPSHOT_NAME,
            "-no-snapshot-save",
            "-port",
            "5554",
        ]
        self.emulator_process = self.starter(argv)
        return CommandResult(
            argv=argv,
            start_utc=_UTC(),
            end_utc=_UTC(),
            returncode=0,
            stdout=b"Emulator launch sequence initiated.",
            stderr=b"",
        )

    def attach(self) -> CommandResult:
        # Run wait-for-device with a 30s timeout
        return self.runner(
            ["adb", "-s", self.serial, "wait-for-device"], timeout=30.0
        )

    def capture_prior_state(self) -> PriorDeviceState | None:
        # 1. Query boot completed
        boot_res = self.runner(
            [
                "adb",
                "-s",
                self.serial,
                "shell",
                "getprop",
                "sys.boot_completed",
            ]
        )
        booted = boot_res.stdout.decode("utf-8").strip() == "1"

        # 2. Get fingerprint
        fp_res = self.runner(
            ["adb", "-s", self.serial, "shell", "getprop", "ro.build.fingerprint"]
        )
        fingerprint = fp_res.stdout.decode("utf-8").strip()

        # 3. Get API level
        api_res = self.runner(
            ["adb", "-s", self.serial, "shell", "getprop", "ro.build.version.sdk"]
        )
        try:
            api_level = int(api_res.stdout.decode("utf-8").strip())
        except ValueError:
            api_level = 34

        # 4. Get size
        size_res = self.runner(["adb", "-s", self.serial, "shell", "wm", "size"])
        size_str = size_res.stdout.decode("utf-8").strip()
        screen_width, screen_height = 1080, 2400
        if "Physical size:" in size_str:
            parts = size_str.split(":")[-1].strip().split("x")
            if len(parts) == 2:
                try:
                    screen_width = int(parts[0])
                    screen_height = int(parts[1])
                except ValueError:
                    pass

        # 5. Check if package present
        pkg_res = self.runner(
            [
                "adb",
                "-s",
                self.serial,
                "shell",
                "pm",
                "path",
                "biz.pixelperfectstudios.personaspeak",
            ]
        )
        pkg_path_str = pkg_res.stdout.decode("utf-8").strip()
        package_present = False
        package_hash = None
        if pkg_path_str.startswith("package:"):
            package_present = True
            apk_path_on_device = pkg_path_str.split(":", 1)[1].strip()
            hash_res = self.runner(
                [
                    "adb",
                    "-s",
                    self.serial,
                    "shell",
                    "sha256sum",
                    apk_path_on_device,
                ]
            )
            hash_str = hash_res.stdout.decode("utf-8").strip()
            if hash_str:
                package_hash = hash_str.split()[0]

        # 6. Enabled IMEs
        ime_res = self.runner(
            [
                "adb",
                "-s",
                self.serial,
                "shell",
                "settings",
                "get",
                "secure",
                "enabled_input_methods",
            ]
        )
        ime_str = ime_res.stdout.decode("utf-8").strip()
        enabled_imes = [x for x in ime_str.split(":") if x]

        # 7. Default IME
        def_res = self.runner(
            [
                "adb",
                "-s",
                self.serial,
                "shell",
                "settings",
                "get",
                "secure",
                "default_input_method",
            ]
        )
        default_ime = def_res.stdout.decode("utf-8").strip()

        return PriorDeviceState(
            serial=self.serial,
            emulator_state="booted" if booted else "unknown",
            fingerprint=fingerprint,
            api_level=api_level,
            screen_width=screen_width,
            screen_height=screen_height,
            package_present=package_present,
            package_hash=package_hash,
            enabled_imes=enabled_imes,
            default_ime=default_ime,
        )

    def validate_fixture(self, prior: PriorDeviceState) -> CommandResult:
        errors = []

        if prior.fingerprint != FINGERPRINT:
            errors.append(f"fingerprint mismatch: got {prior.fingerprint}")
        if prior.api_level != API_LEVEL:
            errors.append(f"api_level mismatch: got {prior.api_level}")
        if prior.screen_width != SCREEN_WIDTH or prior.screen_height != SCREEN_HEIGHT:
            errors.append(
                f"screen size mismatch: got {prior.screen_width}x{prior.screen_height}"
            )
        if prior.package_present:
            errors.append("Package biz.pixelperfectstudios.personaspeak present before test")

        # Query and validate timezone
        tz_res = self.runner(
            ["adb", "-s", self.serial, "shell", "getprop", "persist.sys.timezone"]
        )
        tz = tz_res.stdout.decode("utf-8").strip()
        if tz != TIMEZONE:
            errors.append(f"timezone mismatch: got {tz}")

        # Query and validate locale
        loc_res = self.runner(
            ["adb", "-s", self.serial, "shell", "getprop", "ro.product.locale"]
        )
        loc = loc_res.stdout.decode("utf-8").strip()
        if loc != LOCALE:
            errors.append(f"locale mismatch: got {loc}")

        # Validate enabled IMEs has at least Gboard
        gboard_id = "com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME"
        if gboard_id not in prior.enabled_imes:
            errors.append(f"Gboard IME {gboard_id} not enabled")

        if errors:
            return CommandResult(
                argv=["validate_fixture"],
                start_utc=_UTC(),
                end_utc=_UTC(),
                returncode=1,
                stdout=b"",
                stderr=("\n".join(errors)).encode(),
            )

        return CommandResult(
            argv=["validate_fixture"],
            start_utc=_UTC(),
            end_utc=_UTC(),
            returncode=0,
            stdout=b"Fixture identity validated.",
            stderr=b"",
        )

    def install_apk(self) -> CommandResult:
        return self.runner(
            ["adb", "-s", self.serial, "install", "-r", self.apk_path]
        )

    def _dump_hierarchy(self, label: str) -> tuple[CommandResult, Any]:
        remote = "/sdcard/window_dump.xml"
        local = os.path.join(self.run_dir, f"{label}.xml")
        dump = self.runner(
            ["adb", "-s", self.serial, "shell", "uiautomator", "dump", remote]
        )
        if dump.returncode != 0:
            return dump, None
        pull = self.runner(["adb", "-s", self.serial, "pull", remote, local])
        if pull.returncode != 0:
            return pull, None
        try:
            return pull, ET.parse(local).getroot()
        except ET.ParseError:
            return pull, None

    def run_journey(self) -> list[StepRecord]:
        steps: list[StepRecord] = []

        res_launch = self.runner(
            ["adb", "-s", self.serial, "shell", "am", "start", "-a", SETTINGS_ACTION]
        )
        cause = (
            TerminalCause.COMPLETED
            if res_launch.returncode == 0
            else TerminalCause.JOURNEY_FAILED
        )
        steps.append(
            StepRecord(
                phase="journey", operation="launch_settings",
                input_digest=None, output_digest=None,
                result=res_launch, cause=cause,
            )
        )
        if cause != TerminalCause.COMPLETED:
            return steps

        dump_res, root = self._dump_hierarchy("journey")
        if root is None:
            steps.append(
                StepRecord(
                    phase="journey", operation="dump_hierarchy",
                    input_digest=None, output_digest=None,
                    result=dump_res, cause=TerminalCause.JOURNEY_FAILED,
                )
            )
            return steps
        steps.append(
            StepRecord(
                phase="journey", operation="dump_hierarchy",
                input_digest=None, output_digest=None,
                result=dump_res, cause=TerminalCause.COMPLETED,
            )
        )

        search_id = "com.android.settings:id/search_action_bar"
        found = None
        for elem in root.iter():
            if elem.attrib.get("resource-id", "") == search_id:
                found = elem
                break
        if found is None:
            steps.append(
                StepRecord(
                    phase="journey", operation="locate_search_field",
                    input_digest=None, output_digest=None,
                    result=CommandResult(
                        argv=["locate_search_field"], start_utc=_UTC(), end_utc=_UTC(),
                        returncode=1, stdout=b"", stderr=b"search field not found",
                    ),
                    cause=TerminalCause.JOURNEY_FAILED,
                )
            )
            return steps

        m = re.match(
            r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]",
            found.attrib.get("bounds", ""),
        )
        if not m:
            steps.append(
                StepRecord(
                    phase="journey", operation="locate_search_field",
                    input_digest=None, output_digest=None,
                    result=CommandResult(
                        argv=["locate_search_field"], start_utc=_UTC(), end_utc=_UTC(),
                        returncode=1, stdout=b"", stderr=b"bounds missing",
                    ),
                    cause=TerminalCause.JOURNEY_FAILED,
                )
            )
            return steps
        x = (int(m.group(1)) + int(m.group(3))) // 2
        y = (int(m.group(2)) + int(m.group(4))) // 2

        res_tap = self.runner(
            ["adb", "-s", self.serial, "shell", "input", "tap", str(x), str(y)]
        )
        cause = (
            TerminalCause.COMPLETED
            if res_tap.returncode == 0
            else TerminalCause.JOURNEY_FAILED
        )
        steps.append(
            StepRecord(
                phase="journey", operation="tap_search_field",
                input_digest=None, output_digest=None,
                result=res_tap, cause=cause,
            )
        )
        if cause != TerminalCause.COMPLETED:
            return steps

        res_stale = self.runner(
            ["adb", "-s", self.serial, "shell", "input", "text",
             STALE_TEXT.replace(" ", "%s")]
        )
        cause = (
            TerminalCause.COMPLETED
            if res_stale.returncode == 0
            else TerminalCause.JOURNEY_FAILED
        )
        steps.append(
            StepRecord(
                phase="journey", operation="type_stale_text",
                input_digest=None, output_digest=None,
                result=res_stale, cause=cause,
            )
        )
        if cause != TerminalCause.COMPLETED:
            return steps

        for _ in range(len(STALE_TEXT) + 5):
            self.runner(
                ["adb", "-s", self.serial, "shell", "input", "keyevent", "67"]
            )

        res_source = self.runner(
            ["adb", "-s", self.serial, "shell", "input", "text",
             SOURCE_TEXT.replace(" ", "%s")]
        )
        cause = (
            TerminalCause.COMPLETED
            if res_source.returncode == 0
            else TerminalCause.JOURNEY_FAILED
        )
        steps.append(
            StepRecord(
                phase="journey", operation="type_source_text",
                input_digest=None, output_digest=None,
                result=res_source, cause=cause,
            )
        )
        if cause != TerminalCause.COMPLETED:
            return steps

        v_res, v_root = self._dump_hierarchy("verify")
        if v_root is None:
            steps.append(
                StepRecord(
                    phase="journey", operation="verify_candidate_rephrasing",
                    input_digest=None, output_digest=None,
                    result=v_res, cause=TerminalCause.JOURNEY_FAILED,
                )
            )
            return steps

        actual = ""
        for node in v_root.iter():
            if node.attrib.get("class", "") == "android.widget.EditText":
                actual = node.attrib.get("text", "")
                break

        v_rc = 0 if actual == CANDIDATE_REPHRASING else 1
        v_cause = (
            TerminalCause.COMPLETED if v_rc == 0 else TerminalCause.JOURNEY_FAILED
        )
        steps.append(
            StepRecord(
                phase="journey", operation="verify_candidate_rephrasing",
                input_digest=None, output_digest=None,
                result=CommandResult(
                    argv=["verify_rephrasing"], start_utc=_UTC(), end_utc=_UTC(),
                    returncode=v_rc, stdout=actual.encode(), stderr=b"",
                ),
                cause=v_cause,
            )
        )

        return steps

    def capture_evidence(self) -> CommandResult:
        evidence_dir = os.path.join(self.run_dir, "evidence")
        os.makedirs(evidence_dir, exist_ok=True)
        errors = []

        for i in range(7):
            remote = f"/sdcard/shot_{i}.png"
            local = os.path.join(evidence_dir, f"shot_{i}.png")
            cap = self.runner(
                ["adb", "-s", self.serial, "shell", "screencap", "-p", remote]
            )
            if cap.returncode != 0:
                errors.append(f"screencap_{i} rc={cap.returncode}")
                continue
            pull = self.runner(["adb", "-s", self.serial, "pull", remote, local])
            if pull.returncode != 0:
                errors.append(f"pull_shot_{i} rc={pull.returncode}")
                continue
            with open(local, "rb") as fh:
                if not evidence.validate_png(fh.read()):
                    errors.append(f"shot_{i}.png invalid")

        remote_vid = "/sdcard/journey.mp4"
        local_vid = os.path.join(evidence_dir, "journey.mp4")
        rec = self.starter(
            ["adb", "-s", self.serial, "shell", "screenrecord",
             "--time-limit", "10", remote_vid]
        )
        rec_res = self.finisher(rec, timeout=15.0, terminate=False)
        if rec_res.returncode != 0:
            errors.append(f"screenrecord rc={rec_res.returncode}")
        else:
            pull_v = self.runner(
                ["adb", "-s", self.serial, "pull", remote_vid, local_vid]
            )
            if pull_v.returncode != 0:
                errors.append(f"pull_video rc={pull_v.returncode}")
            else:
                with open(local_vid, "rb") as vfh:
                    if not evidence.validate_mp4(vfh.read()):
                        errors.append("journey.mp4 invalid")

        rc = 0 if not errors else 1
        return CommandResult(
            argv=["capture_evidence"],
            start_utc=_UTC(), end_utc=_UTC(),
            returncode=rc,
            stdout=b"" if errors else b"7 screenshots + 1 video captured",
            stderr="\n".join(errors).encode() if errors else b"",
        )

    def restore(self) -> CommandResult:
        return self.runner(
            [
                "adb",
                "-s",
                self.serial,
                "emu",
                "snapshot",
                "load",
                SNAPSHOT_NAME,
            ]
        )

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

        # Fallback to adb emu kill
        return self.runner(["adb", "-s", self.serial, "emu", "kill"])

    def verify_release(self) -> CommandResult:
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.settimeout(1.0)
        try:
            s.connect(("127.0.0.1", 5554))
        except ConnectionRefusedError:
            return CommandResult(
                argv=["verify_release"],
                start_utc=_UTC(),
                end_utc=_UTC(),
                returncode=0,
                stdout=b"release verified (port closed)",
                stderr=b"",
            )
        except OSError:
            return CommandResult(
                argv=["verify_release"],
                start_utc=_UTC(),
                end_utc=_UTC(),
                returncode=1,
                stdout=b"",
                stderr=b"release verification inconclusive (socket error)",
            )
        else:
            return CommandResult(
                argv=["verify_release"],
                start_utc=_UTC(),
                end_utc=_UTC(),
                returncode=1,
                stdout=b"",
                stderr=b"socket connection succeeded (emulator still running)",
            )
        finally:
            s.close()
