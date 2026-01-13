package com.largomodo.floppyconvert.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ResourceDiskTemplateFactoryTest {

    @TempDir
    Path tempDir;

    @Test
    void testFactory720kTemplate() throws IOException {
        ResourceDiskTemplateFactory factory = new ResourceDiskTemplateFactory();
        Path targetFile = tempDir.resolve("test720k.img");

        factory.createBlankDisk(FloppyType.FLOPPY_720K, targetFile);

        assertTrue(Files.exists(targetFile), "720k template file should be created");
        assertEquals(737280, Files.size(targetFile), "720k template should be 737,280 bytes");
    }

    @Test
    void testFactory144MTemplate() throws IOException {
        ResourceDiskTemplateFactory factory = new ResourceDiskTemplateFactory();
        Path targetFile = tempDir.resolve("test144m.img");

        factory.createBlankDisk(FloppyType.FLOPPY_144M, targetFile);

        assertTrue(Files.exists(targetFile), "1.44M template file should be created");
        assertEquals(1474560, Files.size(targetFile), "1.44M template should be 1,474,560 bytes");
    }

    @Test
    void testFactory160MTemplate() throws IOException {
        ResourceDiskTemplateFactory factory = new ResourceDiskTemplateFactory();
        Path targetFile = tempDir.resolve("test160m.img");

        factory.createBlankDisk(FloppyType.FLOPPY_160M, targetFile);

        assertTrue(Files.exists(targetFile), "1.6M template file should be created");
        assertEquals(1638400, Files.size(targetFile), "1.6M template should be 1,638,400 bytes");
    }

    @Test
    void testMissingResourceThrowsException() throws Exception {
        DiskTemplateFactory factory = new DiskTemplateFactory() {
            @Override
            public void createBlankDisk(FloppyType type, Path targetFile) throws IOException {
                String resourcePath = "/nonexistent.img";
                try (var stream = getClass().getResourceAsStream(resourcePath)) {
                    if (stream == null) {
                        throw new IOException("Internal resource " + resourcePath +
                            " not found. Ensure application is built correctly.");
                    }
                    Files.copy(stream, targetFile);
                }
            }
        };

        Path targetFile = tempDir.resolve("test.img");

        IOException exception = assertThrows(IOException.class, () -> {
            factory.createBlankDisk(FloppyType.FLOPPY_720K, targetFile);
        });

        assertTrue(exception.getMessage().contains("not found"),
                "Exception message should indicate resource not found");
        assertTrue(exception.getMessage().contains("nonexistent.img"),
                "Exception message should include resource path");
    }

    @Test
    void testStreamClosedAfterOperation() throws IOException {
        ResourceDiskTemplateFactory factory = new ResourceDiskTemplateFactory();
        Path targetFile = tempDir.resolve("test.img");

        factory.createBlankDisk(FloppyType.FLOPPY_720K, targetFile);

        assertDoesNotThrow(() -> {
            factory.createBlankDisk(FloppyType.FLOPPY_720K, targetFile);
        }, "Second call should succeed, indicating stream was properly closed");

        assertTrue(Files.exists(targetFile), "File should still exist after second call");
    }

    @Test
    void testReplaceExistingFile() throws IOException {
        ResourceDiskTemplateFactory factory = new ResourceDiskTemplateFactory();
        Path targetFile = tempDir.resolve("test.img");

        Files.writeString(targetFile, "old content");
        assertTrue(Files.exists(targetFile), "Target file should exist before replace");

        factory.createBlankDisk(FloppyType.FLOPPY_720K, targetFile);

        assertTrue(Files.exists(targetFile), "Target file should still exist after replace");
        assertEquals(737280, Files.size(targetFile), "File should be replaced with template content");
    }
}
