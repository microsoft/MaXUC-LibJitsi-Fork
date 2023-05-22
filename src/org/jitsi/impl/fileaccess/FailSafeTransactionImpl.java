/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
// Portions (c) Microsoft Corporation. All rights reserved.
package org.jitsi.impl.fileaccess;

import java.io.*;

import org.jitsi.service.fileaccess.*;
import org.jitsi.util.*;

/**
 * A failsafe transaction class. By failsafe we mean here that the file
 * concerned always stays in a coherent state. This class use the transactional
 * model.
 *
 * @author Benoit Pradelle
 */
public class FailSafeTransactionImpl
    implements FailSafeTransaction
{
    private static final Logger logger
        = Logger.getLogger(FailSafeTransactionImpl.class);

    /**
     * Original file used by the transaction
     */
    private File file;

    /**
     * Backup file used by the transaction
     */
    private File backup;

    /**
     * Extension of a partial file
     */
    private static final String PART_EXT = ".part";

    /**
     * Extension of a backup copy
     */
    private static final String BAK_EXT = ".bak";

    /**
     * Creates a new transaction.
     *
     * @param file The file associated with this transaction
     *
     * @throws NullPointerException if the file is null
     */
    protected FailSafeTransactionImpl(File file)
        throws NullPointerException
    {
        if (file == null) {
            throw new NullPointerException("null file provided");
        }

        this.file = file;
        this.backup = null;
    }

    /**
     * Ensure that the file accessed is in a coherent state. This function is
     * useful to do a failsafe read without starting a transaction.
     *
     * @throws IllegalStateException if the file doesn't exists anymore
     * @throws IOException if an IOException occurs during the file restoration
     */
    @Override
    public synchronized void restoreFile()
        throws IllegalStateException, IOException
    {
        File back = new File(this.file.getAbsolutePath() + BAK_EXT);

        // if a backup copy is still present, simply restore it
        if (back.exists()) {
            logger.info("Restoring backup file: " + back);

            failsafeCopy(back.getAbsolutePath(),
                    this.file.getAbsolutePath());

            if (!back.delete()) {
                // We failed to delete the backup file.
                // We should be able to recover, as we'll overwrite the backup
                // the next time we start a transaction, but it's an indication
                // that things might not be working.
                logger.error("Failed to delete backup file after restore.");
            }
        }
    }

    /**
     * Begins a new transaction. If a transaction is already active, commits the
     * changes and begin a new transaction.
     * A transaction can be closed by a commit or rollback operation.
     * When the transaction begins, the file is restored to a coherent state if
     * needed.
     *
     * @throws IllegalStateException if the file doesn't exists anymore
     * @throws IOException if an IOException occurs during the transaction
     * creation
     */
    @Override
    public synchronized void beginTransaction()
        throws IllegalStateException, IOException
    {
        // if the last transaction hasn't been closed, commit it
        if (this.backup != null) {
            logger.error("Last failsafe transaction for " + file +
                                                             " was not closed");
            this.commit();
        }

        // if needed, restore the file in its previous state
        restoreFile();

        this.backup = new File(this.file.getAbsolutePath() + BAK_EXT);

        // else backup the current file
        failsafeCopy(this.file.getAbsolutePath(),
                this.backup.getAbsolutePath());
    }

    /**
     * Closes the transaction and commit the changes. Everything written in the
     * file during the transaction is saved.
     *
     * @throws IllegalStateException if the file doesn't exists anymore
     */
    @Override
    public synchronized void commit()
        throws IllegalStateException
    {
        if (this.backup == null) {
            logger.error("No backup to delete during commit");
            return;
        }

        // simply delete the backup file
        if (!this.backup.delete()) {
            // We failed to delete the backup file.
            // We can usually recover from this, as it will be overwritten
            // the next time we start a transaction, but if the client
            // closes suddenly before then, we'll lose this commit.
            logger.error("Failed to delete backup file during commit.");
        }

        this.backup = null;
    }

    /**
     * Closes the transaction and cancel the changes. Everything written in the
     * file during the transaction is NOT saved.
     * @throws IllegalStateException if the file doesn't exists anymore
     * @throws IOException if an IOException occurs during the operation
     */
    @Override
    public synchronized void rollback()
        throws IllegalStateException, IOException
    {
        logger.warn("Failsafe transaction rolling back " + file);

        if (this.backup == null) {
            logger.error("Could not roll back - no backup found!");
            throw new IllegalStateException("Unable to roll back - no backup found!");
        }

        // restore the backup and delete it
        failsafeCopy(this.backup.getAbsolutePath(),
                this.file.getAbsolutePath());

        if (!this.backup.delete()) {
            // We failed to delete the backup file.
            // We should be able to recover, as we'll overwrite the backup
            // the next time we start a transaction, but it's an indication
            // that things might not be working.
            logger.error("Failed to delete backup file after rollback.");
        }

        logger.info("Rollback of " + file + " completed.");

        this.backup = null;
    }

    /**
     * Copy a file in a fail-safe way. The destination is created in an atomic
     * way.
     *
     * @param from The file to copy
     * @param to The copy to create
     *
     * @throws IllegalStateException if the file doesn't exists anymore
     */
    private synchronized void failsafeCopy(String from, String to)
        throws IllegalStateException
    {
        logger.trace("Beginning failsafe copy from " + from + " to " + to);

        File ptoF = new File(to + PART_EXT);
        if (ptoF.exists()) {
            logger.debug("Deleting existing PART file: " + ptoF);
            if (!ptoF.delete()) {
                // This shouldn't matter, as FileOutputStream will overwrite
                // the target by default. But let's log it just in case.
                logger.error("Failed to delete existing PART file " + ptoF +
                                                      " during failsafe copy.");
            }
        }

        try (FileInputStream in = new FileInputStream(from);
             FileOutputStream out = new FileOutputStream(ptoF))
        {
            // actually copy the file
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0)
            {
                out.write(buf, 0, len);
            }
        }
        catch (IOException e)
        {
            logger.error("Failsafe copy failed: " + e.getMessage());
            throw new IllegalStateException(e.getMessage());
        }

        // to ensure a perfect copy, delete the destination if it exists
        File toF = new File(to);
        if (toF.exists()) {
            logger.debug("Overwriting file at " + to + " for failsafe copy.");
            boolean success = toF.delete();
            if (!success) {
                String error = "Failed to delete file at " + to +
                               " during failsafe copy. Failsafe copy failed.";
                logger.error(error);
                throw new IllegalStateException(error);
            }
        }

        // once done, rename the partial file to the final copy
        boolean success = ptoF.renameTo(toF);
        if (success) {
            logger.trace("Failsafe copy succeeded from " + from + " to " + to);
        }
        else {
            String error = "Failed to rename " + ptoF + "to " + toF +
                           " during failsafe copy. Failsafe copy failed.";
            logger.error(error);
            throw new IllegalStateException(error);
        }
    }
}
