/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.codec.audio.silk;

/**
 * So we only allocate memory blocks once per thread.
 */
public class EncodeMem extends BaseMem
{
    // Make the blocks public to save having to write getter methods.

    final SKP_Silk_encoder_control_FLP SKP_Silk_encode_frame_FLP__sEncCtrl = new SKP_Silk_encoder_control_FLP();

    final NSQImplNSQDelDec SKP_Silk_control_encoder_FLP__NSQImplNSQDelDec = new NSQImplNSQDelDec();

    final int[]   SKP_Silk_NSQ_del_dec__sLTP_Q16 = new int[ 2 * Define.MAX_FRAME_LENGTH ];
    final short[] SKP_Silk_NSQ_del_dec__sLTP = new short[ 2 * Define.MAX_FRAME_LENGTH ];
    final int[]   SKP_Silk_NSQ_del_dec__FiltState = new int[ Define.MAX_LPC_ORDER ];
    final int[]   SKP_Silk_NSQ_del_dec__x_sc_Q10 = new int[ Define.MAX_FRAME_LENGTH / Define.NB_SUBFR ];

    // The array elements are created in the constructor.
    final NSQDelDecStruct[] SKP_Silk_NSQ_del_dec__psDelDec = new NSQDelDecStruct[ Define.DEL_DEC_STATES_MAX ];
    final int[]   SKP_Silk_NSQ_del_dec__smpl_buf_idx_ptr = new int[1];

    final int[] SKP_Silk_A2NLSF_FLP__NLSF_fix = new int[ Define.MAX_LPC_ORDER ];
    final int[] SKP_Silk_A2NLSF_FLP__a_fix_Q16 = new int[ Define.MAX_LPC_ORDER ];

    final int[]   SKP_Silk_NLSF2A_stable_FLP__NLSF_fix = new int[  Define.MAX_LPC_ORDER ];
    final short[] SKP_Silk_NLSF2A_stable_FLP__a_fix_Q12 = new short[ Define.MAX_LPC_ORDER ];

    final int[] SKP_Silk_NLSF_stabilize_FLP__NLSF_Q15 = new int[ Define.MAX_LPC_ORDER ];
    final int[] SKP_Silk_NLSF_stabilize_FLP__ndelta_min_Q15 = new int[ Define.MAX_LPC_ORDER + 1 ];

    final int[] SKP_Silk_interpolate_wrapper_FLP__x0_int = new int[ Define.MAX_LPC_ORDER ];
    final int[] SKP_Silk_interpolate_wrapper_FLP__x1_int = new int[ Define.MAX_LPC_ORDER ];
    final int[] SKP_Silk_interpolate_wrapper_FLP__xi_int = new int[ Define.MAX_LPC_ORDER ];

    final int[] SKP_Silk_VAD_FLP__SA_Q8 = new int[1];
    final int[] SKP_Silk_VAD_FLP__SNR_dB_Q7 = new int[1];
    final int[] SKP_Silk_VAD_FLP__Tilt_Q15 = new int[1];
    final int[] SKP_Silk_VAD_FLP__Quality_Bands_Q15 = new int[ Define.VAD_N_BANDS ];

    final short[] SKP_Silk_NSQ_wrapper_FLP__x_16 = new short[ Define.MAX_FRAME_LENGTH ];
    final int[]   SKP_Silk_NSQ_wrapper_FLP__Gains_Q16 = new int[ Define.NB_SUBFR ];
    final short[] SKP_Silk_NSQ_wrapper_FLP__LTPCoef_Q14 = new short[ Define.LTP_ORDER * Define.NB_SUBFR ];
    final short[] SKP_Silk_NSQ_wrapper_FLP__AR2_Q13 = new short[ Define.NB_SUBFR * Define.SHAPE_LPC_ORDER_MAX ];
    final int[]   SKP_Silk_NSQ_wrapper_FLP__LF_shp_Q14 = new int[ Define.NB_SUBFR ];
    final int[]   SKP_Silk_NSQ_wrapper_FLP__Tilt_Q14 = new int[ Define.NB_SUBFR ];
    final int[]   SKP_Silk_NSQ_wrapper_FLP__HarmShapeGain_Q14 = new int[ Define.NB_SUBFR ];
    final short[][] SKP_Silk_NSQ_wrapper_FLP__PredCoef_Q12 = new short[ 2 ][ Define.MAX_LPC_ORDER ];
    final short[]   SKP_Silk_NSQ_wrapper_FLP__PredCoef_Q12_dim1_tmp = new short[2 * Define.MAX_LPC_ORDER];

    final float[] SKP_Silk_find_LPC_FLP__a = new float[ Define.MAX_LPC_ORDER ];
    final float[] SKP_Silk_find_LPC_FLP__a_tmp = new float[ Define.MAX_LPC_ORDER ];
    final float[] SKP_Silk_find_LPC_FLP__NLSF0 = new float[ Define.MAX_LPC_ORDER ];
    final float[] SKP_Silk_find_LPC_FLP__LPC_res = new float[ ( Define.MAX_FRAME_LENGTH + Define.NB_SUBFR * Define.MAX_LPC_ORDER ) / 2 ];

    final float[] SKP_Silk_find_pred_coefs_FLP__WLTP = new float[ Define.NB_SUBFR * Define.LTP_ORDER * Define.LTP_ORDER ];
    final float[] SKP_Silk_find_pred_coefs_FLP__invGains = new float[ Define.NB_SUBFR ];
    final float[] SKP_Silk_find_pred_coefs_FLP__Wght = new float[ Define.NB_SUBFR ];
    final float[] SKP_Silk_find_pred_coefs_FLP__NLSF = new float[ Define.MAX_LPC_ORDER ];
    final float[] SKP_Silk_find_pred_coefs_FLP__LPC_in_pre = new float[ Define.NB_SUBFR * Define.MAX_LPC_ORDER + Define.MAX_FRAME_LENGTH ];
    final float[] SKP_Silk_find_pred_coefs_FLP__tmp_float = new float[1];
    final int[]   SKP_Silk_find_pred_coefs_FLP__tmp_int = new int[1];

    final short[]   SKP_Silk_SDK_Encode__tmp_short = new short[1];

    final int[]     SKP_Silk_encode_frame_FLP__nBytes = new int[1];
    final short[]   SKP_Silk_encode_frame_FLP__pIn_HP = new short[    Define.MAX_FRAME_LENGTH ];
    final short[]   SKP_Silk_encode_frame_FLP__pIn_HP_LP = new short[ Define.MAX_FRAME_LENGTH ];
    final float[]   SKP_Silk_encode_frame_FLP__xfw = new float[       Define.MAX_FRAME_LENGTH ];
    final float[]   SKP_Silk_encode_frame_FLP__res_pitch = new float[ 2 * Define.MAX_FRAME_LENGTH + Define.LA_PITCH_MAX ];
    final byte[]    SKP_Silk_encode_frame_FLP__LBRRpayload = new byte[Define.MAX_ARITHM_BYTES];
    final short[]   SKP_Silk_encode_frame_FLP__nBytesLBRR = new short[1];

    final int[]    SKP_Silk_HP_variable_cutoff_FLP__B_Q28 = new int[ 3 ];
    final int[]    SKP_Silk_HP_variable_cutoff_FLP__A_Q28 = new int[ 2 ];

    final double[] SKP_Silk_burg_modified_FLP__C_first_row = new double [ SigProcFIX.SKP_Silk_MAX_ORDER_LPC ];
    final double[] SKP_Silk_burg_modified_FLP__C_last_row = new double [ SigProcFIX.SKP_Silk_MAX_ORDER_LPC ];
    final double[] SKP_Silk_burg_modified_FLP__CAf = new double [ SigProcFIX.SKP_Silk_MAX_ORDER_LPC + 1 ];
    final double[] SKP_Silk_burg_modified_FLP__CAb = new double [ SigProcFIX.SKP_Silk_MAX_ORDER_LPC + 1 ];
    final double[] SKP_Silk_burg_modified_FLP__Af = new double [ SigProcFIX.SKP_Silk_MAX_ORDER_LPC ];

    final float[] SKP_Silk_prefilter_FLP__B = new float[ 2 ];
    final float[] SKP_Silk_prefilter_FLP__AR1_shp = new float[ Define.NB_SUBFR * Define.SHAPE_LPC_ORDER_MAX ];
    final float[] SKP_Silk_prefilter_FLP__HarmShapeFIR = new float[ 3 ];
    final float[] SKP_Silk_prefilter_FLP__st_res = new float[ Define.MAX_FRAME_LENGTH / Define.NB_SUBFR + Define.MAX_LPC_ORDER ];

    final int[]   SKP_Silk_LP_variable_cutoff__B_Q28 = new int[ Define.TRANSITION_NB ];
    final int[]   SKP_Silk_LP_variable_cutoff__A_Q28 = new int[ Define.TRANSITION_NA ];

    final int[]   SKP_Silk_LBRR_encode_FLP__Gains_Q16 = new int[ Define.NB_SUBFR ];
    final int[]   SKP_Silk_LBRR_encode_FLP__TempGainsIndices = new int[ Define.NB_SUBFR ];
    final int[]   SKP_Silk_LBRR_encode_FLP__nBytes = new int[1];
    final float[] SKP_Silk_LBRR_encode_FLP__TempGains = new float[ Define.NB_SUBFR ];
    final int[]   SKP_Silk_LBRR_encode_FLP__tmp_int = new int[1];

    final int[]   SKP_Silk_encode_pulses__abs_pulses = new int[ Define.MAX_FRAME_LENGTH ];
    final int[]   SKP_Silk_encode_pulses__sum_pulses = new int[ Define.MAX_NB_SHELL_BLOCKS ];
    final int[]   SKP_Silk_encode_pulses__nRshifts   = new int[ Define.MAX_NB_SHELL_BLOCKS ];
    final int[]   SKP_Silk_encode_pulses__pulses_comb = new int[ 8 ];

    final int[]   SKP_Silk_encode_signs__cdf = new int[3];

    final int[]   SKP_Silk_shell_encoder__pulses1 = new int[ 8 ];
    final int[]   SKP_Silk_shell_encoder__pulses2 = new int[ 4 ];
    final int[]   SKP_Silk_shell_encoder__pulses3 = new int[ 2 ];
    final int[]   SKP_Silk_shell_encoder__pulses4 = new int[ 1 ];

    // The array elements are created in the constructor.
    final NSQ_sample_struct[][] SKP_Silk_noise_shape_quantizer_del_dec__psSampleState = new NSQ_sample_struct[ Define.DEL_DEC_STATES_MAX ][ 2 ];

    final float[]   SKP_Silk_NLSF_MSVQ_encode_FLP__pNLSF_in =     new float[ Define.MAX_LPC_ORDER ];
    final float[]   SKP_Silk_NLSF_MSVQ_encode_FLP__pRateDist =    new float[Define.NLSF_MSVQ_TREE_SEARCH_MAX_VECTORS_EVALUATED() ];
    final float[]   SKP_Silk_NLSF_MSVQ_encode_FLP__pRate =        new float[Define.MAX_NLSF_MSVQ_SURVIVORS ];
    final float[]   SKP_Silk_NLSF_MSVQ_encode_FLP__pRate_new =    new float[Define.MAX_NLSF_MSVQ_SURVIVORS ];
    final int[]     SKP_Silk_NLSF_MSVQ_encode_FLP__pTempIndices = new int[Define.MAX_NLSF_MSVQ_SURVIVORS ];
    final int[]     SKP_Silk_NLSF_MSVQ_encode_FLP__pPath =        new int[Define.MAX_NLSF_MSVQ_SURVIVORS * Define.NLSF_MSVQ_MAX_CB_STAGES ];
    final int[]     SKP_Silk_NLSF_MSVQ_encode_FLP__pPath_new =    new int[Define.MAX_NLSF_MSVQ_SURVIVORS * Define.NLSF_MSVQ_MAX_CB_STAGES ];
    final float[]   SKP_Silk_NLSF_MSVQ_encode_FLP__pRes =         new float[Define.MAX_NLSF_MSVQ_SURVIVORS * Define.MAX_LPC_ORDER ];
    final float[]   SKP_Silk_NLSF_MSVQ_encode_FLP__pRes_new =     new float[Define.MAX_NLSF_MSVQ_SURVIVORS * Define.MAX_LPC_ORDER ];

    final float     SKP_Silk_NLSF_VQ_sum_error_FLP__Wcpy[] = new float[ Define.MAX_LPC_ORDER ];

    final float[] SKP_Silk_find_pitch_lags_FLP__auto_corr = new float[ Define.FIND_PITCH_LPC_ORDER_MAX + 1 ];
    final float[] SKP_Silk_find_pitch_lags_FLP__A = new float[         Define.FIND_PITCH_LPC_ORDER_MAX ];
    final float[] SKP_Silk_find_pitch_lags_FLP__refl_coef = new float[ Define.FIND_PITCH_LPC_ORDER_MAX ];
    final float[] SKP_Silk_find_pitch_lags_FLP__Wsig = new float[      Define.FIND_PITCH_LPC_WIN_MAX ];
    final float[] SKP_Silk_find_pitch_lags_FLP__tmp_float = new float[1];
    final int[]   SKP_Silk_find_pitch_lags_FLP__tmp_int1 = new int[1];
    final int[]   SKP_Silk_find_pitch_lags_FLP__tmp_int2 = new int[1];

    final float[][] SKP_Silk_schur_FLP__C = new float[SigProcFIX.SKP_Silk_MAX_ORDER_LPC + 1][2];

    final float[]   SKP_Silk_noise_shape_analysis_FLP__x_windowed = new float[ Define.SHAPE_LPC_WIN_MAX ];
    final float[]   SKP_Silk_noise_shape_analysis_FLP__auto_corr = new float[ Define.SHAPE_LPC_ORDER_MAX + 1 ];
    final float[]   SKP_Silk_noise_shape_analysis_FLP_float_tmp = new float[1];

    final int[]    SKP_Silk_A2NLSF__P = new int[ SigProcFIX.SKP_Silk_MAX_ORDER_LPC / 2 + 1 ];
    final int[]    SKP_Silk_A2NLSF__Q = new int[ SigProcFIX.SKP_Silk_MAX_ORDER_LPC / 2 + 1 ];
    final int[][]  SKP_Silk_A2NLSF__PQ = new int[ 2 ][   ];

    final float[]  LPC_fit_int16__invGain = new float[1];

    final float[][] SKP_Silk_LPC_inverse_pred_gain_FLP__Atmp = new float[ 2 ][ SigProcFIX.SKP_Silk_MAX_ORDER_LPC ];

    final float[]   SKP_Silk_pitch_analysis_core_FLP__signal_8kHz = new float[ CommonPitchEstDefines.PITCH_EST_FRAME_LENGTH_MS * 8 ];
    final float[]   SKP_Silk_pitch_analysis_core_FLP__signal_4kHz = new float[ CommonPitchEstDefines.PITCH_EST_FRAME_LENGTH_MS * 4 ];
    final float[]   SKP_Silk_pitch_analysis_core_FLP__scratch_mem = new float[ CommonPitchEstDefines.PITCH_EST_MAX_FRAME_LENGTH * 3 ];
    final float[]   SKP_Silk_pitch_analysis_core_FLP__filt_state = new float[ CommonPitchEstDefines.PITCH_EST_MAX_DECIMATE_STATE_LENGTH ];
    final float[][] SKP_Silk_pitch_analysis_core_FLP__C = new float[CommonPitchEstDefines.PITCH_EST_NB_SUBFR][(CommonPitchEstDefines.PITCH_EST_MAX_LAG >> 1) + 5]; /* use to be +2 but then valgrind reported errors for SWB */
    final float[]   SKP_Silk_pitch_analysis_core_FLP__CC = new float[CommonPitchEstDefines.PITCH_EST_NB_CBKS_STAGE2_EXT];
    final int[]     SKP_Silk_pitch_analysis_core_FLP__d_srch = new int[CommonPitchEstDefines.PITCH_EST_D_SRCH_LENGTH];
    final short[]   SKP_Silk_pitch_analysis_core_FLP__d_comp = new short[(CommonPitchEstDefines.PITCH_EST_MAX_LAG >> 1) + 5];
    final float[][][] SKP_Silk_pitch_analysis_core_FLP__energies_st3 = new float[ CommonPitchEstDefines.PITCH_EST_NB_SUBFR ][ CommonPitchEstDefines.PITCH_EST_NB_CBKS_STAGE3_MAX ][ CommonPitchEstDefines.PITCH_EST_NB_STAGE3_LAGS ];
    final float[][][] SKP_Silk_pitch_analysis_core_FLP__cross_corr_st3 = new float[ CommonPitchEstDefines.PITCH_EST_NB_SUBFR ][ CommonPitchEstDefines.PITCH_EST_NB_CBKS_STAGE3_MAX ][ CommonPitchEstDefines.PITCH_EST_NB_STAGE3_LAGS ];
    final short[]   SKP_Silk_pitch_analysis_core_FLP__signal_12 = new short[ 12 * CommonPitchEstDefines.PITCH_EST_FRAME_LENGTH_MS ];
    final short[]   SKP_Silk_pitch_analysis_core_FLP__signal_8 = new short[   8 * CommonPitchEstDefines.PITCH_EST_FRAME_LENGTH_MS ];
    final short[]   SKP_Silk_pitch_analysis_core_FLP__signal_24 = new short[ CommonPitchEstDefines.PITCH_EST_MAX_FRAME_LENGTH ];
    final int[]     SKP_Silk_pitch_analysis_core_FLP__filt_state_fix = new int[ 8 ];
    final int[]     SKP_Silk_pitch_analysis_core_FLP__R23 = new int[ 6 ];

    final short[][] SKP_Silk_VAD_GetSA_Q8__X = new short[ Define.VAD_N_BANDS ][ Define.MAX_FRAME_LENGTH / 2 ];
    final int[]     SKP_Silk_VAD_GetSA_Q8__Xnrg = new int[ Define.VAD_N_BANDS ];
    final int[]     SKP_Silk_VAD_GetSA_Q8__NrgToNoiseRatio_Q8 = new int[ Define.VAD_N_BANDS ];

    final float[]   SKP_Silk_process_NLSFs_FLP__pNLSFW = new float[ Define.MAX_LPC_ORDER ];
    final float[]   SKP_Silk_process_NLSFs_FLP__pNLSF0_temp = new float[  Define.MAX_LPC_ORDER ];
    final float[]   SKP_Silk_process_NLSFs_FLP__pNLSFW0_temp = new float[ Define.MAX_LPC_ORDER ];

    final int[]     SKP_Silk_process_gains_FLP__pGains_Q16 = new int[ Define.NB_SUBFR ];
    final int[]     SKP_Silk_process_gains_FLP__tmp_int = new int[ 1 ];

    final int[]     SKP_Silk_lin2log__lz_ptr = new int[1];
    final int[]     SKP_Silk_lin2log__frac_Q7_ptr = new int[1];

    final float[]   SKP_Silk_residual_energy_FLP__LPC_res = new float[ ( Define.MAX_FRAME_LENGTH + Define.NB_SUBFR * Define.MAX_LPC_ORDER ) / 2 ];

    final int[]     SKP_Silk_range_enc_wrap_up_tmp_int = new int[1];

    final int[]     SKP_Silk_quant_LTP_gains_FLP__temp_idx = new int[Define.NB_SUBFR];
    final float[]   SKP_Silk_quant_LTP_gains_FLP__tmp_float = new float[1];

    final float[]   SKP_Silk_k2a_FLP__Atmp = new float[SigProcFIX.SKP_Silk_MAX_ORDER_LPC];

    final int[]     SKP_Silk_detect_SWB_input__shift = new int[1];
    final int[]     SKP_Silk_detect_SWB_input__energy_32 = new int[1];
    final short[]   SKP_Silk_detect_SWB_input__in_HP_8_kHz = new short[ Define.MAX_FRAME_LENGTH ];

    final float[]   SKP_Silk_find_LTP_FLP__d = new float[Define.NB_SUBFR];
    final float[]   SKP_Silk_find_LTP_FLP__delta_b = new float[Define.LTP_ORDER];
    final float[]   SKP_Silk_find_LTP_FLP__w = new float[Define.NB_SUBFR];
    final float[]   SKP_Silk_find_LTP_FLP__nrg = new float[Define.NB_SUBFR];
    final float[]   SKP_Silk_find_LTP_FLP__Rr = new float[Define.LTP_ORDER];
    final float[]   SKP_Silk_find_LTP_FLP__rr = new float[Define.NB_SUBFR];

    final float[]   SKP_Silk_LTP_analysis_filter_FLP__Btmp = new float[ Define.LTP_ORDER ];

    final int[]     SKP_Silk_NSQ__sLTP_Q16 = new int[ 2 * Define.MAX_FRAME_LENGTH ];
    final short[]   SKP_Silk_NSQ__sLTP = new short[ 2 * Define.MAX_FRAME_LENGTH ];
    final int[]     SKP_Silk_NSQ__FiltState = new int[ Define.MAX_LPC_ORDER ];
    final int[]     SKP_Silk_NSQ__x_sc_Q10 = new int[ Define.MAX_FRAME_LENGTH / Define.NB_SUBFR ];
    final short[]   SKP_Silk_NSQ_wrapper_FLP__x_tmp = new short[ Define.MAX_FRAME_LENGTH ];

    final float[]   SKP_P_Ana_calc__scratch_mem = new float[ PitchAnalysisCoreFLP.SCRATCH_SIZE ];

    final float[]   SKP_Silk_VQ_WMat_EC_FLP__diff = new float[5];

    final float[]   SKP_Silk_solve_LDL_FLP__L_tmp = new float[Define.MAX_MATRIX_SIZE*Define.MAX_MATRIX_SIZE];
    final float[]   SKP_Silk_solve_LDL_FLP__T = new float[Define.MAX_MATRIX_SIZE];
    final float[]   SKP_Silk_solve_LDL_FLP__Dinv = new float[Define.MAX_MATRIX_SIZE];

    final float[]   SKP_Silk_LDL_FLP__v = new float[ Define.MAX_MATRIX_SIZE ];
    final float[]   SKP_Silk_LDL_FLP__D = new float[ Define.MAX_MATRIX_SIZE ];

    final int[]     SKP_Silk_resampler_down3__buf = new int[ ResamplerPrivate.RESAMPLER_MAX_BATCH_SIZE_IN + ResamplerDown3.ORDER_FIR ];

    final int[]     SKP_Silk_resampler_down2_3__buf = new int[ ResamplerPrivate.RESAMPLER_MAX_BATCH_SIZE_IN + ResamplerDown23.ORDER_FIR ];

    public EncodeMem()
    {
        for(int psDelDecIni_i=0; psDelDecIni_i<Define.DEL_DEC_STATES_MAX; psDelDecIni_i++)
        {
            SKP_Silk_NSQ_del_dec__psDelDec[psDelDecIni_i] = new NSQDelDecStruct();
        }

        for(int Ini_i=0; Ini_i<Define.DEL_DEC_STATES_MAX; Ini_i++)
        {
            for(int Ini_j=0; Ini_j<2; Ini_j++)
            {
                SKP_Silk_noise_shape_quantizer_del_dec__psSampleState[Ini_i][Ini_j] = new NSQ_sample_struct();
            }
        }
    }
}
