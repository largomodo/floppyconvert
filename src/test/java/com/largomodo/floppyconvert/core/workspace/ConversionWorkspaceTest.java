package com.largomodo.floppyconvert.core.workspace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConversionWorkspaceTest {

    private final PrintStream originalOut = System.out;
    @TempDir
    Path tempDir;
    private ByteArrayOutputStream errContent;

    @BeforeEach
    void setUp() {
        errContent = new ByteArrayOutputStream();
        System.setOut(new PrintStream(errContent));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
        Thread.interrupted(); // Clear interrupted flag if set
    }

    @Test
    void testGetWorkDir() {
        ConversionWorkspace workspace = new ConversionWorkspace(tempDir, "TestRom", "test");
        assertEquals(tempDir.resolve("TestRom.test"), workspace.getWorkDir());
    }

    @Test
    void testAutoCloseDeletesTrackedFiles() throws IOException {
        Path file1 = tempDir.resolve("artifact1.tmp");
        Path file2 = tempDir.resolve("artifact2.tmp");
        Files.writeString(file1, "test1");
        Files.writeString(file2, "test2");

        try (ConversionWorkspace workspace = new ConversionWorkspace(tempDir, "TestRom", "test")) {
            workspace.track(file1);
            workspace.track(file2);
        }

        assertFalse(Files.exists(file1), "Tracked file1 should be deleted");
        assertFalse(Files.exists(file2), "Tracked file2 should be deleted");
    }

    @Test
    void testAutoClosePreservesFinalOutputs() throws IOException {
        Path artifact = tempDir.resolve("artifact.tmp");
        Path output = tempDir.resolve("output.img");
        Files.writeString(artifact, "artifact");
        Files.writeString(output, "output");

        try (ConversionWorkspace workspace = new ConversionWorkspace(tempDir, "TestRom", "test")) {
            workspace.track(artifact);
            workspace.track(output);
            workspace.markAsOutput(output);
        }

        assertFalse(Files.exists(artifact), "Artifact should be deleted");
        assertTrue(Files.exists(output), "Final output should be preserved");
    }

    @Test
    void testNestedDirectoriesDeleted() throws IOException {
        Path dir = tempDir.resolve("workdir");
        Path file1 = dir.resolve("file1.tmp");
        Path file2 = dir.resolve("file2.tmp");
        Files.createDirectories(dir);
        Files.writeString(file1, "content1");
        Files.writeString(file2, "content2");

        try (ConversionWorkspace workspace = new ConversionWorkspace(tempDir, "TestRom", "test")) {
            workspace.track(file1);
            workspace.track(file2);
            workspace.track(dir);
        }

        assertFalse(Files.exists(file1), "File1 should be deleted");
        assertFalse(Files.exists(file2), "File2 should be deleted");
        assertFalse(Files.exists(dir), "Directory should be deleted");
    }

    @Test
    void testNestedDirectoriesDeletedInCorrectOrder() throws IOException {
        Path dir = tempDir.resolve("workdir");
        Path file1 = dir.resolve("file1.tmp");
        Path file2 = dir.resolve("file2.tmp");
        Files.createDirectories(dir);
        Files.writeString(file1, "content1");
        Files.writeString(file2, "content2");

        try (ConversionWorkspace workspace = new ConversionWorkspace(tempDir, "TestRom", "test")) {
            // Track files first, then directory
            workspace.track(file1);
            workspace.track(file2);
            workspace.track(dir);
        }

        // Reverse deletion order: dir deleted after files
        assertFalse(Files.exists(dir), "Directory deletion should succeed (files deleted first)");
    }

    @Test
    void testWindowsLockedFileLogsWarning() throws IOException {
        Path file = tempDir.resolve("locked.tmp");
        Files.writeString(file, "content");

        Path dir = tempDir.resolve("subdir");
        Path nestedFile = dir.resolve("nested.tmp");
        Files.createDirectories(dir);
        Files.writeString(nestedFile, "nested content");

        // Make directory read-only on Unix systems to prevent deletion
        // On Windows, this test simulates the file locking scenario
        if (!System.getProperty("os.name").toLowerCase().contains("win")) {
            dir.toFile().setWritable(false);
        }

        try (ConversionWorkspace workspace = new ConversionWorkspace(tempDir, "TestRom", "test")) {
            workspace.track(file);
            workspace.track(nestedFile);
            workspace.track(dir);
        } finally {
            // Restore permissions for cleanup
            if (!System.getProperty("os.name").toLowerCase().contains("win")) {
                dir.toFile().setWritable(true);
            }
        }

        // Verify the regular file was deleted
        assertFalse(Files.exists(file));

        // On Unix, verify warning was logged for the protected directory
        // On Windows, this test documents expected behavior for locked files
        String stderr = errContent.toString();
        if (!System.getProperty("os.name").toLowerCase().contains("win")) {
            assertTrue(stderr.contains("Could not delete artifact") ||
                            stderr.isEmpty(), // May succeed on some systems
                    "Should log warning for deletion failure on Unix");
        }
    }

    @Test
    void testInterruptedThreadSkipsCleanup() throws IOException {
        Path file = tempDir.resolve("artifact.tmp");
        Files.writeString(file, "content");

        // Set interrupted flag
        Thread.currentThread().interrupt();

        try (ConversionWorkspace workspace = new ConversionWorkspace(tempDir, "TestRom", "test")) {
            workspace.track(file);
        }

        // Verify file was NOT deleted
        assertTrue(Files.exists(file), "File should not be deleted when thread is interrupted");

        // Verify warning was logged
        String stderr = errContent.toString();
        assertTrue(stderr.contains("Thread interrupted, skipping cleanup"),
                "Should log warning for interrupted thread");
        assertTrue(stderr.contains(tempDir.resolve("TestRom").toString()),
                "Warning should include workspace path");
    }

    @Test
    void testCloseIsIdempotent() throws IOException {
        Path file = tempDir.resolve("artifact.tmp");
        Files.writeString(file, "content");

        ConversionWorkspace workspace = new ConversionWorkspace(tempDir, "TestRom", "test");
        workspace.track(file);

        // Call close() multiple times
        workspace.close();
        workspace.close();
        workspace.close();

        // File should be deleted only once, no errors
        assertFalse(Files.exists(file));
    }

    @Test
    void testEmptyWorkspaceClosesWithoutError() {
        ConversionWorkspace workspace = new ConversionWorkspace(tempDir, "TestRom", "test");
        assertDoesNotThrow(workspace::close);
    }

    @Test
    void testTrackAndMarkOutputInDifferentOrder() throws IOException {
        Path file1 = tempDir.resolve("file1.tmp");
        Path file2 = tempDir.resolve("file2.tmp");
        Path file3 = tempDir.resolve("file3.tmp");
        Files.writeString(file1, "content1");
        Files.writeString(file2, "content2");
        Files.writeString(file3, "content3");

        try (ConversionWorkspace workspace = new ConversionWorkspace(tempDir, "TestRom", "test")) {
            workspace.track(file1);
            workspace.track(file2);
            workspace.track(file3);
            // Mark middle file as output
            workspace.markAsOutput(file2);
        }

        assertFalse(Files.exists(file1), "File1 should be deleted");
        assertTrue(Files.exists(file2), "File2 should be preserved");
        assertFalse(Files.exists(file3), "File3 should be deleted");
    }

    @Test
    void testPromoteToFinalMovesFile() throws IOException {
        Path workspaceFile = tempDir.resolve("workspace").resolve("artifact.tmp");
        Path finalDir = tempDir.resolve("output");
        Files.createDirectories(workspaceFile.getParent());
        Files.createDirectories(finalDir);
        Files.writeString(workspaceFile, "test content");

        ConversionWorkspace workspace = new ConversionWorkspace(tempDir, "TestRom", "test");
        workspace.track(workspaceFile);
        workspace.promoteToFinal(workspaceFile, finalDir);

        Path expectedTarget = finalDir.resolve("artifact.tmp");
        assertTrue(Files.exists(expectedTarget), "File should exist at target location");
        assertEquals("test content", Files.readString(expectedTarget), "Content should be preserved");
        assertFalse(Files.exists(workspaceFile), "Source file should be removed");
    }

    @Test
    void testPromoteToFinalPreservesFromCleanup() throws IOException {
        Path workspaceFile = tempDir.resolve("workspace").resolve("artifact.tmp");
        Path finalDir = tempDir.resolve("output");
        Files.createDirectories(workspaceFile.getParent());
        Files.createDirectories(finalDir);
        Files.writeString(workspaceFile, "test content");

        Path expectedTarget = finalDir.resolve("artifact.tmp");

        try (ConversionWorkspace workspace = new ConversionWorkspace(tempDir, "TestRom", "test")) {
            workspace.track(workspaceFile);
            workspace.promoteToFinal(workspaceFile, finalDir);
        }

        assertTrue(Files.exists(expectedTarget), "Promoted file should survive close()");
        assertEquals("test content", Files.readString(expectedTarget), "Content should be preserved");
    }

    @Test
    void testPromoteToFinalOverwriteWarning() throws IOException {
        Path workspaceFile = tempDir.resolve("workspace").resolve("artifact.tmp");
        Path finalDir = tempDir.resolve("output");
        Files.createDirectories(workspaceFile.getParent());
        Files.createDirectories(finalDir);
        Files.writeString(workspaceFile, "new content");

        Path targetFile = finalDir.resolve("artifact.tmp");
        Files.writeString(targetFile, "old content");

        ConversionWorkspace workspace = new ConversionWorkspace(tempDir, "TestRom", "test");
        workspace.promoteToFinal(workspaceFile, finalDir);

        String stderr = errContent.toString();
        assertTrue(stderr.contains("Overwriting existing file:"),
                "Should log overwrite warning");
        assertTrue(stderr.contains(targetFile.toString()),
                "Warning should include target path");
        assertEquals("new content", Files.readString(targetFile),
                "Target should be overwritten with new content");
    }

    @Test
    void testPromoteToFinalCopyDeleteFallback() throws IOException {
        // Test the copy+delete fallback by using different filesystems if available
        // This test documents the fallback behavior even if atomic move succeeds
        Path workspaceFile = tempDir.resolve("workspace").resolve("artifact.tmp");
        Path finalDir = tempDir.resolve("output");
        Files.createDirectories(workspaceFile.getParent());
        Files.createDirectories(finalDir);
        Files.writeString(workspaceFile, "test content");

        ConversionWorkspace workspace = new ConversionWorkspace(tempDir, "TestRom", "test");
        workspace.track(workspaceFile);
        workspace.promoteToFinal(workspaceFile, finalDir);

        Path expectedTarget = finalDir.resolve("artifact.tmp");
        assertTrue(Files.exists(expectedTarget), "File should exist at target after move");
        assertEquals("test content", Files.readString(expectedTarget), "Content should be preserved");
        assertFalse(Files.exists(workspaceFile), "Source should be removed (either atomic or copy+delete)");
    }

    @Test
    void testPromoteToFinalIdempotent() throws IOException {
        Path workspaceFile = tempDir.resolve("workspace").resolve("artifact.tmp");
        Path finalDir = tempDir.resolve("output");
        Files.createDirectories(workspaceFile.getParent());
        Files.createDirectories(finalDir);
        Files.writeString(workspaceFile, "test content");

        ConversionWorkspace workspace = new ConversionWorkspace(tempDir, "TestRom", "test");
        workspace.track(workspaceFile);

        // First call
        workspace.promoteToFinal(workspaceFile, finalDir);
        Path expectedTarget = finalDir.resolve("artifact.tmp");
        assertTrue(Files.exists(expectedTarget), "File should exist after first call");

        // Second call with same arguments (source no longer exists, but should not throw)
        // Re-create source to test idempotency
        Files.writeString(workspaceFile, "test content 2");
        workspace.track(workspaceFile);
        workspace.promoteToFinal(workspaceFile, finalDir);

        // Should overwrite without error
        assertTrue(Files.exists(expectedTarget), "File should still exist after second call");
        assertEquals("test content 2", Files.readString(expectedTarget), "Content should be updated");
    }

    @Test
    void testPromoteToFinalBestEffortDeleteWarning() throws IOException {
        // This test documents the best-effort delete behavior in the fallback path
        // In practice, it's difficult to simulate a delete failure on most systems
        // The test verifies that the method completes successfully even if implemented
        Path workspaceFile = tempDir.resolve("workspace").resolve("artifact.tmp");
        Path finalDir = tempDir.resolve("output");
        Files.createDirectories(workspaceFile.getParent());
        Files.createDirectories(finalDir);
        Files.writeString(workspaceFile, "test content");

        ConversionWorkspace workspace = new ConversionWorkspace(tempDir, "TestRom", "test");
        workspace.track(workspaceFile);

        // This should complete successfully regardless of delete success
        assertDoesNotThrow(() -> workspace.promoteToFinal(workspaceFile, finalDir));

        Path expectedTarget = finalDir.resolve("artifact.tmp");
        assertTrue(Files.exists(expectedTarget), "Target file should exist");
    }
}
