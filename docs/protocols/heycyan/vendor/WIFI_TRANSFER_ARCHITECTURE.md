# Complete Technical Deep Dive: HeyCyan Smart Glasses WiFi Transfer System

## 1. Architecture Overview

The HeyCyan Smart Glasses SDK implements a sophisticated dual-protocol communication system that leverages both Bluetooth Low Energy (BLE) for control and WiFi for high-bandwidth data transfer. This hybrid approach solves the fundamental challenge of transferring large media files from resource-constrained smart glasses while maintaining real-time control capabilities.

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

    // Draw speaker icon using Core Graphics
    [[UIColor whiteColor] setFill];

    // Speaker body (rectangle)
    CGContextFillRect(context, CGRectMake(30, 35, 25, 30));

    // Speaker cone (triangle)
    CGContextMoveToPoint(context, 55, 35);
    CGContextAddLineToPoint(context, 75, 25);
    CGContextAddLineToPoint(context, 75, 75);
    CGContextAddLineToPoint(context, 55, 65);
    CGContextClosePath(context);
    CGContextFillPath(context);

    // Sound waves (curved lines)
    CGContextSetLineWidth(context, 2.0);
    [[UIColor whiteColor] setStroke];

    // First wave
    CGContextMoveToPoint(context, 80, 35);
    CGContextAddQuadCurveToPoint(context, 85, 50, 80, 65);
    CGContextStrokePath(context);

    UIImage *image = UIGraphicsGetImageFromCurrentImageContext();
    UIGraphicsEndImageContext();

    return image;
}
```

## 7. Gallery UI Implementation

### 7.1 UICollectionView Architecture

The media gallery uses UICollectionView with custom layout:

```objective-c
- (void)setupCollectionView {
    UICollectionViewFlowLayout *layout = [[UICollectionViewFlowLayout alloc] init];
    layout.minimumLineSpacing = 8.0;
    layout.minimumInteritemSpacing = 8.0;

    // Calculate 3-column grid with proper spacing
    CGFloat itemSize = (self.view.bounds.size.width - 32.0) / 3.0;
    layout.itemSize = CGSizeMake(itemSize, itemSize);

    self.collectionView = [[UICollectionView alloc] initWithFrame:self.view.bounds collectionViewLayout:layout];
    self.collectionView.dataSource = self;
    self.collectionView.delegate = self;

    [self.collectionView registerClass:[UICollectionViewCell class] forCellWithReuseIdentifier:@"MediaCell"];
}
```

### 7.2 Cell Configuration

Each cell handles different media types appropriately:

```objective-c
- (UICollectionViewCell *)collectionView:(UICollectionView *)collectionView cellForItemAtIndexPath:(NSIndexPath *)indexPath {
    UICollectionViewCell *cell = [collectionView dequeueReusableCellWithReuseIdentifier:@"MediaCell" forIndexPath:indexPath];

    NSURL *fileURL = self.mediaFiles[indexPath.row];
    UIImage *thumbnail = self.thumbnails[indexPath.row];
    NSString *fileExtension = fileURL.pathExtension.lowercaseString;

    // Configure image view
    UIImageView *imageView = [[UIImageView alloc] initWithFrame:CGRectMake(4, 4, cell.contentView.bounds.size.width - 8, cell.contentView.bounds.size.height - 8)];
    imageView.contentMode = UIViewContentModeScaleAspectFill;
    imageView.clipsToBounds = YES;
    imageView.image = thumbnail;

    // Add video badge for video files
    if ([@[@"mov", @"mp4", @"m4v"] containsObject:fileExtension]) {
        UIView *videoBadge = [[UIView alloc] initWithFrame:CGRectMake(imageView.bounds.size.width - 20, imageView.bounds.size.height - 20, 16, 16)];
        videoBadge.backgroundColor = [UIColor blackColor];
        videoBadge.alpha = 0.7;
        videoBadge.layer.cornerRadius = 8;

        UILabel *playLabel = [[UILabel alloc] initWithFrame:videoBadge.bounds];
        playLabel.text = @"▶";
        playLabel.textColor = [UIColor whiteColor];
        playLabel.font = [UIFont systemFontOfSize:8];
        playLabel.textAlignment = NSTextAlignmentCenter;
        [videoBadge addSubview:playLabel];

        [imageView addSubview:videoBadge];
    }

    [cell.contentView addSubview:imageView];
    return cell;
}
```

### 7.3 Media Playback Integration

The gallery handles different media playback scenarios:

```objective-c
- (void)openMediaFile:(NSURL *)fileURL {
    NSString *fileExtension = fileURL.pathExtension.lowercaseString;

    if ([@[@"jpg", @"jpeg", @"png", @"heic"] containsObject:fileExtension]) {
        [self openImageInFullscreen:fileURL];
    } else if ([@[@"mov", @"mp4", @"m4v"] containsObject:fileExtension]) {
        [self playVideo:fileURL];
    } else if ([@[@"opus"] containsObject:fileExtension]) {
        [self playAudio:fileURL];
    }
}

- (void)playVideo:(NSURL *)videoURL {
    AVPlayer *player = [AVPlayer playerWithURL:videoURL];
    AVPlayerViewController *playerViewController = [[AVPlayerViewController alloc] init];
    playerViewController.player = player;

    [self presentViewController:playerViewController animated:YES completion:^{
        [player play];
    }];
}
```

## 8. Error Handling & Recovery

### 8.1 Network Resilience

The system implements comprehensive error handling:

```objective-c
- (void)handleWiFiConnectionError:(NSError *)error {
    switch (error.code) {
        case NEHotspotConfigurationErrorInternal:
            // System-level configuration error
            [self retryWithDelay:2.0];
            break;

        case NEHotspotConfigurationErrorInvalidSSID:
            // Invalid hotspot credentials
            [self requestNewCredentials];
            break;

        case NEHotspotConfigurationErrorInvalidPassphrase:
            // Incorrect password
            [self tryDefaultPasswords];
            break;

        case NEHotspotConfigurationErrorUserDenied:
            // User cancelled system prompt
            [self showUserInstructions];
            break;
    }
}
```

### 8.2 Connection Recovery

Automatic recovery mechanisms handle disconnections:

```objective-c
- (void)attemptReconnection {
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, 3.0 * NSEC_PER_SEC), dispatch_get_main_queue(), ^{
        [self reconnectToGlasses];
    });
}
```

## 9. Memory Management & Performance

### 9.1 Thumbnail Caching

The system implements efficient thumbnail generation:

```objective-c
@property (nonatomic, strong) NSMutableDictionary *thumbnailCache;

- (UIImage *)getCachedThumbnail:(NSString *)filePath {
    return self.thumbnailCache[filePath];
}

- (void)cacheThumbnail:(UIImage *)thumbnail forPath:(NSString *)filePath {
    if (thumbnail && filePath) {
        self.thumbnailCache[filePath] = thumbnail;
    }
}
```

### 9.2 Background Processing

Heavy operations run on background queues:

```objective-c
dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0), ^{
    // Process thumbnails in background
    for (NSURL *fileURL in mediaFiles) {
        UIImage *thumbnail = [self generateThumbnailForFile:fileURL];

        dispatch_async(dispatch_get_main_queue(), ^{
            // Update UI on main thread
            [self.thumbnails addObject:thumbnail];
            [self.collectionView reloadData];
        });
    }
});
```

This complete implementation demonstrates a production-ready, enterprise-grade file transfer system that seamlessly integrates iOS networking capabilities with custom hardware protocols, providing users with a smooth experience for transferring and viewing media from smart glasses devices.