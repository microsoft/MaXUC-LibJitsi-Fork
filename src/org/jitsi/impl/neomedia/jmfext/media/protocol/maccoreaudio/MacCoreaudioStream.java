/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
// Portions (c) Microsoft Corporation. All rights reserved.
package org.jitsi.impl.neomedia.jmfext.media.protocol.maccoreaudio;

import java.util.*;
import java.util.concurrent.locks.*;

import javax.media.*;
import javax.media.control.*;
import javax.media.format.*;

import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.codec.*;
import org.jitsi.impl.neomedia.device.*;
import org.jitsi.impl.neomedia.jmfext.media.protocol.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.util.*;

import net.sf.fmj.media.*;

/**
 * Implements <tt>PullBufferStream</tt> for MacCoreaudio.
 *
 * @author Vincent Lucas
 */
public class MacCoreaudioStream
    extends AbstractPullBufferStream<DataSource>
{
    /**
     * The <tt>Logger</tt> used by the <tt>MacCoreaudioStream</tt> class and its
     * instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(MacCoreaudioStream.class);

    /**
     * The number of bytes to read from a native MacCoreaudio stream in a single
     * invocation. Based on framesPerBuffer.
     */
    private int bytesPerBuffer;

    /**
     * The device identifier (the device UID, or if not available, the device
     * name) of the MacCoreaudio device read through this
     * <tt>PullBufferStream</tt>.
     */
    private String deviceUID;

    /**
     * A lock used to avoid conflict when starting / stopping the
     * stream for this stream;
     */
    private Lock startStopLock = new ReentrantLock();
    private Condition startStopCondition = startStopLock.newCondition();

    /**
     * The buffer which stores the outgoing data before sending them to
     * the RTP stack.
     */
    private byte[] buffer = null;

    /**
     * A list of already allocated buffers, ready to accept new captured data.
     */
    private Vector<byte[]> freeBufferList = new Vector<>();

    /**
     * A list of already allocated and filled buffers, ready to be send throw
     * the network.
     */
    private Vector<byte[]> fullBufferList = new Vector<>();

    /**
     * The number of data available to feed the RTP stack.
     */
    private int nbBufferData = 0;

    /**
     * The last-known <tt>Format</tt> of the media data made available by this
     * <tt>PullBufferStream</tt>.
     */
    private AudioFormat format = null;

    /**
     * The <tt>GainControl</tt> through which the volume/gain of captured media
     * is controlled.
     */
    private final GainControl gainControl;

    /**
     * Locked when currently stopping the stream. Prevents deadlock between the
     * CoreAudio callback and the AudioDeviceStop function.
     * startStopLock must always be held before attempting to get stopLock.
     */
    private final Object stopLock = new Object();

    private final MacCoreaudioSystem.UpdateAvailableDeviceListListener
        updateAvailableDeviceListListener
            = new MacCoreaudioSystem.UpdateAvailableDeviceListListener()
            {
                /**
                 * The device ID (could be deviceUID or name but that is not
                 * really of concern to MacCoreaudioStream) used before and
                 * after (if still available) the update.
                 */
                private String deviceUID = null;

                private boolean start = false;

                @Override
                public void didUpdateAvailableDeviceList()
                    throws Exception
                {
                    startStopLock.lock();
                    try
                    {
                        if(stream == 0 && start)
                        {
                            setDeviceUID(deviceUID);
                            start();
                        }
                        deviceUID = null;
                        start = false;
                    }
                    finally
                    {
                        startStopLock.unlock();
                    }
                }

                @Override
                public void willUpdateAvailableDeviceList()
                    throws Exception
                {
                    startStopLock.lock();
                    try
                    {
                        if(stream == 0)
                        {
                            deviceUID = null;
                            start = false;
                        }
                        else
                        {
                            deviceUID = MacCoreaudioStream.this.deviceUID;
                            start = true;
                            stop();
                            setDeviceUID(null);
                        }
                    }
                    finally
                    {
                        startStopLock.unlock();
                    }
                }
            };

    /**
     * Current sequence number.
     */
    private int sequenceNumber = 0;

    /**
     * The stream structure used by the native maccoreaudio library.
     */
    private long stream = 0;

    /**
     * Initializes a new <tt>MacCoreaudioStream</tt> instance which is to have
     * its <tt>Format</tt>-related information abstracted by a specific
     * <tt>FormatControl</tt>.
     *
     * @param dataSource the <tt>DataSource</tt> which is creating the new
     * instance so that it becomes one of its <tt>streams</tt>
     * @param formatControl the <tt>FormatControl</tt> which is to abstract the
     * <tt>Format</tt>-related information of the new instance
     */
    public MacCoreaudioStream(
            DataSource dataSource,
            FormatControl formatControl)
    {
        super(dataSource, formatControl);

        MediaServiceImpl mediaServiceImpl
            = NeomediaServiceUtils.getMediaServiceImpl();

        gainControl = (mediaServiceImpl == null)
            ? null
            : (GainControl) mediaServiceImpl.getCaptureVolumeControl();

        // XXX We will add a UpdateAvailableDeviceListListener and will not
        // remove it because we will rely on MacCoreaudioSystem's use of
        // WeakReference.
        MacCoreaudioSystem.addUpdateAvailableDeviceListListener(
                updateAvailableDeviceListListener);
    }

    private void connect()
    {
        AudioFormat format = (AudioFormat) getFormat();
        int channels = format.getChannels();
        if (channels == Format.NOT_SPECIFIED)
            channels = 1;
        int sampleSizeInBits = format.getSampleSizeInBits();
        double sampleRate = format.getSampleRate();
        int framesPerBuffer
            = (int) ((sampleRate * MacCoreAudioDevice.DEFAULT_MILLIS_PER_BUFFER)
                    / (channels * 1000));
        bytesPerBuffer = (sampleSizeInBits / 8) * channels * framesPerBuffer;

        // Know the Format in which this MacCoreaudioStream will output audio
        // data so that it can report it without going through its DataSource.
        this.format = new AudioFormat(
                AudioFormat.LINEAR,
                sampleRate,
                sampleSizeInBits,
                channels,
                AudioFormat.LITTLE_ENDIAN,
                AudioFormat.SIGNED,
                Format.NOT_SPECIFIED /* frameSizeInBits */,
                Format.NOT_SPECIFIED /* frameRate */,
                Format.byteArray);
    }

    /**
     * Gets the <tt>Format</tt> of this <tt>PullBufferStream</tt> as directly
     * known by it.
     *
     * @return the <tt>Format</tt> of this <tt>PullBufferStream</tt> as directly
     * known by it or <tt>null</tt> if this <tt>PullBufferStream</tt> does not
     * directly know its <tt>Format</tt> and it relies on the
     * <tt>PullBufferDataSource</tt> which created it to report its
     * <tt>Format</tt>
     * @see AbstractPullBufferStream#doGetFormat()
     */
    @Override
    protected Format doGetFormat()
    {
        return (format == null) ? super.doGetFormat() : format;
    }

    /**
     * Reads media data from this <tt>PullBufferStream</tt> into a specific
     * <tt>Buffer</tt> with blocking.
     *
     * @param buffer the <tt>Buffer</tt> in which media data is to be read from
     * this <tt>PullBufferStream</tt>
     */
    @Override
    public void read(Buffer buffer)
    {
        int length = 0;
        byte[] data = AbstractCodec2.validateByteArraySize(
                        buffer,
                        bytesPerBuffer,
                        false);

        startStopLock.lock();
        try
        {
            // Waits for the next buffer.
            while(this.fullBufferList.size() == 0 && stream != 0)
            {
                try
                {
                    startStopCondition.await();
                }
                catch(InterruptedException ex)
                {}
            }

            // If the stream is running.
            if(stream != 0)
            {
                this.freeBufferList.add(data);
                data = this.fullBufferList.remove(0);
                length = data.length;
            }
        }
        finally
        {
            startStopLock.unlock();
        }

        // Take into account the user's preferences with respect to the
        // input volume.
        if(length != 0 && gainControl != null)
        {
            BasicVolumeControl.applyGain(
                    gainControl,
                    data,
                    0,
                    length);
        }

        long bufferTimeStamp = System.nanoTime();

        buffer.setData(data);
        buffer.setFlags(Buffer.FLAG_SYSTEM_TIME);
        if (format != null)
            buffer.setFormat(format);
        buffer.setHeader(null);
        buffer.setLength(length);
        buffer.setOffset(0);
        buffer.setSequenceNumber(sequenceNumber++);
        buffer.setTimeStamp(bufferTimeStamp);
        Log.logReceivedBytes(this, length);
    }

    /**
     * Sets the device index of the MacCoreaudio device to be read through this
     * <tt>PullBufferStream</tt>.
     *
     * @param deviceUID The ID of the device used to be read trough this
     * MacCoreaudioStream.  This String contains the deviceUID, or if not
     * available, the device name.  If set to null, then there was no device
     * used before the update.
     */
    void setDeviceUID(String deviceUID)
    {
        startStopLock.lock();
        try
        {
            if (this.deviceUID != null)
            {
                // If there is a running stream, then close it.
                stop();

                // Make sure this AbstractPullBufferStream asks its DataSource
                // for the Format in which it is supposed to output audio data
                // the next time it is opened instead of using its Format from a
                // previous open.
                this.format = null;
            }
            this.deviceUID = deviceUID;

            if (this.deviceUID != null)
            {
                connect();
            }
        }
        finally
        {
            startStopLock.unlock();
        }
    }

    /**
     * Starts the transfer of media data from this <tt>PullBufferStream</tt>.
     */
    @Override
    public void start()
    {
        startStopLock.lock();
        try
        {
            if(stream == 0 && deviceUID != null)
            {
                Log.logMediaStackObjectStarted(this);
                buffer = new byte[bytesPerBuffer];
                nbBufferData = 0;
                this.fullBufferList.clear();
                this.freeBufferList.clear();

                MacCoreaudioSystem.willOpenStream();
                boolean isEchoCancelActivated = MacCoreaudioSystem.isEchoCancelActivated();
                logger.debug("Call on MacCoreAudioDevice: startStream(" +
                        "..., isEchoCancelActivated=" + isEchoCancelActivated +")");
                stream = MacCoreAudioDevice.startStream(
                        deviceUID,
                        this,
                        (float) format.getSampleRate(),
                        format.getChannels(),
                        format.getSampleSizeInBits(),
                        false,
                        format.getEndian() == AudioFormat.BIG_ENDIAN,
                        false,
                        true,
                        isEchoCancelActivated);
                MacCoreaudioSystem.didOpenStream();
            }
        }
        finally
        {
            startStopLock.unlock();
        }
    }

    /**
     * Stops the transfer of media data from this <tt>PullBufferStream</tt>.
     */
    @Override
    public void stop()
    {
        startStopLock.lock();
        try
        {
            synchronized (stopLock)
            {
                if(stream != 0 && deviceUID != null)
                {
                    Log.logMediaStackObjectStopped(this);
                    MacCoreAudioDevice.stopStream(deviceUID, stream);

                    stream = 0;
                    this.fullBufferList.clear();
                    this.freeBufferList.clear();
                    startStopCondition.signal();
                }
            }
        }
        finally
        {
            startStopLock.unlock();
        }
    }

    /**
     * Callback which receives the data from the coreaudio library.
     *
     * @param buffer The data captured from the input.
     * @param bufferLength The length of the data captured.
     */
    public void readInput(byte[] buffer, int bufferLength)
    {
        int nbCopied = 0;
        while(bufferLength > 0)
        {
            int length = this.buffer.length - nbBufferData;
            if(bufferLength < length)
            {
                length = bufferLength;
            }

            System.arraycopy(
                    buffer,
                    nbCopied,
                    this.buffer,
                    nbBufferData,
                    length);

            nbBufferData += length;
            nbCopied += length;
            bufferLength -= length;

            if(nbBufferData == this.buffer.length)
            {
                this.fullBufferList.add(this.buffer);
                this.buffer = null;
                nbBufferData = 0;

                if (startStopLock.tryLock())
                {
                    try
                    {
                        synchronized (stopLock)
                        {
                            startStopCondition.signal();
                            if(this.freeBufferList.size() > 0)
                            {
                                this.buffer = this.freeBufferList.remove(0);
                            }
                        }
                    }
                    finally
                    {
                        startStopLock.unlock();
                    }
                }

                if(this.buffer == null)
                {
                    this.buffer = new byte[bytesPerBuffer];
                }
            }
        }
    }
}
