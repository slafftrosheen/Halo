#!/usr/bin/env python3
"""
HeyCyan Forensics Recorder

A PC-side tool for systematic BLE protocol discovery using controlled
interaction captures. Records raw packets during specific button/touch
interactions to enable differential byte analysis.
"""

import asyncio
import json
import sys
import time
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path
from typing import List, Dict, Any, Optional

try:
    from bleak import BleakClient, BleakScanner
    from bleak.backends.device import BLEDevice
except ImportError:
    print("ERROR: bleak not installed. Run: pip install bleak")
    sys.exit(1)


@dataclass
class Packet:
    """A single BLE packet with metadata."""
    timestamp: float
    direction: str  # RX (from device) or TX (to device)
    characteristic: str
    payload_hex: str
    payload_bytes: bytes = field(repr=False, compare=False)


@dataclass  
class Capture:
    """A complete capture session with all packets."""
    session_id: str
    device_name: str
    device_address: str
    action_id: str
    action_label: str
    timestamp: str
    gatt_services: List[Dict] = field(default_factory=list)
    packets: List[Packet] = field(default_factory=list)
    
    def to_dict(self) -> Dict[str, Any]:
        return {
            "session_id": self.session_id,
            "device": {
                "name": self.device_name,
                "address": self.device_address,
            },
            "action": {
                "id": self.action_id,
                "label": self.action_label,
            },
            "timestamp": self.timestamp,
            "gatt": {
                "services": self.gatt_services,
            },
            "packets": [
                {
                    "timestamp": p.timestamp,
                    "direction": p.direction,
                    "characteristic": p.characteristic,
                    "payload_hex": p.payload_hex,
                }
                for p in self.packets
            ],
        }


def make_notification_handler(recorder_self, char_uuid: str):
    """Factory to create a notification handler with captured UUID."""
    def handler(data: bytearray):
        recorder_self.notification_handler(char_uuid, data)
    return handler


class InteractionRecorder:
    """Records BLE interactions for forensic analysis."""
    
    # Known HeyCyan device name patterns (from documentation and hardware)
    HEYCYAN_PATTERNS = [
        "HeyCyan",
        "Cyan",
        "BW-AG1",  # HeyCyan glasses model
        "AG1",      # Common abbreviation  
        "Glasses",  # General descriptor
    ]
    
    # Command characteristics to try for initialization
    COMMAND_CHARACTERISTICS = [
        "0000ae03-0000-1000-8000-00805f9b34fb",  # System Command
        "de5bf728-d711-4e47-af26-65e3012a5dc7",  # Protocol Command
        "0000fee3-0000-1000-8000-00805f9b34fb",  # MCU Status
    ]
    
    INTERACTION_TESTS = [
        {"id": "baseline", "label": "BASELINE - Idle 5 seconds"},
        {"id": "ai_single", "label": "AI BUTTON — SINGLE PRESS"},
        {"id": "ai_double", "label": "AI BUTTON — DOUBLE PRESS"},
        {"id": "ai_long", "label": "AI BUTTON — LONG PRESS"},
        {"id": "camera_single", "label": "CAMERA BUTTON — SINGLE"},
        {"id": "camera_double", "label": "CAMERA BUTTON — DOUBLE"},
        {"id": "touch_left", "label": "TOUCH — SWIPE LEFT"},
        {"id": "touch_right", "label": "TOUCH — SWIPE RIGHT"},
        {"id": "touch_tap", "label": "TOUCH — CENTER TAP"},
        {"id": "touch_double", "label": "TOUCH — CENTER DOUBLE TAP"},
        {"id": "touch_hold", "label": "TOUCH — CENTER HOLD"},
        {"id": "wear_off", "label": "WEAR SENSOR — REMOVE"},
        {"id": "wear_on", "label": "WEAR SENSOR — PUT ON"},
    ]
    
    def __init__(self, capture_dir: Path):
        self.capture_dir = capture_dir
        self.capture_dir.mkdir(parents=True, exist_ok=True)
        self.client: Optional[BleakClient] = None
        self.device: Optional[BLEDevice] = None
        self.packets: List[Packet] = []
        self.gatt_services: List[Dict] = []
        self.session_counter = 0
        self.session_start_time = 0.0
        self.subscribed_chars = []
        self._callback_registered = False
        
    def notification_handler(self, characteristic_uuid: str, data: bytearray):
        """Handle incoming BLE notifications."""
        timestamp = time.time()
        
        packet = Packet(
            timestamp=timestamp,
            direction="RX",
            characteristic=characteristic_uuid,
            payload_hex=data.hex().upper(),
            payload_bytes=bytes(data)
        )
        self.packets.append(packet)
        
        delta = packet.timestamp - self.session_start_time
        print(f"  [{delta:.3f}s] RX {characteristic_uuid[:8]}... {len(data)}B: {data.hex().upper()}")
    
    def dump_gatt_services(self):
        """Dump all GATT services and characteristics."""
        self.gatt_services = []
        print("\nGATT Services:")
        print("-" * 40)
        
        for service in self.client.services:
            service_info = {
                "uuid": str(service.uuid),
                "characteristics": []
            }
            print(f"\nSERVICE {service.uuid}")
            
            for char in service.characteristics:
                props = list(char.properties)
                char_info = {
                    "uuid": str(char.uuid),
                    "properties": props,
                }
                service_info["characteristics"].append(char_info)
                
                print(f"  CHARACTERISTIC {char.uuid}")
                print(f"    properties: {', '.join(props)}")
                
                for desc in char.descriptors:
                    print(f"    DESCRIPTOR {desc.uuid}")
                    
            self.gatt_services.append(service_info)
    
    def _find_char_by_uuid(self, target_uuid: str):
        """Find a characteristic by its UUID."""
        for service in self.client.services:
            for char in service.characteristics:
                if str(char.uuid) == target_uuid:
                    return char
        return None
    
    async def initialize_notifications(self):
        """Initialize notifications by reading some characteristics first."""
        print("\nInitializing device state...")
        
        # Try to read initial values from key characteristics to "wake" the device
        for service in self.client.services:
            for char in service.characteristics:
                if "read" in char.properties:
                    try:
                        value = await self.client.read_gatt_char(char.uuid)
                        print(f"  Read {char.uuid[:8]}: {value.hex().upper()}")
                    except Exception as e:
                        pass  # Ignore read errors
        
        # Now subscribe to notifications
        print("\nSubscribing to notifications...")
        
        for service in self.client.services:
            for char in service.characteristics:
                char_uuid = str(char.uuid)
                
                if "notify" in char.properties:
                    try:
                        handler = make_notification_handler(self, char_uuid)
                        await self.client.start_notify(char_uuid, handler)
                        print(f"  Subscribed to {char_uuid}")
                        self.subscribed_chars.append(char_uuid)
                    except Exception as e:
                        print(f"  Failed to subscribe to {char_uuid}: {e}")
                
                elif "indicate" in char.properties:
                    try:
                        handler = make_notification_handler(self, char_uuid)
                        await self.client.start_notify(char_uuid, handler)
                        print(f"  Subscribed to {char_uuid} (indicate)")
                        self.subscribed_chars.append(char_uuid)
                    except Exception as e:
                        print(f"  Failed to subscribe to {char_uuid}: {e}")
        
        # Try sending initialization commands to wake up the device
        print("\nSending initialization commands...")
        for char_uuid in self.COMMAND_CHARACTERISTICS:
            char = self._find_char_by_uuid(char_uuid)
            if char is None:
                continue
            # Try a simple ping/write command
            if "write" in char.properties:
                # Try various command formats that might trigger notifications
                commands_to_try = [
                    b'\x00\x01',  # Start command
                    b'\x01',      # Single byte start
                    b'\x01\x00',  # Two byte start
                    bytes([0x55, 0x55]),  # Sync pattern
                    bytes([0x01, 0x01]),  # Alternative start
                ]
                for cmd in commands_to_try:
                    try:
                        await self.client.write_gatt_char(char_uuid, cmd)
                        print(f"  Sent {cmd.hex().upper()} to {char_uuid[:8]}...")
                        await asyncio.sleep(0.05)
                    except Exception as e:
                        pass  # Try next command

    async def find_device(self) -> Optional[BLEDevice]:
        """Find a HeyCyan device."""
        print("Scanning for BLE devices (15s)...")
        print("Place your HeyCyan glasses in visible range and ready to pair.")
        
        devices = await BleakScanner.discover(timeout=15.0)
        
        candidates = []
        for device in devices:
            name = device.name or "Unknown"
            for pattern in self.HEYCYAN_PATTERNS:
                if pattern in name:
                    candidates.append(device)
                    rssi = getattr(device, 'rssi', 'N/A')
                    print(f"  Found: {name} ({device.address}) RSSI: {rssi}")
                    break
        
        if not candidates:
            print("\nNo HeyCyan devices found.")
            print("All nearby devices:")
            for device in devices:
                name = device.name or "Unknown"
                rssi = getattr(device, 'rssi', 'N/A')
                print(f"  {name} ({device.address}) RSSI: {rssi}")
            return None
        
        if len(candidates) == 1:
            return candidates[0]
        
        print("\nMultiple candidates found.")
        for i, device in enumerate(candidates):
            rssi = getattr(device, 'rssi', 'N/A')
            print(f"  [{i+1}] {device.name} ({device.address}) RSSI: {rssi}")
        
        try:
            choice = int(input("Selection (Enter): ")) - 1
            if choice < 0 or choice >= len(candidates):
                print("Invalid selection. Using first candidate.")
                return candidates[0]
            return candidates[choice]
        except (ValueError, IndexError):
            print("Invalid input. Using first candidate.")
            return candidates[0]
    
    def countdown(self, seconds: int):
        """Display a countdown."""
        for i in range(seconds, 0, -1):
            print(f"  {i}...", flush=True)
            time.sleep(1)
    
    def save_capture(self, capture: Capture) -> Path:
        """Save a capture to a JSON file."""
        filepath = self.capture_dir / f"{capture.session_id}.json"
        with open(filepath, 'w') as f:
            json.dump(capture.to_dict(), f, indent=2)
        return filepath
    
    def capture_window(self, label: str, baseline_sec: float = 2.0, record_sec: float = 3.0) -> List[Packet]:
        """Capture a window of packets around an action."""
        print(f"\nCollecting {int(baseline_sec)}s baseline...")
        self.countdown(int(baseline_sec))
        
        start_idx = len(self.packets)
        self.session_start_time = time.time()
        
        print("\n>>> PERFORM ACTION NOW <<<")
        self.countdown(3)
        
        print(f"\nRecording {int(record_sec)}s post-action...")
        time.sleep(record_sec)
        
        packets_captured = len(self.packets[start_idx:])
        print(f"Packets received during this window: {packets_captured}")
        
        return self.packets[start_idx:]
    
    def run_test_action(self, test: Dict) -> Capture:
        """Run a single test action with timing window."""
        test_id = test["id"]
        test_label = test["label"]
        
        print("\n" + "=" * 60)
        print(f"ACTION: {test_label}")
        print("=" * 60)
        
        window_packets = self.capture_window(test_label)
        
        self.session_counter += 1
        capture_id = f"{test_id}-{self.session_counter:03d}"
        
        capture = Capture(
            session_id=capture_id,
            device_name=self.device.name if self.device else "Unknown",
            device_address=self.device.address if self.device else "Unknown",
            action_id=test_id,
            action_label=test_label,
            timestamp=datetime.now().isoformat(),
            gatt_services=self.gatt_services,
            packets=window_packets
        )
        
        return capture
    
    async def run(self):
        """Main recording loop."""
        print("=" * 60)
        print("HEY CYAN FORENSICS RECORDER v2")
        print("=" * 60)
        
        self.device = await self.find_device()
        if not self.device:
            print("\nNo device found. Is your HeyCyan glasses powered on and in pairing mode?")
            return
        
        print(f"\nFound: {self.device.name} ({self.device.address})")
        
        print("\nConnecting...")
        self.client = BleakClient(self.device)
        
        try:
            await self.client.connect()
        except Exception as e:
            print(f"Connection failed: {e}")
            return
        
        if not self.client.is_connected:
            print("Failed to connect. Exiting.")
            return
        
        print("Connected!")
        print(f"MTU: {self.client.mtu_size}")
        
        # Dump GATT services
        self.dump_gatt_services()
        
        # Initialize notifications and send wake-up commands
        await self.initialize_notifications()
        
        print(f"\nSubscribed to {len(self.subscribed_chars)} characteristics")
        print(f"Sent initialization commands to {len(self.COMMAND_CHARACTERISTICS)} command characteristics")
        
        # Wait a moment for device to respond
        print("\nWaiting 2 seconds for device to start streaming...")
        await asyncio.sleep(2)
        
        print(f"\nCaptured so far: {len(self.packets)} packets")
        
        print("\nREADY. Starting interaction recordings...")
        
        all_captures = []
        
        for test in self.INTERACTION_TESTS:
            self.packets = []
            self.session_start_time = time.time()
            
            capture = self.run_test_action(test)
            
            if test["id"] == "baseline":
                pass
            else:
                filepath = self.save_capture(capture)
                all_captures.append(capture)
                print(f"Saved: {filepath}")
                print(f"Packets captured: {len(capture.packets)}")
                if len(capture.packets) == 0:
                    print("  WARNING: No packets captured!")
                    print("  The device may not be emitting notifications yet.")
                    print("  Try touching/wearing the glasses while holding the device.")
                print("\nWaiting 2 seconds...")
                time.sleep(2)
        
        # Save baseline
        baseline_capture = Capture(
            session_id="baseline-001",
            device_name=self.device.name,
            device_address=self.device.address,
            action_id="baseline",
            action_label="BASELINE - Idle 5 seconds",
            timestamp=datetime.now().isoformat(),
            gatt_services=self.gatt_services,
            packets=self.packets
        )
        filepath = self.save_capture(baseline_capture)
        all_captures.insert(0, baseline_capture)
        print(f"\nSaved: {filepath}")
        
        print("\n" + "=" * 60)
        print("CAPTURE SUMMARY")
        print("=" * 60)
        print(f"Total captures: {len(all_captures)}")
        for cap in all_captures:
            print(f"  {cap.session_id}: {len(cap.packets)} packets")
        
        print(f"\nCaptures saved to: {self.capture_dir}")
        print("\nKey notification characteristic: de5bf729-d711-4e47-af26-65e3012a5dc7")
        
        await self.client.disconnect()


def main():
    capture_dir = Path(__file__).parent / "captures"
    recorder = InteractionRecorder(capture_dir)
    
    try:
        asyncio.run(recorder.run())
    except KeyboardInterrupt:
        print("\nInterrupted by user")
    except Exception as e:
        print(f"Error: {e}")
        import traceback
        traceback.print_exc()


if __name__ == "__main__":
    main()