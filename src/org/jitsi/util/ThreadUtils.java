// Copyright (c) Microsoft Corporation. All rights reserved.
package org.jitsi.util;

/**
 * A simple helper class which we use to proxy calls to Thread in order to make it mockable in tests.
 */
public final class ThreadUtils
{
    /**
     * Prevents the initialization of <tt>ThreadUtils</tt> instances.
     */
    private ThreadUtils()
    {
    }

    /**
     * Creates a new Thread and starts it immediately.
     */
    public static void startThread(Thread thread)
    {
        thread.start();
    }

    /**
     * Calls wait(timeout) on the passed Thread.
     */
    public static void wait(Thread thread, long timeout) throws InterruptedException
    {
        thread.wait(timeout);
    }

    /**
     * Calls notifyAll() on the passed lock.
     */
    public static void notifyAll(Object lock)
    {
        lock.notifyAll();
    }

    /**
     * Proxy method for Thread.getDefaultUncaughtExceptionHandler()
     *
     * @return <tt>UncaughtExceptionHandler</tt>
     */
    public static Thread.UncaughtExceptionHandler getDefaultUncaughtExceptionHandler()
    {
        return Thread.getDefaultUncaughtExceptionHandler();
    }
}
