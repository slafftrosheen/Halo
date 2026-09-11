# Halo

**Halo is a Portable Cognitive Runtime for Wearable Computing.**

The Android application is the first host, not the product boundary. Halo is designed around a device-agnostic runtime that can observe, interpret, reason, plan and execute across wearable hardware.

### Product principles

- **Runtime is the product** — host applications are replaceable adapters.
- **Device agnostic** — hardware interaction stays behind stable runtime/device interfaces.
- **Local first** — local inference and on-device processing are first-class paths; network providers are optional.
- **Truthful capabilities** — UI and runtime expose only verified or explicitly qualified device behavior.
- **Halo Glass** — dark, layered glass surfaces with a restrained technical/pixel signal language.

## Documentation

Start with [`docs/README.md`](docs/README.md) for the documentation hierarchy and [`PROJECT-DIRECTION.md`](PROJECT-DIRECTION.md) for canonical project direction.

The visual/UI authority is [`docs/03-design/halo-glass/HALO-GLASS-DESIGN.md`](docs/03-design/halo-glass/HALO-GLASS-DESIGN.md).

## Current development focus

The current implementation is being hardened around real wearable operation: device sessions, HeyCyan transport/protocol boundaries, speech lifecycle, runtime observation/replay, typed AI provider routing, and the primary Compose experience.

HeyCyan protocol work remains evidence-backed. Unsupported or unverified protocol operations must not be presented as working controls.

## Privacy

Halo is designed for local-first operation with no requirement for a cloud service or remote storage. Optional network providers are explicit integrations and must remain visible as such.

## Acknowledgments

A special thanks to the [FerSaiyan/Alternative-HeyCyan-App-and-SDK](https://github.com/FerSaiyan/Alternative-HeyCyan-App-and-SDK) repository. Its work and source description were valuable references during extraction and reverse-engineering of the HeyCyan hardware protocol.
