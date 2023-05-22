/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
// Portions (c) Microsoft Corporation. All rights reserved.
package org.jitsi.impl.neomedia.device;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.media.CaptureDeviceInfo;
import javax.media.CaptureDeviceManager;
import javax.media.Format;
import javax.media.MediaLocator;
import javax.media.Renderer;
import javax.media.format.AudioFormat;
import javax.media.format.VideoFormat;

import org.jitsi.impl.neomedia.MediaServiceImpl;
import org.jitsi.service.neomedia.MediaType;
import org.jitsi.util.Logger;
import org.jitsi.util.event.PropertyChangeNotifier;

/**
 * Represents the base of a supported device system/backend such as DirectShow,
 * PortAudio, PulseAudio, AVFoundation, video4linux2. A <tt>DeviceSystem</tt> is
 * initialized at a certain time (usually, during the initialization of the
 * <tt>MediaService</tt> implementation which is going to use it) and it
 * registers with FMJ the <tt>CaptureDevice</tt>s it will provide. In addition
 * to providing the devices for the purposes of capture, a <tt>DeviceSystem</tt>
 * also provides the devices on which playback is to be performed i.e. it acts
 * as a <tt>Renderer</tt> factory via its {@link #createRenderer(boolean)}
 * method.
 */
public abstract class DeviceSystem
    extends PropertyChangeNotifier
{
    /**
     * The <tt>Logger</tt> used by the <tt>DeviceSystem</tt> class and its
     * instances for logging output.
     */
    private static final Logger sLog = Logger.getLogger(DeviceSystem.class);

    public static final String PROP_DEVICES = "devices";

    /**
     * The (base) name of the <tt>ConfigurationService</tt> property which
     * indicates whether noise suppression is to be performed for the captured
     * audio.
     */
    public static final String PNAME_DENOISE = "denoise";

    /**
     * The (base) name of the <tt>ConfigurationService</tt> property which
     * indicates whether noise cancellation is to be performed for the captured
     * audio.
     */
    public static final String PNAME_ECHOCANCEL = "echocancel";

    /**
     * The constant/flag (to be) returned by {@link #getFeatures()} in order to
     * indicate that the respective <tt>DeviceSystem</tt> supports invoking its
     * {@link #initialize()} more than once.
     */
    public static final int FEATURE_REINITIALIZE = 1;

    /**
     * Track whether a thread has been scheduled to call DeviceSystem.initialize(), but has not yet run.
     * Access must be synchronized, hence why it's an AtomicBoolean.
     */
    public AtomicBoolean mDeviceSystemInitThreadPending = new AtomicBoolean(false);

    /**
     * The list of <tt>CaptureDeviceInfo</tt>s representing the devices of this
     * instance at the time its {@link #preInitialize()} method was last
     * invoked.
     */
    private List<CaptureDeviceInfo> mPreInitializeDevices;

    /**
     * The set of flags indicating which optional features are supported by this
     * <tt>DeviceSystem</tt>. For example, the presence of the flag
     * {@link #FEATURE_REINITIALIZE} indicates that this instance is able to
     * deal with multiple consecutive invocations of its {@link #initialize()}
     * method.
     */
    private final int mFeatures;

    /**
     * The protocol of the <tt>MediaLocator</tt> of the
     * <tt>CaptureDeviceInfo</tt>s (to be) registered (with FMJ) by this
     * <tt>DeviceSystem</tt>. The protocol is a unique identifier of a
     * <tt>DeviceSystem</tt>.
     */
    private final String mLocatorProtocol;

    /**
     * The <tt>MediaType</tt> of this <tt>DeviceSystem</tt> i.e. the type of the
     * media that this instance supports for capture and playback such as audio
     * or video.
     */
    private final MediaType mMediaType;

    protected DeviceSystem(MediaType mediaType, String locatorProtocol)
        throws Exception
    {
        this(mediaType, locatorProtocol, 0);
    }

    protected DeviceSystem(
            MediaType mediaType,
            String locatorProtocol,
            int features)
        throws Exception
    {
        if (mediaType == null)
            throw new NullPointerException("mediaType");
        if (locatorProtocol == null)
            throw new NullPointerException("locatorProtocol");

        mMediaType = mediaType;
        mLocatorProtocol = locatorProtocol;
        mFeatures = features;

        DeviceSystemManager.invokeDeviceSystemInitialize(this);
    }

    /**
     * Initializes a new <tt>Renderer</tt> instance which is to perform playback
     * on a device contributed by this system.
     *
     * @return a new <tt>Renderer</tt> instance which is to perform playback on
     * a device contributed by this system or <tt>null</tt>
     */
    public Renderer createRenderer()
    {
        String className = getRendererClassName();

        if (className != null)
        {
            try
            {
                return (Renderer) Class.forName(className).newInstance();
            }
            catch (Throwable t)
            {
                if (t instanceof ThreadDeath)
                    throw (ThreadDeath) t;
                else
                {
                    sLog.error("Failed to initialize a new " + className
                                + " instance", t);
                }
            }
        }
        return null;
    }

    /**
     * Invoked by {@link #initialize()} to perform the very logic of the
     * initialization of this <tt>DeviceSystem</tt>. This instance has been
     * prepared for initialization by an earlier call to
     * {@link #preInitialize()} and the initialization will be completed with a
     * subsequent call to {@link #postInitialize()}.
     *
     * @throws Exception if an error occurs during the initialization of this
     * instance. The initialization of this instance will be completed with a
     * subsequent call to <tt>postInitialize()</tt> regardless of any
     * <tt>Exception</tt> thrown by <tt>doInitialize()</tt>.
     */
    protected abstract void doInitialize()
        throws Exception;

    /**
     * Gets the flags indicating the optional features supported by this
     * <tt>DeviceSystem</tt>.
     *
     * @return the flags indicating the optional features supported by this
     * <tt>DeviceSystem</tt>. The possible flags are among the
     * <tt>FEATURE_XXX</tt> constants defined by the <tt>DeviceSystem</tt> class
     * and its extenders.
     */
    public final int getFeatures()
    {
        return mFeatures;
    }

    /**
     * Returns the format depending on the media type: AudioFormat for AUDIO,
     * VideoFormat for VIDEO. Otherwise, returns null.
     *
     * @return The format depending on the media type: AudioFormat for AUDIO,
     * VideoFormat for VIDEO. Otherwise, returns null.
     */
    public Format getFormat()
    {
        Format format = null;

        switch (getMediaType())
        {
        case AUDIO:
            format = new AudioFormat(null);
            break;
        case VIDEO:
            format = new VideoFormat(null);
            break;
        default:
            format = null;
            break;
        }

        return format;
    }

    /**
     * Gets the protocol of the <tt>MediaLocator</tt>s of the
     * <tt>CaptureDeviceInfo</tt>s (to be) registered (with FMJ) by this
     * <tt>DeviceSystem</tt>. The protocol is a unique identifier of a
     * <tt>DeviceSystem</tt>.
     *
     * @return the protocol of the <tt>MediaLocator</tt>s of the
     * <tt>CaptureDeviceInfo</tt>s (to be) registered (with FMJ) by this
     * <tt>DeviceSystem</tt>
     */
    public final String getLocatorProtocol()
    {
        return mLocatorProtocol;
    }

    public final MediaType getMediaType()
    {
        return mMediaType;
    }

    /**
     * Gets the name of the class which implements the <tt>Renderer</tt>
     * interface to render media on a playback or notification device associated
     * with this <tt>DeviceSystem</tt>. Invoked by
     * {@link #createRenderer}.
     *
     * @return the name of the class which implements the <tt>Renderer</tt>
     * interface to render media on a playback or notification device associated
     * with this <tt>DeviceSystem</tt> or <tt>null</tt> if no <tt>Renderer</tt>
     * instance is to be created by the <tt>DeviceSystem</tt> implementation or
     * <tt>createRenderer(boolean) is overridden.
     */
    protected String getRendererClassName()
    {
        return null;
    }

    /**
     * Initializes this <tt>DeviceSystem</tt> i.e. represents the native/system
     * devices in the terms of the application so that they may be utilized. For
     * example, the capture devices are represented as
     * <tt>CaptureDeviceInfo</tt> instances registered with FMJ.
     * <p>
     * <b>Note</b>: The method is synchronized on this instance in order to
     * guarantee that the whole initialization procedure (which includes
     * {@link #doInitialize()}) executes once at any given time.
     * </p>
     *
     * @throws Exception if an error occurs during the initialization of this
     * <tt>DeviceSystem</tt>
     */
    protected final synchronized void initialize()
        throws Exception
    {
        preInitialize();
        try
        {
            doInitialize();
        }
        finally
        {
            postInitialize();
        }
    }

    /**
     * Invoked as part of the execution of {@link #initialize()} after the
     * execution of {@link #doInitialize()} regardless of whether the latter
     * completed successfully. The implementation of <tt>DeviceSystem</tt> fires
     * a new <tt>PropertyChangeEvent</tt> to notify that the value of the
     * property {@link #PROP_DEVICES} of this instance may have changed i.e.
     * that the list of devices detected by this instance may have changed.
     */
    protected void postInitialize()
    {
        try
        {
            Format format = getFormat();

            if (format != null)
            {
                /*
                 * Calculate the lists of old and new devices and report them in
                 * a PropertyChangeEvent about PROP_DEVICES.
                 */
                List<CaptureDeviceInfo> cdis
                    = CaptureDeviceManager.getDeviceList(format);
                sLog.debug("postInitialize:cdis1 (" + cdis.size() + "): " + cdis);
                sLog.debug("postInitialize:getLocatorProtocol(): " + getLocatorProtocol());

                // Eliminate either audio or video devices.
                cdis = filterDeviceListByLocatorProtocol(
                        cdis, getLocatorProtocol());
                sLog.debug("postInitialize:cdis2 (" + cdis.size() + "): " + cdis);

                List<CaptureDeviceInfo> postInitializeDevices
                    = new ArrayList<>(cdis);

                if (mPreInitializeDevices != null)
                {
                    for (Iterator<CaptureDeviceInfo> preIter
                                = mPreInitializeDevices.iterator();
                            preIter.hasNext();)
                    {
                        if (postInitializeDevices.remove(preIter.next()))
                            preIter.remove();
                    }
                }

                /*
                 * Fire a PropertyChangeEvent but only if there is an actual
                 * change in the value of the property.
                 */
                int preInitializeDeviceCount
                    = (mPreInitializeDevices == null)
                        ? 0
                        : mPreInitializeDevices.size();
                int postInitializeDeviceCount
                    = (postInitializeDevices == null)
                        ? 0
                        : postInitializeDevices.size();

                sLog.debug("preCount: " + preInitializeDeviceCount +
                           ", postCount:" + postInitializeDeviceCount);

                if ((preInitializeDeviceCount != 0)
                        || (postInitializeDeviceCount != 0))
                {
                    firePropertyChange(
                            PROP_DEVICES,
                            mPreInitializeDevices,
                            postInitializeDevices);
                }
            }
        }
        finally
        {
            mPreInitializeDevices = null;
        }
    }

    /**
     * Returns a <tt>List</tt> of <tt>CaptureDeviceInfo</tt>s which are elements
     * of a specific <tt>List</tt> of <tt>CaptureDeviceInfo</tt>s and have a
     * specific <tt>MediaLocator</tt> protocol.
     *
     * @param deviceList the <tt>List</tt> of <tt>CaptureDeviceInfo</tt> which
     * are to be filtered based on the specified <tt>MediaLocator</tt> protocol
     * @param locatorProtocol the protocol of the <tt>MediaLocator</tt>s of the
     * <tt>CaptureDeviceInfo</tt>s which are to be returned
     * @return a <tt>List</tt> of <tt>CaptureDeviceInfo</tt>s which are elements
     * of the specified <tt>deviceList</tt> and have the specified
     * <tt>locatorProtocol</tt>
     */
    private List<CaptureDeviceInfo> filterDeviceListByLocatorProtocol(
            List<CaptureDeviceInfo> deviceList,
            String locatorProtocol)
    {
        if ((deviceList != null) && (deviceList.size() > 0))
        {
            Iterator<CaptureDeviceInfo> deviceListIter = deviceList.iterator();

            while (deviceListIter.hasNext())
            {
                MediaLocator locator = deviceListIter.next().getLocator();

                if ((locator == null)
                        || !locatorProtocol.equalsIgnoreCase(
                                locator.getProtocol()))
                {
                    deviceListIter.remove();
                }
            }
        }
        return deviceList;
    }

    /**
     * Invoked as part of the execution of {@link #initialize()} before the
     * execution of {@link #doInitialize()}. The implementation of
     * <tt>DeviceSystem</tt> removes from FMJ's <tt>CaptureDeviceManager</tt>
     * the <tt>CaptureDeviceInfo</tt>s whose <tt>MediaLocator</tt> has the same
     * protocol as {@link #getLocatorProtocol()} of this instance.
     */
    protected void preInitialize()
    {
        Format format = getFormat();

        if (format != null)
        {
            List<CaptureDeviceInfo> cdis
                = CaptureDeviceManager.getDeviceList(format);

            sLog.debug("preInitialize:cdis1 (" + cdis.size() + "): " + cdis);
            sLog.debug("preInitialize:getLocatorProtocol(): " + getLocatorProtocol());

            // Eliminate either audio or video devices.
            cdis = filterDeviceListByLocatorProtocol(
                    cdis, getLocatorProtocol());
            sLog.debug("preInitialize:cdis2 (" + cdis.size() + "): " + cdis);

            mPreInitializeDevices = new ArrayList<>(cdis);

            if ((cdis != null) && (cdis.size() > 0))
            {
                boolean commit = false;

                for (CaptureDeviceInfo cdi
                        : filterDeviceListByLocatorProtocol(
                                cdis,
                                getLocatorProtocol()))
                {
                    CaptureDeviceManager.removeDevice(cdi);
                    commit = true;
                }
                if (commit && !MediaServiceImpl.isJmfRegistryDisableLoad())
                {
                    try
                    {
                        CaptureDeviceManager.commit();
                    }
                    catch (IOException ioe)
                    {
                        /*
                         * We do not really need commit but we have it for
                         * historical reasons.
                         */
                        sLog.debug("Failed to commit CaptureDeviceManager",
                            ioe);
                    }
                }
            }
        }
    }

    /**
     * Returns a human-readable representation of this <tt>DeviceSystem</tt>.
     * The implementation of <tt>DeviceSystem</tt> returns the protocol of the
     * <tt>MediaLocator</tt>s of the <tt>CaptureDeviceInfo</tt>s (to be)
     * registered by this <tt>DeviceSystem</tt>.
     *
     * @return a <tt>String</tt> which represents this <tt>DeviceSystem</tt> in
     * a human-readable form
     */
    @Override
    public String toString()
    {
        return getLocatorProtocol();
    }
}
