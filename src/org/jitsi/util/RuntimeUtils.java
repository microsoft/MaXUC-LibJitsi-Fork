package org.jitsi.util;

import java.io.IOException;

/**
 * A simple helper class which we use to proxy calls to Runtime.getRuntime()
 * in order to make it mockable in tests.
 */
public class RuntimeUtils
{
    /**
     * Just proxies the call to Runtime.getRuntime().addShutdownHook().
     */
    public static void addShutdownHook(Thread hook)
    {
        Runtime.getRuntime().addShutdownHook(hook);
    }

    /**
     * Calls Runtime.getRuntime().halt(systemExitCode).
     */
    public static void halt(int systemExitCode)
    {
        Runtime.getRuntime().halt(systemExitCode);
    }

    /**
     * Calls Runtime.getRuntime().exec(command).
     */
    public static Process exec(String command) throws IOException
    {
        return Runtime.getRuntime().exec(command);
    }

    /**
     * Calls Runtime.getRuntime().exec([commands]).
     */
    public static Process execVarargs(String... command) throws IOException
    {
        return Runtime.getRuntime().exec(command); // CodeQL [SM00679] Not Exploitable. The command is not user provided.
    }
}
