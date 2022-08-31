/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.device;

import java.awt.*;
import java.beans.*;
import java.io.*;
import java.util.*;
import java.util.List;

import javax.media.*;
import javax.media.format.*;

import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.codec.video.*;
import org.jitsi.service.configuration.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.codec.*;
import org.jitsi.util.*;
import org.jitsi.util.event.*;

/**
 * This class aims to provide a simple configuration interface for JMF. It
 * retrieves stored configuration when started or listens to ConfigurationEvent
 * for property changes and configures the JMF accordingly.
 *
 * @author Martin Andre
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Vincent Lucas
 */
public class DeviceConfiguration
    extends PropertyChangeNotifier
    implements PropertyChangeListener
{
    /**
     * The name of the <tt>DeviceConfiguration</tt> property which represents
     * the device used by <tt>DeviceConfiguration</tt> for audio capture.
     */
    public static final String AUDIO_CAPTURE_DEVICE
        = CaptureDeviceListManager.PROP_DEVICE;

    /**
     * The name of the <tt>DeviceConfiguration</tt> property which represents
     * the device used by <tt>DeviceConfiguration</tt> for audio notify.
     */
    public static final String AUDIO_NOTIFY_DEVICE
        = NotifyDeviceListManager.PROP_DEVICE;

    /**
     * The name of the <tt>DeviceConfiguration</tt> property which represents
     * the device used by <tt>DeviceConfiguration</tt> for audio playback.
     */
    public static final String AUDIO_PLAYBACK_DEVICE
        = PlaybackDeviceListManager.PROP_DEVICE;

    /**
     * The name of the <tt>DeviceConfiguration</tt> property which represents
     * the device used by <tt>DeviceConfiguration</tt> for video capture.
     */
    public static final String VIDEO_CAPTURE_DEVICE
        = VideoDeviceListManager.PROP_DEVICE;

    /**
     * The list of class names of custom <tt>Renderer</tt> implementations to be
     * registered with JMF.
     */
    private static final String[] CUSTOM_RENDERERS
        = new String[]
        {
            null,
            null,
            (OSUtils.IS_WINDOWS && !OSUtils.IS_WINDOWS_VISTA) ? ".audio.WASAPIRenderer" : null,
            (OSUtils.IS_WINDOWS && !OSUtils.IS_WINDOWS_VISTA) ? null : ".audio.MacCoreaudioRenderer",
            ".video.JAWTRenderer"
        };

    /**
     * The default value for video codec bitrate.
     */
    public static final int DEFAULT_VIDEO_BITRATE = 128;

    /**
     * The default frame rate, <tt>-1</tt> unlimited.
     */
    public static final int DEFAULT_VIDEO_FRAMERATE = -1;

    /**
     * The default video height.
     */
    public static final int DEFAULT_VIDEO_HEIGHT = 720;

    /**
     * The default value for video maximum bandwidth.
     */
    public static final int DEFAULT_VIDEO_RTP_PACING_THRESHOLD = 256;

    /**
     * The default video width.
     */
    public static final int DEFAULT_VIDEO_WIDTH = 1280;

    public static final String PROP_AUDIO_SYSTEM
        = "net.java.sip.communicator.impl.neomedia.audioSystem";

    public static final String PROP_AUDIO_SYSTEM_DEVICES
        = PROP_AUDIO_SYSTEM + "." + DeviceSystem.PROP_DEVICES;

    public static final String PROP_VIDEO_SYSTEM
        = "net.java.sip.communicator.impl.neomedia.videoSystem";

    public static final String PROP_VIDEO_SYSTEM_DEVICES
        = PROP_VIDEO_SYSTEM + "." + DeviceSystem.PROP_DEVICES;

    /**
     * The property we use to store the settings for video codec bitrate.
     */
    private static final String PROP_VIDEO_BITRATE
        = "net.java.sip.communicator.impl.neomedia.video.bitrate";

    /**
     * The <tt>ConfigurationService</tt> property which stores the device used
     * by <tt>DeviceConfiguration</tt> for video capture.
     */
    private static final String PROP_VIDEO_DEVICE
        = "net.java.sip.communicator.impl.neomedia.videoDevice";

    /**
     * The property we use to store the video framerate settings.
     */
    private static final String PROP_VIDEO_FRAMERATE
        = "net.java.sip.communicator.impl.neomedia.video.framerate";

    /**
     * The name of the property which specifies the height of the video.
     */
    private static final String PROP_VIDEO_HEIGHT
        = "net.java.sip.communicator.impl.neomedia.video.height";

    /**
     * The property we use to store the settings for maximum allowed video
     * bandwidth (used to normalize RTP traffic, and not in codec configuration)
     */
    private static final String PROP_VIDEO_RTP_PACING_THRESHOLD
        = "net.java.sip.communicator.impl.neomedia.video.maxbandwidth";

    /**
     * The name of the property which specifies the width of the video.
     */
    private static final String PROP_VIDEO_WIDTH
        = "net.java.sip.communicator.impl.neomedia.video.width";

    /**
     * The currently supported resolutions we will show as option
     * and user can select.
     */
    public static final Dimension[] SUPPORTED_RESOLUTIONS
        = new Dimension[]
            {
                // QVGA
                new Dimension(160, 100),
                //QCIF
                new Dimension(176, 144),
                // QVGA
                new Dimension(320, 200),
                // QVGA
                new Dimension(320, 240),
                //CIF
                new Dimension(352, 288),
                // VGA
                new Dimension(640, 480),
                // HD 720
                new Dimension(1280, 720)
            };

    /**
     * Fixes the list of <tt>Renderer</tt>s registered with FMJ in order to
     * resolve operating system-specific issues.
     */
    private static void fixRenderers()
    {
        Vector<String> renderers
            = PlugInManager.getPlugInList(null, null, PlugInManager.RENDERER);

        /*
         * JMF is no longer in use, FMJ is used in its place. FMJ has its own
         * JavaSoundRenderer which is also extended into a JMF-compatible one.
         */
        PlugInManager.removePlugIn(
                "com.sun.media.renderer.audio.JavaSoundRenderer",
                PlugInManager.RENDERER);

        if (OSUtils.IS_WINDOWS)
        {
            if (OSUtils.IS_WINDOWS32 &&
                    (OSUtils.IS_WINDOWS_VISTA || OSUtils.IS_WINDOWS_7))
            {
                /*
                 * DDRenderer will cause 32-bit Windows Vista/7 to switch its
                 * theme from Aero to Vista Basic so try to pick up a different
                 * Renderer.
                 */
                if (renderers.contains(
                        "com.sun.media.renderer.video.GDIRenderer"))
                {
                    PlugInManager.removePlugIn(
                            "com.sun.media.renderer.video.DDRenderer",
                            PlugInManager.RENDERER);
                }
            }
            else if (OSUtils.IS_WINDOWS64)
            {
                /*
                 * Remove the native Renderers for 64-bit Windows because native
                 * JMF libs are not available for 64-bit machines.
                 */
                PlugInManager.removePlugIn(
                        "com.sun.media.renderer.video.GDIRenderer",
                        PlugInManager.RENDERER);
                PlugInManager.removePlugIn(
                        "com.sun.media.renderer.video.DDRenderer",
                        PlugInManager.RENDERER);
            }
        }
        else
        {
            if (renderers.contains(
                        "com.sun.media.renderer.video.LightWeightRenderer")
                    || renderers.contains(
                            "com.sun.media.renderer.video.AWTRenderer"))
            {
                // Remove XLibRenderer because it is native and JMF is supported
                // on 32-bit machines only.
                PlugInManager.removePlugIn(
                        "com.sun.media.renderer.video.XLibRenderer",
                        PlugInManager.RENDERER);
            }
        }
    }

    /**
     * The currently selected audio system.
     */
    private AudioSystem mAudioSystem;

    /**
     * The currently selected video system.
     */
    private VideoSystem mVideoSystem;

    /**
     * The frame rate.
     */
    private int mVideoFrameRate = DEFAULT_VIDEO_FRAMERATE;

    /**
     * The <tt>Logger</tt> used by this instance for logging output.
     */
    private static final Logger sLog = Logger.getLogger(DeviceConfiguration.class);

    /**
     * Current setting for video codec bitrate.
     */
    private int mVideoBitrate = -1;

    /**
     * The device that we'll be using for video capture.
     */
    private CaptureDeviceInfo mVideoCaptureDevice;

    /**
     * Current setting for video maximum bandwidth.
     */
    private int mVideoMaxBandwidth = -1;

    /**
     * The current resolution settings.
     */
    private Dimension mVideoSize;

    /**
     * Initializes a new <tt>DeviceConfiguration</tt> instance.
     */
    public DeviceConfiguration()
    {
        // these seem to be throwing exceptions every now and then so we'll
        // blindly catch them for now
        try
        {
            DeviceSystemManager.initializeDeviceSystems();
            extractConfiguredCaptureDevices();
        }
        catch (Exception ex)
        {
            sLog.error("Failed to initialize media.", ex);
        }

        ConfigurationService cfg = LibJitsi.getConfigurationService();

        if (cfg != null)
        {
            cfg.user().addPropertyChangeListener(PROP_VIDEO_HEIGHT, this);
            cfg.user().addPropertyChangeListener(PROP_VIDEO_WIDTH, this);
            cfg.user().addPropertyChangeListener(PROP_VIDEO_FRAMERATE, this);
            cfg.user().addPropertyChangeListener(
                    PROP_VIDEO_RTP_PACING_THRESHOLD,
                    this);
        }

        registerCustomRenderers();
        fixRenderers();

        /*
         * Adds this instance as a PropertyChangeListener to all DeviceSystems
         * which support reinitialization/reloading in order to be able, for
         * example, to switch from a default/automatic selection of "None" to a
         * DeviceSystem which has started providing at least one device at
         * runtime.
         */
        addDeviceSystemPropertyChangeListener();
    }

    /**
     * Adds this instance as a <tt>PropertyChangeListener</tt> to all
     * <tt>DeviceSystem</tt>s which support reinitialization/reloading in order
     * to be able, for example, to switch from a default/automatic selection of
     * &quot;None&quot; to an <tt>DeviceSystem</tt> which has started providing
     * at least one device at runtime.
     */
    private void addDeviceSystemPropertyChangeListener()
    {
        // Track all kinds of DeviceSystems i.e audio and video.
        for (MediaType mediaType: MediaType.values())
        {
            DeviceSystem[] deviceSystems
                = DeviceSystemManager.getDeviceSystems(mediaType);

            if (deviceSystems != null)
            {
                for (DeviceSystem deviceSystem : deviceSystems)
                {
                    // It only makes sense to track DeviceSystems which support
                    // reinitialization/reloading.
                    if ((deviceSystem.getFeatures()
                                & DeviceSystem.FEATURE_REINITIALIZE)
                            != 0)
                    {
                        deviceSystem.addPropertyChangeListener(this);
                    }
                }
            }
        }
    }

    /**
     * Detects audio capture devices configured through JMF and disable audio if
     * none was found.
     */
    private void extractConfiguredAudioCaptureDevices()
    {
        sLog.info("Looking for configured audio devices.");

        AudioSystem[] availableAudioSystems = getAvailableAudioSystems();

        if ((availableAudioSystems != null)
                && (availableAudioSystems.length != 0))
        {
            AudioSystem audioSystem = getAudioSystem();

            if (audioSystem == null)
            {
                ConfigurationService cfg = LibJitsi.getConfigurationService();

                if (cfg != null)
                {
                    String locatorProtocol = cfg.user().getString(PROP_AUDIO_SYSTEM);

                    if (locatorProtocol != null)
                    {
                        for (AudioSystem availableAudioSystem
                                : availableAudioSystems)
                        {
                            if (locatorProtocol.equalsIgnoreCase(
                                    availableAudioSystem.getLocatorProtocol()))
                            {
                                audioSystem = availableAudioSystem;
                                break;
                            }
                        }
                        /*
                         * Always use the configured AudioSystem regardless of
                         * whether it is available.
                         */
                        if (audioSystem == null)
                        {
                            audioSystem
                                = AudioSystem.getAudioSystem(locatorProtocol);
                        }
                    }
                }

                if (audioSystem == null)
                    audioSystem = availableAudioSystems[0];

                setAudioSystem(audioSystem);
            }
        }
    }

    /**
     * Detects capture devices configured through JMF and disable audio and/or
     * video transmission if none were found.
     */
    private void extractConfiguredCaptureDevices()
    {
        extractConfiguredAudioCaptureDevices();
        extractConfiguredVideoCaptureDevices();
    }

    /**
     * Returns the configured video capture device with the specified
     * output format.
     * @param onlyUseLastConfiguredDevice  If true, only the last used video
     *        device may be selected.
     * @param format the output format of the video format.
     * @return CaptureDeviceInfo for the video device.
     */
    private CaptureDeviceInfo extractConfiguredVideoCaptureDevice(
        boolean onlyUseLastConfiguredDevice, Format format)
    {
        List<CaptureDeviceInfo> videoCaptureDevices
            = CaptureDeviceManager.getDeviceList(format);
        CaptureDeviceInfo videoCaptureDevice = null;

        if (videoCaptureDevices.size() > 0)
        {
            ConfigurationService cfg = LibJitsi.getConfigurationService();
            String videoDevName = (onlyUseLastConfiguredDevice && cfg != null) ?
                cfg.user().getString(PROP_VIDEO_DEVICE) : null;

            if (videoDevName == null)
            {
                videoCaptureDevice = videoCaptureDevices.get(0);
            }
            else
            {
                for (CaptureDeviceInfo captureDeviceInfo : videoCaptureDevices)
                {
                    if (videoDevName.equals(captureDeviceInfo.getName()))
                    {
                        videoCaptureDevice = captureDeviceInfo;
                        break;
                    }
                }
            }

            if (videoCaptureDevice != null)
            {
                sLog.info("Found " + videoCaptureDevice.getName() + " as a "
                          + format + " video capture device.");
            }
        }
        return videoCaptureDevice;
    }

    /**
     * Detects video capture devices configured through JMF and disable video if
     * none was found.
     */
    private void extractConfiguredVideoCaptureDevices()
    {
        VideoSystem videoSystem = getVideoSystem();
        if (videoSystem == null)
        {
            VideoSystem availableVideoSystem = getAvailableVideoSystem();

            if (availableVideoSystem != null)
            {
                setVideoSystem(availableVideoSystem);
            }
        }

        // First, try and use the last-used video device.
        extractConfiguredVideoCaptureDevices(true);

        if (mVideoCaptureDevice == null)
        {
            // The last used video device, if any, is not connected.  Pick any
            // connected video device.
            extractConfiguredVideoCaptureDevices(false);
        }
    }

    /**
     * Detects video capture devices configured through JMF and disable video if
     * none was found.
     * @param onlyUseLastConfiguredDevice  If true, only the last used video
     *        device may be selected.
     */
    private void extractConfiguredVideoCaptureDevices(
        boolean onlyUseLastConfiguredDevice)
    {
        ConfigurationService cfg = LibJitsi.getConfigurationService();
        String videoCaptureDeviceString
            = (cfg == null) ? null : cfg.user().getString(PROP_VIDEO_DEVICE);

        if (NoneAudioSystem.LOCATOR_PROTOCOL.equalsIgnoreCase(
                videoCaptureDeviceString))
        {
            mVideoCaptureDevice = null;
        }
        else
        {
            sLog.info("Scanning for configured Video Devices.");

            Format[] formats
                = new Format[]
                        {
                            new AVFrameFormat(),
                            new VideoFormat(VideoFormat.RGB),
                            new VideoFormat(VideoFormat.YUV),
                            new VideoFormat(Constants.H264)
                        };

            for (Format format : formats)
            {
                mVideoCaptureDevice = extractConfiguredVideoCaptureDevice(
                    onlyUseLastConfiguredDevice, format);
                if (mVideoCaptureDevice != null)
                {
                    break;
                }
            }
            if (mVideoCaptureDevice == null)
            {
                sLog.info("No Video Device was found.");
            }
        }
    }

    /**
     * Returns a device that we could use for audio capture.
     *
     * @return the CaptureDeviceInfo of a device that we could use for audio
     *         capture.
     */
    public Device getAudioCaptureDevice()
    {
        AudioSystem audioSystem = getAudioSystem();

        return
            (audioSystem == null)
                ? null
                : audioSystem.getAndRefreshSelectedDevice(DataFlow.CAPTURE);
    }

    /**
     * Returns a device that we could use for audio playback.
     *
     * @return the CaptureDeviceInfo of a device that we could use for audio
     *         playback.
     */
    public Device getAudioPlaybackDevice()
    {
        AudioSystem audioSystem = getAudioSystem();

        return
            (audioSystem == null)
                ? null
                : audioSystem.getAndRefreshSelectedDevice(DataFlow.PLAYBACK);
    }

    public AudioSystem getAudioSystem()
    {
        return mAudioSystem;
    }

    public VideoSystem getVideoSystem()
    {
        return mVideoSystem;
    }

    /**
     * Gets the list of audio capture devices which are available through this
     * <tt>DeviceConfiguration</tt>, amongst which is
     * {@link #getAudioCaptureDevice()} and represent acceptable values
     * for {@link AudioSystem#setCaptureDevices}
     *
     * @return an array of <tt>CaptureDeviceInfo</tt> describing the audio
     *         capture devices available through this
     *         <tt>DeviceConfiguration</tt>
     */
    public List<Device> getAvailableAudioCaptureDevices()
    {
        return mAudioSystem.getActiveDevices(DataFlow.CAPTURE);
    }

    /**
     * Returns a list of available <tt>AudioSystem</tt>s. By default, an
     * <tt>AudioSystem</tt> is considered available if it reports at least one
     * device.
     *
     * @return an array of available <tt>AudioSystem</tt>s
     */
    public AudioSystem[] getAvailableAudioSystems()
    {
        AudioSystem[] audioSystems = AudioSystem.getAudioSystems();

        if ((audioSystems == null) || (audioSystems.length == 0))
        {
            return audioSystems;
        }
        else
        {
            List<AudioSystem> audioSystemsWithDevices
                = new ArrayList<>();

            for (AudioSystem audioSystem : audioSystems)
            {
                if (!NoneAudioSystem.LOCATOR_PROTOCOL.equalsIgnoreCase(
                        audioSystem.getLocatorProtocol()))
                {
                    List<Device> captureDevices
                        = audioSystem.getActiveDevices(DataFlow.CAPTURE);

                    if ((captureDevices == null)
                            || (captureDevices.size() <= 0))
                    {
                        if ((AudioSystem.FEATURE_NOTIFY_AND_PLAYBACK_DEVICES
                                    & audioSystem.getFeatures())
                                == 0)
                        {
                            continue;
                        }
                        else
                        {
                            List<Device> notifyDevices
                                = audioSystem.getActiveDevices(
                                        DataFlow.NOTIFY);

                            if ((notifyDevices == null)
                                    || (notifyDevices.size() <= 0))
                            {
                                List<Device> playbackDevices
                                    = audioSystem.getActiveDevices(
                                        DataFlow.PLAYBACK);

                                if ((playbackDevices == null)
                                        || (playbackDevices.size() <= 0))
                                {
                                    continue;
                                }
                            }
                        }
                    }
                }
                audioSystemsWithDevices.add(audioSystem);
            }

            int audioSystemsWithDevicesCount = audioSystemsWithDevices.size();

            return
                (audioSystemsWithDevicesCount == audioSystems.length)
                    ? audioSystems
                    : audioSystemsWithDevices.toArray(
                            new AudioSystem[audioSystemsWithDevicesCount]);
        }
    }

    /**
     * Returns the available <tt>VideoSystem</tt>, if any.  There can only be
     * one and if it exists we always consider it available.
     *
     * @return A <tt>VideoSystem</tt> or null.
     */
    public VideoSystem getAvailableVideoSystem()
    {
        VideoSystem videoSystem = VideoSystem.getVideoSystem();
        return videoSystem;
    }

    /**
     * Gets the list of video capture devices which are available through this
     * <tt>DeviceConfiguration</tt>, amongst which is
     * {@link #getVideoCaptureDevice(MediaUseCase)} and represent acceptable
     * values for {@link VideoSystem#setVideoDevices(List)}
     *
     * @param useCase extract video capture devices that correspond to this
     * <tt>MediaUseCase</tt>
     * @return an array of <tt>CaptureDeviceInfo</tt> describing the video
     *         capture devices available through this
     *         <tt>DeviceConfiguration</tt>
     */
    public List<CaptureDeviceInfo> getAvailableVideoCaptureDevices(
            MediaUseCase useCase)
    {
        Format[] formats
            = new Format[]
                    {
                        new AVFrameFormat(),
                        new VideoFormat(VideoFormat.RGB),
                        new VideoFormat(VideoFormat.YUV),
                        new VideoFormat(Constants.H264)
                    };
        Set<CaptureDeviceInfo> videoCaptureDevices
            = new HashSet<>();

        for (Format format : formats)
        {
            Vector<CaptureDeviceInfo> cdis
                = CaptureDeviceManager.getDeviceList(format);

            if (useCase != MediaUseCase.ANY)
            {
                for (CaptureDeviceInfo cdi : cdis)
                {
                    if (MediaUseCase.CALL.equals(useCase))
                    {
                        videoCaptureDevices.add(cdi);
                    }
                }
            }
            else
            {
                videoCaptureDevices.addAll(cdis);
            }
        }

        return new ArrayList<>(videoCaptureDevices);
    }

    /**
     * Gets the frame rate set on this <tt>DeviceConfiguration</tt>.
     *
     * @return the frame rate set on this <tt>DeviceConfiguration</tt>. The
     * default value is {@link #DEFAULT_VIDEO_FRAMERATE}
     */
    public int getFrameRate()
    {
        if (mVideoFrameRate == -1)
        {
            ConfigurationService cfg = LibJitsi.getConfigurationService();
            int value = DEFAULT_VIDEO_FRAMERATE;

            if (cfg != null)
                value = cfg.user().getInt(PROP_VIDEO_FRAMERATE, value);

            mVideoFrameRate = value;
        }
        return mVideoFrameRate;
    }

    /**
     * Gets the video bitrate.
     *
     * @return the video codec bitrate. The default value is
     * {@link #DEFAULT_VIDEO_BITRATE}.
     */
    public int getVideoBitrate()
    {
        if (mVideoBitrate == -1)
        {
            ConfigurationService cfg = LibJitsi.getConfigurationService();
            int value = DEFAULT_VIDEO_BITRATE;

            if (cfg != null)
                value = cfg.user().getInt(PROP_VIDEO_BITRATE, value);

            if (value > 0)
            {
                mVideoBitrate = value;
            }
            else
            {
                mVideoBitrate = DEFAULT_VIDEO_BITRATE;
            }
        }
        return mVideoBitrate;
    }

    /**
     * Returns a device that we could use for video capture.
     *
     * @param useCase <tt>MediaUseCase</tt> that will determined device
     * we will use
     * @return the Device of a device that we could use for video
     *         capture.
     */
    public Device getVideoCaptureDevice(MediaUseCase useCase)
    {
        Device videoDevice = null;
        VideoSystem videoSystem = getVideoSystem();

        if ((videoSystem != null) &&
            ((useCase == MediaUseCase.ANY) || (useCase == MediaUseCase.CALL)))
        {
            videoDevice = videoSystem.getAndRefreshSelectedDevice();
        }

        sLog.info("Return video device: " + videoDevice);
        return videoDevice;
    }

    /**
     * Gets the maximum allowed video bandwidth.
     *
     * @return the maximum allowed video bandwidth. The default value is
     * {@link #DEFAULT_VIDEO_RTP_PACING_THRESHOLD}.
     */
    public int getVideoRTPPacingThreshold()
    {
        if (mVideoMaxBandwidth == -1)
        {
            ConfigurationService cfg = LibJitsi.getConfigurationService();
            int value = DEFAULT_VIDEO_RTP_PACING_THRESHOLD;

            if (cfg != null)
                value = cfg.user().getInt(PROP_VIDEO_RTP_PACING_THRESHOLD, value);

            if (value > 0)
            {
                mVideoMaxBandwidth = value;
            }
            else
            {
                mVideoMaxBandwidth = DEFAULT_VIDEO_RTP_PACING_THRESHOLD;
            }
        }
        return mVideoMaxBandwidth;
    }

    /**
     * Gets the video size set on this <tt>DeviceConfiguration</tt>.
     *
     * @return the video size set on this <tt>DeviceConfiguration</tt>
     */
    public Dimension getVideoSize()
    {
        if (mVideoSize == null)
        {
            ConfigurationService cfg = LibJitsi.getConfigurationService();
            int height = DEFAULT_VIDEO_HEIGHT;
            int width = DEFAULT_VIDEO_WIDTH;

            if (cfg != null)
            {
                height = cfg.user().getInt(PROP_VIDEO_HEIGHT, height);
                width = cfg.user().getInt(PROP_VIDEO_WIDTH, width);
            }

            mVideoSize = new Dimension(width, height);
        }
        return mVideoSize;
    }

    /**
     * Notifies this <tt>PropertyChangeListener</tt> about
     * <tt>PropertyChangeEvent</tt>s fired by, for example, the
     * <tt>ConfigurationService</tt> and the <tt>DeviceSystem</tt>s which
     * support reinitialization/reloading.
     *
     * @param ev the <tt>PropertyChangeEvent</tt> to notify this
     * <tt>PropertyChangeListener</tt> about and which describes the source and
     * other specifics of the notification
     */
    @Override
    public void propertyChange(PropertyChangeEvent ev)
    {
        String propertyName = ev.getPropertyName();

        if (AUDIO_CAPTURE_DEVICE.equals(propertyName) ||
            AUDIO_NOTIFY_DEVICE.equals(propertyName) ||
            AUDIO_PLAYBACK_DEVICE.equals(propertyName))
        {
            /*
             * The current audioSystem may represent a default/automatic
             * selection which may have been selected because the user's
             * selection may have been unavailable at the time. Make sure that
             * the user's selection is respected if possible.
             */
            extractConfiguredAudioCaptureDevices();

            /*
             * The specified PropertyChangeEvent has been fired by a
             * DeviceSystem i.e. a certain DeviceSystem is the source. Translate
             * it to a PropertyChangeEvent fired by this instance.
             */
            AudioSystem audioSystem = getAudioSystem();

            if (audioSystem != null)
            {
                CaptureDeviceInfo oldValue
                    = (CaptureDeviceInfo) ev.getOldValue();
                CaptureDeviceInfo newValue
                    = (CaptureDeviceInfo) ev.getNewValue();
                CaptureDeviceInfo device
                    = (oldValue == null) ? newValue : oldValue;

                // Fire an event on the selected device only if the event is
                // generated by the selected audio system.
                if ((device == null)
                        || device.getLocator().getProtocol().equals(
                                audioSystem.getLocatorProtocol()))
                {
                    firePropertyChange(propertyName, oldValue, newValue);
                }
            }
        }
        else if (VIDEO_CAPTURE_DEVICE.equals(propertyName))
        {
            /**
             * The current videoSystem may represent a default/automatic
             * selection which may have been selected because the user's
             * selection may have been unavailable at the time. Make sure that
             * the user's selection is respected if possible.
             */
            extractConfiguredVideoCaptureDevices();

            /*
             * The specified PropertyChangeEvent has been fired by a
             * DeviceSystem i.e. a certain DeviceSystem is the source. Translate
             * it to a PropertyChangeEvent fired by this instance.
             */
            VideoSystem videoSystem = getVideoSystem();

            if (videoSystem != null)
            {
                CaptureDeviceInfo oldValue
                    = (CaptureDeviceInfo) ev.getOldValue();
                CaptureDeviceInfo newValue
                    = (CaptureDeviceInfo) ev.getNewValue();
                CaptureDeviceInfo device
                    = (oldValue == null) ? newValue : oldValue;

                // Fire an event on the selected device only if the event is
                // generated by the selected video system.
                if ((device == null)
                        || device.getLocator().getProtocol().equals(
                                videoSystem.getLocatorProtocol()))
                {
                    firePropertyChange(propertyName, oldValue, newValue);
                }
            }
        }
        else if (DeviceSystem.PROP_DEVICES.equals(propertyName))
        {
            if (ev.getSource() instanceof AudioSystem)
            {
                /*
                 * The current audioSystem may represent a default/automatic
                 * selection which may have been selected because the user's
                 * selection may have been unavailable at the time. Make sure
                 * that the user's selection is respected if possible.
                 */
                extractConfiguredAudioCaptureDevices();

                @SuppressWarnings("unchecked")
                List<CaptureDeviceInfo> newValue
                    = (List<CaptureDeviceInfo>) ev.getNewValue();

                firePropertyChange(
                        PROP_AUDIO_SYSTEM_DEVICES,
                        ev.getOldValue(),
                        newValue);
            }
            else if (ev.getSource() instanceof VideoSystem)
            {
                extractConfiguredVideoCaptureDevices();

                @SuppressWarnings("unchecked")
                List<CaptureDeviceInfo> newValue
                    = (List<CaptureDeviceInfo>) ev.getNewValue();

                firePropertyChange(
                        PROP_VIDEO_SYSTEM_DEVICES,
                        ev.getOldValue(),
                        newValue);
            }
        }
        else if (PROP_VIDEO_FRAMERATE.equals(propertyName))
        {
            mVideoFrameRate = -1;
        }
        else if (PROP_VIDEO_HEIGHT.equals(propertyName)
                || PROP_VIDEO_WIDTH.equals(propertyName))
        {
            mVideoSize = null;
        }
        else if (PROP_VIDEO_RTP_PACING_THRESHOLD.equals(propertyName))
        {
            mVideoMaxBandwidth = -1;
        }
    }

    /**
     * Registers the custom <tt>Renderer</tt> implementations defined by class
     * name in {@link #CUSTOM_RENDERERS} with JMF.
     */
    private void registerCustomRenderers()
    {
        Vector<String> renderers
            = PlugInManager.getPlugInList(null, null, PlugInManager.RENDERER);
        boolean commit = false;

        for (String customRenderer : CUSTOM_RENDERERS)
        {
            if (customRenderer == null)
                continue;
            if (customRenderer.startsWith("."))
            {
                customRenderer
                    = "org.jitsi.impl.neomedia"
                        + ".jmfext.media.renderer"
                        + customRenderer;
            }
            if ((renderers == null) || !renderers.contains(customRenderer))
            {
                try
                {
                    Renderer customRendererInstance
                        = (Renderer)
                            Class.forName(customRenderer).newInstance();

                    PlugInManager.addPlugIn(
                            customRenderer,
                            customRendererInstance.getSupportedInputFormats(),
                            null,
                            PlugInManager.RENDERER);
                    commit = true;
                }
                catch (Throwable t)
                {
                    sLog.error("Failed to register custom Renderer "
                                   + customRenderer
                                   + " with JMF.",
                               t);
                }
            }
        }

        /*
         * Just in case, bubble our JMF contributions at the top so that they
         * are considered preferred.
         */
        int pluginType = PlugInManager.RENDERER;
        Vector<String> plugins
            = PlugInManager.getPlugInList(null, null, pluginType);

        if (plugins != null)
        {
            int pluginCount = plugins.size();
            int pluginBeginIndex = 0;

            for (int pluginIndex = pluginCount - 1;
                 pluginIndex >= pluginBeginIndex;)
            {
                String plugin = plugins.get(pluginIndex);

                if (plugin.startsWith("org.jitsi.")
                        || plugin.startsWith("net.java.sip.communicator."))
                {
                    plugins.remove(pluginIndex);
                    plugins.add(0, plugin);
                    pluginBeginIndex++;
                    commit = true;
                }
                else
                    pluginIndex--;
            }
            PlugInManager.setPlugInList(plugins, pluginType);
            sLog.trace("Reordered plug-in list:" + plugins);
        }

        if (commit && !MediaServiceImpl.isJmfRegistryDisableLoad())
        {
            try
            {
                PlugInManager.commit();
            }
            catch (IOException ioex)
            {
                sLog.warn("Failed to commit changes to the JMF plug-in list.");
            }
        }
    }

    private void setAudioSystem(AudioSystem audioSystem)
    {
        if (mAudioSystem != audioSystem)
        {
            // Removes the registration to change listener only if this audio
            // system does not support reinitialize.
            if ((mAudioSystem != null)
                    && (mAudioSystem.getFeatures()
                                & DeviceSystem.FEATURE_REINITIALIZE)
                            == 0)
            {
                mAudioSystem.removePropertyChangeListener(this);
            }

            AudioSystem oldValue = mAudioSystem;

            mAudioSystem = audioSystem;

            // Registers the new selected audio system.  Even if every
            // FEATURE_REINITIALIZE audio system is registered already, the
            // check for duplicate entries will be done by the
            // addPropertyChangeListener method.
            if (mAudioSystem != null)
            {
                mAudioSystem.addPropertyChangeListener(this);
            }

            firePropertyChange(PROP_AUDIO_SYSTEM, oldValue, mAudioSystem);
        }
    }

    private void setVideoSystem(VideoSystem videoSystem)
    {
        if (mVideoSystem != videoSystem)
        {
            VideoSystem oldValue = mVideoSystem;
            mVideoSystem = videoSystem;

            // Registers the new selected audio system.  Even if every
            // FEATURE_REINITIALIZE audio system is registered already, the
            // check for duplicate entries will be done by the
            // addPropertyChangeListener method.
            if (mVideoSystem != null)
            {
                mVideoSystem.addPropertyChangeListener(this);
            }

            firePropertyChange(PROP_VIDEO_SYSTEM, oldValue, mVideoSystem);
        }
    }
}
