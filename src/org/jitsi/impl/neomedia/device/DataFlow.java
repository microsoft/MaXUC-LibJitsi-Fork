/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.device;

/**
 * Enumerates the different types of media data flow of
 * <tt>Device</tt>s contributed by an <tt>AudioSystem</tt>.
 *
 * @author Lyubomir Marinov
 */
public enum DataFlow
{
    /**
     * Audio Capture Device.
     */
    CAPTURE,

    /**
     * Audio device for notification sounds.
     */
    NOTIFY,

    /**
     * Audio playback device.
     */
    PLAYBACK,

    /**
     * Video device.
     */
    VIDEO
}