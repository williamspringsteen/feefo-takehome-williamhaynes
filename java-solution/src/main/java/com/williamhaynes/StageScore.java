package com.williamhaynes;

import java.util.Comparator;

/**
 * A score between 0 and 1, and the stage that produced it.
 *
 * @param value the score, from 0 (no match) to 1 (perfect)
 * @param stage the stage that produced it
 */
record StageScore(double value, MatchStage stage) {

    static final StageScore NONE = new StageScore(0, MatchStage.NONE);

    /** Higher value is better; on a tie, the stage with the higher priority (lower number) is better. */
    static final Comparator<StageScore> BEST_LAST = Comparator
            .comparingDouble(StageScore::value)
            .thenComparing(Comparator.comparingInt((StageScore s) -> s.stage().priority()).reversed());
}
