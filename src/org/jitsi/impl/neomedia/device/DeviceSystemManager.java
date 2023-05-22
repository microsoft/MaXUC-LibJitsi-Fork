/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
// Portions (c) Microsoft Corporation. All rights reserved.
package org.jitsi.impl.neomedia.device;

import java.lang.reflect.*;
import java.util.*;

import org.jitsi.impl.neomedia.*;
import org.jitsi.service.configuration.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.util.*;
import org.jitsi.util.event.*;

/**
 * Static methods for managing DeviceSystem instances.
 */
public final class DeviceSystemManager
    extends PropertyChangeNotifier
{
    /**
     * The <tt>Logger</tt> used by the <tt>DeviceSystemManager</tt> class and its
     * instances for logging output.
     */
    private static final Logger sLog =
        Logger.getLogger(DeviceSystemManager.class);

    /**
     * The list of <tt>DeviceSystem</tt>s which have been initialized.
     */
    private static List<DeviceSystem> mDeviceSystems =
            new LinkedList<>();

    public static DeviceSystem[] getDeviceSystems(MediaType mediaType)
    {
        List<DeviceSystem> ret;

        synchronized (mDeviceSystems)
        {
            ret = new ArrayList<>(mDeviceSystems.size());
            for (DeviceSystem deviceSystem : mDeviceSystems)
                if (deviceSystem.getMediaType().equals(mediaType))
                    ret.add(deviceSystem);
        }
        return ret.toArray(new DeviceSystem[ret.size()]);
    }

    /**
     * Initializes the <tt>DeviceSystem</tt> instances which are to represent
     * the supported device systems/backends such as DirectShow, PortAudio,
     * PulseAudio, AVFoundation, video4linux2. The method may be invoked multiple
     * times. If a <tt>DeviceSystem</tt> has been initialized by a previous
     * invocation of the method, its {@link DeviceSystem#initialize()} method will be called
     * again as part of the subsequent invocation only if the
     * <tt>DeviceSystem</tt> in question returns a set of flags from its
     * {@link DeviceSystem#getFeatures()} method which contains the constant/flag
     * {@link DeviceSystem#FEATURE_REINITIALIZE}.
     */
    public static void initializeDeviceSystems()
    {
        ConfigurationService cfg = LibJitsi.getConfigurationService();

        /*
         * Detect the audio capture devices unless the configuration explicitly
         * states that they are to not be detected.
         */
        if (((cfg == null)
                || !cfg.user().getBoolean(
                        MediaServiceImpl.DISABLE_AUDIO_SUPPORT_PNAME,
                        false))
            && !Boolean.getBoolean(
                    MediaServiceImpl.DISABLE_AUDIO_SUPPORT_PNAME))
        {
            sLog.info("Initializing audio devices");

            initializeAudioDeviceSystems();
        }

        /*
         * Detect the video capture devices unless the configuration explicitly
         * states that they are to not be detected.
         */
        if ((cfg == null)
                || !cfg.user().getBoolean(
                        MediaServiceImpl.DISABLE_VIDEO_SUPPORT_PNAME,
                        false))
        {
            sLog.info("Initializing video devices");

            initializeVideoDeviceSystems();
        }
    }

    /**
     * Initializes the <tt>AudioSystem</tt> instances which are to represent
     * the supported audio device systems/backends which are to capable of
     * capturing and playing back audio.
     */
    private static void initializeAudioDeviceSystems()
    {
        /*
         * The list of supported AudioSystem implementations is hard-coded. The
         * order of the classes is significant and represents a decreasing
         * preference with respect to which AudioSystem is to be picked up as
         * the default one.
         */
        String[] classNames = new String[]
                {
                    OSUtils.IS_WINDOWS ? ".WASAPISystem" : null,
                    OSUtils.IS_MAC ? ".MacCoreaudioSystem" : null,
                    ".NoneAudioSystem"
                };

        initializeDeviceSystems(classNames);
    }

    /**
     * Initializes the <tt>VideoSystem</tt> instances which are to represent
     * the supported video device systems/backends which are to capable of
     * capturing video.
     */
    public static void initializeVideoDeviceSystems()
    {
        /*
         * The list of supported VideoSystem implementations is hard-coded.
         * There is only one VideoSystem for each OS.
         */
        String[] classNames = new String[]
                {
                    OSUtils.IS_MAC ? ".AVFoundationSystem" : null,
                    OSUtils.IS_WINDOWS ? ".DirectShowSystem" : null
                };

        initializeDeviceSystems(classNames);
    }

    /**
     * Initializes the <tt>DeviceSystem</tt> instances specified by the names of
     * the classes which implement them. If a <tt>DeviceSystem</tt> instance has
     * already been initialized for a specific class name, no new instance of
     * the class in question will be initialized and rather the
     * {@link DeviceSystem#initialize()} method of the existing <tt>DeviceSystem</tt>
     * instance will be invoked if the <tt>DeviceSystem</tt> instance returns a
     * set of flags from its {@link DeviceSystem#getFeatures()} which contains
     * {@link DeviceSystem#FEATURE_REINITIALIZE}.
     *
     * @param classNames the names of the classes which extend the
     * <tt>DeviceSystem</tt> class and instances of which are to be initialized
     */
    private static void initializeDeviceSystems(String[] classNames)
    {
        synchronized (mDeviceSystems)
        {
            String packageName = null;

            for (String className : classNames)
            {
                if (className == null)
                    continue;

                if (className.startsWith("."))
                {
                    if (packageName == null)
                        packageName = DeviceSystem.class.getPackage().getName();
                    className = packageName + className;
                }

                // Initialize a single instance per className.
                DeviceSystem deviceSystem = null;

                for (DeviceSystem aDeviceSystem : mDeviceSystems)
                    if (aDeviceSystem.getClass().getName().equals(className))
                    {
                        deviceSystem = aDeviceSystem;
                        break;
                    }

                boolean reinitialize;

                if (deviceSystem == null)
                {
                    reinitialize = false;

                    Object o = null;

                    try
                    {
                        o = Class.forName(className).newInstance();
                    }
                    catch (Throwable t)
                    {
                        if (t instanceof ThreadDeath)
                            throw (ThreadDeath) t;
                        else
                        {
                            sLog.warn("Failed to initialize " + className, t);
                        }
                    }
                    if (o instanceof DeviceSystem)
                    {
                        deviceSystem = (DeviceSystem) o;
                        if (!mDeviceSystems.contains(deviceSystem))
                            mDeviceSystems.add(deviceSystem);
                    }
                }
                else
                    reinitialize = true;

                // Reinitializing is an optional feature.
                if (reinitialize
                        && ((deviceSystem.getFeatures() & DeviceSystem.FEATURE_REINITIALIZE)
                                != 0))
                {
                    try
                    {
                        sLog.debug("initialising device systems");
                        invokeDeviceSystemInitialize(deviceSystem);
                    }
                    catch (Throwable t)
                    {
                        if (t instanceof ThreadDeath)
                            throw (ThreadDeath) t;
                        else
                        {
                            sLog.warn("Failed to reinitialize " + className, t);
                        }
                    }
                }
            }
        }
    }

    /**
     * Invokes {@link DeviceSystem#initialize()} on a specific <tt>DeviceSystem</tt>. The
     * method returns after the invocation returns.
     *
     * @param deviceSystem the <tt>DeviceSystem</tt> to invoke
     * <tt>initialize()</tt> on
     * @throws Exception if an error occurs during the initialization of
     * <tt>initialize()</tt> on the specified <tt>deviceSystem</tt>
     */
    static void invokeDeviceSystemInitialize(DeviceSystem deviceSystem)
        throws Exception
    {
        invokeDeviceSystemInitialize(deviceSystem, false, false);
    }

    /**
     * Invokes {@link DeviceSystem#initialize()} on a specific <tt>DeviceSystem</tt>.
     *
     * @param deviceSystem the <tt>DeviceSystem</tt> to invoke
     * <tt>initialize()</tt> on
     * @param asynchronous <tt>true</tt> if the invocation is to be performed in
     * a separate thread and the method is to return immediately without waiting
     * for the invocation to return; otherwise, <tt>false</tt>
     * @throws Exception if an error occurs during the initialization of
     * <tt>initialize()</tt> on the specified <tt>deviceSystem</tt>
     */
    static void invokeDeviceSystemInitialize(
            final DeviceSystem deviceSystem,
            boolean asynchronous,
            boolean reinitialize)
        throws Exception
    {
        sLog.info("Invoking device system initialize");
        boolean isVideoSystem = (deviceSystem instanceof VideoSystem);

        if (reinitialize && !isVideoSystem)
        {
            // We are reinitializing an audio system.  We don't get any
            // notification from the OS when a video device is plugged/unplugged,
            // so use the audio notification to also reinitialize the video
            // system.  This will handle all video devices that include an
            // audio device (e.g. webcam with inbuilt microphone).
            sLog.info("Reinitializing video system");
            reinitializeVideoSystem(asynchronous);
        }

        if (OSUtils.IS_WINDOWS || asynchronous)
        {
            /*
             * The use of Component Object Model (COM) technology is common on
             * Windows. The initialization of the COM library is done per
             * thread. However, there are multiple concurrency models which may
             * interfere among themselves. Dedicate a new thread on which the
             * COM library has surely not been initialized per invocation of
             * initialize().
             */
            if (deviceSystem.mDeviceSystemInitThreadPending.getAndSet(true))
            {
                sLog.debug("Drop out: another thread is about to call " +
                           "DeviceSystem.initialize() for: " + deviceSystem);
                return;
            }

            sLog.info("Create a thread to call DeviceSystem.initialize() for: " +
                      deviceSystem);

            final Throwable[] threadException = new Throwable[1];

            Thread thread = startDeviceSystemInitThread(
                    deviceSystem,
                    asynchronous && isVideoSystem,
                    threadException);

            if (!asynchronous)
            {
                waitForDeviceSystemInitThreadToExit(thread, threadException);
            }
        }
        else
        {
            sLog.debug("Initializing device system");
            deviceSystem.initialize();
        }
    }

    /**
     * @param asynchronous <tt>true</tt> if the invocation is to be performed in
     * a separate thread and the method is to return immediately without waiting
     * for the invocation to return; otherwise, <tt>false</tt>
     */
    private static void reinitializeVideoSystem(boolean asynchronous)
        throws Exception
    {
        // Find the VideoSystem, if any.  There will be zero or one.
        DeviceSystem videoSystem = null;

        for (DeviceSystem dsx : mDeviceSystems)
        {
            if (dsx instanceof VideoSystem)
            {
                videoSystem = dsx;
                break;
            }
        }

        if (videoSystem != null)
        {
            invokeDeviceSystemInitialize(videoSystem, asynchronous, true);
        }
    }

    /**
     * Windows only.  Start a new thread to initialize the supplied
     * DeviceSystem.
     * @param deviceSystem The DeviceSystem to initialize.
     * @param delayStart Whether to delay starting initialization.
     * @param threadException Output parameter - if the thread threw an
     *        exception, this is it.
     * @return A new thread.  Always valid.
     */
    private static Thread startDeviceSystemInitThread(
            final DeviceSystem deviceSystem,
            final boolean delayStart,
            Throwable[] threadException)
    {
        final String className = deviceSystem.getClass().getName();

        Thread thread = new Thread()
            {
                @Override
                public void run()
                {
                    sLog.debug("Thread started, delay:" + delayStart);

                    // Delay starting in case another "device (un)plugged"
                    // notification is received immediately after the
                    // notification that triggered us to start this thread.
                    if (delayStart)
                    {
                        try
                        {
                            // Trial and error has shown that with a shorter
                            // duration, when we reinitialize video after
                            // detecting a microphone plugged event, the camera
                            // sometimes isn't detected.  The longer the
                            // duration, the worse the user experience.
                            Thread.sleep(1000);
                        }
                        catch (Exception e)
                        {
                        }
                        sLog.debug("Thread running now after 1 second delay");
                    }

                    synchronized (deviceSystem)
                    {
                        // From now on, another "device (un)plugged" notification
                        // is almost certainly from a DIFFERENT physical device
                        // being plugged/unplugged, so we will want that to
                        // create a new instance of this thread to initialize
                        // the DeviceSystem again once this thread has finished.
                        deviceSystem.mDeviceSystemInitThreadPending.set(false);

                        boolean loggerIsTraceEnabled = sLog.isTraceEnabled();

                        // Calling initialize() below synchronizes on the DeviceSystem. We've just got the lock, so don't
                        // give it up before calling that. This means that the setting of mDeviceSystemInitThreadPending
                        // to false, above, signals initialize() is ready to be called.
                        try
                        {
                            if (loggerIsTraceEnabled)
                            {
                                sLog.trace("Will initialize " + className);
                            }

                            deviceSystem.initialize();

                            if (loggerIsTraceEnabled)
                            {
                                sLog.trace("Did initialize " + className);
                            }
                        }
                        catch (Throwable t)
                        {
                            threadException[0] = t;
                            if (t instanceof ThreadDeath)
                            {
                                throw (ThreadDeath) t;
                            }
                        }
                        sLog.debug("Thread exit");
                    }
                }
            };

        thread.setName(className + ".initialize() [" + thread.getId() + "]");
        thread.setDaemon(true);
        thread.start();

        return thread;
    }

    /**
     * Wait for DeviceSystem.initialize() to return.  This means waiting for
     * the thread created by startDeviceSystemInitThread() to die.
     * @param thread The thread to wait on.
     * @param threadException If the thread threw an exception, this is it.
     */
    private static void waitForDeviceSystemInitThreadToExit(
            Thread thread, Throwable[] threadException)
        throws Exception
    {
        boolean interrupted = false;

        while (thread.isAlive())
        {
            try
            {
                thread.join();
            }
            catch (InterruptedException ie)
            {
                interrupted = true;
            }
        }
        if (interrupted)
        {
            Thread.currentThread().interrupt();
        }

        /* Re-throw any exception thrown by the thread. */
        Throwable t = threadException[0];

        if (t != null)
        {
            if (t instanceof Exception)
            {
                throw (Exception) t;
            }
            else
            {
                throw new UndeclaredThrowableException(t);
            }
        }
    }
}
