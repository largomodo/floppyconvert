package com.largomodo.floppyconvert.core;

import java.io.File;
import java.util.function.Predicate;

/**
 * Enum representing SNES backup unit formats supported by ucon64.
 * Encapsulates the command-line flag for each format.
 */
public enum CopierFormat {
    FIG("--fig"),   // Doctor V64 / Mr Backup Z64
    SWC("--swc"),   // Super Wild Card
    UFO("--ufo"),   // Super UFO
    GD3("--gd3");   // Game Doctor SF3/SF6/SF7

    private final String cmdFlag;

    CopierFormat(String cmdFlag) {
        this.cmdFlag = cmdFlag;
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
