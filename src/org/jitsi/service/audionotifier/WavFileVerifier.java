// Copyright (c) Microsoft Corporation. All rights reserved.
package org.jitsi.service.audionotifier;

/**
 * Utility class for checking whether a wav file is playable by Accession.
 * We simply check the wav file format information against a preconfigured list
 * of allowed values.
 */
public class WavFileVerifier
{
    /**
     * The supported sample rates, in Hz.
     */
    public static final int[] SUPPORTED_SAMPLE_RATES_HZ = new int[]
    {
        8000,
        11025,
        22050,
        44100,
        48000
    };

    /**
     * Supported bit depths (i.e. bits per single-channel sample).
     */
    public static final int[] SUPPORTED_BIT_DEPTHS = new int[]
    {
        16  // 16-bit samples, (usually) signed
    };

    /**
     * The minimum number of channels allowed in a supported WAV file.
     */
    public static final int MIN_NUM_CHANNELS = 1;

    /**
     * The maximum number of channels allowed in a supported WAV file.
     */
    public static final int MAX_NUM_CHANNELS = 5;

    /**
     * Throws an exception if the given WavFileData is not supported by
     * Accession. Otherwise, just returns.
     * @param wavData The WavData to check
     * @throws UnsupportedWavFileException If the wav file has an unsupported
     * format
     */
    public static void assertWavFileIsSupported(WavData wavData)
        throws UnsupportedWavFileException
    {
        if (!wavData.isPCM())
        {
            throw new UnsupportedEncodingException();
        }
        else if (!contains(SUPPORTED_SAMPLE_RATES_HZ, wavData.getSampleRate()))
        {
            throw new UnsupportedSampleRateException(wavData.getSampleRate());
        }
        else if (!contains(SUPPORTED_BIT_DEPTHS, wavData.getBitDepth()))
        {
            throw new UnsupportedBitDepthException(wavData.getBitDepth());
        }
        else if (wavData.getNumChannels() < MIN_NUM_CHANNELS ||
                 wavData.getNumChannels() > MAX_NUM_CHANNELS)
        {
            throw new UnsupportedNumberOfChannelsException(
                                                      wavData.getNumChannels());
        }
    }

    /**
     * Indicates that the parsed WAV file is unsupported by Accession, and will
     * probably be unable to be played.
     */
    public static class UnsupportedWavFileException extends Exception
    {
        private static final long serialVersionUID = 0L;

        /**
         * The specific value of the parameter which was invalid; or null if not
         * applicable.
         */
        public final Object mInvalidValue;

        public UnsupportedWavFileException()
        {
            this(null);
        }

        public UnsupportedWavFileException(Object invalidValue)
        {
            mInvalidValue = invalidValue;
        }
    }

    /**
     * Indicates that the parsed WAV file has an unsupported sample rate.
     */
    public static class UnsupportedSampleRateException
        extends UnsupportedWavFileException
    {
        private static final long serialVersionUID = 0L;

        public UnsupportedSampleRateException(int sampleRate)
        {
            super(sampleRate);
        }
    }

    /**
     * Indicates that the parsed WAV file uses an unsupported number of bits
     * per audio sample.
     */
    public static class UnsupportedBitDepthException
        extends UnsupportedWavFileException
    {
        private static final long serialVersionUID = 0L;

        public UnsupportedBitDepthException(int bitDepth)
        {
            super(bitDepth);
        }
    }

    /**
     * Indicates that the parsed WAV file has either too many channels, or too
     * few, and is therefore unsupported.
     */
    public static class UnsupportedNumberOfChannelsException
        extends UnsupportedWavFileException
    {
        private static final long serialVersionUID = 0L;

        public UnsupportedNumberOfChannelsException(int numChannels)
        {
            super(numChannels);
        }
    }

    /**
     * Indicates that the parsed WAV file uses an encoding we do not support,
     * e.g. A-law or mu-law.
     */
    public static class UnsupportedEncodingException
        extends UnsupportedWavFileException
    {
        private static final long serialVersionUID = 0L;
    }

    /**
     * Utility method to test if a given value is present in an array of ints.
     * @param xs The array of ints
     * @param y The value whose membership of the array we're testing
     * @return True if y is present in xs, false otherwise.
     */
    private static boolean contains(int[] xs, int y)
    {
        for (int x : xs)
        {
            if (x == y)
                return true;
        }

        return false;
    }
}
