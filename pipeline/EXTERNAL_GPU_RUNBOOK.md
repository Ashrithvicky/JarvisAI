# VIVVA External GPU Training Runbook

## Overview
This runbook provides the exact one-command procedure to train VIVVA's Gemma 2B behavioral LoRA adapter on any NVIDIA CUDA GPU machine.

---

## Hardware Requirements (Derived from Implementation)
- **Minimum Likely GPU**: 1x NVIDIA GPU with **$\ge 8$ GB VRAM** (e.g., RTX 3060 8GB/12GB, RTX 4060, Tesla T4).
  - *Configuration*: `micro_batch_size=2`, `gradient_accumulation_steps=8`, `gradient_checkpointing=True`.
  - *Peak VRAM*: $\approx 7.5\text{ GB}$.
- **Recommended GPU**: 1x NVIDIA GPU with **$\ge 12$ GB – 16 GB VRAM** (e.g., RTX 3080, RTX 4070, RTX 4080, Tesla V100).
- **Comfortable / Fast GPU**: 1x NVIDIA GPU with **24 GB – 80 GB VRAM** (e.g., RTX 3090, RTX 4090, A100, H100).
- **Disk Space**: $\ge 15\text{ GB}$ free space (for base model checkpoint + adapter + merged float16 weights).
- **System RAM**: $\ge 12\text{ GB}$.

---

## Execution Instructions

### Step 1: Clone or Copy Repository to the GPU Server
```bash
git clone <repository_url>
cd PrivateAI
```

### Step 2: Set Hugging Face Token (if required for base Gemma access)
```bash
export HF_TOKEN="your_hf_access_token"
```

### Step 3: Run the One-Command Training & Merge Runner
```bash
bash pipeline/train_external_gpu.sh
```

---

## What the Runner Executes Automatically
1. **Preflight**: Validates CUDA hardware, VRAM, disk space, and dataset integrity.
2. **LoRA Fine-Tuning**: Executes multi-epoch gradient backpropagation on Gemma 2B with loss masking and cosine learning rate decay.
3. **Truth Gate**: Asserts $B \ne 0$ on all LoRA B matrices, validates training loss records, and generates `vivva_behavioral_adapter/training_manifest.json`.
4. **Offline Merge**: Merges the verified trained weights into a standalone float16 checkpoint in `vivva_merged_model/`.

---

## Step 4: Compile Model to On-Device LiteRT-LM (on Linux x86_64 / macOS)
```bash
bash pipeline/compile_litert_external.sh
```
This compiles `vivva_merged_model/` into the production on-device artifact:
`app/src/main/assets/models/vivva-gemma-2b.litertlm`.
