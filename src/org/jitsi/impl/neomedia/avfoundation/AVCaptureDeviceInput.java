/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.avfoundation;

import org.jitsi.util.Logger;

/**
 * Represents an AVFoundation <tt>AVCaptureDeviceInput</tt> object.
 *
 * @author Lyubomir Marinov
 */
public class AVCaptureDeviceInput
    extends AVCaptureInput
{
    private static final Logger sLog = Logger.getLogger(AVCaptureDeviceInput.class);

    /**
     * Initializes a new <tt>AVCaptureDeviceInput</tt> which is to represent a
     * specific AVFoundation <tt>AVCaptureDeviceInput</tt> object.
     *
     * @param ptr the pointer to the AVFoundation <tt>AVCaptureDeviceInput</tt> object
     * to be represented by the new instance
     */
    public AVCaptureDeviceInput(long ptr)
    {
        super(ptr);
    }

    public static AVCaptureDeviceInput deviceInputWithDevice(
            AVCaptureDevice device)
        throws IllegalArgumentException
    {
        sLog.debug("About to call native deviceInputWithDevice method for " +
                           device.localizedName());
        long deviceInputWithDevice = deviceInputWithDevice(device.getPtr());
        sLog.debug("Finished calling native method - deviceInputWithDevice = " +
                           deviceInputWithDevice);
        return new AVCaptureDeviceInput(deviceInputWithDevice);
    }

    private static native long deviceInputWithDevice(long devicePtr)
        throws IllegalArgumentException;

    /**
     * Called by the garbage collector to release system resources and perform
     * other cleanup.
     *
     * @see Object#finalize()
     */
    @Override
    protected void finalize()
    {
        release();
    }
}
