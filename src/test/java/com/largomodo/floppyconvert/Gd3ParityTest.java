package com.largomodo.floppyconvert;

import com.largomodo.floppyconvert.Gd3ParityExpectations.Gap;
import com.largomodo.floppyconvert.Gd3ParityExpectations.LiveOracle;
import com.largomodo.floppyconvert.Gd3ParityExpectations.Row;
import com.largomodo.floppyconvert.Ucon64Oracle.Converted;
import com.largomodo.floppyconvert.Ucon64Oracle.PartDigest;
import com.largomodo.floppyconvert.Ucon64Oracle.Rejected;
import com.largomodo.floppyconvert.Ucon64Oracle.Result;
import com.largomodo.floppyconvert.format.CopierFormat;
import com.largomodo.floppyconvert.service.NativeConversionServiceFactory;
import com.largomodo.floppyconvert.service.RomSplitter;
import com.largomodo.floppyconvert.snes.Gd3Layout;
import com.largomodo.floppyconvert.snes.SnesInterleaver;
import com.largomodo.floppyconvert.snes.InvalidChecksumException;
import com.largomodo.floppyconvert.snes.InvalidRomHeaderException;
import com.largomodo.floppyconvert.snes.SnesRom;
import com.largomodo.floppyconvert.snes.SnesRomReader;
import com.largomodo.floppyconvert.snes.UnsupportedHardwareException;
import com.largomodo.floppyconvert.util.SnesRomMatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Byte-parity matrix between floppyconvert GD3 output and uCON64 2.2.3, compared per part (name + SHA1).
 * <p>
 * CI runs the committed-table layer on synthetic fixtures only. Real fixtures and the live ucon64 layer run
 * where the files exist locally. (ref: DL-004, DL-022)
 * <p>
 * System properties: {@code gd3.parity.regenerate=true} prints table source instead of asserting;
 * {@code gd3.parity.sweepDir=<dir>} sweeps every ROM under dir against live ucon64.
 */
class Gd3ParityTest {

    private static final boolean REGENERATE = Boolean.getBoolean("gd3.parity.regenerate");
    private static final String SWEEP_DIR = System.getProperty("gd3.parity.sweepDir");

    @TempDir
    Path tempDir;

    static Stream<Row> rows() {
        return Gd3ParityExpectations.ROWS.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rows")
    void outputMatchesCommittedExpectation(Row row) throws Exception {
        assumeFalse(REGENERATE, "regenerate mode prints table rows instead of asserting");
        Path input = materializeOrSkip(row);
        if (row.twinOf() != null) {
            input = interleavedCopy(input, row);
        }

        Result ours = convertWithOurs(input, row.id());
        Result expected = Gd3ParityExpectations.expected(row.twinOf() != null ? row.twinOf() : row.id());

        if (expected instanceof Rejected rejected) {
            if (row.gaps().isEmpty()) {
                Rejected oursRejected = assertInstanceOf(Rejected.class, ours,
                        "ucon64 rejects " + row.id() + "; we must reject it too");
                // Rejecting for another reason (a bad checksum, say) is not parity: the wording is ucon64's.
                assertEquals(rejected.errorLine(), "ERROR: " + oursRejected.errorLine(),
                        "Rejection reason for " + row.id() + " differs from ucon64");
            } else {
                assertFalse(ours instanceof Rejected, "Row " + row.id() + " is now rejected like ucon64: remove its tags " + row.gaps());
            }
            return;
        }
        if (row.gaps().isEmpty()) {
            assertTrue(matches(row, expected, ours), "Parity mismatch for " + row.id() + ": expected " + expected + " but was " + ours);
            return;
        }
        assertKnownGap(row, expected, ours);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rows")
    void committedExpectationAgreesWithLiveUcon64(Row row) throws Exception {
        assumeFalse(REGENERATE, "regenerate mode prints table rows instead of asserting");
        assumeTrue(row.liveOracle() == LiveOracle.UCON64 || row.pinsOwnOutput(),
                () -> "live layer skipped: " + row.exemptReason());
        Path binary = Ucon64Oracle.locateOrSkip(Files.createDirectories(tempDir.resolve("oracle")));
        Path input = materializeOrSkip(row);

        Result live = Ucon64Oracle.convert(binary, input, row.preHeadered(), tempDir.resolve("live"));

        if (row.pinsOwnOutput()) {
            // The row may pin our own output only because uCON64 has none to compare with
            assertInstanceOf(Rejected.class, live, "uCON64 now converts " + row.id() + ": make it a UCON64 row");
            return;
        }

        assertEquals(Gd3ParityExpectations.expected(row.id()), live,
                "Committed table for " + row.id() + " drifted from live uCON64 output");
    }

    @Test
    void regenerate() throws Exception {
        assumeTrue(REGENERATE, "set -Dgd3.parity.regenerate=true to print table source");
        Path binary = Ucon64Oracle.locateOrSkip(Files.createDirectories(tempDir.resolve("oracle")));

        StringBuilder digests = new StringBuilder();
        StringBuilder rejections = new StringBuilder();
        for (Row row : Gd3ParityExpectations.ROWS) {
            if (row.twinOf() != null) {
                continue; // a twin has no digests of its own
            }
            Optional<Path> input = row.source().materialize(tempDir.resolve("in-" + row.id()));
            if (input.isEmpty()) {
                System.out.println("// SKIPPED (fixture absent): " + row.id());
                continue;
            }
            Result result;
            if (row.liveOracle() == LiveOracle.EXEMPT) {
                result = row.pinsOwnOutput()
                        ? convertWithOurs(input.get(), "regen-" + row.id())
                        : new Converted(hardwareSetDigests(row));
            } else {
                result = Ucon64Oracle.convert(binary, input.get(), row.preHeadered(), tempDir.resolve("live-" + row.id()));
            }
            if (result instanceof Converted converted) {
                digests.append("            Map.entry(\"").append(row.id()).append("\", List.of(\n");
                for (int i = 0; i < converted.parts().size(); i++) {
                    PartDigest part = converted.parts().get(i);
                    digests.append("                    new PartDigest(\"").append(part.name()).append("\", \"")
                            .append(part.sha1()).append("\")")
                            .append(i + 1 < converted.parts().size() ? ",\n" : "\n");
                }
                digests.append("            )),\n");
            } else if (result instanceof Rejected rejected) {
                rejections.append("            Map.entry(\"").append(row.id()).append("\", \"")
                        .append(rejected.errorLine()).append("\"),\n");
            }
        }
        System.out.println("// ---- DIGESTS (drop the trailing comma of the last entry) ----\n" + digests);
        System.out.println("// ---- REJECTIONS ----\n" + rejections);
    }

    @Test
    void sweep() throws Exception {
        assumeTrue(SWEEP_DIR != null, "set -Dgd3.parity.sweepDir=<dir> to sweep a ROM tree");
        Path binary = Ucon64Oracle.locateOrSkip(Files.createDirectories(tempDir.resolve("oracle")));

        Map<String, List<String>> byClass = new TreeMap<>();
        List<String> hardFailures = new ArrayList<>();
        List<Path> roms;
        try (Stream<Path> walk = Files.walk(Path.of(SWEEP_DIR))) {
            roms = walk.filter(Files::isRegularFile).filter(SnesRomMatcher::isRom).sorted().toList();
        }
        int counter = 0;
        for (Path rom : roms) {
            Path work = tempDir.resolve("sweep-" + counter++);
            Result live = Ucon64Oracle.convert(binary, rom, false, work);
            Result ours = convertWithOurs(rom, "sweep-out-" + counter);
            String verdict = classify(binary, rom, work, live, ours);
            if (verdict != null) {
                byClass.computeIfAbsent(verdict, k -> new ArrayList<>()).add(rom.toString());
                if (live instanceof Converted) {
                    hardFailures.add(verdict + ": " + rom);
                }
            }
        }
        byClass.forEach((name, list) -> System.out.println("SWEEP " + name + " " + list.size() + "\n  " + String.join("\n  ", list)));
        assertTrue(hardFailures.isEmpty(), "ROMs ucon64 converts that we do not match:\n" + String.join("\n", hardFailures));
    }

    private String classify(Path binary, Path rom, Path work, Result live, Result ours) throws Exception {
        if (live instanceof Rejected) {
            return ours instanceof Converted ? "WE_CONVERT_UCON64_REJECTS" : null;
        }
        if (ours instanceof Rejected) {
            return "REJECT";
        }
        if (live.equals(ours)) {
            return null;
        }
        String info = Ucon64Oracle.info(binary, rom, work);
        boolean ucon64HiRom = info.lines().anyMatch(l -> l.trim().toLowerCase(Locale.ROOT).startsWith("hirom: yes"));
        SnesRom ourRom = new SnesRomReader().load(rom, CopierFormat.GD3);
        if (ucon64HiRom != ourRom.isHiRom()) {
            return "DETECTION";
        }
        List<PartDigest> liveParts = ((Converted) live).parts();
        List<PartDigest> ourParts = ((Converted) ours).parts();
        if (liveParts.size() != ourParts.size()) {
            return "LAYOUT";
        }
        if (liveParts.stream().map(PartDigest::sha1).toList().equals(ourParts.stream().map(PartDigest::sha1).toList())) {
            return "NAME";
        }
        return liveParts.getFirst().sha1().equals(ourParts.getFirst().sha1()) ? "LAYOUT" : "HEADER";
    }

    // The twin input is the linear ROM interleaved with the layout the converter itself would use, under the same
    // file name so both rows produce the same part names.
    // Reading it back must restore the linear bytes, so the twin has to yield the linear row's digests.
    private Path interleavedCopy(Path linearFile, Row row) throws IOException {
        SnesRom rom = new SnesRomReader().load(linearFile, CopierFormat.GD3);
        byte[] interleaved = new SnesInterleaver().interleave(rom.rawData(), Gd3Layout.of(rom));
        Path twin = Files.createDirectories(tempDir.resolve("twin-" + row.id())).resolve(linearFile.getFileName());
        Files.write(twin, interleaved);
        return twin;
    }

    private Path materializeOrSkip(Row row) throws IOException {
        Optional<Path> input = row.source().materialize(tempDir.resolve("in-" + row.id()));
        assumeTrue(input.isPresent(), "fixture absent on this machine: " + row.id());
        return input.get();
    }

    private Result convertWithOurs(Path input, String outputName) throws IOException {
        RomSplitter splitter = new NativeConversionServiceFactory().createRomSplitter();
        Path out = Files.createDirectories(tempDir.resolve("ours-" + outputName));
        try {
            List<File> files = splitter.split(input.toFile(), out, CopierFormat.GD3);
            return new Converted(digest(files));
        } catch (UnsupportedHardwareException | InvalidChecksumException | InvalidRomHeaderException e) {
            return new Rejected(e.getMessage());
        }
    }

    private static List<PartDigest> digest(List<File> files) throws IOException {
        List<PartDigest> parts = new ArrayList<>();
        for (File file : files) {
            parts.add(PartDigest.of(file.toPath()));
        }
        parts.sort(Comparator.comparing(PartDigest::name));
        return parts;
    }

    private static List<PartDigest> hardwareSetDigests(Row row) throws IOException {
        try (Stream<Path> files = Files.list(Path.of(row.hardwareParts()))) {
            List<File> parts = files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".078"))
                    .map(Path::toFile).toList();
            return digest(parts);
        }
    }

    // A tagged row must still differ; G14-only rows differ in names alone and G13-only rows in part A alone,
    // so a name gap or a header gap cannot hide a body regression. (ref: DL-005)
    private static void assertKnownGap(Row row, Result expected, Result ours) {
        assertFalse(matches(row, expected, ours), "Row " + row.id() + " now matches ucon64: remove its tags " + row.gaps());
        System.out.println("KNOWN GAP " + row.id() + " " + row.gaps());

        if (row.gaps().equals(Set.of(Gap.G14))) {
            assertEquals(sha1s(expected), sha1s(ours), "G14 row " + row.id() + " must differ in names only");
        }
        if (row.gaps().equals(Set.of(Gap.G13))) {
            List<PartDigest> want = ((Converted) expected).parts();
            List<PartDigest> got = ((Converted) ours).parts();
            assertEquals(want.stream().map(PartDigest::name).toList(), got.stream().map(PartDigest::name).toList());
            assertEquals(want.subList(1, want.size()), got.subList(1, got.size()),
                    "G13 row " + row.id() + " must differ in part A only");
        }
    }

    // A row whose expectation is a hardware-verified set compares bytes only: that set was built from a file
    // with another name, so its part names are not the names our converter derives from this input.
    private static boolean matches(Row row, Result expected, Result ours) {
        return row.hardwareParts() != null ? sha1s(expected).equals(sha1s(ours)) && !sha1s(ours).isEmpty() : expected.equals(ours);
    }

    private static List<String> sha1s(Result result) {
        return result instanceof Converted converted
                ? converted.parts().stream().map(PartDigest::sha1).toList()
                : List.of();
    }
}
