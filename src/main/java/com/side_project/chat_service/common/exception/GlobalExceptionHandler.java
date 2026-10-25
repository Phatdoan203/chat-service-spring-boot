package com.side_project.chat_service.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApi(ApiException ex) {
        log.warn("api error code={} message={}", ex.errorCode(), ex.getMessage());
        return problem(ex.errorCode().status(), ex.getMessage(), ex.errorCode().name());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse(ErrorCode.INVALID_REQUEST.defaultMessage());
        return problem(ErrorCode.INVALID_REQUEST.status(), detail, ErrorCode.INVALID_REQUEST.name());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("api error code={} param={}", "BAD_IDENTIFIER", ex.getName());
        return problem(HttpStatus.BAD_REQUEST, "Tham so '" + ex.getName() + "' sai dinh dang", "BAD_IDENTIFIER");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("loi khong luong truoc", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Loi he thong", "INTERNAL_ERROR");
    }

    private static ProblemDetail problem(HttpStatus status, String detail, String code) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }
}
