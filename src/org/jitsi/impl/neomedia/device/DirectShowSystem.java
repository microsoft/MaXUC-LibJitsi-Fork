/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
// Portions (c) Microsoft Corporation. All rights reserved.
package org.jitsi.impl.neomedia.device;

import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_NONE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.media.CaptureDeviceManager;
import javax.media.Format;
import javax.media.MediaLocator;

import org.jitsi.impl.neomedia.MediaServiceImpl;
import org.jitsi.impl.neomedia.codec.video.AVFrameFormat;
import org.jitsi.impl.neomedia.jmfext.media.protocol.directshow.DSCaptureDevice;
import org.jitsi.impl.neomedia.jmfext.media.protocol.directshow.DSFormat;
import org.jitsi.impl.neomedia.jmfext.media.protocol.directshow.DSManager;
import org.jitsi.impl.neomedia.jmfext.media.protocol.directshow.DataSource;
import org.jitsi.util.Logger;

/**
 * Discovers and registers DirectShow video capture devices with JMF.
 *
 * @author Sebastien Vincent
 * @author Lyubomir Marinov
 */
public class DirectShowSystem
    extends VideoSystem
{
    /**
     * The protocol of the <tt>MediaLocator</tt>s identifying AVFoundation
     * capture devices.
     */
    private static final String LOCATOR_PROTOCOL = LOCATOR_PROTOCOL_DIRECTSHOW;

    /**
     * The <tt>Logger</tt> used by the <tt>DirectShowSystem</tt> class and its
     * instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(DirectShowSystem.class);

    /**
     * Constructor. Discover and register DirectShow capture devices
     * with JMF.
     *
     * @throws Exception if anything goes wrong while discovering and
     * registering DirectShow capture defines with JMF
     */
    public DirectShowSystem()
        throws Exception
    {
        super(LOCATOR_PROTOCOL, 0);
    }

    @Override
    protected void doInitialize()
        throws Exception
    {
        List<Device> videoDevices = null;
        DSManager manager = new DSManager();

        try
        {
            DSCaptureDevice devices[] = manager.getCaptureDevices();
            boolean captureDeviceInfoIsAdded = false;

            int numDevices = (devices == null) ? 0 : devices.length;
            videoDevices = new ArrayList<>(numDevices);

            for(int i = 0; i < numDevices; i++)
            {
                DSCaptureDevice device = devices[i];
                DSFormat[] dsFormats = device.getSupportedFormats();
                String name = device.getName();

                if (dsFormats.length == 0)
                {
                    logger.warn(
                            "Camera '" + name
                                + "' reported no supported formats.");
                    continue;
                }

                List<Format> formats
                    = new ArrayList<>(dsFormats.length);

                for (DSFormat dsFormat : dsFormats)
                {
                    int pixelFormat = dsFormat.getPixelFormat();
                    int ffmpegPixFmt = DataSource.getFFmpegPixFmt(pixelFormat);

                    if (ffmpegPixFmt != AV_PIX_FMT_NONE)
                    {
                        Format format
                            = new AVFrameFormat(ffmpegPixFmt, pixelFormat);

                        if (!formats.contains(format))
                            formats.add(format);
                    }
                }
                if (formats.isEmpty())
                {
                    logger.warn(
                            "No support for the formats of camera '" + name
                                + "': " + Arrays.toString(dsFormats));
                    continue;
                }

                Format[] formatsArray
                    = formats.toArray(new Format[formats.size()]);

                logger.info("Support for the formats of camera '" + name
                                + "': " + Arrays.toString(formatsArray));

                Device cdi
                    = new Device(
                            name,
                            new MediaLocator(LOCATOR_PROTOCOL + ':' + name),
                            formatsArray,
                            device.getPath(),
                            null,
                            null);

                videoDevices.add(cdi);
                CaptureDeviceManager.addDevice(cdi);
                captureDeviceInfoIsAdded = true;
            }

            if (captureDeviceInfoIsAdded
                    && !MediaServiceImpl.isJmfRegistryDisableLoad())
            {
                CaptureDeviceManager.commit();
            }
        }
        finally
        {
            manager.dispose();
        }

        setVideoDevices(videoDevices);
    }
}
