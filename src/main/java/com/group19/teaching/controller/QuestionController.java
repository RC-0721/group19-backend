package com.group19.teaching.controller;

import com.group19.teaching.common.ApiResponse;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/questions")
public class QuestionController {

    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(value = "knowledge_id", required = false) String knowledgeId,
            @RequestParam(value = "job_id", required = false) String jobId,
            @RequestParam(value = "tech_id", required = false) String techId,
            @RequestParam(value = "question_type", required = false) String questionType,
            @RequestParam(value = "difficulty", required = false) String difficulty,
            @RequestParam("page_no") Integer pageNo,
            @RequestParam("page_size") Integer pageSize) {
        // Echoing filters helps frontend verify query wiring while data is still mocked.
        return ApiResponse.success(Map.of(
                "records", List.of(Map.of(
                        "question_id", "question-001",
                        "title", "Spring Boot Controller 的职责是什么？",
                        "question_type", questionType == null ? "单选题" : questionType,
                        "difficulty", difficulty == null ? "简单" : difficulty,
                        "knowledge_id", knowledgeId == null ? "knowledge-spring-boot" : knowledgeId,
                        "job_id", jobId == null ? "job-java-backend" : jobId,
                        "tech_id", techId == null ? "tech-spring-boot" : techId,
                        "audit_status", "已发布"
                )),
                "total", 1,
                "page_no", pageNo,
                "page_size", pageSize
        ));
    }
}
