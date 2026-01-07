package com.largomodo.floppyconvert.service;

import org.apache.commons.exec.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Base class for external process execution with timeout and exit code enforcement.
 *
 * Provides watchdog-based timeout protection (prevents hung ucon64/mtools from stalling
 * batch jobs) and automatic non-zero exit code detection (fails fast on tool errors
 * with stderr captured for diagnostics).
 *
 * Subclasses: Ucon64Driver, MtoolsDriver
 */
public abstract class ExternalProcessDriver {

    // 60-second timeout = 6x safety margin over typical 10s ucon64 split operation
    // Prevents infinite hangs on corrupted ROMs while covering 99% of normal cases
    protected static final long DEFAULT_TIMEOUT_MS = 60_000;

    /**
     * Execute external command with timeout and exit code validation.
     *
     * @param cmdArray Command and arguments (cmdArray[0] = binary path)
     * @param timeoutMs Watchdog timeout in milliseconds
     * @return Exit code (always 0; non-zero throws exception)
     * @throws ProcessTimeoutException if watchdog kills process
     * @throws ProcessFailureException if exit code != 0 or execution fails
     */
    protected int executeCommand(String[] cmdArray, long timeoutMs) throws IOException {
        // Validate timeout is positive (prevents disabling watchdog or immediate kills)
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("Timeout must be positive, got: " + timeoutMs + "ms");
        }

        // Build CommandLine from array (Commons Exec handles escaping/quoting)
        CommandLine cmdLine = new CommandLine(cmdArray[0]);
        for (int i = 1; i < cmdArray.length; i++) {
            cmdLine.addArgument(cmdArray[i]);
        }

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        DefaultExecutor executor = new DefaultExecutor();
        executor.setStreamHandler(new PumpStreamHandler(stdout, stderr));

        // Watchdog terminates process if execution exceeds timeout
        // Prevents hung ucon64/mtools processes from stalling batch jobs indefinitely
        // 60s default = 6x typical runtime, balances responsiveness with large ROM tolerance
        ExecuteWatchdog watchdog = new ExecuteWatchdog(timeoutMs);
        executor.setWatchdog(watchdog);

        try {
            int exitCode = executor.execute(cmdLine);

            // Non-zero exit = tool failure (corrupted ROM, invalid arguments, disk full, etc.)
            // Fail fast with stderr for diagnostics rather than silently continuing
            if (exitCode != 0) {
                throw new ProcessFailureException(
                    "Process exited with code " + exitCode + ": " + stderr.toString()
                );
            }

            return exitCode;

        } catch (ExecuteException e) {
            // Check watchdog FIRST: if process was killed, that's the root cause
            // ExecuteException alone is ambiguous (could be exit code or timeout)
            if (watchdog.killedProcess()) {
                throw new ProcessTimeoutException(
                    "Process exceeded timeout of " + timeoutMs + "ms"
                );
            }
            throw new ProcessFailureException(
                "Process failed: " + e.getMessage() + "\nStderr: " + stderr.toString()
            );
        }
    }

    public static class ProcessTimeoutException extends IOException {
        public ProcessTimeoutException(String message) {
            super(message);
        }
    }

    public static class ProcessFailureException extends IOException {
        public ProcessFailureException(String message) {
            super(message);
        }
    }
}
