// Copyright (c) Microsoft Corporation. All rights reserved.
package org.jitsi.impl.neomedia.device;

import java.util.*;

import javax.media.*;

import org.jitsi.service.neomedia.*;
import org.jitsi.util.*;

/**
 * Represents a <tt>DeviceSystem</tt> which provides support for the devices to
 * capture and play back video.
 */
public abstract class VideoSystem
    extends DeviceSystem implements DeviceSystemProperties
{
    /**
     * The <tt>Logger</tt> used by this instance for logging output.
     */
    private static final Logger sLog = Logger.getLogger(VideoSystem.class);

    public static final String LOCATOR_PROTOCOL_DIRECTSHOW = "directshow";

    public static final String LOCATOR_PROTOCOL_AVFOUNDATION = "avfoundation";

    public static VideoSystem getVideoSystem(String locatorProtocol)
    {
        VideoSystem videoSystem = getVideoSystem();
        VideoSystem videoSystemWithLocatorProtocol = null;

        if ((videoSystem != null) &&
            (videoSystem.getLocatorProtocol().equalsIgnoreCase(locatorProtocol)))
        {
            videoSystemWithLocatorProtocol = videoSystem;
        }
        return videoSystemWithLocatorProtocol;
    }

    public static VideoSystem getVideoSystem()
    {
        VideoSystem videoSystem = null;

        DeviceSystem[] deviceSystems = DeviceSystemManager.getDeviceSystems(MediaType.VIDEO);

        for (DeviceSystem deviceSystem : deviceSystems)
        {
            if (deviceSystem instanceof VideoSystem)
            {
                videoSystem = (VideoSystem) deviceSystem;
                break;
            }
        }

        return videoSystem;
    }

    /**
     * The device list manager for video.
     */
    VideoDeviceListManager mDeviceListManager;

    protected VideoSystem(String locatorProtocol)
        throws Exception
    {
        this(locatorProtocol, 0);
    }

    protected VideoSystem(String locatorProtocol, int features)
        throws Exception
    {
        super(MediaType.VIDEO, locatorProtocol, features);
    }

    /**
     * Gets a <tt>Device</tt> which has been contributed by this
     * <tt>VideoSystem</tt> and is identified by a specific
     * <tt>MediaLocator</tt>.
     *
     * @param locator the <tt>MediaLocator</tt> of the
     * <tt>Device</tt> to be returned
     * @return a <tt>Device</tt> which has been contributed by this
     * instance, and is identified by the specified <tt>locator</tt>.
     */
    public Device getDevice(MediaLocator locator)
    {
        return mDeviceListManager.getDevice(locator);
    }

    /**
     * Gets the list of active video devices.
     *
     * @return the list of devices.
     */
    public List<Device> getActiveDevices()
    {
        return mDeviceListManager.getActiveDevices();
    }

    /**
     * Gets the list of all video devices in user preferences.
     * @return the list of all devices.
     */
    public LinkedHashMap<String, String> getAllKnownDevices()
    {
        return mDeviceListManager.getAllKnownDevices();
    }

    @Override
    public String getPropertyName(String basePropertyName)
    {
        return
            DeviceConfiguration.PROP_VIDEO_SYSTEM + "." + getLocatorProtocol()
                + "." + basePropertyName;
    }

    /**
     * Gets the selected video device.
     * @return the selected device.
     */
    public Device getSelectedDevice()
    {
        return mDeviceListManager.getSelectedDevice();
    }

    /**
     * Refreshes the selected video device and gets the new selected device.
     * @return the selected device.
     */
    public Device getAndRefreshSelectedDevice()
    {
        return mDeviceListManager.getAndRefreshSelectedDevice(false);
    }

    /**
     * {@inheritDoc}
     *
     * Because <tt>AudioSystem</tt> may support playback and notification audio
     * devices apart from capture audio devices, fires more specific
     * <tt>PropertyChangeEvent</tt>s than <tt>DeviceSystem</tt>
     */
    @Override
    protected void postInitialize()
    {
        try
        {
            postInitializeSpecificDevices();
        }
        finally
        {
            super.postInitialize();
        }
    }

    /**
     * Sets the device lists after the different video systems have finished
     * detecting their devices.
     */
    private void postInitializeSpecificDevices()
    {
        // Fires a PropertyChangeEvent to enable hot-plugging devices during a call.
        Device selectedActiveDevice = mDeviceListManager.getAndRefreshSelectedDevice(true);
        sLog.debug("Selected device: " + selectedActiveDevice + ", active devices: " + getActiveDevices());
    }

    /**
     * {@inheritDoc}
     *
     * Removes any capture, playback and notification devices previously
     * detected by this <tt>AudioSystem</tt> and prepares it for the execution
     * of its {@link DeviceSystem#doInitialize()} implementation (which detects
     * all devices to be provided by this instance).
     */
    @Override
    protected void preInitialize()
    {
        super.preInitialize();

        if (mDeviceListManager == null)
        {
            mDeviceListManager = new VideoDeviceListManager(this);
        }
    }

    @Override
    public void propertyChange(String property, Object oldValue, Object newValue)
    {
        firePropertyChange(property, oldValue, newValue);
    }

    /**
     * Sets the list of active video devices.
     *
     * @param videoDevices The list of active capture devices.
     */
    void setVideoDevices(List<Device> videoDevices)
    {
        mDeviceListManager.setActiveDevices(videoDevices);
    }

    /**
     * Selects the selected active device.
     * @param device The selected active device.
     */
    public void setSelectedDevice(Device device)
    {
        mDeviceListManager.setSelectedDevice(device);
    }
}
