/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.codec.audio.silk;

import java.util.*;

/**
 * Classes for IIR/FIR resamplers.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
public class ResamplerStructs
{
    static final int SKP_Silk_RESAMPLER_MAX_FIR_ORDER =                16;
    static final int SKP_Silk_RESAMPLER_MAX_IIR_ORDER =                6;
}

 class SKP_Silk_resampler_state_struct
 {
    final int[]       sIIR = new int[ ResamplerStructs.SKP_Silk_RESAMPLER_MAX_IIR_ORDER ];        /* this must be the first element of this struct */
    final int[]       sFIR = new int[ ResamplerStructs.SKP_Silk_RESAMPLER_MAX_FIR_ORDER ];
    final int[]       sDown2 = new int[ 2 ];

    String resampler_function;
    ResamplerFP resamplerCB;
    void resampler_function( Object state, short[] out, int out_offset, short[] in, int in_offset, int len, BaseMem mem )
    {
        resamplerCB.resampler_function(state, out, out_offset, in, in_offset, len, mem);
    }

    String up2_function;
    Up2FP up2CB;
    void up2_function(  int[] state, short[] out, int out_offset, short[] in, int in_offset, int len )
    {
        up2CB.up2_function(state, out, out_offset, in, in_offset, len);
    }

    int       batchSize;
    int       invRatio_Q16;
    int       FIR_Fracs;
    int       input2x;
    short[]   Coefs;

    final int[]     sDownPre = new int[ 2 ];
    final int[]     sUpPost = new int[ 2 ];

    String down_pre_function;
    DownPreFP  downPreCB;
    void down_pre_function ( int[] state, short[] out, int out_offset, short[] in, int in_offset, int len )
    {
        downPreCB.down_pre_function(state, out, out_offset, in, in_offset, len);
    }

    String up_post_function;
    UpPostFP  upPostCB;
    void up_post_function ( int[] state, short[] out, int out_offset, short[] in, int in_offset, int len )
    {
        upPostCB.up_post_function(state, out, out_offset, in, in_offset, len);
    }
    int       batchSizePrePost;
    int       ratio_Q16;
    int       nPreDownsamplers;
    int       nPostUpsamplers;
    int magic_number;

    /**
     * set all fields of the instance to zero.
     */
    public void zero()
    {
        Arrays.fill(sIIR, 0);
        Arrays.fill(sFIR, 0);
        Arrays.fill(sDown2, 0);
        resampler_function = null;
        resamplerCB = null;
        up2_function = null;
        up2CB = null;
        batchSize = 0;
        invRatio_Q16 = 0;
        FIR_Fracs = 0;
        input2x = 0;
        Coefs = null;
        Arrays.fill(sDownPre, 0);
        Arrays.fill(sUpPost, 0);
        down_pre_function = null;
        downPreCB = null;
        up_post_function = null;
        upPostCB = null;
        batchSizePrePost = 0;
        ratio_Q16 = 0;
        nPreDownsamplers = 0;
        nPostUpsamplers = 0;
        magic_number = 0;
    }
}
 /*************************************************************************************/
 interface ResamplerFP
 {
     void resampler_function( Object state, short[] out, int out_offset, short[] in, int in_offset, int len, BaseMem mem );
 }
 interface Up2FP
 {
     void up2_function(  int[] state, short[] out, int out_offset, short[] in, int in_offset, int len );
 }
 interface DownPreFP
 {
     void down_pre_function ( int[] state, short[] out, int out_offset, short[] in, int in_offset, int len );
 }
 interface UpPostFP
 {
     void up_post_function ( int[] state, short[] out, int out_offset, short[] in, int in_offset, int len );
 }
 /*************************************************************************************/
