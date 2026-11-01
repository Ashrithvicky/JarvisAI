#!/usr/bin/env python3
"""
VIVVA Gemma 4 E2B LoRA/QLoRA Weight Fusion & Merge Engine
Merges trained PEFT LoRA adapter weights back into google/gemma-4-E2B-it base weights
using PeftModel.merge_and_unload() for official LiteRT-LM conversion.
"""

import argparse
import json
import os
import sys
import time
import torch


def parse_args():
    parser = argparse.ArgumentParser(description="VIVVA Gemma 4 E2B Weight Fusion Engine")
    parser.add_argument("--base_model", type=str, default="google/gemma-4-E2B-it")
    parser.add_argument("--adapter_dir", type=str, default="vivva_gemma4_qlora_adapter")
    parser.add_argument("--output_dir", type=str, default="vivva_merged_gemma4_model")
    parser.add_argument("--hf_token", type=str, default=None)
    return parser.parse_args()


def merge_and_save(args):
    from transformers import AutoTokenizer, AutoModelForCausalLM
    from peft import PeftModel

    print("=" * 80)
    print("VIVVA GEMMA 4 E2B: PEFT WEIGHT FUSION & MERGE RUNNER")
    print("=" * 80)
    print(f"Base Model: {args.base_model}")
    print(f"Adapter Directory: {args.adapter_dir}")
    print(f"Output Merged Directory: {args.output_dir}")

    if not os.path.exists(args.adapter_dir):
        print(f"[ERROR] Adapter directory not found: {args.adapter_dir}")
        sys.exit(1)

    hf_token = args.hf_token or os.environ.get("HF_TOKEN")
    compute_dtype = torch.float16

    print("\n1. Loading Base Model in Float16...")
    base_model = AutoModelForCausalLM.from_pretrained(
        args.base_model,
        torch_dtype=compute_dtype,
        device_map="auto",
        token=hf_token
    )

    print("2. Loading Tokenizer...")
    tokenizer = AutoTokenizer.from_pretrained(args.base_model, token=hf_token)

    print("3. Loading Trained PEFT LoRA Adapter...")
    peft_model = PeftModel.from_pretrained(base_model, args.adapter_dir)

    print("4. Executing PeftModel.merge_and_unload()...")
    merged_model = peft_model.merge_and_unload()

    print(f"5. Saving Merged Full Checkpoint to {args.output_dir}...")
    os.makedirs(args.output_dir, exist_ok=True)
    merged_model.save_pretrained(args.output_dir)
    tokenizer.save_pretrained(args.output_dir)

    merge_manifest = {
        "base_model": args.base_model,
        "adapter_dir": args.adapter_dir,
        "output_dir": args.output_dir,
        "timestamp": time.strftime("%Y-%m-%d %H:%M:%S"),
        "status": "MERGE_SUCCESSFUL"
    }
    with open(os.path.join(args.output_dir, "merge_manifest.json"), "w", encoding="utf-8") as f:
        json.dump(merge_manifest, f, indent=2)

    print(f"\n[SUCCESS] Merged model checkpoint saved to: {args.output_dir}/")
    print("Ready for LiteRT-LM conversion.")
    print("=" * 80)


def main():
    args = parse_args()
    merge_and_save(args)


if __name__ == "__main__":
    main()
