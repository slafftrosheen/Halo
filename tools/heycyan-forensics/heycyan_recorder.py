#!/usr/bin/env python3
"""
HeyCyan Forensics Recorder - PR-017C

Passive BLE protocol recorder for systematic discovery of HeyCyan smart
glasses communication protocols.

This is a STRICTLY PASSIVE recorder:
- No automatic writes or commands
- No speculative protocol guesses
- Records raw BLE traffic for offline analysis

Usage:
    pip install bleak
    python heycyan_recorder.py
"""

import asyncio
import json
import sys
import time
from collections import Counter, defaultdict
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
    mtu: int
    action_id: str
    action_label: str
    capture_started_at: str
    capture_ended_at: str
    action_timestamp: Optional[str]
    gatt_services: List[Dict] = field(default_factory=list)
    subscription_results: List[Dict] = field(default_factory=list)
    packets: List[Packet] = field(default_factory=list)
    _action_ts: float = field(default=0.0, repr=False, compare=False)
    
    def to_dict(self) -> Dict[str, Any]:
        return {
            "session_id": self.session_id,
            "device": {
                "name": self.device_name,
                "address": self.device_address,
            },
            "connection": {
                "mtu": self.mtu,
            },
            "action": {
                "id": self.action_id,
                "label": self.action_label,
            },
            "capture": {
                "started_at": self.capture_started_at,
                "ended_at": self.capture_ended_at,
                "action_timestamp": self.action_timestamp,
            },
            "gatt": {
                "services": self.gatt_services,
            },
            "subscriptions": self.subscription_results,
            "packets": [
                {
                    "timestamp": p.timestamp,
                    "direction": p.direction,
                    "characteristic": p.characteristic,
                    "payload_hex": p.payload_hex,
                    "relative_to_action_ms": round(
                        (p.timestamp - self._action_ts) * 1000, 2
                    ) if self._action_ts else None,
                }
                for p in self.packets
            ],
        }
    
    def set_action_timestamp(self, ts: float):
        """Set the action timestamp for relative calculations."""
        self._action_ts = ts
        self.action_timestamp = datetime.fromtimestamp(ts).isoformat()


class PassiveForensicsRecorder:
    """
    PASSIVE BLE forensics recorder - sends nothing, records everything.
    
    Implements PR-017C: 90-second passive capture with delayed stream verification.
    """
    
    HEYCYAN_PATTERNS = ["HeyCyan", "Cyan", "BW-AG1", "AG1", "Glasses"]
    
    # Default passive capture duration (PR-017C: 90 seconds)
    DEFAULT_PASSIVE_SECONDS = 90
    
    def __init__(self, capture_dir: Path, passive_seconds: float = None):
        self.capture_dir = capture_dir
        self.capture_dir.mkdir(parents=True, exist_ok=True)
        self.client: Optional[BleakClient] = None
        self.device: Optional[BLEDevice] = None
        self.packets: List[Packet] = []
        self.gatt_services: List[Dict] = []
        self.subscription_results: List[Dict] = []
        self.notification_count = 0
        self.first_tx: Optional[float] = None
        self.last_notification_time: Optional[float] = None
        self.mtu: int = 0
        self._action_ts: float = 0
        self.passive_seconds = passive_seconds or self.DEFAULT_PASSIVE_SECONDS
        
    def make_notification_handler(self):
        """
        Create a notification handler with the CORRECT Bleak signature.
        
        Bleak callbacks receive (characteristic, data), NOT just data.
        """
        def handler(characteristic, data: bytearray):
            self._handle_notification(str(characteristic.uuid), bytes(data))
        return handler
    
    def _handle_notification(self, characteristic_uuid: str, data: bytes):
        """Handle an incoming BLE notification."""
        timestamp = time.time()
        
        if self.first_tx is None:
            self.first_tx = timestamp
        
        self.notification_count += 1
        self.last_notification_time = timestamp
        
        packet = Packet(
            timestamp=timestamp,
            direction="RX",
            characteristic=characteristic_uuid,
            payload_hex=data.hex(" ").upper(),
            payload_bytes=bytes(data),
        )
        self.packets.append(packet)
        
        elapsed = timestamp - (self._action_ts if self._action_ts else time.time())
        print(
            f"  [RX +{elapsed:7.3f}s] {characteristic_uuid} {len(data):3d}B: {data.hex(' ').upper()[:48]}",
            flush=True,
        )
    
    def dump_gatt_services(self):
        """Dump all GATT services and characteristics with descriptors."""
        self.gatt_services = []
        print("\nGATT Services:")
        print("-" * 40)
        
        for service in self.client.services:
            service_info = {
                "uuid": str(service.uuid),
                "characteristics": [],
            }
            print(f"\nSERVICE {service.uuid}")
            
            for char in service.characteristics:
                props = list(char.properties)
                char_info = {
                    "uuid": str(char.uuid),
                    "properties": props,
                    "descriptors": [],
                }
                service_info["characteristics"].append(char_info)
                
                print(f"  CHARACTERISTIC {char.uuid}")
                print(f"    properties: {', '.join(props)}")
                
                for desc in char.descriptors:
                    char_info["descriptors"].append({"uuid": str(desc.uuid)})
                    print(f"    DESCRIPTOR {desc.uuid}")
                    
            self.gatt_services.append(service_info)
    
    async def subscribe_to_notifications(self):
        """
        Subscribe to all notify/indicate characteristics.
        Records results with success/failure status.
        """
        print("\nSubscribing to notifications...")
        self.subscription_results = []
        
        handler = self.make_notification_handler()
        
        for service in self.client.services:
            for char in service.characteristics:
                char_uuid = str(char.uuid)
                
                if "notify" in char.properties:
                    mode = "notify"
                elif "indicate" in char.properties:
                    mode = "indicate"
                else:
                    continue
                
                result = {
                    "characteristic": char_uuid,
                    "mode": mode,
                    "success": False,
                    "error": None,
                }
                
                try:
                    await self.client.start_notify(char_uuid, handler)
                    result["success"] = True
                    print(f"  Subscribed to {char_uuid}")
                except Exception as e:
                    result["error"] = str(e)
                    print(f"  Failed: {char_uuid}: {e}")
                
                self.subscription_results.append(result)
    
    async def find_device(self) -> Optional[BLEDevice]:
        """Find a HeyCyan device."""
        print(f"Scanning for BLE devices (15s)...")
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
            print("\nAll nearby devices:")
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
    
    async def passive_capture(self, duration_sec: float, label: str = "PASSIVE") -> List[Packet]:
        """
        Perform a passive capture for the specified duration.
        Prints elapsed time continuously.
        """
        print(f"\n{'=' * 60}")
        print(f"{label}")
        print(f"Listening for {int(duration_sec)} seconds...")
        print("Estimated first activity may take >20 seconds after subscription.")
        print("=" * 60)
        
        start_time = time.time()
        self._action_ts = start_time
        
        print(f"\n[PASSIVE +000.0s] listening")
        
        # Print a dot every second to show we're alive
        async def ticker():
            while True:
                await asyncio.sleep(1)
                elapsed = time.time() - start_time
                print(f"\r[PASSIVE +{elapsed:.1f}s] listening...", end="", flush=True)
        
        ticker_task = asyncio.create_task(ticker())
        
        await asyncio.sleep(duration_sec)
        
        ticker_task.cancel()
        
        print(f"\n[PASSIVE +{duration_sec:.1f}s] done")
        
        return self.packets
    
    def save_capture(self, capture: Capture) -> Path:
        """Save a capture to a JSON file."""
        filepath = self.capture_dir / f"{capture.session_id}.json"
        with open(filepath, 'w') as f:
            json.dump(capture.to_dict(), f, indent=2)
        return filepath
    
    def print_statistics(self):
        """Print detailed packet statistics by characteristic."""
        by_char = defaultdict(list)
        for p in self.packets:
            by_char[p.characteristic].append(p)
        
        print("\n" + "=" * 60)
        print("RX SUMMARY")
        print("=" * 60)
        
        if not self.packets:
            print("\nNo notifications received.")
            return
        
        if self.first_tx:
            first_elapsed = self.first_tx - self._action_ts
            print(f"\nFIRST RX:")
            print(f"  elapsed:     {first_elapsed:.3f}s")
            first_p = by_char[self.packets[0].characteristic][0]
            print(f"  characteristic: {first_p.characteristic}")
            print(f"  length:        {len(first_p.payload_bytes)}B")
            print(f"  raw:           {first_p.payload_hex[:64]}...")
        
        print(f"\nTOTAL PACKETS: {len(self.packets)}")
        
        print("\nCHARACTERISTICS:")
        print(f"{'UUID':<80} {'pkts':>8} {'first':>10} {'last':>10} {'lengths':<40}")
        print("-" * 150)
        
        for url, pkts in sorted(by_char.items(), key=lambda x: -len(x[1])):
            count = len(pkts)
            if pkts:
                first_t = pkts[0].timestamp - self._action_ts
                last_t = pkts[-1].timestamp - self._action_ts
            else:
                first_t = last_t = 0
            lengths = Counter(len(p.payload_bytes) for p in pkts)
            
            # Show most common lengths
            len_strs = [f"{k}B" for k in sorted(lengths.keys())[:5]]
            if len(lengths) > 5:
                len_strs.append("...")
            
            print(f"{url:<80} {count:>8} {first_t:>10.3f}s {last_t:>10.3f}s {', '.join(len_strs):<40}")
        
        # Show first few packets for each characteristic
        print("\nFIRST PACKETS BY CHARACTERISTIC:")
        print("-" * 60)
        for url, pkts in sorted(by_char.items(), key=lambda x: x[1][0].timestamp):
            print(f"\n{url}:")
            for i, p in enumerate(pkts[:3]):
                print(f"  {i+1}. {p.payload_hex[:64]}...")
    
    async def run(self):
        """Main passive recording session (PR-017C)."""
        print("=" * 60)
        print("HEY CYAN FORENSICS RECORDER v3 - PASSIVE MODE")
        print("PR-017C: 90-second delayed stream verification")
        print("=" * 60)
        print("\nThis is a STRICTLY PASSIVE recorder.")
        print("It will NOT send any commands or write to characteristics.")
        print(f"Passive capture duration: {self.passive_seconds} seconds")
        
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
        self.mtu = self.client.mtu_size
        print(f"MTU: {self.mtu}")
        
        # Dump GATT services
        self.dump_gatt_services()
        
        # Subscribe to notifications (PASSIVE)
        await self.subscribe_to_notifications()
        
        successful = sum(1 for r in self.subscription_results if r["success"])
        total = len(self.subscription_results)
        print(f"\nSuccessfully subscribed to {successful}/{total} characteristic(s)")
        
        # Passive capture for extended duration (PR-017C: 90 seconds)
        print("\n" + "-" * 60)
        print("PASSIVE BASELINE CAPTURE")
        print("-" * 60)
        
        await self.passive_capture(self.passive_seconds, "BASELINE - 90 seconds passive listening")
        
        print(f"\nNotifications received during baseline: {self.notification_count}")
        
        if self.first_tx:
            first_elapsed = self.first_tx - self._action_ts
            print(f"First notification at: {first_elapsed:.3f}s after subscription")
        
        # Print detailed statistics
        self.print_statistics()
        
        # Save capture
        capture = Capture(
            session_id="passive-delayed-001",
            device_name=self.device.name,
            device_address=self.device.address,
            mtu=self.mtu,
            action_id="passive_baseline",
            action_label="PASSIVE BASELINE - 90 seconds",
            capture_started_at=datetime.fromtimestamp(self._action_ts).isoformat() if self._action_ts else None,
            capture_ended_at=datetime.now().isoformat(),
            action_timestamp=datetime.fromtimestamp(self.first_tx).isoformat() if self.first_tx else None,
            gatt_services=self.gatt_services,
            subscription_results=self.subscription_results,
            packets=self.packets,
        )
        filepath = self.save_capture(capture)
        print(f"\nSaved capture: {filepath}")
        
        print("\n" + "=" * 60)
        print("NEXT STEPS")
        print("=" * 60)
        print("If packets captured:")
        print("  1. Analyze characteristic patterns")
        print("  2. Correlate with physical actions")
        print("  3. Compare with SDK event definitions")
        print("\nIf NO packets (0):")
        print("  1. Device may not be in streaming mode")
        print("  2. Check companion app is NOT running")
        print("  3. Try different device state")
        print("  4. Consider host-specific behavior")
        
        await self.client.disconnect()


def main():
    # Allow override via environment variable
    import os
    passive_secs = float(os.environ.get("PASSIVE_SECONDS", "90"))
    
    capture_dir = Path(__file__).parent / "captures"
    recorder = PassiveForensicsRecorder(capture_dir, passive_seconds=passive_secs)
    
    try:
        asyncio.run(recorder.run())
    except KeyboardInterrupt:
        print("\n\nInterrupted by user")
    except Exception as e:
        print(f"\n\nError: {e}")
        import traceback
        traceback.print_exc()


if __name__ == "__main__":
    main()