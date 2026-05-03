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

}
