package com.codeagentoj.server.submission;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class SubmissionDtos {
    private SubmissionDtos() {}
    public record CreateRequest(long problemVersion, @NotBlank String language, @NotBlank @Size(max=100_000) String sourceCode) {}
    public record Summary(long id, String status, String verdictMessage, Integer runtimeMs, Integer memoryKb, Instant createdAt, Instant finishedAt,
                          Integer compileMs, String failureKind, int rejudgeCount) {}
    public record CaseSummary(int order, String verdict, Integer runtimeMs, Integer memoryKb, String outputSummary) {}
    public record Detail(Summary submission, List<CaseSummary> cases, String language, String sourceCode, String slug) {}
    public record Event(String type, String status, String message, Instant at) {}
    public record ListItem(long id, String slug, String title, String language, String status, String verdictMessage,
                           Integer runtimeMs, Integer memoryKb, Instant createdAt, Instant finishedAt, boolean diagnosed) {}
    public record Page(List<ListItem> items, int page, int size, long total) {}
}
