package com.williamhaynes;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

import static com.williamhaynes.TestText.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InputCleanerTest {

    private final InputCleaner cleaner = new InputCleaner();

    private List<String> words(String raw) {
        return cleaner.cleanInput(raw).orElseThrow();
    }

    static Stream<String> whitespaceKinds() {
        return Stream.of(NBSP, IDEOGRAPHIC_SPACE, EM_SPACE, THIN_SPACE, "\t", "\n", "\r\n", " " + NBSP + "\t ");
    }

    static Stream<String> forbiddenControlCharacters() {
        return Stream.of(NUL, BEL, ESC, DEL, NEL, ESC + "[31m");
    }

    @Test
    void nullIsRejected() {
        assertEquals(Optional.empty(), cleaner.cleanInput(null));
    }

    @Test
    void lowercasesAndSplitsOnWhitespace() {
        assertEquals(List.of("software", "engineer"), words("  SOFTWARE   Engineer "));
    }

    @Test
    void blankInputHasNoWords() {
        assertEquals(List.of(), words(""));
        assertEquals(List.of(), words(" \t\n "));
    }

    @ParameterizedTest
    @MethodSource("whitespaceKinds")
    void anyKindOfWhitespaceSeparatesWords(String whitespace) {
        assertEquals(List.of("x", "y"), words("x" + whitespace + "y"));
    }

    @Test
    void removesAccentsAndFoldsFullWidthLetters() {
        assertEquals(List.of("cafe"), words("caf" + E_ACUTE));
        assertEquals(List.of("cafe"), words("cafe" + COMBINING_ACUTE));
        assertEquals(List.of("architect"), words(fullWidth("ARCHITECT")));
    }

    @Test
    void removesZeroWidthAndBidiFormatCharacters() {
        assertEquals(List.of("architect"), words("arch" + ZERO_WIDTH_SPACE + "itect"));
        assertEquals(List.of("architect"), words(RIGHT_TO_LEFT_MARK + "architect" + LEFT_TO_RIGHT_MARK));
        assertEquals(List.of("architect"), words("arch" + ZERO_WIDTH_JOINER + "itect" + BYTE_ORDER_MARK));
    }

    @ParameterizedTest
    @MethodSource("forbiddenControlCharacters")
    void controlCharactersOtherThanWhitespaceAreRejected(String control) {
        assertEquals(Optional.empty(), cleaner.cleanInput("a" + control + "b"));
        assertEquals(Optional.empty(), cleaner.cleanInput(control));
    }

    @Test
    void inputOverTheLengthLimitIsRejectedAndAtTheLimitIsNot() {
        assertTrue(cleaner.cleanInput("a".repeat(MatchConfig.MAX_INPUT_LENGTH)).isPresent());
        assertEquals(Optional.empty(), cleaner.cleanInput("a".repeat(MatchConfig.MAX_INPUT_LENGTH + 1)));
    }

    @Test
    void inputOverTheWordLimitIsRejectedAndAtTheLimitIsNot() {
        assertTrue(cleaner.cleanInput("x ".repeat(MatchConfig.MAX_INPUT_WORDS)).isPresent());
        assertEquals(Optional.empty(), cleaner.cleanInput("x ".repeat(MatchConfig.MAX_INPUT_WORDS + 1)));
    }

    @Test
    void punctuationAndSymbolsStayPartOfTheWord() {
        assertEquals(List.of("c++", "developer"), words("C++ Developer"));
        assertEquals(List.of("(software)", "engineer"), words("(Software) engineer"));
        assertEquals(List.of("software", "engineer", "-", "l5"), words("Software Engineer - L5"));
    }

    @Test
    void stopWordsAreDroppedUnlessNothingElseIsLeft() {
        assertEquals(List.of("head", "department"), words("head of the department"));
        assertEquals(List.of("the", "of"), words("the of"));
    }

    @Test
    void unpairedSurrogatesAndEmojiDoNotThrow() {
        assertEquals(1, words(LONE_HIGH_SURROGATE).size());
        assertEquals(1, words(LONE_LOW_SURROGATE).size());
        assertEquals(2, words("architect " + GRINNING_EMOJI).size());
    }

    @Test
    void turkishDefaultLocaleDoesNotChangeLowercasing() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));
            assertEquals(List.of("architect"), words("ARCHITECT"));
            assertEquals(List.of("iii"), words("III"));
            assertEquals(List.of("istanbul"), words(DOTTED_CAPITAL_I + "stanbul"));
        } finally {
            Locale.setDefault(original);
        }
    }
}
