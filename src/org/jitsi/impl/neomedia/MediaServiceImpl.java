/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
// Portions (c) Microsoft Corporation. All rights reserved.
package org.jitsi.impl.neomedia;

import java.awt.*;
import java.awt.event.*;
import java.beans.*;
import java.io.*;
import java.util.*;
import java.util.List;

import javax.media.*;
import javax.media.control.*;
import javax.media.protocol.*;
import javax.swing.*;

import org.jitsi.impl.configuration.*;
import org.jitsi.impl.neomedia.codec.*;
import org.jitsi.impl.neomedia.codec.video.*;
import org.jitsi.impl.neomedia.device.*;
import org.jitsi.impl.neomedia.format.*;
import org.jitsi.impl.neomedia.transform.sdes.*;
import org.jitsi.service.configuration.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.BasicVolumeControl.*;
import org.jitsi.service.neomedia.codec.*;
import org.jitsi.service.neomedia.device.*;
import org.jitsi.service.neomedia.format.*;
import org.jitsi.service.resources.*;
import org.jitsi.util.*;
import org.jitsi.util.event.*;
import org.jitsi.util.swing.*;
import org.json.simple.*;

import com.sun.media.util.*;

/**
 * Implements <tt>MediaService</tt> for JMF.
 *
 * @author Lyubomir Marinov
 * @author Dmitri Melnikov
 */
public class MediaServiceImpl
    extends PropertyChangeNotifier
    implements MediaService
{
    /**
     * The <tt>Logger</tt> used by the <tt>MediaServiceImpl</tt> class and its
     * instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(MediaServiceImpl.class);

    /**
     * The name of the <tt>boolean</tt> <tt>ConfigurationService</tt> property
     * which indicates whether the detection of audio <tt>CaptureDevice</tt>s is
     * to be disabled. The default value is <tt>false</tt> i.e. the audio
     * <tt>CaptureDevice</tt>s are detected.
     */
    public static final String DISABLE_AUDIO_SUPPORT_PNAME
        = "net.java.sip.communicator.service.media.DISABLE_AUDIO_SUPPORT";

    /**
     * The name of the <tt>boolean</tt> <tt>ConfigurationService</tt> property
     * which indicates whether the detection of video <tt>CaptureDevice</tt>s is
     * to be disabled. The default value is <tt>false</tt> i.e. the video
     * <tt>CaptureDevice</tt>s are detected.
     */
    public static final String DISABLE_VIDEO_SUPPORT_PNAME
        = "net.java.sip.communicator.service.media.DISABLE_VIDEO_SUPPORT";

    /**
     * The prefix of the property names the values of which specify the dynamic
     * payload type preferences.
     */
    private static final String DYNAMIC_PAYLOAD_TYPE_PREFERENCES_PNAME_PREFIX
        = "net.java.sip.communicator.impl.neomedia.dynamicPayloadTypePreferences";

    /**
     * The value of the <tt>devices</tt> property of <tt>MediaServiceImpl</tt>
     * when no <tt>MediaDevice</tt>s are available. Explicitly defined in order
     * to reduce unnecessary allocations.
     */
    private static final List<MediaDevice> EMPTY_DEVICES
        = Collections.emptyList();

    /**
     * The name of the <tt>System</tt> boolean property which specifies whether
     * the loading of the JMF/FMJ <tt>Registry</tt> is to be disabled.
     */
    private static final String JMF_REGISTRY_DISABLE_LOAD
        = "net.sf.fmj.utility.JmfRegistry.disableLoad";

    /**
     * The indicator which determines whether the loading of the JMF/FMJ
     * <tt>Registry</tt> is disabled.
     */
    private static boolean jmfRegistryDisableLoad;

    /**
     * The indicator which determined whether
     * {@link #postInitializeOnce(MediaServiceImpl)} has been executed in order
     * to perform one-time initialization after initializing the first instance
     * of <tt>MediaServiceImpl</tt>.
     */
    private static boolean postInitializeOnce;

    /**
     * The prefix that is used to store configuration for encodings preference.
     */
    private static final String ENCODING_CONFIG_PROP_PREFIX
        = "net.java.sip.communicator.impl.neomedia.codec.EncodingConfiguration";

    /**
     * The <tt>CaptureDevice</tt> user choices such as the default audio and
     * video capture devices.
     */
    private final DeviceConfiguration deviceConfiguration
        = new DeviceConfiguration();

    /**
     * The <tt>PropertyChangeListener</tt> which listens to
     * {@link #deviceConfiguration}.
     */
    private final PropertyChangeListener
        deviceConfigurationPropertyChangeListener
            = new PropertyChangeListener()
                    {
                        @Override
                        public void propertyChange(PropertyChangeEvent event)
                        {
                            deviceConfigurationPropertyChange(event);
                        }
                    };

    /**
     * The list of audio <tt>MediaDevice</tt>s reported by this instance when
     * its {@link MediaService#getDevices(MediaType, MediaUseCase)} method is
     * called with an argument {@link MediaType#AUDIO}.
     */
    private final List<MediaDeviceImpl> audioDevices
        = new ArrayList<>();

    /**
     * The {@link EncodingConfiguration} instance that holds the current (global)
     * list of formats and their preference.
     */
    private final EncodingConfiguration currentEncodingConfiguration;

    /**
     * The <tt>MediaFormatFactory</tt> through which <tt>MediaFormat</tt>
     * instances may be created for the purposes of working with the
     * <tt>MediaStream</tt>s created by this <tt>MediaService</tt>.
     */
    private MediaFormatFactory formatFactory;

    /**
     * The one and only <tt>MediaDevice</tt> instance with
     * <tt>MediaDirection</tt> not allowing sending and <tt>MediaType</tt> equal
     * to <tt>AUDIO</tt>.
     */
    private MediaDevice nonSendAudioDevice;

    /**
     * The one and only <tt>MediaDevice</tt> instance with
     * <tt>MediaDirection</tt> not allowing sending and <tt>MediaType</tt> equal
     * to <tt>VIDEO</tt>.
     */
    private MediaDevice nonSendVideoDevice;

    /**
     * The list of video <tt>MediaDevice</tt>s reported by this instance when
     * its {@link MediaService#getDevices(MediaType, MediaUseCase)} method is
     * called with an argument {@link MediaType#VIDEO}.
     */
    private final List<MediaDeviceImpl> videoDevices
        = new ArrayList<>();

    /**
     * A {@link Map} that binds indicates whatever preferences this
     * media service implementation may have for the RTP payload type numbers
     * that get dynamically assigned to {@link MediaFormat}s with no static
     * payload type. The method is useful for formats such as "telephone-event"
     * for example that is statically assigned the 101 payload type by some
     * legacy systems. Signalling protocol implementations such as SIP and XMPP
     * should make sure that, whenever this is possible, they assign to formats
     * the dynamic payload type returned in this {@link Map}.
     */
    private static Map<MediaFormat, Byte> dynamicPayloadTypePreferences;

    /**
     * The control for call volume.
     */
    private static VolumeControl callVolumeControl;

    /**
     * The control for capture volume.
     * */
    private static VolumeControl captureVolumeControl;

    /**
     * The control for playback volume.
     * */
    private static VolumeControl playbackVolumeControl;

    /**
     * The control for notify volume.
     * */
    private static VolumeControl notifyVolumeControl;
    /**
     * Listeners interested in Recorder events without the need to
     * have access to their instances.
     */
    private final List<Recorder.Listener> recorderListeners =
            new ArrayList<>();

    static
    {
        setupFMJ();
    }

    /**
     * Initializes a new <tt>MediaServiceImpl</tt> instance.
     */
    public MediaServiceImpl()
    {
        /*
         * XXX The deviceConfiguration is initialized and referenced by this
         * instance so adding deviceConfigurationPropertyChangeListener does not
         * need a matching removal.
         */
        deviceConfiguration.addPropertyChangeListener(
                deviceConfigurationPropertyChangeListener);

        currentEncodingConfiguration
             = new EncodingConfigurationConfigImpl(ENCODING_CONFIG_PROP_PREFIX);

        /*
         * Perform one-time initialization after initializing the first instance
         * of MediaServiceImpl.
         */
        synchronized (MediaServiceImpl.class)
        {
            if (!postInitializeOnce)
            {
                postInitializeOnce = true;
                postInitializeOnce(this);
            }
        }
    }

    /**
     * Create a <tt>MediaStream</tt> which will use a specific
     * <tt>MediaDevice</tt> for capture and playback of media. The new instance
     * will not have a <tt>StreamConnector</tt> at the time of its construction
     * and a <tt>StreamConnector</tt> will be specified later on in order to
     * enable the new instance to send and receive media.
     *
     * @param device the <tt>MediaDevice</tt> to be used by the new instance for
     * capture and playback of media
     * @return a newly-created <tt>MediaStream</tt> which will use the specified
     * <tt>device</tt> for capture and playback of media
     * @see MediaService#createMediaStream(MediaDevice)
     */
    @Override
    public MediaStream createMediaStream(MediaDevice device)
    {
        return createMediaStream(null, device);
    }

    /**
     * {@inheritDoc}
     *
     * Implements {@link MediaService#createMediaStream(MediaType)}. Initializes
     * a new <tt>AudioMediaStreamImpl</tt> or <tt>VideoMediaStreamImpl</tt> in
     * accord with <tt>mediaType</tt>
     */
    @Override
    public MediaStream createMediaStream(MediaType mediaType)
    {
        return createMediaStream(mediaType, null, null, null);
    }

    /**
     * Creates a new <tt>MediaStream</tt> instance which will use the specified
     * <tt>MediaDevice</tt> for both capture and playback of media exchanged
     * via the specified <tt>StreamConnector</tt>.
     *
     * @param connector the <tt>StreamConnector</tt> that the new
     * <tt>MediaStream</tt> instance is to use for sending and receiving media
     * @param device the <tt>MediaDevice</tt> that the new <tt>MediaStream</tt>
     * instance is to use for both capture and playback of media exchanged via
     * the specified <tt>connector</tt>
     * @return a new <tt>MediaStream</tt> instance
     * @see MediaService#createMediaStream(StreamConnector, MediaDevice)
     */
    @Override
    public MediaStream createMediaStream(
            StreamConnector connector,
            MediaDevice device)
    {
        return createMediaStream(connector, device, null);
    }

    /**
     * Creates a new <tt>MediaStream</tt> instance which will use the specified
     * <tt>MediaDevice</tt> for both capture and playback of media exchanged
     * via the specified <tt>StreamConnector</tt>.
     *
     * @param connector the <tt>StreamConnector</tt> that the new
     * <tt>MediaStream</tt> instance is to use for sending and receiving media
     * @param device the <tt>MediaDevice</tt> that the new <tt>MediaStream</tt>
     * instance is to use for both capture and playback of media exchanged via
     * the specified <tt>connector</tt>
     * @param srtpControl a control which is already created, used to control
     * the SRTP operations.
     *
     * @return a new <tt>MediaStream</tt> instance
     * @see MediaService#createMediaStream(StreamConnector, MediaDevice)
     */
    @Override
    public MediaStream createMediaStream(
            StreamConnector connector,
            MediaDevice device,
            SrtpControl srtpControl)
    {
        return createMediaStream(null, connector, device, srtpControl);
    }

    /**
     * Initializes a new <tt>MediaStream</tt> instance. The method is the actual
     * implementation to which the public <tt>createMediaStream</tt> methods of
     * <tt>MediaServiceImpl</tt> delegate.
     *
     * @param mediaType the <tt>MediaType</tt> of the new <tt>MediaStream</tt>
     * instance to be initialized. If <tt>null</tt>, <tt>device</tt> must be
     * non-<tt>null</tt> and its {@link MediaDevice#getMediaType()} will be used
     * to determine the <tt>MediaType</tt> of the new instance. If
     * non-<tt>null</tt>, <tt>device</tt> may be <tt>null</tt>. If
     * non-<tt>null</tt> and <tt>device</tt> is non-<tt>null</tt>, the
     * <tt>MediaType</tt> of <tt>device</tt> must be (equal to)
     * <tt>mediaType</tt>.
     * @param connector the <tt>StreamConnector</tt> to be used by the new
     * instance if non-<tt>null</tt>
     * @param device the <tt>MediaDevice</tt> to be used by the instance if
     * non-<tt>null</tt>
     * @param srtpControl the <tt>SrtpControl</tt> to be used by the new
     * instance if non-<tt>null</tt>
     * @return a new <tt>MediaStream</tt> instance
     */
    private MediaStream createMediaStream(
            MediaType mediaType,
            StreamConnector connector,
            MediaDevice device,
            SrtpControl srtpControl)
    {
        // Make sure that mediaType and device are in accord.
        if (mediaType == null)
        {
            if (device == null)
                throw new NullPointerException("device");
            else
                mediaType = device.getMediaType();
        }
        else if ((device != null) && !mediaType.equals(device.getMediaType()))
            throw new IllegalArgumentException("device");

        switch (mediaType)
        {
        case AUDIO:
            return new AudioMediaStreamImpl(connector, device, srtpControl);
        case VIDEO:
            return new VideoMediaStreamImpl(connector, device, srtpControl);
        default:
            return null;
        }
    }

    /**
     * Creates a new <tt>MediaDevice</tt> which uses a specific
     * <tt>MediaDevice</tt> to capture and play back media and performs mixing
     * of the captured media and the media played back by any other users of the
     * returned <tt>MediaDevice</tt>. For the <tt>AUDIO</tt> <tt>MediaType</tt>,
     * the returned device is commonly referred to as an audio mixer. The
     * <tt>MediaType</tt> of the returned <tt>MediaDevice</tt> is the same as
     * the <tt>MediaType</tt> of the specified <tt>device</tt>.
     *
     * @throws IllegalArgumentException if mixer cannot be made from device.
     *
     * @param device the <tt>MediaDevice</tt> which is to be used by the
     * returned <tt>MediaDevice</tt> to actually capture and play back media
     * @return a new <tt>MediaDevice</tt> instance which uses <tt>device</tt> to
     * capture and play back media and performs mixing of the captured media and
     * the media played back by any other users of the returned
     * <tt>MediaDevice</tt> instance
     * @see MediaService#createMixer(MediaDevice)
     */
    @Override
    public MediaDevice createMixer(MediaDevice device)
        throws IllegalArgumentException
    {
        switch (device.getMediaType())
        {
        case AUDIO:
            return new AudioMixerMediaDevice((AudioMediaDeviceImpl) device);
        case VIDEO:
            return new VideoTranslatorMediaDevice((MediaDeviceImpl) device);
        default:
            /*
             * TODO If we do not support mixing, should we return null or rather
             * a MediaDevice with INACTIVE MediaDirection?
             */
            return null;
        }
    }

    /**
     * Gets the default <tt>MediaDevice</tt> for the specified
     * <tt>MediaType</tt>.
     *
     * @param mediaType a <tt>MediaType</tt> value indicating the type of media
     * to be handled by the <tt>MediaDevice</tt> to be obtained
     * @param useCase the <tt>MediaUseCase</tt> to obtain the
     * <tt>MediaDevice</tt> list for
     * @return the default <tt>MediaDevice</tt> for the specified
     * <tt>mediaType</tt> if such a <tt>MediaDevice</tt> exists; otherwise,
     * <tt>null</tt>
     * @see MediaService#getDefaultDevice(MediaType, MediaUseCase)
     */
    @Override
    public MediaDevice getDefaultDevice(
            MediaType mediaType,
            MediaUseCase useCase)
    {
        Device captureDeviceInfo;

        switch (mediaType)
        {
        case AUDIO:
            captureDeviceInfo
                = getDeviceConfiguration().getAudioCaptureDevice();
            break;
        case VIDEO:
            captureDeviceInfo
                = getDeviceConfiguration().getVideoCaptureDevice(useCase);
            break;
        default:
            captureDeviceInfo = null;
            break;
        }

        MediaDevice defaultDevice = null;

        if (captureDeviceInfo != null)
        {
            for (MediaDevice device : getDevices(mediaType, useCase))
            {
                if ((device instanceof MediaDeviceImpl)
                        && captureDeviceInfo.equals(
                                ((MediaDeviceImpl) device)
                                    .getCaptureDeviceInfo()))
                {
                    defaultDevice = device;
                    break;
                }
            }
        }
        if (defaultDevice == null)
        {
            switch (mediaType)
            {
            case AUDIO:
                defaultDevice = getNonSendAudioDevice();
                break;
            case VIDEO:
                defaultDevice = getNonSendVideoDevice();
                break;
            default:
                /*
                 * There is no MediaDevice with direction which does not allow
                 * sending and mediaType other than AUDIO and VIDEO.
                 */
                break;
            }
        }

        return defaultDevice;
    }

    @Override
    public DeviceConfiguration getDeviceConfiguration()
    {
        return deviceConfiguration;
    }

    /**
     * Gets a list of the <tt>MediaDevice</tt>s known to this
     * <tt>MediaService</tt> and handling the specified <tt>MediaType</tt>.
     *
     * @param mediaType the <tt>MediaType</tt> to obtain the
     * <tt>MediaDevice</tt> list for
     * @param useCase the <tt>MediaUseCase</tt> to obtain the
     * <tt>MediaDevice</tt> list for
     * @return a new <tt>List</tt> of <tt>MediaDevice</tt>s known to this
     * <tt>MediaService</tt> and handling the specified <tt>MediaType</tt>. The
     * returned <tt>List</tt> is a copy of the internal storage and,
     * consequently, modifications to it do not affect this instance. Despite
     * the fact that a new <tt>List</tt> instance is returned by each call to
     * this method, the <tt>MediaDevice</tt> instances are the same if they are
     * still known to this <tt>MediaService</tt> to be available.
     * @see MediaService#getDevices(MediaType, MediaUseCase)
     */
    @Override
    public List<MediaDevice> getDevices(
            MediaType mediaType,
            MediaUseCase useCase)
    {
        List<? extends CaptureDeviceInfo> cdis;
        List<MediaDeviceImpl> privateDevices;

        if (MediaType.VIDEO.equals(mediaType))
        {
            /*
             * In case a video capture device has been added to or removed from
             * system (i.e. webcam, monitor, etc.), rescan the video capture
             * devices.
             */
            DeviceSystemManager.initializeVideoDeviceSystems();
        }

        switch (mediaType)
        {
        case AUDIO:
            cdis = getDeviceConfiguration().getAvailableAudioCaptureDevices();
            privateDevices = audioDevices;
            break;
        case VIDEO:
            cdis = getDeviceConfiguration().getAvailableVideoCaptureDevices(
                        useCase);
            privateDevices = videoDevices;
            break;
        default:
            /*
             * MediaService does not understand MediaTypes other than AUDIO and
             * VIDEO.
             */
            return EMPTY_DEVICES;
        }

        List<MediaDevice> publicDevices;

        synchronized (privateDevices)
        {
            if ((cdis == null)
                    || (cdis.size() <= 0))
                privateDevices.clear();
            else
            {
                Iterator<MediaDeviceImpl> deviceIter
                    = privateDevices.iterator();

                while (deviceIter.hasNext())
                {
                    Iterator<? extends CaptureDeviceInfo> cdiIter
                        = cdis.iterator();
                    CaptureDeviceInfo captureDeviceInfo
                        = deviceIter.next().getCaptureDeviceInfo();
                    boolean deviceIsFound = false;

                    while (cdiIter.hasNext())
                    {
                        if (captureDeviceInfo.equals(cdiIter.next()))
                        {
                            deviceIsFound = true;
                            cdiIter.remove();
                            break;
                        }
                    }
                    if (!deviceIsFound)
                        deviceIter.remove();
                }

                for (CaptureDeviceInfo cdi : cdis)
                {
                    if (cdi == null)
                        continue;

                    MediaDeviceImpl device;

                    switch (mediaType)
                    {
                    case AUDIO:
                        device = new AudioMediaDeviceImpl(cdi);
                        break;
                    case VIDEO:
                        device
                            = new MediaDeviceImpl(cdi, mediaType);
                        break;
                    default:
                        device = null;
                        break;
                    }
                    if (device != null)
                        privateDevices.add(device);
                }
            }

            publicDevices = new ArrayList<>(privateDevices);
        }

        /*
         * If there are no MediaDevice instances of the specified mediaType,
         * make sure that there is at least one MediaDevice which does not allow
         * sending.
         */
        if (publicDevices.isEmpty())
        {
            MediaDevice nonSendDevice;

            switch (mediaType)
            {
            case AUDIO:
                nonSendDevice = getNonSendAudioDevice();
                break;
            case VIDEO:
                nonSendDevice = getNonSendVideoDevice();
                break;
            default:
                /*
                 * There is no MediaDevice with direction not allowing sending
                 * and mediaType other than AUDIO and VIDEO.
                 */
                nonSendDevice = null;
                break;
            }
            if (nonSendDevice != null)
                publicDevices.add(nonSendDevice);
        }

        return publicDevices;
    }

    /**
     * Returns the current encoding configuration -- the instance that contains
     * the global settings. Note that any changes made to this instance will
     * have immediate effect on the configuration.
     *
     * @return the current encoding configuration -- the instance that contains
     * the global settings.
     */
    @Override
    public EncodingConfiguration getCurrentEncodingConfiguration()
    {
        return currentEncodingConfiguration;
    }

    /**
     * Gets the <tt>MediaFormatFactory</tt> through which <tt>MediaFormat</tt>
     * instances may be created for the purposes of working with the
     * <tt>MediaStream</tt>s created by this <tt>MediaService</tt>.
     *
     * @return the <tt>MediaFormatFactory</tt> through which
     * <tt>MediaFormat</tt> instances may be created for the purposes of working
     * with the <tt>MediaStream</tt>s created by this <tt>MediaService</tt>
     * @see MediaService#getFormatFactory()
     */
    @Override
    public MediaFormatFactory getFormatFactory()
    {
        if (formatFactory == null)
            formatFactory = new MediaFormatFactoryImpl();
        return formatFactory;
    }

    /**
     * Gets the one and only <tt>MediaDevice</tt> instance with
     * <tt>MediaDirection</tt> not allowing sending and <tt>MediaType</tt> equal
     * to <tt>AUDIO</tt>.
     *
     * @return the one and only <tt>MediaDevice</tt> instance with
     * <tt>MediaDirection</tt> not allowing sending and <tt>MediaType</tt> equal
     * to <tt>AUDIO</tt>
     */
    private MediaDevice getNonSendAudioDevice()
    {
        if (nonSendAudioDevice == null)
            nonSendAudioDevice = new AudioMediaDeviceImpl();
        return nonSendAudioDevice;
    }

    /**
     * Gets the one and only <tt>MediaDevice</tt> instance with
     * <tt>MediaDirection</tt> not allowing sending and <tt>MediaType</tt> equal
     * to <tt>VIDEO</tt>.
     *
     * @return the one and only <tt>MediaDevice</tt> instance with
     * <tt>MediaDirection</tt> not allowing sending and <tt>MediaType</tt> equal
     * to <tt>VIDEO</tt>
     */
    private MediaDevice getNonSendVideoDevice()
    {
        if (nonSendVideoDevice == null)
            nonSendVideoDevice = new MediaDeviceImpl(MediaType.VIDEO);
        return nonSendVideoDevice;
    }

    /**
     * Initializes a new <tt>SDesControl</tt> instance which is to control all
     * SDes options.
     *
     * @return a new <tt>SDesControl</tt> instance which is to control all SDes
     * options
     */
    @Override
    public SDesControl createSDesControl()
    {
        return new SDesControlImpl();
    }

    /**
     * Gets the <tt>VolumeControl</tt> which controls the volume level of audio
     * output/playback in calls.
     *
     * @return the <tt>VolumeControl</tt> which controls the volume level of
     * audio output/playback in calls
     */
    @Override
    public VolumeControl getCallVolumeControl()
    {
        if (callVolumeControl == null)
        {
            callVolumeControl = new BasicVolumeControl(
                VolumeControl.CALL_VOLUME_LEVEL_PROPERTY_NAME, Mode.OUTPUT);
        }
        return callVolumeControl;
    }

    /**
     * Gets the <tt>VolumeControl</tt> which controls the volume level of the
     * audio capture device.
     *
     * @return the <tt>VolumeControl</tt> which controls the volume level of
     * the audio capture device
     */
    @Override
    public VolumeControl getCaptureVolumeControl()
    {
        if (captureVolumeControl == null)
        {
            // If available, use hardware.
            try
            {
                captureVolumeControl
                    = new HardwareVolumeControl(
                            this,
                            DataFlow.CAPTURE,
                            VolumeControl.CAPTURE_VOLUME_LEVEL_PROPERTY_NAME);
            }
            catch (Throwable t)
            {
                if (t instanceof ThreadDeath)
                    throw (ThreadDeath) t;
                else if (t instanceof InterruptedException)
                    Thread.currentThread().interrupt();

                logger.error("Unable to create hardware volume control ", t);
            }
            // Otherwise, use software.
            if (captureVolumeControl == null)
            {
                captureVolumeControl = new BasicVolumeControl(
                    VolumeControl.CAPTURE_VOLUME_LEVEL_PROPERTY_NAME, Mode.INPUT);
            }
        }
        return captureVolumeControl;
    }

    /**
     * Gets the <tt>VolumeControl</tt> which controls the volume level of the
     * audio playback device.
     *
     * @return the <tt>VolumeControl</tt> which controls the volume level of
     * the audio playback device
     */
    @Override
    public VolumeControl getPlaybackVolumeControl()
    {
        if (playbackVolumeControl == null)
        {
            try
            {
                playbackVolumeControl
                    = new HardwareVolumeControl(
                            this,
                            DataFlow.PLAYBACK,
                            VolumeControl.PLAYBACK_VOLUME_LEVEL_PROPERTY_NAME);
            }
            catch (Throwable t)
            {
                if (t instanceof ThreadDeath)
                    throw (ThreadDeath) t;
                else if (t instanceof InterruptedException)
                    Thread.currentThread().interrupt();
            }
        }
        return playbackVolumeControl;
    }

    /**
     * Gets the <tt>VolumeControl</tt> which controls the volume level of the
     * audio notify device.
     *
     * @return the <tt>VolumeControl</tt> which controls the volume level of
     * the audio notify device
     */
    @Override
    public VolumeControl getNotifyVolumeControl()
    {
        if (notifyVolumeControl == null)
        {
            try
            {
                notifyVolumeControl
                    = new HardwareVolumeControl(
                            this,
                            DataFlow.NOTIFY,
                            VolumeControl.NOTIFY_VOLUME_LEVEL_PROPERTY_NAME);
            }
            catch (Throwable t)
            {
                if (t instanceof ThreadDeath)
                    throw (ThreadDeath) t;
                else if (t instanceof InterruptedException)
                    Thread.currentThread().interrupt();
            }
        }
        return notifyVolumeControl;
    }
    /**
     * Get available screens.
     *
     * @return screens
     */
    @Override
    public List<ScreenDevice> getAvailableScreenDevices()
    {
        ScreenDevice[] screens = ScreenDeviceImpl.getAvailableScreenDevices();
        List<ScreenDevice> screenList;

        if ((screens != null) && (screens.length != 0))
            screenList = new ArrayList<>(Arrays.asList(screens));
        else
            screenList = Collections.emptyList();
        return screenList;
    }

    /**
     * Get default screen device.
     *
     * @return default screen device
     */
    @Override
    public ScreenDevice getDefaultScreenDevice()
    {
        return ScreenDeviceImpl.getDefaultScreenDevice();
    }

    /**
     * Creates a new <tt>Recorder</tt> instance that can be used to record a
     * call which captures and plays back media using a specific
     * <tt>MediaDevice</tt>.
     *
     * @param device the <tt>MediaDevice</tt> which is used for media capture
     * and playback by the call to be recorded
     * @return a new <tt>Recorder</tt> instance that can be used to record a
     * call which captures and plays back media using the specified
     * <tt>MediaDevice</tt>
     * @see MediaService#createRecorder(MediaDevice)
     */
    @Override
    public Recorder createRecorder(MediaDevice device)
    {
        if (device instanceof AudioMixerMediaDevice)
            return new RecorderImpl((AudioMixerMediaDevice) device);
        else
            return null;
    }

    /**
     * Returns a {@link Map} that binds indicates whatever preferences this
     * media service implementation may have for the RTP payload type numbers
     * that get dynamically assigned to {@link MediaFormat}s with no static
     * payload type. The method is useful for formats such as "telephone-event"
     * for example that is statically assigned the 101 payload type by some
     * legacy systems. Signaling protocol implementations such as SIP and XMPP
     * should make sure that, whenever this is possible, they assign to formats
     * the dynamic payload type returned in this {@link Map}.
     *
     * @return a {@link Map} binding some formats to a preferred dynamic RTP
     * payload type number.
     */
    @Override
    public Map<MediaFormat, Byte> getDynamicPayloadTypePreferences()
    {
        if(dynamicPayloadTypePreferences == null)
        {
            dynamicPayloadTypePreferences = new HashMap<>();

            /*
             * Set the dynamicPayloadTypePreferences to their default values. If
             * the user chooses to override them through the
             * ConfigurationService, they will be overwritten later on.
             */
            MediaFormat telephoneEvent
                = MediaUtils.getMediaFormat("telephone-event", 8000);
            if (telephoneEvent != null)
                dynamicPayloadTypePreferences.put(telephoneEvent, (byte) 101);

            MediaFormat h264
                = MediaUtils.getMediaFormat(
                        "H264",
                        VideoMediaFormatImpl.DEFAULT_CLOCK_RATE);
            if (h264 != null)
                dynamicPayloadTypePreferences.put(h264, (byte) 99);

            /*
             * Try to load dynamicPayloadTypePreferences from the
             * ConfigurationService.
             */
            ConfigurationService cfg = LibJitsi.getConfigurationService();

            if (cfg != null)
            {
                String prefix = DYNAMIC_PAYLOAD_TYPE_PREFERENCES_PNAME_PREFIX;
                List<String> propertyNames
                    = cfg.global().getPropertyNamesByPrefix(prefix, true);

                for (String propertyName : propertyNames)
                {
                    /*
                     * The dynamic payload type is the name of the property name
                     * and the format which prefers it is the property value.
                     */
                    byte dynamicPayloadTypePreference = 0;
                    Throwable exception = null;

                    try
                    {
                        dynamicPayloadTypePreference
                            = Byte.parseByte(
                                    propertyName.substring(
                                            prefix.length() + 1));
                    }
                    catch (IndexOutOfBoundsException | NumberFormatException ioobe)
                    {
                        exception = ioobe;
                    }
                    if (exception != null)
                    {
                        logger.warn(
                                "Ignoring dynamic payload type preference"
                                    + " which could not be parsed: "
                                    + propertyName,
                                exception);
                        continue;
                    }

                    String source = cfg.global().getString(propertyName);

                    if ((source != null) && (source.length() != 0))
                    {
                        try
                        {
                            JSONObject json = (JSONObject)JSONValue
                                .parseWithException(source);
                            String encoding
                                = (String)json.get(
                                        MediaFormatImpl.ENCODING_PNAME);
                            long clockRate = (Long)json.get(
                                MediaFormatImpl.CLOCK_RATE_PNAME);
                            Map<String, String> fmtps
                                = new HashMap<>();

                            if (json.containsKey(
                                    MediaFormatImpl.FORMAT_PARAMETERS_PNAME))
                            {
                                JSONObject jsonFmtps
                                    = (JSONObject)json.get(
                                            MediaFormatImpl
                                                .FORMAT_PARAMETERS_PNAME);
                                Iterator<?> jsonFmtpsIter
                                    = jsonFmtps.keySet().iterator();

                                while (jsonFmtpsIter.hasNext())
                                {
                                    String key
                                        = jsonFmtpsIter.next().toString();
                                    String value = (String)jsonFmtps.get(key);

                                    fmtps.put(key, value);
                                }
                            }

                            MediaFormat mediaFormat
                                = MediaUtils.getMediaFormat(
                                        encoding, clockRate,
                                        fmtps);

                            if (mediaFormat != null)
                            {
                                dynamicPayloadTypePreferences.put(
                                        mediaFormat,
                                        dynamicPayloadTypePreference);
                            }
                        }
                        catch (Throwable jsone)
                        {
                            logger.warn(
                                    "Ignoring dynamic payload type preference"
                                        + " which could not be parsed: "
                                        + source,
                                    jsone);
                        }
                    }
                }
            }
        }
        return dynamicPayloadTypePreferences;
    }

    /**
     * Creates a preview component for the specified video device used to show
     * video preview from that device.
     *
     * @param device the video device
     * @param preferredWidth the width we prefer for the component
     * @param preferredHeight the height we prefer for the component
     * @return the preview component.
     */
    @Override
    public Object getVideoPreviewComponent(
            MediaDevice device,
            final int preferredWidth,
            final int preferredHeight)
    {
        ResourceManagementService resources
            = LibJitsi.getResourceManagementService();

        JLabel noVideoAvailableLabel = new JLabel(
              (resources == null)
                ? ""
                : resources.getI18NString("impl.media.configform.NO_PREVIEW"));

        noVideoAvailableLabel.setHorizontalAlignment(SwingConstants.CENTER);
        noVideoAvailableLabel.setVerticalAlignment(SwingConstants.CENTER);

        final JComponent videoContainer = new VideoContainer(noVideoAvailableLabel);

        if ((preferredWidth > 0) && (preferredHeight > 0))
        {
            videoContainer.setPreferredSize(
                    new Dimension(preferredWidth, preferredHeight));
        }

        CaptureDeviceInfo captureDeviceInfo;

        if ((device != null) &&
                ((captureDeviceInfo
                            = ((MediaDeviceImpl) device)
                                .getCaptureDeviceInfo())
                        != null))
        {
            // We need to invokeLater so that the UI is updated immediately,
            // otherwise the delay below holds up refreshing the UI.
            SwingUtilities.invokeLater(new Runnable()
            {
                public void run()
                {
                    try
                    {
                        // This fix doesn't work on Mac (after changing camera,
                        // NO preview is shown).  But it isn't required either
                        // as Mac doesn't exhibit the problem it solves.
                        if (OSUtils.IS_WINDOWS)
                        {
                            // Delay creating the DataSource to allow for any previous
                            // DataSource from a previous preview to be disconnected.
                            // Without this fix we often see a failure to display the new
                            // preview with a "Your webcam is unavailable" popup.
                            logger.debug("Delay creating data source");
                            Thread.sleep(500);
                            logger.debug("Delayed creating data source");
                        }

                        DataSource dataSource
                            = Manager.createDataSource(captureDeviceInfo.getLocator());

                        /*
                         * Don't let the size be uselessly small just because the
                         * videoContainer has too small a preferred size.
                         */
                        int usePreferredWidth = preferredWidth;
                        int usePreferredHeight = preferredHeight;

                        if ((preferredWidth < 128) || (preferredHeight < 96))
                        {
                            usePreferredWidth = 128;
                            usePreferredHeight = 96;
                        }

                        VideoMediaStreamImpl.selectVideoSize(
                                dataSource,
                                usePreferredWidth, usePreferredHeight);

                        Processor player = Manager.createProcessor(dataSource);
                        final VideoContainerHierarchyListener listener =
                                new VideoContainerHierarchyListener(
                                        videoContainer, player);
                        videoContainer.addHierarchyListener(listener);

                        player.addControllerListener(new ControllerListener()
                        {
                            @Override
                            public void controllerUpdate(ControllerEvent event)
                            {
                                controllerUpdateForPreview(
                                        event,
                                        videoContainer,
                                        listener);
                            }
                        });
                        player.configure();
                    }
                    catch (Throwable t)
                    {
                        if (t instanceof ThreadDeath)
                            throw (ThreadDeath) t;
                        else
                            logger.error("Failed to create video preview", t);
                    }
                }
            });
        }

        return videoContainer;
    }

    /**
     * Listens and shows the video in the video container when needed.
     * @param event the event when player has ready visual component.
     * @param videoContainer the container.
     * @param listener the hierarchy listener we created for the video container.
     */
    private static void controllerUpdateForPreview(
            ControllerEvent event,
            JComponent videoContainer,
            VideoContainerHierarchyListener listener)
    {
        if (event instanceof ConfigureCompleteEvent)
        {
            Processor player = (Processor) event.getSourceController();

            /*
             * Use SwScale for the scaling since it produces an image with
             * better quality and add the "flip" effect to the video.
             */
            TrackControl[] trackControls = player.getTrackControls();

            if ((trackControls != null) && (trackControls.length != 0))
                try
                {
                    for (TrackControl trackControl : trackControls)
                    {
                        trackControl.setCodecChain(
                            new Codec[] { new HFlip(), new SwScale() });
                        break;
                    }
                }
                catch (UnsupportedPlugInException upiex)
                {
                    logger.warn(
                            "Failed to add SwScale/VideoFlipEffect to " +
                            "codec chain", upiex);
                }

            // Turn the Processor into a Player.
            try
            {
                player.setContentDescriptor(null);
            }
            catch (NotConfiguredError nce)
            {
                logger.error(
                    "Failed to set ContentDescriptor of Processor",
                    nce);
            }

            player.realize();
        }
        else if (event instanceof RealizeCompleteEvent)
        {
            Player player = (Player) event.getSourceController();
            Component video = player.getVisualComponent();

            // sets the preview to the listener
            listener.setPreview(video);
            showPreview(videoContainer, video, player);
        }
    }

    /**
     * Shows the preview panel.
     * @param previewContainer the container
     * @param preview the preview component.
     * @param player the player.
     */
    private static void showPreview(
            final JComponent previewContainer,
            final Component preview,
            final Player player)
    {
        if (!SwingUtilities.isEventDispatchThread())
        {
            SwingUtilities.invokeLater(new Runnable()
            {
                @Override
                public void run()
                {
                    showPreview(previewContainer, preview, player);
                }
            });
            return;
        }

        previewContainer.removeAll();

        if (preview != null)
        {
            previewContainer.add(preview);
            player.start();

            if (previewContainer.isDisplayable())
            {
                previewContainer.revalidate();
                previewContainer.repaint();
            }
            else
                previewContainer.doLayout();
        }
        else
        {
            disposePlayer(player);
        }
    }

    /**
     * Dispose the player used for the preview.
     * @param player the player.
     */
    private static void disposePlayer(final Player player)
    {
        // launch disposing preview player in separate thread
        // will lock renderer and can produce lock if user has quickly
        // requested preview component and can lock ui thread
        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                player.stop();
                player.deallocate();
                player.close();
            }
        }, "DisposePlayerThread").start();
    }

    /**
     * Gives access to currently registered <tt>Recorder.Listener</tt>s.
     * @return currently registered <tt>Recorder.Listener</tt>s.
     */
    @Override
    public Iterator<Recorder.Listener> getRecorderListeners()
    {
        return recorderListeners.iterator();
    }

    /**
     * Notifies this instance that the value of a property of
     * {@link #deviceConfiguration} has changed.
     *
     * @param event a <tt>PropertyChangeEvent</tt> which specifies the name of
     * the property which had its value changed and the old and the new values
     * of that property
     */
    private void deviceConfigurationPropertyChange(PropertyChangeEvent event)
    {
        String propertyName = event.getPropertyName();

        /*
         * While AUDIO_CAPTURE_DEVICE is sure to affect the DEFAULT_DEVICE,
         * AUDIO_PLAYBACK_DEVICE is not. Anyway, MediaDevice is supposed to
         * represent the device to be used for capture AND playback (though its
         * current implementation MediaDeviceImpl may be incomplete with respect
         * to the playback representation). Since it is not clear at this point
         * of the execution whether AUDIO_PLAYBACK_DEVICE really affects the
         * DEFAULT_DEVICE and for the sake of completeness, throw in the changes
         * to the AUDIO_NOTIFY_DEVICE as well.
         */
        if (DeviceConfiguration.AUDIO_CAPTURE_DEVICE.equals(propertyName)
                || DeviceConfiguration.AUDIO_NOTIFY_DEVICE.equals(propertyName)
                || DeviceConfiguration.AUDIO_PLAYBACK_DEVICE.equals(
                        propertyName)
                || DeviceConfiguration.VIDEO_CAPTURE_DEVICE.equals(
                        propertyName))
        {
            /*
             * We do not know the old value of the property at the time of this
             * writing. We cannot report the new value either because we do not
             * know the MediaType and the MediaUseCase.
             */
            firePropertyChange(DEFAULT_DEVICE, null, null);
        }
    }

    /**
     * Initializes a new <tt>RTPTranslator</tt> which is to forward RTP and RTCP
     * traffic between multiple <tt>MediaStream</tt>s.
     *
     * @return a new <tt>RTPTranslator</tt> which is to forward RTP and RTCP
     * traffic between multiple <tt>MediaStream</tt>s
     * @see MediaService#createRTPTranslator()
     */
    @Override
    public RTPTranslator createRTPTranslator()
    {
        return new RTPTranslatorImpl();
    }

    /**
     * Gets the indicator which determines whether the loading of the JMF/FMJ
     * <tt>Registry</tt> has been disabled.
     *
     * @return <tt>true</tt> if the loading of the JMF/FMJ <tt>Registry</tt> has
     * been disabled; otherwise, <tt>false</tt>
     */
    public static boolean isJmfRegistryDisableLoad()
    {
        return jmfRegistryDisableLoad;
    }

    /**
     * Performs one-time initialization after initializing the first instance of
     * <tt>MediaServiceImpl</tt>.
     *
     * @param mediaServiceImpl the <tt>MediaServiceImpl</tt> instance which has
     * caused the need to perform the one-time initialization
     */
    private static void postInitializeOnce(MediaServiceImpl mediaServiceImpl)
    {
//        new ZrtpFortunaEntropyGatherer(
//                mediaServiceImpl.getDeviceConfiguration())
//            .setEntropy();
    }

    /**
     * Sets up FMJ for execution. For example, sets properties which instruct
     * FMJ whether it is to create a log, where the log is to be created.
     */
    private static void setupFMJ()
    {
        /*
         * FMJ now uses java.util.logging.Logger, but only logs if
         * "allowLogging" is set in it's registry. Since the levels can be
         * configured through properties for the net.sf.fmj.media.Log class,
         * we always enable this (as opposed to only enabling it when
         * <tt>this.logger</tt> has debug enabled).
         */
        Registry.set("allowLogging", true);

        /*
         * Disable the loading of .fmj.registry because Kertesz Laszlo has
         * reported that audio input devices duplicate after restarting Jitsi.
         * Besides, Jitsi does not really need .fmj.registry on startup.
         */
        if (System.getProperty(JMF_REGISTRY_DISABLE_LOAD) == null)
            System.setProperty(JMF_REGISTRY_DISABLE_LOAD, "true");
        jmfRegistryDisableLoad
            = "true".equalsIgnoreCase(System.getProperty(
                    JMF_REGISTRY_DISABLE_LOAD));

        String scHomeDirLocation
            = System.getProperty(
                AbstractScopedConfigurationServiceImpl.PNAME_SC_HOME_DIR_LOCATION);

        if (scHomeDirLocation != null)
        {
            String scHomeDirName
                = System.getProperty(
                    AbstractScopedConfigurationServiceImpl.PNAME_SC_HOME_DIR_NAME);

            if (scHomeDirName != null)
            {
                File scHomeDir = new File(scHomeDirLocation, scHomeDirName);

                /* Write FMJ's log in Jitsi's log directory. */
                Registry.set(
                    "secure.logDir",
                    new File(scHomeDir, "log").getPath());

                /* Write FMJ's registry in Jitsi's user data directory. */
                String jmfRegistryFilename
                    = "net.sf.fmj.utility.JmfRegistry.filename";

                if (System.getProperty(jmfRegistryFilename) == null)
                {
                    System.setProperty(
                        jmfRegistryFilename,
                        new File(scHomeDir, ".fmj.registry").getAbsolutePath());
                }
            }
        }

        ConfigurationService cfg = LibJitsi.getConfigurationService();
        for(String prop : cfg.global().getPropertyNamesByPrefix("net.java.sip."
                + "communicator.impl.neomedia.adaptive_jitter_buffer", true))
        {
            String suffix
                    = prop.substring(prop.lastIndexOf(".") + 1 ,prop.length());
            Registry.set("adaptive_jitter_buffer_" + suffix, cfg.global().getString(prop));
        }

        String jitterBufferPrefix = "net.java.sip.communicator.impl.neomedia.jitterbuffer";
        for(String prop : cfg.global().getPropertyNamesByPrefix(jitterBufferPrefix, false))
        {
            String suffix = prop.substring(jitterBufferPrefix.length() +1, prop.length());
            Registry.set("jitterbuffer_" + suffix, cfg.global().getString(prop));
        }

        FMJPlugInConfiguration.registerCustomPackages();
        FMJPlugInConfiguration.registerCustomCodecs();
    }

    /**
     * Returns a new {@link EncodingConfiguration} instance that can be
     * used by other bundles.
     *
     * @return a new {@link EncodingConfiguration} instance.
     */
    @Override
    public EncodingConfiguration createEmptyEncodingConfiguration()
    {
        return new EncodingConfigurationImpl();
    }

    /**
     * The listener which will be notified for changes in the video container.
     * Whether the container is displayable or not we will stop the player
     * or start it.
     */
    private class VideoContainerHierarchyListener
        implements HierarchyListener
    {
        /**
         * The parent window.
         */
        private Window window;

        /**
         * The listener for the parent window. Used to dispose player on close.
         */
        private WindowListener windowListener;

        /**
         * The parent container of our preview.
         */
        private JComponent container;

        /**
         * The player showing the video preview.
         */
        private Player player;

        /**
         * The preview component of the player, must be set once the
         * player has been realized.
         */
        private Component preview = null;

        /**
         * Creates VideoContainerHierarchyListener.
         * @param container the video container.
         * @param player the player.
         */
        VideoContainerHierarchyListener(JComponent container, Player player)
        {
            this.container = container;
            this.player = player;
        }

        /**
         * After the player has been realized the preview can be obtained and supplied
         * to this listener. Normally done on player RealizeCompleteEvent.
         * @param preview the preview.
         */
        void setPreview(Component preview)
        {
            this.preview = preview;
        }

        /**
         * Disposes player and cleans listeners as we will no longer need them.
         */
        public void dispose()
        {
            if (windowListener != null)
            {
                if (window != null)
                {
                    window.removeWindowListener(windowListener);
                    window = null;
                }
                windowListener = null;
            }
            container.removeHierarchyListener(this);

            disposePlayer(player);

            /*
             * We've just disposed the player which created the preview
             * component so the preview component is of no use
             * regardless of whether the Media configuration form will
             * be redisplayed or not. And since the preview component
             * appears to be a huge object even after its player is
             * disposed, make sure to not reference it.
             */
            if(preview != null)
                container.remove(preview);
        }

        /**
         * Change in container.
         * @param event the event for the chnage.
         */
        @Override
        public void hierarchyChanged(HierarchyEvent event)
        {
            if ((event.getChangeFlags()
                    & HierarchyEvent.DISPLAYABILITY_CHANGED)
                    == 0)
                return;

            if (!container.isDisplayable())
            {
                dispose();
                return;
            }
            else
            {
                // if this is just a change in the video container
                // and preview has not been created yet, do nothing
                // otherwise start the player which will show in preview
                if(preview != null)
                {
                    player.start();
                }
            }

            if (windowListener == null)
            {
                window = SwingUtilities.windowForComponent(container);
                if (window != null)
                {
                    windowListener = new WindowAdapter()
                    {
                        @Override
                        public void windowClosing(WindowEvent event)
                        {
                            dispose();
                        }
                    };
                    window.addWindowListener(windowListener);
                }
            }
        }
    }
}
