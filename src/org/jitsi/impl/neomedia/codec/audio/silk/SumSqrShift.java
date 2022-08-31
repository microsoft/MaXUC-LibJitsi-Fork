/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.codec.audio.silk;

/**
 * Compute number of bits to right shift the sum of squares of a vector
 * of int16s to make it fit in an int32.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
public class SumSqrShift
{
    /**
     * Compute number of bits to right shift the sum of squares of a vector
     * of int16s to make it fit in an int32.
     * @param energy Energy of x, after shifting to the right.
     * @param shift Number of bits right shift applied to energy.
     * @param x Input vector.
     * @param x_offset offset of valid data.
     * @param len Length of input vector.
     */
    static void SKP_Silk_sum_sqr_shift(
            int       []energy,           /* O    Energy of x, after shifting to the right            */
            int       []shift,            /* O    Number of bits right shift applied to energy        */
            short     []x,                /* I    Input vector                                        */
            int       x_offset,
            final int len                 /* I    Length of input vector                              */
        )
    {
        long myBigInt = 0;

        for (int ii=0; ii<len; ii++)
        {
            short nextShort = x[x_offset + ii];
            myBigInt = myBigInt + (nextShort * nextShort);
        }

        int numberOfBits = Long.SIZE - Long.numberOfLeadingZeros(myBigInt);

        int shift2 = 0;
        if (numberOfBits > 30)
        {
            shift2 = ((numberOfBits - 29) / 2 ) * 2;
        }

        energy[0] = (int) (myBigInt >> shift2);
        shift[0] = shift2;
    }
}
