package com.largomodo.floppyconvert.core.domain;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class RomPartMetadataTest {

    @Test
    void validMetadataCreated() {
        Path path = Path.of("/test/rom.fig");
        RomPartMetadata metadata = new RomPartMetadata(path, 1024, "ROM.FIG");

        assertEquals(path, metadata.originalPath());
        assertEquals(1024, metadata.sizeInBytes());
        assertEquals("ROM.FIG", metadata.dosName());
    }

    @Test
    void negativeSizeThrowsException() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new RomPartMetadata(Path.of("/test/rom.fig"), -1, "ROM.FIG")
        );
        assertTrue(ex.getMessage().contains("must be positive"));
        assertTrue(ex.getMessage().contains("-1"));
    }

    @Test
    void zeroSizeThrowsException() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new RomPartMetadata(Path.of("/test/rom.fig"), 0, "ROM.FIG")
        );
        assertTrue(ex.getMessage().contains("must be positive"));
        assertTrue(ex.getMessage().contains("0"));
    }

}
