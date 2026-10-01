package com.codeagentoj.server.problem;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class ProblemDtos {
    private ProblemDtos() {}
    public record ProblemSummary(String slug, String title, String difficulty, List<TagView> tags, boolean favorite) {}
    public record TagView(String name, String slug) {}
    public record ExampleView(int order, String input, String output, String explanation) {}
    public record ProblemDetail(String slug, String title, String difficulty, String statementMd, String constraintsMd, String javaTemplate, int version, List<TagView> tags, List<ExampleView> examples, boolean favorite) {}
    public record PageView(List<ProblemSummary> items, int page, int size, long total) {}
    public record CreateProblemRequest(@NotBlank @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*") @Size(max = 128) String slug, @NotBlank @Size(max = 255) String title, @NotBlank @Pattern(regexp = "EASY|MEDIUM|HARD") String difficulty, @Valid VersionRequest version, @NotEmpty List<@NotBlank String> tags) {}
    public record VersionRequest(@NotBlank String statementMd, @NotBlank String constraintsMd, @NotBlank String javaTemplate, List<@Valid ExampleRequest> examples, List<@Valid TestCaseRequest> testCases) {}
    public record ExampleRequest(@NotBlank String input, @NotBlank String output, String explanation) {}
    public record TestCaseRequest(@NotBlank @Pattern(regexp = "PUBLIC|HIDDEN") String visibility, @NotBlank String input, @NotBlank String expectedOutput, Integer weight) {}
    public record AdminProblemView(String slug, String title, String difficulty, String status, Long publishedVersion, Instant updatedAt) {}
}
