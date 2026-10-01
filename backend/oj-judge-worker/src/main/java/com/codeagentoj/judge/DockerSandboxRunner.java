package com.codeagentoj.judge;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DockerSandboxRunner implements SandboxRunner {
    private final String dockerCommand;
    private final String image;
    private final int memoryMb;
    private final int pidsLimit;
    private final String cpus;

    public DockerSandboxRunner(
            @Value("${app.judge.sandbox.docker-command:docker}") String dockerCommand,
            @Value("${app.judge.sandbox.image:codeagent-oj-java21-sandbox:latest}") String image,
            @Value("${app.judge.sandbox.memory-mb:256}") int memoryMb,
            @Value("${app.judge.sandbox.pids-limit:64}") int pidsLimit,
            @Value("${app.judge.sandbox.cpus:1}") String cpus) {
        this.dockerCommand = dockerCommand;
        this.image = image;
        this.memoryMb = memoryMb;
        this.pidsLimit = pidsLimit;
        this.cpus = cpus;
    }

    @Override
    public SandboxResult execute(Path workspace, List<String> command, byte[] input, long timeoutMs) throws Exception {
        String name = "codeagent-judge-" + UUID.randomUUID().toString().replace("-", "");
        Path stdout = Files.createTempFile(workspace, "sandbox-stdout-", ".log");
        Path stderr = Files.createTempFile(workspace, "sandbox-stderr-", ".log");
        Path memoryPeak = workspace.resolve(".codeagent-memory-" + UUID.randomUUID());
        List<String> docker = new ArrayList<>(List.of(
                dockerCommand, "run", "--rm", "--interactive", "--name", name,
                "--network=none", "--read-only", "--cap-drop=ALL",
                "--security-opt=no-new-privileges", "--pids-limit=" + pidsLimit,
                "--memory=" + memoryMb + "m", "--cpus=" + cpus,
                "--tmpfs", "/tmp:rw,noexec,nosuid,size=64m",
                "--mount", "type=bind,src=" + workspace.toAbsolutePath() + ",dst=/workspace",
                "--workdir", "/workspace", "--user", "10001:10001", image));
        String containerMemoryFile = "/workspace/" + memoryPeak.getFileName();
        docker.addAll(List.of("sh", "-c", shellCommand(command) + "; status=$?; cat /sys/fs/cgroup/memory.peak > " + shellQuote(containerMemoryFile) + " 2>/dev/null || true; exit $status"));

        try {
            Process process = new ProcessBuilder(docker)
                    .redirectOutput(stdout.toFile())
                    .redirectError(stderr.toFile())
                    .start();
            if (input != null) {
                process.getOutputStream().write(input);
            }
            process.getOutputStream().close();
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
            int peakMemoryKb = 0;
            while (process.isAlive() && System.nanoTime() < deadline) {
                peakMemoryKb = Math.max(peakMemoryKb, currentMemoryKb(name));
                process.waitFor(75, TimeUnit.MILLISECONDS);
            }
            peakMemoryKb = Math.max(peakMemoryKb, Math.max(currentMemoryKb(name), readMemoryPeakKb(memoryPeak)));
            boolean finished = !process.isAlive();
            if (!finished) {
                process.destroyForcibly();
                remove(name);
                return new SandboxResult(-1, read(stdout), read(stderr), peakMemoryKb, true, false);
            }
            int exit = process.exitValue();
            String out = read(stdout);
            String err = read(stderr);
            return new SandboxResult(exit, out, err, peakMemoryKb, false, exit == 125);
        } finally {
            Files.deleteIfExists(stdout);
            Files.deleteIfExists(stderr);
            Files.deleteIfExists(memoryPeak);
        }
    }

    private void remove(String name) {
        try {
            new ProcessBuilder(dockerCommand, "rm", "--force", name).start().waitFor(5, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // Docker's --rm normally performs this cleanup; this handles a killed client process.
        }
    }

    private String read(Path path) throws Exception {
        return Files.exists(path) ? Files.readString(path, StandardCharsets.UTF_8) : "";
    }

    private int currentMemoryKb(String name) {
        try {
            Process process = new ProcessBuilder(dockerCommand, "stats", "--no-stream", "--format", "{{.MemUsage}}", name)
                    .redirectErrorStream(true).start();
            if (!process.waitFor(250, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return 0;
            }
            if (process.exitValue() != 0) return 0;
            return parseMemoryKb(new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private int parseMemoryKb(String stats) {
        String value = stats.split("/", 2)[0].trim().toUpperCase(Locale.ROOT);
        java.util.regex.Matcher match = java.util.regex.Pattern.compile("([0-9.]+)\\s*([KMG]?I?B)").matcher(value);
        if (!match.find()) return 0;
        double amount = Double.parseDouble(match.group(1));
        return switch (match.group(2)) {
            case "B" -> (int) Math.ceil(amount / 1024d);
            case "KB", "KIB" -> (int) Math.ceil(amount);
            case "MB", "MIB" -> (int) Math.ceil(amount * 1024d);
            case "GB", "GIB" -> (int) Math.ceil(amount * 1024d * 1024d);
            default -> 0;
        };
    }

    private String shellCommand(List<String> command) {
        return command.stream().map(value -> "'" + value.replace("'", "'\\\"'\\\"'") + "'")
                .collect(java.util.stream.Collectors.joining(" "));
    }

    private String shellQuote(String value) { return "'" + value.replace("'", "'\\\"'\\\"'") + "'"; }
    private int readMemoryPeakKb(Path path) {
        try { long bytes = Long.parseLong(Files.readString(path, StandardCharsets.UTF_8).trim()); return (int) Math.max(1, (bytes + 1023L) / 1024L); }
        catch (Exception ignored) { return 0; }
    }
}
