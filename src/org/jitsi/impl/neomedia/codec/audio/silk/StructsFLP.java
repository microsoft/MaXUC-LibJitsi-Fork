/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.codec.audio.silk;

import java.util.*;

/**
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
public class StructsFLP
{
}

/**
 * Noise shaping analysis state.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
class SKP_Silk_shape_state_FLP
{
    int     LastGainIndex;
    float   HarmBoost_smth;
    float   HarmShapeGain_smth;
    float   Tilt_smth;

    /**
     * set all fields of the instance to zero
     */
    public void zero()
    {
        this.LastGainIndex = 0;
        this.HarmBoost_smth = 0;
        this.HarmShapeGain_smth = 0;
        this.Tilt_smth = 0;
    }
}

/**
 * Prefilter state
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
class SKP_Silk_prefilter_state_FLP
{
    final float[]   sLTP_shp1 = new float[ Define.LTP_BUF_LENGTH ];
    final float[]   sLTP_shp2 = new float[ Define.LTP_BUF_LENGTH ];
    final float[]   sAR_shp1 = new float[ Define.SHAPE_LPC_ORDER_MAX + 1 ];
    final float[]   sAR_shp2 = new float[ Define.SHAPE_LPC_ORDER_MAX ];
    int     sLTP_shp_buf_idx1;
    int     sLTP_shp_buf_idx2;
    int     sAR_shp_buf_idx2;
    float   sLF_AR_shp1;
    float   sLF_MA_shp1;
    float   sLF_AR_shp2;
    float   sLF_MA_shp2;
    float   sHarmHP;
    int   rand_seed;
    int     lagPrev;

    /**
     * set all fields of the instance to zero
     */
    public void zero()
    {
        Arrays.fill(this.sAR_shp1, 0);
        Arrays.fill(this.sAR_shp2, 0);
        Arrays.fill(this.sLTP_shp1, 0);
        Arrays.fill(this.sLTP_shp2, 0);

        this.sLTP_shp_buf_idx1 = 0;
        this.sLTP_shp_buf_idx2 = 0;
        this.sAR_shp_buf_idx2 = 0;
        this.sLF_AR_shp1 = 0;
        this.sLF_AR_shp2 = 0;
        this.sLF_MA_shp1 = 0;
        this.sLF_MA_shp2 = 0;
        this.sHarmHP = 0;
        this.rand_seed = 0;
        this.lagPrev = 0;
    }
}

/**
 * Prediction analysis state
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
class SKP_Silk_predict_state_FLP
{
    int     pitch_LPC_win_length;
    int     min_pitch_lag;                      /* Lowest possible pitch lag (samples)  */
    int     max_pitch_lag;                      /* Highest possible pitch lag (samples) */
    final float[]   prev_NLSFq = new float[ Define.MAX_LPC_ORDER ];        /* Previously quantized NLSF vector     */

    /**
     * set all fields of the instance to zero
     */
    public void zero()
    {
        this.pitch_LPC_win_length = 0;
        this.max_pitch_lag = 0;
        this.min_pitch_lag = 0;
        Arrays.fill(this.prev_NLSFq, 0.0f);
    }
}

/*******************************************/
/* Structure containing NLSF MSVQ codebook */
/*******************************************/
/* structure for one stage of MSVQ */
class SKP_Silk_NLSF_CBS_FLP
{
    public SKP_Silk_NLSF_CBS_FLP()
    {
        super();
    }

    public SKP_Silk_NLSF_CBS_FLP(int nVectors, float[] CB, float[] Rates)
    {
        this.nVectors = nVectors;
        this.CB = CB;
        this.Rates = Rates;
    }

    public SKP_Silk_NLSF_CBS_FLP(int nVectors, float[] CB, int CB_offset, float[] Rates, int Rates_offset)
    {
        this.nVectors = nVectors;
        this.CB = new float[CB.length - CB_offset];
        System.arraycopy(CB, CB_offset, this.CB, 0, this.CB.length);
        this.Rates = new float[Rates.length - Rates_offset];
        System.arraycopy(Rates, Rates_offset, this.Rates, 0, this.Rates.length);
    }

    int         nVectors;
    float[]     CB;
    float[]     Rates;
}

class SKP_Silk_NLSF_CB_FLP
{
    public SKP_Silk_NLSF_CB_FLP()
    {
        super();
    }

    public SKP_Silk_NLSF_CB_FLP(int nStages, SKP_Silk_NLSF_CBS_FLP[] CBStages,
            float[] NDeltaMin, int[] CDF, int[][] StartPtr, int[] MiddleIx)
    {
        this.nStages = nStages;
        this.CBStages = CBStages;
        this.NDeltaMin = NDeltaMin;
        this.CDF = CDF;
        this.StartPtr = StartPtr;
        this.MiddleIx = MiddleIx;
    }
//const SKP_int32                         nStages;
    int                         nStages;

    /* fields for (de)quantizing */
    SKP_Silk_NLSF_CBS_FLP[] CBStages;
    float[]                         NDeltaMin;

    /* fields for arithmetic (de)coding */
//    const SKP_uint16                        *CDF;
    int[] CDF;
//    const SKP_uint16 * const                *StartPtr;
    int[][] StartPtr;
//    const SKP_int                           *MiddleIx;
    int[] MiddleIx;
}

/************************************/
/* Noise shaping quantization state */
/************************************/

/**
 * Encoder state FLP.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
class SKP_Silk_encoder_state_FLP
{
    final SKP_Silk_encoder_state              sCmn = new SKP_Silk_encoder_state(); /* Common struct, shared with fixed-point code */

    float                           variable_HP_smth1;          /* State of first smoother */
    float                           variable_HP_smth2;          /* State of second smoother */

    final SKP_Silk_shape_state_FLP            sShape = new SKP_Silk_shape_state_FLP();                     /* Noise shaping state */
    final SKP_Silk_prefilter_state_FLP        sPrefilt = new SKP_Silk_prefilter_state_FLP();                   /* Prefilter State */
    final SKP_Silk_predict_state_FLP          sPred = new SKP_Silk_predict_state_FLP();                      /* Prediction State */
    final SKP_Silk_nsq_state                  sNSQ = new SKP_Silk_nsq_state();                       /* Noise Shape Quantizer State */
    final SKP_Silk_nsq_state                  sNSQ_LBRR = new SKP_Silk_nsq_state();                  /* Noise Shape Quantizer State ( for low bitrate redundancy )*/

    NoiseShapingQuantizerFP noiseShapingQuantizerCB;
    void    NoiseShapingQuantizer( SKP_Silk_encoder_state psEnc, SKP_Silk_encoder_control psEncCtrl, SKP_Silk_nsq_state NSQ, final short[]x ,
        byte[]q , final int arg6, final short[] arg7, final short[]arg8, final short[]arg9, final int[]arg10,
         final int []arg11, final int[]arg12, final int[]arg13, int arg14 , final int arg15, final EncodeMem mem
    )
    {
        noiseShapingQuantizerCB.NoiseShapingQuantizer(psEnc, psEncCtrl, NSQ, x, q, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, mem);
    }

    /* Buffer for find pitch and noise shape analysis */
    final float[]                         x_buf = new float[ 2 * Define.MAX_FRAME_LENGTH + Define.LA_SHAPE_MAX ];/* Buffer for find pitch and noise shape analysis */
// djinn: add a parameter: offset
    int                             x_buf_offset;
    float                           LTPCorr;                    /* Normalized correlation from pitch lag estimator */
    float                           mu_LTP;                     /* Rate-distortion tradeoff in LTP quantization */
    float                           SNR_dB;                     /* Quality setting */
    float                           avgGain;                    /* average gain during active speech */
    float                           BufferedInChannel_ms;       /* Simulated number of ms buffer in channel because of exceeded TargetRate_bps */
    float                           speech_activity;            /* Speech activity */
    float                           pitchEstimationThreshold;   /* Threshold for pitch estimator */

    /* Parameters for LTP scaling control */
    float                           prevLTPredCodGain;
    float                           HPLTPredCodGain;

    float                           inBandFEC_SNR_comp;         /* Compensation to SNR_DB when using inband FEC Voiced */

    final SKP_Silk_NLSF_CB_FLP[]    psNLSF_CB_FLP = new SKP_Silk_NLSF_CB_FLP[ 2 ];        /* Pointers to voiced/unvoiced NLSF codebooks */
}

/**
 * Encoder control FLP
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
class SKP_Silk_encoder_control_FLP
{
    final SKP_Silk_encoder_control    sCmn = new SKP_Silk_encoder_control();                               /* Common struct, shared with fixed-point code */

    /* Prediction and coding parameters */
    final float[]                 Gains = new float[Define.NB_SUBFR];
    final float[][]               PredCoef = new float[ 2 ][ Define.MAX_LPC_ORDER ];     /* holds interpolated and final coefficients */
    final float[]                 LTPCoef = new float[Define.LTP_ORDER * Define.NB_SUBFR];
    float                   LTP_scale;

    /* Prediction and coding parameters */
    final int[]                   Gains_Q16 = new int[ Define.NB_SUBFR ];
    final short PredCoef_Q12[][] = new short[ 2 ][Define.MAX_LPC_ORDER];

    final short[]                 LTPCoef_Q14 = new short[ Define.LTP_ORDER * Define.NB_SUBFR ];
    int                     LTP_scale_Q14;

    /* Noise shaping parameters */
    /* Testing */
    final short[] AR2_Q13 = new short[Define.NB_SUBFR * Define.SHAPE_LPC_ORDER_MAX];

    final int[]                   LF_shp_Q14 = new int[        Define.NB_SUBFR ];      /* Packs two int16 coefficients per int32 value             */
    final int[]                   Tilt_Q14 = new int[          Define.NB_SUBFR ];
    final int[]                   HarmShapeGain_Q14 = new int[ Define.NB_SUBFR ];
    int                     Lambda_Q10;

    /* Noise shaping parameters */
    final float[]                 AR1 = new float[ Define.NB_SUBFR * Define.SHAPE_LPC_ORDER_MAX ];
    final float[]                 AR2 = new float[ Define.NB_SUBFR * Define.SHAPE_LPC_ORDER_MAX ];
    final float[]                 LF_MA_shp = new float[     Define.NB_SUBFR ];
    final float[]                 LF_AR_shp = new float[     Define.NB_SUBFR ];
    final float[]                 GainsPre = new float[      Define.NB_SUBFR ];
    final float[]                 HarmBoost = new float[     Define.NB_SUBFR ];
    final float[]                 Tilt = new float[          Define.NB_SUBFR ];
    final float[]                 HarmShapeGain = new float[ Define.NB_SUBFR ];
    float                   Lambda;
    float                   input_quality;
    float                   coding_quality;
    float                   pitch_freq_low_Hz;
    float                   current_SNR_dB;

    /* Measures */
    float                   sparseness;
    float                   LTPredCodGain;
    final float[]           input_quality_bands = new float[ Define.VAD_N_BANDS ];
    float                   input_tilt;
    final float[]           ResNrg = new float[ Define.NB_SUBFR ];                 /* Residual energy per subframe */

    public void zero()
    {
        sCmn.zero();

        /* Prediction and coding parameters */
        Arrays.fill(Gains, (float)0);
        for (float[] row : PredCoef)
            Arrays.fill(row, (float)0);
        Arrays.fill(LTPCoef, (float)0);
        LTP_scale = 0;
        Arrays.fill(Gains_Q16, 0);
        for (short[] row : PredCoef_Q12)
            Arrays.fill(row, (short)0);
        Arrays.fill(LTPCoef_Q14, (short)0);
        LTP_scale_Q14 = 0;
        Arrays.fill(AR2_Q13, (short)0);
        Arrays.fill(LF_shp_Q14, 0);
        Arrays.fill(Tilt_Q14, 0);
        Arrays.fill(HarmShapeGain_Q14, 0);
        Lambda_Q10 = 0;
        Arrays.fill(AR1, (float)0);
        Arrays.fill(AR2, (float)0);
        Arrays.fill(LF_MA_shp, (float)0);
        Arrays.fill(LF_AR_shp, (float)0);
        Arrays.fill(GainsPre, (float)0);
        Arrays.fill(HarmBoost, (float)0);
        Arrays.fill(Tilt, (float)0);
        Arrays.fill(HarmShapeGain, (float)0);
        Lambda = 0;
        input_quality = 0;
        coding_quality = 0;
        pitch_freq_low_Hz = 0;
        current_SNR_dB = 0;
        sparseness = 0;
        LTPredCodGain = 0;
        Arrays.fill(input_quality_bands, (float)0);
        input_tilt = 0;
        Arrays.fill(ResNrg, (float)0);
    }
}

interface NoiseShapingQuantizerFP
{
    /* Function pointer to noise shaping quantizer (will be set to SKP_Silk_NSQ or SKP_Silk_NSQ_del_dec) */
    void NoiseShapingQuantizer( SKP_Silk_encoder_state psEnc, SKP_Silk_encoder_control psEncCtrl, SKP_Silk_nsq_state NSQ, final short[]x ,
                                byte[]q , final int arg6, final short[] arg7, final short[]arg8, final short[]arg9, final int[]arg10,
                                final int []arg11, final int[]arg12, final int[]arg13, int arg14 , final int arg15, final EncodeMem mem
    );
}
