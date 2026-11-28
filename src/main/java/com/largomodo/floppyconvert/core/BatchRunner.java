package com.largomodo.floppyconvert.core;

import com.largomodo.floppyconvert.core.workspace.CleanupException;
import com.largomodo.floppyconvert.format.CopierFormat;
import com.largomodo.floppyconvert.util.SnesRomMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Converts every ROM under a directory tree concurrently, mirroring the input
 * directory structure under the output root.
 * <p>
 * Fail-soft: a ROM that fails is logged and counted, and the remaining ROMs are
 * still processed. The caller decides what a non-clean {@link Result} means.
 * <p>
 * Concurrency: fixed pool of one thread per CPU core, a bounded queue
 * (2 * cores) so a large library does not pile up queued tasks, and
 * CallerRunsPolicy so the submitting thread converts a ROM itself when the queue
 * is full. Each ROM gets its own workspace (UUID suffix), so tasks share no
 * mutable state.
 */
public class BatchRunner {

    private static final Logger log = LoggerFactory.getLogger(BatchRunner.class);

    private final RomProcessor processor;
    private final FailureClassifier classifier;

    public BatchRunner(RomProcessor processor) {
        this(processor, FailureClassifier.ALWAYS_OTHER);
    }

    // Injected because core cannot import snes exception types; the single-argument constructor above keeps
    // callers without domain knowledge on OTHER.
    public BatchRunner(RomProcessor processor, FailureClassifier classifier) {
        this.processor = processor;
        this.classifier = classifier;
    }

    /**
     * Outcome of a batch run.
     *
     * @param succeeded         ROMs converted
     * @param failed            ROMs that failed to convert
     * @param traversalComplete false when the input tree could not be walked completely,
     *                          in which case some ROMs may never have been attempted
     */
    public record Result(int succeeded, int failed, boolean traversalComplete) {
        public boolean isClean() {
            return failed == 0 && traversalComplete;
        }
    }

    public Result run(Path inputRoot, Path outputRoot, CopierFormat format) {
        ExecutorService executor = createExecutor();
        registerShutdownHook(executor);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        ConversionObserver observer = createObserver(inputRoot, successCount, failCount);

        boolean traversalComplete = true;
        // The ROM list is collected before any task is submitted, so images written
        // during the run (output may live inside the input tree) are never walked.
        try (Stream<Path> stream = Files.walk(inputRoot)) {
            List<Path> romFiles = collectRomFiles(stream, inputRoot);

            for (Path romPath : romFiles) {
                submit(executor, romPath, inputRoot, outputRoot, observer, format);
            }
        } catch (UncheckedIOException e) {
            traversalComplete = false;
            log.error("WARNING: Directory traversal interrupted - {}", e.getCause().getMessage());
        } catch (IOException e) {
            traversalComplete = false;
            log.error("ERROR: Cannot traverse input directory: {}", e.getMessage());
        } finally {
            try {
                awaitCompletion(executor);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        Result result = new Result(successCount.get(), failCount.get(), traversalComplete);
        if (traversalComplete) {
            log.info("Batch complete: {} successful, {} failed", result.succeeded(), result.failed());
        } else {
            log.error("Batch incomplete: {} successful, {} failed, some directories skipped",
                    result.succeeded(), result.failed());
        }
        return result;
    }

    private static ExecutorService createExecutor() {
        int coreCount = Runtime.getRuntime().availableProcessors();
        return new ThreadPoolExecutor(
                coreCount,
                coreCount,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(2 * coreCount),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    /**
     * On SIGINT, stop accepting work and give running conversions time to finish
     * so their workspaces are cleaned up.
     */
    private static void registerShutdownHook(ExecutorService executor) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (!executor.isShutdown()) {
                log.info("Interrupt received, shutting down gracefully...");
                executor.shutdown();
                try {
                    if (!executor.awaitTermination(5, TimeUnit.MINUTES)) {
                        executor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    executor.shutdownNow();
                }
            }
        }));
    }

    private static void awaitCompletion(ExecutorService executor) throws InterruptedException {
        executor.shutdown();
        if (!executor.awaitTermination(5, TimeUnit.MINUTES)) {
            executor.shutdownNow();
        }
    }

    private static List<Path> collectRomFiles(Stream<Path> stream, Path inputRoot) {
        return stream.filter(path -> {
                    try {
                        return Files.isRegularFile(path);
                    } catch (UncheckedIOException e) {
                        log.warn("WARNING: Cannot access {} - skipping", inputRoot.relativize(path));
                        return false;
                    }
                })
                .filter(SnesRomMatcher::isRom)
                .collect(Collectors.toList());
    }

    private ConversionObserver createObserver(Path inputRoot, AtomicInteger successCount,
                                              AtomicInteger failCount) {
        return new ConversionObserver() {
            @Override
            public void onSuccess(Path rom, int diskCount) {
                successCount.incrementAndGet();
            }

            @Override
            public void onFailure(Path rom, Exception e) {
                failCount.incrementAndGet();
                // The category token sits between path and message so failures.log can be grouped with grep or cut
                log.error("FAILED: {} - {} - {}", inputRoot.relativize(rom), classifier.classify(e), e.getMessage());
            }
        };
    }

    private void submit(ExecutorService executor, Path romPath, Path inputRoot, Path outputRoot,
                        ConversionObserver observer, CopierFormat format) {
        executor.submit(() -> {
            try {
                MDC.put("rom", romPath.getFileName().toString());
                String uniqueSuffix = UUID.randomUUID().toString();
                Path relativePath = inputRoot.relativize(romPath.getParent());
                Path targetBaseDir = outputRoot.resolve(relativePath);
                Files.createDirectories(targetBaseDir);

                observer.onStart(romPath);
                int diskCount = processor.processRom(
                        romPath.toFile(),
                        targetBaseDir,
                        uniqueSuffix,
                        format,
                        false
                );
                observer.onSuccess(romPath, diskCount);
            } catch (CleanupException e) {
                // Logged with the exception so the suppressed cleanup failures reach the log file
                log.error("Cleanup failed for {}", romPath, e);
                observer.onFailure(romPath, e);
            } catch (Exception e) {
                observer.onFailure(romPath, e);
            } finally {
                MDC.clear();
            }
            return null;
        });
    }
}
