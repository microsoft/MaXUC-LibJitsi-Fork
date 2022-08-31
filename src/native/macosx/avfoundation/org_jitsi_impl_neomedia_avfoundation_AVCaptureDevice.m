/*
 * SIP Communicator, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

#include "org_jitsi_impl_neomedia_avfoundation_AVCaptureDevice.h"

#include "common.h"

#include <stdint.h>
#include <string.h>

#import <Foundation/NSArray.h>
#import <Foundation/NSAutoreleasePool.h>
#import <Foundation/NSError.h>
#import <Foundation/NSObject.h> /* NSSelectorFromString */
#import <Foundation/NSString.h>
#import <AVFoundation/AVCaptureDevice.h>
#import <AVFoundation/AVFoundation.h>

jstring AVCaptureDevice_getString(JNIEnv *, jlong, NSString *);
NSString * AVCaptureDevice_jstringToMediaType(JNIEnv *, jobject);
jlongArray AVCaptureDevice_nsArrayToJlongArray(JNIEnv *, NSArray *);

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_avfoundation_AVCaptureDevice_close
    (JNIEnv *jniEnv, jclass clazz, jlong ptr)
{
    NSObject_performSelector((id) (intptr_t) ptr, @"close");
}

JNIEXPORT jlongArray JNICALL
Java_org_jitsi_impl_neomedia_avfoundation_AVCaptureDevice_formatDescriptions
    (JNIEnv *jniEnv, jclass clazz, jlong ptr)
{
    AVCaptureDevice *captureDevice;
    NSAutoreleasePool *autoreleasePool;
    NSArray *formatDescriptions;
    jlongArray formatDescriptionPtrs;

    captureDevice = (AVCaptureDevice *) (intptr_t) ptr;
    autoreleasePool = [[NSAutoreleasePool alloc] init];

    formatDescriptions = captureDevice.formats;
	
    formatDescriptionPtrs
        = AVCaptureDevice_nsArrayToJlongArray(jniEnv, formatDescriptions);

    [autoreleasePool release];
    return formatDescriptionPtrs;
}

JNIEXPORT jlongArray JNICALL
Java_org_jitsi_impl_neomedia_avfoundation_AVCaptureDevice_inputDevicesWithMediaType
    (JNIEnv *jniEnv, jclass clazz, jstring mediaType)
{
    NSAutoreleasePool *autoreleasePool;
    NSArray *inputDevices;
    jlongArray inputDevicePtrs;

    autoreleasePool = [[NSAutoreleasePool alloc] init];
	
	// AVCaptureDeviceDiscoverySession is only available from Catalina onwards,
	// but devicesWithMediaType is deprecated so we need both implementations.
	if (@available(macOS 10.15, *)) 
	{
		AVCaptureDeviceDiscoverySession *captureDeviceDiscoverySession = 
		[AVCaptureDeviceDiscoverySession discoverySessionWithDeviceTypes:@[AVCaptureDeviceTypeBuiltInWideAngleCamera, AVCaptureDeviceTypeExternalUnknown]
		                                                                  mediaType:AVCaptureDevice_jstringToMediaType(jniEnv, mediaType)
																		  position: AVCaptureDevicePositionUnspecified];    
		inputDevices = [captureDeviceDiscoverySession devices];		
	}
	else
	{
		inputDevices = [AVCaptureDevice devicesWithMediaType: AVCaptureDevice_jstringToMediaType(jniEnv, mediaType)];
	}
	
	inputDevicePtrs = AVCaptureDevice_nsArrayToJlongArray(jniEnv, inputDevices);

    [autoreleasePool release];
    return inputDevicePtrs;
}

JNIEXPORT jboolean JNICALL
Java_org_jitsi_impl_neomedia_avfoundation_AVCaptureDevice_isConnected
    (JNIEnv *jniEnv, jclass clazz, jlong ptr)
{	
	BOOL ret = (YES == (BOOL) (intptr_t) NSObject_performSelector((id) (intptr_t) ptr, @"isConnected"));	
    return ret ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_org_jitsi_impl_neomedia_avfoundation_AVCaptureDevice_localizedName
    (JNIEnv *jniEnv, jclass clazz, jlong ptr)
{
	jstring name = AVCaptureDevice_getString (jniEnv, ptr, @"localizedName");
    return name;
}

JNIEXPORT jboolean JNICALL
Java_org_jitsi_impl_neomedia_avfoundation_AVCaptureDevice_open
    (JNIEnv *jniEnv, jclass clazz, jlong ptr)
{
    AVCaptureDevice *captureDevice;
    NSAutoreleasePool *autoreleasePool;
    NSError *error;
   
    captureDevice = (AVCaptureDevice *) (intptr_t) ptr;
    autoreleasePool = [[NSAutoreleasePool alloc] init];
	
    AVCaptureDeviceInput *captureDeviceInput = [[AVCaptureDeviceInput alloc] initWithDevice:captureDevice error:&error];

    [autoreleasePool release];

    return (captureDeviceInput) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_org_jitsi_impl_neomedia_avfoundation_AVCaptureDevice_uniqueID
    (JNIEnv *jniEnv, jclass clazz, jlong ptr)
{
	jstring uniqueID = AVCaptureDevice_getString (jniEnv, ptr, @"uniqueID");
    return uniqueID;
}

jstring
AVCaptureDevice_getString(JNIEnv *jniEnv, jlong ptr, NSString *selectorName)
{
    id obj;
    NSAutoreleasePool *autoreleasePool;
    SEL selector;
    NSString *str;
    jstring jstr;

    obj = (id) (intptr_t) ptr;
    autoreleasePool = [[NSAutoreleasePool alloc] init];

    selector = NSSelectorFromString(selectorName);
	
    if (selector)
    {
        str = [obj performSelector:selector];
        jstr = str ? (*jniEnv)->NewStringUTF(jniEnv, [str UTF8String]) : NULL;
    }
    else
        jstr = NULL;

    [autoreleasePool release];
    return jstr;
}

NSString *
AVCaptureDevice_jstringToMediaType(JNIEnv *jniEnv, jstring str)
{
    const char *cstr;
    NSString *mediaType;
	
    cstr = (const char *) (*jniEnv)->GetStringUTFChars (jniEnv, str, NULL);
	
    if (cstr)
    {
        if (0 == strcmp ("Muxed", cstr))
            mediaType = AVMediaTypeMuxed;
        else if (0 == strcmp ("Sound", cstr))
            mediaType = AVMediaTypeAudio;
        else if (0 == strcmp ("Video", cstr))
            mediaType = AVMediaTypeVideo;
        else
            mediaType = nil;
        (*jniEnv)->ReleaseStringUTFChars (jniEnv, str, cstr);
    }
    else
        mediaType = nil;

    return mediaType;
}

jlongArray
AVCaptureDevice_nsArrayToJlongArray(JNIEnv *jniEnv, NSArray *oArray)
{
    jlongArray jArray;

    if (oArray)
    {
        NSUInteger count;

        count = [oArray count];
        jArray = (*jniEnv)->NewLongArray(jniEnv, count);
		
        if (jArray)
        {
            NSUInteger i;

            for (i = 0; i < count; i++)
            {
                id obj;
                jlong ptr;

                obj = [oArray objectAtIndex:i];
                ptr = (jlong) (intptr_t) obj;
                (*jniEnv)->SetLongArrayRegion(jniEnv, jArray, i, 1, &ptr);
                [obj retain];
                if ((*jniEnv)->ExceptionCheck(jniEnv))
                {
                    NSUInteger j;

                    for (j = 0; j < i; j++)
                        [[oArray objectAtIndex:j] release];
                    break;
                }
            }
        }
    }
    else
        jArray = NULL;
	
    return jArray;
}
