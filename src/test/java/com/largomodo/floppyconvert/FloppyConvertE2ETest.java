package com.largomodo.floppyconvert;

import com.largomodo.floppyconvert.snes.generators.SyntheticRomFactory;
import com.largomodo.floppyconvert.snes.generators.SyntheticRomFactory.DspChipset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * End-to-end conversion through the CLI in single-file mode, checked at the only place that matters to a user:
 * what is left in the output directory, and which files of which size each floppy image holds.
 * <p>
 * Every expectation is the complete output tree, so a leftover part, a surviving workspace directory, a blank
 * image, a wrong disk size or a wrong disk number all fail the comparison. Part bytes are pinned elsewhere
 * (Gd3ParityTest, CrossFormatStabilityTest, FloppyImageContentTest).
 */
class FloppyConvertE2ETest {

    // 4 Mbit of data plus the 512-byte copier header; 8 Mbit GD3 parts carry the header on part A only
    private static final int PART_4M = 524_800;
    private static final int PART_8M_HEADERED = 1_049_088;
    private static final int PART_8M = 1_048_576;
    private static final int DISK_720K = 737_280;
    private static final int DISK_144M = 1_474_560;
    private static final int DISK_160M = 1_638_400;

    @TempDir
    Path tempDir;

    enum Kind {LOROM, HIROM, EXTENDED_HIROM}

    static Stream<Arguments> conversions() {
        return Stream.of(
                // One 4 Mbit part fits the smallest disk
                Arguments.of("swc", Kind.LOROM, 4, List.of(
                        image("game.img", DISK_720K, "GAME    1  ", PART_4M))),
                // Three 4 Mbit parts fill a 1.6M disk (1,574,400 of 1,600,000 usable bytes); the last two take a 1.44M
                Arguments.of("fig", Kind.HIROM, 32, List.of(
                        image("game_1.img", DISK_160M, "GAME    1  ", PART_4M, "GAME    2  ", PART_4M, "GAME    3  ", PART_4M),
                        image("game_2.img", DISK_160M, "GAME    4  ", PART_4M, "GAME    5  ", PART_4M, "GAME    6  ", PART_4M),
                        image("game_3.img", DISK_144M, "GAME    7  ", PART_4M, "GAME    8  ", PART_4M))),
                // 12 Mbit LoROM is mirrored to 16 Mbit
                Arguments.of("ufo", Kind.LOROM, 12, List.of(
                        image("game_1.img", DISK_160M, "GAME    1GM", PART_4M, "GAME    2GM", PART_4M, "GAME    3GM", PART_4M),
                        image("game_2.img", DISK_720K, "GAME    4GM", PART_4M))),
                // UFO HiROM 12 Mbit follows the 4-2-4-2 Mbit table (ucon64 snes.c size_to_partsizes)
                Arguments.of("ufo", Kind.HIROM, 12, List.of(
                        image("game.img", DISK_160M, "GAME    1GM", PART_4M, "GAME    2GM", 262_656,
                                "GAME    3GM", PART_4M, "GAME    4GM", 262_656))),
                // A Game Doctor does not read 720K disks, so even one small part goes on a 1.44M
                Arguments.of("gd3", Kind.LOROM, 4, List.of(
                        image("game.img", DISK_144M, "SF4GAM  078", PART_4M))),
                // GD3 HiROM up to 16 Mbit is cut in two halves
                Arguments.of("gd3", Kind.HIROM, 8, List.of(
                        image("game.img", DISK_144M, "SF8GAMXA078", PART_4M, "SF8GAMXB078", 524_288))),
                Arguments.of("gd3", Kind.HIROM, 16, List.of(
                        image("game_1.img", DISK_144M, "SF16GAMA078", PART_8M_HEADERED),
                        image("game_2.img", DISK_144M, "SF16GAMB078", PART_8M))),
                // 20 Mbit HiROM is padded to 24 Mbit and named for it
                Arguments.of("gd3", Kind.HIROM, 20, List.of(
                        image("game_1.img", DISK_144M, "SF24GAMA078", PART_8M_HEADERED),
                        image("game_2.img", DISK_144M, "SF24GAMB078", PART_8M),
                        image("game_3.img", DISK_144M, "SF24GAMC078", PART_8M))),
                Arguments.of("gd3", Kind.EXTENDED_HIROM, 48, List.of(
                        image("game_1.img", DISK_144M, "SF48GAMA078", PART_8M_HEADERED),
                        image("game_2.img", DISK_144M, "SF48GAMB078", PART_8M),
                        image("game_3.img", DISK_144M, "SF48GAMC078", PART_8M),
                        image("game_4.img", DISK_144M, "SF48GAMD078", PART_8M),
                        image("game_5.img", DISK_144M, "SF48GAME078", PART_8M),
                        image("game_6.img", DISK_144M, "SF48GAMF078", PART_8M)))
        );
    }

    @ParameterizedTest(name = "{0} {1} {2} Mbit")
    @MethodSource("conversions")
    void conversionLeavesExactlyTheExpectedImages(String format, Kind kind, int sizeMbit, List<String> expected)
            throws IOException {
        Path rom = Files.move(generate(kind, sizeMbit), tempDir.resolve("game.sfc"));
        Path out = tempDir.resolve("out");

        assertEquals(0, run(rom.toString(), "--output-dir", out.toString(), "--format", format));

        assertEquals(expected, describe(out));
    }

    @Test
    // The image keeps a sanitized form of the whole file name; the 8.3 name on the disk keeps only letters and digits.
    void specialCharactersAreSanitizedInImageAndDosNames() throws IOException {
        Path rom = Files.move(generate(Kind.LOROM, 4),
                tempDir.resolve("Ren & Stimpy Show, The - Buckeroo$! (USA).sfc"));
        Path out = tempDir.resolve("out");

        assertEquals(0, run(rom.toString(), "--output-dir", out.toString(), "--format", "fig"));

        assertEquals(List.of(image("Ren___Stimpy_Show__The_-_Buckeroo____USA_.img", DISK_720K, "RENSTIMP1  ", PART_4M)),
                describe(out));
    }

    @Test
    // A dot, dash or space in the file name must not move the X a small HiROM needs at index 6 of the name on the disk.
    void gd3NamesOnTheDiskKeepTheXOfASmallHiRomAtIndexSix() throws IOException {
        Path rom = Files.move(generate(Kind.HIROM, 8), tempDir.resolve("a.b - c.sfc"));
        Path out = tempDir.resolve("out");

        assertEquals(0, run(rom.toString(), "--output-dir", out.toString(), "--format", "gd3"));

        assertEquals(List.of(image("a.b_-_c.img", DISK_144M, "SF8ABCXA078", PART_4M, "SF8ABCXB078", 524_288)),
                describe(out));
    }

    private Path generate(Kind kind, int sizeMbit) throws IOException {
        Path dir = Files.createDirectories(tempDir.resolve("gen"));
        return switch (kind) {
            case LOROM -> SyntheticRomFactory.generateLoRom(sizeMbit, 0, DspChipset.ABSENT, dir);
            case HIROM -> SyntheticRomFactory.generateHiRom(sizeMbit, 0, DspChipset.ABSENT, dir);
            case EXTENDED_HIROM -> SyntheticRomFactory.generateExHiRom(sizeMbit, 0, DspChipset.ABSENT, "SYNEX", dir);
        };
    }

    private static int run(String... args) {
        return new CommandLine(new FloppyConvert()).setCaseInsensitiveEnumValuesAllowed(true).execute(args);
    }

    // "name:imageBytes[DIRNAME=fileBytes,...]"; nameAndSize alternates the 11-character directory field and the size
    private static String image(String name, int imageBytes, Object... nameAndSize) {
        List<String> files = new ArrayList<>();
        for (int i = 0; i < nameAndSize.length; i += 2) {
            files.add(nameAndSize[i] + "=" + nameAndSize[i + 1]);
        }
        return name + ":" + imageBytes + "[" + String.join(",", files) + "]";
    }

    // Everything under the output directory, sorted by relative path; anything that is not an image shows up
    // by its bare path and so breaks the comparison.
    private static List<String> describe(Path out) throws IOException {
        List<Path> entries;
        try (Stream<Path> walk = Files.walk(out)) {
            entries = walk.filter(p -> !p.equals(out)).sorted().toList();
        }
        List<String> described = new ArrayList<>();
        for (Path entry : entries) {
            String relative = out.relativize(entry).toString();
            if (!Files.isRegularFile(entry) || !relative.endsWith(".img")) {
                described.add(relative);
                continue;
            }
            Map<String, byte[]> files = new Fat12TestReader(entry).files();
            described.add(relative + ":" + Files.size(entry) + "[" + files.entrySet().stream()
                    .map(file -> file.getKey() + "=" + file.getValue().length)
                    .collect(Collectors.joining(",")) + "]");
        }
        return described;
    }
}
