/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
/*
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * Portions copyright (c) Microsoft Corporation.
 */
package org.jitsi.impl.neomedia.codec.video;

import static org.bytedeco.ffmpeg.global.avfilter.*;
import static org.bytedeco.ffmpeg.global.avutil.*;

import java.awt.Dimension;

import javax.media.Buffer;
import javax.media.Effect;
import javax.media.Format;
import javax.media.ResourceUnavailableException;

import org.bytedeco.ffmpeg.avfilter.AVFilterContext;
import org.bytedeco.ffmpeg.avfilter.AVFilterGraph;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.javacpp.BytePointer;
import org.jitsi.impl.neomedia.codec.AbstractCodec2;
import org.jitsi.util.Logger;

/**
 * Implements a video <tt>Effect</tt> which horizontally flips
 * <tt>AVFrame</tt>s.
 *
 * @author Sebastien Vincent
 * @author Lyubomir Marinov
 */
public class HFlip
        extends AbstractCodec2
        implements Effect
{
    /**
     * The <tt>Logger</tt> used by the <tt>HFlip</tt> class and its instances
     * for logging output.
     */
    private static final Logger logger = Logger.getLogger(HFlip.class);

    /**
     * The list of <tt>Format</tt>s supported by <tt>HFlip</tt> instances as
     * input and output.
     */
    private static final Format[] SUPPORTED_FORMATS
            = new Format[] { new AVFrameFormat() };

    /**
     * The name of the FFmpeg ffsink video source <tt>AVFilter</tt> used by
     * <tt>HFlip</tt>.
     */
    private static final String VSINK_FFSINK_NAME = "buffersink";

    /**
     * The name of the FFmpeg buffer video source <tt>AVFilter</tt> used by
     * <tt>HFlip</tt>.
     */
    private static final String VSRC_BUFFER_NAME = "buffer";

    /**
     * The pointer to the <tt>AVFilterContext</tt> in {@link #graph} of the
     * FFmpeg video source with the name {@link #VSRC_BUFFER_NAME}.
     */
    private AVFilterContext buffer;

    /**
     * The pointer to the <tt>AVFilterContext</tt> in {@link #graph} of the
     * FFmpeg video sink with the name {@link #VSINK_FFSINK_NAME}.
     */
    private AVFilterContext ffsink;

    /**
     * The pointer to the <tt>AVFilterGraph</tt> instance which contains the
     * FFmpeg hflip filter represented by this <tt>Effect</tt>.
     */
    private AVFilterGraph graph;

    /**
     * The indicator which determines whether the fact that {@link #graph} is
     * equal to zero means that an attempt to initialize it is to be made. If
     * <tt>false</tt>, indicates that such an attempt has already been made and
     * has failed. In other words, prevents multiple initialization attempts
     * with the same parameters.
     */
    private boolean graphIsPending = true;

    /**
     * The height of {@link #graph}.
     */
    private int height;

    /**
     * The pointer to the <tt>AVFrame</tt> instance which is the output (data)
     * of this <tt>Effect</tt>.
     */
    private AVFrameWrapper outputFrame;

    /**
     * The FFmpeg pixel format of {@link #graph}.
     */
    private int pixFmt = AV_PIX_FMT_NONE;

    /**
     * The width of {@link #graph}.
     */
    private int width;

    /**
     * Initializes a new <tt>HFlip</tt> instance.
     */
    public HFlip()
    {
        super("FFmpeg HFlip Filter", AVFrameFormat.class, SUPPORTED_FORMATS);
    }

    /**
     * Closes this <tt>Effect</tt>.
     *
     * @see AbstractCodec2#doClose()
     */
    @Override
    protected synchronized void doClose()
    {
        try
        {
            if (outputFrame != null)
            {
                av_frame_unref(outputFrame.getNativeFrame());
                outputFrame = null;
            }
        }
        finally
        {
            reset();
        }
    }

    /**
     * Opens this <tt>Effect</tt>.
     *
     * @throws ResourceUnavailableException if any of the required resource
     * cannot be allocated
     * @see AbstractCodec2#doOpen()
     */
    @Override
    protected synchronized void doOpen()
            throws ResourceUnavailableException
    {
        try
        {
            outputFrame = new AVFrameWrapper();
        }
        catch (OutOfMemoryError | Exception e)
        {
            String reason = "AVFrame allocation failed";

            logger.error(reason);
            throw new ResourceUnavailableException(reason);
        }
    }

    /**
     * Performs the media processing defined by this <tt>Effect</tt>.
     *
     * @param inputBuffer the <tt>Buffer</tt> that contains the media data to be
     * processed
     * @param outputBuffer the <tt>Buffer</tt> in which to store the processed
     * media data
     * @return <tt>BUFFER_PROCESSED_OK</tt> if the processing is successful
     * @see AbstractCodec2#doProcess(Buffer, Buffer)
     */
    @Override
    protected synchronized int doProcess(
            final Buffer inputBuffer,
            final Buffer outputBuffer)
    {
        // Make sure the graph is configured with the current Format i.e. size
        // and pixFmt.
        final AVFrameFormat format = (AVFrameFormat) inputBuffer.getFormat();
        final Dimension size = format.getSize();
        final int pixFmt = format.getPixFmt();

        if ((this.width != size.width)
            || (this.height != size.height)
            || (this.pixFmt != pixFmt))
       {
            reset();
       }

        if (!allocateFfmpegGraph(format, size, pixFmt))
        {
            return BUFFER_PROCESSED_FAILED;
        }

        // The graph is configured for the current Format, apply its filters to
        // the inputFrame.
        final AVFrame inFrame = ((AVFrameWrapper) inputBuffer.getData()).getNativeFrame();
        final AVFrame outFrame = outputFrame.getNativeFrame();
        inFrame.width(this.width);
        inFrame.height(this.height);
        inFrame.format(this.pixFmt);

        long filterResult = av_buffersrc_write_frame(buffer, inFrame);
        if (filterResult == 0)
        {
            av_frame_unref(outFrame); // Free data from previous call to process() to avoid memory leak
            filterResult = av_buffersink_get_frame(ffsink, outFrame);
        }

        if (filterResult < 0)
        {
            // If av_buffersrc_write_frame/av_buffersink_get_frame fails,
            // it is likely to fail for any frame. Consequently, printing that
            // it has failed will result in a lot of repeating logging output.
            // Since the failure in question will be visible in the UI anyway,
            // just debug it.
            final BytePointer errorPointer = new BytePointer(Integer.BYTES);
            if (logger.isTraceEnabled())
                logger.trace("av_buffersrc_write_frame/av_buffersink_get_frame: "
                             + av_strerror((int)filterResult, errorPointer, Integer.BYTES));
            return BUFFER_PROCESSED_FAILED;
        }

        final Object out = outputBuffer.getData();
        if (!(out instanceof AVFrameWrapper)
            || (((AVFrameWrapper) out).getNativeFrame() != outputFrame.getNativeFrame()))
        {
            outputBuffer.setData(outputFrame);
        }

        outputBuffer.setDiscard(inputBuffer.isDiscard());
        outputBuffer.setDuration(inputBuffer.getDuration());
        outputBuffer.setEOM(inputBuffer.isEOM());
        outputBuffer.setFlags(inputBuffer.getFlags());
        outputBuffer.setFormat(format);
        outputBuffer.setHeader(inputBuffer.getHeader());
        outputBuffer.setLength(inputBuffer.getLength());
        outputBuffer.setSequenceNumber(inputBuffer.getSequenceNumber());
        outputBuffer.setTimeStamp(inputBuffer.getTimeStamp());
        return BUFFER_PROCESSED_OK;
    }

    private boolean allocateFfmpegGraph(AVFrameFormat format, Dimension size,
                                        int pixFmt)
    {
        if (graph != null)
        {
            return true;
        }

        String errorReason = null;
        int error = 0;
        AVFilterContext buffer = null;
        AVFilterContext ffsink = null;

        if (graphIsPending)
        {
            graphIsPending = false;

            graph = avfilter_graph_alloc();
            if (graph == null)
                errorReason = "avfilter_graph_alloc";
            else
            {
                String filters
                        = VSRC_BUFFER_NAME + "=" + size.width + ":" + size.height
                          + ":" + pixFmt + ":1:1000000:1,"
                          + "scale,hflip,scale,"
                          + "format=pix_fmts=" + pixFmt + ","
                          + VSINK_FFSINK_NAME;

                error
                        = avfilter_graph_parse(
                        graph,
                        filters,
                        null,
                        null,
                        null);

                if (error == 0)
                {
                    // Unfortunately, the name of an AVFilterContext created by
                    // avfilter_graph_parse is not the name of the AVFilter.
                    String parsedFilterNameFormat = "Parsed_%2$s_%1$d";
                    String parsedFilterName
                            = String.format(
                            parsedFilterNameFormat,
                            0, VSRC_BUFFER_NAME);

                    buffer
                            = avfilter_graph_get_filter(
                            graph,
                            parsedFilterName);

                    if (buffer == null)
                    {
                        errorReason
                                = "avfilter_graph_get_filter: "
                                  + VSRC_BUFFER_NAME
                                  + "/"
                                  + parsedFilterName;
                    }
                    else
                    {
                        parsedFilterName
                                = String.format(
                                parsedFilterNameFormat,
                                5,
                                VSINK_FFSINK_NAME);
                        ffsink
                                = avfilter_graph_get_filter(
                                graph,
                                parsedFilterName);

                        if (ffsink == null)
                        {
                            errorReason
                                    = "avfilter_graph_get_filter: "
                                      + VSINK_FFSINK_NAME
                                      + "/"
                                      + parsedFilterName;
                        }
                        else
                        {
                            error
                                    = avfilter_graph_config(graph, null);
                            if (error != 0)
                                errorReason = "avfilter_graph_config";
                        }
                    }
                }
                else
                {
                    errorReason = "avfilter_graph_parse";
                }

                if (errorReason != null)
                {
                    avfilter_graph_free(graph);
                    graph = null;
                }
            }
        }

        if (graph == null)
        {
            if (errorReason != null)
            {
                StringBuilder msg = new StringBuilder(errorReason);
                if (error != 0)
                {
                    msg.append(": ").append(error);
                }

                msg.append(", format ").append(format);
                logger.error(msg);
            }

            logger.error("Failed to allocate FfmpegGraph");
            return false;
        }
        else
        {
            this.width = size.width;
            this.height = size.height;
            this.pixFmt = pixFmt;
            this.buffer = buffer;
            this.ffsink = ffsink;
        }

        return true;
    }

    /**
     * Resets the state of this <tt>PlugIn</tt>.
     */
    @Override
    public synchronized void reset()
    {
        if (graph != null)
        {
            avfilter_graph_free(graph);
            graph = null;
            graphIsPending = true;

            width = 0;
            height = 0;
            pixFmt = AV_PIX_FMT_NONE;
            buffer = null;
            ffsink = null;
        }
    }
}