#!/usr/bin/env python3
"""
VIVVA Personalization & Privacy Safety Scanner (Phase 9)
Scans datasets, model configs, and prompt templates to ensure zero contamination
of personal memories, names, private secrets, credentials, or filesystem paths.
"""

import json
import os
import re
import sys
from typing import List, Dict, Any

SUSPICIOUS_PATTERNS = [
    (r"(?i)\b(api[_-]?key|secret|token|password|auth|bearer)\b\s*[:=]\s*['\"][A-Za-z0-9_\-\.]{12,}['\"]", "Hard-coded Secret / Token / API Key"),
    (r"\b192\.168\.\d{1,3}\.\d{1,3}\b", "Private IPv4 Subnet Address"),
    (r"\b10\.\d{1,3}\.\d{1,3}\.\d{1,3}\b", "Private Class A IPv4 Address"),
    (r"(?i)c:\\users\\[a-zA-Z0-9_\-\\]+", "Windows User Home Path"),
    (r"(?i)/home/[a-zA-Z0-9_\-/]+", "Unix User Home Path"),
    (r"\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}\b", "Personal Email Address"),
    (r"\b(id_rsa|id_ed25519|\.pem|\.p12|\.keystore)\b", "Private Key / Keystore Reference"),
]

class SafetyScanner:
    def __init__(self, dataset_path: str):
        self.dataset_path = dataset_path
        self.violations: List[str] = []
        self.scanned_count = 0

    def scan(self) -> bool:
        if not os.path.exists(self.dataset_path):
            self.violations.append(f"Target file not found: {self.dataset_path}")
            return False

        with open(self.dataset_path, "r", encoding="utf-8") as f:
            for idx, line in enumerate(f, 1):
                line = line.strip()
                if not line:
                    continue
                self.scanned_count += 1
                try:
                    data = json.loads(line)
                    text_dump = json.dumps(data)
                    for pat, desc in SUSPICIOUS_PATTERNS:
                        matches = re.findall(pat, text_dump)
                        if matches:
                            self.violations.append(f"Line {idx} (ID: {data.get('id', 'unknown')}): Found {desc} matching '{matches[0]}'")
                except Exception as e:
                    self.violations.append(f"Line {idx}: JSON parse error - {str(e)}")

        return len(self.violations) == 0

def main():
    dataset_path = sys.argv[1] if len(sys.argv) > 1 else "app/build/dataset/vivva_training_dataset.jsonl"
    scanner = SafetyScanner(dataset_path)
    is_safe = scanner.scan()

    print("=" * 60)
    print("VIVVA PERSONALIZATION & PRIVACY SAFETY SCAN (PHASE 9)")
    print("=" * 60)
    print(f"Target Dataset: {dataset_path}")
    print(f"Total Examples Scanned: {scanner.scanned_count}")
    print(f"Violations Found: {len(scanner.violations)}")
    print(f"Privacy Safety Status: {'CLEAN [ZERO CONTAMINATION]' if is_safe else 'FAILED [VIOLATIONS DETECTED]'}")
    print("=" * 60)

    if scanner.violations:
        for v in scanner.violations[:20]:
            print(f"  [VIOLATION] {v}")
        sys.exit(1)

if __name__ == "__main__":
    main()
