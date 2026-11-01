#!/usr/bin/env python3
"""
VIVVA Step 12 Phase 3: Production Merge Gate
Enforces strict rule: Structural initialization must not be silently merged and labeled as production VIVVA model.
Outputs: pipeline/step12_merge_gate_report.json
"""

import json
import os
import sys

def evaluate_merge_gate():
    print("=" * 85)
    print("VIVVA STEP 12: MERGE HARD GATE EVALUATION (PHASE 3)")
    print("=" * 85)

    training_report_path = "pipeline/step12_training_report.json"
    with open(training_report_path, "r", encoding="utf-8") as f:
        training_report = json.load(f)

    is_trained = training_report.get("classification") == "TRAINED_AND_VERIFIED"
    merged_path = "vivva_merged_model/model.safetensors"
    merged_exists = os.path.exists(merged_path)

    gate_result = {
        "timestamp": "2026-09-01",
        "lora_status": training_report.get("classification"),
        "production_merge_allowed": is_trained,
        "current_merged_model_state": "ENGINEERING_STANDALONE_CHECKPOINT" if not is_trained else "TRAINED_PRODUCTION_CHECKPOINT",
        "merged_checkpoint_exists": merged_exists,
        "gate_action": (
            "HALTED_PRODUCTION_MERGE: Retaining offline fused engineering checkpoint. "
            "Production LoRA merge will trigger automatically upon completion of GPU training."
            if not is_trained else "PROCEED_WITH_PRODUCTION_MERGE"
        ),
        "anti_fabrication_rule": "The current merged model is an engineering standalone float16 checkpoint, not a post-training gradient merge."
    }

    report_path = "pipeline/step12_merge_gate_report.json"
    with open(report_path, "w", encoding="utf-8") as f:
        json.dump(gate_result, f, indent=2)

    print(f"LoRA Status: {gate_result['lora_status']}")
    print(f"Production Merge Allowed: {gate_result['production_merge_allowed']}")
    print(f"Gate Action: {gate_result['gate_action']}")
    print(f"Report written to: {report_path}")
    print("=" * 85)

if __name__ == "__main__":
    evaluate_merge_gate()
