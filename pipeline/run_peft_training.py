#!/usr/bin/env python3
"""
VIVVA Production Gemma 4 E2B QLoRA SFT Training Pipeline
Executes multi-turn fine-tuning on google/gemma-4-E2B-it strictly following Google's official Gemma 4 QLoRA specification:
- Official AutoProcessor loading with mandatory apply_chat_template()
- AutoModelForImageTextToText loading without silent fallback
- BitsAndBytes 4-bit NF4 with double quantization and quant_storage=compute_dtype (FP16 on Tesla T4)
- Official Google Gemma 4 LoraConfig (r=16, lora_alpha=16, modules_to_save=['lm_head', 'embed_tokens'], ensure_weight_tying=True)
- Exhaustive 720-record dataset audit asserting exact prompt prefix array equality and unmasked loss bounds
- Accurate optimizer-step calculation using math.ceil(len(train_loader) / gradient_accumulation_steps)
- CUDA peak memory statistics (reset_peak_memory_stats, max_memory_allocated, max_memory_reserved)
- Isolated output directory: vivva_gemma4_qlora_adapter/
"""

import argparse
import json
import math
import os
import random
import shutil
import sys
import time
import hashlib
from typing import Dict, List, Any, Optional, Tuple


def get_file_sha256(path: str) -> str:
    if not os.path.exists(path):
        return "N/A"
    h = hashlib.sha256()
    with open(path, "rb") as f:
        while chunk := f.read(65536):
            h.update(chunk)
    return h.hexdigest()


def parse_args():
    parser = argparse.ArgumentParser(description="VIVVA Gemma 4 E2B Official QLoRA Pipeline")
    parser.add_argument("--dataset_path", type=str, default="app/build/dataset/vivva_training_dataset.jsonl")
    parser.add_argument("--base_model", type=str, default="google/gemma-4-E2B-it")
    parser.add_argument("--output_dir", type=str, default="vivva_gemma4_qlora_adapter")
    parser.add_argument("--hf_token", type=str, default=None, help="Hugging Face access token (or env HF_TOKEN)")
    parser.add_argument("--lora_rank", type=int, default=16)
    parser.add_argument("--lora_alpha", type=int, default=16)
    parser.add_argument("--lora_dropout", type=float, default=0.05)
    parser.add_argument("--learning_rate", type=float, default=2e-4)
    parser.add_argument("--num_epochs", type=int, default=3)
    parser.add_argument("--micro_batch_size", type=int, default=2)
    parser.add_argument("--gradient_accumulation_steps", type=int, default=8)
    parser.add_argument("--max_seq_len", type=int, default=1024)
    parser.add_argument("--gradient_checkpointing", action="store_true", default=True)
    parser.add_argument("--use_4bit", action="store_true", default=True)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--preflight_only", action="store_true", help="Perform model load, LoRA attach, full 720-record loss boundary audit, 1 forward pass, and peak VRAM audit")
    return parser.parse_args()


def print_environment_versions():
    import torch
    import transformers
    import peft
    import bitsandbytes as bnb

    print("=" * 85)
    print("VIVVA GEMMA 4 E2B QLoRA TRAINING ENVIRONMENT")
    print("=" * 85)
    print(f"PyTorch Version:      {torch.__version__} (CUDA Available: {torch.cuda.is_available()})")
    print(f"Transformers Version: {transformers.__version__}")
    print(f"PEFT Version:         {peft.__version__}")
    print(f"BitsAndBytes Version: {bnb.__version__}")
    if torch.cuda.is_available():
        device_name = torch.cuda.get_device_name(0)
        capability = torch.cuda.get_device_capability(0)
        total_vram_gb = torch.cuda.get_device_properties(0).total_memory / (1024 ** 3)
        print(f"GPU Hardware:         {device_name} (Compute Capability: {capability[0]}.{capability[1]}, VRAM: {total_vram_gb:.2f} GB)")
    else:
        print("GPU Hardware:         None (CPU Environment)")
    print("=" * 85)


def get_compute_dtype():
    import torch
    if not torch.cuda.is_available():
        return torch.float32
    major, minor = torch.cuda.get_device_capability(0)
    # Tesla T4 is CC 7.5 -> FP16 compute and storage
    # Ampere/Hopper (CC >= 8.0) -> BF16 compute and storage
    if major >= 8:
        return torch.bfloat16
    return torch.float16


def load_gemma4_processor(model_id: str, hf_token: Optional[str]):
    """
    Loads official AutoProcessor for Gemma 4. Fails closed if processor cannot load.
    """
    from transformers import AutoProcessor

    processor = AutoProcessor.from_pretrained(model_id, token=hf_token, trust_remote_code=True)
    tokenizer = getattr(processor, "tokenizer", None) or getattr(processor, "_tokenizer", None)
    if tokenizer is None:
        raise RuntimeError("Loaded AutoProcessor does not contain an accessible tokenizer.")

    if tokenizer.pad_token is None:
        tokenizer.pad_token = tokenizer.eos_token

    return processor, tokenizer


def load_gemma4_model(model_id: str, hf_token: Optional[str], use_4bit: bool, compute_dtype):
    """
    Loads Gemma 4 using official AutoModelForImageTextToText with Google-specified BitsAndBytesConfig.
    Fails closed if model fails to load.
    """
    import torch
    from transformers import AutoModelForImageTextToText, BitsAndBytesConfig

    bnb_config = None
    if use_4bit and torch.cuda.is_available():
        bnb_config = BitsAndBytesConfig(
            load_in_4bit=True,
            bnb_4bit_use_double_quant=True,
            bnb_4bit_quant_type="nf4",
            bnb_4bit_compute_dtype=compute_dtype,
            bnb_4bit_quant_storage=compute_dtype,
        )

    model = AutoModelForImageTextToText.from_pretrained(
        model_id,
        quantization_config=bnb_config,
        device_map="auto" if torch.cuda.is_available() else None,
        torch_dtype=compute_dtype,
        token=hf_token,
        trust_remote_code=True
    )
    return model


def build_messages_from_record(record: Dict[str, Any]) -> List[Dict[str, str]]:
    """
    Extracts full multi-turn dialogue history from the dataset record.
    """
    messages = []
    conv = record.get("conversation", [])
    if isinstance(conv, list):
        for turn in conv:
            role = turn.get("role", "user")
            content = turn.get("content", "")
            if content:
                norm_role = "assistant" if role in ("assistant", "model") else "user"
                messages.append({"role": norm_role, "content": content})

    user_msg = record.get("user_message") or record.get("instruction") or record.get("prompt", "")
    if user_msg:
        if not messages or messages[-1].get("content") != user_msg:
            messages.append({"role": "user", "content": user_msg})

    ideal_resp = record.get("ideal_response") or record.get("response") or record.get("target", "")
    if ideal_resp:
        messages.append({"role": "assistant", "content": ideal_resp})

    return messages


def create_gemma4_lora_config(args) -> Any:
    """
    Creates PEFT LoraConfig matching Google's official Gemma 4 QLoRA guide.
    """
    from peft import LoraConfig, TaskType

    return LoraConfig(
        r=args.lora_rank,
        lora_alpha=args.lora_alpha,
        lora_dropout=args.lora_dropout,
        bias="none",
        task_type=TaskType.CAUSAL_LM,
        modules_to_save=["lm_head", "embed_tokens"],
        ensure_weight_tying=True,
    )


def audit_entire_dataset(records: List[Dict[str, Any]], tokenizer, max_seq_len: int) -> Dict[str, Any]:
    """
    Forensically audits all records in the dataset:
    - Builds prompt and full token sequences
    - Verifies prompt_ids == full_ids[:len(prompt_ids)]
    - Counts prefix mismatches, length overflows, and zero-loss token instances
    - Computes raw full token count, truncated sequence length, and padding count
    """
    total_records = len(records)
    prefix_mismatches = 0
    prompt_overflows = 0
    zero_loss_records = 0

    raw_full_lengths = []
    truncated_lengths = []
    padding_counts = []
    unmasked_loss_counts = []

    for idx, r in enumerate(records):
        messages = build_messages_from_record(r)
        prompt_messages = [m for m in messages[:-1]]

        prompt_str = tokenizer.apply_chat_template(prompt_messages, tokenize=False, add_generation_prompt=True)
        full_str = tokenizer.apply_chat_template(messages, tokenize=False, add_generation_prompt=False)

        prompt_ids = tokenizer(prompt_str, add_special_tokens=False)["input_ids"]
        raw_full_ids = tokenizer(full_str, add_special_tokens=False)["input_ids"]

        raw_len = len(raw_full_ids)
        prompt_len = len(prompt_ids)
        raw_full_lengths.append(raw_len)

        # Truncation and padding calculations
        trunc_len = min(raw_len, max_seq_len)
        truncated_lengths.append(trunc_len)
        pad_count = max(0, max_seq_len - trunc_len)
        padding_counts.append(pad_count)

        # Prefix equality assertion check
        prefix_slice = raw_full_ids[:prompt_len]
        if prefix_slice != prompt_ids:
            prefix_mismatches += 1

        # Check prompt overflow
        if prompt_len >= max_seq_len:
            prompt_overflows += 1

        # Check unmasked loss token count
        unmasked_count = max(0, trunc_len - min(prompt_len, max_seq_len))
        unmasked_loss_counts.append(unmasked_count)
        if unmasked_count == 0:
            zero_loss_records += 1

    audit_summary = {
        "total_records": total_records,
        "prefix_mismatches": prefix_mismatches,
        "prompt_overflows": prompt_overflows,
        "zero_loss_records": zero_loss_records,
        "avg_raw_full_tokens": round(sum(raw_full_lengths) / total_records, 1),
        "min_raw_full_tokens": min(raw_full_lengths),
        "max_raw_full_tokens": max(raw_full_lengths),
        "avg_truncated_tokens": round(sum(truncated_lengths) / total_records, 1),
        "avg_padding_tokens": round(sum(padding_counts) / total_records, 1),
        "avg_unmasked_loss_tokens": round(sum(unmasked_loss_counts) / total_records, 1),
        "min_unmasked_loss_tokens": min(unmasked_loss_counts),
        "passed": (prefix_mismatches == 0 and prompt_overflows == 0 and zero_loss_records == 0)
    }
    return audit_summary


def preflight_check(args):
    import torch
    from peft import get_peft_model, prepare_model_for_kbit_training

    print_environment_versions()

    print("\n[PREFLIGHT] Base Model:      ", args.base_model)
    print("[PREFLIGHT] Dataset Path:    ", args.dataset_path)
    print("[PREFLIGHT] Output Directory:", args.output_dir)
    print(f"[PREFLIGHT] LoRA Target:      Rank={args.lora_rank}, Alpha={args.lora_alpha}, Dropout={args.lora_dropout}")

    if not os.path.exists(args.dataset_path):
        print(f"[ERROR] Dataset file not found at: {args.dataset_path}")
        sys.exit(1)

    with open(args.dataset_path, "r", encoding="utf-8") as f:
        records = [json.loads(line) for line in f if line.strip()]

    print(f"[PREFLIGHT] Dataset verified on disk: {len(records)} records (SHA256: {get_file_sha256(args.dataset_path)[:16]}...)")

    if not torch.cuda.is_available():
        print("\n[PREFLIGHT HALTED] PyTorch CUDA is not available on this host. Run on Google Colab GPU.")
        return {"status": "CPU_ENVIRONMENT"}

    hf_token = args.hf_token or os.environ.get("HF_TOKEN")
    compute_dtype = get_compute_dtype()

    print("\n1. Loading Gemma 4 Official AutoProcessor...")
    processor, tokenizer = load_gemma4_processor(args.base_model, hf_token)

    print("\n2. Executing Comprehensive 720-Record Dataset Loss-Boundary Audit...")
    dataset_audit = audit_entire_dataset(records, tokenizer, args.max_seq_len)
    
    print(f"   - Total Records Audited:           {dataset_audit['total_records']}")
    print(f"   - Exact Prefix Mismatches:         {dataset_audit['prefix_mismatches']}")
    print(f"   - Prompt Overflows (>=max_seq_len):{dataset_audit['prompt_overflows']}")
    print(f"   - Zero-Loss Records:               {dataset_audit['zero_loss_records']}")
    print(f"   - Raw Full Tokens (Min/Avg/Max):   {dataset_audit['min_raw_full_tokens']} / {dataset_audit['avg_raw_full_tokens']} / {dataset_audit['max_raw_full_tokens']}")
    print(f"   - Truncated Sequence Length (Avg): {dataset_audit['avg_truncated_tokens']}")
    print(f"   - Padding Tokens (Avg):            {dataset_audit['avg_padding_tokens']}")
    print(f"   - Unmasked Loss Tokens (Min/Avg):  {dataset_audit['min_unmasked_loss_tokens']} / {dataset_audit['avg_unmasked_loss_tokens']}")
    print(f"   - 720-Record Audit Passed:         {dataset_audit['passed']}")

    if not dataset_audit["passed"]:
        print("\n[FATAL AUDIT FAILURE] Dataset integrity audit failed! Execution aborted before model training.")
        print(f"Prefix Mismatches: {dataset_audit['prefix_mismatches']}, Overflows: {dataset_audit['prompt_overflows']}, Zero-Loss: {dataset_audit['zero_loss_records']}")
        sys.exit(1)

    print("   => PROVEN: All 720 records satisfy exact prompt prefix equality with non-zero assistant loss tokens.")

    print("\n3. Loading Gemma 4 Base Model (AutoModelForImageTextToText with 4-bit NF4)...")
    torch.cuda.empty_cache()
    torch.cuda.reset_peak_memory_stats(0)

    model = load_gemma4_model(args.base_model, hf_token, args.use_4bit, compute_dtype)
    print("   Model Class: AutoModelForImageTextToText (LOAD SUCCESSFUL)")

    if args.use_4bit:
        model = prepare_model_for_kbit_training(model)

    print("\n4. Applying Official Google Gemma 4 LoRA Configuration...")
    lora_config = create_gemma4_lora_config(args)
    peft_model = get_peft_model(model, lora_config)

    trainable_params, all_params = peft_model.get_nb_trainable_parameters()
    print(f"   Trainable Parameters: {trainable_params:,} / {all_params:,} ({100 * trainable_params / all_params:.4f}%)")

    print("\n5. Executing Single Test Forward Pass & Measuring Peak Memory...")
    sample_messages = build_messages_from_record(records[0])
    prompt_messages = [m for m in sample_messages[:-1]]

    prompt_str = tokenizer.apply_chat_template(prompt_messages, tokenize=False, add_generation_prompt=True)
    full_str = tokenizer.apply_chat_template(sample_messages, tokenize=False, add_generation_prompt=False)

    prompt_ids = tokenizer(prompt_str, add_special_tokens=False)["input_ids"]
    full_tokens = tokenizer(full_str, truncation=True, max_length=args.max_seq_len, padding="max_length", return_tensors="pt")

    input_ids = full_tokens["input_ids"].squeeze(0)
    labels = input_ids.clone()
    prompt_len = min(len(prompt_ids), args.max_seq_len)
    labels[:prompt_len] = -100
    labels[labels == tokenizer.pad_token_id] = -100

    torch.cuda.reset_peak_memory_stats(0)

    tokenized_inputs = {
        "input_ids": input_ids.unsqueeze(0).to("cuda"),
        "attention_mask": full_tokens["attention_mask"].to("cuda"),
        "labels": labels.unsqueeze(0).to("cuda")
    }

    peft_model.eval()
    with torch.no_grad():
        outputs = peft_model(**tokenized_inputs)
        loss = outputs.loss
        logits = outputs.logits

    max_allocated_mb = torch.cuda.max_memory_allocated(0) / (1024 ** 2)
    max_reserved_mb = torch.cuda.max_memory_reserved(0) / (1024 ** 2)

    print(f"   Forward Pass Successful! Loss: {loss.item():.4f}, Logits Shape: {list(logits.shape)}")
    print(f"   Peak Allocated VRAM (max_memory_allocated): {max_allocated_mb:.2f} MB ({max_allocated_mb / 1024:.2f} GB)")
    print(f"   Peak Reserved VRAM  (max_memory_reserved):  {max_reserved_mb:.2f} MB ({max_reserved_mb / 1024:.2f} GB)")
    print("\n[PREFLIGHT SUCCESS] Gemma 4 E2B QLoRA preflight verified. Ready for GPU training.")
    print("=" * 85)

    return {
        "status": "PREFLIGHT_PASSED",
        "model_class": "AutoModelForImageTextToText",
        "trainable_params": trainable_params,
        "all_params": all_params,
        "dataset_audit": dataset_audit,
        "max_allocated_gb": max_allocated_mb / 1024,
        "max_reserved_gb": max_reserved_mb / 1024
    }


def execute_training(args):
    import torch
    from torch.utils.data import Dataset, DataLoader
    from transformers import get_cosine_schedule_with_warmup
    from peft import get_peft_model, prepare_model_for_kbit_training

    if not torch.cuda.is_available():
        print("\n[FATAL ERROR] PyTorch CUDA is not available. Execution stopped.")
        sys.exit(1)

    random.seed(args.seed)
    torch.manual_seed(args.seed)
    torch.cuda.manual_seed_all(args.seed)

    hf_token = args.hf_token or os.environ.get("HF_TOKEN")
    compute_dtype = get_compute_dtype()

    print("\n[1/6] Loading Processor and Dataset...")
    processor, tokenizer = load_gemma4_processor(args.base_model, hf_token)

    with open(args.dataset_path, "r", encoding="utf-8") as f:
        raw_records = [json.loads(line) for line in f if line.strip()]

    print(f"Loaded {len(raw_records)} multi-turn training records from {args.dataset_path}.")

    # Full audit before training dataset initialization
    audit = audit_entire_dataset(raw_records, tokenizer, args.max_seq_len)
    if not audit["passed"]:
        print("[FATAL ERROR] Dataset audit failed before training initiation.")
        sys.exit(1)

    class Gemma4SFTDataset(Dataset):
        def __init__(self, records, tok, max_len):
            self.examples = []
            for r in records:
                messages = build_messages_from_record(r)
                prompt_messages = [m for m in messages[:-1]]

                prompt_str = tok.apply_chat_template(prompt_messages, tokenize=False, add_generation_prompt=True)
                full_str = tok.apply_chat_template(messages, tokenize=False, add_generation_prompt=False)

                prompt_tokens = tok(prompt_str, add_special_tokens=False)["input_ids"]
                full_tokens = tok(full_str, truncation=True, max_length=max_len, padding="max_length", return_tensors="pt")

                input_ids = full_tokens["input_ids"].squeeze(0)
                attention_mask = full_tokens["attention_mask"].squeeze(0)

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

    dataset = Gemma4SFTDataset(raw_records, tokenizer, args.max_seq_len)
    val_size = 72
    train_size = len(dataset) - val_size
    train_ds, val_ds = torch.utils.data.random_split(
        dataset, [train_size, val_size],
        generator=torch.Generator().manual_seed(args.seed)
    )

    train_loader = DataLoader(train_ds, batch_size=args.micro_batch_size, shuffle=True)
    val_loader = DataLoader(val_ds, batch_size=args.micro_batch_size, shuffle=False)

    print(f"Dataset Split: {len(train_ds)} train examples, {len(val_ds)} validation examples.")

    print("\n[2/6] Loading Gemma 4 Base Model (AutoModelForImageTextToText in 4-bit NF4)...")
    model = load_gemma4_model(args.base_model, hf_token, args.use_4bit, compute_dtype)

    if args.use_4bit:
        model = prepare_model_for_kbit_training(model)

    if args.gradient_checkpointing:
        model.gradient_checkpointing_enable()

    print("\n[3/6] Configuring Google Gemma 4 LoRA Adapter...")
    lora_config = create_gemma4_lora_config(args)
    model = get_peft_model(model, lora_config)

    trainable_params, all_params = model.get_nb_trainable_parameters()
    print(f"   Trainable Parameters: {trainable_params:,} / {all_params:,} ({100 * trainable_params / all_params:.4f}%)")

    # Accurate optimizer steps calculation using math.ceil
    steps_per_epoch = math.ceil(len(train_loader) / args.gradient_accumulation_steps)
    total_steps = steps_per_epoch * args.num_epochs
    warmup_steps = int(total_steps * 0.1)

    print(f"   Steps per Epoch: {steps_per_epoch} | Total Optimizer Steps: {total_steps} | Warmup Steps: {warmup_steps}")

    optimizer = torch.optim.AdamW(model.parameters(), lr=args.learning_rate, weight_decay=0.01)
    lr_scheduler = get_cosine_schedule_with_warmup(optimizer, num_warmup_steps=warmup_steps, num_training_steps=total_steps)

    print(f"\n[4/6] Executing QLoRA Gradient Training ({args.num_epochs} Epochs, {total_steps} Optimizer Steps)...")
    model.train()
    training_loss_history = []
    global_step = 0
    start_time = time.time()

    for epoch in range(1, args.num_epochs + 1):
        epoch_loss = 0.0
        step_loss = 0.0
        optimizer.zero_grad()

        for step, batch in enumerate(train_loader, 1):
            input_ids = batch["input_ids"].to("cuda")
            attention_mask = batch["attention_mask"].to("cuda")
            labels = batch["labels"].to("cuda")

            outputs = model(input_ids=input_ids, attention_mask=attention_mask, labels=labels)
            loss = outputs.loss / args.gradient_accumulation_steps
            loss.backward()

            step_loss += loss.item() * args.gradient_accumulation_steps
            epoch_loss += loss.item() * args.gradient_accumulation_steps

            if step % args.gradient_accumulation_steps == 0 or step == len(train_loader):
                torch.nn.utils.clip_grad_norm_(model.parameters(), max_norm=1.0)
                optimizer.step()
                lr_scheduler.step()
                optimizer.zero_grad()
                global_step += 1

                if global_step % 10 == 0 or global_step == total_steps:
                    current_lr = lr_scheduler.get_last_lr()[0]
                    avg_step_loss = step_loss / (step % args.gradient_accumulation_steps or args.gradient_accumulation_steps)
                    print(f"  Epoch {epoch}/{args.num_epochs} | Step {global_step}/{total_steps} | Loss: {avg_step_loss:.4f} | LR: {current_lr:.2e}")
                    step_loss = 0.0

        avg_epoch_loss = epoch_loss / len(train_loader)
        
        # Validation
        model.eval()
        val_loss = 0.0
        with torch.no_grad():
            for val_batch in val_loader:
                v_ids = val_batch["input_ids"].to("cuda")
                v_mask = val_batch["attention_mask"].to("cuda")
                v_labels = val_batch["labels"].to("cuda")
                v_out = model(input_ids=v_ids, attention_mask=v_mask, labels=v_labels)
                val_loss += v_out.loss.item()
        avg_val_loss = val_loss / len(val_loader)
        model.train()

        print(f"==> Epoch {epoch} Completed | Train Loss: {avg_epoch_loss:.4f} | Val Loss: {avg_val_loss:.4f}")
        training_loss_history.append({
            "epoch": epoch,
            "train_loss": avg_epoch_loss,
            "val_loss": avg_val_loss
        })

    elapsed_time = round(time.time() - start_time, 2)
    print(f"\n[5/6] Training Finished in {elapsed_time}s ({elapsed_time/60:.2f} mins).")

    print(f"\n[6/6] Saving Trained QLoRA Adapter to {args.output_dir}...")
    os.makedirs(args.output_dir, exist_ok=True)
    model.save_pretrained(args.output_dir)
    tokenizer.save_pretrained(args.output_dir)

    manifest = {
        "model": args.base_model,
        "method": "QLORA_4BIT_NF4",
        "timestamp": time.strftime("%Y-%m-%d %H:%M:%S"),
        "epochs": args.num_epochs,
        "lora_rank": args.lora_rank,
        "lora_alpha": args.lora_alpha,
        "train_samples": len(train_ds),
        "val_samples": len(val_ds),
        "loss_history": training_loss_history,
        "elapsed_seconds": elapsed_time
    }
    with open(os.path.join(args.output_dir, "training_manifest.json"), "w", encoding="utf-8") as f:
        json.dump(manifest, f, indent=2)

    print(f"Trained adapter saved to: {args.output_dir}/")
    print("================================================================================")


def main():
    args = parse_args()
    if args.preflight_only:
        preflight_check(args)
    else:
        preflight_check(args)
        execute_training(args)


if __name__ == "__main__":
    main()
