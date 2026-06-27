package com.group19.teaching.common;

import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusinessException(BusinessException exception) {
        return ApiResponse.failure(exception.errorCode());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleValidationException() {
        return ApiResponse.failure(ErrorCode.PARAM_ERROR);
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleUnexpectedException() {
        return ApiResponse.failure(ErrorCode.INTERNAL_ERROR);
    }
}
