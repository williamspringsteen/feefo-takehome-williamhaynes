package com.williamhaynes;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static com.williamhaynes.MatchStage.EXACT_PHRASE;
import static com.williamhaynes.MatchStage.NONE;
import static com.williamhaynes.MatchStage.RELATED_WORD;
import static com.williamhaynes.MatchStage.WORD;
import static org.junit.jupiter.api.Assertions.assertEquals;

class StageScoreTest {

    private static Arguments row(StageScore a, StageScore b, int expectedSign) {
        return Arguments.of(a, b, expectedSign);
    }

    /** (a, b, sign of BEST_LAST.compare(a, b)): positive means a is the better score. */
    static Stream<Arguments> comparisons() {
        return Stream.of(
                // equal value: lower stage priority number wins, for every pair of real stages
                row(new StageScore(0.5, EXACT_PHRASE), new StageScore(0.5, WORD), 1),
                row(new StageScore(0.5, WORD), new StageScore(0.5, RELATED_WORD), 1),
                row(new StageScore(0.5, EXACT_PHRASE), new StageScore(0.5, RELATED_WORD), 1),
                row(new StageScore(0.5, WORD), new StageScore(0.5, EXACT_PHRASE), -1),
                row(new StageScore(0.5, RELATED_WORD), new StageScore(0.5, WORD), -1),
                row(new StageScore(0.5, RELATED_WORD), new StageScore(0.5, EXACT_PHRASE), -1),
                // equal value and stage is a full tie
                row(new StageScore(0.5, WORD), new StageScore(0.5, WORD), 0),
                row(new StageScore(0.0, NONE), new StageScore(0.0, NONE), 0),
                // a real stage beats the NONE marker at the same value
                row(new StageScore(0.5, RELATED_WORD), new StageScore(0.5, NONE), 1),
                // value always beats stage
                row(new StageScore(0.8, RELATED_WORD), new StageScore(0.7, EXACT_PHRASE), 1),
                row(new StageScore(0.7, EXACT_PHRASE), new StageScore(0.8, RELATED_WORD), -1),
                row(new StageScore(0.01, EXACT_PHRASE), new StageScore(0.0, NONE), 1));
    }

    @ParameterizedTest(name = "{0} vs {1} -> {2}")
    @MethodSource("comparisons")
    void bestLastOrdersByValueThenStagePriority(StageScore a, StageScore b, int expectedSign) {
        assertEquals(expectedSign, Integer.signum(StageScore.BEST_LAST.compare(a, b)));
    }

    @ParameterizedTest(name = "{0} vs {1} is antisymmetric")
    @MethodSource("comparisons")
    void comparatorIsAntisymmetric(StageScore a, StageScore b, int expectedSign) {
        assertEquals(-expectedSign, Integer.signum(StageScore.BEST_LAST.compare(b, a)));
    }
}
