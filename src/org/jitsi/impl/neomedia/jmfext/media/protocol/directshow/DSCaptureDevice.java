/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
// Portions (c) Microsoft Corporation. All rights reserved.
package org.jitsi.impl.neomedia.jmfext.media.protocol.directshow;

import java.util.*;

import org.jitsi.util.*;

/**
 * DirectShow capture device.
 *
 * @author Sebastien Vincent
 * @author Lyubomir Marinov
 */
public class DSCaptureDevice
{
    private static Logger sLog = Logger.getLogger(DSCaptureDevice.class);

    /**
     * The Java equivalent of the DirectShow <tt>ISampleGrabberCB</tt> interface
     * as it is utilized by <tt>DSCaptureDevice</tt>.
     */
    public interface ISampleGrabberCB
    {
        /**
         * Notifies this instance that a specific video frame has been
         * captured/grabbed.
         *
         * @param source pointer to the native <tt>DSCaptureDevice</tt> which is the
         * source of the notification
         * @param ptr a pointer to the captured/grabbed video frame i.e. to the
         * data of the DirectShow <tt>IMediaSample</tt>
         * @param length the length in bytes of the valid data pointed to by
         * <tt>ptr</tt>
         */
        void SampleCB(long source, long ptr, int length);
    }

    /**
     * Empty array with <tt>DSFormat</tt> element type. Explicitly defined
     * in order to avoid unnecessary allocations.
     */
    private static final DSFormat EMPTY_FORMATS[] = new DSFormat[0];

    public static final int S_FALSE = 1;

    public static final int S_OK = 0;

    static native int samplecopy(long thiz, long src, long dst, int length);

    /**
     * Native pointer of <tt>DSCaptureDevice</tt>.
     *
     * This pointer is held and will be released by <tt>DSManager</tt>
     * singleton.
     */
    private final long ptr;

    /**
     * Constructor.
     *
     * @param ptr native pointer
     */
    public DSCaptureDevice(long ptr)
    {
        /* Do not allow 0/NULL pointer value. */
        if (ptr == 0)
            throw new IllegalArgumentException("ptr");

        sLog.debug("Contruct new DSCaptureDevice with ptr " + ptr);
        this.ptr = ptr;
    }

    /**
     * Connects to this DirectShow video capture device.
     */
    public void connect()
    {
        sLog.debug("connect (ptr " + ptr + ")");
        connect(ptr);
    }

    /**
     * Connects to the specified DirectShow video capture device
     *
     * @param ptr a pointer to a native <tt>DSCaptureDevice</tt> to connect to
     */
    private native void connect(long ptr);

    /**
     * Disconnects from this DirectShow video capture device.
     */
    public void disconnect()
    {
        sLog.debug("disconnect (ptr " + ptr + ")");
        disconnect(ptr);
    }

    /**
     * Disconnects from a specific DirectShow video capture device
     *
     * @param ptr a pointer to a native <tt>DSCaptureDevice</tt> to disconnect
     * from
     */
    private native void disconnect(long ptr);

    /**
     * Get current format.
     *
     * @return current format used
     */
    public DSFormat getFormat()
    {
        DSFormat format = getFormat(ptr);
        sLog.debug("getFormat: " + ((format == null) ?
                            "null" : format.toString()) + " (ptr " + ptr + ")");
        return format;
    }

    /**
     * Native method to get format on the capture device.
     *
     * @param ptr native pointer of <tt>DSCaptureDevice</tt>
     * @return format current format
     */
    private native DSFormat getFormat(long ptr);

    /**
     * Get name of the capture device.
     *
     * @return name of the capture device
     */
    public String getName()
    {
        String name = getName(ptr).trim();
        sLog.debug("getName: " + name + " (ptr "+ ptr + ")");
        return name;
    }

    /**
     * Get path of the capture device.
     *
     * @return path of the capture device
     */
    public String getPath()
    {
        String path = getPath(ptr).trim();
        sLog.debug("getPath: " + path + " (ptr " + ptr + ")");
        return path;
    }

    /**
     * Native method to get the name of the capture device.
     *
     * @param ptr native pointer of <tt>DSCaptureDevice</tt>
     * @return name of the capture device
     */
    private native String getName(long ptr);

    /**
     * Native method to get the path of the capture device.
     *
     * @param ptr native pointer of <tt>DSCaptureDevice</tt>
     * @return path of the capture device
     */
    private native String getPath(long ptr);

    /**
     * Get the supported video format this capture device supports.
     *
     * @return array of <tt>DSFormat</tt>
     */
    public DSFormat[] getSupportedFormats()
    {
        DSFormat[] formats = getSupportedFormats(ptr);
        formats = (formats == null) ? EMPTY_FORMATS : formats;
        sLog.debug("getSupportedFormats: " +
                               Arrays.toString(formats) + " (ptr " + ptr + ")");

        return formats;
    }

    /**
     * Native method to get supported formats from capture device.
     *
     * @param ptr native pointer of <tt>DSCaptureDevice</tt>
     * @return array of native pointer corresponding to formats
     */
    private native DSFormat[] getSupportedFormats(long ptr);

    /**
     * Set a delegate to use when a frame is received.
     * @param delegate delegate
     */
    public void setDelegate(ISampleGrabberCB delegate)
    {
        sLog.debug("setDelegate: ptr " + ptr + " delegate " +
                           ((delegate == null) ? "null" : delegate.toString()));
        setDelegate(ptr, delegate);
    }

    /**
     * Native method to set a delegate to use when a frame is received.
     * @param ptr native pointer
     * @param delegate delegate
     */
    private native void setDelegate(long ptr, ISampleGrabberCB delegate);

    /**
     * Set format to use with this capture device.
     *
     * @param format format to set
     * @return an <tt>HRESULT</tt> value indicating whether the specified
     * <tt>format</tt> was successfully set or describing a failure
     *
     */
    public int setFormat(DSFormat format)
    {
        sLog.debug("setFormat: " + ((format == null) ?
                            "null" : format.toString()) + " (ptr " + ptr + ")");
        return setFormat(ptr, format);
    }

    /**
     * Native method to set format on the capture device.
     *
     * @param ptr native pointer of <tt>DSCaptureDevice</tt>
     * @param format format to set
     * @return an <tt>HRESULT</tt> value indicating whether the specified
     * <tt>format</tt> was successfully set or describing a failure
     */
    private native int setFormat(long ptr, DSFormat format);

    public int start()
    {
        sLog.debug("start (ptr " + ptr + ")");
        return start(ptr);
    }

    private native int start(long ptr);

    public int stop()
    {
        sLog.debug("stop (ptr " + ptr + ")");
        return stop(ptr);
    }

    private native int stop(long ptr);
}
