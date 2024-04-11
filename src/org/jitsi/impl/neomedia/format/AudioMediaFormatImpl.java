/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.format;

import java.util.*;

import javax.media.*;
import javax.media.format.*;

import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.format.*;

/**
 * Implements <tt>AudioMediaFormat</tt> for the JMF <tt>AudioFormat</tt>.
 *
 * @author Lubomir Marinov
 */
public class AudioMediaFormatImpl
    extends MediaFormatImpl<AudioFormat>
    implements AudioMediaFormat
{
    /**
     * Initializes a new <tt>AudioMediaFormatImpl</tt> instance which is to
     * provide an implementation of <tt>AudioMediaFormat</tt> for a specific
     * JMF <tt>AudioFormat</tt>.
     *
     * @param format the JMF <tt>AudioFormat</tt> the new instance is to wrap
     * and provide an implementation of <tt>AudioMediaFormat</tt> for
     */
    public AudioMediaFormatImpl(AudioFormat format)
    {
        this(format, null, null);
    }

    /**
     * Initializes a new <tt>AudioMediaFormatImpl</tt> instance which is to
     * provide an implementation of <tt>AudioMediaFormat</tt> for a specific
     * JMF <tt>AudioFormat</tt> and to have a specific set of format-specific
     * parameters.
     *
     * @param format the JMF <tt>AudioFormat</tt> the new instance is to wrap
     * and provide an implementation of <tt>AudioMediaFormat</tt> for
     * @param formatParameters  the set of format-specific parameters of the new
     * instance
     * @param advancedParameters  the set of format-specific parameters of the new
     * instance
     */
    AudioMediaFormatImpl(
            AudioFormat format,
            Map<String, String> formatParameters,
            Map<String, String> advancedParameters)
    {
        super(fixChannels(format), formatParameters, advancedParameters);
    }

    /**
     * Gets an <tt>AudioFormat</tt> instance which matches a specific
     * <tt>AudioFormat</tt> and has 1 channel if the specified
     * <tt>AudioFormat</tt> has its number of channels not specified.
     *
     * @param format the <tt>AudioFormat</tt> to get a match of
     * @return if the specified <tt>format</tt> has a specific number of
     * channels, <tt>format</tt>; otherwise, a new <tt>AudioFormat</tt> instance
     * which matches <tt>format</tt> and has 1 channel
     */
    private static AudioFormat fixChannels(AudioFormat format)
    {
        if (Format.NOT_SPECIFIED == format.getChannels())
            format
                = (AudioFormat)
                    format
                        .intersects(
                            new AudioFormat(
                                    format.getEncoding(),
                                    format.getSampleRate(),
                                    format.getSampleSizeInBits(),
                                    1));
        return format;
    }

    /**
     * Gets the number of audio channels associated with this
     * <tt>AudioMediaFormat</tt>.
     *
     * @return the number of audio channels associated with this
     * <tt>AudioMediaFormat</tt>
     * @see AudioMediaFormat#getChannels()
     */
    public int getChannels()
    {
        int channels = format.getChannels();

        return (Format.NOT_SPECIFIED == channels) ? 1 : channels;
    }

    /**
     * Gets the clock rate associated with this <tt>MediaFormat</tt>.
     *
     * @return the clock rate associated with this <tt>MediaFormat</tt>
     * @see MediaFormat#getClockRate()
     */
    public double getClockRate()
    {
        return format.getSampleRate();
    }

    /**
     * Gets the type of this <tt>MediaFormat</tt> which is
     * {@link MediaType#AUDIO} for <tt>AudioMediaFormatImpl</tt> instances.
     *
     * @return the <tt>MediaType</tt> that this format represents and which is
     * <tt>MediaType.AUDIO</tt> for <tt>AudioMediaFormatImpl</tt> instances
     * @see MediaFormat#getMediaType()
     */
    public final MediaType getMediaType()
    {
        return MediaType.AUDIO;
    }
}
