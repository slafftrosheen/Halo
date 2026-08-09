# HeyCyan Physical Discovery Checklist

This checklist is the manual acceptance procedure for PR-015. The workflow is
**BLE PROTOCOL DISCOVERY**, not HeyCyan identification. It collects evidence only:
it does not invoke `HaloRuntime`, decode protocol packets, or send speculative commands.

## Preconditions

- Use the physical device under investigation and an Android phone with Bluetooth enabled.
- Grant Bluetooth scan/connect permissions to Halo.
- Keep other BLE devices away from the test area where practical.
- Open **BLE Protocol Discovery** from the Halo developer navigation.
- Save each scenario as a separate capture. The application stores captures in `captures/heycyan/<capture-id>.json` within its app files directory.

## Workflow

1. Press **Scan**.
2. Confirm that advertisements from multiple nearby BLE devices can be listed without automatic connection.
3. Select one address explicitly and verify the selected row. Do not assume the device is HeyCyan from its name.
4. Press **Connect**.
5. Wait for **Ready**. The transport performs GATT discovery and subscribes to notification/indication characteristics.
6. Perform only the controlled actions for the current scenario.
7. Press **Save Capture**. This disconnects and persists the existing recorder session.
8. Copy the JSON artifact from the application files directory into the repository capture archive when the session is ready to commit.
9. Replay the JSON offline with the existing capture replay tooling before analyzing any bytes.

Every capture must include the advertisement, connection transitions, services, characteristics, descriptors, descriptor writes, MTU observations, raw RX/TX packets, transport-ready transition, and disconnect timeline where the device exposes those observations.

## Capture A — Boot

Filename: `heycyan-boot-001.json`

- Start with the glasses powered off.
- Power on the glasses.
- Scan, explicitly select the glasses, and connect.
- Wait for Ready.
- Remain idle for 30 seconds.
- Save the capture.

## Capture B — Buttons

Filename: `heycyan-buttons-001.json`

- Connect and wait for Ready.
- Perform one single press, then wait.
- Perform one double press, then wait.
- Perform one long press, then wait.
- Save the capture.

Do not label any packet as a button event until the controlled action has been correlated against the raw timeline.

## Capture C — Battery

Filename: `heycyan-battery-001.json`

- Connect and wait for Ready.
- Observe idle battery traffic.
- If supported, record a charging transition and observe the resulting traffic.
- Save the capture.

## Capture D — Reconnect

Filename: `heycyan-reconnect-001.json`

- Connect and wait for Ready.
- Disconnect the glasses or otherwise end the link.
- Scan again, explicitly select the same device, and reconnect.
- Save the capture after the second Ready/disconnect cycle.

## Evidence rules

- Treat all packets as raw and unknown during discovery.
- Do not issue battery, media, camera, audio, Wi-Fi, or other device commands.
- Do not promote synthetic fixture definitions into production.
- Discovery Mode must not start a notification decoder or command encoder.
- The runtime must remain unused during discovery.
- A real capture must be correlated with a controlled action before it can update `BLE_PROTOCOL.md`.
- Record the capture ID and exact scenario in the protocol analysis notes.
