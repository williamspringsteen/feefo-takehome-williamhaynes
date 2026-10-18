package com.williamhaynes;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class WordAlignerTest {

    private static final double DELTA = 1e-9;

    /** Stub similarity: scores listed as "input|candidate" pairs, 0 for everything else. */
    private static WordAligner aligner(Map<String, Double> scores) {
        return new WordAligner((input, candidate) -> scores.getOrDefault(input + "|" + candidate, 0.0));
    }

    @Test
    void pairsEachCandidateWordWithItsMatchingInputWord() {
        WordAlignment alignment = aligner(Map.of("a|x", 1.0, "b|y", 0.8))
                .align(List.of("b", "a"), List.of("x", "y"));

        assertArrayEquals(new int[]{1, 0}, alignment.matchedAt());
        assertArrayEquals(new double[]{1.0, 0.8}, alignment.qualities(), DELTA);
    }

    @Test
    void picksTheBestInputWordNotTheFirstOne() {
        WordAlignment alignment = aligner(Map.of("a|x", 0.6, "b|x", 0.9))
                .align(List.of("a", "b"), List.of("x"));

        assertArrayEquals(new int[]{1}, alignment.matchedAt());
        assertArrayEquals(new double[]{0.9}, alignment.qualities(), DELTA);
    }

    @Test
    void anInputWordSatisfiesAtMostOneCandidateWord() {
        // Both candidate words like the single input word; only the first gets it.
        WordAlignment alignment = aligner(Map.of("a|x", 1.0, "a|y", 1.0))
                .align(List.of("a"), List.of("x", "y"));

        assertArrayEquals(new int[]{0, -1}, alignment.matchedAt());
        assertArrayEquals(new double[]{1.0, 0}, alignment.qualities(), DELTA);
        assertEquals(1, alignment.matchedCount());
    }

    @Test
    void earlierCandidateWordsWinContestedInputWords() {
        // Greedy: x takes a (0.7) even though y would have scored 1.0 with it, leaving y with b.
        WordAlignment alignment = aligner(Map.of("a|x", 0.7, "a|y", 1.0, "b|y", 0.5))
                .align(List.of("a", "b"), List.of("x", "y"));

        assertArrayEquals(new int[]{0, 1}, alignment.matchedAt());
        assertArrayEquals(new double[]{0.7, 0.5}, alignment.qualities(), DELTA);
    }

    @Test
    void reportsNoMatchWhenNothingIsSimilar() {
        WordAlignment alignment = aligner(Map.of()).align(List.of("a", "b"), List.of("x", "y"));

        assertArrayEquals(new int[]{-1, -1}, alignment.matchedAt());
        assertEquals(0, alignment.matchedCount());
        assertEquals(0, alignment.qualitySum(), DELTA);
    }
}
