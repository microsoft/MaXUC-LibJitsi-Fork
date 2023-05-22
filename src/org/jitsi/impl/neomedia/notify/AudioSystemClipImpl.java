/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
// Portions (c) Microsoft Corporation. All rights reserved.
package org.jitsi.impl.neomedia.notify;

import java.io.*;
import java.net.*;
import java.util.*;

import javax.media.*;
import javax.media.format.*;

import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.codec.audio.speex.*;
import org.jitsi.impl.neomedia.device.*;
import org.jitsi.service.audionotifier.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.util.*;

/**
 * Implementation of SCAudioClip using PortAudio.
 *
 * @author Damyian Minkov
 * @author Lyubomir Marinov
 */
public class AudioSystemClipImpl
    extends AbstractSCAudioClip
{
    /**
     * The default length of {@link #bufferData}.
     */
    private static final int DEFAULT_BUFFER_DATA_LENGTH = 8 * 1024;

    /**
     * We have seen problems where it can take too long to pass a buffer to the native renderer.
     * This could indicate the native renderer is lagging.  When the native renderer's buffer gets
     * full then we wait until it empties so we can pass the buffer along.  Therefore, the maximum
     * amount of time until we can pass the buffer along is correlated to the size of the buffer.
     * We therefore wait bufferLength + RENDER_TIME_SAFETY_MARGIN for the buffer to be passed along,
     * before tearing down the render process.
     *
     * Note: this has the possible side effect that an audio clip may stop
     * being played if the device is swapped part-way through (and rebuilding
     * the renderer takes a while) but that's acceptable.
     */
    private static final long RENDER_TIME_SAFETY_MARGIN = 500;

    /**
     * The <tt>Logger</tt> used by the <tt>AudioSystemClipImpl</tt> class and
     * its instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(AudioSystemClipImpl.class);

    private final AudioSystem audioSystem;

    private Buffer buffer;

    private byte[] bufferData;

    private final boolean playback;

    private Renderer renderer;

    /**
     * Creates the audio clip and initializes the listener used from the
     * loop timer.
     *
     * @param url the URL pointing to the audio file
     * @param audioNotifier the audio notify service
     * @param playback to use playback or notification device
     */
    public AudioSystemClipImpl(
            String url,
            AudioNotifierService audioNotifier,
            AudioSystem audioSystem,
            boolean playback)
    {
        super(url, audioNotifier);

        this.audioSystem = audioSystem;
        this.playback = playback;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void enterRunInPlayThread()
    {
        logger.debug("Enter run in play thread called");
        buffer = new Buffer();
        bufferData = new byte[DEFAULT_BUFFER_DATA_LENGTH];
        buffer.setData(bufferData);

        // If the renderer differentiates between blocking and non-blocking behaviour, choose blocking behaviour
        // for playback of an audio clip.
        renderer = audioSystem.createRenderer(playback, true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void exitRunInPlayThread()
    {
        logger.debug("Exit run in play thread called");
        buffer = null;
        bufferData = null;
        renderer = null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void exitRunOnceInPlayThread()
    {
        logger.debug("Exit run once in play thread called");
        if (renderer != null)
        {
            try
            {
                renderer.stop();
            }
            finally
            {
                renderer.close();
            }
        }
    }

    @Override
    public boolean testRender()
    {
        buffer = new Buffer();
        bufferData = new byte[DEFAULT_BUFFER_DATA_LENGTH];
        buffer.setData(bufferData);

        // Use a temporary renderer, with playback disabled, since we're only
        // rendering to check validity rather than to actually produce audio.
        Renderer tempRenderer = audioSystem.createRenderer(false);

        // Unless the renderer explicitly tells us we succeeded, then we failed.
        boolean success = false;

        success = renderAudio(tempRenderer, false);
        if (success)
        {
            logger.debug("Audio " + this + " is valid.");
        }
        else
        {
            logger.debug("Audio " + this + " is invalid.");
            logAudioDataInNewThread();
        }

        tempRenderer.close();

        return success;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean runOnceInPlayThread()
    {
        logger.debug("Run once in play thread called");

        boolean success = renderAudio(renderer, true);

        if (!success)
            logAudioDataInNewThread();

        return success;
    }

    /**
     * Renders audio from the file at the given URI.
     *
     * @param renderer The renderer to use for rendering audio.
     * @param notifyListeners Whether to notify listeners when rendering starts
     * and ends.
     * @return <tt>true</tt> if the rendering was successful, <tt>false</tt>
     * otherwise.
     * @throws IOException If the file is not accessible, or is invalid.
     * @throws ResourceUnavailableException If the resampler or renderer is not
     * available.
     */
    public boolean renderAudio(Renderer renderer, boolean notifyListeners)
    {
        InputStream audioStream = null;
        logger.debug("Rendering audio " + uri + "...");

        try
        {
            audioStream = audioSystem.getAudioInputStream(uri);
        }
        catch (IOException ioex)
        {
            logger.error("Failed to get audio stream " + uri, ioex);
            return false;
        }

        if (audioStream == null)
        {
            logger.error("Audio stream " + uri + " unexpectedly null.");
            return false;
        }

        Codec resampler = null;
        boolean success = true;
        long rendererProcessStartTime = 0;

        try
        {
            Format rendererFormat = audioSystem.getFormat(audioStream);

            if (rendererFormat == null)
            {
                logger.error("Renderer format was unexpectedly null for " +
                             uri);
                return false;
            }
            else if (renderer == null)
            {
                logger.error("Renderer was unexpectedly null for " + uri);
                return false;
            }

            double sampleRate = ((AudioFormat)rendererFormat).getSampleRate();
            if (sampleRate > MediaUtils.MAX_AUDIO_SAMPLE_RATE)
            {
                logger.error("The audio data at " + uri + " has a sample rate "+
                             "of " + sampleRate + "Hz, exceeding the maximum " +
                             "of " + MediaUtils.MAX_AUDIO_SAMPLE_RATE + "Hz," +
                             " and so was not rendered.");
                return false;
            }

            Format resamplerFormat = null;

            if (renderer.setInputFormat(rendererFormat) == null)
            {
                /*
                 * Try to negotiate a resampling of the audioStream to one of
                 * the formats supported by the renderer.
                 */
                resampler = new SpeexResampler();
                resamplerFormat = rendererFormat;
                resampler.setInputFormat(resamplerFormat);

                Format[] supportedResamplerFormats
                    = resampler.getSupportedOutputFormats(resamplerFormat);

                Format[] supportedRendererFormats
                    = renderer.getSupportedInputFormats();

                logger.debug("Attempting to negotiate resampling of the audio " +
                             "stream to match the renderer." +
                             "\nRenderer formats: " +
                             Arrays.toString(supportedRendererFormats) +
                             "\nResampler formats: " +
                             Arrays.toString(supportedResamplerFormats));

                boolean isFormatSet = false;
                for (Format supportedRendererFormat : supportedRendererFormats)
                {
                    for (Format supportedResamplerFormat
                            : supportedResamplerFormats)
                    {
                        if (supportedRendererFormat.matches(
                                supportedResamplerFormat))
                        {
                            rendererFormat = supportedRendererFormat;
                            if (renderer.setInputFormat(rendererFormat) != null &&
                                resampler.setOutputFormat(rendererFormat) != null)
                            {
                                logger.debug("Set renderer and resampler formats" +
                                             " to " + rendererFormat);
                                isFormatSet = true;
                                break;
                            }
                            else
                            {
                                logger.debug("Failed to set renderer and " +
                                      "resampler formats to " + rendererFormat);
                            }
                        }
                    }

                    if (isFormatSet)
                    {
                        // If we have managed to set the formats on the renderer
                        // and resampler then we should break out here.
                        break;
                    }
                }

                if (!isFormatSet)
                {
                    logger.warn("Failed to find resampler to match the renderer." +
                                "\nRenderer formats: " +
                                Arrays.toString(supportedRendererFormats) +
                                "\nResampler formats: " +
                                Arrays.toString(supportedResamplerFormats));
                }
            }

            logger.debug("Renderer format set as: " + rendererFormat);

            if (buffer == null)
            {
                logger.error("Audio buffer was not initialized for " + uri + "," +
                             " so audio could not be rendered.");
                return false;
            }

            Buffer rendererBuffer = buffer;
            Buffer resamplerBuffer;

            rendererBuffer.setFormat(rendererFormat);
            if (resampler == null)
                resamplerBuffer = null;
            else
            {
                resamplerBuffer = new Buffer();

                int bufferDataLength = DEFAULT_BUFFER_DATA_LENGTH;

                if (resamplerFormat instanceof AudioFormat)
                {
                    AudioFormat af = (AudioFormat) resamplerFormat;
                    int frameSize
                        = (af.getSampleSizeInBits() / 8) * af.getChannels();

                    // Ensure the buffer is still an integer number of frames
                    bufferDataLength -= bufferDataLength % frameSize;
                }

                bufferData = new byte[bufferDataLength];
                resamplerBuffer.setData(bufferData);
                resamplerBuffer.setFormat(resamplerFormat);
                resampler.open();
            }

            try
            {
                // Open and start the renderer here but note that we don't
                // close and stop it - that is done in
                // exitRunOnceInPlayThread().
                logger.debug("Opening renderer");
                renderer.open();
                logger.debug("Opened renderer");

                renderer.start();

                if (notifyListeners)
                {
                    logger.debug("Firing audio-started event.");
                    fireAudioStartedEvent();
                }

                int bufferLength;
                boolean renderTookTooLong = false;

                while (isStarted() &&
                       ((bufferLength = audioStream.read(bufferData)) != -1) &&
                       !renderTookTooLong)
                {
                    if (resampler == null)
                    {
                        rendererBuffer.setLength(bufferLength);
                        rendererBuffer.setOffset(0);
                    }
                    else
                    {
                        resamplerBuffer.setLength(bufferLength);
                        resamplerBuffer.setOffset(0);
                        rendererBuffer.setLength(0);
                        rendererBuffer.setOffset(0);
                        resampler.process(resamplerBuffer, rendererBuffer);
                    }

                    int rendererProcess;

                    // Work out how long it SHOULD take to render this buffer
                    int bufferLengthMillis =
                                     (int)((bufferLength * 1000L) / sampleRate);

                    // We're about to pass the buffer to the native renderer.  We have seen hangs
                    // inside the renderer so put in a fail-safe here by checking that we don't take
                    // too long to pass the buffer along.
                    rendererProcessStartTime = System.currentTimeMillis();
                    do
                    {
                        rendererProcess = renderer.process(rendererBuffer);
                        if (rendererProcess == Renderer.BUFFER_PROCESSED_FAILED)
                        {
                            logger.error("Failed to render audio stream " + uri);

                            return false;
                        }

                        long timeSpendRenderingSoFar = System.currentTimeMillis() - rendererProcessStartTime;

                        if (timeSpendRenderingSoFar > RENDER_TIME_SAFETY_MARGIN + bufferLengthMillis)
                        {
                            logger.error("Failed to complete rendering in time: " + bufferLengthMillis + " ms ( " + bufferLength +
                                " * 1000 / " + sampleRate + ") buffer not rendered in " + timeSpendRenderingSoFar + " ms");
                            renderTookTooLong = true;
                            break;
                        }
                    }
                    while ((rendererProcess & Renderer.INPUT_BUFFER_NOT_CONSUMED) ==
                            Renderer.INPUT_BUFFER_NOT_CONSUMED);
                }
            }
            catch (IOException ioex)
            {
                logger.error("Failed to read from audio stream " + uri, ioex);
                return false;
            }
            catch (ResourceUnavailableException ruex)
            {
                logger.error("Failed to open "+renderer.getClass().getName(),
                             ruex);
                return false;
            }
        }
        catch (ResourceUnavailableException ruex)
        {
            if (resampler != null)
            {
                logger.error("Failed to open "+resampler.getClass().getName(),
                             ruex);
                return false;
            }
        }
        finally
        {
            try
            {
                audioStream.close();
            }
            catch (IOException ioex)
            {
                // The audio stream failed to close but it doesn't mean the URL
                // will fail to open again so just log the exception.
                logger.info("Hit IOException attempting to close file " + ioex);
            }

            if (resampler != null)
            {
                resampler.close();
            }

            if (notifyListeners)
            {
                logger.debug("Firing audio-ended event.");
                fireAudioEndedEvent();
            }
        }

        return success;
    }

    /**
     * Logs an extract from the start and end of the audio file, for debugging
     * purposes. Works in its own thread.
     */
    private void logAudioDataInNewThread()
    {
        new Thread("AudioFileLogger")
        {
            @Override
            public void run()
            {
                logAudioData();
            }
        }.start();
    }

    /**
     * Logs an extract from the start and end of the audio file, for debugging
     * purposes.
     */
    private void logAudioData()
    {
        URL audioUrl = LibJitsi.getResourceManagementService().
                                                        getSoundURLForPath(uri);
        if (audioUrl == null)
        {
            try
            {
                audioUrl = new URL(uri);
            }
            catch (MalformedURLException e)
            {
                logger.error("Could not log audio data: uri " + uri +
                             " was invalid.", e);
                return;
            }
        }

        InputStreamReader audioStream;
        try
        {
            audioStream = new InputStreamReader(audioUrl.openStream());
        }
        catch (IOException ioex)
        {
            logger.error("Could not log audio data: " +
                         "failed to get audio stream " + uri, ioex);
            return;
        }

        int maxHeaderSize = 50;

        // Obtain the first n bytes of the file for debugging.
        int[] header = new int[maxHeaderSize];
        int idx;
        for (idx = 0; idx < maxHeaderSize; idx++)
        {
            try
            {
                int nextByte = audioStream.read();

                if (nextByte == -1)
                {
                    break;
                }

                header[idx] = nextByte;
            }
            catch (IOException e)
            {
                logger.error("Tried to log audio data, but failed to read " +
                        " from stream.", e);
                return;
            }
        }

        logger.error("Failed to play audio file at " + uri + " started with:\n"+
                     Arrays.toString(header));

        LimitedQueue<Integer> lastFewBytes =
                new LimitedQueue<>(maxHeaderSize);

        // Spin through the rest of the file, maintaining a List of the last
        // n bytes, so that when the file ends we can output the last n bytes
        // in the file for debugging.
        while (idx > 0)
        {
            try
            {
                int nextByte = audioStream.read();

                if (nextByte == -1)
                    break;

                lastFewBytes.add(nextByte);
                idx += 1;
            }
            catch (IOException e)
            {
                logger.error("Tried to log audio data, but failed to read " +
                             " from stream.");
                return;
            }
        }

        logger.error("Invalid audio file at " + uri + " ended with:\n"+
                     lastFewBytes.toString());
    }

    /**
     * Implementation of List with a fixed capacity, which discards the oldest
     * element from the list when a new element is added and the list is already
     * at capacity.
     *
     * @param <E> The type of objects in the list.
     */
    private static class LimitedQueue<E> extends LinkedList<E>
    {
        private static final long serialVersionUID = 0L;

        private final int limit;

        public LimitedQueue(int limit)
        {
            this.limit = limit;
        }

        @Override
        public boolean add(E o)
        {
            super.add(o);

            while (size() > limit)
                remove();

            return true;
        }
    }
}
