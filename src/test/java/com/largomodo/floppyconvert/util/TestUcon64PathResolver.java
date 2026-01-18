package com.largomodo.floppyconvert.util;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Test utility for ucon64 binary path resolution.
 * <p>
 * Implements resolution strategy: system PATH only.
 * Returns Optional.empty() when binary unavailable, allowing tests to skip gracefully via JUnit Assumptions.
 * <p>
 * Static utility class pattern: resolution is stateless and reusable across
 * multiple test classes (Ucon64DriverTest, AppE2ETest, future tests).
 */
public class TestUcon64PathResolver {

    /**
     * Resolves the path to the ucon64 binary.
     * <p>
     * Resolution strategy:
     * 1. If ucon64 is available in PATH, returns the system path (e.g., /usr/local/bin/ucon64)
     * 3. Returns Optional.empty() if neither location is available
     *
     * @return Optional containing the absolute path to ucon64, or empty if not found
     */
    public static Optional<String> resolveUcon64Path() {
        // Stage 1: Check system PATH
        if (isCommandAvailable("ucon64")) {
            try {
                Process process = new ProcessBuilder("which", "ucon64")
                        .redirectErrorStream(true)
                        .start();
                // 5-second timeout (which command completes in <100ms; generous margin for slow systems)
                boolean completed = process.waitFor(5, TimeUnit.SECONDS);
                if (completed && process.exitValue() == 0) {
                    String path = new String(process.getInputStream().readAllBytes()).trim();
                    if (!path.isEmpty()) {
                        return Optional.of(path);
                    }
                }
            } catch (IOException | InterruptedException e) {
                // Fall through to Stage 2
            }
        }

        return Optional.empty();
    }

    /**
     * Checks if a command is available in the system PATH.
     *
     * @param command the command name to check
     * @return true if the command exists and is executable, false otherwise
     */
    public static boolean isCommandAvailable(String command) {
        try {
            Process process = new ProcessBuilder("which", command)
                    .redirectErrorStream(true)
                    .start();
            // 5-second timeout (which command completes in <100ms; generous margin for slow systems)
            boolean completed = process.waitFor(5, TimeUnit.SECONDS);
            return completed && process.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }
}
