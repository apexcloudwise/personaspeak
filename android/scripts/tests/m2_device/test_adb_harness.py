"""Tests for the production adb_harness implementation."""

from __future__ import annotations

import os
import tempfile
import unittest
from unittest.mock import MagicMock, patch

from android.scripts.m2_device import commands
from android.scripts.m2_device.adb_harness import (
    ABI,
    API_LEVEL,
    AVD_NAME,
    CANDIDATE_REPHRASING,
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
    with open(path, 'wb') as f:
        f.write(_s.pack('>I', 8 + len(payload)) + b'ftyp' + payload)


class TestAdbHarness(unittest.TestCase):

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

    def tearDown(self):
        self.tmp_dir.cleanup()

    @patch("android.scripts.m2_device.commands.resolve_tool")
    def test_preflight_success(self, mock_resolve):
        mock_resolve.side_effect = lambda name: ToolIdentity(
            name=name, path=f"/bin/{name}", version="1.0"
        )
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
        self.mock_runner.return_value = _cr(stdout=b"abc123git\n")

        ctx = self.harness.capture_context()
        self.assertEqual(ctx.repo_head, "abc123git")
        self.assertTrue(ctx.apk_sha256)
        self.assertEqual(len(ctx.tools), 2)

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
        enabled_imes = f"{gboard}:com.android.inputmethod.latin/com.google.android.apps.inputmethod.latin.MockVoiceIME"

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

    def test_validate_fixture_success(self):
        gboard = (
            "com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME"
        )
        prior = PriorDeviceState(
            serial="emulator-5554",
            emulator_state="booted",
            fingerprint=FINGERPRINT,
            api_level=API_LEVEL,
            screen_width=SCREEN_WIDTH,
            screen_height=SCREEN_HEIGHT,
            package_present=False,
            package_hash=None,
            enabled_imes=[gboard],
            default_ime=gboard,
        )

        def side_effect(argv, **kwargs):
            cmd = " ".join(argv)
            if "getprop persist.sys.timezone" in cmd:
                return _cr(stdout=TIMEZONE.encode())
            if "getprop ro.product.locale" in cmd:
                return _cr(stdout=LOCALE.encode())
            return _cr()

        self.mock_runner.side_effect = side_effect
        res = self.harness.validate_fixture(prior)
        self.assertEqual(res.returncode, 0)

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
        self.mock_runner.return_value = _cr(rc=0)
        res = self.harness.install_apk()
        self.assertEqual(res.returncode, 0)
        self.mock_runner.assert_called_once_with(
            ["adb", "-s", "emulator-5554", "install", "-r", self.apk_path]
        )

    def test_run_journey_success(self):

        def side_effect(argv, **kwargs):
            cmd = " ".join(argv)
            if "pull" in cmd:
                dest_path = argv[-1]
                text = CANDIDATE_REPHRASING if "verify" in dest_path else ""
                with open(dest_path, "w") as f:
                    f.write(
                        f'<hierarchy rotation="0"><node index="0" text="{text}" '
                        'resource-id="com.android.settings:id/search_action_bar" '
                        'class="android.widget.EditText" bounds="[100,200][900,300]"/></hierarchy>'
                    )
                return _cr(rc=0, argv=argv)
            return _cr(rc=0, argv=argv)

        self.mock_runner.side_effect = side_effect
        steps = self.harness.run_journey()

        self.assertTrue(steps)
        operations = [s.operation for s in steps]
        self.assertIn("launch_settings", operations)
        self.assertIn("dump_hierarchy", operations)
        self.assertIn("tap_search_field", operations)
        self.assertIn("type_stale_text", operations)
        self.assertIn("type_source_text", operations)
        self.assertIn("verify_candidate_rephrasing", operations)

        tap_step = [s for s in steps if s.operation == "tap_search_field"][0]
        self.assertEqual(
            tap_step.result.argv,
            ["adb", "-s", "emulator-5554", "shell", "input", "tap", "500", "250"],
        )

        for step in steps:
            self.assertEqual(step.cause, TerminalCause.COMPLETED)

    def test_run_journey_rephrasing_mismatch(self):

        def side_effect(argv, **kwargs):
            if "pull" in argv:
                dest = argv[-1]
                text = "wrong text" if "verify" in dest else ""
                with open(dest, "w") as f:
                    f.write(
                        f'<hierarchy rotation="0"><node index="0" text="{text}" '
                        'resource-id="com.android.settings:id/search_action_bar" '
                        'class="android.widget.EditText" bounds="[100,200][900,300]"/></hierarchy>'
                    )
                return _cr(rc=0, argv=argv)
            return _cr(rc=0, argv=argv)

        self.mock_runner.side_effect = side_effect
        steps = self.harness.run_journey()
        verify = [s for s in steps if s.operation == "verify_candidate_rephrasing"]
        self.assertTrue(verify)
        self.assertEqual(verify[0].cause, TerminalCause.JOURNEY_FAILED)

    def test_run_journey_field_not_found(self):

        def side_effect(argv, **kwargs):
            if "pull" in argv:
                with open(argv[-1], "w") as f:
                    f.write('<hierarchy rotation="0"></hierarchy>')
                return _cr(rc=0, argv=argv)
            return _cr(rc=0, argv=argv)

        self.mock_runner.side_effect = side_effect
        steps = self.harness.run_journey()
        locate = [s for s in steps if s.operation == "locate_search_field"]
        self.assertTrue(locate)
        self.assertEqual(locate[0].cause, TerminalCause.JOURNEY_FAILED)

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

        def side_effect(argv, **kwargs):
            if "pull" in argv and argv[-1].endswith(".png"):
                _write_valid_png(argv[-1])
                return _cr(rc=0, argv=argv)
            if "pull" in argv and argv[-1].endswith(".mp4"):
                _write_valid_mp4(argv[-1])
                return _cr(rc=0, argv=argv)
            return _cr(rc=0, argv=argv)

        self.mock_runner.side_effect = side_effect
        self.mock_finisher.return_value = _cr(rc=0)
        res = self.harness.capture_evidence()
        self.assertEqual(res.returncode, 0)

    def test_restore(self):
        self.mock_runner.return_value = _cr(rc=0)
        res = self.harness.restore()
        self.assertEqual(res.returncode, 0)
        self.mock_runner.assert_called_once_with(
            [
                "adb",
                "-s",
                "emulator-5554",
                "emu",
                "snapshot",
                "load",
                SNAPSHOT_NAME,
            ]
        )

    def test_release_emulator_process(self):
        mock_process = MagicMock()
        self.harness.emulator_process = mock_process
        self.mock_finisher.return_value = _cr(rc=0)

        res = self.harness.release_emulator()
        self.assertEqual(res.returncode, 0)
        self.mock_finisher.assert_called_once_with(
            mock_process, terminate=True
        )
        self.assertIsNone(self.harness.emulator_process)

    def test_release_emulator_fallback(self):
        self.harness.emulator_process = None
        self.mock_runner.return_value = _cr(rc=0)

        res = self.harness.release_emulator()
        self.assertEqual(res.returncode, 0)
        self.mock_runner.assert_called_once_with(
            ["adb", "-s", "emulator-5554", "emu", "kill"]
        )

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


if __name__ == "__main__":
    unittest.main()
