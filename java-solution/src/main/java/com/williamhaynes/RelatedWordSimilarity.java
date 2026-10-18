package com.williamhaynes;

/**
 * Like {@link TypoTolerantSimilarity}, but also accepts different forms of the same word, in two ways:
 * <ul>
 *   <li>Same stem, allowing typos in it, once common suffixes are stripped ("accountant" / "accounting",
 *       "developer" / "developing", "acounting" / "accountant").</li>
 *   <li>A long shared prefix that covers most of the longer word ("architect" / "architecture"), scored lower
 *       the more the words differ in length.</li>
 * </ul>
 * Arbitrary substrings do not count, and both rules need at least {@value MatchConfig#MIN_STEM_LENGTH} shared characters,
 * so short fragments ("art" / "architect", "soft" / "software") are not related. Agent endings such as -er and -or
 * are deliberately not stripped, since "engineer" is not the same thing as "engine".
 */
class RelatedWordSimilarity implements WordSimilarity {

    private final WordSimilarity typoTolerant;

    /** Builds a related-word comparison on top of the default typo-tolerant comparison. */
    RelatedWordSimilarity() {
        this(new TypoTolerantSimilarity());
    }

    /** @param typoTolerant the direct (typo-tolerant) comparison, also used on stems */
    RelatedWordSimilarity(WordSimilarity typoTolerant) {
        this.typoTolerant = typoTolerant;
    }

    /**
     * Takes the best of a direct typo-tolerant match, a stem match and a shared-prefix match.
     *
     * @param inputWord     a cleaned input word
     * @param candidateWord a cleaned word of the candidate output
     * @return 0 for no relation; related (non-direct) matches never exceed {@code MAX_RELATED_QUALITY}
     */
    @Override
    public double similarity(String inputWord, String candidateWord) {
        double direct = typoTolerant.similarity(inputWord, candidateWord);
        double related = Math.max(stemSimilarity(inputWord, candidateWord), prefixSimilarity(inputWord, candidateWord));
        return Math.max(direct, related);
    }

    /**
     * Typo-tolerant comparison of the stems, capped like the other related-word rules ("acounting" / "accountant").
     */
    private double stemSimilarity(String a, String b) {
        return MatchConfig.MAX_RELATED_QUALITY * typoTolerant.similarity(stem(a), stem(b));
    }

    /** The more of the longer word the shared prefix covers, the better. */
    private static double prefixSimilarity(String a, String b) {
        int shared = sharedPrefixLength(a, b);
        double share = (double) shared / Math.max(a.length(), b.length());
        if (shared < MatchConfig.MIN_PREFIX_LENGTH || share < MatchConfig.MIN_PREFIX_SHARE) {
            return 0;
        }
        return MatchConfig.MAX_RELATED_QUALITY * share;
    }

    /** Strips one known suffix, as long as at least {@value MatchConfig#MIN_STEM_LENGTH} characters remain. */
    private static String stem(String word) {
        for (String suffix : MatchConfig.SUFFIXES) {
            if (word.endsWith(suffix) && word.length() - suffix.length() >= MatchConfig.MIN_STEM_LENGTH) {
                return word.substring(0, word.length() - suffix.length());
            }
        }
        return word;
    }

    private static int sharedPrefixLength(String a, String b) {
        int limit = Math.min(a.length(), b.length());
        int i = 0;
        while (i < limit && a.charAt(i) == b.charAt(i)) {
            i++;
        }
        return i;
    }
}
