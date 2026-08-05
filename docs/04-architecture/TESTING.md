# Testing Strategy

## Philosophy
Every dependency must be replaceable. Every interface should have a fake implementation for tests.

## Layers
1. **Unit Tests**: Provider mocks, Driver mocks, Speech mocks, Plugin validation.
2. **Integration Tests**: Workflow execution without UI.
3. **Instrumentation Tests**: BLE emulator, Fake wearable driver, Latency simulator.

## Mock Drivers
A `MockWearableDriver` is mandatory for testing. It simulates button presses, injects PCM audio data, and toggles battery states to test the Runtime's resilience.
