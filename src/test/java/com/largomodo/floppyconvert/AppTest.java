package com.largomodo.floppyconvert;

import com.largomodo.floppyconvert.core.CopierFormat;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for App CLI argument parsing.
 * <p>
 * Focus: Format flag validation, default behavior, and error cases.
 * Does not test full batch processing (covered by integration tests).
 */
class AppTest {

    /**
     * Test --format flag with valid GD3 value.
     * Validates that CopierFormat enum is correctly parsed.
     */
    @Test
    void testParseArgs_ValidFormat_GD3() throws Exception {
        String[] args = {
                "--input-dir", "/test/input",
                "--output-dir", "/test/output",
                "--empty-image", "/test/empty.img",
                "--format", "gd3"
        };

        Object config = invokeParseArgs(args);
        Object format = getConfigField(config, "format");

        assertEquals("GD3", format.toString(), "Format should be GD3");
    }

    /**
     * Test --format flag with valid FIG value (lowercase).
     * Validates case-insensitive parsing.
     */
    @Test
    void testParseArgs_ValidFormat_FIG() throws Exception {
        String[] args = {
                "--input-dir", "/test/input",
                "--output-dir", "/test/output",
                "--empty-image", "/test/empty.img",
                "--format", "fig"
        };

        Object config = invokeParseArgs(args);
        Object format = getConfigField(config, "format");

        assertEquals("FIG", format.toString(), "Format should be FIG");
    }

    /**
     * Test --format flag with valid SWC value (uppercase).
     * Validates case-insensitive parsing.
     */
    @Test
    void testParseArgs_ValidFormat_SWC() throws Exception {
        String[] args = {
                "--input-dir", "/test/input",
                "--output-dir", "/test/output",
                "--empty-image", "/test/empty.img",
                "--format", "SWC"
        };

        Object config = invokeParseArgs(args);
        Object format = getConfigField(config, "format");

        assertEquals("SWC", format.toString(), "Format should be SWC");
    }

    /**
     * Test --format flag with valid UFO value (mixed case).
     * Validates case-insensitive parsing.
     */
    @Test
    void testParseArgs_ValidFormat_UFO() throws Exception {
        String[] args = {
                "--input-dir", "/test/input",
                "--output-dir", "/test/output",
                "--empty-image", "/test/empty.img",
                "--format", "UfO"
        };

        Object config = invokeParseArgs(args);
        Object format = getConfigField(config, "format");

        assertEquals("UFO", format.toString(), "Format should be UFO");
    }

    /**
     * Test default format when --format flag is omitted.
     * Validates backward compatibility (FIG default).
     */
    @Test
    void testParseArgs_DefaultFormat() throws Exception {
        String[] args = {
                "--input-dir", "/test/input",
                "--output-dir", "/test/output",
                "--empty-image", "/test/empty.img"
        };

        Object config = invokeParseArgs(args);
        Object format = getConfigField(config, "format");

        assertEquals("FIG", format.toString(), "Default format should be FIG");
    }

    /**
     * Test --format flag with invalid value.
     * Validates fail-fast validation with clear error message.
     */
    @Test
    void testParseArgs_InvalidFormat() {
        String[] args = {
                "--input-dir", "/test/input",
                "--output-dir", "/test/output",
                "--empty-image", "/test/empty.img",
                "--format", "invalid"
        };

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> invokeParseArgs(args),
                "Should throw IllegalArgumentException for invalid format"
        );

        String message = exception.getMessage();
        assertTrue(message.contains("Invalid format: invalid"),
                "Error message should mention 'Invalid format: invalid', got: " + message);
        assertTrue(message.contains("Supported: FIG, SWC, UFO, GD3"),
                "Error message should list supported formats, got: " + message);
    }

    /**
     * Test --format flag without value (missing argument).
     * Validates early detection of malformed CLI input.
     */
    @Test
    void testParseArgs_MissingFormatValue() {
        String[] args = {
                "--input-dir", "/test/input",
                "--output-dir", "/test/output",
                "--empty-image", "/test/empty.img",
                "--format"
        };

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> invokeParseArgs(args),
                "Should throw IllegalArgumentException when --format lacks value"
        );

        assertEquals("--format requires a value", exception.getMessage(),
                "Error message should indicate missing value");
    }

    /**
     * Test required arguments still required when --format specified.
     * Validates that new optional flag doesn't break existing validation.
     */
    @Test
    void testParseArgs_MissingRequiredArgs_WithFormat() {
        String[] args = {
                "--input-dir", "/test/input",
                "--format", "gd3"
        };

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> invokeParseArgs(args),
                "Should throw IllegalArgumentException when required args missing"
        );

        assertTrue(exception.getMessage().contains("Batch mode") || exception.getMessage().contains("Missing required argument"),
                "Error message should indicate missing required arguments");
    }

    /**
     * Test --format placement (before required args).
     * Validates order-independent argument parsing.
     */
    @Test
    void testParseArgs_FormatBeforeRequiredArgs() throws Exception {
        String[] args = {
                "--format", "swc",
                "--input-dir", "/test/input",
                "--output-dir", "/test/output",
                "--empty-image", "/test/empty.img"
        };

        Object config = invokeParseArgs(args);
        Object format = getConfigField(config, "format");

        assertEquals("SWC", format.toString(), "Format should be SWC regardless of argument order");
    }

    /**
     * Test --format with other optional flags.
     * Validates interaction with existing optional arguments.
     */
    @Test
    void testParseArgs_FormatWithOtherOptionalFlags() throws Exception {
        String[] args = {
                "--input-dir", "/test/input",
                "--output-dir", "/test/output",
                "--empty-image", "/test/empty.img",
                "--ucon64-path", "/custom/ucon64",
                "--format", "ufo",
                "--mtools-path", "/custom/mcopy"
        };

        Object config = invokeParseArgs(args);
        Object format = getConfigField(config, "format");

        assertEquals("UFO", format.toString(), "Format should be UFO");
        assertEquals("/custom/ucon64", getConfigField(config, "ucon64Path"),
                "ucon64Path should be preserved");
        assertEquals("/custom/mcopy", getConfigField(config, "mtoolsPath"),
                "mtoolsPath should be preserved");
    }

    @Test
    void testParseArgs_InputFile_Success() throws Exception {
        String[] args = {"--input-file", "test.sfc", "--empty-image", "template.img"};
        Object config = invokeParseArgs(args);

        assertEquals("test.sfc", getConfigField(config, "inputFile"));
        assertNull(getConfigField(config, "inputDir"));
        assertNull(getConfigField(config, "outputDir"));
        assertEquals("template.img", getConfigField(config, "emptyImage"));
    }

    @Test
    void testParseArgs_MutualExclusivity() {
        String[] args = {
                "--input-dir", "./roms",
                "--input-file", "test.sfc",
                "--output-dir", "./output",
                "--empty-image", "template.img"
        };

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> invokeParseArgs(args));
        assertTrue(e.getMessage().contains("Both --input-dir and --input-file"));
    }

    @Test
    void testParseArgs_MissingInputStrategy() {
        String[] args = {"--empty-image", "template.img"};

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> invokeParseArgs(args));
        assertTrue(e.getMessage().contains("Must provide either"));
    }

    @Test
    void testParseArgs_OutputDirOptionalForInputFile() throws Exception {
        String[] args = {
                "--input-file", "test.sfc",
                "--empty-image", "template.img",
                "--format", "gd3"
        };

        Object config = invokeParseArgs(args);
        assertEquals("test.sfc", getConfigField(config, "inputFile"));
        assertNull(getConfigField(config, "outputDir"));
        assertEquals(CopierFormat.GD3, getConfigField(config, "format"));
    }

    @Test
    void testParseArgs_InputFileWithOutputDir() {
        String[] args = {
                "--input-file", "test.sfc",
                "--output-dir", "./output",
                "--empty-image", "template.img"
        };

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> invokeParseArgs(args));
        assertTrue(e.getMessage().contains("Single-file mode"));
        assertTrue(e.getMessage().contains("does not support --output-dir"));
    }

    // === Reflection Helpers (Access Private Members) ===

    /**
     * Invoke private parseArgs method via reflection.
     * Necessary because parseArgs is package-private.
     * Unwraps InvocationTargetException to expose the actual exception.
     */
    private Object invokeParseArgs(String[] args) throws Exception {
        Method parseArgs = App.class.getDeclaredMethod("parseArgs", String[].class);
        parseArgs.setAccessible(true);
        try {
            return parseArgs.invoke(null, (Object) args);
        } catch (java.lang.reflect.InvocationTargetException e) {
            // Unwrap the actual exception thrown by parseArgs
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            } else if (cause instanceof Exception) {
                throw (Exception) cause;
            } else {
                throw e;
            }
        }
    }

    /**
     * Extract field from Config record via reflection.
     * Necessary because Config is private record.
     */
    private Object getConfigField(Object config, String fieldName) throws Exception {
        Method getter = config.getClass().getDeclaredMethod(fieldName);
        getter.setAccessible(true);
        return getter.invoke(config);
    }
}
