# DAEX (Daedalus Execution Engine)

**DAEX** is a high-performance, edge-optimized neural processing engine designed to run Large Language Models (LLMs) directly on your Android device. DAEX hosts an advanced autonomous AI assistant known as **Icarus**, leveraging cutting-edge on-device inference to prioritize speed, privacy, and full offline capability.

Formerly known as *Aegis*, the project has been fully reimagined from the ground up as a native Kotlin Android application with a sleek, reactive, and highly dynamic UI.

---

## 🚀 Key Features

* **True On-Device Inference**: Powered by a highly optimized `llama.cpp` integration, keeping your data entirely local and allowing for complete offline usage.
* **Hardware Acceleration**: Built-in support for Vulkan GPU delegation to maximize token generation speeds on modern mobile SoCs.
* **Modern Android Stack**: 
  * **UI**: 100% Jetpack Compose with custom glassmorphic styling, smooth vector animations, and dynamic theming (Dark/Light mode, custom accent colors).
  * **Persistence**: Robust session management and chat history using Android Room.
  * **Settings**: Persistent configuration via Jetpack Preferences DataStore.
* **Advanced Inference UI**: Features a live "Thinking Process" block for models that use reasoning tags (like `<|think|>`), real-time token generation metrics (`tok/s`), and interactive markdown rendering.

---

## 📂 Repository Structure

* **`DaexAndroid/`**: The primary Android application (Kotlin/Compose). This is the core of the project.
* **`DaexForge/`**: Python-based tooling for NPU stamping, model quantization, and prompt engineering utilities.
* **`docs/`**: Architectural research, blueprints, and implementation notes.

---

## 🛠️ Setup & Build Instructions

### Prerequisites
1. **Android Studio** (Jellyfish or later recommended).
2. **Android SDK** (API 36+ target support).
3. **Java 17**.

### Getting Started
1. Clone the repository to your local machine:
   ```bash
   git clone <repository-url>
   cd DAEX
   ```
2. Open the **`DaexAndroid`** folder directly in Android Studio.
   *(Do not open the root `DAEX` folder as the project root in Android Studio, as it may cause Gradle sync issues).*
3. Allow Gradle to sync and download all dependencies (Compose, Room, DataStore, LlamaCPP, etc.).
4. Select your target device (Physical device recommended for inference performance, or a high-end emulator).
5. Click **Run** (`Shift + F10`).

### Downloading Models
DAEX uses `.gguf` quantized models. 
1. Launch the app and complete the initial landing sequence.
2. Open the Settings (or Model Selector).
3. Download one of the supported edge models (e.g., Gemma 2b, Qwen 1.5b) directly within the app.
4. Once downloaded, toggle **GPU Acceleration** if supported by your device, and start chatting!

---

## 🗺️ Roadmap

For a detailed look at where DAEX is heading—including upcoming RAG (Retrieval-Augmented Generation) implementations, Intent Routing, and Agentic OS integrations—please view the [ROADMAP.md](./DaexAndroid/ROADMAP.md).
