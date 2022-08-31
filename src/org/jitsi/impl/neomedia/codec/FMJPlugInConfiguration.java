/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.codec;

import java.io.*;
import java.util.*;

import javax.media.*;

import org.jitsi.impl.neomedia.*;
import org.jitsi.util.*;

/**
 * Utility class that handles registration of JMF packages and plugins.
 *
 * @author Damian Minkov
 * @author Lyubomir Marinov
 * @author Boris Grozev
 */
public class FMJPlugInConfiguration
{
    /**
     * Whether custom codecs have been registered with JFM
     */
    private static boolean codecsRegistered = false;

    /**
     * The additional custom JMF codecs.
     */
    private static final String[] CUSTOM_CODECS
        = {
            // Don't bother loading up codecs that we don't actually support.
            // This speeds up media cut-through as we don't need to create
            // en- and de-coders for each one every time we create a media stack
            // for a call.
            "org.jitsi.impl.neomedia.codec.audio.alaw.DePacketizer",
            "org.jitsi.impl.neomedia.codec.audio.alaw.JavaEncoder",
            "org.jitsi.impl.neomedia.codec.audio.alaw.Packetizer",
            "org.jitsi.impl.neomedia.codec.audio.ulaw.JavaDecoder",
            "org.jitsi.impl.neomedia.codec.audio.ulaw.JavaEncoder",
            "org.jitsi.impl.neomedia.codec.audio.ulaw.Packetizer",
// TODO - We will want to add OPUS support in the future.
//            "org.jitsi.impl.neomedia.codec.audio.opus.JNIDecoder",
//            "org.jitsi.impl.neomedia.codec.audio.opus.JNIEncoder",
            "org.jitsi.impl.neomedia.codec.audio.speex.SpeexResampler",
            "net.java.sip.communicator.impl.neomedia.codec.audio.g722.JNIDecoder",
            "net.java.sip.communicator.impl.neomedia.codec.audio.g722.JNIEncoder",
            "org.jitsi.impl.neomedia.codec.audio.silk.JavaDecoder",
            "org.jitsi.impl.neomedia.codec.audio.silk.JavaEncoder",
            "org.jitsi.impl.neomedia.codec.video.h264.DePacketizer",
            "org.jitsi.impl.neomedia.codec.video.h264.JNIDecoder",
            "org.jitsi.impl.neomedia.codec.video.h264.JNIEncoder",
            "org.jitsi.impl.neomedia.codec.video.h264.Packetizer",
            "org.jitsi.impl.neomedia.codec.video.SwScale"
        };

    /**
     * The package prefixes of the additional JMF <tt>DataSource</tt>s (e.g. low
     * latency PortAudio and ALSA <tt>CaptureDevice</tt>s).
     */
    private static final String[] CUSTOM_PACKAGES
        = {
            "org.jitsi.impl.neomedia.jmfext",
            "net.java.sip.communicator.impl.neomedia.jmfext",
            "net.sf.fmj"
        };

    /**
     * The <tt>Logger</tt> used by the <tt>FMJPlugInConfiguration</tt> class
     * for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(FMJPlugInConfiguration.class);

    /**
     * Whether custom packages have been registered with JFM
     */
    private static boolean packagesRegistered = false;

    /**
     * Register in JMF the custom codecs we provide
     */
    public static void registerCustomCodecs()
    {
        if(codecsRegistered)
            return;

        logger.debug("Get registered plugins");

        // Register the custom codecs which haven't already been registered.
        Collection<String> registeredPlugins
                = new HashSet<>(
                PlugInManager.getPlugInList(
                        null,
                        null,
                        PlugInManager.CODEC));
        boolean commit = false;

        logger.debug("Remove unwanted codecs");

        // Remove JavaRGBToYUV.
        PlugInManager.removePlugIn(
                "com.sun.media.codec.video.colorspace.JavaRGBToYUV",
                PlugInManager.CODEC);
        PlugInManager.removePlugIn(
                "com.sun.media.codec.video.colorspace.JavaRGBConverter",
                PlugInManager.CODEC);
        PlugInManager.removePlugIn(
                "com.sun.media.codec.video.colorspace.RGBScaler",
                PlugInManager.CODEC);

        // Remove JMF's H263 codec.
        PlugInManager.removePlugIn(
                "com.sun.media.codec.video.vh263.NativeDecoder",
                PlugInManager.CODEC);
        PlugInManager.removePlugIn(
                "com.ibm.media.codec.video.h263.NativeEncoder",
                PlugInManager.CODEC);

        // Remove JMF's GSM codec. As working only on some OS.
        String gsmCodecPackage = "com.ibm.media.codec.audio.gsm.";
        String[] gsmCodecClasses
                = new String[]
                {
                        "JavaDecoder",
                        "JavaDecoder_ms",
                        "JavaEncoder",
                        "JavaEncoder_ms",
                        "NativeDecoder",
                        "NativeDecoder_ms",
                        "NativeEncoder",
                        "NativeEncoder_ms",
                        "Packetizer"
                };
        for(String gsmCodecClass : gsmCodecClasses)
        {
            PlugInManager.removePlugIn(
                    gsmCodecPackage + gsmCodecClass,
                    PlugInManager.CODEC);
        }

        /*
         * Remove FMJ's JavaSoundCodec because it seems to slow down the
         * building of the filter graph and we do not currently seem to need it.
         */
        PlugInManager.removePlugIn(
                "net.sf.fmj.media.codec.JavaSoundCodec",
                PlugInManager.CODEC);

        logger.debug("Register custom codecs");

        for (String className : CUSTOM_CODECS)
        {
            if (registeredPlugins.contains(className))
            {
                logger.debug("Codec " + className + " is already registered");
            }
            else
            {
                commit = true;

                boolean registered;
                Throwable exception = null;

                try
                {
                    Codec codec = (Codec)
                            Class.forName(className).newInstance();

                    registered =
                            PlugInManager.addPlugIn(
                                    className,
                                    codec.getSupportedInputFormats(),
                                    codec.getSupportedOutputFormats(null),
                                    PlugInManager.CODEC);
                }
                catch (Throwable ex)
                {
                    registered = false;
                    exception = ex;
                }
                if (registered)
                {
                    logger.debug("Codec "
                                    + className
                                    + " is successfully registered");
                }
                else
                {
                    logger.debug("Codec "
                                    + className
                                    + " is NOT successfully registered",
                                    exception);
                }
            }
        }

        /*
         * If Jitsi provides a codec which is also provided by FMJ and/or JMF,
         * use Jitsi's version.
         */
        Vector<String> codecs
                = PlugInManager.getPlugInList(null, null, PlugInManager.CODEC);

        if (codecs != null)
        {
            boolean setPlugInList = false;

            logger.debug("Replace JMF codecs with custom versions");

            for (int i = CUSTOM_CODECS.length - 1; i >= 0; i--)
            {
                String className = CUSTOM_CODECS[i];

                int classNameIndex = codecs.indexOf(className);

                if (classNameIndex != -1)
                {
                    logger.debug("Replace: " + className);
                    codecs.remove(classNameIndex);
                    codecs.add(0, className);
                    setPlugInList = true;
                }
            }

            if (setPlugInList)
                PlugInManager.setPlugInList(codecs, PlugInManager.CODEC);
        }

        if (commit && !MediaServiceImpl.isJmfRegistryDisableLoad())
        {
            try
            {
                PlugInManager.commit();
            }
            catch (IOException ex)
            {
                logger.error("Cannot commit to PlugInManager", ex);
            }
        }

        codecsRegistered = true;
        logger.debug("Finished registering custom codecs");
    }

    /**
     * Register in JMF the custom packages we provide
     */
    public static void registerCustomPackages()
    {
        if (packagesRegistered)
            return;

        logger.debug("Get packages");

        Vector<String> packages = PackageManager.getProtocolPrefixList();

        logger.debug("Add custom packages");

        for (String customPackage : CUSTOM_PACKAGES)
        {
            /*
             * Linear search in a loop but it doesn't have to scale since the
             * list is always short.
             */
            if (!packages.contains(customPackage))
            {
                packages.add(customPackage);
                logger.debug("Adding package  : " + customPackage);
            }
        }

        PackageManager.setProtocolPrefixList(packages);
        PackageManager.commitProtocolPrefixList();

        logger.debug("Registering new protocol prefix list: " + packages);

        packagesRegistered = true;
        logger.debug("Finished registering custom packages");
    }
}
