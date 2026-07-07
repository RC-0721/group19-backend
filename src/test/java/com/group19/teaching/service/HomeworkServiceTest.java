package com.group19.teaching.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import com.group19.teaching.domain.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

class HomeworkServiceTest {

    private final JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
    private final HomeworkService homeworkService = new HomeworkService(
            jdbcTemplate, null, new ObjectMapper(), "target/test-uploads", 50);

    @Test
    void createHomework() {
        when(jdbcTemplate.queryForList(anyString(), eq("class-java-001"), eq("teacher001")))
                .thenReturn(List.of(Map.of("course_id", "course-java-001")));

        Map<String, Object> result = homeworkService.create(Map.of(
                "course_class_id", "class-java-001",
                "title", "作业",
                "submit_requirement", "提交文本",
                "scoring_standard", "满分100",
                "deadline", "2099-01-01T00:00:00",
                "status", "已发布"
        ), user("teacher001", "TEACHER"));

        assertEquals("已发布", result.get("status"));
        verify(jdbcTemplate).update(anyString(), anyString(), eq("course-java-001"), eq("class-java-001"),
                eq("作业"), eq("提交文本"), eq("满分100"),
                eq(Timestamp.valueOf(LocalDateTime.parse("2099-01-01T00:00:00"))), eq("已发布"));
    }

    @Test
    void submitHomeworkCreatesReview() {
        when(jdbcTemplate.queryForList(anyString(), eq("hw-1"))).thenReturn(List.of(Map.of(
                "homework_id", "hw-1",
                "title", "作业",
                "scoring_standard", "满分100",
                "status", "已发布",
                "deadline", Timestamp.valueOf(LocalDateTime.now().plusDays(1))
        )));

        Map<String, Object> result = homeworkService.submit("hw-1", Map.of(
                "submit_content", "答案",
                "attachment_path", "/tmp/a.txt"
        ), user("student001", "STUDENT"));

        assertEquals("待批改", result.get("submit_status"));
        verify(jdbcTemplate).update(anyString(), anyString(), eq("hw-1"), eq("student001"), eq("答案"),
                eq("/tmp/a.txt"), eq("待批改"), any(Timestamp.class));
        verify(jdbcTemplate).update(anyString(), anyString(), anyString(), eq(60.0), eq("待教师确认"));
    }

    @Test
    void submitAllowsOnlyUploadedFile() {
        when(jdbcTemplate.queryForList(anyString(), eq("hw-1"))).thenReturn(List.of(Map.of(
                "homework_id", "hw-1",
                "title", "作业",
                "scoring_standard", "满分100",
                "status", "已发布",
                "deadline", Timestamp.valueOf(LocalDateTime.now().plusDays(1))
        )));
        when(jdbcTemplate.queryForList(anyString(), eq("file-1"), eq("hw-1"), eq("student001")))
                .thenReturn(List.of(Map.of(
                        "file_id", "file-1",
                        "file_name", "answer.md",
                        "file_type", "md",
                        "file_size", 12L,
                        "storage_path", "target/test-uploads/homeworks/hw-1/student001/file-1.md"
                )));

        Map<String, Object> result = homeworkService.submit("hw-1", Map.of(
                "file_ids", List.of("file-1")
        ), user("student001", "STUDENT"));

        assertEquals("待批改", result.get("submit_status"));
        assertEquals(1, result.get("attachment_count"));
        verify(jdbcTemplate).update(anyString(), anyString(), eq("hw-1"), eq("student001"), eq(null),
                eq("target/test-uploads/homeworks/hw-1/student001/file-1.md"), eq("待批改"), any(Timestamp.class));
        verify(jdbcTemplate).update(anyString(), anyString(), anyString(), eq("file-1"),
                eq("answer.md"), eq("md"), eq(12L), eq("target/test-uploads/homeworks/hw-1/student001/file-1.md"));
    }

    @Test
    void uploadRejectsExecutableFile() {
        when(jdbcTemplate.queryForList(anyString(), eq("hw-1"))).thenReturn(List.of(Map.of(
                "homework_id", "hw-1",
                "status", "已发布",
                "deadline", Timestamp.valueOf(LocalDateTime.now().plusDays(1))
        )));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> homeworkService.uploadFile("hw-1",
                        new MockMultipartFile("file", "bad.exe", "application/octet-stream", new byte[]{1}),
                        user("student001", "STUDENT")));

        assertEquals(ErrorCode.FILE_INVALID, exception.errorCode());
    }

    @Test
    void submitRejectsExpiredHomework() {
        when(jdbcTemplate.queryForList(anyString(), eq("hw-1"))).thenReturn(List.of(Map.of(
                "homework_id", "hw-1",
                "status", "已发布",
                "deadline", Timestamp.valueOf(LocalDateTime.now().minusDays(1))
        )));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> homeworkService.submit("hw-1", Map.of(
                        "submit_content", "答案",
                        "attachment_path", "/tmp/a.txt"
                ), user("student001", "STUDENT")));

        assertEquals(ErrorCode.STATE_NOT_ALLOWED, exception.errorCode());
    }

    @Test
    void reviewHomework() {
        when(jdbcTemplate.queryForList(anyString(), eq("review-1"), eq("teacher001"))).thenReturn(List.of(Map.of(
                "review_id", "review-1",
                "submit_id", "submit-1",
                "course_class_id", "class-java-001"
        )));

        Map<String, Object> result = homeworkService.review("review-1", Map.of(
                "teacher_score", 88.0,
                "teacher_comment", "合格"
        ), user("teacher001", "TEACHER"));

        assertEquals(88.0, result.get("teacher_score"));
        verify(jdbcTemplate).update(anyString(), eq(88.0), eq("合格"), any(Timestamp.class), eq("review-1"));
        verify(jdbcTemplate).update(anyString(), eq("已批改"), eq("submit-1"));
    }

    @Test
    void reviewRejectsRepeatedConfirm() {
        when(jdbcTemplate.queryForList(anyString(), eq("review-1"), eq("teacher001"))).thenReturn(List.of(Map.of(
                "review_id", "review-1",
                "teacher_score", 80.0,
                "submit_id", "submit-1",
                "course_class_id", "class-java-001"
        )));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> homeworkService.review("review-1", Map.of(
                        "teacher_score", 88.0,
                        "teacher_comment", "合格"
                ), user("teacher001", "TEACHER")));

        assertEquals(ErrorCode.STATE_NOT_ALLOWED, exception.errorCode());
    }

    @Test
    void duplicateAppealRejected() {
        when(jdbcTemplate.queryForList(anyString(), eq("submit-1"), eq("student001"))).thenReturn(List.of(Map.of(
                "submit_id", "submit-1",
                "homework_id", "hw-1",
                "student_id", "student001",
                "submit_status", "已批改"
        )));
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("submit-1"))).thenReturn(1);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> homeworkService.appeal("submit-1", Map.of("reason", "分数有误"), user("student001", "STUDENT")));

        assertEquals(ErrorCode.STATE_NOT_ALLOWED, exception.errorCode());
    }

    @Test
    void listSubmitsReturnsPagedRows() {
        when(jdbcTemplate.queryForList(anyString(), eq("hw-1"), eq("teacher001")))
                .thenReturn(List.of(Map.of("homework_id", "hw-1")));
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("hw-1"), eq("待批改")))
                .thenReturn(1);
        when(jdbcTemplate.queryForList(anyString(), eq("hw-1"), eq("待批改"), eq(10), eq(0)))
                .thenReturn(List.of(Map.of(
                        "submit_id", "submit-1",
                        "homework_id", "hw-1",
                        "review_id", "review-1",
                        "student_name", "学生一",
                        "ai_score", 60.0,
                        "ai_comment", "待教师确认",
                        "submit_status", "待批改"
                )));

        Map<String, Object> result = homeworkService.listSubmits(
                "hw-1", "待批改", 1, 10, user("teacher001", "TEACHER"));

        assertEquals(1, result.get("total"));
        Map<?, ?> record = (Map<?, ?>) ((List<?>) result.get("records")).get(0);
        assertEquals("hw-1", record.get("homework_id"));
        assertEquals("review-1", record.get("review_id"));
        assertEquals("学生一", record.get("student_name"));
        assertEquals("待批改", record.get("submit_status"));
        assertEquals(60.0, ((Map<?, ?>) record.get("ai_review")).get("score"));
        assertEquals("待教师确认", ((Map<?, ?>) record.get("ai_review")).get("comment"));
    }

    @Test
    void listSubmitsRejectsOtherTeacher() {
        when(jdbcTemplate.queryForList(anyString(), eq("hw-1"), eq("teacher002"))).thenReturn(List.of());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> homeworkService.listSubmits("hw-1", null, 1, 10, user("teacher002", "TEACHER")));

        assertEquals(ErrorCode.FORBIDDEN, exception.errorCode());
    }

    @Test
    void listSubmitsRejectsInvalidPage() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> homeworkService.listSubmits("hw-1", null, 0, 10, user("teacher001", "TEACHER")));

        assertEquals(ErrorCode.PARAM_ERROR, exception.errorCode());
    }

    @Test
    void listReturnsTeacherHomeworks() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("teacher001"), eq("已发布")))
                .thenReturn(1);
        when(jdbcTemplate.queryForList(anyString(), eq("teacher001"), eq("已发布"), eq(10), eq(0)))
                .thenReturn(List.of(Map.of("homework_id", "hw-1")));

        Map<String, Object> result = homeworkService.list(null, "已发布", 1, 10, user("teacher001", "TEACHER"));

        assertEquals(1, result.get("total"));
    }

    private static User user(String account, String role) {
        User user = new User();
        user.setAccount(account);
        user.setRole(role);
        return user;
    }
}
