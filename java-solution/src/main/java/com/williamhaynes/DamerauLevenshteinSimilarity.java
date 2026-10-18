package com.williamhaynes;

/**
 * Damerau-Levenshtein distance is a well-known algorithm for detecting typos in words. It extends Levenshtein
 * by also counting a swap of two adjacent characters as a single edit, since transposed letters ("acocunt" for
 * "account") are one of the most common typing mistakes. Here it is reported as a similarity,
 * 1 - distance / length of the longer word. This is the "optimal string alignment" variant, where each part of a
 * word is edited at most once; it is the common form of Damerau-Levenshtein.
 */
class DamerauLevenshteinSimilarity implements StringSimilarity {

    /**
     * Compares two words using optimal-string-alignment distance, so a swapped pair of letters is one edit.
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
     * Dynamic programming over a grid where d[i][j] is the edit distance between the first i characters of
     * {@code a} and the first j characters of {@code b}. The whole grid is kept (not just two rows), because the
     * swap rule looks back two rows and two columns.
     */
    private static int editDistance(String a, String b) {
        int[][] d = new int[a.length() + 1][b.length() + 1];
        // Turning a prefix of a into an empty string takes i deletions.
        for (int i = 0; i <= a.length(); i++) {
            d[i][0] = i;
        }
        // Turning an empty string into a prefix of b takes j insertions.
        for (int j = 0; j <= b.length(); j++) {
            d[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                // Matching characters cost nothing; differing ones cost one substitution.
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                // Cheapest of: substitute/match (diagonal), delete from a (above), insert into a (left).
                d[i][j] = Math.min(d[i - 1][j - 1] + cost, Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1));
                // The last two characters of a are the last two of b in reverse order, e.g. "ca" and "ac".
                boolean swapped = i > 1 && j > 1
                        && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1);
                if (swapped) {
                    // Fixing a swap costs one edit, on top of the distance before those two characters.
                    d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
                }
            }
        }
        return d[a.length()][b.length()];
    }
}
