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
public class Decimate2CoarsestFLP
{
    /* coefficients for coarsest 2-fold resampling */
    /* note that these differ from the interpolator with the same filter orders! */
    static final float A20cst_FLP[  ] = {0.289001464843750f};
    static final float A21cst_FLP[  ] = {0.780487060546875f};

    /**
     * downsample by a factor 2, coarsest.
     * @param in 16 kHz signal [2*len].
     * @param S state vector [2].
     * @param out 8 kHz signal [len].
     * @param scratch scratch memory [3*len].
     * @param len number of OUTPUT samples.
     */
    static void SKP_Silk_decimate2_coarsest_FLP(
        float[]           in,        /* I:   16 kHz signal [2*len]       */
        float[]           S,         /* I/O: state vector [2]            */
        float[]           out,       /* O:   8 kHz signal [len]          */
        float[]           scratch,   /* I:   scratch memory [3*len]      */
        final int         len         /* I:   number of OUTPUT samples    */
    )
    {
        int k;

        /* de-interleave allpass inputs */
        for ( k = 0; k < len; k++ )
        {
            scratch[ k ]       = in[ 2 * k + 0 ];
            scratch[ k + len ] = in[ 2 * k + 1 ];
        }

        /* allpass filters */
        AllpassIntFLP.SKP_Silk_allpass_int_FLP( scratch,0,   S,0, A21cst_FLP[ 0 ], scratch,2 * len, len );
        AllpassIntFLP.SKP_Silk_allpass_int_FLP( scratch,len, S,1, A20cst_FLP[ 0 ], scratch,0,       len );

        /* add two allpass outputs */
        for ( k = 0; k < len; k++ )
        {
            out[ k ] = 0.5f * ( scratch[ k ] + scratch[ k + 2 * len ] );
        }
    }
}
