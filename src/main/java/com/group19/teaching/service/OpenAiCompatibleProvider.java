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
import java.util.Locale;
import java.util.Map;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class OpenAiCompatibleProvider implements AiProvider {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final ObjectMapper objectMapper;
    private final OkHttpClient client;
    private final String baseUrl;
    private final String resourceApiKeyFile;
    private final String interviewApiKeyFile;
    private final String adminApiKeyFile;
    private final String assistantApiKeyFile;
    private final String model;

    public OpenAiCompatibleProvider(
            ObjectMapper objectMapper,
            @Value("${teaching.ai.base-url:https://api.deepseek.com}") String baseUrl,
            @Value("${teaching.ai.api-key-files.resources:}") String resourceApiKeyFile,
            @Value("${teaching.ai.api-key-files.interview:}") String interviewApiKeyFile,
            @Value("${teaching.ai.api-key-files.admin:}") String adminApiKeyFile,
            @Value("${teaching.ai.api-key-files.assistant:}") String assistantApiKeyFile,
            @Value("${teaching.ai.model:deepseek-v4-flash}") String model,
            @Value("${teaching.ai.timeout-ms:30000}") int timeoutMs) {
        this.objectMapper = objectMapper;
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.resourceApiKeyFile = resourceApiKeyFile;
        this.interviewApiKeyFile = interviewApiKeyFile;
        this.adminApiKeyFile = adminApiKeyFile;
        this.assistantApiKeyFile = assistantApiKeyFile;
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
        String key = apiKeyForScene(request.scene());
        if (!StringUtils.hasText(key)) {
            throw new IllegalStateException("AI API key is missing");
        }
        long start = System.currentTimeMillis();
        try {
            Request httpRequest = new Request.Builder()
                    .url(baseUrl + "/chat/completions")
                    .addHeader("Authorization", "Bearer " + key)
                    .post(RequestBody.create(objectMapper.writeValueAsString(payload(request, false)), JSON))
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

    @Override
    public AiProviderStreamResult stream(AiRequest request, AiStreamHandler handler) {
        String key = apiKeyForScene(request.scene());
        if (!StringUtils.hasText(key)) {
            throw new IllegalStateException("AI API key is missing");
        }
        long start = System.currentTimeMillis();
        try {
            Request httpRequest = new Request.Builder()
                    .url(baseUrl + "/chat/completions")
                    .addHeader("Authorization", "Bearer " + key)
                    .post(RequestBody.create(objectMapper.writeValueAsString(payload(request, true)), JSON))
                    .build();
            try (Response response = client.newCall(httpRequest).execute()) {
                if (!response.isSuccessful()) {
                    throw new IllegalStateException("AI HTTP " + response.code());
                }
                if (response.body() == null) {
                    throw new IllegalStateException("AI response body is empty");
                }
                StringBuilder content = new StringBuilder();
                Integer tokenInput = null;
                Integer tokenOutput = null;
                BufferedSource source = response.body().source();
                String line;
                while ((line = source.readUtf8Line()) != null) {
                    if (!line.startsWith("data:")) {
                        continue;
                    }
                    String data = line.substring("data:".length()).trim();
                    if ("[DONE]".equals(data)) {
                        break;
                    }
                    JsonNode root = objectMapper.readTree(data);
                    JsonNode deltaNode = root.path("choices").path(0).path("delta").path("content");
                    if (deltaNode.isMissingNode() || deltaNode.isNull()) {
                        continue;
                    }
                    String delta = deltaNode.asText();
                    if (StringUtils.hasText(delta)) {
                        content.append(delta);
                        if (handler != null) {
                            handler.onDelta(delta);
                        }
                    }
                    JsonNode usage = root.path("usage");
                    if (!usage.isMissingNode() && !usage.isNull()) {
                        tokenInput = usage.path("prompt_tokens").isMissingNode() ? tokenInput : usage.path("prompt_tokens").asInt();
                        tokenOutput = usage.path("completion_tokens").isMissingNode() ? tokenOutput : usage.path("completion_tokens").asInt();
                    }
                }
                if (!StringUtils.hasText(content)) {
                    throw new IllegalStateException("AI response content is empty");
                }
                return new AiProviderStreamResult(
                        model,
                        content.toString(),
                        tokenInput,
                        tokenOutput,
                        System.currentTimeMillis() - start);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("AI request failed", exception);
        }
    }

    private Map<String, Object> payload(AiRequest request, boolean stream) {
        List<Map<String, String>> messages = new ArrayList<>();
        if (StringUtils.hasText(request.systemPrompt())) {
            messages.add(Map.of("role", "system", "content", request.systemPrompt()));
        }
        messages.add(Map.of("role", "user", "content", request.prompt()));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("messages", messages);
        payload.put("stream", stream);
        return payload;
    }

    String apiKeyForScene(String scene) {
        String selectedApiKeyFile = apiKeyFileForScene(scene);
        if (!StringUtils.hasText(selectedApiKeyFile)) {
            return "";
        }
        try {
            return Files.readString(Path.of(selectedApiKeyFile)).trim();
        } catch (IOException exception) {
            return "";
        }
    }

    String apiKeyFileForScene(String scene) {
        String normalized = scene == null ? "" : scene.toUpperCase(Locale.ROOT);
        String selected = "";
        if (containsAny(normalized, "INTERVIEW", "面试")) {
            selected = interviewApiKeyFile;
        } else if (containsAny(normalized, "HOMEWORK", "PRE_TASK", "PRACTICE_RECOMMENDATION",
                "ASSISTANT", "作业", "课前", "课后")) {
            selected = assistantApiKeyFile;
        } else if (containsAny(normalized, "ADMIN", "ALERT", "MONITOR", "MATERIAL_REVIEW", "管理员")) {
            selected = adminApiKeyFile;
        } else if (containsAny(normalized, "MATERIAL", "RESOURCE", "KNOWLEDGE", "QUESTION",
                "CONTENT_SCORE", "DATA_CLEANUP", "资料", "资源", "知识", "题目")) {
            selected = resourceApiKeyFile;
        }
        return StringUtils.hasText(selected) ? selected : resourceApiKeyFile;
    }

    private boolean containsAny(String value, String... keywords) {
        for (String keyword : keywords) {
            if (value.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String stripTrailingSlash(String value) {
        return value != null && value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
