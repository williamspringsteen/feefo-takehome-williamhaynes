package com.williamhaynes;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests {@link Normaliser}'s ranking and tie-breaking with mocked stage scorers returning fixed scores, so the
 * results do not depend on the real scoring numbers.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NormaliserRankingTest {

    private static final String UNKNOWN = "Unknown";

    // Outputs "Alpha beta", "Gamma delta" and "Epsilon zeta" are cleaned to these word lists.
    private static final List<String> ALPHA = List.of("alpha", "beta");
    private static final List<String> GAMMA = List.of("gamma", "delta");
    private static final List<String> EPSILON = List.of("epsilon", "zeta");
    private static final List<String> INPUT = List.of("some", "input");

    private static final List<String> OUTPUTS = List.of("Alpha beta", "Gamma delta", "Epsilon zeta");

    @Mock
    private StageScorer phrase;
    @Mock
    private StageScorer word;
    @Mock
    private StageScorer related;

    private Normaliser normaliser;

    @BeforeEach
    void setUp() {
        when(phrase.stage()).thenReturn(MatchStage.EXACT_PHRASE);
        when(word.stage()).thenReturn(MatchStage.WORD);
        when(related.stage()).thenReturn(MatchStage.RELATED_WORD);
        normaliser = new Normaliser(OUTPUTS, List.of(phrase, word, related));
    }

    private static void score(StageScorer stage, List<String> output, double value) {
        when(stage.score(INPUT, output)).thenReturn(value);
    }

    @Nested
    @DisplayName("ranking")
    class Ranking {

        @Test
        void highestScoreWins() {
            score(word, ALPHA, 0.6);
            score(word, GAMMA, 0.9);
            score(word, EPSILON, 0.7);
            assertEquals("Gamma delta", normaliser.normalise("some input"));
        }

        @Test
        void anOutputIsScoredByItsBestStage() {
            score(phrase, ALPHA, 0.6);
            score(related, ALPHA, 0.95);
            score(word, GAMMA, 0.9);
            assertEquals("Alpha beta", normaliser.normalise("some input"));
        }

        @Test
        void aHigherScoreBeatsABetterStage() {
            score(phrase, ALPHA, 0.7);
            score(related, GAMMA, 0.8);
            assertEquals("Gamma delta", normaliser.normalise("some input"));
        }

        @Test
        void aBetterScoreLaterInTheListBeatsAnEarlierOne() {
            score(word, ALPHA, 0.6);
            score(word, EPSILON, 0.61);
            assertEquals("Epsilon zeta", normaliser.normalise("some input"));
        }

        @Test
        void theOutputIsReturnedInItsOriginalCasing() {
            var custom = new Normaliser(List.of("ALPHA Beta"), List.of(word));
            when(word.score(INPUT, ALPHA)).thenReturn(1.0);
            assertEquals("ALPHA Beta", custom.normalise("SOME Input"));
        }
    }

    @Nested
    @DisplayName("threshold")
    class Threshold {

        @Test
        void allZeroScoresReturnUnknown() {
            assertEquals(UNKNOWN, normaliser.normalise("some input"));
        }

        @Test
        void aScoreJustBelowTheMinimumReturnsUnknown() {
            score(word, ALPHA, Math.nextDown(MatchConfig.MIN_SCORE));
            assertEquals(UNKNOWN, normaliser.normalise("some input"));
        }

        @Test
        void aScoreExactlyAtTheMinimumIsAMatch() {
            score(word, ALPHA, MatchConfig.MIN_SCORE);
            assertEquals("Alpha beta", normaliser.normalise("some input"));
        }
    }

    @Nested
    @DisplayName("ties")
    class Ties {

        @Test
        void sameScoreFromDifferentStagesIsBrokenByStagePriority() {
            score(related, ALPHA, 0.8);
            score(phrase, GAMMA, 0.8);
            score(word, EPSILON, 0.8);
            assertEquals("Gamma delta", normaliser.normalise("some input"));
        }

        @Test
        void stagePriorityDecidesBetweenEveryPairOfStages() {
            score(word, ALPHA, 0.8);
            score(related, GAMMA, 0.8);
            assertEquals("Alpha beta", normaliser.normalise("some input"));

            var reversed = new Normaliser(List.of("Gamma delta", "Alpha beta"), List.of(phrase, word, related));
            assertEquals("Alpha beta", reversed.normalise("some input"));
        }

        @Test
        void sameScoreAndSameStageIsUnknown() {
            score(word, ALPHA, 0.8);
            score(word, GAMMA, 0.8);
            assertEquals(UNKNOWN, normaliser.normalise("some input"));
        }

        @Test
        void aTieBetweenTheTwoBestIsStillUnknownWhenAWorseOutputFollows() {
            score(word, ALPHA, 0.8);
            score(word, GAMMA, 0.8);
            score(word, EPSILON, 0.6);
            assertEquals(UNKNOWN, normaliser.normalise("some input"));
        }

        @Test
        void aTiedPairIsBeatenByABetterOutputListedAfterThem() {
            score(word, ALPHA, 0.8);
            score(word, GAMMA, 0.8);
            score(phrase, EPSILON, 0.8);
            assertEquals("Epsilon zeta", normaliser.normalise("some input"));
        }

        @Test
        void aTieBelowTheMinimumScoreDoesNotMatterBecauseNothingMatches() {
            score(word, ALPHA, 0.3);
            score(word, GAMMA, 0.3);
            assertEquals(UNKNOWN, normaliser.normalise("some input"));
        }
    }

    @Nested
    @DisplayName("collaboration")
    class Collaboration {

        @Test
        void everyStageIsAskedAboutEveryOutputWithTheCleanedWords() {
            normaliser.normalise("Some INPUT");

            for (StageScorer stage : List.of(phrase, word, related)) {
                verify(stage).score(INPUT, ALPHA);
                verify(stage).score(INPUT, GAMMA);
                verify(stage).score(INPUT, EPSILON);
                verify(stage, times(3)).score(anyList(), anyList());
            }
        }

        @Test
        void stagesAreNotCalledForRejectedInput() {
            normaliser.normalise(null);
            normaliser.normalise("a".repeat(MatchConfig.MAX_INPUT_LENGTH + 1));
            normaliser.normalise("bad\u0000input");
            normaliser.normalise("   ");
            verify(phrase, never()).score(anyList(), anyList());
            verify(word, never()).score(anyList(), anyList());
            verify(related, never()).score(anyList(), anyList());
        }

        @Test
        void duplicateOutputsAreScoredOnce() {
            var custom = new Normaliser(List.of("Alpha beta", "ALPHA BETA"), List.of(word));
            custom.normalise("some input");
            verify(word, times(1)).score(any(), any());
        }

        @ParameterizedTest
        @ValueSource(ints = {0})
        void constructorRejectsNoStages(int ignored) {
            assertThrows(IllegalArgumentException.class, () -> new Normaliser(OUTPUTS, List.of()));
            assertThrows(IllegalArgumentException.class, () -> new Normaliser(OUTPUTS, (List<StageScorer>) null));
        }

        @Test
        void nothingIsCalledWhenTheOutputListIsInvalid() {
            assertThrows(IllegalArgumentException.class, () -> new Normaliser(List.of(), List.of(word)));
            verifyNoInteractions(word);
        }
    }
}
