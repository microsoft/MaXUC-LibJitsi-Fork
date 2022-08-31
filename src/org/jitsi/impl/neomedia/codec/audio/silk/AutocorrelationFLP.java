/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.codec.audio.silk;

/**
 * compute autocorrelation.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
public class AutocorrelationFLP
{
    /**
     * compute autocorrelation.
     * @param results result (length correlationCount)
     * @param inputData input data to correlate
     * @param inputDataSize length of input
     * @param correlationCount number of correlation taps to compute
     */
 //TODO: float or double???
    static void SKP_Silk_autocorrelation_FLP(
        float[]       results,           /* O    result (length correlationCount)            */
        float[]       inputData,         /* I    input data to correlate                     */
        int         inputDataSize,      /* I    length of input                             */
        int         correlationCount    /* I    number of correlation taps to compute       */
    )
    {
        int i;

        if ( correlationCount > inputDataSize )
        {
            correlationCount = inputDataSize;
        }

        for( i = 0; i < correlationCount; i++ )
        {
            results[ i ] =  (float)InnerProductFLP.SKP_Silk_inner_product_FLP( inputData,0, inputData, i, inputDataSize - i );
        }
    }
}
