/*
 * SIP Communicator, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

#include "org_jitsi_impl_neomedia_avfoundation_AVCaptureVideoDataOutput.h"

#import <CoreVideo/CVImageBuffer.h>
#import <CoreVideo/CVPixelBuffer.h>
#import <Foundation/NSAutoreleasePool.h>
#import <Foundation/NSDictionary.h>
#import <Foundation/NSObject.h>
#import <Dispatch/Dispatch.h>
#import <CoreFoundation/CoreFoundation.h>
#import <AVFoundation/AVCaptureVideoDataOutput.h>
#import <AVFoundation/AVFoundation.h>
#import <AVFoundation/AVCaptureOutput.h>
#import <AVFoundation/AVFoundation.h>
#import <stdint.h>

@interface AVCaptureVideoDataOutputSampleBufferDelegate : NSObject
{
@private
    jobject _delegate;
    JavaVM *_vm;
}

- (void)captureOutput:(AVCaptureOutput *)captureOutput 
		didOutputSampleBuffer:(CMSampleBufferRef)sampleBuffer 
		fromConnection:(AVCaptureConnection *)connection;
- (void)dealloc;
- (id)init;
- (void)setDelegate:(jobject)delegate inJNIEnv:(JNIEnv *)jniEnv;

@end

JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_avfoundation_AVCaptureVideoDataOutput_allocAndInit
    (JNIEnv *jniEnv, jclass clazz)
{
    NSAutoreleasePool *autoreleasePool;
    AVCaptureVideoDataOutput *captureVideoDataOutput;

    autoreleasePool = [[NSAutoreleasePool alloc] init];

    captureVideoDataOutput
        = [[AVCaptureVideoDataOutput alloc] init];

    [autoreleasePool release];
    return (jlong) (intptr_t) captureVideoDataOutput;
}

JNIEXPORT jdouble JNICALL
Java_org_jitsi_impl_neomedia_avfoundation_AVCaptureVideoDataOutput_videoMinFrameDuration
    (JNIEnv *jniEnv, jclass clazz, jlong ptr)
{
    AVCaptureVideoDataOutput *captureVideoDataOutput;
    AVCaptureConnection *captureConnection;
    NSAutoreleasePool *autoreleasePool;
    CMTime videoMinFrameDuration;

    captureVideoDataOutput = (AVCaptureVideoDataOutput *) (intptr_t) ptr;
    autoreleasePool = [[NSAutoreleasePool alloc] init];
    
    captureConnection = [captureVideoDataOutput connectionWithMediaType:AVMediaTypeVideo];
    videoMinFrameDuration = captureConnection.videoMinFrameDuration;

    [autoreleasePool release];
    return CMTimeGetSeconds(videoMinFrameDuration);
}

JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_avfoundation_AVCaptureVideoDataOutput_pixelBufferAttributes
    (JNIEnv *jniEnv, jclass clazz, jlong ptr)
{
    AVCaptureVideoDataOutput *captureVideoDataOutput;
    NSAutoreleasePool *autoreleasePool;
    autoreleasePool = [[NSAutoreleasePool alloc] init];
    captureVideoDataOutput = (AVCaptureVideoDataOutput *) (intptr_t) ptr;

    NSDictionary *result = captureVideoDataOutput.videoSettings;
    [result retain];
    
    [autoreleasePool release];
    return (jlong) (intptr_t) result;
}

JNIEXPORT jboolean JNICALL
Java_org_jitsi_impl_neomedia_avfoundation_AVCaptureVideoDataOutput_setAlwaysDiscardsLateVideoFrames
    (JNIEnv *jniEnv, jclass clazz, jlong ptr,
        jboolean alwaysDiscardsLateVideoFrames)
{
    AVCaptureVideoDataOutput *captureVideoDataOutput;
    NSAutoreleasePool *autoreleasePool;

    captureVideoDataOutput = (AVCaptureVideoDataOutput *) (intptr_t) ptr;
    autoreleasePool = [[NSAutoreleasePool alloc] init];

    if ([captureVideoDataOutput
            respondsToSelector:@selector(alwaysDiscardsLateVideoFrames)])
    {
        captureVideoDataOutput.alwaysDiscardsLateVideoFrames =
                ((JNI_TRUE == alwaysDiscardsLateVideoFrames) ? YES : NO);
    }
    else
        alwaysDiscardsLateVideoFrames = JNI_FALSE;
    
    [autoreleasePool release];
    return alwaysDiscardsLateVideoFrames;
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_avfoundation_AVCaptureVideoDataOutput_setDelegate
    (JNIEnv *jniEnv, jclass clazz, jlong ptr, jobject delegate)
{
    AVCaptureVideoDataOutput *captureVideoDataOutput;
    NSAutoreleasePool *autoreleasePool;
    AVCaptureVideoDataOutputSampleBufferDelegate *oDelegate;
    id oPrevDelegate;

    captureVideoDataOutput = (AVCaptureVideoDataOutput *) (intptr_t) ptr;
    autoreleasePool = [[NSAutoreleasePool alloc] init];

    if (delegate)
    {
        oDelegate = [[AVCaptureVideoDataOutputSampleBufferDelegate alloc] init];
        [oDelegate setDelegate:delegate inJNIEnv:jniEnv];
    }
    else
        oDelegate = nil;
    oPrevDelegate = captureVideoDataOutput.sampleBufferDelegate;
    if (oDelegate != oPrevDelegate)
    {
        dispatch_queue_t video_queue = dispatch_queue_create("video_queue", NULL);
        [captureVideoDataOutput setSampleBufferDelegate:(id<AVCaptureVideoDataOutputSampleBufferDelegate>)oDelegate queue:video_queue];
        dispatch_release(video_queue);
        if (oPrevDelegate)
            [oPrevDelegate release];
    }

    [autoreleasePool release];
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_avfoundation_AVCaptureVideoDataOutput_setVideoMinFrameDuration
    (JNIEnv *jniEnv, jclass clazz, jlong ptr, jdouble videoMinFrameDuration)
{
    AVCaptureVideoDataOutput *captureVideoDataOutput;
    AVCaptureConnection *captureConnection;
    NSAutoreleasePool *autoreleasePool;

    captureVideoDataOutput = (AVCaptureVideoDataOutput *) (intptr_t) ptr;
    autoreleasePool = [[NSAutoreleasePool alloc] init];
    
    captureConnection = [captureVideoDataOutput connectionWithMediaType:AVMediaTypeVideo];
    
    if (captureConnection.supportsVideoMinFrameDuration)
        captureConnection.videoMinFrameDuration = CMTimeMake(videoMinFrameDuration,1);
	
    [autoreleasePool release];
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_avfoundation_AVCaptureVideoDataOutput_setPixelBufferAttributes
    (JNIEnv *jniEnv, jclass clazz, jlong ptr, jlong pixelBufferAttributesPtr)
{
    AVCaptureVideoDataOutput *captureVideoDataOutput;
    NSDictionary *pixelBufferAttributes;
    NSAutoreleasePool *autoreleasePool;

    captureVideoDataOutput = (AVCaptureVideoDataOutput *) (intptr_t) ptr;
    pixelBufferAttributes = (NSDictionary *) (intptr_t) pixelBufferAttributesPtr;
    autoreleasePool = [[NSAutoreleasePool alloc] init];
	
    captureVideoDataOutput.videoSettings = pixelBufferAttributes;

    [autoreleasePool release];
}

@implementation AVCaptureVideoDataOutputSampleBufferDelegate

- (void)captureOutput:(AVCaptureOutput *)captureOutput 
		didOutputSampleBuffer:(CMSampleBufferRef)sampleBuffer 
		fromConnection:(AVCaptureConnection *)connection		
{
    jobject delegate;
    JavaVM *vm;
    JNIEnv *jniEnv;
    jclass delegateClass;

    delegate = self->_delegate;
    if (!delegate)
        return;

    vm = self->_vm;
    if (0 != (*vm)->AttachCurrentThreadAsDaemon(vm, (void **) &jniEnv, NULL))
        return;

    delegateClass = (*jniEnv)->GetObjectClass(jniEnv, delegate);
    if (delegateClass)
    {
        jmethodID didOutputVideoFrameWithSampleBufferMethodID;

        didOutputVideoFrameWithSampleBufferMethodID
            = (*jniEnv)
                ->GetMethodID(
                    jniEnv,
                    delegateClass,
                    "outputVideoFrameWithSampleBuffer",
                    "(JJ)V");
        if (didOutputVideoFrameWithSampleBufferMethodID)
		{
		    CVImageBufferRef videoFrame = CMSampleBufferGetImageBuffer(sampleBuffer);
            (*jniEnv)->CallVoidMethod(
                    jniEnv,
                    delegate,
                    didOutputVideoFrameWithSampleBufferMethodID,
                    (jlong) (intptr_t) videoFrame,
                    (jlong) (intptr_t) sampleBuffer);
					
		}
    }
    (*jniEnv)->ExceptionClear(jniEnv);
}

- (void)dealloc
{
    [self setDelegate:NULL inJNIEnv:NULL];
    [super dealloc];
}

- (id)init
{
    if ((self = [super init]))
    {
        self->_delegate = NULL;
        self->_vm = NULL;
    }

    return self;
}

- (void)setDelegate:(jobject)delegate inJNIEnv:(JNIEnv *)jniEnv
{	
    if (self->_delegate)
    {
        if (!jniEnv)
            (*(self->_vm))->AttachCurrentThread(self->_vm, (void **) &jniEnv, NULL);
        (*jniEnv)->DeleteGlobalRef(jniEnv, self->_delegate);
        self->_delegate = NULL;
        self->_vm = NULL;
    }
    if (delegate)
    {
        delegate = (*jniEnv)->NewGlobalRef(jniEnv, delegate);
        if (delegate)
        {
            (*jniEnv)->GetJavaVM(jniEnv, &(self->_vm));
            self->_delegate = delegate;
        }
    }
}

@end
