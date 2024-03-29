/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.codec.video;

import static org.bytedeco.ffmpeg.global.avutil.*;

import org.bytedeco.javacpp.BytePointer;

import org.jitsi.util.Logger;

/**
 * Represents a buffer of native memory with a specific size/capacity which may
 * contain a specific number of bytes of valid data. If the memory represented
 * by a <tt>NeomediaByteBuffer</tt> instance has been allocated by the
 * <tt>NeomediaByteBuffer</tt> instance itself, the native memory will automatically be
 * freed upon finalization.
 *
 * Note: in JITSI this was called ByteBuffer, but for MaX UC we have renamed it
 * to avoid confusion with java.nio.ByteBuffer, which is also in use.  This class
 * is now a thin wrapper around JavaCPP's BytePointer - TODO to see whether we
 * can remove this class altogether.
 */
public class NeomediaByteBuffer
{
    private static final Logger logger = Logger.getLogger(NeomediaByteBuffer.class);

    /**
     * The maximum number of bytes which may be written into the native memory
     * represented by this instance. If <tt>0</tt>, this instance has been
     * initialized to provide read-only access to the native memory it
     * represents and will not deallocate it upon finalization.
     */
    private int capacity;

    /**
     * The number of bytes of valid data that the native memory represented by
     * this instance contains.
     */
    private int length;

    /**
     * The pointer to the native memory represented by this instance.
     */
    private BytePointer ptr;

    /**
     * Save the string representation of the ptr to track double frees.
     */
    private String freedPtr;

    /**
     * Initializes a new <tt>NeomediaByteBuffer</tt> instance with a specific
     * <tt>capacity</tt> of native memory. The new instance allocates the native
     * memory and automatically frees it upon finalization.
     *
     * @param capacity the maximum number of bytes which can be written into the
     * native memory represented by the new instance
     */
    public NeomediaByteBuffer(final int capacity)
    {
        if (capacity < 1)
            throw new IllegalArgumentException("NeomediaByteBuffer requires capacity of at least 1, " +
                                               "got: " + capacity);

        this.ptr = new BytePointer(av_malloc(capacity)).capacity(capacity);
        if (this.ptr == null)
        {
            logger.error(
                    "Unable to allocate memory for ByteBuffer size:" +
                            capacity);
            throw new OutOfMemoryError("av_malloc(" + capacity + ")");
        }
        else
        {
            logger.debug(
                    "Allocating native FFmpeg memory for new ByteBuffer with capacity " +
                            capacity + " bytes for ptr " + ptr);
            this.capacity = capacity;
            this.length = 0;
        }
    }

    /**
     * Initializes a new <tt>NeomediaByteBuffer</tt> instance which is to represent a
     * specific block of native memory. Since the specified native memory has
     * been allocated outside the new instance, the new instance will not
     * automatically free it.
     *
     * @param ptr a pointer to the block of native memory to be represented by
     * the new instance
     */
    public NeomediaByteBuffer(final BytePointer ptr)
    {
        this.ptr = ptr;

        this.capacity = 0;
        this.length = 0;
    }

    /**
     * {@inheritDoc}
     *
     * Frees the native memory represented by this instance if the native memory
     * has been allocated by this instance and has not been freed yet i.e.
     * ensures that {@link #free()} is invoked on this instance.
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
     * Frees the native memory represented by this instance if the native memory
     * has been allocated by this instance and has not been freed yet.
     */
    public synchronized void free()
    {
        if (freedPtr != null)
        {
            logger.debug("Additional free call for " + freedPtr);
        }
        else
        {
            logger.debug("Freeing " + capacity +
                         " bytes of native FFmpeg memory for ptr " + ptr);
            if ((capacity != 0) && (ptr != null))
            {
                // We save the string representation of the ptr to track double frees
                freedPtr = ptr.toString();
                av_free(ptr);
                capacity = 0;
                ptr = null;
            }
        }
    }

    /**
     * Gets the maximum number of bytes which may be written into the native
     * memory represented by this instance. If <tt>0</tt>, this instance has
     * been initialized to provide read-only access to the native memory it
     * represents and will not deallocate it upon finalization.
     *
     * @return the maximum number of bytes which may be written into the native
     * memory represented by this instance
     */
    public synchronized int getCapacity()
    {
        return capacity;
    }

    /**
     * Gets the number of bytes of valid data that the native memory represented
     * by this instance contains.
     *
     * @return the number of bytes of valid data that the native memory
     * represented by this instance contains
     */
    public int getLength()
    {
        return length;
    }

    /**
     * Gets the pointer to the native memory represented by this instance.
     *
     * @return the pointer to the native memory represented by this instance
     */
    public synchronized BytePointer getPtr()
    {
        return ptr;
    }

    /**
     * Sets the number of bytes of valid data that the native memory represented
     * by this instance contains.
     *
     * @param length the number of bytes of valid data that the native memory
     * represented by this instance contains
     * @throws IllegalArgumentException if <tt>length</tt> is a negative value
     */
    public void setLength(final int length)
    {
        if (length < 0)
        {
            throw new IllegalArgumentException("length");
        }

        this.length = length;
    }
}
