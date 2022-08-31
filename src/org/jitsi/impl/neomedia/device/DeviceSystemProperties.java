/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.device;

public interface DeviceSystemProperties
{
    /**
     * Gets the (full) name of the <tt>ConfigurationService</tt> property which
     * is associated with a (base) <tt>DeviceSystem</tt>-specific property name.
     *
     * @param basePropertyName the (base) <tt>DeviceSystem</tt>-specific property
     * name of which the associated (full) <tt>ConfigurationService</tt>
     * property name is to be returned
     * @return the (full) name of the <tt>ConfigurationService</tt> property
     * which is associated with the (base) <tt>Deviceystem</tt>-specific
     * property name
     */
    String getPropertyName(String basePropertyName);

    /**
     * Fires a new <tt>PropertyChangeEvent</tt> to the
     * <tt>PropertyChangeListener</tt>s registered with this
     * <tt>PropertyChangeNotifier</tt> in order to notify about a change in the
     * value of a specific property which had its old value modified to a
     * specific new value. <tt>PropertyChangeNotifier</tt> does not check
     * whether the specified <tt>oldValue</tt> and <tt>newValue</tt> are indeed
     * different.
     *
     * @param property the name of the property of this
     * <tt>PropertyChangeNotifier</tt> which had its value changed
     * @param oldValue the value of the property with the specified name before
     * the change
     * @param newValue the value of the property with the specified name after
     * the change
     */
    void propertyChange(String property, Object oldValue, Object newValue);
}
