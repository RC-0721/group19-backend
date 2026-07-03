package com.group19.teaching.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
