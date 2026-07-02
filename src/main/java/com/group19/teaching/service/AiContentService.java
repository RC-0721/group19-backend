package com.group19.teaching.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import com.group19.teaching.domain.entity.User;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.apache.tika.Tika;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AiContentService {

    private static final List<String> PARSEABLE_TYPES = List.of("txt", "md", "pdf", "doc", "docx", "ppt", "pptx");
    private static final List<String> VIDEO_TYPES = List.of("mp4", "mov", "avi", "mkv");
    private static final List<String> QUESTION_TYPES = List.of("单选题", "多选题", "判断题", "简答题", "编程题");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Tika tika = new Tika();

    public AiContentService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> parseMaterial(Map<String, Object> request, User actor) {
        String materialId = stringValue(request.get("material_id"));
        if (!StringUtils.hasText(materialId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        Map<String, Object> material = material(materialId);
        requireTeacherCourse(stringValue(material.get("course_id")), actor);

        String taskId = "parse-" + UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO material_parse_task
                  (parse_task_id, material_id, task_type, task_status, created_by, started_time)
                VALUES (?, ?, 'MATERIAL_PARSE', '解析中', ?, ?)
                """, taskId, materialId, actor.getAccount(), Timestamp.valueOf(now));
        jdbcTemplate.update("UPDATE course_material SET parse_status = '解析中' WHERE material_id = ?", materialId);

        try {
            Map<String, Object> result = buildParseResult(material);
            jdbcTemplate.update("""
                    UPDATE material_parse_task
                    SET task_status = '待确认', result_json = ?, finished_time = ?
                    WHERE parse_task_id = ?
                    """, json(result), Timestamp.valueOf(LocalDateTime.now()), taskId);
            jdbcTemplate.update("UPDATE course_material SET parse_status = '待确认' WHERE material_id = ?", materialId);
            return Map.of("parse_task_id", taskId, "task_status", "待确认", "material_id", materialId);
        } catch (RuntimeException exception) {
            String message = shortMessage(exception);
            jdbcTemplate.update("""
                    UPDATE material_parse_task
                    SET task_status = '解析失败', error_message = ?, finished_time = ?
                    WHERE parse_task_id = ?
                    """, message, Timestamp.valueOf(LocalDateTime.now()), taskId);
            jdbcTemplate.update("UPDATE course_material SET parse_status = '解析失败' WHERE material_id = ?", materialId);
            return Map.of("parse_task_id", taskId, "task_status", "解析失败", "error_message", message);
        }
    }

    public Map<String, Object> getParseTask(String taskId, User actor) {
        Map<String, Object> task = parseTask(taskId);
        requireTeacherCourse(stringValue(task.get("course_id")), actor);
        return task;
    }

    @Transactional
    public Map<String, Object> confirmParseTask(String taskId, User actor) {
        Map<String, Object> task = parseTask(taskId);
        requireTeacherCourse(stringValue(task.get("course_id")), actor);
        if (!"待确认".equals(stringValue(task.get("task_status")))) {
            throw new BusinessException(ErrorCode.STATE_NOT_ALLOWED);
        }
        Map<String, Object> result = objectMap(stringValue(task.get("result_json")));
        String materialId = stringValue(task.get("material_id"));
        jdbcTemplate.update("""
                UPDATE course_material
                SET parse_status = '已确认', ai_tags = ?, ai_category = ?, content_summary = ?
                WHERE material_id = ?
                """, json(result.getOrDefault("tags", List.of())), stringValue(result.get("category")),
                stringValue(result.get("summary")), materialId);
        for (Map<String, Object> chunk : objectList(result.get("chunks"))) {
            String text = stringValue(chunk.get("chunk_text"));
            if (StringUtils.hasText(text)) {
                jdbcTemplate.update("""
                        INSERT INTO knowledge_chunk
                          (chunk_id, material_id, knowledge_id, chunk_text, embedding_id, version, status)
                        VALUES (?, ?, NULL, ?, NULL, 'v1', '待审核')
                        """, "chunk-" + UUID.randomUUID(), materialId, text);
            }
        }
        jdbcTemplate.update("""
                UPDATE material_parse_task
                SET task_status = '已确认', finished_time = ?
                WHERE parse_task_id = ?
                """, Timestamp.valueOf(LocalDateTime.now()), taskId);
        return Map.of("parse_task_id", taskId, "task_status", "已确认", "material_id", materialId);
    }

    @Transactional
    public Map<String, Object> buildKnowledgeGraph(Map<String, Object> request, User actor) {
        String materialId = stringValue(request.get("material_id"));
        String courseId = stringValue(request.get("course_id"));
        String chapterId = stringValue(request.get("chapter_id"));
        if (StringUtils.hasText(materialId)) {
            Map<String, Object> material = material(materialId);
            courseId = stringValue(material.get("course_id"));
            chapterId = stringValue(material.get("chapter_id"));
        }
        if (!StringUtils.hasText(courseId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        requireTeacherCourse(courseId, actor);
        String taskId = createAiTask("KNOWLEDGE_GRAPH_BUILD", actor, request);
        int created = createKnowledgeCandidates(taskId, courseId, chapterId, materialId, actor);
        finishAiTask(taskId, Map.of("created_candidates", created), null);
        return Map.of("task_id", taskId, "task_status", "已完成", "created_candidates", created);
    }

    public Map<String, Object> listKnowledgeCandidates(
            String courseId, String auditStatus, Integer pageNo, Integer pageSize, User actor) {
        validatePage(pageNo, pageSize);
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder("WHERE 1 = 1\n");
        if (StringUtils.hasText(courseId)) {
            requireTeacherCourse(courseId, actor);
            where.append("AND course_id = ?\n");
            params.add(courseId);
        } else if ("TEACHER".equalsIgnoreCase(actor.getRole())) {
            where.append("""
                    AND EXISTS (
                        SELECT 1 FROM course_class cc
                        WHERE cc.course_id = knowledge_candidate.course_id AND cc.teacher_id = ?
                    )
                    """);
            params.add(actor.getAccount());
        }
        if (StringUtils.hasText(auditStatus)) {
            where.append("AND audit_status = ?\n");
            params.add(auditStatus);
        }
        Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM knowledge_candidate " + where,
                Integer.class, params.toArray());
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(pageSize);
        pageParams.add((pageNo - 1) * pageSize);
        List<Map<String, Object>> records = jdbcTemplate.queryForList("""
                SELECT candidate_id, candidate_type, course_id, chapter_id, material_id, source_task_id,
                       name, description, parent_id, source_knowledge_id, target_knowledge_id,
                       relation_type, confidence, chunk_id, raw_output_json, audit_status,
                       created_by, created_time, reviewed_time
                FROM knowledge_candidate
                """ + where + """
                ORDER BY created_time DESC, candidate_id
                LIMIT ? OFFSET ?
                """, pageParams.toArray());
        return page(records, total, pageNo, pageSize);
    }

    @Transactional
    public Map<String, Object> approveKnowledgeCandidate(String candidateId, User actor) {
        Map<String, Object> candidate = knowledgeCandidate(candidateId);
        requireTeacherCourse(stringValue(candidate.get("course_id")), actor);
        if (!"待审核".equals(stringValue(candidate.get("audit_status")))) {
            throw new BusinessException(ErrorCode.STATE_NOT_ALLOWED);
        }
        String type = stringValue(candidate.get("candidate_type"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("candidate_id", candidateId);
        if ("KNOWLEDGE_POINT".equals(type)) {
            String knowledgeId = "kp-" + UUID.randomUUID();
            jdbcTemplate.update("""
                    INSERT INTO knowledge_point
                      (knowledge_id, course_id, chapter_id, name, description, level, source,
                       audit_status, parent_id, sort_order)
                    VALUES (?, ?, ?, ?, ?, ?, 'ai_candidate', '已发布', ?, 0)
                    """, knowledgeId, candidate.get("course_id"), candidate.get("chapter_id"),
                    candidate.get("name"), candidate.get("description"), "AI", candidate.get("parent_id"));
            result.put("knowledge_id", knowledgeId);
        } else if ("KNOWLEDGE_RELATION".equals(type)) {
            String relationId = "kr-" + UUID.randomUUID();
            jdbcTemplate.update("""
                    INSERT INTO knowledge_relation
                      (relation_id, source_knowledge_id, target_knowledge_id, relation_type, confidence, audit_status)
                    VALUES (?, ?, ?, ?, ?, '已发布')
                    """, relationId, candidate.get("source_knowledge_id"), candidate.get("target_knowledge_id"),
                    candidate.get("relation_type"), candidate.get("confidence"));
            result.put("relation_id", relationId);
        } else {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        jdbcTemplate.update("""
                UPDATE knowledge_candidate
                SET audit_status = '已通过', reviewed_time = ?
                WHERE candidate_id = ?
                """, Timestamp.valueOf(LocalDateTime.now()), candidateId);
        result.put("audit_status", "已通过");
        return result;
    }

    @Transactional
    public Map<String, Object> generateQuestions(Map<String, Object> request, User actor) {
        String knowledgeId = stringValue(request.get("knowledge_id"));
        String materialId = stringValue(request.get("material_id"));
        Map<String, Object> scope = questionScope(knowledgeId, materialId);
        requireTeacherCourse(stringValue(scope.get("course_id")), actor);
        String taskId = createAiTask("QUESTION_GENERATE", actor, request);
        List<String> types = requestedQuestionTypes(request.get("question_types"));
        int created = 0;
        for (String type : types) {
            createQuestionCandidate(taskId, scope, type, actor);
            created++;
        }
        finishAiTask(taskId, Map.of("created_candidates", created), null);
        return Map.of("task_id", taskId, "task_status", "已完成", "created_candidates", created);
    }

    public Map<String, Object> listQuestionCandidates(
            String knowledgeId, String auditStatus, Integer pageNo, Integer pageSize, User actor) {
        validatePage(pageNo, pageSize);
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder("WHERE 1 = 1\n");
        if (StringUtils.hasText(knowledgeId)) {
            where.append("AND knowledge_id = ?\n");
            params.add(knowledgeId);
        }
        if (StringUtils.hasText(auditStatus)) {
            where.append("AND audit_status = ?\n");
            params.add(auditStatus);
        }
        if ("TEACHER".equalsIgnoreCase(actor.getRole())) {
            where.append("""
                    AND EXISTS (
                        SELECT 1 FROM course_class cc
                        WHERE cc.course_id = question_candidate.course_id AND cc.teacher_id = ?
                    )
                    """);
            params.add(actor.getAccount());
        }
        Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM question_candidate " + where,
                Integer.class, params.toArray());
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(pageSize);
        pageParams.add((pageNo - 1) * pageSize);
        List<Map<String, Object>> records = jdbcTemplate.queryForList("""
                SELECT candidate_id, source_task_id, material_id, course_id, chapter_id, question_type,
                       stem, difficulty, options_json, answer, answer_analysis, knowledge_id, job_id,
                       tech_id, duplicate_status, raw_output_json, audit_status, created_by,
                       created_time, reviewed_time
                FROM question_candidate
                """ + where + """
                ORDER BY created_time DESC, candidate_id
                LIMIT ? OFFSET ?
                """, pageParams.toArray());
        return page(records, total, pageNo, pageSize);
    }

    @Transactional
    public Map<String, Object> approveQuestionCandidate(String candidateId, User actor) {
        Map<String, Object> candidate = questionCandidate(candidateId);
        requireTeacherCourse(stringValue(candidate.get("course_id")), actor);
        if (!"待审核".equals(stringValue(candidate.get("audit_status")))) {
            throw new BusinessException(ErrorCode.STATE_NOT_ALLOWED);
        }
        String questionId = "q-" + UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO question
                  (question_id, source_id, source_path, source_url, question_type, stem, difficulty,
                   answer, answer_analysis, audit_status)
                VALUES (?, NULL, ?, NULL, ?, ?, ?, ?, ?, '已发布')
                """, questionId, candidate.get("material_id"), candidate.get("question_type"),
                candidate.get("stem"), candidate.get("difficulty"), candidate.get("answer"),
                candidate.get("answer_analysis"));
        for (Map<String, Object> option : objectList(candidate.get("options_json"))) {
            jdbcTemplate.update("""
                    INSERT INTO question_option (option_id, question_id, option_label, option_content, is_correct)
                    VALUES (?, ?, ?, ?, ?)
                    """, "qo-" + UUID.randomUUID(), questionId, option.get("option_label"),
                    option.get("option_content"), booleanValue(option.get("is_correct")));
        }
        String knowledgeId = stringValue(candidate.get("knowledge_id"));
        if (StringUtils.hasText(knowledgeId)) {
            jdbcTemplate.update("""
                    INSERT INTO question_knowledge_relation (relation_id, question_id, knowledge_id, weight)
                    VALUES (?, ?, ?, 1.0)
                    """, "qk-" + UUID.randomUUID(), questionId, knowledgeId);
        }
        String jobId = stringValue(candidate.get("job_id"));
        String techId = stringValue(candidate.get("tech_id"));
        if (StringUtils.hasText(jobId) && StringUtils.hasText(techId)) {
            jdbcTemplate.update("""
                    INSERT INTO question_job_relation (relation_id, question_id, job_id, tech_id, match_level)
                    VALUES (?, ?, ?, ?, '核心')
                    """, "qj-" + UUID.randomUUID(), questionId, jobId, techId);
        }
        jdbcTemplate.update("""
                UPDATE question_candidate
                SET audit_status = '已通过', reviewed_time = ?
                WHERE candidate_id = ?
                """, Timestamp.valueOf(LocalDateTime.now()), candidateId);
        return Map.of("candidate_id", candidateId, "question_id", questionId, "audit_status", "已通过");
    }

    @Transactional
    public Map<String, Object> rejectQuestionCandidate(String candidateId, User actor) {
        Map<String, Object> candidate = questionCandidate(candidateId);
        requireTeacherCourse(stringValue(candidate.get("course_id")), actor);
        if (!"待审核".equals(stringValue(candidate.get("audit_status")))) {
            throw new BusinessException(ErrorCode.STATE_NOT_ALLOWED);
        }
        jdbcTemplate.update("""
                UPDATE question_candidate
                SET audit_status = '已驳回', reviewed_time = ?
                WHERE candidate_id = ?
                """, Timestamp.valueOf(LocalDateTime.now()), candidateId);
        return Map.of("candidate_id", candidateId, "audit_status", "已驳回");
    }

    String extractText(Path path) {
        try (InputStream inputStream = Files.newInputStream(path)) {
            return tika.parseToString(inputStream).replaceAll("\\s+", " ").trim();
        } catch (IOException | org.apache.tika.exception.TikaException exception) {
            throw new IllegalStateException("Material content cannot be parsed", exception);
        }
    }

    private Map<String, Object> buildParseResult(Map<String, Object> material) {
        String fileType = stringValue(material.get("file_type")).toLowerCase(Locale.ROOT);
        String materialId = stringValue(material.get("material_id"));
        String fileName = stringValue(material.get("file_name"));
        if (VIDEO_TYPES.contains(fileType)) {
            return linkedMap(
                    "material_id", materialId,
                    "summary", "视频资料已保存元数据，音频转写将在后续阶段处理。",
                    "category", "视频资料",
                    "tags", List.of(fileType, "video"),
                    "chunks", List.of(),
                    "raw", Map.of("file_name", fileName, "file_type", fileType)
            );
        }
        if (!PARSEABLE_TYPES.contains(fileType)) {
            throw new IllegalStateException("Unsupported material type: " + fileType);
        }
        String text = extractText(Path.of(stringValue(material.get("storage_path"))));
        if (!StringUtils.hasText(text)) {
            throw new IllegalStateException("Parsed material text is empty");
        }
        List<Map<String, Object>> chunks = chunks(text);
        return linkedMap(
                "material_id", materialId,
                "summary", limit(text, 240),
                "category", category(fileType),
                "tags", tags(fileName, text),
                "chunks", chunks,
                "raw", Map.of("text_length", text.length(), "file_type", fileType)
        );
    }

    private int createKnowledgeCandidates(
            String taskId, String courseId, String chapterId, String materialId, User actor) {
        List<String> names = candidateNames(courseId, materialId);
        int count = 0;
        for (String name : names) {
            jdbcTemplate.update("""
                    INSERT INTO knowledge_candidate
                      (candidate_id, candidate_type, course_id, chapter_id, material_id, source_task_id,
                       name, description, raw_output_json, audit_status, created_by)
                    VALUES (?, 'KNOWLEDGE_POINT', ?, ?, ?, ?, ?, ?, ?, '待审核', ?)
                    """, "kcand-" + UUID.randomUUID(), courseId, blankToNull(chapterId), blankToNull(materialId),
                    taskId, name, "AI 根据资料生成的知识点候选", json(Map.of("name", name)), actor.getAccount());
            count++;
        }
        List<Map<String, Object>> points = jdbcTemplate.queryForList("""
                SELECT knowledge_id
                FROM knowledge_point
                WHERE course_id = ?
                ORDER BY knowledge_id
                LIMIT 2
                """, courseId);
        if (points.size() == 2) {
            jdbcTemplate.update("""
                    INSERT INTO knowledge_candidate
                      (candidate_id, candidate_type, course_id, chapter_id, material_id, source_task_id,
                       source_knowledge_id, target_knowledge_id, relation_type, confidence, raw_output_json,
                       audit_status, created_by)
                    VALUES (?, 'KNOWLEDGE_RELATION', ?, ?, ?, ?, ?, ?, 'related', 0.75, ?, '待审核', ?)
                    """, "kcand-" + UUID.randomUUID(), courseId, blankToNull(chapterId), blankToNull(materialId),
                    taskId, points.get(0).get("knowledge_id"), points.get(1).get("knowledge_id"),
                    json(Map.of("relation_type", "related")), actor.getAccount());
            count++;
        }
        return count;
    }

    private List<String> candidateNames(String courseId, String materialId) {
        List<Map<String, Object>> chunks = StringUtils.hasText(materialId)
                ? jdbcTemplate.queryForList("""
                        SELECT chunk_text
                        FROM knowledge_chunk
                        WHERE material_id = ?
                        ORDER BY chunk_id
                        LIMIT 3
                        """, materialId)
                : jdbcTemplate.queryForList("""
                        SELECT kc.chunk_text
                        FROM knowledge_chunk kc
                        JOIN course_material cm ON kc.material_id = cm.material_id
                        WHERE cm.course_id = ?
                        ORDER BY kc.chunk_id
                        LIMIT 3
                        """, courseId);
        List<String> names = new ArrayList<>();
        for (Map<String, Object> chunk : chunks) {
            String text = stringValue(chunk.get("chunk_text"));
            if (StringUtils.hasText(text)) {
                names.add(limit(text, 18));
            }
        }
        if (names.isEmpty()) {
            names.add("AI 资料核心概念");
        }
        return names;
    }

    private void createQuestionCandidate(String taskId, Map<String, Object> scope, String type, User actor) {
        String knowledgeName = stringValue(scope.getOrDefault("knowledge_name", "课程知识点"));
        String stem = switch (type) {
            case "单选题" -> knowledgeName + " 的核心概念是什么？";
            case "多选题" -> knowledgeName + " 的相关能力要求包括哪些？";
            case "判断题" -> knowledgeName + " 可以直接用于课程学习评价。";
            case "编程题" -> "请编写一个示例程序说明 " + knowledgeName + " 的应用。";
            default -> "请简述 " + knowledgeName + " 的主要内容。";
        };
        List<Map<String, Object>> options = options(type);
        String duplicateStatus = duplicateStatus(stringValue(scope.get("knowledge_id")), stem);
        jdbcTemplate.update("""
                INSERT INTO question_candidate
                  (candidate_id, source_task_id, material_id, course_id, chapter_id, question_type, stem,
                   difficulty, options_json, answer, answer_analysis, knowledge_id, job_id, tech_id,
                   duplicate_status, raw_output_json, audit_status, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, '中等', ?, ?, ?, ?, ?, ?, ?, ?, '待审核', ?)
                """, "qcand-" + UUID.randomUUID(), taskId, scope.get("material_id"), scope.get("course_id"),
                scope.get("chapter_id"), type, stem, json(options), answer(type), "AI 生成候选，需人工审核后发布。",
                scope.get("knowledge_id"), firstNonBlank(scope.get("job_id"), "job-java-backend"),
                firstNonBlank(scope.get("tech_id"), "tech-001"), duplicateStatus,
                json(Map.of("question_type", type, "stem", stem)), actor.getAccount());
    }

    private Map<String, Object> questionScope(String knowledgeId, String materialId) {
        if (StringUtils.hasText(knowledgeId)) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                    SELECT knowledge_id, course_id, chapter_id, name AS knowledge_name
                    FROM knowledge_point
                    WHERE knowledge_id = ?
                    LIMIT 1
                    """, knowledgeId);
            if (rows.isEmpty()) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
            }
            return new LinkedHashMap<>(rows.get(0));
        }
        if (StringUtils.hasText(materialId)) {
            Map<String, Object> material = material(materialId);
            Map<String, Object> scope = new LinkedHashMap<>();
            scope.put("material_id", materialId);
            scope.put("course_id", material.get("course_id"));
            scope.put("chapter_id", material.get("chapter_id"));
            scope.put("knowledge_name", firstNonBlank(material.get("content_summary"), material.get("file_name")));
            return scope;
        }
        throw new BusinessException(ErrorCode.PARAM_ERROR);
    }

    private Map<String, Object> material(String materialId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT material_id, course_id, chapter_id, file_name, file_type, storage_path,
                       parse_status, ai_tags, ai_category, content_summary
                FROM course_material
                WHERE material_id = ?
                LIMIT 1
                """, materialId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return rows.get(0);
    }

    private Map<String, Object> parseTask(String taskId) {
        if (!StringUtils.hasText(taskId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT mpt.parse_task_id, mpt.material_id, cm.course_id, cm.chapter_id,
                       mpt.task_type, mpt.task_status, mpt.result_json, mpt.error_message,
                       mpt.created_by, mpt.started_time, mpt.finished_time
                FROM material_parse_task mpt
                JOIN course_material cm ON mpt.material_id = cm.material_id
                WHERE mpt.parse_task_id = ?
                LIMIT 1
                """, taskId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return rows.get(0);
    }

    private Map<String, Object> knowledgeCandidate(String candidateId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT candidate_id, candidate_type, course_id, chapter_id, material_id, source_task_id,
                       name, description, parent_id, source_knowledge_id, target_knowledge_id,
                       relation_type, confidence, chunk_id, raw_output_json, audit_status
                FROM knowledge_candidate
                WHERE candidate_id = ?
                LIMIT 1
                """, candidateId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return rows.get(0);
    }

    private Map<String, Object> questionCandidate(String candidateId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT candidate_id, source_task_id, material_id, course_id, chapter_id, question_type,
                       stem, difficulty, options_json, answer, answer_analysis, knowledge_id, job_id,
                       tech_id, duplicate_status, raw_output_json, audit_status
                FROM question_candidate
                WHERE candidate_id = ?
                LIMIT 1
                """, candidateId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return rows.get(0);
    }

    private String createAiTask(String taskType, User actor, Map<String, Object> request) {
        String taskId = "ai-task-" + UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO ai_task
                  (task_id, task_type, created_by, request_json, task_status, created_time, started_time)
                VALUES (?, ?, ?, ?, '运行中', ?, ?)
                """, taskId, taskType, actor.getAccount(), json(request), Timestamp.valueOf(now), Timestamp.valueOf(now));
        return taskId;
    }

    private void finishAiTask(String taskId, Map<String, Object> result, String errorMessage) {
        if (StringUtils.hasText(errorMessage)) {
            jdbcTemplate.update("""
                    UPDATE ai_task
                    SET task_status = '失败', error_message = ?, finished_time = ?
                    WHERE task_id = ?
                    """, errorMessage, Timestamp.valueOf(LocalDateTime.now()), taskId);
            return;
        }
        jdbcTemplate.update("""
                UPDATE ai_task
                SET task_status = '已完成', result_json = ?, finished_time = ?
                WHERE task_id = ?
                """, json(result), Timestamp.valueOf(LocalDateTime.now()), taskId);
    }

    private void requireTeacherCourse(String courseId, User actor) {
        if (!StringUtils.hasText(courseId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        if ("EDU_ADMIN".equalsIgnoreCase(actor.getRole())) {
            return;
        }
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM course_class
                WHERE course_id = ? AND teacher_id = ?
                """, Integer.class, courseId, actor.getAccount());
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private String duplicateStatus(String knowledgeId, String stem) {
        if (!StringUtils.hasText(knowledgeId)) {
            return "未发现重复";
        }
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM question q
                JOIN question_knowledge_relation qkr ON q.question_id = qkr.question_id
                WHERE qkr.knowledge_id = ? AND q.stem = ?
                """, Integer.class, knowledgeId, stem);
        return count != null && count > 0 ? "疑似重复" : "未发现重复";
    }

    private List<Map<String, Object>> chunks(String text) {
        List<Map<String, Object>> chunks = new ArrayList<>();
        for (int start = 0, index = 1; start < text.length(); start += 800, index++) {
            chunks.add(linkedMap(
                    "chunk_no", index,
                    "chunk_text", text.substring(start, Math.min(start + 800, text.length())),
                    "suggested_knowledge_id", null
            ));
        }
        return chunks;
    }

    private List<String> tags(String fileName, String text) {
        List<String> tags = new ArrayList<>();
        for (String part : fileName.replace('.', ' ').split("[_\\-\\s]+")) {
            if (StringUtils.hasText(part) && tags.size() < 3) {
                tags.add(part.toLowerCase(Locale.ROOT));
            }
        }
        if (text.contains("Java")) {
            tags.add("Java");
        }
        if (text.contains("Spring")) {
            tags.add("Spring");
        }
        return tags.stream().distinct().limit(5).toList();
    }

    private String category(String fileType) {
        return switch (fileType) {
            case "pdf", "doc", "docx" -> "文档资料";
            case "ppt", "pptx" -> "课件资料";
            default -> "文本资料";
        };
    }

    private List<String> requestedQuestionTypes(Object value) {
        if (value == null) {
            return QUESTION_TYPES;
        }
        List<String> types = new ArrayList<>();
        if (value instanceof List<?> values) {
            for (Object item : values) {
                String type = stringValue(item);
                if (!QUESTION_TYPES.contains(type)) {
                    throw new BusinessException(ErrorCode.PARAM_ERROR);
                }
                types.add(type);
            }
        } else {
            String type = stringValue(value);
            if (!QUESTION_TYPES.contains(type)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR);
            }
            types.add(type);
        }
        return types.isEmpty() ? QUESTION_TYPES : types;
    }

    private List<Map<String, Object>> options(String type) {
        if ("单选题".equals(type)) {
            return List.of(
                    linkedMap("option_label", "A", "option_content", "核心概念与应用场景", "is_correct", true),
                    linkedMap("option_label", "B", "option_content", "与课程目标无关的内容", "is_correct", false),
                    linkedMap("option_label", "C", "option_content", "仅用于系统配置", "is_correct", false),
                    linkedMap("option_label", "D", "option_content", "只包含记忆性定义", "is_correct", false)
            );
        }
        if ("多选题".equals(type)) {
            return List.of(
                    linkedMap("option_label", "A", "option_content", "概念理解", "is_correct", true),
                    linkedMap("option_label", "B", "option_content", "实践应用", "is_correct", true),
                    linkedMap("option_label", "C", "option_content", "问题分析", "is_correct", true),
                    linkedMap("option_label", "D", "option_content", "无关知识", "is_correct", false)
            );
        }
        if ("判断题".equals(type)) {
            return List.of(
                    linkedMap("option_label", "T", "option_content", "正确", "is_correct", true),
                    linkedMap("option_label", "F", "option_content", "错误", "is_correct", false)
            );
        }
        return List.of();
    }

    private String answer(String type) {
        return switch (type) {
            case "单选题" -> "A";
            case "多选题" -> "A,B,C";
            case "判断题" -> "正确";
            case "编程题" -> "参考实现需体现核心概念、输入输出和异常处理。";
            default -> "围绕概念、应用场景和注意事项作答。";
        };
    }

    private void validatePage(Integer pageNo, Integer pageSize) {
        if (pageNo == null || pageNo < 1 || pageSize == null || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
    }

    private Map<String, Object> page(List<Map<String, Object>> records, Integer total, Integer pageNo, Integer pageSize) {
        return Map.of("records", records, "total", total == null ? 0 : total, "page_no", pageNo, "page_size", pageSize);
    }

    private List<Map<String, Object>> objectList(Object value) {
        try {
            if (value instanceof String text) {
                return StringUtils.hasText(text)
                        ? objectMapper.readValue(text, new TypeReference<>() {})
                        : List.of();
            }
            return objectMapper.convertValue(value, new TypeReference<>() {});
        } catch (IllegalArgumentException | JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
    }

    private Map<String, Object> objectMap(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
    }

    private String shortMessage(RuntimeException exception) {
        Throwable cause = exception.getCause() == null ? exception : exception.getCause();
        String message = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
        return limit(message, 500);
    }

    private String limit(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    private Object blankToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private String firstNonBlank(Object first, Object fallback) {
        String firstValue = stringValue(first);
        return StringUtils.hasText(firstValue) ? firstValue : stringValue(fallback);
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(stringValue(value));
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Map<String, Object> linkedMap(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            map.put(String.valueOf(values[index]), values[index + 1]);
        }
        return map;
    }
}
