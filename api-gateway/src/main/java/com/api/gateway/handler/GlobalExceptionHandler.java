package com.api.gateway.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({Exception.class})
    public ProblemDetail handleExceptions(Exception ex) throws Exception {
        log.error("Exception: {}", ex.getMessage());
        throw ex;
    }
}
