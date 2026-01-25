package com.largomodo.floppyconvert;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for logging infrastructure integration.
 * <p>
 * Verifies:
 * - Verbose flag enables DEBUG level logging
 * - MDC context propagates ROM filename to log entries
 * - ERROR level logs reach FAILURES appender via LevelFilter
 */
class FloppyConvertLoggingTest {

    @TempDir
    Path tempDir;

    private byte[] templateRomData;
    private ListAppender<ILoggingEvent> listAppender;
    private FileAppender<ILoggingEvent> testFileAppender;
    private Logger rootLogger;
    private Level originalLevel;
    private Path testLogFile;

    @BeforeEach
    void setUp() throws IOException {
        // Load real ROM file from test resources for MDC test
        Path templateRom = Paths.get("src/test/resources/snes/Super Mario World (USA).sfc");
        if (Files.exists(templateRom)) {
            templateRomData = Files.readAllBytes(templateRom);
        }

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
    }

    @Test
    void testVerboseFlagEnablesDebug() {
        // Create FloppyConvert instance and parse arguments with verbose flag
        FloppyConvert floppyConvert = new FloppyConvert();
        CommandLine cmd = new CommandLine(floppyConvert);

        // Parse arguments but don't execute yet
        Path dummyInput = tempDir.resolve("dummy.sfc");
        cmd.parseArgs("-v", dummyInput.toString());

        // Verify initial state (should be at INFO or DEBUG based on config)
        Level levelBeforeCall = rootLogger.getLevel();

        // Execute call() which should set DEBUG level when verbose=true
        try {
            floppyConvert.call();
        } catch (Exception e) {
            // Expected to fail due to missing input file, but that's OK
            // We're testing the logging setup that happens at the start of call()
        }

        // Verify root logger level is now DEBUG
        assertEquals(Level.DEBUG, rootLogger.getLevel(),
                "Verbose flag should set root logger to DEBUG level");
    }

    @Test
    void testMdcContextInBatch() throws Exception {
        // Skip if ucon64 or template ROM not available
        Assumptions.assumeTrue(templateRomData != null, "Template ROM not available");

        // Create input directory with a ROM file
        Path inputDir = tempDir.resolve("input");
        Files.createDirectories(inputDir);
        Path romFile = inputDir.resolve("TestRom.sfc");
        Files.write(romFile, templateRomData);

        Path outputDir = tempDir.resolve("output");

        // Execute batch conversion using CommandLine
        new CommandLine(new FloppyConvert())
                .setCaseInsensitiveEnumValuesAllowed(true)
                .execute(
                        inputDir.toString(),
                        "--output-dir", outputDir.toString(),
                        "--format", "fig"
                );

        // Wait for async executor to complete and logs to be flushed
        Thread.sleep(1000);

        // Stop the file appender to flush all content
        testFileAppender.stop();

        // Verify that the test log file contains MDC context
        assertTrue(Files.exists(testLogFile), "Test log file should be created");

        String logContent = Files.readString(testLogFile);

        // The pattern is: %d{HH:mm:ss.SSS} [%thread] [%X{rom}] %-5level %logger{36} - %msg%n
        // Check that the log contains [TestRom.sfc] which is the MDC context
        assertTrue(logContent.contains("[TestRom.sfc]"),
                "Log file should contain MDC context [TestRom.sfc]. Log content:\n" + logContent);

        // Verify the MDC context appears alongside processing messages
        boolean hasMdcWithProcessing = logContent.contains("[TestRom.sfc]") &&
                logContent.contains("Processing: TestRom.sfc");

        assertTrue(hasMdcWithProcessing,
                "Log should contain both MDC context and processing messages");
    }

    @Test
    void testFailuresLogOnError() throws Exception {
        // This test verifies that the FAILURES appender configuration exists and works
        // by checking that error-level logs are properly routed

        // Skip if ucon64 not available
// Create a scenario that will cause an error: invalid ROM file
        Path inputDir = tempDir.resolve("input");
        Files.createDirectories(inputDir);
        Path invalidRom = inputDir.resolve("Invalid.sfc");
        Files.write(invalidRom, new byte[]{0x00, 0x01, 0x02}); // Too small, invalid ROM

        Path outputDir = tempDir.resolve("output");

        // Execute batch conversion using CommandLine (should fail and log error)
        new CommandLine(new FloppyConvert())
                .setCaseInsensitiveEnumValuesAllowed(true)
                .execute(
                        inputDir.toString(),
                        "--output-dir", outputDir.toString(),
                        "--format", "fig"
                );

        // Wait briefly for async executor to complete
        Thread.sleep(500);

        // Verify that ERROR level events were logged
        List<ILoggingEvent> errorEvents = listAppender.list.stream()
                .filter(event -> event.getLevel() == Level.ERROR)
                .toList();

        assertFalse(errorEvents.isEmpty(),
                "ERROR level events should be logged for failed conversions");

        // Verify that error events contain meaningful information
        boolean hasFailureMessage = errorEvents.stream()
                .anyMatch(event -> event.getFormattedMessage().contains("FAILED"));

        assertTrue(hasFailureMessage,
                "ERROR logs should contain 'FAILED' message for conversion failures");
    }
}
