/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia;

import com.google.common.annotations.VisibleForTesting;

import org.jitsi.impl.neomedia.device.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.util.*;

/**
 * Implementation of VolumeControl which uses system sound architecture (MacOsX
 * or Windows CoreAudio) to change input/output hardware volume.
 *
 * @author Vincent Lucas
 */
public class HardwareVolumeControl
    extends BasicVolumeControl
{
   /**
    * The <tt>Logger</tt> used by the <tt>HarwareVolumeControl</tt>
    * class and its instances for logging output.
    */
    private static final Logger logger
        = Logger.getLogger(HardwareVolumeControl.class);

   /**
    * The maximal power level used for hardware amplification. Over this value
    * software amplification is used.
    */
    private static final float MAX_HARDWARE_POWER = 1.0F;

   /**
    * The media service implementation.
    */
    private final MediaServiceImpl mMediaServiceImpl;

   /**
    * Whether this controls capture, playback or notify volume.
    */
    private final DataFlow mDataFlow;

   /**
    * Creates volume control instance and initializes initial level value
    * if stored in the configuration service.
    *
    * @param mediaServiceImpl The media service implementation.
    * @param dataFlow Whether this controls capture, playback or notify volume.
    * @param volumeLevelConfigurationPropertyName the name of the configuration
    * property which specifies the value of the volume level of the new
    * instance.
    */
    public HardwareVolumeControl(MediaServiceImpl mediaServiceImpl,
                                 DataFlow dataFlow,
                                 String volumeLevelConfigurationPropertyName)
    {
        super(volumeLevelConfigurationPropertyName,
                    (dataFlow == DataFlow.CAPTURE) ? Mode.INPUT : Mode.OUTPUT);

        mMediaServiceImpl = mediaServiceImpl;
        mDataFlow = dataFlow;

        // Gets the device volume (an error use the default volume).
        float volume = getVolume();

        if(volume != -1)
        {
            volumeLevel = volume;
        }
        else
        {
            volumeLevel = getDefaultVolumeLevel();
        }

        logger.debug("Created HVC " + this + " for dataFlow " + dataFlow);
    }

    /**
     * Returns the default volume level.
     *
     * @return The default volume level.
     */
    @VisibleForTesting
    public static float getDefaultVolumeLevel()
    {
        // By default set the device volume at the middle of its range.
        return MAX_HARDWARE_POWER / 2;
    }

    /**
     * Returns the reference volume level for computing the gain.
     *
     * @return The reference volume level for computing the gain.
     */
    protected static float getGainReferenceLevel()
    {
        // Starts to activate the gain (software amplification), only once the
        // microphone sensibility is sets to its maximum (hardware
        // amplification).
        return MAX_HARDWARE_POWER;
    }

    @Override
    protected void updateHardwareVolume()
    {
        // Gets the selected input device UID.
        String deviceUID = getDeviceUID();

        // Computes the hardware volume.
        float jitsiHarwareVolumeFactor = MAX_VOLUME_LEVEL / MAX_HARDWARE_POWER;
        float hardwareVolumeLevel = volumeLevel * jitsiHarwareVolumeFactor;
        if (hardwareVolumeLevel > 1.0F)
        {
            hardwareVolumeLevel = 1.0F;
        }

        logger.debug("Change hardware " + mDataFlow + " device level for " + deviceUID + " to " + hardwareVolumeLevel);

        // Changes the volume of the device.
        if (setDeviceVolume(deviceUID, hardwareVolumeLevel) != 0)
        {
            logger.debug("Could not change hardware " + mDataFlow + " device level");
        }
    }

   /**
    * Changes the device volume via the system API.
    *
    * @param deviceUID The device ID.
    * @param volume The volume requested.
    *
    * @return 0 if everything works fine.
    */
    protected int setDeviceVolume(String deviceUID, float volume)
    {
        if (deviceUID == null)
        {
            return -1;
        }

        if(CoreAudioDevice.initDevices() == -1)
        {
            CoreAudioDevice.freeDevices();
            logger.debug("Could not initialize CoreAudio devices");
            return -1;
        }

        if(setCoreAudioDeviceVolume(deviceUID, volume) != 0)
        {
            CoreAudioDevice.freeDevices();
            logger.debug("Could not change CoreAudio device level");
            return -1;
        }

        CoreAudioDevice.freeDevices();

        return 0;
    }

   /**
    * Attempts to change volume through CoreAudio.
    *
    * @param deviceUID The device ID.
    * @param volume The volume requested.
    *
    * @return 0 if everything works fine.
    */
    private int setCoreAudioDeviceVolume(String deviceUID, float volume)
    {
        return (mDataFlow == DataFlow.CAPTURE) ?
            CoreAudioDevice.setInputDeviceVolume(deviceUID, volume) :
            CoreAudioDevice.setOutputDeviceVolume(deviceUID, volume);
    }

   /**
    * Current volume value.
    *
    * @return the current volume level.
    *
    * @see org.jitsi.service.neomedia.VolumeControl
    */
    @Override
    public float getVolume()
    {
        String deviceUID = getDeviceUID();
        float volume = getDeviceVolume(deviceUID);

        // If the hardware volume for this device is not available, then switch
        // to the software volume.
        if(volume == -1)
        {
            volume = super.getVolume();
        }
        else
        {
            volumeLevel = volume;
        }

        logger.debug("Hardware " + mDataFlow + " device level for " + deviceUID + " is " + volume);

        return volume;
    }

    /**
     * Returns the selected device UID.
     *
     * @return The selected device UID. Or null if not found.
     */
    protected String getDeviceUID()
    {
        AudioSystem audioSystem
            = mMediaServiceImpl.getDeviceConfiguration().getAudioSystem();
        Device device
            = (audioSystem == null)
                ? null
                : audioSystem.getSelectedDevice(mDataFlow);

        return (device == null) ? null : device.getUID();
    }

    /**
     * Returns the device volume via the system API.
     *
     * @param deviceUID The device ID.
     *
     * @Return A scalar value between 0 and 1 if everything works fine. -1 if an
     * error occurred.
     */
    protected float getDeviceVolume(String deviceUID)
    {
        float volume;

        if(deviceUID == null)
        {
            return -1;
        }

        if (CoreAudioDevice.initDevices() == -1)
        {
            logger.debug("Could not initialize CoreAudio devices.");
        }

        if((volume = getCoreAudioDeviceVolume(deviceUID)) == -1)
        {
            CoreAudioDevice.freeDevices();
            logger.debug("Could not get CoreAudio device volume");
            return -1;
        }

        CoreAudioDevice.freeDevices();

        return volume;
    }

    /**
     * Attempts to get volume from CoreAudio.
     *
     * @param deviceUID The device ID.
     *
     * @return 0 if everything works fine.
     */
    private float getCoreAudioDeviceVolume(String deviceUID)
    {
        return (mDataFlow == DataFlow.CAPTURE)
            ? CoreAudioDevice.getInputDeviceVolume(deviceUID)
            : CoreAudioDevice.getOutputDeviceVolume(deviceUID);
    }
}
