/*
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.configuration;

import org.jitsi.service.configuration.*;

/**
 * A straightforward implementation of the <tt>ConfigurationService</tt> using
 * an XML or a .properties file for storing properties. Currently only
 * <tt>String</tt> properties are meaningfully saved (we should probably
 * consider how and whether we should take care of the rest).
 *
 * Configuration is split between global() and user() (specific) configuration
 * and this is the global() implementation.
 *
 * @author Emil Ivov
 * @author Damian Minkov
 * @author Lyubomir Marinov
 * @author Dmitri Melnikov
 */
public class GlobalConfigurationServiceImpl
    extends AbstractScopedConfigurationServiceImpl
{
    public GlobalConfigurationServiceImpl()
    {
        init();
    }

    @Override
    public ScopedConfigurationService getGlobalConfigurationService()
    {
        return this;
    }
}

