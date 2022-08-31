// Copyright (c) Microsoft Corporation. All rights reserved.
package org.jitsi.service.resources;

import java.awt.image.*;
import java.util.concurrent.*;

import javax.swing.*;

/**
 * This represents a javax.swing.ImageIcon which may not be available yet
 */
public interface ImageIconFuture extends Future<ImageIcon>
{
    /**
     * Causes this image to resolved, so this may take some time.
     *
     * @return the buffered image outstanding, or null
     */
    ImageIcon resolve();

    /**
     * @param resolution Called once the icon has been retrieved
     */
    void onResolve(Resolution<ImageIcon> resolution);

    /**
     * @param resolution Called on the EDT thread once the icon has been
     * retrieved
     */
    void onUiResolve(Resolution<ImageIcon> resolution);

    /**
     * @return BufferedImageFuture representing this image
     */
    BufferedImageFuture getImage();

    /**
     * @return a new ImageIconFuture representing this image, or the supplied
     * alternative if this image is null.
     */
    ImageIconFuture withAlternative(ImageIconFuture alternative);

    // Helper methods
    JLabel addToLabel(JLabel label);
    JButton addToButton(JButton button);
    JToggleButton addToButton(JToggleButton button);
    JMenuItem addToMenuItem(JMenuItem item);
    void setImageObserver(ImageObserver observer);

    void addToButton(AbstractButton button);
    void addToButtonAsSelected(AbstractButton button);
    void addToButtonAsRollover(AbstractButton button);
    void addToButtonAsPressed(AbstractButton button);
    void addToButtonAsRolloverSelected(AbstractButton button);
    void addToButtonAsDisabled(AbstractButton button);
    void addToButtonAsDisabledSelected(AbstractButton button);
}
