/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.service.neomedia.device;

import java.awt.*;

/**
 * Represents a physical screen display.
 *
 * @author Sebastien Vincent
 */
public interface ScreenDevice
{
    /**
     * Gets this screen's index.
     *
     * @return this screen's index
     */
    int getIndex();

    /**
     * Gets the current resolution of this screen.
     *
     * @return the current resolution of this screen
     */
    Dimension getSize();
}
