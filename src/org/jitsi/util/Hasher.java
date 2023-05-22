// Copyright (c) Microsoft Corporation. All rights reserved.
package org.jitsi.util;

import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.joining;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

/**
 * Class used to hash Personal Data before it is logged.
 *
 * Hash only the Personal Data by calling Hasher.logHasher(plaintext).
 * If there are multiple bits of Personal Data, then hash each one separately,
 * so that the resulting hashes can be correlated with other hashes elsewhere. e.g.:
 *   sLog.d("Some Personal Data to log: ", Hasher.logHasher(targetName));
 *   sLog.d("Some Personal Data to log: ", Hasher.logHasher(targetName), " and ", Hasher.logHasher(targetNumber));
 * these will then output:
 *   Some Personal Data to log: 10d1g17454(hash)
 *   Some Personal Data to log: 10d1g17454(hash) and 10d1g17a54(hash)
 *
 * Do NOT do this:
 * sLog.d("Some Personal Data to log: ", Hasher.logHasher(targetName + " and " + targetNumber));
 *
 * Use in Contact related toString methods: there are many places where contacts are logged.  Rather
 * than hash each of these in the logging call, it makes sense for the toString methods to do the
 * hashing.  This is OK as toString methods shouldn't be used for any real function.
 *
 * This leads to a special case anti-pattern: do not hash something that has already been hashed as
 * once again it will not be possible to correlate the resulting hash with any other log.
 *
 * If you know the plaintext, you can check the hash using online tools to get the 1st 10 digits of
 * a SHA224 hash, e.g. https://www.browserling.com/tools/sha224-hash
 *
 * This file is in Accession Desktop and Accession Mobile for Android, they should be kept as
 * similar as possible.  For that reason they are both written in Java.
 *
 * For safety, don't log anything from inside this class.
 */
public class Hasher
{
    private static String sSalt = "";
    private static MessageDigest sDigest;
    private static final Object sDigestLock = new Object();

    /**
     * We must have a salt before we are allowed to hash most Personal Data (excluding data that can.
     * be revealed before a user has logged in i.e. SSIDs, IP addresses etc.)
     * We choose the salt to be the DN of the logged in user. This is not done for security but
     * to allow unique, per-user hashes that can be calculated by Support/Sustaining Engineering
     * and correlated throughout the logs for a given subscriber, provided
     * that person consents to supplying them with their DN for debugging purposes.
     *
     * @param salt - New salt (may overwrite an existing one if we are changing users).
     */
    public static void setSalt(final String salt)
    {
        sSalt = salt;
    }

    /**
     * Convert the collection of Strings into a String of comma separated hashes
     * (see @Javadoc LogHasher.hash)
     */
    public static String logCollectionHasher(final Collection<String> collection)
    {
        return collection.stream()
                .map(Hasher::logHasher)
                .collect(joining(", "));
    }

    /**
     * Protect Personal Data by hashing (truncated SHA-224) it with the salt.  If it cannot hash then
     * replace with a redacted message for safety.
     */
    public static String logHasher(final Object object)
    {
        return Optional.ofNullable(object)
                .map(Objects::toString)
                .map(Hasher::logHasher)
                .orElse("null");
    }

    /**
     * Protect Personal Data by hashing (truncated SHA-224) it with the salt.
     * If it cannot be hashed, then replace with a redacted message to ensure
     * privacy.
     *
     * Sometimes strings that we need to hash can be null or empty,
     * particularly when running the UTs. Avoid NPEs and unnecessary work
     * trying to hash empty strings by returning those strings unchanged.
     *
     * There is also no need to hash the following values: "null", "0", "1", "-1",
     * "true", "false" (case-insensitive), and excessively hashing them decreases
     * diagnosability. Ideally, we would check before passing these to the hasher,
     * but as this problem is widespread it is easier to check here.
     *
     * We consider IP addresses, SSIDs, etc.
     * to be Personal Data, and these data can be obtained by the client
     * *before* we know what the user DN is (and thus before we have
     * a salt). Simply return the hashed value, as we do not need a salt
     * in this case.
     */
    public static String logHasher(final String plaintext)
    {
        return Optional.ofNullable(plaintext)
                .filter(not(StringUtils::isNullOrEmptyOrBoolean))
                .map(text -> sSalt + text)
                .map(Hasher::hashPersonalData)
                .orElse(plaintext);
    }

    /**
     * Protect Personal Data by hashing (truncated SHA-224) it.  This method does NOT use a salt -
     * if a salt is required it must be added to the plain text by the caller.
     *
     * It is intended for direct use to hash IDs sent in analytics that individually are
     * not Personal Data, but several together may be correlated with other diags (e.g. SAS events)
     * to identify a particular user.
     *
     * If it cannot be hashed then replace with a redacted message.
     */
    public static String hashPersonalData(final String plaintext)
    {
        if (StringUtils.isNullOrEmpty(plaintext))
        {
            return plaintext;
        }

        // Only take the first 10 characters of the hash string (that is a
        // sufficient level of uniqueness). Append "(hash)" for clarity.
        return sha224(plaintext, 10) + "(hash)";
    }

    /**
     * Take the SHA-224 hash of a given string and truncate the output to
     * maxLength characters. This method does NOT use a salt - if a salt is
     * required it must be added to the plain text by the caller.
     *
     * As well as providing the underlying implementation for the logHasher
     * method, it is intended for direct use to hash strings where we don't want
     * "(hash)" appended at the end e.g. for hash strings that we re-hash.
     *
     * If it cannot be hashed then replace with a redacted message.
     */
    public static String sha224(final String plaintext, final int maxLength)
    {
        if (StringUtils.isNullOrEmpty(plaintext))
        {
            return plaintext;
        }

        // Handle when we get a negative maxLength.
        int safeMaxLength = Math.max(maxLength, 0);

        byte[] plaintextBytes = StringUtils.getUTF8Bytes(plaintext);
        byte[] encodedHash;

        // Only 1 thread at a time is allowed to access the MessageDigest so
        // access to it is synchronized.
        synchronized (sDigestLock)
        {
            // Ensure we have a MessageDigest to create the hash.
            if (sDigest == null)
            {
                try
                {
                    sDigest = MessageDigest.getInstance("SHA-224");
                }
                catch (NoSuchAlgorithmException e)
                {
                    return "redacted_as_no_hash_algorithm";
                }
            }

            // Generate the hash here.
            encodedHash = sDigest.digest(plaintextBytes);
        }

        return hexToString(encodedHash, safeMaxLength);
    }

    private static String hexToString(final byte[] encodedHash, final int safeMaxLength)
    {
        final char[] hexConversionArray = {'0','1','2','3','4','5','6','7',
                                           '8','9','a','b','c','d','e','f'};
        // For example, if we want to print out the hex value "10" as a string:
        // In binary, 0x10 = 00010000. Taking that value and doing a
        // bitwise AND with 0xF0, and then doing 4 right shifts gives
        // 0001 = 1 in binary.

        // We can take the corresponding hexConversionArray[1] to get the char
        // representation of this binary value. Doing a bitwise AND of 0x0F
        // with the final 4 bits of the hex value gives 0000 = 0, and
        // hexConversionArray[0] will give the correct char representation, '0'.

        // Printing that new array as a string will concatenate the '1' and '0'
        // to give '10', as desired.

        // Each byte of encodedHash produces two chars of output. The longest
        // string output will be safeMaxLength (or 0). We therefore want to
        // process no more than Math.min(safeMaxLength/2, encodedHash.length).
        // Note: this method is only called for a non-zero-length encodedHash.

        int hashLength = encodedHash.length;
        int maxBytesToProcess = Math.min(safeMaxLength/2, hashLength);
        StringBuilder output = new StringBuilder();
        for (int i = 0; i < maxBytesToProcess; i++)
        {
            byte b = encodedHash[i];
            output.append(hexConversionArray[(b & 0xF0) >>> 4]);
            output.append(hexConversionArray[(b & 0x0F)]);
        }

        // Special case: if safeMaxLength is odd, and
        // (hashLength - safeMaxLength/2) >= 1, we will have a string
        // truncated by one char too many. Add final char.

        if (((safeMaxLength % 2) == 1) && ((hashLength - safeMaxLength/2) >= 1))
        {
            output.append(hexConversionArray[(encodedHash[safeMaxLength/2] & 0xF0) >>> 4]);
        }

        return output.toString();
    }
}