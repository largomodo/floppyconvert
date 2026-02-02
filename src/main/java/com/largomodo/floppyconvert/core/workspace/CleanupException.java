package com.largomodo.floppyconvert.core.workspace;

import java.io.IOException;
import java.util.List;

/**
 * Exception thrown when workspace cleanup encounters failures.
 * <p>
 * Accumulates all cleanup failures as suppressed exceptions to preserve
 * full diagnostic context for production troubleshooting.
 * Replaces swallowed exceptions that lose cause chains.
 */
public class CleanupException extends RuntimeException {

    /**
     * Create exception with accumulated cleanup failures.
     *
     * @param message Description of cleanup failure context
     * @param failures List of IOExceptions from cleanup attempts
     */
    public CleanupException(String message, List<IOException> failures) {
        super(message);
        // Preserve all failure causes for diagnostics
        for (IOException failure : failures) {
            addSuppressed(failure);
        }
    }
}
