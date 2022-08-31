/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.codec.video;

import static org.bytedeco.ffmpeg.global.avutil.*;

import java.awt.Dimension;

import javax.media.Buffer;
import javax.media.Format;

import org.bytedeco.ffmpeg.avutil.AVFrame;

/**
 * Represents a pointer to a native FFmpeg <tt>AVFrame</tt> object.
 *
 * @author Lyubomir Marinov
 */
public class AVFrameWrapper
{
    public static int read(final Buffer buffer, final Format format, final NeomediaByteBuffer data)
    {
        final AVFrameFormat frameFormat = (AVFrameFormat) format;

        final Object o = buffer.getData();
        final AVFrameWrapper frame;

        if (o instanceof AVFrameWrapper)
            frame = (AVFrameWrapper) o;
        else
        {
            frame = new AVFrameWrapper();
            buffer.setData(frame);
        }

        return frame.fillImage(data, frameFormat);
    }

    /**
     * The <tt>NeomediaByteBuffer</tt> whose native memory is set on the native
     * counterpart of this instance/<tt>AVFrame</tt>.
     */
    private NeomediaByteBuffer data;

    /**
     * The indicator which determines whether the native memory represented by
     * this instance is to be freed upon finalization.
     */
    private boolean free;

    /**
     * The native FFmpeg <tt>AVFrame</tt> object represented by
     * this instance.
     */
    private AVFrame nativeFrame;

    /**
     * Initializes a new <tt>AVFrameWrapper</tt> instance which is to
     * allocate a new native FFmpeg <tt>AVFrame</tt> and represent it.
     */
    public AVFrameWrapper()
    {
        this.nativeFrame = av_frame_alloc();
        if (this.nativeFrame == null)
            throw new OutOfMemoryError("Could not allocate using av_frame_alloc()");

        this.free = true;
    }

    /**
     * Initializes a new <tt>AVFrame</tt> instance which is to represent a
     * specific pointer to a native FFmpeg <tt>AVFrame</tt> object. Because the
     * native memory/<tt>AVFrame</tt> has been allocated outside the new
     * instance, the new instance does not automatically free it upon
     * finalization.
     *
     * @param nativeFrame the native FFmpeg <tt>AVFrame</tt> object to be
     * represented by the new instance
     */
    public AVFrameWrapper(final AVFrame nativeFrame)
    {
        if (nativeFrame == null)
            throw new IllegalArgumentException("Cannot create AVFrameWrapper with null nativeFrame argument");

        this.nativeFrame = nativeFrame;
        this.free = false;
    }

    public synchronized int fillImage(
            final NeomediaByteBuffer data,
            final AVFrameFormat format)
    {
        final Dimension size = format.getSize();
        final int ret
            = av_image_fill_arrays(
                nativeFrame.data(),
                nativeFrame.linesize(),
                data.getPtr(),
                format.getPixFmt(),
                size.width,
                size.height,
                1);

        if (ret >= 0)
        {
            if (this.data != null)
                this.data.free();

            this.data = data;
        }
        return ret;
    }

    /**
     * Deallocates the native memory/FFmpeg <tt>AVFrame</tt> object represented
     * by this instance if this instance has allocated it upon initialization
     * and it has not been deallocated yet i.e. ensures that {@link #free()} is
     * invoked on this instance.
     *
     * @see Object#finalize()
     */
    @Override
    protected void finalize()
        throws Throwable
    {
        try
        {
            free();
        }
        finally
        {
            super.finalize();
        }
    }

    /**
     * Deallocates the native memory/FFmpeg <tt>AVFrame</tt> object represented
     * by this instance if this instance has allocated it upon initialization
     * and it has not been deallocated yet.
     */
    public synchronized void free()
    {
        if (free && (nativeFrame != null))
        {
            av_frame_free(nativeFrame);
            free = false;
            nativeFrame = null;
        }

        if (data != null)
        {
            data.free();
            data = null;
        }
    }

    /**
     * Gets the <tt>NeomediaByteBuffer</tt> whose native memory is set on the native
     * counterpart of this instance/<tt>AVFrame</tt>.
     *
     * @return the <tt>NeomediaByteBuffer</tt> whose native memory is set on the native
     * counterpart of this instance/<tt>AVFrame</tt>.
     */
    public synchronized NeomediaByteBuffer getData()
    {
        return data;
    }

    /**
     * @return the native FFmpeg <tt>AVFrame</tt> object
     * represented by this instance
     */
    public synchronized AVFrame getNativeFrame()
    {
        return nativeFrame;
    }
}
