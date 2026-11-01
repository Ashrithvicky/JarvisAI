#!/usr/bin/env python3
"""
VIVVA Format Converter
Converts raw vivva_training_dataset.jsonl into:
1. Gemma Tokenizer Chat Template (for SFT / LoRA training)
2. ShareGPT / OpenAI format
3. Alpaca / Instruction-Response pair format
"""

import json
import os
import sys
from typing import List, Dict, Any

def convert_to_gemma_chat(examples: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    formatted = []
    for ex in examples:
        text_parts = []
        
        # Add conversation history
        for turn in ex.get("conversation", []):
            role = turn["role"]
            content = turn["content"]
            text_parts.append(f"<start_of_turn>{role}\n{content}<end_of_turn>")
            
        # Add current turn
        user_msg = ex["user_message"]
        ideal_resp = ex["ideal_response"]
        text_parts.append(f"<start_of_turn>user\n{user_msg}<end_of_turn>")
        text_parts.append(f"<start_of_turn>model\n{ideal_resp}<end_of_turn>")
        
        full_text = "\n".join(text_parts)
        formatted.append({
            "id": ex["id"],
            "text": full_text,
            "metadata": {
                "category": ex["category"],
                "domain": ex["domain"],
                "task": ex["task"],
                "user_state": ex["user_state"],
                "explanation_level": ex["explanation_level"]
            }
        })
    return formatted

def convert_to_openai_messages(examples: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    formatted = []
    for ex in examples:
        messages = []
        for turn in ex.get("conversation", []):
            messages.append({"role": turn["role"], "content": turn["content"]})
        messages.append({"role": "user", "content": ex["user_message"]})
        messages.append({"role": "assistant", "content": ex["ideal_response"]})
        
        formatted.append({
            "id": ex["id"],
            "messages": messages,
            "metadata": {
                "category": ex["category"],
                "domain": ex["domain"],
                "task": ex["task"],
                "user_state": ex["user_state"],
                "explanation_level": ex["explanation_level"]
            }
        })
    return formatted

def main():
    input_path = sys.argv[1] if len(sys.argv) > 1 else "app/build/dataset/vivva_training_dataset.jsonl"
    output_dir = "app/build/dataset"
    os.makedirs(output_dir, exist_ok=True)
    
    with open(input_path, "r", encoding="utf-8") as f:
        raw_examples = [json.loads(line) for line in f if line.strip()]
        
    gemma_chat = convert_to_gemma_chat(raw_examples)
    gemma_path = os.path.join(output_dir, "gemma_formatted_dataset.jsonl")
    with open(gemma_path, "w", encoding="utf-8") as f:
        for item in gemma_chat:
            f.write(json.dumps(item, ensure_ascii=False) + "\n")
            
    openai_msgs = convert_to_openai_messages(raw_examples)
    openai_path = os.path.join(output_dir, "openai_messages_dataset.jsonl")
    with open(openai_path, "w", encoding="utf-8") as f:
        for item in openai_msgs:
            f.write(json.dumps(item, ensure_ascii=False) + "\n")
            
    print(f"Successfully converted {len(raw_examples)} examples:")
    print(f"  - Gemma Chat Format: {gemma_path}")
    print(f"  - OpenAI Messages Format: {openai_path}")

if __name__ == "__main__":
    main()
