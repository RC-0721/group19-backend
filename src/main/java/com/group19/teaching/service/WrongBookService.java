package com.group19.teaching.service;

import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class WrongBookService {

    private final JdbcTemplate jdbcTemplate;

    public WrongBookService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> list(String studentId, Integer pageNo, Integer pageSize) {
        if (pageNo == null || pageNo < 1 || pageSize == null || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        Integer total = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM wrong_book wb
                JOIN question q ON wb.question_id = q.question_id
                WHERE wb.student_id = ?
                """, Integer.class, studentId);
        List<Map<String, Object>> records = jdbcTemplate.queryForList("""
                SELECT wb.question_id, q.stem, q.question_type, q.difficulty, q.answer_analysis,
                       wb.wrong_count, wb.last_wrong_time, wb.master_status
                FROM wrong_book wb
                JOIN question q ON wb.question_id = q.question_id
                WHERE wb.student_id = ?
                ORDER BY wb.last_wrong_time DESC, wb.question_id
                LIMIT ? OFFSET ?
                """, studentId, pageSize, (pageNo - 1) * pageSize);
        return Map.of(
                "records", records,
                "total", total == null ? 0 : total,
                "page_no", pageNo,
                "page_size", pageSize
        );
    }
}
