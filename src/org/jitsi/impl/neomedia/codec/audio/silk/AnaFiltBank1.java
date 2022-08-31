/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.codec.audio.silk;

/**
 * Split signal into two decimated bands using first-order allpass filters.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
public class AnaFiltBank1
{
    /* Coefficients for 2-band filter bank based on first-order allpass filters */
    // old
    static final short[] A_fb1_20 = {  5394 << 1 };
    static final short[] A_fb1_21 = { (short)(20623 << 1) };        /* wrap-around to negative number is intentional */

    /**
     * Split signal into two decimated bands using first-order allpass filters.
     * @param in Input signal [N].
     * @param S State vector [2].
     * @param outL Low band [N/2].
     * @param outH High band [N/2].
     * @param N Number of input samples.
     */
    static void SKP_Silk_ana_filt_bank_1
    (
        short[]      in,          /* I:   Input signal [N]        */
        int[]        S,           /* I/O: State vector [2]        */
        short[]      outL,        /* O:   Low band [N/2]          */
        short[]      outH,        /* O:   High band [N/2]         */
        final int    N            /* I:   Number of input samples */
    )
    {
        int      k, N2 = N >> 1;
        int    in32, X, Y, out_1, out_2;

        /* Internal variables and state are in Q10 format */
        for( k = 0; k < N2; k++ )
        {
            /* Convert to Q10 */
            in32 = in[ 2 * k ] << 10;

            /* All-pass section for even input sample */
            Y      = in32 - S[ 0 ];
            X      = Macros.SKP_SMLAWB( Y, Y, A_fb1_21[ 0 ] );
            out_1  = S[ 0 ] + X;
            S[ 0 ] = in32 + X;

            /* Convert to Q10 */
            in32 = in[ 2 * k + 1 ] << 10;

            /* All-pass section for odd input sample, and add to output of previous section */
            Y      = in32 - S[ 1 ];
            X      = Macros.SKP_SMULWB( Y, A_fb1_20[ 0 ] );
            out_2  = S[ 1 ] + X;
            S[ 1 ] = in32 + X;

            /* Add/subtract, convert back to int16 and store to output */
            outL[ k ] = (short)SigProcFIX.SKP_SAT16( SigProcFIX.SKP_RSHIFT_ROUND( out_2 + out_1, 11 ) );
            outH[ k ] = (short)SigProcFIX.SKP_SAT16( SigProcFIX.SKP_RSHIFT_ROUND( out_2 - out_1, 11 ) );
        }
    }
}
