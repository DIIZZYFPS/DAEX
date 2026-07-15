# DAEX (Daedalus Execution Engine)

**DAEX** is a high-performance, edge-optimized execution client designed to run Large Language Models directly on Android hardware. DAEX hosts an advanced local AI agent known as **Icarus**, leveraging on-device inference to prioritize zero-latency speed, user privacy, and complete offline autonomy.

The project is structured as a native Kotlin Android application with a premium dark cybernetic terminal user interface.

---

## Key Capabilities

* **On-Device LLM Inference**: Powered by a highly optimized [`LiteRT`](https://github.com/google-ai-edge/LiteRT) runtime integration (Gemma model family, with hardware-specific variants for Qualcomm and Google Tensor NPUs), keeping user data entirely local and allowing for complete offline usage.
* **Hardware Acceleration**: Built-in support for Vulkan GPU delegation and LiteRT NPU acceleration mapping to maximize token generation speeds on modern mobile SoCs.
* **Offline Document Vector Space (RAG)**: Integrates ObjectBox vector database and native embedding models to ingest, chunk, and semantic-search local PDFs or plain text documents offline. Fully manageable via the settings console.
* **Cross-Conversation Memory & Search**: Hybrid BM25/vector search (ObjectBox + SQLite FTS5) over full conversation history, so past discussions are recalled and searchable, not just the active thread.
* **Core Memory Curation**: Implements a localized fact logging engine that dynamically curates a local memory profile based on conversations.
* **Voice Mode**: Fully offline speech pipeline (Sherpa-ONNX ASR, on-device VAD, Kokoro TTS) for hands-free, continuous voice conversations with the model.
* **Sandbox Tool Calling & Modular Skills**: A native capability framework with individually toggleable tools (device time/battery/storage info, launching apps, sending email, running system intents, recalling past conversations) plus a skills system that loads domain-specific instructions on demand instead of bloating the system prompt. Higher-risk tools are off by default.
* **Advanced Inference UI**: Features a reasoning process visualizer (thinking block collapsible logs), real-time token speed indicators (tok/s), dynamic hardware status monitors, and custom liquid glass overlay input bars.

---

## Repository Structure

* **`DaexAndroid/`**: The primary Android application.
  * **`app/`**: Kotlin/Jetpack Compose source, organized into `data/` (persistence/preferences), `domain/` (business logic/tools), `framework/` (system/hardware integrations), and `ui/` (Compose screens and ViewModels). Persistence is ObjectBox (object database + vector search) paired with SQLite FTS5 for BM25 text search — not Room.
  * **`macrobenchmark/`**: A `com.android.test` module with Macrobenchmark startup-timing tests, run against `app`'s dedicated `benchmark` build type.
* **`docs/`**: Architecture blueprints, system metrics, and developer references.

---

## Testing

The app has a full automated test suite, all runnable without a physical device except where noted:

* **Unit tests**: ViewModels and pure business logic (`app/src/test`).
* **Integration tests**: Real ObjectBox `BoxStore` + FTS5 search, and real HTTP resumable-download behavior via MockWebServer.
* **Compose UI tests**: Real semantics-tree interaction testing via Robolectric, no emulator required.
* **Snapshot/screenshot tests**: Pixel-level Compose rendering diffed against committed baselines (Roborazzi).
* **End-to-end test**: A full simulated user journey (onboarding, model load, chat, pin, cross-conversation search, live voice session) against a fake inference engine.
* **Static analysis**: Android Lint and detekt, both gated on a baseline so only newly introduced issues fail a build.
* **Instrumented tests** (`app/src/androidTest`) and **Macrobenchmark** (`macrobenchmark/`): require a real device or emulator, so CI doesn't run them, but both are verified passing on real hardware (median cold-start time-to-initial-display ~181ms).

Run everything CI runs, locally:
```bash
cd DaexAndroid
./gradlew testDebugUnitTest   # unit, integration, Compose UI, snapshot, and end-to-end tests
./gradlew lintDebug
./gradlew detekt
```

With a device or emulator connected:
```bash
./gradlew :app:connectedDebugAndroidTest           # instrumented tests
./gradlew :macrobenchmark:connectedBenchmarkAndroidTest   # startup benchmark
```

GitHub Actions (`.github/workflows/android-ci.yml`) runs the JVM-only checks on every push and pull request.

---

## Setup and Build Instructions

### Prerequisites
1. Android Studio (latest stable recommended).
2. Android SDK Platform 36, minimum supported device runs Android 8.0 (API 26).
3. JDK 21.

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
   ./gradlew installDebug        # macOS/Linux
   gradlew.bat installDebug      # Windows
   ```

### Initializing Engines
1. Complete the initial landing and configuration wizard sequence.
2. Select your preferred engine model (Gemma 4 family, with variants tuned for generic LiteRT, Qualcomm, and Google Tensor hardware).
3. The app will download and stamp the model to local storage (a multi-gigabyte first-run download).
4. Toggle GPU Offload or NPU acceleration if supported by your hardware, and begin execution.

---

## License

Released under the [MIT License](LICENSE).
