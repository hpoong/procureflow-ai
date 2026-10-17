package com.hopoong.procureflow.common.error;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity.status(errorCode.status()).body(new ErrorResponse(errorCode.code(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleArgumentNotValid(MethodArgumentNotValidException e) {
        List<String> messages = new ArrayList<>();
        for (ObjectError error : e.getBindingResult().getAllErrors()) {
            messages.add(describe(error));
        }
        return invalid(messages);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleMethodValidation(HandlerMethodValidationException e) {
        List<String> messages = new ArrayList<>();
        e.getParameterValidationResults().forEach(result -> {
            String name = result.getMethodParameter().getParameterName();
            result.getResolvableErrors().forEach(error -> {
                if (error instanceof ObjectError objectError) {
                    messages.add(describe(objectError));
                } else {
                    messages.add(name + ": " + error.getDefaultMessage());
                }
            });
        });
        return invalid(messages);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException e) {
        return invalid(List.of(e.getHeaderName() + ": 필수 헤더입니다"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException e) {
        log.debug("unreadable request body", e);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(CommonErrorCode.INVALID_REQUEST.code(), CommonErrorCode.INVALID_REQUEST.message()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        if (e instanceof org.springframework.web.ErrorResponse springError) {
            HttpStatusCode status = springError.getStatusCode();
            HttpStatus resolved = HttpStatus.resolve(status.value());
            String code = resolved != null ? resolved.name() : "HTTP_" + status.value();
            String message = resolved != null ? resolved.getReasonPhrase() : code;
            return ResponseEntity.status(status).body(new ErrorResponse(code, message));
        }
        log.error("unexpected error", e);
        return ResponseEntity.status(CommonErrorCode.INTERNAL_ERROR.status())
                .body(new ErrorResponse(CommonErrorCode.INTERNAL_ERROR.code(), CommonErrorCode.INTERNAL_ERROR.message()));
    }

    private ResponseEntity<ErrorResponse> invalid(List<String> messages) {
        String message = messages.isEmpty() ? CommonErrorCode.INVALID_REQUEST.message()
                : String.join("; ", messages.stream().sorted().toList());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(CommonErrorCode.INVALID_REQUEST.code(), message));
    }

    private String describe(ObjectError error) {
        if (error instanceof FieldError fieldError) {
            return fieldError.getField() + ": " + fieldError.getDefaultMessage();
        }
        return error.getObjectName() + ": " + error.getDefaultMessage();
    }
}
