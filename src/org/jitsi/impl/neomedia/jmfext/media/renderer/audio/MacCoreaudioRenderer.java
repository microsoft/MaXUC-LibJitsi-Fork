/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
// Portions (c) Microsoft Corporation. All rights reserved.
package org.jitsi.impl.neomedia.jmfext.media.renderer.audio;

import java.beans.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

import javax.media.*;
import javax.media.format.*;

import org.jitsi.impl.neomedia.device.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.util.*;

import com.google.common.annotations.VisibleForTesting;

import net.sf.fmj.media.*;

/**
 * Implements an audio <tt>Renderer</tt> which uses MacOSX Coreaudio.
 *
 * @author Vincent Lucas
 */
public class MacCoreaudioRenderer
    extends AbstractAudioRenderer<MacCoreaudioSystem>
{
    /**
     * The <tt>Logger</tt> used by the <tt>MacCoreaudioRenderer</tt> class and
     * its instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(MacCoreaudioRenderer.class);

    /**
     * The device used for this renderer.
     */
    private String deviceUID = null;

    /**
     * The stream structure used by the native maccoreaudio library.
     */
    private long stream = 0;

    /**
     * A lock used to avoid conflict when starting / stopping the
     * stream for this renderer;
     */
    @VisibleForTesting
    protected Lock startStopLock = new ReentrantLock();
    private Condition startStopCondition = startStopLock.newCondition();

    /**
     * The buffer which stores the incoming data before sending them to
     * CoreAudio.
     */
    @VisibleForTesting
    protected byte[] buffer = null;

    /**
     * The number of data available to feed CoreAudio output.
     */
    @VisibleForTesting
    protected int nbBufferData = 0;

    /**
     * Indicates when we start to close the stream.
     */
    private boolean isStopping = false;

    /**
     * Locked when currently stopping the stream. Prevents deadlock between the
     * CoreAudio callback and the AudioDeviceStop function.
     * startStopLock must always be locked before attempting to get stopLock.
     */
    private final Object stopLock = new Object();

    /**
     * The constant which represents an empty array with
     * <tt>Format</tt> element type. Explicitly defined in order to
     * reduce unnecessary allocations.
     */
    private static final Format[] EMPTY_SUPPORTED_INPUT_FORMATS
        = new Format[0];

    /**
     * The human-readable name of the <tt>MacCoreaudioRenderer</tt> JMF plug-in.
     */
    private static final String PLUGIN_NAME = "MacCoreaudio Renderer";

    /**
     * The list of JMF <tt>Format</tt>s of audio data which
     * <tt>MacCoreaudioRenderer</tt> instances are capable of rendering.
     */
    private static final Format[] SUPPORTED_INPUT_FORMATS;

    /**
     * The list of the sample rates supported by <tt>MacCoreaudioRenderer</tt> as input.
     */
    private static final double[] SUPPORTED_INPUT_SAMPLE_RATES
            = new double[]{8000, 11025, 16000, 22050, 24000, 32000, 44100, 48000};

    static
    {
        int count = SUPPORTED_INPUT_SAMPLE_RATES.length;

        SUPPORTED_INPUT_FORMATS = new Format[count];
        for (int i = 0; i < count; i++)
        {
            SUPPORTED_INPUT_FORMATS[i]
                = new AudioFormat(
                        AudioFormat.LINEAR,
                        SUPPORTED_INPUT_SAMPLE_RATES[i],
                        16 /* sampleSizeInBits */,
                        Format.NOT_SPECIFIED /* channels */,
                        AudioFormat.LITTLE_ENDIAN,
                        AudioFormat.SIGNED,
                        Format.NOT_SPECIFIED /* frameSizeInBits */,
                        Format.NOT_SPECIFIED /* frameRate */,
                        Format.byteArray);
        }
    }

    /**
     * The <tt>UpdateAvailableDeviceListListener</tt> which is to be notified
     * before and after MacCoreaudio's native function
     * <tt>UpdateAvailableDeviceList()</tt> is invoked. It will close
     * {@link #stream} before the invocation in order to mitigate memory
     * corruption afterwards and it will attempt to restore the state of this
     * <tt>Renderer</tt> after the invocation.
     */
    private final MacCoreaudioSystem.UpdateAvailableDeviceListListener
        updateAvailableDeviceListListener
            = new MacCoreaudioSystem.UpdateAvailableDeviceListListener()
    {
        private boolean start = false;

        @Override
        public void didUpdateAvailableDeviceList()
            throws Exception
        {
            startStopLock.lock();
            try
            {
                updateDeviceUID();
                if(start)
                {
                    open();
                    start();
                }
            }
            finally
            {
                startStopLock.unlock();
            }
        }

        @Override
        public void willUpdateAvailableDeviceList()
        {
            startStopLock.lock();
            try
            {
                start = false;
                if(stream != 0)
                {
                    start = true;
                    stop();
                }
            }
            finally
            {
                startStopLock.unlock();
            }
        }
    };

    /**
     * Array of supported input formats.
     */
    private Format[] supportedInputFormats;

    /**
     * Should we block new data from coming in whilst our backlog is full?  Otherwise we drop new
     * data given to us that we don't have room for.  The former is used for playing audio clips
     * etc. where it's important to play back all the audio data without dropping any.  The latter
     * is used for calls, where it's more important to keep our rendering of the audio in time with
     * the audio we receive (to prevent the user experience a delay in the call), even if this is
     * at the expense of dropping audio data.
     */
    private boolean blocking = false;

    /**
     * Maximum size of the process buffer - enough to give us 500ms of data.
     * Calculated as:
     *    sample rate in Hz -> samples per second
     *    * bits per sample -> bits per second
     *    / 8               -> bytes per second
     *    / 1000            -> bytes per millisecond
     *    * 500             -> bytes for 500ms
     */
    private static final int DEFAULT_MAXIMUM_PROCESS_BUFFER_SIZE = (int)
          Math.round(500 * (MacCoreAudioDevice.DEFAULT_SAMPLE_RATE * MacCoreAudioDevice.DEFAULT_BITS_PER_SAMPLE / 8) / 1000);

    private int maximumProcessBufferSize = DEFAULT_MAXIMUM_PROCESS_BUFFER_SIZE;

    /**
     * Initializes a new <tt>MacCoreaudioRenderer</tt> instance.
     */
    public MacCoreaudioRenderer()
    {
        this(true);
    }

    /**
     * Default blocking to false in {@link #MacCoreaudioRenderer(boolean, boolean)}
     * @param enableVolumeControl
     */
    public MacCoreaudioRenderer(boolean enableVolumeControl)
    {
        this(true, false);
    }

    /**
     * Initializes a new <tt>MacCoreaudioRenderer</tt> instance which is to
     * either perform playback or sound a notification.
     *
     * @param enableVolumeControl <tt>true</tt> if the new instance is to perform playback
     * or <tt>false</tt> if the new instance is to sound a notification
     * @param blocking <tt>true</tt> if we should only accept new data when we have room for it.
     * Used for playing audio clips etc.
     * <tt>false</tt> if we accept all data given to us and discard anything more than we can store.
     * Used for playing audio in calls.
     */
    public MacCoreaudioRenderer(boolean enableVolumeControl, boolean blocking)
    {
        super(AudioSystem.LOCATOR_PROTOCOL_MACCOREAUDIO,
              enableVolumeControl
                  ? DataFlow.PLAYBACK
                  : DataFlow.NOTIFY);

        this.blocking = blocking;

        // XXX We will add a PaUpdateAvailableDeviceListListener and will not
        // remove it because we will rely on MacCoreaudioSystem's use of
        // WeakReference.
        MacCoreaudioSystem.addUpdateAvailableDeviceListListener(
                updateAvailableDeviceListListener);
    }

    /**
     * Closes this <tt>PlugIn</tt>.
     */
    @Override
    public void close()
    {
        stop();
        super.close();
    }

    /**
     * Gets the descriptive/human-readable name of this JMF plug-in.
     *
     * @return the descriptive/human-readable name of this JMF plug-in
     */
    @Override
    public String getName()
    {
        return PLUGIN_NAME;
    }

    /**
     * Gets the list of JMF <tt>Format</tt>s of audio data which this
     * <tt>Renderer</tt> is capable of rendering.
     *
     * @return an array of JMF <tt>Format</tt>s of audio data which this
     * <tt>Renderer</tt> is capable of rendering
     */
    @Override
    public Format[] getSupportedInputFormats()
    {
        if (supportedInputFormats == null)
        {
            this.updateDeviceUID();

            if (deviceUID == null)
            {
                supportedInputFormats = SUPPORTED_INPUT_FORMATS;
            }
            else
            {
                int minOutputChannels = 1;
                // The maximum output channels may be a lot and checking all of
                // them will take a lot of time. Besides, we currently support
                // at most 2.
                int maxOutputChannels = Math.min(MacCoreAudioDevice.countOutputChannels(deviceUID), 2);
                List<Format> supportedInputFormats = new ArrayList<>(SUPPORTED_INPUT_FORMATS.length);

                for (Format supportedInputFormat : SUPPORTED_INPUT_FORMATS)
                {
                    getSupportedInputFormats(
                            supportedInputFormat,
                            minOutputChannels,
                            maxOutputChannels,
                            supportedInputFormats);
                }

                if (supportedInputFormats.isEmpty())
                {
                    float minRate = getDeviceMinimalNominalSampleRate(deviceUID);
                    float maxRate = getDeviceMaximalNominalSampleRate(deviceUID);
                    logger.warn("No supported input formats for device: " + deviceUID
                                + ". Using minRate: " + minRate
                                + " and maxRate: " + maxRate);
                    this.supportedInputFormats = EMPTY_SUPPORTED_INPUT_FORMATS;
                }
                else
                {
                    this.supportedInputFormats = supportedInputFormats.toArray(EMPTY_SUPPORTED_INPUT_FORMATS);
                }
            }
        }
        return
            (supportedInputFormats.length == 0)
                ? EMPTY_SUPPORTED_INPUT_FORMATS
                : supportedInputFormats.clone();
    }

    private void getSupportedInputFormats(
            Format format,
            int minOutputChannels,
            int maxOutputChannels,
            List<Format> supportedInputFormats)
    {
        AudioFormat audioFormat = (AudioFormat) format;
        int sampleSizeInBits = audioFormat.getSampleSizeInBits();
        double sampleRate = audioFormat.getSampleRate();
        float minRate = getDeviceMinimalNominalSampleRate(deviceUID);
        float maxRate = getDeviceMaximalNominalSampleRate(deviceUID);

        for (int channels = minOutputChannels;
             channels <= maxOutputChannels;
             channels++)
        {
            if (sampleRate >= minRate && sampleRate <= maxRate)
            {
                supportedInputFormats.add(
                        new AudioFormat(
                                audioFormat.getEncoding(),
                                sampleRate,
                                sampleSizeInBits,
                                channels,
                                audioFormat.getEndian(),
                                audioFormat.getSigned(),
                                Format.NOT_SPECIFIED, // frameSizeInBits
                                Format.NOT_SPECIFIED, // frameRate
                                audioFormat.getDataType()));
            }
        }
    }

    private float getDeviceMinimalNominalSampleRate(String deviceUID)
    {
        return MacCoreAudioDevice.getMinimalNominalSampleRate(deviceUID,
                                                              true,
                                                              MacCoreaudioSystem.isEchoCancelActivated());
    }

    private float getDeviceMaximalNominalSampleRate(String deviceUID)
    {
        return MacCoreAudioDevice.getMaximalNominalSampleRate(deviceUID,
                                                              true,
                                                              MacCoreaudioSystem.isEchoCancelActivated());
    }

    /**
     * Opens the MacCoreaudio device and output stream represented by this instance which are to be used to render
     * audio.
     *
     * @throws ResourceUnavailableException if the MacCoreaudio device or output stream cannot be created or opened
     */
    @Override
    public void open()
        throws ResourceUnavailableException
    {
        startStopLock.lock();
        try
        {
            if(stream == 0)
            {
                MacCoreaudioSystem.willOpenStream();
                try
                {
                    if(!this.updateDeviceUID())
                    {
                        throw new ResourceUnavailableException(
                                "No locator/MediaLocator is set.");
                    }

                    if (inputFormat == null)
                    {
                        throw new ResourceUnavailableException(
                                "inputFormat not set");
                    }
                }
                finally
                {
                    MacCoreaudioSystem.didOpenStream();
                }
            }
            super.open();
        }
        finally
        {
            startStopLock.unlock();
        }
    }

    /**
     * Notifies this instance that the value of the
     * {@link AudioSystem#PROP_PLAYBACK_DEVICE} property of its associated
     * <tt>AudioSystem</tt> has changed.
     *
     * @param ev a <tt>PropertyChangeEvent</tt> which specifies details about
     * the change such as the name of the property and its old and new values
     */
    @Override
    protected synchronized void playbackDevicePropertyChange(
            PropertyChangeEvent ev)
    {
        startStopLock.lock();
        try
        {
            stop();
            updateDeviceUID();
            start();
        }
        finally
        {
            startStopLock.unlock();
        }
    }

    /**
     * Renders the audio data contained in a specific <tt>Buffer</tt> onto the
     * MacCoreaudio device represented by this <tt>Renderer</tt>.
     *
     * @param buffer the <tt>Buffer</tt> which contains the audio data to be
     * rendered
     * @return <tt>BUFFER_PROCESSED_OK</tt> if the specified <tt>buffer</tt> has
     * been successfully processed
     */
    @Override
    public int process(Buffer buffer)
    {
        int ret = BUFFER_PROCESSED_OK;

        startStopLock.lock();
        try
        {
            if (stream != 0 && !isStopping)
            {
                int length = buffer.getLength();

                /*
                 * Update the buffer size if too small.  However, don't allow
                 * the buffer to grow too large - instead we should block here
                 * until the data is consumed.
                 * Increase up to the smaller of the space required vs our
                 * maximum (which is enough for 500ms of data).  The only caveat
                 * is we must always allow at least enough room for the current
                 * data chunk, or else we'll never process it.  In fact allow
                 * room for 2 chunks - this prevents an audio glitch when the
                 * chunk is consumed (since otherwise we'd have to wait for the
                 * next chunk to be added before continuing).
                 */
                updateBufferLength(Math.max(
                                        2 * length,
                                        Math.min(nbBufferData + length,
                                                 maximumProcessBufferSize)));

                // Is current held data + new data we're being given > max data we can store?
                if (nbBufferData + length > this.buffer.length)
                {
                    // Not enough space in our buffer to fit what we're being given.
                    if (blocking)
                    {
                        // We're blocking - pause and then let the caller retry.
                        boolean interrupted = false;
                        try
                        {
                            // Pause to allow one spin of the audio mixer.
                            startStopCondition.await(10, TimeUnit.MILLISECONDS); // 10ms - default device period? TODO
                        }
                        catch (InterruptedException ie)
                        {
                            interrupted = true;
                        }
                        if (interrupted)
                        {
                            Thread.currentThread().interrupt();
                        }

                        ret |= INPUT_BUFFER_NOT_CONSUMED;
                    }
                    else
                    {
                        // We're not blocking, so just store what we can fit in our available space
                        // (which might be nothing).  The rest will just be lost.
                        length = this.buffer.length - nbBufferData;
                    }
                }

                // If there were no problems thus far, copy across the data into the buffer
                if (ret == BUFFER_PROCESSED_OK)
                {
                    // Take into account the user's preferences with respect to
                    // the output volume.  Only do this when we're about to
                    // consume the buffer.
                    GainControl gainControl = getGainControl();
                    if (gainControl != null)
                    {
                        BasicVolumeControl.applyGain(
                                gainControl,
                                (byte[]) buffer.getData(),
                                buffer.getOffset(),
                                buffer.getLength());
                    }

                    // Copy the received data.
                    System.arraycopy(
                            (byte[]) buffer.getData(),
                            buffer.getOffset(),
                            this.buffer,
                            nbBufferData,
                            length);
                    nbBufferData += length;
                    Log.logReceivedBytes(this, length);
                }
            }
        }
        finally
        {
            startStopLock.unlock();
        }

        return ret;
    }

    /**
     * Sets the <tt>MediaLocator</tt> which specifies the device index of the
     * MacCoreaudio device to be used by this instance for rendering.
     *
     * @param locator a <tt>MediaLocator</tt> which specifies the device index
     * of the MacCoreaudio device to be used by this instance for rendering
     */
    @Override
    public void setLocator(MediaLocator locator)
    {
        super.setLocator(locator);

        this.updateDeviceUID();

        supportedInputFormats = null;
    }

    /**
     * Intercepts the call to parent class method so as to update MAXIMUM_PROCESS_BUFFER_SIZE based on
     * this format's sample rate and sample size.  See parent class's JavaDoc for more info.
     */
    @Override
    public Format setInputFormat(Format format)
    {
        // Max buffer size should hold 500ms of data
        // 'sample rate' is 'samples in a second'
        // '* sample size in bits' gives 'bits per second'
        // '/ 8' gives 'bytes per second'
        // '/ 2' gives 'bytes in 500ms'
        AudioFormat af = (AudioFormat) format;
        maximumProcessBufferSize = (int) (af.getSampleRate() * af.getSampleSizeInBits() / 8) / 2;
        logger.info("set Input to " + af + ",  maximumProcessBufferSize now " + maximumProcessBufferSize);

        return super.setInputFormat(format);
    }

    /**
     * Starts the rendering process. Any audio data available in the internal
     * resources associated with this <tt>MacCoreaudioRenderer</tt> will begin
     * being rendered.
     */
    @Override
    public void start()
    {
        // Start the stream
        startStopLock.lock();
        try
        {
            if(stream == 0 && deviceUID != null)
            {
                Log.logMediaStackObjectStarted(this);
                int nbChannels = inputFormat.getChannels();
                if (nbChannels == Format.NOT_SPECIFIED)
                    nbChannels = 1;

                MacCoreaudioSystem.willOpenStream();
                try
                {
                    /*
                     * XXX A Renderer will participate in the acoustic echo
                     * cancellation if (acoustic echo cancellation is enabled,
                     * of course, and) the Renderer is not sounding a
                     * notification.
                     */
                    boolean isEchoCancel
                        = DataFlow.PLAYBACK.equals(dataFlow)
                            && audioSystem.isEchoCancel();

                    logger.debug("Call on MacCoreAudioDevice: startStream(" +
                                               isEchoCancel + ")");
                    stream
                        = MacCoreAudioDevice.startStream(
                                deviceUID,
                                this,
                                (float) inputFormat.getSampleRate(),
                                nbChannels,
                                inputFormat.getSampleSizeInBits(),
                                false,
                                inputFormat.getEndian()
                                    == AudioFormat.BIG_ENDIAN,
                                false,
                                false,
                                isEchoCancel);
                }
                finally
                {
                    MacCoreaudioSystem.didOpenStream();
                }
            }
        }
        finally
        {
            startStopLock.unlock();
        }
    }

    /**
     * Stops the rendering process.
     */
    @Override
    public void stop()
    {
        boolean doStop = false;
        startStopLock.lock();
        try
        {
            if(stream != 0 && deviceUID != null && !isStopping)
            {
                logger.debug("About to stop stream");
                doStop = true;
                this.isStopping = true;
                long timeout = 500;
                long startTime = System.currentTimeMillis();
                long currentTime = startTime;
                // Wait at most 500 ms to render the already received data.
                while(nbBufferData > 0
                        && (currentTime - startTime) < timeout)
                {
                    try
                    {
                        startStopCondition.await(timeout, TimeUnit.MILLISECONDS);
                    }
                    catch(InterruptedException ex)
                    {
                    }
                    currentTime = System.currentTimeMillis();
                }
            }

            if(doStop)
            {
                synchronized (stopLock)
                {
                    if(stream != 0 && deviceUID != null)
                    {
                        Log.logMediaStackObjectStopped(this);
                        MacCoreAudioDevice.stopStream(deviceUID, stream);

                        stream = 0;
                        buffer = null;
                        nbBufferData = 0;
                        this.isStopping = false;
                    }
                }
            }
        }
        finally
        {
            startStopLock.unlock();
        }
    }

    // Hack - only exists to provide a named object for logReadBytes calls from writeOutput().
    class MacCoreaudioNativeRenderer { }
    MacCoreaudioNativeRenderer nativeRenderer = new MacCoreaudioNativeRenderer();

    /**
     * Writes the data received to the buffer give in arguments, which is
     * provided by the CoreAudio library.
     *
     * @param buffer The buffer to fill in provided by the CoreAudio library.
     * @param bufferLength The length of the buffer provided.
     */
    public void writeOutput(byte[] buffer, int bufferLength)
    {
        // If the stop function has been called, then skip the synchronize which
        // can lead to a deadlock because the AudioDeviceStop native function
        // waits for this callback to end.
        if (startStopLock.tryLock())
        {
            try
            {
                synchronized (stopLock)
                {
                    // Our internal buffer needs to be at least big enough to copy out what the OS is asking of us.
                    updateBufferLength(bufferLength);

                    int length = nbBufferData;
                    if(bufferLength < length)
                        length = bufferLength;

                    System.arraycopy(this.buffer, 0, buffer, 0, length);
                    Log.logReceivedBytes(nativeRenderer, length);

                    // Fills the end of the buffer with silence.
                    if(length < bufferLength)
                        Arrays.fill(buffer, length, bufferLength, (byte) 0);

                    nbBufferData -= length;

                // Shift the remaining buffer data up to the start.
                    if(nbBufferData > 0)
                    {
                        System.arraycopy(
                                this.buffer, length,
                                this.buffer, 0,
                                nbBufferData);
                    }
                    else
                    {
                        // If the stop process is waiting, notifies that every
                        // sample has been consumed (nbBufferData == 0).
                        startStopCondition.signal();
                    }
                }
            }
            finally
            {
                startStopLock.unlock();
            }
        }
    }

    /**
     * Updates the deviceUID based on the current locator.
     *
     * @return True if the deviceUID has been updated. False otherwise.
     */
    private boolean updateDeviceUID()
    {
        MediaLocator locator = getLocator();
        if(locator != null)
        {
            String remainder = locator.getRemainder();
            if(remainder != null && remainder.length() > 1)
            {
                startStopLock.lock();
                try
                {
                    this.deviceUID = remainder.substring(1);
                }
                finally
                {
                    startStopLock.unlock();
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Increases the buffer length if necessary: if the new length is greater
     * than the current buffer length.
     *
     * @param newLength The new length requested.
     */
    private void updateBufferLength(int newLength)
    {
        startStopLock.lock();
        try
        {
            if(this.buffer == null)
            {
                this.buffer = new byte[newLength];
                nbBufferData = 0;
            }
            else if(newLength > this.buffer.length)
            {
                byte[] newBuffer = new byte[newLength];
                System.arraycopy(
                        this.buffer, 0,
                        newBuffer, 0,
                        nbBufferData);
                this.buffer = newBuffer;
            }
        }
        finally
        {
            startStopLock.unlock();
        }
    }
}
