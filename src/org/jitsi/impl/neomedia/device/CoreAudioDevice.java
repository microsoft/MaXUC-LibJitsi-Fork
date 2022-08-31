/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.device;

import org.jitsi.util.CustomAnnotations;
import org.jitsi.util.Logger;
import org.jitsi.util.OSUtils;
import org.jitsi.util.StringUtils;

/**
 * JNI link to the MacOsX / Windows CoreAudio library.
 *
 * @author Vincent Lucas
 */
public class CoreAudioDevice
{
    /**
     * The <tt>Logger</tt> used by the <tt>CoreAudioDevice</tt> class and
     * its instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(CoreAudioDevice.class);

    /**
     * Tells if the CoreAudio library used by this CoreAudioDevice is correctly
     * loaded: if we are under a supported operating system.
     */
    public static boolean isLoaded;

    /*
     * Loads CoreAudioDevice now if we are running Windows.  For Mac, wait to
     * load the library until we are about to use it to avoid trying to load it
     * multiple times.
     */
    static
    {
        if (OSUtils.IS_WINDOWS)
        {
            logger.info("Windows so try to Core audio library now");
            loadAudioLibrary();
        }
    }

    static synchronized void loadAudioLibrary()
    {
        if (!isLoaded)
        {
            try
            {
                if (OSUtils.IS_MAC)
                {
                    logger.info("About to load Mac Core audio library");
                    System.loadLibrary("jnmaccoreaudio");
                    isLoaded = true;
                    logger.info("Finished loading Mac Core audio library");
                }
                else if (OSUtils.IS_WINDOWS)
                {
                    logger.info("About to load Win Core audio library");
                    System.loadLibrary("jnwincoreaudio");
                    isLoaded = true;
                    logger.info("Finished loading Win Core audio library");
                }
            }
            catch (NullPointerException | UnsatisfiedLinkError | SecurityException npe)
            {
                /*
                 * Swallow whatever exceptions are known to be thrown by
                 * System.loadLibrary() because the class has to be loaded in order
                 * to not prevent the loading of its users and isLoaded will remain
                 * false eventually.
                 */
                logger.warn("Failed to load CoreAudioDevice library: ", npe);
            }
        }
        else
        {
            logger.info("Not loading CoreAudioDevice library as it is already loaded");
        }
    }

    public static native void freeDevices();

    public static String getDeviceModelIdentifier(String deviceUID)
    {
        // Prevent an access violation in getDeviceModelIdentifierBytes.
        if (deviceUID == null)
            throw new NullPointerException("deviceUID");

        byte[] deviceModelIdentifierBytes
            = getDeviceModelIdentifierBytes(deviceUID);

        return StringUtils.newString(deviceModelIdentifierBytes);
    }

    public static native byte[] getDeviceModelIdentifierBytes(
            String deviceUID);

    public static String getDeviceName(
            String deviceUID)
    {
        byte[] deviceNameBytes = getDeviceNameBytes(deviceUID);
        return StringUtils.newString(deviceNameBytes);
    }

    public static native byte[] getDeviceNameBytes(
            String deviceUID);

    public static native float getInputDeviceVolume(
            String deviceUID);

    public static native float getOutputDeviceVolume(
            String deviceUID);

    public static native int initDevices();

    public static native int setInputDeviceVolume(
            String deviceUID,
            float volume);

    public static native int setOutputDeviceVolume(
            String deviceUID,
            float volume);

    private static Runnable devicesChangedCallback;

    /**
     * Implements a callback which gets called by the native coreaudio
     * counterpart to notify the Java counterpart that the list of devices has
     * changed.
     */
    @CustomAnnotations.CalledFromNativeCode
    public static void devicesChangedCallback()
    {
        // Take local ref for multi-threading reasons
        Runnable devicesChangedCallbackLocalRef = CoreAudioDevice.devicesChangedCallback;

        // On Mac, this callback can get invoked on the AppKit thread.
        // We should get off this thread ASAP to free it up for other
        // things that might want to use it for OS integration (e.g. Swing/AWT)
        // as otherwise we could get deadlocks.
        //
        // Sadly, we can't use the ThreadingService here because we're in
        // libjitsi.
        if (devicesChangedCallbackLocalRef != null)
        {
            logger.info("About to run devicesChangedCallback on to-be-created thread");
            new Thread("devicesChangedCallback")
            {
                @Override
                public void run()
                {
                    logger.debug("About to run devicesChangedCallback on created thread");
                    devicesChangedCallbackLocalRef.run();
                }
            }.start();
        }
        else
        {
            logger.warn("Cannot notify of change - devicesChangedCallback is null");
        }
    }

    public static void setDevicesChangedCallback(
            Runnable devicesChangedCallback)
    {
        logger.debug("Setting devicesChangeCallback to: " + devicesChangedCallback);
        CoreAudioDevice.devicesChangedCallback = devicesChangedCallback;
    }

    public static void log(byte[] message)
    {
        String messageString = StringUtils.newString(message);
        logger.info(messageString);
    }
}
