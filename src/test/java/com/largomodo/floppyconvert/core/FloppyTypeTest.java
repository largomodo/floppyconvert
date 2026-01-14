package com.largomodo.floppyconvert.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

class FloppyTypeTest {

    @Test
    void testBestFitBoundaries() throws IOException {
        // Edge case: empty payload uses smallest template
        assertEquals(FloppyType.FLOPPY_720K, FloppyType.bestFit(0));

        // Small ROMs use 720K
        assertEquals(FloppyType.FLOPPY_720K, FloppyType.bestFit(500_000));
        assertEquals(FloppyType.FLOPPY_720K, FloppyType.bestFit(725_000));

        // Medium ROMs use 1.44M
        assertEquals(FloppyType.FLOPPY_144M, FloppyType.bestFit(726_000));
        assertEquals(FloppyType.FLOPPY_144M, FloppyType.bestFit(1_000_000));
        assertEquals(FloppyType.FLOPPY_144M, FloppyType.bestFit(1_450_000));

        // Large ROMs use 1.6M
        assertEquals(FloppyType.FLOPPY_160M, FloppyType.bestFit(1_460_000));
        assertEquals(FloppyType.FLOPPY_160M, FloppyType.bestFit(1_600_000));
    }

    @Test
    void testNegativeInputThrows() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> FloppyType.bestFit(-1));
        assertTrue(ex.getMessage().contains("negative"));
    }

    @Test
    void testOverflowThrows() {
        IOException ex = assertThrows(IOException.class,
                () -> FloppyType.bestFit(1_700_000));
        assertTrue(ex.getMessage().contains("1700000"));
        assertTrue(ex.getMessage().contains("1.6MB"));
    }

    @Test
    void testResourcesExist() {
        for (FloppyType type : FloppyType.values()) {
            try (InputStream stream = getClass().getResourceAsStream(type.getResourcePath())) {
                assertNotNull(stream, "Missing resource: " + type.getResourcePath());
            } catch (IOException e) {
                fail("Failed to load resource: " + type.getResourcePath());
            }
        }
    }
}
