package com.largomodo.floppyconvert.snes.header;

import com.largomodo.floppyconvert.core.CopierFormat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HeaderGeneratorFactoryTest {

    @Test
    void testCreateSwcGenerator() {
        HeaderGeneratorFactory factory = new HeaderGeneratorFactory();
        HeaderGenerator generator = factory.get(CopierFormat.SWC);
        assertInstanceOf(SwcHeaderGenerator.class, generator);
    }

    @Test
    void testCreateFigGenerator() {
        HeaderGeneratorFactory factory = new HeaderGeneratorFactory();
        HeaderGenerator generator = factory.get(CopierFormat.FIG);
        assertInstanceOf(FigHeaderGenerator.class, generator);
    }

    @Test
    void testCreateUfoGenerator() {
        HeaderGeneratorFactory factory = new HeaderGeneratorFactory();
        HeaderGenerator generator = factory.get(CopierFormat.UFO);
        assertInstanceOf(UfoHeaderGenerator.class, generator);
    }

    @Test
    void testCreateGd3Generator() {
        HeaderGeneratorFactory factory = new HeaderGeneratorFactory();
        HeaderGenerator generator = factory.get(CopierFormat.GD3);
        assertInstanceOf(Gd3HeaderGenerator.class, generator);
    }
}
