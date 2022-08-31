/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.transform.pt;

import java.util.*;
import java.util.concurrent.*;

import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.transform.*;

/**
 * We use this engine to change payload types of outgoing RTP packets if needed.
 * This is necessary so that we can support the RFC3264 case where the answerer
 * has the right to declare what payload type mappings it wants to receive even
 * if they are different from those in the offer. RFC3264 claims this is for
 * support of legacy protocols such as H.323 but we've been bumping with
 * a number of cases where multi-component pure SIP systems also need to
 * behave this way.
 *
 * @author Damian Minkov
 */
public class PayloadTypeTransformEngine
    implements TransformEngine,
               PacketTransformer
{
    /**
     * The mapping we use to override payloads. By default it is empty
     * and we do nothing, packets are passed through without modification.
     * Maps source payload to target payload.
     */
    private final Map<Byte, Byte> mappingOverrides =
            new ConcurrentHashMap<>();

    /**
     * Checks if there are any override mappings, if no setting just pass
     * through the packet.
     * If the <tt>RawPacket</tt> payload has entry in mappings to override,
     * we override packet payload type.
     *
     * @param pkt the RTP <tt>RawPacket</tt> that we check for need to change
     * payload type.
     *
     * @return the updated <tt>RawPacket</tt> instance containing the changed
     * payload type.
     */
    @Override
    public RawPacket transform(RawPacket pkt)
    {
        Byte newPT = mappingOverrides.get(pkt.getPayloadType());
        if(newPT != null)
        {
            pkt.setPayload(newPT);
        }

        return pkt;
    }

    /**
     * Do nothing just passes the incoming packet.
     *
     * @param pkt the RTP <tt>RawPacket</tt> that we will pass through.
     *
     * @return the same <tt>RawPacket</tt> that is passing through.
     */
    @Override
    public RawPacket reverseTransform(RawPacket pkt)
    {
        return pkt;
    }

    /**
     * Closes this <tt>PacketTransformer</tt> i.e. releases the resources
     * allocated by it and prepares it for garbage collection.
     */
    @Override
    public void close()
    {
    }

    /**
     * Returns a reference to this class since it is performing RTP
     * transformations in here.
     *
     * @return a reference to <tt>this</tt> instance of the
     * <tt>PayloadTypeTransformEngine</tt>.
     */
    @Override
    public PacketTransformer getRTPTransformer()
    {
        return this;
    }

    /**
     * Always returns <tt>null</tt> since this engine does not require any
     * RTCP transformations.
     *
     * @return <tt>null</tt> since this engine does not require any
     * RTCP transformations.
     */
    @Override
    public PacketTransformer getRTCPTransformer()
    {
        return null;
    }

    /**
     * Adds an additional RTP payload type mapping used to override the payload
     * type of outgoing RTP packets. If an override for <tt>originalPT<tt/>,
     * was already being overridden, this call is simply going to update the
     * override to the new one.
     *
     * @param originalPt the payload type that we are overriding
     * @param overridePt the payload type that we are overriding it with
     */
    public void addPTMappingOverride(byte originalPt, byte overridePt)
    {
        mappingOverrides.put(originalPt, overridePt);
    }

    /**
     * Clears the dynamic RTP payload type override mapping as set by
     * {@link addPTMappingOverride(byte, byte)} so that we can set new
     * overrides.
     */
    public void clearPTMappingOverrides()
    {
        mappingOverrides.clear();
    }
}
