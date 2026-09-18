# Halo

Status: CURRENT SOURCE MAP. Updated 2026-09-18.

Halo is an Android application and modular cognitive-runtime project for phone and wearable
interaction. The repository includes Compose UI, speech/cognition paths, local/network
providers, Android capability adapters, HeyCyan integration and simulator tooling.

Start with [current state](CURRENT-STATE.md), [documentation](docs/README.md) and
[the source audit](docs/engineering/DOCUMENTATION-AUDIT.md). Current code and actual wiring
are the source of truth for implementation. Specifications describe intent; code presence
does not certify passing tests, security properties or physical device behavior.

The next requested direction is [Halo Presence + Capable Assistant](docs/14-roadmap/NEXT-PHASE-2027.md):
expand the pixel orb and interface, connect useful assistant actions, and validate remaining
requirements. This work is planned, not shipped.

Local processing and user-controlled providers are product goals. Optional network integrations
exist. Read [security limits](docs/12-security/THREAT_MODEL.md) before assuming all processing
is on-device or credentials are application-encrypted.

Thanks to [Alternative HeyCyan App and SDK](https://github.com/FerSaiyan/Alternative-HeyCyan-App-and-SDK)
for protocol reference material.
