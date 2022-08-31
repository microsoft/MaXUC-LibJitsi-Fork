// Copyright (c) Microsoft Corporation. All rights reserved.
package org.jitsi.util;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A thread factory for use in executors which names the threads that are created
 */
public final class NamedThreadFactory implements ThreadFactory
{
    private final AtomicInteger i = new AtomicInteger(1);

    /**
     * The name to give the thread
     */
    private final String mName;

    public NamedThreadFactory(String name)
    {
        mName = name;
    }

    @Override
    public Thread newThread(Runnable r)
    {
        return new Thread(r, mName + "-" + i.getAndIncrement());
    }
}