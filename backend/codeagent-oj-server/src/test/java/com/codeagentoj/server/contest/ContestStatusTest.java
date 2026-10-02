package com.codeagentoj.server.contest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 状态机守卫回归：每个动作允许的状态必须明确，越界一律拒绝（避免把已定榜的比赛改回去）。 */
class ContestStatusTest {

    @Test void publishOnlyFromDraftOrScheduled() {
        assertTrue(ContestStatus.canPublish(ContestStatus.DRAFT));
        assertTrue(ContestStatus.canPublish(ContestStatus.SCHEDULED));
        assertFalse(ContestStatus.canPublish(ContestStatus.RUNNING));
        assertFalse(ContestStatus.canPublish(ContestStatus.ENDED));
        assertFalse(ContestStatus.canPublish(ContestStatus.FINALIZED));
        assertFalse(ContestStatus.canPublish(ContestStatus.CANCELLED));
    }

    @Test void cancelAllowedUntilEnded() {
        assertTrue(ContestStatus.canCancel(ContestStatus.DRAFT));
        assertTrue(ContestStatus.canCancel(ContestStatus.SCHEDULED));
        assertTrue(ContestStatus.canCancel(ContestStatus.RUNNING));
        assertFalse(ContestStatus.canCancel(ContestStatus.ENDED));
        assertFalse(ContestStatus.canCancel(ContestStatus.CANCELLED));
    }

    @Test void finalizeOnlyOnceAndOnlyAfterEnded() {
        assertTrue(ContestStatus.canFinalize(ContestStatus.ENDED));
        assertFalse(ContestStatus.canFinalize(ContestStatus.RUNNING));
        assertFalse(ContestStatus.canFinalize(ContestStatus.FINALIZED));
    }

    @Test void editableOnlyBeforeStart() {
        assertTrue(ContestStatus.EDITABLE.contains(ContestStatus.DRAFT));
        assertTrue(ContestStatus.EDITABLE.contains(ContestStatus.SCHEDULED));
        assertFalse(ContestStatus.EDITABLE.contains(ContestStatus.RUNNING));
        assertFalse(ContestStatus.EDITABLE.contains(ContestStatus.ENDED));
    }

    @Test void autoTransitionsAreOnlyTheTwoAllowedOnes() {
        assertTrue(ContestStatus.AUTO_TRANSITIONS.stream().anyMatch(pair -> pair[0].equals(ContestStatus.SCHEDULED) && pair[1].equals(ContestStatus.RUNNING)));
        assertTrue(ContestStatus.AUTO_TRANSITIONS.stream().anyMatch(pair -> pair[0].equals(ContestStatus.RUNNING) && pair[1].equals(ContestStatus.ENDED)));
        assertTrue(ContestStatus.AUTO_TRANSITIONS.size() == 2);
    }

    @Test void terminalStates() {
        assertTrue(ContestStatus.isTerminal(ContestStatus.FINALIZED));
        assertTrue(ContestStatus.isTerminal(ContestStatus.CANCELLED));
        assertFalse(ContestStatus.isTerminal(ContestStatus.RUNNING));
    }
}
