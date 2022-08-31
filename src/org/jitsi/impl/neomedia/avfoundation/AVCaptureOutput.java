/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.avfoundation;

/**
 * Represents an AVFoundation <tt>AVCaptureOutput</tt> object.
 *
 * @author Lyubomir Marinov
 */
public class AVCaptureOutput
    extends NSObject
{
    /**
     * Initializes a new <tt>AVCaptureOutput</tt> instance which is to represent
     * a specific AVFoundation <tt>AVCaptureOutput</tt> object.
     *
     * @param ptr the pointer to the AVFoundation <tt>AVCaptureOutput</tt> object to be
     * represented by the new instance
     */
    public AVCaptureOutput(long ptr)
    {
        super(ptr);
    }
}
