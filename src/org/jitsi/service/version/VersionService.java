/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.service.version;

/**
 * The version service keeps track of the SIP Communicator version that we are
 * currently running. Other modules (such as a Help->About dialog) query and
 * use this service in order to show the current application version.
 *
 * @author Emil Ivov
 */
public interface VersionService
{
    /**
     * Returns a <tt>Version</tt> object containing version details of the SIP
     * Communicator version that we're currently running.
     *
     * @return a <tt>Version</tt> object containing version details of the SIP
     * Communicator version that we're currently running.
     */
    Version getCurrentVersion();

    /**
     * Returns a Version instance corresponding to the <tt>version</tt> string.
     *
     * @param version a version String that we have obtained by calling a
     * <tt>Version.toString()</tt> method.
     *
     * @return the <tt>Version</tt> object corresponding to the <tt>version</tt>
     * string.
     */
    Version parseVersionString(String version);

    /**
     * Returns true if the client is running a version below the minimum
     * allowed version specified in the current subscriber's config (false if
     * no such minimum version is provided in the config).
     */
    boolean isOutOfDate();
}
