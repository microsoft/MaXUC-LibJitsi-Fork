/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.util;

import java.io.*;
import java.util.*;

/**
 * Defines the different permit extension file.
 *
 * @author Alexandre Maillard
 * @author Dmitri Melnikov
 * @author Vincent Lucas
 */
public class SoundFileUtils
{
    /**
     * Different extension of a sound file
     */
    public static final String wav = "wav";
    public static final String mid = "midi";
    public static final String mp2 = "mp2";
    public static final String mp3 = "mp3";
    public static final String mod = "mod";
    public static final String ram = "ram";
    public static final String wma = "wma";
    public static final String ogg = "ogg";
    public static final String gsm = "gsm";
    public static final String aif = "aiff";
    public static final String au = "au";

    /**
     * The file extension and the format of call recording to be used by
     * default.
     */
    public static final String DEFAULT_CALL_RECORDING_FORMAT = mp3;

    /**
     * Checks whether this file is a sound file.
     *
     * @param f <tt>File</tt> to check
     * @return <tt>true</tt> if it's a sound file, <tt>false</tt> otherwise
     */
    public static boolean isSoundFile(File f)
    {
        String ext = getExtension(f);

        if (ext != null)
        {
            return
                ext.equals(wma)
                    || ext.equals(wav)
                    || ext.equals(ram)
                    || ext.equals(ogg)
                    || ext.equals(mp3)
                    || ext.equals(mp2)
                    || ext.equals(mod)
                    || ext.equals(mid)
                    || ext.equals(gsm)
                    || ext.equals(au);
        }
        return false;
    }

    /**
     * Checks whether this file is a sound file.
     *
     * @param f <tt>File</tt> to check
     * @param soundFormats The sound formats to restrict the file name
     * extension. If soundFormats is null, then every sound format defined by
     * SoundFileUtils is correct.
     *
     * @return <tt>true</tt> if it's a sound file conforming to the format given
     * in parameters (if soundFormats is null, then every sound format defined
     * by SoundFileUtils is correct), <tt>false</tt> otherwise.
     */
    public static boolean isSoundFile(File f, String[] soundFormats)
    {
        // If there is no specific filters, then compare the file to all sound
        // extension available.
        if(soundFormats == null)
        {
            return SoundFileUtils.isSoundFile(f);
        }
        // Compare the file extension to the sound formats provided in
        // parameter.
        else
        {
            String ext = getExtension(f);

            // If the file has an extension
            if (ext != null)
            {
                return (Arrays.binarySearch(
                            soundFormats,
                            ext,
                            String.CASE_INSENSITIVE_ORDER) > -1);
            }
        }
        return false;
    }

    /**
     * Gets the file extension.
     * TODO: There are at least 2 other methods like this scattered around
     * the SC code, we should move them all to util package.
     *
     * @param f which wants the extension
     * @return Return the extension as a String
     */
    public static String getExtension(File f)
    {
        String s = f.getName();
        int i = s.lastIndexOf('.');
        String ext = null;

        if ((i > 0) &&  (i < s.length() - 1))
            ext = s.substring(i+1).toLowerCase();
        return ext;
    }
}
