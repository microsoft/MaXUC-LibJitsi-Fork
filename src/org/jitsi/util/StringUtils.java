/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.util;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/**
 * Implements utility functions to facilitate work with <tt>String</tt>s.
 *
 * @author Grigorii Balutsel
 * @author Emil Ivov
 */
public final class StringUtils
{
    private static String lineSeparator = System.getProperty("line.separator");

    /**
     * Prevents the initialization of <tt>StringUtils</tt> instances because the
     * <tt>StringUtils</tt> class implements utility function only.
     */
    private StringUtils()
    {
    }

    /**
     * Gets a <tt>String</tt> which represents the conversion of a specific
     * camel-case formatted <tt>String</tt> to a more human-readable form (i.e.
     * with spaces between the words).
     *
     * @param camelCase a camel-case (or Pascal-case) formatted <tt>String</tt>
     * from which a human-readable <tt>String</tt> is to be constructed
     * @return a <tt>String</tt> which represents the conversion of the specified
     * camel-case formatted <tt>String</tt> to a more human-readable form
     */
    public static String convertCamelCaseToDisplayString(String camelCase)
    {
        if (camelCase == null)
            return null;

        int camelCaseLength = camelCase.length();

        if (camelCaseLength == 0)
            return camelCase;

        int wordEndIndex = 0;
        int wordBeginIndex = 0;
        StringBuilder display = new StringBuilder();

        for (; wordEndIndex < camelCaseLength; wordEndIndex++)
        {
            char ch = camelCase.charAt(wordEndIndex);

            if (Character.isUpperCase(ch) && (wordBeginIndex != wordEndIndex))
            {
                display.append(
                        camelCase.substring(wordBeginIndex, wordEndIndex));
                display.append(' ');
                wordBeginIndex = wordEndIndex;
            }
        }
        if (wordEndIndex >= camelCaseLength)
            display.append(camelCase.substring(wordBeginIndex));
        return display.toString();
    }

    /**
     * Indicates whether string is <tt>null</tt> or empty.
     *
     * @param s the string to analyze.
     * @return <tt>true</tt> if string is <tt>null</tt> or empty.
     */
    public static boolean isNullOrEmpty(String s)
    {
        return isNullOrEmpty(s, true);
    }

    /**
     * Indicates whether string is <tt>null</tt> or empty.
     *
     * @param s    the string to analyze.
     * @param trim indicates whether to trim the string.
     * @return <tt>true</tt> if string is <tt>null</tt> or empty.
     */
    public static boolean isNullOrEmpty(String s, boolean trim)
    {
        if (s == null)
            return true;
        if (trim)
            s = s.trim();
        return s.length() == 0;
    }

    /**
     * Determines whether a specific <tt>String</tt> value equals another
     * <tt>String</tt> value. If the two specified <tt>String</tt> values are
     * equal to <tt>null</tt>, they are considered equal.
     *
     * @param s1 the first <tt>String</tt> value to check for value equality
     * with the second
     * @param s2 the second <tt>String</tt> value to check for value equality
     * with the first
     * @return <tt>true</tt> if the two specified <tt>Sting</tt> values are
     * equal; otherwise, <tt>false</tt>
     */
    public static boolean isEquals(String s1, String s2)
    {
        return Objects.equals(s1, s2);
    }

    /**
     * Creates <tt>InputStream</tt> from the string in UTF8 encoding.
     *
     * @param string the string to convert.
     * @return the <tt>InputStream</tt>.
     * @throws UnsupportedEncodingException if UTF8 is unsupported.
     */
    public static InputStream fromString(String string)
            throws UnsupportedEncodingException
    {
        return fromString(string, "UTF-8");
    }

    /**
     * Creates <tt>InputStream</tt> from the string in the specified encoding.
     *
     * @param string   the string to convert.
     * @param encoding the encoding
     * @return the <tt>InputStream</tt>.
     * @throws UnsupportedEncodingException if the encoding is unsupported.
     */
    public static InputStream fromString(String string, String encoding)
            throws UnsupportedEncodingException
    {
        return new ByteArrayInputStream(string.getBytes(encoding));
    }

    /**
     * Returns the UTF8 bytes for <tt>string</tt> and handles the unlikely case
     * where UTF-8 is not supported.
     *
     * @param string the <tt>String</tt> whose bytes we'd like to obtain.
     * @return <tt>string</tt>'s bytes.
     */
    public static byte[] getUTF8Bytes(String string)
    {
        return string.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Converts <tt>string</tt> into an UTF8 <tt>String</tt> and handles the
     * unlikely case where UTF-8 is not supported.
     *
     * @param bytes the <tt>byte</tt> array that we'd like to convert into a
     * <tt>String</tt>.
     * @return the UTF-8 <tt>String</tt>.
     */
    public static String getUTF8String(byte[] bytes)
    {
        try
        {
            return new String(bytes, "UTF-8");
        }
        catch(UnsupportedEncodingException exc)
        {
            // shouldn't happen. UTF-8 is always supported, anyways ... if
            // this happens, we'll cheat
            return new String(bytes);
        }
    }

    /**
     * Indicates if the given string is composed only of digits or not.
     *
     * @param string the string to check
     * @return <tt>true</tt> if the given string is composed only of digits;
     * <tt>false</tt>, otherwise
     */
    public static boolean isNumber(String string)
    {
        for (int i = 0; i < string.length(); i++)
        {
            //If we find a non-digit character we return false.
            if (!Character.isDigit(string.charAt(i)))
                return false;
        }
        return true;
    }

    /**
     * Indicates whether the given string contains any letters.
     *
     * @param string the string to check for letters
     * @return <tt>true</tt> if the given string contains letters;
     * <tt>false</tt>, otherwise
     */
    public static boolean containsLetters(String string)
    {
        for (int i = 0; i < string.length(); i++)
        {
            if (Character.isLetter(string.charAt(i)))
                return true;
        }
        return false;
    }

    /**
     * Removes all spaces from the given string and returns a concatenated
     * result string.
     *
     * @param string the string to concatenate
     * @return the concatenated string
     */
    public static String concatenateWords(String string)
    {
        char[] chars = string.toCharArray();
        StringBuilder buff = new StringBuilder(chars.length);

        for (char ch : chars)
        {
            if (ch != ' ')
                buff.append(ch);
        }
        return buff.toString();
    }

    /**
     * Concatenate the string representations of the contents of a given array,
     * with items separated by the given separator, which can be empty.
     * @param values The values to concatenate
     * @param sep The separating string
     * @return The concatenated string
     */
    public static String join(Object[] values, String sep)
    {
        StringBuilder buff = new StringBuilder();

        for (int idx = 0; idx < values.length - 1; idx++)
        {
            buff.append(values[idx]);
            buff.append(sep);
        }

        buff.append(values[values.length - 1]);

        return buff.toString();
    }

    /**
     * Concatenate the string representations of the contents of a given int
     * array, with items separated by the given separator, which can be empty.
     * @param values The ints to concatenate
     * @param sep The separating string
     * @return The concatenated string
     */
    public static String join(int[] values, String sep)
    {
        StringBuilder buff = new StringBuilder();

        for (int idx = 0; idx < values.length - 1; idx++)
        {
            buff.append(values[idx]);
            buff.append(sep);
        }

        buff.append(values[values.length - 1]);

        return buff.toString();
    }

    /**
     * Concatenate the 0th, 2nd, 4th, etc. elements of a list of String values,
     * with elements separated by a given separator, which can be empty.
     * @param sep The separator
     * @param list The list of strings
     * @return The concatenated string
     */
    public static String joinEvenIndexedElements(String sep, List<String> list)
    {
        if (list.isEmpty())
        {
            return "";
        }

        StringBuilder sb = new StringBuilder(list.get(0));
        for (int i = 2; i < list.size(); i += 2)
        {
            sb.append(sep);
            sb.append(list.get(i));
        }

        return sb.toString();
    }

    /**
     * Initializes a new <tt>String</tt> instance by decoding a specified array
     * of bytes (mostly used by JNI).
     *
     * @param bytes the bytes to be decoded into characters/a new
     * <tt>String</tt> instance
     * @return a new <tt>String</tt> instance whose characters were decoded from
     * the specified <tt>bytes</tt>
     */
    public static String newString(byte[] bytes)
    {
        if ((bytes == null) || (bytes.length == 0))
            return null;
        else
        {
            Charset defaultCharset = Charset.defaultCharset();
            String charsetName
                = (defaultCharset == null) ? "UTF-8" : defaultCharset.name();

            try
            {
                return new String(bytes, charsetName);
            }
            catch (UnsupportedEncodingException ueex)
            {
                return new String(bytes);
            }
        }
    }

    /**
     * Given a string, truncate it to the given length, leaving it unmodified
     * if the string is already shorter than that length.
     * @param string The string to truncate.
     * @param length The length to truncate the string to.
     * @return The truncated string.
     */
    public static String truncate(String string, int length)
    {
        return (string.length() > length) ? string.substring(0, length) : string;
    }

    /**
     * @return true iff the supplied string contains a newline character.
     */
    public static boolean containsNewLine(String string)
    {
        return string.contains(lineSeparator) || string.contains("\n") || string.contains("\r");
    }
}
