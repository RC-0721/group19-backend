package com.group19.teaching.controller;

import com.group19.teaching.common.ApiResponse;
import com.group19.teaching.domain.entity.User;
import com.group19.teaching.service.AuthService;
import com.group19.teaching.service.KnowledgeService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class KnowledgeController {

    private final KnowledgeService knowledgeService;
    private final AuthService authService;

    public KnowledgeController(KnowledgeService knowledgeService, AuthService authService) {
        this.knowledgeService = knowledgeService;
        this.authService = authService;
    }

    @GetMapping("/api/materials")
    public ApiResponse<Map<String, Object>> materials(
            @RequestHeader(value = "token", required = false) String token,
            @RequestParam(value = "course_id", required = false) String courseId,
            @RequestParam(value = "chapter_id", required = false) String chapterId,
            @RequestParam(value = "parse_status", required = false) String parseStatus,
            @RequestParam("page_no") Integer pageNo,
            @RequestParam("page_size") Integer pageSize) {
        User actor = authService.requireRole(token, "STUDENT", "TEACHER", "EDU_ADMIN");
        return ApiResponse.success(knowledgeService.listMaterials(courseId, chapterId, parseStatus, pageNo, pageSize, actor));
    }

    @PostMapping("/api/materials")
    public ApiResponse<Map<String, Object>> uploadMaterial(
            @RequestHeader(value = "token", required = false) String token,
            @RequestParam("course_id") String courseId,
            @RequestParam("chapter_id") String chapterId,
            @RequestPart("file") MultipartFile file) {
        User actor = authService.requireRole(token, "TEACHER", "EDU_ADMIN");
        return ApiResponse.success(knowledgeService.uploadMaterial(courseId, chapterId, file, actor));
    }

    @PutMapping("/api/materials/{materialId}")
    public ApiResponse<Map<String, Object>> updateMaterial(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable String materialId,
            @RequestBody Map<String, Object> request) {
        User actor = authService.requireRole(token, "TEACHER", "EDU_ADMIN");
        return ApiResponse.success(knowledgeService.updateMaterial(materialId, request, actor));
    }

    @GetMapping("/api/materials/{materialId}/parse-tasks")
    public ApiResponse<Map<String, Object>> parseTasks(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable String materialId) {
        User actor = authService.requireRole(token, "TEACHER", "EDU_ADMIN");
        return ApiResponse.success(knowledgeService.parseTasks(materialId, actor));
    }

    @PostMapping("/api/files")
    public ApiResponse<Map<String, Object>> uploadFile(
            @RequestHeader(value = "token", required = false) String token,
            @RequestPart("file") MultipartFile file) {
        User actor = authService.requireRole(token, "STUDENT", "TEACHER", "EDU_ADMIN");
        return ApiResponse.success(knowledgeService.uploadFile(file, actor));
    }

    @GetMapping("/api/knowledge/chunks")
    public ApiResponse<Map<String, Object>> chunks(
            @RequestHeader(value = "token", required = false) String token,
            @RequestParam(value = "material_id", required = false) String materialId,
            @RequestParam(value = "knowledge_id", required = false) String knowledgeId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam("page_no") Integer pageNo,
            @RequestParam("page_size") Integer pageSize) {
        User actor = authService.requireRole(token, "TEACHER", "EDU_ADMIN");
        return ApiResponse.success(knowledgeService.listChunks(materialId, knowledgeId, status, pageNo, pageSize, actor));
    }

    @GetMapping("/api/knowledge/points")
    public ApiResponse<Map<String, Object>> knowledgePoints(
            @RequestHeader(value = "token", required = false) String token,
            @RequestParam(value = "course_id", required = false) String courseId,
            @RequestParam(value = "chapter_id", required = false) String chapterId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam("page_no") Integer pageNo,
            @RequestParam("page_size") Integer pageSize) {
        User actor = authService.requireRole(token, "STUDENT", "TEACHER", "EDU_ADMIN");
        return ApiResponse.success(knowledgeService.listKnowledgePoints(courseId, chapterId, keyword, pageNo, pageSize, actor));
    }

    @PostMapping("/api/knowledge/points")
    public ApiResponse<Map<String, Object>> createKnowledgePoint(
            @RequestHeader(value = "token", required = false) String token,
            @RequestBody Map<String, Object> request) {
        User actor = authService.requireRole(token, "TEACHER", "EDU_ADMIN");
        return ApiResponse.success(knowledgeService.createKnowledgePoint(request, actor));
    }

    @PutMapping("/api/knowledge/points/{knowledgeId}")
    public ApiResponse<Map<String, Object>> updateKnowledgePoint(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable String knowledgeId,
            @RequestBody Map<String, Object> request) {
        User actor = authService.requireRole(token, "TEACHER", "EDU_ADMIN");
        return ApiResponse.success(knowledgeService.updateKnowledgePoint(knowledgeId, request, actor));
    }

    @PostMapping("/api/knowledge/chunks/{chunkId}/audit")
    public ApiResponse<Map<String, Object>> auditChunk(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable String chunkId,
            @RequestBody Map<String, String> request) {
        User actor = authService.requireRole(token, "TEACHER", "EDU_ADMIN");
        String status = request.getOrDefault("status", request.get("audit_status"));
        return ApiResponse.success(knowledgeService.auditChunk(chunkId, status, request.get("audit_comment"), actor));
    }

    @PostMapping("/api/qa")
    public ApiResponse<Map<String, Object>> qa(
            @RequestHeader(value = "token", required = false) String token,
            @RequestBody Map<String, String> request) {
        User actor = authService.requireRole(token, "STUDENT");
        return ApiResponse.success(knowledgeService.answer(
                request.get("course_id"), request.get("chapter_id"), request.get("question_text"), actor));
    }
}
