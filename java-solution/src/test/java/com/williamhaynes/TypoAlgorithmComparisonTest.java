package com.williamhaynes;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the same cases through each typo algorithm, all on the shared 0-1 scale and the same cut-off, and
 * prints a scoreboard so they can be compared. To try another algorithm, implement {@link StringSimilarity}
 * and add it to {@link #ALGORITHMS}; to try another cut-off, change {@link MatchConfig#MIN_TYPO_SIMILARITY}.
 * Only the baseline cases (the original Levenshtein behaviour) are asserted; the rest are for comparison.
 */
class TypoAlgorithmComparisonTest {

    private static final List<String> DEFAULT_OUTPUTS =
            List.of("Software engineer", "Architect", "Quantity surveyor", "Accountant");

    private static final Map<String, StringSimilarity> ALGORITHMS = new LinkedHashMap<>();

    static {
        ALGORITHMS.put("Levenshtein", new LevenshteinSimilarity());
        ALGORITHMS.put("Damerau-Levenshtein", new DamerauLevenshteinSimilarity());
        ALGORITHMS.put("Jaro-Winkler", new JaroWinklerSimilarity());
    }

    private record Case(String name, List<String> outputs, String input, String expected, boolean baseline) {}

    private static Case baseline(String input, String expected) {
        return new Case(input, DEFAULT_OUTPUTS, input, expected, true);
    }

    private static Case extra(String input, String expected) {
        return new Case(input, DEFAULT_OUTPUTS, input, expected, false);
    }

    private static Case custom(List<String> outputs, String input, String expected) {
        return new Case(input + " vs " + outputs, outputs, input, expected, false);
    }

    private static final List<Case> CASES = List.of(
            // 1. Exact matches and ordinary title variations
            baseline("Software engineer", "Software engineer"),
            baseline("Architect", "Architect"),
            baseline("Quantity surveyor", "Quantity surveyor"),
            baseline("Accountant", "Accountant"),
            extra("software Engineer", "Software engineer"),
            extra("SOFTWARE ENGINEER", "Software engineer"),
            extra("accountant", "Accountant"),
            extra("  architect  ", "Architect"),
            extra("quantity   surveyor", "Quantity surveyor"),

            // 2. Missing letters (deletions)
            baseline("acountant", "Accountant"),
            extra("accoutant", "Accountant"),
            extra("accountan", "Accountant"),
            extra("archtect", "Architect"),
            extra("architec", "Architect"),
            extra("sofware engineer", "Software engineer"),
            extra("softwar engineer", "Software engineer"),
            extra("software enginer", "Software engineer"),
            extra("qantity surveyor", "Quantity surveyor"),
            extra("quantity survyor", "Quantity surveyor"),
            extra("quantity surveyer", "Quantity surveyor"),

            // 3. Adjacent transpositions (where Damerau-Levenshtein should help)
            extra("acocuntant", "Accountant"),
            extra("accountnat", "Accountant"),
            extra("arhcitect", "Architect"),
            extra("architetc", "Architect"),
            extra("softwrae engineer", "Software engineer"),
            extra("sofwtare engineer", "Software engineer"),
            extra("quantity surveoyr", "Quantity surveyor"),

            // 4. Substitutions and extra letters
            extra("quantity surveryor", "Quantity surveyor"),
            extra("accountent", "Accountant"),
            extra("accountantt", "Accountant"),
            extra("acccountant", "Accountant"),
            extra("architecht", "Architect"),
            extra("architectt", "Architect"),
            extra("softwore engineer", "Software engineer"),
            extra("software enginneer", "Software engineer"),
            extra("quantitty surveyor", "Quantity surveyor"),
            extra("quantity surveyorr", "Quantity surveyor"),

            // 5. Plausible spelling mistakes (useful for Jaro-Winkler)
            extra("softwear engineer", "Software engineer"),
            extra("accountint", "Accountant"),
            extra("quantaty surveyor", "Quantity surveyor"),
            extra("quantity survayor", "Quantity surveyor"),

            // 6. Partial titles: one exact word of a two-word output scores 0.5, which meets the threshold.
            extra("software", "Software engineer"),
            extra("quantity", "Quantity surveyor"),
            extra("engineer", "Software engineer"),
            extra("account", "Accountant"),
            extra("arch", "Unknown"),

            // 7. Similar-looking words that should NOT match
            baseline("plumber", "Unknown"),
            baseline("engage", "Unknown"),
            baseline("english", "Unknown"),
            baseline("engine", "Unknown"),
            baseline("survivor", "Unknown"),
            extra("archive", "Unknown"),
            extra("archives", "Unknown"),
            extra("archival", "Unknown"),
            extra("superior", "Unknown"),
            extra("partner", "Unknown"),
            extra("party", "Unknown"),
            extra("nurse", "Unknown"),
            extra("nursery", "Unknown"),
            extra("director", "Unknown"),
            extra("directory", "Unknown"),
            extra("accord", "Unknown"),
            extra("quantum", "Unknown"),
            extra("survival", "Unknown"),
            extra("survey", "Unknown"),

            // 8. Other professions / unrelated words
            extra("carpenter", "Unknown"),
            extra("doctor", "Unknown"),
            extra("teacher", "Unknown"),
            extra("electrician", "Unknown"),
            extra("analyst", "Unknown"),
            extra("developer", "Unknown"),
            extra("technician", "Unknown"),
            extra("mechanic", "Unknown"),
            extra("bookkeeper", "Unknown"),
            extra("actuary", "Unknown"),

            // 9. Extra words around the title. Words not in the output are ignored, so these still match.
            extra("senior software engineer", "Software engineer"),
            extra("junior architect", "Architect"),
            extra("assistant accountant", "Accountant"),
            extra("graduate quantity surveyor", "Quantity surveyor"),
            extra("software engineering manager", "Software engineer"),
            extra("quantity control surveyor", "Quantity surveyor"),
            extra("account manager", "Accountant"),

            // 10. Custom candidate lists, to isolate word similarity
            custom(List.of("Chef"), "chef", "Chef"),
            custom(List.of("Chef"), "chfe", "Chef"),
            custom(List.of("Chef"), "cheff", "Chef"),
            custom(List.of("Chef"), "chess", "Unknown"),
            custom(List.of("Nurse"), "nurse", "Nurse"),
            custom(List.of("Nurse"), "nruse", "Nurse"),
            custom(List.of("Nurse"), "nursee", "Nurse"),
            custom(List.of("Plumber", "Painter"), "plimber", "Plumber"),
            custom(List.of("Plumber", "Painter"), "painter", "Painter"),
            custom(List.of("Plumber", "Painter"), "planner", "Unknown"),
            custom(List.of("Engineer"), "engineer", "Engineer"),
            custom(List.of("Engineer"), "enginer", "Engineer"),
            custom(List.of("Surveyor"), "surveyor", "Surveyor"),
            custom(List.of("Surveyor"), "survayor", "Surveyor"),

            // 11. Two typos (2 edits) in a one-word output: a cut-off of 0.8 rejects these, 0.75 accepts them.
            custom(List.of("Engineer"), "engine", "Unknown"),
            custom(List.of("Surveyor"), "survivor", "Unknown"));

    @Test
    void comparesAlgorithms() {
        List<String> report = new ArrayList<>();
        Map<String, Long> baselineFailures = new LinkedHashMap<>();
        for (var entry : ALGORITHMS.entrySet()) {
            var failures = new ArrayList<String>();
            long baselineFailed = 0;
            for (Case c : CASES) {
                String actual = new Normaliser(c.outputs(), entry.getValue()).normalise(c.input());
                if (!actual.equals(c.expected())) {
                    failures.add(c.name() + " (expected " + c.expected() + ", got " + actual + ")");
                    if (c.baseline()) {
                        baselineFailed++;
                    }
                }
            }
            baselineFailures.put(entry.getKey(), baselineFailed);
            report.add(String.format("%-20s %d/%d passed%s", entry.getKey(), CASES.size() - failures.size(),
                    CASES.size(), failures.isEmpty() ? "" : "\n    failed: " + String.join("\n            ", failures)));
        }
        System.out.println("Typo algorithm scoreboard (min similarity "
                + MatchConfig.MIN_TYPO_SIMILARITY + "):\n" + String.join("\n", report));

        assertTrue(baselineFailures.get("Levenshtein") == 0, "Levenshtein must keep passing the baseline cases");
    }
}
