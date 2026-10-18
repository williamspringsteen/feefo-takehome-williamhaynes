package com.williamhaynes;

import java.util.Arrays;

/**
 * Which input word (if any) each candidate word was paired with, and how well they matched.
 * Both arrays are indexed by candidate word.
 *
 * @param matchedAt index in the input of the word paired with each candidate word, or -1 if it found no match
 * @param qualities similarity of each pair, or 0 if the candidate word found no match
 */
record WordAlignment(int[] matchedAt, double[] qualities) {

    /** @return the number of words in the candidate */
    int candidateSize() {
        return matchedAt.length;
    }

    /** @return how many candidate words found a match */
    int matchedCount() {
        return (int) Arrays.stream(matchedAt).filter(index -> index >= 0).count();
    }

    /** @return the sum of the match qualities (unmatched words contribute 0) */
    double qualitySum() {
        return Arrays.stream(qualities).sum();
    }
}
