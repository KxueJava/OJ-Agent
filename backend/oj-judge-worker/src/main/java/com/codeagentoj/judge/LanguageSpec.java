package com.codeagentoj.judge;

import java.util.List;

record LanguageSpec(String id, String sourceFile, List<String> compileCommand, List<String> runCommand) {
    static LanguageSpec from(Object value) {
        String language = value == null ? "JAVA_21" : value.toString();
        return switch (language) {
            case "CPP_17", "C++17" -> new LanguageSpec("CPP_17", "main.cpp",
                    List.of("g++", "-std=c++17", "-O2", "-pipe", "-static", "-s", "-o", "/workspace/main", "/workspace/main.cpp"),
                    List.of("/workspace/main"));
            case "C_17", "C17" -> new LanguageSpec("C_17", "main.c",
                    List.of("gcc", "-std=c17", "-O2", "-pipe", "-static", "-s", "-o", "/workspace/main", "/workspace/main.c"),
                    List.of("/workspace/main"));
            default -> new LanguageSpec("JAVA_21", "Main.java",
                    List.of("javac", "-encoding", "UTF-8", "-d", "/workspace", "/workspace/Main.java"),
                    List.of("java", "-Xmx128m", "-cp", "/workspace", "Main"));
        };
    }
}
