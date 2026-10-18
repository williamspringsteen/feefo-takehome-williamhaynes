package com.williamhaynes;

import java.util.List;

/**
 * Scores one input against one candidate for a single stage as q = C * S * O:
 * <ul>
 *   <li>C: proportion of candidate words matched</li>
 *   <li>S: average quality of those matches (typos lower it)</li>
 *   <li>O: order adjustment (words in a different order lower it slightly)</li>
 * </ul>
 * The stage's character comes from its {@link WordSimilarity} (what counts as a match for C)
 * and whether matches must fall within a window of the input as wide as the candidate phrase.
 */
class AlignmentStageScorer implements StageScorer {

    private final MatchStage stage;
    private final WordAligner aligner;
    // When true, the candidate is only scored against runs of consecutive input words as long as the
    // candidate itself (e.g. a 3-word candidate vs. each 3-word slice of the input), so its words must
    // appear together. When false, the candidate is scored against the whole input, wherever its words are.
    private final boolean phraseWindow;

    /**
     * @param stage        the stage this scorer reports as
     * @param similarity   what counts as a match between an input word and a candidate word for this stage
     * @param phraseWindow true to score the candidate only against runs of consecutive input words as long as the
     *                     candidate (the exact phrase stage); false to score it against the whole input
     */
    AlignmentStageScorer(MatchStage stage, WordSimilarity similarity, boolean phraseWindow) {
        this.stage = stage;
        this.aligner = new WordAligner(similarity);
        this.phraseWindow = phraseWindow;
    }

    @Override
    public MatchStage stage() {
        return stage;
    }

    /**
     * Scores the candidate against the input, sliding a candidate-sized window along the input when
     * {@code phraseWindow} is set and keeping the best window.
     *
     * @param inputWords     the cleaned input words
     * @param candidateWords the cleaned words of the candidate output
     * @return C * S * O from 0 (no match, or either list empty) to 1 (perfect)
     */
    @Override
    public double score(List<String> inputWords, List<String> candidateWords) {
        if (inputWords.isEmpty() || candidateWords.isEmpty()) {
            return 0;
        }
        int width = candidateWords.size();
        // No windowing needed: either the stage scores the whole input, or the input is no longer
        // than the candidate so there is only one possible window (the input itself).
        if (!phraseWindow || inputWords.size() <= width) {
            return scoreAgainst(inputWords, candidateWords);
        }
        // Slide a candidate-sized window along the input and keep the best-scoring position.
        double best = 0;
        for (int start = 0; start + width <= inputWords.size(); start++) {
            best = Math.max(best, scoreAgainst(inputWords.subList(start, start + width), candidateWords));
        }
        return best;
    }

    /** Computes C * S * O for the candidate against one span of input words (the whole input or a window of it). */
    private double scoreAgainst(List<String> inputWords, List<String> candidateWords) {
        WordAlignment alignment = aligner.align(inputWords, candidateWords);
        if (alignment.matchedCount() == 0) {
            return 0;
        }
        return ScoreFactors.coverage(alignment)
                * ScoreFactors.quality(alignment)
                * ScoreFactors.orderAdjustment(alignment);
    }
}
