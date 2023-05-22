/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
// Portions (c) Microsoft Corporation. All rights reserved.
package org.jitsi.impl.neomedia.jmfext.media.renderer.audio;

import static org.jitsi.impl.neomedia.jmfext.media.protocol.wasapi.WASAPI.*;

import java.beans.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

import javax.media.*;
import javax.media.format.*;

import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.control.*;
import org.jitsi.impl.neomedia.control.DiagnosticsControl.*;
import org.jitsi.impl.neomedia.device.*;
import org.jitsi.impl.neomedia.jmfext.media.protocol.wasapi.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.codec.*;
import org.jitsi.util.*;

import net.sf.fmj.media.*;

/**
 * Implements an audio <tt>Renderer</tt> using Windows Audio Session API
 * (WASAPI) and related Core Audio APIs such as Multimedia Device (MMDevice)
 * API.
 *
 * @author Lyubomir Marinov
 */
public class WASAPIRenderer
    extends AbstractAudioRenderer<WASAPISystem>
{
    /**
     * The <tt>Logger</tt> used by the <tt>WASAPIRenderer</tt> class and its
     * instances to log debug information.
     */
    private static final Logger logger = Logger.getLogger(WASAPIRenderer.class);

    /**
     * The human-readable name of the <tt>WASAPIRenderer</tt> <tt>PlugIn</tt>
     * implementation instances.
     */
    private static final String PLUGIN_NAME
        = "Windows Audio Session API (WASAPI) Renderer";

    /**
     * The interval logger to use for some logs that may be spammy
     */
    private final IntervalLogger intervalLogger = new IntervalLogger(logger);

    /**
     * The duration in milliseconds of the endpoint buffer.
     */
    private long bufferDuration;

    /**
     * The indicator which determines whether the audio stream represented by
     * this instance, {@link #iAudioClient} and {@link #iAudioRenderClient} is
     * busy and, consequently, its state should not be modified. For example,
     * the audio stream is busy during the execution of
     * {@link #process(Buffer)}.
     */
    private boolean busy;

    /**
     * The length in milliseconds of the interval between successive, periodic
     * processing passes by the audio engine on the data in the endpoint buffer.
     */
    private long devicePeriod = WASAPISystem.DEFAULT_DEVICE_PERIOD;

    /**
     * The value of {@link #devicePeriod} expressed in terms of numbers of
     * frames (i.e. takes the sample rate into account).
     */
    private int devicePeriodInFrames;

    /**
     * The number of channels with which {@link #iAudioClient} has been
     * initialized.
     */
    private int dstChannels;

    /**
     * The <tt>AudioFormat</tt> with which {@link #iAudioClient} has been
     * initialized.
     */
    private AudioFormat dstFormat;

    /**
     * The sample size in bytes with which {@link #iAudioClient} has been
     * initialized.
     */
    private int dstSampleSize;

    /**
     * The event handle that the system signals when an audio buffer is ready to
     * be processed by the client.
     */
    private long eventHandle;

    /**
     * The <tt>Runnable</tt> which is scheduled by this <tt>WASAPIRenderer</tt>
     * and executed by {@link #eventHandleExecutor} and waits for
     * {@link #eventHandle} to be signaled.
     */
    private Runnable eventHandleCmd;

    /**
     * The <tt>Executor</tt> implementation which is to execute
     * {@link #eventHandleCmd}.
     */
    private Executor eventHandleExecutor;

    /**
     * The WASAPI <tt>IAudioClient</tt> instance which enables this
     * <tt>Renderer</tt> to create and initialize an audio stream between this
     * <tt>Renderer</tt> and the audio engine of the associated audio endpoint
     * device.
     */
    private long iAudioClient;

    /**
     * The WASAPI <tt>IAudioRenderClient</tt> obtained from
     * {@link #iAudioClient} which enables this <tt>Renderer</tt> to write
     * output data to the rendering endpoint buffer.
     */
    private long iAudioRenderClient;

    /**
     * The indicator which determines whether the value of the <tt>locator</tt>
     * property of this instance was equal to null when this <tt>Renderer</tt>
     * was opened. Indicates that this <tt>Renderer</tt> should successfully
     * process media data without actually rendering to any render endpoint
     * device.
     */
    private boolean locatorIsNull;

    /**
     * The maximum capacity in frames of the endpoint buffer.
     */
    private int numBufferFrames;

    /**
     * The data which has remained unwritten during earlier invocations of
     * {@link #process(Buffer)} because it represents frames which are few
     * enough to be accepted on their own for writing by
     * {@link #iAudioRenderClient}.
     */
    private byte[] remainder;

    /**
     * The number of bytes in {@link #remainder} which represent valid audio
     * data to be written by {@link #iAudioRenderClient}.
     */
    private int remainderLength;

    /**
     * The <tt>Codec</tt> which resamples the media provided to this
     * <tt>Renderer</tt> via {@link #process(Buffer)} into {@link #dstFormat}
     * if necessary.
     */
    private Codec resampler;

    /**
     * The number of channels of the audio signal output by {@link #resampler}.
     * It may differ from {@link #dstChannels}.
     */
    private int resamplerChannels;

    /**
     * The size in bytes of an audio frame produced by {@link #resampler}. Based
     * on {@link #resamplerChannels} and {@link #resamplerSampleSize} and cached
     * in order to reduce calculations.
     */
    private int resamplerFrameSize;

    /**
     * The <tt>Buffer</tt> which provides the input to {@link #resampler}.
     * Represents a unit of {@link #remainder} to be processed in a single call
     * to <tt>resampler</tt>.
     */
    private Buffer resamplerInBuffer;

    /**
     * The <tt>Buffer</tt> which receives the output of {@link #resampler}.
     */
    private Buffer resamplerOutBuffer;

    /**
     * The size in bytes of an audio sample produced by {@link #resampler}.
     */
    private int resamplerSampleSize;

    /**
     * The number of channels which which this <tt>Renderer</tt> has been
     * opened.
     */
    private int srcChannels;

    /**
     * The frame size in bytes with which this <tt>Renderer</tt> has been
     * opened. It is the product of {@link #srcSampleSize} and
     * {@link #srcChannels}.
     */
    private int srcFrameSize;

    /**
     * The <tt>AudioFormat</tt> with which this <tt>Renderer</tt> has been
     * opened.
     */
    private AudioFormat srcFormat;

    /**
     * The sample size in bytes with which this <tt>Renderer</tt> has been
     * opened.
     */
    private int srcSampleSize;

    /**
     * The indicator which determines whether this <tt>Renderer</tt> is started
     * i.e. there has been a successful invocation of {@link #start()} without
     * an intervening invocation of {@link #stop()}.
     */
    private boolean started;

    /**
     * The time in milliseconds at which the writing to the render endpoint
     * buffer has started malfunctioning. For example, {@link #remainder} being
     * full from the point of view of {@link #process(Buffer)} for an extended
     * period of time may indicate abnormal functioning.
     */
    private long writeIsMalfunctioningSince = DiagnosticsControl.NEVER;

    /**
     * Indicates whether the device is currently malfunctioning, and if so, why.
     */
    private MalfunctionState malfunctionState = MalfunctionState.FUNCTIONING_CORRECTLY;

    /**
     * The maximum interval of time in milliseconds that the writing to the
     * render endpoint buffer is allowed to be under suspicion that it is
     * malfunctioning. If it remains under suspicion after the maximum interval
     * of time has elapsed, the writing to the render endpoint buffer is to be
     * considered malfunctioning for real.
     */
    private long writeIsMalfunctioningTimeout;

    /**
     * The <tt>DiagnosticsControl</tt> implementation of this instance which
     * allows the diagnosis of the functional health of WASAPI devices.
     */
    private final DiagnosticsControl diagnosticsControl
        = new DiagnosticsControl()
        {
            /**
             * {@inheritDoc}
             *
             * <tt>WASAPIRenderer</tt>'s <tt>DiagnosticsControl</tt>
             * implementation does not provide its own user interface and always
             * returns <tt>null</tt>.
             */
            @Override
            public java.awt.Component getControlComponent()
            {
                return null;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public long getMalfunctioningSince()
            {
                return writeIsMalfunctioningSince;
            }

            @Override
            public MalfunctionState getMalfunctionState()
            {
                return malfunctionState;
            }

            /**
             * {@inheritDoc}
             *
             * Returns the identifier of the WASAPI device written through
             * this <tt>WASAPIRenderer</tt>.
             */
            @Override
            public String toString()
            {
                String name;

                try
                {
                    CaptureDeviceInfo device = audioSystem.getSelectedDevice(dataFlow);
                    name = device.getName();
                }
                catch (Exception e)
                {
                    logger.error("Failed to find name for device:" + e);
                    name = "";
                }
                return name;
            }
        };

    /**
     * Initializes a new <tt>WASAPIRenderer</tt> instance which is to perform
     * playback (as opposed to sound a notification).
     */
    public WASAPIRenderer()
    {
        this(DataFlow.PLAYBACK);
    }

    /**
     * Initializes a new <tt>WASAPIRenderer</tt> instance which is to either
     * perform playback or sound a notification.
     *
     * @param dataFlow {@link DataFlow#PLAYBACK} if the new instance
     * is to perform playback or {@link DataFlow#NOTIFY} if the new
     * instance is to sound a notification
     */
    public WASAPIRenderer(DataFlow dataFlow)
    {
        super(AudioSystem.LOCATOR_PROTOCOL_WASAPI, dataFlow);
        logger.debug("Created WASAPIRenderer " + this.hashCode());
    }

    /**
     * Initializes a new <tt>WASAPIRenderer</tt> instance which is to either
     * perform playback or sound a notification.
     *
     * @param playback <tt>true</tt> if the new instance is to perform playback
     * or <tt>false</tt> if the new instance is to sound a notification
     */
    public WASAPIRenderer(boolean playback)
    {
        this(
                playback
                    ? DataFlow.PLAYBACK
                    : DataFlow.NOTIFY);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void close()
    {
        close(true);
    }

    /**
     * Overridden close method that allows us to skip stopping the device.
     * Should only be used if the device is broken!
     */
    public synchronized void close(boolean doStop)
    {
        try
        {
            logger.debug("WASAPIRenderer.close() on " + this.hashCode());
            if (doStop)
            {
                logger.debug("Call stop");
                stop();
            }
        }
        finally
        {
            if (iAudioRenderClient != 0)
            {
                IAudioRenderClient_Release(iAudioRenderClient);
                iAudioRenderClient = 0;
            }
            if (iAudioClient != 0)
            {
                IAudioClient_Release(iAudioClient);
                logger.info("iAudioClient with value " + iAudioClient
                    + " released");
                iAudioClient = 0;
            }
            if (eventHandle != 0)
            {
                try
                {
                    CloseHandle(eventHandle);
                }
                catch (HResultException hre)
                {
                    // The event HANDLE will be leaked.
                    logger.warn("Failed to close event HANDLE.", hre);
                }
                eventHandle = 0;
            }
            maybeCloseResampler();

            dstFormat = null;
            locatorIsNull = false;
            remainder = null;
            remainderLength = 0;
            srcFormat = null;
            started = false;

            super.close();
        }
    }

    /**
     * Finds the first non-<tt>null</tt> element in a specific array of
     * <tt>AudioFormat</tt>s.
     *
     * @param formats the array of <tt>AudioFormat</tt>s in which the first
     * non-<tt>null</tt> element is to be found
     * @return the first non-<tt>null</tt> element in <tt>format</tt>s if any;
     * otherwise, <tt>null</tt>
     */
    private static AudioFormat findFirst(AudioFormat[] formats)
    {
        AudioFormat format = null;

        for (AudioFormat aFormat : formats)
        {
            if (aFormat != null)
            {
                format = aFormat;
                break;
            }
        }
        return format;
    }

    /**
     * Gets an array of alternative <tt>AudioFormat</tt>s based on
     * <tt>inputFormat</tt> with which an attempt is to be made to initialize a
     * new <tt>IAudioClient</tt> instance.
     *
     * @return an array of alternative <tt>AudioFormat</tt>s based on
     * <tt>inputFormat</tt> with which an attempt is to be made to initialize a
     * new <tt>IAudioClient</tt> instance
     */
    private AudioFormat[] getFormatsToInitializeIAudioClient()
    {
        AudioFormat inputFormat = this.inputFormat;

        if (inputFormat == null)
        {
            throw new NullPointerException("No inputFormat set.");
        }
        else
        {
            /*
             * Prefer to initialize the IAudioClient with an AudioFormat which
             * matches the inputFormat as closely as possible.
             */
            AudioFormat[] preferredFormats
                = WASAPISystem.getFormatsToInitializeIAudioClient(inputFormat);
            // Otherwise, any supported Format will do.
            Format[] supportedFormats = getSupportedInputFormats();
            List<AudioFormat> formats
                = new ArrayList<>(
                    preferredFormats.length + supportedFormats.length);

            for (AudioFormat format : preferredFormats)
            {
                if (!formats.contains(format))
                {
                    formats.add(format);
                }
            }
            for (Format format : supportedFormats)
            {
                if (!formats.contains(format)
                        && (format instanceof AudioFormat))
                {
                    formats.add((AudioFormat) format);
                }
            }
            return formats.toArray(new AudioFormat[formats.size()]);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName()
    {
        return PLUGIN_NAME;
    }

    /**
     * {@inheritDoc}
     *
     * Overrides the super implementation to handle the case in which the user
     * has selected "none" for the playback/notify device.
     */
    @Override
    public Format[] getSupportedInputFormats()
    {
        if (getLocator() == null)
        {
            /*
             * XXX We toyed with the idea of calculating a list of common
             * Formats supported by all devices (of the dataFlow of this
             * AbstractAudioRenderer, of course) but that turned out to be
             * monstrous in code, inefficient at least in terms of garbage
             * collection and with questionable suitability. The following
             * approach will likely have a comparable suitability with better
             * efficiency achieved code that is easier to understand.
             */

            /*
             * The maximums supported by the WASAPI integration at the time of
             * this writing.
             */
            double sampleRate = MediaUtils.MAX_AUDIO_SAMPLE_RATE;
            int sampleSizeInBits = 16;
            int channels = 2;

            if ((sampleRate == Format.NOT_SPECIFIED)
                    && (Constants.AUDIO_SAMPLE_RATES.length != 0))
            {
                sampleRate = Constants.AUDIO_SAMPLE_RATES[0];
            }
            return
                WASAPISystem.getFormatsToInitializeIAudioClient(
                        new AudioFormat(
                                AudioFormat.LINEAR,
                                sampleRate,
                                sampleSizeInBits,
                                channels,
                                AudioFormat.LITTLE_ENDIAN,
                                AudioFormat.SIGNED,
                                /* frameSizeInBits */ Format.NOT_SPECIFIED,
                                /* frameRate */ Format.NOT_SPECIFIED,
                                Format.byteArray));
        }
        else
        {
            return super.getSupportedInputFormats();
        }
    }

    /**
     * Closes {@link #resampler} if it is non-<tt>null</tt>.
     */
    private void maybeCloseResampler()
    {
        Codec resampler = this.resampler;

        if (resampler != null)
        {
            this.resampler = null;
            resamplerInBuffer = null;
            resamplerOutBuffer = null;

            try
            {
                resampler.close();
            }
            catch (Throwable t)
            {
                if (t instanceof InterruptedException)
                {
                    Thread.currentThread().interrupt();
                }
                else if (t instanceof ThreadDeath)
                {
                    throw (ThreadDeath) t;
                }
                else
                {
                    logger.error("Failed to close resampler.", t);
                }
            }
        }
    }

    /**
     * Invokes <tt>WASAPI.IAudioRenderClient_Write</tt> on
     * {@link #iAudioRenderClient} and logs and swallows any
     * <tt>HResultException</tt>.
     *
     * @param data the bytes of the audio samples to be written into the render
     * endpoint buffer
     * @param offset the offset in <tt>data</tt> at which the bytes of the audio
     * samples to be written into the render endpoint buffer begin
     * @param length the number of the bytes in <tt>data</tt> beginning at
     * <tt>offset</tt> of the audio samples to be written into the render
     * endpoint buffer
     * @param srcSampleSize the size in bytes of an audio sample in
     * <tt>data</tt>
     * @param srcChannels the number of channels of the audio signal provided in
     * <tt>data</tt>
     * @return the number of bytes from <tt>data</tt> (starting at
     * <tt>offset</tt>) which have been written into the render endpoint buffer
     * or <tt>0</tt> upon <tt>HResultException</tt>
     */
    private int maybeIAudioRenderClientWrite(
            byte[] data, int offset, int length,
            int srcSampleSize, int srcChannels)
    {
        int written;

        try
        {
            written
                = IAudioRenderClient_Write(
                        iAudioRenderClient,
                        data, offset, length,
                        srcSampleSize, srcChannels,
                        dstSampleSize, dstChannels);
        }
        catch (HResultException hre)
        {
            written = 0;

            // The exception here is quite verbose and likely to be present for
            // every future packet write as well.  Therefore only interval log.
            intervalLogger.errorAndIntervalDebug("IAudioRenderClient_Write",
                                                 hre);
        }
        return written;
    }

    /**
     * Initializes and opens a new instance of {@link #resampler} if the
     * <tt>Format</tt>-related state of this instance deems its existence
     * necessary.
     */
    private void maybeOpenResampler()
    {
        AudioFormat inFormat = this.inputFormat;
        AudioFormat outFormat = this.dstFormat;
        logger.info("Opening resampler for " + inFormat + " to " + outFormat);

        // We are able to translate between mono and stereo.
        if ((inFormat.getSampleRate() == outFormat.getSampleRate())
                && (inFormat.getSampleSizeInBits()
                        == outFormat.getSampleSizeInBits()))
        {
            logger.debug("No resample required");
            return;
        }

        // The resamplers are not expected to convert between mono and stereo.
        int channels = inFormat.getChannels();

        if (outFormat.getChannels() != channels)
        {
            outFormat
                = new AudioFormat(
                        outFormat.getEncoding(),
                        outFormat.getSampleRate(),
                        outFormat.getSampleSizeInBits(),
                        channels,
                        outFormat.getEndian(),
                        outFormat.getSigned(),
                        /* frameSizeInBits */ Format.NOT_SPECIFIED,
                        /* frameRate */ Format.NOT_SPECIFIED,
                        outFormat.getDataType());
        }

        Codec resampler = maybeOpenResampler(inFormat, outFormat);

        if (resampler == null)
        {
            throw new IllegalStateException(
                    "Failed to open a codec to resample [" + inFormat
                        + "] into [" + outFormat + "].");
        }
        else
        {
            this.resampler = resampler;

            resamplerInBuffer = new Buffer();
            resamplerInBuffer.setFormat(inFormat);

            resamplerChannels = outFormat.getChannels();
            resamplerSampleSize = WASAPISystem.getSampleSizeInBytes(outFormat);
            resamplerFrameSize = resamplerChannels * resamplerSampleSize;

            byte[] resamplerData =
                                new byte[numBufferFrames * resamplerFrameSize];

            resamplerOutBuffer = new Buffer();
            resamplerOutBuffer.setData(resamplerData);
            resamplerOutBuffer.setLength(0);
            resamplerOutBuffer.setOffset(0);
        }
    }

    /**
     * Processes audio samples from {@link #remainder} through the
     * {@link #resampler}.  I.e. resample the audio samples into
     * {@link #resamplerOutBuffer}.
     *
     * @param numFramesRequested the number of audio frames in the units of
     * {@link #dstFormat} requested by the rendering endpoint
     */
    private void maybeResample(int numFramesRequested)
    {
        int outLengthBytes = resamplerOutBuffer.getLength();

        // Only resample if the resamplerOutBuffer doesn't have enough data to
        // satisfy the request
        if (outLengthBytes < numFramesRequested * resamplerFrameSize)
        {
            // Sample rate conversions work on audio frames, not on bytes.
            int outFrames =
                (((byte[])resamplerOutBuffer.getData()).length -
                                          outLengthBytes) / resamplerFrameSize;

            if (outFrames > 0)
            {
                // Predict how many bytes will be consumed from the input during
                // the sample rate conversion.
                int srcSampleRate = (int) srcFormat.getSampleRate();
                int dstSampleRate = (int) dstFormat.getSampleRate();
                int inLengthBytes = (outFrames *
                                     srcSampleRate /
                                     dstSampleRate) * srcFrameSize;

                if (inLengthBytes > remainder.length ||
                    inLengthBytes > remainderLength)
                {
                    inLengthBytes = Math.min(remainder.length, remainderLength);
                }

                if (inLengthBytes > 0)
                {
                    int resampledBytes;

                    resamplerOutBuffer.setLength(0);
                    resamplerOutBuffer.setOffset(outLengthBytes);

                    try
                    {
                        resamplerOutBuffer.setDiscard(false);
                        resamplerInBuffer.setLength(inLengthBytes);
                        resamplerInBuffer.setOffset(0);

                        resampler.process(resamplerInBuffer, resamplerOutBuffer);
                    }
                    finally
                    {
                        resampledBytes = resamplerOutBuffer.getLength();
                        outLengthBytes = resamplerOutBuffer.getOffset() + resampledBytes;
                        resamplerOutBuffer.setLength(outLengthBytes);
                        resamplerOutBuffer.setOffset(0);
                    }

                    if (resampledBytes > 0)
                    {
                        // How many bytes have actually been consumed from the
                        // input during the sample rate conversion?
                        int resampledFrames = resampledBytes / resamplerFrameSize;
                        resampledBytes =
                            (resampledFrames * srcSampleRate / dstSampleRate)
                                                                 * srcFrameSize;

                        if (resampledBytes > 0)
                        {
                            popFromRemainder(resampledBytes);
                        }
                    }
                }
            }
        }
    }

    /**
     * Attempts to initialize and open a new <tt>Codec</tt> to resample media
     * data from a specific input <tt>AudioFormat</tt> into a specific output
     * <tt>AudioFormat</tt>. If no suitable resampler is found, returns
     * <tt>null</tt>. If a suitable resampler is found but its initialization or
     * opening fails, logs and swallows any <tt>Throwable</tt> and returns
     * <tt>null</tt>.
     *
     * @param inFormat the <tt>AudioFormat</tt> in which the new instance is to
     * input media data
     * @param outFormat the <tt>AudioFormat</tt> in which the new instance is to
     * output media data
     * @return a new <tt>Codec</tt> which is able to resample media data from
     * the specified <tt>inFormat</tt> into the specified <tt>outFormat</tt> if
     * such a resampler could be found, initialized and opened; otherwise,
     * <tt>null</tt>
     */
    public static Codec maybeOpenResampler(
            AudioFormat inFormat,
            AudioFormat outFormat)
    {
        logger.info("Opening the resampler for in " + inFormat + ", out " + outFormat);
        List<String> classNames
            = PlugInManager.getPlugInList(
                    inFormat,
                    outFormat,
                    PlugInManager.CODEC);
        Codec resampler = null;

        if (classNames != null)
        {
            for (String className : classNames)
            {
                try
                {
                    Codec codec
                        = (Codec) Class.forName(className).newInstance();
                    logger.debug("Considering codec for resample " + codec);
                    Format setInput = codec.setInputFormat(inFormat);

                    if ((setInput != null) && inFormat.matches(setInput))
                    {
                        Format setOutput = codec.setOutputFormat(outFormat);

                        if ((setOutput != null) && outFormat.matches(setOutput))
                        {
                            codec.open();
                            resampler = codec;
                            logger.info("Using codec for resample " + codec);

                            break;
                        }
                    }
                }
                catch (Throwable t)
                {
                    if (t instanceof ThreadDeath)
                    {
                        throw (ThreadDeath) t;
                    }
                    else
                    {
                        logger.warn(
                                "Failed to open resampler " + className,
                                t);
                    }
                }
            }
        }
        return resampler;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void open()
        throws ResourceUnavailableException
    {
        logger.debug("WASAPIRenderer.open() on " + this.hashCode());
        if (this.iAudioClient != 0)
        {
            logger.debug("iAudioClient already exists: " + iAudioClient);
            return;
        }

        MediaLocator locator = null;

        try
        {
            locator = getLocator();
            logger.debug("Opening found locator " + locator);

            if (locatorIsNull = (locator == null))
            {
                /*
                 * We actually want to allow the user to switch the playback
                 * and/or notify device to none mid-stream in order to disable
                 * the playback.
                 */
                logger.debug("WASAPIRenderer open() called but no locator");
                super.open();
                return;
            }

            /*
             * The method getFormatsToInitializeIAudioClient will assert that
             * inputFormat is set.
             */
            AudioFormat[] formats = getFormatsToInitializeIAudioClient();
            long eventHandle = CreateEvent(0, false, false, null);

            try
            {
                long iAudioClient
                    = audioSystem.initializeIAudioClient(
                            locator,
                            dataFlow,
                            /* streamFlags */ 0,
                            eventHandle,
                            WASAPISystem.DEFAULT_BUFFER_DURATION,
                            formats);

                if (iAudioClient == 0)
                {
                    throw new ResourceUnavailableException(
                            "Failed to initialize IAudioClient"
                                + " for MediaLocator " + locator
                                + " and AudioSystem.DataFlow " + dataFlow);
                }
                try
                {
                    long iAudioRenderClient
                        = IAudioClient_GetService(
                                iAudioClient,
                                IID_IAudioRenderClient);

                    if (iAudioRenderClient == 0)
                    {
                        throw new ResourceUnavailableException(
                                "IAudioClient_GetService"
                                    + "(IID_IAudioRenderClient)");
                    }
                    try
                    {
                        srcFormat = this.inputFormat;
                        dstFormat = findFirst(formats);
                        logger.debug("srcFormat = " + srcFormat +
                            " and dstFormat = " + dstFormat);

                        /*
                         * The value hnsDefaultDevicePeriod is documented to
                         * specify the default scheduling period for a
                         * shared-mode stream.
                         */
                        devicePeriod
                            = IAudioClient_GetDefaultDevicePeriod(iAudioClient)
                                / 10000L;
                        numBufferFrames
                            = IAudioClient_GetBufferSize(iAudioClient);

                        int dstSampleRate = (int) dstFormat.getSampleRate();

                        bufferDuration
                            = numBufferFrames * 1000L / dstSampleRate;
                        /*
                         * We will very likely be inefficient if we fail to
                         * synchronize with the scheduling period of the audio
                         * engine but we have to make do with what we have.
                         */
                        if (devicePeriod <= 1)
                        {
                            devicePeriod = bufferDuration / 2;
                            if ((devicePeriod
                                        > WASAPISystem.DEFAULT_DEVICE_PERIOD)
                                    || (devicePeriod <= 1))
                            {
                                devicePeriod
                                    = WASAPISystem.DEFAULT_DEVICE_PERIOD;
                            }
                        }
                        devicePeriodInFrames
                            = (int) (devicePeriod * dstSampleRate / 1000L);

                        dstChannels = dstFormat.getChannels();
                        dstSampleSize
                            = WASAPISystem.getSampleSizeInBytes(dstFormat);

                        maybeOpenResampler();

                        srcChannels = srcFormat.getChannels();
                        srcSampleSize
                            = WASAPISystem.getSampleSizeInBytes(srcFormat);
                        srcFrameSize = srcSampleSize * srcChannels;

                        /*
                         * The remainder/residue in frames of
                         * IAudioRenderClient_Write cannot be more than the
                         * maximum capacity of the endpoint buffer.
                         */
                        int remainderCapacityInFrames;

                        if (resampler == null)
                        {
                            remainderCapacityInFrames = numBufferFrames;
                        }
                        else
                        {
                            // The units of srcBuffer are based on srcFormat,
                            // the units of numBufferFrames are based on
                            // dstFormat.
                            int srcSampleRate = (int) srcFormat.getSampleRate();
                            remainderCapacityInFrames = numBufferFrames *
                                                  srcSampleRate / dstSampleRate;
                        }

                        remainder = new byte[remainderCapacityInFrames * srcFrameSize];

                        if (resamplerInBuffer != null)
                        {
                            resamplerInBuffer.setData(remainder);
                        }

                        /*
                         * Introduce latency in order to decrease the likelihood
                         * of underflow.
                         */
                        remainderLength = remainder.length;

                        setWriteIsMalfunctioning(MalfunctionState.FUNCTIONING_CORRECTLY);
                        writeIsMalfunctioningTimeout
                            = 2 * Math.max(bufferDuration, devicePeriod);

                        this.eventHandle = eventHandle;
                        eventHandle = 0;
                        logger.info("iAudioClient changed from " + this.iAudioClient
                            + " to " + iAudioClient);
                        this.iAudioClient = iAudioClient;
                        iAudioClient = 0;
                        this.iAudioRenderClient = iAudioRenderClient;
                        iAudioRenderClient = 0;
                    }
                    finally
                    {
                        if (iAudioRenderClient != 0)
                        {
                            IAudioRenderClient_Release(iAudioRenderClient);
                        }
                    }
                }
                finally
                {
                    if (iAudioClient != 0)
                    {
                        IAudioClient_Release(iAudioClient);
                        maybeCloseResampler();
                    }
                }
            }
            finally
            {
                if (eventHandle != 0)
                {
                    CloseHandle(eventHandle);
                }
            }
        }
        catch (Throwable t)
        {
            if (t instanceof InterruptedException)
            {
                Thread.currentThread().interrupt();
            }
            else if (t instanceof ThreadDeath)
            {
                throw (ThreadDeath) t;
            }
            else
            {
                logger.error(
                        "Failed to open a WASAPIRenderer on audio endpoint"
                            + " device " + toString(locator),
                        t);
                if (t instanceof ResourceUnavailableException)
                {
                    throw (ResourceUnavailableException) t;
                }
                else
                {
                    ResourceUnavailableException rue
                        = new ResourceUnavailableException();

                    rue.initCause(t);
                    throw rue;
                }
            }
        }

        super.open();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected synchronized void playbackDevicePropertyChange(
            PropertyChangeEvent ev)
    {
        /*
         * Stop, close, re-open and re-start this Renderer (performing whichever
         * of these in order to bring it into the same state) in order to
         * reflect the change in the selection with respect to the playback or
         * notify device.
         */

        waitWhileBusy();

        boolean open
            = ((iAudioClient != 0) && (iAudioRenderClient != 0))
                || locatorIsNull;

        if (open)
        {
            boolean start = started;

            close();

            try
            {
                open();
            }
            catch (ResourceUnavailableException rue)
            {
                throw new UndeclaredThrowableException(rue);
            }
            if (start)
            {
                start();
            }
        }
    }

    /**
     * Pops a specific number of bytes from {@link #remainder}. For example,
     * because such a number of bytes have been read from <tt>remainder</tt> and
     * written into the rendering endpoint buffer.
     *
     * @param length the number of bytes to pop from <tt>remainder</tt>
     */
    private void popFromRemainder(int length)
    {
        remainderLength = pop(remainder, remainderLength, length);
    }

    /**
     * Pops a specific number of bytes from (the head of) a specific array of
     * <tt>byte</tt>s.
     *
     * @param array the array of <tt>byte</tt> from which the specified number
     * of bytes are to be popped
     * @param arrayLength the number of elements in <tt>array</tt> which contain
     * valid data
     * @param length the number of bytes to be popped from <tt>array</tt>
     * @return the number of elements in <tt>array</tt> which contain valid data
     * after the specified number of bytes have been popped from it
     */
    public static int pop(byte[] array, int arrayLength, int length)
    {
        if (length < 0)
        {
            throw new IllegalArgumentException("length");
        }
        if (length == 0)
        {
            return arrayLength;
        }

        int newArrayLength = arrayLength - length;

        if (newArrayLength > 0)
        {
            for (int i = 0, j = length; i < newArrayLength; i++, j++)
            {
                array[i] = array[j];
            }
        }
        else
        {
            newArrayLength = 0;
        }
        return newArrayLength;
    }

    /**
     * Processes the provided buffer of audio data into the remainder buffer to
     * be written to WASAPI on the EventHandleCmd thread.
     *
     * Try to spot malfunctioning devices by checking whether the remainder
     * buffer is filling up and never emptying.
     *
     * @param buffer the input audio data
     */
    @Override
    public int process(Buffer buffer)
    {
        int length = buffer.getLength();
        if (length < 1)
        {
            return BUFFER_PROCESSED_OK;
        }

        byte[] data = (byte[]) buffer.getData();
        int offset = buffer.getOffset();

        synchronized (this)
        {
            if ((iAudioClient == 0) || (iAudioRenderClient == 0))
            {
                /*
                 * We actually want to allow the user to switch the playback
                 * and/or notify device to none mid-stream in order to disable
                 * the playback.
                 */

                int ret = locatorIsNull ? BUFFER_PROCESSED_OK : BUFFER_PROCESSED_FAILED;

                if (ret == BUFFER_PROCESSED_FAILED)
                    intervalLogger.debug("Process failed, locator is not null");

                return ret;
            }
            else if (!started)
            {
                intervalLogger.debug("Process failed, not started");
                return BUFFER_PROCESSED_FAILED;
            }
            else
            {
                waitWhileBusy();
                busy = true;
            }
        }

        int ret = BUFFER_PROCESSED_OK;
        long sleep = 0;

        try
        {
            /*
             * We write into remainder, the runInEventHandleCmd will read from
             * remainder and write into the rendering endpoint buffer.
             */
            int toCopy = remainder.length - remainderLength;
            if (toCopy > 0)
            {
                if (toCopy > length)
                {
                    toCopy = length;
                }
                System.arraycopy(
                        data, offset,
                        remainder, remainderLength,
                        toCopy);
                remainderLength += toCopy;

                if (length > toCopy)
                {
                    buffer.setLength(length - toCopy);
                    buffer.setOffset(offset + toCopy);
                    ret |= INPUT_BUFFER_NOT_CONSUMED;
                }

                /*
                 * Writing from the input Buffer into remainder has occurred so
                 * the remainder buffer is not stuck full of data.  Therefore,
                 * it does not look like the writing to the render endpoint
                 * buffer is malfunctioning.
                 */
                setWriteIsMalfunctioning(MalfunctionState.FUNCTIONING_CORRECTLY);
            }
            else
            {
                /*
                 * The remainder buffer is full so it is possible that the
                 * writing to the render endpoint buffer is malfunctioning.
                 *
                 * As well as calling the standard malfunctioning method, we
                 * check how long we've been failing for and if it exceeds the
                 * timeout, we return failed.
                 *
                 * Note we don't clear the existing data in remainder as that
                 * would cause us to incorrectly think the device is working
                 * again next time we come into this method.
                 */
                ret |= INPUT_BUFFER_NOT_CONSUMED;
                sleep = devicePeriod;
                setWriteIsMalfunctioning(MalfunctionState.REMAINDER_BUFFER_FULL);

                long writeIsMalfunctioningDuration = System.currentTimeMillis() - writeIsMalfunctioningSince;

                if (writeIsMalfunctioningDuration > writeIsMalfunctioningTimeout)
                {
                    intervalLogger.debug("Process failed, remainder buffer full");
                    ret = BUFFER_PROCESSED_FAILED;
                }
            }
        }
        finally
        {
            synchronized (this)
            {
                busy = false;
                notifyAll();
            }
        }

        /*
         * If we've been told to sleep (to make space available in the
         * remainder buffer), do so now.
         */
        if (sleep > 0)
        {
            boolean interrupted = false;

            synchronized (this)
            {
                /*
                 * Spurious wake-ups should not be a big issue here. While this
                 * Renderer may check for available space in the rendering
                 * endpoint buffer more often than practically necessary (which
                 * may very well classify as a case of performance loss), the
                 * ability to unblock this Renderer is considered more
                 * important.
                 */
                try
                {
                    wait(sleep);
                }
                catch (InterruptedException ie)
                {
                    interrupted = true;
                }
            }
            if (interrupted)
            {
                Thread.currentThread().interrupt();
            }
        }
        return ret;
    }

    /**
     * Runs/executes in the thread associated with a specific <tt>Runnable</tt>
     * initialized to wait for {@link #eventHandle} to be signaled.
     *
     * @param eventHandleCmd the <tt>Runnable</tt> which has been initialized to
     * wait for <tt>eventHandle</tt> to be signaled and in whose associated
     * thread the method is invoked
     */
    private void runInEventHandleCmd(Runnable eventHandleCmd)
    {
        boolean resetDevice = false;

        try
        {
            useAudioThreadPriority();

            do
            {
                long eventHandle;

                /*
                 * If we hit a device exception while running through the loop,
                 * reset it now and then attempt to continue.
                 */
                if (resetDevice)
                {
                    logger.error("Try to reset audio device");
                    synchronized (this)
                    {
                        logger.debug("Try to close");
                        close(false);
                        logger.debug("Closed, try to re-Open");

                        try
                        {
                            open();
                            logger.debug("Opened");

                            // Fix these up so we stay in the loop.
                            eventHandleCmd = this.eventHandleCmd;
                            started = true;
                        }
                        catch (ResourceUnavailableException rue)
                        {
                            logger.error("Unable to reopen renderer", rue);
                        }
                    }

                    resetDevice = false;
                }

                synchronized (this)
                {
                    /*
                     * Does this WASAPIRender still want eventHandleCmd to
                     * execute?
                     */
                    if (!eventHandleCmd.equals(this.eventHandleCmd))
                    {
                        logger.debug("eventHandleCmd changed");
                        break;
                    }
                    // Is this WASAPIRenderer still opened and started?
                    if ((iAudioClient == 0)
                            || (iAudioRenderClient == 0)
                            || !started)
                    {
                        logger.debug("Renderer no longer opened/started. " +
                           "iAudioClient: " + iAudioClient +
                           " iAudioRenderClient: " + iAudioRenderClient +
                           " started: " + started);
                        break;
                    }

                    /*
                     * The value of eventHandle will remain valid while this
                     * WASAPIRenderer wants eventHandleCmd to execute.
                     */
                    eventHandle = this.eventHandle;
                    if (eventHandle == 0)
                    {
                        throw new IllegalStateException("eventHandle");
                    }

                    waitWhileBusy();
                    busy = true;
                }
                try
                {
                    int numPaddingFrames;

                    try
                    {
                        numPaddingFrames
                            = IAudioClient_GetCurrentPadding(iAudioClient);
                    }
                    catch (HResultException hre)
                    {
                        logger.error("IAudioClient_GetCurrentPadding", hre);

                        if (HRESULT_BLOCKLIST.contains(hre.getHResult()))
                        {
                            // We have hit an error that we cannot recover the
                            // device from, so reset it here. This causes the
                            // current audio stream to collapse and the user
                            // will lose their call if they are in one.
                            //
                            // TODO Tear down and re-establish the renderer so
                            // the user does not lose their call.
                            logger.debug("Device needs to be reset");
                            resetDevice = true;
                            continue;
                        }
                        else
                        {
                            logger.debug("Different error");
                            break;
                        }
                    }

                    int numFramesRequested = numBufferFrames - numPaddingFrames;

                    /*
                     * If there is no available space in the rendering endpoint
                     * buffer, wait for the system to signal when an audio
                     * buffer is ready to be processed by the client.
                     */
                    if (numFramesRequested > 0)
                    {
                        byte[] buf;
                        int bufChannels;
                        int bufFrameSize;
                        int bufLength;
                        int bufSampleSize;

                        if (resampler == null)
                        {
                            buf = remainder;
                            bufChannels = srcChannels;
                            bufFrameSize = srcFrameSize;
                            bufLength = remainderLength;
                            bufSampleSize = srcSampleSize;
                        }
                        else
                        {
                            maybeResample(numFramesRequested);

                            buf = (byte[])resamplerOutBuffer.getData();
                            bufChannels = resamplerChannels;
                            bufFrameSize = resamplerFrameSize;
                            bufLength = resamplerOutBuffer.getLength();
                            bufSampleSize = resamplerSampleSize;
                        }

                        /*
                         * Write as much from buf as possible while minimizing
                         * the risk of audio glitches and the amount of
                         * artificial/induced silence.
                         */
                        int bufFrames = bufLength / bufFrameSize;

                        if ((numFramesRequested > bufFrames) &&
                            (bufFrames >= devicePeriodInFrames))
                        {
                            numFramesRequested = bufFrames;
                        }

                        // Pad with silence in order to avoid underflows.
                        int toWrite = numFramesRequested * bufFrameSize;

                        if (toWrite > buf.length)
                        {
                            toWrite = buf.length;
                        }

                        int silence = toWrite - bufLength;

                        if (silence > 0)
                        {
                            Arrays.fill(buf, bufLength, toWrite, (byte) 0);
                            bufLength = toWrite;
                        }

                        /*
                         * Take into account the user's preferences with respect
                         * to the output volume.
                         */
                        GainControl gainControl = getGainControl();

                        if ((gainControl != null) && (toWrite != 0))
                        {
                            BasicVolumeControl.applyGain(
                                    gainControl,
                                    buf, 0, toWrite);
                        }

                        int written = maybeIAudioRenderClientWrite(
                                    buf, 0, toWrite,
                                    bufSampleSize, bufChannels);
                        Log.logReceivedBytes(this, written);

                        if (written != 0)
                        {
                            bufLength = pop(buf, bufLength, written);

                            if (buf == remainder)
                            {
                                remainderLength = bufLength;
                            }
                            else
                            {
                                resamplerOutBuffer.setLength(bufLength);
                            }

                            if (writeIsMalfunctioningSince
                                    != DiagnosticsControl.NEVER)
                            {
                                setWriteIsMalfunctioning(MalfunctionState.FUNCTIONING_CORRECTLY);
                            }
                        }
                    }
                }
                finally
                {
                    synchronized (this)
                    {
                        busy = false;
                        notifyAll();
                    }
                }

                int wfso;

                try
                {
                    wfso = WaitForSingleObject(eventHandle, devicePeriod);
                }
                catch (HResultException hre)
                {
                    /*
                     * WaitForSingleObject will throw HResultException only in
                     * the case of WAIT_FAILED. Event if it didn't, it would
                     * still be a failure from our point of view.
                     */
                    wfso = WAIT_FAILED;
                    logger.error("WaitForSingleObject", hre);
                }
                /*
                 * If the function WaitForSingleObject fails once, it will very
                 * likely fail forever. Bail out of a possible busy wait.
                 */
                if ((wfso == WAIT_FAILED) || (wfso == WAIT_ABANDONED))
                {
                    break;
                }
            }
            while (true);
        }
        finally
        {
            synchronized (this)
            {
                if (eventHandleCmd.equals(this.eventHandleCmd))
                {
                    this.eventHandleCmd = null;
                    notifyAll();
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * Disallows mid-stream changes of the <tt>inputFormat</tt> of this
     * <tt>AbstractRenderer</tt>.
     */
    @Override
    public synchronized Format setInputFormat(Format format)
    {
        /*
         * WASAPIRenderer does not support mid-stream changes of the
         * inputFormat.
         */
        if ((iAudioClient != 0) || (iAudioRenderClient != 0))
        {
            logger.warn("Attempted to set input format of renderer mid-stream");
            return null;
        }
        else
        {
            return super.setInputFormat(format);
        }
    }

    /**
     * Indicates whether the writing to the render endpoint buffer is
     * malfunctioning. Keeps track of the time at which the malfunction has
     * started.
     *
     * @param state The cause of a malfunction, <tt>FUNCTIONING_CORRECTLY</tt> otherwise.
     */
    private void setWriteIsMalfunctioning(MalfunctionState state)
    {
        malfunctionState = state;

        if (!MalfunctionState.FUNCTIONING_CORRECTLY.equals(state))
        {
            if (writeIsMalfunctioningSince == DiagnosticsControl.NEVER)
            {
                writeIsMalfunctioningSince = System.currentTimeMillis();
                WASAPISystem.monitorFunctionalHealth(diagnosticsControl);
            }
        }
        else
        {
            writeIsMalfunctioningSince = DiagnosticsControl.NEVER;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void start()
    {
        Log.logMediaStackObjectStarted(this);
        if (iAudioClient == 0)
        {
            /*
             * We actually want to allow the user to switch the playback and/or
             * notify device to none mid-stream in order to disable the
             * playback.
             */
            if (locatorIsNull)
            {
                started = true;
            }
        }
        else
        {
            logger.debug("About to wait while busy");
            waitWhileBusy();
            logger.debug("Finished wait while busy");
            waitWhileEventHandleCmd();
            logger.debug("Finished wait while event handle cmd");

            /*
             * Introduce latency in order to decrease the likelihood of
             * underflow.
             */
            if (remainder != null)
            {
                if (remainderLength > 0)
                {
                    /*
                     * Shift the valid audio data to the end of remainder so
                     * that silence can be written at the beginning.
                     */
                    for (int i = remainder.length - 1, j = remainderLength - 1;
                            j >= 0;
                            i--, j--)
                    {
                        remainder[i] = remainder[j];
                    }
                }
                else if (remainderLength < 0)
                {
                    remainderLength = 0;
                }

                /*
                 * If there is valid audio data in remainder, it has been
                 * shifted to the end to make room for silence at the beginning.
                 */
                int silence = remainder.length - remainderLength;

                if (silence > 0)
                {
                    Arrays.fill(remainder, 0, silence, (byte) 0);
                }
                remainderLength = remainder.length;
            }

            try
            {
                logger.debug("About to start audio client");
                IAudioClient_Start(iAudioClient);
                logger.debug("Started audio client");
                started = true;

                if ((eventHandle != 0) && (this.eventHandleCmd == null))
                {
                    Runnable eventHandleCmd
                        = new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                runInEventHandleCmd(this);
                            }
                        };
                    boolean submitted = false;

                    try
                    {
                        if (eventHandleExecutor == null)
                        {
                            NamedThreadFactory factory =
                                   new NamedThreadFactory("WasapiRendererPool");
                            eventHandleExecutor =
                                     Executors.newSingleThreadExecutor(factory);
                        }

                        this.eventHandleCmd = eventHandleCmd;
                        eventHandleExecutor.execute(eventHandleCmd);
                        submitted = true;
                    }
                    finally
                    {
                        if (!submitted
                                && eventHandleCmd.equals(this.eventHandleCmd))
                        {
                            this.eventHandleCmd = null;
                        }
                    }
                }
            }
            catch (HResultException hre)
            {
                /*
                 * If IAudioClient_Start is invoked multiple times without
                 * intervening IAudioClient_Stop, it will likely return/throw
                 * AUDCLNT_E_NOT_STOPPED.
                 */
                if (hre.getHResult() != AUDCLNT_E_NOT_STOPPED)
                {
                    logger.error("IAudioClient_Start", hre);
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void stop()
    {
        eventHandleExecutor = null;

        Log.logMediaStackObjectStopped(this);
        setWriteIsMalfunctioning(MalfunctionState.FUNCTIONING_CORRECTLY);
        if (iAudioClient == 0)
        {
            /*
             * We actually want to allow the user to switch the playback and/or
             * notify device to none mid-stream in order to disable the
             * playback.
             */
            if (locatorIsNull)
            {
                started = false;
            }
        }
        else
        {
            waitWhileBusy();

            try
            {
                stopIAudioClient(iAudioClient);
                started = false;
            }
            catch (HResultException hre)
            {
                logger.error("IAudioClient_Stop", hre);
            }

            waitWhileEventHandleCmd();
        }
    }

    /**
     * Gets a human-readable representation of a specific <tt>MediaLocator</tt>
     * for the purposes of testing/debugging.
     *
     * @param locator the <tt>MediaLocator</tt> that is to be represented in a
     * human-readable form for the purposes of testing/debugging
     * @return a human-readable representation of the specified <tt>locator</tt>
     * for the purposes of testing/debugging
     */
    private String toString(MediaLocator locator)
    {
        String s;

        if (locator == null)
        {
            s = "null";
        }
        else
        {
            s = null;
            /*
             * Try to not throw any exceptions because the purpose is to produce
             * at least some identification of the specified MediaLocator even
             * if not the most complete.
             */
            try
            {
                String id = locator.getRemainder();

                if (id != null)
                {
                    Device cdi2
                        = audioSystem.getDevice(dataFlow, locator);

                    if (cdi2 != null)
                    {
                        String name = cdi2.getName();

                        if ((name != null) && !id.equals(name))
                        {
                            s = id + " with friendly name " + name;
                        }
                    }
                    if (s == null)
                    {
                        s = id;
                    }
                }
            }
            catch (Throwable t)
            {
                if (t instanceof ThreadDeath)
                {
                    throw (ThreadDeath) t;
                }
            }
            if (s == null)
            {
                s = locator.toString();
            }
        }
        return s;
    }

    /**
     * Waits on this instance while the value of {@link #busy} is equal to
     * <tt>true</tt>.
     */
    private synchronized void waitWhileBusy()
    {
        boolean interrupted = false;

        while (busy)
        {
            try
            {
                wait(devicePeriod);
            }
            catch (InterruptedException ie)
            {
                interrupted = true;
            }
        }
        if (interrupted)
        {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Waits on this instance while the value of {@link #eventHandleCmd} is
     * non-<tt>null</tt>.
     */
    private synchronized void waitWhileEventHandleCmd()
    {
        if (eventHandle == 0)
        {
            throw new IllegalStateException("eventHandle");
        }

        boolean interrupted = false;

        while (eventHandleCmd != null)
        {
            try
            {
                wait(devicePeriod);
            }
            catch (InterruptedException ie)
            {
                interrupted = true;
            }
        }
        if (interrupted)
        {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    protected void finalize()
    {
        // We should have closed this renderer by now but try to do so here
        // just in case.
        logger.debug("Called dispose on WASAPI renderer " + this.hashCode());
        close();
    }
}
