package ghart.space.pi_drive.shared.obd

import java.io.IOException

/**
 * Thrown when an OBD adapter does not send the `>` prompt within the configured timeout.
 *
 * Wraps the underlying [java.net.SocketTimeoutException] or timeout detection as a single
 * domain-level exception so callers do not need to handle transport-specific exception types.
 *
 * @param message Description of the timeout, including the adapter and duration.
 * @param cause   The underlying socket or IO exception, if any.
 */
class OBDTimeoutException(message: String, cause: Throwable? = null) : IOException(message, cause)
