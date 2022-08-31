/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.avfoundation;

import org.jitsi.util.Logger;

/**
 * Represents an AVFoundation <tt>AVCaptureVideoDataOutput</tt> object.
 *
 * @author Lyubomir Marinov
 */
public class AVCaptureVideoDataOutput
    extends AVCaptureOutput
{
    private static final Logger sLog = Logger.getLogger(AVCaptureVideoDataOutput.class);

    /**
     * Initializes a new <tt>AVCaptureVideoDataOutput</tt> which
     * represents a new AVFoundation <tt>AVCaptureVideoDataOutput</tt> object.
     */
    public AVCaptureVideoDataOutput()
    {
        this(allocAndInit());
    }

    /**
     * Initializes a new <tt>AVCaptureVideoDataOutput</tt> which is to
     * represent a new AVFoundation <tt>AVCaptureVideoDataOutput</tt> object.
     *
     * @param ptr the pointer to the AVFoundation
     * <tt>AVCaptureVideoDataOutput</tt> object to be represented by the
     * new instance
     */
    public AVCaptureVideoDataOutput(long ptr)
    {
        super(ptr);
    }

    private static native long allocAndInit();

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

    /**
     * Gets the minimum time interval between which this
     * <tt>AVCaptureVideoDataOutput</tt> will output consecutive video
     * frames.
     *
     * @return the minimum time interval between which this
     * <tt>AVCaptureVideoDataOutput</tt> will output consecutive video
     * frames. It is equivalent to the inverse of the maximum frame rate. The
     * value of <tt>0</tt> indicates an unlimited maximum frame rate.
     */
    public double videoMinFrameDuration()
    {
        long ptr = getPtr();
        sLog.debug(
                "About to call native method to get videoMinFrameDuration for ptr: " +
                        ptr);
        double videoMinFrameDuration = videoMinFrameDuration(ptr);
        sLog.debug("Finished calling native method - videoMinFrameDuration = " +
                           videoMinFrameDuration);
        return videoMinFrameDuration;
    }

    /**
     * Gets the minimum time interval between which a specific
     * <tt>AVCaptureVideoDataOutput</tt> instance will output
     * consecutive video frames.
     *
     * @param ptr a pointer to the <tt>AVCaptureVideoDataOutput</tt>
     * instance to get the minimum time interval between consecutive video frame
     * output of
     * @return the minimum time interval between which a specific
     * <tt>AVCaptureVideoDataOutput</tt> instance will output
     * consecutive video frames. It is equivalent to the inverse of the maximum
     * frame rate. The value of <tt>0</tt> indicates an unlimited maximum frame
     * rate.
     */
    private static native double videoMinFrameDuration(long ptr);

    public NSDictionary pixelBufferAttributes()
    {
        long ptr = getPtr();
        sLog.debug("About to call native method to get Pixel Buffer Attributes for ptr: " + ptr);
        long pixelBufferAttributesPtr = pixelBufferAttributes(ptr);
        sLog.debug("Finished calling native method - pixelBufferAttributesPtr = " +
                           pixelBufferAttributesPtr);

        NSDictionary attributesDict = (pixelBufferAttributesPtr == 0)
                ? null
                : new NSDictionary(pixelBufferAttributesPtr);

        sLog.debug("Got attributes Dict: " + attributesDict);

        return attributesDict;
    }

    private static native long pixelBufferAttributes(long ptr);

    public boolean setAlwaysDiscardsLateVideoFrames(
            boolean alwaysDiscardsLateVideoFrames)
    {
        sLog.debug("About to call native method to setAlwaysDiscardsLateVideoFrames = " +
                           alwaysDiscardsLateVideoFrames);
        boolean setAlwaysDiscardsLateVideoFrames = setAlwaysDiscardsLateVideoFrames(
                getPtr(),
                alwaysDiscardsLateVideoFrames);
        sLog.debug("Finished calling native method - success? " +
                           setAlwaysDiscardsLateVideoFrames);
        return setAlwaysDiscardsLateVideoFrames;
    }

    private static native boolean setAlwaysDiscardsLateVideoFrames(
            long ptr,
            boolean alwaysDiscardsLateVideoFrames);

    public void setDelegate(Delegate delegate)
    {
        sLog.debug("About to call native method to set delegate to " + delegate);
        setDelegate(getPtr(), delegate);
        sLog.debug("Finished calling native method to set delegate.");
    }

    private static native void setDelegate(long ptr, Delegate delegate);

    /**
     * Sets the minimum time interval between which this
     * <tt>AVCaptureVideoDataOutput</tt> is to output consecutive video
     * frames.
     *
     * @param videoMinFrameDuration the minimum time interval between which
     * this <tt>AVCaptureVideoDataOutput</tt> is to output consecutive
     * video frames. It is equivalent to the inverse of the maximum frame rate.
     * The value of <tt>0</tt> indicates an unlimited frame rate.
     */
    public void setVideoMinFrameDuration(double videoMinFrameDuration)
    {
        sLog.debug("About to call native method to set videoMinFrameDuration to " +
                           videoMinFrameDuration);
        setVideoMinFrameDuration(getPtr(), videoMinFrameDuration);
        sLog.debug("Finished calling native method to set videoMinFrameDuration.");
    }

    /**
     * Sets the minimum time interval between which a specific
     * <tt>AVCaptureVideoDataOutput</tt> instance is to output
     * consecutive video frames.
     *
     * @param ptr a pointer to the <tt>AVCaptureVideoDataOutput</tt>
     * instance to set the minimum time interval between consecutive video frame
     * output on
     * @param videoMinFrameDuration the minimum time interval between which
     * a specific <tt>AVCaptureVideoDataOutput</tt> instance is to
     * output consecutive video frames. It is equivalent to the inverse of the
     * maximum frame rate. The value of <tt>0</tt> indicates an unlimited frame
     * rate.
     */
    private static native void setVideoMinFrameDuration(
            long ptr,
            double videoMinFrameDuration);

    public void setPixelBufferAttributes(NSDictionary pixelBufferAttributes)
    {
        sLog.debug("About to call native method to set pixelBufferAttributes to " +
                           pixelBufferAttributes);
        setPixelBufferAttributes(getPtr(), pixelBufferAttributes.getPtr());
        sLog.debug("Finished calling native method to set pixelBufferAttributes.");
    }

    private static native void setPixelBufferAttributes(
            long ptr,
            long pixelBufferAttributesPtr);

    /**
     * Represents the receiver of <tt>CVImageBuffer</tt> video frames and their
     * associated <tt>CMSampleBuffer</tt>s captured by a
     * <tt>AVCaptureVideoDataOutput</tt>.
     */
    public abstract static class Delegate
    {
        private MutableCMSampleBuffer sampleBuffer;

        private MutableCVPixelBuffer videoFrame;

        /**
         * Notifies this <tt>Delegate</tt> that the <tt>AVCaptureOutput</tt> to
         * which it is set has output a specific <tt>CVImageBuffer</tt>
         * representing a video frame with a specific <tt>CMSampleBuffer</tt>.
         *
         * @param videoFrame the <tt>CVImageBuffer</tt> which represents the
         * output video frame
         * @param sampleBuffer the <tt>CMSampleBuffer</tt> which represents
         * additional details about the output video samples
         */
        public abstract void outputVideoFrameWithSampleBuffer(
                CVImageBuffer videoFrame,
                CMSampleBuffer sampleBuffer);

        void outputVideoFrameWithSampleBuffer(
                long videoFramePtr,
                long sampleBufferPtr)
        {
            if (videoFrame == null)
            {
                sLog.debug("Creating new MutableCVPixelBuffer with ptr " +
                                   videoFramePtr);
                videoFrame = new MutableCVPixelBuffer(videoFramePtr);
            }
            else
            {
                videoFrame.setPtr(videoFramePtr);
            }

            if (sampleBuffer == null)
            {
                sLog.debug("Creating new MutableCMSampleBuffer with ptr " +
                                   sampleBufferPtr);
                sampleBuffer = new MutableCMSampleBuffer(sampleBufferPtr);
            }
            else
            {
                sampleBuffer.setPtr(sampleBufferPtr);
            }

            outputVideoFrameWithSampleBuffer(videoFrame, sampleBuffer);
        }
    }

    /**
     * Represents a <tt>CVPixelBuffer</tt> which allows public changing of the
     * CoreVideo <tt>CVPixelBufferRef</tt> it represents.
     */
    private static class MutableCVPixelBuffer
        extends CVPixelBuffer
    {
        /**
         * Initializes a new <tt>MutableCVPixelBuffer</tt> which is to represent
         * a specific CoreVideo <tt>CVPixelBufferRef</tt>.
         *
         * @param ptr the CoreVideo <tt>CVPixelBufferRef</tt> to be represented
         * by the new instance
         */
        private MutableCVPixelBuffer(long ptr)
        {
            super(ptr);
        }

        /**
         * Sets the CoreVideo <tt>CVImageBufferRef</tt> represented by this
         * instance.
         *
         * @param ptr the CoreVideo <tt>CVImageBufferRef</tt> to be represented
         * by this instance
         * @see CVPixelBuffer#setPtr(long)
         */
        @Override
        public void setPtr(long ptr)
        {
            super.setPtr(ptr);
        }
    }

    /**
     * Represents a <tt>CMSampleBuffer</tt> which allows public changing of the
     * AVFoundation <tt>CMSampleBuffer</tt> object it represents.
     */
    private static class MutableCMSampleBuffer
        extends CMSampleBuffer
    {
        /**
         * Initializes a new <tt>MutableCMSampleBuffer</tt> instance which is to
         * represent a specific AVFoundation <tt>CMSampleBuffer</tt> object.
         *
         * @param ptr the pointer to the AVFoundation <tt>CMSampleBuffer</tt> object to
         * be represented by the new instance
         */
        private MutableCMSampleBuffer(long ptr)
        {
            super(ptr);
        }

        /**
         * Sets the pointer to the Objective-C object represented by this
         * instance.
         *
         * @param ptr the pointer to the Objective-C object to be represented by
         * this instance
         * @see CMSampleBuffer#setPtr(long)
         */
        @Override
        public void setPtr(long ptr)
        {
            super.setPtr(ptr);
        }
    }
}
