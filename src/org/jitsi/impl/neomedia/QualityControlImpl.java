/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia;

import java.awt.*;

import org.jitsi.impl.neomedia.device.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.protocol.*;
import org.jitsi.util.*;

/**
 * Implements {@link QualityControl} for the purposes of
 * {@link VideoMediaStreamImpl}.
 *
 * @author Damian Minkov
 * @author Lyubomir Marinov
 */
class QualityControlImpl
    implements QualityControl
{
    /**
     * The <tt>Logger</tt> used by the <tt>QualityControlImpl</tt> class and its
     * instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(QualityControlImpl.class);

    /**
     * This is the local settings from the configuration panel.
     */
    private QualityPreset localSettingsPreset;

    /**
     * The maximum values for resolution, framerate, etc.
     */
    private QualityPreset maxPreset;

    /**
     * The current used preset.
     */
    private QualityPreset preset;

    /**
     * Sets the preset.
     *
     * @param preset the desired video settings
     */
    private void setRemoteReceivePreset(QualityPreset preset)
    {
        QualityPreset preferredSendPreset = getPreferredSendPreset();

        if (preset.compareTo(preferredSendPreset) > 0)
            this.preset = preferredSendPreset;
        else
        {
            this.preset = preset;

            Dimension resolution;

            if ((preset != null)
                 && ((resolution = preset.getResolution()) != null))
            {
                logger.info(
                        "video send resolution: " + resolution.width + "x"
                            + resolution.height);
            }
        }
    }

    /**
     * The current preset.
     *
     * @return the current preset
     */
    @Override
    public QualityPreset getRemoteReceivePreset()
    {
        return preset;
    }

    /**
     * The minimum resolution values for remote part.
     *
     * @return minimum resolution values for remote part.
     */
    @Override
    public QualityPreset getRemoteSendMinPreset()
    {
        /* We do not support such a value at the time of this writing. */
        return null;
    }

    /**
     * The max resolution values for remote part.
     *
     * @return max resolution values for remote part.
     */
    @Override
    public QualityPreset getRemoteSendMaxPreset()
    {
        return maxPreset;
    }

    /**
     * Does nothing specific locally.
     *
     * @param preset the max preset
     */
    @Override
    public void setPreferredRemoteSendMaxPreset(QualityPreset preset)
    {
        setRemoteSendMaxPreset(preset);
    }

    /**
     * Changes remote send preset, the one we will receive.
     *
     * @param preset
     */
    @Override
    public void setRemoteSendMaxPreset(QualityPreset preset)
    {
        this.maxPreset = preset;
    }

    /**
     * Gets the local setting of capture.
     *
     * @return the local setting of capture
     */
    private QualityPreset getPreferredSendPreset()
    {
        if(localSettingsPreset == null)
        {
            DeviceConfiguration devCfg
                = NeomediaServiceUtils
                    .getMediaServiceImpl()
                        .getDeviceConfiguration();

            localSettingsPreset
                = new QualityPreset(
                        devCfg.getVideoSize(),
                        devCfg.getFrameRate());
        }
        return localSettingsPreset;
    }

    /**
     * Sets maximum resolution.
     *
     * @param res
     */
    public void setRemoteReceiveResolution(Dimension res)
    {
        setRemoteReceivePreset(new QualityPreset(res));
    }
}
