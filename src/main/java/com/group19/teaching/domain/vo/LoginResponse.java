package com.group19.teaching.domain.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record LoginResponse(
        String token,
        @JsonProperty("user_id") String userId,
        String name,
        String role,
        List<String> menus
) {
}
