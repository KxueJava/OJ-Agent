package com.codeagentoj.judge;

public record SandboxResult(int exitCode, String stdout, String stderr, int memoryKb, boolean timedOut, boolean infrastructureFailure) {
    public boolean memoryExceeded() {
        String output = stdout + stderr;
        return exitCode == 137 || output.contains("OutOfMemoryError") || output.contains("OOMKilled");
    }
}
