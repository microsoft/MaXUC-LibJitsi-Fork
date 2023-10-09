/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.codec.video.h264;

import static org.bytedeco.ffmpeg.avcodec.AVCodecContext.*;
import static org.bytedeco.ffmpeg.global.avcodec.*;
import static org.bytedeco.ffmpeg.global.avutil.*;

import java.awt.Dimension;

import javax.media.Buffer;
import javax.media.Format;
import javax.media.ResourceUnavailableException;
import javax.media.format.VideoFormat;

import org.bytedeco.ffmpeg.avcodec.AVCodec;
import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.javacpp.BytePointer;
import org.jitsi.impl.neomedia.codec.AbstractCodec2;
import org.jitsi.impl.neomedia.codec.video.AVFrameFormat;
import org.jitsi.impl.neomedia.codec.video.AVFrameWrapper;
import org.jitsi.service.neomedia.codec.Constants;
import org.jitsi.service.neomedia.control.KeyFrameControl;
import org.jitsi.util.Logger;

import net.sf.fmj.media.AbstractCodec;

/**
 * Decodes H.264 NAL units and returns the resulting frames as FFmpeg
 * <tt>AVFrame</tt>s (i.e. in YUV format).
 *
 * @author Damian Minkov
 * @author Lyubomir Marinov
 * @author Sebastien Vincent
 */
public class JNIDecoder
    extends AbstractCodec
{
    private static final Logger logger = Logger.getLogger(JNIDecoder.class);

    /**
     * The default output <tt>VideoFormat</tt>.
     */
    private static final VideoFormat[] DEFAULT_OUTPUT_FORMATS
        = new VideoFormat[] { new AVFrameFormat(AV_PIX_FMT_YUV420P) };

    /**
     * Plugin name.
     */
    private static final String PLUGIN_NAME = "H.264 Decoder";

    /**
     *  The codec context native pointer we will use.
     */
    private AVCodecContext avctx;

    /**
     * The <tt>AVFrame</tt> in which the video frame decoded from the encoded
     * media data is stored.
     */
    private AVFrameWrapper avframe;

    private boolean gotPictureAtLeastOnce;

    /**
     * The last known height of {@link #avctx} i.e. the video output by this
     * <tt>JNIDecoder</tt>. Used to detect changes in the output size.
     */
    private int height;

    /** Max number of 0-width 0-height keyframes before flushing the buffer to avoid video stream freeze */
    private static final int FLUSH_FRAME_COUNT_LIMIT = 60;
    // Other variables used in detecting video freeze condition. See method needBufferFlush
    private int flushCounter = 0;
    private int emptyFrames = 0;
    private int recentlyFlushed = 0;

    /**
     * The <tt>KeyFrameControl</tt> used by this <tt>JNIDecoder</tt> to
     * control its key frame-related logic.
     */
    private KeyFrameControl keyFrameControl;

    /**
     * Array of output <tt>VideoFormat</tt>s.
     */
    private final VideoFormat[] outputFormats;

    /**
     * The last known width of {@link #avctx} i.e. the video output by this
     * <tt>JNIDecoder</tt>. Used to detect changes in the output size.
     */
    private int width;

    /**
     * Initializes a new <tt>JNIDecoder</tt> instance which is to decode H.264
     * NAL units into frames in YUV format.
     */
    public JNIDecoder()
    {
        inputFormats = new VideoFormat[] { new VideoFormat(Constants.H264) };
        outputFormats = DEFAULT_OUTPUT_FORMATS;
    }

    /**
     * Check <tt>Format</tt>.
     *
     * @param format <tt>Format</tt> to check
     * @return true if <tt>Format</tt> is H264_RTP
     */
    public boolean checkFormat(Format format)
    {
        return format.getEncoding().equals(Constants.H264_RTP);
    }

    /**
     * Close <tt>Codec</tt>.
     */
    @Override
    public synchronized void close()
    {
        if (opened)
        {
            opened = false;
            super.close();

            avcodec_close(avctx);
            av_free(avctx);
            avctx = null;

            if (avframe != null)
            {
                avframe.free();
                avframe = null;
            }

            gotPictureAtLeastOnce = false;
        }
    }

    /**
     * Ensure frame rate.
     *
     * @param frameRate frame rate
     * @return frame rate
     */
    private float ensureFrameRate(float frameRate)
    {
        return frameRate;
    }

    /**
     * Get matching outputs for a specified input <tt>Format</tt>.
     *
     * @param inputFormat input <tt>Format</tt>
     * @return array of matching outputs or null if there are no matching
     * outputs.
     */
    protected Format[] getMatchingOutputFormats(Format inputFormat)
    {
        VideoFormat inputVideoFormat = (VideoFormat) inputFormat;

        return
            new Format[]
            {
                new AVFrameFormat(
                        inputVideoFormat.getSize(),
                        ensureFrameRate(inputVideoFormat.getFrameRate()),
                        AV_PIX_FMT_YUV420P)
            };
    }

    /**
     * Get plugin name.
     *
     * @return "H.264 Decoder"
     */
    @Override
    public String getName()
    {
        return PLUGIN_NAME;
    }

    /**
     * Get all supported output <tt>Format</tt>s.
     *
     * @param inputFormat input <tt>Format</tt> to determine corresponding
     * output <tt>Format/tt>s
     * @return an array of supported output <tt>Format</tt>s
     */
    @Override
    public Format[] getSupportedOutputFormats(Format inputFormat)
    {
        Format[] supportedOutputFormats;

        if (inputFormat == null)
            supportedOutputFormats = outputFormats;
        else
        {
            // mismatch input format
            if (!(inputFormat instanceof VideoFormat)
                    || (AbstractCodec2.matches(inputFormat, inputFormats)
                            == null))
                supportedOutputFormats = new Format[0];
            else
            {
                // match input format
                supportedOutputFormats = getMatchingOutputFormats(inputFormat);
            }
        }
        return supportedOutputFormats;
    }

    private boolean needBufferFlush(AVFrameWrapper avframe)
    {
        // There's a bug we hit in FFmpeg where the video stream decoding freezes, despite a continuing
        // input stream. We believe this is likely https://trac.ffmpeg.org/ticket/6815, and unlikely to be fixed
        //
        // The video freeze bug presents as the frame returned always being a parial keyframe.
        // Width, height are 0, Keyframe is 1, and all other values set to probably default.
        // As such, we can't detect the error specifically. However, in normal operation, we only expect
        // a certain number of keyframe frames before the next set of intermediate frames, which do have
        // width and height set. Keep a tally of how long we've been in 0 frame land, and return true if
        // we have seen the error trace for a sustained period. From testing between Mac and Windows client,
        // the limit is set to 60, but this may need further tweaking.

        final AVFrame frame = avframe.getNativeFrame();
        if (frame.width() == 0 && frame.height() == 0)
        {
            emptyFrames++;
        }
        else
        {
            emptyFrames = 0;
        }

        return emptyFrames > FLUSH_FRAME_COUNT_LIMIT;
    }

    /**
     * Decode a video frame.
     *
     * @param src input buffer
     * @param srcLength input buffer size
     * @return number of bytes written to buffer if success
     */
    private synchronized int decodeVideo(byte[] src, int srcLength)
    {
        // Set up packet data buffer
        final long packetBufferSize = srcLength + AV_INPUT_BUFFER_PADDING_SIZE;
        int returnCode;
        try (BytePointer packetBuffer = new BytePointer(av_malloc(packetBufferSize)).limit(packetBufferSize)) {
            // Note: put() requires that we set the limit on the BytePointer.
            packetBuffer.put(src, 0, srcLength);
            final AVPacket pkt = av_packet_alloc();
            av_packet_from_data(pkt, packetBuffer, srcLength);
            returnCode = avcodec_send_packet(avctx, pkt);
            av_packet_free(pkt);
        }

        if (returnCode == 0)
        {
            // Receive packet (decode into frame)
            returnCode = avcodec_receive_frame(avctx, avframe.getNativeFrame());

            // We can end up in an errored state, and need to flush buffers to reset out of it. See needBufferFlush method comment for details
            if (needBufferFlush(avframe))
            {
                // Check if we have recently flushed the buffer to clear this issue. It takes a bit of time after the flush for the
                // first full keyframe to complete, after which the method above can detect all is OK and stop returning true. Implementing
                // a basic buffering behaviour to stop us getting stuck in a flushing loop.
                if (recentlyFlushed == 0)
                {
                    recentlyFlushed++;
                    flushCounter++;
                    logger.error("Detected video freeze error condition; flushing decoder buffers. Flush count: " + flushCounter);
                    avcodec_flush_buffers(avctx);
                }
                else if (recentlyFlushed++ > 100)
                {
                    logger.warn("Wait period after decoder buffer flush complete, but still detecting error condition. Reseting counter so flush occurs next cycle.");
                    recentlyFlushed = 0;
                }
                else
                {
                    logger.debug("Detected potential video freeze error condition, but recently flushed decoder buffers, so taking no action. Counter at " + recentlyFlushed);
                }
            }
            else if (recentlyFlushed > 0)
            {
                // Reset the counter if we've seen a good set of decoded frames, so we don't have to wait on next detecting it.
                logger.warn("Detected healthy video decoding after error condition. Reseting error backoff counter for next time issue hits.");
                recentlyFlushed = 0;
            }
        }

        // We expect to see returnCodes:
        //   0  : Success
        //  -11 : EAGAIN (Windows)
        //  -35 : EAGAIN (Mac)
        // For anything else, log the error code out.
        if (returnCode != 0 && returnCode != -11 && returnCode != -35)
        {
            logger.error("Unexpected return code from decoding video: " + returnCode);
        }

        return returnCode;
    }

    /**
     * Inits the codec instances.
     *
     * @throws ResourceUnavailableException if codec initialization failed
     */
    @Override
    public synchronized void open()
        throws ResourceUnavailableException
    {
        if (opened)
            return;

        if (avframe != null)
        {
            avframe.free();
            avframe = null;
        }
        avframe = new AVFrameWrapper();

        AVCodec avcodec = avcodec_find_decoder(AV_CODEC_ID_H264);

        avctx = avcodec_alloc_context3(avcodec);
        avctx.workaround_bugs(FF_BUG_AUTODETECT);

        // Allows us to pass incomplete frames to the decoder
        // Without this option, we would have to re-compile full frames
        // from received packets before passing them in for decoding.
        // Disabling this option results in corrupt video output.
        avctx.flags2(avctx.flags2() | AV_CODEC_FLAG2_CHUNKS);

        if (avcodec_open2(avctx, avcodec, (AVDictionary) null) < 0)
            throw new RuntimeException("Could not open codec CODEC_ID_H264");

        gotPictureAtLeastOnce = false;

        opened = true;
        super.open();
    }

    /**
     * Decodes H.264 media data read from a specific input <tt>Buffer</tt> into
     * a specific output <tt>Buffer</tt>.
     *
     * @param in input <tt>Buffer</tt>
     * @param out output <tt>Buffer</tt>
     * @return <tt>BUFFER_PROCESSED_OK</tt> if <tt>in</tt> has been successfully
     * processed
     */
    @Override
    public synchronized int process(Buffer in, Buffer out)
    {
        if (!checkInputBuffer(in))
        {
            logger.debug("Input buffer failed basic checks, unable to process");
            return BUFFER_PROCESSED_FAILED;
        }
        // Check for End of Media events, or to see if the codec hasn't been opened
        if (isEOM(in) || !opened)
        {
            propagateEOM(out);
            logger.debug("Input buffer was EOM, End of Media, or codec not opened, so doing nothing");
            return BUFFER_PROCESSED_OK;
        }
        if (in.isDiscard())
        {
            logger.debug("Input buffer isDiscard() was true, so doing nothing");
            out.setDiscard(true);
            return BUFFER_PROCESSED_OK;
        }

        // Returns 0 if we were able to successfully pass the input data packet to FFmpeg, and
        // receive a decoded video frame back. Other return codes can indicate error cases, with
        // unexpected errors logged at ERROR level
        int returnCode = decodeVideo((byte[]) in.getData(),
                                     in.getLength());

        if (returnCode != 0)
        {
            if ((in.getFlags() & Buffer.FLAG_RTP_MARKER) != 0)
            {
                if (keyFrameControl != null)
                    keyFrameControl.requestKeyFrame(!gotPictureAtLeastOnce);
            }

            out.setDiscard(true);
            return BUFFER_PROCESSED_OK;
        }
        gotPictureAtLeastOnce = true;

        // format
        int width = avctx.width();
        int height = avctx.height();

        if ((width > 0)
                && (height > 0)
                && ((this.width != width) || (this.height != height)))
        {
            this.width = width;
            this.height = height;

            // Output in same size and frame rate as input.
            Dimension outSize = new Dimension(this.width, this.height);
            VideoFormat inFormat = (VideoFormat) in.getFormat();
            float outFrameRate = ensureFrameRate(inFormat.getFrameRate());

            outputFormat
                = new AVFrameFormat(
                        outSize,
                        outFrameRate,
                        AV_PIX_FMT_YUV420P);
        }
        out.setFormat(outputFormat);

        if (out.getData() != avframe)
            out.setData(avframe);

        // timeStamp
        long pts = AV_NOPTS_VALUE; // TODO avframe_get_pts(avframe);

        if (pts == AV_NOPTS_VALUE)
            out.setTimeStamp(Buffer.TIME_UNKNOWN);
        else
        {
            out.setTimeStamp(pts);

            int outFlags = out.getFlags();

            outFlags |= Buffer.FLAG_RELATIVE_TIME;
            outFlags &= ~(Buffer.FLAG_RTP_TIME | Buffer.FLAG_SYSTEM_TIME);
            out.setFlags(outFlags);
        }

        return BUFFER_PROCESSED_OK;
    }

    /**
     * Sets the <tt>Format</tt> of the media data to be input for processing in
     * this <tt>Codec</tt>.
     *
     * @param format the <tt>Format</tt> of the media data to be input for
     * processing in this <tt>Codec</tt>
     * @return the <tt>Format</tt> of the media data to be input for processing
     * in this <tt>Codec</tt> if <tt>format</tt> is compatible with this
     * <tt>Codec</tt>; otherwise, <tt>null</tt>
     */
    @Override
    public Format setInputFormat(Format format)
    {
        Format setFormat = super.setInputFormat(format);

        if (setFormat != null)
            reset();
        return setFormat;
    }

    /**
     * Sets the <tt>KeyFrameControl</tt> to be used by this
     * <tt>DePacketizer</tt> as a means of control over its key frame-related
     * logic.
     *
     * @param keyFrameControl the <tt>KeyFrameControl</tt> to be used by this
     * <tt>DePacketizer</tt> as a means of control over its key frame-related
     * logic
     */
    public void setKeyFrameControl(KeyFrameControl keyFrameControl)
    {
        this.keyFrameControl = keyFrameControl;
    }
}
