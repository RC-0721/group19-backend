package com.group19.teaching.service;

import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import com.group19.teaching.domain.entity.User;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class AIInterviewService {

    private static final String MODEL = "mock-ai";
    private static final Set<String> TRANSCRIPT_SOURCES = Set.of("student_audio", "student_text", "ai_text", "manual");
    private static final Set<String> STUDENT_MESSAGE_SOURCES = Set.of("student_text", "student_audio_stt", "manual");
    private static final Set<String> MEDIA_TYPES = Set.of("video", "screenshot", "audio");
    private static final Set<String> MEDIA_MIME_TYPES = Set.of("video/webm", "image/png", "image/jpeg");
    private static final Set<String> VIDEO_MIME_TYPES = Set.of("video/webm", "video/mp4");
    private static final Set<String> AUDIO_MIME_TYPES = Set.of("audio/webm", "audio/mpeg", "audio/mp4", "audio/wav");
    private static final Set<String> SCREENSHOT_MIME_TYPES = Set.of("image/png", "image/jpeg");
    private static final Set<String> VIDEO_EXTENSIONS = Set.of("webm", "mp4");
    private static final Set<String> AUDIO_EXTENSIONS = Set.of("webm", "mp3", "m4a", "wav");
    private static final Set<String> SCREENSHOT_EXTENSIONS = Set.of("png", "jpg", "jpeg");

    private final JdbcTemplate jdbcTemplate;
    private final AiService aiService;
    private final TransactionTemplate transactionTemplate;
    private final Path uploadDir;
    private final long maxUploadBytes;

    @Autowired
    public AIInterviewService(
            JdbcTemplate jdbcTemplate,
            AiService aiService,
            PlatformTransactionManager transactionManager,
            @Value("${teaching.upload-dir:data/uploads}") String uploadDir,
            @Value("${teaching.homework-upload.max-size-mb:50}") long maxSizeMb) {
        this.jdbcTemplate = jdbcTemplate;
        this.aiService = aiService;
        this.transactionTemplate = transactionManager == null ? null : new TransactionTemplate(transactionManager);
        this.uploadDir = Path.of(uploadDir);
        this.maxUploadBytes = maxSizeMb * 1024 * 1024;
    }

    AIInterviewService(JdbcTemplate jdbcTemplate, AiService aiService) {
        this(jdbcTemplate, aiService, null, "data/uploads", 50);
    }

    AIInterviewService(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, null, null, "data/uploads", 50);
    }

    AIInterviewService(JdbcTemplate jdbcTemplate, String uploadDir) {
        this(jdbcTemplate, null, null, uploadDir, 50);
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
        } else if (!"TEACHER".equalsIgnoreCase(actor.getRole()) && !"EDU_ADMIN".equalsIgnoreCase(actor.getRole())) {
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
                       s.status, s.current_round, s.round_count, s.started_at, s.ended_at,
                       s.created_time, s.updated_time, r.report_id, r.score,
                       COALESCE(u.name, s.student_id) AS student_name,
                       jd.job_name,
                       (
                         SELECT m.message_content
                         FROM ai_message m
                         WHERE m.session_id = s.session_id
                         ORDER BY m.created_time DESC, m.message_id DESC
                         LIMIT 1
                       ) AS last_message
                FROM ai_session s
                LEFT JOIN ai_interview_report r ON s.session_id = r.session_id
                LEFT JOIN sys_user u ON u.account = s.student_id
                LEFT JOIN job_direction jd ON jd.job_id = s.job_id
                """ + where + """
                ORDER BY s.created_time DESC, s.session_id
                LIMIT ? OFFSET ?
                """, pageParams.toArray());
        List<Map<String, Object>> enriched = records.stream()
                .map(this::withCanGenerateReport)
                .toList();
        return Map.of("records", enriched, "total", total == null ? 0 : total,
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
        Integer roundCount = intValue(request.get("round_count"));
        if (roundCount == null) {
            roundCount = 5;
        }
        if (!StringUtils.hasText(jobId) || !StringUtils.hasText(scene)
                || !StringUtils.hasText(difficultyLevel) || !StringUtils.hasText(promptVersion)
                || roundCount < 1 || roundCount > 20) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        Map<String, Object> job = requireJob(jobId);
        String sessionId = "session-" + UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        ChatResult question = chat(scene, "请结合" + job.get("job_name") + "方向，生成一道" + difficultyLevel + "面试开场题。",
                actor, "请结合" + job.get("job_name") + "方向，说明你最熟悉的一项技术实践。");
        jdbcTemplate.update("""
                INSERT INTO ai_session
                    (session_id, student_id, job_id, scene, model, prompt_version, status,
                     current_round, round_count, started_at, created_time, updated_time)
                VALUES (?, ?, ?, ?, ?, ?, '进行中', 0, ?, ?, ?, ?)
                """, sessionId, actor.getAccount(), jobId, scene, question.model(), promptVersion,
                roundCount, Timestamp.valueOf(now), Timestamp.valueOf(now), Timestamp.valueOf(now));
        if (aiService == null) {
            jdbcTemplate.update("""
                    INSERT INTO ai_call_log (log_id, scene, model, prompt_version, input_summary, output_summary, call_status)
                    VALUES (?, ?, ?, ?, ?, ?, '成功')
                    """, "ai-log-" + UUID.randomUUID(), scene, MODEL, promptVersion,
                    "start:" + jobId + ":" + difficultyLevel, question.content());
        }
        return Map.of(
                "session_id", sessionId,
                "status", "进行中",
                "first_question", question.content(),
                "current_round", 0,
                "round_count", roundCount,
                "started_at", Timestamp.valueOf(now),
                "can_generate_report", false
        );
    }

    public Map<String, Object> detail(String sessionId, User actor) {
        Map<String, Object> session = requireSessionDetail(sessionId);
        requireSessionReadable(session, actor);
        return withCanGenerateReport(session);
    }

    @Transactional
    public Map<String, Object> sendMessage(String sessionId, Map<String, Object> request, User actor) {
        return completeMessage(sessionId, request, actor);
    }

    public Map<String, Object> listMessages(String sessionId, User actor) {
        Map<String, Object> session = requireSessionMessageDetail(sessionId);
        requireSessionReadable(session, actor);
        List<Map<String, Object>> messages = jdbcTemplate.queryForList("""
                SELECT message_id, session_id, sender_type, message_content, reference_chunk,
                       source, round_no, client_message_id, created_time
                FROM ai_message
                WHERE session_id = ?
                ORDER BY COALESCE(round_no, 999999), created_time ASC, message_id ASC
                """, sessionId);
        List<Map<String, Object>> enriched = messages.stream()
                .map(this::withMessageAliases)
                .toList();
        return Map.of("session", session, "messages", enriched);
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
        Double duration = doubleValue(request.get("duration"));
        if (!MEDIA_TYPES.contains(mediaType) || (!StringUtils.hasText(fileUrl) && !StringUtils.hasText(storagePath))) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        if (duration != null && duration < 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        if (StringUtils.hasText(mimeType) && !MEDIA_MIME_TYPES.contains(mimeType)) {
            throw new BusinessException(ErrorCode.FILE_INVALID);
        }

        String mediaId = "media-" + UUID.randomUUID();
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        jdbcTemplate.update("""
                INSERT INTO ai_interview_media
                    (media_id, session_id, media_type, file_name, file_url, storage_path, mime_type,
                     file_size, duration, created_time, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, NULL, ?, ?, ?)
                """, mediaId, sessionId, mediaType, fileName, fileUrl, storagePath, mimeType, duration, now, now);
        return Map.of("media_id", mediaId);
    }

    @Transactional
    public Map<String, Object> uploadMedia(
            String sessionId,
            String mediaType,
            Double duration,
            MultipartFile file,
            User actor) {
        Map<String, Object> session = requireSession(sessionId);
        requireStudentOwner(session, actor);
        if (!MEDIA_TYPES.contains(mediaType) || file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        if (duration != null && duration < 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        if (file.getSize() > maxUploadBytes) {
            throw new BusinessException(ErrorCode.FILE_INVALID);
        }
        String originalName = cleanFileName(file.getOriginalFilename());
        String extension = fileExtension(originalName);
        String mimeType = StringUtils.hasText(file.getContentType()) ? file.getContentType().trim() : "";
        validateMediaUploadType(mediaType, extension, mimeType);

        String mediaId = "media-" + UUID.randomUUID();
        String storedFileName = mediaId + "." + extension;
        Path root = uploadDir.toAbsolutePath().normalize();
        Path sessionDir = root.resolve("interviews").resolve(sessionId).normalize();
        Path target = sessionDir.resolve(storedFileName).normalize();
        if (!target.startsWith(root)) {
            throw new BusinessException(ErrorCode.FILE_INVALID);
        }
        try {
            Files.createDirectories(sessionDir);
            file.transferTo(target);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.FILE_INVALID);
        }

        String storagePath = target.toString();
        String fileUrl = "/uploads/interviews/" + sessionId + "/" + storedFileName;
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        jdbcTemplate.update("""
                INSERT INTO ai_interview_media
                    (media_id, session_id, media_type, file_name, file_url, storage_path, mime_type,
                     file_size, duration, created_time, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, mediaId, sessionId, mediaType, originalName, fileUrl, storagePath, mimeType,
                file.getSize(), duration, now, now);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("media_id", mediaId);
        result.put("file_url", fileUrl);
        result.put("storage_path", storagePath);
        result.put("mime_type", mimeType);
        result.put("duration", duration);
        result.put("created_at", now);
        return result;
    }

    public Map<String, Object> listMedia(String sessionId, User actor) {
        Map<String, Object> session = requireSession(sessionId);
        requireSessionReadable(session, actor);
        List<Map<String, Object>> media = jdbcTemplate.queryForList("""
                SELECT media_id, session_id, media_type, file_name, file_url, storage_path, mime_type,
                       file_size, duration, created_time, created_at
                FROM ai_interview_media
                WHERE session_id = ?
                ORDER BY created_time ASC, media_id ASC
                """, sessionId);
        return Map.of("media", media);
    }

    public SseEmitter streamMessage(String sessionId, Map<String, Object> request, User actor) {
        return streamChat(sessionId, request, actor);
    }

    public SseEmitter streamChat(String sessionId, Map<String, Object> request, User actor) {
        MessageRequest messageRequest = parseMessageRequest(sessionId, request);
        Map<String, Object> session = requireSession(sessionId);
        requireOpenStudentSession(session, actor);
        int roundNo = intValue(session.get("current_round"), 0) + 1;
        saveStudentMessage(sessionId, messageRequest, roundNo);
        String aiMessageId = "msg-" + UUID.randomUUID();
        String referenceChunk = firstReferenceChunk();
        SseEmitter emitter = new SseEmitter(0L);
        CompletableFuture.runAsync(() -> {
            try {
                emitter.send(SseEmitter.event().name("message_start").data(Map.of(
                        "session_id", sessionId,
                        "message_id", aiMessageId,
                        "role", "assistant",
                        "round", roundNo
                )));
                String aiContent = streamAiReply(emitter, session, messageRequest.content(), actor);
                Map<String, Object> result = runInTransaction(() -> saveAiMessageAndAdvanceRound(
                        session, aiMessageId, aiContent, referenceChunk, roundNo));
                emitter.send(SseEmitter.event().name("message_end").data(result));
                emitter.complete();
            } catch (BusinessException exception) {
                sendError(emitter, exception.errorCode().code(), exception.errorCode().message());
            } catch (RuntimeException | IOException exception) {
                sendError(emitter, ErrorCode.AI_UNAVAILABLE.code(), ErrorCode.AI_UNAVAILABLE.message());
            }
        });
        return emitter;
    }

    private Map<String, Object> completeMessage(String sessionId, Map<String, Object> request, User actor) {
        MessageRequest messageRequest = parseMessageRequest(sessionId, request);
        Map<String, Object> session = requireSession(sessionId);
        requireOpenStudentSession(session, actor);
        int roundNo = intValue(session.get("current_round"), 0) + 1;
        saveStudentMessage(sessionId, messageRequest, roundNo);
        String referenceChunk = firstReferenceChunk();
        ChatResult reply = chat(stringValue(session.get("scene")), messageRequest.content(), actor,
                "Mock 面试反馈：回答已覆盖基础概念，请补充项目场景、关键取舍和验证结果。");
        String aiMessageId = "msg-" + UUID.randomUUID();
        return saveAiMessageAndAdvanceRound(session, aiMessageId, reply.content(), referenceChunk, roundNo);
    }

    private MessageRequest parseMessageRequest(String sessionId, Map<String, Object> request) {
        if (request == null || !StringUtils.hasText(sessionId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        String content = stringValue(request.get("content"));
        if (!StringUtils.hasText(content)) {
            content = stringValue(request.get("message_content"));
        }
        String source = stringValue(request.get("source"));
        if (!StringUtils.hasText(source)) {
            source = "student_text";
        }
        String clientMessageId = stringValue(request.get("client_message_id"));
        if (!StringUtils.hasText(content) || !STUDENT_MESSAGE_SOURCES.contains(source)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        return new MessageRequest(content, source, clientMessageId);
    }

    private String saveStudentMessage(String sessionId, MessageRequest request, int roundNo) {
        String messageId = "msg-" + UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO ai_message
                    (message_id, session_id, sender_type, message_content, reference_chunk,
                     source, round_no, client_message_id, created_time)
                VALUES (?, ?, 'student', ?, NULL, ?, ?, ?, ?)
                """, messageId, sessionId, request.content(), request.source(), roundNo,
                emptyToNull(request.clientMessageId()), Timestamp.valueOf(LocalDateTime.now()));
        return messageId;
    }

    private String streamAiReply(SseEmitter emitter, Map<String, Object> session, String content, User actor) {
        if (aiService == null) {
            String fallback = "Mock 面试反馈：回答已覆盖基础概念，请补充项目场景、关键取舍和验证结果。";
            sendDelta(emitter, fallback);
            jdbcTemplate.update("""
                    INSERT INTO ai_call_log (log_id, scene, model, prompt_version, input_summary, output_summary, call_status)
                    VALUES (?, ?, ?, ?, ?, ?, '成功')
                    """, "ai-log-" + UUID.randomUUID(), "AI_INTERVIEW_CHAT", MODEL,
                    stringValue(session.get("prompt_version")), limitForPrompt(content), fallback);
            return fallback;
        }
        Map<String, Object> aiRequest = buildInterviewAiRequest(session, content);
        AiProviderStreamResult result = aiService.streamChat(aiRequest, actor, delta -> sendDelta(emitter, delta));
        return result.content();
    }

    private Map<String, Object> saveAiMessageAndAdvanceRound(
            Map<String, Object> session,
            String aiMessageId,
            String aiContent,
            String referenceChunk,
            int roundNo) {
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        String sessionId = stringValue(session.get("session_id"));
        jdbcTemplate.update("""
                INSERT INTO ai_message
                    (message_id, session_id, sender_type, message_content, reference_chunk,
                     source, round_no, client_message_id, created_time)
                VALUES (?, ?, 'ai', ?, ?, 'ai_stream', ?, NULL, ?)
                """, aiMessageId, sessionId, aiContent, referenceChunk, roundNo, now);
        jdbcTemplate.update("""
                UPDATE ai_session
                SET current_round = current_round + 1, updated_time = ?
                WHERE session_id = ?
                """, now, sessionId);
        int currentRound = Math.max(0, intValue(session.get("current_round"), 0)) + 1;
        int roundCount = Math.max(1, intValue(session.get("round_count"), 5));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message_id", aiMessageId);
        result.put("content", aiContent);
        result.put("message_content", aiContent);
        result.put("reference_chunk", referenceChunk);
        result.put("role", "assistant");
        result.put("sender_type", "ai");
        result.put("source", "ai_stream");
        result.put("round", roundNo);
        result.put("round_no", roundNo);
        result.put("status", StringUtils.hasText(stringValue(session.get("status")))
                ? stringValue(session.get("status")) : "进行中");
        result.put("current_round", currentRound);
        result.put("round_count", roundCount);
        result.put("can_generate_report", currentRound >= roundCount);
        return result;
    }

    private Map<String, Object> buildInterviewAiRequest(Map<String, Object> session, String content) {
        int roundNo = intValue(session.get("current_round"), 0) + 1;
        int roundCount = intValue(session.get("round_count"), 5);
        String jobName = jobName(stringValue(session.get("job_id")));
        String systemPrompt = "你是智慧课程学习与教学数据分析系统的 AI 面试官。"
                + "请围绕岗位和学生回答进行简洁追问，一次只问一个问题，不输出与面试无关的内容。";
        String prompt = "岗位：" + jobName + "\n"
                + "场景：" + stringValue(session.get("scene")) + "\n"
                + "Prompt版本：" + stringValue(session.get("prompt_version")) + "\n"
                + "轮次：" + roundNo + "/" + roundCount + "\n"
                + "最近对话：\n" + recentMessageText(stringValue(session.get("session_id"))) + "\n"
                + "学生本轮回答：\n" + content + "\n"
                + "请给出自然的面试官回应，并继续提出一个后续问题。";
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("scene", "AI_INTERVIEW_CHAT");
        request.put("prompt", prompt);
        request.put("system_prompt", systemPrompt);
        return request;
    }

    private String recentMessageText(String sessionId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT sender_type, message_content
                FROM ai_message
                WHERE session_id = ?
                ORDER BY created_time DESC, message_id DESC
                LIMIT 10
                """, sessionId);
        if (rows.isEmpty()) {
            return "无";
        }
        StringBuilder text = new StringBuilder();
        for (int index = rows.size() - 1; index >= 0; index--) {
            Map<String, Object> row = rows.get(index);
            String role = "ai".equalsIgnoreCase(stringValue(row.get("sender_type"))) ? "AI" : "学生";
            text.append(role).append("：")
                    .append(limitForPrompt(stringValue(row.get("message_content"))))
                    .append('\n');
        }
        return text.toString().trim();
    }

    private String jobName(String jobId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT job_name
                FROM job_direction
                WHERE job_id = ?
                LIMIT 1
                """, jobId);
        return rows.isEmpty() ? jobId : stringValue(rows.get(0).get("job_name"));
    }

    private void sendDelta(SseEmitter emitter, String content) {
        try {
            emitter.send(SseEmitter.event().name("delta").data(Map.of("content", content)));
        } catch (IOException exception) {
            throw new IllegalStateException("SSE send failed", exception);
        }
    }

    private Map<String, Object> runInTransaction(MessageSupplier supplier) {
        if (transactionTemplate == null) {
            return supplier.get();
        }
        return transactionTemplate.execute(status -> supplier.get());
    }

    private void sendError(SseEmitter emitter, String code, String message) {
        try {
            emitter.send(SseEmitter.event().name("error").data(Map.of("code", code, "message", message)));
        } catch (IOException ignored) {
        }
        emitter.complete();
    }

    @Transactional
    public Map<String, Object> finish(String sessionId, Map<String, Object> request, User actor) {
        Map<String, Object> session = requireSession(sessionId);
        requireStudentOwner(session, actor);
        Timestamp endedAt = Timestamp.valueOf(LocalDateTime.now());
        if (!"已完成".equals(stringValue(session.get("status"))) || session.get("ended_at") == null) {
            jdbcTemplate.update("""
                    UPDATE ai_session
                    SET status = '已完成', ended_at = ?, updated_time = ?
                    WHERE session_id = ?
                    """, endedAt, endedAt, sessionId);
        } else {
            Object existingEndedAt = session.get("ended_at");
            if (existingEndedAt instanceof Timestamp timestamp) {
                endedAt = timestamp;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("session_id", sessionId);
        result.put("status", "已完成");
        result.put("current_round", intValue(session.get("current_round"), 0));
        result.put("round_count", intValue(session.get("round_count"), 5));
        result.put("ended_at", endedAt);
        result.put("can_generate_report", true);
        return result;
    }

    @Transactional
    public Map<String, Object> generateReport(String sessionId, User actor) {
        Map<String, Object> session = requireSession(sessionId);
        requireStudentOwner(session, actor);
        if (!canGenerateReport(session, false)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        String reportId = reportId(sessionId);
        String jobId = stringValue(session.get("job_id"));
        Timestamp generatedTime = Timestamp.valueOf(LocalDateTime.now());
        double overallScore = 82.0;
        double expressionScore = 80.0;
        double technicalScore = 84.0;
        double projectScore = 78.0;
        double logicScore = 86.0;
        String strengths = "能围绕岗位技术作答；基础概念较清晰";
        String weaknesses = "项目化表达和细节验证不足";
        String suggestions = "继续练习 Spring Boot、数据库和缓存场景题";
        String nextActions = "补充一个项目复盘；练习 3 道 Redis 场景题；复盘一次完整面试记录";

        jdbcTemplate.update("""
                INSERT INTO ai_interview_report
                    (report_id, session_id, job_id, score, strength, weakness, suggestion,
                     overall_score, expression_score, technical_score, project_score, logic_score,
                     strengths, weaknesses, suggestions, next_actions, generated_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE score = VALUES(score), strength = VALUES(strength),
                    weakness = VALUES(weakness), suggestion = VALUES(suggestion),
                    overall_score = VALUES(overall_score), expression_score = VALUES(expression_score),
                    technical_score = VALUES(technical_score), project_score = VALUES(project_score),
                    logic_score = VALUES(logic_score), strengths = VALUES(strengths),
                    weaknesses = VALUES(weaknesses), suggestions = VALUES(suggestions),
                    next_actions = VALUES(next_actions), generated_time = VALUES(generated_time)
                """, reportId, sessionId, jobId, overallScore, strengths, weaknesses, suggestions,
                overallScore, expressionScore, technicalScore, projectScore, logicScore,
                strengths, weaknesses, suggestions, nextActions, generatedTime);
        jdbcTemplate.update("""
                INSERT INTO ability_evidence (evidence_id, student_id, source_type, source_id, skill_id, score)
                VALUES (?, ?, 'AI_INTERVIEW', ?, ?, ?)
                ON DUPLICATE KEY UPDATE skill_id = VALUES(skill_id), score = VALUES(score)
                """, "evidence-" + UUID.randomUUID(), actor.getAccount(), reportId, firstSkillId(jobId), overallScore);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("report_id", reportId);
        result.put("session_id", sessionId);
        result.put("job_id", jobId);
        result.put("overall_score", overallScore);
        result.put("expression_score", expressionScore);
        result.put("technical_score", technicalScore);
        result.put("project_score", projectScore);
        result.put("logic_score", logicScore);
        result.put("strengths", strengths);
        result.put("weaknesses", weaknesses);
        result.put("suggestions", suggestions);
        result.put("next_actions", nextActions);
        result.put("generated_time", generatedTime);
        return result;
    }

    public Map<String, Object> report(String sessionId, User actor) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT r.report_id, r.job_id, r.score, r.strength, r.weakness, r.suggestion,
                       r.overall_score, r.expression_score, r.technical_score, r.project_score,
                       r.logic_score, r.strengths, r.weaknesses, r.suggestions, r.next_actions,
                       r.generated_time, s.student_id
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
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("report_id", report.get("report_id"));
        result.put("job_id", report.get("job_id"));
        result.put("score", report.get("score"));
        result.put("strength", report.get("strength"));
        result.put("weakness", report.get("weakness"));
        result.put("suggestion", report.get("suggestion"));
        result.put("overall_score", report.get("overall_score"));
        result.put("expression_score", report.get("expression_score"));
        result.put("technical_score", report.get("technical_score"));
        result.put("project_score", report.get("project_score"));
        result.put("logic_score", report.get("logic_score"));
        result.put("strengths", report.get("strengths"));
        result.put("weaknesses", report.get("weaknesses"));
        result.put("suggestions", report.get("suggestions"));
        result.put("next_actions", report.get("next_actions"));
        result.put("generated_time", report.get("generated_time"));
        return result;
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
                SELECT session_id, student_id, job_id, scene, model, prompt_version, status,
                       current_round, round_count, started_at, ended_at, created_time, updated_time
                FROM ai_session
                WHERE session_id = ?
                LIMIT 1
                """, sessionId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return rows.get(0);
    }

    private Map<String, Object> requireSessionDetail(String sessionId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT s.session_id, s.student_id, s.job_id, s.scene, s.model, s.prompt_version,
                       s.status, s.current_round, s.round_count, s.started_at, s.ended_at,
                       s.created_time, s.updated_time, r.report_id, r.score
                FROM ai_session s
                LEFT JOIN ai_interview_report r ON s.session_id = r.session_id
                WHERE s.session_id = ?
                LIMIT 1
                """, sessionId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return rows.get(0);
    }

    private Map<String, Object> requireSessionMessageDetail(String sessionId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT s.session_id, s.student_id, COALESCE(u.name, s.student_id) AS student_name,
                       s.job_id, jd.job_name, s.scene, s.model, s.prompt_version, s.status,
                       s.current_round, s.round_count, s.started_at, s.ended_at,
                       s.created_time, s.updated_time
                FROM ai_session s
                LEFT JOIN sys_user u ON u.account = s.student_id
                LEFT JOIN job_direction jd ON jd.job_id = s.job_id
                WHERE s.session_id = ?
                LIMIT 1
                """, sessionId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return rows.get(0);
    }

    private Map<String, Object> withCanGenerateReport(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>(row);
        result.putIfAbsent("current_round", 0);
        result.putIfAbsent("round_count", 5);
        boolean hasReport = StringUtils.hasText(stringValue(row.get("report_id")));
        result.put("can_generate_report", canGenerateReport(row, hasReport));
        return result;
    }

    private boolean canGenerateReport(Map<String, Object> session, boolean hasReport) {
        boolean finished = "已完成".equals(stringValue(session.get("status")));
        Integer currentRound = intValue(session.get("current_round"));
        Integer roundCount = intValue(session.get("round_count"));
        boolean enoughRounds = currentRound != null && roundCount != null && currentRound >= roundCount;
        return hasReport || finished || enoughRounds;
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

    private void requireOpenStudentSession(Map<String, Object> session, User actor) {
        requireStudentOwner(session, actor);
        if ("已完成".equals(stringValue(session.get("status")))) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
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

    private Map<String, Object> withMessageAliases(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>(row);
        String senderType = stringValue(row.get("sender_type"));
        boolean ai = "AI".equalsIgnoreCase(senderType) || "ai".equalsIgnoreCase(senderType);
        result.put("sender_type", ai ? "ai" : "student");
        result.put("role", ai ? "assistant" : "user");
        result.put("content", stringValue(row.get("message_content")));
        if (!StringUtils.hasText(stringValue(result.get("source")))) {
            result.put("source", ai ? "ai_stream" : "student_text");
        }
        result.putIfAbsent("round_no", row.get("round_no"));
        return result;
    }

    private void append(StringBuilder where, List<Object> params, String column, String value) {
        if (StringUtils.hasText(value)) {
            where.append("AND ").append(column).append(" = ?\n");
            params.add(value.trim());
        }
    }

    private void validateMediaUploadType(String mediaType, String extension, String mimeType) {
        if ("video".equals(mediaType)
                && VIDEO_EXTENSIONS.contains(extension)
                && VIDEO_MIME_TYPES.contains(mimeType)) {
            return;
        }
        if ("audio".equals(mediaType)
                && AUDIO_EXTENSIONS.contains(extension)
                && AUDIO_MIME_TYPES.contains(mimeType)) {
            return;
        }
        if ("screenshot".equals(mediaType)
                && SCREENSHOT_EXTENSIONS.contains(extension)
                && SCREENSHOT_MIME_TYPES.contains(mimeType)) {
            return;
        }
        throw new BusinessException(ErrorCode.FILE_INVALID);
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

    private String fileExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            throw new BusinessException(ErrorCode.FILE_INVALID);
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
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

    private String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private String limitForPrompt(String value) {
        String text = value == null ? "" : value.trim();
        return text.length() > 500 ? text.substring(0, 500) : text;
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

    private Integer intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = stringValue(value);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private int intValue(Object value, int defaultValue) {
        Integer parsed = intValue(value);
        return parsed == null ? defaultValue : parsed;
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

    private record MessageRequest(String content, String source, String clientMessageId) {
    }

    private interface MessageSupplier {
        Map<String, Object> get();
    }
}
