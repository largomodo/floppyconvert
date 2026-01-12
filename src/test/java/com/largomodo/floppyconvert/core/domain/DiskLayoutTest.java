package com.largomodo.floppyconvert.core.domain;

import com.largomodo.floppyconvert.core.FloppyType;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DiskLayoutTest {

    @Test
    void validLayoutCreated() {
        RomPartMetadata part1 = new RomPartMetadata(Path.of("/test/part1.fig"), 1024, "PART1.FIG");
        RomPartMetadata part2 = new RomPartMetadata(Path.of("/test/part2.fig"), 2048, "PART2.FIG");
        List<RomPartMetadata> parts = List.of(part1, part2);

        DiskLayout layout = new DiskLayout(parts, FloppyType.FLOPPY_720K);

        assertEquals(2, layout.contents().size());
        assertEquals(FloppyType.FLOPPY_720K, layout.floppyType());
        assertTrue(layout.contents().contains(part1));
        assertTrue(layout.contents().contains(part2));
    }

    @Test
    void contentsAreUnmodifiable() {
        RomPartMetadata part = new RomPartMetadata(Path.of("/test/part.fig"), 1024, "PART.FIG");
        List<RomPartMetadata> parts = new ArrayList<>();
        parts.add(part);

        DiskLayout layout = new DiskLayout(parts, FloppyType.FLOPPY_144M);

        // Original list can be modified without affecting the layout
        parts.clear();
        assertEquals(1, layout.contents().size());

        // Layout contents cannot be modified
        assertThrows(UnsupportedOperationException.class, () -> {
            layout.contents().add(new RomPartMetadata(Path.of("/test/other.fig"), 512, "OTHER.FIG"));
        });
    }

    @Test
    void recordProvidesEquals() {
        RomPartMetadata part = new RomPartMetadata(Path.of("/test/part.fig"), 1024, "PART.FIG");
        List<RomPartMetadata> parts1 = List.of(part);
        List<RomPartMetadata> parts2 = List.of(part);

        DiskLayout layout1 = new DiskLayout(parts1, FloppyType.FLOPPY_720K);
        DiskLayout layout2 = new DiskLayout(parts2, FloppyType.FLOPPY_720K);
        DiskLayout layout3 = new DiskLayout(parts1, FloppyType.FLOPPY_144M);

        assertEquals(layout1, layout2);
        assertNotEquals(layout1, layout3);
    }

    @Test
    void recordProvidesHashCode() {
        RomPartMetadata part = new RomPartMetadata(Path.of("/test/part.fig"), 1024, "PART.FIG");
        List<RomPartMetadata> parts1 = List.of(part);
        List<RomPartMetadata> parts2 = List.of(part);

        DiskLayout layout1 = new DiskLayout(parts1, FloppyType.FLOPPY_720K);
        DiskLayout layout2 = new DiskLayout(parts2, FloppyType.FLOPPY_720K);

        assertEquals(layout1.hashCode(), layout2.hashCode());
    }

    @Test
    void recordProvidesToString() {
        RomPartMetadata part = new RomPartMetadata(Path.of("/test/part.fig"), 1024, "PART.FIG");
        DiskLayout layout = new DiskLayout(List.of(part), FloppyType.FLOPPY_720K);
        String str = layout.toString();

        assertTrue(str.contains("DiskLayout"));
        assertTrue(str.contains("FLOPPY_720K"));
    }

    @Test
    void emptyLayoutAllowed() {
        DiskLayout layout = new DiskLayout(List.of(), FloppyType.FLOPPY_160M);
        assertTrue(layout.contents().isEmpty());
        assertEquals(FloppyType.FLOPPY_160M, layout.floppyType());
    }
}
