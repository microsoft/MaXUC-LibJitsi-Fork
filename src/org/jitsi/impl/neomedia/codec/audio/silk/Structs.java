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
public class Structs
{
}

/**
 * Noise shaping quantization state.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
class SKP_Silk_nsq_state
{
    final short[] xq = new short[2 * Define.MAX_FRAME_LENGTH]; /* Buffer for quantized output signal */
    final int[]   sLTP_shp_Q10 = new int[ 2 * Define.MAX_FRAME_LENGTH ];
    final int[]   sLPC_Q14 = new int[ Define.MAX_FRAME_LENGTH / Define.NB_SUBFR + Define.MAX_LPC_ORDER ];
    final int[]   sAR2_Q14 = new int[ Define.SHAPE_LPC_ORDER_MAX ];
    int     sLF_AR_shp_Q12;
    int     lagPrev;
    int     sLTP_buf_idx;
    int     sLTP_shp_buf_idx;
    int     rand_seed;
    int     prev_inv_gain_Q16;
    int     rewhite_flag;

    public void copyFrom(SKP_Silk_nsq_state input)
    {
        System.arraycopy(input.xq , 0, this.xq, 0, input.xq.length);
        System.arraycopy(input.sLTP_shp_Q10, 0, this.sLTP_shp_Q10, 0, input.sLTP_shp_Q10.length);
        System.arraycopy(input.sLPC_Q14, 0, this.sLPC_Q14, 0, input.sLPC_Q14.length);
        System.arraycopy(input.sAR2_Q14, 0, this.sAR2_Q14, 0, input.sAR2_Q14.length);
        this.sLF_AR_shp_Q12 = input.sLF_AR_shp_Q12;
        this.lagPrev = input.lagPrev;
        this.sLTP_buf_idx = input.sLTP_buf_idx;
        this.sLTP_shp_buf_idx = input.sLTP_shp_buf_idx;
        this.rand_seed = input.rand_seed;
        this.prev_inv_gain_Q16 = input.prev_inv_gain_Q16;
        this.rewhite_flag = input.rewhite_flag;
    }

    /**
     * set all fields of the instance to zero
     */
    public void zero()
    {
        Arrays.fill(this.sAR2_Q14, 0);
        Arrays.fill(this.sLPC_Q14, 0);
        Arrays.fill(this.sLTP_shp_Q10, 0);
        Arrays.fill(this.xq, (short)0);

        this.lagPrev = 0;
        this.prev_inv_gain_Q16 = 0;
        this.rand_seed = 0;
        this.rewhite_flag = 0;
        this.sLF_AR_shp_Q12 = 0;
        this.sLTP_buf_idx = 0;
        this.sLTP_shp_buf_idx = 0;
    }
}/* FIX*/

/**
 * Class for Low BitRate Redundant (LBRR) information.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
class SKP_SILK_LBRR_struct
{
    final byte[]      payload = new byte[Define.MAX_ARITHM_BYTES];
    int         nBytes;                         /* Number of bytes in payload                               */
    int         usage;                          /* Tells how the payload should be used as FEC              */

    public void zero()
    {
        Arrays.fill(payload, (byte)0);
        nBytes = 0;
        usage = 0;
    }
}

/**
 * VAD state.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
class SKP_Silk_VAD_state
{
    final int[]     AnaState = new int[ 2 ];                  /* Analysis filterbank state: 0-8 kHz                       */
    final int[]     AnaState1 = new int[ 2 ];                 /* Analysis filterbank state: 0-4 kHz                       */
    final int[]     AnaState2 = new int[ 2 ];                 /* Analysis filterbank state: 0-2 kHz                       */
    final int[]     XnrgSubfr = new int[ Define.VAD_N_BANDS ];       /* Subframe energies                                        */
    final int[]     NrgRatioSmth_Q8 = new int[ Define.VAD_N_BANDS ]; /* Smoothed energy level in each band                       */
    short     HPstate;                        /* State of differentiator in the lowest band               */
    final int[]     NL = new int[ Define.VAD_N_BANDS ];              /* Noise energy level in each band                          */
    final int[]     inv_NL = new int[ Define.VAD_N_BANDS ];          /* Inverse noise energy level in each band                  */
    final int[]     NoiseLevelBias = new int[ Define.VAD_N_BANDS ];  /* Noise level estimator bias/offset                        */
    int   counter;                        /* Frame counter used in the initial phase                  */
}

/**
 * Range encoder/decoder state.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
class SKP_Silk_range_coder_state
{
    int   bufferLength;
    int   bufferIx;
    long  base_Q32;
    long  range_Q16;
    int   error;
    final byte[] buffer = new byte[Define.MAX_ARITHM_BYTES];/* Buffer containing payload                                */

    public void zero()
    {
        int   bufferLength = 0;
        int   bufferIx = 0;
        long  base_Q32 = 0;
        long  range_Q16 = 0;
        int   error = 0;
        Arrays.fill(buffer, (byte)0);
    }
}

/**
 * Input frequency range detection struct.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
class SKP_Silk_detect_SWB_state
{
    final int[][] S_HP_8_kHz = new int[ Define.NB_SOS ][ 2 ];  /* HP filter State */
    int     ConsecSmplsAboveThres;
    int     ActiveSpeech_ms;            /* Accumulated time with active speech */
    int     SWB_detected;               /* Flag to indicate SWB input */
    int     WB_detected;                /* Flag to indicate WB input */
}

/**
 * Variable cut-off low-pass filter state.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
class SKP_Silk_LP_state
{
    final int[] In_LP_State = new int[ 2 ];           /* Low pass filter state */
    int   transition_frame_no;        /* Counter which is mapped to a cut-off frequency */
    int   mode;                       /* Operating mode, 0: switch down, 1: switch up */
}

/**
 * Class for one stage of MSVQ.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
class SKP_Silk_NLSF_CBS
{
    public SKP_Silk_NLSF_CBS(int nVectors, short[] SKP_Silk_NLSF_MSVQ_CB0_10_Q15, int Q15_offset,
                            short[] SKP_Silk_NLSF_MSVQ_CB0_10_rates_Q5, int Q5_offset)
    {
        this.nVectors = nVectors;
        this.CB_NLSF_Q15 = new short[SKP_Silk_NLSF_MSVQ_CB0_10_Q15.length-Q15_offset];
        System.arraycopy( SKP_Silk_NLSF_MSVQ_CB0_10_Q15, Q15_offset, this.CB_NLSF_Q15, 0, this.CB_NLSF_Q15.length);
        this.Rates_Q5 = new short[SKP_Silk_NLSF_MSVQ_CB0_10_rates_Q5.length - Q5_offset];
        System.arraycopy(SKP_Silk_NLSF_MSVQ_CB0_10_rates_Q5, Q5_offset, this.Rates_Q5, 0, this.Rates_Q5.length);
    }

    //TODO: the three fields are constant in C.
    final int       nVectors;
    final short[]   CB_NLSF_Q15;
    final short[]   Rates_Q5;
}

/**
 * Class containing NLSF MSVQ codebook.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
class SKP_Silk_NLSF_CB_struct
{
    public SKP_Silk_NLSF_CB_struct(int nStates, SKP_Silk_NLSF_CBS[] CBStages, int[] NDeltaMin_Q15,
                                    int[] CDF, int[][] StartPtr, int[] MiddleIx)
    {
        this.CBStages      = CBStages;
        this.CDF           = CDF;
        this.MiddleIx      = MiddleIx;
        this.NDeltaMin_Q15 = NDeltaMin_Q15;
        this.nStages       = nStates;
        this.StartPtr      = StartPtr;
    }
    public SKP_Silk_NLSF_CB_struct()
    {
        super();
    }
//TODO: this filed is constant in C.
    int                 nStages;

    /* Fields for (de)quantizing */
//TODO:CBStates should be defined as an array or an object reference?
    SKP_Silk_NLSF_CBS[]   CBStages;
    int[]                 NDeltaMin_Q15;

    /* Fields for arithmetic (de)coding */
    int[]                CDF;
    int[][]              StartPtr;
    int[]                MiddleIx;

    public void zero()
    {
        nStages = 0;
        CBStages = null;
        NDeltaMin_Q15 = null;
        CDF = null;
        StartPtr = null;
        MiddleIx = null;
    }
}

/**
 * Encoder state.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
class SKP_Silk_encoder_state
{
    final SKP_Silk_range_coder_state      sRC = new SKP_Silk_range_coder_state();                            /* Range coder state                                                    */
    final SKP_Silk_range_coder_state      sRC_LBRR = new SKP_Silk_range_coder_state();                       /* Range coder state (for low bitrate redundancy)                       */
    final int[]                           In_HP_State = new int[ 2 ];     /* High pass filter state                                               */
    final SKP_Silk_LP_state               sLP = new SKP_Silk_LP_state();                            /* Low pass filter state */
    final SKP_Silk_VAD_state              sVAD = new SKP_Silk_VAD_state();                           /* Voice activity detector state                                        */

    int                         LBRRprevLastGainIndex;
    int                         prev_sigtype;
    int                         typeOffsetPrev;                 /* Previous signal type and quantization offset                         */
    int                         prevLag;
    int                         API_fs_Hz;                      /* API sampling frequency (Hz)                                          */
    int                         prev_API_fs_Hz;                 /* Previous API sampling frequency (Hz)                                 */
    int                         maxInternal_fs_kHz;             /* Maximum internal sampling frequency (kHz)                            */
    int                         fs_kHz;                         /* Internal sampling frequency (kHz)                                    */
    int                         fs_kHz_changed;                 /* Did we switch yet?                                                   */
    int                         frame_length;                   /* Frame length (samples)                                               */
    int                         subfr_length;                   /* Subframe length (samples)                                            */
    int                         la_pitch;                       /* Look-ahead for pitch analysis (samples)                              */
    int                         la_shape;                       /* Look-ahead for noise shape analysis (samples)                        */
    int                         TargetRate_bps;                 /* Target bitrate (bps)                                                 */
    int                         PacketSize_ms;                  /* Number of milliseconds to put in each packet                         */
    int                         PacketLoss_perc;                /* Packet loss rate measured by farend                                  */
    int                         frameCounter;
    int                         Complexity;                     /* Complexity setting: 0-> low; 1-> medium; 2->high                     */
    int                         nStatesDelayedDecision;         /* Number of states in delayed decision quantization                    */
    int                         useInterpolatedNLSFs;           /* Flag for using NLSF interpolation                                    */
    int                         shapingLPCOrder;                /* Filter order for noise shaping filters                               */
    int                         predictLPCOrder;                /* Filter order for prediction filters                                  */
    int                         pitchEstimationComplexity;      /* Complexity level for pitch estimator                                 */
    int                         pitchEstimationLPCOrder;        /* Whitening filter order for pitch estimator                           */
    int                         LTPQuantLowComplexity;          /* Flag for low complexity LTP quantization                             */
    int                         NLSF_MSVQ_Survivors;            /* Number of survivors in NLSF MSVQ                                     */
    int                         first_frame_after_reset;        /* Flag for deactivating NLSF interp. and fluc. reduction after resets  */

    /* Input/output buffering */
    final short[]                     inputBuf = new short[ Define.MAX_FRAME_LENGTH ];   /* buffer containin input signal                                        */
    int                         inputBufIx;
    int                         nFramesInPayloadBuf;            /* number of frames sitting in outputBuf                                */
    int                         nBytesInPayloadBuf;             /* number of bytes sitting in outputBuf                                 */

    /* Parameters For LTP scaling Control */
    int                         frames_since_onset;

    final SKP_Silk_NLSF_CB_struct[]   psNLSF_CB = new SKP_Silk_NLSF_CB_struct[ 2 ];                /* Pointers to voiced/unvoiced NLSF codebooks */

    /* Struct for Inband LBRR */
    final SKP_SILK_LBRR_struct[]      LBRR_buffer = new SKP_SILK_LBRR_struct[ Define.MAX_LBRR_DELAY ];
    /*
     * LBRR_buffer is an array of references, which has to be created manually.
     */
    {
        for(int LBRR_bufferIni_i=0; LBRR_bufferIni_i<Define.MAX_LBRR_DELAY; LBRR_bufferIni_i++)
        {
            LBRR_buffer[LBRR_bufferIni_i] = new SKP_SILK_LBRR_struct();
        }
    }
    int                         oldest_LBRR_idx;
    int                         useInBandFEC;                   /* Saves the API setting for query                                      */
    int                         LBRR_enabled;
    int                         LBRR_GainIncreases;             /* Number of shifts to Gains to get LBRR rate Voiced frames             */

    /* Bitrate control */
    int                       bitrateDiff;                    /* Accumulated diff. between the target bitrate and the switch bitrates */
    int                       bitrate_threshold_up;           /* Threshold for switching to a higher internal sample frequency        */
    int                       bitrate_threshold_down;         /* Threshold for switching to a lower internal sample frequency         */
    final SKP_Silk_resampler_state_struct  resampler_state = new SKP_Silk_resampler_state_struct();

    /* DTX */
    int                         noSpeechCounter;                /* Counts concecutive nonactive frames, used by DTX                     */
    int                         useDTX;                         /* Flag to enable DTX                                                   */
    int                         inDTX;                          /* Flag to signal DTX period                                            */
    int                         vadFlag;                        /* Flag to indicate Voice Activity                                      */

    /* Struct for detecting SWB input */
    final SKP_Silk_detect_SWB_state   sSWBdetect = new SKP_Silk_detect_SWB_state();

    /* Buffers */
    final byte[]                      q = new byte[ Define.MAX_FRAME_LENGTH ];      /* pulse signal buffer */
    final byte[]                      q_LBRR = new byte[ Define.MAX_FRAME_LENGTH ]; /* pulse signal buffer */
}

/**
 * Encoder control.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
class SKP_Silk_encoder_control
{
    /* Quantization indices */
    int     lagIndex;
    int     contourIndex;
    int     PERIndex;
    final int[]   LTPIndex = new int[ Define.NB_SUBFR ];
    final int[]   NLSFIndices = new int[ Define.NLSF_MSVQ_MAX_CB_STAGES ];  /* NLSF path of quantized LSF vector   */
    int     NLSFInterpCoef_Q2;
    final int[]   GainsIndices = new int[ Define.NB_SUBFR ];
    int     Seed;
    int     LTP_scaleIndex;
    int     RateLevelIndex;
    int     QuantOffsetType;
    int     sigtype;

    /* Prediction and coding parameters */
    final int[]   pitchL = new int[ Define.NB_SUBFR ];

    int     LBRR_usage;                     /* Low bitrate redundancy usage                             */

    public void zero()
    {
        lagIndex = 0;
        contourIndex = 0;
        PERIndex = 0;
        Arrays.fill(LTPIndex, 0);
        Arrays.fill(NLSFIndices, 0);
        NLSFInterpCoef_Q2 = 0;
        Arrays.fill(GainsIndices, 0);
        Seed = 0;
        LTP_scaleIndex = 0;
        RateLevelIndex = 0;
        QuantOffsetType = 0;
        sigtype = 0;
        Arrays.fill(pitchL, 0);
        LBRR_usage = 0;
    }
}

/**
 * Class for Packet Loss Concealment.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
class SKP_Silk_PLC_struct
{
    int       pitchL_Q8;                      /* Pitch lag to use for voiced concealment                  */
    final short[]   LTPCoef_Q14 = new short[ Define.LTP_ORDER ];       /* LTP coeficients to use for voiced concealment            */
    final short[]   prevLPC_Q12 = new short[ Define.MAX_LPC_ORDER ];
    int         last_frame_lost;                /* Was previous frame lost                                  */
    int       rand_seed;                      /* Seed for unvoiced signal generation                      */
    short     randScale_Q14;                  /* Scaling of unvoiced random signal                        */
    int       conc_energy;
    int       conc_energy_shift;
    short     prevLTP_scale_Q14;
    final int[]     prevGain_Q16 = new int[ Define.NB_SUBFR ];
    int       fs_kHz;

    public void zero()
    {
        pitchL_Q8 = 0;
        Arrays.fill(LTPCoef_Q14, (short)0);
        Arrays.fill(prevLPC_Q12, (short)0);
        last_frame_lost = 0;
        rand_seed = 0;
        randScale_Q14 = 0;
        conc_energy = 0;
        conc_energy_shift = 0;
        prevLTP_scale_Q14 = 0;
        Arrays.fill(prevGain_Q16, 0);
        fs_kHz = 0;
    }
}

/**
 * Class for CNG.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
class SKP_Silk_CNG_struct
{
    final int[]   CNG_exc_buf_Q10 = new int[ Define.MAX_FRAME_LENGTH ];
    final int[]   CNG_smth_NLSF_Q15 = new int[ Define.MAX_LPC_ORDER ];
    final int[]   CNG_synth_state = new int[ Define.MAX_LPC_ORDER ];
    int     CNG_smth_Gain_Q16;
    int     rand_seed;
    int     fs_kHz;

    public void zero()
    {
        Arrays.fill(CNG_exc_buf_Q10, 0);
        Arrays.fill(CNG_smth_NLSF_Q15, 0);
        Arrays.fill(CNG_synth_state, 0);
        CNG_smth_Gain_Q16 = 0;
        rand_seed = 0;
        fs_kHz = 0;
    }
}

/**
 * Deocder state
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
class SKP_Silk_decoder_state
{
    final SKP_Silk_range_coder_state  sRC = new SKP_Silk_range_coder_state();                            /* Range coder state */
    int       prev_inv_gain_Q16;
    final int[]     sLTP_Q16 = new int[ 2 * Define.MAX_FRAME_LENGTH ];
    final int[]     sLPC_Q14 = new int[ Define.MAX_FRAME_LENGTH / Define.NB_SUBFR + Define.MAX_LPC_ORDER ];
    final int[]     exc_Q10 = new int [ Define.MAX_FRAME_LENGTH ];
    final int[]     res_Q10 = new int [ Define.MAX_FRAME_LENGTH ];
    final short[]   outBuf = new short[ 2 * Define.MAX_FRAME_LENGTH ];             /* Buffer for output signal                                             */
    int       sLTP_buf_idx;                               /* LTP_buf_index                                                        */
    int       lagPrev;                                    /* Previous Lag                                                         */
    int       LastGainIndex;                              /* Previous gain index                                                  */
    int       typeOffsetPrev;                             /* Previous signal type and quantization offset                         */
    final int[]     HPState = new int[ Define.DEC_HP_ORDER ];   /* HP filter state                                                      */
    short[]   HP_A;                                       /* HP filter AR coefficients                                            */
    short[]   HP_B;                                       /* HP filter MA coefficients                                            */
    int       fs_kHz;                                     /* Sampling frequency in kHz                                            */
    int       prev_API_sampleRate;                        /* Previous API sample frequency (Hz)                                   */
    int       frame_length;                               /* Frame length (samples)                                               */
    int       subfr_length;                               /* Subframe length (samples)                                            */
    int       LPC_order;                                  /* LPC order                                                            */
    final int[]     prevNLSF_Q15 = new int[ Define.MAX_LPC_ORDER ];              /* Used to interpolate LSFs                                             */
    int       first_frame_after_reset;                    /* Flag for deactivating NLSF interp. and fluc. reduction after resets  */

    /* For buffering payload in case of more frames per packet */
    int         nBytesLeft;
    int         nFramesDecoded;
    int         nFramesInPacket;
    int         moreInternalDecoderFrames;
    int         FrameTermination;

    final SKP_Silk_resampler_state_struct  resampler_state = new SKP_Silk_resampler_state_struct();

    final SKP_Silk_NLSF_CB_struct[]  psNLSF_CB= new SKP_Silk_NLSF_CB_struct[ 2 ];      /* Pointers to voiced/unvoiced NLSF codebooks */

    /* Parameters used to investigate if inband FEC is used */
    int         vadFlag;
    int         no_FEC_counter;                             /* Counts number of frames wo inband FEC                                */
    int         inband_FEC_offset;                          /* 0: no FEC, 1: FEC with 1 packet offset, 2: FEC w 2 packets offset    */

    final SKP_Silk_CNG_struct sCNG = new SKP_Silk_CNG_struct();

    /* Stuff used for PLC */
    final SKP_Silk_PLC_struct sPLC = new SKP_Silk_PLC_struct();
    int         lossCnt;
    int         prev_sigtype;                               /* Previous sigtype                                                     */

    public void zero()
    {
        sRC.zero();
        prev_inv_gain_Q16 = 0;
        Arrays.fill(sLTP_Q16, 0);
        Arrays.fill(sLPC_Q14, 0);
        Arrays.fill(exc_Q10, 0);
        Arrays.fill(res_Q10, 0);
        Arrays.fill(outBuf, (short)0);
        sLTP_buf_idx = 0;
        lagPrev = 0;
        LastGainIndex = 0;
        typeOffsetPrev = 0;
        Arrays.fill(HPState, 0);
        HP_A = null;
        HP_B = null;
        fs_kHz = 0;
        prev_API_sampleRate = 0;
        frame_length = 0;
        subfr_length = 0;
        LPC_order = 0;
        Arrays.fill(prevNLSF_Q15, 0);
        first_frame_after_reset = 0;
        nBytesLeft = 0;
        nFramesDecoded = 0;
        nFramesInPacket = 0;
        moreInternalDecoderFrames = 0;
        FrameTermination = 0;
        resampler_state.zero();
        psNLSF_CB[0] = null;
        psNLSF_CB[1] = null;
        vadFlag = 0;
        no_FEC_counter = 0;
        inband_FEC_offset = 0;
        sCNG.zero();
        sPLC.zero();
        lossCnt = 0;
        prev_sigtype = 0;
    }
}

 /**
  * Decoder control.
  *
  * @author Jing Dai
  * @author Dingxin Xu
  */
class SKP_Silk_decoder_control
{
    /* prediction and coding parameters */
    final int[]             pitchL = new int[ Define.NB_SUBFR ];
    final int[]             Gains_Q16 = new int[ Define.NB_SUBFR ];
    int               Seed;
    /* holds interpolated and final coefficients, 4-byte aligned */
    final short[][]        PredCoef_Q12 = new short[2][Define.MAX_LPC_ORDER];

    final short[]           LTPCoef_Q14 = new short[ Define.LTP_ORDER * Define.NB_SUBFR ];
    int               LTP_scale_Q14;

    /* quantization indices */
    int             PERIndex;
    int             RateLevelIndex;
    int             QuantOffsetType;
    int             sigtype;
    int             NLSFInterpCoef_Q2;

    public void zero()
    {
        Arrays.fill(pitchL, 0);
        Arrays.fill(Gains_Q16, 0);
        Seed = 0;
        for (short[] row : PredCoef_Q12)
            Arrays.fill(row, (short)0);
        Arrays.fill(LTPCoef_Q14, (short)0);
        LTP_scale_Q14 = 0;
        PERIndex = 0;
        RateLevelIndex = 0;
        QuantOffsetType = 0;
        sigtype = 0;
        NLSFInterpCoef_Q2 = 0;
    }
}
