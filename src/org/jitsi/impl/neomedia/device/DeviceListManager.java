/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.device;

import org.jitsi.service.configuration.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.util.*;

import javax.media.*;
import java.util.*;
import java.util.regex.*;

/**
 * Manages the list of active (currently plugged-in) capture/notify/playback
 * devices and manages user preferences between all known devices (previously
 * and actually plugged-in).
 *
 * All methods that alter the list of known or active devices (names and UIDs)
 * or that alter the order of preference of those devices are synchronized on
 * the instance of this class. This prevents orders of preference or device
 * selection from being calculated based on an out of date device list as
 * well as exceptions caused by concurrent modification.
 */
abstract class DeviceListManager
{
    /**
     * The <tt>Logger</tt> used by this instance for logging output.
     */
    private static final Logger sLog = Logger.getLogger(DeviceListManager.class);

    /**
     * The <tt>Logger</tt> for logging about device changes.
     */
    private static final Logger deviceLogger =
        Logger.getLogger("jitsi.DeviceLogger");

    /**
     * Dummy UID if a device isn't connected, or we don't have any UUIDs for it
     */
    private static final String UID_DUMMY = "1234";

    private final ConfigurationService mConfigurationService;

    /**
     * The audio/video system properties which store the device list.
     */
    private final DeviceSystemProperties mDeviceSystemProperties;

    /**
     * The list of device names saved by the configuration service and previously saved given user preference order.
     *
     * Access should be synchronized.
     */
    private final List<String> mDevicePreferences = new ArrayList<>();

    /**
     * A map from device name to their UIDs saved by the configuration service and previously saved given user
     * preference order. Should always map to empty list rather than null.
     *
     * Access should be synchronized.
     */
    private final Map<String, List<String>> mDeviceUIDs = new LinkedHashMap<>();

    /**
     * Whether we have already loaded device configuration from the config service.
     */
    private boolean mLoadedDeviceConfig;

    /**
     * The list of <tt>Device</tt>s which are active/plugged-in.
     *
     * Access should be synchronized.
     */
    private final List<Device> mActiveDevices = new ArrayList<>();

    /**
     * The currently selected Device
     */
    private Device mSelectedDevice = null;

    /**
     * @param deviceSystemProperties The audio/video system managing this device list
     */
    DeviceListManager(DeviceSystemProperties deviceSystemProperties)
    {
        this(deviceSystemProperties, LibJitsi.getConfigurationService());
    }

    /**
     * @param deviceSystemProperties The audio/video system managing this device list
     * @param configurationService The configuration service on which device list preferences are stored
     */
    DeviceListManager(DeviceSystemProperties deviceSystemProperties, ConfigurationService configurationService)
    {
        mDeviceSystemProperties = deviceSystemProperties;
        mConfigurationService = configurationService;
    }

    /**
     * Gets a <tt>Device</tt> which is known to this instance and is identified by a specific
     * <tt>MediaLocator</tt>.
     *
     * @param locator the <tt>MediaLocator</tt> of the <tt>Device</tt> to be returned
     * @return a <tt>Device</tt> which is known to this instance and is identified by the specified
     *          <tt>locator</tt>
     */
    public synchronized Device getDevice(MediaLocator locator)
    {
        Device device = null;

        if (locator != null)
        {
            for (Device aDevice : mActiveDevices)
            {
                MediaLocator aLocator = aDevice.getLocator();

                if (locator.equals(aLocator))
                {
                    device = aDevice;
                    break;
                }
            }
        }

        return device;
    }

    /**
     * Sets the list of <tt>Device</tt>s which are active/plugged-in.
     *
     * @param devices the list of <tt>Device</tt>s which are active/plugged-in
     */
    public synchronized void setActiveDevices(List<Device> devices)
    {
        if (devices != null)
        {
            sLog.debug("Setting active devices from : " +
                                          mActiveDevices + " to "+ devices);
            mActiveDevices.clear();

            for (Device device : devices)
            {
                if (device != null)
                {
                    mActiveDevices.add(device);
                }
                else if (device == null)
                {
                    // Don't allow null entries into a device list
                    sLog.warn("Null device found in " + devices);
                }
            }
        }
    }

    /**
     * @return the list of the <tt>Device</tt>s which are active/plugged-in
     */
    public synchronized List<Device> getActiveDevices()
    {
        return new ArrayList<>(mActiveDevices);
    }

    /**
     * @return a map with an entry for each active device containing its unique device name (by appending a number to
     * the name in the case of multiple devices with the same name) and its UID and one entry for each further device
     * name found in properties but not currently active
     */
    synchronized LinkedHashMap<String, String> getAllKnownDevices()
    {
        LinkedHashMap<String, String> deviceNames = new LinkedHashMap<>();
        for (String devicePref : mDevicePreferences)
        {
            boolean isActiveDevice = false;
            List<String> matchingDevicesList = new ArrayList<>();

            // Find the list of active devices that match this device preference
            for (Device activeDevice : mActiveDevices)
            {
                if (devicePref.equals(activeDevice.getName()))
                {
                    matchingDevicesList.add(activeDevice.getUID());
                }
            }

            // If there are multiple connected devices of the same name then we number the duplicates so the user
            // can tell them apart. We must update the map so we can correctly associate a device UID with the new
            // name we have given it.
            for (Device activeDevice : mActiveDevices)
            {
                if (devicePref.equals(activeDevice.getName()))
                {
                    String deviceName = (matchingDevicesList.size() > 1) ?
                        devicePref + " " + (matchingDevicesList.indexOf(activeDevice.getUID()) + 1) :
                        devicePref;

                    // This device is currently plugged in
                    deviceNames.put(deviceName, activeDevice.getUID());
                    isActiveDevice = true;
                }
            }

            // If this device is not plugged in, we still want to show it in the preferences list
            if (!isActiveDevice)
            {
                deviceNames.put(devicePref, null);
            }
        }

        return deviceNames;
    }

    /**
     * Sets the selected device. Seems to only be called with an active device.
     * @param device the selected active device
     */
    void setSelectedDevice(Device device)
    {
        // Checks if there is a change.
        if (!Objects.equals(device, mSelectedDevice))
        {
            sLog.debug("Setting the selected device to " + device.getName() + ":" + device.getUID());

            // Saves the new selected device in top of the user preferences.
            updateDeviceList(device, SaveDeviceMode.PUT_AT_START);
            writeDevicePreferences(getPropDevice());

            // getAndRefresh should always return the value of the first device in properties - at least unless new
            // devices have been plugged in since.
            getAndRefreshSelectedDevice(true);
        }
    }

    /**
     * Gets the selected active device.
     *
     * @return the selected active device
     */
    public Device getSelectedDevice()
    {
        // Use getAndRefreshSelectedDevice to get up to date selected device
        // as the cached mSelected device can sometimes be out of date.
        return getAndRefreshSelectedDevice(false);
    }

    /**
     * Refreshes the selected device.
     *
     * @param firePropertyChange Whether a PropertyChangeEvent should be fired
     * @return The selected device
     */
    Device getAndRefreshSelectedDevice(boolean firePropertyChange)
    {
        deviceLogger.debug("Refreshing the selected device, was: " + mSelectedDevice);

        Device previouslySelectedDevice = mSelectedDevice;

        ensureDevicePreferencesLoaded();
        reorderDevicePreferences();
        writeDevicePreferences(getPropDevice());
        Device deviceToSelect = getPreferredDevice();

        if (firePropertyChange && !Objects.equals(previouslySelectedDevice, deviceToSelect))
        {
            mDeviceSystemProperties.propertyChange(getPropDevice(), previouslySelectedDevice, deviceToSelect);
        }

        deviceLogger.debug("Selected device now: " + deviceToSelect);

        mSelectedDevice = deviceToSelect;

        return deviceToSelect;
    }

    /**
     * Loads device name ordered with user's preference from the <tt>ConfigurationService</tt>.
     */
    private void ensureDevicePreferencesLoaded()
    {
        if (mLoadedDeviceConfig)
        {
            return;
        }

        // We attempt to load using the new config style (V2.9.00+), if it is not found then we load using the old
        // config style (which doesn't include device UIDs), and then migrate this to the new config.
        if (mConfigurationService != null)
        {
            String newProperty = mDeviceSystemProperties.getPropertyName(getPropDevice() + "_list2");
            String deviceIdentifiersString = mConfigurationService.user().getString(newProperty);

            // Device identifier string should be of the form [<device>, <device>,...] where each <device> is of the
            // form "name:<name> uid:<uid>" If not, then act as though the config is not present
            if (deviceIdentifiersString != null &&
                    deviceIdentifiersString.startsWith("[\"") &&
                    deviceIdentifiersString.endsWith("\"]"))
            {
                sLog.debug("Found " + getDataflowType() + " device preferences " + deviceIdentifiersString);
                parseDeviceListString(deviceIdentifiersString);
            }
            else
            {
                // Use the old/legacy property to load the last preferred device.
                String oldProperty = mDeviceSystemProperties.getPropertyName(getPropDevice() + "_list");
                deviceIdentifiersString = mConfigurationService.user().getString(oldProperty);

                if (deviceIdentifiersString != null)
                {
                    sLog.debug("Found legacy format " + getDataflowType() + " device preferences " + deviceIdentifiersString);
                    parseLegacyDeviceListString(deviceIdentifiersString);
                }
            }

            mLoadedDeviceConfig = true;
        }
    }

    private synchronized void parseDeviceListString(String deviceIdentifiersString)
    {
        mDevicePreferences.clear();
        // We must parse the string in order to load the device list.
        String[] deviceIdentifiers = deviceIdentifiersString.substring(2, deviceIdentifiersString.length() - 2)
            .split("\", \"");

        for (String device : deviceIdentifiers)
        {
            // Device identifiers are now in the form: "name:<name> uid:<uid>"
            Matcher m = Pattern.compile("name:(.+) uid:(.+)").matcher(device);
            if (m.find())
            {
                String deviceName = m.group(1);
                String[] deviceUIDsList = m.group(2).replace("[", "").replace("]", "").split(";");

                if (deviceName == null)
                {
                    sLog.warn("Null device found in " + deviceIdentifiers);
                    continue;
                }

                if (!mDevicePreferences.contains(deviceName))
                {
                    sLog.debug("Add device: " + deviceName);
                    mDevicePreferences.add(deviceName);
                }

                List<String> uids = mDeviceUIDs.get(deviceName);

                if (uids == null)
                {
                    uids = new ArrayList<>();
                }

                for (String uid : deviceUIDsList)
                {
                    if (!uids.contains(uid))
                    {
                        sLog.debug("    UID: " + uid);
                        uids.add(uid);
                    }
                }

                mDeviceUIDs.put(deviceName, uids);
            }
        }
    }

    private synchronized void parseLegacyDeviceListString(String deviceIdentifiersString)
    {
        mDevicePreferences.clear();

        // We must parse the string in order to load the device list.
        String[] deviceIdentifiers = deviceIdentifiersString.substring(2, deviceIdentifiersString.length() - 2)
            .split("\", \"");

        for (String deviceIdentifier : deviceIdentifiers)
        {
            // Hack to handle migration from old WASAPI devices - old config for USB devices
            // has a USB port number in it which we now strip in getIMMDeviceFriendlyName().
            String deviceName = deviceIdentifier.replaceAll("\\([0-9]+- ", "(");

            // If we've already added this device name to the list, don't do so again.
            if (mDevicePreferences.contains(deviceName))
            {
                sLog.debug("Removing duplicate device from config: " + deviceName);
                continue;
            }

            mDevicePreferences.add(deviceName);

            // Migration code to add device UIDs to the current list of preferences
            for (Device activeDevice : mActiveDevices)
            {
                if (activeDevice.getName().equals(deviceName))
                {
                    List<String> uidsList = mDeviceUIDs.get(deviceName);
                    if (uidsList == null)
                    {
                        uidsList = new ArrayList<>();
                    }

                    uidsList.add(activeDevice.getUID());

                    mDeviceUIDs.put(deviceName, uidsList);

                    sLog.debug("Adding uid " + activeDevice.getUID() + " to device " + deviceName);
                }
            }

            // If this device isn't currently connected then we must write a dummy UID in order to keep the
            // device preference order.
            if (mDeviceUIDs.get(deviceName) == null)
            {
                List<String> uidsList = new ArrayList<>();
                uidsList.add(UID_DUMMY);
                mDeviceUIDs.put(deviceName, uidsList);
            }
        }

        // Now replace any old device config (old PortAudio config) that has the UID of the devices with
        // the names instead.
        renameToDeviceNames();
    }

    /**
     * Sorts all active devices in order of preference.
     */
    private synchronized void reorderDevicePreferences()
    {
        deviceLogger.debug("Got " + mActiveDevices.size() + " " + getDataflowType() + " active devices");

        // Ordering the device preferences should be an atomic operation, so we check whether the list is empty here,
        // before any new devices have been added, rather than as we add each device, since the length of the list may
        // change as we loop through multiple new active devices.
        boolean isEmptyList = mDevicePreferences.isEmpty();

        // Search if an active device is a new one (is not stored in the preferences yet). If true, then active this
        // device and set it as default device (only for USB devices since the user has deliberately plugged in the device).
        for (int i = mActiveDevices.size() - 1; i >= 0; i--)
        {
            Device activeDevice = mActiveDevices.get(i);
            deviceLogger.debug("Examining " + getDataflowType() + " device: " + activeDevice.getName() + " UID: " + activeDevice.getUID());

            boolean atStart;

            if (mDevicePreferences.contains(activeDevice.getName()))
            {
                // We have an active device that is in our device preference list: check if it is in a higher position
                // than the currently selected device to determine if it should be selected.
                deviceLogger.debug(getDataflowType() + " device preferences do contain: " + activeDevice.getName());

                // In case the selected device changes under our feet.
                Device selectedDevice = mSelectedDevice;
                atStart = (selectedDevice != null) &&
                    (mDevicePreferences.indexOf(activeDevice.getName()) < mDevicePreferences.indexOf(selectedDevice.getName()));
            }
            else
            {
                // Have a new device: if there are currently no devices in preferences, put this one at the start,
                // unless it is Bluetooth or AirPlay (and therefore may be tethered but not in range, for example).
                sLog.debug(getDataflowType() + " device preferences do not contain: " + activeDevice.getName());
                atStart = isEmptyList &&
                    !activeDevice.isSameTransportType(Device.TRANSPORT_TYPE_BLUETOOTH) &&
                    !activeDevice.isSameTransportType(Device.TRANSPORT_TYPE_AIRPLAY);
            }

            updateDeviceList(activeDevice, atStart ?
                SaveDeviceMode.PUT_AT_START_IF_ABSENT :
                SaveDeviceMode.PUT_AT_END_IF_ABSENT);
        }
    }

    /**
     * Renames any devices with UIDs as their name to have names.
     */
    private synchronized void renameToDeviceNames()
    {
        for (Device activeDevice : mActiveDevices)
        {
            String name = activeDevice.getName();
            String id = activeDevice.getModelIdentifier();

            // If the name and identifier for the device don't match and the identifier is currently used in the device
            // preferences, replace it with the name.
            if (!name.equals(id))
            {
                while (mDevicePreferences.contains(id))
                {
                    int nameIndex = mDevicePreferences.indexOf(name);
                    int idIndex = mDevicePreferences.indexOf(id);

                    if (nameIndex == -1)
                    {
                        // No name, replace id with name.
                        mDevicePreferences.set(idIndex, name);
                    }
                    else
                    {
                        // Name exists, just remove id.
                        mDevicePreferences.remove(idIndex);
                    }
                }
            }
        }
    }

    /**
     * @return the active device that appears first in the list of devices ordered by preference
     */
    private synchronized Device getPreferredDevice()
    {
        Device preferredDevice = null;

        // Search if an active device match one of the previously configured in the preferences.
        for (String devicePreference : mDevicePreferences)
        {
            deviceLogger.debug("Searching preferred devices with name " + devicePreference);

            if (devicePreference.equals(NoneAudioSystem.LOCATOR_PROTOCOL))
            {
                // The "none" device is the "preferred" device among "active" device.
                sLog.debug("Found a none device");
                preferredDevice = null;
                break;
            }

            List<Device> matchingDevices = new ArrayList<>();

            // Go through each active device and check for a device name match with user preferences
            for (Device activeDevice : mActiveDevices)
            {
                if (devicePreference.equals(activeDevice.getName()))
                {
                    // We have found the "preferred" device among active device.
                    deviceLogger.debug("Found matching device " + activeDevice.getName() + ":" + activeDevice.getUID());
                    matchingDevices.add(activeDevice);
                }
            }

            if (!matchingDevices.isEmpty())
            {
                // We have a list of devices that match on name. We now must check the UIDs of the devices to
                // determine the most preferred device
                List<String> uids = mDeviceUIDs.get(devicePreference);

                if (uids != null)
                {
                    // The list of UIDs is ordered, so loop through this returning the first matching device
                    for (String uid : uids)
                    {
                        for (Device matchingDevice : matchingDevices)
                        {
                            if (uid.equals(matchingDevice.getUID()))
                            {
                                deviceLogger.debug("Found preferred device " + matchingDevice.getName() + ":" + matchingDevice.getUID());
                                preferredDevice = matchingDevice;
                                return preferredDevice;
                            }
                        }
                    }
                }

                // If we haven't got a selected device yet then we have a matching device by name, but not by UID.
                // Therefore, return the first matching device by name.
                preferredDevice = matchingDevices.get(0);
                sLog.debug("No matching UID found for device " + devicePreference + " - using first one found: " + preferredDevice);
                break;
            }
        }

        return preferredDevice;
    }

    private synchronized void updateDeviceList(Device device, SaveDeviceMode mode)
    {
        String newDeviceName = device.getName();
        String newDeviceUID = device.getUID();

        if (newDeviceName == null || newDeviceUID == null)
        {
            // Don't include null devices
            sLog.warn("Null device identity. Device name: " + newDeviceName +
                                                " Device UID: " + newDeviceUID);
            return;
        }

        if (!mDevicePreferences.contains(newDeviceName) || !mode.preserveOrder())
        {
            sLog.debug("Putting device " + device + (mode.atStart() ? " at start " : " at end ") + " of preferences");
            mDevicePreferences.remove(newDeviceName);
            mDevicePreferences.add(mode.atStart() ? 0 : mDevicePreferences.size(), newDeviceName);
        }

        List<String> uidList = mDeviceUIDs.get(newDeviceName);

        if (uidList == null)
        {
            uidList = new ArrayList<>();
        }

        if (!uidList.contains(newDeviceUID) || !mode.preserveOrder())
        {
            uidList.remove(newDeviceUID);
            uidList.add(mode.atStart() ? 0 : uidList.size(), newDeviceUID);
        }

        mDeviceUIDs.put(newDeviceName, uidList);
    }

    /**
     * Saves the device preferences and write it to the configuration file.
     *
     * @param property the name of the <tt>ConfigurationService</tt> property
     */
    private void writeDevicePreferences(String property)
    {
        if (mConfigurationService != null)
        {
            property = mDeviceSystemProperties.getPropertyName(property + "_list2");
            mConfigurationService.user().setProperty(property, getPreferenceString());
        }
        else
        {
            sLog.error("No configuration service");
        }
    }

    /**
     * @return The string representation of the device list which should be
     *         saved to preferences.
     */
    private synchronized String getPreferenceString()
    {
        StringBuilder value = new StringBuilder("[\"");

        int devicePreferenceCount = mDevicePreferences.size();

        if (devicePreferenceCount != 0)
        {
            String deviceName = mDevicePreferences.get(0);

            value.append("name:" + deviceName + " uid:" + getUidPreferenceString(deviceName));

            for (int i = 1; i < devicePreferenceCount; i++)
            {
                deviceName = mDevicePreferences.get(i);
                value.append("\", \"");
                value.append("name:" + deviceName);
                value.append(" uid:" + getUidPreferenceString(deviceName));
            }
        }

        value.append("\"]");
        return value.toString();
    }

    /**
     * @return the string representation of the input list of UIDs, separated by semi-colons
     */
    private String getUidPreferenceString(String deviceName)
    {
        List<String> uids = mDeviceUIDs.get(deviceName);
        String uidList;

        if (uids == null || (uids.isEmpty()))
        {
            sLog.error("No configured UIDs for " + deviceName + " using dummy UID");
            uidList = UID_DUMMY;
        }
        else
        {
            uidList = String.join(";", uids);
        }

        return uidList;
    }

    /**
     * @return The property of the capture devices.
     */
    protected abstract String getPropDevice();

    protected abstract DataFlow getDataflowType();

    private enum SaveDeviceMode
    {
        PUT_AT_START,
        PUT_AT_START_IF_ABSENT,
        PUT_AT_END_IF_ABSENT;

        /**
         * If true, the device is added/moved to the start of the name and UID lists rather than the end
         */
        boolean atStart()
        {
            return PUT_AT_START.equals(this) || PUT_AT_START_IF_ABSENT.equals(this);
        }

        /**
         * If true, devices already in preferences will not be moved
         */
        boolean preserveOrder()
        {
            return PUT_AT_START_IF_ABSENT.equals(this) || PUT_AT_END_IF_ABSENT.equals(this);
        }
    }
}
