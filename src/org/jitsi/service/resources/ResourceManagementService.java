/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
// Portions (c) Microsoft Corporation. All rights reserved.
package org.jitsi.service.resources;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * The Resource Management Service gives easy access to
 * common resources for the application including texts, images, sounds and
 * some configurations.
 *
 * @author Damian Minkov
 * @author Adam Netocny
 */
public interface ResourceManagementService
{
    // Color pack methods
    /**
     * Returns the int representation of the color corresponding to the
     * given key.
     *
     * @param key The key of the color in the colors properties file.
     * @return the int representation of the color corresponding to the
     * given key.
     */
    int getColor(String key);

    /**
     * Returns the int representation of the color corresponding to the
     * given key.
     *
     * @param key The key of the color in the colors properties file.
     * @param defaultValue The value to use if the key does not exist
     * @return the int representation of the color corresponding to the
     * given key.
     */
    int getColor(String key, int defaultValue);

    /**
     * Returns the int representation of the color corresponding to the
     * given key.
     *
     * @param key The key of the color in the colors properties file.
     * @param defaultValue The value to use if the key does not exist
     * @param errorIfMissing Whether to log an error message if this
     *                       colour is missing from the config.  True
     *                       for most, but there are a few optional
     *                       overrides.
     * @return the int representation of the color corresponding to the
     * given key.
     */
    int getColor(String key, int defaultValue, boolean errorIfMissing);

    /**
     * Returns the string representation of the color corresponding to the
     * given key.
     *
     * @param key The key of the color in the colors properties file.
     * @return the string representation of the color corresponding to the
     * given key.
     */
    String getColorString(String key);

    /**
     * Returns the string representation of the color corresponding to the
     * given key.
     *
     * @param key The key of the color in the colors properties file.
     * @param defaultValue The value to use if the key does not exist
     * @return the string representation of the color corresponding to the
     * given key.
     */
    String getColorString(String key, String defaultValue);

    /**
     * Returns the <tt>InputStream</tt> of the image corresponding to the given
     * path.
     *
     * @param path The path to the image file.
     * @return the <tt>InputStream</tt> of the image corresponding to the given
     * path.
     */
    InputStream getImageInputStreamForPath(String path);

    /**
     * Returns the <tt>URL</tt> of the image corresponding to the given key.
     *
     * @param urlKey The identifier of the image in the resource properties file.
     * @return the <tt>URL</tt> of the image corresponding to the given key
     */
    URL getImageURL(String urlKey);

    /**
     * Returns the <tt>URL</tt> of the image corresponding to the given path.
     *
     * @param path The path to the given image file.
     * @return the <tt>URL</tt> of the image corresponding to the given path.
     */
    URL getImageURLForPath(String path);

    /**
     * Returns the image path corresponding to the given key.
     *
     * @param key The identifier of the image in the resource properties file.
     * @return the image path corresponding to the given key.
     */
    String getImagePath(String key);

    // Language pack methods
    /**
     * Default Locale config string.
     */
    String DEFAULT_LOCALE_CONFIG =
            "net.java.sip.communicator.service.resources.DefaultLocale";

    /**
     * All the locales in the language pack.
     * @return all the locales this Language pack contains.
     */
    Iterator<Locale> getAvailableLocales();

    /**
     * Returns an internationalized string corresponding to the given key.
     *
     * @param key The identifier of the string in the resources properties file.
     * @return An internationalized string corresponding to the given key.
     */
    String getI18NString(String key);

    /**
     * Returns an internationalized string corresponding to the given key.
     *
     * @param key The identifier of the string in the resources properties file.
     * @param locale The locale.
     * @return An internationalized string corresponding to the given key and
     * given locale.
     */
    String getI18NString(String key, Locale locale);

    /**
     * Returns an internationalized string corresponding to the given key.
     *
     * @param key The identifier of the string in the resources properties file.
     * @param params An array of parameters to be replaced in the returned
     * string.
     * @return An internationalized string corresponding to the given key and
     * given locale.
     */
    String getI18NString(String key, String[] params);

    /**
     * Returns an internationalized string corresponding to the given key,
     * taking account of a varying quantity that may need to be applied.
     * The quantity is accessed as just SINGULAR or PLURAL, which is applicable
     * to English, but is a simplification that may not apply to other languages.
     *
     * @param key The identifier of the string.
     * @param quantity the quantity to be considered
     * @param params the parameters to pass to the localized string
     * @return An internationalized string corresponding to the given key.
     */
    String getI18NQuantityString(String key, int quantity, String[] params);

    /**
     * Returns an internationalized string corresponding to the given key.
     *
     * @param key The identifier of the string in the resources properties file.
     * @param params An array of parameters to be replaced in the returned
     * string.
     * @param locale The locale.
     * @return An internationalized string corresponding to the given key.
     */
    String getI18NString(String key, String[] params, Locale locale);

    /**
     * Returns an internationalized string corresponding to the given key.
     *
     * @param key The identifier of the string in the resources properties file.
     * @return An internationalized string corresponding to the given key.
     */
    char getI18nMnemonic(String key);

    /**
     * Returns an internationalized string corresponding to the given key.
     *
     * @param key The key of the string.
     * @param l The locale.
     * @return An internationalized string corresponding to the given key.
     */
    char getI18nMnemonic(String key, Locale l);

    // Settings pack methods

    /**
     * Returns an InputStream for the setting corresponding to the given key.
     * Used when the setting is an actual file.
     *
     * @param streamKey The key of the setting.
     * @return InputStream to the corresponding resource.
     */
    InputStream getSettingsInputStream(String streamKey);

    /**
     * Returns a stream from a given identifier, obtained through the class
     * loader of the given resourceClass.
     *
     * @param streamKey The identifier of the stream.
     * @param resourceClass the resource class through which the resource would
     * be obtained
     * @return The stream for the given identifier.
     */
    InputStream getSettingsInputStream(String streamKey,
                                       Class<?> resourceClass);

    /**
     * Returns the int value of the corresponding configuration key.
     *
     * @param key The identifier of the string in the resources properties file.
     * @return the int value of the corresponding configuration key.
     */
    String getSettingsString(String key);

    /**
     * Returns the int value of the corresponding configuration key.
     *
     * @param key The identifier of the string in the resources properties file.
     * @return the int value of the corresponding configuration key.
     */
    int getSettingsInt(String key);

    /**
     * Returns an int stored in config, scaled according to the current display
     * settings. Should be used when retrieving default of previous sizes in
     * config
     *
     * @param key The identifier of the string in the resources properties file.
     * @return the int value of the corresponding configuration key, scaled
     *         according to the current display settings
     */
    int getScaledSize(String key);

    // Sound pack methods

    /**
     * Returns an url for the sound resource corresponding to the given path.
     *
     * @param path The path to the sound resource.
     * @return Url to the corresponding resource.
     */
    URL getSoundURLForPath(String path);

    /**
     * Returns the path of the sound corresponding to the given
     * property key.
     *
     * @param soundKey The key of the sound.
     * @return the path of the sound corresponding to the given
     * property key.
     */
    String getSoundPath(String soundKey);

    /**
     * Constructs an <tt>ImageIcon</tt> from the specified image ID and returns
     * it.
     *
     * @param imageID The identifier of the image icon.
     * @return An <tt>ImageIcon</tt> containing the image icon with the given
     * identifier.
     */
    ImageIconFuture getImage(String imageID);

    /**
     * Constructs a <tt>BufferedImage</tt> from the specified image ID and
     * returns it.
     *
     * @param imageID The identifier of the image.
     * @return A <tt>BufferedImage</tt> containing the image with the given
     * identifier.
     */
    BufferedImageFuture getBufferedImage(String imageID);

    /**
     * Loads an image from a given path.
     *
     * @param imagePath The path of the image.
     * @return The image for the given identifier.
     */
    BufferedImageFuture getBufferedImageFromPath(String imagePath);

    /**
     * Constructs an <tt>ImageIcon</tt> from the specified image path
     * @param filepath The filepath of the image icon.
     * @return An <tt>ImageIcon</tt> containing the image icon with the given
     * filepath.
     */
    ImageIconFuture getImageFromPath(String filepath);

    /**
     * Gets an ArrayList of URLs (as Strings) of the files matching pattern in
     * the directory specified by path.
     * @param path The path to the given directory
     * @param pattern The file name pattern for selecting entries in the
     * specified path
     * @return An <tt>ArrayList</tt> of String urls representing the contents
     * of the specified directory which match pattern
     */
    ArrayList<String> getUrlsFromDirectory(String path, String pattern);
}
