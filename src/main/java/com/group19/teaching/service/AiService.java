package com.group19.teaching.service;

import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import com.group19.teaching.domain.entity.User;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class AiService {

    private final JdbcTemplate jdbcTemplate;
    private final List<AiProvider> providers;
    private final String providerName;
    private final String model;

    public AiService(
            JdbcTemplate jdbcTemplate,
            List<AiProvider> providers,
            @Value("${teaching.ai.provider:mock}") String providerName,
            @Value("${teaching.ai.model:mock-ai}") String model) {
        this.jdbcTemplate = jdbcTemplate;
        this.providers = providers;
        this.providerName = providerName;
        this.model = model;
    }

    public Map<String, Object> chat(Map<String, Object> request, User actor) {
        ChatRequest chatRequest = validateChatRequest(request);
        String requestId = "ai-req-" + UUID.randomUUID();
        try {
            AiProviderResult result = provider().chat(new AiRequest(
                    chatRequest.scene(), chatRequest.prompt(), chatRequest.systemPrompt()));
            writeLog(requestId, actor, chatRequest.scene(), result.model(), chatRequest.prompt(), result.content(), "成功",
                    result.durationMs(), result.tokenInput(), result.tokenOutput(), null);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("request_id", requestId);
            data.put("model", result.model());
            data.put("content", result.content());
            data.put("duration_ms", result.durationMs());
            return data;
        } catch (RuntimeException exception) {
            writeLog(requestId, actor, chatRequest.scene(), model, chatRequest.prompt(), "", "失败",
                    null, null, null, shortMessage(exception));
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
        }
    }

    public AiProviderStreamResult streamChat(Map<String, Object> request, User actor, AiStreamHandler handler) {
        ChatRequest chatRequest = validateChatRequest(request);
        String requestId = "ai-req-" + UUID.randomUUID();
        return streamChat(chatRequest, actor, handler, requestId);
    }

    private AiProviderStreamResult streamChat(
            ChatRequest chatRequest,
            User actor,
            AiStreamHandler handler,
            String requestId) {
        try {
            AiProviderStreamResult result = provider().stream(new AiRequest(
                    chatRequest.scene(), chatRequest.prompt(), chatRequest.systemPrompt()), handler);
            writeLog(requestId, actor, chatRequest.scene(), result.model(), chatRequest.prompt(), result.content(), "成功",
                    result.durationMs(), result.tokenInput(), result.tokenOutput(), null);
            return result;
        } catch (RuntimeException exception) {
            writeLog(requestId, actor, chatRequest.scene(), model, chatRequest.prompt(), "", "失败",
                    null, null, null, shortMessage(exception));
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
        }
    }

    public SseEmitter stream(Map<String, Object> request, User actor) {
        ChatRequest chatRequest = validateChatRequest(request);
        SseEmitter emitter = new SseEmitter(0L);
        CompletableFuture.runAsync(() -> {
            try {
                String requestId = "ai-req-" + UUID.randomUUID();
                emitter.send(SseEmitter.event().name("meta").data(Map.of(
                        "request_id", requestId,
                        "model", model
                )));
                streamChat(chatRequest, actor, delta -> sendDelta(emitter, delta), requestId);
                emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                emitter.complete();
            } catch (BusinessException exception) {
                sendError(emitter, exception.errorCode().code(), exception.errorCode().message());
            } catch (RuntimeException | IOException exception) {
                sendError(emitter, ErrorCode.INTERNAL_ERROR.code(), ErrorCode.INTERNAL_ERROR.message());
            }
        });
        return emitter;
    }

    private ChatRequest validateChatRequest(Map<String, Object> request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        String scene = stringValue(request.get("scene"));
        String prompt = stringValue(request.get("prompt"));
        String systemPrompt = stringValue(request.get("system_prompt"));
        if (!StringUtils.hasText(scene) || !StringUtils.hasText(prompt)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        return new ChatRequest(scene, prompt, systemPrompt);
    }

    private void sendDelta(SseEmitter emitter, String delta) {
        try {
            emitter.send(SseEmitter.event().name("delta").data(delta));
        } catch (IOException exception) {
            throw new IllegalStateException("SSE send failed", exception);
        }
    }

    private void sendError(SseEmitter emitter, String code, String message) {
        try {
            emitter.send(SseEmitter.event().name("error").data(Map.of("code", code, "message", message)));
        } catch (IOException ignored) {
        }
        emitter.complete();
    }

    private AiProvider provider() {
        return providers.stream()
                .filter(candidate -> candidate.supports(providerName))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("AI provider is unsupported"));
    }

    private void writeLog(String requestId, User actor, String scene, String model, String input, String output,
                          String status, Long durationMs, Integer tokenInput, Integer tokenOutput, String error) {
        jdbcTemplate.update("""
                INSERT INTO ai_call_log
                  (log_id, user_id, scene, model, prompt_version, input_summary, output_summary,
                   call_status, duration_ms, token_input, token_output, error_message, request_id)
                VALUES (?, ?, ?, ?, 'v1', ?, ?, ?, ?, ?, ?, ?, ?)
                """, "ai-log-" + UUID.randomUUID(), userId(actor), scene, model, limit(input), limit(output),
                status, durationMs, tokenInput, tokenOutput, limit(error), requestId);
    }

    private String userId(User actor) {
        return actor == null ? null : actor.getAccount();
    }

    private String shortMessage(RuntimeException exception) {
        Throwable cause = exception.getCause() == null ? exception : exception.getCause();
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    private String limit(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > 500 ? value.substring(0, 500) : value;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private record ChatRequest(String scene, String prompt, String systemPrompt) {
    }
}
