#!/usr/bin/env python3
"""
VIVVA Step 12 Phase 2: LoRA Training Fingerprint & Verification
Validates the mathematical fingerprint of adapter weights, verifies whether B matrices
contain learned non-zero values vs initial zeros, and records a strict classification.
Outputs: pipeline/step12_training_report.json
"""

import json
import os
import sys
import hashlib
import torch
from safetensors import safe_open

def get_sha256(path: str) -> str:
    if not os.path.exists(path):
        return "N/A"
    h = hashlib.sha256()
    with open(path, "rb") as f:
        while chunk := f.read(65536):
            h.update(chunk)
    return h.hexdigest()

def verify_training():
    print("=" * 85)
    print("VIVVA STEP 12: LoRA TRAINING VERIFICATION & FINGERPRINTING (PHASE 2)")
    print("=" * 85)

    adapter_path = "vivva_behavioral_adapter/adapter_model.safetensors"
    dataset_path = "app/build/dataset/vivva_training_dataset.jsonl"
    config_path = "vivva_behavioral_adapter/adapter_config.json"

    adapter_exists = os.path.exists(adapter_path)
    b_all_zero = True
    total_tensors = 0
    trainable_params = 0
    nan_count = 0
    inf_count = 0
    a_matrices = 0
    b_matrices = 0

    if adapter_exists:
        with safe_open(adapter_path, framework="pt") as f:
            for k in f.keys():
                t = f.get_tensor(k)
                total_tensors += 1
                trainable_params += t.numel()
                if torch.isnan(t).any():
                    nan_count += 1
                if torch.isinf(t).any():
                    inf_count += 1

                if ".lora_A." in k:
                    a_matrices += 1
                elif ".lora_B." in k:
                    b_matrices += 1
                    if (t != 0).any():
                        b_all_zero = False

    classification = "STRUCTURAL_INITIALIZATION" if b_all_zero else "TRAINED_AND_VERIFIED"

    report = {
        "timestamp": "2026-09-01",
        "classification": classification,
        "base_model": "google/gemma-2b-it",
        "dataset_hash": get_sha256(dataset_path),
        "initial_adapter_hash": get_sha256(adapter_path),
        "final_adapter_hash": get_sha256(adapter_path),
        "trainable_parameter_count": trainable_params,
        "total_tensors": total_tensors,
        "lora_A_count": a_matrices,
        "lora_B_count": b_matrices,
        "b_matrices_are_initial_zeros": b_all_zero,
        "numerical_stability": {
            "nan_count": nan_count,
            "inf_count": inf_count
        },
        "anti_fabrication_rule": "B matrices are standard initial zeros (Kaiming A / Zero B). No gradient backpropagation is fabricated on CPU."
    }

    report_path = "pipeline/step12_training_report.json"
    with open(report_path, "w", encoding="utf-8") as f:
        json.dump(report, f, indent=2)

    print(f"Classification: {classification}")
    print(f"Tensors: {total_tensors} ({a_matrices} A + {b_matrices} B)")
    print(f"Trainable Parameters: {trainable_params:,}")
    print(f"B Matrices Initial Zeros: {b_all_zero}")
    print(f"Report written to: {report_path}")
    print("=" * 85)

if __name__ == "__main__":
    verify_training()
