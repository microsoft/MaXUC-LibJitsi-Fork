/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.codec.audio.silk;

/**
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
public class Decimate2CoarseFLP
{
    /* coefficients for coarser 2-fold resampling */
    static final float A20c_FLP[  ] = {0.064666748046875f, 0.508514404296875f};
    static final float A21c_FLP[  ] = {0.245666503906250f, 0.819732666015625f};

    /**
     * downsample by a factor 2, coarser.
     * @param in 16 kHz signal [2*len].
     * @param S state vector [4].
     * @param out 8 kHz signal [len]
     * @param scratch scratch memory [3*len].
     * @param len number of OUTPUT samples.
     */
    static void SKP_Silk_decimate2_coarse_FLP
    (
        float[]        in,        /* I:   16 kHz signal [2*len]       */
        float[]        S,         /* I/O: state vector [4]            */
        float[]        out,       /* O:   8 kHz signal [len]          */
        float[]        scratch,   /* I:   scratch memory [3*len]      */
        final int      len        /* I:   number of OUTPUT samples    */
    )
    {
        int k;

        /* de-interleave allpass inputs */
        for ( k = 0; k < len; k++)
        {
            scratch[ k ]       = in[ 2 * k ];
            scratch[ k + len ] = in[ 2 * k + 1 ];
        }

        /* allpass filters */
        AllpassIntFLP.SKP_Silk_allpass_int_FLP( scratch,0, S,0, A21c_FLP[ 0 ], scratch,2 * len, len );
        AllpassIntFLP.SKP_Silk_allpass_int_FLP( scratch,2 * len, S,1, A21c_FLP[ 1 ], scratch,0, len );

        AllpassIntFLP.SKP_Silk_allpass_int_FLP( scratch,len, S,2, A20c_FLP[ 0 ], scratch,2 * len, len );
        AllpassIntFLP.SKP_Silk_allpass_int_FLP( scratch,2 * len, S,3, A20c_FLP[ 1 ], scratch,len, len );

        /* add two allpass outputs */
        for ( k = 0; k < len; k++ )
        {
            out[ k ] = 0.5f * ( scratch[ k ] + scratch[ k + len ] );
        }
    }
}
