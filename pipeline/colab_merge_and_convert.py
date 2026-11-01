# Colab (GPU runtime) script: merge the VIVVA QLoRA adapter into Gemma 4 E2B, then try the LiteRT-LM conversion.
# Upload VIVVA_Gemma4_QLoRA_backup.zip to /content first. Run cell by cell (each "# %%" block).

# %% 1. Install
# !pip install -q -U "transformers>=4.57" peft accelerate safetensors huggingface_hub
# !pip install -q -U litert-torch  # converter; Gemma 4 support must be confirmed (see step 4)

# %% 2. Unpack adapter
import os, zipfile
with zipfile.ZipFile("/content/VIVVA_Gemma4_QLoRA_backup.zip") as z:
    z.extractall("/content/qlora")
ADAPTER = next(r for r, _, f in os.walk("/content/qlora") if "adapter_model.safetensors" in f)
print("adapter dir:", ADAPTER)

# %% 3. Merge (bf16, no quantization) and save a Hugging Face checkpoint
import torch
from peft import PeftModel
from transformers import AutoModelForCausalLM, AutoProcessor

BASE, REVISION = "google/gemma-4-E2B-it", "3e22461f65e89153144f8adb70e3b8c2cc9845a7"
# Log in first if the base model is gated: from huggingface_hub import login; login()
model = AutoModelForCausalLM.from_pretrained(BASE, revision=REVISION, torch_dtype=torch.bfloat16, device_map="cpu")
model = PeftModel.from_pretrained(model, ADAPTER).merge_and_unload()
model.save_pretrained("/content/vivva_merged", safe_serialization=True)
AutoProcessor.from_pretrained(BASE, revision=REVISION).save_pretrained("/content/vivva_merged")

# %% 4. Convert to .litertlm
# Google ships gemma-4-E2B-it.litertlm with its own mixed 2/4/8-bit quantization; a public converter for
# fine-tuned Gemma 4 (with vision and audio encoders) is not documented. Check these in order and use the first that works:
#  a) https://github.com/google-ai-edge/LiteRT-LM  (docs/ for a "convert" or "build model" guide)
#  b) https://github.com/google-ai-edge/litert-torch  (generative examples: Gemma 4)
# The result must keep the same vision/audio sections as the stock file, or the app falls back to text-only.
# Do not use pipeline/export_litert.py: it writes a placeholder file, not a real model.

# %% 5. Download result (once a real .litertlm exists)
# from google.colab import files; files.download("/content/out/gemma-4-E2B-it-vivva.litertlm")
# Then on the PC:  adb push <file> /data/local/tmp/  and copy into no_backup/models/ with run-as (same route as before).
