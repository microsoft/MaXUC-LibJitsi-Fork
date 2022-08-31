/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.service.neomedia;

import java.awt.*;

/**
 * An interface that exposes the <tt>Component</tt>s used in media
 * configuration user interfaces.
 *
 * @author Boris Grozev
 */
public interface MediaConfigurationService
{
    /**
     * Returns a <tt>Component</tt> for audio configuration
     *
     * @return A <tt>Component</tt> for audio configuration
     */
    Component createAudioConfigPanel();

    /**
     * Returns a <tt>Component</tt> for video configuration
     *
     * @return A <tt>Component</tt> for video configuration
     */
    Component createVideoConfigPanel();

    /**
     * Returns the <tt>MediaService</tt> instance
     *
     * @return the <tt>MediaService</tt> instance
     */
    MediaService getMediaService();
}
