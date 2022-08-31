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
public class Typedef
{
    static final int SKP_int32_MAX =  0x7FFFFFFF;             //  2^31 - 1 =  2147483647
    static final short SKP_int16_MAX =  0x7FFF;               //  2^15 - 1 =  32767
    static final short SKP_uint8_MAX =  0xFF;        //  2^8 - 1 = 255

    /* assertions */
    static void SKP_assert(boolean COND)
    {
        assert(COND);
    }
}
