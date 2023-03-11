#import "StreamingMedia.h"
#import <Cordova/CDV.h>
#import <AVFoundation/AVFoundation.h>
#import <AVKit/AVKit.h>
#import "LandscapeVideo.h"
#import "PortraitVideo.h"

@interface StreamingMedia() <AVAssetResourceLoaderDelegate>
- (void)parseOptions:(NSDictionary *) options type:(NSString *) type;
- (void)play:(CDVInvokedUrlCommand *) command type:(NSString *) type;
- (void)setBackgroundColor:(NSString *)color;
- (void)setImage:(NSString*)imagePath withScaleType:(NSString*)imageScaleType;
- (UIImage*)getImage: (NSString *)imageName;
- (void)startPlayer:(NSString*)uri;
- (void)moviePlayBackDidFinish:(NSNotification*)notification;
- (void)cleanup;
@end

@implementation StreamingMedia {
    NSString* callbackId;
    AVPlayerViewController *moviePlayer;
    BOOL shouldAutoClose;
    UIColor *backgroundColor;
    UIImageView *imageView;
    BOOL initFullscreen;
    NSString *mOrientation;
    NSString *language;
    NSString *startFrom;
    NSString *videoType;
    AVPlayer *movie;
    BOOL controls;
}

NSString * const TYPE_VIDEO = @"VIDEO";
NSString * const TYPE_AUDIO = @"AUDIO";
NSString * const DEFAULT_IMAGE_SCALE = @"center";
int const ENDING_THRESHOLD = 60;

-(void)parseOptions:(NSDictionary *)options type:(NSString *) type {
    // Common options
    mOrientation = options[@"orientation"] ?: @"default";
    language = options[@"language"] ?: @"ru";
    startFrom = options[@"startFrom"] ?: @"0";

    if (![options isKindOfClass:[NSNull class]] && [options objectForKey:@"shouldAutoClose"]) {
        shouldAutoClose = [[options objectForKey:@"shouldAutoClose"] boolValue];
    } else {
        shouldAutoClose = YES;
    }
    if (![options isKindOfClass:[NSNull class]] && [options objectForKey:@"bgColor"]) {
        [self setBackgroundColor:[options objectForKey:@"bgColor"]];
    } else {
        backgroundColor = [UIColor blackColor];
    }

    if (![options isKindOfClass:[NSNull class]] && [options objectForKey:@"initFullscreen"]) {
        initFullscreen = [[options objectForKey:@"initFullscreen"] boolValue];
    } else {
        initFullscreen = YES;
    }

    if (![options isKindOfClass:[NSNull class]] && [options objectForKey:@"controls"]) {
        controls = [[options objectForKey:@"controls"] boolValue];
    } else {
        controls = YES;
    }

    if ([type isEqualToString:TYPE_AUDIO]) {
        videoType = TYPE_AUDIO;

        // bgImage
        // bgImageScale
        if (![options isKindOfClass:[NSNull class]] && [options objectForKey:@"bgImage"]) {
            NSString *imageScale = DEFAULT_IMAGE_SCALE;
            if (![options isKindOfClass:[NSNull class]] && [options objectForKey:@"bgImageScale"]) {
                imageScale = [options objectForKey:@"bgImageScale"];
            }
            [self setImage:[options objectForKey:@"bgImage"] withScaleType:imageScale];
        }
        // bgColor
        if (![options isKindOfClass:[NSNull class]] && [options objectForKey:@"bgColor"]) {
            NSLog(@"Found option for bgColor");
            [self setBackgroundColor:[options objectForKey:@"bgColor"]];
        } else {
            backgroundColor = [UIColor blackColor];
        }
    } else {
        // Reset overlay on video player after playing audio
        [self cleanup];
    }
    // No specific options for video yet
}

-(void)play:(CDVInvokedUrlCommand *) command type:(NSString *) type {
    NSLog(@"play called");
    callbackId = command.callbackId;
    NSString *mediaUrl = [command.arguments objectAtIndex:0];
    [self parseOptions:[command.arguments objectAtIndex:1] type:type];

    [self startPlayer:mediaUrl];
}

-(void)stop:(CDVInvokedUrlCommand *) command type:(NSString *) type {
    NSLog(@"stop called");
    callbackId = command.callbackId;
    if (moviePlayer.player) {
        [moviePlayer.player pause];
    }
}

-(void)playVideo:(CDVInvokedUrlCommand *) command {
    NSLog(@"playvideo called");
    [self ignoreMute];
    [self play:command type:[NSString stringWithString:TYPE_VIDEO]];
}

-(void)playAudio:(CDVInvokedUrlCommand *) command {
    NSLog(@"playaudio called");
    [self ignoreMute];
    [self play:command type:[NSString stringWithString:TYPE_AUDIO]];
}

-(void)stopAudio:(CDVInvokedUrlCommand *) command {
    [self stop:command type:[NSString stringWithString:TYPE_AUDIO]];
}

// Ignore the mute button
-(void)ignoreMute {
    AVAudioSession *session = [AVAudioSession sharedInstance];
    [session setCategory:AVAudioSessionCategoryPlayback error:nil];
}

-(void) setBackgroundColor:(NSString *)color {
    NSLog(@"setbackgroundcolor called");
    if ([color hasPrefix:@"#"]) {
        // HEX value
        unsigned rgbValue = 0;
        NSScanner *scanner = [NSScanner scannerWithString:color];
        [scanner setScanLocation:1]; // bypass '#' character
        [scanner scanHexInt:&rgbValue];
        backgroundColor = [UIColor colorWithRed:((float)((rgbValue & 0xFF0000) >> 16))/255.0 green:((float)((rgbValue & 0xFF00) >> 8))/255.0 blue:((float)(rgbValue & 0xFF))/255.0 alpha:1.0];
    } else {
        // Color name
        NSString *selectorString = [[color lowercaseString] stringByAppendingString:@"Color"];
        SEL selector = NSSelectorFromString(selectorString);
        UIColor *colorObj = [UIColor blackColor];
        if ([UIColor respondsToSelector:selector]) {
            colorObj = [UIColor performSelector:selector];
        }
        backgroundColor = colorObj;
    }
}

-(UIImage*)getImage: (NSString *)imageName {
    NSLog(@"getimage called");
    UIImage *image = nil;
    if (imageName != (id)[NSNull null]) {
        if ([imageName hasPrefix:@"http"]) {
            // Web image
            image = [UIImage imageWithData:[NSData dataWithContentsOfURL:[NSURL URLWithString:imageName]]];
        } else if ([imageName hasPrefix:@"www/"]) {
            // Asset image
            image = [UIImage imageNamed:imageName];
        } else if ([imageName hasPrefix:@"file://"]) {
            // Stored image
            image = [UIImage imageWithData:[NSData dataWithContentsOfFile:[[NSURL URLWithString:imageName] path]]];
        } else if ([imageName hasPrefix:@"data:"]) {
            // base64 encoded string
            NSURL *imageURL = [NSURL URLWithString:imageName];
            NSData *imageData = [NSData dataWithContentsOfURL:imageURL];
            image = [UIImage imageWithData:imageData];
        } else {
            // explicit path
            image = [UIImage imageWithData:[NSData dataWithContentsOfFile:imageName]];
        }
    }
    return image;
}

- (void)orientationChanged:(NSNotification *)notification {
    NSLog(@"orientationchanged called");
    if (imageView != nil) {
        // adjust imageView for rotation
        imageView.bounds = moviePlayer.contentOverlayView.bounds;
        imageView.frame = moviePlayer.contentOverlayView.frame;
    }
}

-(void)setImage:(NSString*)imagePath withScaleType:(NSString*)imageScaleType {
    NSLog(@"setimage called");
    imageView = [[UIImageView alloc] initWithFrame:self.viewController.view.bounds];

    if (imageScaleType == nil) {
        NSLog(@"imagescaletype was NIL");
        imageScaleType = DEFAULT_IMAGE_SCALE;
    }

    if ([imageScaleType isEqualToString:@"stretch"]){
        // Stretches image to fill all available background space, disregarding aspect ratio
        imageView.contentMode = UIViewContentModeScaleToFill;
    } else if ([imageScaleType isEqualToString:@"fit"]) {
        // fits entire image perfectly
        imageView.contentMode = UIViewContentModeScaleAspectFit;
    } else if ([imageScaleType isEqualToString:@"aspectStretch"]) {
        // Stretches image to fill all possible space while retaining aspect ratio
        imageView.contentMode = UIViewContentModeScaleAspectFill;
    } else {
        // Places image in the center of the screen
        imageView.contentMode = UIViewContentModeCenter;
        //moviePlayer.backgroundView.contentMode = UIViewContentModeCenter;
    }

    [imageView setImage:[self getImage:imagePath]];
}

-(void)startPlayer:(NSString*)uri {
    NSLog(@"startplayer called");

    NSURL *url = [NSURL URLWithString:uri];
    AVURLAsset *asset = [AVURLAsset URLAssetWithURL:url options:nil];
    AVPlayerItem *item = [AVPlayerItem playerItemWithAsset:asset];
    [self setLanguageIfAvailable:asset item:item];
    [asset.resourceLoader setDelegate:self queue:dispatch_get_main_queue()];

    movie = [AVPlayer playerWithPlayerItem:item];

    // handle orientation
    [self handleOrientation];

    // handle gestures
    [self handleGestures];

    [moviePlayer setPlayer:movie];
    [moviePlayer setShowsPlaybackControls:controls];
    [moviePlayer setUpdatesNowPlayingInfoCenter:YES];

    if(@available(iOS 11.0, *)) { [moviePlayer setEntersFullScreenWhenPlaybackBegins:YES]; }

    // present modally so we get a close button
    __weak StreamingMedia *weakSelf = self;
    [self.viewController presentViewController:moviePlayer animated:YES completion:^(void){
        __strong typeof(self) strongSelf = weakSelf;
        [self seekToVideoTime:strongSelf->startFrom];
    }];

    // add audio image and background color
    if ([videoType isEqualToString:TYPE_AUDIO]) {
        if (imageView != nil) {
            [moviePlayer.contentOverlayView setAutoresizesSubviews:YES];
            [moviePlayer.contentOverlayView addSubview:imageView];
        }
        moviePlayer.contentOverlayView.backgroundColor = backgroundColor;
        [self.viewController.view addSubview:moviePlayer.view];
    }

    // setup listners
    [self handleListeners];
}

- (void)setLanguageIfAvailable:(AVURLAsset *)asset item:(AVPlayerItem *)item {
    AVMediaSelectionGroup *group = [asset mediaSelectionGroupForMediaCharacteristic:AVMediaCharacteristicAudible];
    for (AVMediaSelectionOption *option in [group options]) {
        if([[option extendedLanguageTag] isEqualToString:language]) {
            [item selectMediaOption:option inMediaSelectionGroup:group];
            break;
        }
    }
}

- (void)seekToVideoTime:(NSString *)time {
    double seconds = [time doubleValue];
    double duration = [self getVideoDuration];

    if (duration > 0 && duration - seconds < ENDING_THRESHOLD) {
        seconds = 0;
    }

    if (!seconds) {
        [moviePlayer.player play];
    } else {
        BOOL isReadyToSeek = (movie.status == AVPlayerStatusReadyToPlay);// && (movie.currentItem.status == AVPlayerItemStatusReadyToPlay);
        if (isReadyToSeek) {
            CMTime targetTime = CMTimeMakeWithSeconds(seconds, NSEC_PER_SEC);
            __weak StreamingMedia *weakSelf = self;
            [movie seekToTime: targetTime
              toleranceBefore: kCMTimeZero
               toleranceAfter: kCMTimeZero
            completionHandler: ^(BOOL finished) {
                StreamingMedia *strongSelf = weakSelf;
                if (finished) {
                    [strongSelf->moviePlayer.player play];
                }
            }];
        }
    }
}

- (void) handleListeners {

    // Listen for re-maximize
    [[NSNotificationCenter defaultCenter] addObserver:self
                                             selector:@selector(appDidBecomeActive:)
                                                 name:UIApplicationDidBecomeActiveNotification
                                               object:nil];

    // Listen for minimize
    [[NSNotificationCenter defaultCenter] addObserver:self
                                             selector:@selector(appDidEnterBackground:)
                                                 name:UIApplicationDidEnterBackgroundNotification
                                               object:nil];

    // Listen for playback finishing
    [[NSNotificationCenter defaultCenter] addObserver:self
                                             selector:@selector(moviePlayBackDidFinish:)
                                                 name:AVPlayerItemDidPlayToEndTimeNotification
                                               object:moviePlayer.player.currentItem];

    // Listen for errors
    [[NSNotificationCenter defaultCenter] addObserver:self
                                             selector:@selector(moviePlayBackDidFinish:)
                                                 name:AVPlayerItemFailedToPlayToEndTimeNotification
                                               object:moviePlayer.player.currentItem];

    // Listen for orientation change
    [[NSNotificationCenter defaultCenter] addObserver:self
                                             selector:@selector(orientationChanged:)
                                                 name:UIDeviceOrientationDidChangeNotification
                                               object:nil];

    [moviePlayer addObserver:self
            forKeyPath:@"view.frame"
               options:(NSKeyValueObservingOptionNew | NSKeyValueObservingOptionInitial)
               context:nil];

    /* Listen for click on the "Done" button

     // Deprecated.. AVPlayerController doesn't offer a "Done" listener... thanks apple. We'll listen for an error when playback finishes
     [[NSNotificationCenter defaultCenter] addObserver:self
     selector:@selector(doneButtonClick:)
     name:MPMoviePlayerWillExitFullscreenNotification
     object:nil];
     */
}

- (void)observeValueForKeyPath:(NSString *)keyPath ofObject:(id)object change:(NSDictionary *)change context:(void *)context {
    if (moviePlayer.isBeingDismissed) {
        [self sendFinishTimePluginResult:[self getVideoCurrentTime]];
    }
}

- (void) handleGestures {
    // Get buried nested view
    UIView *contentView = [moviePlayer.view valueForKey:@"contentView"];

    // loop through gestures, remove swipes
    for (UIGestureRecognizer *recognizer in contentView.gestureRecognizers) {
        NSLog(@"gesture loop ");
        NSLog(@"%@", recognizer);
        if ([recognizer isKindOfClass:[UIPanGestureRecognizer class]]) {
            [contentView removeGestureRecognizer:recognizer];
        }
        if ([recognizer isKindOfClass:[UIPinchGestureRecognizer class]]) {
            [contentView removeGestureRecognizer:recognizer];
        }
        if ([recognizer isKindOfClass:[UIRotationGestureRecognizer class]]) {
            [contentView removeGestureRecognizer:recognizer];
        }
        if ([recognizer isKindOfClass:[UILongPressGestureRecognizer class]]) {
            [contentView removeGestureRecognizer:recognizer];
        }
        if ([recognizer isKindOfClass:[UIScreenEdgePanGestureRecognizer class]]) {
            [contentView removeGestureRecognizer:recognizer];
        }
        if ([recognizer isKindOfClass:[UISwipeGestureRecognizer class]]) {
            [contentView removeGestureRecognizer:recognizer];
        }
    }
}

- (void) handleOrientation {
    // hnadle the subclassing of the view based on the orientation variable
    if ([mOrientation isEqualToString:@"landscape"]) {
        moviePlayer            =  [[LandscapeAVPlayerViewController alloc] init];
    } else if ([mOrientation isEqualToString:@"portrait"]) {
        moviePlayer            =  [[PortraitAVPlayerViewController alloc] init];
    } else {
        moviePlayer            =  [[AVPlayerViewController alloc] init];
    }
}

- (void) appDidEnterBackground:(NSNotification*)notification {
    NSLog(@"appDidEnterBackground");

    if (moviePlayer && movie && videoType == TYPE_AUDIO)
    {
        NSLog(@"did set player layer to nil");
        [moviePlayer setPlayer: nil];
    }
}

- (void) appDidBecomeActive:(NSNotification*)notification {
    NSLog(@"appDidBecomeActive");

    if (moviePlayer && movie && videoType == TYPE_AUDIO)
    {
        NSLog(@"did reinstate playerlayer");
        [moviePlayer setPlayer:movie];
    }
}

- (void) sendFinishTimePluginResult:(double)finishTime {
    NSString *finishAt = [[NSNumber numberWithDouble:finishTime] stringValue];
    double duration = [self getVideoDuration];
    NSDictionary* params;

    if (duration > 0 && duration - finishTime < ENDING_THRESHOLD) {
        params = @{ @"finishAt": @"0" };
    } else {
        params = @{ @"finishAt": finishAt };
    }

    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:params];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:callbackId];
}

- (double) getVideoCurrentTime {
    double currentTime = 0;
    if (movie.currentItem) {
        CMTime time = movie.currentItem.currentTime;
        currentTime = CMTimeGetSeconds(time);
        if (isnan(currentTime)) {
            currentTime = -1;
        }
    }
    return currentTime;
}

- (double) getVideoDuration {
    double duration = 0;

    if (movie.currentItem) {
        CMTime time = movie.currentItem.duration;
        duration = CMTimeGetSeconds(time);

        if (isnan(duration)) {
            duration = -1;
        }
    }

    return duration;
}

- (void) moviePlayBackDidFinish:(NSNotification*)notification {
    NSLog(@"Playback did finish with auto close being %d, and error message being %@", shouldAutoClose, notification.userInfo);
    NSError *error = notification.userInfo[AVPlayerItemFailedToPlayToEndTimeErrorKey];
    NSString *errorMsg = error.localizedDescription
    ?: error.localizedFailureReason
    ?: @"Unknown error.";

    if (error) {
        NSLog(@"Playback failed: %@", errorMsg);
        // Temp fix for AVErrorServerIncorrectlyConfigured
        if (error.code == -11850) {
            return;
        } else {
            [self handlePlayBackDidFinishWithError:errorMsg];
        }
    } else if (shouldAutoClose) {
        [self handlePlayBackDidFinishWithSuccess];
    }
}

- (void) handlePlayBackDidFinishWithError:(NSString*)errorMsg {
    [self cleanup];
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:errorMsg];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:self->callbackId];
}

- (void) handlePlayBackDidFinishWithSuccess {
    [self cleanup];
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsBool:true];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:self->callbackId];
}

- (void) cleanup {
    NSLog(@"Clean up called");
    imageView = nil;
    initFullscreen = false;
    backgroundColor = nil;

    // Remove playback finished listener
    [[NSNotificationCenter defaultCenter]
     removeObserver:self
     name:AVPlayerItemDidPlayToEndTimeNotification
     object:moviePlayer.player.currentItem];
    // Remove playback finished error listener
    [[NSNotificationCenter defaultCenter]
     removeObserver:self
     name:AVPlayerItemFailedToPlayToEndTimeNotification
     object:moviePlayer.player.currentItem];
    // Remove orientation change listener
    [[NSNotificationCenter defaultCenter]
     removeObserver:self
     name:UIDeviceOrientationDidChangeNotification
     object:nil];

    if (moviePlayer) {
        [moviePlayer.player pause];
        [moviePlayer dismissViewControllerAnimated:YES completion:nil];
        moviePlayer = nil;
    }
}

#pragma mark - AVAssetResourceLoaderDelegate methods

- (BOOL)resourceLoader:(AVAssetResourceLoader *)resourceLoader shouldWaitForLoadingOfRequestedResource:(AVAssetResourceLoadingRequest *)loadingRequest
{
    return true;
}
@end
