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
class SigProcFIXConstants
{
    /**
     * max order of the LPC analysis in schur() and k2a().
     */
    static final int SKP_Silk_MAX_ORDER_LPC =           16; /* max order of the LPC analysis in schur() and k2a()    */

    /* Pitch estimator */
    static final int SKP_Silk_PITCH_EST_MIN_COMPLEX =       0;
    static final int SKP_Silk_PITCH_EST_MID_COMPLEX =       1;
    static final int SKP_Silk_PITCH_EST_MAX_COMPLEX =       2;

    /* parameter defining the size and accuracy of the piecewise linear  */
    /* cosine approximatin table.                                        */
    static final int LSF_COS_TAB_SZ_FIX =     128;
    /* rom table with cosine values */
//    (to see rom table value, refer to LSFCosTable.java)
}

public class SigProcFIX
    extends SigProcFIXConstants
{
    /**
     * Rotate a32 right by 'rot' bits. Negative rot values result in rotating
     * left. Output is 32bit int.
     *
     * @param a32
     * @param rot
     * @return
     */
    static int SKP_ROR32( int a32, int rot )
    {
        if(rot <= 0)
            return ((a32 << -rot) | (a32 >>> (32 + rot)));
        else
            return ((a32 << (32 - rot)) | (a32 >>> rot));
    }

    /* fixed point */

    /**
     * (a32 * b32) output have to be 32bit int
     */
    static int SKP_MUL(int a32, int b32)
    {
        return a32*b32;
    }

    /**
     *  a32 + (b32 * c32) output have to be 32bit int
     * @param a32
     * @param b32
     * @param c32
     * @return
     */
    static int SKP_MLA(int a32, int b32, int c32)
    {
        return a32 + b32*c32;
    }

    /**
     * (a32 * b32)
     * @param a32
     * @param b32
     * @return
     */
    static long SKP_SMULL(int a32, int b32)
    {
        return ((long)(a32) * /*(long)*/(b32));
    }

    // multiply-accumulate macros that allow overflow in the addition (ie, no asserts in debug mode)

    /**
     * SKP_SMLABB(a32, b32, c32)
     * @param a32
     * @param b32
     * @param c32
     * @return
     */
    static int SKP_SMLABB_ovflw(int a32, int b32, int c32)
    {
        return ((a32) + (((short)(b32))) * ((short)(c32)));
    }
    /**
     * SKP_SMLAWB(a32, b32, c32)
     * @param a32
     * @param b32
     * @param c32
     * @return
     */
    static int SKP_SMLAWB_ovflw(int a32, int b32, int c32)
    {
        return ((a32) + ((((b32) >> 16) * ((short)(c32))) + ((((b32) & 0x0000FFFF) * ((short)(c32))) >> 16)));
    }

    static int SKP_SAT16(int a)
    {
        return ((a) > Short.MAX_VALUE ? Short.MAX_VALUE : ((a) < Short.MIN_VALUE ? Short.MIN_VALUE : (a)));
    }

    /* Add with saturation for positive input values */
    static int SKP_ADD_POS_SAT32(int a, int b)
    {
        return ((((a)+(b)) & 0x80000000) != 0 ? Integer.MAX_VALUE : ((a)+(b)));
    }

    /**
     * ((a)<<(shift))                // shift >= 0, shift < 32
     * @param a
     * @param shift
     * @return
     */
    static int SKP_LSHIFT32(int a, int shift)
    {
        return a<<shift;
    }
    /**
     * (a, shift)               SKP_LSHIFT32(a, shift)        // shift >= 0, shift < 32
     * @param a
     * @param shift
     * @return
     */
    static int SKP_LSHIFT(int a, int shift)
    {
        return a<<shift;
    }

    /**
     * SKP_RSHIFT32(a, shift)        // shift >= 0, shift < 32
     * @param a
     * @param shift
     * @return
     */
    static int SKP_RSHIFT(int a, int shift)
    {
        return a>>shift;
    }

    /* saturates before shifting */
    static int SKP_LSHIFT_SAT32(int a, int shift)
    {
        return SKP_LSHIFT32( SKP_LIMIT( a, Integer.MIN_VALUE>>shift, Integer.MAX_VALUE>>shift ), shift );
    }

    /**
     * SKP_ADD32((a), SKP_LSHIFT32((b), (shift)))    // shift >= 0
     * @param a
     * @param b
     * @param shift
     * @return
     */
    static int SKP_ADD_LSHIFT32(int a, int b, int shift)
    {
        return a + (b<<shift);
    }

    /**
     * ((a) + SKP_RSHIFT((b), (shift)))            // shift >= 0
     * @param a
     * @param b
     * @param shift
     * @return
     */
    static int SKP_ADD_RSHIFT(int a, int b, int shift)
    {
        return a + (b>>shift);
    }
    /**
     * SKP_ADD32((a), SKP_RSHIFT32((b), (shift)))    // shift >= 0
     * @param a
     * @param b
     * @param shift
     * @return
     */
    static int SKP_ADD_RSHIFT32(int a, int b, int shift)
    {
        return a + (b>>shift);
    }

    /**
     * SKP_SUB32((a), SKP_LSHIFT32((b), (shift)))    // shift >= 0
     * @param a
     * @param b
     * @param shift
     * @return
     */
    static int SKP_SUB_LSHIFT32(int a, int b, int shift)
    {
        return a - (b<<shift);
    }
    /**
     * SKP_SUB32((a), SKP_RSHIFT32((b), (shift)))    // shift >= 0
     * @param a
     * @param b
     * @param shift
     * @return
     */
    static int SKP_SUB_RSHIFT32(int a, int b, int shift)
    {
        return a - (b>>shift);
    }

    /* Requires that shift > 0 */
    /**
     * ((shift) == 1 ? ((a) >> 1) + ((a) & 1) : (((a) >> ((shift) - 1)) + 1) >> 1)
     */
    static int SKP_RSHIFT_ROUND(int a, int shift)
    {
        return shift == 1 ? (a >> 1) + (a & 1) : ((a >> (shift - 1)) + 1) >> 1;
    }
    /**
     * ((shift) == 1 ? ((a) >> 1) + ((a) & 1) : (((a) >> ((shift) - 1)) + 1) >> 1)
     * @param a
     * @param shift
     * @return
     */
    static long SKP_RSHIFT_ROUND64(long a, int shift)
    {
        return shift == 1 ? (a >> 1) + (a & 1) : ((a >> (shift - 1)) + 1) >> 1;
    }

    static int SKP_min(int a, int b)
    {
        return a<b ? a:b;
    }

    /* SKP_min() versions with typecast in the function call */
    static int SKP_min_int(int a, int b)
    {
        return (((a) < (b)) ? (a) : (b));
    }

    /* SKP_min() versions with typecast in the function call */
    static int SKP_max_int(int a, int b)
    {
        return (((a) > (b)) ? (a) : (b));
    }

    static int SKP_LIMIT( int a, int limit1, int limit2)
    {
        if( limit1 > limit2 )
            return a > limit1 ? limit1 : (a < limit2 ? limit2 : a);
        else
            return a > limit2 ? limit2 : (a < limit1 ? limit1 : a);
    }
    static float SKP_LIMIT( float a, float limit1, float limit2)
    {
        if( limit1 > limit2 )
            return a > limit1 ? limit1 : (a < limit2 ? limit2 : a);
        else
            return a > limit2 ? limit2 : (a < limit1 ? limit1 : a);
    }

    static int SKP_LIMIT_int( int a, int limit1, int limit2)
    {
        if( limit1 > limit2 )
            return a > limit1 ? limit1 : (a < limit2 ? limit2 : a);
        else
            return a > limit2 ? limit2 : (a < limit1 ? limit1 : a);
    }
    static int SKP_LIMIT_32( int a, int limit1, int limit2)
    {
        if( limit1 > limit2 )
            return a > limit1 ? limit1 : (a < limit2 ? limit2 : a);
        else
            return a > limit2 ? limit2 : (a < limit1 ? limit1 : a);
    }

    /**
     * (((a) >  0)  ? (a) : -(a))
     * Be careful, SKP_abs returns wrong when input equals to SKP_intXX_MIN
     * @param a
     * @return
     */
    static int SKP_abs(int a)
    {
        return  (((a) >  0)  ? (a) : -(a));
    }

    /**
     * PSEUDO-RANDOM GENERATOR
     * Make sure to store the result as the seed for the next call (also in between
     * frames), otherwise result won't be random at all. When only using some of the
     * bits, take the most significant bits by right-shifting. Do not just mask off
     * the lowest bits.
     * SKP_RAND(seed)                   (SKP_MLA_ovflw(907633515, (seed), 196314165))
     * @param seed
     * @return
     */
    static int SKP_RAND(int seed)
    {
        return 907633515 + seed*196314165;
    }

    // Add some multiplication functions that can be easily mapped to ARM.

//       SKP_SMMUL: Signed top word multiply.
//            ARMv6        2 instruction cycles.
//            ARMv3M+        3 instruction cycles. use SMULL and ignore LSB registers.(except xM)
//  #define SKP_SMMUL(a32, b32)            (SKP_int32)SKP_RSHIFT(SKP_SMLAL(SKP_SMULWB((a32), (b32)), (a32), SKP_RSHIFT_ROUND((b32), 16)), 16)
//     the following seems faster on x86
//    #define SKP_SMMUL(a32, b32)              (SKP_int32)SKP_RSHIFT64(SKP_SMULL((a32), (b32)), 32)
    static int SKP_SMMUL(int a32, int b32)
    {
        return (int)( ( (long)a32*b32 )>>32 );
    }
}
