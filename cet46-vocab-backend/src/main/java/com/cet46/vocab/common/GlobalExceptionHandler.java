package com.cet46.vocab.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::buildFieldErrorMessage)
                .distinct()
                .collect(Collectors.joining("; "));
        if (message.isBlank()) {
            message = ResultCode.BAD_REQUEST.getMessage();
        }
        return Result.fail(ResultCode.BAD_REQUEST.getCode(), message);
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    public Result<Void> handleUsernameNotFoundException(UsernameNotFoundException ex) {
        return Result.fail(ResultCode.UNAUTHORIZED.getCode(), ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public Result<Void> handleAccessDeniedException(AccessDeniedException ex) {
        return Result.fail(ResultCode.FORBIDDEN.getCode(), ex.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public Result<Void> handleRuntimeException(RuntimeException ex) {
        log.error("Runtime exception caught", ex);
        return Result.fail(ResultCode.INTERNAL_ERROR);
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception ex) {
        log.error("Unhandled exception caught", ex);
        return Result.fail(ResultCode.INTERNAL_ERROR);
    }

    private String buildFieldErrorMessage(FieldError fieldError) {
        String field = fieldError.getField();
        String err = fieldError.getDefaultMessage() == null ? "invalid value" : fieldError.getDefaultMessage();
        return field + ": " + err;
    }
}
