/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
// Portions (c) Microsoft Corporation. All rights reserved.
package org.jitsi.service.configuration;

import java.beans.*;
import java.io.*;
import java.util.*;

/**
 * The configuration services provides a centralized approach of storing
 * persistent configuration data.
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Dmitri Melnikov
 */
public interface ScopedConfigurationService
{
    /**
     * The name of the property that indicates the name of the directory where
     * Jitsi is to store user specific data such as configuration
     * files, message and call history as well as is bundle repository.
     */
    String PNAME_SC_HOME_DIR_NAME
        = "net.java.sip.communicator.SC_HOME_DIR_NAME";

    /**
     * The name of the property that indicates the location of the directory
     * where Jitsi is to store user specific data such as
     * configuration files, message and call history as well as is bundle
     * repository.
     */
    String PNAME_SC_HOME_DIR_LOCATION
        = "net.java.sip.communicator.SC_HOME_DIR_LOCATION";

    /**
     * The name of the boolean system property  which indicates whether the
     * configuration file is to be considered read-only. The default value is
     * <tt>false</tt> which means that the configuration file is considered
     * writable.
     */
    String PNAME_CONFIGURATION_FILE_IS_READ_ONLY
        = "net.java.sip.communicator.CONFIGURATION_FILE_IS_READ_ONLY";

    /**
     * The name of the system property that stores the name of the configuration
     * file.
     */
    String PNAME_CONFIGURATION_FILE_NAME
        = "net.java.sip.communicator.CONFIGURATION_FILE_NAME";

    /**
     * Sets the property with the specified name to the specified value.
     * A PropertyChangeEvent will be dispatched.
     * <p>
     * @param propertyName the name of the property to change.
     * @param property the new value of the specified property.
     */
    void setProperty(String propertyName, Object property);

    /**
     * Sets the property with the specified name to the specified.
     * A PropertyChangeEvent will be dispatched. This method also allows the
     * caller to specify whether or not the specified property is a system one.
     * <p>
     * @param propertyName the name of the property to change.
     * @param property the new value of the specified property.
     * @param isSystem specifies whether or not the property being is a System
     *                 property and should be resolved against the system
     *                 property set
     */
    void setProperty(String propertyName,
                     Object property,
                     boolean isSystem);

    /**
     * Sets a set of specific properties to specific values as a batch operation
     * then the modifications are performed and finally
     * <code>PropertyChangeListener</code>s are notified about the changes of
     * each of the specified properties. The batch operations allows the
     * <code>ConfigurationService</code> implementations to optimize, for
     * example, the saving of the configuration which in this case can be
     * performed only once for the setting of multiple properties.
     *
     * @param properties
     *            a <code>Map</code> of property names to their new values to be
     *            set
     */
    void setProperties(Map<String, Object> properties);

    /**
     * Returns the value of the property with the specified name or null if no
     * such property exists.
     * @param propertyName the name of the property that is being queried.
     * @return the value of the property with the specified name.
     */
    Object getProperty(String propertyName);

    /**
     * Removes the property with the specified name.
     * A PropertyChangeEvent will be dispatched.
     * All properties with prefix propertyName will also be removed.
     *
     * @param propertyName the name of the property to change.
     */
    void removeProperty(String propertyName);

    /**
     * Removes the property with the specified suffix.
     * A PropertyChangeEvent will be dispatched.
     * All properties with suffix (the part after the final period) propertyName will be removed.
     *
     * @param propertyName the name of the property to remove.
     */
    void removePropertyBySuffix(String propertyName);

    /**
     * Removes all account config for the given protocol.
     * <p>
     * Additionally, if removeReconnect is true, any reconnectplugin config
     * for the given protocol will also be deleted to ensure that we don't try
     * to reconnect to or report connection errors for deleted accounts.
     *
     * @param protocolName the name of the protocol for which all account
     *                     config will be deleted.
     * @param removeReconnect if true, all reconnectplugin config for the
     *                        given protocol will also be deleted.
     */
    void removeAccountConfigForProtocol(String protocolName,
                                        boolean removeReconnect);

    /**
     * Returns a <tt>java.util.List</tt> of <tt>String</tt>s containing all
     * property names.
     *
     * @return a <tt>java.util.List</tt>containing all property names
     */
    List<String> getAllPropertyNames();

    /**
     * Returns a <tt>java.util.List</tt> of <tt>String</tt>s containing the
     * all property names that have the specified prefix. Depending on the value
     * of the <tt>exactPrefixMatch</tt> parameter the method will (when false)
     * or will not (when exactPrefixMatch is true) include property names that
     * have prefixes longer than the specified <tt>prefix</tt> param.
     * <p>
     * Example:
     * <p>
     * Imagine a configuration service instance containing 2 properties only:<br>
     * <code>
     * net.java.sip.communicator.PROP1=value1<br>
     * net.java.sip.communicator.service.protocol.PROP1=value2
     * </code>
     * <p>
     * A call to this method with a prefix="net.java.sip.communicator" and
     * exactPrefixMatch=true would only return the first property -
     * net.java.sip.communicator.PROP1, whereas the same call with
     * exactPrefixMatch=false would return both properties as the second prefix
     * includes the requested prefix string.
     * <p>
     * @param prefix a String containing the prefix (the non dotted non-caps
     * part of a property name) that we're looking for.
     * @param exactPrefixMatch a boolean indicating whether the returned
     * property names should all have a prefix that is an exact match of the
     * the <tt>prefix</tt> param or whether properties with prefixes that
     * contain it but are longer than it are also accepted.
     * @return a <tt>java.util.List</tt>containing all property name String-s
     * matching the specified conditions.
     */
    List<String> getPropertyNamesByPrefix(String prefix,
                                          boolean exactPrefixMatch);

    /**
     * Returns a <tt>List</tt> of <tt>String</tt>s containing the property names
     * that have the specified suffix. A suffix is considered to be everything
     * after the last dot in the property name.
     * <p>
     * For example, imagine a configuration service instance containing two
     * properties only:
     * </p>
     * <code>
     * net.java.sip.communicator.PROP1=value1
     * net.java.sip.communicator.service.protocol.PROP1=value2
     * </code>
     * <p>
     * A call to this method with <tt>suffix</tt> equal to "PROP1" will return
     * both properties, whereas the call with <tt>suffix</tt> equal to
     * "communicator.PROP1" or "PROP2" will return an empty <tt>List</tt>. Thus,
     * if the <tt>suffix</tt> argument contains a dot, nothing will be found.
     * </p>
     *
     * @param suffix the suffix for the property names to be returned
     * @return a <tt>List</tt> of <tt>String</tt>s containing the property names
     * which contain the specified <tt>suffix</tt>
     */
    List<String> getPropertyNamesBySuffix(String suffix);

    /**
     * Returns the String value of the specified property and null in case no
     * property value was mapped against the specified propertyName, or in
     * case the returned property string had zero length or contained
     * whitespaces only.
     *
     * @param propertyName the name of the property that is being queried.
     * @return the result of calling the property's toString method and null in
     * case there was no value mapped against the specified
     * <tt>propertyName</tt>, or the returned string had zero length or
     * contained whitespaces only.
     */
    String getString(String propertyName);

    /**
     * Returns the String value of the specified property and null in case no
     * property value was mapped against the specified propertyName, or in
     * case the returned property string had zero length or contained
     * whitespaces only.
     *
     * @param propertyName the name of the property that is being queried.
     * @param defaultValue the value to be returned if the specified property
     * name is not associated with a value in this
     * <code>ConfigurationService</code>
     * @return the result of calling the property's toString method and
     * <code>defaultValue</code> in case there was no value mapped against
     * the specified <tt>propertyName</tt>, or the returned string had zero
     * length or contained whitespaces only.
     */
    String getString(String propertyName, String defaultValue);

    /**
     * Returns the Enum value of the specified property or a default value if
     * the config doesn't return a specified default value.
     *
     * @param type the type of enum to match against.
     * @param propertyName the name of the property that is being queried.
     * @param defaultValue the value to be returned if the specified property
     * name is not associated with a value in this
     * <code>ConfigurationService</code>
     * @return the result of matching the property against the enum and
     * <code>defaultValue</code> in case the value mapped against the
     * propertyName doesn't match any valid value in the enumeration.
     */
    <T extends Enum<T>> T getEnum(Class<T> type,
                                  String propertyName,
                                  T defaultValue);

    /**
     * Gets the value of a specific property as a boolean. If the specified
     * property name is associated with a value in this
     * <code>ConfigurationService</code>, the string representation of the value
     * is parsed into a boolean according to the rules of
     * {@link Boolean#parseBoolean(String)} . Otherwise,
     * <code>defaultValue</code> is returned.
     *
     * @param propertyName
     *            the name of the property to get the value of as a boolean
     * @param defaultValue
     *            the value to be returned if the specified property name is not
     *            associated with a value in this
     *            <code>ConfigurationService</code>
     * @return the value of the property with the specified name in this
     *         <code>ConfigurationService</code> as a boolean;
     *         <code>defaultValue</code> if the property with the specified name
     *         is not associated with a value in this
     *         <code>ConfigurationService</code>
     */
    boolean getBoolean(String propertyName, boolean defaultValue);

    /**
     * Gets the value of a specific property as a signed decimal integer. If the
     * specified property name is associated with a value in this
     * <tt>ConfigurationService</tt>, the string representation of the value is
     * parsed into a signed decimal integer according to the rules of
     * {@link Integer#parseInt(String)} . If parsing the value as a signed
     * decimal integer fails or there is no value associated with the specified
     * property name, <tt>defaultValue</tt> is returned.
     *
     * @param propertyName the name of the property to get the value of as a
     * signed decimal integer
     * @param defaultValue the value to be returned if parsing the value of the
     * specified property name as a signed decimal integer fails or there is no
     * value associated with the specified property name in this
     * <tt>ConfigurationService</tt>
     * @return the value of the property with the specified name in this
     * <tt>ConfigurationService</tt> as a signed decimal integer;
     * <tt>defaultValue</tt> if parsing the value of the specified property name
     * fails or no value is associated in this <tt>ConfigurationService</tt>
     * with the specified property name
     */
    int getInt(String propertyName, int defaultValue);

    /**
     * Gets the value of a specific property as a signed decimal long integer.
     * If the specified property name is associated with a value in this
     * <tt>ConfigurationService</tt>, the string representation of the value is
     * parsed into a signed decimal long integer according to the rules of
     * {@link Long#parseLong(String)} . If parsing the value as a signed
     * decimal long integer fails or there is no value associated with the
     * specified property name, <tt>defaultValue</tt> is returned.
     *
     * @param propertyName the name of the property to get the value of as a
     * signed decimal long integer
     * @param defaultValue the value to be returned if parsing the value of the
     * specified property name as a signed decimal long integer fails or there
     * is no value associated with the specified property name in this
     * <tt>ConfigurationService</tt>
     * @return the value of the property with the specified name in this
     * <tt>ConfigurationService</tt> as a signed decimal long integer;
     * <tt>defaultValue</tt> if parsing the value of the specified property name
     * fails or no value is associated in this <tt>ConfigurationService</tt>
     * with the specified property name
     */
    long getLong(String propertyName, long defaultValue);

    /**
     * Adds a PropertyChangeListener to the listener list. The listener is
     * registered for all properties in the current configuration.
     * <p>
     * @param listener the PropertyChangeListener to be added
     */
    void addPropertyChangeListener(PropertyChangeListener listener);

    /**
     * Removes a PropertyChangeListener from the listener list.
     * <p>
     * @param listener the PropertyChangeListener to be removed
     */
    void removePropertyChangeListener(PropertyChangeListener listener);

    /**
     * Adds a PropertyChangeListener to the listener list for a specific
     * property. In case a property with the specified name does not exist the
     * listener is still added and would only be taken into account from the
     * moment such a property is set by someone.
     * <p>
     * @param propertyName one of the property names listed above
     * @param listener the PropertyChangeListener to be added
     */
    void addPropertyChangeListener(String propertyName,
                                   PropertyChangeListener listener);

    /**
     * Removes a PropertyChangeListener from the listener list for a specific
     * property. This method should be used to remove PropertyChangeListeners
     * that were registered for a specific property. The method has no effect
     * when called for a listener that was not registered for that specific
     * property.
     * <p>
     *
     * @param propertyName a valid property name
     * @param listener the PropertyChangeListener to be removed
     */
    void removePropertyChangeListener(String propertyName,
                                      PropertyChangeListener listener);

    /**
     * Store the current set of properties back to the configuration file, at
     * most once per a preconfigured delay period. This method should be used
     * normally in preference to <tt>storeConfigurationNow</tt> as it prevents
     * us making hundreds of writes per second, for example when refreshing
     * config. The <tt>storeConfigurationNow</tt> method should only be used if
     * it is imperative that the write to file happens immediately.
     * <p>
     * The name of the configuration file is queried from
     * the system property net.java.sip.communicator.PROPERTIES_FILE_NAME, and
     * is set to sip-communicator.xml in case the property does not contain a
     * valid file name. The location might be one of three possibile, checked
     * in the following order: <br>
     * 1. The current directory. <br>
     * 2. The sip-communicator directory in the user.home
     *    ($HOME/.sip-communicator)
     * 3. A location in the classpath (such as the sip-communicator jar file).
     * <p>
     * In the last case the file is copied to the sip-communicator configuration
     * directory right after being extracted from the classpath location.
     *
     * @throws IOException in case storing the configuration failed.
     */
    void storeConfiguration()
        throws IOException;

    /**
     * Store the current set of properties back to the configuration file
     * immediately if there is a write pending.
     * Ordinarly, <tt>storeConfiguration</tt> should be used to write config to disk, as it writes to file at most
     * once per a preconfigured delay period, which prevents us making hundreds of writes per second. This function
     * should be called before quitting to make sure that any pending write happens immediately, without waiting
     * this delay. <b>It does nothing if there is no such write already pending.<b>
     *
     * @param Whether to delete the backup config file from disk (use if it may
     * contain sensitive data.
     */
    void storePendingConfigurationNow(boolean hardFlush);

    /**
     * Deletes the current configuration and reloads it from the configuration
     * file.  The
     * name of the configuration file is queried from the system property
     * net.java.sip.communicator.PROPERTIES_FILE_NAME, and is set to
     * sip-communicator.xml in case the property does not contain a valid file
     * name. The location might be one of three possibile, checked in the
     * following order: <br>
     * 1. The current directory. <br>
     * 2. The sip-communicator directory in the user.home
     *    ($HOME/.sip-communicator)
     * 3. A location in the classpath (such as the sip-communicator jar file).
     * <p>
     * In the last case the file is copied to the sip-communicator configuration
     * directory right after being extracted from the classpath location.
     * @throws IOException in case reading the configuration failes
     */
    void reloadConfiguration()
        throws IOException;

    /**
     * Returns the name of the directory where Jitsi is to store user
     * specific data such as configuration files, message and call history
     * as well as is bundle repository.
     *
     * @return the name of the directory where Jitsi is to store
     * user specific data such as configuration files, message and call history
     * as well as is bundle repository.
     */
    String getScHomeDirName();

    /**
     * Returns the location of the directory where Jitsi is to store
     * user specific data such as configuration files, message and call history
     * as well as is bundle repository.
     *
     * @return the location of the directory where Jitsi is to store
     * user specific data such as configuration files, message and call history
     * as well as is bundle repository.
     */
    String getScHomeDirLocation();

    /**
     * Find the global configuration.
     * @return  the global configuration.
     */
    ScopedConfigurationService getGlobalConfigurationService();
}
