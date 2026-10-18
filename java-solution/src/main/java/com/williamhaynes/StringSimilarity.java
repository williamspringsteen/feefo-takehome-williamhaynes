package com.williamhaynes;

/**
 * A pluggable string-comparison algorithm for typo detection. Every implementation reports on the same
 * normalised scale, so they can be swapped and compared without retuning {@link TypoTolerantSimilarity}.
 */
interface StringSimilarity {

    /** @return similarity from 0 (nothing in common) to 1 (identical). */
    double similarity(String a, String b);
}
