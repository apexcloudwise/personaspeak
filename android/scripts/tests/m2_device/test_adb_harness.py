"""Tests for the production adb_harness implementation."""

from __future__ import annotations

import json
import os
import stat
import subprocess
import sys
import tempfile
import time
import unittest
from unittest.mock import MagicMock, patch

from android.scripts.m2_device import commands
from android.scripts.m2_device.adb_harness import (
    ABI,
    API_LEVEL,
    AVD_NAME,
    CANDIDATE_REPHRASING,
    EXPECTED_SIGNER,
    EXPECTED_VERSION_CODE,
    EXPECTED_VERSION_NAME,
    FINGERPRINT,
    LOCALE,
    SCREEN_HEIGHT,
    SCREEN_WIDTH,
    SETTINGS_ACTION,
    SNAPSHOT_NAME,
    SOURCE_TEXT,
    STALE_TEXT,
    TIMEZONE,
    AdbHarness,
)
from android.scripts.m2_device.orchestrator import CaptureContext
from android.scripts.m2_device.records import (
    CommandResult,
    PriorDeviceState,
    RemoteResult,
    StepRecord,
    TerminalCause,
    ToolIdentity,
)


def _cr(rc=0, stdout=b"", stderr=b"", argv=None):
    return CommandResult(
        argv=argv if argv is not None else [],
        start_utc="2026-08-06T12:00:00Z",
        end_utc="2026-08-06T12:00:01Z",
        returncode=rc,
        stdout=stdout,
        stderr=stderr,
    )


def _write_valid_png(path):
    import struct as _s
    import zlib as _z
    sig = b'\x89PNG\r\n\x1a\n'
    ihdr_data = _s.pack('>IIBBBBB', 1, 1, 8, 2, 0, 0, 0)
    ihdr = _s.pack('>I', 13) + b'IHDR' + ihdr_data + _s.pack('>I', _z.crc32(b'IHDR' + ihdr_data) & 0xFFFFFFFF)
    comp = _z.compress(b'\x00\xff\x00\x00')
    idat = _s.pack('>I', len(comp)) + b'IDAT' + comp + _s.pack('>I', _z.crc32(b'IDAT' + comp) & 0xFFFFFFFF)
    iend = _s.pack('>I', 0) + b'IEND' + _s.pack('>I', _z.crc32(b'IEND') & 0xFFFFFFFF)
    with open(path, 'wb') as f:
        f.write(sig + ihdr + idat + iend)


def _write_valid_mp4(path):
    import struct as _s
    payload = b'isom' + b'\x00\x00\x00\x00' + b'isom'
    ftyp = _s.pack('>I', 8 + len(payload)) + b'ftyp' + payload
    mdat = _s.pack('>I', 12) + b'mdat' + b'\x00\x00\x00\x00'
    with open(path, 'wb') as f:
        f.write(ftyp + mdat)


_KEY_GEOMETRY = [
    ("T", 430, 1330, 520, 1420), ("E", 240, 1330, 330, 1420),
    ("A", 55, 1430, 145, 1520), ("S", 150, 1430, 240, 1520),
    ("I", 715, 1330, 805, 1420), ("X", 200, 1530, 290, 1620),
    ("V", 390, 1530, 480, 1620), ("N", 580, 1530, 670, 1620),
    ("Space", 345, 1630, 675, 1720), ("Period", 685, 1630, 775, 1720),
]
KEY_NODES_XML = "".join(
    f'<node resource-id="biz.pixelperfectstudios.personaspeak:id/key"'
    f' content-desc="{lbl}" class="android.widget.Key"'
    f' bounds="[{x1},{y1}][{x2},{y2}]"/>'
    for lbl, x1, y1, x2, y2 in _KEY_GEOMETRY)


def _journey_xml(text="", kb="", focused=False, keys=True,
                 editor_class="android.widget.EditText"):
    """Hierarchy XML matching the fake-toolchain contract: editor with
    focus attribute, keyboard view, per-key geometry nodes, and the
    panel/candidate/button set implied by *kb*."""
    ps = "biz.pixelperfectstudios.personaspeak:id"
    ss = "com.android.settings:id"
    nodes = (
        f'<node resource-id="{ps}/keyboard_view" '
        f'class="android.widget.FrameLayout" bounds="[0,1300][1080,2400]"/>'
    )
    if keys:
        nodes += KEY_NODES_XML
    if text:
        nodes += (
            f'<node resource-id="{ss}/search_close_btn" '
            f'content-desc="Clear" class="android.widget.ImageView" '
            f'bounds="[950,200][1020,270]"/>'
        )
    if kb == "LOADING":
        nodes += (
            f'<node text="LOADING" resource-id="{ps}/panel_state" '
            f'class="android.widget.TextView" bounds="[10,1810][1070,1850]"/>'
            f'<node resource-id="{ps}/cancel_button" content-desc="Cancel" '
            f'class="android.widget.Button" bounds="[580,2300][980,2380]"/>'
        )
    elif kb == "REVIEW":
        nodes += (
            f'<node text="REVIEW" resource-id="{ps}/panel_state" '
            f'class="android.widget.TextView" bounds="[10,1810][1070,1850]"/>'
            f'<node text="{CANDIDATE_REPHRASING}" resource-id="{ps}/candidate_text" '
            f'class="android.widget.TextView" bounds="[10,1850][1070,1900]"/>'
            f'<node resource-id="{ps}/apply_button" content-desc="Apply" '
            f'class="android.widget.Button" bounds="[100,2300][500,2380]"/>'
            f'<node resource-id="{ps}/cancel_button" content-desc="Cancel" '
            f'class="android.widget.Button" bounds="[580,2300][980,2380]"/>'
        )
    return (
        '<hierarchy rotation="0">'
        f'<node index="0" text="{text}" '
        f'focused="{"true" if focused else "false"}" '
        f'resource-id="{ss}/search_action_bar" class="{editor_class}" '
        f'bounds="[100,200][900,300]"/>{nodes}</hierarchy>'
    )


def _journey_pull_writer():
    """Returns a side_effect(argv, **kwargs) that answers every pull the
    journey makes, mirroring the fake adb's state machine."""
    def side_effect(argv, **kwargs):
        if "pull" not in argv:
            return _cr(rc=0, argv=argv)
        dest = argv[-1]
        label = os.path.basename(dest)
        if dest.endswith(".png"):
            _write_valid_png(dest)
            return _cr(rc=0, argv=argv)
        if dest.endswith(".mp4"):
            _write_valid_mp4(dest)
            return _cr(rc=0, argv=argv)
        if label.startswith("loading"):
            xml = _journey_xml("Tea at six.", "LOADING")
        elif "after_stale_dismiss" in label:
            xml = _journey_xml(STALE_TEXT)
        elif "after_stale" in label:
            xml = _journey_xml(STALE_TEXT, "REVIEW")
        elif label.startswith("review"):
            xml = _journey_xml("Tea at six.", "REVIEW")
        elif "after_cancel" in label or "after_dismiss" in label:
            xml = _journey_xml("Tea at six.")
        elif "after_apply" in label:
            xml = _journey_xml(CANDIDATE_REPHRASING)
        elif "keyboard_check" in label:
            xml = _journey_xml("", focused=True)
        elif "verify_restore" in label:
            xml = _journey_xml("")
        else:
            xml = _journey_xml("")
        with open(dest, "w") as f:
            f.write(xml)
        return _cr(rc=0, argv=argv)
    return side_effect


class TestAdbHarness(unittest.TestCase):

    def setUp(self):
        self.tmp_dir = tempfile.TemporaryDirectory()
        self.run_dir = self.tmp_dir.name
        self.apk_path = os.path.join(self.run_dir, "test.apk")
        with open(self.apk_path, "wb") as f:
            f.write(b"apk content")

        # Fake fixture tree: real files, digests computed and injected so
        # the fixture transaction runs against honest bytes.
        import hashlib as _hl
        self.fixture_root = os.path.join(self.run_dir, "avd")
        self.fixture_digests = {}
        for rel in ("M2_Qual_Fixture.avd/snapshots/m2_pristine/hardware.ini",
                    "M2_Qual_Fixture.avd/snapshots/m2_pristine/ram.bin",
                    "M2_Qual_Fixture.avd/snapshots/m2_pristine/textures.bin"):
            path = os.path.join(self.fixture_root, *rel.split("/"))
            os.makedirs(os.path.dirname(path), exist_ok=True)
            content = f"fixture-bytes:{rel}".encode()
            with open(path, "wb") as f:
                f.write(content)
            self.fixture_digests[rel] = _hl.sha256(content).hexdigest()

        self.mock_runner = MagicMock()
        self.mock_starter = MagicMock()
        self.mock_finisher = MagicMock()

        self.harness = AdbHarness(
            run_dir=self.run_dir,
            apk_path=self.apk_path,
            runner=self.mock_runner,
            starter=self.mock_starter,
            finisher=self.mock_finisher,
            fixture_root=self.fixture_root,
            fixture_digests=self.fixture_digests,
        )
        self.harness.adb_tool = ToolIdentity(name="adb", path="adb", version="1.0")
        self.harness.emulator_tool = ToolIdentity(name="emulator", path="emulator", version="1.0")

    def tearDown(self):
        self.tmp_dir.cleanup()

    @patch.dict(os.environ, {"ANDROID_HOME": "", "ANDROID_SDK_ROOT": ""})
    @patch("socket.socket")
    @patch("android.scripts.m2_device.commands.resolve_tool")
    def test_preflight_success(self, mock_resolve, mock_socket_cls):
        def fake_resolve(name, **kwargs):
            return ToolIdentity(
                name=name, path=f"/bin/{name}",
                version=f"Android {'Debug Bridge' if name == 'adb' else 'emulator'} version "
                        f"{'1.0.41' if name == 'adb' else '36.6.11.0'}",
                digest="abc123",
            )
        mock_resolve.side_effect = fake_resolve
        mock_sock = MagicMock()
        mock_sock.connect.side_effect = ConnectionRefusedError(111, "Connection refused")
        mock_socket_cls.return_value = mock_sock
        self.mock_runner.return_value = _cr(stdout=b"M2_Qual_Fixture\n")
        res = self.harness.preflight()
        self.assertEqual(res.returncode, 0)
        self.assertIsNotNone(self.harness.adb_tool)
        self.assertIsNotNone(self.harness.emulator_tool)

    @patch("android.scripts.m2_device.commands.resolve_tool")
    def test_preflight_failure(self, mock_resolve):
        mock_resolve.side_effect = FileNotFoundError("tool not found")
        res = self.harness.preflight()
        self.assertEqual(res.returncode, 1)
        self.assertIn(b"tool not found", res.stderr)

    @patch("android.scripts.m2_device.commands.resolve_tool")
    def test_capture_context(self, mock_resolve):
        mock_resolve.side_effect = lambda name: ToolIdentity(
            name=name, path=f"/bin/{name}", version="1.0"
        )

        def side_effect(argv, **kwargs):
            if "status" in argv:
                return _cr(stdout=b"")
            return _cr(stdout=b"abc123git\n")

        self.mock_runner.side_effect = side_effect

        ctx = self.harness.capture_context()
        self.assertEqual(ctx.repo_head, "abc123git")
        self.assertTrue(ctx.apk_sha256)
        self.assertEqual(len(ctx.tools), 2)
        # setUp injects fake-only digests → the run must be mechanically
        # barred from claiming the accepted fixture receipt.
        self.assertEqual(ctx.fixture_receipt_digest, "")

    def test_capture_context_records_accepted_receipt_when_pinned(self):
        h = AdbHarness(
            run_dir=self.run_dir, apk_path=self.apk_path,
            runner=self.mock_runner,
            fixture_root=self.fixture_root,
        )

        def side_effect(argv, **kwargs):
            if "status" in argv:
                return _cr(stdout=b"")
            return _cr(stdout=b"abc123git\n")

        self.mock_runner.side_effect = side_effect
        from android.scripts.m2_device.adb_harness import FIXTURE_RECEIPT_DIGEST
        ctx = h.capture_context()
        self.assertEqual(ctx.fixture_receipt_digest, FIXTURE_RECEIPT_DIGEST)

    def test_run_journey_fails_closed_on_unparsable_keyboard_hierarchy(self):
        # Reviewer reproduction: malformed keyboard_check XML must stop
        # the journey before any tap — absent facts never authorize one.
        writer = _journey_pull_writer()

        def side_effect(argv, **kwargs):
            if "pull" in argv and "keyboard_check" in argv[-1]:
                with open(argv[-1], "w") as f:
                    f.write("<not-xml")
                return _cr(rc=0, argv=argv)
            return writer(argv, **kwargs)

        self.mock_runner.side_effect = side_effect
        self.mock_starter.return_value = MagicMock()
        steps = self.harness.run_journey()
        failed = [s for s in steps if s.operation == "keyboard_hierarchy"]
        self.assertTrue(failed)
        self.assertNotEqual(failed[0].cause, TerminalCause.COMPLETED)
        self.assertIn(b"unparsable", failed[0].result.stderr)
        self.assertFalse(
            [s for s in steps if s.operation.startswith("tap_key")],
            "taps must not run on the strength of absent hierarchy facts")

    def test_run_journey_fails_closed_on_failed_keyboard_dump(self):
        writer = _journey_pull_writer()
        dump_calls = {"n": 0}

        def side_effect(argv, **kwargs):
            cmd = " ".join(argv)
            if "uiautomator" in cmd:
                dump_calls["n"] += 1
                if dump_calls["n"] == 2:  # keyboard_check is the 2nd dump
                    return _cr(rc=1, stderr=b"dump failed")
            return writer(argv, **kwargs)

        self.mock_runner.side_effect = side_effect
        self.mock_starter.return_value = MagicMock()
        steps = self.harness.run_journey()
        failed = [s for s in steps if s.operation == "keyboard_hierarchy"]
        self.assertTrue(failed)
        self.assertNotEqual(failed[0].cause, TerminalCause.COMPLETED)
        self.assertFalse(
            [s for s in steps if s.operation.startswith("tap_key")])

    def test_run_journey_fails_closed_on_unparsable_first_hierarchy(self):
        # Same contract for the first journey dump: a clean wrapper rc
        # never certifies unreadable facts — the journey must fail, not
        # record COMPLETED and truncate silently.
        writer = _journey_pull_writer()

        def side_effect(argv, **kwargs):
            if "pull" in argv and "journey.xml" in argv[-1]:
                with open(argv[-1], "w") as f:
                    f.write("<not-xml")
                return _cr(rc=0, argv=argv)
            return writer(argv, **kwargs)

        self.mock_runner.side_effect = side_effect
        self.mock_starter.return_value = MagicMock()
        steps = self.harness.run_journey()
        failed = [s for s in steps if s.operation == "dump_hierarchy"]
        self.assertTrue(failed)
        self.assertEqual(failed[0].cause, TerminalCause.JOURNEY_FAILED)
        self.assertIn(b"hierarchy missing or unparsable",
                      failed[0].result.stderr)
        self.assertFalse(
            [s for s in steps if s.operation.startswith("tap_key")],
            "taps must not run on unparsable journey facts")

    def test_run_journey_fails_closed_on_silent_hierarchy_pull(self):
        # Hostile pull: rc=0 with no file written. The journey fails
        # closed with a recorded step — no exception may escape
        # run_journey and cost the run its capture record.
        writer = _journey_pull_writer()

        def side_effect(argv, **kwargs):
            if "pull" in argv and "window_dump.xml" in argv[-2]:
                return _cr(rc=0, argv=argv)
            return writer(argv, **kwargs)

        self.mock_runner.side_effect = side_effect
        self.mock_starter.return_value = MagicMock()
        steps = self.harness.run_journey()
        failed = [s for s in steps if s.operation == "dump_hierarchy"]
        self.assertTrue(failed)
        self.assertEqual(failed[0].cause, TerminalCause.JOURNEY_FAILED)
        self.assertIn(b"hierarchy missing or unparsable",
                      failed[0].result.stderr)

    def test_launch_emulator(self):
        self.mock_starter.return_value = MagicMock()
        res = self.harness.launch_emulator()
        self.assertEqual(res.returncode, 0)
        self.mock_starter.assert_called_once()
        self.assertIsNotNone(self.harness.emulator_process)

    def test_attach(self):
        self.mock_runner.return_value = _cr(rc=0)
        res = self.harness.attach()
        self.assertEqual(res.returncode, 0)
        self.mock_runner.assert_called_once_with(
            ["adb", "-s", "emulator-5554", "wait-for-device"], timeout=30.0
        )

    def test_capture_prior_state(self):
        # Set up responses for adb getprop, wm size, pm path, secure settings
        gboard = (
            "com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME"
        )
        enabled_imes = f"{gboard}:com.google.android.tts/com.google.android.apps.speech.tts.googletts.settings.asr.voiceime.VoiceInputMethodService"

        def side_effect(argv, **kwargs):
            cmd = " ".join(argv)
            if "getprop sys.boot_completed" in cmd:
                return _cr(stdout=b"1\n")
            if "getprop ro.build.fingerprint" in cmd:
                return _cr(stdout=FINGERPRINT.encode())
            if "getprop ro.build.version.sdk" in cmd:
                return _cr(stdout=f"{API_LEVEL}\n".encode())
            if "wm size" in cmd:
                return _cr(
                    stdout=f"Physical size: {SCREEN_WIDTH}x{SCREEN_HEIGHT}\n".encode()
                )
            if "pm path" in cmd:
                return _cr(stdout=b"")  # Not present
            if "settings get secure enabled_input_methods" in cmd:
                return _cr(stdout=enabled_imes.encode())
            if "settings get secure default_input_method" in cmd:
                return _cr(stdout=gboard.encode())
            return _cr()

        self.mock_runner.side_effect = side_effect
        state = self.harness.capture_prior_state()
        self.assertIsNotNone(state)
        self.assertEqual(state.serial, "emulator-5554")
        self.assertEqual(state.emulator_state, "booted")
        self.assertEqual(state.fingerprint, FINGERPRINT)
        self.assertEqual(state.api_level, API_LEVEL)
        self.assertEqual(state.screen_width, SCREEN_WIDTH)
        self.assertEqual(state.screen_height, SCREEN_HEIGHT)
        self.assertFalse(state.package_present)
        self.assertEqual(len(state.enabled_imes), 2)
        self.assertEqual(state.default_ime, gboard)

    def test_capture_prior_state_empty_streams(self):
        self.mock_runner.return_value = _cr(stdout=b"")
        state = self.harness.capture_prior_state()
        self.assertIsNone(state)

    def test_capture_prior_state_hostile_bytes(self):
        self.mock_runner.return_value = _cr(stdout=b"\x00\xff\xfe\x00garbage\x80\x81")
        state = self.harness.capture_prior_state()
        self.assertIsNone(state)

    def test_validate_fixture_success(self):
        from android.scripts.m2_device.adb_harness import EXPECTED_ENABLED_IMES
        prior = PriorDeviceState(
            serial="emulator-5554",
            emulator_state="booted",
            fingerprint=FINGERPRINT,
            api_level=API_LEVEL,
            screen_width=SCREEN_WIDTH,
            screen_height=SCREEN_HEIGHT,
            package_present=False,
            package_hash=None,
            enabled_imes=list(EXPECTED_ENABLED_IMES),
            default_ime=EXPECTED_ENABLED_IMES[0],
        )

        def side_effect(argv, **kwargs):
            cmd = " ".join(argv)
            if "getprop persist.sys.timezone" in cmd:
                return _cr(stdout=TIMEZONE.encode())
            if "getprop ro.product.locale" in cmd:
                return _cr(stdout=LOCALE.encode())
            if "ro.product.cpu.abi" in cmd:
                return _cr(stdout=ABI.encode())
            if "qemu.sf.lcd_density" in cmd:
                return _cr(stdout=b"420\n")
            if "window_animation_scale" in cmd:
                return _cr(stdout=b"1.0\n")
            if "transition_animation_scale" in cmd:
                return _cr(stdout=b"1.0\n")
            if "animator_duration_scale" in cmd:
                return _cr(stdout=b"null\n")
            if "default_input_method" in cmd:
                return _cr(stdout=EXPECTED_ENABLED_IMES[0].encode())
            return _cr()

        self.mock_runner.side_effect = side_effect
        res = self.harness.validate_fixture(prior)
        self.assertEqual(res.returncode, 0)
        self.assertIn(b"fake-only", res.stdout)
        self.assertIn(b"not an accepted-fixture qualification", res.stdout)

    def test_validate_fixture_failure(self):
        # Fingerprint mismatch
        prior = PriorDeviceState(
            serial="emulator-5554",
            emulator_state="booted",
            fingerprint="bad_fingerprint",
            api_level=API_LEVEL,
            screen_width=SCREEN_WIDTH,
            screen_height=SCREEN_HEIGHT,
            package_present=False,
            package_hash=None,
            enabled_imes=[],
            default_ime="",
        )
        res = self.harness.validate_fixture(prior)
        self.assertEqual(res.returncode, 1)

    def test_install_apk(self):

        def side_effect(argv, **kwargs):
            cmd = " ".join(argv)
            if "dumpsys" in cmd:
                return _cr(rc=0, stdout=(
                    f"versionName={EXPECTED_VERSION_NAME}\n"
                    f"versionCode={EXPECTED_VERSION_CODE}\n"
                    f"signatures=PackageSignatures{{c441f2a version:2, signatures:[{EXPECTED_SIGNER}], past signatures:[]}}\n"
                ).encode())
            return _cr(rc=0)

        self.mock_runner.side_effect = side_effect
        res = self.harness.install_apk()
        self.assertEqual(res.returncode, 0)

    def test_install_apk_returns_command_result(self):
        def side_effect(argv, **kwargs):
            cmd = " ".join(argv)
            if "install" in cmd and "pull" not in cmd:
                return _cr(rc=0, stdout=b"Success")
            if "dumpsys" in cmd:
                return _cr(rc=0, stdout=(
                    f"versionName={EXPECTED_VERSION_NAME}\n"
                    f"versionCode={EXPECTED_VERSION_CODE}\n"
                    f"signatures=PackageSignatures{{c441f2a version:2, signatures:[{EXPECTED_SIGNER}], past signatures:[]}}\n"
                ).encode())
            return _cr(rc=0)

        self.mock_runner.side_effect = side_effect
        res = self.harness.install_apk()
        self.assertEqual(res.returncode, 0)
        self.assertNotIsInstance(res, RemoteResult)

    def test_install_apk_host_failure(self):
        def side_effect(argv, **kwargs):
            cmd = " ".join(argv)
            if "install" in cmd and "pull" not in cmd:
                return _cr(rc=1, stderr=b"device not found")
            return _cr(rc=0)

        self.mock_runner.side_effect = side_effect
        res = self.harness.install_apk()
        self.assertEqual(res.returncode, 1)

    def test_run_journey_success(self):
        self.mock_runner.side_effect = _journey_pull_writer()
        self.mock_starter.return_value = MagicMock()
        steps = self.harness.run_journey()
        operations = [s.operation for s in steps]
        for expected in ("launch_editor", "pin_pristine_state",
                         "pin_editor_focused_empty", "validate_key_geometry",
                         "verify_loading_1", "cancel_loading",
                         "verify_review_2", "apply_rephrasing",
                         "verify_apply", "dismiss_rephrasing",
                         "apply_stale", "verify_stale_candidate_retained",
                         "dismiss_stale_candidate", "verify_stale_dismissed",
                         "relaunch_settings"):
            self.assertIn(expected, operations)
        for step in steps:
            self.assertEqual(step.cause, TerminalCause.COMPLETED, f"{step.operation} failed")

    def test_run_journey_rephrasing_mismatch(self):
        writer = _journey_pull_writer()

        def side_effect(argv, **kwargs):
            if "pull" in argv and "after_apply" in argv[-1]:
                with open(argv[-1], "w") as f:
                    f.write(_journey_xml("wrong text"))
                return _cr(rc=0, argv=argv)
            return writer(argv, **kwargs)

        self.mock_runner.side_effect = side_effect
        self.mock_starter.return_value = MagicMock()
        steps = self.harness.run_journey()
        apply_verify = [s for s in steps if s.operation == "verify_apply"]
        self.assertTrue(apply_verify)
        self.assertEqual(apply_verify[0].cause, TerminalCause.JOURNEY_FAILED)

    def test_run_journey_field_not_found(self):

        def side_effect(argv, **kwargs):
            if "pull" in argv:
                with open(argv[-1], "w") as f:
                    f.write('<hierarchy rotation="0"></hierarchy>')
                return _cr(rc=0, argv=argv)
            return _cr(rc=0, argv=argv)

        self.mock_runner.side_effect = side_effect
        steps = self.harness.run_journey()
        locate = [s for s in steps if s.operation == "pin_pristine_state"]
        self.assertTrue(locate)
        self.assertEqual(locate[0].cause, TerminalCause.JOURNEY_FAILED)

    def test_validate_fixture_digest_drift(self):
        ram = os.path.join(
            self.fixture_root, "M2_Qual_Fixture.avd",
            "snapshots", "m2_pristine", "ram.bin")
        with open(ram, "ab") as f:
            f.write(b"drift")
        prior = PriorDeviceState(
            serial="emulator-5554", emulator_state="booted",
            fingerprint=FINGERPRINT, api_level=API_LEVEL,
            screen_width=SCREEN_WIDTH, screen_height=SCREEN_HEIGHT,
            package_present=False, package_hash=None,
            enabled_imes=[], default_ime="",
        )
        res = self.harness.validate_fixture(prior)
        self.assertEqual(res.returncode, 1)
        self.assertIn(b"digest drift", res.stderr)
        self.assertIn(b"ram.bin", res.stderr)

    def test_validate_fixture_missing_file(self):
        hw = os.path.join(self.fixture_root, "M2_Qual_Fixture.avd",
                          "snapshots", "m2_pristine", "hardware.ini")
        os.unlink(hw)
        prior = PriorDeviceState(
            serial="emulator-5554", emulator_state="booted",
            fingerprint=FINGERPRINT, api_level=API_LEVEL,
            screen_width=SCREEN_WIDTH, screen_height=SCREEN_HEIGHT,
            package_present=False, package_hash=None,
            enabled_imes=[], default_ime="",
        )
        res = self.harness.validate_fixture(prior)
        self.assertEqual(res.returncode, 1)
        self.assertIn(b"fixture file missing", res.stderr)

    def test_validate_fixture_animator_must_be_unset(self):
        from android.scripts.m2_device.adb_harness import EXPECTED_ENABLED_IMES
        prior = PriorDeviceState(
            serial="emulator-5554", emulator_state="booted",
            fingerprint=FINGERPRINT, api_level=API_LEVEL,
            screen_width=SCREEN_WIDTH, screen_height=SCREEN_HEIGHT,
            package_present=False, package_hash=None,
            enabled_imes=list(EXPECTED_ENABLED_IMES),
            default_ime=EXPECTED_ENABLED_IMES[0],
        )

        def side_effect(argv, **kwargs):
            cmd = " ".join(argv)
            if "animator_duration_scale" in cmd:
                return _cr(stdout=b"1.0\n")  # set — fixture requires unset
            return _cr(stdout=b"null\n")

        self.mock_runner.side_effect = side_effect
        res = self.harness.validate_fixture(prior)
        self.assertEqual(res.returncode, 1)
        self.assertIn(b"animator_duration_scale mismatch", res.stderr)

    def _run_journey_with_override(self, label, xml):
        writer = _journey_pull_writer()

        def side_effect(argv, **kwargs):
            if "pull" in argv and label in os.path.basename(argv[-1]):
                with open(argv[-1], "w") as f:
                    f.write(xml)
                return _cr(rc=0, argv=argv)
            return writer(argv, **kwargs)

        self.mock_runner.side_effect = side_effect
        self.mock_starter.return_value = MagicMock()
        return self.harness.run_journey()

    def test_run_journey_rejects_dirty_editor_at_pristine_pin(self):
        steps = self._run_journey_with_override(
            "journey.xml", _journey_xml("leftover text"))
        pin = [s for s in steps if s.operation == "pin_pristine_state"]
        self.assertTrue(pin)
        self.assertEqual(pin[0].cause, TerminalCause.JOURNEY_FAILED)

    def test_rejected_pristine_observation_not_retained_as_baseline(self):
        # Review finding: a rejected observation must not survive as the
        # restoration baseline, or the receipt blames a correct restore
        # for what was a precondition failure.
        self._run_journey_with_override(
            "journey.xml", _journey_xml("leftover text"))
        self.assertIsNone(self.harness._pristine_private)
        with patch.object(self.harness, "capture_prior_state",
                          return_value=object()):
            self.harness.verify_restore()  # must not raise

    def test_run_journey_rejects_unfocused_editor(self):
        steps = self._run_journey_with_override(
            "keyboard_check", _journey_xml("", focused=False))
        pin = [s for s in steps if s.operation == "pin_editor_focused_empty"]
        self.assertTrue(pin)
        self.assertEqual(pin[0].cause, TerminalCause.JOURNEY_FAILED)

    def test_run_journey_rejects_nonempty_editor_at_focus(self):
        steps = self._run_journey_with_override(
            "keyboard_check", _journey_xml("Tea at six.", focused=True))
        pin = [s for s in steps if s.operation == "pin_editor_focused_empty"]
        self.assertTrue(pin)
        self.assertEqual(pin[0].cause, TerminalCause.JOURNEY_FAILED)

    def _keys_xml_without(self, label):
        keys = "".join(
            f'<node resource-id="biz.pixelperfectstudios.personaspeak:id/key"'
            f' content-desc="{lbl}" class="android.widget.Key"'
            f' bounds="[{x1},{y1}][{x2},{y2}]"/>'
            for lbl, x1, y1, x2, y2 in _KEY_GEOMETRY if lbl != label)
        return _journey_xml("", focused=True).replace(KEY_NODES_XML, keys)

    def test_key_geometry_rejects_duplicate_key(self):
        import xml.etree.ElementTree as ET
        first_node = KEY_NODES_XML[:KEY_NODES_XML.index("/>") + 2]
        dup = _journey_xml("", focused=True).replace(
            KEY_NODES_XML, KEY_NODES_XML + first_node)
        steps = []
        ok = self.harness._validate_key_geometry(steps, ET.fromstring(dup))
        self.assertFalse(ok)
        self.assertIn(b"matching nodes", steps[0].result.stderr)

    def test_key_geometry_rejects_missing_key(self):
        import xml.etree.ElementTree as ET
        steps = []
        ok = self.harness._validate_key_geometry(
            steps, ET.fromstring(self._keys_xml_without("X")))
        self.assertFalse(ok)
        self.assertIn(b"key X", steps[0].result.stderr)

    def test_key_geometry_rejects_displaced_key(self):
        import xml.etree.ElementTree as ET
        displaced = KEY_NODES_XML.replace(
            'content-desc="T" class="android.widget.Key" bounds="[430,1330][520,1420]"',
            'content-desc="T" class="android.widget.Key" bounds="[0,0][10,10]"')
        xml = _journey_xml("", focused=True).replace(KEY_NODES_XML, displaced)
        steps = []
        ok = self.harness._validate_key_geometry(steps, ET.fromstring(xml))
        self.assertFalse(ok)
        self.assertIn(b"outside", steps[0].result.stderr)

    def test_run_journey_stale_candidate_must_be_retained(self):
        writer = _journey_pull_writer()

        def side_effect(argv, **kwargs):
            dest = os.path.basename(argv[-1]) if "pull" in argv else ""
            if "after_stale" in dest and "dismiss" not in dest:
                with open(argv[-1], "w") as f:
                    f.write(_journey_xml(STALE_TEXT))  # candidate dropped
                return _cr(rc=0, argv=argv)
            return writer(argv, **kwargs)

        self.mock_runner.side_effect = side_effect
        self.mock_starter.return_value = MagicMock()
        steps = self.harness.run_journey()
        retained = [s for s in steps
                    if s.operation == "verify_stale_candidate_retained"]
        self.assertTrue(retained)
        self.assertEqual(retained[0].cause, TerminalCause.JOURNEY_FAILED)
        self.assertIn(b"not retained", retained[0].result.stderr)

    def test_verify_restore_private_fact_mismatch(self):
        self.harness._pristine_private = {
            "editor_text": "", "editor_focused": False,
            "panel_present": False}

        def side_effect(argv, **kwargs):
            if "pull" in argv:
                with open(argv[-1], "w") as f:
                    f.write(_journey_xml("leftover text"))
                return _cr(rc=0, argv=argv)
            return _cr(rc=0, argv=argv)

        self.mock_runner.side_effect = side_effect
        with patch.object(self.harness, "capture_prior_state",
                          return_value=object()):
            with self.assertRaises(RuntimeError) as cm:
                self.harness.verify_restore()
        self.assertIn("private facts", str(cm.exception))

    def test_capture_evidence_screencap_failure(self):
        self.mock_runner.return_value = _cr(rc=1)
        self.mock_finisher.return_value = _cr(rc=0)
        res = self.harness.capture_evidence()
        self.assertEqual(res.returncode, 1)

    def test_capture_evidence_invalid_media(self):

        def side_effect(argv, **kwargs):
            if "pull" in argv:
                with open(argv[-1], "wb") as f:
                    f.write(b"not valid media")
                return _cr(rc=0, argv=argv)
            return _cr(rc=0, argv=argv)

        self.mock_runner.side_effect = side_effect
        self.mock_finisher.return_value = _cr(rc=0)
        res = self.harness.capture_evidence()
        self.assertEqual(res.returncode, 1)

    def test_capture_evidence_success(self):
        evidence_dir = os.path.join(self.run_dir, "artifacts")
        os.makedirs(evidence_dir, exist_ok=True)
        for name in ("01-idle-typed", "02-loading-cancel", "03-review",
                      "04-applied", "05-dismissed", "06-stale", "07-settings"):
            _write_valid_png(os.path.join(evidence_dir, f"{name}.png"))

        def side_effect(argv, **kwargs):
            if "pull" in argv and argv[-1].endswith(".mp4"):
                _write_valid_mp4(argv[-1])
                return _cr(rc=0, argv=argv)
            return _cr(rc=0, argv=argv)

        self.mock_runner.side_effect = side_effect
        self.mock_finisher.return_value = _cr(rc=0)
        self.harness.screenrecord_process = MagicMock()
        res = self.harness.capture_evidence()
        self.assertEqual(res.returncode, 0)

    def test_restore(self):
        self.mock_runner.return_value = _cr(rc=0)
        res = self.harness.restore()
        self.assertEqual(res.returncode, 0)
        self.mock_runner.assert_called_once_with(
            ["adb", "-s", "emulator-5554", "emu", "snapshot", "load", SNAPSHOT_NAME],
            timeout=30.0,
        )

    def test_release_emulator_process(self):
        mock_process = MagicMock()
        mock_process.proc.poll.return_value = 0
        mock_process.new_session = False
        self.harness.emulator_process = mock_process

        res = self.harness.release_emulator()
        self.assertEqual(res.returncode, 0)
        self.assertIn(b"already exited", res.stdout)
        mock_process.proc.communicate.assert_called_once()
        self.assertIsNone(self.harness.emulator_process)

    def test_release_emulator_fallback(self):
        self.harness.emulator_process = None
        self.harness._owned_pid = None

        res = self.harness.release_emulator()
        self.assertEqual(res.returncode, 1)
        self.assertIn(b"no owned emulator", res.stderr)

    @patch("socket.socket")
    def test_verify_release_dead(self, mock_socket):
        mock_inst = MagicMock()
        mock_inst.connect.side_effect = ConnectionRefusedError(111, "Connection refused")
        mock_socket.return_value = mock_inst

        res = self.harness.verify_release()
        self.assertEqual(res.returncode, 0)

    @patch("socket.socket")
    def test_verify_release_alive(self, mock_socket):
        mock_inst = MagicMock()
        mock_socket.return_value = mock_inst

        res = self.harness.verify_release()
        self.assertEqual(res.returncode, 1)

    @patch("socket.socket")
    def test_verify_release_inconclusive(self, mock_socket):
        mock_inst = MagicMock()
        mock_inst.connect.side_effect = OSError("timed out")
        mock_socket.return_value = mock_inst

        res = self.harness.verify_release()
        self.assertEqual(res.returncode, 1)


class TestScreenrecordBoundary(unittest.TestCase):
    """screenrecord start/finish must cross the execution boundary:
    shell-v2 argv, bounded finish, RemoteResult conversion, ledger."""

    def setUp(self):
        self.tmp_dir = tempfile.TemporaryDirectory()
        self.run_dir = self.tmp_dir.name
        self.apk_path = os.path.join(self.run_dir, "test.apk")
        with open(self.apk_path, "wb") as f:
            f.write(b"apk content")
        self.mock_runner = MagicMock()
        self.mock_starter = MagicMock()
        self.mock_finisher = MagicMock()
        self.harness = AdbHarness(
            run_dir=self.run_dir,
            apk_path=self.apk_path,
            runner=self.mock_runner,
            starter=self.mock_starter,
            finisher=self.mock_finisher,
        )
        self.harness.adb_tool = ToolIdentity(name="adb", path="adb", version="1.0")

    def tearDown(self):
        self.tmp_dir.cleanup()

    def _recording(self):
        argv = ["adb", "-s", "emulator-5554", "shell", "screenrecord",
                "--time-limit", "30", "/sdcard/journey.mp4"]
        return commands.ManagedProcess(
            proc=MagicMock(), argv=argv,
            start_utc="2026-08-11T12:00:00Z")

    def test_shell_start_uses_shell_v2_argv(self):
        self.mock_starter.return_value = MagicMock()
        self.harness._shell_start(
            "screenrecord", "--time-limit", "30", "/sdcard/journey.mp4")
        self.mock_starter.assert_called_once_with(
            ["adb", "-s", "emulator-5554", "shell", "screenrecord",
             "--time-limit", "30", "/sdcard/journey.mp4"])

    def test_shell_finish_ledgers_and_converts_status(self):
        mp = self._recording()
        self.harness.screenrecord_process = mp
        self.mock_finisher.return_value = _cr(rc=0)
        res = self.harness._shell_finish(mp, timeout=15.0)
        self.assertEqual(res.remote_rc, 0)
        self.mock_finisher.assert_called_once_with(
            mp, timeout=15.0, terminate=False)
        entries = self.harness.ledger.entries()
        self.assertEqual(len(entries), 1)
        self.assertEqual(entries[0].kind, "shell")
        self.assertIn("screenrecord", " ".join(entries[0].argv))

    def _stage_valid_media(self):
        evidence_dir = os.path.join(self.run_dir, "artifacts")
        os.makedirs(evidence_dir, exist_ok=True)
        for name in ("01-idle-typed", "02-loading-cancel", "03-review",
                     "04-applied", "05-dismissed", "06-stale", "07-settings"):
            _write_valid_png(os.path.join(evidence_dir, f"{name}.png"))

        def side_effect(argv, **kwargs):
            if "pull" in argv and argv[-1].endswith(".mp4"):
                _write_valid_mp4(argv[-1])
                return _cr(rc=0, argv=argv)
            return _cr(rc=0, argv=argv)

        self.mock_runner.side_effect = side_effect

    def test_capture_evidence_reports_ambiguous_screenrecord(self):
        self._stage_valid_media()
        self.mock_finisher.return_value = _cr(rc=1, stderr=b"device offline")
        self.harness.screenrecord_process = self._recording()
        res = self.harness.capture_evidence()
        self.assertEqual(res.returncode, 1)
        self.assertIn(b"screenrecord status ambiguous", res.stderr)

    def test_capture_evidence_reports_screenrecord_timeout(self):
        self._stage_valid_media()
        self.mock_finisher.return_value = CommandResult(
            argv=["adb", "shell", "screenrecord"],
            start_utc="2026-08-11T12:00:00Z",
            end_utc="2026-08-11T12:00:30Z",
            returncode=-9, stdout=b"", stderr=b"", timed_out=True,
        )
        self.harness.screenrecord_process = self._recording()
        res = self.harness.capture_evidence()
        self.assertEqual(res.returncode, 1)
        self.assertIn(b"screenrecord timed out", res.stderr)

    def test_capture_evidence_reports_screenrecord_rc(self):
        self._stage_valid_media()
        self.mock_finisher.return_value = _cr(rc=3)
        self.harness.screenrecord_process = self._recording()
        res = self.harness.capture_evidence()
        self.assertEqual(res.returncode, 1)
        self.assertIn(b"screenrecord rc=3", res.stderr)

    def test_release_finishes_and_ledgers_recording(self):
        mp = self._recording()
        self.harness.screenrecord_process = mp
        self.mock_runner.return_value = _cr(rc=0)
        self.mock_finisher.return_value = _cr(rc=0)
        res = self.harness.restore()
        self.assertEqual(res.returncode, 0)
        self.mock_finisher.assert_called_once_with(
            mp, timeout=5.0, terminate=True)
        self.assertIsNone(self.harness.screenrecord_process)
        self.assertEqual(len(self.harness.ledger.entries()), 2)

    def test_capture_evidence_accepts_sigterm_stopped_recording(self):
        self._stage_valid_media()
        self.mock_finisher.return_value = _cr(rc=-15)
        self.harness.screenrecord_process = self._recording()
        res = self.harness.capture_evidence()
        self.assertEqual(res.returncode, 0)
        self.assertNotIn(b"screenrecord", res.stderr)

    def test_launch_emulator_records_launch_ledger_entry(self):
        self.harness.emulator_tool = ToolIdentity(
            name="emulator", path="/usr/bin/emulator", version="1.0")
        real_proc = subprocess.Popen(["sleep", "30"])
        self.mock_starter.return_value = commands.ManagedProcess(
            proc=real_proc,
            argv=["/usr/bin/emulator", "-avd", "M2_Qual_Fixture",
                  "-snapshot", "m2_pristine", "-no-snapshot-save",
                  "-port", "5554", "-gpu", "swiftshader_indirect"],
            start_utc="2026-08-16T12:00:00Z", new_session=True)
        try:
            res = self.harness.launch_emulator()
            self.assertEqual(res.returncode, 0)
            entries = self.harness.ledger.entries()
            self.assertEqual(len(entries), 1)
            self.assertEqual(entries[0].kind, "launch")
            self.assertIn("M2_Qual_Fixture", " ".join(entries[0].argv))
            self.assertIsNotNone(self.harness._launch_identity)
        finally:
            real_proc.terminate()
            real_proc.wait()

    def test_establish_ownership_rejects_defunct_identity(self):
        self.harness.emulator_process = commands.ManagedProcess(
            proc=MagicMock(),
            argv=["/usr/bin/emulator", "-avd", "M2_Qual_Fixture"],
            start_utc="2026-08-16T12:00:00Z")
        with patch(
            "android.scripts.m2_device.commands.pid_identity",
            return_value=commands.ProcessIdentity(
                start="Sun Aug 16 12:00:00 2026", command="<defunct>"),
        ):
            res = self.harness.establish_ownership()
        self.assertEqual(res.returncode, 1)
        self.assertIn(b"neither executable nor AVD", res.stderr)

    def test_establish_ownership_accepts_exec_engine_command(self):
        # The SDK launcher execs its engine: the command line changes
        # but keeps the pinned AVD; start time is continuous.
        self.harness.emulator_process = commands.ManagedProcess(
            proc=MagicMock(),
            argv=["/usr/bin/emulator", "-avd", "M2_Qual_Fixture"],
            start_utc="2026-08-16T12:00:00Z")
        observed = commands.ProcessIdentity(
            start="Sun Aug 16 12:00:00 2026",
            command="/sdk/qemu-system-arm64-headless -avd M2_Qual_Fixture -port 5554")
        with patch(
            "android.scripts.m2_device.commands.pid_identity",
            return_value=observed,
        ):
            res = self.harness.establish_ownership()
        self.assertEqual(res.returncode, 0)
        self.assertEqual(self.harness._owned_identity, observed)

    def test_release_reaps_already_exited_process(self):
        # The P1: a crashed emulator must be reaped and reported clean,
        # not refused as an identity mismatch (<defunct>).
        proc = subprocess.Popen(
            ["sleep", "0.3"], start_new_session=True)
        time.sleep(0.8)  # exited, deliberately not reaped yet
        self.harness.emulator_process = commands.ManagedProcess(
            proc=proc, argv=["/usr/bin/emulator", "-avd", "M2_Qual_Fixture"],
            start_utc="2026-08-16T12:00:00Z", new_session=True)
        self.harness._session_launched = True
        res = self.harness.release_emulator()
        self.assertEqual(res.returncode, 0)
        self.assertIn(b"already exited", res.stdout)
        self.assertIsNotNone(proc.returncode)  # reaped

    def test_release_uses_start_continuity_not_argv(self):
        # Launcher exec'd its engine: command differs from launch argv,
        # start time matches the retained observation → release proceeds.
        proc = subprocess.Popen(
            [sys.executable, "-c",
             "import os; os.execvp('sleep', ['sleep', '30'])"],
            start_new_session=True)
        time.sleep(0.5)
        try:
            self.harness._launch_identity = commands.pid_identity(proc.pid)
            self.assertNotIn(
                "/usr/bin/emulator", self.harness._launch_identity.command)
            self.harness.emulator_process = commands.ManagedProcess(
                proc=proc,
                argv=["/usr/bin/emulator", "-avd", "M2_Qual_Fixture",
                      "-port", "5554"],
                start_utc="2026-08-16T12:00:00Z", new_session=True)
            self.harness._session_launched = True
            res = self.harness.release_emulator()
            self.assertEqual(res.returncode, 0)
            self.assertIsNotNone(proc.returncode)
        finally:
            if proc.poll() is None:
                proc.kill()
            proc.wait()

    def test_dump_ledger_is_private_and_atomic(self):
        self.harness.ledger.record(
            ["adb", "-s", "emulator-5554", "shell", "getprop"],
            "2026-08-16T12:00:00Z", "2026-08-16T12:00:01Z", 0, 0, False,
            "shell")
        res = self.harness.dump_ledger()
        self.assertEqual(res.returncode, 0)
        path = os.path.join(self.run_dir, "artifacts", "command_ledger.json")
        mode = stat.S_IMODE(os.stat(path).st_mode)
        self.assertEqual(mode, 0o600)
        leftovers = [f for f in os.listdir(os.path.dirname(path))
                     if f.startswith(".command_ledger.")]
        self.assertEqual(leftovers, [])
        with open(path) as fh:
            self.assertEqual(json.load(fh)[0]["kind"], "shell")


if __name__ == "__main__":
    unittest.main()
