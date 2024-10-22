/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.jmfext.media.protocol.wasapi;

/**
 * Proxy wrapper class for the {@link WASAPI} native class. Used to encapsulate native calls, and make it easier to
 * test. For detailed descriptions of see {@link WASAPI}
 *
 * @see WASAPI
 */
public class WASAPIProxy
{
    /**
     * Prevents the initialization of <tt>WASAPIProxy</tt> instances.
     */
    private WASAPIProxy()
    {
    }

    public static void CloseHandle(long hObject) throws HResultException
    {
        WASAPI.CloseHandle(hObject);
    }

    public static String CoCreateGuid() throws HResultException
    {
        return WASAPI.CoCreateGuid();
    }

    public static long CoCreateInstance(String clsid,
                                        long pUnkOuter,
                                        int dwClsContext,
                                        String iid) throws HResultException
    {
        return WASAPI.CoCreateInstance(clsid, pUnkOuter, dwClsContext, iid);
    }

    public static int CoInitializeEx(long pvReserved, int dwCoInit) throws HResultException
    {
        return WASAPI.CoInitializeEx(pvReserved, dwCoInit);
    }

    public static void CoTaskMemFree(long pv)
    {
        WASAPI.CoTaskMemFree(pv);
    }

    public static void CoUninitialize()
    {
        WASAPI.CoUninitialize();
    }

    public static long CreateEvent(long lpEventAttributes,
                                   boolean bManualReset,
                                   boolean bInitialState,
                                   String lpName) throws HResultException
    {
        return WASAPI.CreateEvent(lpEventAttributes, bManualReset, bInitialState, lpName);
    }

    /**
     * See full description at {@link WASAPI#FAILED(int)}
     */
    public static boolean FAILED(int hresult)
    {
        return WASAPI.FAILED(hresult);
    }

    public static int IAudioCaptureClient_GetNextPacketSize(long thiz) throws HResultException
    {
        return WASAPI.IAudioCaptureClient_GetNextPacketSize(thiz);
    }

    public static int IAudioCaptureClient_Read(long thiz,
                                               byte[] data,
                                               int offset,
                                               int length,
                                               int srcSampleSize,
                                               int srcChannels,
                                               int dstSampleSize,
                                               int dstChannels) throws HResultException
    {
        return WASAPI.IAudioCaptureClient_Read(thiz,
                                               data,
                                               offset,
                                               length,
                                               srcSampleSize,
                                               srcChannels,
                                               dstSampleSize,
                                               dstChannels);
    }

    public static void IAudioCaptureClient_Release(long thiz)
    {
        WASAPI.IAudioCaptureClient_Release(thiz);
    }

    public static int IAudioClient_GetBufferSize(long thiz) throws HResultException
    {
        return WASAPI.IAudioClient_GetBufferSize(thiz);
    }

    public static int IAudioClient_GetCurrentPadding(long thiz) throws HResultException
    {
        return WASAPI.IAudioClient_GetCurrentPadding(thiz);
    }

    public static long IAudioClient_GetDefaultDevicePeriod(long thiz) throws HResultException
    {
        return WASAPI.IAudioClient_GetDefaultDevicePeriod(thiz);
    }

    public static long IAudioClient_GetMinimumDevicePeriod(long thiz) throws HResultException
    {
        return WASAPI.IAudioClient_GetMinimumDevicePeriod(thiz);
    }

    public static long IAudioClient_GetService(long thiz, String iid) throws HResultException
    {
        return WASAPI.IAudioClient_GetService(thiz, iid);
    }

    public static int IAudioClient_Initialize(long thiz,
                                              int shareMode,
                                              int streamFlags,
                                              long hnsBufferDuration,
                                              long hnsPeriodicity,
                                              long pFormat,
                                              String audioSessionGuid) throws HResultException
    {
        return WASAPI.IAudioClient_Initialize(thiz,
                                              shareMode,
                                              streamFlags,
                                              hnsBufferDuration,
                                              hnsPeriodicity,
                                              pFormat,
                                              audioSessionGuid);
    }

    public static long IAudioClient_IsFormatSupported(long thiz, int shareMode, long pFormat) throws HResultException
    {
        return WASAPI.IAudioClient_IsFormatSupported(thiz, shareMode, pFormat);
    }

    public static void IAudioClient_Release(long thiz)
    {
        WASAPI.IAudioClient_Release(thiz);
    }

    public static void IAudioClient_SetEventHandle(long thiz, long eventHandle) throws HResultException
    {
        WASAPI.IAudioClient_SetEventHandle(thiz, eventHandle);
    }

    public static int IAudioClient_Start(long thiz) throws HResultException
    {
        return WASAPI.IAudioClient_Start(thiz);
    }

    public static int IAudioClient_Stop(long thiz) throws HResultException
    {
        return WASAPI.IAudioClient_Stop(thiz);
    }

    /**
     * See full description at {@link WASAPI#stopIAudioClient(long)}
     */
    public static void stopIAudioClient(long iAudioClient) throws HResultException
    {
        WASAPI.stopIAudioClient(iAudioClient);
    }

    public static void IAudioRenderClient_Release(long thiz)
    {
        WASAPI.IAudioRenderClient_Release(thiz);
    }

    /**
     * See full description at {@link WASAPI#IAudioRenderClient_Write}
     */
    public static int IAudioRenderClient_Write(long thiz,
                                               byte[] data,
                                               int offset,
                                               int length,
                                               int srcSampleSize,
                                               int srcChannels,
                                               int dstSampleSize,
                                               int dstChannels) throws HResultException
    {
        return WASAPI.IAudioRenderClient_Write(thiz,
                                               data,
                                               offset,
                                               length,
                                               srcSampleSize,
                                               srcChannels,
                                               dstSampleSize,
                                               dstChannels);
    }

    public static long IMMDevice_Activate(long thiz,
                                          String iid,
                                          int dwClsCtx,
                                          long pActivationParams) throws HResultException
    {
        return WASAPI.IMMDevice_Activate(thiz, iid, dwClsCtx, pActivationParams);
    }

    public static String IMMDevice_GetId(long thiz) throws HResultException
    {
        return WASAPI.IMMDevice_GetId(thiz);
    }

    public static int IMMDevice_GetState(long thiz) throws HResultException
    {
        return WASAPI.IMMDevice_GetState(thiz);
    }

    public static long IMMDevice_OpenPropertyStore(long thiz, int stgmAccess) throws HResultException
    {
        return WASAPI.IMMDevice_OpenPropertyStore(thiz, stgmAccess);
    }

    public static long IMMDevice_QueryInterface(long thiz, String iid) throws HResultException
    {
        return WASAPI.IMMDevice_QueryInterface(thiz, iid);
    }

    public static void IMMDevice_Release(long thiz)
    {
        WASAPI.IMMDevice_Release(thiz);
    }

    public static int IMMDeviceCollection_GetCount(long thiz) throws HResultException
    {
        return WASAPI.IMMDeviceCollection_GetCount(thiz);
    }

    public static long IMMDeviceCollection_Item(long thiz, int nDevice) throws HResultException
    {
        return WASAPI.IMMDeviceCollection_Item(thiz, nDevice);
    }

    public static void IMMDeviceCollection_Release(long thiz)
    {
        WASAPI.IMMDeviceCollection_Release(thiz);
    }

    public static long IMMDeviceEnumerator_EnumAudioEndpoints(long thiz,
                                                              int dataFlow,
                                                              int dwStateMask) throws HResultException
    {
        return WASAPI.IMMDeviceEnumerator_EnumAudioEndpoints(thiz, dataFlow, dwStateMask);
    }

    public static long IMMDeviceEnumerator_GetDevice(long thiz, String pwstrId) throws HResultException
    {
        return WASAPI.IMMDeviceEnumerator_GetDevice(thiz, pwstrId);
    }

    public static void IMMDeviceEnumerator_Release(long thiz)
    {
        WASAPI.IMMDeviceEnumerator_Release(thiz);
    }

    public static int IMMEndpoint_GetDataFlow(long thiz) throws HResultException
    {
        return WASAPI.IMMEndpoint_GetDataFlow(thiz);
    }

    public static void IMMEndpoint_Release(long thiz)
    {
        WASAPI.IMMEndpoint_Release(thiz);
    }

    public static String IPropertyStore_GetString(long thiz, long key) throws HResultException
    {
        return WASAPI.IPropertyStore_GetString(thiz, key);
    }

    public static void IPropertyStore_Release(long thiz)
    {
        WASAPI.IPropertyStore_Release(thiz);
    }

    public static long PSPropertyKeyFromString(String pszString) throws HResultException
    {
        return WASAPI.PSPropertyKeyFromString(pszString);
    }

    public static void ResetEvent(long hEvent) throws HResultException
    {
        WASAPI.ResetEvent(hEvent);
    }

    /**
     * See full description at {@link WASAPI#SUCCEEDED(int)}
     */
    public static boolean SUCCEEDED(int hresult)
    {
        return WASAPI.SUCCEEDED(hresult);
    }

    /**
     * See full description at {@link WASAPI#WaitForSingleObject(long, long)}
     */
    public static int WaitForSingleObject(long hHandle, long dwMilliseconds) throws HResultException
    {
        return WASAPI.WaitForSingleObject(hHandle, dwMilliseconds);
    }

    public static long WAVEFORMATEX_alloc()
    {
        return WASAPI.WAVEFORMATEX_alloc();
    }

    public static void WAVEFORMATEX_fill(long thiz,
                                         char wFormatTag,
                                         char nChannels,
                                         int nSamplesPerSec,
                                         int nAvgBytesPerSec,
                                         char nBlockAlign,
                                         char wBitsPerSample,
                                         char cbSize)
    {
        WASAPI.WAVEFORMATEX_fill(thiz,
                                 wFormatTag,
                                 nChannels,
                                 nSamplesPerSec,
                                 nAvgBytesPerSec,
                                 nBlockAlign,
                                 wBitsPerSample,
                                 cbSize);
    }

    public static char WAVEFORMATEX_getCbSize(long thiz)
    {
        return WASAPI.WAVEFORMATEX_getCbSize(thiz);
    }

    public static int WAVEFORMATEX_getNAvgBytesPerSec(long thiz)
    {
        return WASAPI.WAVEFORMATEX_getNAvgBytesPerSec(thiz);
    }

    public static char WAVEFORMATEX_getNBlockAlign(long thiz)
    {
        return WASAPI.WAVEFORMATEX_getNBlockAlign(thiz);
    }

    public static char WAVEFORMATEX_getNChannels(long thiz)
    {
        return WASAPI.WAVEFORMATEX_getNChannels(thiz);
    }

    public static int WAVEFORMATEX_getNSamplesPerSec(long thiz)
    {
        return WASAPI.WAVEFORMATEX_getNSamplesPerSec(thiz);
    }

    public static char WAVEFORMATEX_getWBitsPerSample(long thiz)
    {
        return WASAPI.WAVEFORMATEX_getWBitsPerSample(thiz);
    }

    public static char WAVEFORMATEX_getWFormatTag(long thiz)
    {
        return WASAPI.WAVEFORMATEX_getWFormatTag(thiz);
    }

    public static void WAVEFORMATEX_setCbSize(long thiz, char cbSize)
    {
        WASAPI.WAVEFORMATEX_setCbSize(thiz, cbSize);
    }

    public static void WAVEFORMATEX_setNAvgBytesPerSec(long thiz, int nAvgBytesPerSec)
    {
        WASAPI.WAVEFORMATEX_setNAvgBytesPerSec(thiz, nAvgBytesPerSec);
    }

    public static void WAVEFORMATEX_setNBlockAlign(long thiz, char nBlockAlign)
    {
        WASAPI.WAVEFORMATEX_setNBlockAlign(thiz, nBlockAlign);
    }

    public static void WAVEFORMATEX_setNChannels(long thiz, char nChannels)
    {
        WASAPI.WAVEFORMATEX_setNChannels(thiz, nChannels);
    }

    public static void WAVEFORMATEX_setNSamplesPerSec(long thiz, int nSamplesPerSec)
    {
        WASAPI.WAVEFORMATEX_setNSamplesPerSec(thiz, nSamplesPerSec);
    }

    public static void WAVEFORMATEX_setWBitsPerSample(long thiz, char wBitsPerSample)
    {
        WASAPI.WAVEFORMATEX_setWBitsPerSample(thiz, wBitsPerSample);
    }

    public static void WAVEFORMATEX_setWFormatTag(long thiz, char wFormatTag)
    {
        WASAPI.WAVEFORMATEX_setWFormatTag(thiz, wFormatTag);
    }

    public static int WAVEFORMATEX_sizeof()
    {
        return WASAPI.WAVEFORMATEX_sizeof();
    }
}
