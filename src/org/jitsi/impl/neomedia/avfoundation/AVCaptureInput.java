/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
// Portions (c) Microsoft Corporation. All rights reserved.
package org.jitsi.impl.neomedia.avfoundation;

/**
 * Represents an AVFoundation <tt>AVCaptureInput</tt> object.
 *
 * @author Lyubomir Marinov
 */
public class AVCaptureInput
    extends NSObject
{
    /**
     * Initializes a new <tt>AVCaptureInput</tt> instance which is to represent
     * a specific AVFoundation <tt>AVCaptureInput</tt> object.
     *
     * @param ptr the pointer to the AVFoundation <tt>AVCaptureInput</tt> object to be
     * represented by the new instance
     */
    public AVCaptureInput(long ptr)
    {
        super(ptr);
    }
}
