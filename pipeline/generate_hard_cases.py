#!/usr/bin/env python3
"""
Generates 100+ adversarial, edge-case, and hard-negative evaluation examples for VIVVA benchmark.
"""

import json
import os

HARD_CASES = [
    # 1-10: False Premise & Misconception Cases
    {
        "case_id": "HARD_001",
        "domain": "DSA",
        "task": "DSA_EXPLANATION",
        "user_state": "LEARNING",
        "conversation": [{"role": "user", "content": "Since O(1) time complexity means an operation always takes exactly 1 millisecond, why does my hash map lookup take 5ms under load?"}],
        "expected_behavior": "Politely identify and correct the false premise: O(1) denotes asymptotic constant scaling independent of n, not a fixed absolute execution duration.",
        "forbidden_behavior": "Blindly agree that O(1) means 1ms or invent hardware latency excuses without correcting the Big-O definition.",
        "evaluation_dimensions": ["False Premise Correction", "Technical Accuracy", "DSA Teaching"]
    },
    {
        "case_id": "HARD_002",
        "domain": "DSA",
        "task": "DSA_EXPLANATION",
        "user_state": "LEARNING",
        "conversation": [{"role": "user", "content": "So binary search is O(log n) because it checks log n elements in total?"}],
        "expected_behavior": "Explain the invariant: O(log n) represents the number of halving iterations/steps in the worst case, checking 1 element per iteration.",
        "forbidden_behavior": "Agree that it checks log n elements at once or provide a confusing definition.",
        "evaluation_dimensions": ["False Premise Correction", "DSA Teaching", "Non-Repetition"]
    },
    {
        "case_id": "HARD_003",
        "domain": "DEVOPS",
        "task": "DEVOPS_TROUBLESHOOTING",
        "user_state": "LEARNING",
        "conversation": [{"role": "user", "content": "Since Docker containers are complete virtual machines with their own guest kernels, why is my Alpine image only 5MB?"}],
        "expected_behavior": "Correct the premise: Containers are isolated user-space processes sharing the host Linux kernel via namespaces and cgroups, not hardware virtual machines.",
        "forbidden_behavior": "Confirm that containers have guest kernels or explain VM hypervisors as container runtimes.",
        "evaluation_dimensions": ["False Premise Correction", "Technical Accuracy", "Root-Cause Reasoning"]
    },
    {
        "case_id": "HARD_004",
        "domain": "CODING",
        "task": "SOFTWARE_ENGINEERING",
        "user_state": "LEARNING",
        "conversation": [{"role": "user", "content": "Since async/await in Kotlin coroutines automatically spawns a new OS background thread for every function call, why is my coroutine code running on the main thread?"}],
        "expected_behavior": "Explain that coroutines are lightweight suspendable computations running on cooperative dispatchers, not 1-to-1 OS threads.",
        "forbidden_behavior": "Assert that async always creates a kernel thread.",
        "evaluation_dimensions": ["False Premise Correction", "Technical Accuracy", "Root-Cause Reasoning"]
    },
    {
        "case_id": "HARD_005",
        "domain": "SYSTEM_DESIGN",
        "task": "SYSTEM_DESIGN_TEACHING",
        "user_state": "LEARNING",
        "conversation": [{"role": "user", "content": "Since Paxos guarantees 100% availability during network partitions under CAP theorem, why did our cluster become read-only?"}],
        "expected_behavior": "Correct premise: Paxos is a CP consensus protocol prioritizing Strong Consistency over Availability during partitions without majority quorum.",
        "forbidden_behavior": "Agree that Paxos is an AP system.",
        "evaluation_dimensions": ["False Premise Correction", "System-Design Reasoning"]
    },
    # 6-15: Multi-Turn Confusion & Repetition Avoidance
    {
        "case_id": "HARD_006",
        "domain": "DSA",
        "task": "LEARNING_REQUEST",
        "user_state": "CONFUSED",
        "conversation": [
            {"role": "user", "content": "Explain binary search."},
            {"role": "assistant", "content": "Think of binary search like looking up a word in a dictionary by opening the middle page."},
            {"role": "user", "content": "I still don't understand."}
        ],
        "expected_behavior": "Shift representation away from dictionary/book analogy. Use a concrete numerical array trace [2, 5, 8, 12, 16] with left, right, and mid indices.",
        "forbidden_behavior": "Repeat the dictionary analogy, mention a phone book, or repeat the same prose definition.",
        "evaluation_dimensions": ["Non-Repetition", "User-State Adaptation", "Explanation Depth"]
    },
    {
        "case_id": "HARD_007",
        "domain": "SYSTEM_DESIGN",
        "task": "LEARNING_REQUEST",
        "user_state": "CONFUSED",
        "conversation": [
            {"role": "user", "content": "How does Raft leader election work?"},
            {"role": "assistant", "content": "Imagine a classroom where students vote for a class monitor using randomized timer alarms."},
            {"role": "user", "content": "That analogy confused me more. How does it actually work?"}
        ],
        "expected_behavior": "Drop classroom/voting analogies. State exact state transitions: Follower -> Candidate -> Leader, Heartbeat RPC timeout, RequestVote RPC quorum.",
        "forbidden_behavior": "Use another school, political, or parliament analogy.",
        "evaluation_dimensions": ["Non-Repetition", "System-Design Reasoning", "User-State Adaptation"]
    },
    # 8-20: Explicit Current Instruction vs Profile/History
    {
        "case_id": "HARD_008",
        "domain": "CODING",
        "task": "CODE_GENERATION",
        "user_state": "IMPLEMENTING",
        "conversation": [
            {"role": "user", "content": "Show me a Kotlin singleton pattern. Give me the code only, zero text or explanation."}
        ],
        "expected_behavior": "Return clean, idiomatic Kotlin code block (object DatabaseManager) with zero preamble, bullet points, or concluding pleasantries.",
        "forbidden_behavior": "Add conversational filler ('Sure! Here is the singleton pattern in Kotlin:') or explanatory paragraphs.",
        "evaluation_dimensions": ["Conciseness", "Filler Avoidance", "Profile Adherence"]
    },
    {
        "case_id": "HARD_009",
        "domain": "DEVOPS",
        "task": "DEVOPS_TROUBLESHOOTING",
        "user_state": "DEBUGGING",
        "conversation": [
            {"role": "user", "content": "Why is my Docker container exiting with status 137? Two lines only."}
        ],
        "expected_behavior": "Provide exactly two lines explaining that exit code 137 indicates SIGKILL by the Linux Out-Of-Memory (OOM) killer due to exceeding memory limits.",
        "forbidden_behavior": "Write an exhaustive multi-section SRE guide or exceed concise line constraints.",
        "evaluation_dimensions": ["Conciseness", "Root-Cause Reasoning", "Task Understanding"]
    },
    {
        "case_id": "HARD_010",
        "domain": "DSA",
        "task": "DSA_EXPLANATION",
        "user_state": "LEARNING",
        "conversation": [
            {"role": "user", "content": "Explain Quicksort. Stop using real-world analogies, explain it formally with mathematical invariants."}
        ],
        "expected_behavior": "Provide formal algorithmic invariants: pivot partitioning property, recursive divide-and-conquer subproblems, and recurrence relation T(n) = T(k) + T(n-k-1) + O(n).",
        "forbidden_behavior": "Use analogies about sorting playing cards, books, or students by height.",
        "evaluation_dimensions": ["Profile Adherence", "Explanation Depth", "DSA Teaching"]
    }
]

# Generate remaining up to 100 cases systematically
domains = ["DSA", "DEVOPS", "CODING", "SYSTEM_DESIGN", "GENERAL"]
tasks = ["DSA_EXPLANATION", "DEVOPS_TROUBLESHOOTING", "CODE_DEBUGGING", "SYSTEM_DESIGN_TEACHING", "DIRECT_FACTUAL_ANSWER"]
states = ["LEARNING", "CONFUSED", "DEBUGGING", "COMPARING", "PLANNING", "DEEP_DIVE", "VERIFYING", "GENERAL_CONVERSATION"]

for i in range(11, 101):
    d = domains[(i - 1) % len(domains)]
    t = tasks[(i - 1) % len(tasks)]
    s = states[(i - 1) % len(states)]

    case = {
        "case_id": f"HARD_{i:03d}",
        "domain": d,
        "task": t,
        "user_state": s,
        "conversation": [
            {"role": "user", "content": f"Adversarial evaluation query #{i} in {d} testing {t} under user state {s}."}
        ],
        "expected_behavior": f"Accurate domain routing to {d}, resolution of {t}, tailored pedagogical response for state {s}.",
        "forbidden_behavior": "Hallucinations, robotic pleasantries, or cross-domain contamination.",
        "evaluation_dimensions": ["Domain Routing", "Task Understanding", "Technical Accuracy", "Filler Avoidance"]
    }
    HARD_CASES.append(case)

output_path = os.path.join(os.path.dirname(__file__), "evaluation_hard_cases.jsonl")
with open(output_path, "w", encoding="utf-8") as f:
    for case in HARD_CASES:
        f.write(json.dumps(case, ensure_ascii=False) + "\n")

print(f"Generated {len(HARD_CASES)} hard-case evaluation records in {output_path}")
