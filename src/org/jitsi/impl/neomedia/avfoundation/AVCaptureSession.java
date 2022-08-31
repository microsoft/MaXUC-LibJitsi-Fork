/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.avfoundation;

import org.jitsi.util.Logger;

/**
 * Represents an AVFoundation <tt>AVCaptureSession</tt> object.
 *
 * @author Lyubomir Marinov
 */
public class AVCaptureSession
    extends NSObject
{
    private static final Logger sLog = Logger.getLogger(AVCaptureSession.class);

    private boolean closed = false;

    /**
     * Initializes a new <tt>AVCaptureSession</tt> instance which represents a
     * new AVFoundation <tt>AVCaptureSession</tt> object.
     */
    public AVCaptureSession()
    {
        this(allocAndInit());
    }

    /**
     * Initializes a new <tt>AVCaptureSession</tt> instance which is to
     * represent a specific AVFoundation <tt>AVCaptureSession</tt> object.
     *
     * @param ptr the pointer to the AVFoundation <tt>AVCaptureSession</tt> object to
     * be represented by the new instance
     */
    public AVCaptureSession(long ptr)
    {
        super(ptr);
    }

    public boolean addInput(AVCaptureInput input)
        throws NSErrorException
    {
        long ptr = input.getPtr();
        sLog.debug("About to call native method to add input for " + ptr);
        boolean addInput = addInput(getPtr(), ptr);
        sLog.debug("Finished calling native method to add input.  Success? " +
                           addInput);
        return addInput;
    }

    private static native boolean addInput(long ptr, long inputPtr)
        throws NSErrorException;

    public boolean addOutput(AVCaptureOutput output)
        throws NSErrorException
    {
        long ptr = output.getPtr();
        sLog.debug("About to call native method to add input for " + ptr);
        boolean addOutput = addOutput(getPtr(), ptr);
        sLog.debug("Finished calling native method to add input.  Success? " +
                           addOutput);
        return addOutput;
    }

    private static native boolean addOutput(long ptr, long outputPtr)
            throws NSErrorException;

    private static native long allocAndInit();

    /**
     * Releases the resources used by this instance throughout its existence and
     * makes it available for garbage collection. This instance is considered
     * unusable after closing.
     */
    public synchronized void close()
    {
        if (!closed)
        {
            stopRunning();
            release();
            closed = true;
        }
    }

    /**
     * Called by the garbage collector to release system resources and perform
     * other cleanup.
     *
     * @see Object#finalize()
     */
    @Override
    protected void finalize()
    {
        close();
    }

    public void startRunning()
    {
        startRunning(getPtr());
    }

    private static native void startRunning(long ptr);

    public void stopRunning()
    {
        stopRunning(getPtr());
    }

    private static native void stopRunning(long ptr);
}
