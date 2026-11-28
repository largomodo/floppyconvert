package com.largomodo.floppyconvert.service;

import com.largomodo.floppyconvert.core.FailureCategory;
import com.largomodo.floppyconvert.core.FailureClassifier;
import com.largomodo.floppyconvert.snes.InvalidChecksumException;
import com.largomodo.floppyconvert.snes.InvalidRomHeaderException;
import com.largomodo.floppyconvert.snes.UnsupportedHardwareException;

/**
 * Classifies conversion failures by the snes exception found anywhere in the cause chain. Lives in service because
 * core cannot import snes types; the facade may wrap the exception, so the chain is walked.
 */
public class SnesFailureClassifier implements FailureClassifier {

    // Cause chains are short; the bound only guards against a malformed cyclic chain
    private static final int MAX_CAUSE_DEPTH = 32;

    @Override
    public FailureCategory classify(Exception e) {
        Throwable current = e;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (current instanceof InvalidChecksumException) {
                return FailureCategory.CHECKSUM;
            }
            if (current instanceof UnsupportedHardwareException unsupported) {
                return unsupported.reason() == UnsupportedHardwareException.Reason.HARDWARE
                        ? FailureCategory.HARDWARE
                        : FailureCategory.SIZE;
            }
            if (current instanceof InvalidRomHeaderException) {
                return FailureCategory.HEADER;
            }
            current = current.getCause();
        }
        return FailureCategory.OTHER;
    }
}
