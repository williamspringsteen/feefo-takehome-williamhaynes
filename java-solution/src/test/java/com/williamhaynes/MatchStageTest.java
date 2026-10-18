package com.williamhaynes;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchStageTest {

    @ParameterizedTest
    @CsvSource({"EXACT_PHRASE, 1", "WORD, 2", "RELATED_WORD, 3"})
    void realStagesHaveTheIntendedPriority(MatchStage stage, int priority) {
        assertEquals(priority, stage.priority());
    }

    @Test
    void prioritiesAreUnique() {
        long distinct = Arrays.stream(MatchStage.values()).map(MatchStage::priority).distinct().count();
        assertEquals(MatchStage.values().length, distinct);
    }

    @Test
    void stagesAreInPriorityOrderWithNoneLast() {
        List<MatchStage> byPriority = Arrays.stream(MatchStage.values())
                .sorted(Comparator.comparingInt(MatchStage::priority)).toList();
        assertEquals(List.of(MatchStage.EXACT_PHRASE, MatchStage.WORD, MatchStage.RELATED_WORD, MatchStage.NONE),
                byPriority);
    }

    @ParameterizedTest
    @EnumSource(value = MatchStage.class, mode = EnumSource.Mode.EXCLUDE, names = "NONE")
    void noneLosesToEveryRealStage(MatchStage stage) {
        assertTrue(MatchStage.NONE.priority() > stage.priority());
    }
}
