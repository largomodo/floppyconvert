package com.largomodo.floppyconvert.core;

import com.largomodo.floppyconvert.format.CopierFormat;
import org.junit.jupiter.api.Test;

import java.io.IOException;

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
    void testBestFitWithMinimumClampsUp() throws IOException {
        // size fits 720K, but minimum is 1.44M -> returns 1.44M
        assertEquals(FloppyType.FLOPPY_144M,
                FloppyType.bestFit(500_000, FloppyType.FLOPPY_144M));
    }

    @Test
    void testBestFitWithMinimumNoClampNeeded() throws IOException {
        // size fits 1.44M, minimum is 720K -> returns 1.44M (no clamp needed)
        assertEquals(FloppyType.FLOPPY_144M,
                FloppyType.bestFit(1_000_000, FloppyType.FLOPPY_720K));
    }

    @Test
    void testBestFitWithMinimumSameType() throws IOException {
        // size fits 720K, minimum is 720K -> returns 720K
        assertEquals(FloppyType.FLOPPY_720K,
                FloppyType.bestFit(500_000, FloppyType.FLOPPY_720K));
    }

    @Test
    void testBestFitWithMinimum144mPayloadExceeds144m() throws IOException {
        // payload > 1.44M with FLOPPY_144M minimum -> returns FLOPPY_160M (minimum does not cap upward)
        assertEquals(FloppyType.FLOPPY_160M,
                FloppyType.bestFit(1_460_000, FloppyType.FLOPPY_144M));
    }

    @Test
    void testMinimumForFormatGd3Returns144M() {
        assertEquals(FloppyType.FLOPPY_144M, FloppyType.minimumForFormat(CopierFormat.GD3));
    }

    @Test
    void testMinimumForFormatOthersReturn720K() {
        assertEquals(FloppyType.FLOPPY_720K, FloppyType.minimumForFormat(CopierFormat.FIG));
        assertEquals(FloppyType.FLOPPY_720K, FloppyType.minimumForFormat(CopierFormat.SWC));
        assertEquals(FloppyType.FLOPPY_720K, FloppyType.minimumForFormat(CopierFormat.UFO));
    }

    @Test
    void testGetSectorsPerCluster() {
        assertEquals((byte) 2, FloppyType.FLOPPY_720K.getSectorsPerCluster());
        assertEquals((byte) 1, FloppyType.FLOPPY_144M.getSectorsPerCluster());
        assertEquals((byte) 1, FloppyType.FLOPPY_160M.getSectorsPerCluster());
    }

    @Test
    void testEnumOrdinalOrderMatchesSizeOrder() {
        // bestFit() overload uses ordinal comparison for floor clamping.
        // Declaration order must equal size order (720K < 1.44M < 1.6M) or the clamp silently breaks.
        FloppyType[] values = FloppyType.values();
        for (int i = 1; i < values.length; i++) {
            assertTrue(values[i].getUsableBytes() > values[i - 1].getUsableBytes(),
                    "FloppyType ordinal " + i + " must have larger capacity than ordinal " + (i - 1));
        }
    }
}
