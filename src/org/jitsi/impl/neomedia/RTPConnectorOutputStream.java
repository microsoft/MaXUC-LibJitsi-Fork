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
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

import javax.media.rtp.*;

import net.sf.fmj.media.Log;

import org.jitsi.service.libjitsi.*;
import org.jitsi.service.packetlogging.*;
import org.jitsi.util.*;

/**
 * @author Bing SU (nova.su@gmail.com)
 * @author Lyubomir Marinov
 */
public abstract class RTPConnectorOutputStream
    implements OutputDataStream
{
    /**
     * The <tt>Logger</tt> used by the <tt>RTPConnectorOutputStream</tt> class
     * and its instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(RTPConnectorOutputStream.class);

    /**
     * The maximum number of packets to be sent to be kept in the queue of
     * <tt>MaxPacketsPerMillisPolicy</tt>. When the maximum is reached, the next
     * attempt to write a new packet in the queue will block until at least one
     * packet from the queue is sent. Defined in order to prevent
     * <tt>OutOfMemoryError</tt>s which, technically, may arise if the capacity
     * of the queue is unlimited.
     */
    public static final int
        MAX_PACKETS_PER_MILLIS_POLICY_PACKET_QUEUE_CAPACITY
            = 256;

    /**
     * The functionality which allows this <tt>OutputDataStream</tt> to control
     * how many RTP packets it sends through its <tt>DatagramSocket</tt> per a
     * specific number of milliseconds.
     */
    private MaxPacketsPerMillisPolicy maxPacketsPerMillisPolicy;

    /**
     * Stream targets' IP addresses and ports.
     */
    protected final List<InetSocketAddress> targets
        = new LinkedList<>();

    /**
     * The pool of <tt>RawPacket</tt> instances which reduces the number of
     * allocations performed by {@link #createRawPacket(byte[], int, int)}.
     */
    private final LinkedBlockingQueue<RawPacket> rawPacketPool
        = new LinkedBlockingQueue<>();

    /**
     * Used for debugging. As we don't log every packet
     * we must count them and decide which to log.
     */
    private long numberOfPackets = 0;

    /**
     * Initializes a new <tt>RTPConnectorOutputStream</tt> which is to send
     * packet data out through a specific socket.
     */
    public RTPConnectorOutputStream()
    {
        Log.logMediaStackObjectStarted(this);
    }

    /**
     * Add a target to stream targets list
     *
     * @param remoteAddr target ip address
     * @param remotePort target port
     */
    public void addTarget(InetAddress remoteAddr, int remotePort)
    {
        InetSocketAddress target
            = new InetSocketAddress(remoteAddr, remotePort);

        if (!targets.contains(target))
            targets.add(target);
    }

    /**
     * Close this output stream.
     */
    public void close()
    {
        Log.logMediaStackObjectStopped(this);
        if (maxPacketsPerMillisPolicy != null)
        {
            maxPacketsPerMillisPolicy.close();
            maxPacketsPerMillisPolicy = null;
        }
        removeTargets();
    }

    /**
     * Creates a new <tt>RawPacket</tt> from a specific <tt>byte[]</tt> buffer
     * in order to have this instance send its packet data through its
     * {@link #write(byte[], int, int)} method. Allows extenders to intercept
     * the packet data and possibly filter and/or modify it.
     *
     * @param buffer the packet data to be sent to the targets of this instance
     * @param offset the offset of the packet data in <tt>buffer</tt>
     * @param length the length of the packet data in <tt>buffer</tt>
     * @return a new <tt>RawPacket</tt> containing the packet data of the
     * specified <tt>byte[]</tt> buffer or possibly its modification;
     * <tt>null</tt> to ignore the packet data of the specified <tt>byte[]</tt>
     * buffer and not send it to the targets of this instance through its
     * {@link #write(byte[], int, int)} method
     */
    protected RawPacket createRawPacket(byte[] buffer, int offset, int length)
    {
        RawPacket pkt = rawPacketPool.poll();

        if ((pkt == null) || (pkt.getBuffer().length < length))
            pkt = new RawPacket(new byte[length], 0, 0);

        System.arraycopy(buffer, offset, pkt.getBuffer(), 0, length);
        pkt.setLength(length);
        pkt.setOffset(0);
        return pkt;
    }

    /**
     * Remove a target from stream targets list
     *
     * @param remoteAddr target ip address
     * @param remotePort target port
     * @return <tt>true</tt> if the target is in stream target list and can be
     * removed; <tt>false</tt>, otherwise
     */
    public boolean removeTarget(InetAddress remoteAddr, int remotePort)
    {
        for (Iterator<InetSocketAddress> targetIter = targets.iterator();
                targetIter.hasNext();)
        {
            InetSocketAddress target = targetIter.next();

            if (target.getAddress().equals(remoteAddr)
                    && (target.getPort() == remotePort))
            {
                targetIter.remove();
                return true;
            }
        }
        return false;
    }

    /**
     * Remove all stream targets from this session.
     */
    public void removeTargets()
    {
        targets.clear();
    }

    /**
     * Determines whether a <tt>RawPacket</tt> which has a specific number in
     * the total number of sent <tt>RawPacket</tt>s is to be logged by
     * {@link PacketLoggingService}.
     *
     * @param numOfPacket the number of the <tt>RawPacket</tt> in the total
     * number of sent <tt>RawPacket</tt>s
     * @return <tt>true</tt> if the <tt>RawPacket</tt> with the specified
     * <tt>numOfPacket</tt> is to be logged by <tt>PacketLoggingService</tt>;
     * otherwise, <tt>false</tt>
     */
    static boolean logPacket(long numOfPacket)
    {
        // We log all packets (without voice data)
        return true;
    }

    /**
     * Sends a specific <tt>RawPacket</tt> through this
     * <tt>OutputDataStream</tt> to a specific <tt>InetSocketAddress</tt>.
     *
     * @param packet the <tt>RawPacket</tt> to send through this
     * <tt>OutputDataStream</tt> to the specified <tt>target</tt>
     * @param target the <tt>InetSocketAddress</tt> to which the specified
     * <tt>packet</tt> is to be sent through this <tt>OutputDataStream</tt>
     * @throws IOException if anything goes wrong while sending the specified
     * <tt>packet</tt> through this <tt>OutputDataStream</tt> to the specified
     * <tt>target</tt>
     */
    protected abstract void sendToTarget(
            RawPacket packet,
            InetSocketAddress target)
        throws IOException;

    /**
     * Logs a specific <tt>RawPacket</tt> associated with a specific remote
     * address.
     *
     * @param packet packet to log
     * @param target the remote address associated with the <tt>packet</tt>
     */
    protected abstract void doLogPacket(
        RawPacket packet,
        InetSocketAddress target);

    /**
     * Returns whether or not this <tt>RTPConnectorOutputStream</tt> has a valid
     * socket.
     *
     * @return <tt>true</tt> if this <tt>RTPConnectorOutputStream</tt> has a
     * valid socket; <tt>false</tt>, otherwise
     */
    protected abstract boolean isSocketValid();

    /**
     * Sends a specific RTP packet through the <tt>DatagramSocket</tt> of this
     * <tt>OutputDataSource</tt>.
     *
     * @param packet the RTP packet to be sent through the
     * <tt>DatagramSocket</tt> of this <tt>OutputDataSource</tt>
     * @return <tt>true</tt> if the specified <tt>packet</tt> was successfully
     * sent; otherwise, <tt>false</tt>
     */
    private boolean send(RawPacket packet)
    {
        if(!isSocketValid())
            return false;

        numberOfPackets++;
        for (InetSocketAddress target : targets)
        {
            try
            {
                sendToTarget(packet, target);

                if(logPacket(numberOfPackets))
                {
                    PacketLoggingService packetLogging
                        = LibJitsi.getPacketLoggingService();

                    if ((packetLogging != null)
                            && packetLogging.isLoggingEnabled(
                                    PacketLoggingService.ProtocolName.RTP))
                        doLogPacket(packet, target);
                }
                Log.logReceivedBytes(this, packet.getLength());
            }
            catch (IOException ioe)
            {
                rawPacketPool.offer(packet);
                // TODO error handling
                return false;
            }
        }
        rawPacketPool.offer(packet);
        return true;
    }

    /**
     * Sets the maximum number of RTP packets to be sent by this
     * <tt>OutputDataStream</tt> through its <tt>DatagramSocket</tt> per
     * a specific number of milliseconds.
     *
     * @param maxPackets the maximum number of RTP packets to be sent by this
     * <tt>OutputDataStream</tt> through its <tt>DatagramSocket</tt> per the
     * specified number of milliseconds; <tt>-1</tt> if no maximum is to be set
     * @param perMillis the number of milliseconds per which <tt>maxPackets</tt>
     * are to be sent by this <tt>OutputDataStream</tt> through its
     * <tt>DatagramSocket</tt>
     */
    public void setMaxPacketsPerMillis(int maxPackets, long perMillis)
    {
        if (maxPacketsPerMillisPolicy == null)
        {
            if (maxPackets > 0)
            {
                if (perMillis < 1)
                    throw new IllegalArgumentException("perMillis");

                maxPacketsPerMillisPolicy
                    = new MaxPacketsPerMillisPolicy(maxPackets, perMillis);
            }
        }
        else
        {
            maxPacketsPerMillisPolicy
                .setMaxPacketsPerMillis(maxPackets, perMillis);
        }
    }

    /**
     * Implements {@link OutputDataStream#write(byte[], int, int)}.
     *
     * @param buffer the <tt>byte[]</tt> that we'd like to copy the content
     * of the packet to.
     * @param offset the position where we are supposed to start writing in
     * <tt>buffer</tt>.
     * @param length the number of <tt>byte</tt>s available for writing in
     * <tt>inBuffer</tt>.
     *
     * @return the number of bytes read
     */
    @Override
    public int write(byte[] buffer, int offset, int length)
    {
        /*
         * While calling write without targets can be carried out without a
         * problem, such a situation may be a symptom of a problem. For example,
         * it was discovered during testing that RTCP was seemingly-endlessly
         * sent after hanging up a call.
         */
        if (targets.isEmpty())
            logger.trace("Write called without targets!");

        RawPacket packet = createRawPacket(buffer, offset, length);

        /*
         * If we got extended, the delivery of the packet may have been
         * canceled.
         */
        if (packet != null)
        {
            if (maxPacketsPerMillisPolicy == null)
            {
                if (!send(packet))
                    return -1;
            }
            else
                maxPacketsPerMillisPolicy.write(packet);
        }
        return length;
    }

    /**
     * Changes current thread priority.
     * @param priority the new priority.
     */
    public void setPriority(int priority)
    {
        // currently no priority is set
//        if ((maxPacketsPerMillisPolicy != null)
//                && (maxPacketsPerMillisPolicy.sendThread != null))
//            maxPacketsPerMillisPolicy.sendThread.setPriority(priority);
    }

    /**
     * Implements the functionality which allows this <tt>OutputDataStream</tt>
     * to control how many RTP packets it sends through its
     * <tt>DatagramSocket</tt> per a specific number of milliseconds.
     */
    private class MaxPacketsPerMillisPolicy
    {
        /**
         * The maximum number of RTP packets to be sent by this
         * <tt>OutputDataStream</tt> through its <tt>DatagramSocket</tt> per
         * {@link #perNanos} nanoseconds.
         */
        private int maxPackets = -1;

        /**
         * The time stamp in nanoseconds of the start of the current
         * <tt>perNanos</tt> interval.
         */
        private long millisStartTime = 0;

        /**
         * The list of RTP packets to be sent through the
         * <tt>DatagramSocket</tt> of this <tt>OutputDataSource</tt>.
         */
        private final ArrayBlockingQueue<RawPacket> packetQueue
            = new ArrayBlockingQueue<>(
                MAX_PACKETS_PER_MILLIS_POLICY_PACKET_QUEUE_CAPACITY);

        /**
         * The number of RTP packets already sent during the current
         * <tt>perNanos</tt> interval.
         */
        private long packetsSentInMillis = 0;

        /**
         * The time interval in nanoseconds during which {@link #maxPackets}
         * number of RTP packets are to be sent through the
         * <tt>DatagramSocket</tt> of this <tt>OutputDataSource</tt>.
         */
        private long perNanos = -1;

        /**
         * The <tt>Thread</tt> which is to send the RTP packets in
         * {@link #packetQueue} through the <tt>DatagramSocket</tt> of this
         * <tt>OutputDataSource</tt>.
         */
        private Thread sendThread;

        /**
         * To signal run or stop condition to send thread.
         */
        private boolean sendRun = true;

        /**
         * Initializes a new <tt>MaxPacketsPerMillisPolicy</tt> instance which
         * is to control how many RTP packets this <tt>OutputDataSource</tt> is
         * to send through its <tt>DatagramSocket</tt> per a specific number of
         * milliseconds.
         *
         * @param maxPackets the maximum number of RTP packets to be sent per
         * <tt>perMillis</tt> milliseconds through the <tt>DatagramSocket</tt>
         * of this <tt>OutputDataStream</tt>
         * @param perMillis the number of milliseconds per which a maximum of
         * <tt>maxPackets</tt> RTP packets are to be sent through the
         * <tt>DatagramSocket</tt> of this <tt>OutputDataStream</tt>
         */
        public MaxPacketsPerMillisPolicy(int maxPackets, long perMillis)
        {
            setMaxPacketsPerMillis(maxPackets, perMillis);
            synchronized (this) {
                if (sendThread == null)
                {
                    sendThread
                        = new Thread(getClass().getName())
                        {
                            @Override
                            public void run()
                            {
                                runInSendThread();
                            }
                        };
                    sendThread.setDaemon(true);
                    sendThread.start();
                }
            }
        }

        /**
         * Closes the connector.
         */
        synchronized void close()
        {
            if (!sendRun)
                return;
            sendRun = false;
            // just offer a new packet to wakeup thread in case it waits for
            // a packet.
            packetQueue.offer(new RawPacket(null, 0, 0));
        }

        /**
         * Sends the RTP packets in {@link #packetQueue} in accord with
         * {@link #maxPackets} and {@link #perNanos}.
         */
        private void runInSendThread()
        {
            try
            {
                while (sendRun)
                {
                    RawPacket packet = null;

                    while (true)
                    {
                        try
                        {
                            packet = packetQueue.take();
                            break;
                        }
                        catch (InterruptedException iex)
                        {
                            continue;
                        }
                    }
                    if (!sendRun)
                        break;

                    long time = System.nanoTime();
                    long millisRemainingTime = time - millisStartTime;

                    if ((perNanos < 1)
                            || (millisRemainingTime >= perNanos))
                    {
                        millisStartTime = time;
                        packetsSentInMillis = 0;
                    }
                    else if ((maxPackets > 0)
                            && (packetsSentInMillis >= maxPackets))
                    {
                        while (true)
                        {
                            millisRemainingTime = System.nanoTime()
                                    - millisStartTime;
                            if (millisRemainingTime >= perNanos)
                                break;
                            LockSupport.parkNanos(millisRemainingTime);
                        }
                        millisStartTime = System.nanoTime();
                        packetsSentInMillis = 0;
                    }

                    send(packet);
                    packetsSentInMillis++;
                }
            }
            finally
            {
                packetQueue.clear();
                synchronized (packetQueue)
                {
                    if (Thread.currentThread().equals(sendThread))
                        sendThread = null;
                }
            }
        }

        /**
         * Sets the maximum number of RTP packets to be sent by this
         * <tt>OutputDataStream</tt> through its <tt>DatagramSocket</tt> per
         * a specific number of milliseconds.
         *
         * @param maxPackets the maximum number of RTP packets to be sent by
         * this <tt>OutputDataStream</tt> through its <tt>DatagramSocket</tt>
         * per the specified number of milliseconds; <tt>-1</tt> if no maximum
         * is to be set
         * @param perMillis the number of milliseconds per which
         * <tt>maxPackets</tt> are to be sent by this <tt>OutputDataStream</tt>
         * through its <tt>DatagramSocket</tt>
         */
        public void setMaxPacketsPerMillis(int maxPackets, long perMillis)
        {
            if (maxPackets < 1)
            {
                this.maxPackets = -1;
                this.perNanos = -1;
            }
            else
            {
                if (perMillis < 1)
                    throw new IllegalArgumentException("perMillis");

                this.maxPackets = maxPackets;
                this.perNanos = perMillis * 1000000;
            }
        }

        /**
         * Queues a specific RTP packet to be sent through the
         * <tt>DatagramSocket</tt> of this <tt>OutputDataStream</tt>.
         *
         * @param packet the RTP packet to be queued for sending through the
         * <tt>DatagramSocket</tt> of this <tt>OutputDataStream</tt>
         */
        public void write(RawPacket packet)
        {
            while (true)
            {
                try
                {
                    packetQueue.put(packet);
                    break;
                }
                catch (InterruptedException iex)
                {
                }
            }
        }
    }
}
