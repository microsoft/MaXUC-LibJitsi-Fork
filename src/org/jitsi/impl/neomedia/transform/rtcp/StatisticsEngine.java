/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.transform.rtcp;

import java.io.*;
import java.util.*;

import javax.media.control.*;
import javax.media.rtp.*;

import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.device.*;
import org.jitsi.impl.neomedia.transform.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.codec.*;
import org.jitsi.service.neomedia.control.*;
import org.jitsi.service.neomedia.format.*;
import org.jitsi.service.neomedia.rtp.*;
import org.jitsi.util.*;

import net.sf.fmj.media.rtp.*;
import net.sf.fmj.utility.*;

/**
 * Implements a <tt>TransformEngine</tt> monitors the incoming and outgoing RTCP
 * packets, logs and stores statistical data about an associated
 * <tt>MediaStream</tt>.
 *
 * @author Damian Minkov
 * @author Lyubomir Marinov
 */
public class StatisticsEngine
    implements TransformEngine,
               PacketTransformer
{
    /**
     * The <tt>Logger</tt> used by the <tt>StatisticsEngine</tt> class and its
     * instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(StatisticsEngine.class);

    /**
     * The RTP statistics prefix we use for every log.
     * Simplifies parsing and searching for statistics info in log files.
     */
    public static final String RTP_STAT_PREFIX = "rtpstat:";

    /**
     * Determines whether <tt>buf</tt> appears to contain an RTCP packet
     * starting at <tt>off</tt> and spanning at most <tt>len</tt> bytes. Returns
     * the length in bytes of the RTCP packet if it was determined that there
     * indeed appears to be such an RTCP packet; otherwise, <tt>-1</tt>.
     *
     * @param buf
     * @param off
     * @param len
     * @return the length in bytes of the RTCP packet in <tt>buf</tt> starting
     * at <tt>off</tt> and spanning at most <tt>len</tt> bytes if it was
     * determined that there indeed appears to be such an RTCP packet;
     * otherwise, <tt>-1</tt>
     */
    private static int getLengthIfRTCP(byte[] buf, int off, int len)
    {
        if ((off >= 0)
                && (len >= 4)
                && (buf != null)
                && (buf.length >= (off + len)))
        {
            int v = (buf[off] & 0xc0) >>> 6;

            if (v == RTCPHeader.VERSION)
            {
                int words = (buf[off + 2] << 8) + (buf[off + 3] << 0);
                int bytes = (words + 1) * 4;

                if (bytes <= len)
                    return bytes;
            }
        }
        return -1;
    }

    /**
     * Determines whether a specific <tt>RawPacket</tt> appears to represent an
     * RTCP packet.
     *
     * @param pkt the <tt>RawPacket</tt> to be examined
     * @return <tt>true</tt> if the specified <tt>pkt</tt> appears to represent
     * an RTCP packet
     */
    private static boolean isRTCP(RawPacket pkt)
    {
        return
            getLengthIfRTCP(pkt.getBuffer(), pkt.getOffset(), pkt.getLength())
                > 0;
    }

    /**
     * Removes any RTP Control Protocol Extended Report (RTCP XR) packets from
     * <tt>pkt</tt>.
     *
     * @param pkt the <tt>RawPacket</tt> from which any RTCP XR packets are to
     * be removed
     * @return a list of <tt>RTCPExtendedReport</tt> packets removed from
     * <tt>pkt</tt> or <tt>null</tt> or an empty list if no RTCP XR packets were
     * removed from <tt>pkt</tt>
     */
    private static List<RTCPExtendedReport> removeRTCPExtendedReports(
            RawPacket pkt)
    {
        int off = pkt.getOffset();
        List<RTCPExtendedReport> rtcpXRs = null;

        do
        {
            /*
             * XXX RawPacket may shrink if an RTCP XR packet is removed. Such an
             * operation may (or may not) modify either of the buffer, offset,
             * and length properties of RawPacket. Ensure that the buf and end
             * values are in accord with pkt.
             */
            int end = pkt.getOffset() + pkt.getLength();

            if (off >= end)
                break;

            byte[] buf = pkt.getBuffer();
            int rtcpPktLen = getLengthIfRTCP(buf, off, end - off);

            if (rtcpPktLen <= 0) // Not an RTCP packet.
                break;

            int pt = 0xff & buf[off + 1];

            if (pt == RTCPExtendedReport.XR) // An RTCP XR packet.
            {
                RTCPExtendedReport rtcpXR;

                try
                {
                    rtcpXR = new RTCPExtendedReport(buf, off, rtcpPktLen);
                }
                catch (IOException ioe)
                {
                    // It looked like an RTCP XR packet but didn't parse.
                    rtcpXR = null;
                }
                if (rtcpXR == null)
                {
                    // It looked like an RTCP XR packet but didn't parse.
                    off += rtcpPktLen;
                }
                else
                {
                    // Remove the RTCP XR packet.
                    int tailOff = off + rtcpPktLen;
                    int tailLen = end - tailOff;

                    if (tailLen > 0)
                        System.arraycopy(buf, tailOff, buf, off, tailLen);

                    /*
                     * XXX RawPacket may be shrunk if an RTCP XR packet is
                     * removed. Such an operation may (or may not) modify either
                     * of the buffer, offset, and length properties of
                     * RawPacket. Ensure that the off value is in accord with
                     * pkt.
                     */
                    int oldOff = pkt.getOffset();

                    pkt.shrink(rtcpPktLen);

                    int newOff = pkt.getOffset();

                    off = off - oldOff + newOff;

                    // Return the (removed) RTCP XR packet.
                    if (rtcpXRs == null)
                        rtcpXRs = new LinkedList<>();
                    rtcpXRs.add(rtcpXR);
                }
            }
            else
            {
                // Not an RTCP XR packet.
                off += rtcpPktLen;
            }
        }
        while (true);
        return rtcpXRs;
    }

    /**
     * Number of lost packets reported.
     */
    private long lost = 0;

    /**
     * The minimum inter arrival jitter value we have reported.
     */
    private long maxInterArrivalJitter = 0;

    /**
     * The stream created us.
     */
    private final MediaStreamImpl mediaStream;

    /**
     * The <tt>MediaType</tt> of {@link #mediaStream}. Cached for the purposes
     * of purformance.
     */
    private final MediaType mediaType;

    /**
     * The minimum inter arrival jitter value we have reported.
     */
    private long minInterArrivalJitter = -1;

    /*
     * The last sent Seq Number, used to calculate RTT.
     * Stored by SSRC since this class needs to handle all
     * calls and you can have more than one call at a time.
     */
    private HashMap<Integer, Integer> mLastSentSeqNum = new HashMap<>();

    /**
     * Creates Statistic engine.
     * @param stream the stream creating us.
     */
    public StatisticsEngine(MediaStreamImpl stream)
    {
        this.mediaStream = stream;

        mediaType = this.mediaStream.getMediaType();
    }

    /**
     * Adds a specific RTCP XR packet into <tt>pkt</tt>.
     *
     * @param pkt the <tt>RawPacket</tt> into which <tt>extendedReport</tt> is
     * to be added
     * @param extendedReport the RTCP XR packet to add into <tt>pkt</tt>
     * @return <tt>true</tt> if <tt>extendedReport</tt> was added into
     * <tt>pkt</tt>; otherwise, <tt>false</tt>
     */
    private boolean addRTCPExtendedReport(
            RawPacket pkt,
            RTCPExtendedReport extendedReport)
    {
        /*
         * Find an offset within pkt at which the specified RTCP XR packet is to
         * be added. According to RFC 3550, it should not follow an RTCP BYE
         * packet with matching SSRC.
         */
        byte[] buf = pkt.getBuffer();
        int off = pkt.getOffset();
        int end = off + pkt.getLength();

        while (off < end)
        {
            int rtcpPktLen = getLengthIfRTCP(buf, off, end - off);

            if (rtcpPktLen <= 0) // Not an RTCP packet.
                break;

            int pt = 0xff & buf[off + 1]; // payload type (PT)
            boolean before = false;

            if (pt == RTCPPacket.BYE)
            {
                int sc = 0x1f & buf[off]; // source count

                if ((sc < 0) || (rtcpPktLen < ((1 + sc) * 4)))
                {
                    /*
                     * If the packet is not really an RTCP BYE, then we should
                     * better add the RTCP XR before a chunk of bytes that we do
                     * not fully understand.
                     */
                    before = true;
                }
                else
                {
                    for (int i = 0, ssrcOff = off + 4;
                            i < sc;
                            ++i, ssrcOff += 4)
                    {
                        if (RTPTranslatorImpl.readInt(buf, ssrcOff)
                                == extendedReport.getSSRC())
                        {
                            before = true;
                            break;
                        }
                    }
                }
            }

            if (before)
                break;
            else
                off += rtcpPktLen;
        }

        boolean added = false;

        if (off <= end)
        {
            // Make room within pkt for extendedReport.
            int extendedReportLen = extendedReport.calcLength();
            int oldOff = pkt.getOffset();

            pkt.grow(extendedReportLen);

            int newOff = pkt.getOffset();

            buf = pkt.getBuffer();
            off = off - oldOff + newOff;
            end = newOff + pkt.getLength();

            if (off < end)
            {
                System.arraycopy(
                        buf, off,
                        buf, off + extendedReportLen,
                        end - off);
            }

            // Write extendedReport into pkt.
            DataOutputStream dataoutputstream
                = new DataOutputStream(
                        new ByteBufferOutputStream(
                                buf,
                                off,
                                extendedReportLen));

            try
            {
                extendedReport.assemble(dataoutputstream);
                added = (dataoutputstream.size() == extendedReportLen);
            }
            catch (IOException e)
            {
                logger.warn("IOException", e);
            }
            if (added)
            {
                pkt.setLength(pkt.getLength() + extendedReportLen);
            }
            else if (off < end)
            {
                // Reclaim the room within pkt for extendedReport.
                System.arraycopy(
                        buf, off + extendedReportLen,
                        buf, off,
                        end - off);
            }
        }

        return added;
    }

    /**
     * Adds RTP Control Protocol Extended Report (RTCP XR) packets to
     * <tt>pkt</tt> if <tt>pkt</tt> contains RTCP SR or RR packets.
     *
     * @param pkt the <tt>RawPacket</tt> to which RTCP XR packets are to be
     * added
     * @param sdpParams
     * @return a list of <tt>RTCPExtendedReport</tt> packets added to
     * <tt>pkt</tt> or <tt>null</tt> or an empty list if no RTCP XR packets were
     * added to <tt>pkt</tt>
     */
    private List<RTCPExtendedReport> addRTCPExtendedReports(
            RawPacket pkt,
            String sdpParams)
    {
        /*
         * Create an RTCP XR packet for each RTCP SR or RR packet. Afterwards,
         * add the newly created RTCP XR packets into pkt.
         */
        byte[] buf = pkt.getBuffer();
        int off = pkt.getOffset();
        List<RTCPExtendedReport> rtcpXRs = null;

        for (int end = off + pkt.getLength(); off < end;)
        {
            int rtcpPktLen = getLengthIfRTCP(buf, off, end - off);

            if (rtcpPktLen <= 0) // Not an RTCP packet.
                break;

            int pt = 0xff & buf[off + 1]; // payload type (PT)

            if ((pt == RTCPPacket.RR) || (pt == RTCPPacket.SR))
            {
                int rc = 0x1f & buf[off]; // reception report count

                if (rc >= 0)
                {
                    /*
                     * Does the packet still look like an RTCP packet of the
                     * advertised packet type (PT)?
                     */
                    int minRTCPPktLen = (2 + rc * 6) * 4;
                    int receptionReportBlockOff = off + 2 * 4;

                    if (pt == RTCPPacket.SR)
                    {
                        minRTCPPktLen += 5 * 4;
                        receptionReportBlockOff += 5 * 4;
                    }
                    if (rtcpPktLen < minRTCPPktLen)
                    {
                        logger.error("RTCP packet not the right legnth");
                        rtcpXRs = null; // Abort, not an RTCP RR or SR packet.
                        break;
                    }

                    int senderSSRC = RTPTranslatorImpl.readInt(buf, off + 4);
                    /*
                     * Collect the SSRCs of the RTP data packet sources being
                     * reported upon by the RTCP RR/SR packet because they may
                     * be of concern to the RTCP XR packet (e.g. VoIP Metrics
                     * Report Block).
                     */
                    int[] sourceSSRCs = new int[rc];

                    for (int i = 0; i < rc; i++)
                    {
                        sourceSSRCs[i]
                            = RTPTranslatorImpl.readInt(
                                    buf,
                                    receptionReportBlockOff);
                        receptionReportBlockOff += 6 * 4;
                    }

                    // Initialize an RTCP XR packet.
                    RTCPExtendedReport rtcpXR
                        = createRTCPExtendedReport(
                                senderSSRC,
                                sourceSSRCs,
                                sdpParams);

                    if (rtcpXR != null)
                    {
                        if (rtcpXRs == null)
                            rtcpXRs = new LinkedList<>();
                        rtcpXRs.add(rtcpXR);
                    }
                }
                else
                {
                    rtcpXRs = null; // Abort, not an RTCP RR or SR packet.
                    break;
                }
            }

            off += rtcpPktLen;
        }

        // Add the newly created RTCP XR packets into pkt.
        if ((rtcpXRs != null) && !rtcpXRs.isEmpty())
        {
            for (RTCPExtendedReport rtcpXR : rtcpXRs)
                addRTCPExtendedReport(pkt, rtcpXR);
        }

        return rtcpXRs;
    }

    /**
     * Close the transformer and underlying transform engine.
     *
     * Nothing to do here.
     */
    @Override
    public void close()
    {
    }

    /**
     * Initializes a new RTP Control Protocol Extended Report (RTCP XR) packet.
     *
     * @param senderSSRC the synchronization source identifier (SSRC) of the
     * originator of the new RTCP XR packet
     * @param sourceSSRCs the SSRCs of the RTP data packet sources to be
     * reported upon by the new RTCP XR packet
     * @param sdpParams
     * @return a new RTCP XR packet with originator <tt>senderSSRC</tt> and
     * reporting upon <tt>sourceSSRCs</tt>
     */
    private RTCPExtendedReport createRTCPExtendedReport(
            int senderSSRC,
            int[] sourceSSRCs,
            String sdpParams)
    {
        RTCPExtendedReport xr = null;

        if ((sourceSSRCs != null)
                && (sourceSSRCs.length != 0)
                && (sdpParams != null)
                && sdpParams.contains(
                        RTCPExtendedReport.VoIPMetricsReportBlock
                            .SDP_PARAMETER))
        {
            xr = new RTCPExtendedReport();
            for (int sourceSSRC : sourceSSRCs)
            {
                RTCPExtendedReport.VoIPMetricsReportBlock reportBlock
                    = createVoIPMetricsReportBlock(senderSSRC, sourceSSRC);

                if (reportBlock != null)
                    xr.addReportBlock(reportBlock);
            }
            if (xr.getReportBlockCount() > 0)
            {
                xr.setSSRC(senderSSRC);
            }
            else
            {
                /*
                 * An RTCP XR packet with zero report blocks is fine, generally,
                 * but we see no reason to send such a packet.
                 */
                xr = null;
            }
        }
        return xr;
    }

    /**
     * Initializes a new RTCP XR &quot;VoIP Metrics Report Block&quot; as
     * defined by RFC 3611.
     *
     * @param senderSSRC
     * @param sourceSSRC the synchronization source identifier (SSRC) of the RTP
     * data packet source to be reported upon by the new instance
     * @return a new <tt>RTCPExtendedReport.VoIPMetricsReportBlock</tt> instance
     * reporting upon <tt>sourceSSRC</tt>
     */
    private RTCPExtendedReport.VoIPMetricsReportBlock
        createVoIPMetricsReportBlock(int senderSSRC, int sourceSSRC)
    {
        RTCPExtendedReport.VoIPMetricsReportBlock voipMetrics = null;

        if (MediaType.AUDIO.equals(mediaType))
        {
            ReceiveStream receiveStream = mediaStream.getReceiveStream(sourceSSRC);

            if (receiveStream != null)
            {
                voipMetrics
                    = createVoIPMetricsReportBlock(senderSSRC, receiveStream);
            }
        }
        return voipMetrics;
    }

    /**
     * Initializes a new RTCP XR &quot;VoIP Metrics Report Block&quot; as
     * defined by RFC 3611.
     *
     * @param senderSSRC
     * @param receiveStream the <tt>ReceiveStream</tt> to be reported upon by
     * the new instance
     * @return a new <tt>RTCPExtendedReport.VoIPMetricsReportBlock</tt> instance
     * reporting upon <tt>receiveStream</tt>
     */
    private RTCPExtendedReport.VoIPMetricsReportBlock
        createVoIPMetricsReportBlock(
                int senderSSRC,
                ReceiveStream receiveStream)
    {
        boolean outputMosCQ = true;
        boolean isSilk = true;
        int jbDiscardedPacketCount = 0;
        int jbNominalDelay = 0;

        RTCPExtendedReport.VoIPMetricsReportBlock voipMetrics
            = new RTCPExtendedReport.VoIPMetricsReportBlock();

        voipMetrics.setSourceSSRC((int) receiveStream.getSSRC());

        // loss rate
        long expectedPacketCount = 0;
        int lostPacketCount = 0;
        int processedPacketCount = 0;
        int invalidPacketCount = 0;
        long fecDecodedPacketCount = 0;
        ReceptionStats receptionStats = receiveStream.getSourceReceptionStats();
        double lossRate;

        if (receiveStream instanceof SSRCInfo)
        {
            SSRCInfo ssrcInfo = (SSRCInfo) receiveStream;

            expectedPacketCount = ssrcInfo.getExpectedPacketCount();
            lostPacketCount = receptionStats.getPDUlost();
            processedPacketCount = receptionStats.getPDUProcessed();
            invalidPacketCount = receptionStats.getPDUInvalid();
            fecDecodedPacketCount = getFECDecodedPacketCount(receiveStream);

            if (expectedPacketCount > 0)
            {
                if (lostPacketCount > 0)
                {
                      if (lostPacketCount > expectedPacketCount)
                      {
                            logger.error("More lost packets than expected"
                                    + ": lost=" + lostPacketCount
                                    + ", expected=" + expectedPacketCount);
                      }

                    /*
                     * RFC 3611 mentions that the total number of packets lost
                     * takes into account "the effects of applying any error
                     * protection such as FEC".
                     */
                    if (fecDecodedPacketCount > lostPacketCount)
                    {
                        logger.error("More FEC decoded than lost"
                             + ": FEC=" + fecDecodedPacketCount
                             + ", lost=" + lostPacketCount);
                    }

                    if ((fecDecodedPacketCount > 0)
                            && (fecDecodedPacketCount <= lostPacketCount))
                    {
                        lostPacketCount -= fecDecodedPacketCount;
                    }

                    lossRate
                        = (lostPacketCount / (double) expectedPacketCount)
                            * 256;
                    if (lossRate > 255)
                        lossRate = 255;
                    voipMetrics.setLossRate((short) lossRate);
                }
            }
        }
        else
        {
            logger.error("Could not get SSRC Info");
            outputMosCQ = false;
        }

        // round trip delay
        int rttDelay = 0;
        int rttViaSeq = mediaStream.getMediaStreamStats().getRTCPReports().getRTTViaSeq(senderSSRC);

        if (receiveStream instanceof RecvSSRCInfo)
        {
            rttDelay = ((RecvSSRCInfo) receiveStream).getRoundTripDelay(senderSSRC);
            voipMetrics.setRoundTripDelay(rttDelay);

            // End system delay.  If it's <0, put 0.
            int esd = rttViaSeq - (rttDelay / 2) + 100;
            if (esd < 0) esd = 0;
            voipMetrics.setEndSystemDelay(esd);
        }
        else
        {
            logger.error("Could not get RTT");
            outputMosCQ = false;
        }

        // signal level
        // noise level
        /*
         * The computation of noise level requires the notion of silent period
         * which we do not have (because, for example, we do not voice activity
         * detection).
         */

        // residual echo return loss (RERL)
        /*
         * WebRTC, which is available and default on OS X, appears to be able to
         * provide the residual echo return loss. Speex, which is available and
         * not default on the supported operating systems, and WASAPI, which is
         * available and default on Windows, do not seem to be able to provide
         * the metric. Taking into account the availability of the metric and
         * the distribution of the users according to operating system, the
         * support for the metric is estimated to be insufficiently useful.
         * Moreover, RFC 3611 states that RERL for "PC softphone or
         * speakerphone" is "extremely variable, consider reporting "undefined"
         * (127)".
         */

        // R factor
        /*
         * TODO Requires notions such as noise and noise sources, simultaneous
         * impairments, and others that we do not know how to define at the time
         * of this writing.
         */
        // ext. R factor
        /*
         * The external R factor is a voice quality metric describing the
         * segment of the call that is carried over a network segment external
         * to the RTP segment, for example a cellular network. We implement the
         * RTP segment only and we do not have a notion of a network segment
         * external to the RTP segment.
         */
        // MOS-LQ
        /*
         * TODO It is unclear at the time of this writing from RFC 3611 how
         * MOS-LQ is to be calculated.
         */

        // receiver configuration byte (RX config)
        // packet loss concealment (PLC)
        /*
         * We insert silence in place of lost packets by default and we have FEC
         * and/or PLC for OPUS and SILK.
         */
        byte packetLossConcealment
            = RTCPExtendedReport.VoIPMetricsReportBlock
                .DISABLED_PACKET_LOSS_CONCEALMENT;
        MediaFormat mediaFormat = mediaStream.getFormat();

        if (mediaFormat != null)
        {
            String encoding = mediaFormat.getEncoding();

            if (encoding != null)
            {
                encoding = encoding.toLowerCase();
                if (Constants.OPUS_RTP.toLowerCase().contains(encoding)
                        || Constants.SILK_RTP.toLowerCase().contains(encoding))
                {
                    packetLossConcealment
                        = RTCPExtendedReport.VoIPMetricsReportBlock
                            .STANDARD_PACKET_LOSS_CONCEALMENT;
                }

                isSilk = Constants.SILK_RTP.toLowerCase().contains(encoding);
            }
            else
            {
                logger.error("Not enough info for MOSCQ");
                outputMosCQ = false;
            }
        }
        else
        {
            logger.error("Not enough info for MOSCQ");
            outputMosCQ = false;
        }
        voipMetrics.setPacketLossConcealment(packetLossConcealment);

        // jitter buffer adaptive (JBA)
        JitterBufferControl jbc =
           MediaStreamStatsImpl.getJitterBufferControl(receiveStream);
        double discardRate;

        if (jbc == null)
        {
            logger.error("Jitter buffer control is null");
            voipMetrics.setJitterBufferAdaptive(
                    RTCPExtendedReport.VoIPMetricsReportBlock
                        .UNKNOWN_JITTER_BUFFER_ADAPTIVE);
            outputMosCQ = false;
        }
        else
        {
            // discard rate
            if (expectedPacketCount > 0)
            {
                jbDiscardedPacketCount = jbc.getDiscarded();

                if ((jbDiscardedPacketCount > 0)
                        && (jbDiscardedPacketCount <= expectedPacketCount))
                {
                    discardRate
                        = (jbDiscardedPacketCount / (double) expectedPacketCount)
                            * 256;
                    if (discardRate > 255)
                        discardRate = 255;
                    voipMetrics.setDiscardRate((short) discardRate);
                }
            }
            else
            {
                logger.error("No expected packets");
                outputMosCQ = false;
            }

            // jitter buffer nominal delay (JB nominal)
            // jitter buffer maximum delay (JB maximum)
            // jitter buffer absolute maximum delay (JB abs max)
            int maximumDelay = jbc.getMaximumDelay();
            jbNominalDelay = jbc.getNominalDelay();

            voipMetrics.setJitterBufferMaximumDelay(maximumDelay);
            voipMetrics.setJitterBufferNominalDelay(jbNominalDelay);
            if (jbc.isAdaptiveBufferEnabled())
            {
                voipMetrics.setJitterBufferAdaptive(
                        RTCPExtendedReport.VoIPMetricsReportBlock
                            .ADAPTIVE_JITTER_BUFFER_ADAPTIVE);
                voipMetrics.setJitterBufferAbsoluteMaximumDelay(
                        jbc.getAbsoluteMaximumDelay());
            }
            else
            {
                voipMetrics.setJitterBufferAdaptive(
                        RTCPExtendedReport.VoIPMetricsReportBlock
                            .NON_ADAPTIVE_JITTER_BUFFER_ADAPTIVE);
                /*
                 * Jitter buffer absolute maximum delay (JB abs max) MUST be set
                 * to jitter buffer maximum delay (JB maximum) for fixed jitter
                 * buffer implementations.
                 */
                voipMetrics.setJitterBufferAbsoluteMaximumDelay(maximumDelay);
            }

            // jitter buffer rate (JB rate)
            /*
             * TODO We do not know how to calculate it at the time of this
             * writing. It very likely is to be calculated in
             * JitterBufferBehaviour because JitterBufferBehaviour implements
             * the adaptiveness of a jitter buffer implementation.
             */
        }

        if (receptionStats instanceof RTPStats)
        {
            // burst density
            // gap density
            // burst duration
            // gap duration
            RTPStats rtpStats = (RTPStats) receptionStats;
            BurstMetrics burstMetrics = rtpStats.getBurstMetrics();
            long l = burstMetrics.getBurstMetrics();

            int gapDuration, burstDuration;
            short gapDensity, burstDensity;

            gapDuration = (int) (l & 0xFFFFL);
            l >>= 16;
            burstDuration = (int) (l & 0xFFFFL);
            l >>= 16;
            gapDensity = (short) (l & 0xFFL);
            l >>= 8;
            burstDensity = (short) (l & 0xFFL);
            l >>= 8;
            discardRate = l & 0xFFL;
            l >>= 8;
            lossRate = l & 0xFFL;

//            logger.error("Gap density=" + gapDensity);
//            logger.error("Gap duration=" + gapDuration);
//            logger.error("Burst density=" + burstDensity);
//            logger.error("Burst duration=" + burstDuration);

            voipMetrics.setBurstDensity(burstDensity);
            voipMetrics.setGapDensity(gapDensity);
            voipMetrics.setBurstDuration(burstDuration);
            voipMetrics.setGapDuration(gapDuration);

            // Gmin
            voipMetrics.setGMin(burstMetrics.getGMin());
        }

        // MOS-CQ
        /*
         * The metric may be calculated by converting an R factor determined
         * according to ITU-T G.107 or ETSI TS 101 329-5 into an estimated MOS
         * using the equation specified in G.107. However, we do not have R
         * factor.
         */
        if (outputMosCQ)
        {
            int mosCQ = getMosCQ(isSilk,
                                 rttDelay,
                                 rttViaSeq,
                                 jbNominalDelay,
                                 jbDiscardedPacketCount,
                                 processedPacketCount,
                                 lostPacketCount,
                                 invalidPacketCount,
                                 fecDecodedPacketCount);

            voipMetrics.setMosCq((byte) mosCQ);
        }
        else
        {
            logger.error("No MosCQ");
        }

        return voipMetrics;
    }

    /**
     * Gets the number of packets in a <tt>ReceiveStream</tt> which have been
     * decoded by means of FEC.
     *
     * @param receiveStream the <tt>ReceiveStream</tt> of which the number of
     * packets decoded by means of FEC is to be returned
     * @return the number of packets in <tt>receiveStream</tt> which have been
     * decoded by means of FEC
     */
    private long getFECDecodedPacketCount(ReceiveStream receiveStream)
    {
        MediaDeviceSession devSession = mediaStream.getDeviceSession();
        long fecDecodedPacketCount = 0;

        if (devSession != null)
        {
            Iterable<FECDecoderControl> decoderControls
                = devSession.getDecoderControls(
                        receiveStream,
                        FECDecoderControl.class);

            for (FECDecoderControl decoderControl : decoderControls)
                fecDecodedPacketCount += decoderControl.fecPacketsDecoded();
        }
        return fecDecodedPacketCount;
    }

    /**
     * Number of lost packets reported.
     * @return number of lost packets reported.
     */
    public long getLost()
    {
        return lost;
    }

    /**
     * The minimum inter arrival jitter value we have reported.
     * @return minimum inter arrival jitter value we have reported.
     */
    public long getMaxInterArrivalJitter()
    {
        return maxInterArrivalJitter;
    }

    /**
     * The maximum inter arrival jitter value we have reported.
     * @return maximum inter arrival jitter value we have reported.
     */
    public long getMinInterArrivalJitter()
    {
        return minInterArrivalJitter;
    }

    /**
     * Returns a reference to this class since it is performing RTP
     * transformations in here.
     *
     * @return a reference to <tt>this</tt> instance of the
     * <tt>StatisticsEngine</tt>.
     */
    @Override
    public PacketTransformer getRTCPTransformer()
    {
        return this;
    }

    /**
     * Always returns <tt>null</tt> since this engine does not require any
     * RTP transformations.
     *
     * @return <tt>null</tt> since this engine does not require any
     * RTP transformations.
     */
    @Override
    public PacketTransformer getRTPTransformer()
    {
        return this;
    }

    /**
     * Initializes a new SR or RR <tt>RTCPReport</tt> instance from a specific
     * <tt>RawPacket</tt>
     *
     * @param pkt the <tt>RawPacket</tt> to parse into a new SR or RR
     * <tt>RTCPReport</tt> instance
     * @return a new SR or RR <tt>RTCPReport</tt> instance initialized from the
     * specified <tt>pkt</tt>
     * @throws IOException if an I/O error occurs while parsing the specified
     * <tt>pkt</tt> into a new SR or RR <tt>RTCPReport</tt> instance
     */
    private RTCPReport parseRTCPReport(RawPacket pkt)
        throws IOException
    {
        RTCPReport report = null;
        switch (pkt.getRTCPPacketType())
        {
            case RTCPPacket.RR:
                report =
                    new RTCPReceiverReport(
                            pkt.getBuffer(),
                            pkt.getOffset(),
                            pkt.getLength());
            case RTCPPacket.SR:
                report =
                    new RTCPSenderReport(
                            pkt.getBuffer(),
                            pkt.getOffset(),
                            pkt.getLength());
            default:
        }

        return report;
    }

    /**
     * Transfers RTCP sender report feedback as new information about the upload
     * stream for the <tt>MediaStreamStats</tt>. Returns the packet as we are
     * listening just for sending packages.
     *
     * @param pkt the packet to reverse-transform
     * @return the packet which is the result of the reverse-transform
     */
    @Override
    public RawPacket reverseTransform(RawPacket pkt)
    {
        // SRTP may send non-RTCP packets.
        if (isRTCP(pkt))
        {
            /*
             * Remove any RTP Control Protocol Extended Report (RTCP XR) packets
             * because neither FMJ, nor RTCPSenderReport/RTCPReceiverReport
             * understands them.
             */
            List<RTCPExtendedReport> xrs = removeRTCPExtendedReports(pkt);

            logger.debug("Reverse transform of RTCP packet, remove xrs=" +
                xrs + ", newRTCP=" + isRTCP(pkt));

            // The pkt may have contained RTCP XR packets only.
            if (isRTCP(pkt))
            {
                try
                {
                    updateReceivedMediaStreamStats(pkt);
                }
                catch(Throwable t)
                {
                    if (t instanceof InterruptedException)
                    {
                        Thread.currentThread().interrupt();
                    }
                    else if (t instanceof ThreadDeath)
                    {
                        throw (ThreadDeath) t;
                    }
                    else
                    {
                        logger.error(
                                "Failed to analyze an incoming RTCP packet for"
                                    + " the purposes of statistics.",
                                t);
                    }
                }
            }
            else
            {
                // The pkt contained RTCP XR packets only.
                pkt = null;
            }

            // RTCP XR
            if (xrs != null)
            {
                RTCPReports rtcpReports
                    = mediaStream.getMediaStreamStats().getRTCPReports();

                for (RTCPExtendedReport xr : xrs)
                    rtcpReports.rtcpExtendedReportReceived(xr);
            }
        }
        else
        {
               RTCPReports rtcpReports = mediaStream.getMediaStreamStats().getRTCPReports();
            int ssrc = (int) pkt.getSSRC();

            // If this is the first packet received, store the time.
            if (rtcpReports.getFirstReceivedPacketTime(ssrc) == 0)
            {
                logger.debug("Setting first packet received for " + ssrc);
                 long time = System.currentTimeMillis();
                 rtcpReports.setFirstReceivedPacketTime(ssrc, time);
            }
        }

        return pkt;
     }

    /**
     * Transfers RTCP sender report feedback as new information about the
     * download stream for the MediaStreamStats. Finds the info needed for
     * statistics in the packet and stores it, then returns the same packet as
     * <tt>StatisticsEngine</tt> is not modifying it.
     *
     * @param pkt the packet to transform
     * @return the packet which is the result of the transform
     */
    @Override
    public RawPacket transform(RawPacket pkt)
    {
        // SRTP may send non-RTCP packets.
        if (isRTCP(pkt))
        {
            try
            {
                updateSentMediaStreamStats(pkt);
            }
            catch(Throwable t)
            {
                if (t instanceof InterruptedException)
                {
                    Thread.currentThread().interrupt();
                }
                else if (t instanceof ThreadDeath)
                {
                    throw (ThreadDeath) t;
                }
                else
                {
                    logger.error(
                            "Failed to analyze an outgoing RTCP packet for the"
                                + " purposes of statistics.",
                            t);
                }
            }

            // RTCP XR
            /*
             * We support RTCP XR VoIP Metrics Report Block only at the time of
             * this writing. While the methods addRTCPExtendedReports(RawPacket)
             * and createVoIPMetricsReportBlock(int) are aware of the fact, it
             * does not make sense to even go there for the sake of performance.
             */
            if (MediaType.AUDIO.equals(mediaType))
            {
                /*
                 * We will send RTCP XR only if the SDP attribute rtcp-xr is
                 * present and we will send only XR blocks indicated by SDP
                 * parameters.
                 */
                Object o
                    = mediaStream.getProperty(RTCPExtendedReport.SDP_ATTRIBUTE);

                if (o != null)
                {
                    String sdpParams = o.toString();

                    if ((sdpParams != null) && (sdpParams.length() != 0))
                    {
                        List<RTCPExtendedReport> xrs
                            = addRTCPExtendedReports(pkt, sdpParams);

                        logger.debug("Transform of RTCP packet, o=" + o +
                            ", xrs=" + xrs);

                        if (xrs != null)
                        {
                            RTCPReports rtcpReports
                                = mediaStream
                                    .getMediaStreamStats()
                                        .getRTCPReports();

                            for (RTCPExtendedReport xr : xrs)
                                rtcpReports.rtcpExtendedReportSent(xr);
                        }
                    }
                }
            }
        }
        else
        {
            // Update last sent Seq number, store by SSRC, wiping out last top
            // 23 bits.
            RTCPReports rtcpReports = mediaStream.getMediaStreamStats().getRTCPReports();
            int ssrc = (int) pkt.getSSRC();

            mLastSentSeqNum.put(ssrc, pkt.getSequenceNumber());

            // If this is the first packet sent store the time.
            if (rtcpReports.getFirstSentPacketTime(ssrc) == 0)
            {
                logger.debug("Setting first packet sent for " + ssrc);

                long time = System.currentTimeMillis();
                rtcpReports.setFirstSentPacketTime(ssrc, time);
            }
        }

        return pkt;
    }

    /**
     * Transfers RTCP sender/receiver report feedback as new information about
     * the upload stream for the <tt>MediaStreamStats</tt>.
     *
     * @param pkt the received RTCP packet
     * @throws IOException
     */
    private void updateReceivedMediaStreamStats(RawPacket pkt)
            throws IOException
    {
        RTCPReport r = parseRTCPReport(pkt);

        if (r != null)
        {
            mediaStream.getMediaStreamStats().getRTCPReports()
                .rtcpReportReceived(r);

            // Now we have received a report, we need to calculated the RTT.
            if (r instanceof RTCPSenderReport)
            {
                Vector<?> feedbacks = r.getFeedbackReports();
                if (feedbacks.size() > 0)
                {
                    // First get the SSRC and their seq number.
                    RTCPFeedback feedback = (RTCPFeedback) feedbacks.get(0);
                    int ssrc = (int) feedback.getSSRC();

                    // Get the seqNum, and wipe out the top 48 bits,
                    // and keep the bottom 16 bits.
                    long seqNum = feedback.getXtndSeqNum() & 0xFFFFL;

                    int lastSentSeqNum = 0;
                    if (mLastSentSeqNum.containsKey(ssrc))
                    {
                        // We are interested in the lowest 16 bits only.
                        lastSentSeqNum = mLastSentSeqNum.get(ssrc) & 0xFFFF;

                        int seqNumDiff = lastSentSeqNum - (int) seqNum;

                        // This can be -ve on a wrap, so bound it.
                        if (seqNumDiff < 0)
                        {
                            logger.error("RTT diff is -ve, round to 0");
                            seqNumDiff = 0;
                        }

                        // We need to find the time a packet represents.  That
                        // is samples * 1000 / rate.
                          // Unfortunately it doesn't seem possible to get hold of
                           // the number of samples.  So put 20, as that is the
                        // default.
                        int pTime = 20;

                        RTCPReports rtcpReports
                            = mediaStream.getMediaStreamStats().getRTCPReports();
                        rtcpReports.setRTTViaSeq((int)ssrc, seqNumDiff * pTime);
                    }
                }
            }
        }
    }

    /**
     * Transfers RTCP sender/receiver report feedback as new information about
     * the download stream for the <tt>MediaStreamStats</tt>.
     *
     * @param pkt the sent RTCP packet
     */
    private void updateSentMediaStreamStats(RawPacket pkt)
        throws IOException
    {
        RTCPReport r = parseRTCPReport(pkt);

        if (r != null)
        {
            mediaStream.getMediaStreamStats().getRTCPReports().rtcpReportSent(
                    r);
        }
    }

    /**
     *
     * A copy of C code in Accession Mobile.  Style left the same for easy of
     * copying updates in future.
     *
     * @param isSilk Whether the call is SILK or not
     * @param rttDelay       - round trip time
     * @param rttViaSeq      - rtt calculated via sequence numbers
     * @param jbLatency      - jitter buffer latency
     * @param jbDiscards     - jitter buffer discards
     * @param rxPkt          - number pkts received
     * @param rxLoss         - number pkts lost
     * @param rxDiscard      - number pkts discarded.
     * @param rxFECCorrected - number pkts forward error corrected
     * @return The MosCQ
     */
    private static int getMosCQ(boolean isSilk,
                                int rttDelay,
                                int rttViaSeq,
                                int jbLatency,
                                int jbDiscards,
                                int rxPkt,
                                int rxLoss,
                                int rxDiscard,
                                long rxFECCorrected)
    {
        // Default to unknown.
        int mosCQ = 127;

        // Ensure RTT via seq is at least as large as RTT.  The reverse can
        // occur  when the two sets of stats are updated at different times.
        if (rttViaSeq < rttDelay)
            rttViaSeq = rttDelay;

        // First we need to calculate R.
        //
        // R = Ro - Is - Id - (Ie - eff) + A

        // We need some definitions for the parameters involved.
        //
        //        Unit   Default   Range
        //
        // SLR    dB     8         0 - 18         Send loudness rating
        // RLR    dB     2         -5 - 14        Receive loudness rating
        // OLR    dB     10        -5 - 36        Overall loudness rating (OLR = SLR + RLR)
        // Nc     dBm0p  -70       -80 - -40      Circuit noise referred to 0dBr-point
        // Ps     bA     35        35 - 85        Room noise at sender
        // Ds     -      3         -3 - 3         D-Value of telephone at sender
        // Pr     bA     35        35 - 85        Room noise at receiver
        // LSTR   dB     18        13 - 23        Listener sidetone rating
        // Nfor   dBm0p  -64                      Noise floor at receive side
        // STMR   dB     15        10 - 20        Sidetone masking rating
        // T      ms     0         0 - 500        Delay from talking receiver to echo src  This is approximated by RTT_from_seq (which includes Perimeta JB) - RTT/2 + 100 (to account for audio rendering delays)
        // TELR   dB     65        0 - 65         Talker loudness echo rating
        // qdu    -      1         1 - 14         Number of quantisation distortion units
        // WEPL   dB     110       5 - 110        Weighted echo path loss
        // Tr     ms     0         0 - 1000       Round-trip delay in 4-wire loop          This is RTT_from_seq (which includes Perimeta JB)
        // Ta     ms     0         0 - 500        One-way delay from sender to receiver    This is approximated by RTT / 2 + JB latency; incoming packets are not queued in Perimeta, so incoming delay should be RTT / 2
        // Ie     -      0         0 - 40         Equipment impairment factor              Codec-specifc; ITU-T G.113
        // Ppl    %      0         0 - 100        Random packet-loss probability           This is calculated from RTCP packets, loss, discards & FEC and JB discards
        // BurstR -      1         1 - 8          Burst ratio                              Assume 1 - random packet loss
        // Bpl    -      4.3       4.3 - 40       Packet-loss robustness factor            Codec-specifc; ITU-T G.113
        double SLR    = 8;
        double RLR    = 2;
        double OLR    = SLR + RLR;
        double Nc     = -70;
        double Ps     = 35;
        double Ds     = 3;
        double Pr     = 35;
        double LSTR   = 18;
        double Nfor   = -64;
        double STMR   = 15;
        double T      = rttViaSeq - (rttDelay / 2) + 100;
        double TELR   = 65;
        double qdu    = 1;
        double WEPL   = 110;
        double Tr     = rttViaSeq;
        double Ta     = rttDelay / 2 + jbLatency;
        double Ie     = 0;     // From ITU-T G.113 for G.711 PCM

//        double Ppl    = (rxPkt != 0) ?
//            ((rxLoss + rxDiscard + jbDiscards - rxFECCorrected) * 100) / (rxPkt + rxLoss)
//            : 0;
        double Ppl    = (rxPkt != 0) ?
             ((rxLoss + rxDiscard + jbDiscards) * 100) / (rxPkt + rxLoss + rxFECCorrected)
            : 0;

        double BurstR = 1;
        double Bpl    = 25.1;    // From UTI-T G.113 for G.711 PCM

        // Clamp Ppl to cover the of sheared info from the JB, codec and RTCP stats resulting in a negative value.
        if (Ppl < 0) Ppl = 0;

        // Ro - Basic signal-to-noise-ratio
        //
        // Ro = 15 - 1.5 * (SLR + No)
        //   SLR        = see params
        //   No [dBm0p] = 10 log (10^(Nc/10) + 10^(Nos/10) + 10^(Nor/10) + 10^(Nfo/10))          Power addition of various noise sources
        //     Nc          = see params
        //     Nos [dBm0p] = Ps - SLR - Ds - 100 + 0.004 * ((Ps - OLR - Ds - 14)^2)              Circuit noise caused by room noise at sender
        //       Ps  = see params
        //       SLR = see params
        //       Ds  = see params
        //       OLR = see params
        //     Nor [dBm0p] = RLR - 121 + Pre + 0.008 * (Pre - 35)^2                              Circuit noise caused by room noise at receiver
        //       RLR = see params
        //       Pre = Pr + 10 log (1 + 10^(10 - LSTR / 10))                                     Effective room noise caused by receive sidetone path
        //         Pr   = see params
        //         LSTR = see params
        //     Nfo [dBm0p] = Nfor + RLR                                                          Noise floor at receive side
        //         Nfor = see params
        //         RLR  = see params
        //
        // The client doesn't track any of this information, so we could pre-calculate Ro using defaults.
        double Nos = Ps - SLR - Ds - 100 + 0.004 * pow(Ps - OLR - Ds - 14, 2);
        double Pre = Pr + 10 * log10(1 + pow(10, (10 - LSTR) / 10)) / log10(10); // Note: last /log10(10) term is in standard code, but not defn
        double Nor = RLR - 121 + Pre + 0.008 * pow(Pre - 35, 2);
        double Nfo = Nfor + RLR;
        double No = 10 * log10(pow(10, Nc / 10) + pow(10, Nos / 10) + pow(10, Nor / 10) + pow(10, Nfo / 10));
        double Ro = 15 - 1.5 * (SLR + No);

        // Is - Simultaneous impairment factor
        //
        // Is = Iolr + Ist + Iq
        //   Iolr = 20 * ((1 + (Xolr / 8)^8)^(1/8) - Xolr / 8)                                   Too-low values of (SLR + RLR)
        //     Xolr = OLR + 0.2 * (64 + No - RLR)
        //       OLR = see params
        //       RLR = see params
        //       No  = calculated above
        //   Ist = 12 * (1 + ((STMRo - 13) / 6)^8)^(1/8) -
        //         28 * (1 + ((STMRo + 1) / 19.4)^35)^(1/35) -                                   Non-optimum sidetone
        //         13 * (1 + ((STMRo - 3) / 33)^13)^(1/13) + 29
        //     STMRo = -10 log (10^(-STMR/10) + e^(-T/4) * 10^(-TELR/10))
        //       STMR = see params
        //       T    = see params
        //       TELR = see params
        //   Iq = 15 log (1 + 10^Y + 10^Z)                                                       Quantizing distortion
        //     Y = ((Ro - 100) / 15) + (46 / 8.4) - (G / 9)
        //       Ro = calculated above
        //       G = 1.07 + (0.258 * Q) + (0.0602 * Q^2)
        //         Q = 37 - 15 log (qdu)
        //           qdu = see params
        //     Z = (46 / 30) - (G / 40)
        //
        // The client doesn't track any of this information (except T, and that's currently hardcoded), so we could
        // pre-calculate Is using defaults.
        double Q = 37 - 15 * log10(qdu) / log10(10); // Note: last /log10(10) term is in standard code, but not defn
        double G = 1.07 + (0.258 * Q) + (0.0602 * pow(Q, 2));
        double Y = ((Ro - 100) / 15) + (46 / 8.4) - (G / 9);
        double Z = (46.0 / 30) - (G / 40);
        double Iq = 15 * log10(1 + pow(10, Y) + pow(10, Z));
        double STMRo = -10 * log10(pow(10, -STMR / 10) + exp(-T / 4) * pow(10, -TELR / 10));
        double Ist = 12 * pow(1 + pow((STMRo - 13) / 6, 8), 1.0 / 8) -
              28 * pow(1 + pow((STMRo + 1) / 19.4, 35), 1.0 / 35) -
                  13 * pow(1 + pow((STMRo - 3) / 33, 13), 1.0 / 13) +
                  29;
        double Xolr = OLR + 0.2 * (64 + No - RLR);
        double Iolr = 20 * (pow(1 + pow(Xolr / 8, 8), 1.0 / 8) - (Xolr / 8));
        double Is = Iolr + Ist + Iq;

        // Id - Delay impairment factor
        //
        // Id = Idte + Idle + Idd
        //  Idte = ((Roe - Re)/2 + sqrt(((Roe - Re)^2)/4 + 100) - 1) * (1 - e^(-T))                          Talker echo, we assume 9bD < STMR < 20 dB (default is 15)
        //    Roe = -1.5 * (No - RLR)
        //      No  = calculated above
        //      RLR = see params
        //      T   = see params
        //    Re = 80 + 2.5 * (TERV - 14)
        //      TERV = TELR - 40 log ((1 + T/10) / (1 + T/150)) + 6 * e^(-0.3 * T^2)
        //        T    = see params
        //        TELR = see params
        //  Idle = (Ro - Rle) / 2 + sqrt(((Ro - Rle)^2)/4 + 169)                                          Listener echo
        //    Ro  = calculated above
        //    Rle = 10.5 * (WEPL + 7) * (Tr + 1)^-0.25
        //      WEPL = see params
        //      Tr   = see params
        //  Idd = (Ta <= 100ms) => Idd = 0;                                                               Too-long absolute delay
        //        (Ta  > 100ms) => 25 * ((1 + X^6)^(1/6) - 3 * (1 + (X/3)^6)^(1/6) + 2)
        //    Ta = see params
        //    X  = (Ta == 0) => 0
          //         (Ta  > 0) => log (Ta / 100) / log 2
        //
        // We could pre-calculate Idte.
        // However, we need to calculate Idle (based on Tr) and Idd (based on Ta).
        double Roe = -1.5 * (No - RLR);
        double TERV = TELR - 40 * log10((1 + T / 10) / (1 + T / 150)) + 6 * exp(-0.3 * pow(T, 2));
        double Re = 80 + 2.5 * (TERV - 14);
        double Idte = ((Roe - Re) / 2 + sqrt(pow(Roe - Re, 2) / 4 + 100) - 1) * (1 - exp(-T));
        double Rle = 10.5 * (WEPL + 7) * pow(Tr + 1, -0.25);
        double Idle = (Ro - Rle) / 2 + sqrt(pow(Ro - Rle, 2) / 4 + 169);
        double X = (Ta == 0) ? 0 : log10(Ta / 100) / log10(2);
        double Idd = (Ta < 100) ? 0 : 25 * (pow(1 + pow(X, 6), 1.0 / 6) - 3 * pow(1 + pow(X / 3, 6), 1.0 / 6) + 2);
        double Id = Idte + Idle + Idd;

        // (Ie - eff) - Packet loss equipment impairment factor
        //
        // We calculate this in different ways depending on the codec in use.
        double Ie_minus_eff;

        if (!isSilk)
        {
            // The codec is G711 - either alaw or ulaw.  Either way, G.107 defines the calculation as follows.
            //
            // (Ie - eff) = Ie + (95 - Ie) * (Ppl / ((Ppl / BurstR) + Bpl))
            //   Ie     = see params
            //   Ppl    = see params
            //   BurstR = see params
            //   Bpl    = see params
            //
            // We need to calculate based on Ie (codec-dependent), Ppl, and Bpl (codec-dependent).  BurstR is defaulted.
            Ie_minus_eff = Ie + (95 - Ie) * (Ppl / (Ppl / BurstR + Bpl));
        }
        else
        {
            // An alternate calculation for SILK (http://www.maths.tcd.ie/~dwmalone/p/qcman2013.pdf) is as follows.
            // A drawback of G.113 is that it doesn't define Ie and Bpl constants for SILK - it didn't exist yet.
            // The constants below come from empirical testing using PESQ.  They are not tuned for our specific implementation.
            Ie_minus_eff = 18.3442 * log10(1 + 1.54894 * Ppl) + 1.31953;
        }

        // A - Advantage factor
        //
        // A = value of 0-20 based on misc. parameters.  Defined in ITU-T G.113.  We need to decide which value to use.
        double A = 1;  // This is based on "advantage of access".  Well, it's
                      // VoIP, so bit of an advantage... right?

        // Add that all up to calculate the R value.
        //
        // R = Ro - Is - Id - (Ie - eff) + A
        double R = Ro - Is - Id - Ie_minus_eff + A;

        // Now, convert the R value into a MOS CQ value.
        //
        // MOS CQ = (R < 0)       1
        //          (0 < R < 100) 1 + 0.035 * R + R * (R - 60) * (100 - R) * 7 * 10^(-6)
        //          (R > 100)     4.5
        //
        // We multiply this by ten (as RTCP-XR represents the info as a value
        // from 10-50).
        mosCQ = (int) ((R < 0)   ? 10 :
          (R < 100) ? 10 * (1 + 0.035 * R + R * (R - 60) * (100 - R) * 7 * pow(10, -6)) :
          45);

        //logger.info("MosCQ=" + mosCQ);

        logger.info("MosSQ=" + mosCQ + ", " +
                    "isSilk =" + isSilk + ", " +
                    "rttDelay =" + rttDelay + ", " +
                    "rttViaSeq =" + rttViaSeq + ", " +
                    "jbLatency =" + jbLatency + ", " +
                    "jbDiscards =" + jbDiscards + ", " +
                    "rxPkt =" + rxPkt  + ", " +
                    "rxLoss ="  + rxLoss + ", " +
                    "rxDiscard =" + rxDiscard + ", " +
                    "rxFECCorrected =" + rxFECCorrected);

        return mosCQ;
    }

    // Dull wrappers, so the code above matches the C equivalent, so
    // we can diff them against each other.
    private static double sqrt(double d)
    {
        return pow(d, 0.5);
    }

    private static double exp(double d)
    {
       return Math.exp(d);
    }

    private static double pow(double a, double b)
    {
       return Math.pow(a, b);
    }

    private static double log10(double a)
    {
      return Math.log10(a);
    }

    public static void main(String[] args)
    {
        getMosCQ(false, // boolean isSilk,
                 0, //int rttDelay,
                 0, //int rttViaSeq,
                 500, // int jbLatency,
                 2, //int jbDiscards,
                 1000, //int rxPkt,
                 100, //int rxLoss,
                 0, //int rxDiscard,
                 300);//,long rxFECCorrected,
    }
}
