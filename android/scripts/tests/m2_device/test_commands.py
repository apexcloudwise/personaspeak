"""Tests for local process execution, remote results, and tool resolution."""

import os
import stat
import sys
import tempfile
import unittest

from android.scripts.m2_device import commands as C
from android.scripts.m2_device.records import CommandResult, RemoteResult


class TestRun(unittest.TestCase):
    def test_successful_command(self):
        cr = C.run(["echo", "hello"])
        self.assertEqual(cr.returncode, 0)
        self.assertEqual(cr.stdout, b"hello\n")
        self.assertEqual(cr.stderr, b"")
        self.assertFalse(cr.timed_out)
        self.assertEqual(cr.argv, ["echo", "hello"])
        self.assertTrue(cr.start_utc)
        self.assertTrue(cr.end_utc)

    def test_nonzero_returncode(self):
        cr = C.run(["sh", "-c", "exit 7"])
        self.assertEqual(cr.returncode, 7)
        self.assertFalse(cr.timed_out)

    def test_empty_output(self):
        cr = C.run(["true"])
        self.assertEqual(cr.stdout, b"")
        self.assertEqual(cr.stderr, b"")

    def test_no_trailing_newline(self):
        cr = C.run(["printf", "no-newline"])
        self.assertEqual(cr.stdout, b"no-newline")

    def test_special_punctuation(self):
        payload = "a/b.C:d;e f-g_h__RC=0"
        cr = C.run(["printf", payload])
        self.assertEqual(cr.stdout, payload.encode())

    def test_stderr_captured(self):
        cr = C.run(["sh", "-c", "echo err >&2"])
        self.assertEqual(cr.stderr, b"err\n")

    def test_timeout(self):
        cr = C.run(["sleep", "30"], timeout=1)
        self.assertTrue(cr.timed_out)

    def test_argv_not_shell_string(self):
        cr = C.run(["echo", "$HOME"])
        self.assertEqual(cr.stdout, b"$HOME\n")

    def test_env_override(self):
        cr = C.run(["sh", "-c", "echo $TESTVAR"], env={"TESTVAR": "val"})
        self.assertEqual(cr.stdout, b"val\n")


class TestRunRemote(unittest.TestCase):
    def test_default_reader_provides_rc(self):
        rr = C.run_remote(["echo", "hello"])
        self.assertIsInstance(rr, RemoteResult)
        self.assertEqual(rr.remote_rc, 0)
        self.assertTrue(rr.remote_available)

    def test_explicit_unavailable_reader(self):
        rr = C.run_remote(["echo", "hello"], reader=C.UnavailableReader())
        self.assertIsNone(rr.remote_rc)
        self.assertFalse(rr.remote_available)

    def test_custom_reader_provides_rc(self):
        class FixedReader:
            def extract_rc(self, transport):
                return 0
        rr = C.run_remote(["echo", "hi"], reader=FixedReader())
        self.assertEqual(rr.remote_rc, 0)
        self.assertTrue(rr.remote_available)

    def test_reader_returns_nonzero(self):
        class FailReader:
            def extract_rc(self, transport):
                return 1
        rr = C.run_remote(["sh", "-c", "exit 1"], reader=FailReader())
        self.assertEqual(rr.remote_rc, 1)

    def test_reader_returns_none_on_failure(self):
        class SelectiveReader:
            def extract_rc(self, transport):
                if transport.returncode != 0:
                    return None
                return transport.returncode
        rr = C.run_remote(["sh", "-c", "exit 1"], reader=SelectiveReader())
        self.assertIsNone(rr.remote_rc)

    def test_transport_still_captured(self):
        rr = C.run_remote(["echo", "payload"])
        self.assertEqual(rr.transport.stdout, b"payload\n")
        self.assertEqual(rr.transport.returncode, 0)


def _transport(rc=0, stdout=b"", stderr=b"", timed_out=False):
    return CommandResult(
        argv=["adb", "-s", "emu-5554", "shell", "exit", str(rc)],
        start_utc="2026-08-06T02:45:00Z",
        end_utc="2026-08-06T02:45:01Z",
        returncode=rc, stdout=stdout, stderr=stderr, timed_out=timed_out,
    )


class TestAdbRemoteStatusReader(unittest.TestCase):
    """Reproduces probe-observed adb shell_v2 behavior (no live adb)."""

    def test_remote_nonzero_7(self):
        self.assertEqual(C.AdbRemoteStatusReader().extract_rc(_transport(rc=7)), 7)

    def test_remote_nonzero_5(self):
        self.assertEqual(C.AdbRemoteStatusReader().extract_rc(_transport(rc=5)), 5)

    def test_remote_nonzero_3(self):
        self.assertEqual(C.AdbRemoteStatusReader().extract_rc(_transport(rc=3)), 3)

    def test_remote_nonzero_2(self):
        self.assertEqual(C.AdbRemoteStatusReader().extract_rc(_transport(rc=2)), 2)

    def test_remote_exit_1_empty_stderr(self):
        rc = C.AdbRemoteStatusReader().extract_rc(_transport(rc=1, stderr=b""))
        self.assertEqual(rc, 1)

    def test_remote_exit_1_with_stderr_fails_closed(self):
        stderr_msg = bytes([0x6d, 0x73, 0x67, 0x0a])
        rc = C.AdbRemoteStatusReader().extract_rc(
            _transport(rc=1, stderr=stderr_msg))
        self.assertIsNone(rc)

    def test_transport_failure_device_not_found(self):
        rc = C.AdbRemoteStatusReader().extract_rc(_transport(
            rc=1, stderr=b"adb: device 'bogus-serial' not found"))
        self.assertIsNone(rc)

    def test_transport_failure_no_devices(self):
        rc = C.AdbRemoteStatusReader().extract_rc(_transport(
            rc=1, stderr=b"adb: no devices/emulators found"))
        self.assertIsNone(rc)

    def test_remote_success_empty_stdout(self):
        rc = C.AdbRemoteStatusReader().extract_rc(_transport(rc=0, stdout=b""))
        self.assertEqual(rc, 0)

    def test_remote_success_no_trailing_newline(self):
        rc = C.AdbRemoteStatusReader().extract_rc(
            _transport(rc=0, stdout=b"no-newline"))
        self.assertEqual(rc, 0)

    def test_sentinel_payload_does_not_fuse(self):
        rc = C.AdbRemoteStatusReader().extract_rc(
            _transport(rc=3, stdout=b"__RC=0"))
        self.assertEqual(rc, 3)

    def test_stderr_only_exit_0(self):
        rc = C.AdbRemoteStatusReader().extract_rc(
            _transport(rc=0, stderr=b"error_msg"))
        self.assertEqual(rc, 0)

    def test_stderr_only_nonzero(self):
        rc = C.AdbRemoteStatusReader().extract_rc(
            _transport(rc=5, stderr=b"error_msg"))
        self.assertEqual(rc, 5)

    def test_timeout_returns_none(self):
        rc = C.AdbRemoteStatusReader().extract_rc(_transport(timed_out=True))
        self.assertIsNone(rc)

    def test_remote_success_with_stdout(self):
        rc = C.AdbRemoteStatusReader().extract_rc(
            _transport(rc=0, stdout=b"hello\n"))
        self.assertEqual(rc, 0)

    def test_mixed_stdout_stderr_nonzero(self):
        rc = C.AdbRemoteStatusReader().extract_rc(
            _transport(rc=2, stdout=b"out_line", stderr=b"err_line"))
        self.assertEqual(rc, 2)


class TestDigestFile(unittest.TestCase):
    def test_known_content(self):
        with tempfile.NamedTemporaryFile(delete=False) as f:
            f.write(b"hello world")
            f.flush()
            path = f.name
        try:
            d = C.digest_file(path)
            self.assertEqual(len(d), 64)
            self.assertEqual(
                d, "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9"
            )
        finally:
            os.unlink(path)

    def test_empty_file(self):
        with tempfile.NamedTemporaryFile(delete=False) as f:
            path = f.name
        try:
            d = C.digest_file(path)
            self.assertEqual(
                d, "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
            )
        finally:
            os.unlink(path)


class TestResolveTool(unittest.TestCase):
    def test_resolve_python(self):
        ti = C.resolve_tool("python3", version_args=["--version"])
        self.assertEqual(ti.name, "python3")
        self.assertTrue(ti.path)
        self.assertIn("Python", ti.version)
        self.assertTrue(ti.digest)

    def test_resolve_missing_tool(self):
        with self.assertRaises(FileNotFoundError):
            C.resolve_tool("nonexistent_tool_xyz_12345")

    def test_custom_version_args(self):
        ti = C.resolve_tool("sh", version_args=["-c", "echo custom-version"])
        self.assertIn("custom-version", ti.version)

    def test_version_from_stderr(self):
        ti = C.resolve_tool("sh", version_args=["-c", "echo v1.0 >&2"])
        self.assertIn("v1.0", ti.version)

    def test_no_version_output_raises(self):
        with self.assertRaises(RuntimeError):
            C.resolve_tool("sh", version_args=["-c", "true"])


class TestNoShellStrings(unittest.TestCase):
    def test_pipe_not_interpreted(self):
        cr = C.run(["echo", "foo | bar"])
        self.assertEqual(cr.stdout, b"foo | bar\n")

    def test_redirect_not_interpreted(self):
        cr = C.run(["echo", "> /dev/null"])
        self.assertEqual(cr.stdout, b"> /dev/null\n")


if __name__ == "__main__":
    unittest.main()
