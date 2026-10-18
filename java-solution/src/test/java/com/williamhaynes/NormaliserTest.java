package com.williamhaynes;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NormaliserTest {

    private Normaliser normaliser;

    @BeforeEach
    void setUp() {
        normaliser = new Normaliser();
    }

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource({
            // case and surrounding whitespace
            "'  SOFTWARE Engineer  ', Software engineer",
            "ACCOUNTANT, Accountant",
            // typos: deletion, insertion, substitution, transposition
            "acountant, Accountant",
            "accountantt, Accountant",
            "accountent, Accountant",
            "acocuntant, Accountant",
            "architet, Architect",
            "archtiect, Architect",
            "qantity surveyor, Quantity surveyor",
            "quantity surveyer, Quantity surveyor",
            "sofware engineer, Software engineer",
            "software enginer, Software engineer",
            // word order
            "surveyor quantity, Quantity surveyor",
            "engineer software, Software engineer",
            // phrase inside a longer input
            "senior architect at acme, Architect",
            "chief accountant, Accountant",
            // one word of a multi-word output
            "engineer, Software engineer",
            "surveyor, Quantity surveyor",
            // related forms, with and without typos
            "accounting, Accountant",
            "acounting, Accountant",
            "head of accounts, Accountant",
            "architects, Architect",
            "architecture, Architect",
            "accountants, Accountant",
            "quantity surveying, Quantity surveyor",
            "softwares engineers, Software engineer",
            "software engineering, Software engineer"
    })
    void matchesInputToOutput(String input, String expected) {
        assertEquals(expected, normaliser.normalise(input));
    }

    @ParameterizedTest(name = "\"{0}\" is not a match")
    @ValueSource(strings = {
            "plumber", "   ",
            // share a fragment with an output but are a different word
            "engage", "english", "quantum", "arch", "art", "count", "soft", "super", "partner", "nurse", "actor",
            "analyst",
            "archive", "archives",
            // typo allowance gives only a half-covered 0.35, below the threshold
            "survivor", "engine",
            // related to "engineer" (0.8) but covers half of "Software engineer": 0.4
            "engineering"
    })
    void unmatchedInputIsUnknown(String input) {
        assertEquals("Unknown", normaliser.normalise(input));
    }

    @Test
    void exactMatchBeatsTypoMatch() {
        var custom = new Normaliser(List.of("Accountants", "Accountant"));
        assertEquals("Accountant", custom.normalise("accountant"));
    }

    @Test
    void outputsDifferingOnlyByCaseAreDeduplicatedKeepingTheFirstSpelling() {
        assertEquals("Architect", new Normaliser(List.of("Architect", "architect")).normalise("architect"));
        assertEquals("architect", new Normaliser(List.of("architect", "Architect")).normalise("architect"));
    }

    @Test
    void sharedStopWordAloneIsNotAMatch() {
        var custom = new Normaliser(List.of("Player of games"));
        assertEquals("Unknown", custom.normalise("manager of people"));
    }

    @Test
    void ignoresStopWordsInInputAndKeepsThemInOutput() {
        var custom = new Normaliser(List.of("Head of the Department"));
        assertEquals("Head of the Department", custom.normalise("head department"));
        assertEquals("Head of the Department", custom.normalise("The head of department"));
    }

    @Test
    void stillMatchesWhenTextIsOnlyStopWords() {
        var custom = new Normaliser(List.of("The", "Architect"));
        assertEquals("The", custom.normalise("the"));
    }
}
