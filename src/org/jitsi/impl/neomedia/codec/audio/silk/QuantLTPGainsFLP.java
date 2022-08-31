/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.codec.audio.silk;

/**
 *
 * @author Dingxin Xu
 */
public class QuantLTPGainsFLP
{
    /**
     *
     * @param B (Un-)quantized LTP gains
     * @param cbk_index Codebook index
     * @param periodicity_index Periodicity index
     * @param W Error weights
     * @param mu Mu value (R/D tradeoff)
     * @param lowComplexity Flag for low complexity
     */
    static void SKP_Silk_quant_LTP_gains_FLP(
              float        B[],                                 /* I/O  (Un-)quantized LTP gains                */
              int          cbk_index[],                         /* O    Codebook index                          */
              int          []periodicity_index,                 /* O    Periodicity index                       */
              final float  W[],                                 /* I    Error weights                           */
              final float  mu,                                  /* I    Mu value (R/D tradeoff)                 */
              final int    lowComplexity,                       /* I    Flag for low complexity                 */
              final EncodeMem mem
    )
    {
        int j,k,cbk_size;
        int[] temp_idx = mem.zero(mem.SKP_Silk_quant_LTP_gains_FLP__temp_idx);
        short[] cl_ptr;
        short[] cbk_ptr_Q14;
        float b_ptr[];
        float W_ptr[]; int b_ptr_offset,W_ptr_offset;
        float rate_dist_subfr = 0, rate_dist, min_rate_dist;

        /***************************************************/
        /* Iterate over different codebooks with different */
        /* rates/distortions, and choose best */
        /***************************************************/
        min_rate_dist = Float.MAX_VALUE;
        for( k = 0; k < 3; k++ )
        {
            cl_ptr      = TablesLTP.SKP_Silk_LTP_gain_BITS_Q6_ptrs[ k ];
            cbk_ptr_Q14 = TablesLTP.SKP_Silk_LTP_vq_ptrs_Q14[       k ];
            cbk_size    = TablesLTP.SKP_Silk_LTP_vq_sizes[          k ];

            /* Setup pointer to first subframe */
            W_ptr = W;
            W_ptr_offset = 0;
            b_ptr = B;
            b_ptr_offset = 0;

            rate_dist = 0.0f;
            for( j = 0; j < Define.NB_SUBFR; j++ ) {

                mem.SKP_Silk_quant_LTP_gains_FLP__tmp_float[0] = rate_dist_subfr;

                VQNearestNeighborFLP.SKP_Silk_VQ_WMat_EC_FLP(
                    temp_idx,         /* O    index of best codebook vector                           */
                    j,
                    mem.SKP_Silk_quant_LTP_gains_FLP__tmp_float,       /* O    best weighted quantization error + mu * rate            */
                    b_ptr,                  /* I    input vector to be quantized                            */
                    b_ptr_offset,
                    W_ptr,                  /* I    weighting matrix                                        */
                    W_ptr_offset,
                    cbk_ptr_Q14,            /* I    codebook                                                */
                    cl_ptr,                 /* I    code length for each codebook vector                    */
                    mu,                     /* I    tradeoff between weighted error and rate                */
                    cbk_size,               /* I    number of vectors in codebook                           */
                    mem
                );
                rate_dist_subfr = mem.SKP_Silk_quant_LTP_gains_FLP__tmp_float[0];

                rate_dist += rate_dist_subfr;

                b_ptr_offset += Define.LTP_ORDER;
                W_ptr_offset += Define.LTP_ORDER * Define.LTP_ORDER;
            }

            if( rate_dist < min_rate_dist ) {
                min_rate_dist = rate_dist;
                System.arraycopy(temp_idx, 0, cbk_index, 0, Define.NB_SUBFR);
                periodicity_index[0] = k;
            }

            /* Break early in low-complexity mode if rate distortion is below threshold */
            if( lowComplexity != 0 && ( rate_dist * 16384.0f < TablesLTP.SKP_Silk_LTP_gain_middle_avg_RD_Q14 ) ) {
                break;
            }
        }

        cbk_ptr_Q14 = TablesLTP.SKP_Silk_LTP_vq_ptrs_Q14[periodicity_index[0]];

        for( j = 0; j < Define.NB_SUBFR; j++ ) {
            SigProcFLP.SKP_short2float_array(B, j*Define.LTP_ORDER,
                    cbk_ptr_Q14, cbk_index[ j ] * Define.LTP_ORDER,
                    Define.LTP_ORDER);
        }

        for( j = 0; j < Define.NB_SUBFR * Define.LTP_ORDER; j++ ) {
            B[ j ] *= DefineFLP.Q14_CONVERSION_FAC;
        }
    }
}
