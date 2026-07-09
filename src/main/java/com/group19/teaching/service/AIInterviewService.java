package com.group19.teaching.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private static final String INTRODUCTION_PROMPT = "请先进行自我介绍，介绍你的学习背景、项目经验和目标岗位相关技术。";
    private static final String INTERVIEW_FALLBACK =
            "Mock 面试反馈：回答已覆盖基础概念，请补充项目场景、关键取舍和验证结果。";
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
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final List<ScoreDimension> SCORE_DIMENSIONS = List.of(
            new ScoreDimension("knowledge", "专业知识", "岗位相关基础概念、工具、框架、行业常识的掌握范围"),
            new ScoreDimension("depth", "技术深度", "对技术或业务领域理解的透彻程度，能否深入原理、边界与局限"),
            new ScoreDimension("project", "项目表达", "过往项目的复杂度、个人贡献、成果量化和工程素养"),
            new ScoreDimension("logic", "逻辑结构", "分析拆解问题的结构化能力、推导严密性和问题解决策略"),
            new ScoreDimension("communication", "沟通呈现", "表达清晰度、简洁性、倾听理解力与协作沟通"),
            new ScoreDimension("reflection", "复盘改进", "自我认知、经验教训总结深度、学习意愿与成长潜力")
    );
    private static final String REPORT_SYSTEM_PROMPT = """
            你是一位严格、公正的面试评估专家。请根据面试对话记录，从六个维度给出 1-10 的整数分。
            六个维度：knowledge 专业知识、depth 技术深度、project 项目表达、logic 逻辑结构、communication 沟通呈现、reflection 复盘改进。
            必须基于记录中的具体行为证据评分，敢于拉开分差，不要全部集中在 5-7 分。
            只输出纯净 JSON，不要输出 Markdown、解释或额外文字。JSON 格式：
            {"dimension_scores":{"knowledge":7,"depth":6,"project":8,"logic":7,"communication":6,"reflection":5},"overall_score":7,"summary":"一句话综合评价"}
            """;

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
        requireJob(jobId);
        String sessionId = "session-" + UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO ai_session
                    (session_id, student_id, job_id, scene, model, prompt_version, status,
                     current_round, round_count, started_at, created_time, updated_time)
                VALUES (?, ?, ?, ?, ?, ?, '进行中', 0, ?, ?, ?, ?)
                """, sessionId, actor.getAccount(), jobId, scene, MODEL, promptVersion,
                roundCount, Timestamp.valueOf(now), Timestamp.valueOf(now), Timestamp.valueOf(now));
        return Map.of(
                "session_id", sessionId,
                "status", "进行中",
                "first_question", INTRODUCTION_PROMPT,
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
        ChatResult reply = chat(buildInterviewAiRequest(session, messageRequest.content()), actor, INTERVIEW_FALLBACK);
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
            sendDelta(emitter, INTERVIEW_FALLBACK);
            jdbcTemplate.update("""
                    INSERT INTO ai_call_log (log_id, scene, model, prompt_version, input_summary, output_summary, call_status)
                    VALUES (?, ?, ?, ?, ?, ?, '成功')
                    """, "ai-log-" + UUID.randomUUID(), "AI_INTERVIEW_CHAT", MODEL,
                    stringValue(session.get("prompt_version")), limitForPrompt(content), INTERVIEW_FALLBACK);
            return INTERVIEW_FALLBACK;
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
                + "会话创建时你只被设置为面试官身份，禁止主动输出任何开场回答。"
                + "只有收到学生自我介绍或回答后，才可以基于学生内容回应。"
                + "请围绕岗位和学生回答进行简洁追问，一次只问一个问题，不输出与面试无关的内容。";
        String prompt = "岗位：" + jobName + "\n"
                + "场景：" + stringValue(session.get("scene")) + "\n"
                + "Prompt版本：" + stringValue(session.get("prompt_version")) + "\n"
                + "轮次：" + roundNo + "/" + roundCount + "\n"
                + "最近对话：\n" + recentMessageText(stringValue(session.get("session_id"))) + "\n"
                + "学生本轮回答：\n" + content + "\n"
                + (roundNo == 1
                ? "这是学生首次输入，通常是自我介绍。请先简短确认其自我介绍内容，再提出一个与目标岗位相关的后续问题。"
                : "请给出自然的面试官回应，并继续提出一个后续问题。");
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
        InterviewReportScores scores = generateReportScores(session, actor);
        double knowledgeScore = scores.dimensionScores().get("knowledge");
        double depthScore = scores.dimensionScores().get("depth");
        double projectScore = scores.dimensionScores().get("project");
        double logicScore = scores.dimensionScores().get("logic");
        double communicationScore = scores.dimensionScores().get("communication");
        double reflectionScore = scores.dimensionScores().get("reflection");
        double overallScore = scores.overallScore();
        double expressionScore = communicationScore;
        double technicalScore = Math.round((knowledgeScore + depthScore) / 2.0);
        String strengths = scores.summary();
        String weaknesses = "请结合报告六维评分定位最低维度并继续补强。";
        String suggestions = "围绕专业知识、技术深度、项目表达、逻辑结构、沟通呈现和复盘改进做针对性复训。";
        String nextActions = "复盘本次面试记录；补充项目量化结果；针对最低分维度完成一次专项练习。";

        jdbcTemplate.update("""
                INSERT INTO ai_interview_report
                    (report_id, session_id, job_id, score, strength, weakness, suggestion,
                     overall_score, expression_score, technical_score, project_score, logic_score,
                     knowledge_score, depth_score, communication_score, reflection_score,
                     strengths, weaknesses, suggestions, next_actions, generated_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE score = VALUES(score), strength = VALUES(strength),
                    weakness = VALUES(weakness), suggestion = VALUES(suggestion),
                    overall_score = VALUES(overall_score), expression_score = VALUES(expression_score),
                    technical_score = VALUES(technical_score), project_score = VALUES(project_score),
                    logic_score = VALUES(logic_score), knowledge_score = VALUES(knowledge_score),
                    depth_score = VALUES(depth_score), communication_score = VALUES(communication_score),
                    reflection_score = VALUES(reflection_score), strengths = VALUES(strengths),
                    weaknesses = VALUES(weaknesses), suggestions = VALUES(suggestions),
                    next_actions = VALUES(next_actions), generated_time = VALUES(generated_time)
                """, reportId, sessionId, jobId, overallScore, strengths, weaknesses, suggestions,
                overallScore, expressionScore, technicalScore, projectScore, logicScore,
                knowledgeScore, depthScore, communicationScore, reflectionScore,
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
        result.put("score", overallScore);
        result.put("strength", strengths);
        result.put("weakness", weaknesses);
        result.put("suggestion", suggestions);
        result.put("overall_score", overallScore);
        result.put("expression_score", expressionScore);
        result.put("technical_score", technicalScore);
        result.put("knowledge_score", knowledgeScore);
        result.put("depth_score", depthScore);
        result.put("project_score", projectScore);
        result.put("logic_score", logicScore);
        result.put("communication_score", communicationScore);
        result.put("reflection_score", reflectionScore);
        result.put("dimension_scores", scores.dimensionScores());
        result.put("score_dimensions", scoreDimensions(scores.dimensionScores()));
        result.put("strengths", strengths);
        result.put("weaknesses", weaknesses);
        result.put("suggestions", suggestions);
        result.put("next_actions", nextActions);
        result.put("generated_time", generatedTime);
        return result;
    }

    public Map<String, Object> report(String sessionId, User actor) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT r.report_id, r.session_id, r.job_id, r.score, r.strength, r.weakness, r.suggestion,
                       r.overall_score, r.expression_score, r.technical_score, r.project_score,
                       r.logic_score, r.knowledge_score, r.depth_score, r.communication_score,
                       r.reflection_score, r.strengths, r.weaknesses, r.suggestions, r.next_actions,
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
        result.put("session_id", report.get("session_id"));
        result.put("job_id", report.get("job_id"));
        result.put("score", report.get("score"));
        result.put("strength", report.get("strength"));
        result.put("weakness", report.get("weakness"));
        result.put("suggestion", report.get("suggestion"));
        result.put("overall_score", report.get("overall_score"));
        result.put("expression_score", report.get("expression_score"));
        result.put("technical_score", report.get("technical_score"));
        result.put("knowledge_score", report.get("knowledge_score"));
        result.put("depth_score", report.get("depth_score"));
        result.put("project_score", report.get("project_score"));
        result.put("logic_score", report.get("logic_score"));
        result.put("communication_score", report.get("communication_score"));
        result.put("reflection_score", report.get("reflection_score"));
        Map<String, Double> dimensionScores = dimensionScoresFromReport(report);
        result.put("dimension_scores", dimensionScores);
        result.put("score_dimensions", scoreDimensions(dimensionScores));
        result.put("strengths", report.get("strengths"));
        result.put("weaknesses", report.get("weaknesses"));
        result.put("suggestions", report.get("suggestions"));
        result.put("next_actions", report.get("next_actions"));
        result.put("generated_time", report.get("generated_time"));
        return result;
    }

    private InterviewReportScores generateReportScores(Map<String, Object> session, User actor) {
        if (aiService == null) {
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
        }
        Map<String, Object> aiRequest = new LinkedHashMap<>();
        aiRequest.put("scene", "AI_INTERVIEW_REPORT");
        aiRequest.put("system_prompt", REPORT_SYSTEM_PROMPT);
        aiRequest.put("prompt", buildReportPrompt(session));
        Map<String, Object> aiResult = aiService.chat(aiRequest, actor);
        return parseReportScores(stringValue(aiResult.get("content")));
    }

    private String buildReportPrompt(Map<String, Object> session) {
        String sessionId = stringValue(session.get("session_id"));
        return "请根据以下面试资料生成六维评分 JSON。\n"
                + "岗位：" + jobName(stringValue(session.get("job_id"))) + "\n"
                + "场景：" + stringValue(session.get("scene")) + "\n"
                + "会话状态：" + stringValue(session.get("status")) + "\n"
                + "轮次：" + intValue(session.get("current_round"), 0) + "/" + intValue(session.get("round_count"), 5) + "\n"
                + "面试消息：\n" + reportMessageText(sessionId) + "\n"
                + "转写片段：\n" + transcriptText(sessionId) + "\n"
                + "媒体信息：\n" + mediaText(sessionId);
    }

    private String reportMessageText(String sessionId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT sender_type, message_content, source, round_no
                FROM ai_message
                WHERE session_id = ?
                ORDER BY COALESCE(round_no, 999999), created_time ASC, message_id ASC
                """, sessionId);
        if (rows.isEmpty()) {
            return "无";
        }
        StringBuilder text = new StringBuilder();
        for (Map<String, Object> row : rows) {
            String role = "ai".equalsIgnoreCase(stringValue(row.get("sender_type"))) ? "AI" : "学生";
            text.append(role)
                    .append("(round=").append(stringValue(row.get("round_no")))
                    .append(", source=").append(stringValue(row.get("source"))).append(")：")
                    .append(limitForPrompt(stringValue(row.get("message_content"))))
                    .append('\n');
        }
        return text.toString().trim();
    }

    private String transcriptText(String sessionId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT content, source, start_time, end_time, is_final
                FROM ai_interview_transcript_segment
                WHERE session_id = ?
                ORDER BY start_time ASC, created_time ASC, segment_id ASC
                """, sessionId);
        if (rows.isEmpty()) {
            return "无";
        }
        StringBuilder text = new StringBuilder();
        for (Map<String, Object> row : rows) {
            text.append('[').append(stringValue(row.get("start_time"))).append('-')
                    .append(stringValue(row.get("end_time"))).append(", ")
                    .append(stringValue(row.get("source"))).append(", final=")
                    .append(stringValue(row.get("is_final"))).append("] ")
                    .append(limitForPrompt(stringValue(row.get("content"))))
                    .append('\n');
        }
        return text.toString().trim();
    }

    private String mediaText(String sessionId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT media_type, file_name, mime_type, duration, file_size
                FROM ai_interview_media
                WHERE session_id = ?
                ORDER BY created_time ASC, media_id ASC
                """, sessionId);
        if (rows.isEmpty()) {
            return "无";
        }
        StringBuilder text = new StringBuilder();
        for (Map<String, Object> row : rows) {
            text.append(stringValue(row.get("media_type")))
                    .append(" file=").append(stringValue(row.get("file_name")))
                    .append(" mime=").append(stringValue(row.get("mime_type")))
                    .append(" duration=").append(stringValue(row.get("duration")))
                    .append(" size=").append(stringValue(row.get("file_size")))
                    .append('\n');
        }
        return text.toString().trim();
    }

    private InterviewReportScores parseReportScores(String content) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(extractJsonObject(content));
            JsonNode dimensionNode = root.path("dimension_scores");
            if (!dimensionNode.isObject()) {
                throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
            }
            int fallbackScore = score1To10(root.path("overall_score"), 5);
            Map<String, Double> dimensions = new LinkedHashMap<>();
            for (ScoreDimension dimension : SCORE_DIMENSIONS) {
                int score1To10 = score1To10(dimensionNode.path(dimension.key()), fallbackScore);
                dimensions.put(dimension.key(), (double) score1To10 * 10);
            }
            double overall = Math.round(dimensions.values().stream()
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(50.0));
            String summary = stringValue(root.path("summary").asText(""));
            if (!StringUtils.hasText(summary)) {
                summary = "已根据本次面试记录生成六维能力评分。";
            }
            return new InterviewReportScores(dimensions, overall, summary);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
        }
    }

    private String extractJsonObject(String content) {
        String text = stringValue(content);
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException("empty AI report");
        }
        int fencedStart = text.indexOf("```");
        if (fencedStart >= 0) {
            int bodyStart = text.indexOf('\n', fencedStart);
            int fencedEnd = text.indexOf("```", bodyStart < 0 ? fencedStart + 3 : bodyStart + 1);
            if (bodyStart >= 0 && fencedEnd > bodyStart) {
                text = text.substring(bodyStart + 1, fencedEnd).trim();
            }
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("missing JSON object");
        }
        return text.substring(start, end + 1);
    }

    private int score1To10(JsonNode node, int fallback) {
        int score = fallback;
        if (node != null && node.isNumber()) {
            score = (int) Math.round(node.asDouble());
        } else if (node != null && node.isTextual()) {
            try {
                score = (int) Math.round(Double.parseDouble(node.asText().trim()));
            } catch (NumberFormatException ignored) {
                score = fallback;
            }
        }
        return Math.max(1, Math.min(10, score));
    }

    private Map<String, Double> dimensionScoresFromReport(Map<String, Object> report) {
        Map<String, Double> scores = new LinkedHashMap<>();
        double knowledge = doubleOrDefault(report.get("knowledge_score"), doubleOrDefault(report.get("technical_score"), 0));
        double depth = doubleOrDefault(report.get("depth_score"), doubleOrDefault(report.get("technical_score"), 0));
        double project = doubleOrDefault(report.get("project_score"), 0);
        double logic = doubleOrDefault(report.get("logic_score"), 0);
        double communication = doubleOrDefault(report.get("communication_score"),
                doubleOrDefault(report.get("expression_score"), 0));
        double reflection = doubleOrDefault(report.get("reflection_score"), 0);
        scores.put("knowledge", knowledge);
        scores.put("depth", depth);
        scores.put("project", project);
        scores.put("logic", logic);
        scores.put("communication", communication);
        scores.put("reflection", reflection);
        return scores;
    }

    private List<Map<String, Object>> scoreDimensions(Map<String, Double> scores) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ScoreDimension dimension : SCORE_DIMENSIONS) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", dimension.key());
            item.put("label", dimension.label());
            item.put("score", scores.getOrDefault(dimension.key(), 0.0));
            item.put("desc", dimension.desc());
            result.add(item);
        }
        return result;
    }

    private double doubleOrDefault(Object value, double defaultValue) {
        Double parsed = doubleValue(value);
        return parsed == null ? defaultValue : parsed;
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

    private ChatResult chat(Map<String, Object> request, User actor, String fallbackContent) {
        if (aiService == null) {
            return new ChatResult(MODEL, fallbackContent);
        }
        Map<String, Object> result = aiService.chat(request, actor);
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

    private record InterviewReportScores(Map<String, Double> dimensionScores, double overallScore, String summary) {
    }

    private record ScoreDimension(String key, String label, String desc) {
    }

    private record MessageRequest(String content, String source, String clientMessageId) {
    }

    private interface MessageSupplier {
        Map<String, Object> get();
    }
}
