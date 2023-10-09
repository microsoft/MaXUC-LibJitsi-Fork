/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
// Portions (c) Microsoft Corporation. All rights reserved.
package org.jitsi.service.neomedia;

import java.beans.*;
import java.util.*;

import org.jitsi.impl.neomedia.device.*;
import org.jitsi.service.neomedia.codec.*;
import org.jitsi.service.neomedia.device.*;
import org.jitsi.service.neomedia.format.*;
import org.jitsi.util.*;

/**
 * The <tt>MediaService</tt> service is meant to be a wrapper of media libraries
 * such as JMF, FMJ, FFMPEG, and/or others. It takes care of all media play and
 * capture as well as media transport (e.g. over RTP).
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 */
public interface MediaService
{
    /**
     * The name of the property of <tt>MediaService</tt> the value of which
     * corresponds to the value returned by
     * {@link #getDefaultDevice(MediaType, MediaUseCase)}. The <tt>oldValue</tt>
     * and the <tt>newValue</tt> of the fired <tt>PropertyChangeEvent</tt> are
     * not to be relied on and instead a call to <tt>getDefaultDevice</tt> is to
     * be performed to retrieve the new value.
     */
    String DEFAULT_DEVICE = "defaultDevice";

    /**
     * The directory within the application's home directory in which to store
     * the microphone sample recorded in audio settings.
     */
    String RECORDED_SAMPLE_DIRECTORY = "recordedSample";

    /**
     * The filename of the microphone sample recorded in audio settings.
     */
    String RECORDED_SAMPLE_FILENAME = "microphoneSample";

    /**
     * The file format of the microphone sample recorded in audio settings.
     */
    String RECORDED_SAMPLE_EXTENSION = SoundFileUtils.wav;

    /**
     * Adds a <tt>PropertyChangeListener</tt> to be notified about changes in
     * the values of the properties of this instance.
     *
     * @param listener the <tt>PropertyChangeListener</tt> to be notified about
     * changes in the values of the properties of this instance
     */
    void addPropertyChangeListener(PropertyChangeListener listener);

    /**
     * Returns a new <tt>EncodingConfiguration</tt> instance.
     *
     * @return a new <tt>EncodingConfiguration</tt> instance.
     */
    EncodingConfiguration createEmptyEncodingConfiguration();

    /**
     * Create a <tt>MediaStream</tt> which will use a specific
     * <tt>MediaDevice</tt> for capture and playback of media. The new instance
     * will not have a <tt>StreamConnector</tt> at the time of its construction
     * and a <tt>StreamConnector</tt> will be specified later on in order to
     * enable the new instance to send and receive media.
     *
     * @param device the <tt>MediaDevice</tt> to be used by the new instance for
     * capture and playback of media
     * @return a newly-created <tt>MediaStream</tt> which will use the specified
     * <tt>device</tt> for capture and playback of media
     */
    MediaStream createMediaStream(MediaDevice device);

    /**
     * Initializes a new <tt>MediaStream</tt> of a specific <tt>MediaType</tt>.
     * The new instance will not have a <tt>MediaDevice</tt> at the time of its
     * initialization and a <tt>MediaDevice</tt> may be specified later on with
     * the constraint that {@link MediaDevice#getMediaType()} equals
     * <tt>mediaType</tt>.
     *
     * @param mediaType the <tt>MediaType</tt> of the new instance to be
     * initialized
     * @return a new <tt>MediaStream</tt> instance of the specified
     * <tt>mediaType</tt>
     */
    MediaStream createMediaStream(MediaType mediaType);

    /**
     * Creates a <tt>MediaStream</tt> that will be using the specified
     * <tt>MediaDevice</tt> for both capture and playback of media exchanged
     * via the specified <tt>StreamConnector</tt>.
     *
     * @param connector the <tt>StreamConnector</tt> the stream should use for
     * sending and receiving media or <tt>null</tt> if the stream is to not have
     * a <tt>StreamConnector</tt> configured at initialization time and a
     * <tt>StreamConnector</tt> is to be specified later on
     * @param device the device to be used for both capture and playback of
     * media exchanged via the specified <tt>StreamConnector</tt>
     *
     * @return the newly created <tt>MediaStream</tt>.
     */
    MediaStream createMediaStream(StreamConnector connector,
                                  MediaDevice device);

    /**
     * Creates a <tt>MediaStream</tt> that will be using the specified
     * <tt>MediaDevice</tt> for both capture and playback of media exchanged
     * via the specified <tt>StreamConnector</tt>.
     *
     * @param connector the <tt>StreamConnector</tt> the stream should use for
     * sending and receiving media or <tt>null</tt> if the stream is to not have
     * a <tt>StreamConnector</tt> configured at initialization time and a
     * <tt>StreamConnector</tt> is to be specified later on
     * @param device the device to be used for both capture and playback of
     * media exchanged via the specified <tt>StreamConnector</tt>
     * @param zrtpControl a control which is already created, used to control
     * the ZRTP operations.
     *
     * @return the newly created <tt>MediaStream</tt>.
     */
    MediaStream createMediaStream(StreamConnector connector,
                                  MediaDevice device,
                                  SrtpControl zrtpControl);

    /**
     * Creates a new <tt>MediaDevice</tt> which uses a specific
     * <tt>MediaDevice</tt> to capture and play back media and performs mixing
     * of the captured media and the media played back by any other users of the
     * returned <tt>MediaDevice</tt>. For the <tt>AUDIO</tt> <tt>MediaType</tt>,
     * the returned device is commonly referred to as an audio mixer. The
     * <tt>MediaType</tt> of the returned <tt>MediaDevice</tt> is the same as
     * the <tt>MediaType</tt> of the specified <tt>device</tt>.
     *
     * @param device the <tt>MediaDevice</tt> which is to be used by the
     * returned <tt>MediaDevice</tt> to actually capture and play back media
     * @return a new <tt>MediaDevice</tt> instance which uses <tt>device</tt> to
     * capture and play back media and performs mixing of the captured media and
     * the media played back by any other users of the returned
     * <tt>MediaDevice</tt> instance
     */
    MediaDevice createMixer(MediaDevice device);

    /**
     * Creates a new <tt>Recorder</tt> instance that can be used to record a
     * call which captures and plays back media using a specific
     * <tt>MediaDevice</tt>.
     *
     * @param device the <tt>MediaDevice</tt> which is used for media capture
     * and playback by the call to be recorded
     * @return a new <tt>Recorder</tt> instance that can be used to record a
     * call which captures and plays back media using the specified
     * <tt>MediaDevice</tt>
     */
    Recorder createRecorder(MediaDevice device);

    /**
     * Initializes a new <tt>RTPTranslator</tt> which is to forward RTP and RTCP
     * traffic between multiple <tt>MediaStream</tt>s.
     *
     * @return a new <tt>RTPTranslator</tt> which is to forward RTP and RTCP
     * traffic between multiple <tt>MediaStream</tt>s
     */
    RTPTranslator createRTPTranslator();

    /**
     * Initializes a new <tt>SDesControl</tt> instance which is to control all
     * SDes options.
     *
     * @return a new <tt>SDesControl</tt> instance which is to control all SDes
     * options
     */
    SDesControl createSDesControl();

    /**
     * Returns the current <tt>EncodingConfiguration</tt> instance.
     *
     * @return the current <tt>EncodingConfiguration</tt> instance.
     */
    EncodingConfiguration getCurrentEncodingConfiguration();

    /**
     * Returns the default <tt>MediaDevice</tt> for the specified media
     * <tt>type</tt>.
     *
     * @param mediaType a <tt>MediaType</tt> value indicating the kind of device
     * that we are trying to obtain.
     * @param useCase <tt>MediaUseCase</tt> value indicating for the use-case of
     * device that we are trying to obtain.
     *
     * @return the currently default <tt>MediaDevice</tt> for the specified
     * <tt>MediaType</tt>, or <tt>null</tt> if no such device exists.
     */
    MediaDevice getDefaultDevice(
            MediaType mediaType,
            MediaUseCase useCase);

    /**
     * Gets the <tt>CaptureDevice</tt> user choices such as the default audio
     * and video capture devices.
     *
     * @return the <tt>CaptureDevice</tt> user choices such as the default audio
     * and video capture devices.
     */
    DeviceConfiguration getDeviceConfiguration();

    /**
     * Returns a list containing all devices known to this service
     * implementation and handling the specified <tt>MediaType</tt>.
     *
     * @param mediaType the media type (i.e. AUDIO or VIDEO) that we'd like
     * to obtain the device list for.
     * @param useCase <tt>MediaUseCase</tt> value indicating for the use-case of
     * device that we are trying to obtain.
     *
     * @return the list of <tt>MediaDevice</tt>s currently known to handle the
     * specified <tt>mediaType</tt>.
     */
    List<MediaDevice> getDevices(MediaType mediaType,
                                 MediaUseCase useCase);

    /**
     * Returns a {@link Map} that binds indicates whatever preferences the
     * media service implementation may have for the RTP payload type numbers
     * that get dynamically assigned to {@link MediaFormat}s with no static
     * payload type. The method is useful for formats such as "telephone-event"
     * for example that is statically assigned the 101 payload type by some
     * legacy systems. Signalling protocol implementations such as SIP and XMPP
     * should make sure that, whenever this is possible, they assign to formats
     * the dynamic payload type returned in this {@link Map}.
     *
     * @return a {@link Map} binding some formats to a preferred dynamic RTP
     * payload type number.
     */
    Map<MediaFormat, Byte> getDynamicPayloadTypePreferences();

    /**
     * Gets the <tt>MediaFormatFactory</tt> through which <tt>MediaFormat</tt>
     * instances may be created for the purposes of working with the
     * <tt>MediaStream</tt>s created by this <tt>MediaService</tt>.
     *
     * @return the <tt>MediaFormatFactory</tt> through which
     * <tt>MediaFormat</tt> instances may be created for the purposes of working
     * with the <tt>MediaStream</tt>s created by this <tt>MediaService</tt>
     */
    MediaFormatFactory getFormatFactory();

    /**
     * Gets the <tt>VolumeControl</tt> which controls the volume level of audio
     * output/playback in calls.
     *
     * @return the <tt>VolumeControl</tt> which controls the volume level of
     * audio output/playback in calls
     */
    VolumeControl getCallVolumeControl();

    /**
     * Gets the <tt>VolumeControl</tt> which controls the volume level of the
     * audio capture device.
     *
     * @return the <tt>VolumeControl</tt> which controls the volume level of
     * the audio capture device
     */
    VolumeControl getCaptureVolumeControl();

    /**
     * Gets the <tt>VolumeControl</tt> which controls the volume level of the
     * audio playback device.
     *
     * @return the <tt>VolumeControl</tt> which controls the volume level of
     * the audio playback device
     */
    VolumeControl getPlaybackVolumeControl();

    /**
     * Gets the <tt>VolumeControl</tt> which controls the volume level of the
     * audio notify device.
     *
     * @return the <tt>VolumeControl</tt> which controls the volume level of
     * the audio notify device
     */
    VolumeControl getNotifyVolumeControl();

    /**
     * Gives access to currently registered <tt>Recorder.Listener</tt>s.
     * @return currently registered <tt>Recorder.Listener</tt>s.
     */
    Iterator<Recorder.Listener> getRecorderListeners();

    /**
     * Creates a preview component for the specified device(video device) used
     * to show video preview from it.
     *
     * @param device the video device
     * @param preferredWidth the width we prefer for the component
     * @param preferredHeight the height we prefer for the component
     * @return the preview component.
     */
    Object getVideoPreviewComponent(
            MediaDevice device, int preferredWidth, int preferredHeight);

    /**
     * Removes a <tt>PropertyChangeListener</tt> to no longer be notified about
     * changes in the values of the properties of this instance.
     *
     * @param listener the <tt>PropertyChangeListener</tt> to no longer be
     * notified about changes in the values of the properties of this instance
     */
    void removePropertyChangeListener(PropertyChangeListener listener);
}
