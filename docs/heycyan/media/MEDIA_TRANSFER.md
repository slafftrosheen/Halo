# HeyCyan Media Transfer

Media downloads (like photos or videos captured by the glasses) are retrieved over an HTTP server hosted directly on the glasses (via a Wi-Fi connection).

## Endpoints

1. **Manifest**
   - **URL**: `GET http://<DEVICE_IP>/files/media.config`
   - **Purpose**: Returns a list of available media files. The format of `media.config` is parsed by `HeyCyanMediaConfigParser` into a `HeyCyanMediaManifest`.

2. **File Download**
   - **URL**: `GET http://<DEVICE_IP>/files/<filename>`
   - **Purpose**: Downloads the raw media file.
   - **Resumable**: Supports standard HTTP `Range` headers for partial/resumed downloads.

## Implementation Details
In Halo, `DefaultHeyCyanMediaApi` handles this transfer. It streams the input directly to the destination file and supports integrity validation using an injected SHA-256 stream, avoiding excessive memory usage.

*(Derived from prior iOS/Android Bridge SDK analysis and implemented in Halo `DefaultHeyCyanMediaApi.kt`)*
