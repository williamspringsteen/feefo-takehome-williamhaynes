package com.williamhaynes;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Each stage on its own: (input words, candidate words) and the range the score must fall in. */
class AlignmentStageScorerTest {

    private static final WordSimilarity TYPO_TOLERANT = new TypoTolerantSimilarity();
    private static final WordSimilarity RELATED = new RelatedWordSimilarity(TYPO_TOLERANT);

    private static List<String> w(String words) {
        return List.of(words.split(" "));
    }

    private static Arguments row(String input, String candidate, double min, double max) {
        return Arguments.of(w(input), w(candidate), min, max);
    }

    static Stream<Arguments> exactPhrase() {
        return Stream.of(
                row("architect", "architect", 1.0, 1.0),
                row("senior architect at", "architect", 1.0, 1.0),
                row("quantity surveyor", "quantity surveyor", 1.0, 1.0),
                // reordered words lose only the order penalty
                row("surveyor quantity", "quantity surveyor", 0.9, 0.9),
                // typo: one wrong letter in ten
                row("acountant", "accountant", 0.9, 0.9),
                // words must sit together: a window of two never holds both, so only half is covered
                row("software xx engineer", "software engineer", 0.5, 0.5),
                // input shorter than the candidate: the whole input is the window
                row("engineer", "software engineer", 0.5, 0.5),
                row("plumber", "architect", 0, 0),
                // a shared stem is not a phrase match (3 typos in 11 letters is over the allowance)
                row("engineering", "engineer", 0, 0));
    }

    static Stream<Arguments> word() {
        return Stream.of(
                row("architect", "architect", 1.0, 1.0),
                // words matched anywhere in the input
                row("software xx engineer", "software engineer", 1.0, 1.0),
                row("surveyor quantity", "quantity surveyor", 0.9, 0.9),
                row("acountant", "accountant", 0.9, 0.9),
                row("engineer", "software engineer", 0.5, 0.5),
                row("plumber", "architect", 0, 0),
                row("engineering", "engineer", 0, 0));
    }

    static Stream<Arguments> relatedWord() {
        return Stream.of(
                row("architect", "architect", 1.0, 1.0),
                row("engineering", "engineer", 0.8, 0.8), // same stem once -ing is stripped
                row("acounting", "accountant", 0.6, 0.8),
                row("architecture", "architect", 0.65, 0.8),
                row("software xx engineering", "software engineer", 0.8, 0.95),
                row("plumber", "architect", 0, 0),
                row("archive", "architect", 0, 0));
    }

    private static void assertScore(AlignmentStageScorer scorer, List<String> input, List<String> candidate,
                                    double min, double max) {
        double score = scorer.score(input, candidate);
        assertTrue(score >= min - 1e-9 && score <= max + 1e-9,
                input + " vs " + candidate + ": " + score + " not in [" + min + ", " + max + "]");
    }

    @ParameterizedTest(name = "{0} vs {1}: {2}..{3}")
    @MethodSource("exactPhrase")
    void exactPhraseStage(List<String> input, List<String> candidate, double min, double max) {
        assertScore(new AlignmentStageScorer(MatchStage.EXACT_PHRASE, TYPO_TOLERANT, true), input, candidate, min, max);
    }

    @ParameterizedTest(name = "{0} vs {1}: {2}..{3}")
    @MethodSource("word")
    void wordStage(List<String> input, List<String> candidate, double min, double max) {
        assertScore(new AlignmentStageScorer(MatchStage.WORD, TYPO_TOLERANT, false), input, candidate, min, max);
    }

    @ParameterizedTest(name = "{0} vs {1}: {2}..{3}")
    @MethodSource("relatedWord")
    void relatedWordStage(List<String> input, List<String> candidate, double min, double max) {
        assertScore(new AlignmentStageScorer(MatchStage.RELATED_WORD, RELATED, false), input, candidate, min, max);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("emptySides")
    void emptyInputOrCandidateScoresZero(List<String> input, List<String> candidate) {
        assertScore(new AlignmentStageScorer(MatchStage.WORD, TYPO_TOLERANT, false), input, candidate, 0, 0);
    }

    static Stream<Arguments> emptySides() {
        return Stream.of(
                Arguments.of(List.of(), List.of("architect")),
                Arguments.of(List.of("architect"), List.of()),
                Arguments.of(List.of(), List.of()));
    }

    @org.junit.jupiter.api.Test
    void reportsTheStageItWasBuiltFor() {
        for (MatchStage stage : List.of(MatchStage.EXACT_PHRASE, MatchStage.WORD, MatchStage.RELATED_WORD)) {
            org.junit.jupiter.api.Assertions.assertEquals(stage,
                    new AlignmentStageScorer(stage, TYPO_TOLERANT, false).stage());
        }
    }

    @org.junit.jupiter.api.Test
    void aWindowOnlyHoldsAsManyWordsAsTheCandidate() {
        WordSimilarity exact = (inputWord, candidateWord) -> inputWord.equals(candidateWord) ? 1 : 0;
        List<String> input = w("a x y b");
        List<String> candidate = w("a b");
        // "a" and "b" are three words apart, so no two-word window holds both; without a window they are found.
        org.junit.jupiter.api.Assertions.assertEquals(0.5,
                new AlignmentStageScorer(MatchStage.EXACT_PHRASE, exact, true).score(input, candidate), 1e-9);
        org.junit.jupiter.api.Assertions.assertEquals(1.0,
                new AlignmentStageScorer(MatchStage.WORD, exact, false).score(input, candidate), 1e-9);
    }

    @org.junit.jupiter.api.Test
    void inputNoLongerThanTheCandidateIsScoredAsASingleWindow() {
        int[] calls = {0};
        WordSimilarity counting = (inputWord, candidateWord) -> {
            calls[0]++;
            return 0;
        };
        new AlignmentStageScorer(MatchStage.EXACT_PHRASE, counting, true).score(w("a b"), w("a b"));
        org.junit.jupiter.api.Assertions.assertEquals(4, calls[0]);
    }
}
