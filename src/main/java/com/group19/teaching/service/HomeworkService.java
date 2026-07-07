package com.group19.teaching.service;

import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import com.group19.teaching.domain.entity.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class HomeworkService {

    private static final List<String> ALLOWED_FILE_TYPES = List.of(
            "txt", "md", "pdf", "doc", "docx",
            "jpg", "jpeg", "png",
            "py", "java", "cpp", "c", "js", "ts", "html", "css", "xml", "json", "yaml", "yml",
            "zip"
    );

    private final JdbcTemplate jdbcTemplate;
    private final AiService aiService;
    private final ObjectMapper objectMapper;
    private final Path uploadDir;
    private final long maxUploadBytes;

    public HomeworkService(
            JdbcTemplate jdbcTemplate,
            AiService aiService,
            ObjectMapper objectMapper,
            @Value("${teaching.upload-dir:data/uploads}") String uploadDir,
            @Value("${teaching.homework-upload.max-size-mb:50}") long maxSizeMb) {
        this.jdbcTemplate = jdbcTemplate;
        this.aiService = aiService;
        this.objectMapper = objectMapper;
        this.uploadDir = Path.of(uploadDir);
        this.maxUploadBytes = maxSizeMb * 1024 * 1024;
    }

    public Map<String, Object> list(
            String courseClassId,
            String status,
            Integer pageNo,
            Integer pageSize,
            User actor) {
        if (pageNo == null || pageNo < 1 || pageSize == null || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        if (StringUtils.hasText(courseClassId)) {
            requireCourseClassAccess(courseClassId, actor);
        }
        List<Object> params = new ArrayList<>();
        String where = buildListWhere(courseClassId, status, actor, params);
        Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM homework h JOIN course_class cc ON h.course_class_id = cc.course_class_id " + where,
                Integer.class, params.toArray());
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(pageSize);
        pageParams.add((pageNo - 1) * pageSize);
        List<Map<String, Object>> records = jdbcTemplate.queryForList("""
                SELECT h.homework_id, h.course_id, h.course_class_id, h.title, h.submit_requirement,
                       h.scoring_standard, h.deadline, h.status, cc.class_id, cc.teacher_id
                FROM homework h
                JOIN course_class cc ON h.course_class_id = cc.course_class_id
                """ + where + """
                ORDER BY h.deadline DESC, h.homework_id
                LIMIT ? OFFSET ?
                """, pageParams.toArray());
        return Map.of("records", records, "total", total == null ? 0 : total,
                "page_no", pageNo, "page_size", pageSize);
    }

    @Transactional
    public Map<String, Object> create(Map<String, Object> request, User actor) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        String courseClassId = stringValue(request.get("course_class_id"));
        String title = stringValue(request.get("title"));
        String submitRequirement = stringValue(request.get("submit_requirement"));
        String scoringStandard = stringValue(request.get("scoring_standard"));
        String status = stringValue(request.get("status"));
        LocalDateTime deadline = parseDateTime(request.get("deadline"));
        if (!StringUtils.hasText(courseClassId) || !StringUtils.hasText(title)
                || !StringUtils.hasText(submitRequirement) || !StringUtils.hasText(scoringStandard)
                || deadline == null || !List.of("草稿", "已发布").contains(status)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        String courseId = requireTeacherCourse(courseClassId, actor.getAccount());
        String homeworkId = "hw-" + UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO homework (homework_id, course_id, course_class_id, title, submit_requirement, scoring_standard, deadline, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, homeworkId, courseId, courseClassId, title, submitRequirement, scoringStandard,
                Timestamp.valueOf(deadline), status);
        return Map.of("homework_id", homeworkId, "status", status);
    }

    @Transactional
    public Map<String, Object> uploadFile(String homeworkId, MultipartFile file, User actor) {
        if (!StringUtils.hasText(homeworkId) || file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        Map<String, Object> homework = requireOpenHomework(homeworkId);
        String originalName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().trim();
        String fileType = fileType(originalName);
        if (!ALLOWED_FILE_TYPES.contains(fileType) || file.getSize() > maxUploadBytes) {
            throw new BusinessException(ErrorCode.FILE_INVALID);
        }
        String fileId = "hw-file-" + UUID.randomUUID();
        Path target = uploadDir.resolve("homeworks")
                .resolve(homeworkId)
                .resolve(actor.getAccount())
                .resolve(fileId + "." + fileType)
                .normalize();
        try {
            Files.createDirectories(target.getParent());
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        jdbcTemplate.update("""
                INSERT INTO homework_upload_file
                  (file_id, homework_id, student_id, file_name, file_type, file_size, storage_path, bind_status)
                VALUES (?, ?, ?, ?, ?, ?, ?, '未绑定')
                """, fileId, homework.get("homework_id"), actor.getAccount(), originalName, fileType, file.getSize(),
                target.toString());
        return Map.of(
                "file_id", fileId,
                "homework_id", homeworkId,
                "file_name", originalName,
                "file_type", fileType,
                "file_size", file.getSize(),
                "bind_status", "未绑定"
        );
    }

    @Transactional
    public Map<String, Object> submit(String homeworkId, Map<String, Object> request, User actor) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        String submitContent = stringValue(request.get("submit_content"));
        String attachmentPath = stringValue(request.get("attachment_path"));
        List<String> fileIds = stringList(request.get("file_ids"));
        if (!StringUtils.hasText(homeworkId)
                || (!StringUtils.hasText(submitContent) && !StringUtils.hasText(attachmentPath) && fileIds.isEmpty())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        Map<String, Object> homework = requireOpenHomework(homeworkId);
        List<Map<String, Object>> files = requireBindableFiles(homeworkId, actor.getAccount(), fileIds);
        String submitId = "hw-submit-" + UUID.randomUUID();
        String reviewId = "hw-review-" + UUID.randomUUID();
        String submitStatus = "待批改";
        LocalDateTime submitTime = LocalDateTime.now();
        String finalAttachmentPath = StringUtils.hasText(attachmentPath) ? attachmentPath : joinedPaths(files);
        jdbcTemplate.update("""
                INSERT INTO homework_submit (submit_id, homework_id, student_id, submit_content, attachment_path, submit_status, submit_time)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, submitId, homework.get("homework_id"), actor.getAccount(), blankToNull(submitContent), blankToNull(finalAttachmentPath),
                submitStatus, Timestamp.valueOf(submitTime));
        for (Map<String, Object> file : files) {
            jdbcTemplate.update("""
                    INSERT INTO homework_attachment
                      (attachment_id, submit_id, file_id, file_name, file_type, file_size, storage_path)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, "hw-att-" + UUID.randomUUID(), submitId, file.get("file_id"), file.get("file_name"),
                    file.get("file_type"), file.get("file_size"), file.get("storage_path"));
            jdbcTemplate.update("""
                    UPDATE homework_upload_file
                    SET bind_status = '已绑定'
                    WHERE file_id = ?
                    """, file.get("file_id"));
        }
        Map<String, Object> aiReview = aiReview(homework, submitContent, files, actor);
        jdbcTemplate.update("""
                INSERT INTO homework_review (review_id, submit_id, ai_score, ai_comment)
                VALUES (?, ?, ?, ?)
                """, reviewId, submitId, aiReview.get("ai_score"), aiReview.get("ai_comment"));
        return Map.of(
                "submit_id", submitId,
                "submit_status", submitStatus,
                "submit_time", submitTime,
                "attachment_count", files.size(),
                "review_id", reviewId
        );
    }

    @Transactional
    public Map<String, Object> review(String reviewId, Map<String, Object> request, User actor) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        Double teacherScore = doubleValue(request.get("teacher_score"));
        String teacherComment = stringValue(request.get("teacher_comment"));
        if (!StringUtils.hasText(reviewId) || teacherScore == null || teacherScore < 0 || teacherScore > 100
                || !StringUtils.hasText(teacherComment)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT hr.review_id, hr.teacher_score, hs.submit_id, h.course_class_id
                FROM homework_review hr
                JOIN homework_submit hs ON hr.submit_id = hs.submit_id
                JOIN homework h ON hs.homework_id = h.homework_id
                JOIN course_class cc ON h.course_class_id = cc.course_class_id
                WHERE hr.review_id = ? AND cc.teacher_id = ?
                LIMIT 1
                """, reviewId, actor.getAccount());
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (rows.get(0).get("teacher_score") != null) {
            throw new BusinessException(ErrorCode.STATE_NOT_ALLOWED);
        }
        LocalDateTime reviewTime = LocalDateTime.now();
        jdbcTemplate.update("""
                UPDATE homework_review
                SET teacher_score = ?, teacher_comment = ?, review_time = ?
                WHERE review_id = ?
                """, teacherScore, teacherComment, Timestamp.valueOf(reviewTime), reviewId);
        jdbcTemplate.update("""
                UPDATE homework_submit
                SET submit_status = ?
                WHERE submit_id = ?
                """, "已批改", rows.get(0).get("submit_id"));
        return Map.of("review_id", reviewId, "teacher_score", teacherScore, "review_time", reviewTime);
    }

    @Transactional
    public Map<String, Object> appeal(String submitId, Map<String, Object> request, User actor) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        String reason = stringValue(request.get("reason"));
        if (!StringUtils.hasText(submitId) || !StringUtils.hasText(reason)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        List<Map<String, Object>> submits = jdbcTemplate.queryForList("""
                SELECT hs.submit_id, hs.homework_id, hs.student_id, hs.submit_status
                FROM homework_submit hs
                WHERE hs.submit_id = ? AND hs.student_id = ?
                LIMIT 1
                """, submitId, actor.getAccount());
        if (submits.isEmpty()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (!"已批改".equals(stringValue(submits.get(0).get("submit_status")))) {
            throw new BusinessException(ErrorCode.STATE_NOT_ALLOWED);
        }
        Integer unfinished = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM homework_appeal
                WHERE submit_id = ? AND status IN ('申诉中')
                """, Integer.class, submitId);
        if (unfinished != null && unfinished > 0) {
            throw new BusinessException(ErrorCode.STATE_NOT_ALLOWED);
        }
        String appealId = "hw-appeal-" + UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO homework_appeal
                  (appeal_id, homework_id, submit_id, student_id, reason, status)
                VALUES (?, ?, ?, ?, ?, '申诉中')
                """, appealId, submits.get(0).get("homework_id"), submitId, actor.getAccount(), reason);
        jdbcTemplate.update("UPDATE homework_submit SET submit_status = '申诉中' WHERE submit_id = ?", submitId);
        return Map.of("appeal_id", appealId, "submit_id", submitId, "status", "申诉中");
    }

    @Transactional
    public Map<String, Object> reviewAppeal(String appealId, Map<String, Object> request, User actor) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        String verdict = stringValue(request.get("verdict"));
        if (!StringUtils.hasText(appealId) || !StringUtils.hasText(verdict)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT ha.appeal_id, ha.submit_id, ha.status, h.course_class_id
                FROM homework_appeal ha
                JOIN homework h ON ha.homework_id = h.homework_id
                JOIN course_class cc ON h.course_class_id = cc.course_class_id
                WHERE ha.appeal_id = ? AND cc.teacher_id = ?
                LIMIT 1
                """, appealId, actor.getAccount());
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (!"申诉中".equals(stringValue(rows.get(0).get("status")))) {
            throw new BusinessException(ErrorCode.STATE_NOT_ALLOWED);
        }
        jdbcTemplate.update("""
                UPDATE homework_appeal
                SET status = '已复核', teacher_id = ?, verdict = ?, reviewed_time = ?
                WHERE appeal_id = ?
                """, actor.getAccount(), verdict, Timestamp.valueOf(LocalDateTime.now()), appealId);
        jdbcTemplate.update("UPDATE homework_submit SET submit_status = '已复核' WHERE submit_id = ?",
                rows.get(0).get("submit_id"));
        return Map.of("appeal_id", appealId, "status", "已复核", "verdict", verdict);
    }

    public Map<String, Object> listSubmits(
            String homeworkId,
            String submitStatus,
            Integer pageNo,
            Integer pageSize,
            User actor) {
        if (!StringUtils.hasText(homeworkId) || pageNo == null || pageNo < 1
                || pageSize == null || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        requireTeacherHomework(homeworkId, actor.getAccount());

        List<Object> params = new java.util.ArrayList<>();
        params.add(homeworkId);
        String where = "WHERE hs.homework_id = ?\n";
        if (StringUtils.hasText(submitStatus)) {
            where += "AND hs.submit_status = ?\n";
            params.add(submitStatus);
        }
        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM homework_submit hs " + where,
                Integer.class,
                params.toArray());
        List<Object> pageParams = new java.util.ArrayList<>(params);
        pageParams.add(pageSize);
        pageParams.add((pageNo - 1) * pageSize);
        List<Map<String, Object>> records = jdbcTemplate.queryForList("""
                SELECT hs.submit_id, hs.homework_id, hs.student_id, hs.submit_content, hs.attachment_path,
                       COALESCE(u.name, hs.student_id) AS student_name,
                       hs.submit_status, hs.submit_time, hr.review_id, hr.ai_score, hr.teacher_score,
                       hr.ai_comment, hr.teacher_comment, hr.review_time
                FROM homework_submit hs
                LEFT JOIN homework_review hr ON hs.submit_id = hr.submit_id
                LEFT JOIN sys_user u ON u.account = hs.student_id
                """ + where + """
                ORDER BY hs.submit_time DESC, hs.submit_id
                LIMIT ? OFFSET ?
                """, pageParams.toArray());
        List<Map<String, Object>> enrichedRecords = records.stream()
                .<Map<String, Object>>map(row -> {
                    Map<String, Object> item = new LinkedHashMap<>(row);
                    Map<String, Object> aiReview = new LinkedHashMap<>();
                    aiReview.put("score", item.get("ai_score"));
                    aiReview.put("comment", item.get("ai_comment"));
                    item.put("ai_review", aiReview);
                    return item;
                }).toList();
        return Map.of(
                "records", enrichedRecords,
                "total", total == null ? 0 : total,
                "page_no", pageNo,
                "page_size", pageSize
        );
    }

    private Map<String, Object> requireOpenHomework(String homeworkId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT homework_id, title, submit_requirement, scoring_standard, status, deadline
                FROM homework
                WHERE homework_id = ?
                LIMIT 1
                """, homeworkId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        Map<String, Object> homework = rows.get(0);
        if (!"已发布".equals(stringValue(homework.get("status")))
                || LocalDateTime.now().isAfter(toLocalDateTime(homework.get("deadline")))) {
            throw new BusinessException(ErrorCode.STATE_NOT_ALLOWED);
        }
        return homework;
    }

    private List<Map<String, Object>> requireBindableFiles(String homeworkId, String studentId, List<String> fileIds) {
        List<Map<String, Object>> files = new ArrayList<>();
        for (String fileId : fileIds) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                    SELECT file_id, homework_id, student_id, file_name, file_type, file_size, storage_path, bind_status
                    FROM homework_upload_file
                    WHERE file_id = ? AND homework_id = ? AND student_id = ? AND bind_status = '未绑定'
                    LIMIT 1
                    """, fileId, homeworkId, studentId);
            if (rows.isEmpty()) {
                throw new BusinessException(ErrorCode.STATE_NOT_ALLOWED);
            }
            files.add(rows.get(0));
        }
        return files;
    }

    private Map<String, Object> aiReview(Map<String, Object> homework, String submitContent,
                                         List<Map<String, Object>> files, User actor) {
        if (aiService == null) {
            return linkedMap("ai_score", 60.0, "ai_comment", "待教师确认");
        }
        try {
            Map<String, Object> result = aiService.chat(Map.of(
                    "scene", "HOMEWORK_REVIEW",
                    "system_prompt", "你是课程作业助教，请给出 0-100 的建议分和简短批改意见。",
                    "prompt", "作业：" + stringValue(homework.get("title"))
                            + "\n评分标准：" + stringValue(homework.get("scoring_standard"))
                            + "\n提交内容：" + submitContent
                            + "\n附件数量：" + files.size()
            ), actor);
            return linkedMap(
                    "ai_score", 60.0,
                    "ai_comment", limit("AI 建议：" + stringValue(result.get("content")), 500)
            );
        } catch (BusinessException exception) {
            if (exception.errorCode() != ErrorCode.AI_UNAVAILABLE) {
                throw exception;
            }
            return linkedMap("ai_score", null, "ai_comment", "AI 批改失败，待教师确认");
        } catch (RuntimeException exception) {
            return linkedMap("ai_score", null, "ai_comment", "AI 批改失败，待教师确认");
        }
    }

    private String joinedPaths(List<Map<String, Object>> files) {
        return files.stream()
                .map(file -> stringValue(file.get("storage_path")))
                .filter(StringUtils::hasText)
                .reduce((left, right) -> left + ";" + right)
                .orElse("");
    }

    private List<String> stringList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> values) {
            return values.stream()
                    .map(this::stringValue)
                    .filter(StringUtils::hasText)
                    .toList();
        }
        String text = stringValue(value);
        return StringUtils.hasText(text) ? List.of(text) : List.of();
    }

    private String fileType(String fileName) {
        int index = fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            throw new BusinessException(ErrorCode.FILE_INVALID);
        }
        return fileName.substring(index + 1).toLowerCase(java.util.Locale.ROOT);
    }

    private String json(Object value) {
        if (objectMapper == null) {
            return String.valueOf(value);
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
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

    private Map<String, Object> linkedMap(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            map.put(String.valueOf(values[index]), values[index + 1]);
        }
        return map;
    }

    private String requireTeacherCourse(String courseClassId, String teacherId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT course_id
                FROM course_class
                WHERE course_class_id = ? AND teacher_id = ?
                LIMIT 1
                """, courseClassId, teacherId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return stringValue(rows.get(0).get("course_id"));
    }

    private void requireTeacherHomework(String homeworkId, String teacherId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT h.homework_id
                FROM homework h
                JOIN course_class cc ON h.course_class_id = cc.course_class_id
                WHERE h.homework_id = ? AND cc.teacher_id = ?
                LIMIT 1
                """, homeworkId, teacherId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private String buildListWhere(String courseClassId, String status, User actor, List<Object> params) {
        StringBuilder where = new StringBuilder("WHERE 1 = 1\n");
        if ("STUDENT".equalsIgnoreCase(actor.getRole())) {
            where.append("""
                    AND EXISTS (
                      SELECT 1 FROM student_profile sp
                      WHERE sp.student_id = ? AND sp.class_id = cc.class_id
                    )
                    """);
            params.add(actor.getAccount());
        } else if ("TEACHER".equalsIgnoreCase(actor.getRole())) {
            where.append("AND cc.teacher_id = ?\n");
            params.add(actor.getAccount());
        } else {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        append(where, params, "h.course_class_id", courseClassId);
        append(where, params, "h.status", status);
        return where.toString();
    }

    private void requireCourseClassAccess(String courseClassId, User actor) {
        Integer count;
        if ("STUDENT".equalsIgnoreCase(actor.getRole())) {
            count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM course_class cc
                    JOIN student_profile sp ON cc.class_id = sp.class_id
                    WHERE cc.course_class_id = ? AND sp.student_id = ?
                    """, Integer.class, courseClassId, actor.getAccount());
        } else if ("TEACHER".equalsIgnoreCase(actor.getRole())) {
            count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM course_class
                    WHERE course_class_id = ? AND teacher_id = ?
                    """, Integer.class, courseClassId, actor.getAccount());
        } else {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void append(StringBuilder where, List<Object> params, String column, String value) {
        if (StringUtils.hasText(value)) {
            where.append("AND ").append(column).append(" = ?\n");
            params.add(value.trim());
        }
    }

    private LocalDateTime parseDateTime(Object value) {
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return LocalDateTime.parse(text.trim().replace(" ", "T"));
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        throw new BusinessException(ErrorCode.PARAM_ERROR);
    }

    private Double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Double.parseDouble(text.trim());
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
