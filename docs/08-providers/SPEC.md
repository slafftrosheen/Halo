# Provider SDK Specification

Providers are the AI backends (e.g., llama.cpp, Gemini, OpenAI).

## Responsibilities
Providers implement:
* **Chat**
* **Vision**
* **Embeddings**
* **Speech** (optional)
* **Streaming**
* **Metadata**

No Android. No UI. No memory.

## Provider Object
Providers are self-describing and immutable once loaded.
They define:
* Latency class
* Context Window size
* Vision support
* Streaming support
* Offline capability
* Cost metric
* Privacy class

## Provider Sandbox
Providers never know about Plugins, Drivers, Android, or Memory.
They only receive:
* `Prompt`
* `Options`
* `Attachments`
* `Cancellation Token`

They return a standardized `Result`.

## AI Router
The AI Router is a workflow node that selects the appropriate provider based on the Intent and Context.
* Simple -> Android local processing
* Complex -> LLM
* Vision -> Vision Provider
* Offline required -> Local Provider
