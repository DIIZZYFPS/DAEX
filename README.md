# DAEX (Daedalus Execution Engine)

**DAEX** is a high-performance, edge-optimized execution client designed to run Large Language Models directly on Android hardware. DAEX hosts an advanced local AI agent known as **Icarus**, leveraging on-device inference to prioritize zero-latency speed, user privacy, and complete offline autonomy.

The project is structured as a native Kotlin Android application with a premium dark cybernetic terminal user interface.

---

## Key Capabilities

* **On-Device LLM Inference**: Powered by a highly optimized [`LiteRT`](https://github.com/google-ai-edge/LiteRT) runtime integration, keeping user data entirely local and allowing for complete offline usage.
* **Hardware Acceleration**: Built-in support for Vulkan GPU delegation and LiteRT NPU acceleration mapping to maximize token generation speeds on modern mobile SoCs.
* **Offline Document Vector Space (RAG)**: Integrates ObjectBox vector database and native embedding models to ingest, chunk, and semantic-search local PDFs or plain text documents offline. Fully manageable via the settings console.
* **Core Memory Curation**: Implements a localized fact logging engine that dynamically curates a local memory profile based on conversations.
* **Sandbox Tool Calling**: Provides a secure native capability framework allowing the model to check battery, storage status, system time, and launch apps under sandbox controls.
* **Advanced Inference UI**: Features a reasoning process visualizer (thinking block collapsible logs), real-time token speed indicators (tok/s), dynamic hardware status monitors, and custom liquid glass overlay input bars.

---

## Repository Structure

* **`DaexAndroid/`**: The primary Android application source (Kotlin, Jetpack Compose, ObjectBox, Room).
* **`docs/`**: Architecture blueprints, system metrics, and developer references.

---

## Setup and Build Instructions

### Prerequisites
1. Android Studio (Ladybug or later recommended).
2. Android SDK (Target API 36+ support).
3. JDK 17+.

### Getting Started
1. Clone the repository:
   ```bash
   git clone <repository-url>
   cd DAEX
   ```
2. Open the `DaexAndroid` directory directly in Android Studio.
3. Allow Gradle to sync and resolve all dependencies (Compose, ObjectBox, Kotlin coroutines).
4. Connect a physical Android device configured with USB debugging enabled.
5. Compile and deploy:
   ```bash
   ./gradlew.bat installDebug
   ```

### Initializing Engines
1. Complete the initial landing and configuration wizard sequence.
2. Select your preferred engine model (e.g. Gemma 2B, Qwen 1.5B).
3. The app will download and stamp the model to local storage.
4. Toggle GPU Offload or NPU acceleration if supported by your hardware, and begin execution.
