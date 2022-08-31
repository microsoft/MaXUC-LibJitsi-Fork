/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.avfoundation;

/**
 * Represents an AVFoundation <tt>CMSampleBuffer</tt> object.
 *
 * @author Lyubomir Marinov
 */
public class CMSampleBuffer
    extends NSObject
{
    /**
     * Initializes a new <tt>CMSampleBuffer</tt> which is to represent a
     * specific AVFoundation <tt>CMSampleBuffer</tt> object.
     *
     * @param ptr the pointer to the AVFoundation <tt>CMSampleBuffer</tt>
     * object to be represented by the new instance
     */
    public CMSampleBuffer(long ptr)
    {
        super(ptr);
    }

    public byte[] bytesForAllSamples()
    {
        return bytesForAllSamples(getPtr());
    }

    private static native byte[] bytesForAllSamples(long ptr);

    public CMFormatDescription formatDescription()
    {
        long formatDescriptionPtr = formatDescription(getPtr());

        return
            (formatDescriptionPtr == 0)
                ? null
                : new CMFormatDescription(formatDescriptionPtr);
    }

    private static native long formatDescription(long ptr);
}
