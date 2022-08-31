/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.avfoundation;

/**
 * Represents a CoreVideo <tt>CVPixelBufferRef</tt>.
 *
 * @author Lyubomir Marinov
 */
public class CVPixelBuffer
    extends CVImageBuffer
{
    /**
     * Initializes a new <tt>CVPixelBuffer</tt> instance which is to represent
     * a specific CoreVideo <tt>CVPixelBufferRef</tt>.
     *
     * @param ptr the CoreVideo <tt>CVPixelBufferRef</tt> to be represented by
     * the new instance
     */
    public CVPixelBuffer(long ptr)
    {
        super(ptr);
    }

    /**
     * Returns the size, in bytes, that is required for a buffer to stream the
     * associated CoreVideo <tt>CVPixelBufferRef</tt>.  Note that this is double
     * the number of bytes that AVFoundation tells us this CVPixelBufferRef
     * actually needs, as we have found that, for devices with a smaller pixel
     * buffer, AV Foundation sometimes returns a byte count that is too small.
     * Therefore, if we were to create a buffer of exactly that number of bytes,
     * the client is likely to end up crashing when it runs over the end of the
     * buffer.  To avoid this, we always make the buffer twice the size that AV
     * Foundation says it needs to be.   The returned number of bytes varies
     * between ~0.2MB-3.5MB, so the impact on memory usage is minimal.
     * See SFR 537387.
     *
     * @return @return the size, in bytes, that is required for a buffer to
     * stream the associated CoreVideo <tt>CVPixelBufferRef</tt>.
     */
    public int getByteBufferSize()
    {
        return getByteCount(getPtr()) * 2;
    }

    /**
     * Gets the number of bytes which represent the pixels of a specific
     * CoreVideo <tt>CVPixelBufferRef</tt>.
     *
     * @param ptr the <tt>CVPixelBufferRef</tt> to get the number of bytes which
     * represent its pixels of
     * @return the number of bytes which represent the pixels of the specified
     * CoreVideo <tt>CVPixelBufferRef</tt>
     */
    private static native int getByteCount(long ptr);

    /**
     * Gets a <tt>byte</tt> array which represents the pixels of the associated
     * CoreVideo <tt>CVPixelBufferRef</tt>.
     *
     * @return a <tt>byte</tt> array which represents the pixels of the
     * associated CoreVideo <tt>CVPixelBufferRef</tt>
     */
    public byte[] getBytes()
    {
        return getBytes(getPtr());
    }

    /**
     * Gets a <tt>byte</tt> array which represents the pixels of a specific
     * CoreVideo <tt>CVPixelBufferRef</tt>.
     *
     * @param ptr the <tt>CVPixelBufferRef</tt> to get the pixel bytes of
     * @return a <tt>byte</tt> array which represents the pixels of the
     * specified CoreVideo <tt>CVPixelBufferRef</tt>
     */
    private static native byte[] getBytes(long ptr);

    /**
     * Gets the bytes which represent the pixels of the associated
     * <tt>CVPixelBufferRef</tt> into a specific native byte buffer with a
     * specific capacity.
     *
     * @param buf the native byte buffer to return the bytes into
     * @param bufLength the capacity in bytes of <tt>buf</tt>
     * @return the number of bytes written into <tt>buf</tt>
     */
    public int getBytes(long buf, int bufLength)
    {
        return getBytes(getPtr(), buf, bufLength);
    }

    /**
     * Gets the bytes which represent the pixels of a specific
     * <tt>CVPixelBufferRef</tt> into a specific native byte buffer with a
     * specific capacity.
     *
     * @param ptr the <tt>CVPixelBufferRef</tt> to get the bytes of
     * @param buf the native byte buffer to return the bytes into
     * @param bufLength the capacity in bytes of <tt>buf</tt>
     * @return the number of bytes written into <tt>buf</tt>
     */
    private static native int getBytes(long ptr, long buf, int bufLength);

    /**
     * Gets the height in pixels of this <tt>CVPixelBuffer</tt>.
     *
     * @return the height in pixels of this <tt>CVPixelBuffer</tt>
     */
    public int getHeight()
    {
        return getHeight(getPtr());
    }

    /**
     * Gets the height in pixels of a specific CoreVideo
     * <tt>CVPixelBufferRef</tt>.
     *
     * @param ptr the CoreVideo <tt>CVPixelBufferRef</tt> to get the height in
     * pixels of
     * @return the height in pixels of the specified CoreVideo
     * <tt>CVPixelBufferRef</tt>
     */
    private static native int getHeight(long ptr);

    /**
     * Gets the width in pixels of this <tt>CVPixelBuffer</tt>.
     *
     * @return the width in pixels of this <tt>CVPixelBuffer</tt>
     */
    public int getWidth()
    {
        return getWidth(getPtr());
    }

    /**
     * Gets the width in pixels of a specific CoreVideo
     * <tt>CVPixelBufferRef</tt>.
     *
     * @param ptr the CoreVideo <tt>CVPixelBufferRef</tt> to get the width in
     * pixels of
     * @return the width in pixels of the specified CoreVideo
     * <tt>CVPixelBufferRef</tt>
     */
    private static native int getWidth(long ptr);

    /**
     * Native copy from native pointer <tt>src</tt> to byte array <tt>dst</tt>.
     * @param dst destination array
     * @param dstOffset offset of <tt>dst</tt> to copy data to
     * @param dstLength length of <tt>dst</tt>
     * @param src native pointer source
     */
    public static native void memcpy(
            byte[] dst, int dstOffset, int dstLength,
            long src);
}
