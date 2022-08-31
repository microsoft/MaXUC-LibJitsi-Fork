/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.device;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import javax.media.CaptureDeviceInfo;
import javax.media.MediaLocator;
import javax.media.Renderer;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.UnsupportedAudioFileException;

import org.jitsi.impl.neomedia.jmfext.media.renderer.audio.AbstractAudioRenderer;
import org.jitsi.service.configuration.ConfigurationService;
import org.jitsi.service.libjitsi.LibJitsi;
import org.jitsi.service.neomedia.MediaType;
import org.jitsi.service.resources.ResourceManagementService;
import org.jitsi.util.Logger;

/**
 * Represents a <tt>DeviceSystem</tt> which provides support for the devices to
 * capture and play back audio (media). Examples include implementations which
 * integrate the native PortAudio, PulseAudio libraries.
 *
 * @author Lyubomir Marinov
 * @author Vincent Lucas
 */
public abstract class AudioSystem
    extends DeviceSystem implements DeviceSystemProperties
{
    /**
     * The <tt>Logger</tt> used by this instance for logging output.
     */
    private static final Logger sLog = Logger.getLogger(AudioSystem.class);

    /**
     * The constant/flag (to be) returned by {@link #getFeatures()} in order to
     * indicate that the respective <tt>AudioSystem</tt> supports toggling its
     * denoise functionality between on and off. The UI will look for the
     * presence of the flag in order to determine whether a check box is to be
     * shown to the user to enable toggling the denoise functionality.
     */
    public static final int FEATURE_DENOISE = 2;

    /**
     * The constant/flag (to be) returned by {@link #getFeatures()} in order to
     * indicate that the respective <tt>AudioSystem</tt> supports toggling its
     * echo cancellation functionality between on and off. The UI will look for
     * the presence of the flag in order to determine whether a check box is to
     * be shown to the user to enable toggling the echo cancellation
     * functionality.
     */
    public static final int FEATURE_ECHO_CANCELLATION = 4;

    /**
     * The constant/flag (to be) returned by {@link #getFeatures()} in order to
     * indicate that the respective <tt>AudioSystem</tt> differentiates between
     * playback and notification audio devices. The UI, for example, will look
     * for the presence of the flag in order to determine whether separate combo
     * boxes are to be shown to the user to allow the configuration of the
     * preferred playback and notification audio devices.
     */
    public static final int FEATURE_NOTIFY_AND_PLAYBACK_DEVICES = 8;

    /**
     * The constant/flag (to be) returned by {@link #getFeatures()} in order to
     * indicate that the respective <tt>AudioSystem</tt> differentiates between
     * rendering audio with blocking behaviour and non-blocking behaviour.
     * See {@link #createRenderer(boolean, boolean)} for a description of the
     * difference in these two behaviours, when there is one.
     * Currently only used on Mac.
     */
    public static final int FEATURE_BLOCKING_RENDERER_OPTION = 16;

    public static final String LOCATOR_PROTOCOL_AUDIORECORD = "audiorecord";

    public static final String LOCATOR_PROTOCOL_JAVASOUND = "javasound";

    /**
     * The protocol of the <tt>MediaLocator</tt>s identifying
     * <tt>CaptureDeviceInfo</tt>s contributed by <tt>MacCoreaudioSystem</tt>.
     */
    public static final String LOCATOR_PROTOCOL_MACCOREAUDIO = "maccoreaudio";

    public static final String LOCATOR_PROTOCOL_OPENSLES = "opensles";

    public static final String LOCATOR_PROTOCOL_PORTAUDIO = "portaudio";

    /**
     * The protocol of the <tt>MediaLocator</tt>s identifying
     * <tt>CaptureDeviceInfo</tt>s contributed by <tt>WASAPISystem</tt>.
     */
    public static final String LOCATOR_PROTOCOL_WASAPI = "wasapi";

    /**
     * The device list manager for each device type
     */
    private HashMap<DataFlow, DeviceListManager> deviceListManagers;

    public static AudioSystem getAudioSystem(String locatorProtocol)
    {
        AudioSystem[] audioSystems = getAudioSystems();
        AudioSystem audioSystemWithLocatorProtocol = null;

        if (audioSystems != null)
        {
            for (AudioSystem audioSystem : audioSystems)
            {
                if (audioSystem.getLocatorProtocol().equalsIgnoreCase(
                        locatorProtocol))
                {
                    audioSystemWithLocatorProtocol = audioSystem;
                    break;
                }
            }
        }
        return audioSystemWithLocatorProtocol;
    }

    public static AudioSystem[] getAudioSystems()
    {
        DeviceSystem[] deviceSystems = DeviceSystemManager.getDeviceSystems(MediaType.AUDIO);
        List<AudioSystem> audioSystems = new ArrayList<>(deviceSystems.length);

        for (DeviceSystem deviceSystem : deviceSystems)
        {
            if (deviceSystem instanceof AudioSystem)
            {
                audioSystems.add((AudioSystem) deviceSystem);
            }
        }

        return audioSystems.toArray(new AudioSystem[audioSystems.size()]);
    }

    protected AudioSystem(String locatorProtocol)
        throws Exception
    {
        this(locatorProtocol, 0);
    }

    protected AudioSystem(String locatorProtocol, int features)
        throws Exception
    {
        super(MediaType.AUDIO, locatorProtocol, features);
    }

    /**
     * {@inheritDoc}
     *
     * Delegates to {@link #createRenderer(boolean)} with the value of the
     * <tt>playback</tt> argument set to true.
     */
    @Override
    public Renderer createRenderer()
    {
        return createRenderer(true);
    }

    /**
     * Call the main constructor, default 'blocking' to false
     * @param playback
     * @return
     */
    public Renderer createRenderer(boolean playback)
    {
        return createRenderer(playback, false);
    }

    /**
     * Initializes a new <tt>Renderer</tt> instance which is to either perform
     * playback on or sound a notification through a device contributed by this
     * system. The (default) implementation of <tt>AudioSystem</tt> ignores the
     * value of the <tt>playback</tt> argument and delegates to
     * {@link DeviceSystem#createRenderer()}.
     *
     * @param playback <tt>true</tt> if the new instance is to perform playback
     * or <tt>false</tt> if the new instance is to sound a notification
     * @param blocking <tt>true</tt> if we want this renderer to 'block' - i.e.
     * stop new data coming in whilst it has no room for new data, with the expectation
     * that the supplier of that data will wait for it to render some of its existing
     * data until there is room for it.
     * The opposite behaviour here is for the renderer to dump excess data if it's
     * receiving data faster than it can render it.
     * A blocking renderer is appropriate for things that aren't time-sensitive
     * e.g. playing back audio clips, playing ringback etc.
     * It's not appropriate for rendering audio in a call, where we need to keep
     * audio in time with the live conversation, and would rather drop some audio
     * data than keep a backlog building up (which would add a growing delay to the
     * call's audio).
     * @return a new <tt>Renderer</tt> instance which is to either perform
     * playback on or sound a notification through a device contributed by this
     * system
     */
    public Renderer createRenderer(boolean playback, boolean blocking)
    {
        sLog.info("Create renderer, playback = " + playback + ", blocking = " + blocking);
        String className = getRendererClassName();
        Renderer renderer = null;

        if (className != null)
        {
            Class<?> clazz;

            try
            {
                clazz = Class.forName(className);
            }
            catch (Throwable t)
            {
                if (t instanceof ThreadDeath)
                {
                    throw (ThreadDeath) t;
                }
                else
                {
                    clazz = null;
                    sLog.error("Failed to get class " + className, t);
                }
            }

            // We should fall back to creating the renderer from the superclass if we meet the
            // minimum requirement of having a class that implements the Renderer interface
            boolean superCreateRenderer = ((clazz != null) && Renderer.class.isAssignableFrom(clazz));

            if ((clazz != null) &&
                (AbstractAudioRenderer.class.isAssignableFrom(clazz)) &&
                ((getFeatures() & FEATURE_NOTIFY_AND_PLAYBACK_DEVICES) != 0))
            {
                Constructor<?> constructor = null;

                try
                {
                     constructor = ((getFeatures() & FEATURE_BLOCKING_RENDERER_OPTION) != 0) ?
                         clazz.getConstructor(boolean.class, boolean.class) :
                         clazz.getConstructor(boolean.class);
                }
                catch (NoSuchMethodException nsme)
                {
                    /*
                     * Such a constructor is optional so the failure to get
                     * it will be swallowed and the super's
                     * createRenderer() will be invoked.
                     */
                }
                catch (SecurityException se)
                {
                }
                if ((constructor != null))
                {
                    superCreateRenderer = false;
                    try
                    {
                        renderer = (Renderer)
                            (((getFeatures() & FEATURE_BLOCKING_RENDERER_OPTION) != 0) ?
                             constructor.newInstance(playback, blocking) :
                             constructor.newInstance(playback));
                    }
                    catch (Throwable t)
                    {
                        if (t instanceof ThreadDeath)
                        {
                            throw (ThreadDeath) t;
                        }
                        else
                        {
                            renderer = null;
                            sLog.error("Failed to initialize a new " + className + " instance", t);
                        }
                    }
                    if ((renderer != null) && !playback)
                    {
                        CaptureDeviceInfo device = getSelectedDevice(DataFlow.NOTIFY);
                        sLog.info("Notifying on " + device + ", renderer " + renderer);

                        if (device == null)
                        {
                            // If there is no notification device, then no notification is to be sounded.
                            renderer = null;
                        }
                        else
                        {
                            MediaLocator locator = device.getLocator();

                            if (locator != null)
                            {
                                ((AbstractAudioRenderer<?>) renderer).setLocator(locator);
                            }
                        }
                    }
                }
            }

            if (superCreateRenderer && (renderer == null))
            {
                renderer = super.createRenderer();
            }
        }

        return renderer;
    }

    /**
     * Obtains an audio input stream from the URL provided.
     * @param uri a valid uri to a sound resource.
     * @return the input stream to audio data.
     * @throws IOException if an I/O exception occurs
     */
    public InputStream getAudioInputStream(String uri)
        throws IOException
    {
        ResourceManagementService resources
            = LibJitsi.getResourceManagementService();
        URL url
            = (resources == null)
                ? null
                : resources.getSoundURLForPath(uri);
        AudioInputStream audioStream = null;

        try
        {
            // Not found by the class loader? Perhaps it is a local file.
            if (url == null)
            {
                url = new URL(uri);
            }

            audioStream
                = javax.sound.sampled.AudioSystem.getAudioInputStream(url);
        }
        catch (MalformedURLException murle)
        {
            // Do nothing, the value of audioStream will remain equal to null.
        }
        catch (UnsupportedAudioFileException uafe)
        {
            sLog.error("Unsupported format of audio stream " + url, uafe);
        }

        return audioStream;
    }

    /**
     * Gets a <tt>Device</tt> which has been contributed by this
     * <tt>AudioSystem</tt>, supports a specific flow of media data (i.e.
     * capture, notify or playback) and is identified by a specific
     * <tt>MediaLocator</tt>.
     *
     * @param dataFlow the flow of the media data supported by the
     * <tt>Device</tt> to be returned
     * @param locator the <tt>MediaLocator</tt> of the
     * <tt>Device</tt> to be returned
     * @return a <tt>Device</tt> which has been contributed by this
     * instance, supports the specified <tt>dataFlow</tt> and is identified by
     * the specified <tt>locator</tt>
     */
    public Device getDevice(DataFlow dataFlow, MediaLocator locator)
    {
        return deviceListManagers.get(dataFlow).getDevice(locator);
    }

    /**
     * Gets the list of devices with a specific data flow: capture, notify or
     * playback.
     *
     * @param dataFlow the data flow of the devices to retrieve: capture, notify
     * or playback
     * @return the list of devices with the specified <tt>dataFlow</tt>
     */
    public List<Device> getActiveDevices(DataFlow dataFlow)
    {
        return deviceListManagers.get(dataFlow).getActiveDevices();
    }

    /**
     * Gets the list of all devices in user preferences with a specific data
     * flow: capture, notify or playback.
     *
     * @param dataFlow the data flow of the devices to retrieve: capture, notify
     * or playback
     * @return the list of all devices with the specified <tt>dataFlow</tt>
     */
    public LinkedHashMap<String, String> getAllKnownDevices(DataFlow dataFlow)
    {
        return deviceListManagers.get(dataFlow).getAllKnownDevices();
    }

    /**
     * Returns the FMJ format of a specific <tt>InputStream</tt> providing audio
     * media.
     *
     * @param audioInputStream the <tt>InputStream</tt> providing audio media to
     * determine the FMJ format of
     * @return the FMJ format of the specified <tt>audioInputStream</tt> or
     * <tt>null</tt> if such an FMJ format could not be determined
     */
    public javax.media.format.AudioFormat getFormat(
            InputStream audioInputStream)
    {
        if ((audioInputStream instanceof AudioInputStream))
        {
            AudioFormat af = ((AudioInputStream) audioInputStream).getFormat();

            return
                new javax.media.format.AudioFormat(
                        javax.media.format.AudioFormat.LINEAR,
                        af.getSampleRate(),
                        af.getSampleSizeInBits(),
                        af.getChannels());
        }
        return null;
    }

    @Override
    public String getPropertyName(String basePropertyName)
    {
        return
            DeviceConfiguration.PROP_AUDIO_SYSTEM + "." + getLocatorProtocol()
                + "." + basePropertyName;
    }

    /**
     * Gets the selected device for a specific data flow: capture, notify or
     * playback.
     *
     * @param dataFlow the data flow of the selected device to retrieve:
     * capture, notify or playback.
     * @return the selected device for the specified <tt>dataFlow</tt>
     */
    public Device getSelectedDevice(DataFlow dataFlow)
    {
        return deviceListManagers.get(dataFlow).getSelectedDevice();
    }

    /**
     * Refreshes the selected device and gets the new selected device for a
     * specific data flow: capture, notify or playback.
     *
     * @param dataFlow the data flow of the selected device to retrieve:
     * capture, notify or playback.
     * @return the selected device for the specified <tt>dataFlow</tt>
     */
    public Device getAndRefreshSelectedDevice(DataFlow dataFlow)
    {
        return deviceListManagers.get(dataFlow).getAndRefreshSelectedDevice(false);
    }

    /**
     * Gets the indicator which determines whether noise suppression is to be
     * performed for captured audio.
     *
     * @return <tt>true</tt> if noise suppression is to be performed for
     * captured audio; otherwise, <tt>false</tt>
     */
    public boolean isDenoise()
    {
        ConfigurationService cfg = LibJitsi.getConfigurationService();
        boolean value = ((getFeatures() & FEATURE_DENOISE) == FEATURE_DENOISE);

        if (cfg != null)
        {
            value = cfg.global().getBoolean(getPropertyName(PNAME_DENOISE), value);
        }
        return value;
    }

    /**
     * Gets the indicator which determines whether echo cancellation is to be
     * performed for captured audio.
     *
     * @return <tt>true</tt> if echo cancellation is to be performed for
     * captured audio; otherwise, <tt>false</tt>
     */
    public boolean isEchoCancel()
    {
        ConfigurationService cfg = LibJitsi.getConfigurationService();
        boolean value
            = ((getFeatures() & FEATURE_ECHO_CANCELLATION)
                    == FEATURE_ECHO_CANCELLATION);

        if (cfg != null)
        {
            value = cfg.global().getBoolean(getPropertyName(PNAME_ECHOCANCEL), value);
        }
        return value;
    }

    /**
     * {@inheritDoc}
     *
     * Because <tt>AudioSystem</tt> may support playback and notification audio
     * devices apart from capture audio devices, fires more specific
     * <tt>PropertyChangeEvent</tt>s than <tt>DeviceSystem</tt>
     */
    @Override
    protected void postInitialize()
    {
        try
        {
            try
            {
                postInitializeSpecificDevices(DataFlow.CAPTURE);
            }
            finally
            {
                if ((FEATURE_NOTIFY_AND_PLAYBACK_DEVICES & getFeatures()) != 0)
                {
                    try
                    {
                        postInitializeSpecificDevices(DataFlow.NOTIFY);
                    }
                    finally
                    {
                        postInitializeSpecificDevices(DataFlow.PLAYBACK);
                    }
                }
            }
        }
        finally
        {
            super.postInitialize();
        }
    }

    /**
     * Sets the device lists after the different audio systems (PortAudio, PulseAudio, etc) have finished detecting
     * their devices.
     *
     * @param dataFlow the data flow of the devices to perform post-initialization on
     */
    private void postInitializeSpecificDevices(DataFlow dataFlow)
    {
        // Fires a PropertyChangeEvent to enable hot-plugging devices during a call.
        Device selectedActiveDevice = deviceListManagers.get(dataFlow).getAndRefreshSelectedDevice(true);
        sLog.debug("Selected device: " + selectedActiveDevice + ", active devices: " + getActiveDevices(dataFlow));
    }

    /**
     * {@inheritDoc}
     *
     * Removes any capture, playback and notification devices previously
     * detected by this <tt>AudioSystem</tt> and prepares it for the execution
     * of its {@link DeviceSystem#doInitialize()} implementation (which detects
     * all devices to be provided by this instance).
     */
    @Override
    protected void preInitialize()
    {
        super.preInitialize();

        if (deviceListManagers == null)
        {
            deviceListManagers = new HashMap<>(3);
            deviceListManagers.put(DataFlow.CAPTURE, new CaptureDeviceListManager(this));
            deviceListManagers.put(DataFlow.NOTIFY, new NotifyDeviceListManager(this));
            deviceListManagers.put(DataFlow.PLAYBACK, new PlaybackDeviceListManager(this));
        }
    }

    @Override
    public void propertyChange(String property, Object oldValue, Object newValue)
    {
        firePropertyChange(property, oldValue, newValue);
    }

    /**
     * Sets the list of active capture devices.
     *
     * @param captureDevices The list of active capture devices.
     */
    void setCaptureDevices(List<Device> captureDevices)
    {
        deviceListManagers.get(DataFlow.CAPTURE).setActiveDevices(captureDevices);
    }

    /**
     * Selects the active device.
     * @param dataFlow the data flow of the device to set: capture, notify or
     * playback
     * @param device The selected active device.
     */
    public void setSelectedDevice(DataFlow dataFlow, Device device)
    {
        deviceListManagers.get(dataFlow).setSelectedDevice(device);
    }

    /**
     * Sets the list of the active playback/notify devices.
     *
     * @param playbackDevices The list of active playback/notify devices.
     */
    void setPlaybackDevices(List<Device> playbackDevices)
    {
        // The notify devices are the same as the playback devices.
        deviceListManagers.get(DataFlow.PLAYBACK).setActiveDevices(playbackDevices);
        deviceListManagers.get(DataFlow.NOTIFY).setActiveDevices(playbackDevices);
    }
}
