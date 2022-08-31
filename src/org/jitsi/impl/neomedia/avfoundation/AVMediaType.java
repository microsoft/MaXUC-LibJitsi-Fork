/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.avfoundation;

/**
 * Enumerates the AVFoundation media types.
 *
 * @author Lyubomir Marinov
 */
public enum AVMediaType
{
    /**
     * The AVFoundation type of multiplexed media which may contain audio, video, and
     * other data in a single stream.
     */
    Muxed,

    /**
     * The AVFoundation type of media which contains only audio frames.
     */
    Sound,

    /**
     * The AVFoundation type of media which contains only video frames.
     */
    Video
}
