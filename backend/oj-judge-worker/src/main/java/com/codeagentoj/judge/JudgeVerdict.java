package com.codeagentoj.judge;

public final class JudgeVerdict {
    private JudgeVerdict() {}
    public static String classify(boolean timedOut, boolean memoryExceeded, int exitCode, String actual, String expected) {
        if (timedOut) return "TLE";
        if (memoryExceeded) return "MLE";
        if (exitCode != 0) return "RE";
        return normalize(actual).equals(normalize(expected)) ? "AC" : "WA";
    }
    private static String normalize(String value) { return value == null ? "" : value.trim().replaceAll("\\s+", " "); }
}
