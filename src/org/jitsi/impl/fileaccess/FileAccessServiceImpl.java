/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.fileaccess;

import java.io.*;

import org.jitsi.impl.configuration.*;
import org.jitsi.service.fileaccess.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.util.*;

/**
 * Default FileAccessService implementation.
 *
 * @author Alexander Pelov
 * @author Lyubomir Marinov
 */
public class FileAccessServiceImpl implements FileAccessService
{
    /**
     * The <tt>Logger</tt> used by the <tt>FileAccessServiceImpl</tt> class and
     * its instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(FileAccessServiceImpl.class);

    /**
     * The file prefix for all temp files.
     */
    public static final String TEMP_FILE_PREFIX = "SIPCOMM";

    /**
     * The file suffix for all temp files.
     */
    public static final String TEMP_FILE_SUFFIX = "TEMP";

    /**
     * Name of the active user being used by configuration.
     */
    private static final String PROPERTY_ACTIVE_USER
        = "net.java.sip.communicator.plugin.provisioning.auth.ACTIVE_USER";

    private final String scHomeDirLocation;

    private final String scHomeDirName;

    public FileAccessServiceImpl()
    {
        scHomeDirLocation
            = getSystemProperty(
                    AbstractScopedConfigurationServiceImpl.PNAME_SC_HOME_DIR_LOCATION);
        if (scHomeDirLocation == null)
            throw new IllegalStateException(
                    AbstractScopedConfigurationServiceImpl.PNAME_SC_HOME_DIR_LOCATION);

        scHomeDirName
            = getSystemProperty(AbstractScopedConfigurationServiceImpl.PNAME_SC_HOME_DIR_NAME);
        if (scHomeDirName == null)
            throw new IllegalStateException(
                    AbstractScopedConfigurationServiceImpl.PNAME_SC_HOME_DIR_NAME);
    }

    /**
     * This method returns a created temporary file. After you close this file
     * it is not guaranteed that you will be able to open it again nor that it
     * will contain any information.
     *
     * Note: DO NOT store unencrypted sensitive information in this file
     *
     * @return The created temporary file
     * @throws IOException
     *             If the file cannot be created
     */
    @Override
    public File getTemporaryFile() throws IOException
    {
        File retVal = null;

        try
        {
            logger.entry();

            retVal = TempFileManager.createTempFile(TEMP_FILE_PREFIX,
                    TEMP_FILE_SUFFIX);
        }
        finally
        {
            logger.exit();
        }

        return retVal;
    }

    /**
     * Returns the temporary directory.
     *
     * @return the created temporary directory
     * @throws IOException if the temporary directory cannot not be created
     */
    @Override
    public File getTemporaryDirectory() throws IOException
    {
        File file = getTemporaryFile();

        if (!file.delete())
        {
            throw new IOException("Could not create temporary directory, "
                    + "because: could not delete temporary file.");
        }
        if (!file.mkdirs())
        {
            throw new IOException("Could not create temporary directory");
        }

        return file;
    }

    /**
     * This method returns a file specific to the current Computer user. It may not
     * exist, but it is guaranteed that you will have the sufficient rights to
     * create it, and that its parent directory exists.
     *
     * This file should not be considered secure because the implementor may
     * return a file accessible to everyone. Generally it will reside in current
     * user's homedir, but it may as well reside in a shared directory.
     *
     * Note: DO NOT store unencrypted sensitive information in this file
     *
     * @param fileName
     *            The name of the private file you wish to access
     * @return The file
     * @throws SecurityException if we fail to create the file due to permissions
     * @throws IOException if the application home directory did not exist,
     * and needed to be created, but creation failed.
     */
    @Override
    public File getPrivatePersistentFile(String fileName)
        throws IOException, SecurityException
    {
        logger.entry(fileName);

        File file = null;

        try
        {
            String fullPath = getFullPath(fileName);
            file = accessibleFile(fullPath, fileName);

            if (file == null)
            {
                throw new IOException("Insufficient rights to access "
                    + "this file in current user's home directory: "
                    + new File(fullPath, fileName).getPath());
            }
        }
        finally
        {
            logger.exit();
        }

        return file;
    }

    /**
     * This method returns a file specific to the active Application user. It may not
     * exist, but it is guaranteed that you will have the sufficient rights to
     * create it, and that its parent directory exists.
     *
     * This file should not be considered secure because the implementor may
     * return a file accessible to everyone. Generally it will reside in current
     * user's homedir, but it may as well reside in a shared directory.
     *
     * Note: DO NOT store unencrypted sensitive information in this file
     *
     * @param fileName
     *            The name of the private file you wish to access
     * @return The file
     * @throws SecurityException if we fail to create the file due to permissions
     * @throws IOException if the application home directory did not exist,
     * and needed to be created, but creation failed.
     */
    @Override
    public File getPrivatePersistentActiveUserFile(String fileName)
        throws IOException, SecurityException
    {
        return getPrivatePersistentFile(
            "users" +
            File.separator +
            LibJitsi.getConfigurationService().global()
                .getString(PROPERTY_ACTIVE_USER) +
            File.separator +
            fileName);
    }

    /**
     * This method creates a directory specific to the active Application user.
     *
     * This directory should not be considered secure because the implementor
     * may return a directory accessible to everyone. Generally it will reside in
     * current user's homedir, but it may as well reside in a shared directory.
     *
     * It is guaranteed that you will be able to create files in it.
     *
     * Note: DO NOT store unencrypted sensitive information in this file
     *
     * @param dirName
     *            The name of the private directory you wish to access.
     * @return The created directory.
     * @throws SecurityException Thrown if we fail to access or create the
     * directory due to permissions.
     * @throws IOException Thrown if the application home directory did not exist,
     * and needed to be created, but creation failed.
     */
    @Override
    public File getPrivatePersistentActiveUserDirectory(String dirName)
        throws IOException, SecurityException
    {
        return getPrivatePersistentDirectory(
            "users" +
            File.separator +
            LibJitsi.getConfigurationService().global()
                .getString(PROPERTY_ACTIVE_USER) +
            File.separator +
            dirName);
    }

    /**
     * This method creates a directory specific to the current Computer user.
     *
     * This directory should not be considered secure because the implementor
     * may return a directory accessible to everyone. Generally it will reside in
     * current user's homedir, but it may as well reside in a shared directory.
     *
     * It is guaranteed that you will be able to create files in it.
     *
     * Note: DO NOT store unencrypted sensitive information in this file
     *
     * @param dirName
     *            The name of the private directory you wish to access.
     * @return The created directory.
     * @throws SecurityException Thrown if we fail to access or create the
     * directory due to permissions issues
     * @throws IOException
     *             Thrown if there is no suitable location for the persistent
     *             directory, or creation of directory failed.
     */
    @Override
    public File getPrivatePersistentDirectory(String dirName)
        throws IOException, SecurityException
    {
        String fullPath = getFullPath(dirName);
        File dir = new File(fullPath, dirName);

        if (dir.exists())
        {
            if (!dir.isDirectory())
            {
                throw new IOException("Could not create directory "
                        + "because: A file exists with this name:"
                        + dir.getAbsolutePath());
            }
        }
        else
        {
            if (!dir.mkdirs())
            {
                throw new IOException("Could not create directory");
            }
        }

        return dir;
    }

    /**
     * This method creates a directory specific to the current Computer user.
     *
     * {@link #getPrivatePersistentDirectory(String)}
     *
     * @param dirNames
     *            The name of the private directory you wish to access.
     * @return The created directory.
     * @throws SecurityException Thrown if we fail to access or create the
     * directory due to permissions issues
     * @throws IOException
     *             Thrown if there is no suitable location for the persistent
     *             directory, or creation of directory failed.
     */
    @Override
    public File getPrivatePersistentDirectory(String[] dirNames)
        throws IOException, SecurityException
    {
        StringBuilder dirName = new StringBuilder();
        for (int i = 0; i < dirNames.length; i++)
        {
            if (i > 0)
            {
                dirName.append(File.separatorChar);
            }
            dirName.append(dirNames[i]);
        }

        return getPrivatePersistentDirectory(dirName.toString());
    }

    /**
     * Returns the full parth corresponding to a file located in the
     * sip-communicator config home and carrying the specified name.
     * @param fileName the name of the file whose location we're looking for.
     * @return the config home location of a file within the specified name.
     */
    private String getFullPath(String fileName)
    {
        // bypass the configurationService here to remove the dependency
        String userhome =  getScHomeDirLocation();
        String sipSubdir = getScHomeDirName();

        if (!userhome.endsWith(File.separator))
        {
            userhome += File.separator;
        }
        if (!sipSubdir.endsWith(File.separator))
        {
            sipSubdir += File.separator;
        }

        return userhome + sipSubdir;
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
    private String getScHomeDirName()
    {
        String scHomeDirName = this.scHomeDirName;

        if (scHomeDirName == null)
            scHomeDirName = ".sip-communicator";

        return scHomeDirName;
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
    private String getScHomeDirLocation()
    {
        String scHomeDirLocation = this.scHomeDirLocation;

        if (scHomeDirLocation == null)
            scHomeDirLocation = getSystemProperty("user.home");

        return scHomeDirLocation;
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
        if (retval == null){
            return retval;
        }

        if (retval.trim().length() == 0){
            return null;
        }
        return retval;
    }
    /**
     * Checks if a file exists and if it is writable or readable. If not -
     * checks if the user has a write privileges to the containing directory.
     *
     * If those conditions are met it returns a File in the directory with a
     * fileName. If not - returns null.
     *
     * @param homedir the location of the sip-communicator home directory.
     * @param fileName the name of the file to create.
     * @return Returns null if the file does not exist and cannot be created.
     *         Otherwise - an object to this file
     * @throws IOException
     *             Thrown if the home directory cannot be created
     */
    private static File accessibleFile(String homedir, String fileName)
            throws IOException
    {
        logger.entry(homedir + "," + fileName);

        File file = null;

        try
        {
            homedir = homedir.trim();
            if (!homedir.endsWith(File.separator))
            {
                homedir += File.separator;
            }

            file = new File(homedir + fileName);
            if (file.canRead() || file.canWrite())
            {
                return file;
            }

            File homedirFile = new File(homedir);

            if (!homedirFile.exists())
            {
                logger.debug("Creating home directory : "
                        + homedirFile.getAbsolutePath());
                if (!homedirFile.mkdirs())
                {
                    String message = "Could not create the home directory : "
                            + homedirFile.getAbsolutePath();

                    logger.debug(message);
                    throw new IOException(message);
                }
                logger.debug("Home directory created : "
                        + homedirFile.getAbsolutePath());
            }
            else if (!homedirFile.canWrite())
            {
                file = null;
            }

            if(file != null && !file.getParentFile().exists())
            {
                if (!file.getParentFile().mkdirs())
                {
                    String message = "Could not create the parent directory : "
                        + homedirFile.getAbsolutePath();

                    logger.debug(message);
                    throw new IOException(message);
                }
            }
        }
        finally
        {
            logger.exit();
        }

        return file;
    }

    /**
     * Returns the default download directory.
     *
     * @return the default download directory
     */
    @Override
    public File getDefaultDownloadDirectory()
    {
        return new File(getSystemProperty("user.home"), "Downloads");
    }

    /**
     * Creates a failsafe transaction which can be used to safely store
     * informations into a file.
     *
     * @param file The file concerned by the transaction, null if file is null.
     *
     * @return A new failsafe transaction related to the given file.
     */
    @Override
    public FailSafeTransaction createFailSafeTransaction(File file)
    {
        return (file == null) ? null : new FailSafeTransactionImpl(file);
    }
}
