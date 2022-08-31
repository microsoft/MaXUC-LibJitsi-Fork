/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.codec.audio.silk;

/**
 * So we only allocate memory blocks once per thread.
 * The user MUST memZero it before each use, to avoid errors using
 * uninitialized data.
 */
public class DecodeMem extends BaseMem
{
    // Make the blocks public to save having to write getter methods.

    final SKP_Silk_decoder_control SKP_Silk_decode_frame__sDecCtrl = new SKP_Silk_decoder_control();
    final int[]   SKP_Silk_decode_frame__Pulses = new int[ Define.MAX_FRAME_LENGTH ];

    final SKP_Silk_decoder_state   SKP_Silk_SDK_search_for_LBRR__sDec = new SKP_Silk_decoder_state();
    final SKP_Silk_decoder_control SKP_Silk_SDK_search_for_LBRR__sDecCtrl = new SKP_Silk_decoder_control();
    final int[]   SKP_Silk_SDK_search_for_LBRR__TempQ = new int[ Define.MAX_FRAME_LENGTH ];

    final int[]   SKP_Silk_decode_signs__data_ptr = new int[1];
    final int[]   SKP_Silk_decode_signs__cdf = new int[3];

    final int[]   SKP_Silk_shell_decoder__pulses3 = new int[ 2 ];
    final int[]   SKP_Silk_shell_decoder__pulses2 = new int[ 4 ];
    final int[]   SKP_Silk_shell_decoder__pulses1 = new int[ 8 ];

    final int[]   SKP_Silk_decode_pulses__sum_pulses = new int[ Define.MAX_NB_SHELL_BLOCKS ];
    final int[]   SKP_Silk_decode_pulses__nLshifts = new int[ Define.MAX_NB_SHELL_BLOCKS ];
    final int[]   SKP_Silk_decode_pulses__tmp_int = new int[1];

    final int[]   SKP_Silk_decode_parameters__Ix_ptr = new int[1];
    final int[]   SKP_Silk_decode_parameters__Ixs = new int[ Define.NB_SUBFR ];
    final int[]   SKP_Silk_decode_parameters__GainsIndices = new int[ Define.NB_SUBFR ];
    final int[]   SKP_Silk_decode_parameters__NLSFIndices = new int[ Define.NLSF_MSVQ_MAX_CB_STAGES ];
    final int[]   SKP_Silk_decode_parameters__pNLSF_Q15 = new int[ Define.MAX_LPC_ORDER ];
    final int[]   SKP_Silk_decode_parameters__pNLSF0_Q15 = new int[ Define.MAX_LPC_ORDER ];
    final int[]   SKP_Silk_decode_parameters__tmp_int = new int[ 1 ];

    final short[] SKP_Silk_PLC_conceal__exc_buf = new short[Define.MAX_FRAME_LENGTH];
    final short[] SKP_Silk_PLC_conceal__A_Q12_tmp = new short[Define.MAX_LPC_ORDER];
    final int[]   SKP_Silk_PLC_conceal__shift1_ptr = new int[1];
    final int[]   SKP_Silk_PLC_conceal__shift2_ptr = new int[1];
    final int[]   SKP_Silk_PLC_conceal__energy1_ptr = new int[1];
    final int[]   SKP_Silk_PLC_conceal__energy2_ptr = new int[1];
    final int[]   SKP_Silk_PLC_conceal__sig_Q10 = new int[Define.MAX_FRAME_LENGTH];
    final int[]   SKP_Silk_PLC_conceal__tmp_int = new int[1];

    final int[]   SKP_Silk_PLC_glue_frames__energy_ptr = new int[1];
    final int[]   SKP_Silk_PLC_glue_frames__energy_shift_ptr = new int[1];
    final int[]   SKP_Silk_PLC_glue_frames__conc_energy_ptr = new int[1];
    final int[]   SKP_Silk_PLC_glue_frames__conc_energy_shift_ptr = new int[1];

    final int[]   SKP_Silk_SDK_Decode__tmp_int = new int[1];
    final short[] SKP_Silk_SDK_Decode__samplesOut_tmp = new short[Define.MAX_API_FS_KHZ * Define.FRAME_LENGTH_MS];

    final short[] SKP_Silk_decode_core__A_Q12_tmp = new short[Define.MAX_LPC_ORDER];
    final short[] SKP_Silk_decode_core__sLTP = new short[ Define.MAX_FRAME_LENGTH ];
    final int[]   SKP_Silk_decode_core__vec_Q10 = new int[ Define.MAX_FRAME_LENGTH / Define.NB_SUBFR ];
    final int[]   SKP_Silk_decode_core__FiltState = new int[ Define.MAX_LPC_ORDER ];

    final short[] SKP_Silk_CNG__LPC_buf = new short[Define.MAX_LPC_ORDER];
    final short[] SKP_Silk_CNG__CNG_sig = new short[Define.MAX_FRAME_LENGTH];
    final int[]   SKP_Silk_CNG__tmp_int = new int[1];

    final int[]   SKP_Silk_range_coder_check_after_decoding__nBytes_ptr = new int[1];
}
