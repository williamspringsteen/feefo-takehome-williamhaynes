package com.williamhaynes;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScoreFactorsTest {

    private static final double DELTA = 1e-9;
    private static final double PENALTY = MatchConfig.MAX_ORDER_PENALTY;

    private static Arguments row(int[] matchedAt, double... values) {
        return Arguments.of(matchedAt, values);
    }

    /** (matchedAt, qualities, coverage, quality) */
    static Stream<Arguments> coverageAndQuality() {
        return Stream.of(
                Arguments.of(new int[]{0, 1}, new double[]{1, 1}, 1.0, 1.0),
                Arguments.of(new int[]{0, -1}, new double[]{1, 0}, 0.5, 1.0),
                Arguments.of(new int[]{-1, -1}, new double[]{0, 0}, 0.0, 0.0),
                // the unmatched word must not drag quality down: that is coverage's job
                Arguments.of(new int[]{0, -1, 1}, new double[]{1, 0, 0.8}, 2.0 / 3, 0.9),
                Arguments.of(new int[]{3}, new double[]{0.6}, 1.0, 0.6));
    }

    /** (matchedAt, expected order adjustment) */
    static Stream<Arguments> orderAdjustments() {
        return Stream.of(
                // in candidate order, gaps in the input do not matter
                Arguments.of(new int[]{0, 1, 2}, 1.0),
                Arguments.of(new int[]{0, 3, 7}, 1.0),
                // fewer than two matches: nothing to judge
                Arguments.of(new int[]{2}, 1.0),
                Arguments.of(new int[]{-1, 0}, 1.0),
                Arguments.of(new int[]{-1, -1}, 1.0),
                // fully reversed: the maximum penalty
                Arguments.of(new int[]{1, 0}, 1 - PENALTY),
                Arguments.of(new int[]{2, 1, 0}, 1 - PENALTY),
                // one swap out of three pairs: a proportional penalty
                Arguments.of(new int[]{1, 0, 2}, 1 - PENALTY / 3),
                // unmatched words are ignored when judging order
                Arguments.of(new int[]{0, -1, 1}, 1.0),
                Arguments.of(new int[]{1, -1, 0}, 1 - PENALTY));
    }

    @ParameterizedTest
    @MethodSource("coverageAndQuality")
    void coverageIsTheShareOfCandidateWordsMatched(int[] matchedAt, double[] qualities, double coverage, double ignored) {
        assertEquals(coverage, ScoreFactors.coverage(new WordAlignment(matchedAt, qualities)), DELTA);
    }

    @ParameterizedTest
    @MethodSource("coverageAndQuality")
    void qualityAveragesOverMatchedWordsOnly(int[] matchedAt, double[] qualities, double ignored, double quality) {
        assertEquals(quality, ScoreFactors.quality(new WordAlignment(matchedAt, qualities)), DELTA);
    }

    @ParameterizedTest
    @MethodSource("orderAdjustments")
    void orderPenaltyGrowsWithTheShareOfSwappedPairs(int[] matchedAt, double expected) {
        double[] qualities = new double[matchedAt.length];
        java.util.Arrays.fill(qualities, 1);
        assertEquals(expected, ScoreFactors.orderAdjustment(new WordAlignment(matchedAt, qualities)), DELTA);
    }
}
