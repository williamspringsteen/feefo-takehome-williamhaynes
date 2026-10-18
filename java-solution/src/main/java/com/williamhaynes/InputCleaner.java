package com.williamhaynes;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Turns raw text into the lowercase words used for matching. Stateless, so safe to share between threads.
 * <p>
 * Input is never interpreted, logged or echoed; hostile text is either cleaned or rejected.
 */
final class InputCleaner {

    // Accents (combining marks) and invisible format characters such as zero-width spaces and bidi marks.
    private static final Pattern IGNORED = Pattern.compile("[\\p{M}\\p{Cf}]");
    // Any kind of whitespace, including non-breaking and other Unicode spaces; repeated runs count as one.
    private static final Pattern WHITESPACE = Pattern.compile("[\\p{Z}\\s\\p{javaWhitespace}]+");

    /**
     * Cleans untrusted user input.
     *
     * @param raw the text to clean, may be null
     * @return the words to match, or empty if the input must be rejected: null, too long, containing control
     *         characters other than whitespace, or with too many words
     */
    Optional<List<String>> cleanInput(String raw) {
        if (raw == null || raw.length() > MatchConfig.MAX_INPUT_LENGTH || hasForbiddenControlCharacter(raw)) {
            return Optional.empty();
        }
        List<String> all = split(raw);
        if (all.size() > MatchConfig.MAX_INPUT_WORDS) {
            return Optional.empty();
        }
        return Optional.of(dropStopWords(all));
    }

    /**
     * Cleans a trusted output (job title) the same way as input, but without the length and word limits.
     *
     * @param output the output text
     * @return the words to match; empty if the text has none
     */
    List<String> cleanOutput(String output) {
        return dropStopWords(split(output));
    }

    /** Tabs and newlines count as whitespace; every other control character (NUL, ESC, DEL...) is forbidden. */
    private static boolean hasForbiddenControlCharacter(String text) {
        return text.chars().anyMatch(c -> Character.isISOControl(c) && !Character.isWhitespace(c));
    }

    /**
     * Lowercases, folds compatibility forms (full-width letters, ligatures, non-breaking spaces), removes accents
     * and invisible characters, then splits on whitespace. Lowercasing uses {@link Locale#ROOT} so the default
     * locale (e.g. Turkish) cannot change the result.
     */
    private static List<String> split(String text) {
        String folded = Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        String plain = IGNORED.matcher(Normalizer.normalize(folded, Normalizer.Form.NFD)).replaceAll("");
        return Arrays.stream(WHITESPACE.split(plain)).filter(word -> !word.isEmpty()).toList();
    }

    /** Drops stop words. If everything would be dropped (e.g. "the"), the original words are kept. */
    private static List<String> dropStopWords(List<String> words) {
        List<String> meaningful = words.stream().filter(word -> !MatchConfig.STOP_WORDS.contains(word)).toList();
        return meaningful.isEmpty() ? words : meaningful;
    }
}
