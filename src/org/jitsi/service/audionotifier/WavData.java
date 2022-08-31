// Copyright (c) Microsoft Corporation. All rights reserved.
package org.jitsi.service.audionotifier;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;

import org.jitsi.util.*;

/**
 * Parses and stores the MetaData for a wav file located on disc.
 */
public class WavData
{
    private static final Logger logger = Logger.getLogger(WavData.class);

    /**
     * Abstract representation of the FMT chunk in the wav file data.
     * Stores all format information for the wav file.
     */
    private final FmtChunk mFmtChunk;

    /**
     * The size of the main body of the wav file, excluding the RIFF header.
     */
    private final int mWaveChunkSize;

    /**
     * Parse the specified file, and store off the metadata.
     * @param file the wav file to parse
     * @throws IOException if the file does not exist or cannot be read
     * @throws InvalidWavDataException if the wav file is invalid or corrupt
     */
    public WavData(File file) throws IOException, InvalidWavDataException
    {
        try (FileInputStream fis = new FileInputStream(file))
        {
            mWaveChunkSize = parseHeader(fis);
            locateFmtChunk(fis);
            mFmtChunk = new FmtChunk(fis);
        }
    }

    /**
     * @return true if the wav file is in PCM (i.e. raw audio) format.
     */
    public boolean isPCM()
    {
        return mFmtChunk.mIsPCM;
    }

    /**
     * @return the number of audio channels in the wav file.
     */
    public int getNumChannels()
    {
        return mFmtChunk.mNumChannels;
    }

    /**
     * Get the sample rate of the audio data. Note that this term is ambiguous;
     * here we mean the 'frame rate', i.e. the number of discrete sampling
     * points in time in a single second of playback - as opposed to the
     * number of distinct audio samples across all channels in a second of
     * playback. Therefore the value returned will be <num_channels> times
     * smaller than a value obtained using the latter definition.
     * @return the sample rate of the audio data, in Hz.
     */
    public int getSampleRate()
    {
        return mFmtChunk.mSampleRate;
    }

    /**
     * @return the number of bytes of raw audio data per second of audio
     */
    public int getDataRate()
    {
        return mFmtChunk.mDataRate;
    }

    /**
     * @return the number of bits per sample of audio data in a single channel.
     * Equal to the number of bits per frame, divided by the number of channels.
     */
    public int getBitDepth()
    {
        return mFmtChunk.mBitsPerSample;
    }

    /**
     * Parse the header at the start of the file, verify that it matches the
     * expected format for a WAV file header, and extract the size of the data
     * contained within.
     * Note that this method consumes the header, including the 'WAVE' chunkID.
     * @param is an InputStream, with the header starting at byte 0.
     * @return the number of bytes of following wav data
     * @throws InvalidWavDataException if the format of the header is invalid
     * @throws IOException if the header cannot be read
     */
    private int parseHeader(InputStream is)
            throws InvalidWavDataException, IOException
    {
        String chunkID = readString(is, 4);
        if (!chunkID.equals("RIFF"))
        {
            throw new InvalidWavDataException("WAV file starts with '" +
                                              chunkID + "' - expected 'RIFF'");
        }

        int waveChunkSize = (int)readInteger(is, 4);

        String waveID = readString(is, 4);
        if (!waveID.equals("WAVE"))
        {
            throw new InvalidWavDataException("WAVE chunk starts with '" +
                                              chunkID + "' - expected 'WAVE'");
        }

        return waveChunkSize;
    }

    /**
     * Consumes bytes from the given InputStream until a fmt chunk is found.
     * The size of the fmt chunk is then consumed, and we return, leaving the
     * InputStream such that byte 0 marks the start of the format data.
     * @param is an InputStream, with byte 0 being the first byte of the first
     * WAV chunk of the file (i.e. having skipped the initial RIFF header and
     * WAVE chunk ID)
     * @throws InvalidWavDataException if the stream ends in the middle of a
     * chunk, or if no fmt chunk is found in the file
     * @throws IOException if we fail to read from the stream
     */
    private void locateFmtChunk(InputStream is)
        throws InvalidWavDataException, IOException
    {
        // bytesRead counts the number of bytes of the WAVE chunk we have read.
        // We've already read the 4-byte chunkID "WAVE" - so we start counting
        // at 4.
        long bytesRead = 4;

        while (bytesRead < mWaveChunkSize)
        {
            String chunkID = readString(is, 4);
            if (chunkID.equals("fmt "))
            {
                // If the chunk ID is "fmt " (space intended), then the chunk is
                // the format chunk, as we can return. We skip the four bytes
                // comprising the chunk size, so that the next read call
                // returns the actual data within the format chunk; the chunk
                // size itself is not used.
                logger.debug("Found FMT chunk located at " + bytesRead);
                safeSkip(is, 4);
                return;
            }
            else
            {
                // The chunk ID is not "fmt ", so this is not a format chunk.
                // Therefore we're not interested in it, and we just skip it
                // entirely. We know how many bytes to skip because the first
                // 4 bytes after the ID always hold the size of the chunk.
                int chunkSize = (int)readInteger(is, 4);
                safeSkip(is, chunkSize);
                bytesRead += chunkSize;
            }
        }

        throw new InvalidWavDataException("No fmt chunk found");
    }

    /**
     * Utility method to read a specified number of bytes from the given stream,
     * encapsulating edge case handling. The method guarantees to read precisely
     * the specified number of bytes, or throw an exception.
     * @param is the stream from which to read
     * @param numBytes the number of bytes to read
     * @return the bytes read from the stream
     * @throws InvalidWavDataException if the stream ends prematurely
     * @throws IOException if we fail to read from the stream
     */
    private static byte[] readBytes(InputStream is, int numBytes)
        throws InvalidWavDataException, IOException
    {
        if (numBytes <= 0)
        {
            throw new IllegalArgumentException("Cannot read " + numBytes +
                                               " bytes from input stream");
        }

        int bytesRead = 0;
        byte[] buffer = new byte[numBytes];

        // InputStream,read(...) isn't guaranteed to read the requested number
        // of bytes, so we loop until it has.
        while (bytesRead < numBytes)
        {
            int retcode = is.read(buffer, bytesRead, numBytes - bytesRead);
            if (retcode == -1)
            {
                throw new InvalidWavDataException("Wav file ended prematurely");
            }

            bytesRead += retcode;
        }

        return buffer;
    }

    /**
     * Utility method to read an integer from the given InputStream, assuming
     * little-endian ordering.
     * @param is the stream from which to read
     * @param numBytes the size of the integer, in bytes.
     * @return the parsed integer, as a long. Downcasting will always be safe
     * if the cast respects the size passed into numBytes.
     * @throws InvalidWavDataException if the stream ends prematurely
     * @throws IOException if we fail to read from the stream
     */
    private static long readInteger(InputStream is, int numBytes)
        throws InvalidWavDataException, IOException
    {
        final byte[] bytes = readBytes(is, numBytes);
        final ByteBuffer buff = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);

        switch (numBytes)
        {
        case 1:
            return buff.get(0);
        case 2:
            return buff.getShort(0);
        case 4:
            return buff.getInt(0);
        case 8:
            return buff.getLong(0);
        default:
            throw new IllegalArgumentException("Illegal integer read size " +
                numBytes + " does not correspond to a primitive integer type.");
        }
    }

    /**
     * Utility method to read an ascii string of a specified size from the given
     * InputStream. This method guarantees to either read the requisite number
     * of bytes, or throw an exception.
     * @param is the stream from which to read
     * @param numBytes the size of the string requested, in bytes
     * @return the parsed String
     * @throws InvalidWavDataException if the stream ends prematurely
     * @throws IOException if we fail to read from the stream
     */
    private static String readString(InputStream is, int numBytes)
        throws InvalidWavDataException, IOException
    {
        return new String(readBytes(is, numBytes), Charset.forName("US-ASCII"));
    }

    /**
     * Utility method to skip precisely numBytes of the given InputStream, or
     * fail with an exception.
     * @param is the stream in which bytes will be skipped
     * @param numBytes the number of bytes to skip
     * @throws InvalidWavDataException if the stream ends prematurely
     * @throws IOException if the underlying skip() call fails
     */
    private static void safeSkip(InputStream is, long numBytes)
        throws InvalidWavDataException, IOException
    {
        if (numBytes < 0)
        {
            throw new IllegalArgumentException("Cannot skip " + numBytes +
                                               " bytes of input stream");
        }

        // InputStream.skip() isn't guaranteed to skip the requested number of
        // bytes in one call, so loop until it has.
        while (numBytes > 0)
        {
            long retcode = is.skip(numBytes);
            if (retcode == -1)
            {
                throw new InvalidWavDataException("Wav file ended prematurely");
            }

            numBytes -= retcode;
        }
    }

    /**
     * Thrown if we fail to parse the specified WAV file, e.g. because it ends
     * prematurely, or because it is not in the expected format.
     */
    public static class InvalidWavDataException extends Exception
    {
        private static final long serialVersionUID = 0L;

        public InvalidWavDataException(String s)
        {
            super(s);
        }
    }

    /**
     * Abstract representation of a FMT chunk within a WAV file.
     */
    private static class FmtChunk
    {
        /**
         * True if the FMT chunk describes the WAV file as PCM-formatted, i.e.
         * unencoded raw audio. False otherwise.
         */
        public final boolean mIsPCM;

        /**
         * The number of distinct channels in the WAV file, according to the FMT
         * chunk.
         */
        public final int mNumChannels;

        /**
         * The sample rate of the WAV file, in Hz.
         * See WavFileData.getSampleRate() for a more precise definition.
         */
        public final int mSampleRate;

        /**
         * The number of unencoded bytes representing one second of audio
         * playback.
         */
        public final int mDataRate;

        /**
         * The number of bits per sample of audio data in a single channel.
         * Equal to the number of bits per frame, divided by the number of
         * channels.
         */
        public final int mBitsPerSample;

        /**
         * Parse the FMT chunk beginning at byte 0 of the given InputStream.
         * The stream will be left in an undefined state following parsing, and
         * should not be subsequently reused.
         * @param is the stream containing the WAV data
         * @throws IOException if we fail to read from the stream
         * @throws InvalidWavDataException if the FMT chunk terminates
         * prematurely
         */
        public FmtChunk(InputStream is)
            throws IOException, InvalidWavDataException
        {
            // The fmt chunk starts with a 2-byte format code.
            // 0x1 indicates PCM data.
            mIsPCM = readInteger(is, 2) == 1;

            mNumChannels = (int)readInteger(is, 2);
            mSampleRate = (int)readInteger(is, 4);
            mDataRate = (int)readInteger(is, 4);

            // Skip the 2-byte block size field. It's always equal to
            // mBitsPerSample * mChannels * 8, so the caller will never need it.
            safeSkip(is, 2);

            mBitsPerSample = (int)readInteger(is, 2);
        }
    }
}
