/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
// Portions (c) Microsoft Corporation. All rights reserved.
package org.jitsi.impl.neomedia;

import java.beans.*;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.util.*;

import javax.media.*;
import javax.media.control.*;
import javax.media.format.*;
import javax.media.protocol.*;
import javax.media.rtp.*;
import javax.media.rtp.event.*;
import javax.media.rtp.rtcp.*;

import org.jitsi.impl.neomedia.device.*;
import org.jitsi.impl.neomedia.format.*;
import org.jitsi.impl.neomedia.protocol.*;
import org.jitsi.impl.neomedia.transform.*;
import org.jitsi.impl.neomedia.transform.dtmf.*;
import org.jitsi.impl.neomedia.transform.pt.*;
import org.jitsi.impl.neomedia.transform.rtcp.*;
import org.jitsi.impl.neomedia.transform.srtp.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.control.*;
import org.jitsi.service.neomedia.device.*;
import org.jitsi.service.neomedia.format.*;
import org.jitsi.util.*;

/**
 * Implements <tt>MediaStream</tt> using JMF.
 *
 * @author Lyubomir Marinov
 * @author Emil Ivov
 * @author Sebastien Vincent
 * @author Boris Grozev
 */
public class MediaStreamImpl
    extends AbstractMediaStream
    implements ReceiveStreamListener,
               SendStreamListener,
               SessionListener,
               RemoteListener
{
    /**
     * The <tt>Logger</tt> used by the <tt>MediaStreamImpl</tt> class and its
     * instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(MediaStreamImpl.class);

    /**
     * The name of the property indicating the length of our receive buffer.
     */
    protected static final String PROPERTY_NAME_RECEIVE_BUFFER_LENGTH
        = "net.java.sip.communicator.impl.neomedia.RECEIVE_BUFFER_LENGTH";

    /**
     * Number of empty UDP packets to send for NAT hole punching.
     */
    private static final String HOLE_PUNCH_PKT_COUNT_PROPERTY =
        "net.java.sip.communicator.impl.protocol.HOLE_PUNCH_PKT_COUNT";

    /**
     * Number of empty UDP packets to send for NAT hole punching.
     */
    private static final int DEFAULT_HOLE_PUNCH_PKT_COUNT = 3;

    /**
     * The session with the <tt>MediaDevice</tt> this instance uses for both
     * capture and playback of media.
     */
    private MediaDeviceSession deviceSession;

    /**
     * The <tt>PropertyChangeListener</tt> which listens to
     * {@link #deviceSession} and changes in the values of its
     * {@link MediaDeviceSession#OUTPUT_DATA_SOURCE} property.
     */
    private final PropertyChangeListener deviceSessionPropertyChangeListener
        = new PropertyChangeListener()
        {
            @Override
            public void propertyChange(PropertyChangeEvent event)
            {
                String propertyName = event.getPropertyName();

                if (MediaDeviceSession.OUTPUT_DATA_SOURCE.equals(propertyName))
                    deviceSessionOutputDataSourceChanged();
                else if (MediaDeviceSession.SSRC_LIST.equals(propertyName))
                    deviceSessionSsrcListChanged(event);
            }
        };

    /**
     * The <tt>MediaDirection</tt> in which this <tt>MediaStream</tt> is allowed
     * to stream media.
     */
    private MediaDirection direction;

    /**
     * The <tt>Map</tt> of associations in this <tt>MediaStream</tt> and the
     * <tt>RTPManager</tt> it utilizes of (dynamic) RTP payload types to
     * <tt>MediaFormat</tt>s.
     */
    private final Map<Byte, MediaFormat> dynamicRTPPayloadTypes
        = new HashMap<>();

    /**
     * The <tt>ReceiveStream</tt>s this instance plays back on its associated
     * <tt>MediaDevice</tt>. The (read and write) accesses to the field are to
     * be synchronized using itself as a lock.
     */
    private final List<ReceiveStream> mReceiveStreams
        = new LinkedList<>();

    /**
     * The <tt>RTPConnector</tt> through which this instance sends and receives
     * RTP and RTCP traffic. The instance is a <tt>TransformConnector</tt> in
     * order to also enable packet transformations.
     */
    private AbstractRTPConnector rtpConnector;

    /**
     * The one and only <tt>MediaStreamTarget</tt> this instance has added as a
     * target in {@link #rtpConnector}.
     */
    private MediaStreamTarget rtpConnectorTarget;

    /**
     * The <tt>RTPManager</tt> which utilizes {@link #rtpConnector} and sends
     * and receives RTP and RTCP traffic on behalf of this <tt>MediaStream</tt>.
     */
    private StreamRTPManager rtpManager;

    /**
     * The <tt>RTPTranslator</tt>, if any, which forwards RTP and RTCP traffic
     * between this and other <tt>MediaStream</tt>s.
     */
    private RTPTranslator rtpTranslator;

    /**
     * The indicator which determines whether {@link #createSendStreams()} has
     * been executed for {@link #rtpManager}. If <tt>true</tt>, the
     * <tt>SendStream</tt>s have to be recreated when the <tt>MediaDevice</tt>,
     * respectively the <tt>MediaDeviceSession</tt>, of this instance is
     * changed.
     */
    protected boolean sendStreamsAreCreated = false;

    /**
     * The indicator which determines whether {@link #start()} has been called
     * on this <tt>MediaStream</tt> without {@link #stop()} or {@link #close()}.
     */
    private boolean started = false;

    /**
     * The <tt>MediaDirection</tt> in which this instance is started. For
     * example, {@link MediaDirection#SENDRECV} if this instances is both
     * sending and receiving data (e.g. RTP and RTCP) or
     * {@link MediaDirection#SENDONLY} if this instance is only sending data.
     */
    private MediaDirection startedDirection;

    /**
     * The SSRC identifiers of the party that we are exchanging media with.
     */
    private final Vector<Long> remoteSourceIDs = new Vector<>(1, 1);

    /**
     * Our own SSRC identifier.
     */
    private long localSourceID = -1;

    /**
     * The list of CSRC IDs contributing to the media that this
     * <tt>MediaStream</tt> is sending to its remote party.
     */
    private long[] localContributingSourceIDList = null;

    /**
     * The indicator which determines whether this <tt>MediaStream</tt> is set
     * to transmit "silence" instead of the actual media fed from its
     * <tt>MediaDevice</tt>.
     */
    private boolean mute = false;

    /**
     * The map of currently active <tt>RTPExtension</tt>s and the IDs that they
     * have been assigned for the lifetime of this <tt>MediaStream</tt>.
     */
    private final Map<Byte, RTPExtension> activeRTPExtensions
        = new Hashtable<>();

    /**
     * The <tt>SrtpControl</tt> which controls the SRTP functionality of this
     * <tt>MediaStream</tt>.
     */
    private SrtpControl srtpControl;

    /**
     * The minimum inter arrival jitter value the other party has reported.
     */
    private long maxRemoteInterArrivalJitter = 0;

    /**
     * The maximum inter arrival jitter value the other party has reported.
     */
    private long minRemoteInterArrivalJitter = -1;

    /**
     * Engine chain reading sent RTCP sender reports and stores/prints
     * statistics.
     */
    private StatisticsEngine statisticsEngine = null;

    /**
     * The MediaStreamStatsImpl object used to compute the statistics about
     * this MediaStreamImpl.
     */
    private MediaStreamStatsImpl mediaStreamStatsImpl;

    /**
     * Engine chain overriding payload type if needed.
     */
    private PayloadTypeTransformEngine ptTransformEngine;

    /**
     * Initializes a new <tt>MediaStreamImpl</tt> instance which will use the
     * specified <tt>MediaDevice</tt> for both capture and playback of media.
     * The new instance will not have an associated <tt>StreamConnector</tt> and
     * it must be set later for the new instance to be able to exchange media
     * with a remote peer.
     *
     * @param device the <tt>MediaDevice</tt> the new instance is to use for
     * both capture and playback of media
     * @param srtpControl an existing control instance to control the SRTP
     * operations
     */
    public MediaStreamImpl(MediaDevice device, SrtpControl srtpControl)
    {
        this(null, device, srtpControl);
    }

    /**
     * Initializes a new <tt>MediaStreamImpl</tt> instance which will use the
     * specified <tt>MediaDevice</tt> for both capture and playback of media
     * exchanged via the specified <tt>StreamConnector</tt>.
     *
     * @param connector the <tt>StreamConnector</tt> the new instance is to use
     * for sending and receiving media or <tt>null</tt> if the
     * <tt>StreamConnector</tt> of the new instance is to not be set at
     * initialization time but specified later on
     * @param device the <tt>MediaDevice</tt> the new instance is to use for
     * both capture and playback of media exchanged via the specified
     * <tt>StreamConnector</tt>
     * @param srtpControl an existing control instance to control the ZRTP
     * operations or <tt>null</tt> if a new control instance is to be created by
     * the new <tt>MediaStreamImpl</tt>
     */
    public MediaStreamImpl(
            StreamConnector connector,
            MediaDevice device,
            SrtpControl srtpControl)
    {
        if (device != null)
        {
            /*
             * XXX Set the device early in order to make sure that it is of the
             * right type because we do not support just about any MediaDevice
             * yet.
             */
            setDevice(device);
        }

        // Set up SRTP control.  If none is specified, use the dummy
        // implementation <tt>SrtpControlNullImpl</tt>.
        this.srtpControl
                = (srtpControl == null)
                    ? new SrtpControlNullImpl()
                    : srtpControl;

        if (connector != null)
        {
            setConnector(connector);
        }

        this.mediaStreamStatsImpl = new MediaStreamStatsImpl(this);

        logger.trace(
                "Created "
                    + getClass().getSimpleName()
                    + " with hashCode "
                    + hashCode());
    }

    /**
     * Performs any optional configuration on a specific
     * <tt>RTPConnectorOuputStream</tt> of an <tt>RTPManager</tt> to be used by
     * this <tt>MediaStreamImpl</tt>. Allows extenders to override.
     *
     * @param dataOutputStream the <tt>RTPConnectorOutputStream</tt> to be used
     * by an <tt>RTPManager</tt> of this <tt>MediaStreamImpl</tt> and to be
     * configured
     */
    protected void configureDataOutputStream(
            RTPConnectorOutputStream dataOutputStream)
    {
        dataOutputStream.setPriority(getPriority());
    }

    /**
     * Performs any optional configuration on a specific
     * <tt>RTPConnectorInputStream</tt> of an <tt>RTPManager</tt> to be used by
     * this <tt>MediaStreamImpl</tt>. Allows extenders to override.
     *
     * @param dataInputStream the <tt>RTPConnectorInputStream</tt> to be used
     * by an <tt>RTPManager</tt> of this <tt>MediaStreamImpl</tt> and to be
     * configured
     */
    protected void configureDataInputStream(
            RTPConnectorInputStream dataInputStream)
    {
        dataInputStream.setPriority(getPriority());
    }

    /**
     * Performs any optional configuration on the <tt>BufferControl</tt> of the
     * specified <tt>RTPManager</tt> which is to be used as the
     * <tt>RTPManager</tt> of this <tt>MediaStreamImpl</tt>. Allows extenders to
     * override.
     *
     * @param rtpManager the <tt>RTPManager</tt> which is to be used by this
     * <tt>MediaStreamImpl</tt>
     * @param bufferControl the <tt>BufferControl</tt> of <tt>rtpManager</tt> on
     * which any optional configuration is to be performed
     */
    protected void configureRTPManagerBufferControl(
            StreamRTPManager rtpManager,
            BufferControl bufferControl)
    {
    }

    /**
     * Creates a chain of transform engines for use with this stream. Note
     * that this is the only place where the <tt>TransformEngineChain</tt> is
     * and should be manipulated to avoid problems with the order of the
     * transformers.
     *
     * @return the <tt>TransformEngineChain</tt> that this stream should be
     * using.
     */
    private TransformEngineChain createTransformEngineChain()
    {
        ArrayList<TransformEngine> engineChain
            = new ArrayList<>(3);

        // DTMF
        DtmfTransformEngine dtmfEngine = createDtmfTransformEngine();

        if (dtmfEngine != null)
            engineChain.add(dtmfEngine);

        // RTCP Statistics
        if (statisticsEngine == null)
            statisticsEngine = new StatisticsEngine(this);
        engineChain.add(statisticsEngine);

        // here comes the override payload type transformer
        // as it changes headers of packets, need to go before encryption
        if(ptTransformEngine == null)
            ptTransformEngine = new PayloadTypeTransformEngine();
        engineChain.add(ptTransformEngine);

        // SRTP
        TransformEngine srtpTransformEngine = srtpControl.getTransformEngine();
        if (srtpTransformEngine != null)
            engineChain.add(srtpTransformEngine);

        return
            new TransformEngineChain(
                    engineChain.toArray(
                            new TransformEngine[engineChain.size()]));
    }

    /**
     * A stub that allows audio oriented streams to create and keep a reference
     * to a <tt>DtmfTransformEngine</tt>.
     *
     * @return a <tt>DtmfTransformEngine</tt> if this is an audio oriented
     * stream and <tt>null</tt> otherwise.
     */
    protected DtmfTransformEngine createDtmfTransformEngine()
    {
        return null;
    }

    /**
     * Adds a new association in this <tt>MediaStream</tt> of the specified RTP
     * payload type with the specified <tt>MediaFormat</tt> in order to allow it
     * to report <tt>rtpPayloadType</tt> in RTP flows sending and receiving
     * media in <tt>format</tt>. Usually, <tt>rtpPayloadType</tt> will be in the
     * range of dynamic RTP payload types.
     *
     * @param rtpPayloadType the RTP payload type to be associated in this
     * <tt>MediaStream</tt> with the specified <tt>MediaFormat</tt>
     * @param format the <tt>MediaFormat</tt> to be associated in this
     * <tt>MediaStream</tt> with <tt>rtpPayloadType</tt>
     * @see MediaStream#addDynamicRTPPayloadType(byte, MediaFormat)
     */
    @Override
    public void addDynamicRTPPayloadType(
            byte rtpPayloadType,
            MediaFormat format)
    {
        @SuppressWarnings("unchecked")
        MediaFormatImpl<? extends Format> mediaFormatImpl
            = (MediaFormatImpl<? extends Format>) format;

        synchronized (dynamicRTPPayloadTypes)
        {
            dynamicRTPPayloadTypes.put(Byte.valueOf(rtpPayloadType), format);

            if (rtpManager != null)
                rtpManager.addFormat(
                        mediaFormatImpl.getFormat(),
                        rtpPayloadType);
        }
    }

    /**
     * Maps or updates the mapping between <tt>extensionID</tt> and
     * <tt>rtpExtension</tt>. If <tt>rtpExtension</tt>'s <tt>MediaDirection</tt>
     * attribute is set to <tt>INACTIVE</tt> the mapping is removed from the
     * local extensions table and the extension would not be transmitted or
     * handled by this stream's <tt>RTPConnector</tt>.
     *
     * @param extensionID the ID that is being mapped to <tt>rtpExtension</tt>
     * @param rtpExtension the <tt>RTPExtension</tt> that we are mapping.
     */
    @Override
    public void addRTPExtension(byte extensionID, RTPExtension rtpExtension)
    {
        synchronized (activeRTPExtensions)
        {
            if(rtpExtension.getDirection() == MediaDirection.INACTIVE)
                activeRTPExtensions.remove(extensionID);
            else
                activeRTPExtensions.put(extensionID, rtpExtension);
        }
    }

    /**
     * Asserts that the state of this instance will remain consistent if a
     * specific <tt>MediaDirection</tt> (i.e. <tt>direction</tt>) and a
     * <tt>MediaDevice</tt> with a specific <tt>MediaDirection</tt> (i.e.
     * <tt>deviceDirection</tt>) are both set on this instance.
     *
     * @param direction the <tt>MediaDirection</tt> to validate against the
     * specified <tt>deviceDirection</tt>
     * @param deviceDirection the <tt>MediaDirection</tt> of a
     * <tt>MediaDevice</tt> to validate against the specified <tt>direction</tt>
     * @param illegalArgumentExceptionMessage the message of the
     * <tt>IllegalArgumentException</tt> to be thrown if the state of this
     * instance would've been compromised if <tt>direction</tt> and the
     * <tt>MediaDevice</tt> associated with <tt>deviceDirection</tt> were both
     * set on this instance
     * @throws IllegalArgumentException if the state of this instance would've
     * been compromised were both <tt>direction</tt> and the
     * <tt>MediaDevice</tt> associated with <tt>deviceDirection</tt> set on this
     * instance
     */
    @Override
    public void assertDirection(
            MediaDirection direction,
            MediaDirection deviceDirection,
            String illegalArgumentExceptionMessage)
        throws IllegalArgumentException
    {
        if ((direction != null)
                && !direction.and(deviceDirection).equals(direction))
            throw new IllegalArgumentException(illegalArgumentExceptionMessage);
    }

    /**
     * Releases the resources allocated by this instance in the course of its
     * execution and prepares it to be garbage collected.
     *
     * @see MediaStream#close()
     */
    @Override
    public void close()
    {
        /* Some statistics cannot be taken from the RTP manager and have to
         * be gathered from the ReceiveStream. We need to do this before
         * calling stop(). */
        printReceiveStreamStatistics();

        stop();
        closeSendStreams();

        srtpControl.cleanup();

        if (rtpManager != null)
        {
            printFlowStatistics(rtpManager);

            rtpManager.removeReceiveStreamListener(this);
            rtpManager.removeSendStreamListener(this);
            rtpManager.removeSessionListener(this);
            rtpManager.removeRemoteListener(this);
            try
            {
                logger.debug("Call dispose() on StreamRTPManager: " + rtpManager);
                rtpManager.dispose();
                rtpManager = null;
            }
            catch (Throwable t)
            {
                if (t instanceof ThreadDeath)
                    throw (ThreadDeath) t;

                /*
                 * Analysis of heap dumps and application logs suggests that
                 * RTPManager#dispose() may throw an exception after a
                 * NullPointerException has been thrown by SendStream#close() as
                 * documented in
                 * #stopSendStreams(Iterable<SendStream>, boolean). It is
                 * unknown at the time of this writing whether we can do
                 * anything to prevent the exception here but it is clear that,
                 * if we let it go through, we will not release at least one
                 * capture device (i.e. we will at least skip the
                 * MediaDeviceSession#close() below). For example, if the
                 * exception is thrown for the audio stream in a call, its
                 * capture device will not be released and any video stream will
                 * not get its #close() method called at all.
                 */
                logger.error("Failed to dispose of RTPManager", t);
            }
        }

        /*
         * XXX Call AbstractRTPConnector#removeTargets() after
         * StreamRTPManager#dispose(). Otherwise, the latter will try to send an
         * RTCP BYE and there will be no targets to send it to.
         */
        if (rtpConnector != null)
            rtpConnector.removeTargets();
        rtpConnectorTarget = null;

        if (deviceSession != null)
            deviceSession.close();
    }

    /**
     * Closes the <tt>SendStream</tt>s this instance is sending to its remote
     * peer.
     */
    private void closeSendStreams()
    {
        stopSendStreams(true);
    }

    /**
     * Creates new <tt>SendStream</tt> instances for the streams of
     * {@link #deviceSession} through {@link #rtpManager}.
     */
    private void createSendStreams()
    {
        StreamRTPManager rtpManager = getRTPManager();
        MediaDeviceSession deviceSession = getDeviceSession();
        DataSource dataSource
                = deviceSession == null
                ? null
                : deviceSession.getOutputDataSource();
        int streamCount;

        if (dataSource instanceof PushBufferDataSource)
        {
            PushBufferStream[] streams
                = ((PushBufferDataSource) dataSource).getStreams();

            streamCount = (streams == null) ? 0 : streams.length;
        }
        else if (dataSource instanceof PushDataSource)
        {
            PushSourceStream[] streams
                = ((PushDataSource) dataSource).getStreams();

            streamCount = (streams == null) ? 0 : streams.length;
        }
        else if (dataSource instanceof PullBufferDataSource)
        {
            PullBufferStream[] streams
                = ((PullBufferDataSource) dataSource).getStreams();

            streamCount = (streams == null) ? 0 : streams.length;
        }
        else if (dataSource instanceof PullDataSource)
        {
            PullSourceStream[] streams
                = ((PullDataSource) dataSource).getStreams();

            streamCount = (streams == null) ? 0 : streams.length;
        }
        else
            streamCount = (dataSource == null) ? 0 : 1;

        /*
         * XXX We came up with a scenario in our testing in which G.722 would
         * work fine for the first call since the start of the application and
         * then it would fail for subsequent calls, JMF would complain that the
         * G.722 RTP format is unknown to the RTPManager. Since
         * RTPManager#createSendStream(DataSource, int) is one of the cases in
         * which the formats registered with the RTPManager are necessary,
         * register them (again) just before we use them.
         */
        registerCustomCodecFormats(rtpManager);

        for (int streamIndex = 0; streamIndex < streamCount; streamIndex++)
        {
            try
            {
                SendStream sendStream
                    = rtpManager.createSendStream(dataSource, streamIndex);

                logger.trace(
                            "Created SendStream"
                                + " with hashCode "
                                + sendStream.hashCode()
                                + " for "
                                + toString(dataSource)
                                + " and streamIndex "
                                + streamIndex
                                + " in RTPManager with hashCode "
                                + rtpManager.hashCode());

                long localSSRC = sendStream.getSSRC();

                if (getLocalSourceID() != localSSRC)
                    setLocalSourceID(localSSRC);
            }
            catch (IOException ioe)
            {
                logger
                    .error(
                        "Failed to create send stream for data source "
                            + dataSource
                            + " and stream index "
                            + streamIndex,
                        ioe);
            }
            catch (UnsupportedFormatException ufe)
            {
                logger
                    .error(
                        "Failed to create send stream for data source "
                            + dataSource
                            + " and stream index "
                            + streamIndex
                            + " because of failed format "
                            + ufe.getFailedFormat(),
                        ufe);
            }
            catch (IllegalStateException ise)
            {
                // This error is probably due to SFR 542726
                // Log additional diags and rethrow
                logger.error("Failed to create send stream for data source "
                             + dataSource
                             + " and stream index "
                             + streamIndex
                             + " due to illegal state in RTPSessionMgr (see SFR542726)",
                             ise);
                throw ise;
            }
        }
        sendStreamsAreCreated = true;

        @SuppressWarnings("unchecked")
        Vector<SendStream> sendStreams = rtpManager.getSendStreams();
        int sendStreamCount
            = (sendStreams == null) ? 0 : sendStreams.size();

        logger
            .trace(
                "Total number of SendStreams in RTPManager with hashCode "
                    + rtpManager.hashCode()
                    + " is "
                    + sendStreamCount);
    }

    /**
     * Notifies this <tt>MediaStream</tt> that the <tt>MediaDevice</tt> (and
     * respectively the <tt>MediaDeviceSession</tt> with it) which this instance
     * uses for capture and playback of media has been changed. Allows extenders
     * to override and provide additional processing of <tt>oldValue</tt> and
     * <tt>newValue</tt>.
     *
     * @param oldValue the <tt>MediaDeviceSession</tt> with the
     * <tt>MediaDevice</tt> this instance used work with
     * @param newValue the <tt>MediaDeviceSession</tt> with the
     * <tt>MediaDevice</tt> this instance is to work with
     */
    protected void deviceSessionChanged(
            MediaDeviceSession oldValue,
            MediaDeviceSession newValue)
    {
        recreateSendStreams();
    }

    /**
     * Notifies this instance that the output <tt>DataSource</tt> of its
     * <tt>MediaDeviceSession</tt> has changed. Recreates the
     * <tt>SendStream</tt>s of this instance as necessary so that it, for
     * example, continues streaming after the change if it was streaming before
     * the change.
     */
    private void deviceSessionOutputDataSourceChanged()
    {
        recreateSendStreams();
    }

    /**
     * Recalculates the list of CSRC identifiers that this <tt>MediaStream</tt>
     * needs to include in RTP packets bound to its interlocutor. The method
     * uses the list of SSRC identifiers currently handled by our device
     * (possibly a mixer), then removes the SSRC ID of this stream's
     * interlocutor. If this turns out to be the only SSRC currently in the list
     * we set the list of local CSRC identifiers to null since this is obviously
     * a non-conf call and we don't need to be advertising CSRC lists. If that's
     * not the case, we also add our own SSRC to the list of IDs and cache the
     * entire list.
     *
     * @param evt the <tt>PropetyChangeEvent</tt> containing the list of SSRC
     * identifiers handled by our device session before and after it changed.
     */
    private void deviceSessionSsrcListChanged(PropertyChangeEvent evt)
    {
        long[] ssrcArray = (long[])evt.getNewValue();

        // the list is empty
        if(ssrcArray == null)
        {
            this.localContributingSourceIDList = null;
            return;
        }

        int elementsToRemove = 0;
        Vector<Long> remoteSourceIDs = this.remoteSourceIDs;

        //in case of a conf call the mixer would return all SSRC IDs that are
        //currently contributing including this stream's counterpart. We need
        //to remove that last one since that's where we will be sending our
        //csrc list
        for(long csrc : ssrcArray)
            if (remoteSourceIDs.contains(csrc))
                elementsToRemove ++;

        //we don't seem to be in a conf call since the list only contains the
        //SSRC id of the party that we are directly interacting with.
        if (elementsToRemove >= ssrcArray.length)
        {
            this.localContributingSourceIDList = null;
            return;
        }

        //prepare the new array. make it big enough to also add the local
        //SSRC id but do not make it bigger than 15 since that's the maximum
        //for RTP.
        int cc = Math.min(ssrcArray.length - elementsToRemove + 1, 15);

        long[] csrcArray = new long[cc];

        for (int i = 0,j = 0;
                (i < ssrcArray.length) && (j < csrcArray.length - 1);
                i++)
        {
            long ssrc = ssrcArray[i];

            if (!remoteSourceIDs.contains(ssrc))
            {
                csrcArray[j] = ssrc;
                j++;
            }
        }

        csrcArray[csrcArray.length - 1] = getLocalSourceID();
        this.localContributingSourceIDList = csrcArray;
    }

    /**
     * Sets the target of this <tt>MediaStream</tt> to which it is to send and
     * from which it is to receive data (e.g. RTP) and control data (e.g. RTCP).
     * In contrast to {@link #setTarget(MediaStreamTarget)}, sets the specified
     * <tt>target</tt> on this <tt>MediaStreamImpl</tt> even if its current
     * <tt>target</tt> is equal to the specified one.
     *
     * @param target the <tt>MediaStreamTarget</tt> describing the data
     * (e.g. RTP) and the control data (e.g. RTCP) locations to which this
     * <tt>MediaStream</tt> is to send and from which it is to receive
     * @see MediaStreamImpl#setTarget(MediaStreamTarget)
     */
    private void doSetTarget(MediaStreamTarget target)
    {
        InetSocketAddress newDataAddr;
        InetSocketAddress newControlAddr;

        if (target == null)
        {
            newDataAddr = null;
            newControlAddr = null;
        }
        else
        {
            newDataAddr = target.getDataAddress();
            newControlAddr = target.getControlAddress();
        }

        /*
         * Invoke AbstractRTPConnector#removeTargets() if the new value does
         * actually remove an RTP or RTCP target in comparison to the old value.
         * If the new value is equal to the oldValue or adds an RTP or RTCP
         * target (i.e. the old value does not specify the respective RTP or
         * RTCP target and the new value does), then removeTargets is
         * unnecessary and would've needlessly allowed a (tiny) interval of
         * (execution) time (between removeTargets and addTarget) without a
         * target.
         */
        if (rtpConnectorTarget != null)
        {
            InetSocketAddress oldDataAddr = rtpConnectorTarget.getDataAddress();
            boolean removeTargets
                = !Objects.equals(oldDataAddr, newDataAddr);

            if (!removeTargets)
            {
                InetSocketAddress oldControlAddr
                    = rtpConnectorTarget.getControlAddress();

                removeTargets
                    = !Objects.equals(oldControlAddr, newControlAddr);
            }

            if (removeTargets)
            {
                rtpConnector.removeTargets();
                rtpConnectorTarget = null;
            }
        }

        boolean targetIsSet;

        if (target == null)
            targetIsSet = true;
        else
        {
            try
            {
                InetAddress controlInetAddr;
                int controlPort;

                if (newControlAddr == null)
                {
                    controlInetAddr = null;
                    controlPort = 0;
                }
                else
                {
                    controlInetAddr = newControlAddr.getAddress();
                    controlPort = newControlAddr.getPort();
                }

                rtpConnector.addTarget(
                        new SessionAddress(
                                newDataAddr.getAddress(), newDataAddr.getPort(),
                                controlInetAddr, controlPort));
                targetIsSet = true;
            }
            catch (IOException ioe)
            {
                // TODO
                targetIsSet = false;
                logger.error("Failed to set target " + target, ioe);
            }
        }
        if (targetIsSet)
        {
            rtpConnectorTarget = target;

            logger.trace(
                    "Set target of " + getClass().getSimpleName()
                        + " with hashCode " + hashCode()
                        + " to " + target);
        }
    }

    /**
     * Gets the <tt>MediaDevice</tt> that this stream uses to play back and
     * capture media.
     *
     * @return the <tt>MediaDevice</tt> that this stream uses to play back and
     * capture media
     * @see MediaStream#getDevice()
     */
    @Override
    public AbstractMediaDevice getDevice()
    {
        MediaDeviceSession deviceSession = getDeviceSession();

        return (deviceSession == null) ? null : deviceSession.getDevice();
    }

    /**
     * Gets the <tt>MediaDirection</tt> of the <tt>device</tt> of this instance.
     * In case there is no device, {@link MediaDirection#SENDRECV} is assumed.
     *
     * @return the <tt>MediaDirection</tt> of the <tt>device</tt> of this
     * instance if any or <tt>MediaDirection.SENDRECV</tt>
     */
    private MediaDirection getDeviceDirection()
    {
        MediaDeviceSession deviceSession = getDeviceSession();

        return
            (deviceSession == null)
                ? MediaDirection.SENDRECV
                : deviceSession.getDevice().getDirection();
    }

    /**
     * Gets the <tt>MediaDeviceSession</tt> which represents the work of this
     * <tt>MediaStream</tt> with its associated <tt>MediaDevice</tt>.
     *
     * @return the <tt>MediaDeviceSession</tt> which represents the work of this
     * <tt>MediaStream</tt> with its associated <tt>MediaDevice</tt>
     */
    public MediaDeviceSession getDeviceSession()
    {
        return deviceSession;
    }

    /**
     * Gets the direction in which this <tt>MediaStream</tt> is allowed to
     * stream media.
     *
     * @return the <tt>MediaDirection</tt> in which this <tt>MediaStream</tt> is
     * allowed to stream media
     * @see MediaStream#getDirection()
     */
    @Override
    public MediaDirection getDirection()
    {
        return (direction == null) ? getDeviceDirection() : direction;
    }

    /**
     * Gets the existing associations in this <tt>MediaStream</tt> of RTP
     * payload types to <tt>MediaFormat</tt>s. The returned <tt>Map</tt>
     * only contains associations previously added in this instance with
     * {@link #addDynamicRTPPayloadType(byte, MediaFormat)} and not globally or
     * well-known associations reported by
     * {@link MediaFormat#getRTPPayloadType()}.
     *
     * @return a <tt>Map</tt> of RTP payload type expressed as <tt>Byte</tt> to
     * <tt>MediaFormat</tt> describing the existing (dynamic) associations in
     * this instance of RTP payload types to <tt>MediaFormat</tt>s. The
     * <tt>Map</tt> represents a snapshot of the existing associations at the
     * time of the <tt>getDynamicRTPPayloadTypes()</tt> method call and
     * modifications to it are not reflected on the internal storage
     * @see MediaStream#getDynamicRTPPayloadTypes()
     */
    @Override
    public Map<Byte, MediaFormat> getDynamicRTPPayloadTypes()
    {
        synchronized (dynamicRTPPayloadTypes)
        {
            return new HashMap<>(dynamicRTPPayloadTypes);
        }
    }

    /**
     * Returns the payload type number that has been negotiated for the
     * specified <tt>encoding</tt> or <tt>-1</tt> if no payload type has been
     * negotiated for it. If multiple formats match the specified
     * <tt>encoding</tt>, then this method would return the first one it
     * encounters while iterating through the map.
     *
     * @param encoding the encoding whose payload type we are trying to obtain.
     *
     * @return the payload type number that has been negotiated for the
     * specified <tt>encoding</tt> or <tt>-1</tt> if no payload type has been
     * negotiated for it.
     */
    public byte getDynamicRTPPayloadType(String encoding)
    {
        synchronized (dynamicRTPPayloadTypes)
        {
            for (Map.Entry<Byte, MediaFormat> entry
                                        : dynamicRTPPayloadTypes.entrySet())
            {
                if (entry.getValue().getEncoding().equals(encoding))
                    return entry.getKey();
            }
            return -1;
        }
    }

    /**
     * Gets the <tt>MediaFormat</tt> that this stream is currently transmitting
     * in.
     *
     * @return the <tt>MediaFormat</tt> that this stream is currently
     * transmitting in
     * @see MediaStream#getFormat()
     */
    @Override
    public MediaFormat getFormat()
    {
        MediaDeviceSession deviceSession = getDeviceSession();

        return (deviceSession == null) ? null : deviceSession.getFormat();
    }

    /**
     * Gets the synchronization source (SSRC) identifier of the local peer or
     * <tt>-1</tt> if it is not yet known.
     *
     * @return  the synchronization source (SSRC) identifier of the local peer
     * or <tt>-1</tt> if it is not yet known
     * @see MediaStream#getLocalSourceID()
     */
    @Override
    public long getLocalSourceID()
    {
        return this.localSourceID;
    }

    /**
     * Gets the address that this stream is sending RTCP traffic to.
     *
     * @return an <tt>InetSocketAddress</tt> instance indicating the address
     * that this stream is sending RTCP traffic to
     * @see MediaStream#getRemoteControlAddress()
     */
    @Override
    public InetSocketAddress getRemoteControlAddress()
    {
        StreamConnector connector =
            (rtpConnector != null) ? rtpConnector.getConnector() : null;

        if(connector != null)
        {
            if(connector.getDataSocket() != null)
            {
                return (InetSocketAddress)connector.getControlSocket().
                    getRemoteSocketAddress();
            }
            else if(connector.getDataTCPSocket() != null)
            {
                return (InetSocketAddress)connector.getControlTCPSocket().
                    getRemoteSocketAddress();
            }
        }

        return null;
    }

    /**
     * Gets the address that this stream is sending RTP traffic to.
     *
     * @return an <tt>InetSocketAddress</tt> instance indicating the address
     * that this stream is sending RTP traffic to
     * @see MediaStream#getRemoteDataAddress()
     */
    @Override
    public InetSocketAddress getRemoteDataAddress()
    {
        StreamConnector connector =
            (rtpConnector != null) ? rtpConnector.getConnector() : null;

        if(connector != null)
        {
            if(connector.getDataSocket() != null)
            {
                return (InetSocketAddress)connector.getDataSocket().
                    getRemoteSocketAddress();
            }
            else if(connector.getDataTCPSocket() != null)
            {
                return (InetSocketAddress)connector.getDataTCPSocket().
                    getRemoteSocketAddress();
            }
        }

        return null;
    }

    /**
     * Gets the local address that this stream is sending RTCP traffic from.
     *
     * @return an <tt>InetSocketAddress</tt> instance indicating the local
     * address that this stream is sending RTCP traffic from.
     */
    public InetSocketAddress getLocalControlAddress()
    {
        StreamConnector connector =
            (rtpConnector != null) ? rtpConnector.getConnector() : null;

        if(connector != null)
        {
            if(connector.getDataSocket() != null)
            {
                return (InetSocketAddress)connector.getControlSocket().
                    getLocalSocketAddress();
            }
            else if(connector.getDataTCPSocket() != null)
            {
                return (InetSocketAddress)connector.getControlTCPSocket().
                    getLocalSocketAddress();
            }
        }

        return null;
    }

    /**
     * Gets the local address that this stream is sending RTP traffic from.
     *
     * @return an <tt>InetSocketAddress</tt> instance indicating the local
     * address that this stream is sending RTP traffic from.
     */
    public InetSocketAddress getLocalDataAddress()
    {
        StreamConnector connector =
            (rtpConnector != null) ? rtpConnector.getConnector() : null;

        if(connector != null)
        {
            if(connector.getDataSocket() != null)
            {
                return (InetSocketAddress)connector.getDataSocket().
                    getLocalSocketAddress();
            }
            else if(connector.getDataTCPSocket() != null)
            {
                return (InetSocketAddress)connector.getDataTCPSocket().
                    getLocalSocketAddress();
            }
        }

        return null;
    }

    /**
     * {@inheritDoc}
     *
     * Returns the last element of {@link #getRemoteSourceIDs()} which may or
     * may not always be appropriate.
     *
     * @see MediaStream#getRemoteSourceID()
     */
    @Override
    public long getRemoteSourceID()
    {
        return remoteSourceIDs.isEmpty() ? -1 : remoteSourceIDs.lastElement();
    }

    /**
     * Gets the synchronization source (SSRC) identifiers of the remote peer.
     *
     * @return the synchronization source (SSRC) identifiers of the remote peer
     */
    @Override
    public List<Long> getRemoteSourceIDs()
    {
        /*
         * TODO Returning an unmodifiable view of remoteSourceIDs prevents
         * modifications of private state from the outside but it does not
         * prevent ConcurrentModificationException.
         */
        return Collections.unmodifiableList(remoteSourceIDs);
    }

    /**
     * Gets the <tt>RTPConnector</tt> through which this instance sends and
     * receives RTP and RTCP traffic.
     *
     * @return the <tt>RTPConnector</tt> through which this instance sends and
     * receives RTP and RTCP traffic
     */
    protected AbstractRTPConnector getRTPConnector()
    {
        return rtpConnector;
    }

    /**
     * Gets the <tt>RTPManager</tt> instance which sends and receives RTP and
     * RTCP traffic on behalf of this <tt>MediaStream</tt>.
     *
     * @return the <tt>RTPManager</tt> instance which sends and receives RTP and
     * RTCP traffic on behalf of this <tt>MediaStream</tt>
     */
    public StreamRTPManager getRTPManager()
    {
        if (rtpManager == null)
        {
            RTPConnector rtpConnector = getRTPConnector();

            if (rtpConnector == null)
                throw new IllegalStateException("rtpConnector");

            rtpManager = new StreamRTPManager(this, rtpTranslator);

            registerCustomCodecFormats(rtpManager);

            rtpManager.addReceiveStreamListener(this);
            rtpManager.addSendStreamListener(this);
            rtpManager.addSessionListener(this);
            rtpManager.addRemoteListener(this);

            BufferControl bc = rtpManager.getControl(BufferControl.class);
            if (bc != null)
                configureRTPManagerBufferControl(rtpManager, bc);

            rtpManager.initialize(rtpConnector);

            /*
             * JMF initializes the local SSRC upon #initialize(RTPConnector) so
             * now's the time to ask.
             */
            /*
             * As JMF keeps the SSRC as a signed int value, convert it to
             * unsigned.
             */
            setLocalSourceID(rtpManager.getLocalSSRC() & 0xFFFFFFFFL);
        }
        return rtpManager;
    }

    /**
     * Gets the <tt>SrtpControl</tt> which controls the SRTP of this stream.
     *
     * @return the <tt>SrtpControl</tt> which controls the SRTP of this stream
     */
    @Override
    public SrtpControl getSrtpControl()
    {
        return srtpControl;
    }

    /**
     * Determines whether this <tt>MediaStream</tt> is set to transmit "silence"
     * instead of the media being fed from its <tt>MediaDevice</tt>. "Silence"
     * for video is understood as video data which is not the captured video
     * data and may represent, for example, a black image.
     *
     * @return <tt>true</tt> if this <tt>MediaStream</tt> is set to transmit
     * "silence" instead of the media fed from its <tt>MediaDevice</tt>;
     * <tt>false</tt>, otherwise
     * @see MediaStream#isMute()
     */
    @Override
    public boolean isMute()
    {
        MediaDeviceSession deviceSession = getDeviceSession();

        return (deviceSession == null) ? mute : deviceSession.isMute();
    }

    /**
     * Determines whether {@link #start()} has been called on this
     * <tt>MediaStream</tt> without {@link #stop()} or {@link #close()}
     * afterwards.
     *
     * @return <tt>true</tt> if {@link #start()} has been called on this
     * <tt>MediaStream</tt> without {@link #stop()} or {@link #close()}
     * afterwards
     * @see MediaStream#isStarted()
     */
    @Override
    public boolean isStarted()
    {
        return started;
    }

    /**
     * Gets the <tt>RTPManager</tt> instance which sends and receives RTP and
     * RTCP traffic on behalf of this <tt>MediaStream</tt>. If the
     * <tt>RTPManager</tt> does not exist yet, it is not created.
     *
     * @return the <tt>RTPManager</tt> instance which sends and receives RTP and
     * RTCP traffic on behalf of this <tt>MediaStream</tt>
     */
    public StreamRTPManager queryRTPManager()
    {
        return rtpManager;
    }

    /**
     * Recreates the <tt>SendStream</tt>s of this instance (i.e. of its
     * <tt>RTPManager</tt>) as necessary. For example, if there was no attempt
     * to create the <tt>SendStream</tt>s prior to the call, does nothing. If
     * they were created prior to the call, closes them and creates them again.
     * If they were not started prior to the call, does not start them after
     * recreating them.
     */
    protected void recreateSendStreams()
    {
        if (sendStreamsAreCreated)
        {
            closeSendStreams();

            if ((getDeviceSession() != null) && (rtpManager != null))
            {
                if (MediaDirection.SENDONLY.equals(startedDirection)
                        || MediaDirection.SENDRECV.equals(startedDirection))
                    startSendStreams();
            }
        }
    }

    /**
     * Registers any custom JMF <tt>Format</tt>s with a specific
     * <tt>RTPManager</tt>. Extenders should override in order to register their
     * own customizations and should call back to this super implementation
     * during the execution of their override in order to register the
     * associations defined in this instance of (dynamic) RTP payload types to
     * <tt>MediaFormat</tt>s.
     *
     * @param rtpManager the <tt>RTPManager</tt> to register any custom JMF
     * <tt>Format</tt>s with
     */
    protected void registerCustomCodecFormats(StreamRTPManager rtpManager)
    {
        synchronized (dynamicRTPPayloadTypes)
        {
            for (Map.Entry<Byte, MediaFormat> dynamicRTPPayloadType
                    : dynamicRTPPayloadTypes.entrySet())
            {
                @SuppressWarnings("unchecked")
                MediaFormatImpl<? extends Format> mediaFormatImpl
                    = (MediaFormatImpl<? extends Format>)
                        dynamicRTPPayloadType.getValue();

                rtpManager.addFormat(
                        mediaFormatImpl.getFormat(),
                        dynamicRTPPayloadType.getKey());
            }
        }
    }

    /**
     * Notifies this <tt>MediaStream</tt> implementation that its
     * <tt>RTPConnector</tt> instance has changed from a specific old value to a
     * specific new value. Allows extenders to override and perform additional
     * processing after this <tt>MediaStream</tt> has changed its
     * <tt>RTPConnector</tt> instance.
     *
     * @param oldValue the <tt>RTPConnector</tt> of this <tt>MediaStream</tt>
     * implementation before it got changed to <tt>newValue</tt>
     * @param newValue the current <tt>RTPConnector</tt> of this
     * <tt>MediaStream</tt> which replaced <tt>oldValue</tt>
     */
    protected void rtpConnectorChanged(
            AbstractRTPConnector oldValue,
            AbstractRTPConnector newValue)
    {
        srtpControl.setConnector(newValue);

        if (newValue != null)
        {
            /*
             * Register the transform engines that we will be using in this
             * stream.
             */
            if(newValue instanceof RTPTransformUDPConnector)
            {
                ((RTPTransformUDPConnector)newValue)
                    .setEngine(createTransformEngineChain());
            }
            else if(newValue instanceof RTPTransformTCPConnector)
                ((RTPTransformTCPConnector)newValue)
                    .setEngine(createTransformEngineChain());

            if (rtpConnectorTarget != null)
            {
                doSetTarget(rtpConnectorTarget);
            }
        }
    }

    /**
     * Notifies this instance that its {@link #rtpConnector} has created a new
     * <tt>RTPConnectorInputStream</tt> either RTP or RTCP.
     *
     * @param inputStream the new <tt>RTPConnectorInputStream</tt> instance
     * created by the <tt>rtpConnector</tt> of this instance
     * @param data <tt>true</tt> if <tt>inputStream</tt> will be used for RTP
     * or <tt>false</tt> for RTCP
     */
    private void rtpConnectorInputStreamCreated(
            RTPConnectorInputStream inputStream,
            boolean data)
    {
        /*
         * TODO The following is a very ugly way to expose the
         * RTPConnectorInputStreams created by the rtpConnector of this
         * instance so they may be configured from outside the class hierarchy
         * (e.g. to invoke addDatagramPacketFilter). That's why the property in
         * use bellow is not defined as a well-known constant and is to be
         * considered internal and likely to be removed in a future revision.
         */
        try
        {
            firePropertyChange(
                    MediaStreamImpl.class.getName()
                        + ".rtpConnector."
                        + (data ? "data" : "control")
                        + "InputStream",
                    null,
                    inputStream);
        }
        catch (Throwable t)
        {
            if (t instanceof ThreadDeath)
                throw (ThreadDeath) t;
            else
                logger.error(t);
        }
    }

    /**
     * Sets the <tt>StreamConnector</tt> to be used by this instance for sending
     * and receiving media.
     *
     * @param connector the <tt>StreamConnector</tt> to be used by this instance
     * for sending and receiving media
     */
    @Override
    public void setConnector(StreamConnector connector)
    {
        if (connector == null)
            throw new NullPointerException("connector");

        AbstractRTPConnector oldValue = rtpConnector;

        // Is the StreamConnector really changing?
        if ((oldValue != null) && (oldValue.getConnector() == connector))
            return;

        switch (connector.getProtocol())
        {
        case UDP:
            rtpConnector
                = new RTPTransformUDPConnector(connector)
                {
                    @Override
                    protected TransformUDPInputStream createControlInputStream()
                        throws IOException
                    {
                        TransformUDPInputStream s
                            = super.createControlInputStream();

                        rtpConnectorInputStreamCreated(s, false);
                        return s;
                    }

                    @Override
                    protected TransformUDPInputStream createDataInputStream()
                        throws IOException
                    {
                        TransformUDPInputStream s
                            = super.createDataInputStream();

                        rtpConnectorInputStreamCreated(s, true);
                        if (s != null)
                            configureDataInputStream(s);
                        return s;
                    }

                    @Override
                    protected TransformUDPOutputStream createDataOutputStream()
                        throws IOException
                    {
                        TransformUDPOutputStream s
                            = super.createDataOutputStream();

                        if (s != null)
                            configureDataOutputStream(s);
                        return s;
                    }
                };
            break;
        case TCP:
            rtpConnector
                = new RTPTransformTCPConnector(connector)
                {
                    @Override
                    protected TransformTCPInputStream createControlInputStream()
                        throws IOException
                    {
                        TransformTCPInputStream s
                            = super.createControlInputStream();

                        rtpConnectorInputStreamCreated(s, false);
                        return s;
                    }

                    @Override
                    protected TransformTCPInputStream createDataInputStream()
                        throws IOException
                    {
                        TransformTCPInputStream s
                            = super.createDataInputStream();

                        rtpConnectorInputStreamCreated(s, true);
                        if (s != null)
                            configureDataInputStream(s);
                        return s;
                    }

                    @Override
                    protected TransformTCPOutputStream createDataOutputStream()
                        throws IOException
                    {
                        TransformTCPOutputStream s
                            = super.createDataOutputStream();

                        if (s != null)
                            configureDataOutputStream(s);
                        return s;
                    }
                };
            break;
        default:
            throw new IllegalArgumentException("connector");
        }

        rtpConnectorChanged(oldValue, rtpConnector);
    }

    /**
     * Sets the <tt>MediaDevice</tt> that this stream should use to play back
     * and capture media.
     * <p>
     * <b>Note</b>: Also resets any previous direction set with
     * {@link #setDirection(MediaDirection)} to the direction of the specified
     * <tt>MediaDevice</tt>.
     * </p>
     *
     * @param device the <tt>MediaDevice</tt> that this stream should use to
     * play back and capture media
     * @see MediaStream#setDevice(MediaDevice)
     */
    @Override
    public void setDevice(MediaDevice device)
    {
        if (device == null)
            throw new NullPointerException("device");

        // Require AbstractMediaDevice for MediaDeviceSession support.
        AbstractMediaDevice abstractMediaDevice = (AbstractMediaDevice) device;

        if ((deviceSession == null) || (deviceSession.getDevice() != device))
        {
            assertDirection(direction, device.getDirection(), "device");

            MediaDeviceSession oldValue = deviceSession;
            MediaFormat format;
            MediaDirection startedDirection;

            if (deviceSession != null)
            {
                format = getFormat();
                startedDirection = deviceSession.getStartedDirection();

                deviceSession.removePropertyChangeListener(
                        deviceSessionPropertyChangeListener);

                // keep player active
                deviceSession.setDisposePlayerOnClose(
                        !(deviceSession instanceof VideoMediaDeviceSession));
                deviceSession.close();
                deviceSession = null;
            }
            else
            {
                format = null;
                startedDirection = MediaDirection.INACTIVE;
            }

            deviceSession = abstractMediaDevice.createSession();

            /*
             * Copy the playback from the old MediaDeviceSession into the new
             * MediaDeviceSession in order to prevent the recreation of the
             * playback of the ReceiveStream(s) when just changing the
             * MediaDevice of this MediaSteam.
             */
            if (oldValue != null)
                deviceSession.copyPlayback(oldValue);

            deviceSession.addPropertyChangeListener(
                    deviceSessionPropertyChangeListener);

            /*
             * Setting a new device resets any previously-set direction.
             * Otherwise, we risk not being able to set a new device if it is
             * mandatory for the new device to fully cover any previously-set
             * direction.
             */
            direction = null;

            if (deviceSession != null)
            {
                if (format != null)
                    deviceSession.setFormat(format);
                deviceSession.setMute(mute);
            }
            deviceSessionChanged(oldValue, deviceSession);
            if (deviceSession != null)
            {
                deviceSession.start(startedDirection);
                synchronized (mReceiveStreams)
                {
                    for (ReceiveStream receiveStream : mReceiveStreams)
                        deviceSession.addReceiveStream(receiveStream);
                }
            }
        }
    }

    /**
     * Sets the direction in which media in this <tt>MediaStream</tt> is to be
     * streamed. If this <tt>MediaStream</tt> is not currently started, calls to
     * {@link #start()} later on will start it only in the specified
     * <tt>direction</tt>. If it is currently started in a direction different
     * than the specified, directions other than the specified will be stopped.
     *
     * @param direction the <tt>MediaDirection</tt> in which this
     * <tt>MediaStream</tt> is to stream media when it is started
     * @see MediaStream#setDirection(MediaDirection)
     */
    @Override
    public void setDirection(MediaDirection direction)
    {
        if (direction == null)
            throw new NullPointerException("direction");
        if(this.direction == direction)
            return;

        logger.trace(
                "Changing direction of stream " + hashCode()
                    + " from:" + this.direction
                    + " to:" + direction);

        /*
         * Make sure that the specified direction is in accord with the
         * direction of the MediaDevice of this instance.
         */
        assertDirection(direction, getDeviceDirection(), "direction");

        this.direction = direction;

        switch (this.direction)
        {
        case INACTIVE:
            stop(MediaDirection.SENDRECV);
            return;
        case RECVONLY:
            stop(MediaDirection.SENDONLY);
            break;
        case SENDONLY:
            stop(MediaDirection.RECVONLY);
            break;
        case SENDRECV:
            break;
        default:
            // Don't know what it may be (in the future) so ignore it.
            return;
        }
        if (started)
            start(this.direction);
    }

    /**
     * Sets the <tt>MediaFormat</tt> that this <tt>MediaStream</tt> should
     * transmit in.
     *
     * @param format the <tt>MediaFormat</tt> that this <tt>MediaStream</tt>
     * should transmit in
     * @see MediaStream#setFormat(MediaFormat)
     */
    @Override
    public void setFormat(MediaFormat format)
    {
        MediaDeviceSession deviceSession = getDeviceSession();
        MediaFormatImpl<? extends Format> deviceSessionFormat = null;

        if (deviceSession != null)
        {
            deviceSessionFormat = deviceSession.getFormat();
            if ((deviceSessionFormat != null)
                    && deviceSessionFormat.equals(format)
                    && deviceSessionFormat.advancedAttributesAreEqual(
                            deviceSessionFormat.getAdvancedAttributes(),
                            format.getAdvancedAttributes()))
            {
                return;
            }
        }

        logger.trace(
                "Changing format of stream " + hashCode()
                    + " from: " + deviceSessionFormat
                    + " to: " + format);

        handleAttributes(format, format.getAdvancedAttributes());
        handleAttributes(format, format.getFormatParameters());

        if (deviceSession != null)
            deviceSession.setFormat(format);
    }

    /**
     * Handles attributes contained in <tt>MediaFormat</tt>.
     *
     * @param format the <tt>MediaFormat</tt> to handle the attributes of
     * @param attrs the attributes <tt>Map</tt> to handle
     */
    @Override
    protected void handleAttributes(
            MediaFormat format,
            Map<String, String> attrs)
    {
    }

    /**
     * Causes this <tt>MediaStream</tt> to stop transmitting the media being fed
     * from this stream's <tt>MediaDevice</tt> and transmit "silence" instead.
     * "Silence" for video is understood as video data which is not the captured
     * video data and may represent, for example, a black image.
     *
     * @param mute <tt>true</tt> to have this <tt>MediaStream</tt> transmit
     * "silence" instead of the actual media data that it captures from its
     * <tt>MediaDevice</tt>; <tt>false</tt> to transmit actual media data
     * captured from the <tt>MediaDevice</tt> of this <tt>MediaStream</tt>
     * @see MediaStream#setMute(boolean)
     */
    @Override
    public void setMute(boolean mute)
    {
        if (this.mute != mute)
        {
            logger.trace((mute? "Muting" : "Unmuting")
                        + " stream with hashcode " + hashCode());

            this.mute = mute;

            MediaDeviceSession deviceSession = getDeviceSession();

            if (deviceSession != null)
                deviceSession.setMute(this.mute);
        }
    }

    /**
     * Returns the target of this <tt>MediaStream</tt> to which it is to send
     * and from which it is to receive data (e.g. RTP) and control data (e.g.
     * RTCP).
     *
     * @return the <tt>MediaStreamTarget</tt> describing the data
     * (e.g. RTP) and the control data (e.g. RTCP) locations to which this
     * <tt>MediaStream</tt> is to send and from which it is to receive
     * @see MediaStream#setTarget(MediaStreamTarget)
     */
    @Override
    public MediaStreamTarget getTarget()
    {
        return rtpConnectorTarget;
    }

    /**
     * Sets the target of this <tt>MediaStream</tt> to which it is to send and
     * from which it is to receive data (e.g. RTP) and control data (e.g. RTCP).
     *
     * @param target the <tt>MediaStreamTarget</tt> describing the data
     * (e.g. RTP) and the control data (e.g. RTCP) locations to which this
     * <tt>MediaStream</tt> is to send and from which it is to receive
     * @see MediaStream#setTarget(MediaStreamTarget)
     */
    @Override
    public void setTarget(MediaStreamTarget target)
    {
        // Short-circuit if setting the same target.
        if (target == null)
        {
            if (rtpConnectorTarget == null)
                return;
        }
        else if (target.equals(rtpConnectorTarget))
            return;

        doSetTarget(target);
    }

    /**
     * Starts capturing media from this stream's <tt>MediaDevice</tt> and then
     * streaming it through the local <tt>StreamConnector</tt> toward the
     * stream's target address and port. Also puts the <tt>MediaStream</tt> in a
     * listening state which make it play all media received from the
     * <tt>StreamConnector</tt> on the stream's <tt>MediaDevice</tt>.
     *
     * @see MediaStream#start()
     */
    @Override
    public void start()
    {
        start(getDirection());
        started = true;
    }

    /**
     * Starts the processing of media in this instance in a specific direction.
     *
     * @param direction a <tt>MediaDirection</tt> value which represents the
     * direction of the processing of media to be started. For example,
     * {@link MediaDirection#SENDRECV} to start both capture and playback of
     * media in this instance or {@link MediaDirection#SENDONLY} to only start
     * the capture of media in this instance
     */
    private void start(MediaDirection direction)
    {
        if (direction == null)
            throw new NullPointerException("direction");

        /*
         * If the local peer is the focus of a conference for which it is to
         * perform RTP translation even without generating media to be sent, it
         * should create its StreamRTPManager.
         */
        boolean getRTPManagerForRTPTranslator = true;

        MediaDeviceSession deviceSession = getDeviceSession();

        if (direction.allowsSending()
                && ((startedDirection == null)
                        || !startedDirection.allowsSending()))
        {
            logger.info("Start sending media on stream: " + this);

            /*
             * The startSendStreams method will be called so the getRTPManager
             * method will be called as part of the execution of the former.
             */
            getRTPManagerForRTPTranslator = false;

            startSendStreams();

            if (deviceSession != null)
                deviceSession.start(MediaDirection.SENDONLY);

            if (MediaDirection.RECVONLY.equals(startedDirection))
                startedDirection = MediaDirection.SENDRECV;
            else if (startedDirection == null)
                startedDirection = MediaDirection.SENDONLY;

            MediaType mediaType = getMediaType();
            MediaStreamStats stats = getMediaStreamStats();

            logger.info(
                    mediaType
                        + " codec/freq: "
                        + stats.getEncoding()
                        + "/"
                        + stats.getEncodingClockRate()
                        + " Hz");
            logger.info(
                    mediaType
                        + " remote IP/port: "
                        + stats.getRemoteIPAddress()
                        + "/"
                        + String.valueOf(stats.getRemotePort()));
        }

        if (direction.allowsReceiving()
                && ((startedDirection == null)
                        || !startedDirection.allowsReceiving()))
        {
            logger.info("Start receiving media on stream: " + this);

            /*
             * The startReceiveStreams method will be called so the
             * getRTPManager method will be called as part of the execution of
             * the former.
             */
            getRTPManagerForRTPTranslator = false;

            startReceiveStreams();

            if (deviceSession != null)
                deviceSession.start(MediaDirection.RECVONLY);

            if (MediaDirection.SENDONLY.equals(startedDirection))
                startedDirection = MediaDirection.SENDRECV;
            else if (startedDirection == null)
                startedDirection = MediaDirection.RECVONLY;
        }

        /*
         * If the local peer is the focus of a conference for which it is to
         * perform RTP translation even without generating media to be sent, it
         * should create its StreamRTPManager.
         */
        if (getRTPManagerForRTPTranslator && (rtpTranslator != null))
            getRTPManager();
    }

    /**
     * Starts the <tt>ReceiveStream</tt>s that this instance is receiving from
     * its remote peer. By design, a <tt>MediaStream</tt> instance is associated
     * with a single <tt>ReceiveStream</tt> at a time. However, the
     * <tt>ReceiveStream</tt>s are created by <tt>RTPManager</tt> and it tracks
     * multiple <tt>ReceiveStream</tt>s. In practice, the <tt>RTPManager</tt> of
     * this <tt>MediaStreamImpl</tt> will have a single <tt>ReceiveStream</tt>
     * in its list.
     */
    @SuppressWarnings("unchecked")
    private void startReceiveStreams()
    {
        StreamRTPManager rtpManager = getRTPManager();
        List<ReceiveStream> receiveStreams;

        try
        {
            receiveStreams = rtpManager.getReceiveStreams();
        }
        catch (Exception ex)
        {
            /*
             * It appears that in early call states when there are no streams, a
             * NullPointerException could be thrown. Make sure we handle it
             * gracefully.
             */
            logger.trace("Failed to retrieve receive streams", ex);
            receiveStreams = null;
        }

        if (receiveStreams != null)
        {
            /*
             * It turns out that the receiveStreams list of rtpManager can be
             * empty. As a workaround, use the receiveStreams of this instance.
             */
            if (receiveStreams.isEmpty() && mReceiveStreams != null)
            {
                synchronized (mReceiveStreams)
                {
                    receiveStreams =
                            new ArrayList<>(mReceiveStreams);
                }
            }

            for (ReceiveStream receiveStream : receiveStreams)
            {
                try
                {
                    DataSource receiveStreamDataSource
                        = receiveStream.getDataSource();

                    /*
                     * For an unknown reason, the stream DataSource can be null
                     * at the end of the Call after re-INVITEs have been
                     * handled.
                     */
                    if (receiveStreamDataSource != null)
                        receiveStreamDataSource.start();
                }
                catch (IOException ioex)
                {
                     logger.warn(
                        "Failed to start receive stream " + receiveStream,
                        ioex);
                }
            }
        }
    }

    /**
     * Starts the <tt>SendStream</tt>s of the <tt>RTPManager</tt> of this
     * <tt>MediaStreamImpl</tt>.
     */
    private void startSendStreams()
    {
        /*
         * Until it's clear that the SendStreams are required (i.e. we've
         * negotiated to send), they will not be created. Otherwise, their
         * creation isn't only illogical but also causes the CaptureDevice to
         * be used.
         */
        if (!sendStreamsAreCreated)
            createSendStreams();

        StreamRTPManager rtpManager = getRTPManager();
        @SuppressWarnings("unchecked")
        Vector<SendStream> sendStreams = rtpManager.getSendStreams();

        if (sendStreams != null)
        {
            logger.debug("Starting " + sendStreams.size() + " in " + this);
            for (SendStream sendStream : sendStreams)
            {
                try
                {
                    DataSource sendStreamDataSource
                        = sendStream.getDataSource();

                    // TODO Are we sure we want to connect here?
                    sendStreamDataSource.connect();
                    sendStream.start();
                    sendStreamDataSource.start();

                    logger.trace("Started SendStream with hashCode "
                                    + sendStream.hashCode());
                }
                catch (IOException ioe)
                {
                    logger.warn("Failed to start stream " + sendStream, ioe);
                }
            }
        }
    }

    /**
     * Stops all streaming and capturing in this <tt>MediaStream</tt> and closes
     * and releases all open/allocated devices/resources. Has no effect if this
     * <tt>MediaStream</tt> is already closed and is simply ignored.
     *
     * @see MediaStream#stop()
     */
    @Override
    public void stop()
    {
        stop(MediaDirection.SENDRECV);
        started = false;
    }

    /**
     * Stops the processing of media in this instance in a specific direction.
     *
     * @param direction a <tt>MediaDirection</tt> value which represents the
     * direction of the processing of media to be stopped. For example,
     * {@link MediaDirection#SENDRECV} to stop both capture and playback of
     * media in this instance or {@link MediaDirection#SENDONLY} to only stop
     * the capture of media in this instance
     */
    private void stop(MediaDirection direction)
    {
        if (direction == null)
            throw new NullPointerException("direction");

        if (rtpManager == null)
            return;

        if ((MediaDirection.SENDRECV.equals(direction)
                    || MediaDirection.SENDONLY.equals(direction))
                && (MediaDirection.SENDRECV.equals(startedDirection)
                        || MediaDirection.SENDONLY.equals(startedDirection)))
        {
            /*
             * XXX It is not very clear at the time of this writing whether the
             * SendStreams are to be stopped or closed. On one hand, stopping a
             * direction may be a temporary transition which relies on retaining
             * the SSRC. On the other hand, it may be permanent. In which case,
             * the respective ReveiveStream on the remote peer will timeout at
             * some point in time. In the context of video conferences, when a
             * member stops the streaming of their video without leaving the
             * conference, they will stop their SendStreams. However, the other
             * members will need respective BYE RTCP packets in order to know
             * that they are to remove the associated ReceiveStreams from
             * display. The initial version of the code here used to stop the
             * SendStreams without closing them but, given the considerations
             * above, it is being changed to close them in the case of video.
             */
            stopSendStreams(this instanceof VideoMediaStream);

            if (deviceSession != null)
                deviceSession.stop(MediaDirection.SENDONLY);

            if (MediaDirection.SENDRECV.equals(startedDirection))
                startedDirection = MediaDirection.RECVONLY;
            else if (MediaDirection.SENDONLY.equals(startedDirection))
                startedDirection = null;
        }

        if (((MediaDirection.SENDRECV.equals(direction)) ||
              (MediaDirection.RECVONLY.equals(direction))) &&
             ((MediaDirection.SENDRECV.equals(startedDirection)) ||
               (MediaDirection.RECVONLY.equals(startedDirection))))
        {
            stopReceiveStreams();

            if (deviceSession != null)
                deviceSession.stop(MediaDirection.RECVONLY);

            if (MediaDirection.SENDRECV.equals(startedDirection))
                startedDirection = MediaDirection.SENDONLY;
            else if (MediaDirection.RECVONLY.equals(startedDirection))
                startedDirection = null;
        }
    }

    /**
     * Stops the <tt>ReceiveStream</tt>s that this instance is receiving from
     * its remote peer. By design, a <tt>MediaStream</tt> instance is associated
     * with a single <tt>ReceiveStream</tt> at a time. However, the
     * <tt>ReceiveStream</tt>s are created by <tt>RTPManager</tt> and it tracks
     * multiple <tt>ReceiveStream</tt>s. In practice, the <tt>RTPManager</tt> of
     * this <tt>MediaStreamImpl</tt> will have a single <tt>ReceiveStream</tt>
     * in its list.
     */
    @SuppressWarnings("unchecked")
    private void stopReceiveStreams()
    {
        List<ReceiveStream> receiveStreams;

        try
        {
            receiveStreams = rtpManager.getReceiveStreams();
        }
        catch (Exception ex)
        {
            /*
             * It appears that in early call states when there are no streams, a
             * NullPointerException could be thrown. Make sure we handle it
             * gracefully.
             */
            logger.trace("Failed to retrieve receive streams", ex);
            receiveStreams = null;
        }

        if (receiveStreams != null)
        {

            synchronized (mReceiveStreams)
            {
                /*
                 * It turns out that the receiveStreams list of rtpManager can be
                 * empty. As a workaround, use the receiveStreams of this instance.
                 */
                if (receiveStreams.isEmpty() && (mReceiveStreams != null))
                {
                   receiveStreams = new ArrayList<>(mReceiveStreams);
                }
            }

            for (ReceiveStream receiveStream : receiveStreams)
            {
                try
                {
                    logger.trace("Stopping receive stream with hashcode "
                                + receiveStream.hashCode());

                    DataSource receiveStreamDataSource
                       = receiveStream.getDataSource();

                    receiveStream.stopReceiving();

                    /*
                     * For an unknown reason, the stream DataSource can be null
                     * at the end of the Call after re-INVITEs have been
                     * handled.
                     */
                    if (receiveStreamDataSource != null)
                    {
                        receiveStreamDataSource.stop();
                    }
                }
                catch (IOException ioex)
                {
                    logger.warn(
                        "Failed to stop receive stream " + receiveStream,
                        ioex);
                }
            }
        }
    }

    /**
     * Stops the <tt>SendStream</tt>s that this instance is sending to its
     * remote peer and optionally closes them.
     *
     * @param close <tt>true</tt> to close the <tt>SendStream</tt>s that this
     * instance is sending to its remote peer after stopping them;
     * <tt>false</tt> to only stop them
     * @return the <tt>SendStream</tt>s which were stopped
     */
    private Iterable<SendStream> stopSendStreams(boolean close)
    {
        if (rtpManager == null)
            return null;

        @SuppressWarnings("unchecked")
        Iterable<SendStream> sendStreams = rtpManager.getSendStreams();
        Iterable<SendStream> stoppedSendStreams
            = stopSendStreams(sendStreams, close);

        if (close)
            sendStreamsAreCreated = false;

        return stoppedSendStreams;
    }

    /**
     * Stops specific <tt>SendStream</tt>s and optionally closes them.
     *
     * @param sendStreams the <tt>SendStream</tt>s to be stopped and optionally
     * closed
     * @param close <tt>true</tt> to close the specified <tt>SendStream</tt>s
     * after stopping them; <tt>false</tt> to only stop them
     * @return the stopped <tt>SendStream</tt>s
     */
    private Iterable<SendStream> stopSendStreams(
            Iterable<SendStream> sendStreams,
            boolean close)
    {
        if (sendStreams == null)
            return null;

        for (SendStream sendStream : sendStreams)
        {
            try
            {
                logger.trace("Stopping send stream with hashcode "
                                + sendStream.hashCode());

                sendStream.getDataSource().stop();
                sendStream.stop();

                if (close)
                {
                    try
                    {
                        sendStream.close();
                    }
                    catch (NullPointerException npe)
                    {
                        /*
                         * Sometimes com.sun.media.rtp.RTCPTransmitter#bye() may
                         * throw NullPointerException but it does not seem to be
                         * guaranteed because it does not happen while debugging
                         * and stopping at a breakpoint on SendStream#close().
                         * One of the cases in which it appears upon call
                         * hang-up is if we do not close the "old" SendStreams
                         * upon reinvite(s). Though we are now closing such
                         * SendStreams, ignore the exception here just in case
                         * because we already ignore IOExceptions.
                         */
                        logger.error(
                                "Failed to close send stream " + sendStream,
                                npe);
                    }
                }
            }
            catch (IOException ioe)
            {
                logger.warn("Failed to stop send stream " + sendStream, ioe);
            }
        }
        return sendStreams;
    }

    /**
     * Gets a <tt>ReceiveStream</tt> which this instance plays back on its
     * associated <tt>MediaDevice</tt> and which has a specific synchronization
     * source identifier (SSRC).
     *
     * @param ssrc the synchronization source identifier of the
     * <tt>ReceiveStream</tt> to return
     * @return a <tt>ReceiveStream</tt> which this instance plays back on its
     * associated <tt>MediaDevice</tt> and which has the specified <tt>ssrc</tt>
     */
    public ReceiveStream getReceiveStream(int ssrc)
    {
        for (ReceiveStream receiveStream : getReceiveStreams())
        {
            int receiveStreamSSRC = (int) receiveStream.getSSRC();

            if (receiveStreamSSRC == ssrc)
                return receiveStream;
        }
        return null;
    }

    /**
     * Gets a list of the <tt>ReceiveStream</tt>s this instance plays back on
     * its associated <tt>MediaDevice</tt>.
     *
     * @return a list of the <tt>ReceiveStream</tt>s this instance plays back on
     * its associated <tt>MediaDevice</tt>
     */
    public Collection<ReceiveStream> getReceiveStreams()
    {
        Set<ReceiveStream> receiveStreams = new HashSet<>();

        // This instance maintains a list of the ReceiveStreams.

        synchronized(mReceiveStreams)
        {
            receiveStreams.addAll(this.mReceiveStreams);
        }

        /*
         * Unfortunately, it has been observed that sometimes there are valid
         * ReceiveStreams in this instance which are not returned by the
         * rtpManager.
         */
        StreamRTPManager rtpManager = queryRTPManager();

        if (rtpManager != null)
        {
            @SuppressWarnings("unchecked")
            Collection<ReceiveStream> rtpManagerReceiveStreams
                = rtpManager.getReceiveStreams();

            receiveStreams.addAll(rtpManagerReceiveStreams);
        }

        return receiveStreams;
    }

    /**
     * Returns a human-readable representation of a specific <tt>DataSource</tt>
     * instance in the form of a <tt>String</tt> value.
     *
     * @param dataSource the <tt>DataSource</tt> to return a human-readable
     * representation of
     * @return a <tt>String</tt> value which gives a human-readable
     * representation of the specified <tt>dataSource</tt>
     */
    public static String toString(DataSource dataSource)
    {
        StringBuffer str = new StringBuffer();

        str.append(dataSource.getClass().getSimpleName());
        str.append(" with hashCode ");
        str.append(dataSource.hashCode());

        MediaLocator locator = dataSource.getLocator();

        if (locator != null)
        {
            str.append(" and locator ");
            str.append(locator);
        }
        return str.toString();
    }

    /**
     * Notifies this <tt>ReceiveStreamListener</tt> that the <tt>RTPManager</tt>
     * it is registered with has generated an event related to a <tt>ReceiveStream</tt>.
     *
     * @param event the <tt>ReceiveStreamEvent</tt> which specifies the
     * <tt>ReceiveStream</tt> that is the cause of the event and the very type
     * of the event
     * @see ReceiveStreamListener#update(ReceiveStreamEvent)
     */
    @Override
    public void update(ReceiveStreamEvent event)
    {
        if (event instanceof NewReceiveStreamEvent)
        {
            ReceiveStream receiveStream = event.getReceiveStream();

            if (receiveStream != null)
            {
                long receiveStreamSSRC = receiveStream.getSSRC();

                logger.trace("Received new ReceiveStream with ssrc "
                                + receiveStreamSSRC);

                addRemoteSourceID(receiveStreamSSRC);

                synchronized (mReceiveStreams)
                {
                    if (!mReceiveStreams.contains(receiveStream))
                    {
                        mReceiveStreams.add(receiveStream);

                        MediaDeviceSession deviceSession = getDeviceSession();

                        if (deviceSession != null)
                            deviceSession.addReceiveStream(receiveStream);
                    }
                }
            }
        }
        else if (event instanceof TimeoutEvent)
        {
            ReceiveStream receiveStream = event.getReceiveStream();

            /*
             * If we recreate streams, we will already have restarted
             * zrtpControl. But when on the other end someone recreates his
             * streams, we will receive a ByeEvent (which extends TimeoutEvent)
             * and then we must also restart our ZRTP. This happens, for
             * example, when we are already in a call and the remote peer
             * converts his side of the call into a conference call.
             */
            /*
            if(!zrtpRestarted)
                restartZrtpControl();
            */

            if (receiveStream != null)
            {
                logger.trace("Received TimeoutEvent on receive stream" +
                        receiveStream.hashCode());

                synchronized (mReceiveStreams)
                {
                    if (mReceiveStreams.contains(receiveStream))
                    {
                        mReceiveStreams.remove(receiveStream);

                        MediaDeviceSession deviceSession = getDeviceSession();

                        if (deviceSession != null)
                            deviceSession.removeReceiveStream(receiveStream);
                    }
                }
            }
        }
        else if (event instanceof RemotePayloadChangeEvent)
        {
            ReceiveStream receiveStream = event.getReceiveStream();

            if(receiveStream != null)
            {
                logger.trace(
                        "Received RemotePayloadChangeEvent on receive stream" +
                        receiveStream.hashCode());

                MediaDeviceSession deviceSession = getDeviceSession();

                if (deviceSession != null)
                {
                    TranscodingDataSource transcodingDataSource
                        = deviceSession.getTranscodingDataSource(receiveStream);

                    // we receive packets, streams are active
                    // if processor in transcoding DataSource is running
                    // we need to recreate it by disconnect, connect
                    // and starting again the DataSource
                    try
                    {
                        if (transcodingDataSource != null)
                        {
                            logger.debug("Restarting transcoding data source " +
                                transcodingDataSource.hashCode());
                            transcodingDataSource.reconnect();
                            transcodingDataSource.start();
                        }

                        // as output streams of the DataSource
                        // are recreated we need to update
                        // mixers and everything that are using them
                        deviceSession.playbackDataSourceChanged(
                            receiveStream.getDataSource());
                    }
                    catch(Exception e)
                    {
                        logger.error("Error re-creating processor in " +
                            "transcoding DataSource", e);
                    }
                }
            }
        }
    }

    /**
     * Notifies this <tt>SendStreamListener</tt> that the <tt>RTPManager</tt> it
     * is registered with has generated an event related to a <tt>SendStream</tt>.
     *
     * @param event the <tt>SendStreamEvent</tt> which specifies the
     * <tt>SendStream</tt> that is the cause of the event and the very type of
     * the event
     * @see SendStreamListener#update(SendStreamEvent)
     */
    @Override
    public void update(SendStreamEvent event)
    {
        // @@@ ENH hack.
        /*
        if (event instanceof NewSendStreamEvent)
        {
            long localSourceID = event.getSendStream().getSSRC();

            if (getLocalSourceID() != localSourceID)
                setLocalSourceID(localSourceID);
        }
        */
    }

    /**
     * Notifies this <tt>SessionListener</tt> that the <tt>RTPManager</tt> it is
     * registered with has generated an event which pertains to the session as a
     * whole and does not belong to a <tt>ReceiveStream</tt> or a
     * <tt>SendStream</tt> or a remote participant necessarily.
     *
     * @param event the <tt>SessionEvent</tt> which specifies the source and the
     * very type of the event
     * @see SessionListener#update(SessionEvent)
     */
    @Override
    public void update(SessionEvent event)
    {
        // TODO Auto-generated method stub
    }

    /**
     * Method called back in the RemoteListener to notify
     * listener of all RTP Remote Events.RemoteEvents are one of
     * ReceiverReportEvent, SenderReportEvent or RemoteCollisionEvent
     *
     * @param remoteEvent the event
     */
    @Override
    public void update(RemoteEvent remoteEvent)
    {
        if(remoteEvent instanceof SenderReportEvent ||
                remoteEvent instanceof ReceiverReportEvent)
        {
            Report report;
            boolean senderReport = false;
            if(remoteEvent instanceof SenderReportEvent)
            {
                report = ((SenderReportEvent)remoteEvent).getReport();
                senderReport = true;
            }
            else
            {
                report = ((ReceiverReportEvent)remoteEvent).getReport();
            }

            Feedback feedback = null;
            long remoteJitter = -1;

            if(report.getFeedbackReports().size() > 0)
            {
                feedback = (Feedback)report.getFeedbackReports().get(0);

                remoteJitter = feedback.getJitter();

                if((remoteJitter < minRemoteInterArrivalJitter)
                        || (minRemoteInterArrivalJitter == -1))
                    minRemoteInterArrivalJitter = remoteJitter;

                if(maxRemoteInterArrivalJitter < remoteJitter)
                    maxRemoteInterArrivalJitter = remoteJitter;
            }

            //Notify encoders of the percentage of packets lost by the
            //other side. See RFC3550 Section 6.4.1 for the interpretation of
            //'fraction lost'
            if ((feedback != null)
                    && (getDirection() != MediaDirection.INACTIVE))
            {
                Set<PacketLossAwareEncoder> plaes = null;
                MediaDeviceSession deviceSession= getDeviceSession();
                if (deviceSession != null)
                    plaes = deviceSession.getEncoderControls(
                            PacketLossAwareEncoder.class);

                if (plaes != null && !plaes.isEmpty())
                {
                    int expectedPacketLoss
                        = (feedback.getFractionLost() * 100) / 256;

                    for (PacketLossAwareEncoder plae : plaes)
                    {
                        if (plae != null)
                            plae.setExpectedPacketLoss(expectedPacketLoss);
                    }
                }
            }

            StringBuilder buff = new StringBuilder("\n");
            buff.append(StatisticsEngine.RTP_STAT_PREFIX);
            MediaType mediaType = getMediaType();
            String mediaTypeStr
                = (mediaType == null) ? "" : mediaType.toString();

            buff.append("Received a ")
                .append(senderReport ? "sender" : "receiver")
                .append(" report for ")
                .append(mediaTypeStr)
                .append(" stream SSRC:")
                .append(getLocalSourceID())
                .append(" [");
            if(senderReport)
            {
                buff.append("packet count:")
                    .append(((SenderReport) report).getSenderPacketCount())
                    .append(", bytes:")
                    .append(((SenderReport) report).getSenderByteCount());
            }

            if(feedback != null)
            {
                buff.append(", interarrival jitter:")
                    .append(remoteJitter)
                    .append(", lost packets:").append(feedback.getNumLost())
                    .append(", time since previous report:")
                    .append((int) (feedback.getDLSR() / 65.536))
                    .append("ms");
            }
            buff.append(" ]");
            logger.info(buff);
        }
    }

    /**
     * Sets the local SSRC identifier and fires the corresponding
     * <tt>PropertyChangeEvent</tt>.
     *
     * @param localSourceID the SSRC identifier that this stream will be using
     * in outgoing RTP packets from now on
     */
    protected void setLocalSourceID(long localSourceID)
    {
        if (this.localSourceID != localSourceID)
        {
            Long oldValue = this.localSourceID;

            this.localSourceID = localSourceID;

            firePropertyChange(PNAME_LOCAL_SSRC, oldValue, this.localSourceID);
        }
    }

    /**
     * Sets the remote SSRC identifier and fires the corresponding
     * <tt>PropertyChangeEvent</tt>.
     *
     * @param remoteSourceID the SSRC identifier that this stream will be using
     * in outgoing RTP packets from now on.
     */
    protected void addRemoteSourceID(long remoteSourceID)
    {
        Long oldValue = getRemoteSourceID();

        if(!remoteSourceIDs.contains(remoteSourceID))
            remoteSourceIDs.add(remoteSourceID);

        firePropertyChange(PNAME_REMOTE_SSRC, oldValue, remoteSourceID);
    }

    /**
     * Used to set the priority of the receive/send streams. Underling
     * implementations can override this and return different than
     * current default value.
     *
     * @return the priority for the current thread.
     */
    protected int getPriority()
    {
        return Thread.currentThread().getPriority();
    }

    /**
     * Prints all statistics available for {@link #rtpManager}.
     *
     * @param rtpManager the <tt>RTPManager</tt> to print statistics for
     */
    private void printFlowStatistics(StreamRTPManager rtpManager)
    {
        try
        {
            //print flow statistics.
            GlobalTransmissionStats s = rtpManager.getGlobalTransmissionStats();

            StringBuilder buff = new StringBuilder("\n");
            buff.append(StatisticsEngine.RTP_STAT_PREFIX);
            MediaType mediaType = getMediaType();
            String mediaTypeStr
                = (mediaType == null) ? "" : mediaType.toString();

            buff.append("call stats for outgoing ")
                .append(mediaTypeStr)
                .append(" stream SSRC:")
                .append(getLocalSourceID())
                    .append("\n").append(StatisticsEngine.RTP_STAT_PREFIX)
                .append("bytes sent: ").append(s.getBytesSent())
                    .append("\n").append(StatisticsEngine.RTP_STAT_PREFIX)
                .append("RTP sent: ").append(s.getRTPSent())
                    .append("\n").append(StatisticsEngine.RTP_STAT_PREFIX)
                .append("remote reported min interarrival jitter : ")
                        .append(minRemoteInterArrivalJitter)
                    .append("\n").append(StatisticsEngine.RTP_STAT_PREFIX)
                .append("remote reported max interarrival jitter : ")
                        .append(maxRemoteInterArrivalJitter)
                    .append("\n").append(StatisticsEngine.RTP_STAT_PREFIX)
                .append("local collisions: ").append(s.getLocalColls())
                    .append("\n").append(StatisticsEngine.RTP_STAT_PREFIX)
                .append("remote collisions: ").append(s.getRemoteColls())
                    .append("\n").append(StatisticsEngine.RTP_STAT_PREFIX)
                .append("RTCP sent: ").append(s.getRTCPSent())
                    .append("\n").append(StatisticsEngine.RTP_STAT_PREFIX)
                .append("transmit failed: ").append(s.getTransmitFailed());

            logger.info(buff);

            GlobalReceptionStats rs = rtpManager.getGlobalReceptionStats();
            MediaFormat format = getFormat();

            buff = new StringBuilder(StatisticsEngine.RTP_STAT_PREFIX);
            buff.append("call stats for incoming ")
                .append((format == null) ? "" : format)
                .append(" stream SSRC:")
                .append(getRemoteSourceID())
                .append("\n").append(StatisticsEngine.RTP_STAT_PREFIX)
                .append("packets received: ").append(rs.getPacketsRecd())
                    .append("\n").append(StatisticsEngine.RTP_STAT_PREFIX)
                .append("bytes received: ").append(rs.getBytesRecd())
                    .append("\n").append(StatisticsEngine.RTP_STAT_PREFIX)
                .append("packets lost: ").append(statisticsEngine.getLost())
                    .append("\n").append(StatisticsEngine.RTP_STAT_PREFIX)
                .append("min interarrival jitter : ")
                    .append(statisticsEngine.getMinInterArrivalJitter())
                    .append("\n").append(StatisticsEngine.RTP_STAT_PREFIX)
                .append("max interarrival jitter : ")
                    .append(statisticsEngine.getMaxInterArrivalJitter())
                    .append("\n").append(StatisticsEngine.RTP_STAT_PREFIX)
                .append("RTCPs received: ").append(rs.getRTCPRecd())
                    .append("\n").append(StatisticsEngine.RTP_STAT_PREFIX)
                .append("bad RTCP packets: ").append(rs.getBadRTCPPkts())
                    .append("\n").append(StatisticsEngine.RTP_STAT_PREFIX)
                .append("bad RTP packets: ").append(rs.getBadRTPkts())
                    .append("\n").append(StatisticsEngine.RTP_STAT_PREFIX)
                .append("local collisions: ").append(rs.getLocalColls())
                    .append("\n").append(StatisticsEngine.RTP_STAT_PREFIX)
                .append("malformed BYEs: ").append(rs.getMalformedBye())
                    .append("\n").append(StatisticsEngine.RTP_STAT_PREFIX)
                .append("malformed RRs: ").append(rs.getMalformedRR())
                    .append("\n").append(StatisticsEngine.RTP_STAT_PREFIX)
                .append("malformed SDESs: ").append(rs.getMalformedSDES())
                    .append("\n").append(StatisticsEngine.RTP_STAT_PREFIX)
                .append("malformed SRs: ").append(rs.getMalformedSR())
                    .append("\n").append(StatisticsEngine.RTP_STAT_PREFIX)
                .append("packets looped: ").append(rs.getPacketsLooped())
                    .append("\n").append(StatisticsEngine.RTP_STAT_PREFIX)
                .append("remote collisions: ").append(rs.getRemoteColls())
                    .append("\n").append(StatisticsEngine.RTP_STAT_PREFIX)
                .append("SRRs received: ").append(rs.getSRRecd())
                    .append("\n").append(StatisticsEngine.RTP_STAT_PREFIX)
                .append("transmit failed: ").append(rs.getTransmitFailed())
                    .append("\n").append(StatisticsEngine.RTP_STAT_PREFIX)
                .append("unknown types: ").append(rs.getUnknownTypes());

            logger.info(buff);
        }
        catch(Throwable t)
        {
            logger.error("Error writing statistics", t);
        }
    }

    private void printReceiveStreamStatistics()
    {
        mediaStreamStatsImpl.updateStats();
        StringBuilder buff = new StringBuilder("\nReceive stream stats: " +
            "discarded RTP packets: ")
                .append(mediaStreamStatsImpl.getNbDiscarded())
                .append("\n").append("Receive stream stats: " +
                    "decoded with FEC: ")
                .append(mediaStreamStatsImpl.getNbFec());
        logger.info(buff);
    }

    /**
     * Sets the <tt>RTPTranslator</tt> which is to forward RTP and RTCP traffic
     * between this and other <tt>MediaStream</tt>s.
     *
     * @param rtpTranslator the <tt>RTPTranslator</tt> which is to forward RTP
     * and RTCP traffic between this and other <tt>MediaStream</tt>s
     */
    @Override
    public void setRTPTranslator(RTPTranslator rtpTranslator)
    {
        if (this.rtpTranslator != rtpTranslator)
            this.rtpTranslator = rtpTranslator;
    }

    /**
     * Returns a MediaStreamStats object used to get statistics about this
     * MediaStream.
     *
     * @return the MediaStreamStats object used to compute the statistics about
     * this MediaStream.
     */
    @Override
    public MediaStreamStats getMediaStreamStats()
    {
        return this.mediaStreamStatsImpl;
    }

    /**
     * Gets the <tt>MediaType</tt> of this <tt>MediaStream</tt>.
     *
     * @return the <tt>MediaType</tt> of this <tt>MediaStream</tt>
     */
    public MediaType getMediaType()
    {
        MediaFormat format = getFormat();
        MediaType mediaType = null;

        if (format != null)
            mediaType = format.getMediaType();
        if (mediaType == null)
        {
            MediaDeviceSession deviceSession = getDeviceSession();

            if (deviceSession != null)
                mediaType = deviceSession.getDevice().getMediaType();
            if (mediaType == null)
            {
                if (this instanceof AudioMediaStream)
                    mediaType = MediaType.AUDIO;
                else if (this instanceof VideoMediaStream)
                    mediaType = MediaType.VIDEO;
            }
        }

        return mediaType;
    }

    /**
     * Adds an additional RTP payload mapping that will overriding one that
     * we've set with {@link #addDynamicRTPPayloadType(byte, MediaFormat)}.
     * This is necessary so that we can support the RFC3264 case where the
     * answerer has the right to declare what payload type mappings it wants to
     * receive RTP packets with even if they are different from those in the
     * offer. RFC3264 claims this is for support of legacy protocols such as
     * H.323 but we've been bumping with a number of cases where multi-component
     * pure SIP systems also need to behave this way.
     * <p>
     *
     * @param originalPt the payload type that we are overriding
     * @param overloadPt the payload type that we are overriging it with
     */
    @Override
    public void addDynamicRTPPayloadTypeOverride(byte originalPt,
                                                 byte overloadPt)
    {
        if(ptTransformEngine != null)
        {
            ptTransformEngine.addPTMappingOverride(originalPt, overloadPt);
        }
    }

    /**
     * Clears the dynamic RTP payload type override mapping as set by
     * {@link #addDynamicRTPPayloadTypeOverride(byte, byte)} so that we can set
     * new overrides.
     */
    @Override
    public void clearDynamicRTPPayloadTypeOverrides()
    {
        if (ptTransformEngine != null)
        {
            ptTransformEngine.clearPTMappingOverrides();
        }
    }

    @Override
    public void sendHolePunchPackets(MediaStreamTarget target)
    {
        // Check how many hole punch packets we would be supposed to send:
        int packetCount = LibJitsi.getConfigurationService().global()
                                    .getInt(HOLE_PUNCH_PKT_COUNT_PROPERTY,
                                            DEFAULT_HOLE_PUNCH_PKT_COUNT);

        if (packetCount < 0)
            packetCount = DEFAULT_HOLE_PUNCH_PKT_COUNT;

        InetSocketAddress remoteDataAddress = target.getDataAddress();
        InetSocketAddress remoteControlAddress = target.getControlAddress();

        // Create the hole punch packets
        RawPacket dataPacket = createHolePunchPacket(getLocalDataAddress().getPort(),
                                                     remoteDataAddress.getPort());
        RawPacket controlPacket = createHolePunchPacket(getLocalControlAddress().getPort(),
                                                        remoteControlAddress.getPort());

        // If we are using SRTP then the packet must be encrypted for the SBC to
        // latch this media stream.
        if (getSrtpControl().getSecureCommunicationStatus())
        {
            dataPacket = srtpControl.getTransformEngine().
                                      getRTPTransformer().transform(dataPacket);

            controlPacket = srtpControl.getTransformEngine().
                                   getRTPTransformer().transform(controlPacket);
        }

        for (int i = 0; i < packetCount; i++)
        {
            try
            {
                logger.debug("Sending data hole punch packet to " + remoteDataAddress);
                rtpConnector.getDataOutputStream().
                                    sendToTarget(dataPacket, remoteDataAddress);

                logger.debug("Sending control hole punch packet to " + remoteControlAddress);
                rtpConnector.getControlOutputStream().
                              sendToTarget(controlPacket, remoteControlAddress);
            }
            catch (IOException ex)
            {
                logger.error("Failed to send hole punch packets", ex);
            }
        }
    }

    /**
     * Constructs hole punch packet for the given local and remote port. The
     * packet has no payload, but must have valid headers.
     *
     * @param localPort the local port for this stream
     * @param remotePort the remote port for this stream
     * @return hole punch UDP packet
     */
    private RawPacket createHolePunchPacket(int localPort, int remotePort)
    {
        // The packet data. This is just the UDP header as the data is 0 length.
        final ByteBuffer byteBuffer = ByteBuffer.allocate(16);

        // Construct the UDP header
        // The source port
        byteBuffer.putInt(localPort);
        // The remote port
        byteBuffer.putInt(remotePort);
        // The length of the header (always 16)
        byteBuffer.putInt(16);
        // The checksum. Not used so just use 0.
        byteBuffer.putInt(0);

        // Create a RawPacket from the byte array we just constructed
        return new RawPacket(byteBuffer.array(), 0, 16);
    }

    @Override
    public void setSrtpControl(SrtpControl srtpControl)
    {
        this.srtpControl = srtpControl;
        rtpConnectorChanged(null, rtpConnector);
    }
}
