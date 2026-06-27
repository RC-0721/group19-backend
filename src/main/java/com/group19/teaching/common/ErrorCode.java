package com.group19.teaching.common;

public enum ErrorCode {
    PARAM_ERROR("40001", "参数错误"),
    AUTH_FAILED("40101", "认证失败"),
    FORBIDDEN("40301", "权限不足"),
    RESOURCE_NOT_FOUND("40401", "资源不存在"),
    STATE_NOT_ALLOWED("40901", "状态不允许"),
    FILE_INVALID("41301", "文件不合法"),
    INTERNAL_ERROR("50001", "系统内部错误"),
    AI_UNAVAILABLE("50201", "AI 服务不可用"),
    KNOWLEDGE_UNAVAILABLE("50301", "知识库不可用");

    private final String code;
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }
}
