package com.williamhaynes;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TypoTolerantSimilarityTest {

    private static final double DELTA = 1e-9;

    private final WordSimilarity similarity = new TypoTolerantSimilarity();

    @ParameterizedTest
    @CsvSource({"it", "cat", "chat", "architect", "accountant"})
    void exactMatchScoresOneAtAnyLength(String word) {
        assertEquals(1, similarity.similarity(word, word));
    }

    @ParameterizedTest(name = "{0} / {1}: a word of 3 letters or fewer allows no typos")
    @CsvSource({
            "cat, cut",
            "cat, chat", // would clear the cut-off if length were ignored
            "chat, cat",
            "it, is",
            "a, b"
    })
    void wordsOfThreeLettersOrFewerAllowNoTypos(String a, String b) {
        assertEquals(0, similarity.similarity(a, b));
    }

    @ParameterizedTest(name = "{0} / {1} -> {2}")
    @CsvSource({
            "chat, chap, 0.75", // one typo in four letters is exactly the cut-off
            "chat, chop, 0",    // two typos in four letters is below it
            "plumber, plimber, 0.857142857",
            "accountant, acountant, 0.9",
            "accountant, acocuntant, 0.9", // a transposition counts as a single typo
            "accountant, accountaxx, 0.8",
            "accountant, accountxxx, 0"    // three typos in ten letters is below the cut-off
    })
    void typoScoreIsTheShareOfTheLongerWordThatIsRight(String a, String b, double expected) {
        assertEquals(expected, similarity.similarity(a, b), 1e-6);
    }

    @ParameterizedTest(name = "{0} / {1}")
    @CsvSource({
            "chat, chap", "plumber, plimber", "accountant, acountant", "accountant, acocuntant",
            "chat, chop", "cat, chat", "architect, architecture", "engineer, engine", "a, abcdef"
    })
    void isSymmetricAndWithinZeroAndOne(String a, String b) {
        double forward = similarity.similarity(a, b);
        assertEquals(forward, similarity.similarity(b, a), DELTA);
        assertTrue(forward >= 0 && forward <= 1, "score " + forward);
    }

    @ParameterizedTest(name = "{0}, {1}, {2}: either word below the minimum length must match exactly")
    @CsvSource({"abc, abd", "abcd, abc", "abc, abcd"})
    void shortWordsNeverMatchLooselyWhicheverSideIsShort(String a, String b) {
        assertEquals(0, similarity.similarity(a, b));
    }

    @ParameterizedTest(name = "{0} / {1}")
    @CsvSource({"chat, chap", "plumber, plimber", "accountant, acountant"})
    void penaltyShrinksAsTheWordGetsLonger(String shorter, String shorterTypo) {
        double penalty = 1 - similarity.similarity(shorter, shorterTypo);
        double longerPenalty = 1 - similarity.similarity("accountant", "acountant");
        assertTrue(penalty >= longerPenalty - DELTA, penalty + " >= " + longerPenalty);
    }

    @ParameterizedTest(name = "algorithm says {0}, cut-off {1} -> {2}")
    @CsvSource({
            "0.9, 0.75, 0.9",   // above the cut-off: the algorithm's own score
            "0.75, 0.75, 0.75", // exactly at the cut-off counts
            "0.74, 0.75, 0",    // just below is no match
            "0.5, 0.4, 0.5",    // a lower cut-off lets weaker matches through
            "0.5, 0.6, 0"       // a higher one rejects them
    })
    void usesThePluggedAlgorithmAndCutOff(double algorithmScore, double minSimilarity, double expected) {
        StringSimilarity algorithm = mock(StringSimilarity.class);
        when(algorithm.similarity("abcdef", "abcdxx")).thenReturn(algorithmScore);
        var custom = new TypoTolerantSimilarity(algorithm, minSimilarity);
        assertEquals(expected, custom.similarity("abcdef", "abcdxx"), DELTA);
    }

    @Test
    void doesNotAskTheAlgorithmForIdenticalOrTooShortWords() {
        StringSimilarity algorithm = mock(StringSimilarity.class);
        var custom = new TypoTolerantSimilarity(algorithm, 0.1);
        custom.similarity("architect", "architect");
        custom.similarity("cat", "cut");
        custom.similarity("abc", "abcdef");
        verify(algorithm, never()).similarity(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void asksTheAlgorithmForLongEnoughWords() {
        StringSimilarity algorithm = mock(StringSimilarity.class);
        new TypoTolerantSimilarity(algorithm, 0.1).similarity("abcd", "abce");
        verify(algorithm).similarity("abcd", "abce");
    }
}
