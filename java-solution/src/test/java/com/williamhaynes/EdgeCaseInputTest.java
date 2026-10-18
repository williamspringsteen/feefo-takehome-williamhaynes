package com.williamhaynes;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static com.williamhaynes.TestText.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/** Edge cases and hostile input, one nested group per TODO.md section 4 bullet. */
class EdgeCaseInputTest {

    private static final String UNKNOWN = "Unknown";
    private static final Duration TIME_LIMIT = Duration.ofSeconds(2);

    private final Normaliser normaliser = new Normaliser();

    @Nested
    @DisplayName("null, empty and whitespace-only input")
    class NullAndBlank {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "   ", "\t", "\n", " \t\r\n "})
        void isUnknown(String input) {
            assertEquals(UNKNOWN, normaliser.normalise(input));
        }

        @Test
        void unicodeWhitespaceOnlyIsUnknown() {
            assertEquals(UNKNOWN, normaliser.normalise(NBSP));
            assertEquals(UNKNOWN, normaliser.normalise(IDEOGRAPHIC_SPACE + EM_SPACE + THIN_SPACE));
        }
    }

    @Nested
    @DisplayName("very long input")
    class VeryLongInput {

        @Test
        void tooManyCharactersIsUnknownEvenIfItStartsWithAMatch() {
            String input = "architect " + "x".repeat(MatchConfig.MAX_INPUT_LENGTH);
            assertEquals(UNKNOWN, normaliser.normalise(input));
        }

        @Test
        void exactlyTheCharacterLimitIsStillProcessed() {
            String input = "architect" + " ".repeat(MatchConfig.MAX_INPUT_LENGTH - "architect".length());
            assertEquals(MatchConfig.MAX_INPUT_LENGTH, input.length());
            assertEquals("Architect", normaliser.normalise(input));
        }

        @Test
        void tooManyWordsIsUnknown() {
            String input = "architect " + "x ".repeat(MatchConfig.MAX_INPUT_WORDS);
            assertEquals(UNKNOWN, normaliser.normalise(input));
        }

        @Test
        void exactlyTheWordLimitIsStillProcessed() {
            String input = "architect " + "x ".repeat(MatchConfig.MAX_INPUT_WORDS - 1);
            assertEquals("Architect", normaliser.normalise(input));
        }

        @Test
        void megabytesOfTextIsRejectedQuicklyWithoutError() {
            String manyWords = "architect ".repeat(1_000_000);
            assertTimeoutPreemptively(TIME_LIMIT, () -> assertEquals(UNKNOWN, normaliser.normalise(manyWords)));
            String oneHugeWord = "a".repeat(20_000_000);
            assertTimeoutPreemptively(TIME_LIMIT, () -> assertEquals(UNKNOWN, normaliser.normalise(oneHugeWord)));
        }
    }

    @Nested
    @DisplayName("unicode")
    class UnicodeInput {

        static Stream<String> spellingsOfArchitect() {
            return Stream.of(
                    "ARCHITECT",
                    "archit" + E_ACUTE + "ct",
                    "archite" + COMBINING_ACUTE + "ct",
                    fullWidth("architect"),
                    "arch" + ZERO_WIDTH_SPACE + "itect",
                    RIGHT_TO_LEFT_MARK + "a" + ZERO_WIDTH_JOINER + "rchitect" + BYTE_ORDER_MARK);
        }

        static Stream<String> oddInputsThatDoNotMatch() {
            return Stream.of(
                    ARABIC, HEBREW,
                    GRINNING_EMOJI + GRINNING_EMOJI + " " + WORKER_EMOJI,
                    LONE_HIGH_SURROGATE, LONE_LOW_SURROGATE, LONE_HIGH_SURROGATE + LONE_HIGH_SURROGATE,
                    "STRASSE", "Stra" + SHARP_S + "e",
                    GREEK_SIGMAS_UPPER, GREEK_SIGMAS_LOWER,
                    DOTTED_CAPITAL_I + "stanbul");
        }

        @ParameterizedTest
        @MethodSource("spellingsOfArchitect")
        void accentsFullWidthAndInvisibleCharactersAreNormalisedAway(String input) {
            assertEquals("Architect", normaliser.normalise(input));
        }

        @ParameterizedTest
        @MethodSource("oddInputsThatDoNotMatch")
        void otherScriptsAndOddCodePointsNeverThrowAndDoNotMatch(String input) {
            assertEquals(UNKNOWN, assertDoesNotThrow(() -> normaliser.normalise(input)));
        }

        @Test
        void anUnpairedSurrogateAttachedToAWordIsJustATypo() {
            assertEquals("Architect", normaliser.normalise("architect" + LONE_LOW_SURROGATE));
        }

        @Test
        void emojiAroundAMatchDoesNotStopIt() {
            assertEquals("Architect", normaliser.normalise(GRINNING_EMOJI + " architect " + WORKER_EMOJI));
        }

        @Test
        void outputsAreCleanedTheSameWayAsInput() {
            var custom = new Normaliser(List.of("Caf" + E_ACUTE + " manager"));
            assertEquals("Caf" + E_ACUTE + " manager", custom.normalise("CAFE MANAGER"));
        }
    }

    @Nested
    @DisplayName("whitespace")
    class Whitespace {

        static Stream<String> separators() {
            return Stream.of(" ", NBSP, IDEOGRAPHIC_SPACE, EM_SPACE, THIN_SPACE, "\t", "\n", "\r\n", "      ",
                    " " + NBSP + " \t ");
        }

        @ParameterizedTest
        @MethodSource("separators")
        void anyKindAndAmountOfWhitespaceSeparatesWords(String separator) {
            assertEquals("Software engineer", normaliser.normalise("software" + separator + "engineer"));
            assertEquals("Software engineer", normaliser.normalise(separator + "software engineer" + separator));
        }
    }

    @Nested
    @DisplayName("punctuation and symbols")
    class Punctuation {

        @ParameterizedTest
        @ValueSource(strings = {"Accountant 2", "Accountant II", "Accountant, ", "accountant/", "Accountant (contract)"})
        void extraSymbolsAndDigitsAroundAMatchDoNotStopIt(String input) {
            assertEquals("Accountant", normaliser.normalise(input));
        }

        @Test
        void symbolsAreKeptInTheWordAndSoCountAsATypo() {
            // "(software)" is "software" plus two characters: within the typo allowance for a 10 letter word.
            assertEquals("Software engineer", normaliser.normalise("(Software) engineer"));
            assertEquals("Software engineer", normaliser.normalise("Software Engineer - L5"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"12345", "C++ Developer", "&", "---", "!!!", "O'Brien"})
        void symbolsAndDigitsAloneDoNotMatch(String input) {
            assertEquals(UNKNOWN, normaliser.normalise(input));
        }
    }

    @Nested
    @DisplayName("control characters")
    class ControlCharacters {

        static Stream<String> controlCharacters() {
            return Stream.of(NUL, BEL, ESC, DEL, NEL, ESC + "[31m");
        }

        @ParameterizedTest
        @MethodSource("controlCharacters")
        void nulEscapeAndOtherControlCharactersAreRejectedAnywhereInTheInput(String control) {
            assertEquals(UNKNOWN, normaliser.normalise("architect" + control));
            assertEquals(UNKNOWN, normaliser.normalise(control + "architect"));
            assertEquals(UNKNOWN, normaliser.normalise("arch" + control + "itect"));
            assertEquals(UNKNOWN, normaliser.normalise(ESC + "[31marchitect" + ESC + "[0m"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"architect\t", "\tarchitect", "architect\n", "architect\r\n", "\narchitect\n"})
        void tabsAndNewlinesAreJustWhitespace(String input) {
            assertEquals("Architect", normaliser.normalise(input));
        }

        @Test
        void tabsAndNewlinesSeparateWords() {
            assertEquals("Software engineer", normaliser.normalise("software\tengineer"));
            assertEquals("Software engineer", normaliser.normalise("software\nengineer"));
        }
    }

    @Nested
    @DisplayName("injection-style strings")
    class Injection {

        @ParameterizedTest
        @ValueSource(strings = {
                "'; DROP TABLE users; --", "\" OR \"1\"=\"1", "<script>alert(1)</script>",
                "%s %n %d %1$s", "${jndi:ldap://evil/a}", "{{7*7}}", ".*", "(a+)+$", "[a-z", "\\Q", "^$", "\\", "$1",
                "../../etc/passwd"})
        void arePlainNonMatchesAndNeverThrow(String input) {
            assertEquals(UNKNOWN, assertDoesNotThrow(() -> normaliser.normalise(input)));
        }

        @Test
        void aMatchingWordWithInjectionAroundItStillMatchesLikeAnyOtherNoise() {
            // The text is compared as plain characters: "architect;" is one stray character away from "architect",
            // and the rest is extra input words, which cost nothing (like "senior architect at acme").
            assertEquals("Architect", normaliser.normalise("architect;"));
            assertEquals("Architect", normaliser.normalise("architect; DROP TABLE users; --"));
        }
    }

    @Nested
    @DisplayName("denial of service")
    class DenialOfService {

        @Test
        void maximumSizeInputsFinishQuickly() {
            String longestWords = "accountan ".repeat(MatchConfig.MAX_INPUT_WORDS).trim();
            String oneWordAtTheLimit = "a".repeat(MatchConfig.MAX_INPUT_LENGTH);
            String nearMisses = "acocuntant ".repeat(MatchConfig.MAX_INPUT_WORDS).trim();
            for (String input : List.of(longestWords, oneWordAtTheLimit, nearMisses)) {
                assertTimeoutPreemptively(TIME_LIMIT, () -> normaliser.normalise(input));
            }
        }

        @Test
        void maximumSizeInputWithAHugeOutputListFinishesQuickly() {
            List<String> outputs = new ArrayList<>();
            for (int i = 0; i < 300; i++) {
                outputs.add("Specialist" + (char) ('a' + i % 26) + (char) ('a' + i / 26 % 26) + " manager");
            }
            Normaliser big = new Normaliser(outputs);
            String input = "specialistaa manager ".repeat(MatchConfig.MAX_INPUT_WORDS / 2).trim();
            assertTimeoutPreemptively(Duration.ofSeconds(10), () -> big.normalise(input));
        }
    }

    @Nested
    @DisplayName("repeated words")
    class RepeatedWords {

        @Test
        void repeatingAMatchingWordDoesNotScoreAboveASingleMatch() {
            assertEquals("Architect", normaliser.normalise("architect"));
            assertEquals("Architect", normaliser.normalise("architect architect architect"));
        }

        @Test
        void repeatedWordsDoNotSatisfyMoreThanOneOutputWord() {
            // "engineer" repeated covers only half of "Software engineer" (0.5), but all of "Engineer" (1.0).
            var custom = new Normaliser(List.of("Software engineer", "Engineer"));
            assertEquals("Engineer", custom.normalise("engineer engineer engineer"));
        }

        @Test
        void repeatedWordsInAnOutputNeedMatchingRepeatsInTheInput() {
            var custom = new Normaliser(List.of("Bora bora"));
            assertEquals("Bora bora", custom.normalise("bora bora"));
            assertEquals("Bora bora", custom.normalise("bora")); // half covered: exactly the threshold
        }
    }

    @Nested
    @DisplayName("ties and equally good outputs")
    class Ties {

        @Test
        void twoDifferentOutputsEquallyGoodOnScoreAndStageIsUnknown() {
            assertEquals(UNKNOWN, new Normaliser(List.of("Data analyst", "Business analyst")).normalise("analyst"));
        }

        @Test
        void tieIsUnknownWhicheverOrderTheOutputsAreListed() {
            assertEquals(UNKNOWN, new Normaliser(List.of("Business analyst", "Data analyst")).normalise("analyst"));
        }

        @Test
        void aTieIsBrokenByAStrictlyBetterLaterOutput() {
            var custom = new Normaliser(List.of("Data analyst", "Business analyst", "Analyst"));
            assertEquals("Analyst", custom.normalise("analyst"));
        }

        @Test
        void aTieIsBrokenByAStrictlyBetterEarlierOutput() {
            var custom = new Normaliser(List.of("Analyst", "Data analyst", "Business analyst"));
            assertEquals("Analyst", custom.normalise("analyst"));
        }

        @Test
        void sameScoreDecidedByTheBetterStageWinsInsteadOfBeingATie() {
            // For "accounts", "Accountant" scores 0.8 only through the related-word stage (shared stem), and
            // "Accountsxx" scores 0.8 through the earlier phrase stage (two typos in ten letters).
            assertEquals("Accountsxx", new Normaliser(List.of("Accountant", "Accountsxx")).normalise("accounts"));
            assertEquals("Accountsxx", new Normaliser(List.of("Accountsxx", "Accountant")).normalise("accounts"));
        }

        @Test
        void duplicateOutputsAreMergedSoTheyDoNotTieWithThemselves() {
            var custom = new Normaliser(List.of("Architect", "Architect", "ARCHITECT", "Accountant"));
            assertEquals("Architect", custom.normalise("architect"));
        }

        @Test
        void aLoneNoMatchIsStillUnknownNotATie() {
            assertEquals(UNKNOWN, new Normaliser(List.of("Data analyst", "Business analyst")).normalise("plumber"));
        }
    }

    @Nested
    @DisplayName("output list edge cases")
    class OutputList {

        @Test
        void emptyOrNullListIsRejected() {
            assertThrows(IllegalArgumentException.class, () -> new Normaliser(List.of()));
            assertThrows(IllegalArgumentException.class, () -> new Normaliser((List<String>) null));
        }

        @Test
        void nullBlankOrWordlessEntriesAreRejected() {
            assertThrows(IllegalArgumentException.class, () -> new Normaliser(Arrays.asList("Architect", null)));
            assertThrows(IllegalArgumentException.class, () -> new Normaliser(List.of("Architect", "")));
            assertThrows(IllegalArgumentException.class, () -> new Normaliser(List.of("Architect", "   ")));
            assertThrows(IllegalArgumentException.class,
                    () -> new Normaliser(List.of("Architect", ZERO_WIDTH_SPACE)));
        }

        @Test
        void aSingleEntryWorks() {
            var custom = new Normaliser(List.of("Plumber"));
            assertEquals("Plumber", custom.normalise("plumber"));
            assertEquals(UNKNOWN, custom.normalise("architect"));
        }

        @Test
        void entriesDifferingOnlyByCaseKeepTheFirstSpelling() {
            assertEquals("Chief Architect",
                    new Normaliser(List.of("Chief Architect", "chief architect")).normalise("CHIEF ARCHITECT"));
            assertEquals("chief architect",
                    new Normaliser(List.of("chief architect", "Chief Architect")).normalise("CHIEF ARCHITECT"));
        }

        @Test
        void callersCannotChangeTheOutputsAfterConstruction() {
            List<String> outputs = new ArrayList<>(List.of("Architect"));
            var custom = new Normaliser(outputs);
            outputs.add("Plumber");
            assertEquals(UNKNOWN, custom.normalise("plumber"));
        }
    }

    @Nested
    @DisplayName("case and locale")
    class LocaleIndependence {

        private final Locale original = Locale.getDefault();

        @AfterEach
        void restoreLocale() {
            Locale.setDefault(original);
        }

        @ParameterizedTest
        @ValueSource(strings = {"tr", "az", "lt", "de", "el"})
        void resultsDoNotDependOnTheDefaultLocale(String language) {
            Locale.setDefault(Locale.forLanguageTag(language));
            var custom = new Normaliser(List.of("Illustrator", "Accountant"));
            assertEquals("Illustrator", custom.normalise("ILLUSTRATOR"));
            assertEquals("Illustrator", custom.normalise("illustrator"));
            assertEquals("Accountant", custom.normalise("ACCOUNTANT"));
            assertEquals("Software engineer", new Normaliser().normalise("SOFTWARE ENGINEER"));
            assertEquals("Quantity surveyor", new Normaliser().normalise("QUANTITY SURVEYOR"));
        }

        @Test
        void upperAndLowerCaseGiveTheSameAnswer() {
            for (String input : List.of("software engineer", "quantity surveyor", "acountant", "plumber")) {
                assertEquals(normaliser.normalise(input), normaliser.normalise(input.toUpperCase(Locale.ROOT)));
            }
            assertNotEquals(UNKNOWN, normaliser.normalise("ARCHITECT"));
        }
    }
}
