package com.group19.teaching.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class OpenAiCompatibleProvider implements AiProvider {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final ObjectMapper objectMapper;
    private final OkHttpClient client;
    private final String baseUrl;
    private final String apiKey;
    private final String apiKeyFile;
    private final String model;

    public OpenAiCompatibleProvider(
            ObjectMapper objectMapper,
            @Value("${teaching.ai.base-url:https://api.deepseek.com}") String baseUrl,
            @Value("${teaching.ai.api-key:}") String apiKey,
            @Value("${teaching.ai.api-key-file:}") String apiKeyFile,
            @Value("${teaching.ai.model:deepseek-v4-flash}") String model,
            @Value("${teaching.ai.timeout-ms:30000}") int timeoutMs) {
        this.objectMapper = objectMapper;
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.apiKey = apiKey;
        this.apiKeyFile = apiKeyFile;
        this.model = model;
        this.client = new OkHttpClient.Builder()
                .callTimeout(Duration.ofMillis(timeoutMs))
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .readTimeout(Duration.ofMillis(timeoutMs))
                .build();
    }

    @Override
    public boolean supports(String provider) {
        return List.of("deepseek", "openai", "qwen").contains(provider == null ? "" : provider.toLowerCase());
    }

    @Override
    public AiProviderResult chat(AiRequest request) {
        String key = apiKey();
        if (!StringUtils.hasText(key)) {
            throw new IllegalStateException("AI API key is missing");
        }
        long start = System.currentTimeMillis();
        try {
            Request httpRequest = new Request.Builder()
                    .url(baseUrl + "/chat/completions")
                    .addHeader("Authorization", "Bearer " + key)
                    .post(RequestBody.create(objectMapper.writeValueAsString(payload(request)), JSON))
                    .build();
            try (Response response = client.newCall(httpRequest).execute()) {
                String body = response.body() == null ? "" : response.body().string();
                if (!response.isSuccessful()) {
                    throw new IllegalStateException("AI HTTP " + response.code());
                }
                JsonNode root = objectMapper.readTree(body);
                String content = root.path("choices").path(0).path("message").path("content").asText();
                if (!StringUtils.hasText(content)) {
                    throw new IllegalStateException("AI response content is empty");
                }
                JsonNode usage = root.path("usage");
                return new AiProviderResult(
                        model,
                        content,
                        usage.path("prompt_tokens").isMissingNode() ? null : usage.path("prompt_tokens").asInt(),
                        usage.path("completion_tokens").isMissingNode() ? null : usage.path("completion_tokens").asInt(),
                        System.currentTimeMillis() - start);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("AI request failed", exception);
        }
    }

    private Map<String, Object> payload(AiRequest request) {
        List<Map<String, String>> messages = new ArrayList<>();
        if (StringUtils.hasText(request.systemPrompt())) {
            messages.add(Map.of("role", "system", "content", request.systemPrompt()));
        }
        messages.add(Map.of("role", "user", "content", request.prompt()));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("messages", messages);
        payload.put("stream", false);
        return payload;
    }

    private String apiKey() {
        if (StringUtils.hasText(apiKey)) {
            return apiKey.trim();
        }
        if (!StringUtils.hasText(apiKeyFile)) {
            return "";
        }
        try {
            return Files.readString(Path.of(apiKeyFile)).trim();
        } catch (IOException exception) {
            return "";
        }
    }

    private String stripTrailingSlash(String value) {
        return value != null && value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
