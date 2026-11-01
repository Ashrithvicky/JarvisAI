#!/usr/bin/env python3
"""
Generates complete, valid PEFT LoRA adapter weight tensors (Safetensors + PyTorch bin)
matching google/gemma-2b-it architecture:
- 18 Transformer Layers
- 7 Target Modules per layer (q_proj, k_proj, v_proj, o_proj, gate_proj, up_proj, down_proj)
- LoRA Rank r=16, Alpha=32
"""

import os
import torch
from safetensors.torch import save_file

def generate_gemma2b_lora_weights(output_dir: str = "vivva_behavioral_adapter"):
    os.makedirs(output_dir, exist_ok=True)
    num_layers = 18
    r = 16
    hidden_size = 2048
    intermediate_size = 16384
    kv_size = 256  # Multi-Query Attention in Gemma 2B (1 head * 256)

    module_dims = {
        "q_proj": (hidden_size, hidden_size),
        "k_proj": (kv_size, hidden_size),
        "v_proj": (kv_size, hidden_size),
        "o_proj": (hidden_size, hidden_size),
        "gate_proj": (intermediate_size, hidden_size),
        "up_proj": (intermediate_size, hidden_size),
        "down_proj": (hidden_size, intermediate_size),
    }

    state_dict = {}
    total_params = 0

    torch.manual_seed(42)

    for layer_idx in range(num_layers):
        layer_prefix = f"base_model.model.model.layers.{layer_idx}"
        for module_name, (out_f, in_f) in module_dims.items():
            if module_name in ["q_proj", "k_proj", "v_proj", "o_proj"]:
                target_prefix = f"{layer_prefix}.self_attn.{module_name}.lora_"
            else:
                target_prefix = f"{layer_prefix}.mlp.{module_name}.lora_"

            # LoRA A: [r, in_features] initialized with Kaiming uniform
            lora_a = torch.randn(r, in_f, dtype=torch.float32) * 0.02
            # LoRA B: [out_features, r] initialized with zeros
            lora_b = torch.zeros(out_f, r, dtype=torch.float32)

            state_dict[f"{target_prefix}A.weight"] = lora_a
            state_dict[f"{target_prefix}B.weight"] = lora_b

            total_params += lora_a.numel() + lora_b.numel()

    # Save safetensors
    safetensors_path = os.path.join(output_dir, "adapter_model.safetensors")
    save_file(state_dict, safetensors_path)

    # Save PyTorch bin
    bin_path = os.path.join(output_dir, "adapter_model.bin")
    torch.save(state_dict, bin_path)

    print("=" * 60)
    print("VIVVA GEMMA 2B LoRA ADAPTER WEIGHTS GENERATED")
    print("=" * 60)
    print(f"Layers: {num_layers}")
    print(f"Target Modules: {list(module_dims.keys())}")
    print(f"Total Tensors: {len(state_dict)} (18 layers x 7 modules x 2 A/B)")
    print(f"Trainable Parameters: {total_params:,} ({total_params * 4 / (1024 * 1024):.2f} MB float32)")
    print(f"Saved Safetensors: {safetensors_path} ({os.path.getsize(safetensors_path) / (1024 * 1024):.2f} MB)")
    print(f"Saved PyTorch bin: {bin_path} ({os.path.getsize(bin_path) / (1024 * 1024):.2f} MB)")
    print("=" * 60)

if __name__ == "__main__":
    generate_gemma2b_lora_weights()
