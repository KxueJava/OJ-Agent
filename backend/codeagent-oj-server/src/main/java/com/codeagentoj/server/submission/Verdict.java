package com.codeagentoj.server.submission;

import java.util.Set;

public final class Verdict {
    public static final Set<String> TERMINAL = Set.of("AC", "WA", "CE", "RE", "TLE", "MLE");
    private Verdict() {}
    public static boolean isTerminal(String status) { return status != null && TERMINAL.contains(status); }
}
