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
public class FindPitchLagsFLP
{
    /**
     *
     * @param psEnc Encoder state FLP.
     * @param psEncCtrl Encoder control FLP.
     * @param res Residual.
     * @param x Speech signal.
     * @param x_offset offset of valid data.
     */
    static void SKP_Silk_find_pitch_lags_FLP(
            SKP_Silk_encoder_state_FLP      psEnc,             /* I/O  Encoder state FLP                       */
            SKP_Silk_encoder_control_FLP    psEncCtrl,         /* I/O  Encoder control FLP                     */
            float                           res[],             /* O    Residual                                */
            float                           x[],               /* I    Speech signal                           */
            int                             x_offset,
            final EncodeMem                 mem
        )
    {
        SKP_Silk_predict_state_FLP psPredSt = psEnc.sPred;
        float[] x_buf_ptr, x_buf;
        int x_buf_ptr_offset, x_buf_offset;
        float[] auto_corr = mem.zero(mem.SKP_Silk_find_pitch_lags_FLP__auto_corr);
        float[] A = mem.zero(mem.SKP_Silk_find_pitch_lags_FLP__A);
        float[] refl_coef = mem.zero(mem.SKP_Silk_find_pitch_lags_FLP__refl_coef);
        float[] Wsig = mem.zero(mem.SKP_Silk_find_pitch_lags_FLP__Wsig);
        float thrhld;
        float[] Wsig_ptr;
        int Wsig_ptr_offset;
        int   buf_len;

        /******************************************/
        /* Setup buffer lengths etc based of Fs   */
        /******************************************/
        buf_len = 2 * psEnc.sCmn.frame_length + psEnc.sCmn.la_pitch;

        /* Safty check */
        assert( buf_len >= psPredSt.pitch_LPC_win_length );

        x_buf = x;
        x_buf_offset = x_offset - psEnc.sCmn.frame_length;

        /*************************************/
        /* Estimate LPC AR coeficients */
        /*************************************/

        /* Calculate windowed signal */

        /* First LA_LTP samples */
        x_buf_ptr = x_buf;
        x_buf_ptr_offset = x_buf_offset + buf_len - psPredSt.pitch_LPC_win_length;
        Wsig_ptr  = Wsig;
        Wsig_ptr_offset=0;
        ApplySineWindowFLP.SKP_Silk_apply_sine_window_FLP( Wsig_ptr,Wsig_ptr_offset, x_buf_ptr,x_buf_ptr_offset, 1, psEnc.sCmn.la_pitch );

        /* Middle non-windowed samples */
        Wsig_ptr_offset  += psEnc.sCmn.la_pitch;
        x_buf_ptr_offset += psEnc.sCmn.la_pitch;
//            SKP_memcpy( Wsig_ptr, x_buf_ptr, ( psPredSt->pitch_LPC_win_length - ( psEnc->sCmn.la_pitch << 1 ) ) * sizeof( SKP_float ) );
        for(int i_djinn=0; i_djinn< psPredSt.pitch_LPC_win_length - ( psEnc.sCmn.la_pitch << 1 ); i_djinn++)
            Wsig_ptr[Wsig_ptr_offset + i_djinn]  = x_buf_ptr[x_buf_ptr_offset+i_djinn];

        /* Last LA_LTP samples */
        Wsig_ptr_offset  += psPredSt.pitch_LPC_win_length - ( psEnc.sCmn.la_pitch << 1 );
        x_buf_ptr_offset += psPredSt.pitch_LPC_win_length - ( psEnc.sCmn.la_pitch << 1 );
        ApplySineWindowFLP.SKP_Silk_apply_sine_window_FLP( Wsig_ptr,Wsig_ptr_offset, x_buf_ptr,x_buf_ptr_offset, 2, psEnc.sCmn.la_pitch );

        /* Calculate autocorrelation sequence */
        AutocorrelationFLP.SKP_Silk_autocorrelation_FLP( auto_corr, Wsig, psPredSt.pitch_LPC_win_length, psEnc.sCmn.pitchEstimationLPCOrder + 1 );

        /* Add white noise, as a fraction of the energy */
        auto_corr[ 0 ] += auto_corr[ 0 ] * DefineFLP.FIND_PITCH_WHITE_NOISE_FRACTION;

        /* Calculate the reflection coefficients using Schur */
        SchurFLP.SKP_Silk_schur_FLP( refl_coef, auto_corr, psEnc.sCmn.pitchEstimationLPCOrder, mem );

        /* Convert reflection coefficients to prediction coefficients */
        K2aFLP.SKP_Silk_k2a_FLP( A, refl_coef, psEnc.sCmn.pitchEstimationLPCOrder, mem );

        /* Bandwidth expansion */
        BwexpanderFLP.SKP_Silk_bwexpander_FLP( A,0, psEnc.sCmn.pitchEstimationLPCOrder, DefineFLP.FIND_PITCH_BANDWITH_EXPANSION );

        /*****************************************/
        /* LPC analysis filtering               */
        /*****************************************/
        LPCAnalysisFilterFLP.SKP_Silk_LPC_analysis_filter_FLP( res, A, x_buf, x_buf_offset, buf_len, psEnc.sCmn.pitchEstimationLPCOrder );
//            SKP_memset( res, 0, psEnc->sCmn.pitchEstimationLPCOrder * sizeof( SKP_float ) );
        for(int i_djinn=0; i_djinn<psEnc.sCmn.pitchEstimationLPCOrder; i_djinn++)
            res[i_djinn] = 0;

        /* Threshold for pitch estimator */
        thrhld  = 0.5f;
        thrhld -= 0.004f * psEnc.sCmn.pitchEstimationLPCOrder;
        thrhld -= 0.1f  * ( float )Math.sqrt( psEnc.speech_activity );
        thrhld += 0.14f * psEnc.sCmn.prev_sigtype;
        thrhld -= 0.12f * psEncCtrl.input_tilt;

        /*****************************************/
        /* Call Pitch estimator */
        /*****************************************/
        mem.SKP_Silk_find_pitch_lags_FLP__tmp_int1[0] = psEncCtrl.sCmn.lagIndex;
        mem.SKP_Silk_find_pitch_lags_FLP__tmp_int2[0] = psEncCtrl.sCmn.contourIndex;
        mem.SKP_Silk_find_pitch_lags_FLP__tmp_float[0] = psEnc.LTPCorr;
        psEncCtrl.sCmn.sigtype = PitchAnalysisCoreFLP.SKP_Silk_pitch_analysis_core_FLP(
            res,
            psEncCtrl.sCmn.pitchL,
            mem.SKP_Silk_find_pitch_lags_FLP__tmp_int1,
            mem.SKP_Silk_find_pitch_lags_FLP__tmp_int2,
            mem.SKP_Silk_find_pitch_lags_FLP__tmp_float,
            psEnc.sCmn.prevLag,
            psEnc.pitchEstimationThreshold,
            thrhld,
            psEnc.sCmn.fs_kHz,
            psEnc.sCmn.pitchEstimationComplexity,
            mem );
        psEncCtrl.sCmn.lagIndex = mem.SKP_Silk_find_pitch_lags_FLP__tmp_int1[0];
        psEncCtrl.sCmn.contourIndex = mem.SKP_Silk_find_pitch_lags_FLP__tmp_int2[0];
        psEnc.LTPCorr = mem.SKP_Silk_find_pitch_lags_FLP__tmp_float[0];
    }
}
