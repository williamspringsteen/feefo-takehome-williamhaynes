package com.williamhaynes;

import java.util.Arrays;
import java.util.List;

/** Pairs each candidate word with the best remaining input word, according to a {@link WordSimilarity}. */
class WordAligner {

    private final WordSimilarity similarity;

    /** @param similarity decides how well an input word matches a candidate word */
    WordAligner(WordSimilarity similarity) {
        this.similarity = similarity;
    }

    /**
     * Greedy matching: take candidate words in order, pairing each with the best input word not yet used.
     * Order-dependent, so it is not guaranteed to be the best overall pairing when words are similar to each other.
     *
     * @param inputWords     the cleaned input words (or a window of them)
     * @param candidateWords the cleaned words of the candidate output
     * @return which input word each candidate word was paired with, and how well
     */
    WordAlignment align(List<String> inputWords, List<String> candidateWords) {
        int[] matchedAt = new int[candidateWords.size()];
        Arrays.fill(matchedAt, -1);
        double[] qualities = new double[candidateWords.size()];
        // Each input word can satisfy at most one candidate word.
        boolean[] used = new boolean[inputWords.size()];

        for (int i = 0; i < candidateWords.size(); i++) {
            double bestQuality = 0;
            int bestIndex = -1;
            for (int j = 0; j < inputWords.size(); j++) {
                if (used[j]) {
                    continue;
                }
                // 0 means no match; anything above 0 counts, with higher meaning a closer match.
                double quality = similarity.similarity(inputWords.get(j), candidateWords.get(i));
                if (quality > bestQuality) {
                    bestQuality = quality;
                    bestIndex = j;
                }
            }
            if (bestIndex >= 0) {
                used[bestIndex] = true;
                matchedAt[i] = bestIndex;
                qualities[i] = bestQuality;
            }
        }
        return new WordAlignment(matchedAt, qualities);
    }
}
