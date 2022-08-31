/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.util;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility fields for OS detection.
 *
 * @author Sebastien Vincent
 * @author Lubomir Marinov
 */
public class OSUtils
{
    private static final Logger sLog = Logger.getLogger(OSUtils.class);

    /** <tt>true</tt> if architecture is 32 bit. */
    public static final boolean IS_32_BIT;

    /** <tt>true</tt> if architecture is 64 bit. */
    public static final boolean IS_64_BIT;

    /** <tt>true</tt> if OS is MacOSX. */
    public static final boolean IS_MAC;

    /** <tt>true</tt> if OS is MacOSX 32-bit. */
    public static final boolean IS_MAC32;

    /** <tt>true</tt> if OS is MacOSX 64-bit. */
    public static final boolean IS_MAC64;

    /** <tt>true</tt> if OS is Windows. */
    public static final boolean IS_WINDOWS;

    /** <tt>true</tt> if OS is Windows 32-bit. */
    public static final boolean IS_WINDOWS32;

    /** <tt>true</tt> if OS is Windows 64-bit. */
    public static final boolean IS_WINDOWS64;

    /** <tt>true</tt> if OS is Windows Vista. */
    public static final boolean IS_WINDOWS_VISTA;

    /** <tt>true</tt> if OS is Windows 7. */
    public static final boolean IS_WINDOWS_7;

    /** <tt>true</tt> if OS is Windows 8. */
    public static final boolean IS_WINDOWS_8;

    /** <tt>true</tt> if OS is Windows 10. */
    public static final boolean IS_WINDOWS_10;

    /**
     * @return true if OS is Windows
     */
    public static boolean isWindows()
    {
        return IS_WINDOWS;
    }

    /**
     * @return true is OS is MacOSX
     */
    public static boolean isMac()
    {
        return IS_MAC;
    }

    /**
     * @return OS name system property
     */
    public static String getOSName()
    {
        return System.getProperty("os.name");
    }

    /**
     * @return OS version system property
     */
    private static String getOSVersion()
    {
        return System.getProperty("os.version");
    }

    static
    {
        String osName = getOSName();
        sLog.info("Working out OS from " + osName);

        if (osName == null)
        {
            IS_MAC = false;
            IS_WINDOWS = false;
            IS_WINDOWS_VISTA = false;
            IS_WINDOWS_7 = false;
            IS_WINDOWS_8 = false;
            IS_WINDOWS_10 = false;
        }
        else if (osName.startsWith("Mac"))
        {
            IS_MAC = true;
            IS_WINDOWS = false;
            IS_WINDOWS_VISTA = false;
            IS_WINDOWS_7 = false;
            IS_WINDOWS_8 = false;
            IS_WINDOWS_10 = false;

        }
        else if (osName.startsWith("Windows"))
        {
            IS_MAC = false;
            IS_WINDOWS = true;
            IS_WINDOWS_VISTA = (osName.contains("Vista"));
            IS_WINDOWS_7 = (osName.contains("7"));
            IS_WINDOWS_8 = (osName.contains("8"));
            IS_WINDOWS_10 = (osName.contains("10"));
        }
        else
        {
            IS_MAC = false;
            IS_WINDOWS = false;
            IS_WINDOWS_VISTA = false;
            IS_WINDOWS_7 = false;
            IS_WINDOWS_8 = false;
            IS_WINDOWS_10 = false;
        }

        // arch i.e. x86, amd64
        String osArch = System.getProperty("sun.arch.data.model");

        if(osArch == null)
        {
            IS_32_BIT = true;
            IS_64_BIT = false;
        }
        else if (osArch.contains("32"))
        {
            IS_32_BIT = true;
            IS_64_BIT = false;
        }
        else if (osArch.contains("64"))
        {
            IS_32_BIT = false;
            IS_64_BIT = true;
        }
        else
        {
            IS_32_BIT = false;
            IS_64_BIT = false;
        }

        // OS && arch
        IS_MAC32 = IS_MAC && IS_32_BIT;
        IS_MAC64 = IS_MAC && IS_64_BIT;
        IS_WINDOWS32 = IS_WINDOWS && IS_32_BIT;
        IS_WINDOWS64 = IS_WINDOWS && IS_64_BIT;
    }

    /**
     * Sets sytem property to show the Java app's Mac dock icon and menu bar.
     *
     * By default, we hide the Java app's Mac dock icon and menu bar.
     * This method is used if we ever want to show that UI.
     * Note that calling this method won't instantly cause the icon/menu bar to appear.
     * As far as we know, changes to this property are picked up when a new Java process is
     * started or when the Swing classes are loaded by the classloader. In practice, that
     * means this method will only have an effect if called before the UIService starts.
     *
     */
    public static void showMacDockAndMenu()
    {
        if (IS_MAC)
        {
            sLog.info("Showing Java app dock icon and menu bar on Mac");

            // False shows the UI; true hides the UI.
            System.setProperty("apple.awt.UIElement", "false");
        }
    }

    /**
     * Returns a MacOSVersion representing the running Mac version.
     *
     * @return MacOSVersion representing the current Mac version. If we are on
     *      windows, this returns null.
     */
    public static MacOSVersion getMacVersion()
    {
        MacOSVersion version = null;
        if (IS_MAC)
        {
            version = new MacOSVersion(getOSVersion());
        }

        return version;
    }

    public static class MacOSVersion
    {
        public static final MacOSVersion EL_CAPITAN_10_11 =
                new MacOSVersion("10.11");

        // Big Sur is supposed to be version 11 but the
        // Beta OS is currently reporting itself as 10.16.
        public static final MacOSVersion BIG_SUR_10_16 =
                new MacOSVersion("10.16");

        // The mac version as a string.
        private String macOSVersionString;

        public MacOSVersion(String versionString)
        {
            macOSVersionString = versionString;
        }

        /**
         * Returns the Mac version as a string.
         *
         * @return Mac version string
         */
        public String getString()
        {
            return macOSVersionString;
        }

        /**
         * Whether this Mac version is greater than or equal to the other Mac
         * version.
         *
         * @param other MacOSVersion to compare with
         * @return true only if this Mac version is greater than or equal to the
         *      input Mac version.
         */
        public boolean isGreaterOrEqual(MacOSVersion other)
        {
            return (compareVersion(this, other) >= 0);
        }

        /**
         * Compares 2 MacOsVersions an returns an integer depending on which one
         * is larger: 1 if version1 is greater than version2, 0 if version1 is
         * equal to version2, -1 if version1 is less than version2
         *
         * If a version contains a part with non numbers, this part will be
         * assumed as 0 in the comparison. For example, 10.abc is treated as
         * 10.0 in the comparison.
         *
         * @param version1 MacOSVersion to compare
         * @param version2 MacOSVersion to compare
         * @return an integer:
         *    1 if version1 is greater than version2
         *    0 if version1 is equal to version2
         *   -1 if version1 is less than version2
         */
        private static int compareVersion(MacOSVersion version1,
            MacOSVersion version2)
        {
            // Split the version strings by '.'
            String[] version1Parts = version1.getString().split("\\.");
            String[] version2Parts = version2.getString().split("\\.");

            int maxLength = (version1Parts.length > version2Parts.length ?
                version1Parts.length : version2Parts.length);

            // This is what we return. This defaults to 0, meaning that the
            // versions are equal. If the comparison finds this is not true,
            // this value will get changed to 1 or -1.
            int result = 0;

            // Compare the version numbers part by part from the left
            for(int i = 0; i < maxLength; i++)
            {
                int v1Part;
                int v2Part;

                try
                {
                    // If we have exceeded the length of version1Parts, then
                    // use 0
                    v1Part = (i < version1Parts.length ?
                        Integer.parseInt(version1Parts[i]): 0);
                }
                catch (NumberFormatException e)
                {
                    // Use 0 if something wrong with the format
                    v1Part = 0;
                }

                try
                {
                    // If we have exceeded the length of version2Parts, then
                    // use 0
                    v2Part = (i < version2Parts.length ?
                        Integer.parseInt(version2Parts[i]): 0);
                }
                catch (NumberFormatException e)
                {
                    // Use 0 if something wrong with the format
                    v2Part = 0;
                }

                if (v1Part > v2Part)
                {
                    result = 1;
                    break;
                }
                else if (v2Part > v1Part)
                {
                    result = -1;
                    break;
                }
            }

            return result;
        }
    }
}

class MacVersionInfo
{
    private String name;
    private int majorVersion;

    /**
     * Make a MacVersionInfo if we can parse the version string.
     * <p>
     * The format we parse is (e.g.) MacbookPro13,4 where:<p><ul>
     * <li>name is MacbookPro
     * <li>majorVersion is 13
     * <li>minorVersion is 4 (currently unused)
     * </ul>
     *
     * @param versionString
     * @return
     */
    static Optional<MacVersionInfo> makeVersionInfo(String versionString)
    {
        Pattern macModelPattern = Pattern.compile("(?<name>\\D+)(?<majorVersion>\\d+),(?<minorVersion>\\d+)");
        Matcher m = macModelPattern.matcher(versionString);

        return (m.matches() ? Optional.of(new MacVersionInfo(m.group("name"), Integer.parseInt(m.group("majorVersion")))) : Optional.empty());
    }

    /**
     * Constructor is private - create the MacVersionInfo through makeVersionInfo.
     *
     * @param name
     * @param majorVersion
     */
    private MacVersionInfo(String name, int majorVersion)
    {
        this.name = name;
        this.majorVersion = majorVersion;
    }

    /**
     * @return Whether this version is blocklisted.
     */
    public boolean versionIsBlocklisted()
    {
        return ("MacBookPro".equals(name) && majorVersion >= 13);
    }
}

