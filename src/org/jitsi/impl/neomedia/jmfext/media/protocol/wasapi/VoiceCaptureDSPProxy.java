/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.jmfext.media.protocol.wasapi;

/**
 * Proxy wrapper class for the {@link VoiceCaptureDSP} native class. Used to encapsulate native calls, and make it
 * easier to test. For detailed descriptions of see {@link VoiceCaptureDSP}
 */
public class VoiceCaptureDSPProxy
{
    /**
     * Prevents the initialization of <tt>WASAPIProxy</tt> instances.
     */
    private VoiceCaptureDSPProxy()
    {
    }

    public static int DMO_MEDIA_TYPE_fill(long thiz,
                                          String majortype,
                                          String subtype,
                                          boolean bFixedSizeSamples,
                                          boolean bTemporalCompression,
                                          int lSampleSize,
                                          String formattype,
                                          long pUnk,
                                          int cbFormat,
                                          long pbFormat) throws HResultException
    {
        return VoiceCaptureDSP.DMO_MEDIA_TYPE_fill(thiz,
                                                   majortype,
                                                   subtype,
                                                   bFixedSizeSamples,
                                                   bTemporalCompression,
                                                   lSampleSize,
                                                   formattype,
                                                   pUnk,
                                                   cbFormat,
                                                   pbFormat);
    }

    public static void DMO_MEDIA_TYPE_setCbFormat(long thiz, int cbFormat)
    {
        VoiceCaptureDSP.DMO_MEDIA_TYPE_setCbFormat(thiz, cbFormat);
    }

    public static int DMO_MEDIA_TYPE_setFormattype(long thiz, String formattype) throws HResultException
    {
        return VoiceCaptureDSP.DMO_MEDIA_TYPE_setFormattype(thiz, formattype);
    }

    public static void DMO_MEDIA_TYPE_setLSampleSize(long thiz, int lSampleSize)
    {
        VoiceCaptureDSP.DMO_MEDIA_TYPE_setLSampleSize(thiz, lSampleSize);
    }

    public static void DMO_MEDIA_TYPE_setPbFormat(long thiz, long pbFormat)
    {
        VoiceCaptureDSP.DMO_MEDIA_TYPE_setPbFormat(thiz, pbFormat);
    }

    public static long DMO_OUTPUT_DATA_BUFFER_alloc(long pBuffer, int dwStatus, long rtTimestamp, long rtTimelength)
    {
        return VoiceCaptureDSP.DMO_OUTPUT_DATA_BUFFER_alloc(pBuffer, dwStatus, rtTimestamp, rtTimelength);
    }

    public static int DMO_OUTPUT_DATA_BUFFER_getDwStatus(long thiz)
    {
        return VoiceCaptureDSP.DMO_OUTPUT_DATA_BUFFER_getDwStatus(thiz);
    }

    public static void DMO_OUTPUT_DATA_BUFFER_setDwStatus(long thiz, int dwStatus)
    {
        VoiceCaptureDSP.DMO_OUTPUT_DATA_BUFFER_setDwStatus(thiz, dwStatus);
    }

    public static int IMediaBuffer_AddRef(long thiz)
    {
        return VoiceCaptureDSP.IMediaBuffer_AddRef(thiz);
    }

    public static long IMediaBuffer_GetBuffer(long thiz) throws HResultException
    {
        return VoiceCaptureDSP.IMediaBuffer_GetBuffer(thiz);
    }

    public static int IMediaBuffer_GetLength(long thiz) throws HResultException
    {
        return VoiceCaptureDSP.IMediaBuffer_GetLength(thiz);
    }

    public static int IMediaBuffer_GetMaxLength(long thiz) throws HResultException
    {
        return VoiceCaptureDSP.IMediaBuffer_GetMaxLength(thiz);
    }

    public static int IMediaBuffer_Release(long thiz)
    {
        return VoiceCaptureDSP.IMediaBuffer_Release(thiz);
    }

    public static void IMediaBuffer_SetLength(long thiz, int cbLength) throws HResultException
    {
        VoiceCaptureDSP.IMediaBuffer_SetLength(thiz, cbLength);
    }

    public static int IMediaObject_Flush(long thiz) throws HResultException
    {
        return VoiceCaptureDSP.IMediaObject_Flush(thiz);
    }

    public static int IMediaObject_GetInputStatus(long thiz, int dwInputStreamIndex) throws HResultException
    {
        return VoiceCaptureDSP.IMediaObject_GetInputStatus(thiz, dwInputStreamIndex);
    }

    public static int IMediaObject_ProcessInput(long thiz,
                                                int dwInputStreamIndex,
                                                long pBuffer,
                                                int dwFlags,
                                                long rtTimestamp,
                                                long rtTimelength) throws HResultException
    {
        return VoiceCaptureDSP.IMediaObject_ProcessInput(thiz,
                                                         dwInputStreamIndex,
                                                         pBuffer,
                                                         dwFlags,
                                                         rtTimestamp,
                                                         rtTimelength);
    }

    public static int IMediaObject_ProcessOutput(long thiz,
                                                 int dwFlags,
                                                 int cOutputBufferCount,
                                                 long pOutputBuffers) throws HResultException
    {
        return VoiceCaptureDSP.IMediaObject_ProcessOutput(thiz, dwFlags, cOutputBufferCount, pOutputBuffers);
    }

    public static long IMediaObject_QueryInterface(long thiz, String iid) throws HResultException
    {
        return VoiceCaptureDSP.IMediaObject_QueryInterface(thiz, iid);
    }

    public static void IMediaObject_Release(long thiz)
    {
        VoiceCaptureDSP.IMediaObject_Release(thiz);
    }

    public static int IMediaObject_SetInputType(long thiz,
                                                int dwInputStreamIndex,
                                                long pmt,
                                                int dwFlags) throws HResultException
    {
        return VoiceCaptureDSP.IMediaObject_SetInputType(thiz, dwInputStreamIndex, pmt, dwFlags);
    }

    public static int IMediaObject_SetOutputType(long thiz,
                                                 int dwOutputStreamIndex,
                                                 long pmt,
                                                 int dwFlags) throws HResultException
    {
        return VoiceCaptureDSP.IMediaObject_SetOutputType(thiz, dwOutputStreamIndex, pmt, dwFlags);
    }

    public static int IPropertyStore_SetValue(long thiz, long key, boolean value) throws HResultException
    {
        return VoiceCaptureDSP.IPropertyStore_SetValue(thiz, key, value);
    }

    public static int IPropertyStore_SetValue(long thiz, long key, int value) throws HResultException
    {
        return VoiceCaptureDSP.IPropertyStore_SetValue(thiz, key, value);
    }

    public static long MediaBuffer_alloc(int maxLength)
    {
        return VoiceCaptureDSP.MediaBuffer_alloc(maxLength);
    }

    public static int MediaBuffer_pop(long thiz, byte[] buffer, int offset, int length) throws HResultException
    {
        return VoiceCaptureDSP.MediaBuffer_pop(thiz, buffer, offset, length);
    }

    public static int MediaBuffer_push(long thiz, byte[] buffer, int offset, int length) throws HResultException
    {
        return VoiceCaptureDSP.MediaBuffer_push(thiz, buffer, offset, length);
    }

    public static long MoCreateMediaType(int cbFormat) throws HResultException
    {
        return VoiceCaptureDSP.MoCreateMediaType(cbFormat);
    }

    public static void MoDeleteMediaType(long pmt) throws HResultException
    {
        VoiceCaptureDSP.MoDeleteMediaType(pmt);
    }

    public static void MoFreeMediaType(long pmt) throws HResultException
    {
        VoiceCaptureDSP.MoFreeMediaType(pmt);
    }

    public static void MoInitMediaType(long pmt, int cbFormat) throws HResultException
    {
        VoiceCaptureDSP.MoInitMediaType(pmt, cbFormat);
    }
}
