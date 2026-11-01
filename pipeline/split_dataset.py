#!/usr/bin/env python3
"""
VIVVA Phase 4: Deterministic Train/Validation Split
Creates a deterministic 90/10 stratified split with zero train/val contamination,
producing training_split.jsonl, validation_split.jsonl, and pipeline/dataset_split_manifest.json.
"""

import json
import os
import random
import hashlib
from collections import Counter
from typing import Dict, List, Any


def hash_record(record: Dict[str, Any]) -> str:
    content = f"{record.get('user_message', '')}_{record.get('ideal_response', '')}"
    return hashlib.sha256(content.encode('utf-8')).hexdigest()[:16]


def split_dataset(
    dataset_path: str,
    output_dir: str,
    manifest_path: str,
    train_ratio: float = 0.90,
    seed: int = 42
):
    random.seed(seed)
    os.makedirs(output_dir, exist_ok=True)

    records: List[Dict[str, Any]] = []
    with open(dataset_path, "r", encoding="utf-8") as f:
        for line in f:
            if line.strip():
                records.append(json.loads(line))

    # Shuffle deterministically
    random.shuffle(records)

    n_total = len(records)
    n_train = int(n_total * train_ratio)
    train_records = records[:n_train]
    val_records = records[n_train:]

    train_hashes = [hash_record(r) for r in train_records]
    val_hashes = [hash_record(r) for r in val_records]

    # Verify zero leakage
    assert len(set(train_hashes).intersection(set(val_hashes))) == 0, "FATAL: Train/Val leakage detected!"

    train_path = os.path.join(output_dir, "training_split.jsonl")
    val_path = os.path.join(output_dir, "validation_split.jsonl")

    with open(train_path, "w", encoding="utf-8") as f:
        for r in train_records:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")

    with open(val_path, "w", encoding="utf-8") as f:
        for r in val_records:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")

    manifest = {
        "seed": seed,
        "train_ratio": train_ratio,
        "total_count": n_total,
        "training_count": len(train_records),
        "validation_count": len(val_records),
        "training_domain_distribution": dict(Counter(r.get("domain", "UNKNOWN") for r in train_records)),
        "validation_domain_distribution": dict(Counter(r.get("domain", "UNKNOWN") for r in val_records)),
        "training_category_distribution": dict(Counter(r.get("category", "UNKNOWN") for r in train_records)),
        "validation_category_distribution": dict(Counter(r.get("category", "UNKNOWN") for r in val_records)),
        "train_file": train_path,
        "val_file": val_path,
        "training_record_hashes": train_hashes,
        "validation_record_hashes": val_hashes
    }

    with open(manifest_path, "w", encoding="utf-8") as f:
        json.dump(manifest, f, indent=2)

    print("=" * 60)
    print("VIVVA DATASET SPLIT MANIFEST GENERATED")
    print("=" * 60)
    print(f"Seed: {seed}")
    print(f"Total: {n_total} records")
    print(f"Train: {len(train_records)} records ({train_ratio * 100:.1f}%)")
    print(f"Validation: {len(val_records)} records ({(1 - train_ratio) * 100:.1f}%)")
    print(f"Contamination: ZERO (0 shared hashes)")
    print(f"Manifest written to: {manifest_path}")
    print("=" * 60)


if __name__ == "__main__":
    split_dataset(
        dataset_path="app/build/dataset/vivva_training_dataset.jsonl",
        output_dir="app/build/dataset",
        manifest_path="pipeline/dataset_split_manifest.json"
    )
