/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
// Portions (c) Microsoft Corporation. All rights reserved.
package org.jitsi.impl.neomedia;

import java.awt.*;
import java.net.*;
import java.util.*;

import javax.media.control.*;
import javax.media.format.*;
import javax.media.protocol.*;
import javax.media.rtp.*;

import org.apache.commons.math3.stat.descriptive.*;
import org.jitsi.impl.neomedia.device.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.control.*;
import org.jitsi.service.neomedia.format.*;
import org.jitsi.service.neomedia.rtp.*;
import org.jitsi.util.*;

import net.sf.fmj.media.rtp.*;

/**
 * Class used to compute stats concerning a MediaStream.
 *
 * @author Vincent Lucas
 * @author Boris Grozev
 */
/**
 * @author enh
 *
 */
public class MediaStreamStatsImpl
    implements MediaStreamStats
{
    /*
     * A copy of the reports for this Media Stream.
     */
    private RTCPReports mReports = new RTCPReports();

    /**
     * The <tt>Logger</tt> used by the <tt>MediaStreamImpl</tt> class and its
     * instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(MediaStreamStatsImpl.class);

    /**
     * Enumeration of the direction (DOWNLOAD or UPLOAD) used for the stats.
     */
    private enum StreamDirection
    {
        DOWNLOAD,
        UPLOAD
    }

    /**
     * The source data stream to analyze in order to compute the stats.
     */
    private final MediaStreamImpl mediaStreamImpl;

    /**
     * The last time these stats have been updated.
     */
    private long updateTimeMs;

    /**
     * The last number of received/sent packets.
     */
    private long[] nbPackets = {0, 0};

    /**
     * Statistics (min/max/avg) about jitter in both directions.
     */
    private SummaryStatistics[] jitterStats = { new SynchronizedSummaryStatistics(),  new SynchronizedSummaryStatistics()};

    /**
     * The last number of sent packets when the last feedback has been received.
     * This counter is used to compute the upload loss rate.
     */
    private long uploadFeedbackNbPackets = 0;

    /**
     * The last number of download/upload lost packets.
     */
    private long[] nbLost = {0, 0};

    /**
     * The total number of discarded packets
     */
    private long nbDiscarded = 0;

    /**
     * The number of packets for which FEC data was decoded. This is only
     */
    private long nbFec = 0;

    /**
     * The last number of received/sent Bytes.
     */
    private long[] nbByte = {0, 0};

    /**
     * The last download/upload loss rate computed (in %).
     */
    private double[] percentLoss = {0, 0};

    /**
     * The last percent of discarded packets
     */
    private double percentDiscarded = 0;

    /**
     * The last used bandwidth computed in download/upload (in Kbit/s).
     */
    private double[] rateKiloBitPerSec = {0, 0};

    /**
     * The last jitter received/sent in a RTCP feedback (in RTP timestamp
     * units).
     */
    private double[] jitterRTPTimestampUnits = {0, 0};

    /**
     * The RTT computed with the RTCP feedback (cf. RFC3550, section 6.4.1,
     * subsection "delay since last SR (DLSR): 32 bits").
     * -1 if the RTT has not been computed yet. Otherwise the RTT in ms.
     */
    private long rttMs = -1;

    /**
     * RTT statistics.
     */
    private SummaryStatistics  rttMsSummary = new SynchronizedSummaryStatistics();

    /**
     * Creates a new instance of stats concerning a MediaStream.
     *
     * @param mediaStreamImpl The MediaStreamImpl used to compute the stats.
     */
    public MediaStreamStatsImpl(MediaStreamImpl mediaStreamImpl)
    {
        this.updateTimeMs = System.currentTimeMillis();
        this.mediaStreamImpl = mediaStreamImpl;

        // This turns on test code that outputs the RTCP reports
        // for this stream to error logging.
        //startRTCPTestCode();
    }

    /*
     * (non-Javadoc)
     * @see org.jitsi.service.neomedia.MediaStreamStats#getRTCPReports()
     */
    @Override
    public RTCPReports getRTCPReports()
    {
        return mReports;
    }

    /* (non-Javadoc)
     * @see org.jitsi.service.neomedia.MediaStreamStats#getRTCPRRForRX()
     */
    @Override
    public RTCPReport getReceivedRTCPRR(long ssrc)
    {
        RTCPReport rrForXR = mReports.getReceivedRTCPReport((int) ssrc);

        return rrForXR;
    }

    /* (non-Javadoc)
     * @see org.jitsi.service.neomedia.MediaStreamStats#getRTCPRRForTX()
     */
    /* (non-Javadoc)
     * @see org.jitsi.service.neomedia.MediaStreamStats#getRTCPRRForTX()
     */
    @Override
    public RTCPReport getSentRTCPRR(long ssrc)
    {
       RTCPReport rrForTX = mReports.getSentRTCPReport((int) ssrc);

       return rrForTX;
    }

    /* (non-Javadoc)
     * @see org.jitsi.service.neomedia.MediaStreamStats#getReceivedRTCPVoIPMetrics(long)
     */
    @Override
    public RTCPExtendedReport.VoIPMetricsReportBlock getReceivedRTCPVoIPMetrics(long ssrc)
    {
        return mReports.getReceivedRTCPVoIPMetrics((int)ssrc);
    }

    /* (non-Javadoc)
     * @see org.jitsi.service.neomedia.MediaStreamStats#getSentRTCPVoIPMetrics(long)
     */
    @Override
    public RTCPExtendedReport.VoIPMetricsReportBlock getSentRTCPVoIPMetrics(long ssrc)
    {
        return mReports.getSentRTCPVoIPMetrics((int) ssrc);
    }

    /* (non-Javadoc)
     * @see org.jitsi.service.neomedia.MediaStreamStats#getReceivedFeedback(long)
     */
    @Override
    public RTCPFeedback getReceivedFeedback(long ssrc)
    {
        return mReports.getReceivedRTCPFeedback((int)ssrc);
    }

    /* (non-Javadoc)
     * @see org.jitsi.service.neomedia.MediaStreamStats#getSentFeedback(long)
     */
    @Override
    public RTCPFeedback getSentFeedback(long ssrc)
    {
        return mReports.getSentRTCPFeedback((int)ssrc);
    }

    /* (non-Javadoc)
     * @see org.jitsi.service.neomedia.MediaStreamStats#getFirstSentPacketTime(long)
     */
    @Override
    public long getFirstSentPacketTime(long ssrc)
    {
        return mReports.getFirstSentPacketTime((int) ssrc);
    }

    /* (non-Javadoc)
     * @see org.jitsi.service.neomedia.MediaStreamStats#getFirstReceivedPacketTime(long)
     */
    @Override
    public long getFirstReceivedPacketTime(long ssrc)
    {
        return mReports.getFirstReceivedPacketTime((int) ssrc);
    }

    /**
     * Computes and updates information for a specific stream.
     */
    @Override
    public void updateStats()
    {
        // Gets the current time.
        long currentTimeMs = System.currentTimeMillis();

        // UPdates stats for the download stream.
        this.updateStreamDirectionStats(
                StreamDirection.DOWNLOAD,
                currentTimeMs);
        // UPdates stats for the upload stream.
        this.updateStreamDirectionStats(
                StreamDirection.UPLOAD,
                currentTimeMs);

        // Saves the last update values.
        this.updateTimeMs = currentTimeMs;
    }

    /**
     * Computes and updates information for a specific stream.
     *
     * @param streamDirection The stream direction (DOWNLOAD or UPLOAD) of the
     * stream from which this function updates the stats.
     * @param currentTimeMs The current time in ms.
     */
    private void updateStreamDirectionStats(
            StreamDirection streamDirection,
            long currentTimeMs)
    {
        int streamDirectionIndex = streamDirection.ordinal();

        // Gets the current number of packets correctly received since the
        // beginning of this stream.
        long newNbRecv = this.getNbPDU(streamDirection);
        // Gets the number of byte received/sent since the beginning of this
        // stream.
        long newNbByte = this.getNbBytes(streamDirection);

        // Computes the number of update steps which has not been done since
        // last update.
        long nbSteps = newNbRecv - this.nbPackets[streamDirectionIndex];
        // Even if the remote peer does not send any packets (i.e. is
        // microphone is muted), Jitsi must updates it stats. Thus, Jitsi
        // computes a number of steps equivalent as if Jitsi receives a packet
        // each 20ms (default value).
        if(nbSteps == 0)
        {
            nbSteps = (currentTimeMs - this.updateTimeMs) / 20;
        }

        // The upload percentLoss is only computed when a new RTCP feedback is
        // received. This is not the case for the download percentLoss which is
        // updated for each new RTP packet received.
        // Computes the loss rate for this stream.
        if(streamDirection == StreamDirection.DOWNLOAD)
        {
            // Gets the current number of losses in download since the beginning
            // of this stream.
            long newNbLost =
                this.getDownloadNbPDULost() - this.nbLost[streamDirectionIndex];

            updateNbLoss(streamDirection, newNbLost, nbSteps + newNbLost);

            long newNbDiscarded = this.getNbDiscarded() - this.nbDiscarded;
            updateNbDiscarded(newNbDiscarded, nbSteps + newNbDiscarded);
        }

        // Computes the bandwidth used by this stream.
        double newRateKiloBitPerSec =
            MediaStreamStatsImpl.computeRateKiloBitPerSec(
                    newNbByte - this.nbByte[streamDirectionIndex],
                    currentTimeMs - this.updateTimeMs);
        this.rateKiloBitPerSec[streamDirectionIndex] =
            MediaStreamStatsImpl.computeEWMA(
                    nbSteps,
                    this.rateKiloBitPerSec[streamDirectionIndex],
                    newRateKiloBitPerSec);

        // Saves the last update values.
        this.nbPackets[streamDirectionIndex] = newNbRecv;
        this.nbByte[streamDirectionIndex] = newNbByte;

        updateNbFec();
    }

    /**
     * Returns the local IP address of the MediaStream.
     *
     * @return the local IP address of the stream.
     */
    @Override
    public String getLocalIPAddress()
    {
        InetSocketAddress mediaStreamLocalDataAddress
            = mediaStreamImpl.getLocalDataAddress();

        return
            (mediaStreamLocalDataAddress == null)
                ? null
                : mediaStreamLocalDataAddress.getAddress().getHostAddress();
    }

    /**
     * Returns the local port of the MediaStream.
     *
     * @return the local port of the stream.
     */
    @Override
    public int getLocalPort()
    {
        InetSocketAddress mediaStreamLocalDataAddress
            = mediaStreamImpl.getLocalDataAddress();

        return
            (mediaStreamLocalDataAddress == null)
                ? -1
                : mediaStreamLocalDataAddress.getPort();
    }

    /**
     * Returns the remote IP address of the MediaStream.
     *
     * @return the remote IP address of the stream.
     */
    @Override
    public String getRemoteIPAddress()
    {
        MediaStreamTarget mediaStreamTarget = mediaStreamImpl.getTarget();

        // Gets this stream IP address endpoint. Stops if the endpoint is
        // disconnected.
        return
            (mediaStreamTarget == null)
                ? null
                : mediaStreamTarget.getDataAddress().getAddress()
                        .getHostAddress();
    }

    /**
     * Returns the remote port of the MediaStream.
     *
     * @return the remote port of the stream.
     */
    @Override
    public int getRemotePort()
    {
        MediaStreamTarget mediaStreamTarget = mediaStreamImpl.getTarget();

        // Gets this stream port endpoint. Stops if the endpoint is
        // disconnected.
        return
            (mediaStreamTarget == null)
                ? -1
                : mediaStreamTarget.getDataAddress().getPort();
    }

    /**
     * Returns the MediaStream enconding.
     *
     * @return the encoding used by the stream.
     */
    @Override
    public String getEncoding()
    {
        MediaFormat format = mediaStreamImpl.getFormat();

        return (format == null) ? null : format.getEncoding();
    }

    /**
     * Returns the MediaStream enconding rate (in Hz)..
     *
     * @return the encoding rate used by the stream.
     */
    @Override
    public String getEncodingClockRate()
    {
        MediaFormat format = mediaStreamImpl.getFormat();

        return (format == null) ? null : format.getRealUsedClockRateString();
    }

    /**
     * Returns the upload video format if this stream uploads a video, or null
     * if not.
     *
     * @return the upload video format if this stream uploads a video, or null
     * if not.
     */
    private VideoFormat getUploadVideoFormat()
    {
        MediaDeviceSession deviceSession = mediaStreamImpl.getDeviceSession();

        return
            (deviceSession instanceof VideoMediaDeviceSession)
                ? ((VideoMediaDeviceSession) deviceSession)
                    .getSentVideoFormat()
                : null;
    }

    /**
     * Returns the download video format if this stream downloads a video, or
     * null if not.
     *
     * @return the download video format if this stream downloads a video, or
     * null if not.
     */
    private VideoFormat getDownloadVideoFormat()
    {
        MediaDeviceSession deviceSession = mediaStreamImpl.getDeviceSession();

        return
            (deviceSession instanceof VideoMediaDeviceSession)
                ? ((VideoMediaDeviceSession) deviceSession)
                    .getReceivedVideoFormat()
                : null;
    }

    /**
     * Returns the upload video size if this stream uploads a video, or null if
     * not.
     *
     * @return the upload video size if this stream uploads a video, or null if
     * not.
     */
    @Override
    public Dimension getUploadVideoSize()
    {
        VideoFormat format = getUploadVideoFormat();

        return (format == null) ? null : format.getSize();
    }

    /**
     * Returns the download video size if this stream downloads a video, or
     * null if not.
     *
     * @return the download video size if this stream downloads a video, or null
     * if not.
     */
    @Override
    public Dimension getDownloadVideoSize()
    {
        VideoFormat format = getDownloadVideoFormat();

        return (format == null) ? null : format.getSize();
    }

    /**
     * Returns the percent loss of the download stream.
     *
     * @return the last loss rate computed (in %).
     */
    @Override
    public double getDownloadPercentLoss()
    {
        return this.percentLoss[StreamDirection.DOWNLOAD.ordinal()];
    }

    /**
     * @return the total percentage packet loss in the download direction
     */
    @Override
    public float getDownloadTotalPercentLost()
    {
        long downloadedPackets = getDownloadTotalPackets();
        downloadedPackets = Math.max(downloadedPackets, 1);
        return (nbLost[StreamDirection.DOWNLOAD.ordinal()] / downloadedPackets) * 100;
    }

    /**
     * @return the total percentage packet loss in the upload direction
     */
    @Override
    public float getUploadTotalPercentLost() {
        long uploadedPackets = getUploadTotalPackets();
        uploadedPackets = Math.max(uploadedPackets, 1);
        return (nbLost[StreamDirection.UPLOAD.ordinal()] / uploadedPackets) * 100;
    }

    /**
     * @return the total number of packets downloaded
     */
    @Override
    public long getDownloadTotalPackets()
    {
        return this.nbPackets[StreamDirection.DOWNLOAD.ordinal()];
    }

    /**
     * @return the total number of packets
     */
    @Override
    public long getUploadTotalPackets()
    {
        return this.nbPackets[StreamDirection.UPLOAD.ordinal()];
    }

    /**
     * Returns the percent of discarded packets
     *
     * @return the percent of discarded packets
     */
    @Override
    public double getPercentDiscarded()
    {
        return percentDiscarded;
    }

    /**
     * Returns the percent loss of the upload stream.
     *
     * @return the last loss rate computed (in %).
     */
    @Override
    public double getUploadPercentLoss()
    {
        return this.percentLoss[StreamDirection.UPLOAD.ordinal()];
    }

    /**
     * Returns the bandwidth used by this download stream.
     *
     * @return the last used download bandwidth computed (in Kbit/s).
     */
    @Override
    public double getDownloadRateKiloBitPerSec()
    {
        return this.rateKiloBitPerSec[StreamDirection.DOWNLOAD.ordinal()];
    }

    /**
     * Returns the bandwidth used by this download stream.
     *
     * @return the last used upload bandwidth computed (in Kbit/s).
     */
    @Override
    public double getUploadRateKiloBitPerSec()
    {
        return this.rateKiloBitPerSec[StreamDirection.UPLOAD.ordinal()];
    }

    /**
     * Returns the jitter average of this download stream.
     *
     * @return the last jitter average computed (in ms).
     */
    @Override
    public double getDownloadJitterMs()
    {
        return this.getJitterMs(StreamDirection.DOWNLOAD);
    }

    /**
     * Returns the jitter average of this upload stream.
     *
     * @return the last jitter average computed (in ms).
     */
    @Override
    public double getUploadJitterMs()
    {
        return this.getJitterMs(StreamDirection.UPLOAD);
    }

    /**
     * Returns the jitter average of this upload/download stream.
     *
     * @param streamDirection The stream direction (DOWNLOAD or UPLOAD) of the
     * stream from which this function retrieve the jitter.
     *
     * @return the last jitter average computed (in ms).
     */
    private double getJitterMs(StreamDirection streamDirection)
    {
        MediaFormat format = mediaStreamImpl.getFormat();
        double clockRate;

        if (format == null)
        {
            MediaType mediaType = mediaStreamImpl.getMediaType();

            clockRate = MediaType.VIDEO.equals(mediaType) ? 90000 : -1;
        }
        else
            clockRate = format.getClockRate();

        if (clockRate <= 0)
            return -1;

        // RFC3550 says that concerning the RTP timestamp unit (cf. section 5.1
        // RTP Fixed Header Fields, subsection timestamp: 32 bits):
        // As an example, for fixed-rate audio the timestamp clock would likely
        // increment by one for each sampling period.
        //
        // Thus we take the jitter in RTP timestamp units, convert it to seconds
        // (/ clockRate) and finally converts it to milliseconds  (* 1000).
        return
            (jitterRTPTimestampUnits[streamDirection.ordinal()] / clockRate)
                * 1000.0;
    }

    /**
     * Updates the jitter stream stats with the new feedback sent.
     *
     * @param feedback The last RTCP feedback sent by the MediaStream.
     * @param streamDirection The stream direction (DOWNLOAD or UPLOAD) of the
     * stream from which this function retrieve the jitter.
     */
    private void updateJitterRTPTimestampUnits(
            RTCPFeedback feedback,
            StreamDirection streamDirection)
    {
        // Updates the download jitter in RTP timestamp units.
        // There is no need to compute a jitter average, since (cf. RFC3550,
        // section 6.4.1 SR: Sender Report RTCP Packet, subsection interarrival
        // jitter: 32 bits) the value contained in the RTCP sender report packet
        // contains a mean deviation of the jitter.
        this.jitterRTPTimestampUnits[streamDirection.ordinal()] =
            feedback.getJitter();

        jitterStats[streamDirection.ordinal()].addValue(getJitterMs(streamDirection));
    }

    /**
     * Updates this stream stats with the new feedback sent.
     *
     * @param feedback The last RTCP feedback sent by the MediaStream.
     */
    @Override
    public void updateNewSentFeedback(RTCPFeedback feedback)
    {
        updateJitterRTPTimestampUnits(feedback, StreamDirection.DOWNLOAD);

        // No need to update the download loss as we have a more accurate value
        // in the global reception stats, which are updated for each new packet
        // received.
    }

    /**
     * Updates this stream stats with the new feedback received.
     *
     * @param feedback The last RTCP feedback received by the MediaStream.
     */
    @Override
    public void updateNewReceivedFeedback(RTCPFeedback feedback)
    {
        StreamDirection streamDirection = StreamDirection.UPLOAD;

        updateJitterRTPTimestampUnits(feedback, streamDirection);

        // Updates the loss rate with the RTCP sender report feedback, since
        // this is the only information source available for the upalod stream.
        long uploadNewNbRecv = feedback.getXtndSeqNum();
        long newNbLost =
            feedback.getNumLost() - nbLost[streamDirection.ordinal()];
        long nbSteps = uploadNewNbRecv - uploadFeedbackNbPackets;

        updateNbLoss(streamDirection, newNbLost, nbSteps);

        // Updates the upload loss counters.
        uploadFeedbackNbPackets = uploadNewNbRecv;

        // Computes RTT.
        rttMs = computeRTTInMs(feedback);

        // Assume RTT information arrives at regular intervals.
        if (rttMs != -1)
        {
            rttMsSummary.addValue(rttMs);
        }
    }

    /**
     * Updates the number of loss for a given stream.
     *
     * @param streamDirection The stream direction (DOWNLOAD or UPLOAD) of the
     * stream from which this function updates the stats.
     * @param newNbLost The last update of the number of lost.
     * @param nbSteps The number of elapsed steps since the last number of loss
     * update.
     */
    private void updateNbLoss(
            StreamDirection streamDirection,
            long newNbLost,
            long nbSteps)
    {
        int streamDirectionIndex = streamDirection.ordinal();

        double newPercentLoss = MediaStreamStatsImpl.computePercentLoss(
                nbSteps,
                newNbLost);
        this.percentLoss[streamDirectionIndex] =
            MediaStreamStatsImpl.computeEWMA(
                    nbSteps,
                    this.percentLoss[streamDirectionIndex],
                    newPercentLoss);

        // Saves the last update number download lost value.
        this.nbLost[streamDirectionIndex] += newNbLost;
    }

    /**
     * Computes the loss rate.
     *
     * @param nbLostAndRecv The number of lost and received packets.
     * @param nbLost The number of lost packets.
     *
     * @return The loss rate in percent.
     */
    private static double computePercentLoss(long nbLostAndRecv, long nbLost)
    {
        if(nbLostAndRecv == 0)
        {
            return 0;
        }
        return ((double) 100 * nbLost) / ((nbLostAndRecv));
    }

    /**
     * Computes the bandwidth usage in Kilo bits per seconds.
     *
     * @param nbByteRecv The number of Byte received.
     * @param callNbTimeMsSpent The time spent since the mediaStreamImpl is
     * connected to the endpoint.
     *
     * @return the bandwidth rate computed in Kilo bits per seconds.
     */
    private static double computeRateKiloBitPerSec(
            long nbByteRecv,
            long callNbTimeMsSpent)
    {
        if(nbByteRecv == 0)
        {
            return 0;
        }
        return (nbByteRecv * 8.0 / 1000.0) / (callNbTimeMsSpent / 1000.0);
    }

    /**
     * Gets the <tt>JitterBufferControl</tt> of a <tt>ReceiveStream</tt>.
     *
     * @param receiveStream the <tt>ReceiveStream</tt> to get the
     * <tt>JitterBufferControl</tt> of
     * @return the <tt>JitterBufferControl</tt> of <tt>receiveStream</tt>.
     */
    public static JitterBufferControl getJitterBufferControl(
            ReceiveStream receiveStream)
    {
        DataSource ds = receiveStream.getDataSource();

        if (ds instanceof PushBufferDataSource)
        {
            for (PushBufferStream pbs
                    : ((PushBufferDataSource) ds).getStreams())
            {
                JitterBufferControl pqc
                    = (JitterBufferControl)
                        pbs.getControl(JitterBufferControl.class.getName());

                if (pqc != null)
                    return pqc;
            }
        }
        return null;
    }

    /**
     * Computes an Exponentially Weighted Moving Average (EWMA). Thus, the most
     * recent history has a more preponderant importance in the average
     * computed.
     *
     * @param nbStepSinceLastUpdate The number of step which has not been
     * computed since last update. In our case the number of packets received
     * since the last computation.
     * @param lastValue The value computed during the last update.
     * @param newValue The value newly computed.
     *
     * @return The EWMA average computed.
     */
    private static double computeEWMA(
            long nbStepSinceLastUpdate,
            double lastValue,
            double newValue)
    {
        // For each new packet received the EWMA moves by a 0.1 coefficient.
        double EWMACoeff = 0.01 * nbStepSinceLastUpdate;
        // EWMA must be <= 1.
        if(EWMACoeff > 1)
        {
            EWMACoeff = 1.0;
        }
        return lastValue * (1.0 - EWMACoeff) + newValue * EWMACoeff;
    }

    /**
     * Returns the number of Protocol Data Units (PDU) sent/received since the
     * beginning of the session.
     *
     * @param streamDirection The stream direction (DOWNLOAD or UPLOAD) of the
     * stream from which this function retrieve the number of sent/received
     * packets.
     *
     * @return the number of packets sent/received for this stream.
     */
    private long getNbPDU(StreamDirection streamDirection)
    {
        StreamRTPManager rtpManager = mediaStreamImpl.getRTPManager();
        long nbPDU = 0;

        if(rtpManager != null)
        {
            switch(streamDirection)
            {
            case UPLOAD:
                nbPDU = rtpManager.getGlobalTransmissionStats().getRTPSent();
                break;

            case DOWNLOAD:
                GlobalReceptionStats globalReceptionStats
                    = rtpManager.getGlobalReceptionStats();

                nbPDU
                    = globalReceptionStats.getPacketsRecd()
                        - globalReceptionStats.getRTCPRecd();
                break;
            }
        }
        return nbPDU;
    }

    /**
     * Returns the number of Protocol Data Units (PDU) lost in download since
     * the beginning of the session.
     *
     * @return the number of packets lost for this stream.
     */
    private long getDownloadNbPDULost()
    {
        MediaDeviceSession devSession = mediaStreamImpl.getDeviceSession();
        int nbLost = 0;

        if (devSession != null)
        {
            for(ReceiveStream receiveStream : devSession.getReceiveStreams())
                nbLost += receiveStream.getSourceReceptionStats().getPDUlost();
        }
        return nbLost;
    }

    /**
     * Returns the total number of Protocol Data Units (PDU) discarded by the
     * FMJ packet queue since the beginning of the session. It's the sum over
     * all <tt>ReceiveStream</tt>s of the <tt>MediaStream</tt>
     *
     * @return the number of discarded packets.
     */
    @Override
    public long getNbDiscarded()
    {
        int nbDiscarded = 0;
        for(PacketQueueControl pqc : getPacketQueueControls())
            nbDiscarded =+ pqc.getDiscarded();
        return nbDiscarded;
    }

    /**
     * Returns the number of Protocol Data Units (PDU) discarded by the
     * FMJ packet queue since the beginning of the session due to shrinking.
     * It's the sum over all <tt>ReceiveStream</tt>s of the <tt>MediaStream</tt>
     *
     * @return the number of discarded packets due to shrinking.
     */
    @Override
    public int getNbDiscardedShrink()
    {
        int nbDiscardedShrink = 0;
        for(PacketQueueControl pqc : getPacketQueueControls())
            nbDiscardedShrink =+ pqc.getDiscardedShrink();
        return nbDiscardedShrink;
    }

    /**
     * Returns the number of Protocol Data Units (PDU) discarded by the
     * FMJ packet queue since the beginning of the session because it was full.
     * It's the sum over all <tt>ReceiveStream</tt>s of the <tt>MediaStream</tt>
     *
     * @return the number of discarded packets because it was full.
     */
    @Override
    public int getNbDiscardedFull()
    {
        int nbDiscardedFull = 0;
        for(PacketQueueControl pqc : getPacketQueueControls())
            nbDiscardedFull =+ pqc.getDiscardedFull();
        return nbDiscardedFull;
    }

    /**
     * Returns the number of Protocol Data Units (PDU) discarded by the
     * FMJ packet queue since the beginning of the session because they were late.
     * It's the sum over all <tt>ReceiveStream</tt>s of the <tt>MediaStream</tt>
     *
     * @return the number of discarded packets because they were late.
     */
    @Override
    public int getNbDiscardedLate()
    {
        int nbDiscardedLate = 0;
        for(PacketQueueControl pqc : getPacketQueueControls())
            nbDiscardedLate =+ pqc.getDiscardedLate();
        return nbDiscardedLate;
    }

    /**
     * Returns the number of Protocol Data Units (PDU) discarded by the
     * FMJ packet queue since the beginning of the session during resets.
     * It's the sum over all <tt>ReceiveStream</tt>s of the <tt>MediaStream</tt>
     *
     * @return the number of discarded packets during resets.
     */
    @Override
    public int getNbDiscardedReset()
    {
        int nbDiscardedReset = 0;
        for(PacketQueueControl pqc : getPacketQueueControls())
            nbDiscardedReset =+ pqc.getDiscardedReset();
        return nbDiscardedReset;
    }

    /**
     * Returns the number of sent/received bytes since the beginning of the
     * session.
     *
     * @param streamDirection The stream direction (DOWNLOAD or UPLOAD) of the
     * stream from which this function retrieve the number of sent/received
     * bytes.
     *
     * @return the number of sent/received bytes for this stream.
     */
    private long getNbBytes(StreamDirection streamDirection)
    {
        long nbBytes = 0;
        StreamRTPManager rtpManager = mediaStreamImpl.getRTPManager();

        if(rtpManager != null)
        {
            switch(streamDirection)
            {
            case DOWNLOAD:
                nbBytes = rtpManager.getGlobalReceptionStats().getBytesRecd();
                break;
            case UPLOAD:
                nbBytes
                    = rtpManager.getGlobalTransmissionStats().getBytesSent();
                break;
            }
        }
        return nbBytes;
    }

    /**
     * Computes the RTT with the data (LSR and DLSR) contained in the last
     * RTCP Sender Report (RTCP feedback). This RTT computation is based on
     * RFC3550, section 6.4.1, subsection "delay since last SR (DLSR): 32
     * bits".
     *
     * @param feedback The last RTCP feedback received by the MediaStream.
     *
     * @return The RTT in milliseconds, or -1 if the RTT is not computable.
     */
    private long computeRTTInMs(RTCPFeedback feedback)
    {
        // Computes RTT.
        long currentTime = System.currentTimeMillis();
        long DLSR = feedback.getDLSR();
        long LSR = feedback.getLSR();

        // If the peer sending us the sender report has at least received on
        // sender report from our side, then computes the RTT.
        if(DLSR != 0 && LSR != 0)
        {
            long LSRs = LSR >> 16;
            long LSRms = ((LSR & 0xffff) * 1000) / 0xffff;
            long DLSRs = DLSR / 0xffff;
            long DLSRms = ((DLSR & 0xffff) *1000) / 0xffff;
            long currentTimeS = (currentTime / 1000) & 0x0000ffff;
            long currentTimeMs = (currentTime % 1000);

            long rttS = currentTimeS - DLSRs - LSRs;
            long rttMs = currentTimeMs - DLSRms - LSRms;

            long computedRTTms = (rttS * 1000) + rttMs;

            // If the RTT is greater than a minute there might be a bug. Thus we
            // log the info to see the source of this error.
            if (computedRTTms > 60000)
            {
                logger.info("RTT computation seems to be wrong ("
                        + computedRTTms + "> 60 seconds):"

                        + "\n\tcurrentTime: " + currentTime
                        + " (" + Long.toHexString(currentTime) + ")"
                        + "\n\tDLSR: " + DLSR
                        + " (" + Long.toHexString(DLSR) + ")"
                        + "\n\tLSR: " + LSR
                        + " (" + Long.toHexString(LSR) + ")"

                        + "\n\n\tcurrentTimeS: " + currentTimeS
                        + " (" + Long.toHexString(currentTimeS) + ")"
                        + "\n\tDLSRs: " + DLSRs
                        + " (" + Long.toHexString(DLSRs) + ")"
                        + "\n\tLSRs: " + LSRs
                        + " (" + Long.toHexString(LSRs) + ")"
                        + "\n\trttS: " + rttS
                        + " (" + Long.toHexString(rttS) + ")"

                        + "\n\n\tcurrentTimeMs: " + currentTimeMs
                        + " (" + Long.toHexString(currentTimeMs) + ")"
                        + "\n\tDLSRms: " + DLSRms
                        + " (" + Long.toHexString(DLSRms) + ")"
                        + "\n\tLSRms: " + LSRms
                        + " (" + Long.toHexString(LSRms) + ")"
                        + "\n\trttMs: " + rttMs
                        + " (" + Long.toHexString(rttMs) + ")"
                        );
            }

            return computedRTTms;
        }
        // Else the RTT can not be computed yet.
        return -1;
    }

    /**
     * Returns the RTT computed with the RTCP feedback (cf. RFC3550, section
     * 6.4.1, subsection "delay since last SR (DLSR): 32 bits").
     *
     * @return The RTT computed with the RTCP feedback. Returns -1 if the RTT
     * has not been computed yet. Otherwise the RTT in ms.
     */
    @Override
    public long getRttMs()
    {
        return this.rttMs;
    }

    /**
     * @return The average of the RTT computed from RTCP. Returns -1 if the RTT
     * has not been computed yet.
     */
    @Override
    public double getAverageRttMs()
    {
        return rttMsSummary.getMean();
    }

    /**
     * Returns the number of packets for which FEC data was decoded. Currently
     * this is cumulative over all <tt>ReceiveStream</tt>s.
     *
     * @return the number of packets for which FEC data was decoded. Currently
     * this is cumulative over all <tt>ReceiveStream</tt>s.
     *
     * @see org.jitsi.impl.neomedia.MediaStreamStatsImpl#updateNbFec()
     */
    @Override
    public long getNbFec()
    {
        return nbFec;
    }

    /**
     * Updates the <tt>nbFec</tt> field with the sum of FEC-decoded packets
     * over the different <tt>ReceiveStream</tt>s
     */
    private void updateNbFec()
    {
        MediaDeviceSession devSession = mediaStreamImpl.getDeviceSession();
        int nbFec = 0;

        if(devSession != null)
        {
            for(ReceiveStream receiveStream : devSession.getReceiveStreams())
            {
                for(FECDecoderControl fecDecoderControl
                        : devSession.getDecoderControls(
                                receiveStream,
                                FECDecoderControl.class))
                {
                    nbFec += fecDecoderControl.fecPacketsDecoded();
                }
            }
        }
        this.nbFec = nbFec;
    }

    /**
     * Updates the number of discarded packets.
     *
     * @param newNbDiscarded The last update of the number of lost.
     * @param nbSteps The number of elapsed steps since the last number of loss
     * update.
     */
    private void updateNbDiscarded(
            long newNbDiscarded,
            long nbSteps)
    {
        double newPercentDiscarded = MediaStreamStatsImpl.computePercentLoss(
                nbSteps,
                newNbDiscarded);
        this.percentDiscarded =
                MediaStreamStatsImpl.computeEWMA(
                        nbSteps,
                        this.percentDiscarded,
                        newPercentDiscarded);

        // Saves the last update number download lost value.
        this.nbDiscarded += newNbDiscarded;
    }

    @Override
    public boolean isAdaptiveBufferEnabled()
    {
        for(PacketQueueControl pcq : getPacketQueueControls())
            if(pcq.isAdaptiveBufferEnabled())
                return true;
        return false;
    }

    /**
     * Returns the delay in number of packets introduced by the jitter buffer.
     * Since there might be multiple <tt>ReceiveStreams</tt>, returns the
     * biggest delay found in any of them.
     *
     * @return the delay in number of packets introduced by the jitter buffer
     */
    @Override
    public int getJitterBufferDelayPackets()
    {
        int delay = 0;
        for(PacketQueueControl pqc : getPacketQueueControls())
            if(pqc.getCurrentDelayPackets() > delay)
                delay = pqc.getCurrentDelayPackets();

        return delay;
    }

    /**
     * Returns the delay in milliseconds introduced by the jitter buffer.
     * Since there might be multiple <tt>ReceiveStreams</tt>, returns the
     * biggest delay found in any of them.
     *
     * @return the delay in milliseconds introduces by the jitter buffer
     */
    @Override
    public int getJitterBufferDelayMs()
    {
        int delay = 0;
        for(PacketQueueControl pqc : getPacketQueueControls())
          if(pqc.getCurrentDelayMs() > delay)
              delay = pqc.getCurrentDelayMs();
        return delay;
    }

    /**
     * Returns the size of the first <tt>PacketQueueControl</tt> found via
     * <tt>getPacketQueueControls</tt>.
     *
     * @return the size of the first <tt>PacketQueueControl</tt> found via
     * <tt>getPacketQueueControls</tt>.
     */
    @Override
    public int getPacketQueueSize()
    {
        for(PacketQueueControl pqc : getPacketQueueControls())
            return pqc.getCurrentSizePackets();
        return 0;
    }

    /**
     * Returns the number of packets in the first <tt>PacketQueueControl</tt>
     * found via <tt>getPacketQueueControls</tt>.
     *
     * @return the number of packets in the first <tt>PacketQueueControl</tt>
     * found via <tt>getPacketQueueControls</tt>.
     */
    @Override
    public int getPacketQueueCountPackets()
    {
        for(PacketQueueControl pqc : getPacketQueueControls())
            return pqc.getCurrentPacketCount();
        return 0;
    }

    /**
     * Returns the set of <tt>PacketQueueControls</tt> found for all the
     * <tt>DataSource</tt>s of all the <tt>ReceiveStream</tt>s. The set contains
     * only non-null elements.
     *
     * @return the set of <tt>PacketQueueControls</tt> found for all the
     * <tt>DataSource</tt>s of all the <tt>ReceiveStream</tt>s. The set contains
     * only non-null elements.
     */
    private Set<PacketQueueControl> getPacketQueueControls()
    {
        Set<PacketQueueControl> set = new HashSet<>();
        if (mediaStreamImpl.isStarted())
        {
            MediaDeviceSession devSession = mediaStreamImpl.getDeviceSession();
            if (devSession != null)
            {
                for(ReceiveStream receiveStream
                        : devSession.getReceiveStreams())
                {
                    DataSource ds = receiveStream.getDataSource();
                    if(ds instanceof net.sf.fmj.media.protocol.rtp.DataSource)
                    {
                        for (PushBufferStream pbs :
                                ((net.sf.fmj.media.protocol.rtp.DataSource)ds)
                                        .getStreams())
                        {
                            PacketQueueControl pqc = (PacketQueueControl)
                                    pbs.getControl(
                                            PacketQueueControl.class.getName());
                            if(pqc != null)
                                set.add(pqc);
                        }
                    }
                }
            }
        }
        return set;
    }

    @Override
    public String toString()
    {
      Dimension up = getUploadVideoSize() == null ? new Dimension(0,0) :
        getUploadVideoSize();
      Dimension down = getDownloadVideoSize() == null ? new Dimension(0,0) :
        getDownloadVideoSize();

    return String
        .format("\nMedia Stats %s:%s->%s:%s %s@%sHz (RTT=%sms.)\n"
                    + "Up:    % 4.2f%%, % 4.0fkbps, % 4.2fms.\n"
                    + "Down:  % 4.2f%%, % 4.0fkbps, % 4.2fms.\n"
                    + "Video:  Up=%sx%s Down=%sx%s\n"
                    + "Discarded: Total=%s Current=%4.2f%% FEC=%s\n"
                    + "Jitter Buffer (Adaptive=%b) %s/%s\n"
                    + "  Delay %s packets (%sms.)\n"
                    + "  Discarded Reset=%s Late=%s Shrink=%s Full=%s",
                getLocalIPAddress(),
                getLocalPort(),
                getRemoteIPAddress(),
                getRemotePort(),
                getEncoding(),
                getEncodingClockRate(),
                getRttMs(),
                getUploadPercentLoss(),
                getUploadRateKiloBitPerSec(),
                getUploadJitterMs(),
                getDownloadPercentLoss(),
                getDownloadRateKiloBitPerSec(),
                getDownloadJitterMs(),
                up.width, up.height,
                down.width, down.height,
                getNbDiscarded(),
                getPercentDiscarded(),
                getNbFec(),
                isAdaptiveBufferEnabled(),
                getPacketQueueCountPackets(),
                getPacketQueueSize(),
                getJitterBufferDelayPackets(),
                getJitterBufferDelayMs(),
                getNbDiscardedReset(),
                getNbDiscardedLate(),
                getNbDiscardedShrink(),
                getNbDiscardedFull());
    }

    @Override
    public double getUploadJitterMin()
    {
        return jitterStats[StreamDirection.UPLOAD.ordinal()].getMin();
    }

    @Override
    public double getUploadJitterMax()
    {
        return jitterStats[StreamDirection.UPLOAD.ordinal()].getMax();
    }

    @Override
    public double getUploadJitterMean()
    {
        return jitterStats[StreamDirection.UPLOAD.ordinal()].getMean();
    }

    @Override
    public double getDownloadJitterMin()
    {
        return jitterStats[StreamDirection.DOWNLOAD.ordinal()].getMin();
    }

    @Override
    public double getDownloadJitterMax()
    {
        return jitterStats[StreamDirection.DOWNLOAD.ordinal()].getMax();
    }

    @Override
    public double getDownloadJitterMean()
    {
        return jitterStats[StreamDirection.DOWNLOAD.ordinal()].getMean();
    }
}
