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

    @Test
    void recordProvidesEquals() {
        RomPartMetadata m1 = new RomPartMetadata(Path.of("/test/rom.fig"), 1024, "ROM.FIG");
        RomPartMetadata m2 = new RomPartMetadata(Path.of("/test/rom.fig"), 1024, "ROM.FIG");
        RomPartMetadata m3 = new RomPartMetadata(Path.of("/test/rom.fig"), 2048, "ROM.FIG");

        assertEquals(m1, m2);
        assertNotEquals(m1, m3);
    }

    @Test
    void recordProvidesHashCode() {
        RomPartMetadata m1 = new RomPartMetadata(Path.of("/test/rom.fig"), 1024, "ROM.FIG");
        RomPartMetadata m2 = new RomPartMetadata(Path.of("/test/rom.fig"), 1024, "ROM.FIG");

        assertEquals(m1.hashCode(), m2.hashCode());
    }

    @Test
    void recordProvidesToString() {
        RomPartMetadata metadata = new RomPartMetadata(Path.of("/test/rom.fig"), 1024, "ROM.FIG");
        String str = metadata.toString();

        assertTrue(str.contains("RomPartMetadata"));
        assertTrue(str.contains("1024"));
        assertTrue(str.contains("ROM.FIG"));
    }
}
