package com.codeagentoj.judge;

import java.nio.file.Path;
import java.util.List;

public interface SandboxRunner {
    SandboxResult execute(Path workspace, List<String> command, byte[] input, long timeoutMs) throws Exception;
}
