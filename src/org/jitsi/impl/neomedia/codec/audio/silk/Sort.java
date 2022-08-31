/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.codec.audio.silk;

/**
 * Insertion sort (fast for already almost sorted arrays):
 *    Best case:  O(n)   for an already sorted array
 *    Worst case: O(n^2) for an inversely sorted array
 * Shell short:    http://en.wikipedia.org/wiki/Shell_sort
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
public class Sort
{
    /**
     *
     * @param a Unsorted / Sorted vector
     * @param a_offset offset of valid data.
     * @param L Vector length
     */
    static void SKP_Silk_insertion_sort_increasing_all_values(
            int             []a,             /* I/O: Unsorted / Sorted vector                */
            final int         L              /* I:   Vector length                           */
        )
    {
        int    value;
        int    i, j;

        /* Safety checks */
        Typedef.SKP_assert( L >  0 );

        /* Sort vector elements by value, increasing order */
        for( i = 1; i < L; i++ ) {
            value = a[ i ];
            for( j = i - 1; ( j >= 0 ) && ( value < a[ j ] ); j-- ) {
                a[ j + 1 ] = a[ j ]; /* Shift value */
            }
            a[ j + 1 ] = value; /* Write value */
        }
    }
}
