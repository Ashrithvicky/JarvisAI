#!/usr/bin/env python3
"""
VIVVA Step 12 Phase 1: GPU Execution Discovery & Training Preflight
Probes local virtualization, WSL, Docker, and hardware interfaces.
Verifies all prerequisites for single-command training execution.
Outputs: pipeline/step12_preflight_report.json
"""

import json
import os
import shutil
import subprocess
import sys
import torch

def run_preflight():
    print("=" * 85)
    print("VIVVA STEP 12: GPU EXECUTION DISCOVERY & TRAINING PREFLIGHT (PHASE 1)")
    print("=" * 85)

    # Hardware check
    cuda_avail = torch.cuda.is_available()
    device_name = torch.cuda.get_device_name(0) if cuda_avail else "CPU_ONLY"
    vram_bytes = torch.cuda.get_device_properties(0).total_memory if cuda_avail else 0
    vram_gb = round(vram_bytes / (1024 ** 3), 2)

    # Virtualization check
    wsl_avail = shutil.which("wsl") is not None
    docker_avail = shutil.which("docker") is not None

    # Dataset check
    dataset_path = "app/build/dataset/vivva_training_dataset.jsonl"
    dataset_exists = os.path.exists(dataset_path)
    sample_count = 0
    if dataset_exists:
        with open(dataset_path, "r", encoding="utf-8") as f:
            sample_count = sum(1 for line in f if line.strip())

    report = {
        "timestamp": "2026-09-01",
        "cuda_available": cuda_avail,
        "device_name": device_name,
        "vram_gb": vram_gb,
        "virtualization": {
            "wsl_installed": wsl_avail,
            "docker_installed": docker_avail
        },
        "dataset": {
            "path": dataset_path,
            "exists": dataset_exists,
            "sample_count": sample_count
        },
        "training_preflight": {
            "script": "pipeline/run_peft_training.py",
            "base_model": "google/gemma-2b-it",
            "rank": 16,
            "alpha": 32,
            "target_modules": ["q_proj", "k_proj", "v_proj", "o_proj", "gate_proj", "up_proj", "down_proj"],
            "status": "BLOCKED_BY_HARDWARE" if not cuda_avail else "READY_TO_TRAIN"
        },
        "one_command_gpu_execution": "python pipeline/run_peft_training.py --base_model google/gemma-2b-it"
    }

    report_path = "pipeline/step12_preflight_report.json"
    with open(report_path, "w", encoding="utf-8") as f:
        json.dump(report, f, indent=2)

    print(f"CUDA Hardware: {report['training_preflight']['status']} ({device_name})")
    print(f"Dataset Verified: {sample_count} samples ({dataset_path})")
    print(f"One-Command Training: {report['one_command_gpu_execution']}")
    print(f"Report written to: {report_path}")
    print("=" * 85)

if __name__ == "__main__":
    run_preflight()
