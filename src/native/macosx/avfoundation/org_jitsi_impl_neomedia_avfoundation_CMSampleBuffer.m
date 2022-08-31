/*
 * SIP Communicator, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

#include "org_jitsi_impl_neomedia_avfoundation_CMSampleBuffer.h"

#import <Foundation/NSAutoreleasePool.h>
#import <AVFoundation/AVFoundation.h>
#import <CoreMedia/CoreMedia.h>
#include <stdint.h>

JNIEXPORT jbyteArray JNICALL
Java_org_jitsi_impl_neomedia_avfoundation_CMSampleBuffer_bytesForAllSamples
    (JNIEnv *jniEnv, jclass clazz, jlong ptr)
{
    CMSampleBufferRef *sampleBuffer;
    NSAutoreleasePool *autoreleasePool;
    NSUInteger lengthForAllSamples;
    jbyteArray jBytesForAllSamples;

    sampleBuffer = (CMSampleBufferRef *) (intptr_t) ptr;
    autoreleasePool = [[NSAutoreleasePool alloc] init];

    lengthForAllSamples = CMSampleBufferGetTotalSampleSize(*sampleBuffer);
    if (lengthForAllSamples)
    {
        jBytesForAllSamples
            = (*jniEnv)->NewByteArray(jniEnv, lengthForAllSamples);
        if (jBytesForAllSamples)
        {
            char *blockBufferPointer;
            CMBlockBufferRef blockBuffer = CMSampleBufferGetDataBuffer(*sampleBuffer);
            CMBlockBufferGetDataPointer(blockBuffer, 0, NULL, &lengthForAllSamples, &blockBufferPointer);
            
            jbyte *bytesForAllSamples = (jbyte*) blockBufferPointer;

            (*jniEnv)
                ->SetByteArrayRegion(
                    jniEnv,
                    jBytesForAllSamples,
                    0,
                    lengthForAllSamples,
                    bytesForAllSamples);
        }
    }
    else
        jBytesForAllSamples = NULL;

    [autoreleasePool release];
    return jBytesForAllSamples;
}

JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_avfoundation_CMSampleBuffer_formatDescription
    (JNIEnv *jniEnv, jclass clazz, jlong ptr)
{
    CMSampleBufferRef *sampleBuffer;
    NSAutoreleasePool *autoreleasePool;
    CMFormatDescriptionRef *formatDescription = NULL;

    sampleBuffer = (CMSampleBufferRef *) (intptr_t) ptr;
    autoreleasePool = [[NSAutoreleasePool alloc] init];

    *formatDescription = CMSampleBufferGetFormatDescription(*sampleBuffer);

    [autoreleasePool release];
    return (jlong) (intptr_t) formatDescription;
}
