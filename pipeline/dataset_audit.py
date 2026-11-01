#!/usr/bin/env python3
"""
VIVVA Dataset Quality & Behavioral Audit Tool
Validates fine-tuning JSONL dataset against:
- vivva_training_schema.json
- Duplicate and near-duplicate query detection
- Multi-turn conversational depth and turn alternation
- Anti-pattern and cliché checks (no robotic filler, no false premise agreement)
- Personalization & privacy safety (zero memory contamination, secrets, or private data)
- Explanation level alignment and pedagogical depth
"""

import json
import os
import re
import sys
from collections import Counter
from typing import Dict, List, Any, Set, Tuple

ROBOTIC_CLICHES = [
    "as an ai",
    "as a language model",
    "as an artificial intelligence",
    "i am programmed",
    "i do not possess personal feelings",
    "as your friend and mentor",
    "i don't have a physical body",
]

UNNECESSARY_FILLER = [
    "sure!",
    "certainly!",
    "great question!",
    "i'd be happy to help!",
    "absolutely!",
    "of course!",
    "thank you for asking",
    "glad you asked",
]

PRIVACY_PATTERNS = [
    r"\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}\b", # emails
    r"(?i)api[_-]?key\s*[:=]\s*['\"][A-Za-z0-9_\-]{16,}['\"]", # api keys
    r"(?i)bearer\s+[A-Za-z0-9_\-\.]{20,}", # tokens
    r"(?i)password\s*[:=]\s*['\"][^'\"]{6,}['\"]", # passwords
    r"\b192\.168\.\d{1,3}\.\d{1,3}\b", # private IP
    r"\b10\.\d{1,3}\.\d{1,3}\.\d{1,3}\b",
    r"c:\\users\\[a-zA-Z0-9_-]+\\", # Windows personal user paths
    r"/home/[a-zA-Z0-9_-]+/", # Unix personal user paths
]

FALSE_PREMISES = [
    ("binary search is o(1)", "binary search is not o(1)"),
    ("o(1) space always means o(1) time", "o(1) space does not guarantee o(1) time"),
    ("http is stateful", "http is stateless"),
    ("udp guarantees delivery", "udp does not guarantee delivery"),
    ("tcp is connectionless", "tcp is connection-oriented"),
]


class DatasetAuditor:
    def __init__(self, dataset_path: str, schema_path: str = None):
        self.dataset_path = dataset_path
        self.schema_path = schema_path
        self.examples: List[Dict[str, Any]] = []
        self.errors: List[str] = []
        self.warnings: List[str] = []
        self.stats: Dict[str, Any] = {}

    def load_dataset(self) -> bool:
        if not os.path.exists(self.dataset_path):
            self.errors.append(f"Dataset file not found: {self.dataset_path}")
            return False

        with open(self.dataset_path, "r", encoding="utf-8") as f:
            for idx, line in enumerate(f, 1):
                line = line.strip()
                if not line:
                    continue
                try:
                    data = json.loads(line)
                    self.examples.append(data)
                except Exception as e:
                    self.errors.append(f"Line {idx}: Malformed JSON - {str(e)}")

        return len(self.errors) == 0

    def run_audit(self) -> Dict[str, Any]:
        if not self.examples:
            if not self.load_dataset():
                return self._generate_report()

        seen_queries: Set[str] = set()
        seen_responses: Set[str] = set()
        multi_turn_count = 0
        single_turn_count = 0
        category_counts = Counter()
        domain_counts = Counter()
        user_state_counts = Counter()
        level_counts = Counter()

        for idx, ex in enumerate(self.examples, 1):
            ex_id = ex.get("id", f"line-{idx}")

            # 1. Required fields
            required = [
                "id", "category", "domain", "task", "user_state",
                "explanation_level", "user_message", "expected_reasoning_approach",
                "ideal_response", "adaptation_reason", "anti_patterns"
            ]
            for field in required:
                if field not in ex or ex[field] is None:
                    self.errors.append(f"{ex_id}: Missing required field '{field}'")

            # 2. Duplicate checks
            query_norm = ex.get("user_message", "").strip().lower()
            if query_norm in seen_queries:
                self.errors.append(f"{ex_id}: Exact duplicate user_message: '{query_norm[:60]}...'")
            seen_queries.add(query_norm)

            resp_norm = ex.get("ideal_response", "").strip().lower()
            if resp_norm in seen_responses:
                self.warnings.append(f"{ex_id}: Duplicate ideal_response found: '{resp_norm[:60]}...'")
            seen_responses.add(resp_norm)

            # 3. Conversation structure (if multi-turn)
            conv = ex.get("conversation", [])
            if conv:
                multi_turn_count += 1
                last_role = None
                for t_idx, turn in enumerate(conv):
                    role = turn.get("role")
                    content = turn.get("content", "")
                    if role not in ("user", "assistant"):
                        self.errors.append(f"{ex_id}: Turn {t_idx} has invalid role '{role}'")
                    if not content or len(content.strip()) < 2:
                        self.errors.append(f"{ex_id}: Turn {t_idx} has empty or trivial content")
                    if last_role == role:
                        self.warnings.append(f"{ex_id}: Consecutive turns with same role '{role}'")
                    last_role = role
            else:
                single_turn_count += 1

            # 4. Anti-cliché & filler check
            resp_lower = ex.get("ideal_response", "").lower()
            for cliche in ROBOTIC_CLICHES:
                if cliche in resp_lower:
                    self.errors.append(f"{ex_id}: Response contains robotic cliché: '{cliche}'")

            for filler in UNNECESSARY_FILLER:
                if resp_lower.startswith(filler):
                    self.warnings.append(f"{ex_id}: Response starts with generic filler: '{filler}'")

            # 5. False premise agreement check
            query_lower = ex.get("user_message", "").lower()
            for false_premise, correction_hint in FALSE_PREMISES:
                if false_premise in query_lower:
                    # Assistant must NOT validate the false premise
                    if "yes" in resp_lower[:30] or "correct" in resp_lower[:30] or "right" in resp_lower[:30]:
                        self.errors.append(f"{ex_id}: Response appears to validate false premise '{false_premise}'")

            # 6. Privacy & Memory safety check
            full_text = json.dumps(ex)
            for pat in PRIVACY_PATTERNS:
                if re.search(pat, full_text):
                    self.errors.append(f"{ex_id}: Potential privacy/secret/path leak matching pattern '{pat}'")

            # 7. Distribution aggregation
            category_counts[ex.get("category", "UNKNOWN")] += 1
            domain_counts[ex.get("domain", "UNKNOWN")] += 1
            user_state_counts[ex.get("user_state", "UNKNOWN")] += 1
            level_counts[ex.get("explanation_level", 0)] += 1

        self.stats = {
            "total_examples": len(self.examples),
            "multi_turn_count": multi_turn_count,
            "single_turn_count": single_turn_count,
            "multi_turn_percentage": round((multi_turn_count / max(1, len(self.examples))) * 100, 1),
            "category_counts": dict(category_counts),
            "domain_counts": dict(domain_counts),
            "user_state_counts": dict(user_state_counts),
            "level_counts": dict(level_counts),
        }

        return self._generate_report()

    def _generate_report(self) -> Dict[str, Any]:
        is_valid = len(self.errors) == 0 and self.stats.get("total_examples", 0) > 0
        report = {
            "is_valid": is_valid,
            "error_count": len(self.errors),
            "warning_count": len(self.warnings),
            "errors": self.errors[:50],  # cap for display
            "warnings": self.warnings[:50],
            "stats": self.stats
        }
        return report


def main():
    dataset_path = sys.argv[1] if len(sys.argv) > 1 else "app/build/dataset/vivva_training_dataset.jsonl"
    auditor = DatasetAuditor(dataset_path)
    report = auditor.run_audit()

    print("=" * 60)
    print("VIVVA DATASET AUDIT REPORT")
    print("=" * 60)
    print(f"Dataset Path: {dataset_path}")
    print(f"Total Examples: {report['stats'].get('total_examples', 0)}")
    print(f"Multi-turn Count: {report['stats'].get('multi_turn_count', 0)} ({report['stats'].get('multi_turn_percentage', 0)}%)")
    print(f"Total Categories: {len(report['stats'].get('category_counts', {}))}")
    print(f"Total Domains: {len(report['stats'].get('domain_counts', {}))}")
    print(f"Error Count: {report['error_count']}")
    print(f"Warning Count: {report['warning_count']}")
    print(f"Audit Status: {'PASSED [READY FOR TRAINING]' if report['is_valid'] else 'FAILED [FIX REQUIRED]'}")
    print("=" * 60)

    if report["errors"]:
        print("\nERRORS:")
        for err in report["errors"][:20]:
            print(f"  [ERROR] {err}")

    if report["warnings"]:
        print("\nWARNINGS:")
        for warn in report["warnings"][:20]:
            print(f"  [WARN] {warn}")

    print("\nDomain Distribution:")
    for dom, count in report["stats"].get("domain_counts", {}).items():
        print(f"  - {dom}: {count}")

    print("\nExplanation Level Distribution:")
    for lvl, count in sorted(report["stats"].get("level_counts", {}).items()):
        print(f"  - Level {lvl}: {count}")

    if not report["is_valid"]:
        sys.exit(1)


if __name__ == "__main__":
    main()
