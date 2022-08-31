/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
/*
 * Portions copyright (c) Microsoft Corporation.
 */
package org.jitsi.impl.neomedia.codec.audio.silk;

import java.util.Arrays;

/**
 * The SILK codec Jvaa implementation is a port of the C code in
 * https://tools.ietf.org/html/draft-vos-silk-01
 * In C, arrays as stack variables don't require a memory allocation.  In Java
 * they do, and this introduced significant GC overhead.  This class fixes that
 * by preallocating all memory up front, once per encoder/decoder instance.
 * Any code that uses any field here must zero it before use, to match the
 * previous behaviour of creating with new.
 */
public abstract class BaseMem
{
    // Fields here are used by both the encoder and decoder.

    final int[]   SKP_Silk_NLSF2A_stable__tmp_int = new int[ 1 ];

    final int[]   SKP_Silk_NLSF2A__cos_LSF_Q20 = new int[SigProcFIX.SKP_Silk_MAX_ORDER_LPC];
    final int[]   SKP_Silk_NLSF2A__P = new int[SigProcFIX.SKP_Silk_MAX_ORDER_LPC/2+1];
    final int[]   SKP_Silk_NLSF2A__Q = new int[SigProcFIX.SKP_Silk_MAX_ORDER_LPC/2+1];
    final int[]   SKP_Silk_NLSF2A__a_int32 = new int[SigProcFIX.SKP_Silk_MAX_ORDER_LPC];

    final int[]   SKP_Silk_SQRT_APPROX__lz = new int[1];
    final int[]   SKP_Silk_SQRT_APPROX__frac_Q7 = new int[1];

    final int[][] SKP_Silk_LPC_inverse_pred_gain__Atmp_QA = new int[ 2 ][ SigProcFIX.SKP_Silk_MAX_ORDER_LPC ];

    final short[] SKP_Silk_resampler__in_buf = new short[ 480 ];
    final short[] SKP_Silk_resampler__out_buf = new short[ 480 ];

    final short[] SKP_Silk_resampler_private_down_FIR__buf1 = new short[ ResamplerPrivate.RESAMPLER_MAX_BATCH_SIZE_IN / 2 ];
    final int[]   SKP_Silk_resampler_private_down_FIR__buf2 = new int[ ResamplerPrivate.RESAMPLER_MAX_BATCH_SIZE_IN + ResamplerRom.RESAMPLER_DOWN_ORDER_FIR ];

    final short[] SKP_Silk_resampler_private_IIR_FIR__buf = new short[ 2 * ResamplerPrivate.RESAMPLER_MAX_BATCH_SIZE_IN + ResamplerRom.RESAMPLER_ORDER_FIR_144 ];

    /**
     * Make it easy to initialize an array.
     * @param intArray An int[] member of a subclass.
     */
    public int[] zero(int[] intArray)
    {
        Arrays.fill(intArray, 0);
        return intArray;
    }

    /**
     * Make it easy to initialize an array.
     * @param byteArray A byte[] member of a subclass.
     */
    public byte[] zero(byte[] byteArray)
    {
        Arrays.fill(byteArray, (byte)0);
        return byteArray;
    }

    /**
     * Make it easy to initialize an array.
     * @param shortArray A short[] member of a subclass.
     */
    public short[] zero(short[] shortArray)
    {
        Arrays.fill(shortArray, (short)0);
        return shortArray;
    }

    /**
     * Make it easy to initialize an array.
     * @param floatArray A float[] member of a subclass.
     */
    public float[] zero(float[] floatArray)
    {
        Arrays.fill(floatArray, (float)0);
        return floatArray;
    }

    /**
     * Make it easy to initialize an array.
     * @param doubleArray A double[] member of a subclass.
     */
    public double[] zero(double[] doubleArray)
    {
        Arrays.fill(doubleArray, (double)0);
        return doubleArray;
    }

    /**
     * Make it easy to initialize a 2D array.
     * @param int2dArray An int[][] member of a subclass.
     */
    public int[][] zero(int[][] int2dArray)
    {
        for (int[] row : int2dArray)
            Arrays.fill(row, 0);
        return int2dArray;
    }

    /**
     * Make it easy to initialize a 2D array.
     * @param short2dArray A short[][] member of a subclass.
     */
    public short[][] zero(short[][] short2dArray)
    {
        for (short[] row : short2dArray)
            Arrays.fill(row, (short)0);
        return short2dArray;
    }

    /**
     * Make it easy to initialize a 2D array.
     * @param float2dArray A float[][] member of a subclass.
     */
    public float[][] zero(float[][] float2dArray)
    {
        for (float[] row : float2dArray)
            Arrays.fill(row, (float)0);
        return float2dArray;
    }
}
