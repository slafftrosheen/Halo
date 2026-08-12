Stop Scan with callback
ViewPostIme pointer 0
ViewPostIme pointer 1
Accessing wearableHost
Connecting to 5F:AE:56:10:38:C2 (mode: RUNTIME)
Starting RFCOMM connection...
cancelDiscovery
BluetoothAdapter() : com.aistudio.braintool.xkqzps
connect() for device XX:XX:XX:XX:38:C2 called by pid: 27406
connect(), SocketState: INIT, mPfd: {ParcelFileDescriptor: java.io.FileDescriptor@e4c1b64}
connect(), socket connected. mPort=1
connect() for device XX:XX:XX:XX:38:C2 called by pid: 27406
connect(), SocketState: INIT, mPfd: {ParcelFileDescriptor: java.io.FileDescriptor@6c4f593}
close() this: XX:XX:XX:XX:38:C2, channel: -1, mSocketIS: android.net.LocalSocketImpl$SocketInputStream@76189d0, mSocketOS: android.net.LocalSocketImpl$SocketOutputStream@3653dc9, mSocket: android.net.LocalSocket@d64bcce impl:android.net.LocalSocketImpl@abf57ef fd:java.io.FileDescriptor@6c4f593, mSocketState: INIT
connect() for device XX:XX:XX:XX:38:C2 called by pid: 27406
connect(), SocketState: INIT, mPfd: {ParcelFileDescriptor: java.io.FileDescriptor@a7aed85}
close() this: XX:XX:XX:XX:38:C2, channel: -1, mSocketIS: android.net.LocalSocketImpl$SocketInputStream@493d7da, mSocketOS: android.net.LocalSocketImpl$SocketOutputStream@b53840b, mSocket: android.net.LocalSocket@4cc2e8 impl:android.net.LocalSocketImpl@b81dd01 fd:java.io.FileDescriptor@a7aed85, mSocketState: INIT
Starting GATT connection...
Connecting to 5F:AE:56:10:38:C2
connect() - device: XX:XX:XX:XX:38:C2, auto: false
registerApp()
registerApp() - UUID=be702ec4-cf8b-4094-bce2-a0b6f0ba951e
onClientRegistered() - status=0 clientIf=108
onClientConnectionState() - status=0 clientIf=108 connected=true device=XX:XX:XX:XX:38:C2
onConnectionStateChange: status=0, newState=2
configureMTU() - device: XX:XX:XX:XX:38:C2 mtu: 517
onConfigureMTU() - Device=XX:XX:XX:XX:38:C2 mtu=517 status=0
[INFO ] [2026-08-12T05:29:26.752185Z] [HALO-ProtocolDiscovery] Negotiated MTU: 517
discoverServices() - device: XX:XX:XX:XX:38:C2
onSearchComplete() = address=XX:XX:XX:XX:38:C2 status=0
onServicesDiscovered: status=0
[INFO ] [2026-08-12T05:29:26.754826Z] [HALO-ProtocolDiscovery] Discovered Service: 00001800-0000-1000-8000-00805f9b34fb (Type: PRIMARY)
[INFO ] [2026-08-12T05:29:26.755404Z] [HALO-ProtocolDiscovery]   Discovered Characteristic: 00002a00-0000-1000-8000-00805f9b34fb (Service: 00001800-0000-1000-8000-00805f9b34fb, Properties: READ|WRITE (0x000A), Permissions: NONE (0x0000))
[INFO ] [2026-08-12T05:29:26.756650Z] [HALO-ProtocolDiscovery] Discovered Service: 0000ae30-0000-1000-8000-00805f9b34fb (Type: PRIMARY)
[INFO ] [2026-08-12T05:29:26.756921Z] [HALO-ProtocolDiscovery]   Discovered Characteristic: 0000ae01-0000-1000-8000-00805f9b34fb (Service: 0000ae30-0000-1000-8000-00805f9b34fb, Properties: WRITE_NO_RESPONSE (0x0004), Permissions: NONE (0x0000))
[INFO ] [2026-08-12T05:29:26.757188Z] [HALO-ProtocolDiscovery]   Discovered Characteristic: 0000ae02-0000-1000-8000-00805f9b34fb (Service: 0000ae30-0000-1000-8000-00805f9b34fb, Properties: NOTIFY (0x0010), Permissions: NONE (0x0000))
[INFO ] [2026-08-12T05:29:26.757440Z] [HALO-ProtocolDiscovery]     Discovered Descriptor: 00002902-0000-1000-8000-00805f9b34fb (Service: 0000ae30-0000-1000-8000-00805f9b34fb, Characteristic: 0000ae02-0000-1000-8000-00805f9b34fb, Permissions: NONE (0x0000))
setCharacteristicNotification() - uuid: 0000ae02-0000-1000-8000-00805f9b34fb enable: true
[INFO ] [2026-08-12T05:29:26.759137Z] [HALO-ProtocolDiscovery]   Discovered Characteristic: 0000ae03-0000-1000-8000-00805f9b34fb (Service: 0000ae30-0000-1000-8000-00805f9b34fb, Properties: WRITE_NO_RESPONSE (0x0004), Permissions: NONE (0x0000))
[INFO ] [2026-08-12T05:29:26.759434Z] [HALO-ProtocolDiscovery]   Discovered Characteristic: 0000ae04-0000-1000-8000-00805f9b34fb (Service: 0000ae30-0000-1000-8000-00805f9b34fb, Properties: NOTIFY (0x0010), Permissions: NONE (0x0000))
[INFO ] [2026-08-12T05:29:26.759685Z] [HALO-ProtocolDiscovery]     Discovered Descriptor: 00002902-0000-1000-8000-00805f9b34fb (Service: 0000ae30-0000-1000-8000-00805f9b34fb, Characteristic: 0000ae04-0000-1000-8000-00805f9b34fb, Permissions: NONE (0x0000))
setCharacteristicNotification() - uuid: 0000ae04-0000-1000-8000-00805f9b34fb enable: true
[INFO ] [2026-08-12T05:29:26.760781Z] [HALO-ProtocolDiscovery]   Discovered Characteristic: 0000ae05-0000-1000-8000-00805f9b34fb (Service: 0000ae30-0000-1000-8000-00805f9b34fb, Properties: INDICATE (0x0020), Permissions: NONE (0x0000))
[INFO ] [2026-08-12T05:29:26.761047Z] [HALO-ProtocolDiscovery]     Discovered Descriptor: 00002902-0000-1000-8000-00805f9b34fb (Service: 0000ae30-0000-1000-8000-00805f9b34fb, Characteristic: 0000ae05-0000-1000-8000-00805f9b34fb, Permissions: NONE (0x0000))
setCharacteristicNotification() - uuid: 0000ae05-0000-1000-8000-00805f9b34fb enable: true
[INFO ] [2026-08-12T05:29:26.761956Z] [HALO-ProtocolDiscovery]   Discovered Characteristic: 0000ae10-0000-1000-8000-00805f9b34fb (Service: 0000ae30-0000-1000-8000-00805f9b34fb, Properties: READ|WRITE (0x000A), Permissions: NONE (0x0000))
[INFO ] [2026-08-12T05:29:26.762081Z] [HALO-ProtocolDiscovery] Discovered Service: 00003802-0000-1000-8000-00805f9b34fb (Type: PRIMARY)
[INFO ] [2026-08-12T05:29:26.762238Z] [HALO-ProtocolDiscovery]   Discovered Characteristic: 00004a02-0000-1000-8000-00805f9b34fb (Service: 00003802-0000-1000-8000-00805f9b34fb, Properties: READ|WRITE|NOTIFY (0x001A), Permissions: NONE (0x0000))
[INFO ] [2026-08-12T05:29:26.762377Z] [HALO-ProtocolDiscovery]     Discovered Descriptor: 00002902-0000-1000-8000-00805f9b34fb (Service: 00003802-0000-1000-8000-00805f9b34fb, Characteristic: 00004a02-0000-1000-8000-00805f9b34fb, Permissions: NONE (0x0000))
setCharacteristicNotification() - uuid: 00004a02-0000-1000-8000-00805f9b34fb enable: true
[INFO ] [2026-08-12T05:29:26.762985Z] [HALO-ProtocolDiscovery] Discovered Service: 0000ae3a-0000-1000-8000-00805f9b34fb (Type: PRIMARY)
[INFO ] [2026-08-12T05:29:26.763148Z] [HALO-ProtocolDiscovery]   Discovered Characteristic: 0000ae3b-0000-1000-8000-00805f9b34fb (Service: 0000ae3a-0000-1000-8000-00805f9b34fb, Properties: WRITE_NO_RESPONSE (0x0004), Permissions: NONE (0x0000))
[INFO ] [2026-08-12T05:29:26.763320Z] [HALO-ProtocolDiscovery]   Discovered Characteristic: 0000ae3c-0000-1000-8000-00805f9b34fb (Service: 0000ae3a-0000-1000-8000-00805f9b34fb, Properties: NOTIFY (0x0010), Permissions: NONE (0x0000))
[INFO ] [2026-08-12T05:29:26.763458Z] [HALO-ProtocolDiscovery]     Discovered Descriptor: 00002902-0000-1000-8000-00805f9b34fb (Service: 0000ae3a-0000-1000-8000-00805f9b34fb, Characteristic: 0000ae3c-0000-1000-8000-00805f9b34fb, Permissions: NONE (0x0000))
setCharacteristicNotification() - uuid: 0000ae3c-0000-1000-8000-00805f9b34fb enable: true
[INFO ] [2026-08-12T05:29:26.764072Z] [HALO-ProtocolDiscovery] Discovered Service: 00001801-0000-1000-8000-00805f9b34fb (Type: PRIMARY)
[INFO ] [2026-08-12T05:29:26.764234Z] [HALO-ProtocolDiscovery]   Discovered Characteristic: 00002a05-0000-1000-8000-00805f9b34fb (Service: 00001801-0000-1000-8000-00805f9b34fb, Properties: INDICATE (0x0020), Permissions: NONE (0x0000))
[INFO ] [2026-08-12T05:29:26.764392Z] [HALO-ProtocolDiscovery]     Discovered Descriptor: 00002902-0000-1000-8000-00805f9b34fb (Service: 00001801-0000-1000-8000-00805f9b34fb, Characteristic: 00002a05-0000-1000-8000-00805f9b34fb, Permissions: NONE (0x0000))
setCharacteristicNotification() - uuid: 00002a05-0000-1000-8000-00805f9b34fb enable: true
[INFO ] [2026-08-12T05:29:26.764992Z] [HALO-ProtocolDiscovery] Discovered Service: 6e40fff0-b5a3-f393-e0a9-e50e24dcca9e (Type: PRIMARY)
[INFO ] [2026-08-12T05:29:26.765157Z] [HALO-ProtocolDiscovery]   Discovered Characteristic: 6e400002-b5a3-f393-e0a9-e50e24dcca9e (Service: 6e40fff0-b5a3-f393-e0a9-e50e24dcca9e, Properties: WRITE_NO_RESPONSE|WRITE (0x000C), Permissions: NONE (0x0000))
[INFO ] [2026-08-12T05:29:26.765319Z] [HALO-ProtocolDiscovery]   Discovered Characteristic: 6e400003-b5a3-f393-e0a9-e50e24dcca9e (Service: 6e40fff0-b5a3-f393-e0a9-e50e24dcca9e, Properties: NOTIFY (0x0010), Permissions: NONE (0x0000))
[INFO ] [2026-08-12T05:29:26.765460Z] [HALO-ProtocolDiscovery]     Discovered Descriptor: 00002902-0000-1000-8000-00805f9b34fb (Service: 6e40fff0-b5a3-f393-e0a9-e50e24dcca9e, Characteristic: 6e400003-b5a3-f393-e0a9-e50e24dcca9e, Permissions: NONE (0x0000))
setCharacteristicNotification() - uuid: 6e400003-b5a3-f393-e0a9-e50e24dcca9e enable: true
[INFO ] [2026-08-12T05:29:26.765997Z] [HALO-ProtocolDiscovery] Discovered Service: de5bf728-d711-4e47-af26-65e3012a5dc7 (Type: PRIMARY)
[INFO ] [2026-08-12T05:29:26.766174Z] [HALO-ProtocolDiscovery]   Discovered Characteristic: de5bf72a-d711-4e47-af26-65e3012a5dc7 (Service: de5bf728-d711-4e47-af26-65e3012a5dc7, Properties: WRITE_NO_RESPONSE|WRITE (0x000C), Permissions: NONE (0x0000))
[INFO ] [2026-08-12T05:29:26.766333Z] [HALO-ProtocolDiscovery]   Discovered Characteristic: de5bf729-d711-4e47-af26-65e3012a5dc7 (Service: de5bf728-d711-4e47-af26-65e3012a5dc7, Properties: NOTIFY (0x0010), Permissions: NONE (0x0000))
[INFO ] [2026-08-12T05:29:26.766473Z] [HALO-ProtocolDiscovery]     Discovered Descriptor: 00002902-0000-1000-8000-00805f9b34fb (Service: de5bf728-d711-4e47-af26-65e3012a5dc7, Characteristic: de5bf729-d711-4e47-af26-65e3012a5dc7, Permissions: NONE (0x0000))
setCharacteristicNotification() - uuid: de5bf729-d711-4e47-af26-65e3012a5dc7 enable: true
[INFO ] [2026-08-12T05:29:26.766993Z] [HALO-ProtocolDiscovery] Discovered Service: 0000180a-0000-1000-8000-00805f9b34fb (Type: PRIMARY)
[INFO ] [2026-08-12T05:29:26.767153Z] [HALO-ProtocolDiscovery]   Discovered Characteristic: 00002a25-0000-1000-8000-00805f9b34fb (Service: 0000180a-0000-1000-8000-00805f9b34fb, Properties: READ (0x0002), Permissions: NONE (0x0000))
[INFO ] [2026-08-12T05:29:26.767333Z] [HALO-ProtocolDiscovery]   Discovered Characteristic: 00002a27-0000-1000-8000-00805f9b34fb (Service: 0000180a-0000-1000-8000-00805f9b34fb, Properties: READ (0x0002), Permissions: NONE (0x0000))
[INFO ] [2026-08-12T05:29:26.767493Z] [HALO-ProtocolDiscovery]   Discovered Characteristic: 00002a26-0000-1000-8000-00805f9b34fb (Service: 0000180a-0000-1000-8000-00805f9b34fb, Properties: READ (0x0002), Permissions: NONE (0x0000))
[INFO ] [2026-08-12T05:29:26.767671Z] [HALO-ProtocolDiscovery]   Discovered Characteristic: 00002a23-0000-1000-8000-00805f9b34fb (Service: 0000180a-0000-1000-8000-00805f9b34fb, Properties: READ (0x0002), Permissions: NONE (0x0000))
[INFO ] [2026-08-12T05:29:26.767784Z] [HALO-ProtocolDiscovery] Discovered Service: 0000fee1-0000-1000-8000-00805f9b34fb (Type: PRIMARY)
[INFO ] [2026-08-12T05:29:26.767939Z] [HALO-ProtocolDiscovery]   Discovered Characteristic: 0000fee3-0000-1000-8000-00805f9b34fb (Service: 0000fee1-0000-1000-8000-00805f9b34fb, Properties: READ|WRITE|NOTIFY (0x001A), Permissions: NONE (0x0000))
[INFO ] [2026-08-12T05:29:26.768085Z] [HALO-ProtocolDiscovery]     Discovered Descriptor: 00002902-0000-1000-8000-00805f9b34fb (Service: 0000fee1-0000-1000-8000-00805f9b34fb, Characteristic: 0000fee3-0000-1000-8000-00805f9b34fb, Permissions: NONE (0x0000))
setCharacteristicNotification() - uuid: 0000fee3-0000-1000-8000-00805f9b34fb enable: true
[INFO ] [2026-08-12T05:29:26.885536Z] [HALO-ProtocolDiscovery]     Descriptor write: 00002902-0000-1000-8000-00805f9b34fb (Status: SUCCESS(0))
[INFO ] [2026-08-12T05:29:26.973790Z] [HALO-ProtocolDiscovery]     Descriptor write: 00002902-0000-1000-8000-00805f9b34fb (Status: SUCCESS(0))
[INFO ] [2026-08-12T05:29:27.060021Z] [HALO-ProtocolDiscovery]     Descriptor write: 00002902-0000-1000-8000-00805f9b34fb (Status: SUCCESS(0))
[INFO ] [2026-08-12T05:29:27.147899Z] [HALO-ProtocolDiscovery]     Descriptor write: 00002902-0000-1000-8000-00805f9b34fb (Status: SUCCESS(0))
[INFO ] [2026-08-12T05:29:27.236705Z] [HALO-ProtocolDiscovery]     Descriptor write: 00002902-0000-1000-8000-00805f9b34fb (Status: SUCCESS(0))
[INFO ] [2026-08-12T05:29:27.322759Z] [HALO-ProtocolDiscovery]     Descriptor write: 00002902-0000-1000-8000-00805f9b34fb (Status: SUCCESS(0))
[INFO ] [2026-08-12T05:29:27.411463Z] [HALO-ProtocolDiscovery]     Descriptor write: 00002902-0000-1000-8000-00805f9b34fb (Status: SUCCESS(0))
[INFO ] [2026-08-12T05:29:27.498438Z] [HALO-ProtocolDiscovery]     Descriptor write: 00002902-0000-1000-8000-00805f9b34fb (Status: SUCCESS(0))
[INFO ] [2026-08-12T05:29:27.583964Z] [HALO-ProtocolDiscovery]     Descriptor write: 00002902-0000-1000-8000-00805f9b34fb (Status: SUCCESS(0))
Accessing wearableHost
[INFO ] [2026-08-12T05:29:27.595107Z] [HALO-interpretation] Loaded 3 speech rules
Create TextToSpeech : caller process id[27406] / name[com.aistudio.braintool.xkqzps] / target[null]
com.aistudio.braintool.xkqzps is not allowed to bind to private engine.
getHighestRankedPublicEngineName = com.google.android.tts
Sucessfully bound to com.google.android.tts
Connected to TTS engine
Transport status: RFCOMM=[primary], GATT=true
Starting event reader loop
Setting up the connection to TTS engine...
[INFO ] [2026-08-12T05:29:28.371723Z] [HALO-ProtocolDiscovery] RX [de5bf729-d711-4e47-af26-65e3012a5dc7]: BC 73 08 00 1A 07 01 06 00 05 00 03 00 01
[INFO ] [2026-08-12T05:29:28.373141Z] [HALO-HeyCyanRX] #1 [de5bf729-d711-4e47-af26-65e3012a5dc7] 14B: BC 73 08 00 1A 07 01 06 00 05 00 03 00 01
Unknown event on de5bf729-d711-4e47-af26-65e3012a5dc7: BC 73 08 00 1A 07 01 06 00 05 00 03 00 01
[INFO ] [2026-08-12T05:29:28.375667Z] [HALO-ProtocolDiscovery] RX [de5bf729-d711-4e47-af26-65e3012a5dc7]: BC 73 0E 00 FA A6 12 01 00 10 03 02 00 0F 0F 03 00 10 0A 03
[INFO ] [2026-08-12T05:29:28.376417Z] [HALO-HeyCyanRX] #2 [de5bf729-d711-4e47-af26-65e3012a5dc7] 20B: BC 73 0E 00 FA A6 12 01 00 10 03 02 00 0F 0F 03 00 10 0A 03
Unknown event on de5bf729-d711-4e47-af26-65e3012a5dc7: BC 73 0E 00 FA A6 12 01 00 10 03 02 00 0F 0F 03 00 10 0A 03
[INFO ] [2026-08-12T05:29:28.457926Z] [HALO-ProtocolDiscovery] RX [de5bf729-d711-4e47-af26-65e3012a5dc7]: BC 73 0E 00 7B 67 12 01 00 10 03 02 00 0F 0F 03 00 10 0A 01
[INFO ] [2026-08-12T05:29:28.458572Z] [HALO-HeyCyanRX] #3 [de5bf729-d711-4e47-af26-65e3012a5dc7] 20B: BC 73 0E 00 7B 67 12 01 00 10 03 02 00 0F 0F 03 00 10 0A 01
Unknown event on de5bf729-d711-4e47-af26-65e3012a5dc7: BC 73 0E 00 7B 67 12 01 00 10 03 02 00 0F 0F 03 00 10 0A 01
handleResized, frames=ClientWindowFrames{frame=[0,0][1080,2340] display=[0,0][1080,2340] parentFrame=[0,0][0,0]} displayId=0 dragResizing=false compatScale=1.0 frameChanged=false attachedFrameChanged=false configChanged=false displayChanged=false compatScaleChanged=false dragResizingChanged=false
call setFrameRateCategory for touch hint category=no preference, reason=boost timeout, vri=VRI[MainActivity]@82e6457
applyRequestedVisibilityToControl: visible=true, type=statusBars, host=com.aistudio.braintool.xkqzps/com.halo.app.MainActivity
[INFO ] [2026-08-12T05:29:31.698674Z] [HALO-ProtocolDiscovery] RX [de5bf729-d711-4e47-af26-65e3012a5dc7]: BC 73 0E 00 FA A6 12 01 00 10 03 02 00 0F 0F 03 00 10 0A 03
[INFO ] [2026-08-12T05:29:31.700796Z] [HALO-HeyCyanRX] #4 [de5bf729-d711-4e47-af26-65e3012a5dc7] 20B: BC 73 0E 00 FA A6 12 01 00 10 03 02 00 0F 0F 03 00 10 0A 03
Unknown event on de5bf729-d711-4e47-af26-65e3012a5dc7: BC 73 0E 00 FA A6 12 01 00 10 03 02 00 0F 0F 03 00 10 0A 03
