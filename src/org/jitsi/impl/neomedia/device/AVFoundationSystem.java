/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.device;

import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_ARGB;

import java.util.ArrayList;
import java.util.List;
import javax.media.CaptureDeviceManager;
import javax.media.Format;
import javax.media.MediaLocator;
import javax.media.format.RGBFormat;

import org.jitsi.impl.neomedia.MediaServiceImpl;
import org.jitsi.impl.neomedia.avfoundation.AVCaptureDevice;
import org.jitsi.impl.neomedia.avfoundation.AVMediaType;
import org.jitsi.impl.neomedia.avfoundation.CMFormatDescription;
import org.jitsi.impl.neomedia.codec.video.AVFrameFormat;
import org.jitsi.util.Logger;

/**
 * Discovers and registers AVFoundation capture devices with JMF.
 *
 * @author Lyubomir Marinov
 */
public class AVFoundationSystem
    extends VideoSystem
{
    /**
     * The <tt>Logger</tt> used by the <tt>AVFoundationSystem</tt> class and its
     * instances for logging output.
     */
    private static final Logger logger = Logger.getLogger(AVFoundationSystem.class);

    /**
     * The protocol of the <tt>MediaLocator</tt>s identifying AVFoundation
     * capture devices.
     */
    private static final String LOCATOR_PROTOCOL = LOCATOR_PROTOCOL_AVFOUNDATION;

    /**
     * Initializes a new <tt>AVFoundationSystem</tt> instance which discovers and
     * registers AVFoundation capture devices with JMF.
     *
     * @throws Exception if anything goes wrong while discovering and
     * registering AVFoundation capture defines with JMF
     */
    public AVFoundationSystem()
        throws Exception
    {
        super(LOCATOR_PROTOCOL, 0);
    }

    @Override
    protected void doInitialize()
        throws Exception
    {
        AVCaptureDevice[] inputDevices
            = AVCaptureDevice.inputDevicesWithMediaType(AVMediaType.Video);
        boolean captureDeviceInfoIsAdded = false;

        List<Device> videoDevices =
                new ArrayList<>(inputDevices != null ?
                                        inputDevices.length : 0);

        for (AVCaptureDevice inputDevice : inputDevices)
        {
            Device device
                = new Device(
                        inputDevice.localizedName(),
                        new MediaLocator(
                                LOCATOR_PROTOCOL
                                    + ':'
                                    + inputDevice.uniqueID()),
                        new Format[]
                                {
                                    new AVFrameFormat(AV_PIX_FMT_ARGB),
                                    new RGBFormat()
                                },
                        inputDevice.uniqueID(),
                        null,
                        null);
            videoDevices.add(device);

            for(CMFormatDescription f : inputDevice.formatDescriptions())
            {
                logger.info(
                        "Webcam available resolution for "
                            + inputDevice.localizedName()
                            + ":"
                            + f.sizeForKey());
            }

            CaptureDeviceManager.addDevice(device);
            captureDeviceInfoIsAdded = true;
            logger.debug("Added CaptureDeviceInfo " + device);
        }
        if (captureDeviceInfoIsAdded
                && !MediaServiceImpl.isJmfRegistryDisableLoad())
        {
            CaptureDeviceManager.commit();
        }

        setVideoDevices(videoDevices);
    }
}
