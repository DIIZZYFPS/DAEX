# About DAEX

DAEX (Daedalus Execution Engine) is an Android app that runs Icarus, an AI assistant, entirely on your device. There are no cloud servers involved in generating a response — once the AI model is downloaded, the app keeps working with airplane mode on. Icarus runs on open-weight language models (such as Google's Gemma family) through Google's LiteRT-LM on-device inference engine, using your phone's CPU, GPU, or NPU depending on what your hardware supports.

## Choosing and Switching Models
You can pick from several on-device model variants from the Marketplace, differing in size and capability — smaller models respond faster and use less RAM, larger models reason more capably but need more RAM and storage. Settings shows hardware diagnostics (total RAM, free storage, Vulkan API support, NPU library availability) so you can judge what your device can comfortably run. Switching to a new model requires downloading its files once; downloaded models can be deleted later from Settings to free up storage.

## Document Search (RAG)
Upload PDF or text files from the "Offline Knowledge Base" section in Settings. Icarus splits each document into overlapping chunks, generates an on-device vector embedding for every chunk, and also indexes the raw text for keyword search. When a document is attached to a conversation and you ask a question, Icarus retrieves the most relevant chunks using a hybrid of semantic (vector) search and keyword (BM25) search, merges the results, and answers grounded in that retrieved content instead of guessing. Documents can be attached or detached per conversation from the document library, and removed from the library entirely when no longer needed.

## Cross-Conversation Search
Every conversation is saved and searchable, not just the one currently open. The Sidebar has a "Search conversations..." box that searches the actual message content of every past conversation (not just conversation titles), using the same hybrid semantic-plus-keyword search as document RAG, and lets you jump straight to the matching conversation. Icarus can also search past conversations on its own initiative, as a tool it calls only when it judges doing so is relevant to what you're currently asking — search results are never automatically pulled into every conversation's context, specifically so unrelated history from other chats doesn't leak into whatever you're discussing right now.

## Native Tool Calling
When "Native Tool Calling" is enabled in Settings, Icarus can call small on-device tools directly instead of just describing what it would do. Read-only tools, enabled by default: checking the current date and local time, battery level and charging state, free and total storage space, and device manufacturer/model/OS version. Action-taking tools, off by default for privacy: launching other installed apps by name, sending email through your device's email client with a prefilled draft (you still send it), and running native system intents to trigger device actions. Also off by default: recalling past conversations, since it exposes personal chat history to the model. Every one of these tools can be individually enabled or disabled under Settings → Agent Options, nested below the main "Native Tool Calling" switch — turning that master switch off disables all tool use at once.

## Live Voice Mode
Tap the microphone button to start a live voice conversation instead of typing. Your speech is transcribed in real time on-device, and Icarus speaks its replies back using a local text-to-speech engine (Kokoro) — no audio is ever sent anywhere. The voice session supports interruption (you can start talking again while Icarus is still speaking), voice activity detection to know when you've stopped talking, and short audio chimes marking when a voice session starts and ends.

## Read Aloud
Outside of live voice sessions, any individual message in a chat — yours or Icarus's — can be read aloud by tapping the "LISTEN" control on that message bubble. This reuses the same on-device Kokoro text-to-speech engine as live voice mode, just triggered per-message on demand rather than automatically during a live session. Tapping the control again while it's speaking stops playback immediately.

## Persistent Memory
Icarus keeps a Global Core Memory: a single markdown file of durable facts and preferences about you, automatically curated in the background as you chat. Curation isn't silent — after it runs, a system log line in the chat summarizes what was actually learned (or notes that nothing new surfaced that round). You can review and directly edit the memory file yourself from Settings at any time. This global memory is separate from ordinary conversation history: every message in every conversation is saved to the device and can be revisited any time from the Sidebar, whether or not any of it ever gets summarized into Core Memory.

## Saved Prompt Library
Any message — something you typed, or something Icarus said — can be pinned into a personal saved-prompt library by tapping "PIN" on its message bubble. Open the library from the Sidebar's "Saved Prompts" entry to see everything you've pinned; tapping a saved entry starts a brand-new conversation with that exact text sent immediately, without needing to retype it.

## Personalization and Tuning
Settings lets you set a custom system prompt that's added on top of Icarus's default persona rather than replacing it, so core behavior (like not sounding like a generic AI assistant, and correctly handling relative timestamps in chat history) stays intact even with custom instructions active. You can also adjust inference parameters (temperature, top-k, top-p, and response verbosity/max tokens), toggle whether Icarus's reasoning/"thinking" process is shown, choose a dark or light theme and accent color, and toggle haptic feedback and ambient visual effects.

## Privacy
Model inference, document indexing and search, voice transcription, and speech synthesis all run locally on your device using on-device models — none of it depends on a network connection to work. Nothing you type, upload, say out loud, or that Icarus generates is uploaded to any server. The only network activity DAEX ever does is the one-time download of model files themselves.
