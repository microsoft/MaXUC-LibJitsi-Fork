/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.service.fileaccess;

import java.io.*;

/**
 * A service used to provide the basic functionality required to access the
 * underlying file system.
 *
 * Note: Never store unencrypted sensitive information, such as passwords,
 * personal data, credit card numbers, etc..
 *
 * @author Alexander Pelov
 */
public interface FileAccessService {

    /**
     * The key of the configuration property containing the user home dir - if
     * it is not defined the system property is used
     */
    String CONFPROPERTYKEY_USER_HOME
        = "net.java.sip.communicator.fileaccess.USER_HOME";

    /**
     * This method returns a created temporary file. After you close this file
     * it is not guaranteed that you will be able to open it again nor that it
     * will contain any information.
     *
     * Note: DO NOT store unencrypted sensitive information in this file
     *
     * @return The created temporary file
     * @throws IOException If the file cannot be created
     * @throws SecurityException If the file cannot be created due to
     * permissions issues.
     */
    File getTemporaryFile() throws IOException, SecurityException;

    /**
     * This method returns a created temporary directory. Any file you create in
     * it will be a temporary file.
     *
     * Note: If there is no opened file in this directory it may be deleted at
     * any time. Note: DO NOT store unencrypted sensitive information in this
     * directory
     *
     * @return The created directory
     * @throws IOException
     *             If the directory cannot be created
     */
    File getTemporaryDirectory() throws IOException, SecurityException;

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
     * @throws SecurityException if we fail to create the file due to a permissions issue
     * @throws IOException if the application home directory did not exist,
     * and needed to be created, but creation failed.
     */
    File getPrivatePersistentFile(String fileName)
            throws IOException, SecurityException;

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
     * @throws SecurityException if we fail to create the file due to a permissions issue
     * @throws IOException if the application home directory did not exist,
     * and needed to be created, but creation failed.
     */
    File getPrivatePersistentActiveUserFile(String fileName)
            throws IOException, SecurityException;

    /**
     * This method creates a directory specific to the active Application user.
     *
     * This directory should not be considered secure because the implementor
     * may return a directory accessible to everyone. Generally, it will reside
     * in current user's homedir, but it may as well reside in a shared
     * directory.
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
    File getPrivatePersistentActiveUserDirectory(String dirName)
            throws IOException, SecurityException;

    /**
     * This method creates a directory specific to the current Computer user.
     *
     * This directory should not be considered secure because the implementor
     * may return a directory accessible to everyone. Generally, it will reside
     * in current user's homedir, but it may as well reside in a shared
     * directory.
     *
     * It is guaranteed that you will be able to create files in it.
     *
     * Note: DO NOT store unencrypted sensitive information in this file
     *
     * @param dirName
     *            The name of the private directory you wish to access.
     * @return The created directory.
     * @throws SecurityException Thrown if we fail to access or create the
     * directory due to permissions issues.
     * @throws IOException
     *             Thrown if there is no suitable location for the persistent
     *             directory, or creation of directory failed.
     */
    File getPrivatePersistentDirectory(String dirName)
            throws IOException, SecurityException;

    /**
     * This method creates a directory specific to the current Computer user.
     *
     * {@link #getPrivatePersistentDirectory(String)}
     *
     * @param dirNames
     *            The name of the private directory you wish to access.
     * @return The created directory.
     * @throws SecurityException Thrown if we fail to access or create the
     * directory due to permissions issues.
     * @throws IOException
     *             Thrown if there is no suitable location for the persistent
     *             directory, or creation of directory failed.
     */
    File getPrivatePersistentDirectory(String[] dirNames)
            throws IOException, SecurityException;

    /**
     * Returns the default download directory depending on the operating system.
     *
     * @return the default download directory depending on the operating system
     */
    File getDefaultDownloadDirectory();

    /**
     * Creates a failsafe transaction which can be used to safely store
     * informations into a file.
     *
     * @param file The file concerned by the transaction, null if file is null.
     *
     * @return A new failsafe transaction related to the given file.
     */
    FailSafeTransaction createFailSafeTransaction(File file);
}
