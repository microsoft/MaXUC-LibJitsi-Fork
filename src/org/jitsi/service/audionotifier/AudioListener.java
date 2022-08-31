// Copyright (c) Microsoft Corporation. All rights reserved.
package org.jitsi.service.audionotifier;

/**
 * Used to track the state of audio clips that are playing.
 * Handler functions are all executed in the same thread, so implementations
 * must be careful to avoid blocking execution.
 */
public interface AudioListener
{
    /**
     * Called when playback of the associated audio clip begins. Note that this
     * happens on a shared thread, so the implementation should avoid blocking
     * execution.
     */
    void onClipStarted();

    /**
     * Called when playback of the associated audio clip ends. Note that this
     * happens on a shared thread, so the implementation should avoid blocking
     * execution.
     */
    void onClipEnded();
}
