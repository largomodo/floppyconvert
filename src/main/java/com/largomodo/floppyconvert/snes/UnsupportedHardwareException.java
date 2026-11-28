package com.largomodo.floppyconvert.snes;

/**
 * Thrown when ROM conversion exceeds hardware constraints of target copier format.
 * <p>
 * RuntimeException enables fail-fast validation without catch blocks at every call site.
 * Follows CleanupException pattern for unchecked exceptional conditions.
 */
public class UnsupportedHardwareException extends RuntimeException {

    /**
     * Constructs exception with descriptive message.
     *
     * @param message Details about hardware constraint violation
     */
    public UnsupportedHardwareException(String message) {
        super(message);
    }

    /**
     * Constructs exception with message and underlying cause.
     */
    public UnsupportedHardwareException(String message, Throwable cause) {
        super(message, cause);
    }
}
