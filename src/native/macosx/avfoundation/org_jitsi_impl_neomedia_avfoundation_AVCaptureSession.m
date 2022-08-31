/*
 * SIP Communicator, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

#include "org_jitsi_impl_neomedia_avfoundation_AVCaptureSession.h"

#include "common.h"

#import <Foundation/NSAutoreleasePool.h>
#import <Foundation/NSError.h>
#import <AVFoundation/AVCaptureInput.h>
#import <AVFoundation/AVCaptureOutput.h>
#import <AVFoundation/AVCaptureSession.h>
#include <stdint.h>

JNIEXPORT jboolean JNICALL
Java_org_jitsi_impl_neomedia_avfoundation_AVCaptureSession_addInput
    (JNIEnv *jniEnv, jclass clazz, jlong ptr, jlong inputPtr)
{
    AVCaptureSession *captureSession;
    AVCaptureInput *input;
    NSAutoreleasePool *autoreleasePool;
    BOOL ret = NO;

    captureSession = (AVCaptureSession *) (intptr_t) ptr;
    input = (AVCaptureInput *) (intptr_t) inputPtr;
    autoreleasePool = [[NSAutoreleasePool alloc] init];

    if ([captureSession canAddInput:input])
    {
        [captureSession addInput:input];
        ret = YES;
    }

    [autoreleasePool release];
    return (YES == ret) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_org_jitsi_impl_neomedia_avfoundation_AVCaptureSession_addOutput
    (JNIEnv *jniEnv, jclass clazz, jlong ptr, jlong outputPtr)
{
    AVCaptureSession *captureSession;
    AVCaptureOutput *output;
    NSAutoreleasePool *autoreleasePool;
    BOOL ret = NO;

    captureSession = (AVCaptureSession *) (intptr_t) ptr;
    output = (AVCaptureOutput *) (intptr_t) outputPtr;
    autoreleasePool = [[NSAutoreleasePool alloc] init];

    if ([captureSession canAddOutput:output])
    {
        [captureSession addOutput:output];
        ret = YES;
    }

    [autoreleasePool release];
    return (YES == ret) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_avfoundation_AVCaptureSession_allocAndInit
    (JNIEnv *jniEnv, jclass clazz)
{
    NSAutoreleasePool *autoreleasePool;
    AVCaptureSession *captureSession;

    autoreleasePool = [[NSAutoreleasePool alloc] init];

    captureSession = [[AVCaptureSession alloc] init];

    [autoreleasePool release];
    return (jlong) (intptr_t) captureSession;
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_avfoundation_AVCaptureSession_startRunning
    (JNIEnv *jniEnv, jclass clazz, jlong ptr)
{
    NSObject_performSelector((id) (intptr_t) ptr, @"startRunning");
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_avfoundation_AVCaptureSession_stopRunning
    (JNIEnv *jniEnv, jclass clazz, jlong ptr)
{
    NSObject_performSelector((id) (intptr_t) ptr, @"stopRunning");
}
