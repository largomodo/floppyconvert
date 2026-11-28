package com.largomodo.floppyconvert.format;

import java.io.File;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * SNES backup unit formats this tool can produce floppy sets for.
 */
public enum CopierFormat {
    FIG,   // Pro Fighter Q/X/Turbo
    SWC,   // Super Wild Card
    UFO,   // Super UFO
    GD3;   // Game Doctor SF3/SF6/SF7

    // Returns Optional to signal graceful fallback for .sfc (no matching format)
    public static Optional<CopierFormat> fromFileExtension(String extension) {
        if (extension == null || extension.isEmpty()) {
            return Optional.empty();
        }

        // Case-insensitive matching: filesystem case sensitivity varies
        String normalized = extension.startsWith(".") ? extension.substring(1) : extension;
        normalized = normalized.toLowerCase();

        return switch (normalized) {
            case "fig" -> Optional.of(FIG);
            case "swc" -> Optional.of(SWC);
            case "ufo" -> Optional.of(UFO);
            default -> Optional.empty();  // .sfc, .078, unrecognized fall through
        };
    }

    public String getFileExtension() {
        return switch (this) {
            case FIG -> "fig";
            case SWC -> "swc";
            case UFO -> "ufo";
            case GD3 -> "078";
        };
    }

    public Predicate<File> getSplitPartFilter() {
        return switch (this) {
            // FIG and SWC share identical numeric-extension naming; combined case arm (ref: DL-004)
            case FIG, SWC -> file -> {
                String name = file.getName();
                return name.matches(".*\\.[0-9]+$");
            };
            case UFO -> file -> {
                String name = file.getName();
                return name.matches(".*\\.[0-9]+gm$");
            };
            // SF + size digits (1 below 10 Mbit, 2 from 10 to 96) + at most 6 characters: the name is cut to 6
            // (below 10 Mbit) or 7 characters, plus a part letter, which follows an X at index 6 for a HiROM below
            // 10 Mbit. The SF + digit prefix still rejects unrelated .078 files. Split output is collected by name,
            // so the filter must accept every name NativeRomSplitter.gd3PartName produces.
            case GD3 -> file -> file.getName().matches("(?i)SF\\d{1,2}[^.]{0,6}\\.078");
        };
    }
}
