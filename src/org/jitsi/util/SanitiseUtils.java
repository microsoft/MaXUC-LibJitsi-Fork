// Copyright (c) Microsoft Corporation. All rights reserved.
package org.jitsi.util;

import static java.util.stream.Collectors.joining;

import org.apache.commons.text.StringEscapeUtils;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.simple.JSONArray;
import org.json.simple.JSONAware;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 * Generic methods for sanitising of strings, string representation of objects or JSON.
 * Substrings to sanitise are defined by regex patterns.
 * Methods support sanitising the whole match or just a part of it.
 * If a pattern has a regex group definition, only the group will be sanitised.
 * For example, a regex checking "property=(value)" would match with "property=value",
 * where group(0) is "property=value", group(1) is "value" - the only part that will be sanitised.
 * If there is no group definition, the whole match will be sanitised.
 * In the case of JSON values, we sanitise or remove data based on key-value pairs.
 */
public final class SanitiseUtils
{
    /**
     * Value returned if string or object to sanitise is null.
     */
    public static final String NULL_STRING = "null";

    private static final String REDACTED = "<redacted>";

    /** Standard logger for the class. */
    private static final Logger logger = Logger.getLogger(SanitiseUtils.class);

    /**
     * Prevent instantiation.
     */
    private SanitiseUtils()
    {
    }

    /**
     * Returns a string with sanitised value if it matches the provided patten.
     * All pattern matches will be hashed using {@link Hasher#logHasher(Object)}.
     *
     * @param original original string
     * @param pattern patten to match
     * @return sanitised string
     */
    public static String sanitise(final String original, final Pattern pattern)
    {
        return sanitise(original, pattern, Hasher::logHasher);
    }

    /**
     * Returns a string with sanitised value if it matches the provided patten.
     * All pattern matches will be sanitised using the provided function.
     *
     * @param original original string
     * @param pattern patten to match
     * @param sanitiser function to sanitise found string
     * @return sanitised string
     */
    public static String sanitise(final String original, final Pattern pattern,
                                  final Function<String, String> sanitiser)
    {
        return sanitiseFirstPatternMatch(original, List.of(pattern), sanitiser);
    }

    /**
     * Returns a string with sanitised value if it matches one of the patterns provided in the collection.
     * Please use sanitiseAllMatchedPatterns method if all the matches should be sanitised.
     * Input string will be sanitised for the first matched pattern only.
     * All matches will be sanitised using the provided function.
     *
     * @param original original string
     * @param patterns collection of patterns to match
     * @param sanitiser function to sanitise found string
     * @return sanitised string
     */
    public static String sanitiseFirstPatternMatch(final String original, final Collection<Pattern> patterns,
                                                   final Function<String, String> sanitiser)
    {
        return sanitiseFirstPatternMatch(original, patterns, sanitiser, () -> original);
    }

    /**
     * Returns a string with sanitised value if it matches one of the patterns provided in the collection.
     * Please use sanitiseAllMatchedPatterns method if all the matches should be sanitised.
     * Input string will be sanitised for the first matched pattern only.
     * All matches will be sanitised using the provided function.
     *
     * @param original original string
     * @param patterns collection of patterns to match
     * @param sanitiser function to sanitise found string
     * @param noMatchSanitiser function to sanitise if no match found
     * @return sanitised string
     */
    public static String sanitiseFirstPatternMatch(final String original, final Collection<Pattern> patterns,
                                                   final Function<String, String> sanitiser,
                                                   final Supplier<String> noMatchSanitiser)
    {
        if (StringUtils.isNullOrEmpty(original))
        {
            return original;
        }

        return patterns.stream()
                .map(pattern -> pattern.matcher(original))
                .filter(Matcher::find)
                .map(matcher -> sanitiseAllMatches(matcher, sanitiser))
                .findFirst()
                .orElseGet(noMatchSanitiser);
    }

    /**
     * Returns a string with sanitised value if it matches the provided collection of patterns.
     * Input string will be sanitised for all the matched patterns.
     * All matches will be sanitised using the provided function.
     *
     * @param original original string
     * @param patterns collection of patterns to match
     * @param sanitiser function to sanitise found string
     * @return sanitised string
     */
    public static String sanitiseAllMatchedPatterns(final String original, final Collection<Pattern> patterns,
                                                  final Function<String, String> sanitiser)
    {
        String hashedString = original;
        for (Pattern pattern: patterns)
        {
            hashedString = sanitise(hashedString, pattern, sanitiser);
        }
        return hashedString;
    }

    /**
     * Apply the provided sanitiser function for every item in the collection.
     * @param collectionToSanitise objects to sanitise
     * @param sanitiser function to sanitise an object
     * @return comma-separated sanitised strings from the input collection
     */
    public static <T> String sanitiseValuesInList(final Collection<T> collectionToSanitise,
                                                  final Function<T, String> sanitiser)
    {
        return Optional.ofNullable(collectionToSanitise)
                .map(col -> col.stream()
                        .map(sanitiser)
                        .collect(joining(", ")))
                .orElse(NULL_STRING);
    }

    /**
     * Sanitises an object using the provided sanitiser returning a string "null" if it's null.
     * @param obj nullable obj
     * @param sanitiser function to sanitise a string
     */
    public static <T> String sanitiseNullable(final T obj, final Function<T, String> sanitiser) {
        return Optional.ofNullable(obj)
                .map(sanitiser)
                .orElse(NULL_STRING);
    }

    /**
     * Returns JSON data as a string after it has been sanitised.
     * For example, for incoming JSON data with objects of the form
     * {"Admin":"admin_value","Dept":"dept_value","Test":"test_value"...},
     * to hash "admin_value", but remove "dept_value",
     * "Admin" would be passed in the set of hashKeys, and "Dept" would be in removeKeys.
     *
     * Note that the whole JSON Object corresponding to Dept will be removed in this case, leaving:
     * {"Admin":"HASH-VALUE(hash)","Test":"test_value"...}
     *
     * @param stringValue string to be parsed to a JSON object.
     * @param hashKeys JSON object keys whose associated values need to be hashed.
     * @param removeKeys JSON object keys whose associated values need to be removed.
     * @return string with matched values hashed or removed.
     */
    public static String sanitiseJSON(final String stringValue,
                                      final Set<String> hashKeys,
                                      final Set<String> removeKeys)
    {
        return sanitiseJSON(stringValue,
                            hashKeys,
                            removeKeys,
                            Hasher::logHasher);
    }

    /**
     * Option to pass a generic Object rather than a String.
     * @param value object to be parsed to a JSON object.
     * @param hashKeys JSON object keys whose associated values need to be hashed.
     * @param removeKeys JSON object keys whose associated values need to be removed.
     * @return string with matched values hashed or removed.
     */
    public static String sanitiseJSON(final JSONAware value,
                                      final Set<String> hashKeys,
                                      final Set<String> removeKeys)
    {
        return sanitiseNullable(value, obj -> sanitiseJSON(obj, hashKeys, removeKeys, Hasher::logHasher));
    }

    /**
     * Returns JSON data as a string after it has been sanitised.
     * For example, for incoming JSON data with objects of the form
     * {"Admin":"admin_value","Dept":"dept_value","Test":"test_value"...},
     * to hash "admin_value", but remove "dept_value",
     * "Admin" would be passed in the set of hashKeys, and "Dept" would be in removeKeys.
     *
     * Note that the whole JSON Object corresponding to Dept will be removed in this case, leaving:
     * {"Admin":"HASH-VALUE(hash)","Test":"test_value"...}
     *
     * @param stringValue string to be parsed to a JSON object.
     * @param hashKeys JSON object keys whose associated values need to be hashed.
     * @param removeKeys JSON object keys whose associated values need to be removed.
     * @param sanitiser a custom sanitiser function to handle edge cases (i.e. in JSON
     *                  messages with multiple identical and ambiguous keys, such as "_").
     *                  Most users of this class will not need this; the version
     *                  {@link #sanitiseJSON(String, Set, Set)}}
     *                  should suffice for the majority of cases.
     * @return string with matched values hashed or removed.
     */
    public static String sanitiseJSON(final String stringValue,
                                      final Set<String> hashKeys,
                                      final Set<String> removeKeys,
                                      final Function<Object, String> sanitiser)
    {
        if (stringValue == null)
        {
            return NULL_STRING;
        }
        else if (stringValue.isEmpty())
        {
            return "";
        }

        try
        {
            final JSONParser parser = new JSONParser();
            final Object jsonObject = parser.parse(stringValue);

            if (jsonObject instanceof JSONAware)
            {
                return sanitiseJSON((JSONAware) jsonObject,
                                    hashKeys,
                                    removeKeys,
                                    sanitiser);
            }
        }
        catch (ParseException ex)
        {
            logger.error("Failed to parse the input as JSON.", ex);
        }

        return stringValue;
    }

    /**
     * Option to pass a generic Object rather than a String.
     *
     * @param value object to be parsed to a JSON object
     * @param hashKeys keys to hash
     * @param removeKeys keys to remove
     * @param sanitiser a custom sanitiser function to handle edge cases (i.e. in JSON
     *                  messages with multiple identical and ambiguous keys, such as "_").
     *                  Most users of this class will not need this; the version
     *                  {@link #sanitiseJSON(String, Set, Set)}}
     *                  should suffice for the majority of cases.
     * @return string with matched values hashed or removed.
     */
    public static String sanitiseJSON(final JSONAware value,
                                      final Set<String> hashKeys,
                                      final Set<String> removeKeys,
                                      final Function<Object, String> sanitiser)
    {
        final Object sanitised = sanitiseJSONArrayOrObject(value, hashKeys, removeKeys, sanitiser);
        return sanitised instanceof JSONObject ?
                StringEscapeUtils.unescapeJson(((JSONObject) sanitised).toJSONString()) :
                String.valueOf(sanitised);
    }

    /**
     * Removes PII from any filepath(s) in a string by removing the username.
     *
     * Eg "C:\Users\USERNAME\folder;C:\Users\USERNAME" will be
     * replaced with "C:\Users\<redacted>\folder;C:\Users\<redacted>"
     *
     * If passed something where the username is the last part of the filepath, any following
     * information will be eaten up to the next delimiter
     * example: "C:\Users\USERNAME is a file path" will become "C:\Users\<redacted>"
     *
     * @param stringToSanitise The string to be treated.
     * @return A string with the relevant PII removed
     */
    public static String sanitisePath(String stringToSanitise)
    {
        if (stringToSanitise == null) {
            return null;
        }
        // replace anything following the string "users/" or "users\" (case
        // insensitive) up to the next /\;:
        // \\\\ makes one \ as you have to escape \ once in regex, and each \
        // once in java
        return stringToSanitise.replaceAll("(?i)(?<=users[/\\\\])[^/\\\\;:]+",
                                           REDACTED);
    }

    /**
     * Helper method used to iterate through objects (JSON Arrays or JSON Objects) in
     * input JSON data. If the supplied object is neither of these types i.e. we have found a
     * String, simply return the object to the caller.
     */
    @SuppressWarnings("unchecked")
    private static Object sanitiseJSONArrayOrObject(final JSONAware jsonAware,
                                                    final Set<String> hashKeys,
                                                    final Set<String> removeKeys,
                                                    final Function<Object, String> sanitiser)
    {
        if (jsonAware instanceof JSONObject)
        {
            return sanitiseJSONObject((JSONObject) jsonAware, hashKeys, removeKeys, sanitiser);
        }
        else if (jsonAware instanceof JSONArray)
        {
            final JSONArray jsonArray = (JSONArray) jsonAware;

            final JSONArray sanitisedArray = new JSONArray();
            for (Object element : jsonArray)
            {
                final Object sanitisedObject = sanitiseJSONArrayOrObject((JSONAware) element,
                                                                         hashKeys,
                                                                         removeKeys,
                                                                         sanitiser);
                if (sanitisedObject != null)
                {
                    sanitisedArray.add(sanitisedObject);
                }
            }

            return sanitisedArray;
        }

        return jsonAware;
    }

    /**
     * Checks each key of the supplied JSON Object to see if its value should be
     * hashed/removed.
     */
    @SuppressWarnings("unchecked")
    private static JSONObject sanitiseJSONObject(final JSONObject jsonObject,
                                                 final Set<String> hashKeys,
                                                 final Set<String> removeKeys,
                                                 final Function<Object, String> sanitiser)
    {
        final JSONObject result = new JSONObject();
        for (Object entryKey : jsonObject.keySet())
        {
            final String key = (String) entryKey;
            Object value = jsonObject.get(entryKey);

            if (hashKeys.contains(key))
            {
                value = sanitiser.apply(value);
            }
            else if (removeKeys.contains(key))
            {
                continue;
            }
            else if (value instanceof JSONAware)
            {
                value = sanitiseJSONArrayOrObject((JSONAware) value,
                                                  hashKeys,
                                                  removeKeys,
                                                  sanitiser);
            }

            result.put(entryKey, value);
        }

        return result;
    }

    private static String sanitiseAllMatches(final Matcher matcher, final Function<String, String> sanitiser)
    {
        final StringBuilder stringToReturn = new StringBuilder();
        do
        {
            final String stringToSanitise = sanitiseMatched(matcher, sanitiser);
            matcher.appendReplacement(stringToReturn, stringToSanitise);
        } while (matcher.find());

        matcher.appendTail(stringToReturn);
        return stringToReturn.toString();
    }

    /**
     * Sanitises (sub)strings found in the matcher depending on regex group presence.
     */
    private static String sanitiseMatched(final Matcher matcher, final Function<String, String> sanitiser)
    {
        String stringToSanitise = matcher.group(0);
        if (matcher.groupCount() >= 1)
        {
            final String valueToSanitise = matcher.group(1);
            stringToSanitise = stringToSanitise.replace(valueToSanitise, sanitiser.apply(valueToSanitise));
        }
        else
        {
            stringToSanitise = sanitiser.apply(stringToSanitise);
        }
        return stringToSanitise;
    }
}