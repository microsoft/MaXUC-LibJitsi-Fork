/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia;

import static java.lang.String.*;

import java.awt.*;
import java.util.*;
import java.util.List;

import javax.media.*;
import javax.media.format.*;
import javax.sdp.*;

import org.jitsi.impl.neomedia.codec.video.h264.*;
import org.jitsi.impl.neomedia.format.*;
import org.jitsi.service.configuration.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.codec.*;
import org.jitsi.service.neomedia.format.*;
import org.jitsi.util.*;

/**
 * Implements static utility methods used by media classes.
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Boris Grozev
 */
public class MediaUtils
{
    private static final Logger logger = Logger.getLogger(MediaUtils.class);

    /**
     * An empty array with <tt>MediaFormat</tt> element type. Explicitly defined
     * in order to reduce unnecessary allocations, garbage collection.
     */
    public static final MediaFormat[] EMPTY_MEDIA_FORMATS = new MediaFormat[0];

    /**
     * The <tt>Map</tt> of JMF-specific encodings to well-known encodings as
     * defined in RFC 3551.
     */
    private static final Map<String, String> jmfEncodingToEncodings
        = new HashMap<>();

    /**
     * The maximum number of channels for audio that is available through
     * <tt>MediaUtils</tt>.
     */
    public static final int MAX_AUDIO_CHANNELS;

    /**
     * The maximum sample rate for audio that is available through
     * <tt>MediaUtils</tt>.
     */
    public static final double MAX_AUDIO_SAMPLE_RATE;

    /**
     * The maximum sample size in bits for audio that is available through
     * <tt>MediaUtils</tt>.
     */
    public static final int MAX_AUDIO_SAMPLE_SIZE_IN_BITS;

    /**
     * The string used in H.264 SDP format attribute for packetization mode.
     */
    private static final String H264_FMT_PACKETIZATION_MODE =
        "packetization-mode";

    /**
     * The string used in H.264 SDP format attribute for H.264 profile level.
     */
    public static final String H264_FMT_PROFILE_LEVEL_ID = "profile-level-id";

    /**
     * H.264 profile level IDC 1.1 (default for Accession Mobile).
     * Supports video resolution up to 352x288 (note we're ignoring frame rate).
     * Profiles below this level support video resolution up to 176x144.
     */
    private static final byte H264_PROFILE_IDC_1_1 = 11;

    /**
     * H.264 profile level IDC 2.1.
     * Supports video resolution up to 352x576 (note we're ignoring frame rate).
     */
    private static final byte H264_PROFILE_IDC_2_1 = 21;

    /**
     * H.264 profile level IDC 2.2.
     * Supports video resolution up to 720x576 (note we're ignoring frame rate).
     */
    private static final byte H264_PROFILE_IDC_2_2 = 22;

    /**
     * H.264 profile level IDC 3.1 (default for Accession Desktop).
     * Supports video resolution up to 1280x720 (aka 720p HD) (note we're
     * ignoring frame rate).
     */
    private static final byte H264_PROFILE_IDC_3_1 = 31;

    /**
     * Maximum H.264 profile level supported.
     */
    private static final byte H264_PROFILE_MAX_SUPPORTED = H264_PROFILE_IDC_3_1;

    /**
     * The values of the level IDC byte in the H.264 profile that we support in
     * preference order.
     */
    private static final byte[] SUPPORTED_H264_PROFILE_IDCS =
        { H264_PROFILE_IDC_3_1,
          H264_PROFILE_IDC_2_2,
          H264_PROFILE_IDC_2_1,
          H264_PROFILE_IDC_1_1 };

    /**
     * The <tt>MediaFormat</tt>s which do not have RTP payload types assigned by
     * RFC 3551 and are thus referred to as having dynamic RTP payload types.
     */
    private static final List<MediaFormat> rtpPayloadTypelessMediaFormats
        = new ArrayList<>();

    /**
     * The <tt>Map</tt> of RTP payload types (expressed as <tt>String</tt>s) to
     * <tt>MediaFormat</tt>s.
     */
    private static final Map<String, MediaFormat[]>
        rtpPayloadTypeStrToMediaFormats
            = new HashMap<>();

    static
    {
        /*
         * CREATE ALL SUPPORTED MEDIA FORMATS
         */

        /* G.711 */
        addMediaFormats(
            (byte) SdpConstants.PCMU,
            "PCMU",
            MediaType.AUDIO,
            AudioFormat.ULAW_RTP,
            8000);
        addMediaFormats(
            (byte) SdpConstants.PCMA,
            "PCMA",
            MediaType.AUDIO,
            Constants.ALAW_RTP,
            8000);

        /* G.722 */
        addMediaFormats(
            (byte) SdpConstants.G722,
            "G722",
            MediaType.AUDIO,
            Constants.G722_RTP,
            8000);

        /*
         * G.723 depends on JMF native libraries which are only available
         * on 32-bit Linux and 32-bit Windows.
         */
        if(OSUtils.IS_WINDOWS32)
        {
            Map<String, String> g723FormatParams
                = new HashMap<>();
            g723FormatParams.put("annexa", "no");
            g723FormatParams.put("bitrate", "6.3");
            addMediaFormats(
                    (byte) SdpConstants.G723,
                    "G723",
                    MediaType.AUDIO,
                    AudioFormat.G723_RTP,
                    g723FormatParams,
                    null,
                    8000);
        }

        /* A load of other random audio codecs */
        addMediaFormats(
            (byte) SdpConstants.GSM,
            "GSM",
            MediaType.AUDIO,
            AudioFormat.GSM_RTP,
            8000);
        addMediaFormats(
            MediaFormat.RTP_PAYLOAD_TYPE_UNKNOWN,
            "speex",
            MediaType.AUDIO,
            Constants.SPEEX_RTP,
            8000, 16000, 32000);

        /* Telephone events */
        addMediaFormats(
            MediaFormat.RTP_PAYLOAD_TYPE_UNKNOWN,
            "telephone-event",
            MediaType.AUDIO,
            Constants.TELEPHONE_EVENT,
            8000);

        /* SILK */
        ConfigurationService cfg = LibJitsi.getConfigurationService();
        boolean advertiseFEC
                = cfg.global().getBoolean(Constants.PROP_SILK_ADVERSISE_FEC, false);
        Map<String,String> silkFormatParams = new HashMap<>();
        if(advertiseFEC)
            silkFormatParams.put("useinbandfec", "1");
        addMediaFormats(
                MediaFormat.RTP_PAYLOAD_TYPE_UNKNOWN,
                "SILK",
                MediaType.AUDIO,
                Constants.SILK_RTP,
                silkFormatParams,
                null,
                8000, 12000, 16000, 24000);

        /* OPUS */
        Map<String, String> opusFormatParams = new HashMap<>();
        boolean opusFec = cfg.global().getBoolean(Constants.PROP_OPUS_FEC, true);
        if(!opusFec)
            opusFormatParams.put("useinbandfec", "0");
        boolean opusDtx = cfg.global().getBoolean(Constants.PROP_OPUS_DTX, true);
        if(opusDtx)
            opusFormatParams.put("usedtx", "1");
        addMediaFormats(
            MediaFormat.RTP_PAYLOAD_TYPE_UNKNOWN,
            "opus",
            MediaType.AUDIO,
            Constants.OPUS_RTP,
            2,
            opusFormatParams,
            null,
            48000);

        /*
         * H264
         *
         * We support multiple H.264 profiles for different video resolutions.
         * Loop through all the supported profiles and add media formats for
         * each.
         */
        for (byte idc : SUPPORTED_H264_PROFILE_IDCS)
        {
            Map<String, String> h264FormatParams = new HashMap<>();

            if (cfg == null ||
                cfg.global().getString("net.java.sip.communicator.impl.neomedia" +
                       ".codec.video.h264.defaultProfile",
                       JNIEncoder.MAIN_PROFILE).equals(JNIEncoder.MAIN_PROFILE))
            {
                // main profile, common features, HD capable level 3.1
                h264FormatParams.put(H264_FMT_PROFILE_LEVEL_ID, format("4DE0%02X", idc));
            }
            else
            {
                // baseline profile, common features, HD capable level 3.1
                h264FormatParams.put(H264_FMT_PROFILE_LEVEL_ID, format("42E0%02X", idc));
            }

            // By default, packetization-mode=1 is enabled.
            if (cfg == null ||
                cfg.global().getBoolean("net.java.sip.communicator.impl.neomedia" +
                               ".codec.video.h264.packetization-mode-1.enabled",
                               true))
            {
                // packetization-mode=1
                h264FormatParams.put(H264_FMT_PACKETIZATION_MODE, "1");
                addMediaFormats(
                    MediaFormat.RTP_PAYLOAD_TYPE_UNKNOWN,
                    "H264",
                    MediaType.VIDEO,
                    Constants.H264_RTP,
                    h264FormatParams,
                    null);
            }

            // packetization-mode=0
            h264FormatParams.put(H264_FMT_PACKETIZATION_MODE, "0");
            addMediaFormats(
                MediaFormat.RTP_PAYLOAD_TYPE_UNKNOWN,
                "H264",
                MediaType.VIDEO,
                Constants.H264_RTP,
                h264FormatParams,
                null);
        }

        /* VP8 */
        addMediaFormats(
                MediaFormat.RTP_PAYLOAD_TYPE_UNKNOWN,
                "VP8",
                MediaType.VIDEO,
                Constants.VP8_RTP,
                null, null);

        /*
         * DONE ADDING MEDIA FORMATS
         */

        // Calculate the values of the MAX_AUDIO_* static fields of MediaUtils.
        List<MediaFormat> audioMediaFormats
            = new ArrayList<>(
                rtpPayloadTypeStrToMediaFormats.size()
                        + rtpPayloadTypelessMediaFormats.size());

        for (MediaFormat[] mediaFormats
                : rtpPayloadTypeStrToMediaFormats.values())
            for (MediaFormat mediaFormat : mediaFormats)
                if (MediaType.AUDIO.equals(mediaFormat.getMediaType()))
                    audioMediaFormats.add(mediaFormat);
        for (MediaFormat mediaFormat : rtpPayloadTypelessMediaFormats)
            if (MediaType.AUDIO.equals(mediaFormat.getMediaType()))
                audioMediaFormats.add(mediaFormat);

        int maxAudioChannels = Format.NOT_SPECIFIED;
        double maxAudioSampleRate = Format.NOT_SPECIFIED;
        int maxAudioSampleSizeInBits = Format.NOT_SPECIFIED;

        for (MediaFormat mediaFormat : audioMediaFormats)
        {
            AudioMediaFormatImpl audioMediaFormat
                = (AudioMediaFormatImpl) mediaFormat;
            int channels = audioMediaFormat.getChannels();
            /*
             * The opus/rtp format has 2 channels, but we don't want it to
             * trigger use of 2 channels elsewhere.
             */
            if ("opus".equals(audioMediaFormat.getEncoding()))
                channels = 1;
            double sampleRate = audioMediaFormat.getClockRate();
            int sampleSizeInBits
                = audioMediaFormat.getFormat().getSampleSizeInBits();

            if (maxAudioChannels < channels)
                maxAudioChannels = channels;
            if (maxAudioSampleRate < sampleRate)
                maxAudioSampleRate = sampleRate;
            if (maxAudioSampleSizeInBits < sampleSizeInBits)
                maxAudioSampleSizeInBits = sampleSizeInBits;
        }

        MAX_AUDIO_CHANNELS = maxAudioChannels;
        MAX_AUDIO_SAMPLE_RATE = maxAudioSampleRate;
        MAX_AUDIO_SAMPLE_SIZE_IN_BITS = maxAudioSampleSizeInBits;
    }

    /**
     * Adds a new mapping of a specific RTP payload type to a list of
     * <tt>MediaFormat</tt>s of a specific <tt>MediaType</tt>, with a specific
     * JMF encoding and, optionally, with specific clock rates.
     *
     * @param rtpPayloadType the RTP payload type to be associated with a list
     * of <tt>MediaFormat</tt>s
     * @param encoding the well-known encoding (name) corresponding to
     * <tt>rtpPayloadType</tt> (in contrast to the JMF-specific encoding
     * specified by <tt>jmfEncoding</tt>)
     * @param mediaType the <tt>MediaType</tt> of the <tt>MediaFormat</tt>s to
     * be associated with <tt>rtpPayloadType</tt>
     * @param jmfEncoding the JMF encoding of the <tt>MediaFormat</tt>s to be
     * associated with <tt>rtpPayloadType</tt>
     * @param clockRates the optional list of clock rates of the
     * <tt>MediaFormat</tt>s to be associated with <tt>rtpPayloadType</tt>
     */
    private static void addMediaFormats(
            byte rtpPayloadType,
            String encoding,
            MediaType mediaType,
            String jmfEncoding,
            double... clockRates)
    {
        addMediaFormats(
            rtpPayloadType,
            encoding,
            mediaType,
            jmfEncoding,
            null,
            null,
            clockRates);
    }

    /**
     * Adds a new mapping of a specific RTP payload type to a list of
     * <tt>MediaFormat</tt>s of a specific <tt>MediaType</tt>, with a specific
     * JMF encoding and, optionally, with specific clock rates.
     *
     * @param rtpPayloadType the RTP payload type to be associated with a list
     * of <tt>MediaFormat</tt>s
     * @param encoding the well-known encoding (name) corresponding to
     * <tt>rtpPayloadType</tt> (in contrast to the JMF-specific encoding
     * specified by <tt>jmfEncoding</tt>)
     * @param mediaType the <tt>MediaType</tt> of the <tt>MediaFormat</tt>s to
     * be associated with <tt>rtpPayloadType</tt>
     * @param jmfEncoding the JMF encoding of the <tt>MediaFormat</tt>s to be
     * associated with <tt>rtpPayloadType</tt>
     * @param formatParameters the set of format-specific parameters of the
     * <tt>MediaFormat</tt>s to be associated with <tt>rtpPayloadType</tt>
     * @param advancedAttributes the set of advanced attributes of the
     * <tt>MediaFormat</tt>s to be associated with <tt>rtpPayload</tt>
     * @param clockRates the optional list of clock rates of the
     * <tt>MediaFormat</tt>s to be associated with <tt>rtpPayloadType</tt>
     */
    private static void addMediaFormats(
            byte rtpPayloadType,
            String encoding,
            MediaType mediaType,
            String jmfEncoding,
            Map<String, String> formatParameters,
            Map<String, String> advancedAttributes,
            double... clockRates)
    {
        addMediaFormats(
                rtpPayloadType,
                encoding,
                mediaType,
                jmfEncoding,
                1 /* channel */,
                formatParameters,
                advancedAttributes,
                clockRates);
    }

    /**
     * Adds a new mapping of a specific RTP payload type to a list of
     * <tt>MediaFormat</tt>s of a specific <tt>MediaType</tt>, with a specific
     * JMF encoding and, optionally, with specific clock rates.
     *
     * @param rtpPayloadType the RTP payload type to be associated with a list
     * of <tt>MediaFormat</tt>s
     * @param encoding the well-known encoding (name) corresponding to
     * <tt>rtpPayloadType</tt> (in contrast to the JMF-specific encoding
     * specified by <tt>jmfEncoding</tt>)
     * @param mediaType the <tt>MediaType</tt> of the <tt>MediaFormat</tt>s to
     * be associated with <tt>rtpPayloadType</tt>
     * @param jmfEncoding the JMF encoding of the <tt>MediaFormat</tt>s to be
     * associated with <tt>rtpPayloadType</tt>
     * @param channels number of channels
     * @param formatParameters the set of format-specific parameters of the
     * <tt>MediaFormat</tt>s to be associated with <tt>rtpPayloadType</tt>
     * @param advancedAttributes the set of advanced attributes of the
     * <tt>MediaFormat</tt>s to be associated with <tt>rtpPayload</tt>
     * @param clockRates the optional list of clock rates of the
     * <tt>MediaFormat</tt>s to be associated with <tt>rtpPayloadType</tt>
     */
    @SuppressWarnings("unchecked")
    private static void addMediaFormats(
            byte rtpPayloadType,
            String encoding,
            MediaType mediaType,
            String jmfEncoding,
            int channels,
            Map<String, String> formatParameters,
            Map<String, String> advancedAttributes,
            double... clockRates)
    {
        int clockRateCount = clockRates.length;
        List<MediaFormat> mediaFormats
            = new ArrayList<>(clockRateCount);

        if (clockRateCount > 0)
        {
            for (double clockRate : clockRates)
            {
                Format format;

                switch (mediaType)
                {
                case AUDIO:
                    if(channels == 1)
                        format = new AudioFormat(jmfEncoding);
                    else
                        format = new AudioFormat(
                                jmfEncoding,
                                Format.NOT_SPECIFIED,
                                Format.NOT_SPECIFIED,
                                channels);
                    break;
                case VIDEO:
                    format
                        = new ParameterizedVideoFormat(
                                jmfEncoding,
                                formatParameters);
                    break;
                default:
                    throw new IllegalArgumentException("mediaType");
                }

                MediaFormat mediaFormat
                    = MediaFormatImpl.createInstance(
                            format,
                            clockRate,
                            formatParameters,
                            advancedAttributes);

                if (mediaFormat != null)
                    mediaFormats.add(mediaFormat);
            }
        }
        else
        {
            Format format;
            double clockRate;

            switch (mediaType)
            {
            case AUDIO:
                AudioFormat audioFormat = new AudioFormat(jmfEncoding);

                format = audioFormat;
                clockRate = audioFormat.getSampleRate();
                break;
            case VIDEO:
                format
                    = new ParameterizedVideoFormat(
                            jmfEncoding,
                            formatParameters);
                clockRate = VideoMediaFormatImpl.DEFAULT_CLOCK_RATE;
                break;
            default:
                throw new IllegalArgumentException("mediaType");
            }

            MediaFormat mediaFormat
                = MediaFormatImpl.createInstance(
                        format,
                        clockRate,
                        formatParameters,
                        advancedAttributes);

            if (mediaFormat != null)
                mediaFormats.add(mediaFormat);
        }

        if (mediaFormats.size() > 0)
        {
            if (MediaFormat.RTP_PAYLOAD_TYPE_UNKNOWN == rtpPayloadType)
                rtpPayloadTypelessMediaFormats.addAll(mediaFormats);
            else
                rtpPayloadTypeStrToMediaFormats.put(
                        Byte.toString(rtpPayloadType),
                        mediaFormats.toArray(EMPTY_MEDIA_FORMATS));

            jmfEncodingToEncodings.put(
                    ((MediaFormatImpl<? extends Format>) mediaFormats.get(0))
                        .getJMFEncoding(),
                    encoding);
        }
    }

    /**
     * Gets a <tt>MediaFormat</tt> predefined in <tt>MediaUtils</tt> which
     * represents a specific JMF <tt>Format</tt>. If there is no such
     * representing <tt>MediaFormat</tt> in <tt>MediaUtils</tt>, returns
     * <tt>null</tt>.
     *
     * @param format the JMF <tt>Format</tt> to get the <tt>MediaFormat</tt>
     * representation for
     * @return a <tt>MediaFormat</tt> predefined in <tt>MediaUtils</tt> which
     * represents <tt>format</tt> if any; <tt>null</tt> if there is no such
     * representing <tt>MediaFormat</tt> in <tt>MediaUtils</tt>
     */
    @SuppressWarnings("unchecked")
    public static MediaFormat getMediaFormat(Format format)
    {
        double clockRate;

        if (format instanceof AudioFormat)
            clockRate = ((AudioFormat) format).getSampleRate();
        else if (format instanceof VideoFormat)
            clockRate = VideoMediaFormatImpl.DEFAULT_CLOCK_RATE;
        else
            clockRate = Format.NOT_SPECIFIED;

        byte rtpPayloadType = getRTPPayloadType(format.getEncoding(), clockRate);

        if (MediaFormatImpl.RTP_PAYLOAD_TYPE_UNKNOWN != rtpPayloadType)
        {
            for (MediaFormat mediaFormat : getMediaFormats(rtpPayloadType))
            {
                MediaFormatImpl<? extends Format> mediaFormatImpl
                    = (MediaFormatImpl<? extends Format>) mediaFormat;

                if (format.matches(mediaFormatImpl.getFormat()))
                    return mediaFormat;
            }
        }
        return null;
    }

    /**
     * Gets the <tt>MediaFormat</tt> known to <tt>MediaUtils</tt> and having the
     * specified well-known <tt>encoding</tt> (name) and <tt>clockRate</tt>.
     *
     * @param encoding the well-known encoding (name) of the
     * <tt>MediaFormat</tt> to get
     * @param clockRate the clock rate of the <tt>MediaFormat</tt> to get
     * @return the <tt>MediaFormat</tt> known to <tt>MediaUtils</tt> and having
     * the specified <tt>encoding</tt> and <tt>clockRate</tt>
     */
    public static MediaFormat getMediaFormat(String encoding, double clockRate)
    {
        return getMediaFormat(encoding, clockRate, null);
    }

    /**
     * Gets the <tt>MediaFormat</tt> known to <tt>MediaUtils</tt> and having the
     * specified well-known <tt>encoding</tt> (name), <tt>clockRate</tt> and
     * matching format parameters.
     *
     * @param encoding the well-known encoding (name) of the
     * <tt>MediaFormat</tt> to get
     * @param clockRate the clock rate of the <tt>MediaFormat</tt> to get
     * @param fmtps the format parameters of the <tt>MediaFormat</tt> to get
     * @return the <tt>MediaFormat</tt> known to <tt>MediaUtils</tt> and having
     * the specified <tt>encoding</tt> (name), <tt>clockRate</tt> and matching
     * format parameters
     */
    public static MediaFormat getMediaFormat(
            String encoding, double clockRate,
            Map<String, String> fmtps)
    {
        for (MediaFormat format : getMediaFormats(encoding))
            if ((format.getClockRate() == clockRate)
                    && format.formatParametersMatch(fmtps))
                return format;
        return null;
    }

    /**
     * Gets the index of a specific <tt>MediaFormat</tt> instance within the
     * internal storage of <tt>MediaUtils</tt>. Since the index is in the
     * internal storage which may or may not be one and the same for the various
     * <tt>MediaFormat</tt> instances and which may or may not be searched for
     * the purposes of determining the index, the index is not to be used as a
     * way to determine whether <tt>MediaUtils</tt> knows the specified
     * <tt>mediaFormat</tt>
     *
     * @param mediaFormat the <tt>MediaFormat</tt> to determine the index of
     * @return the index of the specified <tt>mediaFormat</tt> in the internal
     * storage of <tt>MediaUtils</tt>
     */
    public static int getMediaFormatIndex(MediaFormat mediaFormat)
    {
        return rtpPayloadTypelessMediaFormats.indexOf(mediaFormat);
    }

    /**
     * Gets the <tt>MediaFormat</tt>s (expressed as an array) corresponding to
     * a specific RTP payload type.
     *
     * @param rtpPayloadType the RTP payload type to retrieve the
     * corresponding <tt>MediaFormat</tt>s for
     * @return an array of <tt>MediaFormat</tt>s corresponding to the specified
     * RTP payload type
     */
    public static MediaFormat[] getMediaFormats(byte rtpPayloadType)
    {
        MediaFormat[] mediaFormats
            = rtpPayloadTypeStrToMediaFormats.get(Byte.toString(rtpPayloadType));

        return
            (mediaFormats == null)
                ? EMPTY_MEDIA_FORMATS
                : mediaFormats.clone();
    }

    /**
     * Gets the <tt>MediaFormat</tt>s known to <tt>MediaUtils</tt> and being of
     * the specified <tt>MediaType</tt>.
     *
     * @param mediaType the <tt>MediaType</tt> of the <tt>MediaFormat</tt>s to
     * get
     * @return the <tt>MediaFormat</tt>s known to <tt>MediaUtils</tt> and being
     * of the specified <tt>mediaType</tt>
     */
    public static MediaFormat[] getMediaFormats(MediaType mediaType)
    {
        List<MediaFormat> mediaFormats = new ArrayList<>();

        for (MediaFormat[] formats : rtpPayloadTypeStrToMediaFormats.values())
            for (MediaFormat format : formats)
                if (format.getMediaType().equals(mediaType))
                    mediaFormats.add(format);
        for (MediaFormat format : rtpPayloadTypelessMediaFormats)
            if (format.getMediaType().equals(mediaType))
                mediaFormats.add(format);
        return mediaFormats.toArray(EMPTY_MEDIA_FORMATS);
    }

    /**
     * Gets the <tt>MediaFormat</tt>s predefined in <tt>MediaUtils</tt> with a
     * specific well-known encoding (name) as defined by RFC 3551 "RTP Profile
     * for Audio and Video Conferences with Minimal Control".
     *
     * @param encoding the well-known encoding (name) to get the corresponding
     * <tt>MediaFormat</tt>s of
     * @return a <tt>List</tt> of <tt>MediaFormat</tt>s corresponding to the
     * specified encoding (name)
     */
    @SuppressWarnings("unchecked")
    public static List<MediaFormat> getMediaFormats(String encoding)
    {
        String jmfEncoding = null;

        for (Map.Entry<String, String> jmfEncodingToEncoding
                : jmfEncodingToEncodings.entrySet())
            if (jmfEncodingToEncoding.getValue().equals(encoding))
            {
                jmfEncoding = jmfEncodingToEncoding.getKey();
                break;
            }

        List<MediaFormat> mediaFormats = new ArrayList<>();

        if (jmfEncoding != null)
        {
            for (MediaFormat[] rtpPayloadTypeMediaFormats
                    : rtpPayloadTypeStrToMediaFormats.values())
                for (MediaFormat rtpPayloadTypeMediaFormat
                            : rtpPayloadTypeMediaFormats)
                    if (((MediaFormatImpl<? extends Format>)
                                    rtpPayloadTypeMediaFormat)
                                .getJMFEncoding().equals(jmfEncoding))
                        mediaFormats.add(rtpPayloadTypeMediaFormat);

            if (mediaFormats.size() < 1)
            {
                for (MediaFormat rtpPayloadTypelessMediaFormat
                        : rtpPayloadTypelessMediaFormats)
                    if (((MediaFormatImpl<? extends Format>)
                                    rtpPayloadTypelessMediaFormat)
                                .getJMFEncoding().equals(jmfEncoding))
                        mediaFormats.add(rtpPayloadTypelessMediaFormat);
            }
        }
        return mediaFormats;
    }

    /**
     * Gets the RTP payload type corresponding to a specific JMF encoding and
     * clock rate.
     *
     * @param jmfEncoding the JMF encoding as returned by
     * {@link Format#getEncoding()} or the respective <tt>AudioFormat</tt> and
     * <tt>VideoFormat</tt> encoding constants to get the corresponding RTP
     * payload type of
     * @param clockRate the clock rate to be taken into account in the search
     * for the RTP payload type if the JMF encoding does not uniquely identify
     * it
     * @return the RTP payload type corresponding to the specified JMF encoding
     * and clock rate if known in RFC 3551 "RTP Profile for Audio and Video
     * Conferences with Minimal Control"; otherwise,
     * {@link MediaFormat#RTP_PAYLOAD_TYPE_UNKNOWN}
     */
    public static byte getRTPPayloadType(String jmfEncoding, double clockRate)
    {
        if (jmfEncoding == null)
            return MediaFormat.RTP_PAYLOAD_TYPE_UNKNOWN;
        else if (jmfEncoding.equals(AudioFormat.ULAW_RTP))
            return SdpConstants.PCMU;
        else if (jmfEncoding.equals(Constants.ALAW_RTP))
            return SdpConstants.PCMA;
        else if (jmfEncoding.equals(AudioFormat.GSM_RTP))
            return SdpConstants.GSM;
        else if (jmfEncoding.equals(AudioFormat.G723_RTP))
            return SdpConstants.G723;
        else if (jmfEncoding.equals(AudioFormat.DVI_RTP)
                    && (clockRate == 8000))
            return SdpConstants.DVI4_8000;
        else if (jmfEncoding.equals(AudioFormat.DVI_RTP)
                    && (clockRate == 16000))
            return SdpConstants.DVI4_16000;
        else if (jmfEncoding.equals(AudioFormat.ALAW))
            return SdpConstants.PCMA;
        else if (jmfEncoding.equals(Constants.G722))
            return SdpConstants.G722;
        else if (jmfEncoding.equals(Constants.G722_RTP))
            return SdpConstants.G722;
        else if (jmfEncoding.equals(AudioFormat.GSM))
            return SdpConstants.GSM;
        else if (jmfEncoding.equals(AudioFormat.GSM_RTP))
            return SdpConstants.GSM;
        else if (jmfEncoding.equals(AudioFormat.G728_RTP))
            return SdpConstants.G728;
        else if (jmfEncoding.equals(AudioFormat.G729_RTP))
            return SdpConstants.G729;
        else if (jmfEncoding.equals(VideoFormat.H263_RTP))
            return SdpConstants.H263;
        else if (jmfEncoding.equals(VideoFormat.H261_RTP))
            return SdpConstants.H261;
        else
            return MediaFormat.RTP_PAYLOAD_TYPE_UNKNOWN;
    }

    /**
     * Gets the well-known encoding (name) as defined in RFC 3551 "RTP Profile
     * for Audio and Video Conferences with Minimal Control" corresponding to a
     * given JMF-specific encoding.
     *
     * @param jmfEncoding the JMF encoding to get the corresponding well-known
     * encoding of
     * @return the well-known encoding (name) as defined in RFC 3551 "RTP
     * Profile for Audio and Video Conferences with Minimal Control"
     * corresponding to <tt>jmfEncoding</tt> if any; otherwise, <tt>null</tt>
     */
    public static String jmfEncodingToEncoding(String jmfEncoding)
    {
        return jmfEncodingToEncodings.get(jmfEncoding);
    }

    /**
     * Convert the input H.264 profile to the maximum supported resolution
     * supported by that profile, or null if the locally-supported maximum
     * resolution should be used.
     *
     * @param profile The H.264 profile to check
     * @return The maximum supported resolution of that profile
     */
    public static Dimension h264ProfileToDimension(String profile)
    {
        if (!checkH264ProfileValid(profile))
        {
            // The profile isn't valid so just use the local default - i.e.
            // return null.
            return null;
        }

        // The profile IDC is the 3rd byte of the profile string (i.e. the last
        // two characters of the string converted to a byte).
        byte idc = (byte)((Character.digit(profile.charAt(4), 16) << 4) +
                           Character.digit(profile.charAt(5), 16));
        Dimension dimension;
        if (idc < H264_PROFILE_IDC_1_1)
        {
            // Less than level 1.1 - max dimension is 176x144
            dimension = new java.awt.Dimension(176, 144);
        }
        else if (idc < H264_PROFILE_IDC_2_1)
        {
            // Less than level 2.1 - max dimension is 352x288
            dimension = new java.awt.Dimension(352, 288);
        }
        else if (idc < H264_PROFILE_IDC_2_2)
        {
            // Less than level 2.2 - max dimension is 352x576
            dimension = new java.awt.Dimension(352, 576);
        }
        else if (idc < H264_PROFILE_IDC_3_1)
        {
            // Less than level 3.1 - max dimension is 720x576
            dimension = new java.awt.Dimension(720, 576);
        }
        else
        {
            // The level is at least as good as our maximum supported level, so
            // just return null to indicate any locally-supported dimension is
            // fine.
            return null;
        }

        return dimension;
    }

    /**
     * Check whether the input H.264 profiles are compatible, which means (for
     * our purposes) that they share the same maximum supported resolution, so
     * that we can send video to a remote with that resolution.
     *
     * @param profile1 The first H.264 profile to check
     * @param profile2 The second H.264 profile to check
     * @return Whether the profiles match
     */
    public static boolean h264ProfilesCompatible(String profile1,
                                                 String profile2)
    {
        if (!checkH264ProfileValid(profile1) ||
            !checkH264ProfileValid(profile2))
        {
            // At least one of the profiles is invalid so assume they aren't
            // compatible.
            return false;
        }

        // The profile IDC is the 3rd byte of the profile string (i.e. the last
        // two characters of the string converted to a byte).
        byte idc1 = (byte)((Character.digit(profile1.charAt(4), 16) << 4) +
                            Character.digit(profile1.charAt(5), 16));
        byte idc2 = (byte)((Character.digit(profile2.charAt(4), 16) << 4) +
                            Character.digit(profile2.charAt(5), 16));
        if (idc1 == idc2)
        {
            return true;
        }
        if (idc1 < H264_PROFILE_IDC_1_1)
        {
            // Less than level 1.1
            return (idc2 < H264_PROFILE_IDC_1_1);
        }
        else if (idc1 < H264_PROFILE_IDC_2_1)
        {
            // Between levels 1.1 and 2.1
            return ((idc2 < H264_PROFILE_IDC_2_1) &&
                    (idc2 >= H264_PROFILE_IDC_1_1));
        }
        else if (idc1 < H264_PROFILE_IDC_2_2)
        {
            // Between levels 2.1 and 2.2
            return ((idc2 < H264_PROFILE_IDC_2_2) &&
                    (idc2 >= H264_PROFILE_IDC_2_1));
        }
        else if (idc1 < H264_PROFILE_IDC_3_1)
        {
            // Between levels 2.2 and 3.1
            return ((idc2 < H264_PROFILE_IDC_3_1) &&
                    (idc2 >= H264_PROFILE_IDC_2_2));
        }

        // The first level is at least as good as our maximum supported level,
        // just check whether the second is too.
        return (idc2 >= H264_PROFILE_MAX_SUPPORTED);
    }

    /**
     * Check the passed in profile to confirm it's a valid format - it should
     * be 3 bytes long, with the level IDC byte at the end, determining the
     * supported resolution.
     *
     * @param profile The profile to check
     * @return Whether it's valid
     */
    private static boolean checkH264ProfileValid(String profile)
    {
        if (StringUtils.isNullOrEmpty(profile) ||
            profile.length() != 6)
        {
            logger.debug("Failed to parse H.264 profile: " + profile);
            return false;
        }

        return true;
    }
}
