#!/usr/bin/env python3
"""
VIVVA Production LoRA SFT Training Pipeline (Gemma 2B)
Executes multi-epoch gradient backpropagation on Gemma 2B using PyTorch, Transformers, and PEFT.

Features:
- Validates CUDA hardware environment (requires NVIDIA GPU with >=8GB VRAM)
- Robust SFT dataset parser supporting user_message/ideal_response and chat formats
- Loss masking: Masks user prompt tokens with -100 so loss is computed strictly on model response
- Configures PEFT LoRA (r=16, alpha=32, target_modules=q,k,v,o,gate,up,down)
- Mixed-precision bfloat16/float16 gradient backpropagation
- Gradient checkpointing support for low-VRAM GPUs (8GB+)
- Real PyTorch training loop: forward pass -> loss -> backward pass -> clip_grad_norm -> optimizer step -> lr scheduler
- Verifies LoRA B matrices contain learned non-zero values (B != 0)
- Saves checkpoints and final trained adapter weights with full training_manifest.json
"""

import argparse
import json
import os
import random
import shutil
import sys
import time
import hashlib
from typing import Dict, List, Any


def get_file_sha256(path: str) -> str:
    if not os.path.exists(path):
        return "N/A"
    h = hashlib.sha256()
    with open(path, "rb") as f:
        while chunk := f.read(65536):
            h.update(chunk)
    return h.hexdigest()


def parse_args():
    parser = argparse.ArgumentParser(description="VIVVA Gemma 2B Behavioral LoRA Fine-Tuning")
    parser.add_argument("--dataset_path", type=str, default="app/build/dataset/vivva_training_dataset.jsonl")
    parser.add_argument("--base_model", type=str, default="google/gemma-2b-it")
    parser.add_argument("--output_dir", type=str, default="vivva_behavioral_adapter")
    parser.add_argument("--hf_token", type=str, default=None, help="Hugging Face access token (or env HF_TOKEN)")
    parser.add_argument("--lora_rank", type=int, default=16)
    parser.add_argument("--lora_alpha", type=int, default=32)
    parser.add_argument("--lora_dropout", type=float, default=0.05)
    parser.add_argument("--learning_rate", type=float, default=2e-4)
    parser.add_argument("--num_epochs", type=int, default=3)
    parser.add_argument("--micro_batch_size", type=int, default=2)
    parser.add_argument("--gradient_accumulation_steps", type=int, default=8)
    parser.add_argument("--max_seq_len", type=int, default=1024)
    parser.add_argument("--gradient_checkpointing", action="store_true", default=True, help="Enable gradient checkpointing to reduce VRAM")
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--preflight_only", action="store_true", help="Perform preflight and VRAM check only")
    return parser.parse_args()


def preflight_check(args):
    import torch

    print("=" * 85)
    print("VIVVA STEP 15: PRODUCTION TRAINING PREFLIGHT & HARDWARE AUDIT")
    print("=" * 85)
    print(f"Base Model: {args.base_model}")
    print(f"Dataset: {args.dataset_path}")
    print(f"Output Directory: {args.output_dir}")
    print(f"LoRA Configuration: Rank={args.lora_rank}, Alpha={args.lora_alpha}, Dropout={args.lora_dropout}")
    print(f"Batch Size: Micro={args.micro_batch_size}, GradAccum={args.gradient_accumulation_steps} (Effective Batch={args.micro_batch_size * args.gradient_accumulation_steps})")
    print(f"Gradient Checkpointing: {args.gradient_checkpointing}")

    cuda_available = torch.cuda.is_available()
    device_name = torch.cuda.get_device_name(0) if cuda_available else "CPU_ONLY"
    vram_bytes = torch.cuda.get_device_properties(0).total_memory if cuda_available else 0
    vram_gb = round(vram_bytes / (1024 ** 3), 2)

    # VRAM Derivation:
    # Base model (1.98B params x 2 bytes fp16) = 3.96 GB
    # LoRA params (19.61M params x 2 bytes fp16) = 0.04 GB
    # Optimizer states (AdamW on 19.61M params x 16 bytes) = 0.31 GB
    # KV Cache & Activations (seq_len 1024, micro_batch 2 with grad checkpointing) = ~2.4 GB
    # PyTorch CUDA context overhead = ~0.8 GB
    # Total Estimated Peak VRAM = ~7.51 GB
    min_vram_gb = 7.5
    hardware_ready = cuda_available and vram_gb >= min_vram_gb

    # Dataset validation
    dataset_exists = os.path.exists(args.dataset_path)
    sample_count = 0
    valid_samples = 0
    if dataset_exists:
        with open(args.dataset_path, "r", encoding="utf-8") as f:
            for line in f:
                if line.strip():
                    sample_count += 1
                    try:
                        obj = json.loads(line)
                        u = obj.get("user_message") or obj.get("instruction") or obj.get("prompt")
                        r = obj.get("ideal_response") or obj.get("response") or obj.get("target")
                        if u and r:
                            valid_samples += 1
                    except Exception:
                        pass

    # Disk check
    free_disk_bytes = shutil.disk_usage(".").free
    free_disk_gb = round(free_disk_bytes / (1024 ** 3), 2)

    preflight_result = {
        "timestamp": time.strftime("%Y-%m-%d %H:%M:%S"),
        "cuda_available": cuda_available,
        "device_name": device_name,
        "vram_gb": vram_gb,
        "minimum_vram_required_gb": min_vram_gb,
        "hardware_ready": hardware_ready,
        "dataset_exists": dataset_exists,
        "dataset_samples": sample_count,
        "valid_samples": valid_samples,
        "dataset_sha256": get_file_sha256(args.dataset_path),
        "free_disk_gb": free_disk_gb,
        "status": "READY_FOR_GPU_TRAINING" if hardware_ready else "BLOCKED_BY_HARDWARE"
    }

    print(f"CUDA Available: {cuda_available} ({device_name}, VRAM: {vram_gb} GB / {min_vram_gb} GB required)")
    print(f"Dataset Verified: {valid_samples}/{sample_count} valid samples (SHA256: {preflight_result['dataset_sha256'][:16]}...)")
    print(f"Free Disk Space: {free_disk_gb} GB")
    print(f"Preflight Status: {preflight_result['status']}")
    print("=" * 85)
    return preflight_result


def execute_training(args):
    import torch
    from torch.utils.data import Dataset, DataLoader
    from transformers import AutoTokenizer, AutoModelForCausalLM, get_cosine_schedule_with_warmup
    from peft import LoraConfig, get_peft_model, TaskType

    if not torch.cuda.is_available():
        print("\n[FATAL ERROR] PyTorch CUDA is not available on this host.")
        print("Training Gemma 2B requires an NVIDIA CUDA accelerator with >=8GB VRAM.")
        print("Execution halted to prevent impossible CPU backpropagation.")
        print("\nTo train on an NVIDIA GPU server, run:")
        print(f"  bash pipeline/train_external_gpu.sh")
        sys.exit(1)

    # Set seeds for deterministic training
    random.seed(args.seed)
    torch.manual_seed(args.seed)
    torch.cuda.manual_seed_all(args.seed)

    hf_token = args.hf_token or os.environ.get("HF_TOKEN")

    print("\n[1/6] Loading Tokenizer and Dataset...")
    tokenizer = AutoTokenizer.from_pretrained(args.base_model, token=hf_token)
    if tokenizer.pad_token is None:
        tokenizer.pad_token = tokenizer.eos_token

    with open(args.dataset_path, "r", encoding="utf-8") as f:
        raw_records = [json.loads(line) for line in f if line.strip()]

    print(f"Loaded {len(raw_records)} multi-turn training examples from {args.dataset_path}.")

    class SFTDataset(Dataset):
        def __init__(self, records, tok, max_len):
            self.examples = []
            for r in records:
                user_msg = r.get("user_message") or r.get("instruction") or r.get("prompt", "")
                assistant_resp = r.get("ideal_response") or r.get("response") or r.get("target", "")

                prompt_str = f"<start_of_turn>user\n{user_msg}<end_of_turn>\n<start_of_turn>model\n"
                full_str = f"{prompt_str}{assistant_resp}<end_of_turn>"

                prompt_tokens = tok(prompt_str, add_special_tokens=False)["input_ids"]
                full_tokens = tok(full_str, truncation=True, max_length=max_len, padding="max_length", return_tensors="pt")

                input_ids = full_tokens["input_ids"].squeeze(0)
                attention_mask = full_tokens["attention_mask"].squeeze(0)

                # Mask prompt tokens with -100 so loss is computed strictly on response
                labels = input_ids.clone()
                prompt_len = min(len(prompt_tokens), max_len)
                labels[:prompt_len] = -100
                labels[labels == tok.pad_token_id] = -100

                self.examples.append({
                    "input_ids": input_ids,
                    "attention_mask": attention_mask,
                    "labels": labels
                })

        def __len__(self):
            return len(self.examples)

        def __getitem__(self, idx):
            return self.examples[idx]

    dataset = SFTDataset(raw_records, tokenizer, args.max_seq_len)
    val_size = max(1, int(len(dataset) * 0.1))
    train_size = len(dataset) - val_size
    train_ds, val_ds = torch.utils.data.random_split(dataset, [train_size, val_size])

    train_loader = DataLoader(train_ds, batch_size=args.micro_batch_size, shuffle=True)
    val_loader = DataLoader(val_ds, batch_size=args.micro_batch_size, shuffle=False)

    print(f"Train split: {len(train_ds)} examples | Validation split: {len(val_ds)} examples")

    print("\n[2/6] Initializing Base Model with bfloat16/float16...")
    dtype = torch.bfloat16 if torch.cuda.is_bf16_supported() else torch.float16
    base_model = AutoModelForCausalLM.from_pretrained(
        args.base_model,
        torch_dtype=dtype,
        device_map="auto",
        token=hf_token
    )

    if args.gradient_checkpointing:
        base_model.gradient_checkpointing_enable()

    print("\n[3/6] Attaching LoRA Target Projections...")
    peft_config = LoraConfig(
        task_type=TaskType.CAUSAL_LM,
        r=args.lora_rank,
        lora_alpha=args.lora_alpha,
        lora_dropout=args.lora_dropout,
        target_modules=["q_proj", "k_proj", "v_proj", "o_proj", "gate_proj", "up_proj", "down_proj"],
        bias="none"
    )
    model = get_peft_model(base_model, peft_config)
    model.print_trainable_parameters()

    trainable_params = [p for p in model.parameters() if p.requires_grad]
    assert len(trainable_params) > 0, "FATAL: No parameters require gradients!"

    print("\n[4/6] Executing Multi-Epoch Gradient Backpropagation...")
    total_steps = (len(train_loader) // args.gradient_accumulation_steps) * args.num_epochs
    optimizer = torch.optim.AdamW(trainable_params, lr=args.learning_rate, weight_decay=0.01)
    scheduler = get_cosine_schedule_with_warmup(optimizer, num_warmup_steps=int(total_steps * 0.05), num_training_steps=total_steps)

    model.train()
    step_count = 0
    loss_history = []
    start_time = time.time()

    for epoch in range(args.num_epochs):
        epoch_loss = 0.0
        optimizer.zero_grad()
        for i, batch in enumerate(train_loader):
            input_ids = batch["input_ids"].cuda()
            attention_mask = batch["attention_mask"].cuda()
            labels = batch["labels"].cuda()

            outputs = model(input_ids=input_ids, attention_mask=attention_mask, labels=labels)
            loss = outputs.loss / args.gradient_accumulation_steps
            loss.backward()
            epoch_loss += loss.item() * args.gradient_accumulation_steps

            if (i + 1) % args.gradient_accumulation_steps == 0:
                torch.nn.utils.clip_grad_norm_(trainable_params, 1.0)
                optimizer.step()
                scheduler.step()
                optimizer.zero_grad()
                step_count += 1
                if step_count % 10 == 0 or step_count == 1:
                    avg_step_loss = epoch_loss / (i + 1)
                    loss_history.append({"step": step_count, "loss": round(avg_step_loss, 4)})
                    print(f"Epoch {epoch+1}/{args.num_epochs} | Step {step_count}/{total_steps} | Loss: {avg_step_loss:.4f}")

    training_duration_s = round(time.time() - start_time, 2)
    print(f"\n[5/6] Training Finished in {training_duration_s}s ({step_count} optimizer steps).")

    print("\n[6/6] Verifying Learned Weights and Saving Artifacts...")
    # Verify B matrices contain learned non-zero values
    b_non_zero = False
    for n, p in model.named_parameters():
        if ".lora_B." in n and (p != 0).any():
            b_non_zero = True
            break

    assert b_non_zero, "FATAL: LoRA B matrices remained all zero after training!"

    os.makedirs(args.output_dir, exist_ok=True)
    model.save_pretrained(args.output_dir)
    tokenizer.save_pretrained(args.output_dir)

    manifest = {
        "timestamp": time.strftime("%Y-%m-%d %H:%M:%S"),
        "base_model": args.base_model,
        "dataset_path": args.dataset_path,
        "dataset_sha256": get_file_sha256(args.dataset_path),
        "total_examples": len(raw_records),
        "train_samples": len(train_ds),
        "val_samples": len(val_ds),
        "lora_rank": args.lora_rank,
        "lora_alpha": args.lora_alpha,
        "learning_rate": args.learning_rate,
        "num_epochs": args.num_epochs,
        "optimizer_steps": step_count,
        "initial_loss": loss_history[0]["loss"] if loss_history else 0,
        "final_loss": loss_history[-1]["loss"] if loss_history else 0,
        "training_duration_seconds": training_duration_s,
        "adapter_sha256": get_file_sha256(os.path.join(args.output_dir, "adapter_model.safetensors")),
        "status": "TRAINED_AND_VERIFIED"
    }

    with open(os.path.join(args.output_dir, "training_manifest.json"), "w", encoding="utf-8") as f:
        json.dump(manifest, f, indent=2)

    print(f"[SUCCESS] Trained LoRA adapter and manifest saved to {args.output_dir}/")


def main():
    args = parse_args()
    preflight = preflight_check(args)

    if args.preflight_only:
        return

    if not preflight["hardware_ready"]:
        print("\n[NOTICE] Hardware preflight indicates CUDA GPU is unavailable on this host.")
        print("Preflight audit completed. Run `bash pipeline/train_external_gpu.sh` on an NVIDIA GPU machine.")
        return

    execute_training(args)


if __name__ == "__main__":
    main()
