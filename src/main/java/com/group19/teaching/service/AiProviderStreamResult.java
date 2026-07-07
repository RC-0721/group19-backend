package com.group19.teaching.service;

public record AiProviderStreamResult(
        String model,
        String content,
        Integer tokenInput,
        Integer tokenOutput,
        Long durationMs) {
}
