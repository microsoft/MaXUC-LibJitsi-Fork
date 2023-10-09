/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
// Portions (c) Microsoft Corporation. All rights reserved.
package org.jitsi.impl.neomedia.codec.video.h264;

import static org.bytedeco.ffmpeg.avcodec.AVCodecContext.*;
import static org.bytedeco.ffmpeg.global.avcodec.*;
import static org.bytedeco.ffmpeg.global.avutil.*;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import javax.media.Buffer;
import javax.media.Format;
import javax.media.ResourceUnavailableException;
import javax.media.format.VideoFormat;
import javax.media.format.YUVFormat;

import net.sf.fmj.media.AbstractCodec;
import org.bytedeco.ffmpeg.avcodec.AVCodec;
import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.avutil.AVRational;
import org.bytedeco.javacpp.BytePointer;

import org.jitsi.impl.neomedia.NeomediaServiceUtils;
import org.jitsi.impl.neomedia.codec.AbstractCodec2;
import org.jitsi.impl.neomedia.format.ParameterizedVideoFormat;
import org.jitsi.service.configuration.ConfigurationService;
import org.jitsi.service.libjitsi.LibJitsi;
import org.jitsi.service.neomedia.codec.Constants;
import org.jitsi.service.neomedia.control.KeyFrameControl;
import org.jitsi.service.neomedia.event.RTCPFeedbackEvent;
import org.jitsi.service.neomedia.event.RTCPFeedbackListener;
import org.jitsi.util.OSUtils;
import org.jitsi.util.Logger;

/**
 * Implements an FMJ H.264 encoder using FFmpeg (and x264).
 *
 * @author Damian Minkov
 * @author Lyubomir Marinov
 * @author Sebastien Vincent
 */
public class JNIEncoder
    extends AbstractCodec
    implements RTCPFeedbackListener
{
    /**
     * The available presets we can use with the encoder.
     */
    public static final String[] AVAILABLE_PRESETS
        = {
            "ultrafast",
            "superfast",
            "veryfast",
            "faster",
            "fast",
            "medium",
            "slow",
            "slower",
            "veryslow"
        };

    /**
     * The name of the baseline H.264 (encoding) profile.
     */
    public static final String BASELINE_PROFILE = "baseline";

    /**
     * The default value of the {@link #DEFAULT_INTRA_REFRESH_PNAME}
     * <tt>ConfigurationService</tt> property.
     */
    public static final boolean DEFAULT_DEFAULT_INTRA_REFRESH = true;

    /**
     * The name of the main H.264 (encoding) profile.
     */
    public static final String MAIN_PROFILE = "main";

    /**
     * The default value of the {@link #DEFAULT_PROFILE_PNAME}
     * <tt>ConfigurationService</tt> property.
     */
    public static final String DEFAULT_DEFAULT_PROFILE = MAIN_PROFILE;

    /**
     * The frame rate to be assumed by <tt>JNIEncoder</tt> instances in the
     * absence of any other frame rate indication.
     */
    public static final int DEFAULT_FRAME_RATE = 15;

    /**
     * The name of the boolean <tt>ConfigurationService</tt> property which
     * specifies whether Periodic Intra Refresh is to be used by default. The
     * default value is <tt>true</tt>. The value may be overridden by
     * {@link #setAdditionalCodecSettings(Map)}.
     * This is actually set to false by default and the value is not overridden
     * in any brandings, so it's unlikely the feature is ever used in practice.
     */
    public static final String DEFAULT_INTRA_REFRESH_PNAME
        = "org.jitsi.impl.neomedia.codec.video.h264.defaultIntraRefresh";

    /**
     * The default maximum GOP (group of pictures) size i.e. the maximum
     * interval between keyframes. The x264 library defaults to 250.
     */
    public static final int DEFAULT_KEYINT = 150;

    /**
     * The default value of the {@link #PRESET_PNAME}
     * <tt>ConfigurationService</tt> property.
     */
    public static final String DEFAULT_PRESET = AVAILABLE_PRESETS[0];

    /**
     * The name of the <tt>ConfigurationService</tt> property which specifies
     * the H.264 (encoding) profile to be used in the absence of negotiation.
     * Though it seems that RFC 3984 "RTP Payload Format for H.264 Video"
     * specifies the baseline profile as the default, we have till the time of
     * this writing defaulted to the main profile and we do not currently want
     * to change from the main to the base profile unless we really have to.
     */
    public static final String DEFAULT_PROFILE_PNAME
        = "net.java.sip.communicator.impl.neomedia.codec.video.h264."
            + "defaultProfile";

    /**
     * The name of the high H.264 (encoding) profile.
     */
    public static final String HIGH_PROFILE = "high";

    /**
     * The name of the integer <tt>ConfigurationService</tt> property which
     * specifies the maximum GOP (group of pictures) size i.e. the maximum
     * interval between keyframes. FFmpeg calls it <tt>gop_size</tt>, x264
     * refers to it <tt>keyint</tt> or <tt>i_keyint_max</tt>.
     */
    public static final String KEYINT_PNAME
        = "org.jitsi.impl.neomedia.codec.video.h264.keyint";

    /**
     * The logger used by the <tt>JNIEncoder</tt> class and its instances for
     * logging output.
     */
    private static final Logger logger = Logger.getLogger(JNIEncoder.class);

    /**
     * The name of the format parameter which specifies the packetization mode
     * of H.264 RTP payload.
     */
    public static final String PACKETIZATION_MODE_FMTP = "packetization-mode";

    /**
     * Minimum interval between two PLI request processing (in milliseconds).
     */
    private static final long PLI_INTERVAL = 3000;

    /**
     * Name of the code.
     */
    private static final String PLUGIN_NAME = "H.264 Encoder";

    /**
     * The name of the <tt>ConfigurationService</tt> property which specifies
     * the x264 preset to be used by <tt>JNIEncoder</tt>. A preset is a
     * collection of x264 options that will provide a certain encoding speed to
     * compression ratio. A slower preset will provide better compression i.e.
     * quality per size.
     */
    public static final String PRESET_PNAME
        = "org.jitsi.impl.neomedia.codec.video.h264.preset";

    /**
     * The list of <tt>Formats</tt> supported by <tt>JNIEncoder</tt> instances
     * as output.
     */
    static final Format[] SUPPORTED_OUTPUT_FORMATS
        = {
            new ParameterizedVideoFormat(
                    Constants.H264,
                    PACKETIZATION_MODE_FMTP, "0"),
            new ParameterizedVideoFormat(
                    Constants.H264,
                    PACKETIZATION_MODE_FMTP, "1")
        };

    public static final int X264_KEYINT_MAX_INFINITE = 1 << 30;

    public static final int X264_KEYINT_MIN_AUTO = 0;

    /**
     * Checks the configuration and returns the profile to use.
     * @param profile the profile setting.
     * @return the profile FFmpeg to use.
     */
    private static int getProfileForConfig(String profile)
    {
        if (BASELINE_PROFILE.equalsIgnoreCase(profile))
        {
            // BASELINE is not supported so choose the closest supported one which is CONSTRAINED_BASELINE
            return FF_PROFILE_H264_CONSTRAINED_BASELINE;
        }
        else if (HIGH_PROFILE.equalsIgnoreCase(profile))
        {
            return FF_PROFILE_H264_HIGH;
        }
        else
        {
            return FF_PROFILE_H264_MAIN;
        }
    }

    /**
     * The additional settings of this <tt>Codec</tt>.
     */
    private Map<String, String> additionalCodecSettings;

    /**
     * The codec we will use.
     */
    private AVCodecContext avctx;

    /**
     * The encoded data is stored in avFrame.
     */
    private AVFrame avFrame;

    /**
     * The indicator which determines whether the generation of a keyframe is to
     * be forced during a subsequent execution of
     * {@link #process(Buffer, Buffer)}. The first frame to undergo encoding is
     * naturally a keyframe and, for the sake of clarity, the initial value is
     * <tt>true</tt>.
     */
    private boolean forceKeyFrame = true;

    /**
     * The <tt>KeyFrameControl</tt> used by this <tt>JNIEncoder</tt> to
     * control its key frame-related logic.
     */
    private KeyFrameControl keyFrameControl;

    private KeyFrameControl.KeyFrameRequestee keyFrameRequestee;

    /**
     * The time in milliseconds of the last request for a key frame from the
     * remote peer to this local peer.
     */
    private long lastKeyFrameRequestTime = System.currentTimeMillis();

    /**
     * The packetization mode to be used for the H.264 RTP payload output by
     * this <tt>JNIEncoder</tt> and the associated packetizer. RFC 3984 "RTP
     * Payload Format for H.264 Video" says that "[w]hen the value of
     * packetization-mode is equal to 0 or packetization-mode is not present,
     * the single NAL mode, as defined in section 6.2 of RFC 3984, MUST be
     * used."
     */
    private String packetizationMode;

    /**
     * The raw frame buffer.
     */
    private BytePointer rawFrameBuffer;

    /**
     * Length of the raw frame buffer. Once the dimensions are known, this is
     * set to 3/2 * (height*width), which is the size needed for a YUV420 frame.
     */
    private int rawFrameLen;

    /**
     * The indicator which determines whether two consecutive frames at the
     * beginning of the video transmission have been encoded as keyframes. The
     * first frame is a keyframe but it is at the very beginning of the video
     * transmission and, consequently, there is a higher risk that pieces of it
     * will be lost on their way through the network. To mitigate possible
     * issues in the case of network loss, the second frame is also a keyframe.
     */
    private boolean secondKeyFrame = true;

    private int countDownToKeyFrame = 0;

    // @@@ TODO . Make ffmpeg log level configurable, and redirected to main application logs.
    // Move this code to a more appropriate place when doing so.
    static {
        // To determine what log level is set by default, or in running, use av_log_get_level();
        // Levels are defined in the FFmpeg repo, ffmpeg/libavutil/log.h
        // AV_LOG_QUIET    -8 * Print no output.
        // AV_LOG_PANIC     0 * Something went really wrong and we will crash now.
        // AV_LOG_FATAL     8 * Something went wrong and recovery is not possible.
        // AV_LOG_FATAL    16 * Something went wrong and cannot losslessly be recovered. However, not all future data is affected.
        // AV_LOG_ERROR    24 * Something somehow does not look correct. This may or may not lead to problems. An example would be the use of '-vstrict -2'.
        // AV_LOG_INFO     32 * Standard information
        // AV_LOG_VERBOSE  40 * Detailed information
        // AV_LOG_DEBUG    48 * Stuff which is only useful for libav* developers
        // AV_LOG_TRACE    56 * Extremely verbose debugging, useful for libav* development

        // Setting to ERROR(16) should be enough to disable most logging to console.
        // However, there are a number of benign errors in our standard video calls.
        // Setting the log level lower should only output severe issues in running FFmpeg.
        av_log_set_level(8);
        logger.info("FFmpeg library AV log level set to FATAL (8). These logs currently only appear in stdout console");
    }

    /**
     * Initializes a new <tt>JNIEncoder</tt> instance.
     */
    public JNIEncoder()
    {
        inputFormats
            = new Format[]
            {
                new YUVFormat(
                        /* size */ null,
                        /* maxDataLength */ Format.NOT_SPECIFIED,
                        Format.byteArray,
                        /* frameRate */ Format.NOT_SPECIFIED,
                        YUVFormat.YUV_420,
                        /* strideY */ Format.NOT_SPECIFIED,
                        /* strideUV */ Format.NOT_SPECIFIED,
                        /* offsetY */ Format.NOT_SPECIFIED,
                        /* offsetU */ Format.NOT_SPECIFIED,
                        /* offsetV */ Format.NOT_SPECIFIED)
            };

        inputFormat = null;
        outputFormat = null;
    }

    /**
     * Closes this <tt>Codec</tt>.
     */
    @Override
    public synchronized void close()
    {
        if (opened)
        {
            opened = false;
            super.close();

            if (avctx != null)
            {
                avcodec_close(avctx);
                av_free(avctx);
                avctx = null;
            }

            if (avFrame != null)
            {
                av_frame_unref(avFrame);
                avFrame = null;
            }
            if (rawFrameBuffer != null)
            {
                av_free(rawFrameBuffer);
                rawFrameBuffer = null;
            }

            if (keyFrameRequestee != null)
            {
                if (keyFrameControl != null)
                    keyFrameControl.removeKeyFrameRequestee(keyFrameRequestee);
                keyFrameRequestee = null;
            }
        }
    }

    /**
     * Gets the matching output formats for a specific format.
     *
     * @param inputFormat input format
     * @return array for formats matching input format
     */
    private Format[] getMatchingOutputFormats(Format inputFormat)
    {
        VideoFormat inputVideoFormat = (VideoFormat) inputFormat;

        String[] packetizationModes
            = (this.packetizationMode == null)
                ? new String[] { "0", "1" }
                : new String[] { this.packetizationMode };
        Format[] matchingOutputFormats = new Format[packetizationModes.length];
        Dimension size = inputVideoFormat.getSize();
        float frameRate = inputVideoFormat.getFrameRate();

        for (int index = packetizationModes.length - 1; index >= 0; index--)
        {
            matchingOutputFormats[index]
                = new ParameterizedVideoFormat(
                        Constants.H264,
                        size,
                        /* maxDataLength */ Format.NOT_SPECIFIED,
                        Format.byteArray,
                        frameRate,
                        ParameterizedVideoFormat.toMap(
                                PACKETIZATION_MODE_FMTP,
                                packetizationModes[index]));
        }
        return matchingOutputFormats;
    }

    /**
     * Gets the name of this <tt>Codec</tt>.
     *
     * @return codec name
     */
    @Override
    public String getName()
    {
        return PLUGIN_NAME;
    }

    /**
     * Returns the list of formats supported at the output.
     *
     * @param in input <tt>Format</tt> to determine corresponding output
     * <tt>Format</tt>s
     * @return array of formats supported at output
     */
    @Override
    public Format[] getSupportedOutputFormats(Format in)
    {
        Format[] supportedOutputFormats;

        // null input format
        if (in == null)
            supportedOutputFormats = SUPPORTED_OUTPUT_FORMATS;
        // mismatch input format
        else if (!(in instanceof VideoFormat)
                || (null == AbstractCodec2.matches(in, inputFormats)))
            supportedOutputFormats = new Format[0];
        else
            supportedOutputFormats = getMatchingOutputFormats(in);
        return supportedOutputFormats;
    }

    /**
     * Determines whether the encoding of {@link #avFrame} is to produce a
     * keyframe.
     *
     * @return <tt>true</tt> if the encoding of <tt>avFrame</tt> is to produce a
     * keyframe; otherwise, <tt>false</tt>
     */
    private boolean isKeyFrame()
    {
        boolean keyFrame;

        if (forceKeyFrame)
        {
            keyFrame = true;

            /*
             * The first frame is a keyframe but it is at the very beginning of
             * the video transmission and, consequently, there is a higher risk
             * that pieces of it will be lost on their way through the network.
             * To mitigate possible issues in the case of network loss, the
             * second frame is also a keyframe.
             */
            if (secondKeyFrame)
            {
                secondKeyFrame = false;
                forceKeyFrame = true;
            }
            else
            {
                forceKeyFrame = false;
            }
        }
        else
        {
            keyFrame = false;

            // Force send regular keyframes. Intended to maintain stream stability, but may not be necessary long term
            if (--countDownToKeyFrame <= 0)
            {
                keyFrame = true;
                countDownToKeyFrame = 30;
            }
        }

        return keyFrame;
    }

    /**
     * Notifies this <tt>JNIEncoder</tt> that the remote peer has requested a
     * key frame from this local peer.
     *
     * @return <tt>true</tt> if this <tt>JNIEncoder</tt> has honored the request
     * for a key frame; otherwise, <tt>false</tt>
     */
    private boolean keyFrameRequest()
    {
        long now = System.currentTimeMillis();

        if (now > (lastKeyFrameRequestTime + PLI_INTERVAL))
        {
            lastKeyFrameRequestTime = now;
            forceKeyFrame = true;
        }
        return true;
    }

    /**
     * Opens this <tt>Codec</tt>.
     */
    @Override
    public synchronized void open()
        throws ResourceUnavailableException
    {
        if (opened)
            return;

        VideoFormat inputVideoFormat = (VideoFormat) inputFormat;
        VideoFormat outputVideoFormat = (VideoFormat) outputFormat;

        /*
         * An Encoder translates raw media data in (en)coded media data.
         * Consequently, the size of the output is equal to the size of the
         * input.
         */
        Dimension size = null;

        if (inputVideoFormat != null)
            size = inputVideoFormat.getSize();
        if ((size == null) && (outputVideoFormat != null))
            size = outputVideoFormat.getSize();
        if (size == null)
        {
            throw new ResourceUnavailableException(
                    "The input video frame width and height are not set.");
        }

        int width = size.width, height = size.height;

        /*
         * XXX We do not currently negotiate the profile so, regardless of the
         * many AVCodecContext properties we have set above, force the default
         * profile configuration.
         */
        ConfigurationService cfg = LibJitsi.getConfigurationService();
        boolean intraRefresh = DEFAULT_DEFAULT_INTRA_REFRESH;
        int keyint = DEFAULT_KEYINT;
        String preset = DEFAULT_PRESET;
        String profile = DEFAULT_DEFAULT_PROFILE;

        if (cfg != null)
        {
            intraRefresh
                = cfg.global().getBoolean(DEFAULT_INTRA_REFRESH_PNAME, intraRefresh);
            keyint = cfg.global().getInt(KEYINT_PNAME, keyint);
            preset = cfg.global().getString(PRESET_PNAME, preset);
            profile = cfg.global().getString(DEFAULT_PROFILE_PNAME, profile);
        }

        if (additionalCodecSettings != null)
        {
            for (Map.Entry<String, String> e
                    : additionalCodecSettings.entrySet())
            {
                String k = e.getKey();
                String v = e.getValue();

                if ("h264.intrarefresh".equals(k))
                {
                    if("false".equals(v))
                    {
                        intraRefresh = false;
                    }
                }
                else if ("h264.profile".equals(k))
                {
                    if (BASELINE_PROFILE.equals(v)
                            || HIGH_PROFILE.equals(v)
                            || MAIN_PROFILE.equals(v))
                        profile = v;
                }
            }
        }

        AVCodec avcodec = avcodec_find_encoder(AV_CODEC_ID_H264);
        avctx = avcodec_alloc_context3(avcodec);
        avctx.pix_fmt(AV_PIX_FMT_YUV420P);
        avctx.width(width);
        avctx.height(height);

        // Increased compress and qmax/qdiff values enable the client to reduce data rate more dynamically as needed.
        // This reduces incidence of video corruption / lockup, and enables sending below the bitrate without lag.
        // Values were chosen through limited testing, and should potentially be adjusted and refined further after
        // further testing.
        avctx.qcompress(0.8f);
        avctx.qmin(30);
        avctx.qmax(50);
        avctx.max_qdiff(6);

        int bitRate
            = 1000
                * NeomediaServiceUtils
                    .getMediaServiceImpl()
                        .getDeviceConfiguration()
                            .getVideoBitrate();
        int frameRate = Format.NOT_SPECIFIED;

        // Allow the outputFormat to request a certain frameRate.
        if (outputVideoFormat != null)
        {
            frameRate = (int) outputVideoFormat.getFrameRate();
        }
        // Otherwise, output in the frameRate of the inputFormat.
        if ((frameRate == Format.NOT_SPECIFIED) && (inputVideoFormat != null))
        {
            frameRate = (int) inputVideoFormat.getFrameRate();
        }
        if (frameRate == Format.NOT_SPECIFIED)
        {
            frameRate = DEFAULT_FRAME_RATE;
        }

        // average bit rate
        avctx.bit_rate(bitRate);
        // so to be 1 in x264
        avctx.bit_rate_tolerance(bitRate / frameRate);
        avctx.rc_max_rate(bitRate);
        avctx.thread_count(1);

        // time_base should be 1 / frame rate
        try (AVRational timeBase = new AVRational().num(1).den(frameRate)) {
            avctx.time_base(timeBase);
        }

        avctx.ticks_per_frame(2);

        avctx.mb_decision(FF_MB_DECISION_SIMPLE);

        AVDictionary avDict = new AVDictionary();
        // Options for av_dict_set flags argument documented here:
        // https://ffmpeg.org/doxygen/3.1/group__lavu__dict.html#ga8ed4237acfc3d68484301a5d1859936c

        // It's not clear why we set rc_eq and me_method options
        // They are not used in upstream Jitsi, because they don't use x264,
        // which these flags are intended for.
        // We've left them in because they don't seem to cause any adverse effects
        // and we are trying to change as little as possible about this implementation
        // However, they could probably be removed.
        // More detail:
        // https://github.com/jitsi/libjitsi/pull/500
        // https://github.com/jitsi/libjitsi/pull/502

        av_dict_set(avDict, "rc_eq", "blurCplx^(1-qComp)", 0); // rate-control equation
        // Previous version used value 7, which corresponds to 'hex' according to ffmpeg 2.0 docs
        av_dict_set(avDict, "me_method", "hex", 0); // motion estimation method

        // Force the maximum NAL size to be smaller than the maximum RTP payload, to avoid transmission of FU-A NALU.
        // This is to work around corruption issues on the Android client video receive end.
        av_dict_set(avDict, "max_nal_size", "1000", 0);

        // On Mac, enable the encoder to skip_frames, to allow better video stream latency when dealing with high bitrate images; this may
        // result in a lower frame rate, but the video is far less laggy. Currently we only on apply these changes on Mac clients, as
        // sending from Windows seems OK, and the skip_frames function caused significant choppiness in Windows testing.
        if (OSUtils.IS_MAC)
        {
            av_dict_set(avDict, "allow_skip_frames", "1", 0);
        }

        int prevFlags = avctx.flags();
        if (prevFlags != 0)
        {
            logger.warn("Wiping out previously set flags of " + prevFlags);
        }
        avctx.flags(prevFlags | AV_CODEC_FLAG_LOOP_FILTER);
        avctx.me_subpel_quality(2);
        avctx.me_range(16);
        avctx.me_cmp(FF_CMP_CHROMA);
        avctx.scenechange_threshold(40);
        avctx.rc_buffer_size(10);
        avctx.gop_size(keyint);
        avctx.i_quant_factor(1f / 1.4f);

        avctx.refs(1);

        avctx.keyint_min(X264_KEYINT_MIN_AUTO);

        if ((null == packetizationMode) || "0".equals(packetizationMode))
        {
            avctx.rtp_payload_size(Packetizer.MAX_PAYLOAD_SIZE);
        }

        try
        {
            avctx.profile(getProfileForConfig(profile));
        }
        catch (UnsatisfiedLinkError ule)
        {
            logger.error("The FFmpeg JNI library could not be loaded; it is likely out-of-date.");
        }

        /*
         * XXX crf=0 means lossless coding which is not supported by
         * the baseline and main profiles. Consequently, we cannot
         * specify it because we specify either the baseline or the
         * main profile. Otherwise, x264 will detect the
         * inconsistency in the specified parameters/options and
         * FFmpeg will fail.
         */
        //"crf" /* constant quality mode, constant ratefactor */, "0",

        av_dict_set(avDict, "intra-refresh", intraRefresh ? "1" : "0", 0);
        av_dict_set(avDict, "keyint", Integer.toString(keyint), 0);
        av_dict_set(avDict, "partitions", "b8x8,i4x4,p8x8", 0);
        av_dict_set(avDict, "preset", preset, 0);
        av_dict_set(avDict, "thread_type", "slice", 0);
        av_dict_set(avDict, "tune", "zerolatency", 0);

        if (avcodec_open2(avctx, avcodec, avDict) < 0 )
        {
            throw new ResourceUnavailableException(
                    "Could not open codec " + avcodec.toString());
        }

        rawFrameLen = (width * height * 3) / 2;
        rawFrameBuffer = new BytePointer(av_malloc(rawFrameLen));

        avFrame = av_frame_alloc();

        // Attach rawFrameBuffer to frame as underlying buffer
        av_image_fill_arrays(avFrame.data(), avFrame.linesize(),
                             rawFrameBuffer, AV_PIX_FMT_YUV420P,
                             width, height, 1);

        avFrame.width(width);
        avFrame.height(height);
        avFrame.format(AV_PIX_FMT_YUV420P);

        /*
         * Implement the ability to have the remote peer request key frames from
         * this local peer.
         */
        if (keyFrameRequestee == null)
        {
            keyFrameRequestee
                = new KeyFrameControl.KeyFrameRequestee()
                        {
                            public boolean keyFrameRequest()
                            {
                                return JNIEncoder.this.keyFrameRequest();
                            }
                        };
        }
        if (keyFrameControl != null)
            keyFrameControl.addKeyFrameRequestee(-1, keyFrameRequestee);

        opened = true;
        super.open();
    }

    /**
     * Processes/encodes a buffer.
     *
     * @param inBuffer input buffer
     * @param outBuffer output buffer
     * @return <tt>BUFFER_PROCESSED_OK</tt> if buffer has been successfully
     * processed
     */
    @Override
    public synchronized int process(Buffer inBuffer, Buffer outBuffer)
    {
        /* Check if this is an End of Media event */
        if (isEOM(inBuffer))
        {
            propagateEOM(outBuffer);
            reset();
            logger.debug("Input buffer marked EOM, End of Media, so doing nothing");
            return BUFFER_PROCESSED_OK;
        }
        if (inBuffer.isDiscard())
        {
            outBuffer.setDiscard(true);
            reset();
            logger.debug("Input buffer had DISCARD flag set, so doing nothing");
            return BUFFER_PROCESSED_OK;
        }

        Format inFormat = inBuffer.getFormat();

        if ((inFormat != inputFormat) && !inFormat.equals(inputFormat))
            setInputFormat(inFormat);

        if (inBuffer.getLength() < 10)
        {
            outBuffer.setDiscard(true);
            reset();
            logger.debug("Input buffer was too short, so doing nothing");
            return BUFFER_PROCESSED_OK;
        }

        // Copy the valid data of inBuffer into rawFrameBuffer
        // (the underlying buffer backing avFrame)

        if (!(inBuffer.getData() instanceof byte[]))
        {
            throw new IllegalArgumentException(
                    "Expected inBuffer.getData() to be a byte array, but was: " + inBuffer.getData());
        }

        rawFrameBuffer.put((byte[])inBuffer.getData(), inBuffer.getOffset(), rawFrameLen);

        boolean isKeyFrame = isKeyFrame();
        if (isKeyFrame) {
            avFrame.key_frame(1);
            avFrame.pict_type(AV_PICTURE_TYPE_I);
        } else {
            avFrame.key_frame(0);
            avFrame.pict_type(AV_PICTURE_TYPE_NONE);
        }

        // Encode frame

        AVPacket pkt = new AVPacket();
        int send_ret = avcodec_send_frame(avctx, avFrame);
        int receive_ret = avcodec_receive_packet(avctx, pkt);

        if (send_ret != 0 || receive_ret != 0)
        {
            logger.warn("ffmpeg hit error during H264 encoding:" +
                         " send return code: " + send_ret +
                         " receive return code: " + receive_ret);
        }

        if (pkt.buf() != null)
        {
            int bytesToOutput = pkt.buf().size(); // Number of bytes used in the output buffer

            // Copy packet data into output array
            byte[] out = AbstractCodec2.validateByteArraySize(outBuffer,
                                                              rawFrameLen,
                                                              false);

            pkt.buf().data().get(out, 0, bytesToOutput);

            av_packet_unref(pkt);

            outBuffer.setData(out);
            outBuffer.setLength(bytesToOutput);
            outBuffer.setOffset(0);
            outBuffer.setTimeStamp(inBuffer.getTimeStamp());
        }
        else
        {
            av_packet_unref(pkt);
        }

        return BUFFER_PROCESSED_OK;
    }

    /**
     * Notifies this <tt>RTCPFeedbackListener</tt> that an RTCP feedback message
     * has been received
     *
     * @param event an <tt>RTCPFeedbackEvent</tt> which specifies the details of
     * the notification event such as the feedback message type and the payload
     * type
     */
    public void rtcpFeedbackReceived(RTCPFeedbackEvent event)
    {
        /*
         * If RTCP message is a Picture Loss Indication (PLI) or a Full
         * Intra-frame Request (FIR) the encoder will force the next frame to be
         * a keyframe.
         */
        if (event.getPayloadType() == RTCPFeedbackEvent.PT_PS)
        {
            switch (event.getFeedbackMessageType())
            {
                case RTCPFeedbackEvent.FMT_PLI:
                case RTCPFeedbackEvent.FMT_FIR:
                    logger.trace("Scheduling a key-frame, because we" +
                                " received an RTCP PLI or FIR.");
                    keyFrameRequest();
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * Sets additional settings on this <tt>Codec</tt>.
     *
     * @param additionalCodecSettings the additional settings to be set on this
     * <tt>Codec</tt>
     */
    public void setAdditionalCodecSettings(
            Map<String, String> additionalCodecSettings)
    {
        this.additionalCodecSettings = additionalCodecSettings;
    }

    /**
     * Sets the <tt>Format</tt> of the media data to be input to this
     * <tt>Codec</tt>.
     *
     * @param format the <tt>Format</tt> of media data to set on this
     * <tt>Codec</tt>
     * @return the <tt>Format</tt> of media data set on this <tt>Codec</tt> or
     * <tt>null</tt> if the specified <tt>format</tt> is not supported by this
     * <tt>Codec</tt>
     */
    @Override
    public Format setInputFormat(Format format)
    {
        // mismatch input format
        if (!(format instanceof VideoFormat)
                || (null == AbstractCodec2.matches(format, inputFormats)))
            return null;

        YUVFormat yuvFormat = (YUVFormat) format;

        if (yuvFormat.getOffsetU() > yuvFormat.getOffsetV())
            return null;

        inputFormat = AbstractCodec2.specialize(yuvFormat, Format.byteArray);

        // Return the selected inputFormat
        return inputFormat;
    }

    /**
     * Sets the <tt>KeyFrameControl</tt> to be used by this
     * <tt>JNIEncoder</tt> as a means of control over its key frame-related
     * logic.
     *
     * @param keyFrameControl the <tt>KeyFrameControl</tt> to be used by this
     * <tt>JNIEncoder</tt> as a means of control over its key frame-related
     * logic
     */
    public void setKeyFrameControl(KeyFrameControl keyFrameControl)
    {
        if (this.keyFrameControl != keyFrameControl)
        {
            if ((this.keyFrameControl != null) && (keyFrameRequestee != null))
                this.keyFrameControl.removeKeyFrameRequestee(keyFrameRequestee);

            this.keyFrameControl = keyFrameControl;

            if ((this.keyFrameControl != null) && (keyFrameRequestee != null))
                this.keyFrameControl.addKeyFrameRequestee(-1, keyFrameRequestee);
        }
    }

    /**
     * Sets the <tt>Format</tt> in which this <tt>Codec</tt> is to output media
     * data.
     *
     * @param format the <tt>Format</tt> in which this <tt>Codec</tt> is to
     * output media data
     * @return the <tt>Format</tt> in which this <tt>Codec</tt> is currently
     * configured to output media data or <tt>null</tt> if <tt>format</tt> was
     * found to be incompatible with this <tt>Codec</tt>
     */
    @Override
    public Format setOutputFormat(Format format)
    {
        // mismatch output format
        if (!(format instanceof VideoFormat)
                || (null
                        == AbstractCodec2.matches(
                                format,
                                getMatchingOutputFormats(inputFormat))))
            return null;

        VideoFormat videoFormat = (VideoFormat) format;
        /*
         * An Encoder translates raw media data in (en)coded media data.
         * Consequently, the size of the output is equal to the size of the
         * input.
         */
        Dimension size = null;

        if (inputFormat != null)
            size = ((VideoFormat) inputFormat).getSize();
        if ((size == null) && format.matches(outputFormat))
            size = ((VideoFormat) outputFormat).getSize();

        Map<String, String> fmtps = null;

        if (format instanceof ParameterizedVideoFormat)
            fmtps = ((ParameterizedVideoFormat) format).getFormatParameters();
        if (fmtps == null)
            fmtps = new HashMap<>();
        if (packetizationMode != null)
            fmtps.put(PACKETIZATION_MODE_FMTP, packetizationMode);

        outputFormat
            = new ParameterizedVideoFormat(
                    videoFormat.getEncoding(),
                    size,
                    /* maxDataLength */ Format.NOT_SPECIFIED,
                    Format.byteArray,
                    videoFormat.getFrameRate(),
                    fmtps);

        // Return the selected outputFormat
        return outputFormat;
    }

    /**
     * Sets the packetization mode to be used for the H.264 RTP payload output
     * by this <tt>JNIEncoder</tt> and the associated packetizer.
     *
     * @param packetizationMode the packetization mode to be used for the H.264
     * RTP payload output by this <tt>JNIEncoder</tt> and the associated
     * packetizer
     */
    public void setPacketizationMode(String packetizationMode)
    {
        /*
         * RFC 3984 "RTP Payload Format for H.264 Video" says that "[w]hen the
         * value of packetization-mode is equal to 0 or packetization-mode is
         * not present, the single NAL mode, as defined in section 6.2 of RFC
         * 3984, MUST be used."
         */
        if ((packetizationMode == null) || "0".equals(packetizationMode))
            this.packetizationMode = "0";
        else if ("1".equals(packetizationMode))
            this.packetizationMode = "1";
        else
            throw new IllegalArgumentException("packetizationMode");
    }
}
