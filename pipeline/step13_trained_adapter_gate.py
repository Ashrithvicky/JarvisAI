#!/usr/bin/env python3
"""
VIVVA Step 13 Phase B: Hard Trained Adapter Gate
Strictly verifies whether adapter weights contain learned non-zero values or if they are initial zeros.
Rejects un-trained adapters from production merging.
Outputs: pipeline/step13_adapter_gate_report.json
"""

import json
import os
import sys
import hashlib
import torch
from safetensors import safe_open

def evaluate_adapter_gate(adapter_dir: str = "vivva_behavioral_adapter"):
    print("=" * 85)
    print("VIVVA STEP 13: HARD TRAINED ADAPTER GATE (PHASE B)")
    print("=" * 85)

    weights_path = os.path.join(adapter_dir, "adapter_model.safetensors")
    manifest_path = os.path.join(adapter_dir, "training_manifest.json")

    weights_exist = os.path.exists(weights_path)
    manifest_exists = os.path.exists(manifest_path)

    b_all_zero = True
    total_elements = 0
    tensors_count = 0
    nan_count = 0
    inf_count = 0

    if weights_exist:
        with safe_open(weights_path, framework="pt") as f:
            for k in f.keys():
                t = f.get_tensor(k)
                tensors_count += 1
                total_elements += t.numel()

                if torch.isnan(t).any():
                    nan_count += 1
                if torch.isinf(t).any():
                    inf_count += 1

                if ".lora_B." in k and (t != 0).any():
                    b_all_zero = False

    # Production Gate Decision
    if not weights_exist:
        gate_decision = "REJECTED_MISSING_ADAPTER"
        status = "MISSING"
    elif b_all_zero:
        gate_decision = "REJECTED_STRUCTURAL_INITIALIZATION_ONLY"
        status = "STRUCTURAL_INITIALIZATION"
    else:
        gate_decision = "APPROVED_TRAINED_WEIGHTS_VERIFIED"
        status = "TRAINED_AND_VERIFIED"

    report = {
        "timestamp": "2026-09-01",
        "gate_decision": gate_decision,
        "status": status,
        "can_proceed_to_prod_merge": (gate_decision == "APPROVED_TRAINED_WEIGHTS_VERIFIED"),
        "adapter_file": weights_path,
        "adapter_size_mb": round(os.path.getsize(weights_path) / (1024 * 1024), 2) if weights_exist else 0,
        "tensors_count": tensors_count,
        "trainable_parameters": total_elements,
        "b_matrices_are_initial_zeros": b_all_zero,
        "training_manifest_present": manifest_exists,
        "numerical_stability": {
            "nan_count": nan_count,
            "inf_count": inf_count
        },
        "anti_fabrication_rule": "B matrices are checked directly from safetensors data. All zeros = REJECTED from production merge."
    }

    report_path = "pipeline/step13_adapter_gate_report.json"
    with open(report_path, "w", encoding="utf-8") as f:
        json.dump(report, f, indent=2)

    print(f"Gate Decision: {gate_decision}")
    print(f"Status: {status}")
    print(f"Can Proceed to Production Merge: {report['can_proceed_to_prod_merge']}")
    print(f"Tensors: {tensors_count} ({total_elements:,} parameters)")
    print(f"B Matrices Initial Zeros: {b_all_zero}")
    print(f"Report written to: {report_path}")
    print("=" * 85)

if __name__ == "__main__":
    evaluate_adapter_gate()
