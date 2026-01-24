package com.largomodo.floppyconvert.snes.header;

import com.largomodo.floppyconvert.core.CopierFormat;

/**
 * Factory for creating format-specific HeaderGenerators.
 */
public class HeaderGeneratorFactory {

    private final SwcHeaderGenerator swc = new SwcHeaderGenerator();
    private final FigHeaderGenerator fig = new FigHeaderGenerator();
    private final UfoHeaderGenerator ufo = new UfoHeaderGenerator();
    private final Gd3HeaderGenerator gd3 = new Gd3HeaderGenerator();

    public HeaderGenerator get(CopierFormat format) {
        return switch (format) {
            case FIG -> fig;
            case SWC -> swc;
            case UFO -> ufo;
            case GD3 -> gd3;
        };
    }
}