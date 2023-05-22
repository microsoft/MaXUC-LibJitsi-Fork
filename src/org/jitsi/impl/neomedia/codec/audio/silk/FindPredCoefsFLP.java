/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
// Portions (c) Microsoft Corporation. All rights reserved.
package org.jitsi.impl.neomedia.codec.audio.silk;

import java.util.*;

/**
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
public class FindPredCoefsFLP
{
    /**
     *
     * @param psEnc Encoder state FLP.
     * @param psEncCtrl Encoder control FLP.
     * @param res_pitch Residual from pitch analysis.
     */
    static void SKP_Silk_find_pred_coefs_FLP(
            SKP_Silk_encoder_state_FLP      psEnc,         /* I/O  Encoder state FLP               */
            SKP_Silk_encoder_control_FLP    psEncCtrl,     /* I/O  Encoder control FLP             */
            float                           res_pitch[],   /* I    Residual from pitch analysis    */
            final EncodeMem                 mem
    )
    {
        int     i;
        float[] WLTP = mem.zero(mem.SKP_Silk_find_pred_coefs_FLP__WLTP);
        float[] invGains = mem.zero(mem.SKP_Silk_find_pred_coefs_FLP__invGains);
        float[] Wght = mem.zero(mem.SKP_Silk_find_pred_coefs_FLP__Wght);
        float[] NLSF = mem.zero(mem.SKP_Silk_find_pred_coefs_FLP__NLSF);
        float[] x_ptr;
        int x_ptr_offset;
        float[] x_pre_ptr;
        float[] LPC_in_pre = mem.zero(mem.SKP_Silk_find_pred_coefs_FLP__LPC_in_pre);
        int x_pre_ptr_offset;

        /* Weighting for weighted least squares */
        for( i = 0; i < Define.NB_SUBFR; i++ )
        {
            assert( psEncCtrl.Gains[ i ] > 0.0f );
            invGains[ i ] = 1.0f / psEncCtrl.Gains[ i ];
            Wght[ i ]     = invGains[ i ] * invGains[ i ];
        }

        if( psEncCtrl.sCmn.sigtype == Define.SIG_TYPE_VOICED )
        {
            /**********/
            /* VOICED */
            /**********/
            assert( psEnc.sCmn.frame_length - psEnc.sCmn.predictLPCOrder >= psEncCtrl.sCmn.pitchL[ 0 ] + Define.LTP_ORDER / 2 );

            /* LTP analysis */
            mem.SKP_Silk_find_pred_coefs_FLP__tmp_float[0] = psEncCtrl.LTPredCodGain;
            FindLTPFLP.SKP_Silk_find_LTP_FLP( psEncCtrl.LTPCoef, WLTP, mem.SKP_Silk_find_pred_coefs_FLP__tmp_float, res_pitch,
                res_pitch,( psEnc.sCmn.frame_length >> 1 ), psEncCtrl.sCmn.pitchL, Wght,
                psEnc.sCmn.subfr_length, psEnc.sCmn.frame_length, mem );
            psEncCtrl.LTPredCodGain = mem.SKP_Silk_find_pred_coefs_FLP__tmp_float[0];

            /* Quantize LTP gain parameters */
            mem.SKP_Silk_find_pred_coefs_FLP__tmp_int[0] = psEncCtrl.sCmn.PERIndex;
            QuantLTPGainsFLP.SKP_Silk_quant_LTP_gains_FLP( psEncCtrl.LTPCoef, psEncCtrl.sCmn.LTPIndex, mem.SKP_Silk_find_pred_coefs_FLP__tmp_int,
                WLTP, psEnc.mu_LTP, psEnc.sCmn.LTPQuantLowComplexity, mem );
            psEncCtrl.sCmn.PERIndex = mem.SKP_Silk_find_pred_coefs_FLP__tmp_int[0];

            /* Control LTP scaling */
            LTPScaleCtrlFLP.SKP_Silk_LTP_scale_ctrl_FLP( psEnc, psEncCtrl );

            /* Create LTP residual */
            LTPAnalysisFilterFLP.SKP_Silk_LTP_analysis_filter_FLP( LPC_in_pre, psEnc.x_buf, psEnc.sCmn.frame_length - psEnc.sCmn.predictLPCOrder,
                psEncCtrl.LTPCoef, psEncCtrl.sCmn.pitchL, invGains, psEnc.sCmn.subfr_length, psEnc.sCmn.predictLPCOrder, mem );
        }
        else
        {
            /************/
            /* UNVOICED */
            /************/
            /* Create signal with prepended subframes, scaled by inverse gains */
            x_ptr     = psEnc.x_buf;
            x_ptr_offset = psEnc.sCmn.frame_length - psEnc.sCmn.predictLPCOrder;

            x_pre_ptr = LPC_in_pre;
            x_pre_ptr_offset = 0;
            for( i = 0; i < Define.NB_SUBFR; i++ ) {
                ScaleCopyVectorFLP.SKP_Silk_scale_copy_vector_FLP( x_pre_ptr, x_pre_ptr_offset, x_ptr, x_ptr_offset, invGains[ i ],
                    psEnc.sCmn.subfr_length + psEnc.sCmn.predictLPCOrder );
                x_pre_ptr_offset += psEnc.sCmn.subfr_length + psEnc.sCmn.predictLPCOrder;
                x_ptr_offset     += psEnc.sCmn.subfr_length;
            }

            Arrays.fill(psEncCtrl.LTPCoef, 0, Define.NB_SUBFR * Define.LTP_ORDER, 0.0f);
            psEncCtrl.LTPredCodGain = 0.0f;
        }

        /* LPC_in_pre contains the LTP-filtered input for voiced, and the unfiltered input for unvoiced */
        FindLPCFLP.SKP_Silk_find_LPC_FLP( NLSF, mem.SKP_Silk_find_pred_coefs_FLP__tmp_int, psEnc.sPred.prev_NLSFq,
            psEnc.sCmn.useInterpolatedNLSFs * ( 1 - psEnc.sCmn.first_frame_after_reset ), psEnc.sCmn.predictLPCOrder,
            LPC_in_pre, psEnc.sCmn.subfr_length + psEnc.sCmn.predictLPCOrder, mem );
        psEncCtrl.sCmn.NLSFInterpCoef_Q2 = mem.SKP_Silk_find_pred_coefs_FLP__tmp_int[0];

        /* Quantize LSFs */
        ProcessNLSFsFLP.SKP_Silk_process_NLSFs_FLP( psEnc, psEncCtrl, NLSF, mem );

        /* Calculate residual energy using quantized LPC coefficients */
        ResidualEnergyFLP.SKP_Silk_residual_energy_FLP( psEncCtrl.ResNrg, LPC_in_pre, psEncCtrl.PredCoef, psEncCtrl.Gains,
            psEnc.sCmn.subfr_length, psEnc.sCmn.predictLPCOrder, mem );

        /* Copy to prediction struct for use in next frame for fluctuation reduction */
        System.arraycopy(NLSF, 0, psEnc.sPred.prev_NLSFq, 0, psEnc.sCmn.predictLPCOrder);
    }
}
