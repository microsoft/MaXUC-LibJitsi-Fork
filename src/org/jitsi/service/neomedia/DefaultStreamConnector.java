/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
// Portions (c) Microsoft Corporation. All rights reserved.
package org.jitsi.service.neomedia;

import java.net.*;

import org.jitsi.service.configuration.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.util.*;

import net.sf.fmj.media.*;

/**
 * Represents a default implementation of <tt>StreamConnector</tt> which is
 * initialized with a specific pair of control and data <tt>DatagramSocket</tt>s
 * and which closes them (if they exist) when its {@link #close()} is invoked.
 *
 * @author Lubomir Marinov
 */
public class DefaultStreamConnector
    implements StreamConnector
{
    /**
     * The <tt>Logger</tt> used by the <tt>DefaultStreamConnector</tt> class and
     * its instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(DefaultStreamConnector.class);

    /**
     * The default number of binds that a Media Service Implementation should
     * execute in case a port is already bound to (each retry would be on a
     * new random port).
     */
    public static final int BIND_RETRIES_DEFAULT_VALUE = 50;

    /**
     * The name of the property containing the number of binds that a Media
     * Service Implementation should execute in case a port is already
     * bound to (each retry would be on a new port in the allowed boundaries).
     */
    public static final String BIND_RETRIES_PROPERTY_NAME
        = "net.java.sip.communicator.service.media.BIND_RETRIES";

    /**
     * The name of the property that contains the maximum port number that we'd
     * like our RTP managers to bind upon.
     */
    public static final String MAX_PORT_NUMBER_PROPERTY_NAME
        = "net.java.sip.communicator.service.media.MAX_PORT_NUMBER";

    /**
     * The maximum port number <tt>DefaultStreamConnector</tt> instances are to
     * attempt to bind to.
     */
    private static int maxPort = -1;

    /**
     * The name of the property that contains the minimum port number that we'd
     * like our RTP managers to bind upon.
     */
    public static final String MIN_PORT_NUMBER_PROPERTY_NAME
        = "net.java.sip.communicator.service.media.MIN_PORT_NUMBER";

    /**
     * The minimum port number <tt>DefaultStreamConnector</tt> instances are to
     * attempt to bind to.
     */
    private static int minPort = -1;

    /**
     * The timeout for the <tt>DatagramSocket</tt> receive operation.
     */
    private static int SOCKET_RECEIVE_TIMEOUT = 1000;

    /**
     * The local <tt>InetAddress</tt> this <tt>StreamConnector</tt> attempts to
     * bind to on demand.
     */
    private final InetAddress bindAddr;

    /**
     * The <tt>DatagramSocket</tt> that a stream should use for control data
     * (e.g. RTCP) traffic.
     */
    protected DatagramSocket controlSocket;

    /**
     * The <tt>DatagramSocket</tt> that a stream should use for data (e.g. RTP)
     * traffic.
     */
    protected DatagramSocket dataSocket;

    /**
     * Initializes a new <tt>DefaultStreamConnector</tt> instance with no
     * control and data <tt>DatagramSocket</tt>s.
     * <p>
     * Suitable for extenders willing to delay the creation of the control and
     * data sockets. For example, they could override
     * {@link #getControlSocket()} and/or {@link #getDataSocket()} and create
     * them on demand.
     */
    public DefaultStreamConnector()
    {
        this(null, null);
    }

    /**
     * Creates a new <tt>DatagramSocket</tt> instance which is bound to the
     * specified local <tt>InetAddress</tt> and its port is within the range
     * defined by the <tt>ConfigurationService</tt> properties
     * {@link #MIN_PORT_NUMBER_PROPERTY_NAME} and
     * {@link #MAX_PORT_NUMBER_PROPERTY_NAME}. Attempts at most
     * {@link #BIND_RETRIES_PROPERTY_NAME} times to bind.
     *
     * @param bindAddr the local <tt>InetAddress</tt> the new
     * <tt>DatagramSocket</tt> is to bind to
     * @return a new <tt>DatagramSocket</tt> instance bound to the specified
     * local <tt>InetAddress</tt>
     */
    private static synchronized DatagramSocket createDatagramSocket(
            InetAddress bindAddr)
    {
        ConfigurationService cfg = LibJitsi.getConfigurationService();
        int bindRetries = BIND_RETRIES_DEFAULT_VALUE;

        if (cfg != null)
            bindRetries = cfg.global().getInt(BIND_RETRIES_PROPERTY_NAME, bindRetries);
        if (maxPort < 0)
        {
            maxPort = 6000;
            if (cfg != null)
                maxPort = cfg.global().getInt(MAX_PORT_NUMBER_PROPERTY_NAME, maxPort);
        }

        for (int i = 0; i < bindRetries; i++)
        {
            if ((minPort < 0) || (minPort > maxPort))
            {
                minPort = 5000;
                if (cfg != null)
                {
                    minPort
                        = cfg.global().getInt(MIN_PORT_NUMBER_PROPERTY_NAME, minPort);
                }
            }

            int port = minPort++;

            try
            {
                DatagramSocket socket =
                    (bindAddr == null)
                        ? new DatagramSocket(port)
                        : new DatagramSocket(port, bindAddr);
                setSocketTimeout(socket);
                return socket;
            }
            catch (SocketException se)
            {
                logger.warn(
                    "Retrying a bind because of a failure to bind to address "
                    + bindAddr
                    + " and port "
                    + port,
                    se);
            }
        }
        return null;
    }

    /**
     * Sets a timeout for the receive operations made on the given
     * <tt>DatagramSocket</tt>.
     *
     * @param socket the <tt>DatagramSocket</tt> to set the timeout on.
     */
    private static void setSocketTimeout(DatagramSocket socket)
    {
        try
        {
            socket.setSoTimeout(SOCKET_RECEIVE_TIMEOUT);
        }
        catch (SocketException e)
        {
            logger.error("Could not set timeout on socket.", e);
        }
    }

    /**
     * Initializes a new <tt>DefaultStreamConnector</tt> instance with a
     * specific bind <tt>InetAddress</tt>. The new instance is to attempt to
     * bind on demand to the specified <tt>InetAddress</tt> in the port range
     * defined by the <tt>ConfigurationService</tt> properties
     * {@link #MIN_PORT_NUMBER_PROPERTY_NAME} and
     * {@link #MAX_PORT_NUMBER_PROPERTY_NAME} at most
     * {@link #BIND_RETRIES_PROPERTY_NAME} times.
     *
     * @param bindAddr the local <tt>InetAddress</tt> the new instance is to
     * attempt to bind to
     */
    public DefaultStreamConnector(InetAddress bindAddr)
    {
        this.bindAddr = bindAddr;
        Log.logMediaStackObjectStarted(this);
    }

    /**
     * Initializes a new <tt>DefaultStreamConnector</tt> instance which is to
     * represent a specific pair of control and data <tt>DatagramSocket</tt>s.
     *
     * @param dataSocket the <tt>DatagramSocket</tt> to be used for data (e.g.
     * RTP) traffic
     * @param controlSocket the <tt>DatagramSocket</tt> to be used for control
     * data (e.g. RTCP) traffic
     */
    public DefaultStreamConnector(
            DatagramSocket dataSocket,
            DatagramSocket controlSocket)
    {
        this.controlSocket = controlSocket;
        this.dataSocket = dataSocket;
        this.bindAddr = null;

        if (controlSocket != null)
        {
            setSocketTimeout(controlSocket);
        }

        if (dataSocket != null)
        {
            setSocketTimeout(dataSocket);
        }

        Log.logMediaStackObjectStarted(this);
    }

    /**
     * Releases the resources allocated by this instance in the course of its
     * execution and prepares it to be garbage collected.
     *
     * @see StreamConnector#close()
     */
    public void close()
    {
        Log.logMediaStackObjectStopped(this);
        if (controlSocket != null)
            controlSocket.close();
        if (dataSocket != null)
            dataSocket.close();
    }

    /**
     * Returns a reference to the <tt>DatagramSocket</tt> that a stream should
     * use for control data (e.g. RTCP) traffic.
     *
     * @return a reference to the <tt>DatagramSocket</tt> that a stream should
     * use for control data (e.g. RTCP) traffic
     * @see StreamConnector#getControlSocket()
     */
    public DatagramSocket getControlSocket()
    {
        if ((controlSocket == null) && (bindAddr != null))
            controlSocket = createDatagramSocket(bindAddr);
        return controlSocket;
    }

    /**
     * Returns a reference to the <tt>DatagramSocket</tt> that a stream should
     * use for data (e.g. RTP) traffic.
     *
     * @return a reference to the <tt>DatagramSocket</tt> that a stream should
     * use for data (e.g. RTP) traffic
     * @see StreamConnector#getDataSocket()
     */
    public DatagramSocket getDataSocket()
    {
        if ((dataSocket == null) && (bindAddr != null))
            dataSocket = createDatagramSocket(bindAddr);
        return dataSocket;
    }

    /**
     * Returns a reference to the <tt>Socket</tt> that a stream should
     * use for data (e.g. RTP) traffic.
     *
     * @return a reference to the <tt>Socket</tt> that a stream should
     * use for data (e.g. RTP) traffic.
     */
    public Socket getDataTCPSocket()
    {
        return null;
    }

    /**
     * Returns a reference to the <tt>Socket</tt> that a stream should
     * use for control data (e.g. RTCP).
     *
     * @return a reference to the <tt>Socket</tt> that a stream should
     * use for control data (e.g. RTCP).
     */
    public Socket getControlTCPSocket()
    {
        return null;
    }

    /**
     * Returns the protocol of this <tt>StreamConnector</tt>.
     *
     * @return the protocol of this <tt>StreamConnector</tt>
     */
    public Protocol getProtocol()
    {
        return Protocol.UDP;
    }

    /**
     * Notifies this instance that utilization of its <tt>DatagramSocket</tt>s
     * for data and/or control traffic has started.
     *
     * @see StreamConnector#started()
     */
    public void started()
    {
    }

    /**
     * Notifies this instance that utilization of its <tt>DatagramSocket</tt>s
     * for data and/or control traffic has temporarily stopped. This instance
     * should be prepared to be started at a later time again though.
     *
     * @see StreamConnector#stopped()
     */
    public void stopped()
    {
    }
}
