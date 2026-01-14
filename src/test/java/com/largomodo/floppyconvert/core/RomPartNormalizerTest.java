package com.largomodo.floppyconvert.core;

import com.largomodo.floppyconvert.core.domain.RomPartMetadata;
import com.largomodo.floppyconvert.core.workspace.ConversionWorkspace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RomPartNormalizerTest {

    @TempDir
    Path tempDir;

    @Test
    void testFilesRenamedWithShellSafeNames() throws IOException {
        RomPartNormalizer normalizer = new RomPartNormalizer();
        ConversionWorkspace workspace = new ConversionWorkspace(tempDir, "TestRom", "test");

        Path workDir = workspace.getWorkDir();
        Files.createDirectories(workDir);

        Path unsafePath = workDir.resolve("game[#1].sfc.1");
        Files.writeString(unsafePath, "content");

        List<File> rawParts = List.of(unsafePath.toFile());
        workspace.track(unsafePath);

        List<RomPartMetadata> result = normalizer.normalize(rawParts, workspace);

        assertFalse(Files.exists(unsafePath), "Original unsafe file should not exist");

        Path safePath = workDir.resolve("game__1_.sfc.1");
        assertTrue(Files.exists(safePath), "Renamed safe file should exist");
        assertEquals("content", Files.readString(safePath), "File content should be preserved");

        workspace.close();
    }

    @Test
    void testWorkspaceTrackingUpdated() throws IOException {
        RomPartNormalizer normalizer = new RomPartNormalizer();
        ConversionWorkspace workspace = new ConversionWorkspace(tempDir, "TestRom", "test");

        Path workDir = workspace.getWorkDir();
        Files.createDirectories(workDir);

        Path unsafePath = workDir.resolve("game[test].sfc");
        Files.writeString(unsafePath, "content");

        List<File> rawParts = List.of(unsafePath.toFile());
        workspace.track(unsafePath);

        normalizer.normalize(rawParts, workspace);

        Path safePath = workDir.resolve("game_test_.sfc");
        assertTrue(Files.exists(safePath), "Safe file should exist");

        workspace.close();

        assertFalse(Files.exists(safePath),
                "Safe file should be deleted on close (verifying workspace tracking updated)");
    }

    @Test
    void testMetadataContainsCorrectDosNames() throws IOException {
        RomPartNormalizer normalizer = new RomPartNormalizer();
        ConversionWorkspace workspace = new ConversionWorkspace(tempDir, "TestRom", "test");

        Path workDir = workspace.getWorkDir();
        Files.createDirectories(workDir);

        Path part1 = workDir.resolve("game[1].sfc");
        Path part2 = workDir.resolve("game[2].sfc");
        Files.writeString(part1, "content1");
        Files.writeString(part2, "content2");

        List<File> rawParts = List.of(part1.toFile(), part2.toFile());
        workspace.track(part1);
        workspace.track(part2);

        List<RomPartMetadata> result = normalizer.normalize(rawParts, workspace);

        assertEquals(2, result.size(), "Should have 2 metadata entries");
        assertEquals("GAME1.SFC", result.get(0).dosName(), "First DOS name should be sanitized");
        assertEquals("GAME2.SFC", result.get(1).dosName(), "Second DOS name should be sanitized");

        workspace.close();
    }

    @Test
    void testSpecialCharactersReplaced() throws IOException {
        RomPartNormalizer normalizer = new RomPartNormalizer();
        ConversionWorkspace workspace = new ConversionWorkspace(tempDir, "TestRom", "test");

        Path workDir = workspace.getWorkDir();
        Files.createDirectories(workDir);

        Path unsafePath = workDir.resolve("game[#1].sfc.1");
        Files.writeString(unsafePath, "content");

        List<File> rawParts = List.of(unsafePath.toFile());
        workspace.track(unsafePath);

        List<RomPartMetadata> result = normalizer.normalize(rawParts, workspace);

        Path expectedPath = workDir.resolve("game__1_.sfc.1");
        assertTrue(Files.exists(expectedPath), "File should be renamed to game__1_.sfc.1");
        assertEquals(1, result.size(), "Should have 1 metadata entry");

        workspace.close();
    }

    @Test
    void testMultipleSpecialCharacters() throws IOException {
        RomPartNormalizer normalizer = new RomPartNormalizer();
        ConversionWorkspace workspace = new ConversionWorkspace(tempDir, "TestRom", "test");

        Path workDir = workspace.getWorkDir();
        Files.createDirectories(workDir);

        Path unsafePath = workDir.resolve("Ren & Stimpy$! (USA).sfc");
        Files.writeString(unsafePath, "content");

        List<File> rawParts = List.of(unsafePath.toFile());
        workspace.track(unsafePath);

        List<RomPartMetadata> result = normalizer.normalize(rawParts, workspace);

        Path expectedPath = workDir.resolve("Ren___Stimpy____USA_.sfc");
        assertTrue(Files.exists(expectedPath), "File should be renamed with all special chars replaced");
        assertEquals(1, result.size(), "Should have 1 metadata entry");

        workspace.close();
    }

    @Test
    void testEmptyListReturnsEmptyList() throws IOException {
        RomPartNormalizer normalizer = new RomPartNormalizer();
        ConversionWorkspace workspace = new ConversionWorkspace(tempDir, "TestRom", "test");

        List<File> emptyList = Collections.emptyList();
        List<RomPartMetadata> result = normalizer.normalize(emptyList, workspace);

        assertNotNull(result, "Result should not be null");
        assertTrue(result.isEmpty(), "Result should be empty for empty input");

        workspace.close();
    }

    @Test
    void testMetadataPathsSizeAndDosName() throws IOException {
        RomPartNormalizer normalizer = new RomPartNormalizer();
        ConversionWorkspace workspace = new ConversionWorkspace(tempDir, "TestRom", "test");

        Path workDir = workspace.getWorkDir();
        Files.createDirectories(workDir);

        String content = "test content with some bytes";
        Path part = workDir.resolve("game[test].sfc");
        Files.writeString(part, content);

        List<File> rawParts = List.of(part.toFile());
        workspace.track(part);

        List<RomPartMetadata> result = normalizer.normalize(rawParts, workspace);

        assertEquals(1, result.size(), "Should have 1 metadata entry");

        RomPartMetadata metadata = result.get(0);
        Path expectedPath = workDir.resolve("game_test_.sfc");

        assertEquals(expectedPath, metadata.originalPath(), "Metadata should have sanitized path");
        assertEquals(content.length(), metadata.sizeInBytes(), "Metadata should have correct file size");
        assertEquals("GAMETEST.SFC", metadata.dosName(), "Metadata should have DOS name");

        workspace.close();
    }

    @Test
    void testAllShellSensitiveCharacters() throws IOException {
        RomPartNormalizer normalizer = new RomPartNormalizer();
        ConversionWorkspace workspace = new ConversionWorkspace(tempDir, "TestRom", "test");

        Path workDir = workspace.getWorkDir();
        Files.createDirectories(workDir);

        Path unsafePath = workDir.resolve("test#[]()&$!~',+ file.sfc");
        Files.writeString(unsafePath, "content");

        List<File> rawParts = List.of(unsafePath.toFile());
        workspace.track(unsafePath);

        List<RomPartMetadata> result = normalizer.normalize(rawParts, workspace);

        Path expectedPath = workDir.resolve("test_____________file.sfc");
        assertTrue(Files.exists(expectedPath),
                "All shell-sensitive characters should be replaced with underscores");

        workspace.close();
    }

    @Test
    void testMultipleFilesProcessedCorrectly() throws IOException {
        RomPartNormalizer normalizer = new RomPartNormalizer();
        ConversionWorkspace workspace = new ConversionWorkspace(tempDir, "TestRom", "test");

        Path workDir = workspace.getWorkDir();
        Files.createDirectories(workDir);

        Path part1 = workDir.resolve("game[1].sfc");
        Path part2 = workDir.resolve("game[2].sfc");
        Path part3 = workDir.resolve("game[3].sfc");

        Files.writeString(part1, "content1");
        Files.writeString(part2, "content22");
        Files.writeString(part3, "content333");

        List<File> rawParts = List.of(part1.toFile(), part2.toFile(), part3.toFile());
        workspace.track(part1);
        workspace.track(part2);
        workspace.track(part3);

        List<RomPartMetadata> result = normalizer.normalize(rawParts, workspace);

        assertEquals(3, result.size(), "Should have 3 metadata entries");

        assertEquals(workDir.resolve("game_1_.sfc"), result.get(0).originalPath());
        assertEquals(workDir.resolve("game_2_.sfc"), result.get(1).originalPath());
        assertEquals(workDir.resolve("game_3_.sfc"), result.get(2).originalPath());

        assertEquals(8, result.get(0).sizeInBytes());
        assertEquals(9, result.get(1).sizeInBytes());
        assertEquals(10, result.get(2).sizeInBytes());

        workspace.close();
    }
}
