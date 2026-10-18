package com.williamhaynes;

import java.util.List;

/** Scores one input against one candidate for a single {@link MatchStage}. */
interface StageScorer {

    /** @return the stage this scorer produces scores for */
    MatchStage stage();

    /**
     * @param inputWords     the already-cleaned words of the input
     * @param candidateWords the already-cleaned words of the candidate output
     * @return this stage's score for the candidate, from 0 (no match) to 1 (perfect)
     */
    double score(List<String> inputWords, List<String> candidateWords);
}
