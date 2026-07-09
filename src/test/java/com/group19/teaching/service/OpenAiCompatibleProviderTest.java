package com.group19.teaching.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpenAiCompatibleProviderTest {

    @TempDir
    Path tempDir;

    @Test
    void routesSceneToDedicatedApiKeyFiles() throws IOException {
        Path resources = keyFile("resources.key", "resources-key");
        Path interview = keyFile("interview.key", "interview-key");
        Path admin = keyFile("admin.key", "admin-key");
        Path assistant = keyFile("assistant.key", "assistant-key");
        OpenAiCompatibleProvider provider = provider(resources, interview, admin, assistant);

        assertEquals(resources.toString(), provider.apiKeyFileForScene("MATERIAL_PARSE"));
        assertEquals(resources.toString(), provider.apiKeyFileForScene("DATA_CLEANUP_JAVA_BACKEND_DRYRUN"));
        assertEquals(interview.toString(), provider.apiKeyFileForScene("AI_INTERVIEW_MESSAGE"));
        assertEquals(interview.toString(), provider.apiKeyFileForScene("模拟面试"));
        assertEquals(admin.toString(), provider.apiKeyFileForScene("MATERIAL_REVIEW"));
        assertEquals(admin.toString(), provider.apiKeyFileForScene("ADMIN_AI_MONITOR"));
        assertEquals(assistant.toString(), provider.apiKeyFileForScene("HOMEWORK_REVIEW"));
        assertEquals(assistant.toString(), provider.apiKeyFileForScene("PRE_TASK_CANDIDATE"));
        assertEquals(resources.toString(), provider.apiKeyFileForScene("CHAT"));
    }

    @Test
    void readsApiKeyFromSelectedSceneFile() throws IOException {
        Path resources = keyFile("resources.key", "resources-key");
        Path interview = keyFile("interview.key", "interview-key");
        Path admin = keyFile("admin.key", "admin-key");
        Path assistant = keyFile("assistant.key", "assistant-key");
        OpenAiCompatibleProvider provider = provider(resources, interview, admin, assistant);

        assertEquals("interview-key", provider.apiKeyForScene("AI_INTERVIEW_REPORT"));
        assertEquals("assistant-key", provider.apiKeyForScene("HOMEWORK_REVIEW"));
        assertEquals("admin-key", provider.apiKeyForScene("MATERIAL_REVIEW"));
        assertEquals("resources-key", provider.apiKeyForScene("KNOWLEDGE_GRAPH_BUILD"));
    }

    @Test
    void streamParsesOpenAiCompatibleSseDeltas() throws IOException {
        Path resources = keyFile("resources.key", "resources-key");
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        List<String> requestBodies = new ArrayList<>();
        server.createContext("/chat/completions", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes());
            requestBodies.add(body);
            byte[] response = """
                    data: {"choices":[{"delta":{"content":"he"}}]}

                    data: {"choices":[{"delta":{"content":"llo"}}]}

                    data: [DONE]

                    """.getBytes();
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            OpenAiCompatibleProvider provider = new OpenAiCompatibleProvider(new ObjectMapper(),
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    resources.toString(), "", "", "", "deepseek-v4-flash", 1000);
            List<String> deltas = new ArrayList<>();

            AiProviderStreamResult result = provider.stream(new AiRequest("CHAT", "hello", ""), deltas::add);

            assertTrue(requestBodies.get(0).contains("\"stream\":true"));
            assertEquals("hello", result.content());
            assertEquals(List.of("he", "llo"), deltas);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void streamSkipsNullAndMissingDeltaContent() throws IOException {
        Path resources = keyFile("resources.key", "resources-key");
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] response = """
                    data: {"choices":[{"delta":{"role":"assistant","content":null}}]}

                    data: {"choices":[{"delta":{"role":"assistant"}}]}

                    data: {"choices":[{"delta":{"content":""}}]}

                    data: {"choices":[{"delta":{"content":"o"}}]}

                    data: {"choices":[{"delta":{"content":"k"}}]}

                    data: [DONE]

                    """.getBytes();
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            OpenAiCompatibleProvider provider = new OpenAiCompatibleProvider(new ObjectMapper(),
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    resources.toString(), "", "", "", "deepseek-v4-flash", 1000);
            List<String> deltas = new ArrayList<>();

            AiProviderStreamResult result = provider.stream(new AiRequest("CHAT", "hello", ""), deltas::add);

            assertEquals("ok", result.content());
            assertEquals(List.of("o", "k"), deltas);
        } finally {
            server.stop(0);
        }
    }

    private OpenAiCompatibleProvider provider(Path resources, Path interview, Path admin, Path assistant) {
        return new OpenAiCompatibleProvider(new ObjectMapper(), "https://api.deepseek.com",
                resources.toString(), interview.toString(), admin.toString(),
                assistant.toString(), "deepseek-v4-flash", 1000);
    }

    private Path keyFile(String name, String content) throws IOException {
        Path path = tempDir.resolve(name);
        Files.writeString(path, content);
        return path;
    }
}
