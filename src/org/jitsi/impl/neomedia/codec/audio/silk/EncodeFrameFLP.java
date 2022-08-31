/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.codec.audio.silk;

import java.util.*;

/**
 * Encode frame.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
@SuppressWarnings("unused")
public class EncodeFrameFLP
{
    /**
     * Encode frame.
     * @param psEnc Encoder state FLP
     * @param pCode payload
     * @param pCode_offset offset of valid data.
     * @param pnBytesOut Number of payload bytes; input: max length; output: used.
     * @param pIn Input speech frame
     * @return
     */
    static int SKP_Silk_encode_frame_FLP(
        SKP_Silk_encoder_state_FLP      psEnc,             /* I/O  Encoder state FLP                       */
              byte[]                    pCode,
              int                       pCode_offset,
              short[]                   pnBytesOut,        /* I/O  Number of payload bytes;                */
                                                           /*      input: max length; output: used         */
              short[]                   pIn,               /* I    Input speech frame                      */
              final EncodeMem           mem
    )
    {
        SKP_Silk_encoder_control_FLP sEncCtrl = mem.SKP_Silk_encode_frame_FLP__sEncCtrl;
        sEncCtrl.zero();
        int k;
        int[] nBytes = mem.zero(mem.SKP_Silk_encode_frame_FLP__nBytes);
        int ret = 0;
        float[]   x_frame, res_pitch_frame;
        int x_frame_offset, res_pitch_frame_offset;
        short[] pIn_HP = mem.zero(mem.SKP_Silk_encode_frame_FLP__pIn_HP);
        short[] pIn_HP_LP = mem.zero(mem.SKP_Silk_encode_frame_FLP__pIn_HP_LP);
        float[] xfw = mem.zero(mem.SKP_Silk_encode_frame_FLP__xfw);
        float[] res_pitch = mem.zero(mem.SKP_Silk_encode_frame_FLP__res_pitch);
        int     LBRR_idx, frame_terminator;

        /* Low bitrate redundancy parameters */
        byte[]  LBRRpayload = mem.zero(mem.SKP_Silk_encode_frame_FLP__LBRRpayload);
        short[] nBytesLBRR = mem.zero(mem.SKP_Silk_encode_frame_FLP__nBytesLBRR);

        int[] FrameTermination_CDF;

        sEncCtrl.sCmn.Seed = psEnc.sCmn.frameCounter++ & 3;
        /**************************************************************/
        /* Setup Input Pointers, and insert frame in input buffer    */
        /*************************************************************/
        /* pointers aligned with start of frame to encode */
        x_frame                = psEnc.x_buf;
        x_frame_offset         = psEnc.x_buf_offset + psEnc.sCmn.frame_length; // start of frame to encode
        res_pitch_frame        = res_pitch;
        res_pitch_frame_offset = psEnc.sCmn.frame_length; // start of pitch LPC residual frame

        /****************************/
        /* Voice Activity Detection */
        /****************************/
        WrappersFLP.SKP_Silk_VAD_FLP( psEnc, sEncCtrl, pIn, mem );

        /*******************************************/
        /* High-pass filtering of the input signal */
        /*******************************************/
        /* Variable high-pass filter */
        HPVariableCutoffFLP.SKP_Silk_HP_variable_cutoff_FLP( psEnc, sEncCtrl, pIn_HP, pIn, mem );

        /* Ensure smooth bandwidth transitions */
        LPVariableCutoff.SKP_Silk_LP_variable_cutoff( psEnc.sCmn.sLP, pIn_HP_LP, pIn_HP, psEnc.sCmn.frame_length, mem );

        /*******************************************/
        /* Copy new frame to front of input buffer */
        /*******************************************/
        SigProcFLP.SKP_short2float_array( x_frame, x_frame_offset +psEnc.sCmn.la_shape,
                pIn_HP_LP, 0, psEnc.sCmn.frame_length );

        /* Add tiny signal to avoid high CPU load from denormalized floating point numbers */
        for( k = 0; k < 8; k++ ) {
            x_frame[ x_frame_offset + psEnc.sCmn.la_shape + k * ( psEnc.sCmn.frame_length >> 3 ) ] += ( 1 - ( k & 2 ) ) * 1e-6f;
        }

        /*****************************************/
        /* Find pitch lags, initial LPC analysis */
        /*****************************************/
        FindPitchLagsFLP.SKP_Silk_find_pitch_lags_FLP( psEnc, sEncCtrl, res_pitch, x_frame, x_frame_offset, mem );

        /************************/
        /* Noise shape analysis */
        /************************/
        NoiseShapeAnalysisFLP.SKP_Silk_noise_shape_analysis_FLP( psEnc, sEncCtrl,
                res_pitch_frame, res_pitch_frame_offset, x_frame, x_frame_offset, mem );

        /*****************************************/
        /* Prefiltering for noise shaper         */
        /*****************************************/
        PrefilterFLP.SKP_Silk_prefilter_FLP( psEnc, sEncCtrl, xfw, x_frame, x_frame_offset, mem );

        /***************************************************/
        /* Find linear prediction coefficients (LPC + LTP) */
        /***************************************************/
        FindPredCoefsFLP.SKP_Silk_find_pred_coefs_FLP( psEnc, sEncCtrl, res_pitch, mem );

        /****************************************/
        /* Process gains                        */
        /****************************************/
        ProcessGainsFLP.SKP_Silk_process_gains_FLP( psEnc, sEncCtrl, mem );

        /****************************************/
        /* Low Bitrate Redundant Encoding       */
        /****************************************/
        nBytesLBRR[0] = Define.MAX_ARITHM_BYTES;
        SKP_Silk_LBRR_encode_FLP( psEnc, sEncCtrl, LBRRpayload, nBytesLBRR, xfw, mem );

        /*****************************************/
        /* Noise shaping quantization            */
        /*****************************************/
        WrappersFLP.SKP_Silk_NSQ_wrapper_FLP( psEnc, sEncCtrl, xfw, psEnc.sCmn.q, 0, mem );

        /**************************************************/
        /* Convert speech activity into VAD and DTX flags */
        /**************************************************/
        if( psEnc.speech_activity < DefineFLP.SPEECH_ACTIVITY_DTX_THRES ) {
            psEnc.sCmn.vadFlag = Define.NO_VOICE_ACTIVITY;
            psEnc.sCmn.noSpeechCounter++;
            if( psEnc.sCmn.noSpeechCounter > Define.NO_SPEECH_FRAMES_BEFORE_DTX ) {
                psEnc.sCmn.inDTX = 1;
            }
            if( psEnc.sCmn.noSpeechCounter > Define.MAX_CONSECUTIVE_DTX ) {
                psEnc.sCmn.noSpeechCounter = 0;
                psEnc.sCmn.inDTX           = 0;
            }
        } else {
            psEnc.sCmn.noSpeechCounter = 0;
            psEnc.sCmn.inDTX           = 0;
            psEnc.sCmn.vadFlag         = Define.VOICE_ACTIVITY;
        }

        /****************************************/
        /* Initialize arithmetic coder          */
        /****************************************/
        if( psEnc.sCmn.nFramesInPayloadBuf == 0 )
        {
            RangeCoder.SKP_Silk_range_enc_init( psEnc.sCmn.sRC );
            psEnc.sCmn.nBytesInPayloadBuf = 0;
        }

        /****************************************/
        /* Encode Parameters                    */
        /****************************************/
        EncodeParameters.SKP_Silk_encode_parameters( psEnc.sCmn, sEncCtrl.sCmn, psEnc.sCmn.sRC, psEnc.sCmn.q, mem );
        FrameTermination_CDF = TablesOther.SKP_Silk_FrameTermination_CDF;

        /****************************************/
        /* Update Buffers and State             */
        /****************************************/
        /* Update input buffer */
        System.arraycopy(psEnc.x_buf, psEnc.x_buf_offset + psEnc.sCmn.frame_length,
                psEnc.x_buf, psEnc.x_buf_offset, psEnc.sCmn.frame_length + psEnc.sCmn.la_shape);

        /* Parameters needed for next frame */
        psEnc.sCmn.prev_sigtype = sEncCtrl.sCmn.sigtype;
        psEnc.sCmn.prevLag      = sEncCtrl.sCmn.pitchL[ Define.NB_SUBFR - 1];
        psEnc.sCmn.first_frame_after_reset = 0;

        if( psEnc.sCmn.sRC.error != 0 ) {
            /* Encoder returned error: Clear payload buffer */
            psEnc.sCmn.nFramesInPayloadBuf = 0;
        } else {
            psEnc.sCmn.nFramesInPayloadBuf++;
        }

        /****************************************/
        /* Finalize payload and copy to output  */
        /****************************************/
        if( psEnc.sCmn.nFramesInPayloadBuf * Define.FRAME_LENGTH_MS >= psEnc.sCmn.PacketSize_ms ) {

            LBRR_idx = ( psEnc.sCmn.oldest_LBRR_idx + 1 ) & Define.LBRR_IDX_MASK;

            /* Check if FEC information should be added */
            frame_terminator = Define.SKP_SILK_LAST_FRAME;
            if( psEnc.sCmn.LBRR_buffer[ LBRR_idx ].usage == Define.SKP_SILK_ADD_LBRR_TO_PLUS1 ) {
                frame_terminator = Define.SKP_SILK_LBRR_VER1;
            }
            if( psEnc.sCmn.LBRR_buffer[ psEnc.sCmn.oldest_LBRR_idx ].usage == Define.SKP_SILK_ADD_LBRR_TO_PLUS2 ) {
                frame_terminator = Define.SKP_SILK_LBRR_VER2;
                LBRR_idx = psEnc.sCmn.oldest_LBRR_idx;
            }

            /* Add the frame termination info to stream */
            RangeCoder.SKP_Silk_range_encoder( psEnc.sCmn.sRC, frame_terminator, FrameTermination_CDF,0 );

            /* Payload length so far */
            RangeCoder.SKP_Silk_range_coder_get_length( psEnc.sCmn.sRC, nBytes );

            /* Check that there is enough space in external output buffer, and move data */
            if( pnBytesOut[0] >= nBytes[0] ) {
                RangeCoder.SKP_Silk_range_enc_wrap_up( psEnc.sCmn.sRC, mem );
                System.arraycopy(psEnc.sCmn.sRC.buffer, 0, pCode, pCode_offset, nBytes[0]);

                if( frame_terminator > Define.SKP_SILK_MORE_FRAMES &&
                        pnBytesOut[0] >= nBytes[0] + psEnc.sCmn.LBRR_buffer[ LBRR_idx ].nBytes ) {
                    /* Get old packet and add to payload. */
                    System.arraycopy(psEnc.sCmn.LBRR_buffer[ LBRR_idx ].payload, 0,
                            pCode, pCode_offset+nBytes[0], psEnc.sCmn.LBRR_buffer[ LBRR_idx ].nBytes);
                    nBytes[0] += psEnc.sCmn.LBRR_buffer[ LBRR_idx ].nBytes;
                }
                pnBytesOut[0] = (short) nBytes[0];

                /* Update FEC buffer */
                System.arraycopy(LBRRpayload, 0,
                        psEnc.sCmn.LBRR_buffer[ psEnc.sCmn.oldest_LBRR_idx ].payload, 0, nBytesLBRR[0]);
                psEnc.sCmn.LBRR_buffer[ psEnc.sCmn.oldest_LBRR_idx ].nBytes = nBytesLBRR[0];
                /* The below line describes how FEC should be used */
                psEnc.sCmn.LBRR_buffer[ psEnc.sCmn.oldest_LBRR_idx ].usage = sEncCtrl.sCmn.LBRR_usage;
                psEnc.sCmn.oldest_LBRR_idx = ( ( psEnc.sCmn.oldest_LBRR_idx + 1 ) & Define.LBRR_IDX_MASK );

                /* Reset the number of frames in payload buffer */
                psEnc.sCmn.nFramesInPayloadBuf = 0;
            } else {
                /* Not enough space: Payload will be discarded */
                pnBytesOut[0] = 0;
                nBytes[0]      = 0;
                psEnc.sCmn.nFramesInPayloadBuf = 0;
                ret = Errors.SKP_SILK_ENC_PAYLOAD_BUF_TOO_SHORT;
            }
        } else {
            /* No payload for you this time */
            pnBytesOut[0] = 0;

            /* Encode that more frames follows */
            frame_terminator = Define.SKP_SILK_MORE_FRAMES;
            RangeCoder.SKP_Silk_range_encoder( psEnc.sCmn.sRC, frame_terminator, FrameTermination_CDF, 0);

            /* Payload length so far */
            RangeCoder.SKP_Silk_range_coder_get_length( psEnc.sCmn.sRC, nBytes );
        }

        /* Check for arithmetic coder errors */
        if( psEnc.sCmn.sRC.error != 0 ) {
            ret = Errors.SKP_SILK_ENC_INTERNAL_ERROR;
        }

        /* simulate number of ms buffered in channel because of exceeding TargetRate */
        psEnc.BufferedInChannel_ms   += ( 8.0f * 1000.0f * ( nBytes[0] - psEnc.sCmn.nBytesInPayloadBuf ) ) / psEnc.sCmn.TargetRate_bps;
        psEnc.BufferedInChannel_ms   -= Define.FRAME_LENGTH_MS;
        psEnc.BufferedInChannel_ms    = SigProcFLP.SKP_LIMIT_float( psEnc.BufferedInChannel_ms, 0.0f, 100.0f );
        psEnc.sCmn.nBytesInPayloadBuf = nBytes[0];

        if( psEnc.speech_activity > DefineFLP.WB_DETECT_ACTIVE_SPEECH_LEVEL_THRES ) {
            psEnc.sCmn.sSWBdetect.ActiveSpeech_ms = SigProcFIX.SKP_ADD_POS_SAT32( psEnc.sCmn.sSWBdetect.ActiveSpeech_ms, Define.FRAME_LENGTH_MS );
        }

        return( ret );
    }

    /**
     * Low Bitrate Redundancy (LBRR) encoding. Reuse all parameters but encode with lower bitrate.
     * @param psEnc Encoder state FLP.
     * @param psEncCtrl Encoder control FLP.
     * @param pCode Payload.
     * @param pnBytesOut Payload bytes; in: max; out: used.
     * @param xfw Input signal.
     */
    static void SKP_Silk_LBRR_encode_FLP(
        SKP_Silk_encoder_state_FLP      psEnc,             /* I/O  Encoder state FLP                       */
        SKP_Silk_encoder_control_FLP    psEncCtrl,         /* I/O  Encoder control FLP                     */
              byte                      []pCode,           /* O    Payload                                 */
              short                     []pnBytesOut,      /* I/O  Payload bytes; in: max; out: used       */
              float                     xfw[],             /* I    Input signal                            */
              final EncodeMem           mem
    )
    {
        int[]   Gains_Q16 = mem.zero(mem.SKP_Silk_LBRR_encode_FLP__Gains_Q16);
        int     k;
        int TempGainsIndices[] = mem.zero(mem.SKP_Silk_LBRR_encode_FLP__TempGainsIndices);
        int     frame_terminator;
        int     nBytes[] = mem.zero(mem.SKP_Silk_LBRR_encode_FLP__nBytes);
        int     nFramesInPayloadBuf;
        float   TempGains[] = mem.zero(mem.SKP_Silk_LBRR_encode_FLP__TempGains);
        int     typeOffset, LTP_scaleIndex, Rate_only_parameters = 0;
        /* Control use of inband LBRR */
        ControlCodecFLP.SKP_Silk_LBRR_ctrl_FLP( psEnc, psEncCtrl.sCmn );

        if( psEnc.sCmn.LBRR_enabled != 0 ) {
            /* Save original gains */
            System.arraycopy(psEncCtrl.sCmn.GainsIndices, 0, TempGainsIndices, 0, Define.NB_SUBFR);
            System.arraycopy(psEncCtrl.Gains, 0, TempGains, 0, Define.NB_SUBFR);

            typeOffset     = psEnc.sCmn.typeOffsetPrev; // Temp save as cannot be overwritten
            LTP_scaleIndex = psEncCtrl.sCmn.LTP_scaleIndex;

            /* Set max rate where quant signal is encoded */
            if( psEnc.sCmn.fs_kHz == 8 ) {
                Rate_only_parameters = 13500;
            } else if( psEnc.sCmn.fs_kHz == 12 ) {
                Rate_only_parameters = 15500;
            } else if( psEnc.sCmn.fs_kHz == 16 ) {
                Rate_only_parameters = 17500;
            } else if( psEnc.sCmn.fs_kHz == 24 ) {
                Rate_only_parameters = 19500;
            } else {
                assert( false );
            }

            if( psEnc.sCmn.Complexity > 0 && psEnc.sCmn.TargetRate_bps > Rate_only_parameters ) {
                if( psEnc.sCmn.nFramesInPayloadBuf == 0 ) {
                    /* First frame in packet copy everything */
                    psEnc.sNSQ_LBRR.copyFrom(psEnc.sNSQ);

                    psEnc.sCmn.LBRRprevLastGainIndex = psEnc.sShape.LastGainIndex;
                    /* Increase Gains to get target LBRR rate */
                    psEncCtrl.sCmn.GainsIndices[ 0 ] += psEnc.sCmn.LBRR_GainIncreases;
                    psEncCtrl.sCmn.GainsIndices[ 0 ]  = SigProcFIX.SKP_LIMIT( psEncCtrl.sCmn.GainsIndices[ 0 ], 0, Define.N_LEVELS_QGAIN - 1 );
                }
                /* Decode to get Gains in sync with decoder */
                mem.SKP_Silk_LBRR_encode_FLP__tmp_int[0] = psEnc.sCmn.LBRRprevLastGainIndex;
                GainQuant.SKP_Silk_gains_dequant( Gains_Q16, psEncCtrl.sCmn.GainsIndices,
                    mem.SKP_Silk_LBRR_encode_FLP__tmp_int, psEnc.sCmn.nFramesInPayloadBuf );
                psEnc.sCmn.LBRRprevLastGainIndex = mem.SKP_Silk_LBRR_encode_FLP__tmp_int[0];

                /* Overwrite unquantized gains with quantized gains and convert back to Q0 from Q16 */
                for( k = 0; k < Define.NB_SUBFR; k++ ) {
                    psEncCtrl.Gains[ k ] = Gains_Q16[ k ] / 65536.0f;
                }

                /*****************************************/
                /* Noise shaping quantization            */
                /*****************************************/
                WrappersFLP.SKP_Silk_NSQ_wrapper_FLP( psEnc, psEncCtrl, xfw, psEnc.sCmn.q_LBRR, 1, mem );
            } else {
                Arrays.fill(psEnc.sCmn.q_LBRR, (byte)0);
                psEncCtrl.sCmn.LTP_scaleIndex = 0;
            }
            /****************************************/
            /* Initialize arithmetic coder          */
            /****************************************/
            if( psEnc.sCmn.nFramesInPayloadBuf == 0 ) {
                RangeCoder.SKP_Silk_range_enc_init( psEnc.sCmn.sRC_LBRR );
                psEnc.sCmn.nBytesInPayloadBuf = 0;
            }

            /****************************************/
            /* Encode Parameters                    */
            /****************************************/
            EncodeParameters.SKP_Silk_encode_parameters( psEnc.sCmn, psEncCtrl.sCmn, psEnc.sCmn.sRC_LBRR, psEnc.sCmn.q_LBRR, mem );
            /****************************************/
            /* Encode Parameters                    */
            /****************************************/
            if( psEnc.sCmn.sRC_LBRR.error != 0) {
                /* Encoder returned error: Clear payload buffer */
                nFramesInPayloadBuf = 0;
            } else {
                nFramesInPayloadBuf = psEnc.sCmn.nFramesInPayloadBuf + 1;
            }

            /****************************************/
            /* Finalize payload and copy to output  */
            /****************************************/
            if( Macros.SKP_SMULBB( nFramesInPayloadBuf, Define.FRAME_LENGTH_MS ) >= psEnc.sCmn.PacketSize_ms ) {

                /* Check if FEC information should be added */
                frame_terminator = Define.SKP_SILK_LAST_FRAME;

                /* Add the frame termination info to stream */
                RangeCoder.SKP_Silk_range_encoder( psEnc.sCmn.sRC_LBRR, frame_terminator, TablesOther.SKP_Silk_FrameTermination_CDF, 0 );

                /* Payload length so far */
                RangeCoder.SKP_Silk_range_coder_get_length( psEnc.sCmn.sRC_LBRR, nBytes );

                /* Check that there is enough space in external output buffer and move data */
                if( pnBytesOut[0] >= nBytes[0] ) {
                    RangeCoder.SKP_Silk_range_enc_wrap_up( psEnc.sCmn.sRC_LBRR, mem );
                    System.arraycopy(psEnc.sCmn.sRC_LBRR.buffer, 0, pCode, 0, nBytes[0]);

                    pnBytesOut[0] = (short) nBytes[0];
                } else {
                    /* Not enough space: Payload will be discarded */
                    pnBytesOut[0] = 0;
                    assert( false );
                }
            } else {
                /* No payload for you this time */
                pnBytesOut[0] = 0;

                /* Encode that more frames follows */
                frame_terminator = Define.SKP_SILK_MORE_FRAMES;
                RangeCoder.SKP_Silk_range_encoder( psEnc.sCmn.sRC_LBRR, frame_terminator, TablesOther.SKP_Silk_FrameTermination_CDF, 0 );
            }

            /* Restore original Gains */
            System.arraycopy(TempGainsIndices, 0, psEncCtrl.sCmn.GainsIndices, 0, Define.NB_SUBFR);
            System.arraycopy(TempGains, 0, psEncCtrl.Gains, 0, Define.NB_SUBFR);

            /* Restore LTP scale index and typeoffset */
            psEncCtrl.sCmn.LTP_scaleIndex = LTP_scaleIndex;
            psEnc.sCmn.typeOffsetPrev     = typeOffset;
        }
    }
}
