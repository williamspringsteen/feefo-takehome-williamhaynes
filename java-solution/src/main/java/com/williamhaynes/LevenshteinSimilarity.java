package com.williamhaynes;

/**
 * Levenshtein distance is a well-known algorithm for detecting typos in words: it counts the fewest single-character
 * edits (insert, delete or substitute) needed to turn one word into the other. Here it is reported as a similarity,
 * 1 - distance / length of the longer word, so 1 means identical and 0 means nothing in common.
 */
class LevenshteinSimilarity implements StringSimilarity {

    /**
     * Compares two words using Levenshtein distance, where a swapped pair of letters costs two edits.
     *
     * @param a the first (already cleaned) word
     * @param b the second (already cleaned) word
     * @return similarity from 0 (nothing in common) to 1 (identical); two empty words are identical
     */
    @Override
    public double similarity(String a, String b) {
        int longest = Math.max(a.length(), b.length());
        // Two empty strings are identical (and would otherwise divide by zero below).
        if (longest == 0) {
            return 1;
        }
        // Scale by the longer word so one typo costs less in a long word than in a short one.
        return 1 - (double) editDistance(a, b) / longest;
    }

    /**
     * Dynamic programming over a grid where cell (i, j) is the edit distance between the first i characters of
     * {@code a} and the first j characters of {@code b}. Only the previous row is needed to compute the next,
     * so two rows are kept instead of the whole grid.
     */
    private static int editDistance(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        // Row 0: turning an empty string into the first j characters of b takes j insertions.
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            // Column 0: turning the first i characters of a into an empty string takes i deletions.
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                // Matching characters cost nothing; differing ones cost one substitution.
                int substitution = previous[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
                // Take the cheapest of: substitute/match, delete from a (previous[j]), insert into a (current[j - 1]).
                current[j] = Math.min(substitution, Math.min(previous[j] + 1, current[j - 1] + 1));
            }
            // Reuse the two arrays: the row just computed becomes the previous row.
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        // After the last swap, "previous" holds the final row; its last cell is the full distance.
        return previous[b.length()];
    }
}
