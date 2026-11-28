package com.largomodo.floppyconvert;

import com.largomodo.floppyconvert.Gd3ParityExpectations.Row;
import com.largomodo.floppyconvert.Ucon64Oracle.Converted;
import com.largomodo.floppyconvert.Ucon64Oracle.PartDigest;
import com.largomodo.floppyconvert.Ucon64Oracle.Rejected;
import com.largomodo.floppyconvert.Ucon64Oracle.Result;
import com.largomodo.floppyconvert.format.CopierFormat;
import com.largomodo.floppyconvert.service.NativeConversionServiceFactory;
import com.largomodo.floppyconvert.service.RomSplitter;
import com.largomodo.floppyconvert.snes.UnsupportedHardwareException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Guards FIG, SWC and UFO output against edits to shared code: every fixture of the GD3 parity matrix is split
 * for each format and compared with CrossFormatExpectations. Real fixtures skip when absent. (ref: DL-006)
 * <p>
 * {@code -Dgd3.parity.regenerate=true} prints the table source from the code under test instead of asserting.
 */
class CrossFormatStabilityTest {

    private static final boolean REGENERATE = Boolean.getBoolean("gd3.parity.regenerate");
    private static final List<CopierFormat> FORMATS = List.of(CopierFormat.FIG, CopierFormat.SWC, CopierFormat.UFO);

    @TempDir
    Path tempDir;

    static Stream<Arguments> cases() {
        return Gd3ParityExpectations.ROWS.stream()
                // Pre-headered and twin rows exercise GD3 input handling only; the other formats never see them.
                .filter(row -> !row.preHeadered() && row.twinOf() == null)
                .flatMap(row -> FORMATS.stream().map(format -> Arguments.of(row, format)));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("cases")
    void outputMatchesCommittedTable(Row row, CopierFormat format) throws IOException {
        assumeFalse(REGENERATE, "regenerate mode prints table rows instead of asserting");
        Optional<Path> input = row.source().materialize(tempDir.resolve("in"));
        assumeTrue(input.isPresent(), "fixture absent on this machine: " + row.id());

        assertEquals(CrossFormatExpectations.expected(row.id(), format), split(input.get(), format),
                format + " output changed for " + row.id());
    }

    @Test
    void regenerate() throws IOException {
        assumeTrue(REGENERATE, "set -Dgd3.parity.regenerate=true to print table source");
        StringBuilder digests = new StringBuilder();
        StringBuilder rejections = new StringBuilder();
        int counter = 0;
        for (Row row : Gd3ParityExpectations.ROWS) {
            if (row.preHeadered() || row.twinOf() != null) {
                continue;
            }
            Optional<Path> input = row.source().materialize(tempDir.resolve("in-" + counter++));
            if (input.isEmpty()) {
                continue;
            }
            for (CopierFormat format : FORMATS) {
                String key = CrossFormatExpectations.key(row.id(), format);
                Result result = split(input.get(), format);
                if (result instanceof Converted converted) {
                    digests.append("            Map.entry(\"").append(key).append("\", List.of(\n");
                    for (int i = 0; i < converted.parts().size(); i++) {
                        PartDigest part = converted.parts().get(i);
                        digests.append("                    new PartDigest(\"").append(part.name()).append("\", \"")
                                .append(part.sha1()).append("\")")
                                .append(i + 1 < converted.parts().size() ? ",\n" : "\n");
                    }
                    digests.append("            )),\n");
                } else if (result instanceof Rejected rejected) {
                    rejections.append("            Map.entry(\"").append(key).append("\", \"")
                            .append(rejected.errorLine().replace("\"", "\\\"")).append("\"),\n");
                }
            }
        }
        System.out.println("// ---- DIGESTS (drop the trailing comma of the last entry) ----\n" + digests);
        System.out.println("// ---- REJECTIONS ----\n" + rejections);
    }

    private Result split(Path input, CopierFormat format) throws IOException {
        RomSplitter splitter = new NativeConversionServiceFactory().createRomSplitter();
        Path out = Files.createTempDirectory(tempDir, "split-");
        try {
            List<File> files = splitter.split(input.toFile(), out, format);
            List<PartDigest> parts = new ArrayList<>();
            for (File file : files) {
                parts.add(PartDigest.of(file.toPath()));
            }
            parts.sort(Comparator.comparing(PartDigest::name));
            return new Converted(parts);
        } catch (UnsupportedHardwareException e) {
            return new Rejected(e.getMessage());
        }
    }
}
