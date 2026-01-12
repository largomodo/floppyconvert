package com.largomodo.floppyconvert.service;

import com.largomodo.floppyconvert.core.CopierFormat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Service interface for splitting SNES ROM files into backup unit format parts.
 * <p>
 * Abstracts the ROM splitting process to enable alternative implementations beyond ucon64.
 * Primary use cases:
 * <ul>
 *   <li>Production: ucon64-based splitting (current implementation)</li>
 *   <li>Testing: Mock implementations for unit testing without external dependencies</li>
 *   <li>Future: Alternative splitters (nsrt, unf, custom implementations)</li>
 * </ul>
 * <p>
 * <b>Contract Guarantees:</b>
 * <ul>
 *   <li>Input ROM file remains unmodified (read-only access)</li>
 *   <li>Output parts written to workDir directory</li>
 *   <li>Returned list contains split parts in correct order for sequential disk spanning</li>
 *   <li>Small ROMs (≤4 Mbit) may return single file without splitting</li>
 * </ul>
 * <p>
 * <b>Implementation Requirements:</b>
 * <ul>
 *   <li>Work-in-place strategy: direct output to workDir, no temporary directories</li>
 *   <li>Format-specific naming: must honor backup unit conventions (e.g., .1/.2 for FIG/SWC, .078A/.078B for GD3)</li>
 *   <li>Cleanup: delete intermediate conversion artifacts (e.g., .fig file before .1/.2 split)</li>
 *   <li>Fail-fast: throw IOException immediately on conversion/split failure</li>
 * </ul>
 */
public interface RomSplitter {

    /**
     * Split a SNES ROM file into backup unit format parts.
     * <p>
     * The splitting process typically involves:
     * <ol>
     *   <li>Convert input ROM (.sfc) to target backup unit format</li>
     *   <li>Split converted file into parts sized for floppy disk spanning</li>
     *   <li>Clean up intermediate conversion artifacts</li>
     * </ol>
     * <p>
     * <b>Path Handling:</b> Implementation must use absolute paths for input ROM to avoid
     * ambiguity when changing working directories. Output parts go directly to workDir.
     * <p>
     * <b>Ordering Contract:</b> Returned list must be ordered for sequential disk writes
     * (Part 1 → Disk 1, Part 2 → Disk 2, etc.). Typically sorted by numeric extension.
     *
     * @param inputRom Source ROM file (.sfc format expected, but format-agnostic contract)
     * @param workDir  Target directory for split parts (implementation writes directly here)
     * @param format   Target backup unit format (FIG/SWC/UFO/GD3) - determines output naming
     * @return Ordered list of split part files (empty list never returned - throw IOException instead)
     * @throws IOException              if splitting fails, no parts generated, or I/O error occurs
     * @throws IllegalArgumentException if inputRom does not exist or workDir is not a directory
     */
    List<File> split(File inputRom, Path workDir, CopierFormat format) throws IOException;
}
