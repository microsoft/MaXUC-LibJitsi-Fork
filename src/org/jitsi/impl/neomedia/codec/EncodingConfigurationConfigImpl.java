/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.codec;

import java.util.*;

import org.jitsi.service.configuration.*;
import org.jitsi.service.libjitsi.*;

/**
 * An EncodingConfiguration implementation that synchronizes it's preferences
 * with a ConfigurationService.
 *
 * @author Boris Grozev
 */
public class EncodingConfigurationConfigImpl
       extends EncodingConfigurationImpl
{
    /**
     * Holds the prefix that will be used to store properties
     */
    private String propPrefix;

    /**
     * The <tt>ConfigurationService</tt> instance that will be used to
     * store properties
     */
    private ConfigurationService configurationService
            = LibJitsi.getConfigurationService();

    /**
     * Constructor. Loads the configuration from <tt>prefix</tt>
     *
     * @param prefix the prefix to use when loading and storing properties
     */
    public EncodingConfigurationConfigImpl(String prefix)
    {
        propPrefix = prefix;
        loadConfig();
    }

    /**
     * Loads the properties stored under <tt>this.propPrefix</tt>
     */
    private void loadConfig()
    {
        Map<String, String> properties = new HashMap<>();

        for (String pName :
               configurationService.user().getPropertyNamesByPrefix(propPrefix, true))
            properties.put(pName, configurationService.user().getString(pName));

        loadProperties(properties);
    }
}
