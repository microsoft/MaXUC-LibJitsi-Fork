// Copyright (c) Microsoft Corporation. All rights reserved.
package org.jitsi.service.resources;

import java.awt.*;
import java.awt.image.*;
import java.util.concurrent.*;

/**
 * This represents a java.awt.image.BufferedImage which may not be available yet
 */
public interface BufferedImageFuture extends Future<BufferedImage>
{
    /**
     * Causes this image to resolved, so this may take some time.
     *
     * @return the buffered image outstanding, or null
     */
    BufferedImage resolve();

    /**
     * @param resolution Called once the image has been retrieved
     */
    void onResolve(Resolution<BufferedImage> resolution);

    /**
     * @param resolution Called on the EDT thread once the image has been
     * retrieved
     */
    void onUiResolve(Resolution<BufferedImage> resolution);

    /**
     * @return ImageIconFuture representing this image
     */
    ImageIconFuture getImageIcon();

    // Helper methods

    /**
     * Set the image on the window once retrieved.
     */
    void addToWindow(Window window);

    ImageIconFuture getScaledEllipticalIcon(int width, int height);
    ImageIconFuture getScaledRoundedIcon(int width, int height);
    BufferedImageFuture scaleImageWithinBounds(int width, int height);
    ImageIconFuture getScaledCircularIcon(int width, int height);
    ImageIconFuture scaleIconWithinBounds(int width, int height);

    /**
     * Returns this image in bytes. Causes this image to resolved,
     * so this may take some time.
     *
     * @return a byte representation of this image
     */
    byte[] getBytes();
}
