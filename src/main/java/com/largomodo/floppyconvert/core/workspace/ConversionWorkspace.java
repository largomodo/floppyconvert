package com.largomodo.floppyconvert.core.workspace;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manages temporary artifacts during ROM conversion with automatic cleanup.
 * <p>
 * Centralizes the artifact cleanup pattern (replaces 15-line finally blocks).
 * AutoCloseable enables try-with-resources for automatic cleanup of intermediate files
 * while preserving final outputs.
 * <p>
 * Best-effort deletion: Windows file locking may prevent immediate deletion
 * (e.g., ucon64.exe holding handle). IOException logged but not propagated
 * since cleanup is non-critical. Subsequent runs will overwrite/clean stale artifacts.
 * <p>
 * Thread interruption: If thread is interrupted before cleanup, logs warning and skips
 * deletion to avoid blocking on I/O operations.
 */
public class ConversionWorkspace implements AutoCloseable {

    private final Path workDir;
    private final List<Path> trackedFiles = new ArrayList<>();

    /**
     * Creates a conversion workspace for a single ROM.
     *
     * @param outputBaseDir Base output directory
     * @param romBaseName   ROM base name (sanitized, without extension)
     * @param uniqueSuffix  Unique suffix to prevent workspace collisions (e.g., UUID)
     */
    public ConversionWorkspace(Path outputBaseDir, String romBaseName, String uniqueSuffix) {
        this.workDir = outputBaseDir.resolve(romBaseName + "." + uniqueSuffix);
    }

    /**
     * Returns the working directory for this ROM conversion.
     * Path is outputBaseDir/romBaseName.
     *
     * @return Working directory path
     */
    public Path getWorkDir() {
        return workDir;
    }

    /**
     * Track an artifact for cleanup on close().
     * Files tracked before containing directories are deleted in reverse order.
     *
     * @param artifact Path to track (file or directory)
     */
    public void track(Path artifact) {
        trackedFiles.add(artifact);
    }

    /**
     * Mark a file as final output, preventing deletion on close().
     * Removes the path from tracked artifacts.
     *
     * @param finalFile Path to preserve
     */
    public void markAsOutput(Path finalFile) {
        trackedFiles.remove(finalFile);
    }

    /**
     * Deletes tracked artifacts in reverse acquisition order.
     * <p>
     * Idempotent: safe to call multiple times.
     * Never throws exceptions: swallows and logs IOException per file.
     * <p>
     * If thread is interrupted, logs warning and skips cleanup to avoid blocking.
     */
    @Override
    public void close() {
        // Check interruption status WITHOUT clearing flag (non-destructive read)
        if (Thread.currentThread().isInterrupted()) {
            System.err.println("Warning: Thread interrupted, skipping cleanup for workspace: " + workDir);
            return;
        }

        // Delete in reverse order: files before containing directories
        Collections.reverse(trackedFiles);
        for (Path artifact : trackedFiles) {
            try {
                if (Files.isDirectory(artifact)) {
                    // Delete directory and all its contents recursively
                    deleteDirectoryRecursively(artifact);
                } else {
                    Files.deleteIfExists(artifact);
                }
            } catch (IOException e) {
                // Best-effort cleanup: log but don't propagate
                // Windows file locking may prevent immediate deletion
                System.err.println("Warning: Could not delete artifact " + artifact +
                        ": " + e.getMessage());
            }
        }
    }

    /**
     * Recursively deletes a directory and all its contents.
     */
    private void deleteDirectoryRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }

        try (var stream = Files.walk(dir)) {
            stream.sorted(java.util.Comparator.reverseOrder())
                  .forEach(path -> {
                      try {
                          Files.deleteIfExists(path);
                      } catch (IOException e) {
                          throw new java.io.UncheckedIOException(e);
                      }
                  });
        } catch (java.io.UncheckedIOException e) {
            throw e.getCause();
        }
    }
}
