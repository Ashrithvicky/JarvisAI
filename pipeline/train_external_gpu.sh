#!/usr/bin/env bash
# ==============================================================================
# VIVVA Gemma 4 E2B QLoRA Training & LiteRT-LM Export Pipeline
# Target Hardware: NVIDIA GPU with >=8GB VRAM (e.g. Tesla T4 in Google Colab)
# ==============================================================================

set -euo pipefail

echo "================================================================================"
echo "VIVVA PRODUCTION GEMMA 4 E2B QLoRA TRAINING & EXPORT RUNNER"
echo "================================================================================"

# 1. Hardware Check
if ! command -v nvidia-smi &> /dev/null; then
    echo "ERROR: nvidia-smi not found. NVIDIA CUDA drivers must be installed."
    exit 1
fi

echo "[1/5] Checking GPU hardware and VRAM..."
nvidia-smi

echo ""
echo "[2/5] Installing Python dependencies for QLoRA & LiteRT..."
pip install -r pipeline/requirements_gpu.txt

echo ""
echo "[3/5] Executing Gemma 4 E2B QLoRA Preflight (1 Forward Pass)..."
python pipeline/run_peft_training.py \
    --base_model google/gemma-4-E2B-it \
    --dataset_path app/build/dataset/vivva_training_dataset.jsonl \
    --output_dir vivva_gemma4_qlora_adapter \
    --preflight_only

echo ""
echo "[4/5] Executing Full QLoRA SFT Training..."
python pipeline/run_peft_training.py \
    --base_model google/gemma-4-E2B-it \
    --dataset_path app/build/dataset/vivva_training_dataset.jsonl \
    --output_dir vivva_gemma4_qlora_adapter \
    --num_epochs 3 \
    --micro_batch_size 2 \
    --gradient_accumulation_steps 8 \
    --use_4bit \
    --gradient_checkpointing

echo ""
echo "[5/5] Performing PeftModel Merge to Full Checkpoint..."
python pipeline/merge_lora.py \
    --base_model google/gemma-4-E2B-it \
    --adapter_dir vivva_gemma4_qlora_adapter \
    --output_dir vivva_merged_gemma4_model

echo ""
echo "================================================================================"
echo "TRAINING AND MERGE COMPLETE!"
echo "Trained QLoRA adapter saved to: vivva_gemma4_qlora_adapter/"
echo "Merged full checkpoint saved to: vivva_merged_gemma4_model/"
echo "================================================================================"
