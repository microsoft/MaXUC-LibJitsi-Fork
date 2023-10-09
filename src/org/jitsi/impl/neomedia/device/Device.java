/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.device;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.media.*;

/**
 * Adds some important information (i.e. device type, UID.) to FMJ
 * <tt>CaptureDeviceInfo</tt>.
 *
 * @author Vincent Lucas
 * @author Lyubomir Marinov
 */
public class Device
    extends CaptureDeviceInfo
{
    private static final long serialVersionUID = 0L;

    static final String TRANSPORT_TYPE_USB = "USB";
    static final String TRANSPORT_TYPE_BLUETOOTH = "Bluetooth";
    static final String TRANSPORT_TYPE_AIRPLAY = "AirPlay";

    /**
     * The device transport type.
     */
    private final String transportType;

    /**
     * The device UID (unique identifier).
     */
    private final String uid;

    /**
     * The persistent identifier for the model of this device.
     */
    private final String modelIdentifier;

    /**
     * Initializes a new <tt>Device</tt> instance with the
     * specified name, media locator, and array of Format objects.
     *
     * @param name the human-readable name of the new instance
     * @param locator the <tt>MediaLocator</tt> which uniquely identifies the
     * device to be described by the new instance
     * @param formats an array of the <tt>Format</tt>s supported by the device
     * to be described by the new instance
     * @param uid the unique identifier of the hardware device (interface) which
     * is to be represented by the new instance
     * @param transportType the transport type (e.g. USB) of the device to be
     * represented by the new instance
     * @param modelIdentifier the persistent identifier of the model of the
     * hardware device to be represented by the new instance
     */
    public Device(
            String name,
            MediaLocator locator,
            Format[] formats,
            String uid,
            String transportType,
            String modelIdentifier)
    {
        super(name, locator, formats);

        this.uid = uid;
        this.transportType = transportType;
        this.modelIdentifier = modelIdentifier;
    }

    /**
     * Determines whether a specific <tt>Object</tt> is equal (by value) to this
     * instance.
     *
     * @param obj the <tt>Object</tt> to be determined whether it is equal (by
     * value) to this instance
     * @return <tt>true</tt> if the specified <tt>obj</tt> is equal (by value)
     * to this instance; otherwise, <tt>false</tt>
     */
    @Override
    public boolean equals(Object obj)
    {
        if (obj == null)
            return false;
        else if (obj == this)
            return true;
        else if (obj instanceof Device)
        {
            Device cdi2 = (Device) obj;

            // locator
            MediaLocator locator = getLocator();
            MediaLocator cdi2Locator = cdi2.getLocator();

            if (locator == null)
            {
                if (cdi2Locator != null)
                    return false;
            }
            else if (cdi2Locator == null)
                return false;
            else
            {
                // protocol
                String protocol = locator.getProtocol();
                String cdi2Protocol = cdi2Locator.getProtocol();

                if (protocol == null)
                {
                    if (cdi2Protocol != null)
                        return false;
                }
                else if (cdi2Protocol == null)
                    return false;
                else if (!protocol.equals(cdi2Protocol))
                    return false;
            }

            // identifier
            return getIdentifier().equals(cdi2.getIdentifier());
        }
        else
            return false;
    }

    /**
     * Returns the device identifier used to save and load device preferences.
     * It is composed by the system UID if not null. Otherwise returns the
     * device name and (if not null) the transport type.
     *
     * @return The device identifier.
     */
    public String getIdentifier()
    {
        return (uid == null) ? name : uid;
    }

    /**
     * Returns the device UID (unique identifier) of this instance.
     *
     * @return the device UID (unique identifier) of this instance
     */
    public String getUID()
    {
        return uid;
    }

    /**
     * Returns the model identifier of this instance.
     *
     * @return the model identifier of this instance
     */
    public String getModelIdentifier()
    {
        return (modelIdentifier == null) ? name : modelIdentifier;
    }

    /**
     * Returns a hash code value for this object for the benefit of hashtables.
     *
     * @return a hash code value for this object for the benefit of hashtables
     */
    @Override
    public int hashCode()
    {
        return getIdentifier().hashCode();
    }

    /**
     * Determines whether a specific transport type is equal to/the same as the
     * transport type of this instance.
     *
     * @param transportType the transport type to compare to the transport type
     * of this instance
     * @return <tt>true</tt> if the specified <tt>transportType</tt> is equal
     * to/the same as the transport type of this instance; otherwise,
     * <tt>false</tt>
     */
    public boolean isSameTransportType(String transportType)
    {
        return Objects.equals(this.transportType, transportType);
    }

    /**
     * Returns the device's name without the type (the string inside the first
     * set of parentheses). For example, if the device has a name "Headset
     * Microphone (Jabra UC VOICE 550a MS)" this function will return
     * "Jabra UC VOICE 550a MS". If the device name does not have a type
     * (no parentheses), then returns the raw device name.
     *
     * @return the name without the device type
     */
    public String getNameWithoutDeviceType()
    {
        String deviceName = getName();

        Pattern p = Pattern.compile("\\(.*?\\)");
        Matcher m = p.matcher(deviceName);

        // If there are parentheses in the name, extract the contact inside
        if(m.find())
        {
            deviceName = (String) m.group().subSequence(1, m.group().length()-1);
        }

        return deviceName;
    }
}
