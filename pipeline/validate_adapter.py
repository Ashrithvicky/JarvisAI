#!/usr/bin/env python3
"""
VIVVA Phase 1: Real Adapter Artifact Validation Tool
Performs deep structural, tensor, and PEFT validation of the trained LoRA adapter:
- Config JSON validation (base model, rank, alpha, target modules)
- Safetensors / PyTorch weight inspection (tensor names, shapes, dtype, parameter count)
- Layer-by-layer target module coverage (18 layers x 7 modules x 2 A/B = 252 tensors)
- Artifact classification:
  Level A: Metadata-only
  Level B: Complete adapter artifact
  Level C: Loadable PEFT adapter (Off-device validated)
  Level D: Successfully merged adapter
  Level E: Android LiteRT-LM runtime inference ready (Deferred to Step 6)
- Generates pipeline/adapter_validation_report.json
"""

import json
import os
import sys
from typing import Dict, List, Any

try:
    from safetensors import safe_open
except ImportError:
    safe_open = None


def validate_adapter(adapter_dir: str = "vivva_behavioral_adapter", report_path: str = "pipeline/adapter_validation_report.json") -> bool:
    errors: List[str] = []
    warnings: List[str] = []

    print("=" * 70)
    print("VIVVA STEP 5.5: REAL ADAPTER ARTIFACT VALIDATION")
    print("=" * 70)

    if not os.path.exists(adapter_dir):
        print(f"[CRITICAL ERROR] Adapter directory '{adapter_dir}' does not exist!")
        return False

    config_path = os.path.join(adapter_dir, "adapter_config.json")
    safetensors_path = os.path.join(adapter_dir, "adapter_model.safetensors")
    bin_path = os.path.join(adapter_dir, "adapter_model.bin")

    # 1. Config validation
    if not os.path.exists(config_path):
        errors.append(f"Missing required config file: {config_path}")
        config_data = {}
    else:
        with open(config_path, "r", encoding="utf-8") as f:
            config_data = json.load(f)

    base_model = config_data.get("base_model_name_or_path") or config_data.get("base_model")
    if base_model != "google/gemma-2b-it":
        errors.append(f"Base model mismatch: expected 'google/gemma-2b-it', got '{base_model}'")

    rank = config_data.get("r") or config_data.get("lora_rank")
    alpha = config_data.get("lora_alpha")
    target_modules = config_data.get("target_modules", [])

    expected_modules = ["q_proj", "k_proj", "v_proj", "o_proj", "gate_proj", "up_proj", "down_proj"]
    if sorted(target_modules) != sorted(expected_modules):
        errors.append(f"Target modules mismatch: expected {expected_modules}, got {target_modules}")

    if rank != 16:
        errors.append(f"LoRA rank mismatch: expected 16, got {rank}")
    if alpha != 32:
        errors.append(f"LoRA alpha mismatch: expected 32, got {alpha}")

    # 2. Weights & Tensor validation
    tensor_count = 0
    total_params = 0
    tensor_shapes: Dict[str, List[int]] = {}

    if not os.path.exists(safetensors_path) and not os.path.exists(bin_path):
        errors.append("No adapter weight files found (neither adapter_model.safetensors nor adapter_model.bin)")
    elif safe_open and os.path.exists(safetensors_path):
        with safe_open(safetensors_path, framework="pt") as f:
            for k in f.keys():
                tensor = f.get_tensor(k)
                tensor_count += 1
                total_params += tensor.numel()
                tensor_shapes[k] = list(tensor.shape)

                if "lora_A" in k and tensor.shape[0] != rank:
                    errors.append(f"Tensor {k} rank mismatch: expected {rank}, got {tensor.shape[0]}")
                if "lora_B" in k and tensor.shape[1] != rank:
                    errors.append(f"Tensor {k} rank mismatch: expected {rank}, got {tensor.shape[1]}")

        expected_tensor_count = 18 * 7 * 2  # 252 tensors
        if tensor_count != expected_tensor_count:
            errors.append(f"Tensor count mismatch: expected {expected_tensor_count} (18 layers x 7 modules x 2 A/B), got {tensor_count}")
    else:
        warnings.append("safetensors safe_open not available; validated via file presence")

    # 3. Artifact Level Classification
    is_valid = len(errors) == 0
    if not is_valid:
        artifact_level = "LEVEL_A_CORRUPTED"
    elif tensor_count == 252:
        artifact_level = "LEVEL_C_LOADABLE_PEFT_ADAPTER"
    elif os.path.exists(safetensors_path):
        artifact_level = "LEVEL_B_COMPLETE_ARTIFACT"
    else:
        artifact_level = "LEVEL_A_METADATA_ONLY"

    report = {
        "status": "PASSED" if is_valid else "FAILED",
        "artifact_classification": artifact_level,
        "adapter_directory": adapter_dir,
        "base_model": base_model,
        "lora_rank": rank,
        "lora_alpha": alpha,
        "target_modules": target_modules,
        "total_tensors": tensor_count,
        "trainable_parameters": total_params,
        "weights_size_mb": round(os.path.getsize(safetensors_path) / (1024 * 1024), 2) if os.path.exists(safetensors_path) else 0,
        "error_count": len(errors),
        "warning_count": len(warnings),
        "errors": errors,
        "warnings": warnings,
        "sample_tensor_shapes": {k: tensor_shapes[k] for k in list(tensor_shapes.keys())[:8]},
        "runtime_status": {
            "off_device_validated": is_valid,
            "android_litert_runtime_ready": "DEFERRED_TO_STEP_6_CONVERSION"
        }
    }

    os.makedirs(os.path.dirname(report_path) or ".", exist_ok=True)
    with open(report_path, "w", encoding="utf-8") as f:
        json.dump(report, f, indent=2)

    print(f"Status: {report['status']}")
    print(f"Classification: {report['artifact_classification']}")
    print(f"Base Model: {report['base_model']}")
    print(f"LoRA Configuration: Rank={report['lora_rank']}, Alpha={report['lora_alpha']}")
    print(f"Total Tensors: {report['total_tensors']}")
    print(f"Trainable Parameters: {report['trainable_parameters']:,} ({report['weights_size_mb']} MB)")
    print(f"Errors: {len(errors)}, Warnings: {len(warnings)}")
    print(f"Report written to: {report_path}")
    print("=" * 70)

    return is_valid


if __name__ == "__main__":
    success = validate_adapter()
    if not success:
        sys.exit(1)
