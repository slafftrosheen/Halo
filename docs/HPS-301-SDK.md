# HPS-301: SDK Specification (HAL)

Hardware Abstraction Layer (HAL). Everything uses interfaces.

## Driver
Must implement:
* Discovery
* Connection
* Streaming
* Capabilities
* Diagnostics

Never AI. Never UI.

## Provider
Must implement:
* Chat
* Vision
* Streaming
* Embeddings
* Metadata

## Plugin
Must implement:
* Manifest
* Capabilities
* Commands
* Events
* Configuration
* Permissions

## Runtime API
Every engine receives: `Context`, `CommandBus`, `QueryBus`, `EventBus`, `Logger`, `Configuration`, `Clock`.
Nothing Android.
