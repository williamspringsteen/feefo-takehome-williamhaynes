package com.williamhaynes;

/**
 * Jaro-Winkler is a well-known algorithm for detecting typos in words, designed for short strings such as names.
 * Jaro similarity looks at how many characters the two words share (within a limited distance of each other)
 * and how many of those are out of order; Winkler's addition boosts the score when the words start with the
 * same characters, since typos tend to appear later in a word. The result is already on a 0 to 1 scale.
 */
class JaroWinklerSimilarity implements StringSimilarity {

    /**
     * Compares two words using Jaro similarity plus the Winkler shared-prefix boost.
     *
     * @param a the first (already cleaned) word
     * @param b the second (already cleaned) word
     * @return similarity from 0 (nothing in common) to 1 (identical)
     */
    @Override
    public double similarity(String a, String b) {
        if (a.equals(b)) {
            return 1;
        }
        double jaro = jaro(a, b);
        if (jaro < MatchConfig.JARO_WINKLER_BOOST_THRESHOLD) {
            return jaro;
        }
        // Count the leading characters the words share, up to the maximum prefix.
        int prefix = 0;
        int limit = Math.min(MatchConfig.JARO_WINKLER_MAX_PREFIX, Math.min(a.length(), b.length()));
        while (prefix < limit && a.charAt(prefix) == b.charAt(prefix)) {
            prefix++;
        }
        // Move the Jaro score a fraction of the way towards 1 for each shared leading character.
        return jaro + prefix * MatchConfig.JARO_WINKLER_PREFIX_SCALE * (1 - jaro);
    }

    /**
     * Jaro similarity: the average of three ratios, namely matched characters as a share of each word and
     * matched characters that are in the right order as a share of all matches.
     */
    private static double jaro(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        // Two characters only count as a match if they are no further apart than this.
        int window = Math.max(Math.max(a.length(), b.length()) / 2 - 1, 0);
        boolean[] aMatched = new boolean[a.length()];
        boolean[] bMatched = new boolean[b.length()];
        int matches = 0;
        // For each character of a, find the first unused equal character of b within the window.
        for (int i = 0; i < a.length(); i++) {
            int from = Math.max(0, i - window);
            int to = Math.min(b.length() - 1, i + window);
            for (int j = from; j <= to; j++) {
                if (!bMatched[j] && a.charAt(i) == b.charAt(j)) {
                    aMatched[i] = true;
                    bMatched[j] = true;
                    matches++;
                    break;
                }
            }
        }
        if (matches == 0) {
            return 0;
        }
        // Walk the matched characters of both words in order; each pair that differs is out of order.
        // Half of the out-of-order pairs count as transpositions.
        int outOfOrder = 0;
        int j = 0;
        for (int i = 0; i < a.length(); i++) {
            if (!aMatched[i]) {
                continue;
            }
            // Skip to the next matched character of b.
            while (!bMatched[j]) {
                j++;
            }
            if (a.charAt(i) != b.charAt(j)) {
                outOfOrder++;
            }
            j++;
        }
        double transpositions = outOfOrder / 2.0;
        return ((double) matches / a.length() + (double) matches / b.length()
                + (matches - transpositions) / matches) / 3;
    }
}
