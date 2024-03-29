/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
// Portions (c) Microsoft Corporation. All rights reserved.
package org.jitsi.impl.neomedia;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.regex.*;

import javax.media.*;
import javax.media.control.*;
import javax.media.format.*;
import javax.media.protocol.*;

import org.jitsi.impl.neomedia.device.*;
import org.jitsi.service.neomedia.QualityControl;
import org.jitsi.service.neomedia.QualityPreset;
import org.jitsi.service.neomedia.SrtpControl;
import org.jitsi.service.neomedia.StreamConnector;
import org.jitsi.service.neomedia.VideoMediaStream;
import org.jitsi.service.neomedia.control.KeyFrameControl;
import org.jitsi.service.neomedia.control.KeyFrameControlAdapter;
import org.jitsi.service.neomedia.device.*;
import org.jitsi.service.neomedia.format.*;
import org.jitsi.util.*;
import org.jitsi.util.event.*;

/**
 * Extends <tt>MediaStreamImpl</tt> in order to provide an implementation of
 * <tt>VideoMediaStream</tt>.
 *
 * @author Lyubomir Marinov
 * @author Sebastien Vincent
 */
public class VideoMediaStreamImpl
    extends MediaStreamImpl
    implements VideoMediaStream
{
    /**
     * The <tt>Logger</tt> used by the <tt>VideoMediaStreamImpl</tt> class and
     * its instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(VideoMediaStreamImpl.class);

    /**
     * The indicator which determines whether RTCP feedback Picture Loss
     * Indication messages are to be used.
     */
    private static final boolean USE_PLI = true;

    /**
     * Extracts and returns maximum resolution can receive from the image
     * attribute.
     *
     * @param imgattr send/recv resolution string
     * @return maximum resolution array (first element is send, second one is
     * recv). Elements could be null if image attribute is not present or if
     * resolution is a wildcard.
     */
    public static java.awt.Dimension[] parseSendRecvResolution(String imgattr)
    {
        java.awt.Dimension res[] = new java.awt.Dimension[2];
        String token = null;
        Pattern pSendSingle = Pattern.compile("send \\[x=[0-9]+,y=[0-9]+\\]");
        Pattern pRecvSingle = Pattern.compile("recv \\[x=[0-9]+,y=[0-9]+\\]");
        Pattern pSendRange = Pattern.compile(
                "send \\[x=\\[[0-9]+-[0-9]+\\],y=\\[[0-9]+-[0-9]+\\]\\]");
        Pattern pRecvRange = Pattern.compile(
                "recv \\[x=\\[[0-9]+-[0-9]+\\],y=\\[[0-9]+-[0-9]+\\]\\]");
        Pattern pNumeric = Pattern.compile("[0-9]+");
        Matcher mSingle = null;
        Matcher mRange = null;
        Matcher m = null;

        /* resolution (width and height) can be on four forms
         *
         * - single value [x=1920,y=1200]
         * - range of values [x=[800-1024],y=[600-768]]
         * - fixed range of values [x=[800,1024],y=[600,768]]
         * - range of values with step [x=[800:32:1024],y=[600:32:768]]
         *
         * For the moment we only support the first two forms.
         */

        /* send part */
        mSingle = pSendSingle.matcher(imgattr);
        mRange = pSendRange.matcher(imgattr);

        if(mSingle.find())
        {
            int val[] = new int[2];
            int i = 0;
            token = imgattr.substring(mSingle.start(), mSingle.end());
            m = pNumeric.matcher(token);

            while(m.find() && i < 2)
            {
                val[i] = Integer.parseInt(token.substring(m.start(), m.end()));
            }

            res[0] = new java.awt.Dimension(val[0], val[1]);
        }
        else if(mRange.find()) /* try with range */
        {
            /* have two value for width and two for height (min-max) */
            int val[]  = new int[4];
            int i = 0;
            token = imgattr.substring(mRange.start(), mRange.end());
            m = pNumeric.matcher(token);

            while(m.find() && i < 4)
            {
                val[i] = Integer.parseInt(token.substring(m.start(), m.end()));
                i++;
            }

            res[0] = new java.awt.Dimension(val[1], val[3]);
        }

        /* recv part */
        mSingle = pRecvSingle.matcher(imgattr);
        mRange = pRecvRange.matcher(imgattr);

        if(mSingle.find())
        {
            int val[] = new int[2];
            int i = 0;
            token = imgattr.substring(mSingle.start(), mSingle.end());
            m = pNumeric.matcher(token);

            while(m.find() && i < 2)
            {
                val[i] = Integer.parseInt(token.substring(m.start(), m.end()));
            }

            res[1] = new java.awt.Dimension(val[0], val[1]);
        }
        else if(mRange.find()) /* try with range */
        {
            /* have two value for width and two for height (min-max) */
            int val[]  = new int[4];
            int i = 0;
            token = imgattr.substring(mRange.start(), mRange.end());
            m = pNumeric.matcher(token);

            while(m.find() && i < 4)
            {
                val[i] = Integer.parseInt(token.substring(m.start(), m.end()));
                i++;
            }

            res[1] = new java.awt.Dimension(val[1], val[3]);
        }

        token = null;
        mSingle = null;
        mRange = null;
        m = null;
        pRecvRange = null;
        pSendSingle = null;
        pRecvSingle = null;
        pSendRange = null;

        return res;
    }

    /**
     * Selects the <tt>VideoFormat</tt> from the list of supported formats of a
     * specific video <tt>DataSource</tt> which has a size as close as possible
     * to a specific size and sets it as the format of the specified video
     * <tt>DataSource</tt>.
     *
     * @param videoDS the video <tt>DataSource</tt> which is to have its
     * supported formats examined and its format changed to the
     * <tt>VideoFormat</tt> which is as close as possible to the specified
     * <tt>preferredWidth</tt> and <tt>preferredHeight</tt>
     * @param preferredWidth the width of the <tt>VideoFormat</tt> to be
     * selected
     * @param preferredHeight the height of the <tt>VideoFormat</tt> to be
     * selected
     * @return the size of the <tt>VideoFormat</tt> from the list of supported
     * formats of <tt>videoDS</tt> which is as close as possible to
     * <tt>preferredWidth</tt> and <tt>preferredHeight</tt> and which has been
     * set as the format of <tt>videoDS</tt>
     */
    public static Dimension selectVideoSize(
            DataSource videoDS,
            final int preferredWidth, final int preferredHeight)
    {
        if (videoDS == null)
            return null;

        FormatControl formatControl
            = (FormatControl) videoDS.getControl(FormatControl.class.getName());

        if (formatControl == null)
            return null;

        Format[] formats = formatControl.getSupportedFormats();
        final int count = formats.length;

        if (count < 1)
            return null;

        VideoFormat selectedFormat = null;

        if (count == 1)
            selectedFormat = (VideoFormat) formats[0];
        else
        {
            class FormatInfo
            {
                public final double difference;

                public final Dimension dimension;

                public final VideoFormat format;

                public FormatInfo(Dimension size)
                {
                    this.format = null;

                    this.dimension = size;

                    this.difference = getDifference(this.dimension);
                }

                public FormatInfo(VideoFormat format)
                {
                    this.format = format;

                    this.dimension = format.getSize();

                    this.difference = getDifference(this.dimension);
                }

                private double getDifference(Dimension size)
                {
                    int width = (size == null) ? 0 : size.width;
                    double xScale;

                    if (width == 0)
                        xScale = Double.POSITIVE_INFINITY;
                    else if (width == preferredWidth)
                        xScale = 1;
                    else
                        xScale = (preferredWidth / (double) width);

                    int height = (size == null) ? 0 : size.height;
                    double yScale;

                    if (height == 0)
                        yScale = Double.POSITIVE_INFINITY;
                    else if (height == preferredHeight)
                        yScale = 1;
                    else
                        yScale = (preferredHeight / (double) height);

                    return Math.abs(1 - Math.min(xScale, yScale));
                }
            }

            FormatInfo[] infos = new FormatInfo[count];

            for (int i = 0; i < count; i++)
            {
                FormatInfo info
                    = infos[i]
                        = new FormatInfo((VideoFormat) formats[i]);

                if (info.difference == 0)
                {
                    selectedFormat = info.format;
                    break;
                }
            }
            if (selectedFormat == null)
            {
                Arrays.sort(infos, new Comparator<FormatInfo>()
                {
                    public int compare(FormatInfo info0, FormatInfo info1)
                    {
                        return
                            Double.compare(info0.difference, info1.difference);
                    }
                });
                selectedFormat = infos[0].format;
            }

            /*
             * If videoDS states to support any size, use the sizes that we
             * support which is closest(or smaller) to the preferred one.
             */
            if ((selectedFormat != null)
                    && (selectedFormat.getSize() == null))
            {
                VideoFormat currentFormat
                    = (VideoFormat) formatControl.getFormat();
                Dimension currentSize = null;
                int width = preferredWidth;
                int height = preferredHeight;

                // Try to preserve the aspect ratio
                if (currentFormat != null)
                    currentSize = currentFormat.getSize();

                // sort supported resolutions by aspect
                FormatInfo[] supportedInfos
                    = new FormatInfo[
                            DeviceConfiguration.SUPPORTED_RESOLUTIONS.length];
                for (int i = 0; i < supportedInfos.length; i++)
                {
                    supportedInfos[i]
                        = new FormatInfo(
                            DeviceConfiguration.SUPPORTED_RESOLUTIONS[i]);
                }
                Arrays.sort(infos, new Comparator<FormatInfo>()
                {
                    public int compare(FormatInfo info0, FormatInfo info1)
                    {
                        return
                            Double.compare(info0.difference, info1.difference);
                    }
                });

                FormatInfo preferredFormat
                    = new FormatInfo(
                            new Dimension(preferredWidth, preferredHeight));

                Dimension closestAspect = null;
                // Let's choose the closest size to the preferred one, finding
                // the first suitable aspect
                for(FormatInfo supported : supportedInfos)
                {
                    // find the first matching aspect
                    if(preferredFormat.difference > supported.difference)
                        continue;
                    else if(closestAspect == null)
                        closestAspect = supported.dimension;

                    if(supported.dimension.height <= preferredHeight
                       && supported.dimension.width <= preferredWidth)
                    {
                        currentSize = supported.dimension;
                    }
                }

                if(currentSize == null)
                    currentSize = closestAspect;

                if ((currentSize.width > 0) && (currentSize.height > 0))
                {
                    width = currentSize.width;
                    height = currentSize.height;
                }
                selectedFormat
                    = (VideoFormat)
                        new VideoFormat(
                                null,
                                new Dimension(width, height),
                                Format.NOT_SPECIFIED,
                                null,
                                Format.NOT_SPECIFIED)
                            .intersects(selectedFormat);
            }
        }

        Format setFormat = formatControl.setFormat(selectedFormat);

        return
            (setFormat instanceof VideoFormat)
                ? ((VideoFormat) setFormat).getSize()
                : null;
    }

    /**
     * The <tt>VideoListener</tt> which handles <tt>VideoEvent</tt>s from the
     * <tt>MediaDeviceSession</tt> of this instance and fires respective
     * <tt>VideoEvent</tt>s from this <tt>VideoMediaStream</tt> to its
     * <tt>VideoListener</tt>s.
     */
    private VideoListener deviceSessionVideoListener;

    /**
     * The <tt>KeyFrameControl</tt> of this <tt>VideoMediaStream</tt>.
     */
    private KeyFrameControl keyFrameControl;

    /**
     * Negotiated output size of the video stream.
     * It may need to scale original capture device stream.
     */
    private Dimension outputSize;

    /**
     * The <tt>QualityControl</tt> of this <tt>VideoMediaStream</tt>.
     */
    private final QualityControlImpl qualityControl = new QualityControlImpl();

    /**
     * The facility which aids this instance in managing a list of
     * <tt>VideoListener</tt>s and firing <tt>VideoEvent</tt>s to them.
     * <p>
     * Since the <tt>videoNotifierSupport</tt> of this
     * <tt>VideoMediaStreamImpl</tt> just forwards the <tt>VideoEvent</tt>s of
     * the associated <tt>VideoMediaDeviceSession</tt> at the time of this
     * writing, it does not make sense to have <tt>videoNotifierSupport</tt>
     * executing asynchronously because it does not know whether it has to wait
     * for the delivery of the <tt>VideoEvent</tt>s and thus it has to default
     * to waiting anyway.
     * </p>
     */
    private final VideoNotifierSupport videoNotifierSupport
        = new VideoNotifierSupport(this, true);

    /**
     * Initializes a new <tt>VideoMediaStreamImpl</tt> instance which will use
     * the specified <tt>MediaDevice</tt> for both capture and playback of video
     * exchanged via the specified <tt>StreamConnector</tt>.
     *
     * @param connector the <tt>StreamConnector</tt> the new instance is to use
     * for sending and receiving video
     * @param device the <tt>MediaDevice</tt> the new instance is to use for
     * both capture and playback of video exchanged via the specified
     * <tt>StreamConnector</tt>
     * @param srtpControl a control which is already created, used to control
     * the srtp operations.
     */
    public VideoMediaStreamImpl(StreamConnector connector, MediaDevice device,
        SrtpControl srtpControl)
    {
        super(connector, device, srtpControl);
    }

    /**
     * Set remote SSRC.
     *
     * @param ssrc remote SSRC
     */
    @Override
    protected void addRemoteSourceID(long ssrc)
    {
        super.addRemoteSourceID(ssrc);

        MediaDeviceSession deviceSession = getDeviceSession();

        if (deviceSession instanceof VideoMediaDeviceSession)
            ((VideoMediaDeviceSession) deviceSession).setRemoteSSRC(ssrc);
    }

    /**
     * Adds a specific <tt>VideoListener</tt> to this <tt>VideoMediaStream</tt>
     * in order to receive notifications when visual/video <tt>Component</tt>s
     * are being added and removed.
     * <p>
     * Adding a listener which has already been added does nothing i.e. it is
     * not added more than once and thus does not receive one and the same
     * <tt>VideoEvent</tt> multiple times.
     * </p>
     *
     * @param listener the <tt>VideoListener</tt> to be notified when
     * visual/video <tt>Component</tt>s are being added or removed in this
     * <tt>VideoMediaStream</tt>
     */
    public void addVideoListener(VideoListener listener)
    {
        videoNotifierSupport.addVideoListener(listener);
    }

    /**
     * Performs any optional configuration on a specific
     * <tt>RTPConnectorOuputStream</tt> of an <tt>RTPManager</tt> to be used by
     * this <tt>MediaStreamImpl</tt>.
     *
     * @param dataOutputStream the <tt>RTPConnectorOutputStream</tt> to be used
     * by an <tt>RTPManager</tt> of this <tt>MediaStreamImpl</tt> and to be
     * configured
     */
    @Override
    protected void configureDataOutputStream(
            RTPConnectorOutputStream dataOutputStream)
    {
        super.configureDataOutputStream(dataOutputStream);

        int maxBandwidth
            = NeomediaServiceUtils
                .getMediaServiceImpl()
                    .getDeviceConfiguration()
                        .getVideoRTPPacingThreshold();

        // Ignore the case of maxBandwidth > 1000, because in this case
        // setMaxPacketsPerMillis fails. Effectively, this means that no
        // pacing is performed when the user deliberately set the setting to
        // over 1000 (1MByte/s according to the GUI). This is probably close
        // to what the user expects, and makes more sense than failing with
        // an exception.
        // TODO: proper handling of maxBandwidth values >1000
        if (maxBandwidth <= 1000)
        {
            // maximum one packet for X milliseconds(the settings are for
            // one second)
            dataOutputStream.setMaxPacketsPerMillis(1, 1000 / maxBandwidth);
        }
    }

    /**
     * Performs any optional configuration on the <tt>BufferControl</tt> of the
     * specified <tt>RTPManager</tt> which is to be used as the
     * <tt>RTPManager</tt> of this <tt>MediaStreamImpl</tt>.
     *
     * @param rtpManager the <tt>RTPManager</tt> which is to be used by this
     * <tt>MediaStreamImpl</tt>
     * @param bufferControl the <tt>BufferControl</tt> of <tt>rtpManager</tt> on
     * which any optional configuration is to be performed
     */
    @Override
    protected void configureRTPManagerBufferControl(
            StreamRTPManager rtpManager,
            BufferControl bufferControl)
    {
        super.configureRTPManagerBufferControl(rtpManager, bufferControl);

        bufferControl.setBufferLength(BufferControl.MAX_VALUE);
    }

    /**
     * Notifies this <tt>MediaStream</tt> that the <tt>MediaDevice</tt> (and
     * respectively the <tt>MediaDeviceSession</tt> with it) which this instance
     * uses for capture and playback of media has been changed. Makes sure that
     * the <tt>VideoListener</tt>s of this instance get <tt>VideoEvent</tt>s for
     * the new/current <tt>VideoMediaDeviceSession</tt> and not for the old one.
     *
     * Note: this overloaded method gets executed in the
     * <tt>MediaStreamImpl</tt> constructor. As a consequence we cannot assume
     * proper initialization of the fields specific to
     * <tt>VideoMediaStreamImpl</tt>.
     *
     * @param oldValue the <tt>MediaDeviceSession</tt> with the
     * <tt>MediaDevice</tt> this instance used work with
     * @param newValue the <tt>MediaDeviceSession</tt> with the
     * <tt>MediaDevice</tt> this instance is to work with
     * @see MediaStreamImpl#deviceSessionChanged(MediaDeviceSession,
     * MediaDeviceSession)
     */
    @Override
    protected void deviceSessionChanged(
            MediaDeviceSession oldValue,
            MediaDeviceSession newValue)
    {
        super.deviceSessionChanged(oldValue, newValue);

        if (oldValue instanceof VideoMediaDeviceSession)
        {
            VideoMediaDeviceSession oldVideoMediaDeviceSession
                = (VideoMediaDeviceSession) oldValue;

            if (deviceSessionVideoListener != null)
                oldVideoMediaDeviceSession.removeVideoListener(
                        deviceSessionVideoListener);

            /*
             * The oldVideoMediaDeviceSession is being disconnected from this
             * VideoMediaStreamImpl so do not let it continue using its
             * keyFrameControl.
             */
            oldVideoMediaDeviceSession.setKeyFrameControl(null);
        }
        if (newValue instanceof VideoMediaDeviceSession)
        {
            VideoMediaDeviceSession newVideoMediaDeviceSession
                = (VideoMediaDeviceSession) newValue;

            if (deviceSessionVideoListener == null)
            {
                deviceSessionVideoListener = new VideoListener()
                {
                    /**
                     * {@inheritDoc}
                     *
                     * Notifies that a visual <tt>Component</tt> depicting video
                     * was reported added by the provider this listener is added
                     * to.
                     */
                    public void videoAdded(VideoEvent e)
                    {
                        if (fireVideoEvent(
                                e.getType(),
                                e.getVisualComponent(),
                                e.getOrigin(),
                                true))
                            e.consume();
                    }

                    /**
                     * {@inheritDoc}
                     *
                     * Notifies that a visual <tt>Component</tt> depicting video
                     * was reported removed by the provider this listener is
                     * added to.
                     */
                    public void videoRemoved(VideoEvent e)
                    {
                        videoAdded(e);
                    }

                    /**
                     * {@inheritDoc}
                     *
                     * Notifies that a visual <tt>Component</tt> depicting video
                     * was reported updated by the provider this listener is
                     * added to.
                     */
                    public void videoUpdate(VideoEvent e)
                    {
                        fireVideoEvent(e, true);
                    }
                };
            }
            newVideoMediaDeviceSession.addVideoListener(
                    deviceSessionVideoListener);

            newVideoMediaDeviceSession.setOutputSize(outputSize);

            AbstractRTPConnector rtpConnector = getRTPConnector();

            if (rtpConnector != null)
                newVideoMediaDeviceSession.setConnector(rtpConnector);
            newVideoMediaDeviceSession.setRtcpFeedbackPLI(USE_PLI);

            /*
             * The newVideoMediaDeviceSession is being connected to this
             * VideoMediaStreamImpl so the key frame-related logic will be
             * controlled by the keyFrameControl of this VideoMediaStreamImpl.
             */
            newVideoMediaDeviceSession.setKeyFrameControl(getKeyFrameControl());
        }
    }

    /**
     * Notifies the <tt>VideoListener</tt>s registered with this
     * <tt>VideoMediaStream</tt> about a specific type of change in the
     * availability of a specific visual <tt>Component</tt> depicting video.
     *
     * @param type the type of change as defined by <tt>VideoEvent</tt> in the
     * availability of the specified visual <tt>Component</tt> depicting video
     * @param visualComponent the visual <tt>Component</tt> depicting video
     * which has been added or removed in this <tt>VideoMediaStream</tt>
     * @param origin {@link VideoEvent#LOCAL} if the origin of the video is
     * local (e.g. it is being locally captured); {@link VideoEvent#REMOTE} if
     * the origin of the video is remote (e.g. a remote peer is streaming it)
     * @param wait <tt>true</tt> if the call is to wait till the specified
     * <tt>VideoEvent</tt> has been delivered to the <tt>VideoListener</tt>s;
     * otherwise, <tt>false</tt>
     * @return <tt>true</tt> if this event and, more specifically, the visual
     * <tt>Component</tt> it describes have been consumed and should be
     * considered owned, referenced (which is important because
     * <tt>Component</tt>s belong to a single <tt>Container</tt> at a time);
     * otherwise, <tt>false</tt>
     */
    protected boolean fireVideoEvent(
            int type, Component visualComponent, int origin,
            boolean wait)
    {
        logger.trace(
                    "Firing VideoEvent with type "
                        + VideoEvent.typeToString(type)
                        + " and origin "
                        + VideoEvent.originToString(origin));

        return
            videoNotifierSupport.fireVideoEvent(
                    type, visualComponent, origin,
                    wait);
    }

    /**
     * Notifies the <tt>VideoListener</tt>s registered with this instance about
     * a specific <tt>VideoEvent</tt>.
     *
     * @param event the <tt>VideoEvent</tt> to be fired to the
     * <tt>VideoListener</tt>s registered with this instance
     * @param wait <tt>true</tt> if the call is to wait till the specified
     * <tt>VideoEvent</tt> has been delivered to the <tt>VideoListener</tt>s;
     * otherwise, <tt>false</tt>
     */
    protected void fireVideoEvent(VideoEvent event, boolean wait)
    {
        videoNotifierSupport.fireVideoEvent(event, wait);
    }

    /**
     * Implements {@link VideoMediaStream#getKeyFrameControl()}.
     *
     * {@inheritDoc}
     * @see VideoMediaStream#getKeyFrameControl()
     */
    public KeyFrameControl getKeyFrameControl()
    {
        if (keyFrameControl == null)
            keyFrameControl = new KeyFrameControlAdapter();
        return keyFrameControl;
    }

    /**
     * Gets the visual <tt>Component</tt>, if any, depicting the video streamed
     * from the local peer to the remote peer.
     *
     * @return the visual <tt>Component</tt> depicting the local video if local
     * video is actually being streamed from the local peer to the remote peer;
     * otherwise, <tt>null</tt>
     */
    public Component getLocalVisualComponent()
    {
        MediaDeviceSession deviceSession = getDeviceSession();

        return
            (deviceSession instanceof VideoMediaDeviceSession)
                ? ((VideoMediaDeviceSession) deviceSession)
                    .getLocalVisualComponent()
                : null;
    }

    /**
     * The priority of the video is 5, which is meant to be higher than
     * other threads and lower than the audio one.
     * @return video priority.
     */
    @Override
    protected int getPriority()
    {
        return 5;
    }

    /**
     * Gets the <tt>QualityControl</tt> of this <tt>VideoMediaStream</tt>.
     *
     * @return the <tt>QualityControl</tt> of this <tt>VideoMediaStream</tt>
     */
    public QualityControl getQualityControl()
    {
        return qualityControl;
    }

    /**
     * Gets the visual <tt>Component</tt> where video from the remote peer is
     * being rendered or <tt>null</tt> if no video is currently being rendered.
     *
     * @return the visual <tt>Component</tt> where video from the remote peer is
     * being rendered or <tt>null</tt> if no video is currently being rendered
     * @see VideoMediaStream#getVisualComponent()
     */
    @Deprecated
    public Component getVisualComponent()
    {
        List<Component> visualComponents = getVisualComponents();

        return visualComponents.isEmpty() ? null : visualComponents.get(0);
    }

    /**
     * Gets the visual <tt>Component</tt>s rendering the <tt>ReceiveStream</tt>
     * corresponding to the given ssrc.
     *
     * @param ssrc the src-id of the receive stream, which visual
     * <tt>Component</tt> we're looking for
     * @return the visual <tt>Component</tt> rendering the
     * <tt>ReceiveStream</tt> corresponding to the given ssrc
     */
    public Component getVisualComponent(long ssrc)
    {
        MediaDeviceSession deviceSession = getDeviceSession();

        return
            (deviceSession instanceof VideoMediaDeviceSession)
                ? ((VideoMediaDeviceSession) deviceSession).getVisualComponent(
                        ssrc)
                : null;
    }

    /**
     * Gets a list of the visual <tt>Component</tt>s where video from the remote
     * peer is being rendered.
     *
     * @return a list of the visual <tt>Component</tt>s where video from the
     * remote peer is being rendered
     * @see VideoMediaStream#getVisualComponents()
     */
    public List<Component> getVisualComponents()
    {
        MediaDeviceSession deviceSession = getDeviceSession();
        List<Component> visualComponents;

        if (deviceSession instanceof VideoMediaDeviceSession)
        {
            visualComponents
                = ((VideoMediaDeviceSession) deviceSession)
                    .getVisualComponents();
        }
        else
            visualComponents = Collections.emptyList();
        return visualComponents;
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
        /*
         * Iterate over the specified attributes and handle those of them which
         * we recognize.
         */
        if(attrs != null)
        {
            /*
             * The width and height attributes are separate but they have to be
             * collected into a Dimension in order to be handled.
             */
            String width = null;
            String height = null;

            for(Map.Entry<String, String> attr : attrs.entrySet())
            {
                String key = attr.getKey();
                String value = attr.getValue();

                switch (key)
                {
                    case MediaUtils.H264_FMT_PROFILE_LEVEL_ID:
                        /*
                         * We've got an H.264 profile level.  That means the remote
                         * device has asked for a specific profile level and we'd
                         * better make sure we don't send video at a higher
                         * resolution than they can cope with.
                         *
                         * Convert the profile provided into a resolution and use
                         * that for this device session.  If we don't get a
                         * resolution back from h264ProfileToDimension(), it means
                         * we should just stick with our default.
                         */
                        logger.debug("Got a " +
                                             MediaUtils.H264_FMT_PROFILE_LEVEL_ID +
                                             " format param: " + value);
                        Dimension res = MediaUtils.h264ProfileToDimension(value);
                        if ((res != null) &&
                                ((outputSize == null) ||
                                        (!outputSize.equals(res))))
                        {
                            setOutputSize(res);
                            ((VideoMediaDeviceSession) getDeviceSession())
                                    .setOutputSize(outputSize);
                        }
                        break;
                    case "CIF":
                    {
                        Dimension dim = new Dimension(352, 288);

                        if ((outputSize == null)
                                || ((outputSize.width < dim.width)
                                && (outputSize.height < dim.height)))
                        {
                            setOutputSize(dim);
                            ((VideoMediaDeviceSession) getDeviceSession())
                                    .setOutputSize(outputSize);
                        }
                        break;
                    }
                    case "QCIF":
                    {
                        Dimension dim = new Dimension(176, 144);

                        if ((outputSize == null)
                                || ((outputSize.width < dim.width)
                                && (outputSize.height < dim.height)))
                        {
                            setOutputSize(dim);
                            ((VideoMediaDeviceSession) getDeviceSession())
                                    .setOutputSize(outputSize);
                        }
                        break;
                    }
                    case "VGA":
// X-Lite sends it.
                    {
                        Dimension dim = new Dimension(640, 480);

                        if ((outputSize == null)
                                || ((outputSize.width < dim.width)
                                && (outputSize.height < dim.height)))
                        {
                            // X-Lite does not display anything if we send 640x480.
                            setOutputSize(dim);
                            ((VideoMediaDeviceSession) getDeviceSession())
                                    .setOutputSize(outputSize);
                        }
                        break;
                    }
                    case "CUSTOM":
                        String args[] = value.split(",");

                        if (args.length < 3)
                            continue;

                        try
                        {
                            Dimension dim
                                    = new Dimension(
                                    Integer.parseInt(args[0]),
                                    Integer.parseInt(args[1]));

                            if ((outputSize == null)
                                    || ((outputSize.width < dim.width)
                                    && (outputSize.height < dim.height)))
                            {
                                setOutputSize(dim);
                                ((VideoMediaDeviceSession) getDeviceSession())
                                        .setOutputSize(outputSize);
                            }
                        }
                        catch (Exception e)
                        {
                        }
                        break;
                    case "width":
                        width = value;
                        if (height != null)
                        {
                            setOutputSize(
                                    new Dimension(
                                            Integer.parseInt(width),
                                            Integer.parseInt(height)));
                            ((VideoMediaDeviceSession) getDeviceSession())
                                    .setOutputSize(outputSize);
                        }
                        break;
                    case "height":
                        height = value;
                        if (width != null)
                        {
                            setOutputSize(
                                    new Dimension(
                                            Integer.parseInt(width),
                                            Integer.parseInt(height)));
                            ((VideoMediaDeviceSession) getDeviceSession())
                                    .setOutputSize(outputSize);
                        }
                        break;
                }
            }
        }
    }

    /**
     * Removes a specific <tt>VideoListener</tt> from this
     * <tt>VideoMediaStream</tt> in order to have to no longer receive
     * notifications when visual/video <tt>Component</tt>s are being added and
     * removed.
     *
     * @param listener the <tt>VideoListener</tt> to no longer be notified when
     * visual/video <tt>Component</tt>s are being added or removed in this
     * <tt>VideoMediaStream</tt>
     */
    public void removeVideoListener(VideoListener listener)
    {
        videoNotifierSupport.removeVideoListener(listener);
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
     * @see MediaStreamImpl#rtpConnectorChanged(AbstractRTPConnector,
     * AbstractRTPConnector)
     */
    @Override
    protected void rtpConnectorChanged(
            AbstractRTPConnector oldValue,
            AbstractRTPConnector newValue)
    {
        super.rtpConnectorChanged(oldValue, newValue);

        if (newValue != null)
        {
            MediaDeviceSession deviceSession = getDeviceSession();

            if (deviceSession instanceof VideoMediaDeviceSession)
            {
                ((VideoMediaDeviceSession) deviceSession)
                    .setConnector(newValue);
            }
        }
    }

    /**
     * Set local SSRC.
     *
     * @param ssrc source ID
     */
    @Override
    protected void setLocalSourceID(long ssrc)
    {
        super.setLocalSourceID(ssrc);

        MediaDeviceSession deviceSession = getDeviceSession();

        if (deviceSession instanceof VideoMediaDeviceSession)
            ((VideoMediaDeviceSession) deviceSession).setLocalSSRC(ssrc);
    }

    /**
     * Sets the size/resolution of the video to be output by this instance.
     *
     * @param outputSize the size/resolution of the video to be output by this
     * instance
     */
    private void setOutputSize(Dimension outputSize)
    {
        this.outputSize = outputSize;
    }

    /**
     * Updates the <tt>QualityControl</tt> of this <tt>VideoMediaStream</tt>.
     *
     * @param advancedParams parameters of advanced attributes that may affect
     * quality control
     */
    public void updateQualityControl(Map<String, String> advancedParams)
    {
        for(Map.Entry<String, String> entry : advancedParams.entrySet())
        {
            if(entry.getKey().equals("imageattr"))
            {
                Dimension res[] = parseSendRecvResolution(entry.getValue());

                if(res != null)
                {
                    qualityControl.setRemoteSendMaxPreset(
                            new QualityPreset(res[0]));
                    qualityControl.setRemoteReceiveResolution(res[1]);
                    setOutputSize(res[1]);
                    ((VideoMediaDeviceSession)getDeviceSession())
                        .setOutputSize(outputSize);
                }
            }
        }
    }
}
