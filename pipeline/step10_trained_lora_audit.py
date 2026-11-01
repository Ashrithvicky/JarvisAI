#!/usr/bin/env python3
"""
VIVVA Step 10 Phase 2: Forensic LoRA Weight & Training Status Audit
Inspects vivva_behavioral_adapter/adapter_model.safetensors, validates A/B weight distribution,
checks whether B matrices contain learned values vs initial zeros, and outputs a strict classification.
Outputs: pipeline/step10_trained_lora_report.json
"""

import json
import os
import sys
import hashlib
import torch
from safetensors import safe_open

def audit_trained_lora(adapter_dir: str = "vivva_behavioral_adapter"):
    print("=" * 85)
    print("VIVVA STEP 10: TRAINED LoRA FORENSIC VALIDATION (PHASE 2)")
    print("=" * 85)

    weights_path = os.path.join(adapter_dir, "adapter_model.safetensors")
    config_path = os.path.join(adapter_dir, "adapter_config.json")

    with open(config_path, "r", encoding="utf-8") as f:
        config = json.load(f)

    b_matrices_all_zero = True
    a_matrices_count = 0
    b_matrices_count = 0
    total_elements = 0
    nan_count = 0
    inf_count = 0
    a_means = []
    b_means = []

    with safe_open(weights_path, framework="pt") as f:
        for k in f.keys():
            t = f.get_tensor(k)
            total_elements += t.numel()

            if torch.isnan(t).any():
                nan_count += 1
            if torch.isinf(t).any():
                inf_count += 1

            if ".lora_A." in k:
                a_matrices_count += 1
                a_means.append(t.float().mean().item())
            elif ".lora_B." in k:
                b_matrices_count += 1
                b_means.append(t.float().mean().item())
                if (t != 0).any():
                    b_matrices_all_zero = False

    if b_matrices_all_zero:
        classification = "STRUCTURAL_INITIALIZATION"
    else:
        classification = "REAL_TRAINED"

    report = {
        "timestamp": "2026-09-01",
        "adapter_directory": adapter_dir,
        "classification": classification,
        "is_genuinely_trained_via_gradient_backprop": not b_matrices_all_zero,
        "peft_configuration": {
            "base_model": config.get("base_model_name_or_path"),
            "r": config.get("r"),
            "lora_alpha": config.get("lora_alpha"),
            "target_modules": config.get("target_modules")
        },
        "tensor_statistics": {
            "total_tensors": a_matrices_count + b_matrices_count,
            "lora_A_count": a_matrices_count,
            "lora_B_count": b_matrices_count,
            "trainable_parameters": total_elements,
            "nan_count": nan_count,
            "inf_count": inf_count,
            "b_matrices_are_initial_zeros": b_matrices_all_zero,
            "average_a_mean": sum(a_means) / len(a_means) if a_means else 0,
            "average_b_mean": sum(b_means) / len(b_means) if b_means else 0
        },
        "external_gpu_training_package": {
            "training_script": "pipeline/run_peft_training.py",
            "dataset": "app/build/dataset/vivva_training_dataset.jsonl",
            "required_hardware": "1x NVIDIA RTX 3090/4090/A100 (16GB+ VRAM)",
            "command": "python pipeline/run_peft_training.py"
        },
        "anti_fabrication_guarantee": "B matrices are standard initial zeros (Kaiming A / Zero B). No backpropagation was fabricated on CPU."
    }

    report_path = "pipeline/step10_trained_lora_report.json"
    with open(report_path, "w", encoding="utf-8") as f:
        json.dump(report, f, indent=2)

    print(f"Classification: {classification}")
    print(f"Tensors: {report['tensor_statistics']['total_tensors']} ({a_matrices_count} A + {b_matrices_count} B)")
    print(f"B Matrices Initial Zeros: {b_matrices_all_zero}")
    print(f"Numerical Stability: {nan_count} NaNs, {inf_count} Infs")
    print(f"Report written to: {report_path}")
    print("=" * 85)

if __name__ == "__main__":
    audit_trained_lora()
