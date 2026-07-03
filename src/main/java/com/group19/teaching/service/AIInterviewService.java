package com.group19.teaching.service;

import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import com.group19.teaching.domain.entity.User;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class AIInterviewService {

    private static final String MODEL = "mock-ai";
    private static final Set<String> TRANSCRIPT_SOURCES = Set.of("student_audio", "student_text", "ai_text", "manual");
    private static final Set<String> MEDIA_TYPES = Set.of("video", "screenshot", "audio");
    private static final Set<String> MEDIA_MIME_TYPES = Set.of("video/webm", "image/png", "image/jpeg");

    private final JdbcTemplate jdbcTemplate;
    private final AiService aiService;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    public AIInterviewService(
            JdbcTemplate jdbcTemplate,
            AiService aiService,
            PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.aiService = aiService;
        this.transactionTemplate = transactionManager == null ? null : new TransactionTemplate(transactionManager);
    }

    AIInterviewService(JdbcTemplate jdbcTemplate, AiService aiService) {
        this(jdbcTemplate, aiService, null);
    }

    AIInterviewService(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, null, null);
    }

    public Map<String, Object> list(
            String studentId,
            String jobId,
            String status,
            Integer pageNo,
            Integer pageSize,
            User actor) {
        if (pageNo == null || pageNo < 1 || pageSize == null || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        if ("STUDENT".equalsIgnoreCase(actor.getRole())) {
            if (StringUtils.hasText(studentId) && !actor.getAccount().equals(studentId.trim())) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
            studentId = actor.getAccount();
        } else if ("TEACHER".equalsIgnoreCase(actor.getRole()) && StringUtils.hasText(studentId)) {
            requireTeacherStudent(studentId.trim(), actor.getAccount());
        } else if (!"TEACHER".equalsIgnoreCase(actor.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        List<Object> params = new ArrayList<>();
        String where = buildListWhere(studentId, jobId, status, actor, params);
        Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ai_session s " + where,
                Integer.class, params.toArray());
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(pageSize);
        pageParams.add((pageNo - 1) * pageSize);
        List<Map<String, Object>> records = jdbcTemplate.queryForList("""
                SELECT s.session_id, s.student_id, s.job_id, s.scene, s.model, s.prompt_version,
                       s.status, s.created_time, r.report_id, r.score
                FROM ai_session s
                LEFT JOIN ai_interview_report r ON s.session_id = r.session_id
                """ + where + """
                ORDER BY s.created_time DESC, s.session_id
                LIMIT ? OFFSET ?
                """, pageParams.toArray());
        return Map.of("records", records, "total", total == null ? 0 : total,
                "page_no", pageNo, "page_size", pageSize);
    }

    @Transactional
    public Map<String, Object> start(Map<String, Object> request, User actor) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        String jobId = stringValue(request.get("job_id"));
        String scene = stringValue(request.get("scene"));
        String difficultyLevel = stringValue(request.get("difficulty_level"));
        String promptVersion = stringValue(request.get("prompt_version"));
        if (!StringUtils.hasText(jobId) || !StringUtils.hasText(scene)
                || !StringUtils.hasText(difficultyLevel) || !StringUtils.hasText(promptVersion)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        Map<String, Object> job = requireJob(jobId);
        String sessionId = "session-" + UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        ChatResult question = chat(scene, "请结合" + job.get("job_name") + "方向，生成一道" + difficultyLevel + "面试开场题。",
                actor, "请结合" + job.get("job_name") + "方向，说明你最熟悉的一项技术实践。");
        jdbcTemplate.update("""
                INSERT INTO ai_session (session_id, student_id, job_id, scene, model, prompt_version, status, created_time)
                VALUES (?, ?, ?, ?, ?, ?, '已创建', ?)
                """, sessionId, actor.getAccount(), jobId, scene, question.model(), promptVersion, Timestamp.valueOf(now));
        if (aiService == null) {
            jdbcTemplate.update("""
                    INSERT INTO ai_call_log (log_id, scene, model, prompt_version, input_summary, output_summary, call_status)
                    VALUES (?, ?, ?, ?, ?, ?, '成功')
                    """, "ai-log-" + UUID.randomUUID(), scene, MODEL, promptVersion,
                    "start:" + jobId + ":" + difficultyLevel, question.content());
        }
        return Map.of("session_id", sessionId, "status", "已创建", "first_question", question.content());
    }

    @Transactional
    public Map<String, Object> sendMessage(String sessionId, Map<String, Object> request, User actor) {
        return completeMessage(sessionId, request, actor);
    }

    @Transactional
    public Map<String, Object> saveTranscript(String sessionId, Map<String, Object> request, User actor) {
        Map<String, Object> session = requireSession(sessionId);
        requireSessionReadable(session, actor);
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        String content = stringValue(request.get("content"));
        String source = stringValue(request.get("source"));
        Double startTime = doubleValue(request.get("start_time"));
        Double endTime = doubleValue(request.get("end_time"));
        Boolean isFinal = booleanValue(request.get("is_final"));
        if (!StringUtils.hasText(content) || !TRANSCRIPT_SOURCES.contains(source)
                || startTime == null || endTime == null || endTime < startTime || isFinal == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }

        String segmentId = "segment-" + UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO ai_interview_transcript_segment
                    (segment_id, session_id, content, source, start_time, end_time, is_final, created_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, segmentId, sessionId, content, source, startTime, endTime, isFinal,
                Timestamp.valueOf(LocalDateTime.now()));
        return Map.of("segment_id", segmentId);
    }

    public Map<String, Object> listTranscripts(String sessionId, User actor) {
        Map<String, Object> session = requireSession(sessionId);
        requireSessionReadable(session, actor);
        List<Map<String, Object>> segments = jdbcTemplate.queryForList("""
                SELECT segment_id, session_id, content, source, start_time, end_time, is_final, created_time
                FROM ai_interview_transcript_segment
                WHERE session_id = ?
                ORDER BY start_time ASC, created_time ASC, segment_id ASC
                """, sessionId);
        return Map.of("segments", segments);
    }

    @Transactional
    public Map<String, Object> bindMedia(String sessionId, Map<String, Object> request, User actor) {
        Map<String, Object> session = requireSession(sessionId);
        requireStudentOwner(session, actor);
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        String mediaType = stringValue(request.get("media_type"));
        String fileName = stringValue(request.get("file_name"));
        String fileUrl = stringValue(request.get("file_url"));
        String storagePath = stringValue(request.get("storage_path"));
        String mimeType = stringValue(request.get("mime_type"));
        if (!MEDIA_TYPES.contains(mediaType) || (!StringUtils.hasText(fileUrl) && !StringUtils.hasText(storagePath))) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        if (StringUtils.hasText(mimeType) && !MEDIA_MIME_TYPES.contains(mimeType)) {
            throw new BusinessException(ErrorCode.FILE_INVALID);
        }

        String mediaId = "media-" + UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO ai_interview_media
                    (media_id, session_id, media_type, file_name, file_url, storage_path, mime_type, created_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, mediaId, sessionId, mediaType, fileName, fileUrl, storagePath, mimeType,
                Timestamp.valueOf(LocalDateTime.now()));
        return Map.of("media_id", mediaId);
    }

    public Map<String, Object> listMedia(String sessionId, User actor) {
        Map<String, Object> session = requireSession(sessionId);
        requireSessionReadable(session, actor);
        List<Map<String, Object>> media = jdbcTemplate.queryForList("""
                SELECT media_id, session_id, media_type, file_name, file_url, storage_path, mime_type, created_time
                FROM ai_interview_media
                WHERE session_id = ?
                ORDER BY created_time ASC, media_id ASC
                """, sessionId);
        return Map.of("media", media);
    }

    public SseEmitter streamMessage(String sessionId, Map<String, Object> request, User actor) {
        validateMessageRequest(sessionId, request);
        SseEmitter emitter = new SseEmitter(0L);
        CompletableFuture.runAsync(() -> {
            try {
                Map<String, Object> result = runInTransaction(() -> completeMessage(sessionId, request, actor));
                emitter.send(SseEmitter.event().name("meta").data(Map.of(
                        "message_id", result.get("message_id"),
                        "reference_chunk", result.get("reference_chunk"),
                        "status", result.get("status")
                )));
                sendChunks(emitter, stringValue(result.get("message_content")));
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

    private Map<String, Object> completeMessage(String sessionId, Map<String, Object> request, User actor) {
        validateMessageRequest(sessionId, request);
        String content = stringValue(request.get("message_content"));
        Map<String, Object> session = requireSession(sessionId);
        if (!actor.getAccount().equals(stringValue(session.get("student_id")))) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO ai_message (message_id, session_id, sender_type, message_content, reference_chunk, created_time)
                VALUES (?, ?, 'STUDENT', ?, NULL, ?)
                """, "msg-" + UUID.randomUUID(), sessionId, content, Timestamp.valueOf(now));
        String referenceChunk = firstReferenceChunk();
        ChatResult reply = chat(stringValue(session.get("scene")), content, actor,
                "Mock 面试反馈：回答已覆盖基础概念，请补充项目场景、关键取舍和验证结果。");
        String aiContent = reply.content();
        String aiMessageId = "msg-" + UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO ai_message (message_id, session_id, sender_type, message_content, reference_chunk, created_time)
                VALUES (?, ?, 'AI', ?, ?, ?)
                """, aiMessageId, sessionId, aiContent, referenceChunk, Timestamp.valueOf(now));
        jdbcTemplate.update("UPDATE ai_session SET status = '已完成' WHERE session_id = ?", sessionId);

        String reportId = reportId(sessionId);
        String jobId = stringValue(session.get("job_id"));
        Double score = 82.0;
        jdbcTemplate.update("""
                INSERT INTO ai_interview_report (report_id, session_id, job_id, score, strength, weakness, suggestion)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE score = VALUES(score), strength = VALUES(strength),
                    weakness = VALUES(weakness), suggestion = VALUES(suggestion)
                """, reportId, sessionId, jobId, score, "能围绕岗位技术作答",
                "项目化表达和细节验证不足", "继续练习 Spring Boot、数据库和缓存场景题");
        jdbcTemplate.update("""
                INSERT INTO ability_evidence (evidence_id, student_id, source_type, source_id, skill_id, score)
                VALUES (?, ?, 'AI_INTERVIEW', ?, ?, ?)
                ON DUPLICATE KEY UPDATE skill_id = VALUES(skill_id), score = VALUES(score)
                """, "evidence-" + UUID.randomUUID(), actor.getAccount(), reportId, firstSkillId(jobId), score);
        if (aiService == null) {
            jdbcTemplate.update("""
                    INSERT INTO ai_call_log (log_id, scene, model, prompt_version, input_summary, output_summary, call_status)
                    VALUES (?, ?, ?, ?, ?, ?, '成功')
                    """, "ai-log-" + UUID.randomUUID(), stringValue(session.get("scene")), MODEL,
                    stringValue(session.get("prompt_version")), content, aiContent);
        }
        return Map.of("message_id", aiMessageId, "message_content", aiContent,
                "reference_chunk", referenceChunk, "status", "已完成");
    }

    private void validateMessageRequest(String sessionId, Map<String, Object> request) {
        if (request == null || !StringUtils.hasText(sessionId)
                || !StringUtils.hasText(stringValue(request.get("message_content")))) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
    }

    private Map<String, Object> runInTransaction(MessageSupplier supplier) {
        if (transactionTemplate == null) {
            return supplier.get();
        }
        return transactionTemplate.execute(status -> supplier.get());
    }

    private void sendChunks(SseEmitter emitter, String content) throws IOException {
        for (int start = 0; start < content.length(); start += 32) {
            emitter.send(SseEmitter.event().name("delta")
                    .data(content.substring(start, Math.min(start + 32, content.length()))));
        }
    }

    private void sendError(SseEmitter emitter, String code, String message) {
        try {
            emitter.send(SseEmitter.event().name("error").data(Map.of("code", code, "message", message)));
        } catch (IOException ignored) {
        }
        emitter.complete();
    }

    public Map<String, Object> report(String sessionId, User actor) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT r.report_id, r.job_id, r.score, r.strength, r.weakness, r.suggestion, s.student_id
                FROM ai_interview_report r
                JOIN ai_session s ON r.session_id = s.session_id
                WHERE r.session_id = ?
                LIMIT 1
                """, sessionId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        Map<String, Object> report = rows.get(0);
        String studentId = stringValue(report.get("student_id"));
        if ("STUDENT".equalsIgnoreCase(actor.getRole()) && !actor.getAccount().equals(studentId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if ("TEACHER".equalsIgnoreCase(actor.getRole())) {
            requireTeacherStudent(studentId, actor.getAccount());
        }
        return Map.of(
                "report_id", report.get("report_id"),
                "job_id", report.get("job_id"),
                "score", report.get("score"),
                "strength", report.get("strength"),
                "weakness", report.get("weakness"),
                "suggestion", report.get("suggestion")
        );
    }

    private Map<String, Object> requireJob(String jobId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT job_id, job_name
                FROM job_direction
                WHERE job_id = ? AND status = '启用'
                LIMIT 1
                """, jobId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return rows.get(0);
    }

    private Map<String, Object> requireSession(String sessionId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT session_id, student_id, job_id, scene, prompt_version
                FROM ai_session
                WHERE session_id = ?
                LIMIT 1
                """, sessionId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return rows.get(0);
    }

    private void requireSessionReadable(Map<String, Object> session, User actor) {
        if ("EDU_ADMIN".equalsIgnoreCase(actor.getRole())) {
            return;
        }
        if ("STUDENT".equalsIgnoreCase(actor.getRole())) {
            requireStudentOwner(session, actor);
            return;
        }
        if ("TEACHER".equalsIgnoreCase(actor.getRole())) {
            requireTeacherStudent(stringValue(session.get("student_id")), actor.getAccount());
            return;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN);
    }

    private void requireStudentOwner(Map<String, Object> session, User actor) {
        if (!"STUDENT".equalsIgnoreCase(actor.getRole())
                || !actor.getAccount().equals(stringValue(session.get("student_id")))) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private String firstReferenceChunk() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT chunk_text
                FROM knowledge_chunk
                WHERE status = '已发布'
                ORDER BY chunk_id
                LIMIT 1
                """);
        return rows.isEmpty() ? "" : stringValue(rows.get(0).get("chunk_text"));
    }

    private String reportId(String sessionId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT report_id
                FROM ai_interview_report
                WHERE session_id = ?
                LIMIT 1
                """, sessionId);
        return rows.isEmpty() ? "report-" + UUID.randomUUID() : stringValue(rows.get(0).get("report_id"));
    }

    private String firstSkillId(String jobId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT skill_id
                FROM job_skill_standard
                WHERE job_id = ?
                ORDER BY skill_id
                LIMIT 1
                """, jobId);
        return rows.isEmpty() ? null : stringValue(rows.get(0).get("skill_id"));
    }

    private void requireTeacherStudent(String studentId, String teacherId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM student_profile sp
                JOIN course_class cc ON sp.class_id = cc.class_id
                WHERE sp.student_id = ? AND cc.teacher_id = ?
                """, Integer.class, studentId, teacherId);
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private String buildListWhere(String studentId, String jobId, String status, User actor, List<Object> params) {
        StringBuilder where = new StringBuilder("WHERE 1 = 1\n");
        if ("TEACHER".equalsIgnoreCase(actor.getRole()) && !StringUtils.hasText(studentId)) {
            where.append("""
                    AND EXISTS (
                      SELECT 1
                      FROM student_profile sp
                      JOIN course_class cc ON sp.class_id = cc.class_id
                      WHERE sp.student_id = s.student_id AND cc.teacher_id = ?
                    )
                    """);
            params.add(actor.getAccount());
        }
        append(where, params, "s.student_id", studentId);
        append(where, params, "s.job_id", jobId);
        append(where, params, "s.status", status);
        return where.toString();
    }

    private void append(StringBuilder where, List<Object> params, String column, String value) {
        if (StringUtils.hasText(value)) {
            where.append("AND ").append(column).append(" = ?\n");
            params.add(value.trim());
        }
    }

    private ChatResult chat(String scene, String prompt, User actor, String fallbackContent) {
        if (aiService == null) {
            return new ChatResult(MODEL, fallbackContent);
        }
        Map<String, Object> result = aiService.chat(Map.of("scene", scene, "prompt", prompt), actor);
        return new ChatResult(stringValue(result.get("model")), stringValue(result.get("content")));
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        String text = stringValue(value);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Boolean booleanValue(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        String text = stringValue(value);
        if ("true".equalsIgnoreCase(text)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(text)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private record ChatResult(String model, String content) {
    }

    private interface MessageSupplier {
        Map<String, Object> get();
    }
}
