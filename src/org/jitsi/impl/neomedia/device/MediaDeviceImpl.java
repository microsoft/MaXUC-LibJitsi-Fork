/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
// Portions (c) Microsoft Corporation. All rights reserved.
package org.jitsi.impl.neomedia.device;

import java.io.*;
import java.util.*;

import javax.media.*;
import javax.media.control.*;
import javax.media.protocol.*;

import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.format.*;
import org.jitsi.impl.neomedia.jmfext.media.protocol.*;
import org.jitsi.impl.neomedia.protocol.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.codec.*;
import org.jitsi.service.neomedia.device.*;
import org.jitsi.service.neomedia.format.*;
import org.jitsi.util.*;

/**
 * Implements <tt>MediaDevice</tt> for the JMF <tt>CaptureDevice</tt>.
 *
 * @author Lyubomir Marinov
 * @author Emil Ivov
 */
public class MediaDeviceImpl
    extends AbstractMediaDevice
{
    /**
     * The <tt>Logger</tt> used by <tt>MediaDeviceImpl</tt> and its instances
     * for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(MediaDeviceImpl.class);

    /**
     * Creates a new <tt>CaptureDevice</tt> which traces calls to a specific
     * <tt>CaptureDevice</tt> for debugging purposes.
     *
     * @param captureDevice the <tt>CaptureDevice</tt> which is to have its
     * calls traced for debugging output
     * @param logger the <tt>Logger</tt> to be used for logging the trace
     * messages
     * @return a new <tt>CaptureDevice</tt> which traces the calls to the
     * specified <tt>captureDevice</tt>
     */
    public static CaptureDevice createTracingCaptureDevice(
            CaptureDevice captureDevice,
            final Logger logger)
    {
        if (captureDevice instanceof PushBufferDataSource)
            captureDevice
                = new CaptureDeviceDelegatePushBufferDataSource(
                        captureDevice)
                {
                    @Override
                    public void connect()
                        throws IOException
                    {
                        super.connect();

                        logger.trace("Connected "
                                        + MediaDeviceImpl
                                            .toString(this.captureDevice));
                    }

                    @Override
                    public void disconnect()
                    {
                        super.disconnect();

                        logger.trace("Disconnected "
                                        + MediaDeviceImpl
                                            .toString(this.captureDevice));
                    }

                    @Override
                    public void start()
                        throws IOException
                    {
                        super.start();

                        logger.trace("Started "
                                        + MediaDeviceImpl
                                            .toString(this.captureDevice));
                    }

                    @Override
                    public void stop()
                        throws IOException
                    {
                        super.stop();

                        logger.trace("Stopped "
                                        + MediaDeviceImpl
                                            .toString(this.captureDevice));
                    }
                };
        return captureDevice;
    }

    /**
     * Returns a human-readable representation of a specific
     * <tt>CaptureDevice</tt> in the form of a <tt>String</tt> value.
     *
     * @param captureDevice the <tt>CaptureDevice</tt> to get a human-readable
     * representation of
     * @return a <tt>String</tt> value which gives a human-readable
     * representation of the specified <tt>captureDevice</tt>
     */
    private static String toString(CaptureDevice captureDevice)
    {
        StringBuffer str = new StringBuffer();

        str.append("CaptureDevice with hashCode ");
        str.append(captureDevice.hashCode());
        str.append(" and captureDeviceInfo ");

        CaptureDeviceInfo captureDeviceInfo
            = captureDevice.getCaptureDeviceInfo();
        MediaLocator mediaLocator = (captureDeviceInfo == null) ?
                                          null : captureDeviceInfo.getLocator();

        str.append((mediaLocator == null) ? captureDeviceInfo : mediaLocator);
        return str.toString();
    }

    /**
     * The <tt>CaptureDeviceInfo</tt> of the device that this instance is
     * representing.
     */
    private final CaptureDeviceInfo captureDeviceInfo;

    /**
     * The <tt>MediaType</tt> of this instance and the <tt>CaptureDevice</tt>
     * that it wraps.
     */
    private final MediaType mediaType;

    private MediaFormat mediaFormat;

    /**
     * Initializes a new <tt>MediaDeviceImpl</tt> instance which is to provide
     * an implementation of <tt>MediaDevice</tt> for a <tt>CaptureDevice</tt>
     * with a specific <tt>CaptureDeviceInfo</tt> and which is of a specific
     * <tt>MediaType</tt>.
     *
     * @param captureDeviceInfo the <tt>CaptureDeviceInfo</tt> of the JMF
     * <tt>CaptureDevice</tt> the new instance is to provide an implementation
     * of <tt>MediaDevice</tt> for
     * @param mediaType the <tt>MediaType</tt> of the new instance
     */
    public MediaDeviceImpl(
            CaptureDeviceInfo captureDeviceInfo,
            MediaType mediaType)
    {
        if (captureDeviceInfo == null)
            throw new NullPointerException("captureDeviceInfo");
        if (mediaType == null)
            throw new NullPointerException("mediaType");

        this.captureDeviceInfo = captureDeviceInfo;
        this.mediaType = mediaType;
        this.mediaFormat = null;
    }

    /**
     * Initializes a new <tt>MediaDeviceImpl</tt> instance with a specific
     * <tt>MediaType</tt> and with <tt>MediaDirection</tt> which does not allow
     * sending.
     *
     * @param mediaType the <tt>MediaType</tt> of the new instance
     */
    public MediaDeviceImpl(MediaType mediaType)
    {
        this.captureDeviceInfo = null;
        this.mediaType = mediaType;
        this.mediaFormat = null;
    }

    /**
     * Creates the JMF <tt>CaptureDevice</tt> this instance represents and
     * provides an implementation of <tt>MediaDevice</tt> for.
     *
     * @return the JMF <tt>CaptureDevice</tt> this instance represents and
     * provides an implementation of <tt>MediaDevice</tt> for; <tt>null</tt>
     * if the creation fails
     */
    protected CaptureDevice createCaptureDevice()
    {
        CaptureDevice captureDevice = null;

        if (getDirection().allowsSending())
        {
            CaptureDeviceInfo captureDeviceInfo = getCaptureDeviceInfo();
            Throwable exception = null;

            try
            {
                captureDevice
                    = (CaptureDevice)
                        Manager.createDataSource(
                                captureDeviceInfo.getLocator());
            }
            catch (IOException | NoDataSourceException ioe)
            {
                exception = ioe;
            }

            if (exception != null)
            {
                logger.error(
                        "Failed to create CaptureDevice"
                            + " from CaptureDeviceInfo "
                            + captureDeviceInfo,
                        exception);
            }
            else
            {
                if (captureDevice instanceof AbstractPullBufferCaptureDevice)
                {
                    ((AbstractPullBufferCaptureDevice<?>) captureDevice)
                        .setCaptureDeviceInfo(captureDeviceInfo);
                }

                // Try to enable tracing on captureDevice.
                if (logger.isTraceEnabled())
                {
                    captureDevice
                        = createTracingCaptureDevice(captureDevice, logger);
                }
            }
        }
        return captureDevice;
    }

    /**
     * Creates a <tt>DataSource</tt> instance for this <tt>MediaDevice</tt>
     * which gives access to the captured media.
     *
     * @return a <tt>DataSource</tt> instance which gives access to the media
     * captured by this <tt>MediaDevice</tt>
     * @see AbstractMediaDevice#createOutputDataSource()
     */
    @Override
    protected DataSource createOutputDataSource()
    {
        return
            getDirection().allowsSending()
                ? (DataSource) createCaptureDevice()
                : null;
    }

    /**
     * Gets the <tt>CaptureDeviceInfo</tt> of the JMF <tt>CaptureDevice</tt>
     * represented by this instance.
     *
     * @return the <tt>CaptureDeviceInfo</tt> of the <tt>CaptureDevice</tt>
     * represented by this instance
     */
    public CaptureDeviceInfo getCaptureDeviceInfo()
    {
        return captureDeviceInfo;
    }

    /**
     * Gets the protocol of the <tt>MediaLocator</tt> of the
     * <tt>CaptureDeviceInfo</tt> represented by this instance.
     *
     * @return the protocol of the <tt>MediaLocator</tt> of the
     * <tt>CaptureDeviceInfo</tt> represented by this instance
     */
    public String getCaptureDeviceInfoLocatorProtocol()
    {
        CaptureDeviceInfo cdi = getCaptureDeviceInfo();

        if (cdi != null)
        {
            MediaLocator locator = cdi.getLocator();

            if (locator != null)
                return locator.getProtocol();
        }

        return null;
    }

    /**
     * Returns the <tt>MediaDirection</tt> supported by this device.
     *
     * @return {@link MediaDirection#SENDONLY} if this is a read-only device,
     * {@link MediaDirection#RECVONLY} if this is a write-only device or
     * {@link MediaDirection#SENDRECV} if this <tt>MediaDevice</tt> can both
     * capture and render media
     * @see MediaDevice#getDirection()
     */
    @Override
    public MediaDirection getDirection()
    {
        if (getCaptureDeviceInfo() != null)
            return MediaDirection.SENDRECV;
        else
            return
                MediaType.AUDIO.equals(getMediaType())
                    ? MediaDirection.INACTIVE
                    : MediaDirection.RECVONLY;
    }

    /**
     * Gets the <tt>MediaFormat</tt> in which this <tt>MediaDevice</tt> captures
     * media.
     *
     * @return the <tt>MediaFormat</tt> in which this <tt>MediaDevice</tt>
     * captures media
     * @see MediaDevice#getFormat()
     */
    @Override
    public MediaFormat getFormat()
    {
        if (mediaFormat == null)
        {
            logger.debug("Figure out the media format");
            CaptureDevice captureDevice = createCaptureDevice();

            if (captureDevice != null)
            {
                try
                {
                    MediaType mediaType = getMediaType(); // fixed & final

                    for (FormatControl formatControl
                            : captureDevice.getFormatControls())
                    {
                        MediaFormat format
                            = MediaFormatImpl.createInstance(formatControl.getFormat());

                        if ((format != null) && format.getMediaType().equals(mediaType))
                        {
                            mediaFormat = format;
                            break;
                        }
                    }
                }
                finally
                {
                    // We must close the capture device we just opened as we're not
                    // actually using it.
                    try
                    {
                        captureDevice.disconnect();
                    }
                    catch (Exception e)
                    {
                        // just ignore any exceptions trying to clean up
                        logger.debug("Error disconneting captureDevice", e);
                    }
                }
            }
        }
        return mediaFormat;
    }

    /**
     * Gets the <tt>MediaType</tt> that this device supports.
     *
     * @return {@link MediaType#AUDIO} if this is an audio device or
     * {@link MediaType#VIDEO} if this is a video device
     * @see MediaDevice#getMediaType()
     */
    @Override
    public MediaType getMediaType()
    {
        return mediaType;
    }

    /**
     * Gets the list of <tt>MediaFormat</tt>s supported by this
     * <tt>MediaDevice</tt> and enabled in <tt>encodingConfiguration</tt>.
     *
     * @param encodingConfiguration the <tt>EncodingConfiguration</tt> instance
     * to use
     * @return the list of <tt>MediaFormat</tt>s supported by this device
     * and enabled in <tt>encodingConfiguration</tt>.
     * @see MediaDevice#getSupportedFormats()
     */
    public List<MediaFormat> getSupportedFormats(
            EncodingConfiguration encodingConfiguration)
    {
            return getSupportedFormats(null, null, encodingConfiguration);
    }

    /**
     * Gets the list of <tt>MediaFormat</tt>s supported by this
     * <tt>MediaDevice</tt>. Uses the current <tt>EncodingConfiguration</tt>
     * from the media service (i.e. the global configuration).
     *
     * @param sendPreset the preset used to set some of the format parameters,
     * used for video and settings.
     * @param receivePreset the preset used to set the receive format
     * parameters, used for video and settings.
     * @return the list of <tt>MediaFormat</tt>s supported by this device
     * @see MediaDevice#getSupportedFormats()
     */
    @Override
    public List<MediaFormat> getSupportedFormats(
            QualityPreset sendPreset,
            QualityPreset receivePreset)
    {
        return
            getSupportedFormats(
                    sendPreset,
                    receivePreset,
                    NeomediaServiceUtils.getMediaServiceImpl()
                            .getCurrentEncodingConfiguration());
    }

    /**
     * Gets the list of <tt>MediaFormat</tt>s supported by this
     * <tt>MediaDevice</tt> and enabled in <tt>encodingConfiguration</tt>.
     *
     * @param sendPreset the preset used to set some of the format parameters,
     * used for video and settings.
     * @param receivePreset the preset used to set the receive format
     * parameters, used for video and settings.
     * @param encodingConfiguration the <tt>EncodingConfiguration</tt> instance
     * to use
     * @return the list of <tt>MediaFormat</tt>s supported by this device
     * and enabled in <tt>encodingConfiguration</tt>.
     * @see MediaDevice#getSupportedFormats()
     */
    @Override
    public List<MediaFormat> getSupportedFormats(
            QualityPreset sendPreset,
            QualityPreset receivePreset,
            EncodingConfiguration encodingConfiguration)
    {
        MediaServiceImpl mediaServiceImpl
            = NeomediaServiceUtils.getMediaServiceImpl();
        MediaFormat[] enabledEncodings
            = encodingConfiguration.getEnabledEncodings(getMediaType());
        List<MediaFormat> supportedFormats = new ArrayList<>();

        // If there is preset, check and set the format attributes where needed.
        if (enabledEncodings != null)
        {
            for (MediaFormat f : enabledEncodings)
            {
                if("h264".equalsIgnoreCase(f.getEncoding()))
                {
                    Map<String,String> h264AdvancedAttributes
                        = f.getAdvancedAttributes();

                    if (h264AdvancedAttributes == null)
                    {
                        h264AdvancedAttributes = new HashMap<>();
                    }

                    f = mediaServiceImpl.getFormatFactory().createMediaFormat(
                        f.getEncoding(),
                        f.getClockRate(),
                        f.getFormatParameters(),
                        h264AdvancedAttributes);
                }

                if (f != null)
                {
                    supportedFormats.add(f);
                }
            }
        }

        return supportedFormats;
    }

    /**
     * Gets a human-readable <tt>String</tt> representation of this instance.
     *
     * @return a <tt>String</tt> providing a human-readable representation of
     * this instance
     */
    @Override
    public String toString()
    {
        CaptureDeviceInfo captureDeviceInfo = getCaptureDeviceInfo();

        return
            (captureDeviceInfo == null)
                ? super.toString()
                : captureDeviceInfo.toString();
    }
}
