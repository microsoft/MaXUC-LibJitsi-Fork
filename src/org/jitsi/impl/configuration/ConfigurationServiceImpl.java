/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.configuration;

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

    /** User configuration file. */
    private static final String CONFIG_FILE_NAME = "sip-communicator.properties";

    // Configuration items that are considered PII, so we we should strip from error reports.
    /**
     *  PAT and some passwords have a key name ending ENCRYPTED_PASSWORD,
     *  Generalising slightly for safety.
     */
    private static final String PASSWORD = "PASSWORD";
    private static final String CUSTOMSTATUS = "CUSTOM_STATUS";
    private static final String CHATSUBJECT = "chatRoomSubject";

    /**
     * Backup user configuration file,
     * basically a copy of the previous version of the main user file.
     */
    private static final String BAK_CONFIG_FILE_NAME = "sip-communicator.properties.bak";

    /** Temporary user configuration file, used only when sanitising the config. */
    private static final String SAFE_CONFIG_FILE_NAME = "sip-communicator-safe.properties";

    private static ScopedConfigurationService globalConfig = null;
    private static UserConfigurationServiceImpl userConfig = null;

    public ConfigurationServiceImpl()
    {
        String activeUser;

        globalConfig = new GlobalConfigurationServiceImpl();

        activeUser = globalConfig.getString(PROPERTY_ACTIVE_USER);
        if (activeUser != null)
        {
            createUser(activeUser);
        }

        //  We disable the Java client's Mac dock icon and menu bar by default.
        //  For dev/testing purposes we can set a config flag to re-enable the Java UI.
        //  If that has been set, we re-enable the Mac dock icon and menu bar here.
        if (OSUtils.isMac())
        {
            boolean showMainFrame = globalConfig.getBoolean(
                    "plugin.wispa.SHOW_MAIN_UI", false);

            sLog.info("Configured to show main UI? " + showMainFrame);
            if (showMainFrame)
            {
                OSUtils.showMacDockAndMenu();
            }
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
            sLog.warn("Updating user to user we already have " + user);
        }
        else
        {
            sLog.info("Setting active user " + user);
            globalConfig.setProperty(PROPERTY_ACTIVE_USER, user);
        }

        // Forget any stored credentials for other users from previous logins (historically,
        // they weren't forgotten on logout, so some may be hanging around).
        // We do this here as this is the 1st point that we know the active user (and we don't
        // want to forget their credentials!).
        forgetOtherUsersCredentials(user);
    }

    /**
     * Forget the credentials for other users (this should have happened on their logout,
     * but historically didn't, so tidy-up here).
     *
     * This method deliberately doesn't use FailSafeTransactionImpl or TransactionBasedFile
     * as they will add complexity and we'd be slightly fighting them to force deletion of
     * the backup.  We do not need their transactions, or ability to recover and we are ok
     * with the rare window condition of the config file being deleted (that user will just
     * have to set their preferences again).
     *
     * We also do not want to use the existing ConfigurationService as we do not want any of
     * the other user config leaking into the active user.
     *
     * @param activeUser The currently active user, who's credentials we DON'T want to forget
     */
    private void forgetOtherUsersCredentials(String activeUser)
    {
        String[] allUsers = listUsers();
        String[] linesToRemove = {PASSWORD, CUSTOMSTATUS, CHATSUBJECT};

        if (allUsers == null)
        {
            sLog.debug("Found no users folder, so nothing to forget.");
            return;
        }

        sLog.debug("Forget other user's credentials and PII, number of users: " + allUsers.length);
        for (String user : allUsers)
        {
            if (!user.equals(activeUser)) {
                sLog.debug("Check stored credentials for inactive user");

                File root = new File(global().getScHomeDirLocation(), global().getScHomeDirName());
                File usersRoot = new File(root, USERS_FOLDER_NAME);
                File specificUserRoot = new File (usersRoot, user);
                File userConfigFile = new File(specificUserRoot, CONFIG_FILE_NAME);
                File userBackupConfigFile = new File(specificUserRoot, BAK_CONFIG_FILE_NAME);

                // 1st check if there is anything to do: in most cases there are either
                // no other users, or they are already clean.
                if (ConfigFileSanitiser.isDirty(userConfigFile, linesToRemove) ||
                    ConfigFileSanitiser.isDirty(userBackupConfigFile, linesToRemove))
                {
                    sLog.info("Need to sanitise credentials for inactive user");
                    File safeUserConfigFile = new File(userConfigFile.getParent(), SAFE_CONFIG_FILE_NAME);
                    ConfigFileSanitiser.sanitiseFile(userConfigFile, safeUserConfigFile, linesToRemove);

                    // If we got here, then regardless of whether the 2nd call to sanitizeFile
                    // was successful, we need to delete the old file.
                    // If we cannot replace with the sanitised config this will forget some user settings,
                    // but that's less important than deleting the credentials.
                    userConfigFile.delete();

                    // Also need to delete the .bak temporary file! We never bother trying to
                    // sanitise it, just not worth it.
                    userBackupConfigFile.delete();

                    if (userConfigFile.exists() || userBackupConfigFile.exists())
                    {
                        sLog.error("Failed to delete old user config containing stored credentials.");
                    }

                    if (!safeUserConfigFile.renameTo(userConfigFile))
                    {
                        sLog.warn("Failed to move sanitised config, just tidy-up");
                        safeUserConfigFile.delete();
                    }
                }
                else
                {
                    sLog.debug("Stored credentials already clean for inactive user");
                }
            }
        }
    }

    @Override
    public void createUser(String user)
    {
        if (userConfig == null)
        {
            sLog.info("Creating user for: " + user);
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
