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
     * Starts a thread from an argument.
     */
    public static void startThread(Thread thread)
    {
        thread.start();
    }

    /**
     * Creates a new Thread and starts it immediately.
     */
    public static void createAndStartThread(Runnable runnable, String name)
    {
        new Thread(runnable, name).start();
    }

    /**
     * Calls wait(timeout) on the passed Thread.
     */
    public static void wait(Thread thread, long timeout) throws InterruptedException
    {
        thread.wait(timeout);
    }

    /**
     * Just a proxy method to call .wait on the object, so we can mock it in tests.
     *
     * @param object object on which .wait() is called.
     */
    public static void wait(Object object) throws InterruptedException
    {
        object.wait();
    }

    /**
     * Just a proxy method to call .wait(timeoutMillis) on the object, so we can mock it in tests.
     *
     * @param object object on which .wait(timeoutMillis) is called.
     * @param timeoutMillis the maximum time to wait, in milliseconds.
     */
    public static void wait(Object object, long timeoutMillis) throws InterruptedException
    {
        object.wait(timeoutMillis);
    }

    /**
     * Just a proxy method to call .notifyAll on the object, so we can mock it in tests.
     *
     * @param lock object on which .notifyAll() is called.
     */
    public static void notifyAll(Object lock)
    {
        lock.notifyAll();
    }

    /**
     * Just a proxy method to call .notify on the object, so we can mock it in tests.
     *
     * @param object object on which .notify() is called.
     */
    public static void notify(Object object)
    {
        object.notify();
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
