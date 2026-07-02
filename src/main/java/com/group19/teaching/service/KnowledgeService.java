package com.group19.teaching.service;

import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import com.group19.teaching.domain.entity.User;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class KnowledgeService {

    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;
    private static final List<String> MATERIAL_STATUSES = List.of("待解析", "解析中", "待确认", "已确认", "已上传", "待审核", "已发布", "解析失败");
    private static final List<String> KNOWLEDGE_STATUSES = List.of("待审核", "已发布", "已驳回", "停用");
    private static final List<String> CHUNK_STATUSES = List.of("待审核", "已发布", "已驳回");

    private final JdbcTemplate jdbcTemplate;
    private final Path uploadDir;

    public KnowledgeService(JdbcTemplate jdbcTemplate, @Value("${teaching.upload-dir:data/uploads}") String uploadDir) {
        this.jdbcTemplate = jdbcTemplate;
        this.uploadDir = Path.of(uploadDir);
    }

    public Map<String, Object> uploadFile(MultipartFile file, User actor) {
        if (actor == null || file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        rejectTooLarge(file);
        String fileName = cleanFileName(file.getOriginalFilename());
        String fileType = fileType(fileName);
        if (!List.of("txt", "md", "pdf", "doc", "docx", "zip").contains(fileType)) {
            throw new BusinessException(ErrorCode.FILE_INVALID);
        }

        Path root = uploadDir.toAbsolutePath().normalize();
        Path target = root.resolve("file-" + UUID.randomUUID() + "." + fileType).normalize();
        if (!target.startsWith(root)) {
            throw new BusinessException(ErrorCode.FILE_INVALID);
        }
        try {
            Files.createDirectories(root);
            file.transferTo(target);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.FILE_INVALID);
        }
        return Map.of("file_name", fileName, "file_type", fileType, "storage_path", target.toString());
    }

    @Transactional
    public Map<String, Object> uploadMaterial(String courseId, String chapterId, MultipartFile file, User actor) {
        if (!StringUtils.hasText(courseId) || !StringUtils.hasText(chapterId)
                || file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        rejectTooLarge(file);
        requireTeacherCourse(courseId, actor);
        requireChapter(courseId, chapterId);
        String fileName = cleanFileName(file.getOriginalFilename());
        String fileType = fileType(fileName);
        if (!List.of("txt", "md", "pdf", "doc", "docx", "ppt", "pptx", "mp4", "mov", "avi").contains(fileType)) {
            throw new BusinessException(ErrorCode.FILE_INVALID);
        }

        String materialId = "material-" + UUID.randomUUID();
        String parseTaskId = "parse-" + UUID.randomUUID();
        Path root = uploadDir.toAbsolutePath().normalize();
        Path target = root.resolve(materialId + "." + fileType).normalize();
        if (!target.startsWith(root)) {
            throw new BusinessException(ErrorCode.FILE_INVALID);
        }
        try {
            Files.createDirectories(root);
            file.transferTo(target);
            jdbcTemplate.update("""
                    INSERT INTO course_material (material_id, course_id, chapter_id, file_name, file_type, storage_path, parse_status)
                    VALUES (?, ?, ?, ?, ?, ?, '待解析')
                    """, materialId, courseId, chapterId, fileName, fileType, target.toString());
            jdbcTemplate.update("""
                    INSERT INTO material_parse_task (parse_task_id, material_id, task_type, task_status, created_by, started_time)
                    VALUES (?, ?, 'MATERIAL_PARSE', '待解析', ?, ?)
                    """, parseTaskId, materialId, actor.getAccount(), LocalDateTime.now());
            writeOperationLog(actor, "MATERIAL", "CREATE_MATERIAL");
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.FILE_INVALID);
        }
        return Map.of("material_id", materialId, "parse_status", "待解析", "parse_task_id", parseTaskId);
    }

    public Map<String, Object> listMaterials(String courseId, String chapterId, String parseStatus,
                                             Integer pageNo, Integer pageSize, User actor) {
        validatePage(pageNo, pageSize);
        String status = normalize(parseStatus, MATERIAL_STATUSES, false);

        List<Object> params = new ArrayList<>();
        String where = materialWhere(courseId, chapterId, status, actor, params);
        Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM course_material cm " + where,
                Integer.class, params.toArray());

        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(pageSize);
        pageParams.add((pageNo - 1) * pageSize);
        List<Map<String, Object>> records = jdbcTemplate.queryForList("""
                SELECT cm.material_id, cm.course_id, cm.chapter_id, cm.file_name, cm.file_type,
                       cm.storage_path, cm.parse_status
                FROM course_material cm
                """ + where + """
                ORDER BY cm.material_id
                LIMIT ? OFFSET ?
                """, pageParams.toArray());
        return page(records, total, pageNo, pageSize);
    }

    @Transactional
    public Map<String, Object> updateMaterial(String materialId, Map<String, Object> request, User actor) {
        if (!StringUtils.hasText(materialId) || request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        Map<String, Object> current = material(materialId);
        String courseId = request.containsKey("course_id")
                ? stringValue(request.get("course_id"))
                : stringValue(current.get("course_id"));
        if (!StringUtils.hasText(courseId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        requireCourse(courseId);
        requireTeacherCourse(courseId, actor);

        String chapterId = request.containsKey("chapter_id")
                ? stringValue(request.get("chapter_id"))
                : stringValue(current.get("chapter_id"));
        if (StringUtils.hasText(chapterId)) {
            requireChapter(courseId, chapterId);
        }

        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("UPDATE course_material SET material_id = material_id");
        if (request.containsKey("course_id")) {
            sql.append(", course_id = ?");
            params.add(courseId);
        }
        if (request.containsKey("chapter_id")) {
            sql.append(", chapter_id = ?");
            params.add(StringUtils.hasText(chapterId) ? chapterId : null);
        }
        if (request.containsKey("file_name")) {
            sql.append(", file_name = ?");
            params.add(cleanFileName(stringValue(request.get("file_name"))));
        }
        String parseStatus = stringValue(current.get("parse_status"));
        if (request.containsKey("parse_status")) {
            parseStatus = normalize(stringValue(request.get("parse_status")), MATERIAL_STATUSES, true);
            sql.append(", parse_status = ?");
            params.add(parseStatus);
        }
        if (params.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        sql.append(" WHERE material_id = ?");
        params.add(materialId);
        jdbcTemplate.update(sql.toString(), params.toArray());
        writeOperationLog(actor, "MATERIAL", "UPDATE_MATERIAL");
        return Map.of("material_id", materialId, "parse_status", parseStatus);
    }

    public Map<String, Object> parseTasks(String materialId, User actor) {
        Map<String, Object> current = material(materialId);
        requireTeacherCourse(stringValue(current.get("course_id")), actor);
        return Map.of("records", jdbcTemplate.queryForList("""
                SELECT parse_task_id, material_id, task_type, task_status, result_json, error_message, created_by,
                       started_time, finished_time
                FROM material_parse_task
                WHERE material_id = ?
                ORDER BY started_time DESC, parse_task_id
                """, materialId));
    }

    public Map<String, Object> listChunks(String materialId, String knowledgeId, String status, Integer pageNo, Integer pageSize, User actor) {
        validatePage(pageNo, pageSize);
        String chunkStatus = normalize(status, CHUNK_STATUSES, false);
        List<Object> params = new ArrayList<>();
        String where = chunkWhere(materialId, knowledgeId, chunkStatus, actor, params);
        Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM knowledge_chunk kc JOIN course_material cm ON kc.material_id = cm.material_id " + where,
                Integer.class, params.toArray());
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(pageSize);
        pageParams.add((pageNo - 1) * pageSize);
        List<Map<String, Object>> records = jdbcTemplate.queryForList("""
                SELECT kc.chunk_id, kc.material_id, kc.knowledge_id, kc.chunk_text, kc.version, kc.status
                FROM knowledge_chunk kc
                JOIN course_material cm ON kc.material_id = cm.material_id
                """ + where + """
                ORDER BY kc.chunk_id
                LIMIT ? OFFSET ?
                """, pageParams.toArray());
        return Map.of("records", records, "total", total == null ? 0 : total, "page_no", pageNo, "page_size", pageSize);
    }

    @Transactional
    public Map<String, Object> auditChunk(String chunkId, String status, User actor) {
        return auditChunk(chunkId, status, null, actor);
    }

    @Transactional
    public Map<String, Object> auditChunk(String chunkId, String status, String auditComment, User actor) {
        if (!StringUtils.hasText(chunkId)
                || !StringUtils.hasText(status)
                || !CHUNK_STATUSES.contains(status)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT kc.chunk_id, cm.course_id, cm.material_id
                FROM knowledge_chunk kc
                JOIN course_material cm ON kc.material_id = cm.material_id
                WHERE kc.chunk_id = ?
                LIMIT 1
                """, chunkId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        requireTeacherCourse(String.valueOf(rows.get(0).get("course_id")), actor);
        jdbcTemplate.update("UPDATE knowledge_chunk SET status = ? WHERE chunk_id = ?", status, chunkId);
        if ("已发布".equals(status)) {
            jdbcTemplate.update("UPDATE course_material SET parse_status = '已发布' WHERE material_id = ?",
                    rows.get(0).get("material_id"));
        }
        writeOperationLog(actor, "KNOWLEDGE", "AUDIT_CHUNK");
        return Map.of("chunk_id", chunkId, "status", status);
    }

    public Map<String, Object> listKnowledgePoints(String courseId, String chapterId, String keyword,
                                                   Integer pageNo, Integer pageSize, User actor) {
        validatePage(pageNo, pageSize);
        List<Object> params = new ArrayList<>();
        String where = knowledgeWhere(courseId, chapterId, keyword, actor, params);
        Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM knowledge_point kp " + where,
                Integer.class, params.toArray());

        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(pageSize);
        pageParams.add((pageNo - 1) * pageSize);
        List<Map<String, Object>> records = jdbcTemplate.queryForList("""
                SELECT kp.knowledge_id, kp.course_id, kp.chapter_id, kp.name AS knowledge_name,
                       kp.description, kp.audit_status AS status
                FROM knowledge_point kp
                """ + where + """
                ORDER BY kp.knowledge_id
                LIMIT ? OFFSET ?
                """, pageParams.toArray());
        return page(records, total, pageNo, pageSize);
    }

    @Transactional
    public Map<String, Object> createKnowledgePoint(Map<String, Object> request, User actor) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        String courseId = stringValue(request.get("course_id"));
        String chapterId = stringValue(request.get("chapter_id"));
        String name = stringValue(request.get("knowledge_name"));
        String status = normalize(stringValue(request.get("status")), KNOWLEDGE_STATUSES, true);
        if (!StringUtils.hasText(courseId) || !StringUtils.hasText(chapterId) || !StringUtils.hasText(name)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        requireCourse(courseId);
        requireChapter(courseId, chapterId);
        requireTeacherCourse(courseId, actor);

        String knowledgeId = "kp-" + UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO knowledge_point (knowledge_id, course_id, chapter_id, name, description, level, source, audit_status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, knowledgeId, courseId, chapterId, name, stringValue(request.get("description")),
                "", "manual", status);
        writeOperationLog(actor, "KNOWLEDGE", "CREATE_KNOWLEDGE_POINT");
        return Map.of("knowledge_id", knowledgeId, "status", status);
    }

    @Transactional
    public Map<String, Object> updateKnowledgePoint(String knowledgeId, Map<String, Object> request, User actor) {
        if (!StringUtils.hasText(knowledgeId) || request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        Map<String, Object> current = knowledgePoint(knowledgeId);
        String courseId = stringValue(current.get("course_id"));
        requireTeacherCourse(courseId, actor);

        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("UPDATE knowledge_point SET knowledge_id = knowledge_id");
        if (request.containsKey("chapter_id")) {
            String chapterId = stringValue(request.get("chapter_id"));
            if (StringUtils.hasText(chapterId)) {
                requireChapter(courseId, chapterId);
            }
            sql.append(", chapter_id = ?");
            params.add(StringUtils.hasText(chapterId) ? chapterId : null);
        }
        if (request.containsKey("knowledge_name")) {
            String name = stringValue(request.get("knowledge_name"));
            if (!StringUtils.hasText(name)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR);
            }
            sql.append(", name = ?");
            params.add(name);
        }
        if (request.containsKey("description")) {
            sql.append(", description = ?");
            params.add(stringValue(request.get("description")));
        }
        String status = stringValue(current.get("audit_status"));
        if (request.containsKey("status")) {
            status = normalize(stringValue(request.get("status")), KNOWLEDGE_STATUSES, true);
            sql.append(", audit_status = ?");
            params.add(status);
        }
        if (params.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        sql.append(" WHERE knowledge_id = ?");
        params.add(knowledgeId);
        jdbcTemplate.update(sql.toString(), params.toArray());
        writeOperationLog(actor, "KNOWLEDGE", "UPDATE_KNOWLEDGE_POINT");
        return Map.of("knowledge_id", knowledgeId, "status", status);
    }

    public Map<String, Object> answer(String courseId, String chapterId, String questionText, User actor) {
        if (!StringUtils.hasText(courseId) || !StringUtils.hasText(chapterId) || !StringUtils.hasText(questionText)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        requireStudentCourse(courseId, actor);
        List<Map<String, Object>> chunks = jdbcTemplate.queryForList("""
                SELECT kc.chunk_id, kc.chunk_text
                FROM knowledge_chunk kc
                JOIN course_material cm ON kc.material_id = cm.material_id
                WHERE cm.course_id = ? AND cm.chapter_id = ?
                  AND cm.parse_status = '已发布' AND kc.status = '已发布'
                ORDER BY CASE WHEN kc.chunk_id LIKE 'material-%' THEN 0 ELSE 1 END, kc.chunk_id DESC
                LIMIT 1
                """, courseId, chapterId);
        if (chunks.isEmpty()) {
            writeAiCallLog("QA", questionText, "NO_PUBLISHED_CHUNK", "FAILED");
            throw new BusinessException(ErrorCode.KNOWLEDGE_UNAVAILABLE);
        }
        String chunkText = String.valueOf(chunks.get(0).get("chunk_text"));
        writeAiCallLog("QA", questionText, String.valueOf(chunks.get(0).get("chunk_id")), "SUCCESS");
        return Map.of(
                "message_content", "根据已审核课程资料：" + chunkText,
                "reference_chunk", chunks.get(0).get("chunk_id"),
                "status", "已完成"
        );
    }

    private void createChunks(String materialId, String courseId, String chapterId, String content) {
        String knowledgeId = firstKnowledgeId(courseId, chapterId);
        String normalized = content.replaceAll("\\s+", " ").trim();
        for (int start = 0, index = 1; start < normalized.length(); start += 800, index++) {
            String text = normalized.substring(start, Math.min(start + 800, normalized.length()));
            jdbcTemplate.update("""
                    INSERT INTO knowledge_chunk (chunk_id, material_id, knowledge_id, chunk_text, embedding_id, version, status)
                    VALUES (?, ?, ?, ?, NULL, 'v1', '待审核')
                    """, materialId + "-chunk-" + index, materialId, knowledgeId, text);
        }
    }

    private String firstKnowledgeId(String courseId, String chapterId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT knowledge_id
                FROM knowledge_point
                WHERE course_id = ? AND chapter_id = ?
                ORDER BY knowledge_id
                LIMIT 1
                """, courseId, chapterId);
        return rows.isEmpty() ? null : String.valueOf(rows.get(0).get("knowledge_id"));
    }

    private String materialWhere(String courseId, String chapterId, String status, User actor, List<Object> params) {
        StringBuilder where = new StringBuilder("WHERE 1 = 1\n");
        append(where, params, "cm.course_id", courseId);
        append(where, params, "cm.chapter_id", chapterId);
        append(where, params, "cm.parse_status", status);
        appendCourseScope(where, params, "cm.course_id", actor);
        if ("STUDENT".equalsIgnoreCase(actor.getRole())) {
            where.append("AND cm.parse_status = '已发布'\n");
        }
        return where.toString();
    }

    private String chunkWhere(String materialId, String knowledgeId, String status, User actor, List<Object> params) {
        StringBuilder where = new StringBuilder("WHERE 1 = 1\n");
        append(where, params, "kc.material_id", materialId);
        append(where, params, "kc.knowledge_id", knowledgeId);
        append(where, params, "kc.status", status);
        appendCourseScope(where, params, "cm.course_id", actor);
        return where.toString();
    }

    private String knowledgeWhere(String courseId, String chapterId, String keyword, User actor, List<Object> params) {
        StringBuilder where = new StringBuilder("WHERE 1 = 1\n");
        append(where, params, "kp.course_id", courseId);
        append(where, params, "kp.chapter_id", chapterId);
        if (StringUtils.hasText(keyword)) {
            where.append("AND kp.name LIKE ?\n");
            params.add("%" + keyword.trim() + "%");
        }
        appendCourseScope(where, params, "kp.course_id", actor);
        if ("STUDENT".equalsIgnoreCase(actor.getRole())) {
            where.append("AND kp.audit_status = '已发布'\n");
        }
        return where.toString();
    }

    private void appendCourseScope(StringBuilder where, List<Object> params, String courseColumn, User actor) {
        if ("TEACHER".equalsIgnoreCase(actor.getRole())) {
            where.append("""
                    AND EXISTS (
                        SELECT 1 FROM course_class cc
                        WHERE cc.course_id = %s AND cc.teacher_id = ?
                    )
                    """.formatted(courseColumn));
            params.add(actor.getAccount());
        }
        if ("STUDENT".equalsIgnoreCase(actor.getRole())) {
            where.append("""
                    AND EXISTS (
                        SELECT 1 FROM course_class cc
                        JOIN student_profile sp ON cc.class_id = sp.class_id
                        WHERE cc.course_id = %s AND sp.student_id = ?
                    )
                    """.formatted(courseColumn));
            params.add(actor.getAccount());
        }
    }

    private void requireTeacherCourse(String courseId, User actor) {
        if ("EDU_ADMIN".equalsIgnoreCase(actor.getRole())) {
            return;
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM course_class WHERE course_id = ? AND teacher_id = ?",
                Integer.class, courseId, actor.getAccount());
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void requireStudentCourse(String courseId, User actor) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM course_class cc
                JOIN student_profile sp ON cc.class_id = sp.class_id
                WHERE cc.course_id = ? AND sp.student_id = ?
                """, Integer.class, courseId, actor.getAccount());
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void requireCourse(String courseId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM course WHERE course_id = ?",
                Integer.class, courseId);
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    private void requireChapter(String courseId, String chapterId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM chapter
                WHERE course_id = ? AND chapter_id = ?
                """, Integer.class, courseId, chapterId);
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    private Map<String, Object> material(String materialId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT material_id, course_id, chapter_id, parse_status
                FROM course_material
                WHERE material_id = ?
                LIMIT 1
                """, materialId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return rows.get(0);
    }

    private Map<String, Object> knowledgePoint(String knowledgeId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT knowledge_id, course_id, chapter_id, audit_status
                FROM knowledge_point
                WHERE knowledge_id = ?
                LIMIT 1
                """, knowledgeId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return rows.get(0);
    }

    private void validatePage(Integer pageNo, Integer pageSize) {
        if (pageNo == null || pageNo < 1 || pageSize == null || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
    }

    private String normalize(String value, List<String> allowed, boolean required) {
        if (!StringUtils.hasText(value)) {
            if (required) {
                throw new BusinessException(ErrorCode.PARAM_ERROR);
            }
            return "";
        }
        String normalized = value.trim();
        if (!allowed.contains(normalized)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        return normalized;
    }

    private Map<String, Object> page(List<Map<String, Object>> records, Integer total, Integer pageNo, Integer pageSize) {
        return Map.of("records", records, "total", total == null ? 0 : total, "page_no", pageNo, "page_size", pageSize);
    }

    private void writeOperationLog(User actor, String module, String operationType) {
        jdbcTemplate.update("""
                INSERT INTO operation_log
                  (log_id, user_id, role, module, operation_type, operation_result, operation_time)
                VALUES (?, ?, ?, ?, ?, 'SUCCESS', CURRENT_TIMESTAMP)
                """, "op-" + UUID.randomUUID(), String.valueOf(actor.getId()), actor.getRole(), module, operationType);
    }

    private void writeAiCallLog(String scene, String input, String output, String status) {
        jdbcTemplate.update("""
                INSERT INTO ai_call_log (log_id, scene, model, prompt_version, input_summary, output_summary, call_status)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, "ai-" + UUID.randomUUID(), scene, "mock-local", "stage12", input, output, status);
    }

    private void append(StringBuilder where, List<Object> params, String column, String value) {
        if (StringUtils.hasText(value)) {
            where.append("AND ").append(column).append(" = ?\n");
            params.add(value);
        }
    }

    private String cleanFileName(String filename) {
        String value = StringUtils.hasText(filename) ? filename.trim() : "";
        if (value.contains("/") || value.contains("\\") || ".".equals(value) || "..".equals(value)) {
            throw new BusinessException(ErrorCode.FILE_INVALID);
        }
        value = Path.of(value).getFileName().toString();
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.FILE_INVALID);
        }
        return value;
    }

    private String fileType(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private void rejectTooLarge(MultipartFile file) {
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.FILE_INVALID);
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
