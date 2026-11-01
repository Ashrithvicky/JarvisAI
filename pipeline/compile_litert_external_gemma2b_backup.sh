#!/usr/bin/env bash
# ==============================================================================
# VIVVA One-Command LiteRT-LM Official MediaPipe Compiler Runner
# Target Environment: Linux x86_64 / macOS (Python 3.10 or 3.11, PyTorch >= 2.2.0)
# Minimum System Requirements: 16 GB System RAM, 25 GB free disk space
# ==============================================================================

set -euo pipefail

echo "================================================================================"
echo "VIVVA LITERT-LM OFFICIAL COMPILATION & INT4 QUANTIZATION RUNBOOK"
echo "================================================================================"

# Pre-flight checks
echo "[1/4] Performing Pre-flight Environment Checks..."
if ! command -v python3 &> /dev/null; then
    echo "ERROR: python3 is not installed or not in PATH."
    exit 1
fi

PY_VERSION=$(python3 -c 'import sys; print(f"{sys.version_info.major}.{sys.version_info.minor}")')
echo "Detected Python Version: $PY_VERSION"
if [[ "$PY_VERSION" != "3.10" && "$PY_VERSION" != "3.11" ]]; then
    echo "WARNING: MediaPipe GenAI converter officially targets Python 3.10 or 3.11."
fi

# Dependency Installation
echo ""
echo "[2/4] Installing Official MediaPipe GenAI Toolchain..."
pip install --upgrade pip
pip install "mediapipe>=0.10.14" "torch>=2.2.0" "transformers>=4.40.0" "safetensors>=0.4.0"

# Output Directory Setup
OUTPUT_DIR="app/src/main/assets/models"
mkdir -p "$OUTPUT_DIR"
TARGET_MODEL="$OUTPUT_DIR/vivva-gemma-2b.litertlm"

# MediaPipe GenAI Converter Execution with Weight-Only INT4 Quantization
echo ""
echo "[3/4] Compiling Checkpoint to Official LiteRT-LM FlatBuffer with INT4 Quantization..."
if [ ! -d "vivva_merged_model" ]; then
    echo "ERROR: Source checkpoint 'vivva_merged_model' directory not found."
    echo "Execute the model merge phase prior to compilation."
    exit 1
fi

python3 -m mediapipe.tasks.python.genai.converter \
    --model_type GEMMA_2B \
    --input_ckpt vivva_merged_model/ \
    --output_tflite_file "$TARGET_MODEL" \
    --quantize_weights INT4 \
    --backend GPU

# Forensic Verification
echo ""
echo "[4/4] Verifying Compiled Binary Artifact Integrity & INT4 Quantization..."
python3 pipeline/validate_litertlm_binary.py "$TARGET_MODEL"
python3 pipeline/verify_int4_quantization.py "$TARGET_MODEL"

echo "================================================================================"
echo "COMPILATION & VERIFICATION SUCCESSFUL: $TARGET_MODEL"
echo "================================================================================"
