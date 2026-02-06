package com.largomodo.floppyconvert.format;

import java.io.File;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Enum representing SNES backup unit formats supported by ucon64.
 * Encapsulates the command-line flag for each format.
 */
public enum CopierFormat {
    FIG("--fig"),   // Pro Fighter Q/X/Turbo
    SWC("--swc"),   // Super Wild Card
    UFO("--ufo"),   // Super UFO
    GD3("--gd3");   // Game Doctor SF3/SF6/SF7

    private final String cmdFlag;

    CopierFormat(String cmdFlag) {
        this.cmdFlag = cmdFlag;
    }

    public static CopierFormat fromCliArgument(String arg) {
        if (arg == null) {
            throw new IllegalArgumentException("Format argument cannot be null. Supported: FIG, SWC, UFO, GD3");
        }
        try {
            return valueOf(arg.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid format: " + arg + ". Supported: FIG, SWC, UFO, GD3");
        }
    }

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

    public String getCmdFlag() {
        return cmdFlag;
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
            case FIG -> file -> {
                String name = file.getName();
                return name.matches(".*\\.[0-9]+$");
            };
            case SWC -> file -> {
                String name = file.getName();
                return name.matches(".*\\.[0-9]+$");
            };
            case UFO -> file -> {
                String name = file.getName();
                return name.matches(".*\\.[0-9]+gm$");
            };
            case GD3 -> file -> {
                String name = file.getName();
                return name.matches(".*[A-Z]\\.078$");
            };
        };
    }
}
