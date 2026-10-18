package com.williamhaynes;

/**
 * The matching stages, each with an explicit priority used to break score ties.
 * A lower priority number wins a tie, so declaration order is not relied upon.
 */
enum MatchStage {
    EXACT_PHRASE(1),
    WORD(2),
    RELATED_WORD(3),
    /** Not a real stage: marks the absence of a match, and loses every tie against a real stage. */
    NONE(Integer.MAX_VALUE);

    private final int priority;

    MatchStage(int priority) {
        this.priority = priority;
    }

    /** @return the tie-break priority; lower wins */
    int priority() {
        return priority;
    }
}
