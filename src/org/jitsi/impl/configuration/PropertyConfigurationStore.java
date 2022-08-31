/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.configuration;

import java.io.*;
import java.util.*;

import org.jitsi.util.*;

/**
 * Implements a <tt>ConfigurationStore</tt> which stores property name-value
 * associations in a <tt>Properties</tt> instance and supports its
 * serialization format for the configuration file of
 * <tt>ConfigurationServiceImpl</tt>. Because of the <tt>Properties</tt>
 * backend which can associate names only <tt>String</tt> values, instances
 * of <tt>PropertyConfigurationStore</tt> convert property values to
 * <tt>String</tt> using <tt>Object#toString()</tt>.
 *
 * @author Lyubomir Marinov
 */
public class PropertyConfigurationStore
    extends HashtableConfigurationStore<Properties>
{
    private static Logger logger =
                             Logger.getLogger(PropertyConfigurationStore.class);

    /**
     * Number of entries in the config last time we read from or wrote to the
     * config file.  If this is substantially reduced on a subsequent read or
     * write, we'll raise a warning log.
     */
    private int numberOfEntries;

    /**
     * Initializes a new <tt>PropertyConfigurationStore</tt> instance.
     */
    public PropertyConfigurationStore()
    {
        super(new SortedProperties());
        numberOfEntries = 0;
    }

    /**
     * Implements {@link ConfigurationStore#reloadConfiguration(File)}. Removes
     * all property name-value associations currently present in this
     * <tt>ConfigurationStore</tt> and deserializes new property name-value
     * associations from a specific <tt>File</tt> which presumably is in the
     * format represented by this instance.
     *
     * @param file the <tt>File</tt> to be read and to deserialize new property
     * name-value associations from into this instance
     * @throws IOException if there is an input error while reading from the
     * specified <tt>file</tt>
     * @see ConfigurationStore#reloadConfiguration(File)
     */
    @Override
    public void reloadConfiguration(File file)
        throws IOException
    {
        properties.clear();

        try (InputStream in = new BufferedInputStream(new FileInputStream(file)))
        {
            properties.load(in);

            // Flag if the number of entries has been significantly reduced -
            // shouldn't happen in normal operation.
            int entriesRead = properties.size();
            if (entriesRead < (0.8 * numberOfEntries))
            {
                logger.warn("Number of entries read from config has " +
                                    "reduced substantially: " + entriesRead +
                                    " was: "
                                    + numberOfEntries);
            }

            if (numberOfEntries == 0)
            {
                // Start-up - check we've got some valid config
                logger.info(
                        "Number of config items on startup: " + entriesRead);
            }

            numberOfEntries = entriesRead;
        }
        catch (IOException ioex)
        {
            logger.error("IOException reading from config: ", ioex);
            throw ioex;
        }
        catch (IllegalArgumentException iaex)
        {
            logger.error("IllegalArgumentException reading from config: ",
                         iaex);
            throw iaex;
        }
    }

    /**
     * Overrides
     * {@link HashtableConfigurationStore#setNonSystemProperty(String, Object)}.
     * As the backend of this instance is a <tt>Properties</tt> instance, it can
     * only store <tt>String</tt> values and the specified value to be
     * associated with the specified property name is converted to a
     * <tt>String</tt>.
     *
     * @param name the name of the non-system property to be set to the
     * specified value in this <tt>ConfigurationStore</tt>
     * @param value the value to be assigned to the non-system property with the
     * specified name in this <tt>ConfigurationStore</tt>
     * @see ConfigurationStore#setNonSystemProperty(String, Object)
     */
    @Override
    public void setNonSystemProperty(String name, Object value)
    {
        properties.setProperty(name, value.toString());
    }

    /**
     * Implements {@link ConfigurationStore#storeConfiguration(OutputStream)}.
     * Stores/serializes the property name-value associations currently present
     * in this <tt>ConfigurationStore</tt> into a specific <tt>OutputStream</tt>
     * in the format represented by this instance.
     *
     * @param out the <tt>OutputStream</tt> to receive the serialized form of
     * the property name-value associations currently present in this
     * <tt>ConfigurationStore</tt>
     * @throws IOException if there is an output error while storing the
     * properties managed by this <tt>ConfigurationStore</tt> into the specified
     * <tt>file</tt>
     * @see ConfigurationStore#storeConfiguration(OutputStream)
     */
    @Override
    public void storeConfiguration(OutputStream out)
        throws IOException
    {
        // Flag if the number of entries has been significantly reduced - this
        // shouldn't happen in normal operation.
        int entriesToWrite = properties.size();
        if (entriesToWrite < (0.8 * numberOfEntries))
        {
            logger.warn("Number of entries being written to config has " +
                "reduced substantially: " + entriesToWrite + " was: " +
                    numberOfEntries);
        }
        numberOfEntries = entriesToWrite;

        properties.store(out, null);
    }
}
