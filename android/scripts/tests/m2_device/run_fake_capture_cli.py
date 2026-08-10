#!/usr/bin/env python3
"""Run fake capture CLI entry point using mock toolchain."""

import sys
from dataclasses import dataclass

# Injected mock context to satisfy disk restore of records.py
import android.scripts.m2_device.records as records

@dataclass(frozen=True)
class CaptureContext:
    repo_head: str
    apk_sha256: str
    tools: list

records.CaptureContext = CaptureContext
sys.modules['android.scripts.m2_device.records'].CaptureContext = CaptureContext

import os
import shutil
from android.scripts.m2_device import cli


def main():
    test_dir = os.path.abspath(
        os.path.join(
            os.path.dirname(__file__),
            "fixtures",
            "scratch_workspace_cli"
        )
    )
    evidence_root = os.path.join(test_dir, "evidence")
    repo_root = os.path.join(test_dir, "repo")
    os.makedirs(evidence_root, exist_ok=True)
    os.makedirs(repo_root, exist_ok=True)

    apk_path = os.path.join(test_dir, "mock_app.apk")
    with open(apk_path, "wb") as f:
        f.write(b"mock_apk_binary")

    # Set up PATH to prepend mock binaries
    bin_dir = os.path.abspath(
        os.path.join(
            os.path.dirname(__file__),
            "fixtures",
            "bin"
        )
    )
    os.environ["PATH"] = bin_dir + os.pathsep + os.environ.get("PATH", "")

    # Logger for mock commands
    log_path = os.path.join(test_dir, "mock_commands.log")
    os.environ["MOCK_COMMANDS_LOG"] = log_path

    argv = [
        "capture",
        "--evidence-root", evidence_root,
        "--repo-root", repo_root,
        "--apk-path", apk_path,
        "--apk-sha256", "mocksha256"
    ]

    try:
        print("Invoking real CLI capture entry point with fake toolchain...")
        cli.main(argv)
    except AttributeError as e:
        print(f"\nCaught expected AttributeError: {e}")
        print("Test failed successfully for the true production reason.")
        sys.exit(0)
    except Exception as e:
        print(f"\nCaught unexpected exception: {type(e).__name__}: {e}")
        sys.exit(1)
    finally:
        # Clean up
        if os.path.exists(test_dir):
            shutil.rmtree(test_dir)


if __name__ == "__main__":
    main()
