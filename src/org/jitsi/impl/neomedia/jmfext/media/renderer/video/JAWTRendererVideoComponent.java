/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.jmfext.media.renderer.video;

import java.awt.*;

import org.jitsi.impl.neomedia.codec.video.*;

/**
 * Implements an AWT <tt>Component</tt> in which <tt>JAWTRenderer</tt> paints.
 *
 * @author Lyubomir Marinov
 */
public class JAWTRendererVideoComponent
    extends Canvas
{
    /**
     * The serial version UID of the <tt>JAWTRendererVideoComponent</tt> class
     * defined to silence a serialization compile-time warning.
     */
    private static final long serialVersionUID = 0L;

    /**
     * The <tt>JAWTRenderer</tt> which paints in this
     * <tt>JAWTRendererVideoComponent</tt>.
     */
    protected final JAWTRenderer renderer;

    /**
     * The indicator which determines whether the native counterpart of this
     * <tt>JAWTRenderer</tt> wants <tt>paint</tt> calls on its AWT
     * <tt>Component</tt> to be delivered. For example, after the native
     * counterpart has been able to acquire the native handle of the AWT
     * <tt>Component</tt>, it may be able to determine when the native
     * handle needs painting without waiting for AWT to call <tt>paint</tt>
     * on the <tt>Component</tt>. In such a scenario, the native counterpart
     * may indicate with <tt>false</tt> that it does not need further
     * <tt>paint</tt> deliveries.
     */
    private boolean wantsPaint = true;

    /**
     * Initializes a new <tt>JAWTRendererVideoComponent</tt> instance.
     *
     * @param renderer
     */
    public JAWTRendererVideoComponent(JAWTRenderer renderer)
    {
        this.renderer = renderer;
    }

    /**
     * Overrides {@link Component#addNotify()} to reset the indicator which
     * determines whether the native counterpart of this <tt>JAWTRenderer</tt>
     * wants <tt>paint</tt> calls on its AWT <tt>Component</tt> to be delivered.
     */
    @Override
    public void addNotify()
    {
        try
        {
            super.addNotify();
        }
        catch (Exception e)
        {
            // Not fatal so just continue
        }

        wantsPaint = true;
    }

    /**
     * Gets the handle of the native counterpart of the
     * <tt>JAWTRenderer</tt> which paints in this
     * <tt>AWTVideoComponent</tt>.
     *
     * @return the handle of the native counterpart of the
     * <tt>JAWTRenderer</tt> which paints in this <tt>AWTVideoComponent</tt>
     */
    protected long getHandle()
    {
        return renderer.getHandle();
    }

    /**
     * Gets the synchronization lock which protects the access to the
     * <tt>handle</tt> property of this <tt>AWTVideoComponent</tt>.
     *
     * @return the synchronization lock which protects the access to the
     * <tt>handle</tt> property of this <tt>AWTVideoComponent</tt>
     */
    protected Object getHandleLock()
    {
        return renderer.getHandleLock();
    }

    /**
     * Overrides {@link Canvas#paint(Graphics)} to paint this <tt>Component</tt>
     * in the native counterpart of its associated <tt>JAWTRenderer</tt>.
     */
    @Override
    public void paint(Graphics g)
    {
        /*
         * XXX If the size of this Component is tiny enough to crash sws_scale,
         * then it may cause issues with other functionality as well. Stay on
         * the safe side.
         */
        if (wantsPaint
                && (getWidth() >= SwScale.MIN_SWS_SCALE_HEIGHT_OR_WIDTH)
                && (getHeight() >= SwScale.MIN_SWS_SCALE_HEIGHT_OR_WIDTH))
        {
            synchronized (getHandleLock())
            {
                long handle;

                if ((handle = getHandle()) != 0)
                {
                    Container parent = getParent();
                    int zOrder
                        = (parent == null)
                            ? -1
                            : parent.getComponentZOrder(this);

                    wantsPaint = JAWTRenderer.paint(handle, this, g, zOrder);
                }
            }
        }
    }

    /**
     * Overrides {@link Component#removeNotify()} to reset the indicator which
     * determines whether the native counterpart of this <tt>JAWTRenderer</tt>
     * wants <tt>paint</tt> calls on its AWT <tt>Component</tt> to be delivered.
     */
    @Override
    public void removeNotify()
    {
        /*
         * In case the associated JAWTRenderer has said that it does not
         * want paint events/notifications, ask it again next time because
         * the native handle of this Canvas may be recreated.
         */
        wantsPaint = true;

        super.removeNotify();
    }

    /**
     * Overrides {@link Canvas#update(Graphics)} to skip the filling with the
     * background color in order to prevent flickering.
     */
    @Override
    public void update(Graphics g)
    {
        synchronized (getHandleLock())
        {
            if (!wantsPaint || (getHandle() == 0))
            {
                super.update(g);
                return;
            }
        }

        /*
         * Skip the filling with the background color because it causes
         * flickering.
         */
        paint(g);
    }
}
