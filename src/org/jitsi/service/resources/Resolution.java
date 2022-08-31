// Copyright (c) Microsoft Corporation. All rights reserved.
package org.jitsi.service.resources;

/**
 * Callback waiting for something to be resolved
 *
 * @param <T> Type of the resolved element
 */
public interface Resolution<T>
{
    /**
     * Called when the element is resolved
     *
     * @param resolved
     */
    void onResolution(T resolved);
}
