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
    ASK_KEY_COORDS,
    CANDIDATE_REPHRASING,
    EXPECTED_SIGNER,
    EXPECTED_SIGNER_CERT_SHA256,
    EXPECTED_VERSION_CODE,
    EXPECTED_VERSION_NAME,
    FINGERPRINT,
    LOCALE,
    SCREEN_HEIGHT,
    SCREEN_WIDTH,
    SETTINGS_ACTION,
    SHIFT_TAP,
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


EDITOR_RES_ID = "com.google.android.settings.intelligence:id/open_search_view_edit_text"
SEARCH_BAR_RES_ID = "com.android.settings:id/search_action_bar"
EDITOR_HINT = "Search settings"
IME_COMPONENT = (
    "biz.pixelperfectstudios.personaspeak/com.menny.android.anysoftkeyboard.SoftKeyboard")


class _DeviceModel:
    """Unit-level emulation of what the real device offers the journey:
    host-app hierarchies (editor text included), ime enable/set, the
    dumpsys channels, and taps that only land on real geometry. No
    keyboard nodes are ever rendered — the IME is dump-invisible."""

    def __init__(self, key_shift=0, ime_unbound=False, window_missing=False,
                 never_expands=False, apply_mutates_stale=False):
        self.screen = "home"
        self.editor = ""
        self.focused = False
        self.panel = ""
        self.ime = ""
        self.candidate_source = ""
        self.key_shift = key_shift
        self.ime_unbound = ime_unbound
        self.window_missing = window_missing
        self.never_expands = never_expands
        self.apply_mutates_stale = apply_mutates_stale
        # Mirrors the harness pins (vendored-XML derivation, #82).
        self.key_coords = {
            "t": (486, 1794), "e": (270, 1794), "a": (108, 1932),
            "s": (216, 1932), "i": (810, 1794), "x": (324, 2072),
            "v": (540, 2072), "n": (756, 2072),
            " ": (567, 2210), ".": (756, 2210),
        }
        self.shift_coords = (81, 2072)
        self.shift_latched = False
        self.back_count = 0

    # -- hierarchy rendering -------------------------------------------

    def hierarchy(self):
        if self.screen == "search":
            shown = self.editor if self.editor else EDITOR_HINT
            editor = (
                f'<node index="0" text="{shown}" '
                f'focused="{"true" if self.focused else "false"}" '
                f'resource-id="{EDITOR_RES_ID}" class="android.widget.EditText" '
                'package="com.google.android.settings.intelligence" '
                'bounds="[126,149][1080,275]"/>'
            )
            return (
                '<hierarchy rotation="0">'
                '<node index="0" text="" resource-id="com.google.android.settings.intelligence:id/open_search_view_status_bar_spacer" '
                'class="android.view.View" package="com.google.android.settings.intelligence" bounds="[0,0][1080,128]"/>'
                f'{editor}</hierarchy>'
            )
        return (
            '<hierarchy rotation="0">'
            f'<node index="0" text="" resource-id="{SEARCH_BAR_RES_ID}" '
            'class="android.view.ViewGroup" package="com.android.settings" '
            'clickable="true" focusable="true" focused="false" '
            'bounds="[42,591][1038,728]"/></hierarchy>'
        )

    # -- dumpsys channels ----------------------------------------------

    def bound(self):
        return (self.ime == "set" and self.screen == "search"
                and self.focused)

    def dumpsys_input_method(self):
        if self.ime_unbound or not self.bound():
            return ("  mCurMethodId=com.google.android.inputmethod.latin/"
                    "com.android.inputmethod.latin.LatinIME\n"
                    "  mHaveConnection=false\n  mBoundToMethod=false\n"
                    "  mVisibleBound=false\n")
        if self.panel == "LOADING":
            self.panel = "REVIEW"
        return (f"  mCurMethodId={IME_COMPONENT}\n"
                "  mHaveConnection=true\n  mBoundToMethod=true\n"
                "  mVisibleBound=true\n")

    def dumpsys_window(self):
        # Real API-34 shape (probe 2026-08-20): no mFrame lines; the
        # InputMethod block's touchable region carries the panel state.
        if self.window_missing or not self.bound():
            return ("  Window #1 Window{abc u0 Notification Shade}:\n"
                    "    package=com.android.systemui\n    HAS_DRAWN\n")
        if self.panel == "LOADING":
            self.panel = "REVIEW"
        expanded = self.panel in ("REVIEW", "APPLIED", "STALE")
        top = 1283 if (expanded and not self.never_expands) else 1378
        return (
            "  Window #7 Window{213d245 u0 com.android.settings/com.android.settings.Settings}:\n"
            "    mOwnerUid=1000 showForAllUsers=false package=com.android.settings appop=NONE\n"
            "    mViewVisibility=0x0 mHaveFrame=true mObscured=false\n"
            "    mHasSurface=true isReadyForDisplay()=true mWindowRemovalAllowed=false\n"
            "    Frames: parent=[0,128][1080,2400] display=[0,128][1080,2400] frame=[0,128][1080,2400] last=[0,128][1080,2400] insetsChanged=false\n"
            "    touchable region=SkRegion((0,0,1080,2400))\n"
            "    WindowStateAnimator{b1c2d3 Settings}:\n"
            "      Surface: shown=true layer=0 alpha=1.0 rect=(0.0,0.0)  transform=(1.0, 0.0, 0.0, 0.0)\n"
            "      mDrawState=HAS_DRAWN       mLastHidden=false\n"
            "  Window #8 Window{d035f22 u0 InputMethod}:\n"
            "    mDisplayId=0 rootTaskId=1 mSession=Session{38d43d5 4903:u0a10192} mClient=android.os.BinderProxy@2b264ed\n"
            "    mOwnerUid=10192 showForAllUsers=false package=biz.pixelperfectstudios.personaspeak appop=NONE\n"
            "    mAttrs={(0,0)(fillxfill) gr=BOTTOM CENTER_VERTICAL sim={adjust=pan} ty=INPUT_METHOD fmt=TRANSPARENT wanim=0x1030056 receive insets ignoring z-order\n"
            "    Requested w=1080 h=2272 mLayoutSeq=176\n"
            "    mIsImWindow=true mIsWallpaper=false mIsFloatingLayer=true\n"
            "    mViewVisibility=0x0 mHaveFrame=true mObscured=false\n"
            "    mGivenContentInsets=[0,1250][0,0] mGivenVisibleInsets=[0,1250][0,0]\n"
            f"    touchable region=SkRegion((0,{top},1080,2400))\n"
            "    mHasSurface=true isReadyForDisplay()=true mWindowRemovalAllowed=false\n"
            "    Frames: parent=[0,128][1080,2400] display=[0,128][1080,2400] frame=[0,128][1080,2400] last=[0,128][1080,2400] insetsChanged=false\n"
            "    WindowStateAnimator{a8b0090 InputMethod}:\n"
            "      mSurface=Surface(name=InputMethod)/@0x6bb4d89\n"
            "      Surface: shown=true layer=0 alpha=1.0 rect=(0.0,0.0)  transform=(1.0, 0.0, 0.0, 0.0)\n"
            "      mDrawState=HAS_DRAWN       mLastHidden=false\n"
        )

    # -- interactions ---------------------------------------------------

    def _tap(self, x, y):
        if self.screen == "home":
            if 42 <= x <= 1038 and 591 <= y <= 728:
                self.screen = "search"
                self.editor = ""
                self.panel = ""
                self.focused = True
                self.shift_latched = False
                self.back_count = 0
            return
        if 126 <= x <= 1080 and 149 <= y <= 275:
            self.focused = True
            return

        def near(cx, cy):
            return abs(x - cx) <= 54 and abs(y - cy) <= 54

        if near(116, 1452) or near(105, 1452):
            if self.panel == "":
                if self.editor:
                    self.panel = "LOADING"
                    self.candidate_source = self.editor
            elif self.panel == "REVIEW":
                if (self.editor == self.candidate_source
                        or self.apply_mutates_stale):
                    self.editor = CANDIDATE_REPHRASING
                    self.panel = "APPLIED"
                else:
                    self.panel = "STALE"
            return
        if near(180, 1452):
            if self.panel == "LOADING":
                self.panel = ""
            return
        if near(328, 1452):
            if self.panel == "REVIEW":
                self.panel = ""
            return
        # Sticky shift: one shot, releases on the next letter. No
        # auto-capitalization exists in this editor (attempt 2, #82).
        if near(*self.shift_coords):
            self.shift_latched = True
            return
        for ch, (kx, ky) in self.key_coords.items():
            kx, ky = kx + self.key_shift, ky + self.key_shift
            if abs(x - kx) <= 54 and abs(y - ky) <= 54 and self.focused:
                if self.shift_latched and ch.isalpha():
                    self.editor += ch.upper()
                    self.shift_latched = False
                else:
                    self.editor += ch
                return

    def keyevent(self, code):
        if code == "4" and self.screen == "search":
            # First BACK dismisses the IME only; the second closes the
            # search screen (iteration-3 record, #82).
            self.back_count += 1
            if self.back_count == 1:
                self.panel = ""
                self.shift_latched = False
            else:
                self.screen = "home"
                self.editor = ""
                self.panel = ""
                self.focused = False
                self.back_count = 0
        elif code == "67" and self.screen == "search" and self.focused:
            self.editor = self.editor[:-1]

    def shell(self, cmd):
        if "getprop sys.boot_completed" in cmd:
            return "1"
        if "ime enable" in cmd:
            self.ime = "enabled"
        elif "ime set" in cmd:
            self.ime = "set"
        elif "dumpsys input_method" in cmd:
            return self.dumpsys_input_method()
        elif "dumpsys window" in cmd:
            return self.dumpsys_window()
        elif "am start" in cmd:
            self.screen = "home"
        else:
            for segment in cmd.replace("&&", ";").split(";"):
                seg = segment.split()
                if len(seg) >= 4 and seg[:2] == ["input", "tap"]:
                    self._tap(int(seg[2]), int(seg[3]))
                elif len(seg) >= 3 and seg[:2] == ["input", "keyevent"]:
                    self.keyevent(seg[2])
        return ""

    # -- runner side_effect ---------------------------------------------

    def side_effect(self, argv, **kwargs):
        if argv[0:1] == ["adb"]:
            rest = argv[3:]
        else:
            rest = argv[1:]
        if rest[:1] == ["shell"]:
            payload = " ".join(rest[1:])
            return _cr(stdout=self.shell(payload).encode(), argv=argv)
        return _cr(rc=0, argv=argv)


def _journey_runner(model=None, **model_kwargs):
    """Pull-writing side_effect backed by the device model: every dump
    renders the model's current host-app hierarchy."""
    if model is None:
        model = _DeviceModel(**model_kwargs)

    def side_effect(argv, **kwargs):
        if "pull" in argv:
            dest = argv[-1]
            if dest.endswith(".png"):
                _write_valid_png(dest)
            elif dest.endswith(".mp4"):
                _write_valid_mp4(dest)
            else:
                with open(dest, "w") as f:
                    f.write(model.hierarchy())
            return _cr(rc=0, argv=argv)
        return model.side_effect(argv, **kwargs)

    side_effect.model = model
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
        self.harness.apksigner_tool = ToolIdentity(name="apksigner", path="apksigner", version="0.9")

    def tearDown(self):
        self.tmp_dir.cleanup()

    @patch.dict(os.environ, {"ANDROID_HOME": "", "ANDROID_SDK_ROOT": ""})
    @patch("socket.socket")
    @patch("android.scripts.m2_device.commands.resolve_tool")
    def test_preflight_success(self, mock_resolve, mock_socket_cls):
        def fake_resolve(name, **kwargs):
            if name == "apksigner":
                version = "0.9"
            else:
                version = (f"Android {'Debug Bridge' if name == 'adb' else 'emulator'} "
                           f"version {'1.0.41' if name == 'adb' else '36.6.11.0'}")
            return ToolIdentity(
                name=name, path=f"/bin/{name}", version=version,
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
        self.assertEqual(len(ctx.tools), 3)
        self.assertEqual(
            [t.name for t in ctx.tools], ["adb", "emulator", "apksigner"])
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

    def test_run_journey_fails_closed_on_unparsable_focus_hierarchy(self):
        # Malformed focus_1 XML must stop the journey before any key
        # tap — absent facts never authorize one.
        writer = _journey_runner()

        def side_effect(argv, **kwargs):
            if "pull" in argv and "focus_1" in argv[-1]:
                with open(argv[-1], "w") as f:
                    f.write("<not-xml")
                return _cr(rc=0, argv=argv)
            return writer(argv, **kwargs)

        self.mock_runner.side_effect = side_effect
        self.mock_starter.return_value = MagicMock()
        steps = self.harness.run_journey()
        failed = [s for s in steps if s.operation == "verify_editor_pristine_1"]
        self.assertTrue(failed)
        self.assertNotEqual(failed[0].cause, TerminalCause.COMPLETED)
        self.assertIn(b"unparsable", failed[0].result.stderr)
        self.assertFalse(
            [s for s in steps if s.operation.startswith("tap_key")],
            "taps must not run on the strength of absent hierarchy facts")

    def test_run_journey_fails_closed_on_failed_home_dump(self):
        writer = _journey_runner()
        dump_calls = {"n": 0}

        def side_effect(argv, **kwargs):
            cmd = " ".join(argv)
            if "uiautomator" in cmd:
                dump_calls["n"] += 1
                if dump_calls["n"] == 1:  # home_1 is the first dump
                    return _cr(rc=1, stderr=b"dump failed")
            return writer(argv, **kwargs)

        self.mock_runner.side_effect = side_effect
        self.mock_starter.return_value = MagicMock()
        steps = self.harness.run_journey()
        failed = [s for s in steps if s.operation == "dump_home_1"]
        self.assertTrue(failed)
        self.assertNotEqual(failed[0].cause, TerminalCause.COMPLETED)
        self.assertFalse(
            [s for s in steps if s.operation.startswith("tap_key")])

    def test_run_journey_fails_closed_on_unparsable_home_hierarchy(self):
        writer = _journey_runner()

        def side_effect(argv, **kwargs):
            if "pull" in argv and "home_1.xml" in argv[-1]:
                with open(argv[-1], "w") as f:
                    f.write("<not-xml")
                return _cr(rc=0, argv=argv)
            return writer(argv, **kwargs)

        self.mock_runner.side_effect = side_effect
        self.mock_starter.return_value = MagicMock()
        steps = self.harness.run_journey()
        failed = [s for s in steps if s.operation == "dump_home_1"]
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
        writer = _journey_runner()

        def side_effect(argv, **kwargs):
            if "pull" in argv and "window_dump.xml" in argv[-2]:
                return _cr(rc=0, argv=argv)
            return writer(argv, **kwargs)

        self.mock_runner.side_effect = side_effect
        self.mock_starter.return_value = MagicMock()
        steps = self.harness.run_journey()
        failed = [s for s in steps if s.operation == "dump_home_1"]
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
        # Host-tolerance bound, not a capture condition (#82): healthy
        # runs attach in seconds; 360s only absorbs host load.
        self.mock_runner.assert_called_once_with(
            ["adb", "-s", "emulator-5554", "wait-for-device"],
            timeout=AdbHarness.ATTACH_TIMEOUT_SECONDS,
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
                return _cr(rc=1)  # absent on a real device: exit 1, empty output
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

    def test_capture_prior_state_absent_package_rc1(self):
        # Regression (first counted failure, run 20260819T005731Z): the
        # pristine fixture's REQUIRED state is package-absent, and real
        # pm path exits 1 for it. That must yield prior state with
        # package_present False, not prior_state_unavailable.
        gboard = (
            "com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME"
        )

        def side_effect(argv, **kwargs):
            cmd = " ".join(argv)
            if "getprop sys.boot_completed" in cmd:
                return _cr(stdout=b"1\n")
            if "getprop ro.build.fingerprint" in cmd:
                return _cr(stdout=FINGERPRINT.encode())
            if "getprop ro.build.version.sdk" in cmd:
                return _cr(stdout=f"{API_LEVEL}\n".encode())
            if "wm size" in cmd:
                return _cr(stdout=f"Physical size: {SCREEN_WIDTH}x{SCREEN_HEIGHT}\n".encode())
            if "pm path" in cmd:
                return _cr(rc=1)  # absent: the real device's answer
            if "settings get secure enabled_input_methods" in cmd:
                return _cr(stdout=gboard.encode())
            if "settings get secure default_input_method" in cmd:
                return _cr(stdout=gboard.encode())
            return _cr()

        self.mock_runner.side_effect = side_effect
        state = self.harness.capture_prior_state()
        self.assertIsNotNone(state)
        self.assertFalse(state.package_present)

    def test_capture_prior_state_present_package_hashes(self):
        # Review round 2: the present-package path must still parse the
        # apk path and hash the on-device APK (rc=0, package: output).
        gboard = (
            "com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME"
        )
        dev_apk = ("/data/app/~~abc/biz.pixelperfectstudios.personaspeak-xyz/base.apk")
        sha = "ab" * 32

        def side_effect(argv, **kwargs):
            cmd = " ".join(argv)
            if "getprop sys.boot_completed" in cmd:
                return _cr(stdout=b"1\n")
            if "getprop ro.build.fingerprint" in cmd:
                return _cr(stdout=FINGERPRINT.encode())
            if "getprop ro.build.version.sdk" in cmd:
                return _cr(stdout=f"{API_LEVEL}\n".encode())
            if "wm size" in cmd:
                return _cr(stdout=f"Physical size: {SCREEN_WIDTH}x{SCREEN_HEIGHT}\n".encode())
            if "pm path" in cmd:
                return _cr(rc=0, stdout=f"package:{dev_apk}\n".encode())
            if "sha256sum" in cmd:
                self.assertIn(dev_apk, cmd)
                return _cr(rc=0, stdout=f"{sha}  {dev_apk}\n".encode())
            if "settings get secure enabled_input_methods" in cmd:
                return _cr(stdout=gboard.encode())
            if "settings get secure default_input_method" in cmd:
                return _cr(stdout=gboard.encode())
            return _cr()

        self.mock_runner.side_effect = side_effect
        state = self.harness.capture_prior_state()
        self.assertIsNotNone(state)
        self.assertTrue(state.package_present)
        self.assertEqual(state.package_hash, sha)

    def test_capture_prior_state_pm_path_unknown_stops(self):
        # rc=1 WITH output is not absence — unknown state stops the run.
        def side_effect(argv, **kwargs):
            cmd = " ".join(argv)
            if "getprop sys.boot_completed" in cmd:
                return _cr(stdout=b"1\n")
            if "getprop ro.build.fingerprint" in cmd:
                return _cr(stdout=FINGERPRINT.encode())
            if "getprop ro.build.version.sdk" in cmd:
                return _cr(stdout=f"{API_LEVEL}\n".encode())
            if "wm size" in cmd:
                return _cr(stdout=f"Physical size: {SCREEN_WIDTH}x{SCREEN_HEIGHT}\n".encode())
            if "pm path" in cmd:
                return _cr(rc=1, stdout=b"error: device still settling\n")
            return _cr()

        self.mock_runner.side_effect = side_effect
        state = self.harness.capture_prior_state()
        self.assertIsNone(state)

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
            if "apksigner" in cmd:
                return _cr(rc=0, stdout=(
                    f"Signer #1 certificate SHA-256 digest: {EXPECTED_SIGNER_CERT_SHA256}\n"
                ).encode())
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
            if "apksigner" in cmd:
                return _cr(rc=0, stdout=(
                    f"Signer #1 certificate SHA-256 digest: {EXPECTED_SIGNER_CERT_SHA256}\n"
                ).encode())
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
            if "apksigner" in cmd:
                return _cr(rc=0, stdout=(
                    f"Signer #1 certificate SHA-256 digest: {EXPECTED_SIGNER_CERT_SHA256}\n"
                ).encode())
            if "install" in cmd and "pull" not in cmd:
                return _cr(rc=1, stderr=b"device not found")
            return _cr(rc=0)

        self.mock_runner.side_effect = side_effect
        res = self.harness.install_apk()
        self.assertEqual(res.returncode, 1)

    def test_install_apk_rejects_wrong_signer_certificate(self):
        # A different certificate digest must fail closed before the
        # device is ever asked to install (review finding, PR 76).
        def side_effect(argv, **kwargs):
            cmd = " ".join(argv)
            if "apksigner" in cmd:
                return _cr(rc=0, stdout=(
                    "Signer #1 certificate SHA-256 digest: "
                    + "deadbeef" * 8 + "\n").encode())
            return _cr(rc=0)

        self.mock_runner.side_effect = side_effect
        res = self.harness.install_apk()
        self.assertEqual(res.returncode, 1)
        self.assertIn(b"signer certificate mismatch", res.stderr)
        for call in self.mock_runner.call_args_list:
            self.assertNotIn(
                "install", " ".join(call.args[0]),
                "device install ran despite signer mismatch")

    def test_install_apk_rejects_missing_digest_line(self):
        # rc=0 from the tool but no digest line at all is not a pass.
        def side_effect(argv, **kwargs):
            cmd = " ".join(argv)
            if "apksigner" in cmd:
                return _cr(rc=0, stdout=b"Signer #1 certificate DN: C=US\n")
            return _cr(rc=0)

        self.mock_runner.side_effect = side_effect
        res = self.harness.install_apk()
        self.assertEqual(res.returncode, 1)
        self.assertIn(b"signer certificate mismatch", res.stderr)

    def test_run_journey_success(self):
        self.mock_runner.side_effect = _journey_runner()
        self.mock_starter.return_value = MagicMock()
        steps = self.harness.run_journey()
        operations = [s.operation for s in steps]
        for expected in ("enable_ime", "set_ime",
                         "verify_editor_pristine_1", "rewrite_and_cancel",
                         "verify_after_cancel", "request_rewrite_2",
                         "apply_rephrasing", "verify_after_apply",
                         "dismiss_rephrasing", "verify_after_dismiss",
                         "verify_typed_stale", "apply_stale",
                         "verify_after_stale",
                         "exit_session_4", "relaunch_settings"):
            self.assertIn(expected, operations)
        for step in steps:
            self.assertEqual(step.cause, TerminalCause.COMPLETED, f"{step.operation} failed")

    def test_run_journey_rephrasing_mismatch(self):
        model = _DeviceModel()
        original_tap = model._tap

        def tap(x, y):
            original_tap(x, y)
            if model.panel == "APPLIED":
                # Apply lands but the editor never changes: the bridge
                # must fail the journey on the mismatch.
                model.editor = "wrong text"

        model._tap = tap
        self.mock_runner.side_effect = _journey_runner(model)
        self.mock_starter.return_value = MagicMock()
        steps = self.harness.run_journey()
        apply_verify = [s for s in steps if s.operation == "verify_after_apply"]
        self.assertTrue(apply_verify)
        self.assertEqual(apply_verify[0].cause, TerminalCause.JOURNEY_FAILED)

    def test_run_journey_field_not_found(self):
        writer = _journey_runner()

        def side_effect(argv, **kwargs):
            if "pull" in argv:
                with open(argv[-1], "w") as f:
                    f.write('<hierarchy rotation="0"></hierarchy>')
                return _cr(rc=0, argv=argv)
            return writer(argv, **kwargs)

        self.mock_runner.side_effect = side_effect
        self.mock_starter.return_value = MagicMock()
        steps = self.harness.run_journey()
        locate = [s for s in steps if s.operation == "focus_editor_1"]
        self.assertTrue(locate)
        self.assertEqual(locate[0].cause, TerminalCause.JOURNEY_FAILED)
        self.assertIn(b"search bar not found", locate[0].result.stderr)

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

    def test_run_journey_rejects_dirty_editor_at_pristine_pin(self):
        writer = _journey_runner()
        model = writer.model

        original_tap = model._tap

        def tap(x, y):
            original_tap(x, y)
            if model.screen == "search":
                # The fixture should guarantee a fresh editor; a dirty
                # one must fail the pristine pin before any typing.
                model.editor = "leftover text"

        model._tap = tap
        self.mock_runner.side_effect = writer
        self.mock_starter.return_value = MagicMock()
        steps = self.harness.run_journey()
        pin = [s for s in steps if s.operation == "verify_editor_pristine_1"]
        self.assertTrue(pin)
        self.assertEqual(pin[0].cause, TerminalCause.JOURNEY_FAILED)
        self.assertIn(b"not empty", pin[0].result.stderr)

    def test_run_journey_rejects_unfocused_editor(self):
        writer = _journey_runner()
        model = writer.model

        original_tap = model._tap

        def tap(x, y):
            original_tap(x, y)
            model.focused = False

        model._tap = tap
        self.mock_runner.side_effect = writer
        self.mock_starter.return_value = MagicMock()
        steps = self.harness.run_journey()
        pin = [s for s in steps if s.operation == "verify_editor_pristine_1"]
        self.assertTrue(pin)
        self.assertEqual(pin[0].cause, TerminalCause.JOURNEY_FAILED)
        self.assertIn(b"not focused", pin[0].result.stderr)

    def test_run_journey_stale_mutation_rejected(self):
        # The stale contract: applying over a changed source must make
        # ZERO mutations. If the product mutates anyway, the bridge must
        # fail the journey instead of certifying the rewrite.
        self.mock_runner.side_effect = _journey_runner(apply_mutates_stale=True)
        self.mock_starter.return_value = MagicMock()
        steps = self.harness.run_journey()
        stale_verify = [s for s in steps if s.operation == "verify_after_stale"]
        self.assertTrue(stale_verify)
        self.assertEqual(stale_verify[0].cause, TerminalCause.JOURNEY_FAILED)

    def test_run_journey_ime_enable_fails_closed(self):
        writer = _journey_runner()

        def side_effect(argv, **kwargs):
            if "ime" in argv and "enable" in argv:
                return _cr(rc=1, stderr=b"ime enable refused")
            return writer(argv, **kwargs)

        self.mock_runner.side_effect = side_effect
        self.mock_starter.return_value = MagicMock()
        steps = self.harness.run_journey()
        enable = [s for s in steps if s.operation == "enable_ime"]
        self.assertTrue(enable)
        self.assertNotEqual(enable[0].cause, TerminalCause.COMPLETED)
        self.assertFalse([s for s in steps if s.operation == "set_ime"],
                         "a failed enablement must stop before selection")

    def test_run_journey_ime_set_fails_closed(self):
        writer = _journey_runner()

        def side_effect(argv, **kwargs):
            if "ime" in argv and "set" in argv:
                return _cr(rc=1, stderr=b"ime set refused")
            return writer(argv, **kwargs)

        self.mock_runner.side_effect = side_effect
        self.mock_starter.return_value = MagicMock()
        steps = self.harness.run_journey()
        set_step = [s for s in steps if s.operation == "set_ime"]
        self.assertTrue(set_step)
        self.assertNotEqual(set_step[0].cause, TerminalCause.COMPLETED)
        self.assertFalse([s for s in steps if s.operation == "open_settings_1"],
                         "a failed selection must stop before any session")

    def test_run_journey_detects_unbound_ime(self):
        # The keyboard never binds (wrong mCurMethodId, flags false): the
        # binding check must fail closed before any key tap.
        self.mock_runner.side_effect = _journey_runner(ime_unbound=True)
        self.mock_starter.return_value = MagicMock()
        steps = self.harness.run_journey()
        binding = [s for s in steps if s.operation == "verify_ime_binding_s1"]
        self.assertTrue(binding)
        self.assertEqual(binding[0].cause, TerminalCause.JOURNEY_FAILED)
        self.assertIn(b"mCurMethodId", binding[0].result.stderr)
        self.assertFalse([s for s in steps if s.operation.startswith("tap_key")])

    def test_run_journey_detects_missing_ime_window(self):
        # dumpsys window shows no InputMethod window at all: the window
        # check must fail closed before any key tap.
        self.mock_runner.side_effect = _journey_runner(window_missing=True)
        self.mock_starter.return_value = MagicMock()
        steps = self.harness.run_journey()
        window = [s for s in steps if s.operation == "verify_ime_window_s1"]
        self.assertTrue(window)
        self.assertEqual(window[0].cause, TerminalCause.JOURNEY_FAILED)
        self.assertFalse([s for s in steps if s.operation.startswith("tap_key")])

    # Real InputMethod window blocks as the pinned fixture prints them
    # (API 34, emulator 36.6.11 — capability probe 2026-08-20; trimmed
    # of animation noise, every surviving line verbatim). The real dump
    # carries no mFrame line: the window frame is fill-parent in both
    # panel states and the touchable region carries the panel geometry.
    _PROBE_IM_BLOCK_COMPACT = (
        "  Window #8 Window{d035f22 u0 InputMethod}:\n"
        "    mDisplayId=0 rootTaskId=1 mSession=Session{38d43d5 4903:u0a10192} mClient=android.os.BinderProxy@2b264ed\n"
        "    mOwnerUid=10192 showForAllUsers=false package=biz.pixelperfectstudios.personaspeak appop=NONE\n"
        "    mViewVisibility=0x0 mHaveFrame=true mObscured=false\n"
        "    mGivenContentInsets=[0,1250][0,0] mGivenVisibleInsets=[0,1250][0,0]\n"
        "    touchable region=SkRegion((0,1378,1080,2400))\n"
        "    mHasSurface=true isReadyForDisplay()=true mWindowRemovalAllowed=false\n"
        "    Frames: parent=[0,128][1080,2400] display=[0,128][1080,2400] frame=[0,128][1080,2400] last=[0,128][1080,2400] insetsChanged=false\n"
        "    WindowStateAnimator{a8b0090 InputMethod}:\n"
        "      Surface: shown=true layer=0 alpha=1.0 rect=(0.0,0.0)  transform=(1.0, 0.0, 0.0, 0.0)\n"
        "      mDrawState=HAS_DRAWN       mLastHidden=false\n"
    )
    _PROBE_IM_BLOCK_EXPANDED = (
        "  Window #8 Window{d035f22 u0 InputMethod}:\n"
        "    mDisplayId=0 rootTaskId=1 mSession=Session{38d43d5 4903:u0a10192} mClient=android.os.BinderProxy@2b264ed\n"
        "    mOwnerUid=10192 showForAllUsers=false package=biz.pixelperfectstudios.personaspeak appop=NONE\n"
        "    mViewVisibility=0x0 mHaveFrame=true mObscured=false\n"
        "    mGivenContentInsets=[0,1155][0,0] mGivenVisibleInsets=[0,1155][0,0]\n"
        "    touchable region=SkRegion((0,1283,1080,2400))\n"
        "    mHasSurface=true isReadyForDisplay()=true mWindowRemovalAllowed=false\n"
        "    Frames: parent=[0,128][1080,2400] display=[0,128][1080,2400] frame=[0,128][1080,2400] last=[0,128][1080,2400] insetsChanged=false\n"
        "    WindowStateAnimator{a8b0090 InputMethod}:\n"
        "      Surface: shown=true layer=0 alpha=1.0 rect=(0.0,0.0)  transform=(1.0, 0.0, 0.0, 0.0)\n"
        "      mDrawState=HAS_DRAWN       mLastHidden=false\n"
    )

    def _window_res(self, block):
        return RemoteResult(
            remote_rc=0,
            transport=CommandResult(
                argv=["dumpsys", "window", "windows"],
                start_utc="", end_utc="", returncode=0,
                stdout=block.encode(), stderr=b"",
            ),
        )

    def test_window_frame_reads_real_probe_bytes(self):
        # The parser is pinned to the device's own bytes, not to the
        # fake's rendering of them: compact 1378, expanded 1283 — the
        # 95px upward growth that is the review signal.
        for block, want in ((self._PROBE_IM_BLOCK_COMPACT, (1378, 2400)),
                            (self._PROBE_IM_BLOCK_EXPANDED, (1283, 2400))):
            with patch.object(self.harness, "_shell",
                              return_value=self._window_res(block)):
                self.assertEqual(
                    self.harness._ime_window_frame([], "probe"), want)

    def test_window_failure_preserves_raw_block(self):
        # Attempt 1 (run 20260819T203941Z) failed with "frame absent"
        # and discarded the bytes, leaving the real format unpinnable
        # from the record. A failed check now carries the raw block.
        block = self._PROBE_IM_BLOCK_COMPACT.replace(
            "touchable region=SkRegion((0,1378,1080,2400))\n", "")
        steps = []
        with patch.object(self.harness, "_shell",
                          return_value=self._window_res(block)):
            self.assertIsNone(self.harness._ime_window_frame(steps, "s1"))
        stderr = steps[0].result.stderr.decode("utf-8", "replace")
        self.assertIn("touchable region absent", stderr)
        self.assertIn("WindowStateAnimator", stderr)

    def test_run_journey_detects_review_that_never_expands(self):
        # A rewrite that stays compact is a review that never happened;
        # the window-geometry check must fail closed before any apply.
        self.mock_runner.side_effect = _journey_runner(never_expands=True)
        self.mock_starter.return_value = MagicMock()
        steps = self.harness.run_journey()
        review = [s for s in steps if s.operation == "verify_ime_window_review_2"]
        self.assertTrue(review)
        self.assertEqual(review[0].cause, TerminalCause.JOURNEY_FAILED)
        self.assertIn(b"never rose", review[0].result.stderr)
        self.assertFalse([s for s in steps if s.operation == "apply_rephrasing"])

    def test_run_journey_detects_wrong_key_geometry(self):
        # Taps that do not land are text that does not change: the
        # editor bridge is the wrong-geometry detector. The layout here
        # is shifted off the pins, exactly like the 2026-08-19 drift.
        self.mock_runner.side_effect = _journey_runner(key_shift=200)
        self.mock_starter.return_value = MagicMock()
        steps = self.harness.run_journey()
        typed = [s for s in steps if s.operation == "verify_typed_1"]
        self.assertTrue(typed)
        self.assertEqual(typed[0].cause, TerminalCause.JOURNEY_FAILED)

    def test_verify_restore_editor_still_present_raises(self):
        writer = _journey_runner()
        model = writer.model
        # Restore failed to close the search screen: the journey-time
        # editor is still present in the post-restore dump.
        model.screen = "search"
        model.focused = True

        self.mock_runner.side_effect = writer
        with patch.object(self.harness, "capture_prior_state",
                          return_value=object()):
            with self.assertRaises(RuntimeError) as cm:
                self.harness.verify_restore()
        self.assertIn("search editor still present", str(cm.exception))

    def test_verify_restore_pristine_state_accepted(self):
        writer = _journey_runner()

        self.mock_runner.side_effect = writer
        with patch.object(self.harness, "capture_prior_state",
                          return_value=object()) as prior:
            state = self.harness.verify_restore()
        self.assertIs(state, prior.return_value)
        # Pristine means the home screen: no journey-time editor node.
        with open(os.path.join(self.run_dir, "artifacts",
                               "verify_restore.xml")) as f:
            self.assertNotIn(EDITOR_RES_ID, f.read())

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
            ["adb", "-s", "emulator-5554", "emu", "avd", "snapshot",
             "load", SNAPSHOT_NAME],
            timeout=30.0,
        )

    def test_restore_detects_console_ko_despite_rc_zero(self):
        # The real 36.x console answers a rejected snapshot load with a
        # KO line on stdout AND returncode 0 (attempt 1, run
        # 20260819T203941Z). rc alone would record a restore that never
        # happened; stdout is the verdict.
        self.mock_runner.return_value = CommandResult(
            argv=["adb", "-s", "emulator-5554", "emu", "avd", "snapshot",
                  "load", SNAPSHOT_NAME],
            start_utc="", end_utc="", returncode=0,
            stdout=b"KO: unknown command, try 'help'\r\n", stderr=b"",
        )
        res = self.harness.restore()
        self.assertEqual(res.returncode, 1)
        self.assertIn(b"console rejected restore", res.stderr)
        self.assertIn(b"KO: unknown command", res.stderr)

    def test_restore_ok_line_passes_through(self):
        self.mock_runner.return_value = CommandResult(
            argv=[], start_utc="", end_utc="", returncode=0,
            stdout=b"OK", stderr=b"",
        )
        res = self.harness.restore()
        self.assertEqual(res.returncode, 0)

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


    # ---------- #82 corrections: shift protocol, settle, geometry ------

    def test_type_text_capitals_ride_shift_taps(self):
        # The editor does not auto-capitalize (defect B, #82): every
        # capital in the source text must be preceded by a real sticky
        # shift tap, or the bridge readback fails.
        argvs = []
        writer = _journey_runner()

        def side_effect(argv, **kwargs):
            argvs.append(argv)
            return writer(argv, **kwargs)

        self.mock_runner.side_effect = side_effect
        self.assertTrue(self.harness._type_text([], "Tea"))
        taps = [a for a in argvs if "tap" in a]
        self.assertEqual(
            [t[-2:] for t in taps],
            [[str(SHIFT_TAP[0]), str(SHIFT_TAP[1])], ["486", "1794"],
             ["270", "1794"], ["108", "1932"]])

    def test_verify_restore_settles_after_transient_polls(self):
        # Post-snapshot-load adbd restarts make early boot_completed
        # queries fail (defect H, #82); they must be retried in-bound.
        calls = {"n": 0}
        writer = _journey_runner()

        def side_effect(argv, **kwargs):
            if "boot_completed" in " ".join(argv):
                calls["n"] += 1
                if calls["n"] <= 2:
                    return _cr(rc=1, stderr=b"error: device not responding")
                return _cr(stdout=b"1")
            return writer(argv, **kwargs)

        self.mock_runner.side_effect = side_effect
        with patch.object(self.harness, "capture_prior_state",
                          return_value=object()), \
             patch.object(AdbHarness, "RESTORE_SETTLE_POLL_SECONDS", 0):
            self.harness.verify_restore()
        self.assertEqual(calls["n"], 3)

    def test_verify_restore_never_settling_raises_with_bytes(self):
        # A device that never settles fails closed at the deadline AND
        # the error carries the last failed query's bytes — a hard tool
        # failure surfaces as itself, not as an opaque slow resume.
        def side_effect(argv, **kwargs):
            return _cr(rc=1, stderr=b"error: device still resuming")

        self.mock_runner.side_effect = side_effect
        with patch.object(self.harness, "capture_prior_state",
                          return_value=object()), \
             patch.object(AdbHarness, "RESTORE_SETTLE_TIMEOUT_SECONDS", 0.1), \
             patch.object(AdbHarness, "RESTORE_SETTLE_POLL_SECONDS", 0.01):
            with self.assertRaises(RuntimeError) as cm:
                self.harness.verify_restore()
        self.assertIn("did not settle to boot_completed=1", str(cm.exception))
        self.assertIn("device still resuming", str(cm.exception))


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


class TestKeyboardGeometryPins(unittest.TestCase):
    """The tap coordinates are not free constants: they are derived
    from the vendored ASK layout XML (defect A, #82). Recompute the
    x-centers from the actual vendored files and compare against the
    harness pins, so a keyboard layout change fails here instead of on
    the device. (y-centers depend on rendered row heights and stay
    pinned by the goldens.)"""

    SCREEN_W = 1080  # 1%p = 10.8px

    def _row_centers(self, row, default_width):
        centers = {}
        x = 0.0
        for key in row:
            gap = key.get("{http://schemas.android.com/apk/res/android}"
                          "horizontalGap")
            if gap:
                x += float(gap.rstrip("%p")) * self.SCREEN_W / 100.0
            width = key.get("{http://schemas.android.com/apk/res/android}"
                            "keyWidth") or default_width
            w = float(width.rstrip("%p")) * self.SCREEN_W / 100.0
            centers[key.get("{http://schemas.android.com/apk/res/android}"
                            "codes")] = x + w / 2.0
            x += w
        return centers

    def test_pins_match_vendored_layout(self):
        import xml.etree.ElementTree as ET

        repo = os.path.abspath(os.path.join(
            os.path.dirname(__file__), "..", "..", "..", ".."))
        qwerty = ET.parse(os.path.join(
            repo, "android/keyboard/addons/languages/english/pack/"
            "src/main/res/xml/qwerty.xml")).getroot()
        letters = {}
        for row in qwerty.iter("Row"):
            row_default = row.get(
                "{http://schemas.android.com/apk/res/android}keyWidth",
                qwerty.get("{http://schemas.android.com/apk/res/android}"
                           "keyWidth", "10%p"))
            letters.update(self._row_centers(row, row_default))
        bottom = ET.parse(os.path.join(
            repo, "android/keyboard/ime/app/src/main/res/xml/"
            "ext_kbd_bottom_row_regular_with_voice.xml")).getroot()
        normal = next(
            r for r in bottom.iter("Row")
            if r.get("{http://schemas.android.com/apk/res/android}"
                     "keyboardMode") == "@integer/keyboard_mode_normal")
        bottom_centers = self._row_centers(
            normal, normal.get(
                "{http://schemas.android.com/apk/res/android}keyWidth"))

        # qwerty.xml rows: codes are ASCII; -1 is the sticky shift.
        self.assertEqual(int(ASK_KEY_COORDS["T"][0]), round(letters["116"]))
        self.assertEqual(int(ASK_KEY_COORDS["E"][0]), round(letters["101"]))
        self.assertEqual(int(ASK_KEY_COORDS["A"][0]), round(letters["97"]))
        self.assertEqual(int(ASK_KEY_COORDS["S"][0]), round(letters["115"]))
        self.assertEqual(int(ASK_KEY_COORDS["I"][0]), round(letters["105"]))
        self.assertEqual(int(ASK_KEY_COORDS["X"][0]), round(letters["120"]))
        self.assertEqual(int(ASK_KEY_COORDS["V"][0]), round(letters["118"]))
        self.assertEqual(int(ASK_KEY_COORDS["N"][0]), round(letters["110"]))
        self.assertEqual(int(SHIFT_TAP[0]), round(letters["-1"]))
        # Bottom row: space and period by resource-ref and code.
        space = next(v for k, v in bottom_centers.items()
                     if k and "key_code_space" in k)
        self.assertEqual(int(ASK_KEY_COORDS[" "][0]), round(space))
        self.assertEqual(int(ASK_KEY_COORDS["."][0]),
                         round(bottom_centers["46"]))


if __name__ == "__main__":
    unittest.main()
