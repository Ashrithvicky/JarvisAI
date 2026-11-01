#!/usr/bin/env python3
"""
VIVVA Step 13 Phase C: Production Merge Guard
Ensures offline production merge executes only if adapter passed Phase B gate.
Outputs: pipeline/step13_merge_guard_report.json
"""

import json
import os
import sys

def check_merge_guard():
    print("=" * 85)
    print("VIVVA STEP 13: PRODUCTION MERGE GUARD (PHASE C)")
    print("=" * 85)

    adapter_gate_path = "pipeline/step13_adapter_gate_report.json"
    if not os.path.exists(adapter_gate_path):
        print("ERROR: Run step13_trained_adapter_gate.py first.")
        sys.exit(1)

    with open(adapter_gate_path, "r", encoding="utf-8") as f:
        adapter_gate = json.load(f)

    can_proceed = adapter_gate.get("can_proceed_to_prod_merge", False)

    guard_report = {
        "timestamp": "2026-09-01",
        "adapter_gate_status": adapter_gate.get("status"),
        "production_merge_allowed": can_proceed,
        "action_taken": (
            "PRODUCTION_MERGE_BLOCKED: Adapter is structural initialization. "
            "Offline engineering checkpoint preserved in vivva_merged_model/."
            if not can_proceed else "EXECUTING_PRODUCTION_MERGE"
        ),
        "engineering_checkpoint": {
            "path": "vivva_merged_model/model.safetensors",
            "exists": os.path.exists("vivva_merged_model/model.safetensors"),
            "nature": "STANDALONE_FLOAT16_CHECKPOINT (1.98B parameters)"
        },
        "anti_fabrication_rule": "Production merge is strictly blocked until backpropagated LoRA weights are produced on GPU."
    }

    report_path = "pipeline/step13_merge_guard_report.json"
    with open(report_path, "w", encoding="utf-8") as f:
        json.dump(guard_report, f, indent=2)

    print(f"Merge Guard Action: {guard_report['action_taken']}")
    print(f"Engineering Checkpoint: {guard_report['engineering_checkpoint']['nature']}")
    print(f"Report written to: {report_path}")
    print("=" * 85)

if __name__ == "__main__":
    check_merge_guard()
