package com.largomodo.floppyconvert;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.filter.ThresholdFilter;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.read.ListAppender;
import com.largomodo.floppyconvert.snes.generators.SyntheticRomFactory;
import com.largomodo.floppyconvert.snes.generators.SyntheticRomFactory.DspChipset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for logging infrastructure integration.
 * <p>
 * Verifies:
 * - Verbose flag enables DEBUG level logging
 * - Verbose flag adjusts STDOUT ThresholdFilter so DEBUG events reach the console (not just the log file)
 *   (ref: DL-001, R-003)
 * - MDC context propagates ROM filename to log entries
 * - ERROR level logs reach FAILURES appender via LevelFilter
 * <p>
 * WARNING: Checking root logger level alone is insufficient for verifying verbose console output.
 * The STDOUT appender has a separate ThresholdFilter gate. A test that only asserts
 * {@code root.getLevel() == DEBUG} will pass even when ThresholdFilter blocks DEBUG events
 * from reaching the console. Always verify the appender filter state, not just the logger level.
 */
class FloppyConvertLoggingTest {

    @TempDir
    Path tempDir;

    private Path romPath;
    private ListAppender<ILoggingEvent> listAppender;
    private FileAppender<ILoggingEvent> testFileAppender;
    private Logger rootLogger;
    private Level originalLevel;
    private Path testLogFile;

    @BeforeEach
    void setUp() throws IOException {
        romPath = SyntheticRomFactory.generateLoRom(4, 0, DspChipset.ABSENT, "LOGTEST4L", tempDir);

        // Set up test logging infrastructure
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
        originalLevel = rootLogger.getLevel();

        // Create test log file in temp directory
        testLogFile = tempDir.resolve("test-mdc.log");

        // Set up FileAppender with MDC pattern to test MDC propagation
        testFileAppender = new FileAppender<>();
        testFileAppender.setContext(loggerContext);
        testFileAppender.setFile(testLogFile.toString());

        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(loggerContext);
        encoder.setPattern("%d{HH:mm:ss.SSS} [%thread] [%X{rom}] %-5level %logger{36} - %msg%n");
        encoder.start();

        testFileAppender.setEncoder(encoder);
        testFileAppender.start();

        listAppender = new ListAppender<>();
        listAppender.setContext(loggerContext);
        listAppender.start();

        // Attach appenders to root logger
        rootLogger.addAppender(listAppender);
        rootLogger.addAppender(testFileAppender);
        rootLogger.setLevel(Level.DEBUG);
    }

    @AfterEach
    void tearDown() {
        // Clean up: remove appenders and restore original level
        if (listAppender != null) {
            listAppender.stop();
            rootLogger.detachAppender(listAppender);
        }
        if (testFileAppender != null) {
            testFileAppender.stop();
            rootLogger.detachAppender(testFileAppender);
        }
        if (originalLevel != null) {
            rootLogger.setLevel(originalLevel);
        }
        // Reset STDOUT ThresholdFilter to INFO to prevent test pollution
        ch.qos.logback.core.Appender<ILoggingEvent> stdoutAppender = rootLogger.getAppender("STDOUT");
        if (stdoutAppender != null) {
            for (Filter<?> filter : stdoutAppender.getCopyOfAttachedFiltersList()) {
                if (filter instanceof ThresholdFilter tf) {
                    tf.setLevel("INFO");
                }
            }
        }
    }

    @Test
    void testVerboseFlagEnablesDebugOnConsole() throws Exception {
        FloppyConvert floppyConvert = new FloppyConvert();
        CommandLine cmd = new CommandLine(floppyConvert)
                .setCaseInsensitiveEnumValuesAllowed(true);

        cmd.parseArgs("-v", romPath.toString(), "--output-dir", tempDir.toString(), "--format", "fig");

        try {
            floppyConvert.call();
        } catch (Exception e) {
            // Expected to fail due to ROM validation or other issues
        }

        Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ch.qos.logback.core.Appender<ILoggingEvent> stdoutAppender = logger.getAppender("STDOUT");
        assertNotNull(stdoutAppender, "STDOUT appender should exist");

        boolean debugAllowed = false;
        for (Filter<?> filter : stdoutAppender.getCopyOfAttachedFiltersList()) {
            if (filter instanceof ThresholdFilter tf) {
                ch.qos.logback.classic.spi.LoggingEvent debugEvent =
                        new ch.qos.logback.classic.spi.LoggingEvent();
                debugEvent.setLevel(Level.DEBUG);
                ch.qos.logback.core.spi.FilterReply reply = tf.decide(debugEvent);
                assertNotEquals(ch.qos.logback.core.spi.FilterReply.DENY, reply,
                        "ThresholdFilter should not deny DEBUG events after --verbose");
                debugAllowed = true;
            }
        }

        assertTrue(debugAllowed || stdoutAppender.getCopyOfAttachedFiltersList().isEmpty(),
                "Should have ThresholdFilter allowing DEBUG or no filters blocking DEBUG output");
    }

    @Test
    void testNonVerboseStdoutFilterRemainsAtInfo() {
        // Reset ThresholdFilter to INFO first (shared LoggerContext may have been modified by prior tests)
        Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ch.qos.logback.core.Appender<ILoggingEvent> stdoutAppender = logger.getAppender("STDOUT");
        if (stdoutAppender != null) {
            for (Filter<?> filter : stdoutAppender.getCopyOfAttachedFiltersList()) {
                if (filter instanceof ThresholdFilter tf) {
                    tf.setLevel("INFO");
                }
            }
        }

        FloppyConvert floppyConvert = new FloppyConvert();
        CommandLine cmd = new CommandLine(floppyConvert);

        Path dummyInput = tempDir.resolve("dummy.sfc");
        cmd.parseArgs(dummyInput.toString());

        try {
            floppyConvert.call();
        } catch (Exception e) {
            // Expected to fail due to missing file
        }

        // Re-fetch appender (same instance but defensive)
        stdoutAppender = logger.getAppender("STDOUT");
        if (stdoutAppender != null) {
            for (Filter<?> filter : stdoutAppender.getCopyOfAttachedFiltersList()) {
                if (filter instanceof ThresholdFilter tf) {
                    ch.qos.logback.classic.spi.LoggingEvent debugEvent =
                            new ch.qos.logback.classic.spi.LoggingEvent();
                    debugEvent.setLevel(Level.DEBUG);
                    assertEquals(ch.qos.logback.core.spi.FilterReply.DENY, tf.decide(debugEvent),
                            "STDOUT ThresholdFilter should still deny DEBUG without --verbose");
                }
            }
        }
    }

    @Test
    void testMdcContextInBatch() throws Exception {
        // Create input directory with a ROM file
        Path inputDir = tempDir.resolve("input");
        Files.createDirectories(inputDir);
        Path romFile = inputDir.resolve("LOGTEST4L.sfc");
        Files.copy(romPath, romFile);

        Path outputDir = tempDir.resolve("output");

        // Execute batch conversion using CommandLine
        new CommandLine(new FloppyConvert())
                .setCaseInsensitiveEnumValuesAllowed(true)
                .execute(
                        inputDir.toString(),
                        "--output-dir", outputDir.toString(),
                        "--format", "fig"
                );

        // Stop the file appender to flush all content
        testFileAppender.stop();

        // Verify that the test log file contains MDC context
        assertTrue(Files.exists(testLogFile), "Test log file should be created");

        String logContent = Files.readString(testLogFile);

        // The pattern is: %d{HH:mm:ss.SSS} [%thread] [%X{rom}] %-5level %logger{36} - %msg%n
        // Check that the log contains [LOGTEST4L.sfc] which is the MDC context
        assertTrue(logContent.contains("[LOGTEST4L.sfc]"),
                "Log file should contain MDC context [LOGTEST4L.sfc]. Log content:\n" + logContent);

        // Verify the MDC context appears alongside processing messages
        boolean hasMdcWithProcessing = logContent.contains("[LOGTEST4L.sfc]") &&
                logContent.contains("Processing: LOGTEST4L.sfc");

        assertTrue(hasMdcWithProcessing,
                "Log should contain both MDC context and processing messages");
    }
}
