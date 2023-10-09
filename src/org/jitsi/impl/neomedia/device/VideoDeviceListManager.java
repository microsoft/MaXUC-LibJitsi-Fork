// Copyright (c) Microsoft Corporation. All rights reserved.
package org.jitsi.impl.neomedia.device;

/**
 * Manages the list of active (currently plugged-in) video devices and manages
 * user preferences between all known devices (previously and actually
 * plugged-in).
 */
public class VideoDeviceListManager
    extends DeviceListManager
{
    /**
     * The property of the capture devices.
     */
    public static final String PROP_DEVICE = "videoDevice";

    /**
     * Initializes the video device list management.
     *
     * @param videoSystem The video system managing this video device list.
     */
    public VideoDeviceListManager(VideoSystem videoSystem)
    {
        super(videoSystem);
    }

    /**
     * Returns the property of the video devices.
     *
     * @return The property of the video devices.
     */
    @Override
    protected String getPropDevice()
    {
        return PROP_DEVICE;
    }

    @Override
    protected DataFlow getDataflowType()
    {
        return DataFlow.VIDEO;
    }
}
