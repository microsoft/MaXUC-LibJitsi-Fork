/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
// Portions (c) Microsoft Corporation. All rights reserved.
package org.jitsi.impl.neomedia.jmfext.media.protocol.directshow;

import java.awt.*;
import java.awt.image.*;
import java.io.*;

import javax.media.*;
import javax.media.control.*;
import javax.media.protocol.*;
import javax.swing.*;

import net.sf.fmj.media.Log;

import org.jitsi.impl.neomedia.codec.video.*;
import org.jitsi.impl.neomedia.jmfext.media.protocol.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.service.resources.*;
import org.jitsi.util.*;

/**
 * Implements a <tt>PushBufferStream</tt> using DirectShow.
 *
 * @author Lyubomir Marinov
 * @author Sebastien Vincent
 */
public class DirectShowStream
    extends AbstractPushBufferStream<DataSource>
{
    /**
     * The <tt>Logger</tt> used by the <tt>DirectShowStream</tt> class and its
     * instances to print out debugging information.
     */
    private static final Logger logger
        = Logger.getLogger(DirectShowStream.class);

    /**
     * Determines whether a specific <tt>Format</tt> appears to be suitable for
     * attempts to be set on <tt>DirectShowStream</tt> instances.
     * <p>
     * <b>Note</b>: If the method returns <tt>true</tt>, an actual attempt to
     * set the specified <tt>format</tt> on an specific
     * <tt>DirectShowStream</tt> instance may still fail but that will be
     * because the finer-grained properties of the <tt>format</tt> are not
     * supported by that <tt>DirectShowStream</tt> instance.
     * </p>
     *
     * @param format the <tt>Format</tt> to be checked whether it appears to be
     * suitable for attempts to be set on <tt>DirectShowStream</tt> instances
     * @return <tt>true</tt> if the specified <tt>format</tt> appears to be
     * suitable for attempts to be set on <tt>DirectShowStream</tt> instance;
     * otherwise, <tt>false</tt>
     */
    static boolean isSupportedFormat(Format format)
    {
        if (format instanceof AVFrameFormat)
        {
            AVFrameFormat avFrameFormat = (AVFrameFormat) format;
            long pixFmt = avFrameFormat.getDeviceSystemPixFmt();

            if (pixFmt != -1)
            {
                Dimension size = avFrameFormat.getSize();

                /*
                 * We will set the native format in doStart() because a
                 * connect-disconnect-connect sequence of the native capture
                 * device may reorder its formats in a different way.
                 * Consequently, in the absence of further calls to
                 * setFormat() by JMF, a crash may occur later (typically,
                 * during scaling) because of a wrong format.
                 */
                if (size != null)
                    return true;
            }
        }
        return false;
    }

    /**
     * The pool of <tt>NeomediaByteBuffer</tt>s this instance is using to transfer the
     * media data captured by {@link DSCaptureDevice.ISampleGrabberCB} out of this instance
     * through the <tt>Buffer</tt>s specified in its {@link #read(Buffer)}.
     */
    private final ByteBufferPool byteBufferPool = new ByteBufferPool();

    /**
     * The captured media data to be returned in {@link #read(Buffer)}.
     */
    private NeomediaByteBuffer data;

    /**
     * The <tt>Object</tt> which synchronizes the access to the
     * {@link #data}-related fields of this instance.
     */
    private final Object dataSyncRoot = new Object();

    /**
     * The time stamp in nanoseconds of {@link #data}.
     */
    private long dataTimeStamp;

    /**
     * Delegate class to handle video data.
     */
    private final DSCaptureDevice.ISampleGrabberCB delegate
        = new DSCaptureDevice.ISampleGrabberCB()
                {
                    @Override
                    public void SampleCB(long source, long ptr, int length)
                    {
                        DirectShowStream.this.SampleCB(source, ptr, length);
                    }
                };

    /**
     * Whether the device is active. This means it has been started and has
     * not yet been stopped
     */
    private boolean deviceRunning;

    /**
     * The <tt>DSCaptureDevice</tt> which identifies the DirectShow video
     * capture device this <tt>SourceStream</tt> is to capture data from.
     */
    private DSCaptureDevice device;

    /**
     * The last-known <tt>Format</tt> of the media data made available by this
     * <tt>PushBufferStream</tt>.
     */
    private Format format;

    /**
     * The resource string used to display an error message to the user if the
     * device cannot be read
     */
    private static final String deviceReadErrorProp =
                                 "service.protocol.media.ERROR_READING_WEBCAM";

    /**
     * The resource string used for the error message title
     */
    private static final String errorTitleProp = "service.gui.ERROR";

    /**
     * The captured media data to become the value of {@link #data} as soon as
     * the latter is consumed. Thus prepares this
     * <tt>DirectShowStream</tt> to provide the latest available frame and not
     * wait for DirectShow to capture a new one.
     */
    private NeomediaByteBuffer nextData;

    /**
     * The time stamp in nanoseconds of {@link #nextData}.
     */
    private long nextDataTimeStamp;

    /**
     * The <tt>Thread</tt> which is to call
     * {@link BufferTransferHandler#transferData(PushBufferStream)} for this
     * <tt>DirectShowStream</tt> so that the call is not made in DirectShow.
     */
    private Thread transferDataThread;

    /**
     * Initializes a new <tt>DirectShowStream</tt> instance which is to have its
     * <tt>Format</tt>-related information abstracted by a specific
     * <tt>FormatControl</tt>.
     *
     * @param dataSource the <tt>DataSource</tt> which is creating the new
     * instance so that it becomes one of its <tt>streams</tt>
     * @param formatControl the <tt>FormatControl</tt> which is to abstract the
     * <tt>Format</tt>-related information of the new instance
     */
    DirectShowStream(DataSource dataSource, FormatControl formatControl)
    {
        super(dataSource, formatControl);
    }

    /**
     * Connects this <tt>SourceStream</tt> to the DirectShow video capture
     * device identified by {@link #device}.
     *
     * @throws IOException if anything goes wrong while this
     * <tt>SourceStream</tt> connects to the DirectShow video capture device
     * identified by <tt>device</tt>
     */
    private void connect()
        throws IOException
    {
        if (device == null)
            throw new IOException("device == null");
        else
            device.setDelegate(delegate);
    }

    /**
     * Disconnects this <tt>SourceStream</tt> from the DirectShow video capture
     * device it has previously connected to during the execution of
     * {@link #connect()}.
     *
     * @throws IOException if anything goes wrong while this
     * <tt>SourceStream</tt> disconnects from the DirectShow video capture
     * device it has previously connected to during the execution of
     * <tt>connect()</tt>
     */
    private void disconnect()
        throws IOException
    {
        try
        {
            stop();
        }
        finally
        {
            if (device != null)
                device.setDelegate(null);
        }
    }

    /**
     * Gets the <tt>Format</tt> of this <tt>PushBufferStream</tt> as directly
     * known by it.
     *
     * @return the <tt>Format</tt> of this <tt>PushBufferStream</tt> as directly
     * known by it or <tt>null</tt> if this <tt>PushBufferStream</tt> does not
     * directly know its <tt>Format</tt> and it relies on the
     * <tt>PushBufferDataSource</tt> which created it to report its
     * <tt>Format</tt>
     */
    @Override
    protected Format doGetFormat()
    {
        return (format == null) ? super.doGetFormat() : format;
    }

    /**
     * {@inheritDoc}
     *
     * Overrides the super implementation to enable setting the <tt>Format</tt>
     * of this <tt>DirectShowStream</tt> after the <tt>DataSource</tt> which
     * provides it has been connected.
     */
    @Override
    protected Format doSetFormat(Format format)
    {
        if (isSupportedFormat(format))
        {
            if (device == null)
                return format;
            else
            {
                try
                {
                    setDeviceFormat(format);
                }
                catch (IOException ioe)
                {
                    logger.error(
                            "Failed to set format on DirectShowStream: "
                                + format,
                            ioe);
                    /*
                     * Ignore the exception because the method is to report
                     * failures by returning null (which will be achieved
                     * outside the catch block).
                     */
                }
                return format.matches(this.format) ? format : null;
            }
        }
        else
            return super.doSetFormat(format);
    }

    /**
     * Reads media data from this <tt>PushBufferStream</tt> into a specific
     * <tt>Buffer</tt> without blocking.
     *
     * @param buffer the <tt>Buffer</tt> in which media data is to be read from
     * this <tt>PushBufferStream</tt>
     */
    @Override
    public void read(Buffer buffer)
    {
        synchronized (dataSyncRoot)
        {
            if(data == null)
            {
                buffer.setLength(0);
                return;
            }

            Format bufferFormat = buffer.getFormat();

            if (bufferFormat == null)
            {
                bufferFormat = getFormat();
                if (bufferFormat != null)
                {
                    buffer.setFormat(bufferFormat);
                }
            }
            if (bufferFormat instanceof AVFrameFormat)
            {
                if (AVFrameWrapper.read(buffer, bufferFormat, data) < 0) {
                    data.free();
                }
                Log.logReceived(this);
                /*
                 * XXX For the sake of safety, make sure that this instance does
                 * not reference the data instance as soon as it is set on the
                 * AVFrame.
                 */
                data = null;
            }
            else
            {
                Object o = buffer.getData();
                byte[] bytes;
                int length = data.getLength();
                Log.logReceivedBytes(this, length);

                if(o instanceof byte[])
                {
                    bytes = (byte[]) o;
                    if(bytes.length < length)
                        bytes = null;
                }
                else
                    bytes = null;
                if(bytes == null)
                {
                    bytes = new byte[length];
                    buffer.setData(bytes);
                }

                /*
                 * TODO Copy the media from the native memory into the Java
                 * heap.
                 */
                data.free();
                data = null;

                buffer.setLength(length);
                buffer.setOffset(0);
            }

            buffer.setFlags(Buffer.FLAG_LIVE_DATA | Buffer.FLAG_SYSTEM_TIME);
            buffer.setTimeStamp(dataTimeStamp);

            dataSyncRoot.notifyAll();
        }
    }

    /**
     * Calls {@link BufferTransferHandler#transferData(PushBufferStream)} from
     * inside {@link #transferDataThread} so that the call is not made in
     * DirectShow and we can drop late frames in the meantime.
     */
    private void runInTransferDataThread()
    {
        logger.entry();
        boolean transferData = false;
        FrameRateControl frameRateControl
            = (FrameRateControl)
                dataSource.getControl(FrameRateControl.class.getName());
        long transferDataTimeStamp = -1;

        while (Thread.currentThread().equals(transferDataThread))
        {
            if (transferData)
            {
                BufferTransferHandler transferHandler = this.transferHandler;

                if (transferHandler != null)
                {
                    /*
                     * Respect the frame rate specified through the
                     * FrameRateControl of the associated DataSource.
                     */
                    if (frameRateControl != null)
                    {
                        float frameRate;
                        long newTransferDataTimeStamp
                            = System.currentTimeMillis();

                        if ((transferDataTimeStamp != -1)
                                && ((frameRate
                                            = frameRateControl.getFrameRate())
                                        > 0))
                        {
                            long videoMinFrameDuration
                                = (long) (1000 / frameRate);

                            if (videoMinFrameDuration > 0)
                            {
                                long t
                                    = newTransferDataTimeStamp
                                        - transferDataTimeStamp;

                                if ((t > 0) && (t < videoMinFrameDuration))
                                {
                                    boolean interrupted = false;

                                    try
                                    {
                                        Thread.sleep(
                                                videoMinFrameDuration - t);
                                    }
                                    catch (InterruptedException ie)
                                    {
                                        interrupted = true;
                                    }
                                    if (interrupted)
                                        Thread.currentThread().interrupt();
                                    continue;
                                }
                            }
                        }

                        transferDataTimeStamp = newTransferDataTimeStamp;
                    }

                    transferHandler.transferData(this);
                }

                synchronized (dataSyncRoot)
                {
                    if (data != null)
                        data.free();
                    data = nextData;
                    dataTimeStamp = nextDataTimeStamp;
                    nextData = null;
                }
            }

            synchronized (dataSyncRoot)
            {
                if (data == null)
                {
                    data = nextData;
                    dataTimeStamp = nextDataTimeStamp;
                    nextData = null;
                }
                if (data == null)
                {
                    boolean interrupted = false;

                    try
                    {
                        dataSyncRoot.wait(3000);
                        if (data == null && !transferData && deviceRunning)
                        {
                            // If we still have no data then we're probably
                            // never going to get any. Most likely reason is
                            // that the camera is in use elsewhere
                            logger.error("Failed to get data stream from " +
                                                    getClass().getSimpleName());

                            ResourceManagementService res = LibJitsi.
                                                getResourceManagementService();

                            String deviceName =
                                    (device != null) ? device.getName(): "";

                            String error =
                                    res.getI18NString(deviceReadErrorProp,
                                      new String[]{deviceName});

                            String errorTitle = res.getI18NString(errorTitleProp);

                            JOptionPane msgFrame = new JOptionPane(error,
                                                    JOptionPane.ERROR_MESSAGE);

                            JDialog msgDialog = msgFrame.createDialog(
                                                                    errorTitle);
                            msgDialog.setAlwaysOnTop(true);
                            msgDialog.setModal(true);
                            msgDialog.setDefaultCloseOperation(
                                                      JDialog.DISPOSE_ON_CLOSE);
                            msgDialog.setIconImage(new BufferedImage(
                                        1, 1, BufferedImage.TYPE_INT_ARGB_PRE));
                            msgDialog.setVisible(true);

                            try
                            {
                                stop();
                            }
                            catch (IOException ex)
                            {
                                logger.error("Failed to stop " +
                                               getClass().getSimpleName(), ex);
                            }
                        }
                    }
                    catch (InterruptedException iex)
                    {
                        interrupted = true;
                    }
                    if(interrupted)
                        Thread.currentThread().interrupt();

                    transferData = (data != null);
                }
                else
                    transferData = true;
            }
        }
        logger.exit();
    }

    /**
     * Process received frames from DirectShow capture device
     *
     * @param source pointer to the native <tt>DSCaptureDevice</tt> which is the
     * source of the notification
     * @param ptr native pointer to data
     * @param length length of data
     */
    private void SampleCB(long source, long ptr, int length)
    {
        Log.logReceivedBytes(this.delegate, length);

        synchronized (dataSyncRoot)
        {
            if (data != null)
            {
                if (nextData != null)
                {
                    nextData.free();
                    nextData = null;
                }
                nextData = byteBufferPool.getBuffer(length);
                if(nextData != null)
                {
                    nextData.setLength(
                            DSCaptureDevice.samplecopy(
                                    source,
                                    ptr, nextData.getPtr().address(), length));
                    nextDataTimeStamp = System.nanoTime();
                }

                return;
            }

            if (data != null)
            {
                data.free();
                data = null;
            }

            data = byteBufferPool.getBuffer(length);

            if (data != null)
            {
                int copiedLength =
                    DSCaptureDevice.samplecopy(
                        source,
                        ptr, data.getPtr().address(), length);
                if (copiedLength == 0)
                {
                    data.free();
                    data = null;
                }
                else
                {
                    data.setLength(copiedLength);
                    dataTimeStamp = System.nanoTime();
                }
            }

            if (nextData != null)
            {
                nextData.free();
                nextData = null;
            }

            dataSyncRoot.notifyAll();
        }
    }

    /**
     * Sets the <tt>DSCaptureDevice</tt> of this instance which identifies the
     * DirectShow video capture device this <tt>SourceStream</tt> is to capture
     * data from.
     *
     * @param device a <tt>DSCaptureDevice</tt> which identifies the DirectShow
     * video capture device this <tt>SourceStream</tt> is to capture data from
     * @throws IOException if anything goes wrong while setting the specified
     * <tt>device</tt> on this instance
     */
    void setDevice(DSCaptureDevice device)
        throws IOException
    {
        Log.dumpStack("setDevice.  Changed? " + ((device == this.device) ?
                               ("No (" + this.device + ")") :
                               ("Yes (" + this.device + " to " + device +")")));
        if (this.device != device)
        {
            if (this.device != null)
                disconnect();

            this.device = device;

            if (this.device != null)
                connect();
        }
    }

    /**
     * Sets a specific <tt>Format</tt> on the <tt>DSCaptureDevice</tt> of this
     * instance.
     *
     * @param format the <tt>Format</tt> to set on the <tt>DSCaptureDevice</tt>
     * of this instance
     * @throws IOException if setting the specified <tt>format</tt> on the
     * <tt>DSCaptureDevice</tt> of this instance fails
     */
    private void setDeviceFormat(Format format)
        throws IOException
    {
        logger.entry();
        if (format == null)
            throw new IOException("format == null");
        else if (format instanceof AVFrameFormat)
        {
            AVFrameFormat avFrameFormat = (AVFrameFormat) format;
            Dimension size = avFrameFormat.getSize();

            if (size == null)
                throw new IOException("format.size == null");
            else
            {
                int hresult
                    = device.setFormat(
                            new DSFormat(
                                    size.width, size.height,
                                    avFrameFormat.getDeviceSystemPixFmt()));

                switch (hresult)
                {
                case DSCaptureDevice.S_FALSE:
                case DSCaptureDevice.S_OK:
                    this.format = format;
                    logger.debug("Set format on DirectShowStream: " + format);
                    break;
                default:
                    throwNewHResultException(hresult);
                }
            }
        }
        else
            throw new IOException("!(format instanceof AVFrameFormat)");
        logger.exit();
    }

    /**
     * Starts the transfer of media data from this <tt>PushBufferStream</tt>.
     *
     * @throws IOException if anything goes wrong while starting the transfer of
     * media data from this <tt>PushBufferStream</tt>
     */
    @Override
    public void start()
        throws IOException
    {
        logger.entry();
        Log.logMediaStackObjectStarted(this);
        super.start();

        try
        {
            setDeviceFormat(getFormat());

            if (transferDataThread == null)
            {
                transferDataThread
                    = new Thread(getClass().getSimpleName())
                    {
                        @Override
                        public void run()
                        {
                            runInTransferDataThread();
                        }
                    };

                Log.dumpStack("Start DirectShowStream transferDataThread");
                transferDataThread.start();
            }

            device.start();

            deviceRunning = true;
        }
        finally
        {
            if (!deviceRunning)
                stop();
        }
        logger.exit();
    }

    /**
     * Stops the transfer of media data from this <tt>PushBufferStream</tt>.
     *
     * @throws IOException if anything goes wrong while stopping the transfer of
     * media data from this <tt>PushBufferStream</tt>
     */
    @Override
    public void stop()
        throws IOException
    {
        logger.entry();
        Log.logMediaStackObjectStopped(this);
        try
        {
            device.stop();

            deviceRunning = false;

            transferDataThread = null;

            synchronized (dataSyncRoot)
            {
                if (data != null)
                {
                    data.free();
                    data = null;
                }
                if (nextData != null)
                {
                    nextData.free();
                    nextData = null;
                }

                dataSyncRoot.notifyAll();
            }
        }
        finally
        {
            super.stop();

            byteBufferPool.drain();
        }
        logger.exit();
    }

    /**
     * Throws a new <tt>IOException</tt> the detail message of which describes
     * a specific <tt>HRESULT</tt> value indicating a failure.
     *
     * @param hresult the <tt>HRESUlT</tt> to be described by the detail message
     * of the new <tt>IOException</tt> to be thrown
     * @throws IOException
     */
    private void throwNewHResultException(int hresult)
        throws IOException
    {
        throw new IOException(
                "HRESULT 0x" + Long.toHexString(hresult & 0xffffffffL));
    }
}
