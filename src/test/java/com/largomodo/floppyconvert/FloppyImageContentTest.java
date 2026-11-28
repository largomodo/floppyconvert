package com.largomodo.floppyconvert;

import com.largomodo.floppyconvert.format.CopierFormat;
import com.largomodo.floppyconvert.service.NativeConversionServiceFactory;
import com.largomodo.floppyconvert.snes.generators.SyntheticRomFactory;
import com.largomodo.floppyconvert.snes.generators.SyntheticRomFactory.DspChipset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Reads the produced floppy images back with an independent FAT12 reader: the other end-to-end tests stop at
 * "an .img exists", which a blank or corrupted disk satisfies too.
 */
class FloppyImageContentTest {

    private static final int MBIT = 0x20000;
    private static final int COPIER_HEADER = 512;

    @TempDir
    Path tempDir;

    @Test
    // 4 Mbit + header fits a 720K disk, the one geometry with 2 sectors per cluster.
    void swcImageOn720kDiskHoldsTheRomBehindItsHeader() throws IOException {
        Path rom = romNamed("lo4.sfc", SyntheticRomFactory.generateLoRom(4, 0, DspChipset.ABSENT, Files.createDirectories(tempDir.resolve("gen"))));
        Path out = tempDir.resolve("out");

        convert(rom, out, "swc");

        Map<String, Path> images = imagesIn(out);
        assertEquals(List.of("lo4.img"), List.copyOf(images.keySet()), "Output must hold the image and nothing else");
        assertEquals(737_280, Files.size(images.get("lo4.img")));

        Map<String, byte[]> files = new Fat12TestReader(images.get("lo4.img")).files();
        assertEquals(List.of("LO4     1  "), List.copyOf(files.keySet()));
        byte[] part = files.get("LO4     1  ");
        assertEquals(4 * MBIT + COPIER_HEADER, part.length);
        assertArrayEquals(Files.readAllBytes(rom), Arrays.copyOfRange(part, COPIER_HEADER, part.length),
                "The part body must be the ROM, byte for byte");
    }

    @Test
    // 12 Mbit is mirrored to 16 Mbit: four parts, three on a 1.6M disk and the last on a second disk.
    void figImagesHoldEverySplitPartByteForByte() throws IOException {
        Path rom = romNamed("lo12.sfc", SyntheticRomFactory.generateLoRom(12, 0, DspChipset.ABSENT, Files.createDirectories(tempDir.resolve("gen"))));
        Path out = tempDir.resolve("out");

        convert(rom, out, "fig");

        Map<String, Path> images = imagesIn(out);
        assertEquals(List.of("lo12_1.img", "lo12_2.img"), List.copyOf(images.keySet()),
                "Output must hold the two images and nothing else");
        assertEquals(1_638_400, Files.size(images.get("lo12_1.img")));
        assertEquals(737_280, Files.size(images.get("lo12_2.img")));

        Map<String, byte[]> onDisk = new LinkedHashMap<>(new Fat12TestReader(images.get("lo12_1.img")).files());
        assertEquals(List.of("LO12    1  ", "LO12    2  ", "LO12    3  "), List.copyOf(onDisk.keySet()));
        Map<String, byte[]> second = new Fat12TestReader(images.get("lo12_2.img")).files();
        assertEquals(List.of("LO12    4  "), List.copyOf(second.keySet()));
        onDisk.putAll(second);

        // The splitter's own output is the reference for the bytes; its content is pinned by CrossFormatStabilityTest
        Path partsDir = Files.createDirectories(tempDir.resolve("parts"));
        List<File> parts = new NativeConversionServiceFactory().createRomSplitter()
                .split(rom.toFile(), partsDir, CopierFormat.FIG);
        assertEquals(4, parts.size());
        for (File part : parts) {
            String extension = part.getName().substring(part.getName().lastIndexOf('.') + 1);
            assertArrayEquals(Files.readAllBytes(part.toPath()), onDisk.get("LO12    " + extension + "  "),
                    "Content of " + part.getName() + " on the image");
        }
    }

    // A 720K disk has 1440 sectors: 1 boot, 2 x 3 FAT and 7 root directory sectors (112 entries of 32 bytes)
    // leave 1426 data sectors, which is 713 clusters of 1024 bytes.
    private static final int CAPACITY_720K = 713 * 1024;

    @Test
    void fileOfExactlyTheDiskCapacityIsStoredComplete() throws IOException {
        byte[] content = new byte[CAPACITY_720K];
        for (int i = 0; i < content.length; i++) {
            content[i] = (byte) (i * 31 + (i >> 10));
        }
        Path image = blank720k();
        File source = Files.write(tempDir.resolve("full.1"), content).toFile();

        new NativeConversionServiceFactory().createFloppyImageWriter().write(image.toFile(), List.of(source), Map.of(source, "FULL.1"));

        Map<String, byte[]> files = new Fat12TestReader(image).files();
        assertEquals(List.of("FULL    1  "), List.copyOf(files.keySet()));
        assertArrayEquals(content, files.get("FULL    1  "));
    }

    @Test
    void fileOneByteOverTheDiskCapacityIsRefused() throws IOException {
        Path image = blank720k();
        File source = Files.write(tempDir.resolve("over.1"), new byte[CAPACITY_720K + 1]).toFile();

        IOException e = org.junit.jupiter.api.Assertions.assertThrows(IOException.class, () ->
                new NativeConversionServiceFactory().createFloppyImageWriter()
                        .write(image.toFile(), List.of(source), Map.of(source, "OVER.1")));

        assertEquals("Disk full: not enough free clusters", e.getMessage());
    }

    private Path blank720k() throws IOException {
        Path image = tempDir.resolve("blank.img");
        new NativeConversionServiceFactory().createDiskTemplateFactory()
                .createBlankDisk(com.largomodo.floppyconvert.core.FloppyType.FLOPPY_720K, image);
        return image;
    }

    private Path romNamed(String name, Path generated) throws IOException {
        return Files.move(generated, tempDir.resolve(name));
    }

    private static void convert(Path rom, Path out, String format) {
        int exitCode = new CommandLine(new FloppyConvert())
                .setCaseInsensitiveEnumValuesAllowed(true)
                .execute(rom.toString(), "--output-dir", out.toString(), "--format", format);
        assertEquals(0, exitCode);
    }

    // Everything left under the output directory, by file name; asserting on the whole set also proves
    // that no part file and no workspace directory survived the conversion.
    private static Map<String, Path> imagesIn(Path out) throws IOException {
        Map<String, Path> entries = new TreeMap<>();
        try (Stream<Path> walk = Files.walk(out)) {
            walk.filter(p -> !p.equals(out)).forEach(p -> entries.put(p.getFileName().toString(), p));
        }
        return entries;
    }
}
