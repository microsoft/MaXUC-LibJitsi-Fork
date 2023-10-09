/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.avfoundation;

/**
 * Defines an <tt>Exception</tt> which reports an <tt>NSError</tt>.
 *
 * @author Lyubomir Marinov
 */
public class NSErrorException
    extends Exception
{
    private static final long serialVersionUID = 0L;

    /**
     * The <tt>NSError</tt> reported by this instance.
     */
    private final NSError error;

    /**
     * Initializes a new <tt>NSErrorException</tt> instance which is to report a
     * specific <tt>NSError</tt>.
     *
     * @param error the <tt>NSError</tt> to be reported by the new instance
     */
    public NSErrorException(NSError error)
    {
        this.error = error;
    }

    /**
     * Gets the <tt>NSError</tt> reported by this instance.
     *
     * @return the <tt>NSError</tt> reported by this instance
     */
    public NSError getError()
    {
        return error;
    }
}
