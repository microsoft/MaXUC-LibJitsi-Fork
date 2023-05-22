/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
// Portions (c) Microsoft Corporation. All rights reserved.
package org.jitsi.impl.configuration;

import static org.jitsi.util.Hasher.logHasher;

import java.beans.*;
import java.io.*;
import java.util.*;

import javax.swing.SwingUtilities;

import org.jitsi.impl.configuration.xml.*;
import org.jitsi.service.configuration.*;
import org.jitsi.service.fileaccess.*;
import org.jitsi.util.*;
import org.jitsi.util.xml.*;

/**
 * A straightforward implementation of the <tt>ConfigurationService</tt> using
 * an XML or a .properties file for storing properties. Currently only
 * <tt>String</tt> properties are meaningfully saved (we should probably
 * consider how and whether we should take care of the rest).
 *
 * @author Emil Ivov
 * @author Damian Minkov
 * @author Lyubomir Marinov
 * @author Dmitri Melnikov
 */
public abstract class AbstractScopedConfigurationServiceImpl
    implements ScopedConfigurationService
{
    /**
     * The <tt>Logger</tt> used by this <tt>ConfigurationServiceImpl</tt>
     * instance for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(AbstractScopedConfigurationServiceImpl.class);

    /**
     * The name of the <tt>ConfigurationStore</tt> class to be used as the
     * default when no specific <tt>ConfigurationStore</tt> class is determined
     * as necessary.
     */
    private static final String DEFAULT_CONFIGURATION_STORE_CLASS_NAME
        = "net.java.sip.communicator.impl.configuration"
            + ".SQLiteConfigurationStore";

    /**
     * Name of the system file name property.
     */
    private static final String SYS_PROPS_FILE_NAME_PROPERTY
        = "net.java.sip.communicator.SYS_PROPS_FILE_NAME";

    /**
     * Name of the file containing default properties.
     */
    private static final String DEFAULT_PROPS_FILE_NAME
                                         = "jitsi-defaults.properties";

    /**
     * Name of the file containing overrides (possibly set by the deployer)
     * for any of the default properties.
     */
    private static final String DEFAULT_OVERRIDES_PROPS_FILE_NAME
                                             = "jitsi-default-overrides.properties";

    /**
     * Delay between successive writes to the config file.  Having a delay helps
     * prevent us spamming the disk.
     */
    private static final long CONFIG_WRITE_DELAY_MS = 250;

    /**
     * Timer that schedules writes to the config file, ensuring they happen no
     * more often than <tt>CONFIG_WRITE_DELAY_MS</tt>.
     */
    private Timer configDelayTimer;

    /**
     * <tt>true</tt> if a write has been scheduled, and will be made when the
     * <tt>configDelayTimer</tt> pops.
     */
    private boolean configWritePending;

    /**
     * Last time (<tt>System.currentTimeMillis</tt>) that config was written
     * to the config file.  Used to ensure writes are not made too often.
     */
    private long lastConfigWriteTime;

    /**
     * A reference to the currently used configuration file.
     */
    private File configurationFile = null;

    /**
     * Shutdown hook to ensure any pending config writes are made prior to
     * shutdown.
     */
    private Thread shutdownHook = new Thread("ConfigurationServiceImpl shutdown hook")
    {
        @Override
        public void run()
        {
            storePendingConfigurationNow(false);
        }
    };

    /**
     * A set of immutable properties deployed with the application during
     * install time. The properties in this file will be impossible to override
     * and attempts to do so will simply be ignored.
     * @see #defaultProperties
     */
    private Map<String, String> immutableDefaultProperties
                                                = new HashMap<>();

    /**
     * A set of properties deployed with the application during install time.
     * Contrary to the properties in {@link #immutableDefaultProperties} the
     * ones in this map can be overridden with call to the
     * <tt>setProperty()</tt> methods. Still, re-setting one of these properties
     * to <tt>null</tt> would cause for its initial value to be restored.
     */
    private Map<String, String> defaultProperties
                                                = new HashMap<>();

    /**
     * Our event dispatcher.
     */
    private final ChangeEventDispatcher changeEventDispatcher =
        new ChangeEventDispatcher(this);

    /**
     * The transaction object used to ensure that updates to configuration are
     * made atomically, and can be rolled back if errors occur.
     */
    private TransactionBasedFile transactionBasedFile;

    /**
     * The <code>ConfigurationStore</code> implementation which contains the
     * property name-value associations of this
     * <code>ConfigurationService</code> and performs their actual storing in
     * <code>configurationFile</code>.
     */
    private ConfigurationStore store;

    /**
     * Initialise this Config class
     */
    protected void init()
    {
        configDelayTimer = new Timer("Config writer delay timer");
        configWritePending = false;
        lastConfigWriteTime = 0;

        Runtime.getRuntime().addShutdownHook(shutdownHook);

        try
        {
            preloadSystemPropertyFiles();
            loadDefaultProperties();
            reloadConfigInternal(true);
        }
        catch (IOException ex)
        {
            logger.error("Failed to load the configuration file", ex);
        }
    }

    /**
     * Sets the property with the specified name to the specified value.
     * A PropertyChangeEvent will be dispatched.
     * <p>
     * @param propertyName the name of the property
     * @param property the object that we'd like to be come the new value of the
     * property.
     */
    @Override
    public void setProperty(String propertyName, Object property)
    {
        setProperty(propertyName, property, false);
    }

    /**
     * Sets the property with the specified name to the specified value.
     * A PropertyChangeEvent will be dispatched.
     * This method also allows the caller to specify whether or not the
     * specified property is a system one.
     * <p>
     * @param propertyName the name of the property to change.
     * @param property the new value of the specified property.
     * @param isSystem specifies whether or not the property being is a System
     *                 property and should be resolved against the system
     *                 property set. If the property has previously been
     *                 specified as system then this value is internally forced
     *                 to true.
     */
    @Override
    public void setProperty(String propertyName,
                            Object property,
                            boolean isSystem)
    {
        Object oldValue = getProperty(propertyName);

        // No exception was thrown - lets change the property and fire a
        // change event
        doSetProperty(propertyName, property, isSystem);

        try
        {
            storeConfiguration();
        }
        catch (IOException ex)
        {
            logger.error(
                "Failed to store configuration after a property change", ex);
        }

        if (changeEventDispatcher.hasPropertyChangeListeners(propertyName))
        {
            changeEventDispatcher.firePropertyChange(
                propertyName, oldValue, property);
        }
    }

    /*
     * Implements ConfigurationService#setProperties(Map). Optimizes the setting
     * of properties by performing a single saving of the property store to the
     * configuration file which is known to be slow because it involves
     * converting the whole store to a string representation and writing a file
     * to the disk.
     */
    @Override
    public void setProperties(Map<String, Object> properties)
    {
        // First check whether the changes are ok with everyone
        Map<String, Object> oldValues
            = new HashMap<>(properties.size());
        for (Map.Entry<String, Object> property : properties.entrySet())
        {
            String propertyName = property.getKey();
            Object oldValue = getProperty(propertyName);

            oldValues.put(propertyName, oldValue);
        }

        for (Map.Entry<String, Object> property : properties.entrySet())
            doSetProperty(property.getKey(), property.getValue(), false);

        try
        {
            storeConfiguration();
        }
        catch (IOException ex)
        {
            logger.error(
                "Failed to store configuration after property changes");
        }

        for (Map.Entry<String, Object> property : properties.entrySet())
        {
            String propertyName = property.getKey();

            if (changeEventDispatcher.hasPropertyChangeListeners(propertyName))
                changeEventDispatcher
                    .firePropertyChange(
                        propertyName,
                        oldValues.get(propertyName),
                        property.getValue());
        }
    }

    /**
     * Performs the actual setting of a property with a specific name to a
     * specific new value, storing into the configuration file and notifying
     * <code>PropertyChangeListener</code>s.
     *
     * @param propertyName
     *            the name of the property which is to be set to a specific
     *            value
     * @param property
     *            the value to be assigned to the property with the specified
     *            name
     * @param isSystem
     *            <tt>true</tt> if the property with the specified name is to be
     *            set as a system property; <tt>false</tt>, otherwise
     */
    private void doSetProperty(
        String propertyName, Object property, boolean isSystem)
    {
        // Once set system, a property remains system even if the user
        // specified something else.

        if (isSystemProperty(propertyName))
        {
            isSystem = true;
        }

        // Ignore requests to override immutable properties:
        if (immutableDefaultProperties.containsKey(propertyName))
        {
            return;
        }

        if (property == null)
        {
            store.removeProperty(propertyName);

            if (isSystem)
            {
                // We can't remove or nullset a sys prop so let's "empty" it.
                System.setProperty(propertyName, "");
            }
        }
        else
        {
            if (isSystem)
            {
                // In case this is a system property, we must only store it
                // In the System property set and keep only a ref locally.
                System.setProperty(propertyName, property.toString());
                store.setSystemProperty(propertyName);
            }
            else
            {
                store.setNonSystemProperty(propertyName, property);
            }
        }
    }

    @Override
    public void removeProperty(String propertyName)
    {
        List<String> childPropertyNames = getPropertyNamesByPrefix(propertyName, false);
        removeProperties(propertyName, childPropertyNames);
    }

    @Override
    public void removePropertyBySuffix(String propertySuffix)
    {
        List<String> matchingPropertyNames = getPropertyNamesBySuffix(propertySuffix);
        List<String> emptyList = new ArrayList<>();

        // Call directly rather than allowing removeProperties to
        // iterate as matching by suffix can only go one level deep
        // and we can skip looking for children.
        for (String matchingPropertyName : matchingPropertyNames)
        {
            removeProperties(matchingPropertyName, emptyList);
        }
    }

    private void removeProperties(String propertyName, List<String> childPropertyNames)
    {
        // Remove all properties
        for (String pName : childPropertyNames)
        {
            removeProperty(pName);
        }

        Object oldValue = getProperty(propertyName);

        // No exception was thrown - lets change the property and fire a
        // Change event
        store.removeProperty(propertyName);

        if (changeEventDispatcher.hasPropertyChangeListeners(propertyName))
            changeEventDispatcher.firePropertyChange(
                propertyName, oldValue, null);

        try
        {
            storeConfiguration();
        }
        catch (IOException ex)
        {
            logger.error("Failed to store configuration after "
                         + "a property change");
        }
    }

    @Override
    public void removeAccountConfigForProtocol(String protocolName,
                                               boolean removeReconnect)
    {
        logger.info("Removing account config for " + protocolName);

        Set<String> storedUIDs = new HashSet<>();
        String protocolPrefix =
            "net.java.sip.communicator.impl.protocol." + protocolName;
        List<String> protocolConfigStrings =
            getPropertyNamesByPrefix(protocolPrefix, true);

        // If we didn't find any config strings, it's probably because they are
        // stored with the protocol name in lower case, so try that instead.
        if (protocolConfigStrings.isEmpty())
        {
            protocolName = protocolName.toLowerCase();
            protocolPrefix =
                "net.java.sip.communicator.impl.protocol." + protocolName;
            protocolConfigStrings = getPropertyNamesByPrefix(protocolPrefix, true);
        }

        // First get the UIDs of all accounts that are stored in config for
        // this protocol.
        for (String protocolConfigString : protocolConfigStrings)
        {
            if (protocolConfigString.startsWith(protocolPrefix + ".acc"))
            {
                // UIDs start with ".acc" so add this to the list of UIDs
                String uid = getString(protocolConfigString + ".ACCOUNT_UID");

                if ((uid != null) && (uid.trim() != ""))
                {
                    storedUIDs.add(uid);
                }
            }
        }

        for (String storedUID : storedUIDs)
        {
            // Next remove any GUI account config for the stored UIDs for this
            // protocol.
            List<String> accountConfigStrings = getPropertyNamesByPrefix(
                "net.java.sip.communicator.impl.gui.accounts", true);

            for (String accountConfigString : accountConfigStrings)
            {
                String accountUID = getString(accountConfigString);

                if (storedUID.equals(accountUID))
                {
                    logger.debug("Removing account config for " + logHasher(accountUID));
                    removeProperty(accountConfigString);
                    break;
                }
            }

            if (removeReconnect)
            {
                // If we've been asked to remove the reconnectplugin config for
                // this protocol so that we don't report reconnection failures for,
                // or try to reconnect to the removed account, do so now.
                logger.debug("Remove reconnectplugin config for " + logHasher(storedUID));
                String reconnectPrefix =
                    "net.java.sip.communicator.plugin.reconnectplugin." +
                        "ATLEAST_ONE_SUCCESSFUL_CONNECTION.";
                removeProperty(reconnectPrefix + storedUID);
            }
        }

        // Finally, remove all account config for the protocol, making sure we
        // append ".acc" to the prefix for all protocols other than jabber,
        // otherwise we will delete unnecessary config for some protocols and
        // not enough config for jabber.
        protocolPrefix = "jabber".equalsIgnoreCase(protocolName) ?
            protocolPrefix : (protocolPrefix + ".acc");
        logger.debug("Removing all config with prefix " + protocolPrefix);
        removeProperty(protocolPrefix);
    }

    /**
     * Returns the value of the property with the specified name or null if no
     * such property exists.
     *
     * @param propertyName the name of the property that is being queried.
     * @return the value of the property with the specified name.
     */
    @Override
    public Object getProperty(String propertyName)
    {
        Object result = immutableDefaultProperties.get(propertyName);

        if (result != null)
        {
            return result;
        }

        result = store.getProperty(propertyName);

        if (result != null)
        {
            return result;
        }

        return defaultProperties.get(propertyName);
    }

    /**
     * Returns a <tt>java.util.List</tt> of <tt>String</tt>s containing all
     * property names.
     *
     * @return a <tt>java.util.List</tt>containing all property names
     */
    @Override
    public List<String> getAllPropertyNames()
    {
        return Arrays.asList(store.getPropertyNames());
    }

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
     * In addition to stored properties this method will also search the default
     * mutable and immutable properties.
     *
     * @param prefix a String containing the prefix (the non dotted non-caps
     * part of a property name) that we're looking for.
     * @param exactPrefixMatch a boolean indicating whether the returned
     * property names should all have a prefix that is an exact match of the
     * the <tt>prefix</tt> param or whether properties with prefixes that
     * contain it but are longer than it are also accepted.
     * @return a <tt>java.util.List</tt>containing all property name String-s
     * matching the specified conditions.
     */
    @Override
    public List<String> getPropertyNamesByPrefix(String prefix,
            boolean exactPrefixMatch)
    {
        HashSet<String> resultKeySet = new HashSet<>();

        // First fill in the names from the immutable default property set
        Set<String> propertyNameSet;
        String[] namesArray;

        if (immutableDefaultProperties.size() > 0)
        {
            propertyNameSet = immutableDefaultProperties.keySet();

            namesArray
                = propertyNameSet.toArray( new String[propertyNameSet.size()] );

            getPropertyNamesByPrefix(prefix,
                                     exactPrefixMatch,
                                     namesArray,
                                     resultKeySet);
        }

        // Now get property names from the current store.
        getPropertyNamesByPrefix(prefix,
                                 exactPrefixMatch,
                                 store.getPropertyNames(),
                                 resultKeySet);

        // Finally, get property names from mutable default property set.
        if (defaultProperties.size() > 0)
        {
            propertyNameSet = defaultProperties.keySet();

            namesArray
                = propertyNameSet.toArray( new String[propertyNameSet.size()] );

            getPropertyNamesByPrefix(prefix,
                                     exactPrefixMatch,
                                     namesArray,
                                     resultKeySet);
        }

        return new ArrayList<>(resultKeySet);
    }

    /**
     * Updates the specified <tt>String</tt> <tt>resulSet</tt> to contain all
     * property names in the <tt>names</tt> array that partially or completely
     * match the specified prefix. Depending on the value of the
     * <tt>exactPrefixMatch</tt> parameter the method will (when false)
     * or will not (when exactPrefixMatch is true) include property names that
     * have prefixes longer than the specified <tt>prefix</tt> param.
     *
     * @param prefix a String containing the prefix (the non dotted non-caps
     * part of a property name) that we're looking for.
     * @param exactPrefixMatch a boolean indicating whether the returned
     * property names should all have a prefix that is an exact match of the
     * the <tt>prefix</tt> param or whether properties with prefixes that
     * contain it but are longer than it are also accepted.
     * @param names the list of names that we'd like to search.
     *
     * @return a reference to the updated result set.
     */
    private Set<String> getPropertyNamesByPrefix(
                            String      prefix,
                            boolean     exactPrefixMatch,
                            String[]    names,
                            Set<String> resultSet)
    {
        for (String key : names)
        {
            int ix = key.lastIndexOf('.');

            if (ix == -1)
            {
                continue;
            }

            String keyPrefix = key.substring(0, ix);

            if (exactPrefixMatch)
            {
                if (prefix.equals(keyPrefix))
                {
                    resultSet.add(key);
                }
            }
            else
            {
                if (keyPrefix.startsWith(prefix))
                {
                    resultSet.add(key);
                }
            }
        }

        return resultSet;
    }

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
    @Override
    public List<String> getPropertyNamesBySuffix(String suffix)
    {
        List<String> resultKeySet = new LinkedList<>();

        for (String key : store.getPropertyNames())
        {
            int ix = key.lastIndexOf('.');

            if ((ix != -1) && suffix.equals(key.substring(ix+1)))
                resultKeySet.add(key);
        }
        return resultKeySet;
    }

    /**
     * Adds a PropertyChangeListener to the listener list.
     *
     * @param listener the PropertyChangeListener to be added
     */
    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener)
    {
        changeEventDispatcher.addPropertyChangeListener(listener);
    }

    /**
     * Removes a PropertyChangeListener from the listener list.
     *
     * @param listener the PropertyChangeListener to be removed
     */
    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener)
    {
        changeEventDispatcher.removePropertyChangeListener(listener);
    }

    /**
     * Adds a PropertyChangeListener to the listener list for a specific
     * property.
     *
     * @param propertyName one of the property names listed above
     * @param listener the PropertyChangeListener to be added
     */
    @Override
    public void addPropertyChangeListener(String propertyName,
                                          PropertyChangeListener listener)
    {
        changeEventDispatcher.
            addPropertyChangeListener(propertyName, listener);
    }

    /**
     * Removes a PropertyChangeListener from the listener list for a specific
     * property.
     *
     * @param propertyName a valid property name
     * @param listener the PropertyChangeListener to be removed
     */
    @Override
    public void removePropertyChangeListener(String propertyName,
                                             PropertyChangeListener listener)
    {
        changeEventDispatcher.
            removePropertyChangeListener(propertyName, listener);
    }

    /*
     * Implements ConfigurationService#reloadConfiguration().
     */
    @Override
    public synchronized void reloadConfiguration()
        throws IOException
    {
        if (configWritePending)
        {
            logger.debug("Write pending config change prior to reload");
            storeConfigurationInternal(false);
            configWritePending = false;
        }

        reloadConfigInternal(false);
    }

    private synchronized void reloadConfigInternal(boolean startOfDay)
        throws IOException
    {
        this.configurationFile = null;
        this.transactionBasedFile = null;

        File file = getConfigurationFile();

        try
        {
            store.reloadConfiguration(file);

            if (startOfDay)
            {
                // Check we have some config, and recover if not.  We've seen
                // cases where the config file existed but contained only NUL
                // bytes - protect against that here.
                // This used recover the backup when there are less than 10
                // properties. However, this caused some issues when there is
                // genuinely only a few properties (ie. user is logging in for
                // the first time) and using the backup causes issues as it's
                // in-accurate.
                if (store.numProperties() < 3)
                {
                    // This doesn't look right at all
                    logger.warn("Only have " + store.numProperties() +
                                " properties in config file - attempt recovery");
                    TransactionBasedFile.attemptRecovery(file);
                    reloadConfigInternal(false);
                    // After the recursive call above, don't run through
                    // the rest of the function twice.  (Not that there
                    // is any extra function right now, but let's be safe)
                    return;
                }
            }
        }
        catch (XMLException xmle)
        {
            throw new IOException(xmle);
        }
    }

    /*
     * Implements ConfigurationService#storeConfiguration().
     *
     * Includes a delay to ensure we don't write too often to the config file -
     * we'll make at most 1 write per CONFIG_WRITE_DELAY ms.  This prevents us
     * making hundreds of writes per second, for example when refreshing config.
     *
     * The expected behavior of this function is:
     * - call 1 : no recent write, so config (1) is written to file
     * - call 2 : written recently, so a write is scheduled after a delay
     * - calls 3-N : a write is already scheduled, so do nothing
     * - timer pops, and config is written to file (2-N)
     * - next call behaves as for (1)
     *
     * This means that if a single call is made we normally write the change
     * immediately.  However if multiple calls are made at once, we'll write the
     * first property, wait for the delay, and only then write the rest (rather
     * than waiting and then writing all in one go).  But that's ok; we still
     * won't be writing to file too rapidly.
     */
    @Override
    public synchronized void storeConfiguration()
        throws IOException
    {
        if (configWritePending)
        {
            logger.debug("Config write already pending - do nothing");
        }
        else
        {
            // Write to config if we haven't for a while and this isn't the UI thread; otherwise, schedule the write for later.
            long msSinceLastWrite = System.currentTimeMillis() - lastConfigWriteTime;

            if ((msSinceLastWrite > CONFIG_WRITE_DELAY_MS) && !SwingUtilities.isEventDispatchThread())
            {
                storeConfigurationInternal(false);
            }
            else
            {
                // Wait until at least CONFIG_WRITE_DELAY_MS has passed before writing.
                // If we're using a different thread to avoid writing to file on the UI thread, make sure delay isn't negative.
                long delay = Math.max(0, CONFIG_WRITE_DELAY_MS - msSinceLastWrite);
                logger.debug("Schedule config write in " + delay + "ms");
                configWritePending = true;
                configDelayTimer.schedule(new TimerTask(){
                        @Override
                        public void run()
                        {
                            logger.debug("Config write timer popped");
                            synchronized (AbstractScopedConfigurationServiceImpl.this)
                            {
                                storePendingConfigurationNow(false);
                            }
                        }
                                          },
                    delay);
            }
        }
    }

    @Override
    public synchronized void storePendingConfigurationNow(boolean hardFlush)
    {
        if (configWritePending)
        {
            logger.debug("Write pending config change immediately");
            try
            {
                storeConfigurationInternal(hardFlush);
                configWritePending = false;
            }
            catch (IOException e)
            {
                logger.error("Failed to store configuration", e);
            }
        }
    }

    /**
     * Actually store the config.
     * @param hardFlush Whether to delete the backup config file from disk (use if it may
     * contain sensitive data.
     * @throws IOException
     */
    private synchronized void storeConfigurationInternal(boolean hardFlush) throws IOException
    {
        logger.debug("Storing configuration to sip-communicator.properties file in user space.");

        // If the configuration file is forcibly considered read-only, do not
        // write it.
        String readOnly
            = System.getProperty(PNAME_CONFIGURATION_FILE_IS_READ_ONLY);

        if ((readOnly != null) && Boolean.parseBoolean(readOnly))
        {
            return;
        }

        Throwable exception = null;
        getConfigurationFile();

        try
        {
            transactionBasedFile.beginTransaction();

            try (OutputStream stream = transactionBasedFile.getOutputStream())
            {
                store.storeConfiguration(stream);
            }
        }
        catch (IllegalStateException | IOException isex)
        {
            exception = isex;
        }

        if (exception != null)
        {
            logger.error("Can't write data in the configuration file",
                         exception);
            try
            {
                transactionBasedFile.abortTransaction();
            }
            catch (IllegalStateException isex)
            {
                logger.error("Failed to roll back configuration file", isex);
            }
        }
        else
        {
            // Only set this if the write was successful - otherwise we should
            // retry sooner.
            logger.debug("Committing transaction");
            transactionBasedFile.commitTransaction(hardFlush);
            lastConfigWriteTime = System.currentTimeMillis();
            logger.debug("Finished storing configuration.");
        }
    }

    /**
     * Use with caution! Returns the name of the configuration file currently
     * used. Placed in HomeDirLocation/HomeDirName
     * {@link #getScHomeDirLocation()}
     * {@link #getScHomeDirName()}
     * @return  the name of the configuration file currently used.
     */
    @Override
    public String getConfigurationFilename()
    {
        try
        {
            File file =  getConfigurationFile();
            if (file != null)
            {
                return file.getName();
            }

        }
        catch(IOException ex)
        {
            logger.error("Error loading configuration file", ex);
        }

        return null;
    }

    /**
     * Returns the configuration file currently used by the implementation.
     * If there is no such file or this is the first time we reference it
     * a new one is created.
     * @return the configuration File currently used by the implementation.
     */
    private File getConfigurationFile()
        throws IOException
    {
        if (configurationFile == null)
        {
            logger.debug("Configuration file not set");

            createConfigurationFile();

            // Make sure that the properties SC_HOME_DIR_LOCATION and
            // SC_HOME_DIR_NAME are available in the store of this instance so
            // that users don't have to ask the system properties again.
            getScHomeDirLocation();
            getScHomeDirName();
        }

        if (transactionBasedFile == null)
        {
            transactionBasedFile = new TransactionBasedFile(configurationFile);
        }

        return configurationFile;
    }

    /**
     * Determines the name and the format of the configuration file to be used
     * and initializes the {@link #configurationFile} and {@link #store} fields
     * of this instance.
     */
    private void createConfigurationFile()
        throws IOException
    {
        logger.debug("Creating configuration file");

        // Choose the format of the configuration file so that the
        // performance-savvy properties format is used whenever possible and
        // only go with the slow and fat XML format when necessary.
        File configurationFile = getConfigurationFile("xml", false);

        if (configurationFile == null)
        {
            // It's strange that there's no configuration file name but let it
            // play out as it did when the configuration file was in XML format.
            logger.debug("No configuration file - defaulting to XML");

            setConfigurationStore(XMLConfigurationStore.class);
        }
        else
        {
            // Figure out the format of the configuration file by looking at its
            // extension.
            String name = configurationFile.getName();
            int extensionBeginIndex = name.lastIndexOf('.');
            String extension
                = (extensionBeginIndex > -1)
                        ? name.substring(extensionBeginIndex)
                        : null;

            if (".properties".equalsIgnoreCase(extension))
            {
                // Obviously, a file with the .properties extension is in the
                // properties format. Since there's no file with the .xml extension,
                // the case is simple.
                logger.debug(".properties configuration file");

                this.configurationFile = configurationFile;
                if (!(this.store instanceof PropertyConfigurationStore))
                    this.store = new PropertyConfigurationStore();
            }
            else
            {
                // But if we're told that the configuration file name is with
                // the .xml extension, we may also have a .properties file or
                // the .xml extension may be only the default and not forced on
                // us so it may be fine to create a .properties file and use the
                // properties format anyway.
                File newConfigurationFile
                    = new File(
                            configurationFile.getParentFile(),
                            ((extensionBeginIndex > -1)
                                    ? name.substring(0, extensionBeginIndex)
                                    : name)
                                + ".properties");

                if (newConfigurationFile.exists())
                {
                    // If there's an actual file with the .properties extension,
                    // then we've previously migrated the configuration from the XML
                    // format to the properties format. We may have failed to delete
                    // the migrated .xml file but it's fine because the .properties
                    // file is there to signal that we have to use it instead of the
                    // .xml file.
                    logger.debug("Using existing.properties configuration file");

                    this.configurationFile = newConfigurationFile;
                    if (!(this.store instanceof PropertyConfigurationStore))
                    {
                        this.store = new PropertyConfigurationStore();
                    }
                }

                else if (getSystemProperty(PNAME_CONFIGURATION_FILE_NAME) == null)
                {
                    // Otherwise, the lack of an existing .properties file doesn't
                    // help us much and we have the .xml extension for the file name
                    // so we have to determine whether it's just the default or it's
                    // been forced on us.
                    Class<? extends ConfigurationStore>
                        defaultConfigurationStoreClass
                            = getDefaultConfigurationStoreClass();

                    if (configurationFile.exists())
                    {
                        // The .xml is not forced on us so we allow ourselves to not
                        // obey the default and use the properties format. If a
                        // configuration file in the XML format exists already, we
                        // have to migrate it to the properties format.
                        logger.debug("Converting .xml configuration file");

                        ConfigurationStore xmlStore
                            = new XMLConfigurationStore();
                        try
                        {
                            xmlStore.reloadConfiguration(configurationFile);
                        }
                        catch (XMLException xmlex)
                        {
                            IOException ioex = new IOException(xmlex);

                            logger.debug("Failed to load configuration", xmlex);

                            throw ioex;
                        }

                        setConfigurationStore(defaultConfigurationStoreClass);
                        if (this.store != null)
                        {
                            copy(xmlStore, this.store);
                        }

                        Throwable exception = null;
                        try
                        {
                            storeConfiguration();
                        }
                        catch (IllegalStateException | IOException isex)
                        {
                            exception = isex;
                        }
                        if (exception == null)
                        {
                            logger.debug("Deleting XML configuration file");

                            configurationFile.delete();
                        }
                        else
                        {
                            logger.debug("Error converting XML: ", exception);

                            this.configurationFile = configurationFile;
                            this.store = xmlStore;
                        }
                    }
                    else
                    {
                        logger.debug("Setting configuration to: " + defaultConfigurationStoreClass);

                        setConfigurationStore(defaultConfigurationStoreClass);
                    }
                }
                else
                {
                    // The .xml extension is forced on us so we have to assume
                    // that whoever forced it knows what she wants to get so we
                    // have to obey and use the XML format.
                    logger.debug("Forced to use .xml configuration file.");

                    this.configurationFile =
                            configurationFile.exists()
                                ? configurationFile
                                : getConfigurationFile("xml", true);
                    if (!(this.store instanceof XMLConfigurationStore))
                    {
                        this.store = new XMLConfigurationStore();
                    }
                }
            }
        }
    }

    /**
     * Returns the location of the directory where SIP Communicator is to store
     * user specific data such as configuration files, message and call history
     * as well as is bundle repository.
     *
     * @return the location of the directory where SIP Communicator is to store
     * user specific data such as configuration files, message and call history
     * as well as is bundle repository.
     */
    @Override
    public String getScHomeDirLocation()
    {
        // First let's check whether we already have the name of the directory
        // set as a configuration property
        String scHomeDirLocation = null;

        if (store != null)
        {
            scHomeDirLocation = getString(PNAME_SC_HOME_DIR_LOCATION);
        }

        if (scHomeDirLocation == null)
        {
            // No luck, check whether user has specified a custom name in the
            // system properties
            scHomeDirLocation
                = getSystemProperty(PNAME_SC_HOME_DIR_LOCATION);

            if (scHomeDirLocation == null)
            {
                scHomeDirLocation = getSystemProperty("user.home");
            }

            // Now save all this as a configuration property so that we don't
            // have to look for it in the sys props next time and so that it is
            // available for other bundles to consult.
            if (store != null)
            {
                store.setNonSystemProperty(PNAME_SC_HOME_DIR_LOCATION,
                                           scHomeDirLocation);
            }
        }

        return scHomeDirLocation;
    }

    /**
     * Returns the name of the directory where SIP Communicator is to store user
     * specific data such as configuration files, message and call history
     * as well as is bundle repository.
     *
     * @return the name of the directory where SIP Communicator is to store
     * user specific data such as configuration files, message and call history
     * as well as is bundle repository.
     */
    @Override
    public String getScHomeDirName()
    {
        // First let's check whether we already have the name of the directory
        // set as a configuration property
        String scHomeDirName = null;

        if (store != null)
        {
            scHomeDirName = getString(PNAME_SC_HOME_DIR_NAME);
        }

        if (scHomeDirName == null)
        {
            // No luck, check whether user has specified a custom name in the
            // system properties
            scHomeDirName
                = getSystemProperty(PNAME_SC_HOME_DIR_NAME);

            if (scHomeDirName == null)
            {
                scHomeDirName = ".sip-communicator";
            }

            // Now save all this as a configuration property so that we don't
            // have to look for it in the sys props next time and so that it is
            // available for other bundles to consult.
            if (store != null)
            {
                store.setNonSystemProperty(PNAME_SC_HOME_DIR_NAME, scHomeDirName);
            }
        }

        return scHomeDirName;
    }

    /**
     * Returns a reference to the configuration file that the service should
     * load. The method would try to load a file with the name
     * sip-communicator.xml unless a different one is specified in the system
     * property net.java.sip.communicator.PROPERTIES_FILE_NAME . The method
     * would first try to load the file from the current directory if it exists
     * this is not the case a load would be attempted from the
     * $HOME/.sip-communicator directory. In case it was not found there either
     * we'll look for it in all locations currently present in the $CLASSPATH.
     * In case we find it in there we will copy it to the
     * $HOME/.sip-communicator directory in case it was in a jar archive and
     * return the reference to the newly created file. In case the file is to be
     * found nowhere - a new empty file in the user home directory and returns a
     * link to that one.
     *
     * @param extension
     *            the extension of the file name of the configuration file. The
     *            specified extension may not be taken into account if the
     *            configuration file name is forced through a system property.
     * @param create
     *            <tt>true</tt> to create the configuration file with the
     *            determined file name if it does not exist; <tt>false</tt> to
     *            only figure out the file name of the configuration file
     *            without creating it
     * @return the configuration file currently used by the implementation.
     */
    private File getConfigurationFile(String extension, boolean create)
        throws IOException
    {
        logger.debug("Get configuration file with extension: " + extension +
                     " and create: " + create);

        // See whether we have a user specified name for the conf file
        String pFileName = getSystemProperty(PNAME_CONFIGURATION_FILE_NAME);
        if (pFileName == null)
        {
            pFileName = "sip-communicator." + extension;
        }

        // Try to open the file in current directory
        File configFileInCurrentDir = new File(pFileName);
        if (configFileInCurrentDir.exists())
        {
            logger.debug("Using config file in current directory.");

            return configFileInCurrentDir;
        }

        // We didn't find it in ".", try the SIP Communicator home directory
        // first check whether a custom SC home directory is specified

        File configDir = new File(getScHomeDirLocation() +
                                  File.separator +
                                  getScHomeDirName());
        File configFileInUserHomeDir = new File(configDir, pFileName);

        // We've seen files mysteriously go missing, particularly on laptops
        // not shutting down cleanly.  In at least some of those cases the
        // unclean shutdown shows the last shutdown attempting to commit the
        // transaction, but never returning.
        //
        // So let's take a look to see if this file actually exists.  If it
        // doesn't that is very interesting, and we should take a closer look.
        if (!configFileInUserHomeDir.exists() ||
             configFileInUserHomeDir.length() == 0)
        {
            logger.debug("No config file, attempting recovery");
            configFileInUserHomeDir =
                    TransactionBasedFile.attemptRecovery(configFileInUserHomeDir);
        }

        if (configFileInUserHomeDir.exists())
        {
            logger.debug("Using config file in $HOME/.sip-communicator, size=" + configFileInUserHomeDir.length());

            return configFileInUserHomeDir;
        }

        // If we are in a jar - copy config file from jar to user home.
        InputStream in
            = getClass().getClassLoader().getResourceAsStream(pFileName);

        // Return an empty file if there wasn't any in the jar
        // null check report from John J. Barton - IBM
        if (in == null)
        {
            if (create)
            {
                configDir.mkdirs();
                configFileInUserHomeDir.createNewFile();

                logger.debug("Created an empty file in $HOME");
            }
            else
            {
                logger.debug("Returning config file located in $HOME");
            }

            return configFileInUserHomeDir;
        }

            logger.debug("Copying config file from JAR into config file located in $HOME");

        configDir.mkdirs();
        try
        {
            copy(in, configFileInUserHomeDir);
        }
        finally
        {
            try
            {
                in.close();
            }
            catch (IOException ioex)
            {
                logger.debug("Ignoring exception attempting to copy config file:", ioex);
            }
        }
        return configFileInUserHomeDir;
    }

    /**
     * Gets the <tt>ConfigurationStore</tt> <tt>Class</tt> to be used as the
     * default when no specific <tt>ConfigurationStore</tt> <tt>Class</tt> is
     * determined as necessary.
     *
     * @return the <tt>ConfigurationStore</tt> <tt>Class</tt> to be used as the
     * default when no specific <tt>ConfigurationStore</tt> <tt>Class</tt> is
     * determined as necessary
     */
    @SuppressWarnings("unchecked")
    private static Class<? extends ConfigurationStore>
        getDefaultConfigurationStoreClass()
    {
        Class<? extends ConfigurationStore> defaultConfigurationStoreClass
            = null;

        if (DEFAULT_CONFIGURATION_STORE_CLASS_NAME != null)
        {
            Class<?> clazz = null;

            try
            {
                clazz = Class.forName(DEFAULT_CONFIGURATION_STORE_CLASS_NAME);
            }
            catch (ClassNotFoundException cnfe)
            {
            }
            if ((clazz != null)
                    && ConfigurationStore.class.isAssignableFrom(clazz))
            {
                defaultConfigurationStoreClass
                        = (Class<? extends ConfigurationStore>) clazz;
            }
        }
        if (defaultConfigurationStoreClass == null)
        {
            defaultConfigurationStoreClass = PropertyConfigurationStore.class;
        }

        return defaultConfigurationStoreClass;
    }

    private static void copy(ConfigurationStore src, ConfigurationStore dest)
    {
        for (String name : src.getPropertyNames())
        {
            if (src.isSystemProperty(name))
            {
                dest.setSystemProperty(name);
            }
            else
            {
                dest.setNonSystemProperty(name, src.getProperty(name));
            }
        }

    }

    /**
     * Copies the contents of a specific <code>InputStream</code> as bytes into
     * a specific output <code>File</code>.
     *
     * @param inputStream
     *            the <code>InputStream</code> the contents of which is to be
     *            output in the specified <code>File</code>
     * @param outputFile
     *            the <code>File</code> to write the contents of the specified
     *            <code>InputStream</code> into
     * @throws IOException
     */
    private static void copy(InputStream inputStream, File outputFile)
        throws IOException
    {

        try (OutputStream outputStream = new FileOutputStream(outputFile))
        {
            byte[] bytes = new byte[4 * 1024];
            int bytesRead;

            while ((bytesRead = inputStream.read(bytes)) != -1)
            {
                outputStream.write(bytes, 0, bytesRead);
            }
        }
    }

    /**
     * Returns the value of the specified java system property. In case the
     * value was a zero length String or one that only contained whitespaces,
     * null is returned. This method is for internal use only. Users of the
     * configuration service are to use the getProperty() or getString() methods
     * which would automatically determine whether a property is system or not.
     * @param propertyName the name of the property whose value we need.
     * @return the value of the property with name propertyName or null if
     * the value had length 0 or only contained spaces tabs or new lines.
     */
    private static String getSystemProperty(String propertyName)
    {
        String retval = System.getProperty(propertyName);
        if ((retval != null) && (retval.trim().length() == 0))
        {
            retval = null;
        }
        return retval;
    }

    /**
     * Returns the String value of the specified property (minus all
     * encompasssing whitespaces)and null in case no property value was mapped
     * against the specified propertyName, or in case the returned property
     * string had zero length or contained whitespaces only.
     *
     * @param propertyName the name of the property that is being queried.
     * @return the result of calling the property's toString method and null in
     * case there was no vlaue mapped against the specified
     * <tt>propertyName</tt>, or the returned string had zero length or
     * contained whitespaces only.
     */
    @Override
    public String getString(String propertyName)
    {
        Object propValue = getProperty(propertyName);
        if (propValue == null)
        {
            return null;
        }

        String propStrValue = propValue.toString().trim();

        return (propStrValue.length() > 0) ? propStrValue : null;
    }

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
    @Override
    public String getString(String propertyName, String defaultValue)
    {
        String value = getString(propertyName);
        return value != null ? value : defaultValue;
    }

    /*
     * Implements ConfigurationService#getBoolean(String, boolean).
     */
    @Override
    public boolean getBoolean(String propertyName, boolean defaultValue)
    {
        String stringValue = getString(propertyName);

        return (stringValue == null) ? defaultValue :
                                       Boolean.parseBoolean(stringValue);
    }

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
    @Override
    public int getInt(String propertyName, int defaultValue)
    {
        String stringValue = getString(propertyName);
        int intValue = defaultValue;

        if ((stringValue != null) && (stringValue.length() > 0))
        {
            try
            {
                intValue = Integer.parseInt(stringValue);
            }
            catch (NumberFormatException ex)
            {
                logger.error(propertyName
                             + " does not appear to be an integer. " + "Defaulting to "
                             + defaultValue + ".", ex);
            }
        }
        return intValue;
    }

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
    @Override
    public long getLong(String propertyName, long defaultValue)
    {
        String stringValue = getString(propertyName);
        long longValue = defaultValue;

        if ((stringValue != null) && (stringValue.length() > 0))
        {
            try
            {
                longValue = Long.parseLong(stringValue);
            }
            catch (NumberFormatException ex)
            {
                logger.error(propertyName +
                             " does not appear to be a longinteger. " +
                             "Defaulting to " + defaultValue + ".", ex);
            }
        }
        return longValue;
    }

    /**
     * Determines whether the property with the specified
     * <tt>propertyName</tt> has been previously declared as System
     *
     * @param propertyName the name of the property to verify
     * @return true if someone at some point specified that property to be
     * system. (This could have been either through a call to
     * setProperty(string, true)) or by setting the system attribute in the
     * xml conf file to true.
     */
    private boolean isSystemProperty(String propertyName)
    {
        return store.isSystemProperty(propertyName);
    }

    /**
     * Deletes the configuration file currently used by this implementation.
     */
    @Override
    public void purgeStoredConfiguration()
    {
        logger.warn("Purging stored configuration! Stack: ", new Throwable("Call stack:"));
        if (configurationFile != null)
        {
            configurationFile.delete();
            configurationFile = null;
            transactionBasedFile = null;
        }
        if (store != null)
        {
            for (String name : store.getPropertyNames())
            {
                store.removeProperty(name);
            }
        }
    }

    /**
     * The method scans the contents of the SYS_PROPS_FILE_NAME_PROPERTY where
     * it expects to find a comma separated list of names of files that should
     * be loaded as system properties. The method then parses these files and
     * loads their contents as system properties. All such files have to be in
     * a location that's in the classpath.
     */
    private void preloadSystemPropertyFiles()
    {
        String propertyFilesListStr
            = System.getProperty( SYS_PROPS_FILE_NAME_PROPERTY );

        if (propertyFilesListStr == null || propertyFilesListStr.trim().length() == 0)
        {
            return;
        }

        StringTokenizer tokenizer
            = new StringTokenizer(propertyFilesListStr, ";,", false);

        while( tokenizer.hasMoreTokens())
        {
            String fileName = tokenizer.nextToken();
            try
            {
                fileName = fileName.trim();

                Properties fileProps = new Properties();

                fileProps.load(ClassLoader.getSystemResourceAsStream(fileName));

                // Now set all of this file's properties as system properties
                for (Map.Entry<Object, Object> entry : fileProps.entrySet())
                    System.setProperty((String) entry.getKey(), (String) entry
                        .getValue());
            }
            catch (Exception ex)
            {
                // This is an insignificant method that should never affect
                // the rest of the application so we'll afford ourselves to
                // kind of silence all possible exceptions (which would most
                // often be IOExceptions). We will however log them in case
                // anyone would be interested.
                logger.error("Failed to load property file.", ex);
            }
        }
    }

    /**
     * Specifies the configuration store that this instance of the configuration
     * service implementation must use.
     *
     * @param clazz the {@link ConfigurationStore} that this configuration
     * service instance instance has to use.
     *
     * @throws IOException if loading properties from the specified store fails.
     */
    private void setConfigurationStore(
            Class<? extends ConfigurationStore> clazz)
        throws IOException
    {
        String extension = null;

        if (PropertyConfigurationStore.class.isAssignableFrom(clazz))
        {
            extension = "properties";
        }
        else if (XMLConfigurationStore.class.isAssignableFrom(clazz))
        {
            extension = "xml";
        }

        this.configurationFile
                = (extension == null)
                    ? null
                    : getConfigurationFile(extension, true);

        if (!clazz.isInstance(this.store))
        {
            Throwable exception = null;

            try
            {
                this.store = clazz.newInstance();
            }
            catch (IllegalAccessException | InstantiationException iae)
            {
                exception = iae;
            }
            if (exception != null)
            {
                throw new RuntimeException(exception);
            }
        }
    }

    /**
     * Loads the default properties maps from the Jitsi installation directory.
     * then overrides them with the default override values.
     */
    private void loadDefaultProperties()
    {
        loadDefaultProperties(DEFAULT_PROPS_FILE_NAME);
        loadDefaultProperties(DEFAULT_OVERRIDES_PROPS_FILE_NAME);
    }

    /**
     * Loads the specified default properties maps from the Jitsi installation
     * directory. Typically this file is to be called for the default properties
     * and the admin overrides.
     *
     * @param  fileName the name of the file we need to load.
     */
    private void loadDefaultProperties(String fileName)
    {
        try
        {
            Properties fileProps = new Properties();

            InputStream fileStream;
            fileStream = ClassLoader.getSystemResourceAsStream(fileName);

            fileProps.load(fileStream);
            fileStream.close();

            // Now get those properties and place them into the mutable and
            // immutable properties maps.
            for (Map.Entry<Object, Object> entry : fileProps.entrySet())
            {
                String name  = (String) entry.getKey();
                String value = (String) entry.getValue();

                if (name == null || value == null || name.trim().length() == 0)
                {
                    continue;
                }

                if (name.startsWith("*"))
                {
                    name = name.substring(1);

                    if (name.trim().length() == 0)
                    {
                        continue;
                    }

                    // It seems that we have a valid default immutable property
                    immutableDefaultProperties.put(name, value);

                    // In case this is an override, make sure we remove previous
                    // definitions of this property
                    defaultProperties.remove(name);
                }
                else
                {
                    // This property is a regular, mutable default property.
                    defaultProperties.put(name, value);

                    // In case this is an override, make sure we remove previous
                    // Definitions of this property
                    immutableDefaultProperties.remove(name);
                }
            }
        }
        catch (Exception ex)
        {
            // We can function without defaults so we are just logging those.
            logger.info("No defaults property file loaded. Not a problem.");

            logger.debug("load exception", ex);
        }
    }

    /**
     * Returns the Enum value of the specified property or a default value if
     * the config doesn't return a specified default value.
     *
     * @param propertyName the name of the property that is being queried.
     * @param type the type of enum to match against.
     * @param defaultValue the value to be returned if the specified property
     * name is not associated with a value in this
     * <code>ConfigurationService</code>
     * @return the result of matching the property against the enum and
     * <code>defaultValue</code> in case there was no value mapped against
     * the specified <tt>propertyName</tt>, or the returned string had zero
     * length or contained whitespaces only.
     */
    @Override
    public <T extends Enum<T>> T getEnum(Class<T> type,
                                         String propertyName,
                                         T defaultValue)
    {
        String value = getString(propertyName);
        try
        {
            if (value != null)
            {
                return T.valueOf(type, value);
            }
        }
        catch (IllegalArgumentException e)
        {
            logger.info("Unknown value: " + value +
                        " for property: " + propertyName);
        }

        return defaultValue;
    }
 }
