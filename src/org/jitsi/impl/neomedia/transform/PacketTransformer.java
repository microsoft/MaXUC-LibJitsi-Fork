/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.transform;

import org.jitsi.impl.neomedia.*;

/**
 * Encapsulate the concept of packet transformation. Given a packet,
 * <tt>PacketTransformer</tt> can either transform it or reverse the
 * transformation.
 *
 * @author Bing SU (nova.su@gmail.com)
 */
public interface PacketTransformer
{
    /**
     * Transforms a specific packet.
     *
     * @param pkt the packet to be transformed
     * @return the transformed packet
     */
    RawPacket transform(RawPacket pkt);

    /**
     * Reverse-transforms a specific packet (i.e. transforms a transformed
     * packet back).
     *
     * @param pkt the transformed packet to be restored
     * @return the restored packet
     */
    RawPacket reverseTransform(RawPacket pkt);

    /**
     * Closes this <tt>PacketTransformer</tt> i.e. releases the resources
     * allocated by it and prepares it for garbage collection.
     */
    void close();
}
