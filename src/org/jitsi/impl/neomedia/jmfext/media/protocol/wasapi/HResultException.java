/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.jmfext.media.protocol.wasapi;

/**
 * Implements an <tt>Exception</tt> which represents an <tt>HRESULT</tt> value.
 *
 * @author Lyubomir Marinov
 */
public class HResultException
    extends Exception
{
    private static final long serialVersionUID = 0L;

    /**
     * The <tt>HRESULT</tt> value represented by this instance.
     */
    private final int hresult;

    /**
     * Initializes a new <tt>HResultException</tt> which is to represent a
     * specific <tt>HRESULT</tt> value. The detail message of the new instance
     * is derived from the specified <tt>HRESULT</tt> value.
     *
     * @param hresult the <tt>HRESULT</tt> value to be represented by the new
     * instance
     */
    public HResultException(int hresult)
    {
        this(hresult, toString(hresult));
    }

    /**
     * Initializes a new <tt>HResultException</tt> which is to represent a
     * specific <tt>HRESULT</tt> value and have a specific detail message.
     *
     * @param hresult the <tt>HRESULT</tt> value to be represented by the new
     * instance
     * @param message the detail message to initialize the new instance with
     */
    public HResultException(int hresult, String message)
    {
        super(message);

        this.hresult = hresult;
    }

    /**
     * Gets the <tt>HRESULT</tt> value represented by this instance.
     *
     * @return the <tt>HRESULT</tt> value represented by this instance
     */
    public int getHResult()
    {
        return hresult;
    }

    /**
     * Returns a <tt>String</tt> representation of a specific
     * <tt>HRESULT</tt> value.
     *
     * @param hresult the <tt>HRESULT</tt> value of which a <tt>String</tt>
     * representation is to be returned
     * @return a <tt>String</tt> representation of the specified
     * <tt>hresult</tt>
     */
    public static String toString(int hresult)
    {
        return "0x" + Long.toHexString(hresult & 0xffffffffL);
    }
}
