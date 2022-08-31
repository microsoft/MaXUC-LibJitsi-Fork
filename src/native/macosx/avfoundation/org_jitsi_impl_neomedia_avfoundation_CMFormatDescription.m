/*
 * SIP Communicator, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

#include "org_jitsi_impl_neomedia_avfoundation_CMFormatDescription.h"

#import <Foundation/NSAutoreleasePool.h>
#import <Foundation/NSGeometry.h>
#import <Foundation/NSString.h>
#import <Foundation/NSValue.h>
#import <AVFoundation/AVFoundation.h>
#import <CoreMedia/CoreMedia.h>

#include <stdint.h>

JNIEXPORT jobject JNICALL
Java_org_jitsi_impl_neomedia_avfoundation_CMFormatDescription_sizeForKey
    (JNIEnv *jniEnv, jclass clazz, jlong ptr)
{
    jobject size = NULL;
    CMFormatDescriptionRef formatDescription;
    NSAutoreleasePool *autoreleasePool;
    
    formatDescription = (CMFormatDescriptionRef) (intptr_t) ptr;
    autoreleasePool = [[NSAutoreleasePool alloc] init];
    
    jclass dimensionClass;
    
    dimensionClass = (*jniEnv)->FindClass(jniEnv, "java/awt/Dimension");
    CMVideoDimensions dimensions = CMVideoFormatDescriptionGetDimensions(formatDescription);

	if (dimensionClass)
	{
		jmethodID dimensionCtorMethodID;

		dimensionCtorMethodID
			= (*jniEnv)
				->GetMethodID(
					jniEnv,
					dimensionClass,
					"<init>",
					"(II)V");
		if (dimensionCtorMethodID)
			size
				= (*jniEnv)
					->NewObject(
						jniEnv,
						dimensionClass,
						dimensionCtorMethodID,
						(jint) dimensions.width,
						(jint) dimensions.height);
	}

	[autoreleasePool release];
		
    return size;
}

