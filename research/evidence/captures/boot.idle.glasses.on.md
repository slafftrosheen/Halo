isLeEnabled(): ON
Stop Scan with callback
ViewPostIme pointer 0
ViewPostIme pointer 1
Accessing wearableHost
Connecting to 5F:AE:56:10:38:C2 (mode: RUNTIME)
Starting RFCOMM connection...
cancelDiscovery
BluetoothAdapter() : com.aistudio.braintool.xkqzps
connect() for device XX:XX:XX:XX:38:C2 called by pid: 28632
connect(), SocketState: INIT, mPfd: {ParcelFileDescriptor: java.io.FileDescriptor@acbdb86}
connect(), socket connected. mPort=1
connect() for device XX:XX:XX:XX:38:C2 called by pid: 28632
connect(), SocketState: INIT, mPfd: {ParcelFileDescriptor: java.io.FileDescriptor@1931e99}
close() this: XX:XX:XX:XX:38:C2, channel: -1, mSocketIS: android.net.LocalSocketImpl$SocketInputStream@24cd55e, mSocketOS: android.net.LocalSocketImpl$SocketOutputStream@86dad3f, mSocket: android.net.LocalSocket@f36260c impl:android.net.LocalSocketImpl@2ee0b55 fd:java.io.FileDescriptor@1931e99, mSocketState: INIT
connect() for device XX:XX:XX:XX:38:C2 called by pid: 28632
connect(), SocketState: INIT, mPfd: {ParcelFileDescriptor: java.io.FileDescriptor@19de5b}
close() this: XX:XX:XX:XX:38:C2, channel: -1, mSocketIS: android.net.LocalSocketImpl$SocketInputStream@22596f8, mSocketOS: android.net.LocalSocketImpl$SocketOutputStream@9d107d1, mSocket: android.net.LocalSocket@b083236 impl:android.net.LocalSocketImpl@d8e0537 fd:java.io.FileDescriptor@19de5b, mSocketState: INIT
Starting GATT connection...
Connecting to 5F:AE:56:10:38:C2
connect() - device: XX:XX:XX:XX:38:C2, auto: false
registerApp()
registerApp() - UUID=8c3354d4-a8f5-4e3a-ba75-7c2dc21404b3
onClientRegistered() - status=0 clientIf=109
onClientConnectionState() - status=0 clientIf=109 connected=true device=XX:XX:XX:XX:38:C2
onConnectionStateChange: status=0, newState=2
configureMTU() - device: XX:XX:XX:XX:38:C2 mtu: 517
onConfigureMTU() - Device=XX:XX:XX:XX:38:C2 mtu=517 status=0
[INFO ] [2026-08-12T05:33:17.791735Z] [HALO-ProtocolDiscovery] Negotiated MTU: 517
discoverServices() - device: XX:XX:XX:XX:38:C2
onSearchComplete() = address=XX:XX:XX:XX:38:C2 status=0
onServicesDiscovered: status=0
[INFO ] [2026-08-12T05:33:17.797897Z] [HALO-ProtocolDiscovery] Discovered Service: 00001800-0000-1000-8000-00805f9b34fb (Type: PRIMARY)
[INFO ] [2026-08-12T05:33:17.798388Z] [HALO-ProtocolDiscovery]   Discovered Characteristic: 00002a00-0000-1000-8000-00805f9b34fb (Service: 00001800-0000-1000-8000-00805f9b34fb, Properties: READ|WRITE (0x000A), Permissions: NONE (0x0000))
[INFO ] [2026-08-12T05:33:17.798585Z] [HALO-ProtocolDiscovery] Discovered Service: 0000ae30-0000-1000-8000-00805f9b34fb (Type: PRIMARY)
[INFO ] [2026-08-12T05:33:17.798762Z] [HALO-ProtocolDiscovery]   Discovered Characteristic: 0000ae01-0000-1000-8000-00805f9b34fb (Service: 0000ae30-0000-1000-8000-00805f9b34fb, Properties: WRITE_NO_RESPONSE (0x0004), Permissions: NONE (0x0000))
[INFO ] [2026-08-12T05:33:17.798944Z] [HALO-ProtocolDiscovery]   Discovered Characteristic: 0000ae02-0000-1000-8000-00805f9b34fb (Service: 0000ae30-0000-1000-8000-00805f9b34fb, Properties: NOTIFY (0x0010), Permissions: NONE (0x0000))
[INFO ] [2026-08-12T05:33:17.799109Z] [HALO-ProtocolDiscovery]     Discovered Descriptor: 00002902-0000-1000-8000-00805f9b34fb (Service: 0000ae30-0000-1000-8000-00805f9b34fb, Characteristic: 0000ae02-0000-1000-8000-00805f9b34fb, Permissions: NONE (0x0000))
setCharacteristicNotification() - uuid: 0000ae02-0000-1000-8000-00805f9b34fb enable: true
[INFO ] [2026-08-12T05:33:17.800220Z] [HALO-ProtocolDiscovery]   Discovered Characteristic: 0000ae03-0000-1000-8000-00805f9b34fb (Service: 0000ae30-0000-1000-8000-00805f9b34fb, Properties: WRITE_NO_RESPONSE (0x0004), Permissions: NONE (0x0000))
[INFO ] [2026-08-12T05:33:17.800408Z] [HALO-ProtocolDiscovery]   Discovered Characteristic: 0000ae04-0000-1000-8000-00805f9b34fb (Service: 0000ae30-0000-1000-8000-00805f9b34fb, Properties: NOTIFY (0x0010), Permissions: NONE (0x0000))
[INFO ] [2026-08-12T05:33:17.800572Z] [HALO-ProtocolDiscovery]     Discovered Descriptor: 00002902-0000-1000-8000-00805f9b34fb (Service: 0000ae30-0000-1000-8000-00805f9b34fb, Characteristic: 0000ae04-0000-1000-8000-00805f9b34fb, Permissions: NONE (0x0000))
setCharacteristicNotification() - uuid: 0000ae04-0000-1000-8000-00805f9b34fb enable: true
[INFO ] [2026-08-12T05:33:17.801374Z] [HALO-ProtocolDiscovery]   Discovered Characteristic: 0000ae05-0000-1000-8000-00805f9b34fb (Service: 0000ae30-0000-1000-8000-00805f9b34fb, Properties: INDICATE (0x0020), Permissions: NONE (0x0000))
[INFO ] [2026-08-12T05:33:17.801542Z] [HALO-ProtocolDiscovery]     Discovered Descriptor: 00002902-0000-1000-8000-00805f9b34fb (Service: 0000ae30-0000-1000-8000-00805f9b34fb, Characteristic: 0000ae05-0000-1000-8000-00805f9b34fb, Permissions: NONE (0x0000))
setCharacteristicNotification() - uuid: 0000ae05-0000-1000-8000-00805f9b34fb enable: true
[INFO ] [2026-08-12T05:33:17.802402Z] [HALO-ProtocolDiscovery]   Discovered Characteristic: 0000ae10-0000-1000-8000-00805f9b34fb (Service: 0000ae30-0000-1000-8000-00805f9b34fb, Properties: READ|WRITE (0x000A), Permissions: NONE (0x0000))
[INFO ] [2026-08-12T05:33:17.802534Z] [HALO-ProtocolDiscovery] Discovered Service: 00003802-0000-1000-8000-00805f9b34fb (Type: PRIMARY)
[INFO ] [2026-08-12T05:33:17.802699Z] [HALO-ProtocolDiscovery]   Discovered Characteristic: 00004a02-0000-1000-8000-00805f9b34fb (Service: 00003802-0000-1000-8000-00805f9b34fb, Properties: READ|WRITE|NOTIFY (0x001A), Permissions: NONE (0x0000))
[INFO ] [2026-08-12T05:33:17.802865Z] [HALO-ProtocolDiscovery]     Discovered Descriptor: 00002902-0000-1000-8000-00805f9b34fb (Service: 00003802-0000-1000-8000-00805f9b34fb, Characteristic: 00004a02-0000-1000-8000-00805f9b34fb, Permissions: NONE (0x0000))
setCharacteristicNotification() - uuid: 00004a02-0000-1000-8000-00805f9b34fb enable: true
[INFO ] [2026-08-12T05:33:17.803547Z] [HALO-ProtocolDiscovery] Discovered Service: 0000ae3a-0000-1000-8000-00805f9b34fb (Type: PRIMARY)
[INFO ] [2026-08-12T05:33:17.803728Z] [HALO-ProtocolDiscovery]   Discovered Characteristic: 0000ae3b-0000-1000-8000-00805f9b34fb (Service: 0000ae3a-0000-1000-8000-00805f9b34fb, Properties: WRITE_NO_RESPONSE (0x0004), Permissions: NONE (0x0000))
[INFO ] [2026-08-12T05:33:17.803897Z] [HALO-ProtocolDiscovery]   Discovered Characteristic: 0000ae3c-0000-1000-8000-00805f9b34fb (Service: 0000ae3a-0000-1000-8000-00805f9b34fb, Properties: NOTIFY (0x0010), Permissions: NONE (0x0000))
[INFO ] [2026-08-12T05:33:17.804037Z] [HALO-ProtocolDiscovery]     Discovered Descriptor: 00002902-0000-1000-8000-00805f9b34fb (Service: 0000ae3a-0000-1000-8000-00805f9b34fb, Characteristic: 0000ae3c-0000-1000-8000-00805f9b34fb, Permissions: NONE (0x0000))
setCharacteristicNotification() - uuid: 0000ae3c-0000-1000-8000-00805f9b34fb enable: true
[INFO ] [2026-08-12T05:33:17.804684Z] [HALO-ProtocolDiscovery] Discovered Service: 00001801-0000-1000-8000-00805f9b34fb (Type: PRIMARY)
[INFO ] [2026-08-12T05:33:17.804867Z] [HALO-ProtocolDiscovery]   Discovered Characteristic: 00002a05-0000-1000-8000-00805f9b34fb (Service: 00001801-0000-1000-8000-00805f9b34fb, Properties: INDICATE (0x0020), Permissions: NONE (0x0000))
[INFO ] [2026-08-12T05:33:17.805019Z] [HALO-ProtocolDiscovery]     Discovered Descriptor: 00002902-0000-1000-8000-00805f9b34fb (Service: 00001801-0000-1000-8000-00805f9b34fb, Characteristic: 00002a05-0000-1000-8000-00805f9b34fb, Permissions: NONE (0x0000))
setCharacteristicNotification() - uuid: 00002a05-0000-1000-8000-00805f9b34fb enable: true
[INFO ] [2026-08-12T05:33:17.805588Z] [HALO-ProtocolDiscovery] Discovered Service: 6e40fff0-b5a3-f393-e0a9-e50e24dcca9e (Type: PRIMARY)
[INFO ] [2026-08-12T05:33:17.805765Z] [HALO-ProtocolDiscovery]   Discovered Characteristic: 6e400002-b5a3-f393-e0a9-e50e24dcca9e (Service: 6e40fff0-b5a3-f393-e0a9-e50e24dcca9e, Properties: WRITE_NO_RESPONSE|WRITE (0x000C), Permissions: NONE (0x0000))
[INFO ] [2026-08-12T05:33:17.805941Z] [HALO-ProtocolDiscovery]   Discovered Characteristic: 6e400003-b5a3-f393-e0a9-e50e24dcca9e (Service: 6e40fff0-b5a3-f393-e0a9-e50e24dcca9e, Properties: NOTIFY (0x0010), Permissions: NONE (0x0000))
[INFO ] [2026-08-12T05:33:17.806079Z] [HALO-ProtocolDiscovery]     Discovered Descriptor: 00002902-0000-1000-8000-00805f9b34fb (Service: 6e40fff0-b5a3-f393-e0a9-e50e24dcca9e, Characteristic: 6e400003-b5a3-f393-e0a9-e50e24dcca9e, Permissions: NONE (0x0000))
setCharacteristicNotification() - uuid: 6e400003-b5a3-f393-e0a9-e50e24dcca9e enable: true
[INFO ] [2026-08-12T05:33:17.806613Z] [HALO-ProtocolDiscovery] Discovered Service: de5bf728-d711-4e47-af26-65e3012a5dc7 (Type: PRIMARY)
[INFO ] [2026-08-12T05:33:17.806780Z] [HALO-ProtocolDiscovery]   Discovered Characteristic: de5bf72a-d711-4e47-af26-65e3012a5dc7 (Service: de5bf728-d711-4e47-af26-65e3012a5dc7, Properties: WRITE_NO_RESPONSE|WRITE (0x000C), Permissions: NONE (0x0000))
[INFO ] [2026-08-12T05:33:17.806949Z] [HALO-ProtocolDiscovery]   Discovered Characteristic: de5bf729-d711-4e47-af26-65e3012a5dc7 (Service: de5bf728-d711-4e47-af26-65e3012a5dc7, Properties: NOTIFY (0x0010), Permissions: NONE (0x0000))
[INFO ] [2026-08-12T05:33:17.807083Z] [HALO-ProtocolDiscovery]     Discovered Descriptor: 00002902-0000-1000-8000-00805f9b34fb (Service: de5bf728-d711-4e47-af26-65e3012a5dc7, Characteristic: de5bf729-d711-4e47-af26-65e3012a5dc7, Permissions: NONE (0x0000))
setCharacteristicNotification() - uuid: de5bf729-d711-4e47-af26-65e3012a5dc7 enable: true
[INFO ] [2026-08-12T05:33:17.807588Z] [HALO-ProtocolDiscovery] Discovered Service: 0000180a-0000-1000-8000-00805f9b34fb (Type: PRIMARY)
[INFO ] [2026-08-12T05:33:17.807754Z] [HALO-ProtocolDiscovery]   Discovered Characteristic: 00002a25-0000-1000-8000-00805f9b34fb (Service: 0000180a-0000-1000-8000-00805f9b34fb, Properties: READ (0x0002), Permissions: NONE (0x0000))
[INFO ] [2026-08-12T05:33:17.807921Z] [HALO-ProtocolDiscovery]   Discovered Characteristic: 00002a27-0000-1000-8000-00805f9b34fb (Service: 0000180a-0000-1000-8000-00805f9b34fb, Properties: READ (0x0002), Permissions: NONE (0x0000))
[INFO ] [2026-08-12T05:33:17.808079Z] [HALO-ProtocolDiscovery]   Discovered Characteristic: 00002a26-0000-1000-8000-00805f9b34fb (Service: 0000180a-0000-1000-8000-00805f9b34fb, Properties: READ (0x0002), Permissions: NONE (0x0000))
[INFO ] [2026-08-12T05:33:17.808235Z] [HALO-ProtocolDiscovery]   Discovered Characteristic: 00002a23-0000-1000-8000-00805f9b34fb (Service: 0000180a-0000-1000-8000-00805f9b34fb, Properties: READ (0x0002), Permissions: NONE (0x0000))
[INFO ] [2026-08-12T05:33:17.808334Z] [HALO-ProtocolDiscovery] Discovered Service: 0000fee1-0000-1000-8000-00805f9b34fb (Type: PRIMARY)
[INFO ] [2026-08-12T05:33:17.808488Z] [HALO-ProtocolDiscovery]   Discovered Characteristic: 0000fee3-0000-1000-8000-00805f9b34fb (Service: 0000fee1-0000-1000-8000-00805f9b34fb, Properties: READ|WRITE|NOTIFY (0x001A), Permissions: NONE (0x0000))
[INFO ] [2026-08-12T05:33:17.808729Z] [HALO-ProtocolDiscovery]     Discovered Descriptor: 00002902-0000-1000-8000-00805f9b34fb (Service: 0000fee1-0000-1000-8000-00805f9b34fb, Characteristic: 0000fee3-0000-1000-8000-00805f9b34fb, Permissions: NONE (0x0000))
setCharacteristicNotification() - uuid: 0000fee3-0000-1000-8000-00805f9b34fb enable: true
[INFO ] [2026-08-12T05:33:17.879874Z] [HALO-ProtocolDiscovery]     Descriptor write: 00002902-0000-1000-8000-00805f9b34fb (Status: SUCCESS(0))
[INFO ] [2026-08-12T05:33:17.967836Z] [HALO-ProtocolDiscovery]     Descriptor write: 00002902-0000-1000-8000-00805f9b34fb (Status: SUCCESS(0))
[INFO ] [2026-08-12T05:33:18.052861Z] [HALO-ProtocolDiscovery]     Descriptor write: 00002902-0000-1000-8000-00805f9b34fb (Status: SUCCESS(0))
[INFO ] [2026-08-12T05:33:18.139651Z] [HALO-ProtocolDiscovery]     Descriptor write: 00002902-0000-1000-8000-00805f9b34fb (Status: SUCCESS(0))
[INFO ] [2026-08-12T05:33:18.227283Z] [HALO-ProtocolDiscovery]     Descriptor write: 00002902-0000-1000-8000-00805f9b34fb (Status: SUCCESS(0))
[INFO ] [2026-08-12T05:33:18.318124Z] [HALO-ProtocolDiscovery]     Descriptor write: 00002902-0000-1000-8000-00805f9b34fb (Status: SUCCESS(0))
onConnectionUpdated() - Device=XX:XX:XX:XX:38:C2 interval=21 latency=0 timeout=500 status=0
[INFO ] [2026-08-12T05:33:18.395039Z] [HALO-ProtocolDiscovery]     Descriptor write: 00002902-0000-1000-8000-00805f9b34fb (Status: SUCCESS(0))
[INFO ] [2026-08-12T05:33:18.446635Z] [HALO-ProtocolDiscovery]     Descriptor write: 00002902-0000-1000-8000-00805f9b34fb (Status: SUCCESS(0))
[INFO ] [2026-08-12T05:33:18.500692Z] [HALO-ProtocolDiscovery]     Descriptor write: 00002902-0000-1000-8000-00805f9b34fb (Status: SUCCESS(0))
Accessing wearableHost
[INFO ] [2026-08-12T05:33:18.513869Z] [HALO-interpretation] Loaded 3 speech rules
Create TextToSpeech : caller process id[28632] / name[com.aistudio.braintool.xkqzps] / target[null]
com.aistudio.braintool.xkqzps is not allowed to bind to private engine.
getHighestRankedPublicEngineName = com.google.android.tts
Sucessfully bound to com.google.android.tts
Connected to TTS engine
Transport status: RFCOMM=[primary], GATT=true
Starting event reader loop
Setting up the connection to TTS engine...
[INFO ] [2026-08-12T05:33:19.078429Z] [HALO-ProtocolDiscovery] RX [de5bf729-d711-4e47-af26-65e3012a5dc7]: BC 73 0E 00 7B 67 12 01 00 10 03 02 00 0F 0F 03 00 10 0A 01
[INFO ] [2026-08-12T05:33:19.080898Z] [HALO-HeyCyanRX] #1 [de5bf729-d711-4e47-af26-65e3012a5dc7] 20B: BC 73 0E 00 7B 67 12 01 00 10 03 02 00 0F 0F 03 00 10 0A 01
Unknown event on de5bf729-d711-4e47-af26-65e3012a5dc7: BC 73 0E 00 7B 67 12 01 00 10 03 02 00 0F 0F 03 00 10 0A 01
handleResized, frames=ClientWindowFrames{frame=[0,0][1080,2340] display=[0,0][1080,2340] parentFrame=[0,0][0,0]} displayId=0 dragResizing=false compatScale=1.0 frameChanged=false attachedFrameChanged=false configChanged=false displayChanged=false compatScaleChanged=false dragResizingChanged=false
call setFrameRateCategory for touch hint category=no preference, reason=boost timeout, vri=VRI[MainActivity]@aec8b7b
applyRequestedVisibilityToControl: visible=true, type=statusBars, host=com.aistudio.braintool.xkqzps/com.halo.app.MainActivity
[INFO ] [2026-08-12T05:33:22.490248Z] [HALO-ProtocolDiscovery] RX [de5bf729-d711-4e47-af26-65e3012a5dc7]: BC 73 0E 00 FA A6 12 01 00 10 03 02 00 0F 0F 03 00 10 0A 03
[INFO ] [2026-08-12T05:33:22.491198Z] [HALO-HeyCyanRX] #2 [de5bf729-d711-4e47-af26-65e3012a5dc7] 20B: BC 73 0E 00 FA A6 12 01 00 10 03 02 00 0F 0F 03 00 10 0A 03
Unknown event on de5bf729-d711-4e47-af26-65e3012a5dc7: BC 73 0E 00 FA A6 12 01 00 10 03 02 00 0F 0F 03 00 10 0A 03
[INFO ] [2026-08-12T05:33:24.250920Z] [HALO-ProtocolDiscovery] RX [de5bf729-d711-4e47-af26-65e3012a5dc7]: BC 73 0E 00 7B 67 12 01 00 10 03 02 00 0F 0F 03 00 10 0A 01
[INFO ] [2026-08-12T05:33:24.253182Z] [HALO-HeyCyanRX] #3 [de5bf729-d711-4e47-af26-65e3012a5dc7] 20B: BC 73 0E 00 7B 67 12 01 00 10 03 02 00 0F 0F 03 00 10 0A 01
Unknown event on de5bf729-d711-4e47-af26-65e3012a5dc7: BC 73 0E 00 7B 67 12 01 00 10 03 02 00 0F 0F 03 00 10 0A 01
[INFO ] [2026-08-12T05:33:27.852655Z] [HALO-ProtocolDiscovery] RX [de5bf729-d711-4e47-af26-65e3012a5dc7]: BC 73 0E 00 FA A6 12 01 00 10 03 02 00 0F 0F 03 00 10 0A 03
[INFO ] [2026-08-12T05:33:27.855411Z] [HALO-HeyCyanRX] #4 [de5bf729-d711-4e47-af26-65e3012a5dc7] 20B: BC 73 0E 00 FA A6 12 01 00 10 03 02 00 0F 0F 03 00 10 0A 03
Unknown event on de5bf729-d711-4e47-af26-65e3012a5dc7: BC 73 0E 00 FA A6 12 01 00 10 03 02 00 0F 0F 03 00 10 0A 03
onConnectionUpdated() - Device=XX:XX:XX:XX:38:C2 interval=21 latency=10 timeout=500 status=0
