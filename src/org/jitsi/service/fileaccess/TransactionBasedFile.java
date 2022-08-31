// Copyright (c) Microsoft Corporation. All rights reserved.
package org.jitsi.service.fileaccess;

import java.io.*;
import java.nio.file.*;

import org.jitsi.util.*;

/**
 * TransactionBasedFile.
 * <p>
 * Provides safe write access to an underlying file.  Safety is ensured by all
 * writes going to a temp file, which is then moved over the original when <tt>
 * commitTransaction</tt> is called.
 * <p>
 * Usage:<br>
 * <ul>
 * <li>Create a TransactionBasedFile for the file</li>
 * <li>Call <tt>beginTransaction</tt></li>
 * <li>Call <tt>getOutputStream</tt> and write to the returned stream</li>
 * <li>If happy with the changes, call <tt>commitTransaction</tt> to save the
 * result</li>
 * <li>If unhappy, call <tt>abortTransaction</tt> to discard the changes</li>
 * </ul>
 */
public class TransactionBasedFile
{
    private static final String TEMP_SUFFIX = ".tmp";
    private static final String BACKUP_SUFFIX = ".bak";
    private static final CopyOption[] COPY_OPTIONS = new CopyOption[]{
        StandardCopyOption.REPLACE_EXISTING,
        StandardCopyOption.COPY_ATTRIBUTES
    };

    private static final Logger logger = Logger.getLogger(TransactionBasedFile.class);

    private File mTempFile;
    private File mBackupFile;
    private File mFile;
    private boolean transactionInProgress;

    /**
     * Create a new TransactionBasedFile
     * @param file The file to wrap
     */
    public TransactionBasedFile(File file)
    {
        mFile = file;

        logger.debug("Create transaction-based file: " + file.getAbsolutePath() +
                     ", size=" + file.length());
        transactionInProgress = false;
    }

    /**
     * Start a new transaction.  Must be closed by <tt>commitTransaction</tt> or
     * <tt>abortTransaction</tt> before the next transaction can begin.  User
     * can now call <tt>getOutputStream</tt> and write data.
     */
    public synchronized void beginTransaction()
    {
        if (transactionInProgress)
        {
            logger.error("Transaction already in progress");
            throw new IllegalStateException("Transaction already in progress");
        }

        transactionInProgress = true;
        mTempFile = new File(mFile.getAbsolutePath() + TEMP_SUFFIX);
        mBackupFile = new File(mFile.getAbsolutePath() + BACKUP_SUFFIX);

        {
            try
            {
                Files.copy(mFile.toPath(), mBackupFile.toPath(), COPY_OPTIONS);
            }
            catch (IOException e)
            {
                logger.warn("Unable to create backup copy of config file", e);
            }

            syncFileToDisk(mBackupFile);
        }
    }

    /**
     * Do our best to ensure a file has been written to disk.
     * @param fileToSync
     */
    private static void syncFileToDisk(File fileToSync)
    {
        // new FileOutputStream(file).getFD().sync(), plus some error handling
        try
        {
            // Pretend to append, rather than overwriting the file with nothing
            FileOutputStream out = new FileOutputStream(fileToSync, true);
            if (out != null)
            {
                try
                {
                    FileDescriptor fd = out.getFD();
                    out.flush();
                    fd.sync();
                }
                finally
                {
                    out.close();
                }
            }
        }
        catch (IOException e)
        {
            logger.error("Couldn't sync write to file: " + fileToSync, e);
        }
    }

    /**
     * Save all writes to the underlying file and end the transaction.
     * @param hardFlush Whether to delete the backup config file from disk (use if it may
     * contain sensitive data.
     */
    public synchronized void commitTransaction(boolean hardFlush)
    {
        if (!transactionInProgress)
        {
            logger.error("No transaction to commit");
            throw new IllegalStateException("No transaction to commit");
        }

        try
        {
            // Look for suspicious changes in file size.
            long size1 = mFile.length();
            long size2 = mTempFile.length();
            if ((Math.abs(size1 - size2) > 0.1*Math.max(size1, size2)))
            {
                logger.warn("Significant change in properties size "
                             + size1 + "->" + size2);
            }

            logger.debug("Replace config file size " +
                         size1 + "->" + size2);
            Files.move(mTempFile.toPath(),
                       mFile.toPath(),
                       StandardCopyOption.ATOMIC_MOVE);

            syncFileToDisk(mFile);
        }
        catch (IOException ioex)
        {
            logger.error("Couldn't commit transaction", ioex);
        }

        mTempFile = null;

        // Don't delete the backup file unless we have to,
        // as it may help an attempted recovery.
        if (hardFlush)
        {
            mBackupFile.delete();
            syncFileToDisk(mBackupFile);
        }

        mBackupFile = null;

        transactionInProgress = false;
    }

    /**
     * Abandon all writes in this transaction, and end the transaction.
     */
    public synchronized void abortTransaction()
    {
        if (!transactionInProgress)
        {
            logger.error("No transaction to abort");
            throw new IllegalStateException("No transaction to abort");
        }

        mTempFile.delete();
        mTempFile = null;

        transactionInProgress = false;
    }

    /**
     * Obtain an OutputStream to which changes can be written.  Can only be
     * called while a transaction is active.
     * @return The OutputStream.  <b>Caller is responsible for closing this.</b>
     */
    public synchronized OutputStream getOutputStream()
    {
        if (!transactionInProgress)
        {
            logger.error("No transaction - not possible to get output stream");
            throw new IllegalStateException("No transaction - not possible to get output stream");
        }

        try
        {
            return new FileOutputStream(mTempFile);
        }
        catch (FileNotFoundException e)
        {
            logger.error("Couldn't get file output stream for temp file: " +  mTempFile);
            throw new IllegalStateException(
                    "Couldn't get file output stream for temp file", e);
        }
    }

    /**
     * Look for a backup file, and copy that to the original.
     *
     * @param configFile  The missing config file
     * @return A repaired config file, or the original file
     */
    public static File attemptRecovery(File configFile)
    {
        File configFileResult = configFile;
        File backupConfigFile = new File(configFile.getPath() + BACKUP_SUFFIX);

        if (backupConfigFile.exists() && backupConfigFile.length() > 0)
        {
            // Let's try and repair the situation by copying the backup
            // file onto the new location.
            logger.error("Backup file exists, and new does not!");
            logger.error("Backup file size=" + backupConfigFile.length());

            try
            {
                Files.move(backupConfigFile.toPath(),
                           configFile.toPath(),
                           StandardCopyOption.ATOMIC_MOVE);

                configFileResult = new File(configFile.getPath());
                syncFileToDisk(configFileResult);
            }
            catch (IOException e)
            {
                logger.error("Failed to copy backup file", e);
            }
        }
        else
        {
            logger.debug("No temporary file");
        }

        return configFileResult;
    }
}
