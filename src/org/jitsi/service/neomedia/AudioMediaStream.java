/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.service.neomedia;

import org.jitsi.service.neomedia.event.*;
import org.jitsi.service.protocol.*;

/**
 * Extends the <tt>MediaStream</tt> interface and adds methods specific to
 * audio streaming.
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 */
public interface AudioMediaStream
    extends MediaStream
{
    /**
     * Registers a listener that would receive notification events if the
     * remote party starts sending DTMF tones to us.
     *
     * @param listener the <tt>DTMFListener</tt> that we'd like to register.
     */
    void addDTMFListener(DTMFListener listener);

    /**
     * Removes <tt>listener</tt> from the list of <tt>DTMFListener</tt>s
     * registered to receive events for incoming DTMF tones.
     *
     * @param listener the listener that we'd like to unregister
     */
    void removeDTMFListener(DTMFListener listener);

    /**
     * Registers <tt>listener</tt> as the <tt>CsrcAudioLevelListener</tt> that
     * will receive notifications for changes in the levels of conference
     * participants that the remote party could be mixing.
     *
     * @param listener the <tt>CsrcAudioLevelListener</tt> that we'd like to
     * register or <tt>null</tt> if we'd like to stop receiving notifications.
     */
    void setCsrcAudioLevelListener(CsrcAudioLevelListener listener);

    /**
     * Sets <tt>listener</tt> as the <tt>SimpleAudioLevelListener</tt>
     * registered to receive notifications for changes in the levels of the
     * audio that this stream is sending out.
     *
     * @param listener the <tt>SimpleAudioLevelListener</tt> that we'd like to
     * register or <tt>null</tt> if we want to stop local audio level
     * measurements.
     */
    void setLocalUserAudioLevelListener(
            SimpleAudioLevelListener listener);

    /**
     * Sets <tt>listener</tt> as the <tt>SimpleAudioLevelListener</tt>
     * registered to receive notifications for changes in the levels of the
     * party that's at the other end of this stream.
     *
     * @param listener the <tt>SimpleAudioLevelListener</tt> that we'd like to
     * register or <tt>null</tt> if we want to stop stream audio level
     * measurements.
     */
    void setStreamAudioLevelListener(SimpleAudioLevelListener listener);

    /**
     * Starts sending the specified <tt>DTMFTone</tt> until the
     * <tt>stopSendingDTMF()</tt> method is called (Excepts for INBAND DTMF,
     * which stops by itself this is why where there is no need to call the
     * stopSendingDTMF). Callers should keep in mind the fact that calling this
     * method would most likely interrupt all audio transmission until the
     * corresponding stop method is called. Also, calling this method
     * successively without invoking the corresponding stop method between the
     * calls will simply replace the <tt>DTMFTone</tt> from the first call with
     * that from the second.
     *
     * @param tone the <tt>DTMFTone</tt> to start sending.
     * @param dtmfMethod The kind of DTMF used (RTP, SIP-INOF or INBAND).
     * @param minimalToneDuration The minimal DTMF tone duration.
     * @param maximalToneDuration The maximal DTMF tone duration.
     * @param volume The DTMF tone volume. Describes the power level of the
     *               tone, expressed in dBm0 after dropping the sign.
     */
    void startSendingDTMF(
            DTMFTone tone,
            DTMFMethod dtmfMethod,
            int minimalToneDuration,
            int maximalToneDuration,
            int volume);

    /**
     * Interrupts transmission of a <tt>DTMFTone</tt> started with the
     * <tt>startSendingDTMF</tt> method. This method has no effect if no tone
     * is being currently sent.
     *
     * @param dtmfMethod the <tt>DTMFMethod</tt> to stop sending.
     */
    void stopSendingDTMF(DTMFMethod dtmfMethod);
}
