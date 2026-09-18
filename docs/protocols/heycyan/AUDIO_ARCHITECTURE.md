# HeyCyan audio implementation

Status: CURRENT SOURCE MAP. Reviewed 2026-09-18.

[HeyCyanAudioSource](../../../drivers/heycyan/src/main/java/com/halo/drivers/heycyan/HeyCyanAudioSource.kt)
creates RawOpusAudioDecoder, observes HeyCyanVoiceSession, and decodes RawAudioFrame payloads
only while that session is active. Its frames flow exposes decoder output.
The code exists; the old “AudioSource not yet implemented” statement is obsolete.

This is not proof of codec correctness for every packet, actual microphone origin, measured
sample rate, loss, latency or end-to-end speech on physical glasses. A comment saying VERIFIED
is not a capture/run record. Retain packet boundaries and check the real decoder's format;
do not infer that concatenating arbitrary 40-byte chunks is a safe universal audio format.

[HostGraph](../../../app/src/main/java/com/halo/app/di/HostGraph.kt) injects phone
AndroidAudioSource into AudioPerceptionEngine. HybridAudioSource exists but is not selected
there. The capability catalog separately marks audio.live_input unsupported. These code
contradictions/integration gaps need resolution before advertising wearable speech.

Local file recording, live BLE input and Bluetooth speaker routing are distinct paths.
Their implementation and acceptance must be recorded separately.
