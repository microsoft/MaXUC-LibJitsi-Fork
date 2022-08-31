/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.neomedia.codec.audio.speex;

/**
 * Provides the interface to the native Speex library (just the renderer, not
 * encoding and decoding).
 *
 * @author Lubomir Marinov
 */
public final class Speex
{
    public static final int SPEEX_MODEID_NB = 0;

    public static final int SPEEX_RESAMPLER_QUALITY_VOIP = 3;

    static
    {
        System.loadLibrary("jnspeex");
    }

    public static void assertSpeexIsFunctional()
    {
        speex_lib_get_mode(SPEEX_MODEID_NB);
    }

    public static native long speex_lib_get_mode(int mode);

    public static native void speex_resampler_destroy(long state);

    public static native long speex_resampler_init(
            int nb_channels,
            int in_rate,
            int out_rate,
            int quality,
            long err);

    public static native int speex_resampler_process_interleaved_int(
            long state,
            byte[] in, int inOffset, int in_len,
            byte[] out, int outOffset, int out_len);

    public static native int speex_resampler_set_rate(
            long state,
            int in_rate,
            int out_rate);

    /**
     * Prevents the creation of <tt>Speex</tt> instances.
     */
    private Speex()
    {
    }
}
