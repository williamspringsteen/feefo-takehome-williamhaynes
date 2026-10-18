package com.williamhaynes;

import java.util.List;
import java.util.Set;

/**
 * Every tunable number and word list used by the matching classes, in one place.
 */
final class MatchConfig {

    private MatchConfig() {
    }

    // ---- Normaliser ----

    /** A best score must be at least this to be returned; 0.5 is inclusive, so one word of a two-word output counts. */
    static final double MIN_SCORE = 0.5;

    /** Filler words dropped before matching (inputs and outputs alike). */
    static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "the", "of", "at", "and", "for", "in", "on", "to");

    /** Inputs longer than this many characters are rejected (as Unknown) before any processing. */
    static final int MAX_INPUT_LENGTH = 400;

    /** Inputs with more than this many words (before stop words are dropped) are rejected (as Unknown). */
    static final int MAX_INPUT_WORDS = 30;

    // ---- StageScorer ----

    /** Score multiplier lost when every pair of matched words is in the wrong order (1 - this at worst). */
    static final double MAX_ORDER_PENALTY = 0.1;

    // ---- TypoTolerantSimilarity ----

    /** Either word being shorter than this must match exactly. */
    static final int MIN_TYPO_WORD_LENGTH = 4;

    /** With (Damerau-)Levenshtein: 1 typo in a 4-7 letter word, 2 in an 8-11 letter word, none in shorter ones. */
    static final double MIN_TYPO_SIMILARITY = 0.75;

    // ---- RelatedWordSimilarity ----

    /** Stems must keep at least this many characters after suffix stripping. */
    static final int MIN_STEM_LENGTH = 5;

    /** A shared prefix must be at least this many characters to relate two words. */
    static final int MIN_PREFIX_LENGTH = 5;

    /** The shared prefix must cover at least this share of the longer word. */
    static final double MIN_PREFIX_SHARE = 0.65;

    /** Related-word matches never score above this. */
    static final double MAX_RELATED_QUALITY = 0.8;

    /** Longer suffixes first, so the longest matching one is stripped. */
    static final List<String> SUFFIXES = List.of(
            "ments", "ment", "ions", "ion", "ings", "ing", "ants", "ant", "ures", "ure", "ed", "es", "s");

    // ---- JaroWinklerSimilarity ----

    /** Only boost words whose Jaro score is at least this. */
    static final double JARO_WINKLER_BOOST_THRESHOLD = 0.7;

    /** How much each shared leading character closes the gap to 1. */
    static final double JARO_WINKLER_PREFIX_SCALE = 0.1;

    /** Only this many leading characters count towards the boost. */
    static final int JARO_WINKLER_MAX_PREFIX = 4;
}
