/*
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.configuration;

import java.io.*;

import org.jitsi.service.configuration.*;

/**
 * A straightforward implementation of the <tt>ConfigurationService</tt> using
 * an XML or a .properties file for storing properties. Currently only
 * <tt>String</tt> properties are meaningfully saved (we should probably
 * consider how and whether we should take care of the rest).
 *
 * Configuration is split between global() and user() (specific) configuration
 * and this is the user() implementation.
 *
 *
 * @author Emil Ivov
 * @author Damian Minkov
 * @author Lyubomir Marinov
 * @author Dmitri Melnikov
 */
public class UserConfigurationServiceImpl
    extends AbstractScopedConfigurationServiceImpl
{
    /**
     * The name of the <tt>ConfigurationStore</tt> class to be used as the
     * default when no specific <tt>ConfigurationStore</tt> class is determined
     * as necessary.
     */
    private final String mConfigurationAccountName;

    /**
     * Global configuration service.
     */
    private ScopedConfigurationService globalConfigurationService = null;

    public UserConfigurationServiceImpl(String user, ScopedConfigurationService globalConfigurationService)
    {
        mConfigurationAccountName = user;
        this.globalConfigurationService = globalConfigurationService;

        if (mConfigurationAccountName == null)
        {
            throw new java.lang.IllegalArgumentException("user name has not been set.");
        }

        init();
    }

    /**
     * Returns the name of the directory where SIP Communicator is to store user
     * specific data such as configuration files, message and call history
     * as well as is bundle repository.
     *
     * This implementation adds an extra sub-directory so that the
     * configuration file is specific to this user and distinct from the
     * similar file for any other user.
     *
     * @return the name of the directory where SIP Communicator is to store
     * user specific data such as configuration files, message and call history
     * as well as is bundle repository.
     */
    @Override
    public String getScHomeDirName()
    {
        String scHomeDirName = null;

        scHomeDirName = super.getScHomeDirName();
        scHomeDirName = scHomeDirName + File.separator + "users"
            + File.separatorChar + mConfigurationAccountName;

        return scHomeDirName;
    }

    /**
     * @return the username that this UCSI is created for
     */
    String getConfiguredUserName()
    {
        return mConfigurationAccountName;
    }

    @Override
    public ScopedConfigurationService getGlobalConfigurationService()
    {
        return globalConfigurationService;
    }
}
