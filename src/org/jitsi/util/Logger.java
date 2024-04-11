/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.util;

import static java.lang.String.*;

import java.util.*;
import java.util.logging.*;

/**
 * Standard logging methods plus MSw extra USER level.  Logging should conform to
 * Java standards and also match our use in Android.
 *
 * Configuration for all loggers is in logging.properties, or which there are
 * 2 copies to be kept in sync, at: jitsi/lib and jitsi/resources/install
 *
 * Note, there is an [almost] duplicate of this class in net.java.sip.communicator.util.
 * Please keep in sync (or maybe we can commonize).
 *
 * @author Emil Ivov
 */
public class Logger
{
    /**
     * The java.util.Logger that would actually be doing the logging.
     */
    private final java.util.logging.Logger loggerDelegate;

    /**
     * The line separator to use for separating log lines on this system.
     */
    private static final String lineSeparator = System
        .getProperty("line.separator");

    /**
     * Base constructor
     *
     * @param logger the implementation specific logger delegate that this
     * Logger instance should be created around.
     */
    private Logger(java.util.logging.Logger logger)
    {
        this.loggerDelegate = logger;
    }

    /**
     * Find or create a logger for the specified class.  If a logger has
     * already been created for that class it is returned.  Otherwise
     * a new logger is created.
     * <p>
     * If a new logger is created its log level will be configured
     * based on the logging configuration and it will be configured
     * to also send logging output to its parent's handlers.
     * <p>
     * @param clazz The creating class.
     *
     * @return a suitable Logger
     *
     * @throws NullPointerException if the name is null.
     */
    public static Logger getLogger(Class<?> clazz)
        throws NullPointerException
    {
        return getLogger(clazz.getName());
    }

    /**
     * Find or create a logger for a named subsystem.  If a logger has
     * already been created with the given name it is returned.  Otherwise
     * a new logger is created.
     * <p>
     * If a new logger is created its log level will be configured
     * based on the logging configuration and it will be configured
     * to also send logging output to its parent's handlers.
     *
     * @param name A name for the logger. This should be a dot-separated name
     * and should normally be based on the class name of the creator, such as
     * "net.java.sip.communicator.MyFunnyClass"
     *
     * @return a suitable Logger
     *
     * @throws NullPointerException if the name is null.
     */
    public static Logger getLogger(String name)
        throws NullPointerException
    {
        return new Logger(java.util.logging.Logger.getLogger(name));
    }

    /**
     * Utility function to concatenate the string representation of none or more
     * objects.  This will only be called if a particular log level is enabled,
     * making it more efficient when the level is disabled (fairly important
     * for finer and below which are off by default.
     */
    private String concatenateParameters(Object... msgs)
    {
        StringBuilder builder = new StringBuilder();

        for (Object msg : msgs)
        {
            builder.append(msg.toString());
            builder.append(", ");
        }
        return builder.toString();
    }

    /**
     * Logs an entry in the calling method.
     * <p>
     * Use varargs where possible, i.e. use this: sLog.entry("a", "b", "c");
     * NOT this: sLog.entry("a" + "b" + "c");
     */
    public void entry(Object... msgs)
    {
        if (loggerDelegate.isLoggable(Level.FINEST))
        {
            loggerDelegate.log(Level.FINEST, "[entry] " + concatenateParameters(msgs));
        }
    }

    /**
     * Logs exiting the calling method
     * <p>
     * Use varargs where possible, i.e. use this: sLog.exit("a", "b", "c");
     * NOT this: sLog.exit("a" + "b" + "c");
     */
    public void exit(Object... msgs)
    {
        if (loggerDelegate.isLoggable(Level.FINEST))
        {
            loggerDelegate.log(Level.FINEST, "[exit] " + concatenateParameters(msgs));
        }
    }

    /**
     * Check if a message with a TRACE level would actually be logged by this
     * logger.
     *
     * @return true if the TRACE level is currently being logged
     */
    public boolean isTraceEnabled()
    {
        return loggerDelegate.isLoggable(Level.FINER);
    }

    /**
     * Log a TRACE message.
     * <p>
     * Use varargs where possible, i.e. use this: sLog.trace("a", "b", "c");
     * NOT this: sLog.trace("a" + "b" + "c");
     * <p>
     * If the logger is currently enabled for the TRACE message
     * level then the given message is forwarded to all the
     * registered output Handler objects.
     *
     * @param msgs The messages to log
     */
    public void trace(Object... msgs)
    {
        if (isTraceEnabled())
        {
            loggerDelegate.finer(concatenateParameters(msgs));
        }
    }

    /**
     * Log a message, with associated Throwable information.
     *
     * @param msg   The message to log
     * @param   t   Throwable associated with log message.
     */
    public void trace(Object msg, Throwable t)
    {
        loggerDelegate.log(Level.FINER, msg!=null?msg.toString():"null", t);
    }

    /**
     * Check if a message with a DEBUG level would actually be logged by this
     * logger.
     *
     * @return true if the DEBUG level is currently being logged
     */
    public boolean isDebugEnabled()
    {
        return loggerDelegate.isLoggable(Level.FINE);
    }

    /**
     * Log a DEBUG message.
     * <p>
     * Use varargs where possible, i.e. use this: sLog.debug("a", "b", "c");
     * NOT this: sLog.debug("a" + "b" + "c");
     * <p>
     * This version is required to prevent 'Failed to find debug method errors'
     * in JavaLogger.  Even though the version taking varargs would be execpted
     * to handle it.
     *
     * @param msg The message to log
     */
    public void debug(Object msg)
    {
        loggerDelegate.fine(msg!=null?msg.toString():"null");
    }

    /**
     * Log a DEBUG message.
     * <p>
     * Use varargs where possible, i.e. use this: sLog.debug("a", "b", "c");
     * NOT this: sLog.debug("a" + "b" + "c");
     * <p>
     * If the logger is currently enabled for the DEBUG message
     * level then the given message is forwarded to all the
     * registered output Handler objects.
     *
     * @param msg The message to log
     */
    public void debug(Object... msg)
    {
        if (isDebugEnabled())
        {
            loggerDelegate.fine(concatenateParameters(msg));
        }
    }

    /**
     * Log a message, with associated Throwable information.
     *
     * @param msg    The message to log
     * @param t  Throwable associated with log message.
     */
    public void debug(Object msg, Throwable t)
    {
        loggerDelegate.log(Level.FINE, msg!=null?msg.toString():"null", t);
    }

    /**
     * Check if a message with an INFO level would actually be logged by this
     * logger.
     *
     * @return true if the INFO level is currently being logged
     */
    public boolean isInfoEnabled()
    {
        return loggerDelegate.isLoggable(Level.INFO);
    }

    /**
     * Log a INFO message.
     * <p>
     * If the logger is currently enabled for the INFO message
     * level then the given message is forwarded to all the
     * registered output Handler objects.
     *
     * @param msg The message to log
     */
    public void info(Object msg)
    {
        loggerDelegate.info(msg!=null?msg.toString():"null");
    }

    /**
     * Log a message, with associated Throwable information.
     *
     * @param msg   The message to log
     * @param t  Throwable associated with log message.
     */
    public void info(Object msg, Throwable t)
    {
        loggerDelegate.log(Level.INFO, msg!=null?msg.toString():"null", t);
    }

    /**
     * Log a CONFIG message.
     * <p>
     * Use varargs where possible, i.e. use this: sLog.config("a", "b", "c");
     * NOT this: sLog.config("a" + "b" + "c");
     * <p>
     * If the logger is currently enabled for the CONFIG message
     * level then the given message is forwarded to all the
     * registered output Handler objects.
     *
     * @param msg The message to log
     */
    public void config(Object msg)
    {
        loggerDelegate.config(msg!=null?msg.toString():"null");
    }

    /**
     * Log a message, with associated Throwable information.
     *
     * @param msg   The message to log
     * @param t  Throwable associated with log message.
     */
    public void config(Object msg, Throwable t)
    {
        loggerDelegate.log(Level.CONFIG, msg!=null?msg.toString():"null", t);
    }

    /**
     * Log a USER message.
     * <p>
     * If the logger is currently enabled for the INFO message
     * level then the given message is forwarded to all the
     * registered output Handler objects.
     *
     * @param msg The message to log
     */
    public void user(Object msg)
    {
        loggerDelegate.log(Level.USER, msg!=null?msg.toString():"null");
    }

    /**
     * Log a message, with associated Throwable information.
     *
     * @param msg   The message to log
     * @param t  Throwable associated with log message.
     */
    public void user(Object msg, Throwable t)
    {
        loggerDelegate.log(Level.USER, msg!=null?msg.toString():"null", t);
    }

    /**
     * Log a WARN message.
     * <p>
     * If the logger is currently enabled for the WARN message
     * level then the given message is forwarded to all the
     * registered output Handler objects.
     *
     * @param msg The message to log
     */
    public void warn(Object msg)
    {
        loggerDelegate.warning(msg!=null?msg.toString():"null");
    }

    /**
     * Log a message, with associated Throwable information.
     *
     * @param msg   The message to log
     * @param t  Throwable associated with log message.
     */
    public void warn(Object msg, Throwable t)
    {
        loggerDelegate.log(Level.WARNING, msg!=null?msg.toString():"null", t);
    }

    /**
     * Log a ERROR message.
     * <p>
     * If the logger is currently enabled for the ERROR message
     * level then the given message is forwarded to all the
     * registered output Handler objects.
     *
     * @param msg The message to log
     */
    public void error(Object msg)
    {
        loggerDelegate.severe(msg!=null?msg.toString():"null");
    }

    /**
     * Log a message, with associated Throwable information.
     *
     * @param msg   The message to log
     * @param t  Throwable associated with log message.
     */
    public void error(Object msg, Throwable t)
    {
        loggerDelegate.log(Level.SEVERE, msg!=null?msg.toString():"null", t);
    }

    /**
     * Log a FATAL message.
     * <p>
     * If the logger is currently enabled for the FATAL message
     * level then the given message is forwarded to all the
     * registered output Handler objects.
     *
     * @param msg The message to log
     */
    public void fatal(Object msg)
    {
        loggerDelegate.severe(msg!=null?msg.toString():"null");
    }

    /**
     * Log a message, with associated Throwable information.
     *
     * @param msg   The message to log
     * @param t  Throwable associated with log message.
     */
    public void fatal(Object msg, Throwable t)
    {
        loggerDelegate.log(Level.SEVERE, msg!=null?msg.toString():"null", t);
    }

    /**
     * Set logging level for all handlers to FATAL
     */
    public void setLevelFatal()
    {
        setLevel(Level.SEVERE);
    }

    /**
     * Set logging level for all handlers to ERROR
     */
    public void setLevelError()
    {
        setLevel(Level.SEVERE);
    }

    /**
     * Set logging level for all handlers to WARNING
     */
    public void setLevelWarn()
    {
        setLevel(Level.WARNING);
    }

    /**
     * Set logging level for all handlers to INFO
     */
    public void setLevelInfo()
    {
        setLevel(Level.INFO);
    }

    /**
     * Set logging level for all handlers to DEBUG
     */
    public void setLevelDebug()
    {
        setLevel(Level.FINE);
    }

    /**
     * Set logging level for all handlers to TRACE
     */
    public void setLevelTrace()
    {
        setLevel(Level.FINER);
    }

    /**
     * Set logging level for all handlers to ALL (allow all log messages)
     */
    public void setLevelAll()
    {
        setLevel(Level.ALL);
    }

    /**
     * Set logging level for all handlers to OFF (allow no log messages)
     */
    public void setLevelOff()
    {
        setLevel(Level.OFF);
    }

    /**
     * Set logging level for all handlers to <tt>level</tt>
     *
     * @param level the level to set for all logger handlers
     */
    private void setLevel(java.util.logging.Level level)
    {
        Handler[] handlers = loggerDelegate.getHandlers();
        for (Handler handler : handlers)
            handler.setLevel(level);

        loggerDelegate.setLevel(level);
    }

    /**
     * Reinitialize the logging properties and reread the logging configuration.
     * <p>
     * The same rules are used for locating the configuration properties
     * as are used at startup. So if the properties containing the log dir
     * locations have changed, we would read the new configuration.
     */
    public void reset()
    {
        try
        {
            LogManager.getLogManager().reset();
            LogManager.getLogManager().readConfiguration();
        }
        catch (Exception e)
        {
            loggerDelegate.log(Level.INFO, "Failed to reinit logger.", e);
        }
    }

    /**
     * Log out the current stacks of all threads in our process.
     */
    public void dumpThreads()
    {
        StringBuffer buffer = new StringBuffer();
        buffer.append("Dumping current threads:");
        Map<Thread, StackTraceElement[]> stackTraces =
            Thread.getAllStackTraces();
        for (Thread thread : stackTraces.keySet())
        {
            buffer.append(lineSeparator);
            buffer.append(format("Thread %s [%d]: (state = %s)",
                thread.getName(), thread.getId(), thread.getState()));
            for (StackTraceElement stackTraceElement : stackTraces.get(thread))
            {
                buffer.append(lineSeparator);
                buffer.append(format(" - %s", stackTraceElement.toString()));
            }

            buffer.append(lineSeparator);
        }

        loggerDelegate.info(buffer.toString());
    }

    /**
     * Just a wrapper around java.util.logging.Level to allow us to create our
     * own custom logging levels.
     */
    public static class Level extends java.util.logging.Level
    {
        private static final long serialVersionUID = 0L;

        protected Level(String name, int value)
        {
            super(name, value);
        }

        /**
         * USER is a message level for user actions.
         * This level is initialized to <CODE>800</CODE>, i.e.
         * the same as INFO.
         */
        public static final Level USER = new Level("USER", 800);
    }
}
