package com.largomodo.floppyconvert.core.workspace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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

    private static final Logger log = LoggerFactory.getLogger(ConversionWorkspace.class);

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
     * Move artifact from workspace to final output directory with atomic semantics.
     * <p>
     * Combines filesystem move with cleanup prevention (markAsOutput). Atomic move preferred
     * for transactional guarantees (single operation, no intermediate state visible). Fallback
     * to copy+delete preserves move contract when atomic unsupported (cross-filesystem moves,
     * Windows FAT32→NTFS). Copy-only would violate move semantics by leaving source file.
     * <p>
     * Idempotent: repeated calls with same arguments produce same final state (target exists,
     * source removed from cleanup tracking). Overwrite warning logged if target pre-exists.
     *
     * @param sourceArtifact      Path to file in workspace (must be tracked artifact)
     * @param finalDestinationDir Target directory (file placed with same basename)
     * @throws IOException if move/copy operations fail
     */
    public void promoteToFinal(Path sourceArtifact, Path finalDestinationDir) throws IOException {
        Path targetPath = finalDestinationDir.resolve(sourceArtifact.getFileName());

        if (Files.exists(targetPath)) {
            log.warn("Overwriting existing file: {}", targetPath);
        }

        try {
            Files.move(sourceArtifact, targetPath,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            // Atomic move unsupported (cross-filesystem or platform limitation)
            Files.copy(sourceArtifact, targetPath, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.delete(sourceArtifact);
            } catch (IOException deleteEx) {
                // Preserve both move failure and delete failure in exception chain
                IOException compositeEx = new IOException(
                    "Atomic move unsupported and cleanup failed for: " + sourceArtifact, e);
                compositeEx.addSuppressed(deleteEx);
                throw compositeEx;
            }
        }

        markAsOutput(sourceArtifact);
    }

    /**
     * Deletes tracked artifacts in reverse acquisition order.
     * <p>
     * Accumulates cleanup failures and throws CleanupException if any fail.
     * Preserves full cause chains for production diagnostics.
     * <p>
     * If thread is interrupted, logs warning and skips cleanup to avoid blocking.
     *
     * @throws CleanupException if any cleanup operations fail
     */
    @Override
    public void close() throws CleanupException {
        // Check interruption status WITHOUT clearing flag (non-destructive read)
        if (Thread.currentThread().isInterrupted()) {
            log.warn("Thread interrupted, skipping cleanup for workspace: {}", workDir);
            return;
        }

        // Delete in reverse order: files before containing directories
        Collections.reverse(trackedFiles);
        List<IOException> failures = new ArrayList<>();

        for (Path artifact : trackedFiles) {
            try {
                if (Files.isDirectory(artifact)) {
                    deleteDirectoryRecursively(artifact);
                } else {
                    Files.deleteIfExists(artifact);
                }
            } catch (IOException e) {
                // Accumulate failures for diagnostic reporting
                failures.add(e);
                log.warn("Cleanup failed for artifact: {}", artifact, e);
            }
        }

        // Throw exception with all failures as suppressed exceptions
        if (!failures.isEmpty()) {
            throw new CleanupException(
                "Workspace cleanup encountered " + failures.size() + " failure(s) in: " + workDir,
                failures);
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
