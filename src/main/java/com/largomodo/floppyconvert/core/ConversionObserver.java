package com.largomodo.floppyconvert.core;

import java.nio.file.Path;

/**
 * Observer interface for ROM conversion lifecycle events.
 * <p>
 * Implementations can monitor the conversion process by receiving callbacks at key points:
 * start, success, and failure. All methods have default no-op implementations, allowing
 * consumers to override only the events they care about.
 * </p>
 * <p>
 * Example usage:
 * </p>
 * <pre>{@code
 * ConversionObserver observer = new ConversionObserver() {
 *     @Override
 *     public void onSuccess(Path rom, int diskCount) {
 *         System.out.println("Converted " + rom + " to " + diskCount + " disks");
 *     }
 * };
 * }</pre>
 *
 * @see RomProcessor
 */
public interface ConversionObserver {

    /**
     * Called when ROM conversion begins.
     *
     * @param rom the ROM file being processed
     */
    default void onStart(Path rom) {}

    /**
     * Called when ROM conversion completes successfully.
     *
     * @param rom the ROM file that was processed
     * @param diskCount the number of floppy disk images created
     */
    default void onSuccess(Path rom, int diskCount) {}

    /**
     * Called when ROM conversion fails.
     *
     * @param rom the ROM file that failed to process
     * @param e the exception that caused the failure
     */
    default void onFailure(Path rom, Exception e) {}
}
