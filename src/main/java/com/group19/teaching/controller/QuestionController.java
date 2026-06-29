package com.group19.teaching.controller;

import com.group19.teaching.common.ApiResponse;
import com.group19.teaching.service.QuestionService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/questions")
public class QuestionController {

    private final QuestionService questionService;

    public QuestionController(QuestionService questionService) {
        this.questionService = questionService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(value = "knowledge_id", required = false) String knowledgeId,
            @RequestParam(value = "source_id", required = false) String sourceId,
            @RequestParam(value = "job_id", required = false) String jobId,
            @RequestParam(value = "tech_id", required = false) String techId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "question_type", required = false) String questionType,
            @RequestParam(value = "difficulty", required = false) String difficulty,
            @RequestParam("page_no") Integer pageNo,
            @RequestParam("page_size") Integer pageSize) {
        return ApiResponse.success(questionService.list(
                knowledgeId, sourceId, jobId, techId, keyword, questionType, difficulty, pageNo, pageSize));
    }
}
