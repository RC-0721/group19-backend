package com.group19.teaching.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.group19.teaching.domain.entity.User;
import com.group19.teaching.service.AuthService;
import com.group19.teaching.service.ProfileService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProfileController.class)
class ProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProfileService profileService;

    @MockBean
    private AuthService authService;

    @Test
    void getReturnsProfile() throws Exception {
        User student = user("student001", "STUDENT");
        when(authService.requireRole("student-token", "STUDENT", "TEACHER")).thenReturn(student);
        when(profileService.get("student001", "job-java-backend", student)).thenReturn(Map.of(
                "profile_id", "profile-1",
                "knowledge_mastery", "掌握",
                "skill_mastery", "掌握",
                "evidences", List.of(),
                "recommendations", List.of()
        ));

        mockMvc.perform(get("/api/profiles/student001")
                        .header("token", "student-token")
                        .param("job_id", "job-java-backend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profile_id").value("profile-1"));
    }

    private static User user(String account, String role) {
        User user = new User();
        user.setAccount(account);
        user.setRole(role);
        return user;
    }
}
