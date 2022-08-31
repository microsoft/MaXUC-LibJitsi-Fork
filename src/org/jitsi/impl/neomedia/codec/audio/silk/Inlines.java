/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.codec.audio.silk;

public class Inlines
{
    /**
     * get number of leading zeros and fractional part (the bits right after the leading one).
     * @param in
     * @param lz
     * @param frac_Q7
     */
    static void SKP_Silk_CLZ_FRAC(int in,            /* I: input */
                                  int[] lz,          /* O: number of leading zeros */
                                  int[] frac_Q7)     /* O: the 7 bits right after the leading one */
    {
        int lzeros = Integer.numberOfLeadingZeros(in);

        lz[0] = lzeros;
        frac_Q7[0] = SigProcFIX.SKP_ROR32(in, 24 - lzeros) & 0x7f;
    }

    /**
     * Approximation of square root
     * Accuracy: < +/- 10% for output values > 15
     *           < +/- 2.5% for output values > 120
     * @param x
     * @return
     */
    static int SKP_Silk_SQRT_APPROX(int x, final BaseMem mem)
    {
        int y;
        int[] lz = mem.zero(mem.SKP_Silk_SQRT_APPROX__lz);
        int[] frac_Q7 = mem.zero(mem.SKP_Silk_SQRT_APPROX__frac_Q7);

        if( x <= 0 )
        {
            return 0;
        }

        SKP_Silk_CLZ_FRAC(x, lz, frac_Q7);

        if( (lz[0] & 1) != 0 )
        {
            y = 32768;
        }
        else
        {
            y = 46214;        /* 46214 = sqrt(2) * 32768 */
        }

        /* get scaling right */
        y >>= (lz[0]>>1);

        /* increment using fractional part of input */
        y = Macros.SKP_SMLAWB(y, y, Macros.SKP_SMULBB(213, frac_Q7[0]));

        return y;
    }

    /**
     * Divide two int32 values and return result as int32 in a given Q-domain.
     * @param a32 numerator (Q0)
     * @param b32 denominator (Q0)
     * @param Qres Q-domain of result (>= 0)
     * @return returns a good approximation of "(a32 << Qres) / b32"
     */
    static int SKP_DIV32_varQ         /* O    returns a good approximation of "(a32 << Qres) / b32" */
    (
        final int        a32,         /* I    numerator (Q0)                  */
        final int        b32,         /* I    denominator (Q0)                */
        final int        Qres         /* I    Q-domain of result (>= 0)       */
    )
    {
        int   a_headrm, b_headrm, lshift;
        int b32_inv, a32_nrm, b32_nrm, result;

        assert( b32 != 0 );
        assert( Qres >= 0 );

        /* Compute number of bits head room and normalize inputs */
        a_headrm = Integer.numberOfLeadingZeros( Math.abs(a32) ) - 1;
        a32_nrm = a32<<a_headrm;                                    /* Q: a_headrm                    */
        b_headrm = Integer.numberOfLeadingZeros( Math.abs(b32) ) - 1;
        b32_nrm = b32<<b_headrm;                                    /* Q: b_headrm                    */

        /* Inverse of b32, with 14 bits of precision */
        b32_inv = (Integer.MAX_VALUE >> 2) / (b32_nrm>>16) ;  /* Q: 29 + 16 - b_headrm        */

        /* First approximation */
        result = Macros.SKP_SMULWB(a32_nrm, b32_inv);                                  /* Q: 29 + a_headrm - b_headrm    */

        /* Compute residual by subtracting product of denominator and first approximation */
        a32_nrm -= SigProcFIX.SKP_SMMUL(b32_nrm, result)<<3;           /* Q: a_headrm                    */

        /* Refinement */
        result = Macros.SKP_SMLAWB(result, a32_nrm, b32_inv);                          /* Q: 29 + a_headrm - b_headrm    */

        /* Convert to Qres domain */
        lshift = 29 + a_headrm - b_headrm - Qres;
        if( lshift <= 0 )
        {
            return SigProcFIX.SKP_LSHIFT_SAT32(result, -lshift);
        }
        else
        {
            if( lshift < 32)
            {
                return result>>lshift;
            }
            else
            {
                /* Avoid undefined result */
                return 0;
            }
        }
    }

    /**
     * Invert int32 value and return result as int32 in a given Q-domain.
     * @param b32 denominator (Q0)
     * @param Qres Q-domain of result (> 0)
     * @return returns a good approximation of "(1 << Qres) / b32"
     */
    static int SKP_INVERSE32_varQ         /* O    returns a good approximation of "(1 << Qres) / b32" */
    (
        final int        b32,             /* I    denominator (Q0)                */
        final int        Qres             /* I    Q-domain of result (> 0)        */
    )
    {
        int   b_headrm, lshift;
        int b32_inv, b32_nrm, err_Q32, result;

        assert( b32 != 0 );
        assert( Qres > 0 );

        /* Compute number of bits head room and normalize input */
        b_headrm = Integer.numberOfLeadingZeros( Math.abs(b32) ) - 1;
        b32_nrm = b32<<b_headrm;                                    /* Q: b_headrm                */

        /* Inverse of b32, with 14 bits of precision */
        b32_inv = (Integer.MAX_VALUE >> 2) / (b32_nrm>>16);  /* Q: 29 + 16 - b_headrm    */

        /* First approximation */
        result = b32_inv<<16;                                       /* Q: 61 - b_headrm            */

        /* Compute residual by subtracting product of denominator and first approximation from one */
        err_Q32 = -Macros.SKP_SMULWB(b32_nrm, b32_inv)<<3;         /* Q32                        */

        /* Refinement */
        result = Macros.SKP_SMLAWW(result, err_Q32, b32_inv);                          /* Q: 61 - b_headrm            */

        /* Convert to Qres domain */
        lshift = 61 - b_headrm - Qres;
        if( lshift <= 0 )
        {
            return SigProcFIX.SKP_LSHIFT_SAT32(result, -lshift);
        }
        else
        {
            if( lshift < 32)
            {
                return result>>lshift;
            }
            else
            {
                /* Avoid undefined result */
                return 0;
            }
        }
    }
}
