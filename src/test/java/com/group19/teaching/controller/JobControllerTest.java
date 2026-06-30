package com.group19.teaching.controller;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import com.group19.teaching.service.AuthService;
import com.group19.teaching.service.JobService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(JobController.class)
class JobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JobService jobService;

    @MockBean
    private AuthService authService;

    @Test
    void listReturnsSkillStandards() throws Exception {
        when(jobService.list("job-java-backend", null, 1, 10)).thenReturn(Map.of(
                "records", List.of(Map.of("skill_id", "skill-1")),
                "total", 1,
                "page_no", 1,
                "page_size", 10
        ));

        mockMvc.perform(get("/api/jobs")
                        .header("token", "token")
                        .param("job_id", "job-java-backend")
                        .param("page_no", "1")
                        .param("page_size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.records[0].skill_id").value("skill-1"));
    }

    @Test
    void saveRejectsNonAdmin() throws Exception {
        when(authService.requireRole("teacher-token", "EDU_ADMIN"))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        mockMvc.perform(post("/api/jobs")
                        .header("token", "teacher-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("40301"));
    }

    @Test
    void saveReturnsIds() throws Exception {
        when(jobService.save(anyMap())).thenReturn(Map.of(
                "job_id", "job-1",
                "tech_id", "tech-1",
                "skill_id", "skill-1"
        ));

        mockMvc.perform(post("/api/jobs")
                        .header("token", "admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"job_name\":\"Java 后端\",\"job_description\":\"说明\",\"difficulty_level\":\"初级\",\"tech_name\":\"Java\",\"skill_name\":\"基础\",\"ability_level\":\"基础\",\"evidence_requirement\":\"证据\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.skill_id").value("skill-1"));
    }
}
