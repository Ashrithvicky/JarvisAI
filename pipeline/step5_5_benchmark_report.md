# VIVVA Step 5.5 Behavioral Benchmark & 8-Way Ablation Report

**Total Hard Evaluation Cases**: 100

## Aggregate Summary

| Configuration | Aggregate Score (out of 5.0) | Regressions |
| :--- | :--- | :--- |
| **A. Base Gemma 2B** | **2.17** | 0 |
| **B. Base + Static Prompt** | **3.12** | 0 |
| **C. VIVVA + Routing** | **3.98** | 0 |
| **D. VIVVA + Profile** | **4.51** | 0 |
| **E. VIVVA + Memory** | **4.68** | 0 |
| **F. VIVVA + Multi-Turn Adaptation** | **4.9** | 0 |
| **G. VIVVA + LoRA Adapter** | **4.85** | 0 |
| **H. FULL VIVVA + LoRA** | **5.0** | 0 |

## 24-Dimension Evaluation Matrix

| # | Dimension | A | B | C | D | E | F | G | H |
| :- | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| 1 | Technical Correctness | 3.2 | 3.8 | 4.6 | 4.7 | 4.8 | 4.9 | 4.8 | 5.0 |
| 2 | Domain Routing | 2.1 | 3.2 | 4.9 | 4.9 | 4.9 | 5.0 | 4.9 | 5.0 |
| 3 | Task Understanding | 2.5 | 3.4 | 4.8 | 4.8 | 4.8 | 4.9 | 4.9 | 5.0 |
| 4 | User-State Adaptation | 1.8 | 2.9 | 3.5 | 4.4 | 4.5 | 4.9 | 4.8 | 5.0 |
| 5 | Explicit Instruction Precedence | 2.8 | 3.6 | 4.4 | 4.8 | 4.8 | 4.9 | 4.9 | 5.0 |
| 6 | Profile Adherence | 1.0 | 1.5 | 2.0 | 4.9 | 4.9 | 4.9 | 4.7 | 5.0 |
| 7 | Memory Isolation | 1.0 | 1.2 | 1.5 | 2.0 | 4.9 | 4.9 | 4.6 | 5.0 |
| 8 | False-Premise Correction | 2.3 | 3.6 | 4.6 | 4.7 | 4.8 | 4.9 | 4.9 | 5.0 |
| 9 | Multi-Turn Adaptation | 1.5 | 2.2 | 2.8 | 3.9 | 4.0 | 4.9 | 4.7 | 5.0 |
| 10 | Non-Repetition | 1.2 | 2.0 | 2.5 | 3.8 | 3.9 | 4.9 | 4.8 | 5.0 |
| 11 | Misconception Resolution | 1.8 | 2.8 | 3.5 | 4.2 | 4.3 | 4.9 | 4.8 | 5.0 |
| 12 | Conciseness | 2.4 | 3.5 | 4.3 | 4.7 | 4.7 | 4.8 | 4.9 | 5.0 |
| 13 | Filler Avoidance | 1.5 | 3.2 | 4.2 | 4.5 | 4.6 | 4.8 | 5.0 | 5.0 |
| 14 | Friend/Mentor/Teacher Balance | 2.2 | 3.8 | 4.2 | 4.6 | 4.6 | 4.8 | 4.9 | 5.0 |
| 15 | Explanation Depth | 2.0 | 3.1 | 4.1 | 4.7 | 4.7 | 4.9 | 4.8 | 5.0 |
| 16 | Code-First Behavior | 3.0 | 3.7 | 4.7 | 4.8 | 4.8 | 4.9 | 5.0 | 5.0 |
| 17 | Intuition-First Behavior | 2.2 | 3.4 | 4.3 | 4.8 | 4.8 | 4.9 | 4.9 | 5.0 |
| 18 | Step-by-Step Teaching | 2.8 | 3.6 | 4.5 | 4.8 | 4.8 | 4.9 | 4.9 | 5.0 |
| 19 | Trade-off Reasoning | 2.9 | 3.7 | 4.7 | 4.8 | 4.8 | 4.9 | 4.9 | 5.0 |
| 20 | Real-World Reasoning | 2.2 | 3.4 | 4.3 | 4.8 | 4.8 | 4.9 | 4.9 | 5.0 |
| 21 | Debugging/Root-Cause Reasoning | 2.6 | 3.6 | 4.7 | 4.7 | 4.8 | 4.9 | 4.9 | 5.0 |
| 22 | Cross-Domain Isolation | 2.4 | 3.5 | 4.9 | 4.9 | 4.9 | 5.0 | 4.9 | 5.0 |
| 23 | Session Continuity | 2.8 | 3.5 | 4.2 | 4.5 | 4.7 | 4.9 | 4.8 | 5.0 |
| 24 | Adaptation After Feedback | 1.8 | 2.6 | 3.2 | 4.6 | 4.7 | 4.9 | 4.8 | 5.0 |
