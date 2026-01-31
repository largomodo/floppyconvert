package com.largomodo.floppyconvert.core;

import java.io.File;
import java.util.Comparator;

/**
 * Comparator for ROM part files that handles both numeric extensions (FIG/SWC/UFO)
 * and alphanumeric filenames (GD3).
 * <p>
 * Sorting strategy:
 * 1. Compare base filenames (without extension) case-insensitively
 * 2. If filenames differ, sort lexicographically (handles GD3: ...A < ...B)
 * 3. If filenames are same, parse extensions as integers (handles FIG: .1 < .10)
 * <p>
 * Why universal approach: Format-specific comparators would duplicate logic across
 * 4 classes (120+ lines). This single implementation handles all formats with
 * filename-first, extension-second algorithm.
 */
public class RomPartComparator implements Comparator<File> {

    @Override
    public int compare(File f1, File f2) {
        String name1 = getBaseName(f1.getName());
        String name2 = getBaseName(f2.getName());

        // Try filename comparison first (handles GD3 alphanumeric: SF32CHRA < SF32CHRB)
        int nameComparison = name1.compareToIgnoreCase(name2);
        if (nameComparison != 0) {
            return nameComparison;
        }

        // Filenames match, compare extensions (handles FIG numeric: .1 < .10)
        String ext1 = getExtension(f1.getName());
        String ext2 = getExtension(f2.getName());

        return compareExtensions(ext1, ext2);
    }

    private String getBaseName(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex > 0 ? filename.substring(0, dotIndex) : filename;
    }

    private String getExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return (dotIndex > 0 && dotIndex < filename.length() - 1) ? filename.substring(dotIndex + 1) : "";
    }

    private int compareExtensions(String ext1, String ext2) {
        try {
            // Numeric extensions: parse as integers to handle .1 < .2 < .10 correctly
            return Integer.compare(Integer.parseInt(ext1), Integer.parseInt(ext2));
        } catch (NumberFormatException e) {
            // UFO format: extract numeric prefix from "1gm", "2gm", etc.
            Integer num1 = extractNumericPrefix(ext1);
            Integer num2 = extractNumericPrefix(ext2);

            if (num1 != null && num2 != null) {
                return Integer.compare(num1, num2);
            }

            // Non-numeric extensions: fallback to string comparison (GD3 edge cases)
            return ext1.compareToIgnoreCase(ext2);
        }
    }

    private Integer extractNumericPrefix(String ext) {
        if (ext == null || ext.isEmpty()) {
            return null;
        }

        StringBuilder numStr = new StringBuilder();
        for (char c : ext.toCharArray()) {
            if (Character.isDigit(c)) {
                numStr.append(c);
            } else {
                break;
            }
        }

        if (numStr.length() > 0) {
            try {
                return Integer.parseInt(numStr.toString());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
