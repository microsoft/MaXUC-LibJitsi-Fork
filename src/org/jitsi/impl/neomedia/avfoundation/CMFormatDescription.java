/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.avfoundation;

import java.awt.*;

/**
 * Describes the media format of media samples and of media sources, such as
 * devices and capture connections. Includes basic information about the media,
 * such as media type and format type (or codec type), as well as extended
 * information specific to each media type.
 *
 * @author Lyubomir Marinov
 */
public class CMFormatDescription
    extends NSObject
{
    /**
     * Initializes a new <tt>CMFormatDescription</tt> instance which is to
     * represent a specific AVFoundation <tt>CMFormatDescription</tt> object.
     *
     * @param ptr the pointer to the AVFoundation <tt>CMFormatDescription</tt> object
     * which is to be represented by the new instance
     */
    public CMFormatDescription(long ptr)
    {
        super(ptr);
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

    public Dimension sizeForKey()
    {
        return sizeForKey(getPtr());
    }

    private static native Dimension sizeForKey(long ptr);

    // private static native String VideoEncodedPixelsSizeAttribute();
}
