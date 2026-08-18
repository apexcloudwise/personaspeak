"""Fake-only acceptance matrix for the real M2 qualification CLI (issue #62).

Every variant invokes the real CLI as a child process through an absolute
interpreter with an isolated PATH holding only the fake toolchain, then
asserts the contract that must survive ANY outcome:

- exactly one decodable capture-record.json per run,
- exact primary/cleanup step outcomes for the injected failure class,
- child exit-status propagation into the recorded steps,
- zero real-tool contact (every resolved tool lives in fixtures/bin),
- allowed-root containment plus before/after canaries,
- the happy path pinned to the exact complete contact ledger and the
  exact ledgered argv sequence (the goldens below).

Harness-level failure classification is test_execution_boundary's spec;
this file is the CLI-level acceptance matrix on top of it. Failure knobs
are env vars consumed by the fake toolchain in fixtures/bin — unset
knobs leave the honest fake untouched.
"""

import glob
import hashlib
import json
import os
import re
import shutil
import signal
import subprocess
import sys
import time
import unittest

from android.scripts.m2_device.adb_harness import CANDIDATE_REPHRASING
from android.scripts.m2_device.evidence import (
    CANONICAL_ARTIFACTS,
    manifest_digest,
)
from android.scripts.m2_device.records import (
    ApprovalRecord,
    CaptureRecord,
    FinalReceipt,
    TerminalCause,
    decode,
)

HERE = os.path.abspath(os.path.dirname(__file__))
BIN_DIR = os.path.join(HERE, "fixtures", "bin")
REPO_ROOT_ABS = os.path.abspath(os.path.join(HERE, "..", "..", "..", ".."))

GOLDEN_CONTACTS = (
    "adb: --version",
    "emulator: --version",
    "emulator: -list-avds",
    "git: rev-parse HEAD",
    "git: status --porcelain",
    "emulator: -avd M2_Qual_Fixture -snapshot m2_pristine -no-snapshot-save -port 5554",
    "adb: -s emulator-5554 wait-for-device",
    "adb: -s emulator-5554 shell getprop sys.boot_completed",
    "adb: -s emulator-5554 shell getprop ro.build.fingerprint",
    "adb: -s emulator-5554 shell getprop ro.build.version.sdk",
    "adb: -s emulator-5554 shell wm size",
    "adb: -s emulator-5554 shell pm path biz.pixelperfectstudios.personaspeak",
    "adb: -s emulator-5554 shell settings get secure enabled_input_methods",
    "adb: -s emulator-5554 shell settings get secure default_input_method",
    "adb: -s emulator-5554 shell getprop persist.sys.timezone",
    "adb: -s emulator-5554 shell getprop ro.product.locale",
    "adb: -s emulator-5554 shell getprop ro.product.cpu.abi",
    "adb: -s emulator-5554 shell getprop ro.sf.lcd_density",
    "adb: -s emulator-5554 shell settings get global window_animation_scale",
    "adb: -s emulator-5554 shell settings get global transition_animation_scale",
    "adb: -s emulator-5554 shell settings get global animator_duration_scale",
    "adb: -s emulator-5554 shell settings get secure default_input_method",
    "adb: -s emulator-5554 install -r <T>/mock_app.apk",
    "adb: -s emulator-5554 shell dumpsys package biz.pixelperfectstudios.personaspeak",
    "adb: -s emulator-5554 shell screenrecord --time-limit 30 /sdcard/journey.mp4",
    "adb: -s emulator-5554 shell am start -a android.settings.SETTINGS",
    "adb: -s emulator-5554 shell uiautomator dump /sdcard/window_dump.xml",
    "adb: -s emulator-5554 pull /sdcard/window_dump.xml <RUN>/artifacts/journey.xml",
    "adb: -s emulator-5554 shell input tap 500 250",
    "adb: -s emulator-5554 shell uiautomator dump /sdcard/window_dump.xml",
    "adb: -s emulator-5554 pull /sdcard/window_dump.xml <RUN>/artifacts/keyboard_check.xml",
    "adb: -s emulator-5554 shell input tap 475 1375",
    "adb: -s emulator-5554 shell input tap 285 1375",
    "adb: -s emulator-5554 shell input tap 100 1480",
    "adb: -s emulator-5554 shell input tap 540 1690",
    "adb: -s emulator-5554 shell input tap 100 1480",
    "adb: -s emulator-5554 shell input tap 475 1375",
    "adb: -s emulator-5554 shell input tap 540 1690",
    "adb: -s emulator-5554 shell input tap 195 1480",
    "adb: -s emulator-5554 shell input tap 760 1375",
    "adb: -s emulator-5554 shell input tap 245 1585",
    "adb: -s emulator-5554 shell input tap 730 1690",
    "adb: -s emulator-5554 shell screencap -p /sdcard/01-idle-typed.png",
    "adb: -s emulator-5554 pull /sdcard/01-idle-typed.png <RUN>/artifacts/01-idle-typed.png",
    "adb: -s emulator-5554 shell uiautomator dump /sdcard/window_dump.xml",
    "adb: -s emulator-5554 pull /sdcard/window_dump.xml <RUN>/artifacts/loading_1.xml",
    "adb: -s emulator-5554 shell screencap -p /sdcard/02-loading-cancel.png",
    "adb: -s emulator-5554 pull /sdcard/02-loading-cancel.png <RUN>/artifacts/02-loading-cancel.png",
    "adb: -s emulator-5554 shell input tap 780 2340",
    "adb: -s emulator-5554 shell uiautomator dump /sdcard/window_dump.xml",
    "adb: -s emulator-5554 pull /sdcard/window_dump.xml <RUN>/artifacts/after_cancel_loading.xml",
    "adb: -s emulator-5554 shell uiautomator dump /sdcard/window_dump.xml",
    "adb: -s emulator-5554 pull /sdcard/window_dump.xml <RUN>/artifacts/clear.xml",
    "adb: -s emulator-5554 shell input tap 985 235",
    "adb: -s emulator-5554 shell input tap 475 1375",
    "adb: -s emulator-5554 shell input tap 285 1375",
    "adb: -s emulator-5554 shell input tap 100 1480",
    "adb: -s emulator-5554 shell input tap 540 1690",
    "adb: -s emulator-5554 shell input tap 100 1480",
    "adb: -s emulator-5554 shell input tap 475 1375",
    "adb: -s emulator-5554 shell input tap 540 1690",
    "adb: -s emulator-5554 shell input tap 195 1480",
    "adb: -s emulator-5554 shell input tap 760 1375",
    "adb: -s emulator-5554 shell input tap 245 1585",
    "adb: -s emulator-5554 shell input tap 730 1690",
    "adb: -s emulator-5554 shell uiautomator dump /sdcard/window_dump.xml",
    "adb: -s emulator-5554 pull /sdcard/window_dump.xml <RUN>/artifacts/loading_2.xml",
    "adb: -s emulator-5554 shell uiautomator dump /sdcard/window_dump.xml",
    "adb: -s emulator-5554 pull /sdcard/window_dump.xml <RUN>/artifacts/review_2.xml",
    "adb: -s emulator-5554 shell screencap -p /sdcard/03-review.png",
    "adb: -s emulator-5554 pull /sdcard/03-review.png <RUN>/artifacts/03-review.png",
    "adb: -s emulator-5554 shell input tap 300 2340",
    "adb: -s emulator-5554 shell uiautomator dump /sdcard/window_dump.xml",
    "adb: -s emulator-5554 pull /sdcard/window_dump.xml <RUN>/artifacts/after_apply.xml",
    "adb: -s emulator-5554 shell screencap -p /sdcard/04-applied.png",
    "adb: -s emulator-5554 pull /sdcard/04-applied.png <RUN>/artifacts/04-applied.png",
    "adb: -s emulator-5554 shell uiautomator dump /sdcard/window_dump.xml",
    "adb: -s emulator-5554 pull /sdcard/window_dump.xml <RUN>/artifacts/clear.xml",
    "adb: -s emulator-5554 shell input tap 985 235",
    "adb: -s emulator-5554 shell input tap 475 1375",
    "adb: -s emulator-5554 shell input tap 285 1375",
    "adb: -s emulator-5554 shell input tap 100 1480",
    "adb: -s emulator-5554 shell input tap 540 1690",
    "adb: -s emulator-5554 shell input tap 100 1480",
    "adb: -s emulator-5554 shell input tap 475 1375",
    "adb: -s emulator-5554 shell input tap 540 1690",
    "adb: -s emulator-5554 shell input tap 195 1480",
    "adb: -s emulator-5554 shell input tap 760 1375",
    "adb: -s emulator-5554 shell input tap 245 1585",
    "adb: -s emulator-5554 shell input tap 730 1690",
    "adb: -s emulator-5554 shell uiautomator dump /sdcard/window_dump.xml",
    "adb: -s emulator-5554 pull /sdcard/window_dump.xml <RUN>/artifacts/loading_3.xml",
    "adb: -s emulator-5554 shell uiautomator dump /sdcard/window_dump.xml",
    "adb: -s emulator-5554 pull /sdcard/window_dump.xml <RUN>/artifacts/review_3.xml",
    "adb: -s emulator-5554 shell input tap 780 2340",
    "adb: -s emulator-5554 shell uiautomator dump /sdcard/window_dump.xml",
    "adb: -s emulator-5554 pull /sdcard/window_dump.xml <RUN>/artifacts/after_dismiss.xml",
    "adb: -s emulator-5554 shell screencap -p /sdcard/05-dismissed.png",
    "adb: -s emulator-5554 pull /sdcard/05-dismissed.png <RUN>/artifacts/05-dismissed.png",
    "adb: -s emulator-5554 shell uiautomator dump /sdcard/window_dump.xml",
    "adb: -s emulator-5554 pull /sdcard/window_dump.xml <RUN>/artifacts/clear.xml",
    "adb: -s emulator-5554 shell input tap 985 235",
    "adb: -s emulator-5554 shell input tap 475 1375",
    "adb: -s emulator-5554 shell input tap 285 1375",
    "adb: -s emulator-5554 shell input tap 100 1480",
    "adb: -s emulator-5554 shell input tap 540 1690",
    "adb: -s emulator-5554 shell input tap 100 1480",
    "adb: -s emulator-5554 shell input tap 475 1375",
    "adb: -s emulator-5554 shell input tap 540 1690",
    "adb: -s emulator-5554 shell input tap 195 1480",
    "adb: -s emulator-5554 shell input tap 760 1375",
    "adb: -s emulator-5554 shell input tap 245 1585",
    "adb: -s emulator-5554 shell input tap 730 1690",
    "adb: -s emulator-5554 shell uiautomator dump /sdcard/window_dump.xml",
    "adb: -s emulator-5554 pull /sdcard/window_dump.xml <RUN>/artifacts/loading_4.xml",
    "adb: -s emulator-5554 shell uiautomator dump /sdcard/window_dump.xml",
    "adb: -s emulator-5554 pull /sdcard/window_dump.xml <RUN>/artifacts/review_4.xml",
    "adb: -s emulator-5554 shell uiautomator dump /sdcard/window_dump.xml",
    "adb: -s emulator-5554 pull /sdcard/window_dump.xml <RUN>/artifacts/clear.xml",
    "adb: -s emulator-5554 shell input tap 985 235",
    "adb: -s emulator-5554 shell input tap 475 1375",
    "adb: -s emulator-5554 shell input tap 285 1375",
    "adb: -s emulator-5554 shell input tap 100 1480",
    "adb: -s emulator-5554 shell input tap 540 1690",
    "adb: -s emulator-5554 shell input tap 100 1480",
    "adb: -s emulator-5554 shell input tap 475 1375",
    "adb: -s emulator-5554 shell input tap 540 1690",
    "adb: -s emulator-5554 shell input tap 195 1480",
    "adb: -s emulator-5554 shell input tap 285 1375",
    "adb: -s emulator-5554 shell input tap 435 1585",
    "adb: -s emulator-5554 shell input tap 285 1375",
    "adb: -s emulator-5554 shell input tap 625 1585",
    "adb: -s emulator-5554 shell input tap 730 1690",
    "adb: -s emulator-5554 shell input tap 300 2340",
    "adb: -s emulator-5554 shell uiautomator dump /sdcard/window_dump.xml",
    "adb: -s emulator-5554 pull /sdcard/window_dump.xml <RUN>/artifacts/after_stale.xml",
    "adb: -s emulator-5554 shell screencap -p /sdcard/06-stale.png",
    "adb: -s emulator-5554 pull /sdcard/06-stale.png <RUN>/artifacts/06-stale.png",
    "adb: -s emulator-5554 shell input tap 780 2340",
    "adb: -s emulator-5554 shell uiautomator dump /sdcard/window_dump.xml",
    "adb: -s emulator-5554 pull /sdcard/window_dump.xml <RUN>/artifacts/after_stale_dismiss.xml",
    "adb: -s emulator-5554 shell am start -a android.settings.SETTINGS",
    "adb: -s emulator-5554 shell screencap -p /sdcard/07-settings.png",
    "adb: -s emulator-5554 pull /sdcard/07-settings.png <RUN>/artifacts/07-settings.png",
    "adb: -s emulator-5554 pull /sdcard/journey.mp4 <RUN>/artifacts/journey.mp4",
    "adb: -s emulator-5554 emu snapshot load m2_pristine",
    "adb: -s emulator-5554 shell getprop sys.boot_completed",
    "adb: -s emulator-5554 shell getprop ro.build.fingerprint",
    "adb: -s emulator-5554 shell getprop ro.build.version.sdk",
    "adb: -s emulator-5554 shell wm size",
    "adb: -s emulator-5554 shell pm path biz.pixelperfectstudios.personaspeak",
    "adb: -s emulator-5554 shell settings get secure enabled_input_methods",
    "adb: -s emulator-5554 shell settings get secure default_input_method",
    "adb: -s emulator-5554 shell uiautomator dump /sdcard/window_dump.xml",
    "adb: -s emulator-5554 pull /sdcard/window_dump.xml <RUN>/artifacts/verify_restore.xml",
)

# argv of every ledgered command (normalized) + its kind token.
GOLDEN_LEDGER_ARGV = (
    ["<BIN>/emulator", "-avd", "M2_Qual_Fixture", "-snapshot", "m2_pristine", "-no-snapshot-save", "-port", "5554", "launch"],
    ["<BIN>/adb", "-s", "emulator-5554", "wait-for-device", "host"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "getprop", "sys.boot_completed", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "getprop", "ro.build.fingerprint", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "getprop", "ro.build.version.sdk", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "wm", "size", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "pm", "path", "biz.pixelperfectstudios.personaspeak", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "settings", "get", "secure", "enabled_input_methods", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "settings", "get", "secure", "default_input_method", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "getprop", "persist.sys.timezone", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "getprop", "ro.product.locale", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "getprop", "ro.product.cpu.abi", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "getprop", "ro.sf.lcd_density", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "settings", "get", "global", "window_animation_scale", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "settings", "get", "global", "transition_animation_scale", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "settings", "get", "global", "animator_duration_scale", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "settings", "get", "secure", "default_input_method", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "install", "-r", "<T>/mock_app.apk", "host"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "dumpsys", "package", "biz.pixelperfectstudios.personaspeak", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "am", "start", "-a", "android.settings.SETTINGS", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "uiautomator", "dump", "/sdcard/window_dump.xml", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "pull", "/sdcard/window_dump.xml", "<RUN>/artifacts/journey.xml", "host"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "input", "tap", "500", "250", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "uiautomator", "dump", "/sdcard/window_dump.xml", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "pull", "/sdcard/window_dump.xml", "<RUN>/artifacts/keyboard_check.xml", "host"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "input", "tap", "475", "1375", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "input", "tap", "285", "1375", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "input", "tap", "100", "1480", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "input", "tap", "540", "1690", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "input", "tap", "100", "1480", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "input", "tap", "475", "1375", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "input", "tap", "540", "1690", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "input", "tap", "195", "1480", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "input", "tap", "760", "1375", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "input", "tap", "245", "1585", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "input", "tap", "730", "1690", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "screencap", "-p", "/sdcard/01-idle-typed.png", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "pull", "/sdcard/01-idle-typed.png", "<RUN>/artifacts/01-idle-typed.png", "host"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "uiautomator", "dump", "/sdcard/window_dump.xml", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "pull", "/sdcard/window_dump.xml", "<RUN>/artifacts/loading_1.xml", "host"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "screencap", "-p", "/sdcard/02-loading-cancel.png", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "pull", "/sdcard/02-loading-cancel.png", "<RUN>/artifacts/02-loading-cancel.png", "host"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "input", "tap", "780", "2340", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "uiautomator", "dump", "/sdcard/window_dump.xml", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "pull", "/sdcard/window_dump.xml", "<RUN>/artifacts/after_cancel_loading.xml", "host"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "uiautomator", "dump", "/sdcard/window_dump.xml", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "pull", "/sdcard/window_dump.xml", "<RUN>/artifacts/clear.xml", "host"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "input", "tap", "985", "235", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "input", "tap", "475", "1375", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "input", "tap", "285", "1375", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "input", "tap", "100", "1480", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "input", "tap", "540", "1690", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "input", "tap", "100", "1480", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "input", "tap", "475", "1375", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "input", "tap", "540", "1690", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "input", "tap", "195", "1480", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "input", "tap", "760", "1375", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "input", "tap", "245", "1585", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "input", "tap", "730", "1690", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "uiautomator", "dump", "/sdcard/window_dump.xml", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "pull", "/sdcard/window_dump.xml", "<RUN>/artifacts/loading_2.xml", "host"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "uiautomator", "dump", "/sdcard/window_dump.xml", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "pull", "/sdcard/window_dump.xml", "<RUN>/artifacts/review_2.xml", "host"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "screencap", "-p", "/sdcard/03-review.png", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "pull", "/sdcard/03-review.png", "<RUN>/artifacts/03-review.png", "host"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "input", "tap", "300", "2340", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "uiautomator", "dump", "/sdcard/window_dump.xml", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "pull", "/sdcard/window_dump.xml", "<RUN>/artifacts/after_apply.xml", "host"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "screencap", "-p", "/sdcard/04-applied.png", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "pull", "/sdcard/04-applied.png", "<RUN>/artifacts/04-applied.png", "host"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "uiautomator", "dump", "/sdcard/window_dump.xml", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "pull", "/sdcard/window_dump.xml", "<RUN>/artifacts/clear.xml", "host"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "input", "tap", "985", "235", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "input", "tap", "475", "1375", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "input", "tap", "285", "1375", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "input", "tap", "100", "1480", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "input", "tap", "540", "1690", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "input", "tap", "100", "1480", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "input", "tap", "475", "1375", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "input", "tap", "540", "1690", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "input", "tap", "195", "1480", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "input", "tap", "760", "1375", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "input", "tap", "245", "1585", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "input", "tap", "730", "1690", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "uiautomator", "dump", "/sdcard/window_dump.xml", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "pull", "/sdcard/window_dump.xml", "<RUN>/artifacts/loading_3.xml", "host"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "uiautomator", "dump", "/sdcard/window_dump.xml", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "pull", "/sdcard/window_dump.xml", "<RUN>/artifacts/review_3.xml", "host"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "input", "tap", "780", "2340", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "uiautomator", "dump", "/sdcard/window_dump.xml", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "pull", "/sdcard/window_dump.xml", "<RUN>/artifacts/after_dismiss.xml", "host"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "screencap", "-p", "/sdcard/05-dismissed.png", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "pull", "/sdcard/05-dismissed.png", "<RUN>/artifacts/05-dismissed.png", "host"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "uiautomator", "dump", "/sdcard/window_dump.xml", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "pull", "/sdcard/window_dump.xml", "<RUN>/artifacts/clear.xml", "host"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "input", "tap", "985", "235", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "input", "tap", "475", "1375", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "input", "tap", "285", "1375", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "input", "tap", "100", "1480", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "input", "tap", "540", "1690", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "input", "tap", "100", "1480", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "input", "tap", "475", "1375", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "input", "tap", "540", "1690", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "input", "tap", "195", "1480", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "input", "tap", "760", "1375", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "input", "tap", "245", "1585", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "input", "tap", "730", "1690", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "uiautomator", "dump", "/sdcard/window_dump.xml", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "pull", "/sdcard/window_dump.xml", "<RUN>/artifacts/loading_4.xml", "host"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "uiautomator", "dump", "/sdcard/window_dump.xml", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "pull", "/sdcard/window_dump.xml", "<RUN>/artifacts/review_4.xml", "host"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "uiautomator", "dump", "/sdcard/window_dump.xml", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "pull", "/sdcard/window_dump.xml", "<RUN>/artifacts/clear.xml", "host"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "input", "tap", "985", "235", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "input", "tap", "475", "1375", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "input", "tap", "285", "1375", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "input", "tap", "100", "1480", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "input", "tap", "540", "1690", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "input", "tap", "100", "1480", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "input", "tap", "475", "1375", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "input", "tap", "540", "1690", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "input", "tap", "195", "1480", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "input", "tap", "285", "1375", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "input", "tap", "435", "1585", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "input", "tap", "285", "1375", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "input", "tap", "625", "1585", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "input", "tap", "730", "1690", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "input", "tap", "300", "2340", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "uiautomator", "dump", "/sdcard/window_dump.xml", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "pull", "/sdcard/window_dump.xml", "<RUN>/artifacts/after_stale.xml", "host"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "screencap", "-p", "/sdcard/06-stale.png", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "pull", "/sdcard/06-stale.png", "<RUN>/artifacts/06-stale.png", "host"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "input", "tap", "780", "2340", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "uiautomator", "dump", "/sdcard/window_dump.xml", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "pull", "/sdcard/window_dump.xml", "<RUN>/artifacts/after_stale_dismiss.xml", "host"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "am", "start", "-a", "android.settings.SETTINGS", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "screencap", "-p", "/sdcard/07-settings.png", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "pull", "/sdcard/07-settings.png", "<RUN>/artifacts/07-settings.png", "host"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "screenrecord", "--time-limit", "30", "/sdcard/journey.mp4", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "pull", "/sdcard/journey.mp4", "<RUN>/artifacts/journey.mp4", "host"],
    ["<BIN>/adb", "-s", "emulator-5554", "emu", "snapshot", "load", "m2_pristine", "host"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "getprop", "sys.boot_completed", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "getprop", "ro.build.fingerprint", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "getprop", "ro.build.version.sdk", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "wm", "size", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "pm", "path", "biz.pixelperfectstudios.personaspeak", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "settings", "get", "secure", "enabled_input_methods", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "settings", "get", "secure", "default_input_method", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "shell", "uiautomator", "dump", "/sdcard/window_dump.xml", "shell"],
    ["<BIN>/adb", "-s", "emulator-5554", "pull", "/sdcard/window_dump.xml", "<RUN>/artifacts/verify_restore.xml", "host"],
)


_STATE_FILES = ("edittext.state", "keyboard.state", "focus.state")
_TOP_LEVEL_ALLOWED = {
    "evidence", "repo", "avd", "mock_app.apk", "mock_commands.log",
    "fixture_digests.json", "canary",
} | set(_STATE_FILES)


class TestAcceptanceMatrix(unittest.TestCase):

    maxDiff = None

    def setUp(self):
        self.test_dir = os.path.join(HERE, "fixtures", "scratch_matrix")
        shutil.rmtree(self.test_dir, ignore_errors=True)
        self.evidence_root = os.path.join(self.test_dir, "evidence")
        self.repo_root = os.path.join(self.test_dir, "repo")
        os.makedirs(self.evidence_root)
        os.makedirs(self.repo_root)

        # Canary tree outside every allowed root: nothing in the run may
        # create, modify, or delete anything under it.
        self.canary_dir = os.path.join(self.test_dir, "canary")
        os.makedirs(os.path.join(self.canary_dir, "nested"))
        with open(os.path.join(self.canary_dir, "sentinel.bin"), "wb") as f:
            f.write(b"sentinel-bytes")
        with open(os.path.join(self.canary_dir, "nested", "deep.txt"), "w") as f:
            f.write("deep")
        self._canary_before = self._tree_digest(self.canary_dir)

        self.fixture_root = os.path.join(self.test_dir, "avd")
        self.digests_path = os.path.join(self.test_dir, "fixture_digests.json")
        self._fixture_rels = (
            "M2_Qual_Fixture.avd/hardware.ini",
            "M2_Qual_Fixture.avd/snapshots/m2_pristine/ram.bin",
            "M2_Qual_Fixture.avd/snapshots/m2_pristine/textures.bin",
        )
        digests = {}
        for rel in self._fixture_rels:
            path = os.path.join(self.fixture_root, *rel.split("/"))
            os.makedirs(os.path.dirname(path), exist_ok=True)
            content = f"fake-fixture:{rel}".encode()
            with open(path, "wb") as f:
                f.write(content)
            digests[rel] = hashlib.sha256(content).hexdigest()
        with open(self.digests_path, "w") as f:
            json.dump(digests, f)
        self._fixture_digests_before = self._snapshot_fixtures()

        for args in (["git", "init"], ["git", "config", "user.email", "t@t"],
                     ["git", "config", "user.name", "T"], ["git", "add", "-A"],
                     ["git", "commit", "-m", "init"]):
            subprocess.run(args, cwd=self.repo_root, capture_output=True)
        self.head = subprocess.run(
            ["git", "rev-parse", "HEAD"], cwd=self.repo_root,
            capture_output=True).stdout.decode().strip()

        self.apk_path = os.path.join(self.test_dir, "mock_app.apk")
        with open(self.apk_path, "wb") as f:
            f.write(b"mock_apk_binary")
        self.apk_sha256 = hashlib.sha256(b"mock_apk_binary").hexdigest()
        self.log_path = os.path.join(self.test_dir, "mock_commands.log")

    def tearDown(self):
        shutil.rmtree(self.test_dir, ignore_errors=True)

    # ---------- helpers ----------

    def _snapshot_fixtures(self):
        snap = {}
        for rel in self._fixture_rels:
            path = os.path.join(self.fixture_root, *rel.split("/"))
            with open(path, "rb") as f:
                snap[rel] = hashlib.sha256(f.read()).hexdigest()
        return snap

    def _tree_digest(self, path):
        entries = {}
        for root, _, files in os.walk(path):
            for name in sorted(files):
                p = os.path.join(root, name)
                with open(p, "rb") as f:
                    entries[os.path.relpath(p, path)] = (
                        hashlib.sha256(f.read()).hexdigest())
        return entries

    def _env(self, extra=None):
        env = {
            "PATH": BIN_DIR + os.pathsep + os.path.dirname(sys.executable),
            "HOME": os.environ.get("HOME", "/tmp"),
            "MOCK_COMMANDS_LOG": self.log_path,
            "FAKE_ADB_STATE": os.path.join(self.test_dir, "edittext.state"),
            "FAKE_ADB_KEYBOARD": os.path.join(self.test_dir, "keyboard.state"),
            "FAKE_ADB_FOCUS": os.path.join(self.test_dir, "focus.state"),
            "FAKE_ADB_REPHRASING": CANDIDATE_REPHRASING,
            "FAKE_GIT_HEAD": self.head,
            "PYTHONPATH": REPO_ROOT_ABS,
        }
        for k in _STATE_FILES:
            with open(os.path.join(self.test_dir, k), "w") as f:
                f.write("")
        if extra:
            env.update(extra)
        return env

    def _cli_argv(self, evidence_root=None):
        return [sys.executable, "-m", "android.scripts.m2_device.cli", "capture",
                "--evidence-root", evidence_root or self.evidence_root,
                "--repo-root", self.repo_root,
                "--apk-path", self.apk_path,
                "--apk-sha256", self.apk_sha256,
                "--fixture-root", self.fixture_root,
                "--fixture-digests", self.digests_path]

    def _run(self, extra_env=None, timeout=150):
        return subprocess.run(
            self._cli_argv(), env=self._env(extra_env),
            capture_output=True, cwd=REPO_ROOT_ABS, timeout=timeout)

    def _normalize(self, text):
        text = text.replace(self.test_dir, "<T>").replace(BIN_DIR, "<BIN>")
        return re.sub(r"<T>/evidence/\d{8}T\d{6}Z", "<RUN>", text)

    def _contacts(self):
        if not os.path.exists(self.log_path):
            return []
        with open(self.log_path) as f:
            return [self._normalize(l.rstrip("\n")) for l in f if l.strip()]

    def _run_dirs(self):
        return sorted(glob.glob(
            os.path.join(self.evidence_root, "????????T??????Z")))

    def _record(self):
        run_dirs = self._run_dirs()
        self.assertEqual(
            len(run_dirs), 1,
            f"expected exactly one run dir, found {run_dirs}")
        record_path = os.path.join(run_dirs[0], "capture-record.json")
        self.assertTrue(os.path.isfile(record_path),
                        f"capture-record.json missing at {record_path}")
        with open(record_path, "rb") as f:
            record = decode(f.read())
        self.assertIsInstance(record, CaptureRecord)
        return run_dirs[0], record

    def _steps(self, record, phase):
        return [s for s in record.steps if s.phase == phase]

    def _last(self, record, phase):
        steps = self._steps(record, phase)
        return steps[-1] if steps else None

    def _assert_canaries(self, run_dir_extras=(), top_level_extras=()):
        # Fixture immutability: identical bytes before and after the run.
        self.assertEqual(self._snapshot_fixtures(),
                         self._fixture_digests_before)
        # Untouched canary tree outside every allowed root.
        self.assertEqual(self._tree_digest(self.canary_dir),
                         self._canary_before)
        # Containment: the evidence root holds only the run dir, and the
        # run dir holds only canonical outputs (plus declared hostile
        # writes for the variants that inject them).
        run_dirs = self._run_dirs()
        self.assertEqual(sorted(os.listdir(self.evidence_root)),
                         [os.path.basename(d) for d in run_dirs])
        run_dir = run_dirs[0]
        allowed_run = ({"artifacts", "capture-record.json", "manifest.json"}
                       | set(run_dir_extras))
        self.assertTrue(
            set(os.listdir(run_dir)) <= allowed_run,
            f"unexpected files in run dir: "
            f"{set(os.listdir(run_dir)) - allowed_run}")
        artifacts = os.path.join(run_dir, "artifacts")
        if os.path.isdir(artifacts):
            self.assertTrue(
                set(os.listdir(artifacts)) <= CANONICAL_ARTIFACTS | {"hostile-extra.bin"},
                f"non-canonical artifact files: "
                f"{set(os.listdir(artifacts)) - CANONICAL_ARTIFACTS - {'hostile-extra.bin'}}")
        # The scratch workspace gained nothing at top level beyond the
        # known state/log/fixture files (and declared variant extras).
        self.assertEqual(
            set(os.listdir(self.test_dir)),
            _TOP_LEVEL_ALLOWED | set(top_level_extras),
            "unexpected top-level files in scratch workspace")

    def _assert_common(self, record, launched=True,
                       run_dir_extras=(), top_level_extras=()):
        # Zero real-tool contact: every resolved tool identity points
        # inside the isolated fake bin; the isolated PATH is the boundary
        # and the recorded identities are the proof.
        for tool in record.tools:
            self.assertTrue(
                tool.path.startswith(BIN_DIR + os.sep),
                f"tool resolved outside fake bin: {tool.name} -> {tool.path}")
        if launched:
            for phase in ("restore", "release_emulator", "verify_release"):
                self.assertIsNotNone(
                    self._last(record, phase),
                    f"cleanup phase {phase} missing from record")
        self._assert_canaries(run_dir_extras, top_level_extras)

    def _assert_contacts_fake_only(self):
        for line in self._contacts():
            self.assertTrue(
                line.startswith(("adb: ", "emulator: ", "git: ")),
                f"contact with unknown tool: {line}")

    # ---------- happy path ----------

    def test_matrix_happy_path_exact_ledger_and_finalize_chain(self):
        result = self._run()
        self.assertEqual(result.returncode, 0,
                         f"CLI failed: {result.stderr.decode()}")
        run_dir, record = self._record()
        self._assert_common(record)
        self._assert_contacts_fake_only()

        # Exact complete contact-ledger equality against the golden,
        # with one honest exception: the screenrecord start is started
        # via Popen as a concurrent sibling, so the moment its own log
        # write lands relative to the next sequential contact is OS
        # scheduling, not harness behavior. Its presence (exactly once)
        # and every other line's argv and position are pinned exactly.
        contacts = self._contacts()
        rec_start = "adb: -s emulator-5554 shell screenrecord --time-limit 30 /sdcard/journey.mp4"
        self.assertEqual(contacts.count(rec_start), 1,
                         "screenrecord start contact missing or duplicated")
        self.assertEqual(
            [c for c in contacts if c != rec_start],
            [c for c in GOLDEN_CONTACTS if c != rec_start])

        # Exact ledgered argv sequence, all clean.
        with open(os.path.join(run_dir, "artifacts",
                               "command_ledger.json")) as f:
            ledger = json.load(f)
        normalized = [[self._normalize(a) for a in e["argv"]] + [e["kind"]]
                      for e in ledger]
        self.assertEqual(normalized, [list(x) for x in GOLDEN_LEDGER_ARGV])
        for e in ledger:
            self.assertEqual(e["transport_rc"], 0,
                             f"nonzero transport rc: {e['argv']}")
            self.assertFalse(e["timed_out"])

        # Primary outcome: every recorded step completed, canonical set
        # on disk, manifest bound to the record.
        for s in record.steps:
            self.assertEqual(s.cause, TerminalCause.COMPLETED,
                             f"step {s.phase}/{s.operation} was {s.cause}")
        self.assertIsNotNone(record.restoration)
        self.assertEqual(record.restoration.cause, TerminalCause.COMPLETED)
        with open(os.path.join(run_dir, "manifest.json")) as f:
            manifest = json.load(f)
        self.assertEqual(set(manifest), set(CANONICAL_ARTIFACTS))
        self.assertEqual(record.manifest_digest, manifest_digest(manifest))
        for name, digest in manifest.items():
            with open(os.path.join(run_dir, "artifacts", name), "rb") as f:
                self.assertEqual(
                    hashlib.sha256(f.read()).hexdigest(), digest,
                    f"manifest digest mismatch: {name}")

        # Fake-only boundary: injected digests are verdict-marked in the
        # validate_fixture step's serialized stdout.
        vf = self._last(record, "validate_fixture")
        self.assertIn(b"fake-only", vf.result.stdout)
        self.assertIn(b"not an accepted-fixture qualification",
                      vf.result.stdout)

        # Preserved receipts end-to-end: approve + finalize through the
        # real CLI produce a decodable final receipt.
        approval_path = os.path.join(self.test_dir, "approval.bin")
        receipt_path = os.path.join(self.test_dir, "receipt.bin")
        env = self._env()
        r1 = subprocess.run(
            [sys.executable, "-m", "android.scripts.m2_device.cli", "approve",
             "--capture-record", os.path.join(run_dir, "capture-record.json"),
             "--manifest", os.path.join(run_dir, "manifest.json"),
             "--reviewer", "matrix-test",
             "--output", approval_path],
            env=env, capture_output=True, cwd=REPO_ROOT_ABS, timeout=30)
        self.assertEqual(r1.returncode, 0, r1.stderr.decode())
        with open(approval_path, "rb") as f:
            self.assertIsInstance(decode(f.read()), ApprovalRecord)
        r2 = subprocess.run(
            [sys.executable, "-m", "android.scripts.m2_device.cli", "finalize",
             "--capture-record", os.path.join(run_dir, "capture-record.json"),
             "--approval", approval_path,
             "--manifest", os.path.join(run_dir, "manifest.json"),
             "--run-dir", run_dir,
             "--output", receipt_path],
            env=env, capture_output=True, cwd=REPO_ROOT_ABS, timeout=30)
        self.assertEqual(r2.returncode, 0, r2.stderr.decode())
        with open(receipt_path, "rb") as f:
            receipt = decode(f.read())
        self.assertIsInstance(receipt, FinalReceipt)
        self.assertEqual(receipt.counts["png"], 7)
        self.assertEqual(receipt.counts["mp4"], 1)
        self.assertEqual(set(receipt.artifacts), set(CANONICAL_ARTIFACTS))

    # ---------- preflight ----------

    def test_matrix_preflight_failed_avd_missing(self):
        result = self._run({"FAKE_EMU_EMPTY_LIST": "1"})
        self.assertNotEqual(result.returncode, 0)
        _, record = self._record()
        self._assert_common(record, launched=False)
        self._assert_contacts_fake_only()
        # Pinned by test_orchestrator: preflight tool failures classify
        # as tool_failure; preflight_failed is reserved for
        # capture-context failures.
        step = self._last(record, "preflight")
        self.assertEqual(step.cause, TerminalCause.TOOL_FAILURE)
        self.assertIn(b"not found", step.result.stderr)
        # Fail-closed ordering: nothing launched, nothing mutated.
        contacts = self._contacts()
        self.assertFalse(any("-avd" in c and "-list-avds" not in c
                             for c in contacts),
                         "emulator launched despite preflight failure")
        self.assertFalse(any("install" in c for c in contacts),
                         "install attempted despite preflight failure")
        self.assertIsNone(record.prior_state)

    def test_matrix_preflight_failed_version_mismatch(self):
        result = self._run({"FAKE_EMU_VERSION_BOGUS": "1"})
        self.assertNotEqual(result.returncode, 0)
        _, record = self._record()
        self._assert_common(record, launched=False)
        step = self._last(record, "preflight")
        self.assertEqual(step.cause, TerminalCause.TOOL_FAILURE)
        self.assertIn(b"emulator version mismatch", step.result.stderr)

    def test_matrix_preflight_failed_dirty_repo(self):
        # capture-context failure: the repo is not clean, so the run
        # refuses before launching anything.
        result = self._run({"FAKE_GIT_DIRTY": "1"})
        self.assertNotEqual(result.returncode, 0)
        _, record = self._record()
        self._assert_common(record, launched=False)
        step = self._last(record, "capture_context")
        self.assertEqual(step.cause, TerminalCause.PREFLIGHT_FAILED)
        self.assertIn(b"not clean", step.result.stderr)
        self.assertFalse(any("-avd" in c and "-list-avds" not in c
                             for c in self._contacts()),
                         "emulator launched despite dirty repo")

    # ---------- prior state / fixture ----------

    def test_matrix_prior_state_unavailable_garbage_output(self):
        result = self._run({"FAKE_ADB_GARBAGE": "wm size"})
        self.assertNotEqual(result.returncode, 0)
        _, record = self._record()
        self._assert_common(record, launched=False)
        step = self._last(record, "prior_state")
        self.assertEqual(step.cause, TerminalCause.PRIOR_STATE_UNAVAILABLE)
        # No mutation after an unreadable prior state.
        self.assertFalse(any("install" in c for c in self._contacts()))

    def test_matrix_fixture_mismatch_prop_override(self):
        result = self._run({"FAKE_ADB_PROP": "ro.build.version.sdk=33"})
        self.assertNotEqual(result.returncode, 0)
        _, record = self._record()
        self._assert_common(record, launched=False)
        step = self._last(record, "validate_fixture")
        self.assertEqual(step.cause, TerminalCause.FIXTURE_MISMATCH)
        self.assertIn(b"api_level mismatch", step.result.stderr)
        self.assertFalse(any("install" in c for c in self._contacts()))

    def test_matrix_fixture_drift_no_mutation(self):
        ram = os.path.join(self.fixture_root, "M2_Qual_Fixture.avd",
                           "snapshots", "m2_pristine", "ram.bin")
        with open(ram, "ab") as f:
            f.write(b"drift")
        # The drift itself is the pre-existing condition; re-pin the
        # canary so the assert still proves the run added no mutation.
        self._fixture_digests_before = self._snapshot_fixtures()
        result = self._run()
        self.assertNotEqual(result.returncode, 0)
        _, record = self._record()
        self._assert_common(record, launched=False)
        step = self._last(record, "validate_fixture")
        self.assertEqual(step.cause, TerminalCause.FIXTURE_MISMATCH)
        self.assertIn(b"fixture digest drift", step.result.stderr)
        self.assertFalse(any("install" in c for c in self._contacts()))

    # ---------- install ----------

    def test_matrix_install_failed(self):
        result = self._run({"FAKE_ADB_INSTALL_FAIL": "1"})
        self.assertNotEqual(result.returncode, 0)
        self.assertIn(b"install", (result.stderr + result.stdout).lower())
        _, record = self._record()
        self._assert_common(record)
        step = self._last(record, "install")
        self.assertEqual(step.cause, TerminalCause.INSTALL_FAILED)
        # The journey never started, but cleanup still ran.
        self.assertFalse(self._steps(record, "journey"))
        self.assertEqual(self._last(record, "restore").cause,
                         TerminalCause.COMPLETED)

    # ---------- wrapper/remote status collisions ----------

    def test_matrix_wrapper_remote_nonzero_rc_propagates(self):
        # shell_v2: the wrapper exit code IS the remote exit code. rc=7
        # is an unambiguous remote failure and must arrive intact.
        result = self._run({"FAKE_ADB_FAIL": "input tap=7"})
        self.assertNotEqual(result.returncode, 0)
        _, record = self._record()
        self._assert_common(record)
        step = self._last(record, "journey")
        self.assertEqual(step.cause, TerminalCause.JOURNEY_FAILED)
        self.assertEqual(step.result.transport.returncode, 7)
        self.assertEqual(step.result.remote_rc, 7)
        self.assertEqual(step.operation, "focus_editor")

    def test_matrix_wrapper_remote_ambiguous_fails_closed(self):
        # rc=1 with stderr is structurally ambiguous: the step must fail
        # closed as tool_failure, never as success.
        result = self._run({"FAKE_ADB_FAIL": "input tap=1:synthetic transport failure"})
        self.assertNotEqual(result.returncode, 0)
        _, record = self._record()
        self._assert_common(record)
        step = self._last(record, "journey")
        self.assertEqual(step.cause, TerminalCause.TOOL_FAILURE)
        self.assertIsNone(step.result.remote_rc)

    def test_matrix_prior_state_ambiguous_fails_closed(self):
        result = self._run({"FAKE_ADB_FAIL":
                            "getprop sys.boot_completed=1:device offline (ambiguous)"})
        self.assertNotEqual(result.returncode, 0)
        _, record = self._record()
        self._assert_common(record, launched=False)
        step = self._last(record, "prior_state")
        self.assertEqual(step.cause, TerminalCause.TOOL_FAILURE)
        self.assertIn(b"getprop sys.boot_completed", step.result.stderr)
        self.assertFalse(any("install" in c for c in self._contacts()))

    # ---------- malformed output / XML / media ----------

    def test_matrix_malformed_xml_fails_journey_closed(self):
        # An unparsable hierarchy with a zero rc must fail the journey
        # itself — never record COMPLETED for facts it could not read.
        result = self._run({"FAKE_ADB_BAD_XML": "1"})
        self.assertNotEqual(result.returncode, 0)
        _, record = self._record()
        self._assert_common(record)
        step = self._last(record, "journey")
        self.assertEqual(step.cause, TerminalCause.JOURNEY_FAILED)
        self.assertIn(b"hierarchy missing or unparsable", step.result.stderr)

    def test_matrix_silent_pull_preserves_record(self):
        # Hostile tool: pull returns rc=0 and writes nothing. The
        # journey must fail closed AND the capture record must survive
        # — a lying transport never costs the run its receipts.
        result = self._run({"FAKE_ADB_PULL_SILENT": "1"})
        self.assertNotEqual(result.returncode, 0)
        _, record = self._record()
        self._assert_common(record)
        step = self._last(record, "journey")
        self.assertEqual(step.cause, TerminalCause.JOURNEY_FAILED)
        self.assertIn(b"hierarchy missing or unparsable", step.result.stderr)
        self.assertEqual(self._last(record, "release_emulator").cause,
                         TerminalCause.COMPLETED)

    def test_matrix_malformed_png_fails_journey(self):
        result = self._run({"FAKE_ADB_BAD_PNG": "1"})
        self.assertNotEqual(result.returncode, 0)
        _, record = self._record()
        self._assert_common(record)
        step = self._last(record, "journey")
        self.assertEqual(step.cause, TerminalCause.JOURNEY_FAILED)
        self.assertIn(b"invalid PNG", step.result.stderr)

    def test_matrix_malformed_mp4_late_capture_failure(self):
        # Late failure: the whole journey completed; only evidence
        # validation refuses the corrupt video. Cleanup still runs.
        result = self._run({"FAKE_ADB_BAD_MP4": "1"})
        self.assertNotEqual(result.returncode, 0)
        self.assertIn(b"capture", (result.stderr + result.stdout).lower())
        _, record = self._record()
        self._assert_common(record)
        step = self._last(record, "capture")
        self.assertEqual(step.cause, TerminalCause.CAPTURE_FAILED)
        self.assertIn(b"journey.mp4 invalid", step.result.stderr)
        self.assertEqual(self._last(record, "restore").cause,
                         TerminalCause.COMPLETED)
        self.assertEqual(self._last(record, "release_emulator").cause,
                         TerminalCause.COMPLETED)

    # ---------- selector duplication ----------

    def test_matrix_selector_duplication_fails_closed(self):
        # Two nodes answering one resource-id: selection must fail, not
        # pick silently.
        result = self._run({"FAKE_ADB_DUPLICATE": "search_action_bar"})
        self.assertNotEqual(result.returncode, 0)
        _, record = self._record()
        self._assert_common(record)
        step = self._last(record, "journey")
        self.assertEqual(step.cause, TerminalCause.JOURNEY_FAILED)
        self.assertTrue(
            step.operation in ("pin_pristine_state", "locate_editor",
                               "verify_text"),
            f"unexpected failing op: {step.operation}")

    # ---------- restoration / cleanup failures ----------

    def test_matrix_restoration_mismatch_public_state(self):
        # The snapshot load reports success, then the device answers a
        # later identity query with drifted facts.
        result = self._run({
            "FAKE_ADB_POST_RESTORE_WM": "1440x3200",
            "FAKE_ADB_WM_FLAG": os.path.join(self.test_dir, "restored.flag"),
        })
        self.assertNotEqual(result.returncode, 0)
        _, record = self._record()
        self._assert_common(record, top_level_extras=("restored.flag",))
        self.assertEqual(self._last(record, "restore").cause,
                         TerminalCause.COMPLETED)
        step = self._last(record, "verify_restore")
        self.assertEqual(step.cause, TerminalCause.RESTORATION_MISMATCH)

    def test_matrix_combined_cleanup_failures(self):
        # One run, multiple failing cleanup steps: restore exits nonzero
        # AND the un-reset state fails the restoration verdict — while
        # release and the ledger still complete and the record survives.
        result = self._run({"FAKE_ADB_RESTORE_FAIL": "1"})
        self.assertNotEqual(result.returncode, 0)
        _, record = self._record()
        self._assert_common(record)
        restore = self._last(record, "restore")
        self.assertEqual(restore.cause, TerminalCause.CLEANUP_PARTIAL)
        self.assertEqual(restore.result.returncode, 2)
        verify = self._last(record, "verify_restore")
        self.assertEqual(verify.cause, TerminalCause.TOOL_FAILURE)
        self.assertIn(b"restoration mismatch", verify.result.stderr)
        self.assertEqual(self._last(record, "release_emulator").cause,
                         TerminalCause.COMPLETED)
        self.assertEqual(self._last(record, "ledger").cause,
                         TerminalCause.COMPLETED)

    # ---------- timeout ----------

    def test_matrix_timeout_journey_command(self):
        # A journey command that outlives its 30s transport budget: the
        # step records timeout, the child is killed, cleanup converges.
        result = self._run({"FAKE_ADB_SLEEP_ON": "input tap",
                            "FAKE_ADB_SLEEP_SECS": "40"}, timeout=180)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn(b"timeout", (result.stderr + result.stdout).lower())
        _, record = self._record()
        self._assert_common(record)
        step = self._last(record, "journey")
        self.assertEqual(step.cause, TerminalCause.TIMEOUT)
        self.assertTrue(step.result.transport.timed_out)

    # ---------- signals ----------

    def _signal_mid_journey(self, sig):
        env = self._env({"FAKE_ADB_SLEEP_ON": "input tap",
                         "FAKE_ADB_SLEEP_SECS": "120"})
        proc = subprocess.Popen(
            self._cli_argv(), env=env, stdout=subprocess.PIPE,
            stderr=subprocess.PIPE, cwd=REPO_ROOT_ABS)
        deadline = time.monotonic() + 60
        signaled = False
        while time.monotonic() < deadline:
            if any("input tap" in c for c in self._contacts()):
                proc.send_signal(sig)
                signaled = True
                break
            time.sleep(0.1)
        self.assertTrue(signaled, "journey tap never observed in contacts")
        out, err = proc.communicate(timeout=150)
        self.assertNotEqual(proc.returncode, 0)
        self.assertIn(b"signal_interrupt", err)
        _, record = self._record()
        self._assert_common(record)
        # The interrupt converged into cleanup: restore ran after the
        # interrupted tap, release after restore.
        contacts = self._contacts()
        last_tap = max(i for i, c in enumerate(contacts) if "input tap" in c)
        restore_at = next(i for i, c in enumerate(contacts)
                          if "snapshot load" in c)
        self.assertLess(last_tap, restore_at,
                        "restore did not run after the interrupted journey")
        self.assertEqual(self._last(record, "restore").cause,
                         TerminalCause.COMPLETED)
        self.assertEqual(self._last(record, "release_emulator").cause,
                         TerminalCause.COMPLETED)

    def test_matrix_sigint_mid_journey_converges_to_cleanup(self):
        self._signal_mid_journey(signal.SIGINT)

    def test_matrix_sigterm_mid_journey_converges_to_cleanup(self):
        self._signal_mid_journey(signal.SIGTERM)

    # ---------- hostile artifacts: escape / links / extras ----------

    def test_matrix_extra_artifact_rejected_canonical_set(self):
        # A hostile tool drops an unlisted regular file into the
        # artifacts dir: the canonical-set gate refuses qualification.
        result = self._run({"FAKE_ADB_EXTRA_ARTIFACT": "1"})
        self.assertNotEqual(result.returncode, 0)
        self.assertIn(b"hostile-extra.bin",
                      result.stderr + result.stdout)
        _, record = self._record()
        self._assert_common(record)
        step = self._last(record, "ledger")
        self.assertEqual(step.cause, TerminalCause.COMPLETED)

    def test_matrix_symlink_artifact_rejected(self):
        # Artifacts as symlinks to valid bytes elsewhere: the manifest
        # walk rejects links even when the content parses.
        result = self._run({"FAKE_ADB_SYMLINK_XML": "1"})
        self.assertNotEqual(result.returncode, 0)
        self.assertIn(b"symlink rejected", result.stderr + result.stdout)
        run_dir, record = self._record()
        # hierarchy_source.xml lives in the run dir (outside artifacts);
        # every window_dump artifact is a link to it.
        self.assertTrue(os.path.islink(os.path.join(
            run_dir, "artifacts", "journey.xml")))
        self._assert_common(record, run_dir_extras=("hierarchy_source.xml",))

    def test_matrix_canary_positive_control_hostile_escape_detected(self):
        # Positive control for the canary machinery itself: a hostile
        # tool writing outside the artifacts dir is invisible to the
        # harness (the run succeeds) — and is exactly what the
        # containment check is there to catch. A canary that has never
        # fired is not a canary.
        result = self._run({"FAKE_ADB_ESCAPE": "1"})
        self.assertEqual(result.returncode, 0,
                         f"expected harness-blind escape to succeed: "
                         f"{result.stderr.decode()}")
        run_dir, record = self._record()
        self.assertTrue(
            os.path.isfile(os.path.join(run_dir, "hostile-write.bin")),
            "hostile escape write not present — canary has nothing to detect")
        self._assert_common(record, run_dir_extras=("hostile-write.bin",))

    # ---------- late emulator death ----------

    def test_matrix_emulator_late_death_converges_cleanly(self):
        # The emulator dies mid-journey (after ownership was proven on a
        # live process): release observes the exit, reaps, and reports
        # success; the receipt chain still finalizes.
        result = self._run({"FAKE_EMU_EXIT_ON_TEXT": "1"})
        self.assertEqual(result.returncode, 0,
                         f"CLI failed: {result.stderr.decode()}")
        run_dir, record = self._record()
        self._assert_common(record)
        release = self._last(record, "release_emulator")
        self.assertIn(b"already exited", release.result.stdout)
        self.assertEqual(release.cause, TerminalCause.COMPLETED)

        approval_path = os.path.join(self.test_dir, "approval.bin")
        receipt_path = os.path.join(self.test_dir, "receipt.bin")
        env = self._env()
        subprocess.run(
            [sys.executable, "-m", "android.scripts.m2_device.cli", "approve",
             "--capture-record", os.path.join(run_dir, "capture-record.json"),
             "--manifest", os.path.join(run_dir, "manifest.json"),
             "--reviewer", "matrix-test", "--output", approval_path],
            env=env, capture_output=True, cwd=REPO_ROOT_ABS, timeout=30,
            check=True)
        subprocess.run(
            [sys.executable, "-m", "android.scripts.m2_device.cli", "finalize",
             "--capture-record", os.path.join(run_dir, "capture-record.json"),
             "--approval", approval_path,
             "--manifest", os.path.join(run_dir, "manifest.json"),
             "--run-dir", run_dir, "--output", receipt_path],
            env=env, capture_output=True, cwd=REPO_ROOT_ABS, timeout=30,
            check=True)
        with open(receipt_path, "rb") as f:
            self.assertIsInstance(decode(f.read()), FinalReceipt)

    # ---------- evidence-root boundary ----------

    def test_matrix_evidence_root_inside_repo_rejected(self):
        bad_root = os.path.join(self.repo_root, "ev")
        argv = self._cli_argv(evidence_root=bad_root)
        result = subprocess.run(
            argv, env=self._env(), capture_output=True,
            cwd=REPO_ROOT_ABS, timeout=30)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn(b"evidence root", result.stderr)
        self.assertFalse(self._run_dirs(),
                         "run dir created despite rejected evidence root")
        self.assertFalse(os.path.exists(bad_root))


if __name__ == "__main__":
    unittest.main()
