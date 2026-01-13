package com.largomodo.floppyconvert.core;

import com.largomodo.floppyconvert.core.domain.RomPartMetadata;
import com.largomodo.floppyconvert.core.workspace.ConversionWorkspace;
import com.largomodo.floppyconvert.util.DosNameUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Normalizes ROM part filenames for shell and mcopy compatibility.
 *
 * Stateless design enables concurrent use without synchronization (parallel batch processing),
 * simpler unit testing (no state reset between tests), and matches DosNameUtil pattern.
 * All operations idempotent (repeated sanitization produces same result).
 */
public class RomPartNormalizer {

    /**
     * Sanitize ROM part filenames and generate metadata for disk packing.
     *
     * Renames files on disk to remove shell-sensitive characters (prevents mcopy argument
     * parser issues), updates workspace tracking to reflect new paths, and generates
     * DOS-compliant names for floppy image injection.
     *
     * Two-stage naming: sanitizeForShell produces filesystem paths safe for external tool
     * invocation, DosNameUtil produces 8.3 names for FAT12 directory entries.
     *
     * @param rawParts Split ROM parts from RomSplitter (may have shell-unsafe names)
     * @param workspace Conversion workspace for tracking artifact lifecycle
     * @return Immutable list of metadata (sanitized paths, sizes, DOS names)
     * @throws IOException if file rename operations fail or ROM part has no parent directory
     */
    public List<RomPartMetadata> normalize(List<File> rawParts, ConversionWorkspace workspace)
            throws IOException {
        List<RomPartMetadata> metadata = new ArrayList<>();

        for (File part : rawParts) {
            String sanitizedName = sanitizeForShell(part.getName());
            
            Path parentDir = part.toPath().getParent();
            if (parentDir == null) {
                throw new IOException("ROM part file has no parent directory: " + part.toPath());
            }
            Path sanitizedPath = parentDir.resolve(sanitizedName);

            // Check for filename collision before move
            if (Files.exists(sanitizedPath) && !sanitizedPath.equals(part.toPath())) {
                throw new IOException("Filename sanitization collision: " + part.getName() + 
                    " would overwrite existing file " + sanitizedPath.getFileName() + 
                    ". ROM parts have conflicting names after sanitization.");
            }

            Files.move(part.toPath(), sanitizedPath, StandardCopyOption.REPLACE_EXISTING);

            // Workspace tracking must stay synchronized with filesystem state
            workspace.markAsOutput(part.toPath());  // Remove old path
            workspace.track(sanitizedPath);         // Add new path

            String dosName = DosNameUtil.sanitize(sanitizedName);

            metadata.add(new RomPartMetadata(
                sanitizedPath,
                Files.size(sanitizedPath),
                dosName
            ));
        }

        return List.copyOf(metadata);
    }

    /**
     * Sanitize a single string for filesystem and shell compatibility.
     *
     * Public wrapper for sanitizeForShell, enables RomProcessor to sanitize baseName
     * for workspace directory and disk image filenames. Centralizes shell-safe
     * sanitization logic in single location (DRY principle).
     *
     * @param name Original name (e.g., "Game [#1]")
     * @return Shell-safe name (e.g., "Game__1_")
     */
    public String sanitizeName(String name) {
        return sanitizeForShell(name);
    }

    /**
     * Remove shell-sensitive characters that cause mcopy argument parsing issues.
     *
     * Replacements prevent path truncation (mcopy treats brackets/parens as special) and
     * command injection risks (ampersand, dollar sign, exclamation enable shell expansion).
     * Idempotent: applying twice produces same result as applying once (prevents cascading
     * transformations during retries).
     *
     * @param originalName Raw filename from ROM splitter (e.g., "game[#1].sfc.1")
     * @return Shell-safe filename (e.g., "game_1_.sfc.1")
     */
    private String sanitizeForShell(String originalName) {
        return originalName
                .replace("#", "_").replace("[", "_").replace("]", "_")
                .replace("(", "_").replace(")", "_").replace("&", "_")
                .replace("$", "_").replace("!", "_").replace("~", "_")
                .replace("'", "_").replace(",", "_").replace("+", "_")
                .replace("'", "_").replace(" ", "_");
    }
}
