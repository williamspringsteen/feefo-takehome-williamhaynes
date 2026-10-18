package com.williamhaynes;

/**
 * Exact matches score 1. Words of up to 3 letters allow no typos. Otherwise the plugged-in
 * {@link StringSimilarity} decides how close two words are, and anything below the minimum similarity is not a
 * match; a match scores its similarity, so the penalty scales with the share of the word that is wrong. Because every algorithm reports on the same
 * 0 to 1 scale, the same cut-off applies to all of them.
 */
class TypoTolerantSimilarity implements WordSimilarity {

    private final StringSimilarity algorithm;
    private final double minSimilarity;

    /** Uses Damerau-Levenshtein and the default cut-off. */
    TypoTolerantSimilarity() {
        this(new DamerauLevenshteinSimilarity());
    }

    /** @param algorithm the typo algorithm; the default cut-off applies */
    TypoTolerantSimilarity(StringSimilarity algorithm) {
        this(algorithm, MatchConfig.MIN_TYPO_SIMILARITY);
    }

    /**
     * @param algorithm     the typo algorithm
     * @param minSimilarity the similarity (0 to 1) below which two words do not match
     */
    TypoTolerantSimilarity(StringSimilarity algorithm, double minSimilarity) {
        this.algorithm = algorithm;
        this.minSimilarity = minSimilarity;
    }

    /**
     * @param inputWord     a cleaned input word
     * @param candidateWord a cleaned word of the candidate output
     * @return 1 for identical words, 0 if either is shorter than {@code MIN_TYPO_WORD_LENGTH} (and not identical) or
     *         the similarity is under the cut-off, otherwise the algorithm's similarity
     */
    @Override
    public double similarity(String inputWord, String candidateWord) {
        if (inputWord.equals(candidateWord)) {
            return 1;
        }
        if (Math.min(inputWord.length(), candidateWord.length()) < MatchConfig.MIN_TYPO_WORD_LENGTH) {
            return 0;
        }
        double similarity = algorithm.similarity(inputWord, candidateWord);
        return similarity >= minSimilarity ? similarity : 0;
    }
}
