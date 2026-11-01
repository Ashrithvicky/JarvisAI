#!/usr/bin/env python3
"""
VIVVA Step 10 Phase 0: Exhaustive Forensic Environment Discovery
Probes Python packages, CUDA hardware, MediaPipe GenAI converter binaries,
LiteRT tooling, FlatBuffers, Android SDK, and connected physical devices.
Produces: pipeline/step10_environment_report.json
"""

import json
import os
import shutil
import subprocess
import sys
import torch

def discover_environment():
    print("=" * 85)
    print("VIVVA STEP 10: FORENSIC ENVIRONMENT DISCOVERY (PHASE 0)")
    print("=" * 85)

    # 1. CUDA & GPU Hardware Discovery
    cuda_available = torch.cuda.is_available()
    device_count = torch.cuda.device_count() if cuda_available else 0
    device_name = torch.cuda.get_device_name(0) if cuda_available else "CPU_ONLY"
    vram_bytes = torch.cuda.get_device_properties(0).total_memory if cuda_available else 0
    vram_gb = round(vram_bytes / (1024 ** 3), 2) if cuda_available else 0.0

    # 2. MediaPipe & LiteRT Tooling Discovery
    mediapipe_installed = False
    mediapipe_version = "NOT_INSTALLED"
    genai_converter_available = False
    genai_error = None

    try:
        import mediapipe as mp
        mediapipe_installed = True
        mediapipe_version = getattr(mp, "__version__", "UNKNOWN")
        try:
            from mediapipe.tasks.python.genai import converter
            genai_converter_available = True
        except Exception as e:
            genai_error = str(e)
    except Exception as e:
        genai_error = str(e)

    # 3. Executables in PATH
    flatc_bin = shutil.which("flatc")
    tflite_convert_bin = shutil.which("tflite_convert")
    adb_bin = shutil.which("adb")
    if not adb_bin:
        local_adb = os.path.expandvars(r"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe")
        if os.path.exists(local_adb):
            adb_bin = local_adb

    # 4. ADB Device Discovery
    adb_devices = []
    if adb_bin and os.path.exists(adb_bin):
        try:
            res = subprocess.run([adb_bin, "devices"], capture_output=True, text=True, timeout=10)
            lines = res.stdout.strip().splitlines()
            for line in lines[1:]:
                parts = line.split()
                if len(parts) >= 2 and parts[1] == "device":
                    adb_devices.append(parts[0])
        except Exception:
            pass

    # 5. Core ML Package Discovery
    ml_packages = {}
    for pkg in ["transformers", "peft", "datasets", "trl", "bitsandbytes", "accelerate", "safetensors", "torch"]:
        try:
            mod = __import__(pkg)
            ml_packages[pkg] = {
                "available": True,
                "version": getattr(mod, "__version__", "UNKNOWN")
            }
        except ImportError:
            ml_packages[pkg] = {
                "available": False,
                "version": "NOT_INSTALLED"
            }

    report = {
        "timestamp": "2026-09-01",
        "python_executable": sys.executable,
        "python_version": sys.version,
        "hardware": {
            "cuda_available": cuda_available,
            "device_count": device_count,
            "device_name": device_name,
            "vram_gb": vram_gb,
            "status": "AVAILABLE" if cuda_available else "BLOCKED_BY_HARDWARE",
            "minimum_required_for_gemma_2b": "1x NVIDIA GPU with >= 16GB VRAM (RTX 3090/4090/A100)"
        },
        "litert_tooling": {
            "mediapipe_installed": mediapipe_installed,
            "mediapipe_version": mediapipe_version,
            "genai_converter_available": genai_converter_available,
            "genai_error": genai_error,
            "flatc_binary": flatc_bin if flatc_bin else "NOT_FOUND",
            "tflite_convert_binary": tflite_convert_bin if tflite_convert_bin else "NOT_FOUND",
            "status": "AVAILABLE" if genai_converter_available else "BLOCKED_BY_EXTERNAL_TOOL"
        },
        "android_tooling": {
            "adb_binary": adb_bin if adb_bin else "NOT_FOUND",
            "connected_devices": adb_devices,
            "status": "AVAILABLE" if adb_devices else "NO_CONNECTED_DEVICE"
        },
        "ml_packages": ml_packages
    }

    report_path = "pipeline/step10_environment_report.json"
    with open(report_path, "w", encoding="utf-8") as f:
        json.dump(report, f, indent=2)

    print(f"CUDA Hardware: {report['hardware']['status']} ({device_name})")
    print(f"LiteRT Tooling: {report['litert_tooling']['status']}")
    print(f"Connected Devices: {adb_devices if adb_devices else 'None'}")
    print(f"Transformers: {ml_packages['transformers']['version']}")
    print(f"PEFT: {ml_packages['peft']['version']}")
    print(f"Safetensors: {ml_packages['safetensors']['version']}")
    print(f"Report written to: {report_path}")
    print("=" * 85)

if __name__ == "__main__":
    discover_environment()
