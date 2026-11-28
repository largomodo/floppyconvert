package com.largomodo.floppyconvert.core;

/**
 * Maps a conversion failure to a {@link FailureCategory}.
 * <p>
 * core must not import snes or service concretes, so the mapping lives behind this interface and is wired at
 * the composition root. Wrapping snes exceptions in a core exception would change the exception types that
 * single-file callers and tests rely on, which is why the exceptions are classified rather than wrapped.
 */
@FunctionalInterface
public interface FailureClassifier {

    /**
     * Classifier for callers without domain knowledge: every failure is OTHER.
     */
    FailureClassifier ALWAYS_OTHER = e -> FailureCategory.OTHER;

    /**
     * @return the category token logged for {@code e}
     */
    FailureCategory classify(Exception e);
}
