/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
// Portions (c) Microsoft Corporation. All rights reserved.
package org.jitsi.impl.configuration;

import static org.jitsi.util.Hasher.logHasher;

import java.io.*;
import java.util.*;

import org.jitsi.service.configuration.*;
import org.jitsi.util.*;

/**
 * The configuration services provides a centralized approach of storing
 * persistent configuration data.
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Dmitri Melnikov
 */
public class ConfigurationServiceImpl implements ConfigurationService
{
    private static final Logger sLog = Logger.getLogger(ConfigurationServiceImpl.class);

    /** Parent folder for users' config */
    private static final String USERS_FOLDER_NAME = "users";

    public static final String PROPERTY_ACTIVE_USER
        = "net.java.sip.communicator.plugin.provisioning.auth.ACTIVE_USER";

    /** Name of the app start timestamp property. */
    private static final String PROPERTY_APP_START =
            "net.java.sip.communicator.plugin.provisioning.APP_START";

    private static ScopedConfigurationService globalConfig = null;
    private static UserConfigurationServiceImpl userConfig = null;

    public ConfigurationServiceImpl()
    {
        String activeUser;

        globalConfig = new GlobalConfigurationServiceImpl();

        // We need to save the start of the app time in order to log it in an
        // analytic event when the app terminates.
        globalConfig.setProperty(PROPERTY_APP_START, System.currentTimeMillis());

        activeUser = globalConfig.getString(PROPERTY_ACTIVE_USER);
        if (activeUser != null)
        {
            createUser(activeUser);
        }
    }

    /**
     * Return access to the global part of configuration.
     */
    @Override
    public ScopedConfigurationService global()
    {
        return globalConfig;
    }

    /**
     * Return access to the user part of configuration.
     */
    @Override
    public ScopedConfigurationService user()
    {
        return userConfig;
    }

    @Override
    public void setActiveUser(String user)
    {
        if (userConfig != null && user.equals(userConfig.getConfiguredUserName()))
        {
            // Not the end of the world, but shouldn't happen
            sLog.warn("Updating user to user we already have saved " + logHasher(user));
        }
        else
        {
            sLog.info("Setting active user " + logHasher(user));
            globalConfig.setProperty(PROPERTY_ACTIVE_USER, user);
        }
    }

    @Override
    public void createUser(String user)
    {
        if (userConfig == null)
        {
            sLog.info("Creating user for this subscriber " + logHasher(user));
            userConfig = new UserConfigurationServiceImpl(user, globalConfig);
        }
    }

    @Override
    public String[] listUsers()
    {
        File root = new File(global().getScHomeDirLocation(), global().getScHomeDirName());
        File usersRoot = new File(root, USERS_FOLDER_NAME);
        String[] list = usersRoot.list();

        if (list != null)
        {
            Arrays.sort(list);
        }

        return list;
    }
}
