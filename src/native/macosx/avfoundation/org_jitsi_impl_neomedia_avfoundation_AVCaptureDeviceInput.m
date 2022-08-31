/*
 * SIP Communicator, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

#include "org_jitsi_impl_neomedia_avfoundation_AVCaptureDeviceInput.h"

#import <Foundation/NSException.h>
#import <AVFoundation/AVCaptureDevice.h>
#import <AVFoundation/AVCaptureInput.h>
#include <stdint.h>

JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_avfoundation_AVCaptureDeviceInput_deviceInputWithDevice
    (JNIEnv *jniEnv, jclass clazz, jlong devicePtr)
{
    AVCaptureDevice *device;
    NSAutoreleasePool *autoreleasePool;
    NSError *error;
    id deviceInput;

    device = (AVCaptureDevice *) (intptr_t) devicePtr;
    autoreleasePool = [[NSAutoreleasePool alloc] init];

    @try
    {
        deviceInput = [AVCaptureDeviceInput deviceInputWithDevice:device error:&error];
    }
    @catch (NSException *ex)
    {
        deviceInput = nil;
    }
    if (deviceInput)
        [deviceInput retain];

    [autoreleasePool release];
    return (jlong) (intptr_t) deviceInput;
}
