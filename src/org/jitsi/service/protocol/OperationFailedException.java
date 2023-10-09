/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.service.protocol;

/**
 * <tt>OperationFailedException</tt> indicates an exception that occurred in the
 * API.
 * <p>
 * <p>A matching class is provided by net.java.sip.communicator.service.protocol.OperationFailedException
 * <p>
 * <tt>OperationFailedException</tt> contains an error code that gives more
 * information on the exception. The application can obtain the error code using
 * {@link OperationFailedException#getErrorCode()}. The error code values are
 * defined in the <tt>OperationFailedException</tt> fields.
 * </p>
 *
 * @author Emil Ivov
 */
public class OperationFailedException
    extends Exception
{
    /**
     * Serial version UID.
     */
    private static final long serialVersionUID = 0L;

    /**
     * Set when no other error code can describe the exception that occurred.
     */
    public static final int GENERAL_ERROR = 1;

    /**
     * Set when command fails due to a failure in network communications or
     * a transport error.
     */
    public static final int NETWORK_FAILURE = 2;

    /**
     * Set when an operation fails for implementation specific reasons.
     */
    public static final int INTERNAL_ERROR = 4;

    /**
     * Indicates that authentication with a server has failed.
     */
    public static final int AUTHENTICATION_FAILED = 401;

    /**
     * The error code of the exception
     */
    private final int errorCode;

    /**
     * Creates an exception with the specified error message and error code.
     * @param message A message containing details on the error that caused the
     * exception
     * @param errorCode the error code of the exception (one of the error code
     * fields of this class)
     */
    public OperationFailedException(String message, int errorCode)
    {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * Creates an exception with the specified message, errorCode and cause.
     * @param message A message containing details on the error that caused the
     * exception
     * @param errorCode the error code of the exception (one of the error code
     * fields of this class)
     * @param cause the error that caused this exception
     */
    public OperationFailedException(String message,
                                    int errorCode,
                                    Throwable cause)
    {
        super(message, cause);

        this.errorCode = errorCode;
    }

    /**
     * Obtain the error code value.
     *
     * @return the error code for the exception.
     */
    public int getErrorCode()
    {
        return errorCode;
    }
}
