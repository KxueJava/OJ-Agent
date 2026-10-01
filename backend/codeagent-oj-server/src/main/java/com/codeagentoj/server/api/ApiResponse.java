package com.codeagentoj.server.api;

public record ApiResponse<T>(boolean success, T data) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data);
    }
}
