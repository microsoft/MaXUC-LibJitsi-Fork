/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.service.configuration;

/**
 * The configuration services provides a centralized approach of storing
 * persistent configuration data.
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Dmitri Melnikov
 */
public interface ConfigurationService
{
    /**
     * Return access to the global part of configuration.
     */
    ScopedConfigurationService global();

    /**
     * Return access to the user part of configuration.
     */
    ScopedConfigurationService user();

    /**
     * Set the currently active user, i.e. determine which configuration will
     * be returned by user() above.
     */
    void setActiveUser(String user);

    /**
     * Create a new UserConfigurationServiceImpl for the user if
     * there isn't an existing one
     */
    void createUser(String user);

    /**
     * Return a list of the known users or null if the users folder has
     * not yet been created.
     */
    String[] listUsers();
}
