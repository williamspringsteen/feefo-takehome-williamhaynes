package com.williamhaynes;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What every {@link StringSimilarity} must do, run against each implementation (Liskov substitution). */
class StringSimilarityContractTest {

    private static final double DELTA = 1e-9;

    private static final List<StringSimilarity> ALGORITHMS = List.of(
            new LevenshteinSimilarity(), new DamerauLevenshteinSimilarity(), new JaroWinklerSimilarity());

    private static final List<String[]> PAIRS = List.of(
            new String[]{"accountant", "acountant"}, new String[]{"accountant", "acocuntant"},
            new String[]{"architect", "architecture"}, new String[]{"engineer", "engine"},
            new String[]{"plumber", "plimber"}, new String[]{"chat", "chap"}, new String[]{"cat", "dog"},
            new String[]{"a", "abcdef"}, new String[]{"surveyor", "survivor"}, new String[]{"abc", "xyz"});

    static Stream<Arguments> algorithms() {
        return ALGORITHMS.stream().map(a -> Arguments.of(a.getClass().getSimpleName(), a));
    }

    static Stream<Arguments> algorithmsWithPairs() {
        return ALGORITHMS.stream().flatMap(a -> PAIRS.stream()
                .map(p -> Arguments.of(a.getClass().getSimpleName(), a, p[0], p[1])));
    }

    @Nested
    @DisplayName("identity and emptiness")
    class Identity {

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.williamhaynes.StringSimilarityContractTest#algorithms")
        void identicalWordsScoreOne(String name, StringSimilarity algorithm) {
            for (String word : List.of("a", "cat", "architect", "accountant", "x".repeat(50))) {
                assertEquals(1, algorithm.similarity(word, word), DELTA, word);
            }
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.williamhaynes.StringSimilarityContractTest#algorithms")
        void twoEmptyWordsAreIdentical(String name, StringSimilarity algorithm) {
            assertEquals(1, algorithm.similarity("", ""), DELTA);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.williamhaynes.StringSimilarityContractTest#algorithms")
        void anEmptyWordHasNothingInCommonWithAnotherWord(String name, StringSimilarity algorithm) {
            assertEquals(0, algorithm.similarity("", "architect"), DELTA);
            assertEquals(0, algorithm.similarity("architect", ""), DELTA);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.williamhaynes.StringSimilarityContractTest#algorithms")
        void completelyDifferentWordsScoreLow(String name, StringSimilarity algorithm) {
            assertTrue(algorithm.similarity("abc", "xyz") < 0.5);
        }
    }

    @Nested
    @DisplayName("range and symmetry")
    class RangeAndSymmetry {

        @ParameterizedTest(name = "{0}: {2} / {3}")
        @MethodSource("com.williamhaynes.StringSimilarityContractTest#algorithmsWithPairs")
        void scoreIsBetweenZeroAndOne(String name, StringSimilarity algorithm, String a, String b) {
            double score = algorithm.similarity(a, b);
            assertTrue(score >= 0 && score <= 1, "score " + score);
        }

        @ParameterizedTest(name = "{0}: {2} / {3}")
        @MethodSource("com.williamhaynes.StringSimilarityContractTest#algorithmsWithPairs")
        void orderOfTheWordsDoesNotMatter(String name, StringSimilarity algorithm, String a, String b) {
            assertEquals(algorithm.similarity(a, b), algorithm.similarity(b, a), DELTA);
        }

        @ParameterizedTest(name = "{0}: {2} / {3}")
        @MethodSource("com.williamhaynes.StringSimilarityContractTest#algorithmsWithPairs")
        void aWordIsNeverMoreSimilarToAnotherThanToItself(String name, StringSimilarity algorithm, String a, String b) {
            assertTrue(algorithm.similarity(a, b) <= algorithm.similarity(a, a));
        }
    }
}
