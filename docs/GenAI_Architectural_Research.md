# GenAI Architectural Research: Griffin, Qwen 3.5, and Qualcomm Genie

**Project:** DAEX  
**Date:** April 27, 2026  
**Subject:** Academic Foundations for Hybrid Edge Inference

---

## 1. The Griffin Architecture (Google DeepMind)
**Source Paper:** [Griffin: Mixing Gated Linear Recurrences with Local Attention for Efficient Language Models](https://arxiv.org/abs/2402.19427) (Feb 2024)

### 1.1 Core Innovation: RG-LRU
The paper introduces the **Real-Gated Linear Recurrent Unit (RG-LRU)**. 
*   **The Problem:** Traditional Transformers suffer from $O(L^2)$ complexity, where doubling the sequence length quadruples the compute.
*   **The Solution:** Griffin uses a "diagonal recurrence" that allows for parallel training (like a Transformer) but constant-time inference (like an RNN).
*   **Performance:** A 14B Griffin model matched Llama-2-13B while using significantly less memory for long sequences.

### 1.2 Hybridization Strategy
Griffin does not discard attention entirely. It alternates:
1.  **Two Recurrent Blocks (RG-LRU):** To handle global state and long-range history.
2.  **One Local Attention Block:** To provide high-resolution focus on the immediate context window.

---

## 2. Qwen 3.5: The Gated DeltaNet Evolution
**Source Paper:** [Qwen2.5 Technical Report](https://arxiv.org/abs/2412.15115) (Dec 2024) and the [Qwen 3.5 Hybrid Announcement](https://qwenlm.github.io/blog/qwen2.5-1m/) (Qwen Team)

### 2.1 Gated DeltaNet (GDN)
Qwen 3.5 builds on the "linear attention" trend by implementing **Gated DeltaNet**.
*   **Delta Rule:** Unlike standard linear attention which simply adds to the hidden state, GDN uses a "Delta Rule" to *update* or *correct* existing memory. This significantly improves retrieval accuracy for "needle-in-the-haystack" tasks.
*   **Hybrid Layout:** Uses a 3:1 ratio of Linear Attention to Softmax Attention.

### 2.2 Sparse Mixture-of-Experts (MoE)
Qwen 3.5 combines this hybrid attention with extreme sparsity.
*   **Active Parameters:** In the 397B model, only ~17B parameters are active per token. 
*   **Edge Impact:** This allows "large-model" logic to run on the memory-constrained NPUs of mobile devices by only loading the necessary "expert" weights into the cache.

---

## 3. Qualcomm Genie SDK: Technical Whitepaper Summary
**Source:** [Qualcomm AI Stack (QAIRT) Overview](https://www.qualcomm.com/products/technology/ai/ai-stack) and [Snapdragon 8 Elite Gen 5 AI Documentation](https://www.qualcomm.com/products/technology/processors/snapdragon-8-elite-mobile-platform)

### 3.1 Orchestration Layer
Genie is not just a driver; it is a **GenAI Runtime**. 
*   **Abstracting QNN:** Genie sits above the raw QNN API. It handles the "Graph Sharding" required to fit these massive hybrid models into the Hexagon V79's VTCM.
*   **Speculative Decoding:** Genie natively supports running a "Draft Model" (e.g., Qwen 0.5B) alongside a "Target Model" (e.g., Qwen 3.5B) to accelerate token generation by up to 2-3x.

### 3.2 KV Cache Persistence
A major finding in the QAIRT docs is the **In-Memory KV-Share**. 
*   **Genie Node Rewind:** Allows the NPU to "rewind" the KV cache to a previous state without re-processing the entire prompt. This is a "Bare-Metal" requirement for conversational AI (Icarus).

---

## 4. Synthesis for DAEX Project

| Feature | Griffin / Gemma | Qwen 3.5 | DAEX Implementation |
| :--- | :--- | :--- | :--- |
| **Recurrence** | RG-LRU | Gated DeltaNet | Use Custom HTP Op Package |
| **Sizing** | Fixed KV Window | State-based | Pin to VTCM via Genie |
| **NPU Tool** | TFLite / QNN | QNN / Genie | **Genie SDK (GenAiTransformer)** |

### Conclusion
The research confirms that the "Silicon Lock" you encountered is a known hurdle when moving from standard Transformers to hybrid models. The industry (Google, Alibaba, Qualcomm) has converged on **Hybrid Linear-Recurrence** as the solution for edge AI. To implement this in DAEX, we must leverage the **Genie SDK's sharding and KV-share capabilities** rather than raw TFLite delegation.
