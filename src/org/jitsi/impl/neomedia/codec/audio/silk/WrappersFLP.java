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
public class WrappersFLP
{
    /* Convert AR filter coefficients to NLSF parameters */
    static void SKP_Silk_A2NLSF_FLP(
              float[]               pNLSF,             /* O    NLSF vector      [ LPC_order ]          */
              float[]               pAR,               /* I    LPC coefficients [ LPC_order ]          */
        final int                   LPC_order,         /* I    LPC order                               */
        final EncodeMem             mem
    )
    {
        int   i;
        int[] NLSF_fix = mem.zero(mem.SKP_Silk_A2NLSF_FLP__NLSF_fix);
        int[] a_fix_Q16 = mem.zero(mem.SKP_Silk_A2NLSF_FLP__a_fix_Q16);

        for( i = 0; i < LPC_order; i++ )
        {
            a_fix_Q16[ i ] = SigProcFLP.SKP_float2int( pAR[ i ] * 65536.0f );
        }
        A2NLSF.SKP_Silk_A2NLSF( NLSF_fix, a_fix_Q16, LPC_order, mem );

        for( i = 0; i < LPC_order; i++ )
        {
            pNLSF[ i ] = NLSF_fix[ i ] * ( 1.0f / 32768.0f );
        }
    }

    /* Convert LSF parameters to AR prediction filter coefficients */
    static void SKP_Silk_NLSF2A_stable_FLP(
              float []                pAR,               /* O    LPC coefficients [ LPC_order ]          */
              float[]                 pNLSF,             /* I    NLSF vector      [ LPC_order ]          */
        final int                     LPC_order,         /* I    LPC order                               */
        final EncodeMem               mem

    )
    {
        int     i;
        int[]   NLSF_fix = mem.zero(mem.SKP_Silk_NLSF2A_stable_FLP__NLSF_fix);
        short[] a_fix_Q12 = mem.zero(mem.SKP_Silk_NLSF2A_stable_FLP__a_fix_Q12);

        for( i = 0; i < LPC_order; i++ )
        {
            NLSF_fix[ i ] = SigProcFLP.SKP_float2int( pNLSF[ i ] * 32768.0f );
        }

        NLSF2AStable.SKP_Silk_NLSF2A_stable( a_fix_Q12, NLSF_fix, LPC_order, mem );

        for( i = 0; i < LPC_order; i++ )
        {
            pAR[ i ] = a_fix_Q12[ i ] / 4096.0f;
        }
    }

    /* LSF stabilizer, for a single input data vector */
    static void SKP_Silk_NLSF_stabilize_FLP(
              float[]                 pNLSF,             /* I/O  (Un)stable NLSF vector [ LPC_order ]    */
              float[]                 pNDelta_min,       /* I    Normalized delta min vector[LPC_order+1]*/
        final int                     LPC_order,         /* I    LPC order                               */
        final EncodeMem               mem
    )
    {
        int   i;
        int[] NLSF_Q15 = mem.zero(mem.SKP_Silk_NLSF_stabilize_FLP__NLSF_Q15);
        int[] ndelta_min_Q15 = mem.zero(mem.SKP_Silk_NLSF_stabilize_FLP__ndelta_min_Q15);

        for( i = 0; i < LPC_order; i++ )
        {
            NLSF_Q15[       i ] = SigProcFLP.SKP_float2int( pNLSF[       i ] * 32768.0f );
            ndelta_min_Q15[ i ] = SigProcFLP.SKP_float2int( pNDelta_min[ i ] * 32768.0f );
        }
        ndelta_min_Q15[ LPC_order ] = SigProcFLP.SKP_float2int( pNDelta_min[ LPC_order ] * 32768.0f );

        /* NLSF stabilizer, for a single input data vector */
        NLSFStabilize.SKP_Silk_NLSF_stabilize( NLSF_Q15, ndelta_min_Q15, LPC_order );

        for( i = 0; i < LPC_order; i++ )
        {
            pNLSF[ i ] = NLSF_Q15[ i ] * ( 1.0f / 32768.0f );
        }
    }

    /* Interpolation function with fixed point rounding */
    static void SKP_Silk_interpolate_wrapper_FLP(
              float                 xi[],               /* O    Interpolated vector                     */
              float                 x0[],               /* I    First vector                            */
              float                 x1[],               /* I    Second vector                           */
        final float                 ifact,              /* I    Interp. factor, weight on second vector */
        final int                   d,                  /* I    Number of parameters                    */
        final EncodeMem             mem
    )
    {
        int[] x0_int = mem.zero(mem.SKP_Silk_interpolate_wrapper_FLP__x0_int);
        int[] x1_int = mem.zero(mem.SKP_Silk_interpolate_wrapper_FLP__x1_int);
        int[] xi_int = mem.zero(mem.SKP_Silk_interpolate_wrapper_FLP__xi_int);
        int ifact_Q2 = ( int )( ifact * 4.0f );
        int i;

        /* Convert input from flp to fix */
        for( i = 0; i < d; i++ ) {
            x0_int[ i ] = SigProcFLP.SKP_float2int( x0[ i ] * 32768.0f );
            x1_int[ i ] = SigProcFLP.SKP_float2int( x1[ i ] * 32768.0f );
        }

        /* Interpolate two vectors */
        Interpolate.SKP_Silk_interpolate( xi_int, x0_int, x1_int, ifact_Q2, d );

        /* Convert output from fix to flp */
        for( i = 0; i < d; i++ )
        {
            xi[ i ] = xi_int[ i ] * ( 1.0f / 32768.0f );
        }
    }

    /****************************************/
    /* Floating-point Silk VAD wrapper      */
    /****************************************/
    static int SKP_Silk_VAD_FLP(
        SKP_Silk_encoder_state_FLP      psEnc,             /* I/O  Encoder state FLP                       */
        SKP_Silk_encoder_control_FLP    psEncCtrl,         /* I/O  Encoder control FLP                     */
        short[]                         pIn,               /* I    Input signal                            */
        final EncodeMem                 mem
    )
    {
        int i, ret;
        int[] SA_Q8 = mem.zero(mem.SKP_Silk_VAD_FLP__SA_Q8);
        int[] SNR_dB_Q7 = mem.zero(mem.SKP_Silk_VAD_FLP__SNR_dB_Q7);
        int[] Tilt_Q15 = mem.zero(mem.SKP_Silk_VAD_FLP__Tilt_Q15);
        int[] Quality_Bands_Q15 = mem.zero(mem.SKP_Silk_VAD_FLP__Quality_Bands_Q15);

        ret = VAD.SKP_Silk_VAD_GetSA_Q8( psEnc.sCmn.sVAD, SA_Q8, SNR_dB_Q7, Quality_Bands_Q15, Tilt_Q15,
            pIn, psEnc.sCmn.frame_length, mem );

        psEnc.speech_activity = SA_Q8[0] / 256.0f;
        for( i = 0; i < Define.VAD_N_BANDS; i++ )
        {
            psEncCtrl.input_quality_bands[ i ] = Quality_Bands_Q15[ i ] / 32768.0f;
        }
        psEncCtrl.input_tilt = Tilt_Q15[0] / 32768.0f;

        return ret;
    }

    /****************************************/
    /* Floating-point Silk NSQ wrapper      */
    /****************************************/
    static void SKP_Silk_NSQ_wrapper_FLP(
        SKP_Silk_encoder_state_FLP      psEnc,      /* I/O  Encoder state FLP                           */
        SKP_Silk_encoder_control_FLP    psEncCtrl,  /* I/O  Encoder control FLP                         */
              float                 x[],            /* I    Prefiltered input signal                    */
              byte                  q[],            /* O    Quantized pulse signal                      */
        final int                   useLBRR,        /* I    LBRR flag                                   */
        final EncodeMem             mem
    )
    {
        int     i, j;
        float   tmp_float;
        short[]   x_16 = mem.zero(mem.SKP_Silk_NSQ_wrapper_FLP__x_16);
        /* Prediction and coding parameters */
        int[]   Gains_Q16 = mem.zero(mem.SKP_Silk_NSQ_wrapper_FLP__Gains_Q16);
        short[][] PredCoef_Q12 = mem.zero(mem.SKP_Silk_NSQ_wrapper_FLP__PredCoef_Q12);
        short[]   LTPCoef_Q14 = mem.zero(mem.SKP_Silk_NSQ_wrapper_FLP__LTPCoef_Q14);
        int     LTP_scale_Q14;

        /* Noise shaping parameters */
        /* Testing */
        short[] AR2_Q13 = mem.zero(mem.SKP_Silk_NSQ_wrapper_FLP__AR2_Q13);
        int[]   LF_shp_Q14 = mem.zero(mem.SKP_Silk_NSQ_wrapper_FLP__LF_shp_Q14);
        int     Lambda_Q10;
        int[]   Tilt_Q14 = mem.zero(mem.SKP_Silk_NSQ_wrapper_FLP__Tilt_Q14);
        int[]   HarmShapeGain_Q14 = mem.zero(mem.SKP_Silk_NSQ_wrapper_FLP__HarmShapeGain_Q14);

        /* Convert control struct to fix control struct */
        /* Noise shape parameters */
        for( i = 0; i < Define.NB_SUBFR * Define.SHAPE_LPC_ORDER_MAX; i++ )
        {
            AR2_Q13[ i ] = (short)SigProcFIX.SKP_SAT16( SigProcFLP.SKP_float2int( psEncCtrl.AR2[ i ] * 8192.0f ) );
        }

        for( i = 0; i < Define.NB_SUBFR; i++ )
        {
            LF_shp_Q14[ i ] =   ( SigProcFLP.SKP_float2int( psEncCtrl.LF_AR_shp[ i ]     * 16384.0f ) << 16 ) |
                                  ( 0x0000FFFF & SigProcFLP.SKP_float2int( psEncCtrl.LF_MA_shp[ i ]     * 16384.0f ) );
            Tilt_Q14[ i ]   =        SigProcFLP.SKP_float2int( psEncCtrl.Tilt[ i ]          * 16384.0f );
            HarmShapeGain_Q14[ i ] = SigProcFLP.SKP_float2int( psEncCtrl.HarmShapeGain[ i ] * 16384.0f );
        }
        Lambda_Q10 = SigProcFLP.SKP_float2int( psEncCtrl.Lambda * 1024.0f );

        /* prediction and coding parameters */
        for( i = 0; i < Define.NB_SUBFR * Define.LTP_ORDER; i++ )
        {
            LTPCoef_Q14[ i ] = ( short )SigProcFLP.SKP_float2int( psEncCtrl.LTPCoef[ i ] * 16384.0f );
        }

        for( j = 0; j < Define.NB_SUBFR >> 1; j++ )
        {
            for( i = 0; i < Define.MAX_LPC_ORDER; i++ )
            {
                PredCoef_Q12[ j ][ i ] = ( short )SigProcFLP.SKP_float2int( psEncCtrl.PredCoef[ j ][ i ] * 4096.0f );
            }
        }

        for( i = 0; i < Define.NB_SUBFR; i++ )
        {
            tmp_float = SigProcFIX.SKP_LIMIT( ( psEncCtrl.Gains[ i ] * 65536.0f ), 2147483000.0f, -2147483000.0f );
            Gains_Q16[ i ] = SigProcFLP.SKP_float2int( tmp_float );
            if( psEncCtrl.Gains[ i ] > 0.0f )
            {
                assert( tmp_float >= 0.0f );
                assert( Gains_Q16[ i ] >= 0 );
            }
        }

        if( psEncCtrl.sCmn.sigtype == Define.SIG_TYPE_VOICED ) {

            LTP_scale_Q14 = TablesOther.SKP_Silk_LTPScales_table_Q14[ psEncCtrl.sCmn.LTP_scaleIndex ];
        }
        else
        {
            LTP_scale_Q14 = 0;
        }

        /* Convert input to fix */
        SigProcFLP.SKP_float2short_array( x_16, x, psEnc.sCmn.frame_length );

        /* Call NSQ */
        short[] PredCoef_Q12_dim1_tmp = mem.zero(mem.SKP_Silk_NSQ_wrapper_FLP__PredCoef_Q12_dim1_tmp);
        int PredCoef_Q12_offset = 0;
        for(int PredCoef_Q12_i = 0; PredCoef_Q12_i < PredCoef_Q12.length; PredCoef_Q12_i++)
        {
            System.arraycopy(PredCoef_Q12[PredCoef_Q12_i],0, PredCoef_Q12_dim1_tmp, PredCoef_Q12_offset, PredCoef_Q12[PredCoef_Q12_i].length);
            PredCoef_Q12_offset += PredCoef_Q12[PredCoef_Q12_i].length;
        }
        if( useLBRR!=0 )
        {
            psEnc.NoiseShapingQuantizer( psEnc.sCmn, psEncCtrl.sCmn, psEnc.sNSQ_LBRR,
                x_16, q, psEncCtrl.sCmn.NLSFInterpCoef_Q2, PredCoef_Q12_dim1_tmp, LTPCoef_Q14, AR2_Q13,
                HarmShapeGain_Q14, Tilt_Q14, LF_shp_Q14, Gains_Q16, Lambda_Q10, LTP_scale_Q14, mem );
        }
        else
        {
            psEnc.NoiseShapingQuantizer( psEnc.sCmn, psEncCtrl.sCmn, psEnc.sNSQ,
                x_16, q, psEncCtrl.sCmn.NLSFInterpCoef_Q2, PredCoef_Q12_dim1_tmp, LTPCoef_Q14, AR2_Q13,
                HarmShapeGain_Q14, Tilt_Q14, LF_shp_Q14, Gains_Q16, Lambda_Q10, LTP_scale_Q14, mem );
        }
    }
}
