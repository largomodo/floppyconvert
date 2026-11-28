package com.largomodo.floppyconvert.snes;

/**
 * Thrown when ROM conversion exceeds hardware constraints of target copier format.
 * <p>
 * RuntimeException enables fail-fast validation without catch blocks at every call site.
 * Follows CleanupException pattern for unchecked exceptional conditions.
 * <p>
 * The {@link Reason} separates "no copier converts this" from "a larger configuration would": a batch audit
 * can then tell impossible ROMs from ROMs that need {@code --gd3-dram 128}. (ref: DL-010, DL-011)
 */
public class UnsupportedHardwareException extends RuntimeException {

    /**
     * Why the conversion was refused.
     */
    public enum Reason {
        /** The ROM size or layout is outside what the copier format can represent. */
        SIZE,
        /** The configured hardware is too small; a larger configuration would convert it. */
        HARDWARE
    }

    private final Reason reason;

    /**
     * Constructs exception with descriptive message; the reason defaults to {@link Reason#SIZE}.
     *
     * @param message Details about hardware constraint violation
     */
    public UnsupportedHardwareException(String message) {
        this(Reason.SIZE, message);
    }

    /**
     * Constructs exception with message and underlying cause; the reason defaults to {@link Reason#SIZE}.
     */
    public UnsupportedHardwareException(String message, Throwable cause) {
        this(Reason.SIZE, message, cause);
    }

    // Callers that know the cause pass the reason; the batch classifier reads it back.
    public UnsupportedHardwareException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public UnsupportedHardwareException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
