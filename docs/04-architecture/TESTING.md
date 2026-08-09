# Testing Strategy

## Philosophy
Every dependency must be replaceable. Every interface should have a fake implementation for tests.

## Layers
1. **Unit Tests**: Provider mocks, Driver mocks, Speech mocks, Plugin validation.
2. **Integration Tests**: Workflow execution without UI.
3. **Instrumentation Tests**: BLE emulator, Fake wearable driver, Latency simulator.

## Deterministic simulator

The JVM module `drivers/simulator` provides `SimulatedWearableHost`,
`SimulatedDriver`, `SimulatedAudioSource`, `SimulatedAudioOutput`, and
`SimulatedClock`. It is the canonical host for tests that must exercise runtime
behavior without Android, Bluetooth, or physical hardware.

The simulator supports deterministic connection, button, battery/charging,
wear-state, PCM injection, spoken-output capture, and virtual-time advancement.
Tests should inject a test `CoroutineScope` when they need scheduler control and
must disconnect the host during cleanup.

## Mock Drivers
A `MockWearableDriver` is mandatory for testing. It simulates button presses, injects PCM audio data, and toggles battery states to test the Runtime's resilience.
