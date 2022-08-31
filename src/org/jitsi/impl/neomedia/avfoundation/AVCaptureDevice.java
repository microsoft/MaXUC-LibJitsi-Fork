/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.avfoundation;

import java.util.*;

import org.jitsi.util.Logger;

/**
 * Represents an AVFoundation capture device which is connected or has been previously
 * connected to the user's computer during the lifetime of the application.
 *
 * @author Lyubomir Marinov
 */
public class AVCaptureDevice
    extends NSObject
{
    private static final Logger sLog = Logger.getLogger(AVCaptureVideoDataOutput.class);

    /**
     * The cached <tt>AVCaptureDevice</tt> instances previously returned by the
     * last call to {@link #inputDevicesWithMediaType(AVMediaType)}.
     */
    private static final Map<AVMediaType, List<AVCaptureDevice>> inputDevices
        = new HashMap<>();

    /**
     * The constant which represents an empty array with
     * <tt>CMFormatDescription</tt> element type. Explicitly defined in order to
     * avoid unnecessary allocations.
     */
    private static final CMFormatDescription[] NO_FORMAT_DESCRIPTIONS
        = new CMFormatDescription[0];

    /**
     * The constant which represents an empty array with
     * <tt>AVCaptureDevice</tt> element type. Explicitly defined in order to
     * avoid unnecessary allocations.
     */
    private static final AVCaptureDevice[] NO_INPUT_DEVICES
        = new AVCaptureDevice[0];

    /**
     * Initializes a new <tt>AVCaptureDevice</tt> instance which is to represent
     * a specific AVFoundation <tt>AVCaptureDevice</tt> object.
     *
     * @param ptr the pointer to the AVFoundation <tt>AVCaptureDevice</tt> object which
     * is to be represented by the new instance
     */
    public AVCaptureDevice(long ptr)
    {
        super(ptr);
    }

    /**
     * Releases application control over this device acquired in the
     * {@link #open()} method.
     */
    public void close()
    {
        sLog.debug("About to call native close method");
        close(getPtr());
        sLog.debug("Finished calling native close method");
    }

    /**
     * Releases application control over a specific AVFoundation
     * <tt>AVCaptureDevice</tt> object acquired in the {@link #open(long)}
     * method.
     *
     * @param ptr the pointer to the AVFoundation <tt>AVCaptureDevice</tt> object to
     * close
     */
    private static native void close(long ptr);

    /**
     * Gets the <tt>AVCaptureDevice</tt> with a specific unique identifier.
     *
     * @param deviceUID the unique identifier of the <tt>AVCaptureDevice</tt> to
     * be retrieved
     * @return the <tt>AVCaptureDevice</tt> with the specified unique identifier
     * if such a <tt>AVCaptureDevice</tt> exists; otherwise, <tt>null</tt>
     */
    public static AVCaptureDevice deviceWithUniqueID(String deviceUID)
    {
        AVCaptureDevice[] inputDevices
            = inputDevicesWithMediaType(AVMediaType.Video);
        AVCaptureDevice deviceWithUniqueID
            = deviceWithUniqueID(deviceUID, inputDevices);

        if (deviceWithUniqueID == null)
        {
            inputDevices = inputDevicesWithMediaType(AVMediaType.Sound);
            deviceWithUniqueID = deviceWithUniqueID(deviceUID, inputDevices);
        }
        return deviceWithUniqueID;
    }

    private static AVCaptureDevice deviceWithUniqueID(
            String deviceUID,
            AVCaptureDevice[] inputDevices)
    {
        if (inputDevices != null)
            for (AVCaptureDevice inputDevice : inputDevices)
                if (deviceUID.equals(inputDevice.uniqueID()))
                    return inputDevice;
        return null;
    }

    /**
     * Called by the garbage collector to release system resources and perform
     * other cleanup.
     *
     * @see Object#finalize()
     */
    @Override
    protected void finalize()
    {
        release();
    }

    public CMFormatDescription[] formatDescriptions()
    {
        long ptr = getPtr();
        sLog.debug("About to call native formatDescriptions method with ptr: " +
                           ptr);
        long[] formatDescriptionPtrs = formatDescriptions(ptr);
        sLog.debug("Received formatDescriptionPtrs from native method: " +
                           formatDescriptionPtrs);
        CMFormatDescription[] formatDescriptions;

        if (formatDescriptionPtrs == null)
            formatDescriptions = NO_FORMAT_DESCRIPTIONS;
        else
        {
            int formatDescriptionCount = formatDescriptionPtrs.length;

            if (formatDescriptionCount == 0)
                formatDescriptions = NO_FORMAT_DESCRIPTIONS;
            else
            {
                formatDescriptions
                    = new CMFormatDescription[formatDescriptionCount];
                for (int i = 0; i < formatDescriptionCount; i++)
                    formatDescriptions[i]
                        = new CMFormatDescription(formatDescriptionPtrs[i]);
            }
        }
        return formatDescriptions;
    }

    private static native long[] formatDescriptions(long ptr);

    public static AVCaptureDevice[] inputDevicesWithMediaType(
            AVMediaType mediaType)
    {
        String name = mediaType.name();
        sLog.debug("About to call native method to get inputDevicePointers for " +
                           name);
        long[] inputDevicePtrs = inputDevicesWithMediaType(name);
        sLog.debug("Got inputDevicePointers: " + inputDevicePtrs);
        int inputDeviceCount
            = (inputDevicePtrs == null) ? 0 : inputDevicePtrs.length;
        AVCaptureDevice[] inputDevicesWithMediaType;

        if (inputDeviceCount == 0)
        {
            inputDevicesWithMediaType = NO_INPUT_DEVICES;
            inputDevices.remove(mediaType);
        }
        else
        {
            inputDevicesWithMediaType = new AVCaptureDevice[inputDeviceCount];

            List<AVCaptureDevice> cachedInputDevicesWithMediaType
                    = inputDevices.computeIfAbsent(mediaType,
                                                   k -> new LinkedList<>());

            for (int i = 0; i < inputDeviceCount; i++)
            {
                long inputDevicePtr = inputDevicePtrs[i];
                AVCaptureDevice inputDevice = null;

                for (AVCaptureDevice cachedInputDevice
                        : cachedInputDevicesWithMediaType)
                    if (inputDevicePtr == cachedInputDevice.getPtr())
                    {
                        inputDevice = cachedInputDevice;
                        break;
                    }
                if (inputDevice == null)
                {
                    inputDevice = new AVCaptureDevice(inputDevicePtr);
                    cachedInputDevicesWithMediaType.add(inputDevice);
                }
                else
                    NSObject.release(inputDevicePtr);
                inputDevicesWithMediaType[i] = inputDevice;
            }

            Iterator<AVCaptureDevice> cachedInputDeviceIter
                = cachedInputDevicesWithMediaType.iterator();

            while (cachedInputDeviceIter.hasNext())
            {
                long cachedInputDevicePtr
                    = cachedInputDeviceIter.next().getPtr();
                boolean remove = true;

                for (long inputDevicePtr : inputDevicePtrs)
                    if (cachedInputDevicePtr == inputDevicePtr)
                    {
                        remove = false;
                        break;
                    }
                if (remove)
                    cachedInputDeviceIter.remove();
            }
        }
        return inputDevicesWithMediaType;
    }

    private static native long[] inputDevicesWithMediaType(String mediaType);

    /**
     * Gets the indicator which determines whether this <tt>AVCaptureDevice</tt>
     * is connected and available to applications.
     *
     * @return <tt>true</tt> if this <tt>AVCaptureDevice</tt> is connected and
     * available to applications; otherwise, <tt>false</tt>
     */
    public boolean isConnected()
    {
        long ptr = getPtr();
        sLog.debug("About to call native isConnected method for ptr: " + ptr);
        boolean isConnected = isConnected(ptr);
        sLog.debug("Finished calling native method - isConnected? " +
                           isConnected);
        return isConnected;
    }

    /**
     * Gets the indicator which determines whether a specific AVFoundation
     * <tt>AVCaptureDevice</tt> object is connected and available to
     * applications.
     *
     * @param ptr the pointer to the AVFoundation <tt>AVCaptureDevice</tt> object which
     * is to get the indicator for
     * @return <tt>true</tt> if the specified AVFoundation <tt>AVCaptureDevice</tt>
     * object is connected and available to applications; otherwise,
     * <tt>false</tt>
     */
    private static native boolean isConnected(long ptr);

    /**
     * Gets the localized human-readable name of this <tt>AVCaptureDevice</tt>.
     *
     * @return the localized human-readable name of this
     * <tt>AVCaptureDevice</tt>
     */
    public String localizedName()
    {
        // Don't add logs to this method as it is called a lot!
        return localizedName(getPtr());
    }

    /**
     * Gets the localized human-readable name of a specific AVFoundation
     * <tt>AVCaptureDevice</tt> object.
     *
     * @param ptr the pointer to the AVFoundation <tt>AVCaptureDevice</tt> object to
     * get the localized human-readable name of
     * @return the localized human-readable name of the specified AVFoundation
     * <tt>AVCaptureDevice</tt> object
     */
    private static native String localizedName(long ptr);

    /**
     * Attempts to give the application control over this
     * <tt>AVCaptureDevice</tt> so that it can be used for capture.
     *
     * @return <tt>true</tt> if this device was opened successfully; otherwise,
     * <tt>false</tt>
     * @throws NSErrorException if this device was not opened successfully and
     * carries an <tt>NSError</tt> describing why this device could not be
     * opened
     */
    public boolean open()
        throws NSErrorException
    {
        long ptr = getPtr();
        sLog.debug("About to call native open method for ptr: " + ptr);
        boolean open = open(ptr);
        sLog.debug("Finished calling native method - open? " + open);
        return open;
    }

    /**
     * Attempts to give the application control over a specific AVFoundation
     * <tt>AVCaptureDevice</tt> object so that it can be used for capture.
     *
     * @param ptr the pointer to the AVFoundation <tt>AVCaptureDevice</tt> to be opened
     * @return <tt>true</tt> if the device was opened successfully; otherwise,
     * <tt>false</tt>
     * @throws NSErrorException if the device was not opened successfully and
     * carries an <tt>NSError</tt> describing why the device could not be opened
     */
    private static native boolean open(long ptr) throws NSErrorException;

    /**
     * Gets the unique identifier of this <tt>AVCaptureDevice</tt>.
     *
     * @return the unique identifier of this <tt>AVCaptureDevice</tt>
     */
    public String uniqueID()
    {
        long ptr = getPtr();
        sLog.debug("About to call native uniqueID method for ptr: " + ptr);
        String uniqueID = uniqueID(ptr);
        sLog.debug("Finished calling native method - uniqueID = " + uniqueID);
        return uniqueID;
    }

    /**
     * Gets the unique identifier of a specific AVFoundation <tt>AVCaptureDevice</tt>
     * object.
     *
     * @param ptr the pointer to the AVFoundation <tt>AVCaptureDevice</tt> object to
     * get the unique identifier of
     * @return the unique identifier of the specified AVFoundation
     * <tt>AVCaptureDevice</tt> object
     */
    private static native String uniqueID(long ptr);
}
