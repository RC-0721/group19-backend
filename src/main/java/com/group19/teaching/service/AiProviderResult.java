package com.group19.teaching.service;

public record AiProviderResult(
        String model,
        String content,
        Integer tokenInput,
        Integer tokenOutput,
        Long durationMs) {
}
