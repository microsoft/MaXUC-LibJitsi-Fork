/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
// Portions (c) Microsoft Corporation. All rights reserved.
package org.jitsi.impl.neomedia.codec.audio.silk;

/**
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
public class HPVariableCutoffFLP
{
    /**
     * High-pass filter with cutoff frequency adaptation based on pitch lag statistics.
     * @param psEnc Encoder state FLP
     * @param psEncCtrl Encoder control FLP
     * @param out High-pass filtered output signal
     * @param in Input signal
     */
    static void SKP_Silk_HP_variable_cutoff_FLP(
        SKP_Silk_encoder_state_FLP      psEnc,             /* I/O  Encoder state FLP                       */
        SKP_Silk_encoder_control_FLP    psEncCtrl,         /* I/O  Encoder control FLP                     */
              short[]                   out,               /* O    High-pass filtered output signal        */
              short[]                   in,                /* I    Input signal                            */
              final EncodeMem           mem
    )
    {
        float pitch_freq_Hz, pitch_freq_log, quality, delta_freq, smth_coef, Fc, r;
        int[] B_Q28 = mem.zero(mem.SKP_Silk_HP_variable_cutoff_FLP__B_Q28);
        int[] A_Q28 = mem.zero(mem.SKP_Silk_HP_variable_cutoff_FLP__A_Q28);

        /*********************************************/
        /* Estimate low end of pitch frequency range */
        /*********************************************/
        if( psEnc.sCmn.prev_sigtype == Define.SIG_TYPE_VOICED )
        {
            /* Difference, in log domain */
            pitch_freq_Hz  = 1e3f * psEnc.sCmn.fs_kHz / psEnc.sCmn.prevLag;
            pitch_freq_log = MainFLP.SKP_Silk_log2( pitch_freq_Hz );

            /* Adjustment based on quality */
            quality = psEncCtrl.input_quality_bands[ 0 ];
            pitch_freq_log -= quality * quality * ( pitch_freq_log - MainFLP.SKP_Silk_log2( DefineFLP.VARIABLE_HP_MIN_FREQ ) );
            pitch_freq_log += 0.5f * ( 0.6f - quality );

            delta_freq = pitch_freq_log - psEnc.variable_HP_smth1;
            if( delta_freq < 0.0 )
            {
                /* Less smoothing for decreasing pitch frequency, to track something close to the minimum */
                delta_freq *= 3.0f;
            }

            /* Limit delta, to reduce impact of outliers */
            delta_freq = SigProcFLP.SKP_LIMIT_float( delta_freq, -DefineFLP.VARIABLE_HP_MAX_DELTA_FREQ, DefineFLP.VARIABLE_HP_MAX_DELTA_FREQ );

            /* Update smoother */
            smth_coef = DefineFLP.VARIABLE_HP_SMTH_COEF1 * psEnc.speech_activity;
            psEnc.variable_HP_smth1 += smth_coef * delta_freq;
        }

        /* Second smoother */
        psEnc.variable_HP_smth2 += DefineFLP.VARIABLE_HP_SMTH_COEF2 * ( psEnc.variable_HP_smth1 - psEnc.variable_HP_smth2 );

        /* Convert from log scale to Hertz */
        psEncCtrl.pitch_freq_low_Hz = ( float )Math.pow( 2.0, psEnc.variable_HP_smth2 );

        /* Limit frequency range */
        psEncCtrl.pitch_freq_low_Hz = SigProcFLP.SKP_LIMIT_float( psEncCtrl.pitch_freq_low_Hz, DefineFLP.VARIABLE_HP_MIN_FREQ, DefineFLP.VARIABLE_HP_MAX_FREQ );

        /*******************************/
        /* Compute filter coefficients */
        /*******************************/
        /* Compute cut-off frequency, in radians */
        Fc = ( float )( 0.45f * 2.0f * 3.14159265359 * psEncCtrl.pitch_freq_low_Hz / ( 1e3f * psEnc.sCmn.fs_kHz ) );

        /* 2nd order ARMA coefficients */
        r = 1.0f - 0.92f * Fc;

        /* b = r * [1; -2; 1]; */
        /* a = [1; -2 * r * (1 - 0.5 * Fc^2); r^2]; */
        B_Q28[ 0 ] = SigProcFLP.SKP_float2int( ( 1 << 28 ) * r );
        B_Q28[ 1 ] = SigProcFLP.SKP_float2int( ( 1 << 28 ) * -2.0f * r );
        B_Q28[ 2 ] = B_Q28[ 0 ];
        A_Q28[ 0 ] = SigProcFLP.SKP_float2int( ( 1 << 28 ) * -2.0f * r * ( 1.0f - 0.5f * Fc * Fc ) );
        A_Q28[ 1 ] = SigProcFLP.SKP_float2int( ( 1 << 28 ) * r * r );

        /********************/
        /* High-pass filter */
        /********************/
        BiquadAlt.SKP_Silk_biquad_alt( in, B_Q28, A_Q28, psEnc.sCmn.In_HP_State, out, psEnc.sCmn.frame_length );
    }
}
