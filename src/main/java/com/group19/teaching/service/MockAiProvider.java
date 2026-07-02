package com.group19.teaching.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MockAiProvider implements AiProvider {

    private final String model;

    public MockAiProvider(@Value("${teaching.ai.model:mock-ai}") String model) {
        this.model = model;
    }

    @Override
    public boolean supports(String provider) {
        return "mock".equalsIgnoreCase(provider);
    }

    @Override
    public AiProviderResult chat(AiRequest request) {
        long start = System.currentTimeMillis();
        String prompt = request.prompt() == null ? "" : request.prompt().strip();
        String content = "Mock AI 回复：" + (prompt.length() > 80 ? prompt.substring(0, 80) : prompt);
        return new AiProviderResult(model, content, prompt.length(), content.length(), System.currentTimeMillis() - start);
    }
}
