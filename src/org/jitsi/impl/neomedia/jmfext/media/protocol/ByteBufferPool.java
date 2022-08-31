/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.jmfext.media.protocol;

import static org.bytedeco.ffmpeg.global.avcodec.AV_INPUT_BUFFER_PADDING_SIZE;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.jitsi.impl.neomedia.codec.video.NeomediaByteBuffer;
import org.jitsi.util.Logger;

/**
 * Represents a pool of <tt>NeomediaByteBuffer</tt>s which reduces the allocations and
 * deallocations of <tt>NeomediaByteBuffer</tt>s in the Java heap and of native memory
 * in the native heap.
 *
 * @author Lyubomir Marinov
 */
public class ByteBufferPool
{
    private static final Logger logger = Logger.getLogger(ByteBufferPool.class);

    /**
     * The <tt>NeomediaByteBuffer</tt>s which are managed by this
     * <tt>NeomediaByteBuffer</tt>.
     */
    private final List<PooledByteBuffer> buffers
        = new ArrayList<>();

    /**
     * Drains this <tt>ByteBufferPool</tt> i.e. frees the <tt>NeomediaByteBuffer</tt>s
     * that it contains.
     */
    public synchronized void drain()
    {
        for (Iterator<PooledByteBuffer> i = buffers.iterator(); i.hasNext();)
        {
            PooledByteBuffer buffer = i.next();

            i.remove();
            buffer.doFree();
        }
    }

    /**
     * Gets a <tt>NeomediaByteBuffer</tt> out of this pool of <tt>NeomediaByteBuffer</tt>s which
     * is capable to receiving at least <tt>capacity</tt> number of bytes.
     *
     * @param capacity the minimal number of bytes that the returned
     * <tt>NeomediaByteBuffer</tt> is to be capable of receiving
     * @return a <tt>NeomediaByteBuffer</tt> which is ready for writing captured media
     * data into and which is capable of receiving at least <tt>capacity</tt>
     * number of bytes
     */
    public synchronized NeomediaByteBuffer getBuffer(int capacity)
    {
        // XXX Pad with AV_INPUT_BUFFER_PADDING_SIZE or all hell will break loose.
        capacity += AV_INPUT_BUFFER_PADDING_SIZE;

        NeomediaByteBuffer buffer = null;

        for (Iterator<PooledByteBuffer> i = buffers.iterator(); i.hasNext();)
        {
            NeomediaByteBuffer aBuffer = i.next();

            if (aBuffer.getCapacity() >= capacity)
            {
                i.remove();
                buffer = aBuffer;
                break;
            }
        }
        if (buffer == null)
        {
            logger.debug("No existing buffer found in list of " + buffers.size() +
                              " buffers, so creating new buffer with capacity " + capacity);
            buffer = new PooledByteBuffer(capacity, this);
        }

        return buffer;
    }

    /**
     * Returns a specific <tt>NeomediaByteBuffer</tt> into this pool of
     * <tt>NeomediaByteBuffer</tt>s.
     *
     * @param buffer the <tt>NeomediaByteBuffer</tt> to be returned into this pool of
     * <tt>NeomediaByteBuffer</tt>s
     */
    private synchronized void returnBuffer(final PooledByteBuffer buffer)
    {
        if (!buffers.contains(buffer))
        {
            buffers.add(buffer);
        }
    }

    /**
     * Implements a <tt>NeomediaByteBuffer</tt> which is pooled in a
     * <tt>ByteBufferPool</tt> in order to reduce the numbers of allocations
     * and deallocations of <tt>NeomediaByteBuffer</tt>s and their respective native
     * memory.
     */
    private static class PooledByteBuffer
        extends NeomediaByteBuffer
    {
        /**
         * The <tt>ByteBufferPool</tt> in which this instance is pooled and in
         * which it should returns upon {@link #free()}.
         */
        private final WeakReference<ByteBufferPool> pool;

        public PooledByteBuffer(final int capacity, final ByteBufferPool pool)
        {
            super(capacity);

            this.pool = new WeakReference<>(pool);
        }

        /**
         * Invokes {@link NeomediaByteBuffer#free()} i.e. does not make any attempt to
         * return this instance to the associated <tt>ByteBufferPool</tt> and
         * frees the native memory represented by this instance.
         */
        void doFree()
        {
            super.free();
        }

        /**
         * {@inheritDoc}
         *
         * Returns this <tt>NeomediaByteBuffer</tt> and, respectively, the native memory
         * that it represents to the associated <tt>ByteBufferPool</tt>. If the
         * <tt>ByteBufferPool</tt> has already been finalized by the garbage
         * collector, frees the native memory represented by this instance.
         */
        @Override
        public void free()
        {
            final ByteBufferPool pool = this.pool.get();

            if (pool == null)
                doFree();
            else
                pool.returnBuffer(this);
        }
    }
}
