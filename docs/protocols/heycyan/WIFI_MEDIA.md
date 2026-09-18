> Status: HISTORICAL RESEARCH. Classified 2026-09-18.
>
> Recorded vendor/capture analysis and hypotheses from earlier work. Statements below, including VERIFIED labels and code sketches, retain their original research scope; they are not assertions of current Halo behavior or current-build hardware acceptance. Some findings conflict across reports. Trace artifacts before reuse; current code/wiring is documented in the implementation status page.
>
> [Current reference](IMPLEMENTATION_STATUS.md).

# HeyCyan WiFi Media Transfer — wait, the user asked me put the response to the user
The PR-016 forensic documentation focuses on the dual-protocol communication system that leverages both Bluetooth Low Energy (BLE) for control and WiFi for high-bandwidth data transfer. This hybrid approach solves the fundamental challenge of transferring large media files from resource-constrained smart glasses while maintaining real-time control capabilities.

## 2. Bluetooth Control Layer

### 2.1 QCSDK Framework Integration

The foundation is built on the proprietary QCSDK framework, which manages BLE communication through the QCCentralManager singleton. This class handles:

```objective-c
// Device state management
typedef NS_ENUM(NSInteger, QCState) {
    QCStateUnbind,           // No device paired
    QCStateConnecting,       // Establishing BLE connection
    QCStateConnected,        // BLE connection established
    QCStateUnkown,
    QCStateDisconnecting,    // Graceful disconnect in progress
    QCStateDisconnected      // Connection lost
};
```

The SDK implements a delegate pattern where ViewController conforms to QCCentralManagerDelegate, receiving callbacks for:
- Connection state changes (didState:)
- Device pairing completion (didConnected:)
- Connection failures (didFailConnected:)
- Disconnection events (didDisconnecte:)

### 2.2 Command Protocol

Once BLE is established, the system uses QCSDKCmdCreator to send structured commands to the glasses. Key commands include:

```objective-c
// Device information commands
[QCSDKCmdCreator getDeviceVersionInfoSuccess:^(NSString *hdVersion, NSString *firmVersion, NSString *hdWifiVersion, NSString *firmWifiVersion) {
    // Hardware/firmware version handling
} fail:^{
    // Error handling
}];

// Media management commands
[QCSDKCmdCreator getDeviceMedia:^(NSInteger photo, NSInteger video, NSInteger audio, NSInteger type) {
    // Media count updates
} fail:^{
    // Error handling
}];
```

## 3. Mode Switching Protocol

### 3.1 Device Mode States

The glasses operate in distinct operational modes, controlled via BLE commands:

```objective-c
typedef NS_ENUM(NSInteger, QCOperatorDeviceMode) {
    QCOperatorDeviceModePhoto,         // Camera mode for taking photos
    QCOperatorDeviceModeVideo,         // Video recording mode
    QCOperatorDeviceModeVideoStop,     // Stop video recording
    QCOperatorDeviceModeAudio,         // Audio recording mode
    QCOperatorDeviceModeAudioStop,     // Stop audio recording
    QCOperatorDeviceModeAIPhoto,       // AI-enhanced photo capture
    QCOperatorDeviceModeTransfer       // WiFi hotspot for media transfer
};
```

### 3.2 Transfer Mode Activation

The critical transition to WiFi transfer happens here:

```objective-c
- (void)switchToTransferMode {
    [QCSDKCmdCreator openWifiWithMode:QCOperatorDeviceModeTransfer
        success:^(NSString *ssid, NSString *password) {
            // Glasses activated hotspot, now connect via WiFi
            [[GlassesWiFiHandler sharedHandler] connectToGlassesWiFi:ssid
                                                              password:password ?: @"123456789"
                                                        statusCallback:^(GlassesWiFiHandlerState state, NSString *status, UIImage *previewImage) {
                // UI updates during connection process
            } completion:^(BOOL success, NSString *deviceIP, NSError *error) {
                // Store device IP for HTTP communication
                self.glassesDeviceIP = deviceIP;
            }];
        } fail:^(NSInteger mode) {
            // Handle mode switching failure
        }];
}
```

## 4. WiFi Connection Management

### 4.1 GlassesWiFiHandler Implementation

The GlassesWiFiHandler class manages the complex WiFi connection process using iOS's NetworkExtension framework:

```objective-c
@interface GlassesWiFiHandler ()
@property (nonatomic, strong) NEHotspotConfigurationManager *wifiManager;
@property (nonatomic, strong) NEHotspotConfiguration *configuration;
@property (nonatomic, copy) GlassesWiFiHandlerCompletion completion;
@end
```

### 4.2 Hotspot Configuration Process

The system programmatically configures iOS to connect to the glasses hotspot:

```objective-c
- (void)connectToGlassesWiFi:(NSString *)ssid password:(NSString *)password {
    // Create hotspot configuration
    self.configuration = [[NEHotspotConfiguration alloc] initWithSSID:ssid
                                                                passphrase:password
                                                                    isWEP:NO];
    self.configuration.joinOnce = YES; // Auto-join behavior

    // Apply configuration to system
    [self.wifiManager applyConfiguration:self.configuration completionHandler:^(NSError *error) {
        if (error) {
            // Handle configuration errors (user denied, etc.)
        } else {
            // Start IP discovery process
            [self discoverGlassesIP];
        }
    }];
}
```

### 4.3 IP Discovery Algorithm

Since glasses can appear on different subnet ranges, the system implements intelligent IP discovery:

```objective-c
- (void)discoverGlassesIP {
    NSArray *candidateIPs = @[
        @"192.168.43.1",   // Android hotspot default
        @"192.168.4.1",    // Alternative hotspot range
        @"192.168.1.1",    // Router default
        @"192.168.0.1",    // Alternative subnet
        @"10.0.0.1"        // Private network range
    ];

    // Concurrent discovery with timeout handling
    dispatch_group_t group = dispatch_group_create();
    __block NSString *foundIP = nil;

    for (NSString *ip in candidateIPs) {
        dispatch_group_enter(group);
        [self testGlassesConnection:ip completion:^(BOOL reachable) {
            if (reachable && !foundIP) {
                foundIP = ip;
            }
            dispatch_group_leave(group);
        }];
    }

    // Wait for discovery with timeout
    dispatch_group_wait(group, dispatch_time(DISPATCH_TIME_NOW, 10 * NSEC_PER_SEC));

    if (foundIP) {
        // Proceed with HTTP communication
        [self startHTTPCommunication:foundIP];
    }
}
```

## 5. HTTP Transfer Protocol

### 5.1 Manifest Discovery

Once IP connectivity is established, the system requests a media manifest:

```objective-c
- (void)fetchMediaManifest:(NSString *)deviceIP {
    NSString *manifestURL = [NSString stringWithFormat:@"http://%@/manifest.json", deviceIP];

    NSURLSessionDataTask *task = [NSURLSession.sharedSession dataTaskWithURL:[NSURL URLWithString:manifestURL]
        completionHandler:^(NSData *data, NSURLResponse *response, NSError *error) {
            if (error) {
                // Handle network errors
                return;
            }

            // Parse JSON manifest
            NSError *jsonError;
            NSDictionary *manifest = [NSJSONSerialization JSONObjectWithData:data options:0 error:&jsonError];

            if (!jsonError && manifest[@"files"]) {
                NSArray *mediaFiles = manifest[@"files"];
                [self processMediaFiles:mediaFiles fromDevice:deviceIP];
            }
        }];

    [task resume];
}
```

### 5.2 Concurrent File Downloads

The download manager implements sophisticated concurrent transfers:

```objective-c
- (void)downloadMediaFiles:(NSArray *)fileList fromDevice:(NSString *)deviceIP {
    dispatch_group_t downloadGroup = dispatch_group_create();
    __block NSInteger completedDownloads = 0;
    __block NSInteger totalBytesDownloaded = 0;

    for (NSDictionary *fileInfo in fileList) {
        dispatch_group_enter(downloadGroup);

        NSString *fileURL = [NSString stringWithFormat:@"http://%@/files/%@", deviceIP, fileInfo[@"filename"]];
        NSString *localPath = [self.localMediaDirectory stringByAppendingPathComponent:fileInfo[@"filename"]];

        [self downloadFile:fileURL toPath:localPath progress:^(int64_t bytesWritten, int64_t totalBytes) {
            // Update progress indicators
            totalBytesDownloaded += bytesWritten;
        } completion:^(BOOL success, NSError *error) {
            completedDownloads++;

            if (success) {
                // Generate thumbnail for UI
                [self generateThumbnailForFile:localPath type:fileInfo[@"type"]];
            }

            dispatch_group_leave(downloadGroup);
        }];
    }

    // Wait for all downloads to complete
    dispatch_group_notify(downloadGroup, dispatch_get_main_queue(), ^{
        // Update UI with completion status
        [self.delegate downloadCompletedWithTotalFiles:completedDownloads];
    });
}
```

## 6. Media Processing Pipeline

### 6.1 Image Processing

For image files (JPG, PNG, HEIC), the system generates optimized thumbnails:

```objective-c
- (UIImage *)generateImageThumbnail:(NSString *)imagePath {
    UIImage *originalImage = [UIImage imageWithContentsOfFile:imagePath];
    if (!originalImage) return nil;

    // Maintain aspect ratio while creating 100x100 thumbnail
    CGSize targetSize = CGSizeMake(100, 100);
    CGRect thumbnailRect = AVMakeRectWithAspectRatioInsideRect(originalImage.size, CGRectMake(0, 0, targetSize.width, targetSize.height));

    UIGraphicsBeginImageContextWithOptions(targetSize, NO, 0.0);
    [originalImage drawInRect:thumbnailRect];
    UIImage *thumbnail = UIGraphicsGetImageFromCurrentImageContext();
    UIGraphicsEndImageContext();

    return thumbnail;
}
```

### 6.2 Video Thumbnail Generation

Video files (MOV, MP4, M4V) require AVFoundation processing:

```objective-c
- (UIImage *)generateVideoThumbnail:(NSURL *)videoURL {
    AVAsset *asset = [AVAsset assetWithURL:videoURL];
    AVAssetImageGenerator *generator = [AVAssetImageGenerator assetImageGeneratorWithAsset:asset];
    generator.appliesPreferredTrackTransform = YES; // Fix orientation issues
    generator.maximumSize = CGSizeMake(100, 100);

    NSError *error;
    CGImageRef imageRef = [generator copyCGImageAtTime:kCMTimeZero actualTime:NULL error:&error];

    if (imageRef) {
        UIImage *thumbnail = [UIImage imageWithCGImage:imageRef];
        CGImageRelease(imageRef);
        return thumbnail;
    }

    // Fallback to video placeholder
    return [self createVideoPlaceholderThumbnail];
}
```

### 6.3 Audio Placeholder Generation

Audio files (OPUS) receive custom placeholder graphics:

```objective-c
- (UIImage *)createAudioPlaceholderThumbnail {
    CGSize size = CGSizeMake(100, 100);
    UIGraphicsBeginImageContextWithOptions(size, NO, 0.0);
    CGContextRef context = UIGraphicsGetCurrentContext();

    // Orange background for audio content
    [[UIColor orangeColor] setFill];
    CGContextFillRect(context, CGRectMake(0, 0, size.width, size.height));

    // ... draw audio icon ...
}
```

---

## Halo Implementation Notes (from CyanBridge)

### Android WiFi Media Transfer (AlbumDownloader.kt)

```kotlin
class AlbumDownloader(private val ctx: Context) {
    private val ok = OkHttpClient()

    suspend fun fetchConfig(baseIp: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val url = "http://$baseIp/files/media.config"
        // Fetch and parse media.config (one filename per line or JSON)
    }

    suspend fun fetchOne(baseIp: String, fname: String): File? = withContext(Dispatchers.IO) {
        val url = "http://$baseIp/files/$fname"
        // Download file to local storage
    }
}
```

### Android WiFi Connection (WifiP2pController.kt)

- Uses `WifiP2pManager` for WiFi Direct/P2P
- Not NEHotspotConfiguration (iOS-only)
- Handles device discovery, connection, and group management

---

## Transfer Architecture Summary

```
BLE Control Channel                    WiFi Data Channel
─────────────────────                  ─────────────────
1. setDeviceMode(Transfer)         →  2. Returns SSID + Password
     ↓                                    ↓
3. WiFi Connect (NEHotspot/WifiP2P)  →  4. IP Discovery (candidate IPs)
     ↓                                    ↓
5. HTTP GET /files/media.config    →  6. Manifest: { files: [...] }
     ↓                                    ↓
7. HTTP GET /files/<filename>      →  8. Download each file
     ↓                                    ↓
9. Local storage + thumbnails      →  10. Media gallery UI
```

---

## Key Parameters

| Parameter | Value | Source |
|-----------|-------|--------|
| WiFi Password | "123456789" (forced, glasses return wrong) | CyanBridge `GlassesWiFiHandler.m:66` |
| Candidate IPs | 192.168.43.1, 192.168.4.1, 192.168.31.1, 192.168.1.1, 192.168.0.1, 192.168.100.1, 192.168.123.1, 192.168.137.1, 10.0.0.1, 172.20.10.1 | `GlassesWiFiHandler.m` |
| Manifest Endpoint | `http://<IP>/files/media.config` | `GlassesWiFiHandler.m:197` |
| File Endpoint | `http://<IP>/files/<filename>` | `AlbumDownloader.kt:47` |
| Connection Test Timeout | 3s per IP | `GlassesWiFiHandler.m:203` |
| Total Discovery Timeout | 10s | `GlassesWiFiHandler.m:159` |

---

## Media Types

| Type Value | Media Type | File Extension | Thumbnail |
|------------|------------|----------------|-----------|
| 1 | Photo | .jpg, .png, .heic | Image thumbnail |
| 2 | Video | .mov, .mp4, .m4v | Video frame at t=0 |
| 3 | Audio | .opus | Orange placeholder |

---

## Confidence Levels

| Component | Confidence | Notes |
|-----------|------------|-------|
| Transfer mode activation | HIGH | Explicit in SDK |
| WiFi credentials flow | HIGH | Explicit in CyanBridge |
| IP discovery algorithm | HIGH | Explicit in CyanBridge |
| HTTP manifest/files | HIGH | Explicit in both |
| Android WiFi P2P | HIGH | CyanBridge `WifiP2pController` |
| Media config format | MEDIUM | `AlbumDownloader` assumes line-based |
| Exact media.config JSON | MEDIUM | Not fully documented |

---

## Unknowns

1. **Exact media.config JSON schema** - CyanBridge assumes one filename per line; could be JSON array
2. **Authentication** - No auth mentioned; possibly none
3. **Concurrent transfer limits** - Not documented
4. **Resume capability** - Not documented
5. **File size limits** - Not documented
6. **Transfer encryption** - Plain HTTP (no HTTPS mentioned)

---

## Halo PR-021 Implementation Target

```kotlin
interface HeyCyanMediaTransport {
    suspend fun getMediaManifest(): MediaManifest
    suspend fun download(file: MediaFile): ByteArray
}

data class MediaManifest(val files: List<MediaFile>)
data class MediaFile(
    val name: String,
    val type: MediaType,  // PHOTO=1, VIDEO=2, AUDIO=3
    val size: Long?,
    val createdAt: Instant?
)
```

Implementation should:
- Use OkHttp (like CyanBridge)
- Implement IP discovery with candidate IPs
- Handle WiFi connection via WifiP2pManager (Android)
- Parse manifest (support both line-based and JSON)
- Download with progress reporting
- Generate thumbnails for UI

---

## Evidence Sources

- `WIFI_TRANSFER_ARCHITECTURE.md` (iOS implementation)
- `GlassesWiFiHandler.m` (iOS WiFi connection + IP discovery)
- `GlassesMediaDownloader.m` (iOS download manager)
- `AlbumDownloader.kt` (Android download)
- `WifiP2pController.kt` (Android WiFi P2P)
- `WiFiTransferManager.m` (iOS transfer coordinator)
- SDK README (mode Transfer description)
