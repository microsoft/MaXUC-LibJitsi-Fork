/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.service.neomedia;

import java.net.*;

/**
 * The <tt>StreamConnector</tt> interface represents a pair of datagram sockets
 * that a media stream could use for RTP and RTCP traffic.
 * <p>
 * The reason why this media service makes sockets visible through this
 * <tt>StreamConnector</tt> is so that they could be shared among media
 * and other libraries that may need to use them like an ICE implementation for
 * example.
 *
 * @author Emil Ivov
 */
public interface StreamConnector
{
    /**
     * Enumerates the protocols supported by <tt>StreamConnector</tt>.
     */
    enum Protocol
    {
        /**
         * UDP protocol.
         */
        UDP,

        /**
         * TCP protocol.
         */
        TCP
    }

    /**
     * Returns a reference to the <tt>DatagramSocket</tt> that a stream should
     * use for data (e.g. RTP) traffic.
     *
     * @return a reference to the <tt>DatagramSocket</tt> that a stream should
     * use for data (e.g. RTP) traffic or <tt>null</tt> if this
     * <tt>StreamConnector</tt> does not handle UDP sockets.
     */
    DatagramSocket getDataSocket();

    /**
     * Returns a reference to the <tt>DatagramSocket</tt> that a stream should
     * use for control data (e.g. RTCP).
     *
     * @return a reference to the <tt>DatagramSocket</tt> that a stream should
     * use for control data (e.g. RTCP) or <tt>null</tt> if this
     * <tt>StreamConnector</tt> does not handle UDP sockets.
     */
    DatagramSocket getControlSocket();

    /**
     * Returns a reference to the <tt>Socket</tt> that a stream should
     * use for data (e.g. RTP) traffic.
     *
     * @return a reference to the <tt>Socket</tt> that a stream should
     * use for data (e.g. RTP) traffic or <tt>null</tt> if this
     * <tt>StreamConnector</tt> does not handle TCP sockets.
     */
    Socket getDataTCPSocket();

    /**
     * Returns a reference to the <tt>Socket</tt> that a stream should
     * use for control data (e.g. RTCP).
     *
     * @return a reference to the <tt>Socket</tt> that a stream should
     * use for control data (e.g. RTCP) or <tt>null</tt> if this
     * <tt>StreamConnector</tt> does not handle TCP sockets.
     */
    Socket getControlTCPSocket();

    /**
     * Returns the protocol of this <tt>StreamConnector</tt>.
     *
     * @return the protocol of this <tt>StreamConnector</tt>
     */
    Protocol getProtocol();

    /**
     * Releases the resources allocated by this instance in the course of its
     * execution and prepares it to be garbage collected.
     */
    void close();

    /**
     * Notifies this instance that utilization of its <tt>DatagramSocket</tt>s
     * for data and/or control traffic has started.
     */
    void started();

    /**
     * Notifies this instance that utilization of its <tt>DatagramSocket</tt>s
     * for data and/or control traffic has temporarily stopped. This instance
     * should be prepared to be started at a later time again though.
     */
    void stopped();
}
