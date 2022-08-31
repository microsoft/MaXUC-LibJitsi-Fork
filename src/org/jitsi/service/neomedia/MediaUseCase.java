/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.service.neomedia;

/**
 * The <tt>MediaUseCase</tt> enumeration contains a list of use-cases for media
 * related.
 *
 * @author Sebastien Vincent
 */
public enum MediaUseCase
{
    /**
     * Represents any usecase.
     */
    ANY("any"),

    /**
     * Represents a standard call (voice/video).
     */
    CALL("call");

    /**
     * Name of this <tt>MediaUseCase</tt>.
     */
    private final String mediaUseCase;

    /**
     * Constructor.
     *
     * @param mediaUseCase type of <tt>MediaUseCase</tt> we'd like to create
     */
    MediaUseCase(String mediaUseCase)
    {
        this.mediaUseCase = mediaUseCase;
    }

    /**
     * Returns the name of this <tt>MediaUseCase</tt>.
     *
     * @return the name of this <tt>MediaUseCase</tt>.
     */
    @Override
    public String toString()
    {
        return mediaUseCase;
    }
}
