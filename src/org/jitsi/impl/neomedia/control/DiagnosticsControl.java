/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.control;

import javax.media.*;

/**
 * Defines an FMJ <tt>Control</tt> which allows the diagnosis of the functional
 * health of a procedure/process.
 *
 * @author Lyubomir Marinov
 */
public interface DiagnosticsControl
    extends Control
{
    /**
     * The constant which expresses a non-existent time in milliseconds for the
     * purposes of {@link #getMalfunctioningSince()}. Explicitly chosen to be
     * <tt>0</tt> rather than <tt>-1</tt> in the name of efficiency.
     */
    long NEVER = 0;

    /**
     * Gets the time in milliseconds at which the associated procedure/process
     * has started malfunctioning.
     *
     * @return the time in milliseconds at which the associated
     * procedure/process has started malfunctioning or <tt>NEVER</tt> if the
     * associated procedure/process is functioning normally
     */
    long getMalfunctioningSince();

    /**
     * Indicates whether the device is currently malfunctioning, and if so, why.
     *
     * @return the current malfunction state
     */
    MalfunctionState getMalfunctionState();

    /**
     * Returns a human-readable <tt>String</tt> representation of the associated
     * procedure/process.
     *
     * @return a human-readable <tt>String</tt> representation of the associated
     * procedure/process
     */
    String toString();

    enum MalfunctionState
    {
        FUNCTIONING_CORRECTLY,
        AEC_BUFFER_EMPTY,
        AEC_BUFFER_READ_FAILED,
        REMAINDER_BUFFER_FULL
    }
}
