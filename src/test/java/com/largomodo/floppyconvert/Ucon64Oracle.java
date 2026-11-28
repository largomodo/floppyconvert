package com.largomodo.floppyconvert;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Live oracle for GD3 parity tests: runs the uCON64 2.2.3 binary at classpath resource /ucon64.
 * <p>
 * The binary is Linux x86-64 only and never committed (src/test/resources/.gitignore), so callers skip
 * through {@link #locateOrSkip(Path)} when it is absent. The version check guards the committed digest
 * tables: a different ucon64 would silently rewrite them in regenerate mode. (ref: DL-025)
 */
public final class Ucon64Oracle {

    private static final String RESOURCE = "/ucon64";
    private static final String REQUIRED_VERSION = "uCON64 2.2.3";
    // Measured runs take 14-32 ms; the limit only exists to kill a hung process (e.g. an interactive prompt)
    // instead of hanging the build.
    private static final long TIMEOUT_SECONDS = 120;

    private Ucon64Oracle() {
    }

    /**
     * One output part: file name and SHA1 of its bytes.
     */
    public record PartDigest(String name, String sha1) {
        public static PartDigest of(Path file) throws IOException {
            return new PartDigest(file.getFileName().toString(), Ucon64Oracle.sha1(Files.readAllBytes(file)));
        }
    }

    public sealed interface Result permits Converted, Rejected {
    }

    /**
     * ucon64 produced a part set, sorted by name.
     */
    public record Converted(List<PartDigest> parts) implements Result {
    }

    /**
     * ucon64 printed an ERROR line and produced no parts.
     */
    public record Rejected(String errorLine) implements Result {
    }

    public static String sha1(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 is required on every JVM", e);
        }
    }

    /**
     * Copies the classpath binary into scratchDir, marks it executable and checks its version.
     * Skips the calling test (JUnit assumption) when any of that is not possible.
     */
    public static Path locateOrSkip(Path scratchDir) throws IOException, InterruptedException {
        assumeTrue(System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux"),
                "ucon64 oracle runs on Linux only");
        URL url = Ucon64Oracle.class.getResource(RESOURCE);
        assumeTrue(url != null, "no ucon64 binary on the test classpath");

        Path binary = scratchDir.resolve("ucon64");
        try (InputStream in = url.openStream()) {
            Files.copy(in, binary, StandardCopyOption.REPLACE_EXISTING);
        }
        assumeTrue(binary.toFile().setExecutable(true), "cannot mark the ucon64 binary executable");

        Path home = Files.createDirectories(scratchDir.resolve("version-home"));
        String output = run(List.of(binary.toString(), "--version"), home, scratchDir);
        assumeTrue(output.contains(REQUIRED_VERSION), "ucon64 binary is not " + REQUIRED_VERSION);
        return binary;
    }

    /**
     * Converts rom the way a user would: {@code --gd3} then {@code -s}. A pre-headered GD3 input runs only {@code -s}.
     */
    public static Result convert(Path binary, Path rom, boolean preHeadered, Path workDir)
            throws IOException, InterruptedException {
        Path home = Files.createDirectories(workDir.resolve("home"));
        Path gd3Dir = Files.createDirectories(workDir.resolve("gd3"));
        Path partsDir = Files.createDirectories(workDir.resolve("parts"));

        Path toSplit = rom;
        if (!preHeadered) {
            String output = run(List.of(binary.toString(), "--gd3", "--nbak", "--ncol", "-q",
                    "-o=" + gd3Dir, rom.toString()), home, workDir);
            Optional<String> error = errorLine(output);
            if (error.isPresent()) {
                return new Rejected(error.get());
            }
            try (Stream<Path> files = Files.list(gd3Dir)) {
                toSplit = files.findFirst().orElseThrow(() ->
                        new IOException("ucon64 --gd3 wrote no file for " + rom));
            }
        }

        String output = run(List.of(binary.toString(), "-s", "--nbak", "--ncol", "-q",
                "-o=" + partsDir, toSplit.toString()), home, workDir);
        Optional<String> error = errorLine(output);
        if (error.isPresent()) {
            return new Rejected(error.get());
        }

        List<PartDigest> parts = new ArrayList<>();
        try (Stream<Path> files = Files.list(partsDir)) {
            for (Path part : files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".078"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString())).toList()) {
                parts.add(PartDigest.of(part));
            }
        }
        return new Converted(parts);
    }

    /**
     * Runs ucon64's plain info mode; the sweep reads its "HiROM:" line to compare bank-type detection.
     */
    public static String info(Path binary, Path rom, Path workDir) throws IOException, InterruptedException {
        Path home = Files.createDirectories(workDir.resolve("home"));
        return run(List.of(binary.toString(), "--nbak", "--ncol", rom.toString()), home, workDir);
    }

    private static Optional<String> errorLine(String output) {
        return output.lines().map(String::trim).filter(line -> line.startsWith("ERROR:")).findFirst();
    }

    // List arguments only: no shell is involved. stdin is /dev/null so a prompt cannot block the run.
    private static String run(List<String> command, Path home, Path workDir) throws IOException, InterruptedException {
        Path log = Files.createTempFile(workDir, "ucon64-", ".log");
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(workDir.toFile())
                .redirectErrorStream(true)
                .redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null")))
                .redirectOutput(log.toFile());
        builder.environment().put("HOME", home.toString());

        Process process = builder.start();
        if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("ucon64 did not finish within " + TIMEOUT_SECONDS + " s: " + command);
        }
        return Files.readString(log, StandardCharsets.ISO_8859_1);
    }
}
