package com.williamhaynes;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RelatedWordSimilarityTest {

    private final WordSimilarity similarity = new RelatedWordSimilarity();

    @ParameterizedTest
    @CsvSource({
            "architect, architecture",
            "accountant, accounting",
            "accounts, accountant",
            "developer, developing",
            "engineer, engineering",
            "manager, management",
            "acounting, accountant",
            "architect, architects",
            "surveyor, surveying",
            "quantity, quantities",
            "developer, development"
    })
    void relatedForms(String a, String b) {
        assertTrue(similarity.similarity(a, b) > 0, a + " / " + b);
        assertTrue(similarity.similarity(a, b) < 1, a + " / " + b);
        assertEquals(similarity.similarity(a, b), similarity.similarity(b, a), a + " / " + b);
    }

    @ParameterizedTest
    @CsvSource({
            "art, architect",
            "ant, accountant",
            "soft, software",
            "engineer, developer",
            "manager, player",
            // unrelated words sharing a prefix
            "engineer, engage",
            "engineer, english",
            "account, accord",
            "survey, surgeon",
            "manager, manual",
            // short words inside longer ones
            "count, accountant",
            "eng, engineer",
            "tect, architect",
            "sur, surveyor",
            // look-alike roles
            "actor, actuary"
    })
    void unrelatedWords(String a, String b) {
        assertEquals(0, similarity.similarity(a, b), a + " / " + b);
        assertEquals(0, similarity.similarity(b, a), a + " / " + b);
    }

    @ParameterizedTest(name = "{0} / {1} -> {2}")
    @CsvSource({
            // shared stem once a suffix is stripped: capped at MAX_RELATED_QUALITY
            "engineering, engineer, 0.8",
            "architecture, architect, 0.8",
            // one typo is already a direct match (0.9), better than the cap, so it wins
            "architects, architect, 0.9",
            // different stems that still share most of the longer word: scaled below the cap
            "developing, developer, 0.62",
            // typo in the stem still related, scaled by the cap
            "acounting, accountant, 0.72",
            // exact words are not scaled down
            "architect, architect, 1.0"
    })
    void relatedScoresScaleAndNeverExceedTheCap(String a, String b, double expected) {
        assertEquals(expected, similarity.similarity(a, b), 0.05, a + " / " + b);
    }

    @ParameterizedTest(name = "{0} / {1}: only {2} shared letters")
    @CsvSource({
            "arch, architect, 4",       // shorter than the minimum prefix
            "archi, architect, 5",      // long enough but covers 5/9 of the longer word, under the share minimum
            "archive, architect, 5"
    })
    void aPrefixMustBeLongEnoughAndCoverEnoughOfTheLongerWord(String a, String b, int shared) {
        assertEquals(0, similarity.similarity(a, b));
        assertEquals(shared, commonPrefix(a, b));
    }

    @Test
    void theLongestMatchingSuffixIsStripped() {
        WordSimilarity typoTolerant = mock(WordSimilarity.class);
        // "managements" loses -ments (not just -s), leaving the same stem as "manage".
        when(typoTolerant.similarity("manage", "manage")).thenReturn(1.0);
        assertEquals(0.8, new RelatedWordSimilarity(typoTolerant).similarity("managements", "manage"), 1e-9);
    }

    @ParameterizedTest(name = "{0} / {1} score like plain typos, with no stem boost")
    @CsvSource({"engineer, engine", "developer, develop", "manager, manage", "surveyor, survey"})
    void agentEndingsAreNotStripped(String agent, String base) {
        // Stripping -er/-or would make these the same stem and score 0.8, however far apart the typo score is.
        assertEquals(new TypoTolerantSimilarity().similarity(agent, base), similarity.similarity(agent, base), 1e-9);
    }

    @Test
    void aStemMustKeepEnoughLettersAfterStripping() {
        WordSimilarity typoTolerant = mock(WordSimilarity.class);
        // Stripping would leave "team" (4 letters, under the minimum of 5), so the words stay whole.
        when(typoTolerant.similarity("team", "team")).thenReturn(1.0);
        assertEquals(0, new RelatedWordSimilarity(typoTolerant).similarity("teaming", "teams"), 1e-9);
    }

    @Test
    void usesTheInjectedTypoToleranceForBothDirectComparisonAndStems() {
        WordSimilarity typoTolerant = mock(WordSimilarity.class);
        var related = new RelatedWordSimilarity(typoTolerant);
        when(typoTolerant.similarity("zzzzzz", "yyyyyy")).thenReturn(0.9);
        // The direct similarity is passed through unscaled.
        assertEquals(0.9, related.similarity("zzzzzz", "yyyyyy"), 1e-9);
        // Stems "accountant"/"account" come from stripping -ant and -ing; the mock rates stems at 0.5 -> 0.4.
        when(typoTolerant.similarity("account", "account")).thenReturn(1.0);
        assertEquals(0.8, related.similarity("accounting", "accounts"), 1e-9);
    }

    @Test
    void takesTheBetterOfDirectAndRelatedScores() {
        WordSimilarity typoTolerant = mock(WordSimilarity.class);
        when(typoTolerant.similarity("engineering", "engineer")).thenReturn(0.95);
        when(typoTolerant.similarity("engineer", "engineer")).thenReturn(1.0);
        // direct 0.95 beats the related cap of 0.8
        assertEquals(0.95, new RelatedWordSimilarity(typoTolerant).similarity("engineering", "engineer"), 1e-9);
    }

    private static int commonPrefix(String a, String b) {
        int i = 0;
        while (i < Math.min(a.length(), b.length()) && a.charAt(i) == b.charAt(i)) {
            i++;
        }
        return i;
    }
}
