package com.williamhaynes;

/** The three factors of a stage score (q = C * S * O), each computed from a {@link WordAlignment}. */
final class ScoreFactors {

    private ScoreFactors() {
    }

    /**
     * C: share of candidate words matched. Unmatched input words are ignored.
     *
     * @param alignment the pairing of candidate words to input words
     * @return matched candidate words divided by all candidate words, 0 to 1
     */
    static double coverage(WordAlignment alignment) {
        return (double) alignment.matchedCount() / alignment.candidateSize();
    }

    /**
     * S: mean similarity over the matched words only.
     *
     * @param alignment the pairing of candidate words to input words
     * @return the average match quality, 0 to 1; 0 when nothing matched
     */
    static double quality(WordAlignment alignment) {
        int matched = alignment.matchedCount();
        return matched == 0 ? 0 : alignment.qualitySum() / matched;
    }

    /**
     * O: 1 when matched words appear in candidate order, falling towards {@code 1 - MAX_ORDER_PENALTY} as more
     * pairs are swapped. Only pairs where both words matched are compared.
     *
     * @param alignment the pairing of candidate words to input words
     * @return the order multiplier, from {@code 1 - MAX_ORDER_PENALTY} to 1; 1 with fewer than two matched words
     */
    static double orderAdjustment(WordAlignment alignment) {
        int[] matchedAt = alignment.matchedAt();
        int pairs = 0;
        int inversions = 0;
        // Compare every pair of candidate words (i before k). If both matched, the pair is in order when
        // i's input position is also before k's; otherwise it is an inversion.
        for (int i = 0; i < matchedAt.length; i++) {
            for (int k = i + 1; k < matchedAt.length; k++) {
                if (matchedAt[i] < 0 || matchedAt[k] < 0) {
                    continue; // unmatched words say nothing about order
                }
                pairs++;
                if (matchedAt[i] > matchedAt[k]) {
                    inversions++;
                }
            }
        }
        // Fewer than two matched words: no order to judge, so no penalty.
        if (pairs == 0) {
            return 1;
        }
        return 1 - MatchConfig.MAX_ORDER_PENALTY * inversions / pairs;
    }
}
