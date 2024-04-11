/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
// Portions (c) Microsoft Corporation. All rights reserved.
package org.jitsi.impl.neomedia;

import java.io.*;
import java.net.*;

import javax.media.protocol.*;

import net.sf.fmj.media.*;

import org.jitsi.service.libjitsi.*;
import org.jitsi.service.packetlogging.*;
import org.jitsi.util.*;

/**
 * @author Bing SU (nova.su@gmail.com)
 * @author Lyubomir Marinov
 */
public abstract class RTPConnectorInputStream
    implements PushSourceStream,
               Runnable
{
    private static final Logger logger =
                               Logger.getLogger(RTPConnectorInputStream.class);

    /**
     * The value of the property <tt>controls</tt> of
     * <tt>RTPConnectorInputStream</tt> when there are no controls. Explicitly
     * defined in order to reduce unnecessary allocations.
     */
    private static final Object[] EMPTY_CONTROLS = new Object[0];

    /**
     * The length in bytes of the buffers of <tt>RTPConnectorInputStream</tt>
     * receiving packets from the network.
     */
    private static final int PACKET_RECEIVE_BUFFER_LENGTH = 4 * 1024;

    /**
     * Packet receive buffer
     */
    private final byte[] buffer = new byte[PACKET_RECEIVE_BUFFER_LENGTH];

    /**
     * Whether this stream is closed. Used to control the termination of worker
     * thread.
     */
    protected boolean closed;

    /**
     * Caught an IO exception during read from socket
     */
    protected boolean ioError = false;

    /**
     * The packet data to be read out of this instance through its
     * {@link #read(byte[], int, int)} method.
     */
    protected RawPacket pkt;

    /**
     * SourceTransferHandler object which is used to read packets.
     */
    private SourceTransferHandler transferHandler;

    /**
     * The <tt>DatagramPacketFilter</tt>s which allow dropping
     * <tt>DatagramPacket</tt>s before they are converted into
     * <tt>RawPacket</tt>s.
     */
    private DatagramPacketFilter[] datagramPacketFilters;

    /**
     * Initializes a new <tt>RTPConnectorInputStream</tt> which is to receive
     * packet data from a specific UDP socket.
     */
    public RTPConnectorInputStream()
    {
        // PacketLoggingService
        addDatagramPacketFilter(
                new DatagramPacketFilter()
                {
                    /**
                     * Used for debugging. As we don't log every packet, we must
                     * count them and decide which to log.
                     */
                    private long numberOfPackets = 0;

                    @Override
                    public boolean accept(DatagramPacket p)
                    {
                        numberOfPackets++;
                        if (RTPConnectorOutputStream.logPacket(numberOfPackets))
                        {
                            PacketLoggingService packetLogging
                                = LibJitsi.getPacketLoggingService();

                            if ((packetLogging != null)
                                    && packetLogging.isLoggingEnabled(
                                            PacketLoggingService.ProtocolName
                                                    .RTP))
                                doLogPacket(p);
                        }
                        Log.logReceivedBytes(this, p.getLength());

                        return true;
                    }
                });
    }

    /**
     * Close this stream, stops the worker thread.
     */
    public synchronized void close()
    {
    }

    /**
     * Creates a new <tt>RawPacket</tt> from a specific <tt>DatagramPacket</tt>
     * in order to have this instance receive its packet data through its
     * {@link #read(byte[], int, int)} method. Allows extenders to intercept the
     * packet data and possibly filter and/or modify it.
     *
     * @param datagramPacket the <tt>DatagramPacket</tt> containing the packet
     * data
     * @return a new <tt>RawPacket</tt> containing the packet data of the
     * specified <tt>DatagramPacket</tt> or possibly its modification;
     * <tt>null</tt> to ignore the packet data of the specified
     * <tt>DatagramPacket</tt> and not make it available to this instance
     * through its {@link #read(byte[], int, int)} method
     */
    protected RawPacket createRawPacket(DatagramPacket datagramPacket)
    {
        if (pkt == null)
        {
            return
                new RawPacket(
                        datagramPacket.getData(),
                        datagramPacket.getOffset(),
                        datagramPacket.getLength());
        }
        pkt.setBuffer(datagramPacket.getData());
        pkt.setLength(datagramPacket.getLength());
        pkt.setOffset(datagramPacket.getOffset());
        return pkt;
    }

    /**
     * Provides a dummy implementation to {@link
     * RTPConnectorInputStream#endOfStream()} that always returns
     * <tt>false</tt>.
     *
     * @return <tt>false</tt>, no matter what.
     */
    @Override
    public boolean endOfStream()
    {
        return false;
    }

    /**
     * Provides a dummy implementation to {@link
     * RTPConnectorInputStream#getContentDescriptor()} that always returns
     * <tt>null</tt>.
     *
     * @return <tt>null</tt>, no matter what.
     */
    @Override
    public ContentDescriptor getContentDescriptor()
    {
        return null;
    }

    /**
     * Provides a dummy implementation to {@link
     * RTPConnectorInputStream#getContentLength()} that always returns
     * <tt>LENGTH_UNKNOWN</tt>.
     *
     * @return <tt>LENGTH_UNKNOWN</tt>, no matter what.
     */
    @Override
    public long getContentLength()
    {
        return pkt.getLength();
    }

    /**
     * Provides a dummy implementation to {@link
     * RTPConnectorInputStream#getControl(String)} that always returns
     * <tt>null</tt>.
     *
     * @param controlType ignored.
     *
     * @return <tt>null</tt>, no matter what.
     */
    @Override
    public Object getControl(String controlType)
    {
        return null;
    }

    /**
     * Provides a dummy implementation to {@link
     * RTPConnectorInputStream#getControls()} that always returns
     * <tt>EMPTY_CONTROLS</tt>.
     *
     * @return <tt>EMPTY_CONTROLS</tt>, no matter what.
     */
    @Override
    public Object[] getControls()
    {
        return EMPTY_CONTROLS;
    }

    /**
     * Provides a dummy implementation to {@link
     * RTPConnectorInputStream#getMinimumTransferSize()} that always returns
     * <tt>2 * 1024</tt>.
     *
     * @return <tt>2 * 1024</tt>, no matter what.
     */
    @Override
    public int getMinimumTransferSize()
    {
        return 2 * 1024; // twice the MTU size, just to be safe.
    }

    /**
     * Copies the content of the most recently received packet into
     * <tt>buffer</tt>.
     *
     * @param buffer the <tt>byte[]</tt> that we'd like to copy the content of
     * the packet to.
     * @param offset the position where we are supposed to start writing in
     * <tt>buffer</tt>.
     * @param length the number of <tt>byte</tt>s available for writing in
     * <tt>buffer</tt>.
     *
     * @return the number of bytes read
     *
     * @throws IOException if <tt>length</tt> is less than the size of the
     * packet.
     */
    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException
    {
        if (ioError)
        {
            return -1;
        }

        int pktLength = pkt.getLength();

        if (length < pktLength)
        {
            throw new IOException("Input buffer " + length + " not big enough for " + pktLength);
        }

        System.arraycopy(
                pkt.getBuffer(), pkt.getOffset(),
                buffer, offset,
                pktLength);

        return pktLength;
    }

    /**
     * Log the packet.
     *
     * @param packet packet to log
     */
    protected abstract void doLogPacket(DatagramPacket packet);

    /**
     * Receive packet.
     *
     * @param p packet for receiving
     * @throws IOException if something goes wrong during receiving
     * @returns true when a packet was successfully received
     */
    protected abstract boolean receivePacket(DatagramPacket p)
        throws IOException;

    /**
     * Listens for incoming datagrams, stores them for reading by the
     * <tt>read</tt> method and notifies the local <tt>transferHandler</tt>
     * that there's data to be read.
     */
    @Override
    public void run()
    {
        Log.logMediaStackObjectStarted(this);
        DatagramPacket p = new DatagramPacket(buffer, 0, PACKET_RECEIVE_BUFFER_LENGTH);

        while (!closed)
        {
            try
            {
                if (!receivePacket(p))
                {
                    logger.debug("Didn't receive a packet to read");
                    continue;
                }
            }
            catch (SocketTimeoutException e)
            {
                /*
                 * The socket timeout allows us to terminate this thread
                 * before receiving the first packet if this input stream
                 * was closed.
                 */
                continue;
            }
            catch (IOException e)
            {
                logger.error("IOException receiving packet", e);
                ioError = true;
                break;
            }

            /*
             * Do the DatagramPacketFilters accept the received DatagramPacket?
             */
            DatagramPacketFilter[] datagramPacketFilters = getDatagramPacketFilters();
            boolean accept;

            if (datagramPacketFilters == null)
            {
                accept = true;
            }
            else
            {
                accept = true;
                for (int i = 0; i < datagramPacketFilters.length; i++)
                {
                    try
                    {
                        if (!datagramPacketFilters[i].accept(p))
                        {
                            accept = false;
                            break;
                        }
                    }
                    catch (Throwable t)
                    {
                        if (t instanceof ThreadDeath)
                            throw (ThreadDeath) t;
                    }
                }
            }

            /*
             * Ignore the packet if this input stream was closed.
             */
            if (closed)
            {
                logger.warn("Ignoring packet received after the stream was closed.");
            }
            else if (accept)
            {
                pkt = createRawPacket(p);

                /*
                 * If we got extended, the delivery of the packet may have been
                 * canceled.
                 */
                if ((pkt != null) && (!pkt.isInvalid()) && (transferHandler != null) && !closed)
                {
                    transferHandler.transferData(this);
                }
            }
        }
        Log.logMediaStackObjectStopped(this);
    }

    /**
     * Sets the <tt>transferHandler</tt> that this connector should be notifying
     * when new data is available for reading.
     *
     * @param transferHandler the <tt>transferHandler</tt> that this connector
     * should be notifying when new data is available for reading.
     */
    @Override
    public void setTransferHandler(SourceTransferHandler transferHandler)
    {
        if (!closed)
            this.transferHandler = transferHandler;
    }

    /**
     * Changes current thread priority.
     * @param priority the new priority.
     */
    public void setPriority(int priority)
    {
        // currently no priority is set
//        if (receiverThread != null)
//            receiverThread.setPriority(priority);
    }

    /**
     * Gets the <tt>DatagramPacketFilter</tt>s which allow dropping
     * <tt>DatagramPacket</tt>s before they are converted into
     * <tt>RawPacket</tt>s.
     *
     * @return the <tt>DatagramPacketFilter</tt>s which allow dropping
     * <tt>DatagramPacket</tt>s before they are converted into
     * <tt>RawPacket</tt>s.
     */
    public synchronized DatagramPacketFilter[] getDatagramPacketFilters()
    {
        return datagramPacketFilters;
    }

    /**
     * Adds a <tt>DatagramPacketFilter</tt> which allows dropping
     * <tt>DatagramPacket</tt>s before they are converted into
     * <tt>RawPacket</tt>s.
     *
     * @param datagramPacketFilter the <tt>DatagramPacketFilter</tt> which
     * allows dropping <tt>DatagramPacket</tt>s before they are converted into
     * <tt>RawPacket</tt>s
     */
    public synchronized void addDatagramPacketFilter(
            DatagramPacketFilter datagramPacketFilter)
    {
        if (datagramPacketFilter == null)
            throw new NullPointerException("datagramPacketFilter");

        if (datagramPacketFilters == null)
        {
            datagramPacketFilters
                = new DatagramPacketFilter[] { datagramPacketFilter };
        }
        else
        {
            final int length = datagramPacketFilters.length;

            for (int i = 0; i < length; i++)
                if (datagramPacketFilter.equals(datagramPacketFilters[i]))
                    return;

            DatagramPacketFilter[] newDatagramPacketFilters
                = new DatagramPacketFilter[length + 1];

            System.arraycopy(
                    datagramPacketFilters, 0,
                    newDatagramPacketFilters, 0,
                    length);
            newDatagramPacketFilters[length] = datagramPacketFilter;
            datagramPacketFilters = newDatagramPacketFilters;
        }
    }
}
