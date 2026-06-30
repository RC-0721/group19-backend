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
        String fileName = cleanFileName(file.getOriginalFilename());
        String fileType = fileType(fileName);
        if (!List.of("txt", "md", "pdf", "doc", "docx", "zip").contains(fileType)) {
            throw new BusinessException(ErrorCode.FILE_INVALID);
        }

        Path target = uploadDir.resolve("file-" + UUID.randomUUID() + "." + fileType).normalize();
        try {
            Files.createDirectories(uploadDir);
            file.transferTo(target);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.FILE_INVALID);
        }
        return Map.of("file_name", fileName, "file_type", fileType, "storage_path", target.toString());
    }

    @Transactional
    public Map<String, Object> uploadMaterial(String courseId, String chapterId, MultipartFile file, User actor) {
        if (!StringUtils.hasText(courseId) || !StringUtils.hasText(chapterId) || file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        requireTeacherCourse(courseId, actor);
        String fileName = cleanFileName(file.getOriginalFilename());
        String fileType = fileType(fileName);
        if (!List.of("txt", "md").contains(fileType)) {
            throw new BusinessException(ErrorCode.FILE_INVALID);
        }

        String materialId = "material-" + UUID.randomUUID();
        String parseTaskId = "parse-" + UUID.randomUUID();
        Path target = uploadDir.resolve(materialId + "." + fileType).normalize();
        try {
            Files.createDirectories(uploadDir);
            file.transferTo(target);
            jdbcTemplate.update("""
                    INSERT INTO course_material (material_id, course_id, chapter_id, file_name, file_type, storage_path, parse_status)
                    VALUES (?, ?, ?, ?, ?, ?, '待审核')
                    """, materialId, courseId, chapterId, fileName, fileType, target.toString());
            jdbcTemplate.update("""
                    INSERT INTO material_parse_task (parse_task_id, material_id, task_status, started_time, finished_time)
                    VALUES (?, ?, '已完成', ?, ?)
                    """, parseTaskId, materialId, LocalDateTime.now(), LocalDateTime.now());
            createChunks(materialId, courseId, chapterId, Files.readString(target));
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.FILE_INVALID);
        }
        return Map.of("material_id", materialId, "parse_status", "待审核", "parse_task_id", parseTaskId);
    }

    public Map<String, Object> listChunks(String materialId, String knowledgeId, String status, Integer pageNo, Integer pageSize, User actor) {
        if (pageNo == null || pageNo < 1 || pageSize == null || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        List<Object> params = new ArrayList<>();
        String where = chunkWhere(materialId, knowledgeId, status, actor, params);
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
        if (!StringUtils.hasText(chunkId)
                || !StringUtils.hasText(status)
                || !List.of("已发布", "已驳回", "待审核").contains(status)) {
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
        return Map.of("chunk_id", chunkId, "status", status);
    }

    public Map<String, Object> answer(String courseId, String chapterId, String questionText, User actor) {
        if (!StringUtils.hasText(courseId) || !StringUtils.hasText(chapterId) || !StringUtils.hasText(questionText)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        List<Map<String, Object>> chunks = jdbcTemplate.queryForList("""
                SELECT kc.chunk_id, kc.chunk_text
                FROM knowledge_chunk kc
                JOIN course_material cm ON kc.material_id = cm.material_id
                WHERE cm.course_id = ? AND cm.chapter_id = ? AND kc.status = '已发布'
                ORDER BY CASE WHEN kc.chunk_id LIKE 'material-%' THEN 0 ELSE 1 END, kc.chunk_id DESC
                LIMIT 1
                """, courseId, chapterId);
        if (chunks.isEmpty()) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_UNAVAILABLE);
        }
        String chunkText = String.valueOf(chunks.get(0).get("chunk_text"));
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

    private String chunkWhere(String materialId, String knowledgeId, String status, User actor, List<Object> params) {
        StringBuilder where = new StringBuilder("WHERE 1 = 1\n");
        append(where, params, "kc.material_id", materialId);
        append(where, params, "kc.knowledge_id", knowledgeId);
        append(where, params, "kc.status", status);
        if ("TEACHER".equalsIgnoreCase(actor.getRole())) {
            where.append("""
                    AND EXISTS (
                        SELECT 1 FROM course_class cc
                        WHERE cc.course_id = cm.course_id AND cc.teacher_id = ?
                    )
                    """);
            params.add(actor.getAccount());
        }
        return where.toString();
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
}
