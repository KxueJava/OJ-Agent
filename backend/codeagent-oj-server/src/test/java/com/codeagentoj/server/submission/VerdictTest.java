package com.codeagentoj.server.submission;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class VerdictTest {
    @Test void recognizesEveryTerminalVerdict() {
        for (String status : new String[]{"AC", "WA", "CE", "RE", "TLE", "MLE"}) assertTrue(Verdict.isTerminal(status));
    }

    @Test void keepsQueueStatusesNonTerminal() {
        assertFalse(Verdict.isTerminal("PENDING"));
        assertFalse(Verdict.isTerminal("RUNNING"));
        assertFalse(Verdict.isTerminal(null));
    }
}
