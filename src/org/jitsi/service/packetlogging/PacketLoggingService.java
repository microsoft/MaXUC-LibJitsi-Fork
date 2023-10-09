/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
// Portions (c) Microsoft Corporation. All rights reserved.
package org.jitsi.service.packetlogging;

/**
 * A Packet Logging Service to log packets that were send/received
 * by protocols or any other network related services in various formats.
 * Its for debugging purposes.
 *
 * @author Damian Minkov
 */
public interface PacketLoggingService
{
    /**
     * These are the services that this packet logging service
     * cab handle.
     */
    enum ProtocolName
    {
        /**
         * SIP protocol name.
         */
        SIP,

        /**
         * Jabber protocol name.
         */
        JABBER,

        /**
         * RTP protocol name.
         */
        RTP
    }

    /**
     * The transport names.
     */
    enum TransportName
    {
        /**
         * UDP transport name.
         */
        UDP,

        /**
         * TCP transport name.
         */
        TCP
    }

    /**
     * Checks is logging globally enabled for and is it currently
     * available fo the given protocol.
     *.
     * @param protocol that is checked.
     * @return is logging enabled.
     */
    boolean isLoggingEnabled(ProtocolName protocol);

    /**
     * Log a packet with all the required information.
     *
     * @param protocol the source protocol that logs this packet.
     * @param sourceAddress the source address of the packet.
     * @param sourcePort the source port of the packet.
     * @param destinationAddress the destination address.
     * @param destinationPort the destination port.
     * @param transport the transport this packet uses.
     * @param sender are we the sender of the packet or not.
     * @param packetContent the packet content.
     */
    void logPacket(
            ProtocolName protocol,
            byte[] sourceAddress,
            int sourcePort,
            byte[] destinationAddress,
            int destinationPort,
            TransportName transport,
            boolean sender,
            byte[] packetContent);

    /**
     * Log a packet with all the required information.
     *
     * @param protocol the source protocol that logs this packet.
     * @param sourceAddress the source address of the packet.
     * @param sourcePort the source port of the packet.
     * @param destinationAddress the destination address.
     * @param destinationPort the destination port.
     * @param transport the transport this packet uses.
     * @param sender are we the sender of the packet or not.
     * @param packetContent the packet content.
     * @param packetOffset the packet content offset.
     * @param packetLength the packet content length.
     */
    void logPacket(
            ProtocolName protocol,
            byte[] sourceAddress,
            int sourcePort,
            byte[] destinationAddress,
            int destinationPort,
            TransportName transport,
            boolean sender,
            byte[] packetContent,
            int packetOffset,
            int packetLength);

    /**
     * Returns the current Packet Logging Configuration.
     *
     * @return the Packet Logging Configuration.
     */
    PacketLoggingConfiguration getConfiguration();

    /**
     * Dump all the media that's currently in the media buffer.
     */
    void dumpMediaBuffer();

    /**
     * Record an RTP packet in the rolling media buffer.
     *
     * @param packetContent The full bytes of the UDP Datagram
     * @param timeStamp The exact time at which the packet arrived
     * @param sourceAddress The IP address of the source of the packet
     * @param sourcePort The port of the source of the packet
     * @param destinationAddress The IP address of the destination of the packet
     * @param sourcePort The port of the destination of the packet
     */
    void bufferMedia(
            byte[] packetContent,
            long timeStamp,
            byte[] sourceAddress,
            int sourcePort,
            byte[] destinationAddress,
            int destinationPort);

    /**
     * @return is this Packet Logging Service allowed to capture media?
     */
    boolean allowedToCaptureMedia();

    /**
     * @return is this Packet Logging Service allowed to write media?
     */
    boolean allowedToWriteMedia();

    /**
     * Mark the user has started recording.
     */
    void startRecording();

    /**
     * Mark the user has stopped recording.
     */
    void stopRecording();
}
