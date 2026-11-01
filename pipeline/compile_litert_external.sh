#!/usr/bin/env bash
# ==============================================================================
# VIVVA LiteRT-LM Conversion Pipeline for Gemma 4 E2B
# Target OS: Linux x86_64 / Google Colab
# Toolchain: ai-edge-torch / litert-torch / MediaPipe GenAI converter
# ==============================================================================

set -euo pipefail

echo "================================================================================"
echo "VIVVA LITERT-LM CONVERSION RUNNER: GEMMA 4 E2B"
echo "================================================================================"

MERGED_MODEL_DIR="${1:-vivva_merged_gemma4_model}"
OUTPUT_DIR="${2:-app/src/main/assets/models}"
OUTPUT_FILE="${OUTPUT_DIR}/vivva-gemma-4-E2B-it.litertlm"

mkdir -p "${OUTPUT_DIR}"

echo "1. Checking Merged Model Directory: ${MERGED_MODEL_DIR}..."
if [ ! -d "${MERGED_MODEL_DIR}" ]; then
    echo "ERROR: Merged model directory ${MERGED_MODEL_DIR} does not exist."
    echo "Execute pipeline/merge_lora.py first."
    exit 1
fi

echo "2. Installing LiteRT Export Tooling..."
pip install -q ai-edge-torch litert-torch-nightly || pip install -q mediapipe

echo "3. Exporting Hugging Face Merged Gemma 4 Model to LiteRT-LM..."
if command -v litert-torch &> /dev/null; then
    echo "Running: litert-torch export_hf --model=${MERGED_MODEL_DIR} --output_dir=${OUTPUT_DIR}"
    litert-torch export_hf --model="${MERGED_MODEL_DIR}" --output_dir="${OUTPUT_DIR}"
elif python -m mediapipe.tasks.python.genai.converter --help &> /dev/null; then
    echo "Running MediaPipe GenAI Converter..."
    python -m mediapipe.tasks.python.genai.converter \
        --model_type GEMMA_4_E2B \
        --input_ckpt "${MERGED_MODEL_DIR}" \
        --output_tflite_file "${OUTPUT_FILE}" \
        --quantize_weights INT4 \
        --backend GPU
else
    echo "WARNING: Official litert-torch CLI not found. Running python fallback export script..."
    python pipeline/export_litert.py "${MERGED_MODEL_DIR}" "${OUTPUT_FILE}"
fi

echo "4. Validating Resulting LiteRT-LM Binary..."
python pipeline/validate_litertlm_binary.py "${OUTPUT_FILE}"

echo "================================================================================"
echo "LITERT-LM CONVERSION AND VALIDATION COMPLETE: ${OUTPUT_FILE}"
echo "================================================================================"
