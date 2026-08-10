#!/usr/bin/env python3
"""Run fake capture CLI entry point using mock toolchain in child process."""

import os
import sys
import shutil
import subprocess

# Configure sys.path to run directly from repository root
workspace_root = os.path.abspath(
    os.path.join(
        os.path.dirname(__file__),
        "../../../../.."
    )
)
if workspace_root not in sys.path:
    sys.path.insert(0, workspace_root)


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

    # Logger for mock commands
    log_path = os.path.join(test_dir, "mock_commands.log")
    if os.path.exists(log_path):
        os.remove(log_path)

    # Isolated PATH and PYTHONPATH env (include python binary directory to resolve shebangs)
    isolated_path = bin_dir + os.pathsep + os.path.dirname(sys.executable)
    env = {
        "PATH": isolated_path,
        "PYTHONPATH": workspace_root,
        "MOCK_COMMANDS_LOG": log_path,
    }
    for k in ["SYSTEMROOT", "PATHEXT", "TMPDIR", "HOME", "USER"]:
        if k in os.environ:
            env[k] = os.environ[k]

    argv = [
        sys.executable,
        "-m", "android.scripts.m2_device.cli",
        "capture",
        "--evidence-root", evidence_root,
        "--repo-root", repo_root,
        "--apk-path", apk_path,
        "--apk-sha256", "mocksha256"
    ]

    print("Executing real CLI capture entry point with fake toolchain in child process...")
    res = subprocess.run(
        argv,
        env=env,
        capture_output=True,
        text=True
    )

    print("\n--- CLI Child Process Output ---")
    print(f"Exit Code: {res.returncode}")
    print("Stdout:")
    print(res.stdout)
    print("Stderr:")
    print(res.stderr)
    print("--------------------------------\n")

    # Read and check the fake argv ledger
    if os.path.exists(log_path):
        print("--- Mock Commands Ledger ---")
        with open(log_path) as f:
            for line in f:
                print(line.strip())
        print("----------------------------\n")

    # Assert expected AttributeError in child process stderr (true production failure)
    expected_error = "AttributeError: 'AdbHarness' object has no attribute 'capture_evidence'"
    if expected_error in res.stderr and res.returncode == 1:
        print("RED verification check: child failed successfully for the true production reason.")
        print(f"Child status: {res.returncode}, Wrapper status: {res.returncode}")
        # Clean up and exit with same status to preserve the real child nonzero status
        if os.path.exists(test_dir):
            shutil.rmtree(test_dir)
        sys.exit(res.returncode)
    else:
        print("RED verification check: FAILURE! Child did not exit as expected.")
        print(f"Child status: {res.returncode}, Wrapper status: 99")
        if os.path.exists(test_dir):
            shutil.rmtree(test_dir)
        sys.exit(99)


if __name__ == "__main__":
    main()
