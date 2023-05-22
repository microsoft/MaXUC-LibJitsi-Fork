/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
// Portions (c) Microsoft Corporation. All rights reserved.
package org.jitsi.util.swing;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Implements the <tt>LayoutManager</tt> for video previews.
 */
public class VideoLayout
    extends FitLayout
{
    /**
     * The center remote video constraint.
     */
    public static final String CENTER_REMOTE = "CENTER_REMOTE";

    /**
     * The horizontal gap between the <tt>Component</tt> being laid out by
     * <tt>VideoLayout</tt>.
     */
    private static final int HGAP = 10;

    /**
     * The maximum initial size of the video - we don't want to create a
     * massive window when video is fist added
     */
    private static final Dimension MAX_INITIAL_SIZE = new Dimension(640, 480);

    /**
     * The map of component constraints.
     */
    private final Map<Component, Object> constraints
        = new HashMap<>();

    /**
     * The list of <tt>Component</tt>s depicting remote videos.
     */
    private final List<Component> remotes = new LinkedList<>();

    /**
     * Adds the given component in this layout on the specified by name
     * position.
     *
     * @param name the constraint giving the position of the component in this
     * layout
     * @param comp the component to add
     */
    @Override
    public void addLayoutComponent(String name, Component comp)
    {
        super.addLayoutComponent(name, comp);

        synchronized (constraints)
        {
            constraints.put(comp, name);
        }

        if ((name == null) || name.equals(CENTER_REMOTE))
        {
            if (!remotes.contains(comp))
                remotes.add(comp);
        }
    }

    /**
     * Determines whether the aspect ratio of a specific <tt>Dimension</tt> is
     * to be considered equal to the aspect ratio of specific <tt>width</tt> and
     * <tt>height</tt>.
     *
     * @param size the <tt>Dimension</tt> whose aspect ratio is to be compared
     * to the aspect ratio of <tt>width</tt> and <tt>height</tt>
     * @param width the width which defines in combination with <tt>height</tt>
     * the aspect ratio to be compared to the aspect ratio of <tt>size</tt>
     * @param height the height which defines in combination with <tt>width</tt>
     * the aspect ratio to be compared to the aspect ratio of <tt>size</tt>
     * @return <tt>true</tt> if the aspect ratio of <tt>size</tt> is to be
     * considered equal to the aspect ratio of <tt>width</tt> and
     * <tt>height</tt>; otherwise, <tt>false</tt>
     */
    public static boolean areAspectRatiosEqual(
            Dimension size,
            int width, int height)
    {
        if ((size.height == 0) || (height == 0))
            return false;
        else
        {
            double a = size.width / (double) size.height;
            double b = width / (double) height;
            double diff = a - b;

            return (-0.01 < diff) && (diff < 0.01);
        }
    }

    /**
     * Returns the remote video component.
     *
     * @return the remote video component
     */
    @Override
    protected Component getComponent(Container parent)
    {
        return (remotes.size() == 1) ? remotes.get(0) : null;
    }

    /**
     * Returns the constraints for the given component.
     *
     * @param c the component for which constraints we're looking for
     * @return the constraints for the given component
     */
    public Object getComponentConstraints(Component c)
    {
        synchronized (constraints)
        {
            return constraints.get(c);
        }
    }

    /**
     * Lays out the specified <tt>Container</tt> (i.e. the <tt>Component</tt>s
     * it contains) in accord with the logic implemented by this
     * <tt>LayoutManager</tt>.
     *
     * @param parent the <tt>Container</tt> to lay out
     */
    @Override
    public void layoutContainer(Container parent)
    {
        /*
         * XXX The methods layoutContainer and preferredLayoutSize must be kept
         * in sync.
         */

        List<Component> visibleRemotes = new ArrayList<>();
        List<Component> remotes;

        for (int i = 0; i < this.remotes.size(); i++)
        {
            if (this.remotes.get(i).isVisible())
                visibleRemotes.add(this.remotes.get(i));
        }

        remotes = visibleRemotes;

        int remoteCount = remotes.size();
        Dimension parentSize = parent.getSize();

        if (remoteCount == 1)
        {
            super.layoutContainer(parent, Component.CENTER_ALIGNMENT);
        }
    }

    /**
     * Returns the preferred layout size for the given container.
     *
     * @param parent the container which preferred layout size we're looking for
     * @return a Dimension containing, the preferred layout size for the given
     * container
     */
    @Override
    public Dimension preferredLayoutSize(Container parent)
    {
        List<Component> visibleRemotes = new ArrayList<>();
        List<Component> remotes;

        for (int i = 0; i < this.remotes.size(); i++)
        {
            if (this.remotes.get(i).isVisible())
                visibleRemotes.add(this.remotes.get(i));
        }

        remotes = visibleRemotes;

        int remoteCount = remotes.size();
        Dimension prefLayoutSize;

        if (remoteCount == 1)
        {
            // The super call gets the size according to the resolution of the
            // video that we are displaying. However this can sometimes be too
            // large. Thus we scale it to make sure that it is a sensible size
            // to start with.
            prefLayoutSize = super.preferredLayoutSize(parent);

            if (prefLayoutSize.height > MAX_INITIAL_SIZE.height)
            {
                float scaleFactor =
                         (float)prefLayoutSize.height / MAX_INITIAL_SIZE.height;
                prefLayoutSize = new Dimension((int)(prefLayoutSize.width / scaleFactor),
                                               (int)(prefLayoutSize.height / scaleFactor));
            }

            if (prefLayoutSize.width > MAX_INITIAL_SIZE.width)
            {
                float scaleFactor =
                           (float)prefLayoutSize.width / MAX_INITIAL_SIZE.width;
                prefLayoutSize = new Dimension((int)(prefLayoutSize.width / scaleFactor),
                                               (int)(prefLayoutSize.height / scaleFactor));
            }
        }
        else
        {
            prefLayoutSize = null;
        }

        if (prefLayoutSize == null)
            prefLayoutSize = super.preferredLayoutSize(parent);
        else if ((prefLayoutSize.height < 1) || (prefLayoutSize.width < 1))
        {
            prefLayoutSize.height = DEFAULT_HEIGHT_OR_WIDTH;
            prefLayoutSize.width = DEFAULT_HEIGHT_OR_WIDTH;
        }

        return prefLayoutSize;
    }

    /**
     * Removes the given component from this layout.
     *
     * @param comp the component to remove from the layout
     */
    @Override
    public void removeLayoutComponent(Component comp)
    {
        super.removeLayoutComponent(comp);

        synchronized (constraints)
        {
            constraints.remove(comp);
        }

        remotes.remove(comp);
    }
}
