package com.williamhaynes;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Maps free-text job titles onto a fixed list of outputs. Immutable and stateless once built, so one instance can be
 * shared between threads.
 */
public class Normaliser {

    private static final String UNKNOWN = "Unknown";

    // Damerau-Levenshtein scored best in TypoAlgorithmComparisonTest: it treats swapped letters as one typo.
    private static final StringSimilarity DEFAULT_TYPO_ALGORITHM = new DamerauLevenshteinSimilarity();

    private static final List<String> DEFAULT_OUTPUTS = List.of(
            "Software engineer",
            "Architect",
            "Quantity surveyor",
            "Accountant");

    private final InputCleaner cleaner = new InputCleaner();
    // Each distinct output (original spelling) with its cleaned words, computed once.
    private final Map<String, List<String>> outputWords;
    // Run in priority order; the stage's own priority is what breaks ties between stages.
    private final List<StageScorer> stages;

    /** Builds a normaliser for the default outputs (Software engineer, Architect, Quantity surveyor, Accountant). */
    public Normaliser() {
        this(DEFAULT_OUTPUTS);
    }

    /**
     * Builds a normaliser with the default typo algorithm.
     *
     * @param possibleOutputs the outputs that can be returned, as for the full constructor
     * @throws IllegalArgumentException if the list is null or empty, or has a null or blank entry
     */
    Normaliser(List<String> possibleOutputs) {
        this(possibleOutputs, DEFAULT_TYPO_ALGORITHM);
    }

    /**
     * @param possibleOutputs the outputs that can be returned; duplicates (ignoring case, accents and spacing) are
     *                        dropped, keeping the first spelling
     * @param typoAlgorithm   how closely two words must be spelled alike to count as the same word with typos
     * @throws IllegalArgumentException if the list is null or empty, or has a null or blank entry
     */
    Normaliser(List<String> possibleOutputs, StringSimilarity typoAlgorithm) {
        this(possibleOutputs, defaultStages(typoAlgorithm));
    }

    /**
     * @param possibleOutputs as for the other constructors
     * @param stages          the stage scorers to run for every output; the best score across them is used
     * @throws IllegalArgumentException if the outputs are invalid, or there are no stages
     */
    Normaliser(List<String> possibleOutputs, List<StageScorer> stages) {
        if (stages == null || stages.isEmpty()) {
            throw new IllegalArgumentException("stages must not be null or empty");
        }
        this.outputWords = cleanOutputs(possibleOutputs);
        this.stages = List.copyOf(stages);
    }

    /**
     * The three standard stages, in priority order: exact phrase, word, related word.
     *
     * @param typoAlgorithm the typo algorithm shared by all three stages
     * @return the stage scorers
     */
    private static List<StageScorer> defaultStages(StringSimilarity typoAlgorithm) {
        WordSimilarity typoTolerant = new TypoTolerantSimilarity(typoAlgorithm);
        return List.of(
                new AlignmentStageScorer(MatchStage.EXACT_PHRASE, typoTolerant, true),
                new AlignmentStageScorer(MatchStage.WORD, typoTolerant, false),
                new AlignmentStageScorer(MatchStage.RELATED_WORD, new RelatedWordSimilarity(typoTolerant), false));
    }

    /**
     * Validates the outputs and pre-cleans them once, so they are not re-split on every call.
     *
     * @param possibleOutputs the raw outputs
     * @return each distinct output (original spelling of the first duplicate) with its cleaned words, in list order
     * @throws IllegalArgumentException if the list is null or empty, or has a null, blank or wordless entry
     */
    private Map<String, List<String>> cleanOutputs(List<String> possibleOutputs) {
        if (possibleOutputs == null || possibleOutputs.isEmpty()) {
            throw new IllegalArgumentException("possibleOutputs must not be null or empty");
        }
        // Keyed by cleaned words, so spellings differing only by case, accents or spacing collapse to the first.
        Map<List<String>, String> firstSpelling = new LinkedHashMap<>();
        for (String output : possibleOutputs) {
            if (output == null || output.isBlank()) {
                throw new IllegalArgumentException("possibleOutputs must not contain null or blank entries");
            }
            List<String> words = cleaner.cleanOutput(output);
            if (words.isEmpty()) {
                throw new IllegalArgumentException("possibleOutputs entry has no usable words");
            }
            firstSpelling.putIfAbsent(words, output);
        }
        Map<String, List<String>> result = new LinkedHashMap<>();
        firstSpelling.forEach((words, output) -> result.put(output, words));
        return Collections.unmodifiableMap(result);
    }

    /**
     * Finds the output that best matches the input.
     *
     * @param input free text; may be null
     * @return the best matching output, or "Unknown" if the input is null, rejected (too long, too many words,
     *         control characters), no output reaches the minimum score, or two outputs tie for best on both
     *         score and stage
     */
    public String normalise(String input) {
        Optional<List<String>> cleaned = cleaner.cleanInput(input);
        // Rejected, or nothing but whitespace: there is nothing to score.
        if (cleaned.isEmpty() || cleaned.get().isEmpty()) {
            return UNKNOWN;
        }
        List<String> inputWords = cleaned.get();

        String bestOutput = null;
        StageScore bestScore = StageScore.NONE;
        boolean tied = false;
        for (Map.Entry<String, List<String>> output : outputWords.entrySet()) {
            StageScore score = bestStageScore(inputWords, output.getValue());
            if (score.value() < MatchConfig.MIN_SCORE) {
                continue;
            }
            int comparison = StageScore.BEST_LAST.compare(score, bestScore);
            if (comparison > 0) {
                bestOutput = output.getKey();
                bestScore = score;
                tied = false;
            } else if (comparison == 0 && bestOutput != null) {
                // Equally good on score and stage: the answer is ambiguous unless something later beats both.
                tied = true;
            }
        }
        return ((bestOutput == null) || tied) ? UNKNOWN : bestOutput;
    }

    /**
     * Runs every stage for one output and keeps the best result (higher score, then higher stage priority).
     *
     * @param inputWords  the cleaned input words
     * @param outputWords the cleaned words of one output
     * @return the best stage score for that output
     */
    private StageScore bestStageScore(List<String> inputWords, List<String> outputWords) {
        StageScore best = StageScore.NONE;
        for (StageScorer stage : stages) {
            StageScore score = new StageScore(stage.score(inputWords, outputWords), stage.stage());
            if (StageScore.BEST_LAST.compare(score, best) > 0) {
                best = score;
            }
        }
        return best;
    }
}
