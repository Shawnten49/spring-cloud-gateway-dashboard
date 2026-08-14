package com.gatewaydashboard.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.server.MethodNotAllowedException;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        return ResponseEntity.status(e.getStatus())
                .body(ApiResponse.fail(e.getStatus().value(), e.getMessage()));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(ObjectOptimisticLockingFailureException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail(409, "该路由已被其他操作修改，请刷新后重试"));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, WebExchangeBindException.class})
    public ResponseEntity<ApiResponse<Void>> handleValidation(Exception e) {
        String message = "参数校验失败";
        if (e instanceof MethodArgumentNotValidException mve) {
            FieldError fieldError = mve.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
            if (fieldError != null) {
                message = fieldError.getField() + " " + fieldError.getDefaultMessage();
            }
        } else if (e instanceof WebExchangeBindException webe) {
            FieldError fieldError = webe.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
            if (fieldError != null) {
                message = fieldError.getField() + " " + fieldError.getDefaultMessage();
            }
        }
        return ResponseEntity.badRequest().body(ApiResponse.fail(400, message));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, ServerWebInputException.class})
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(Exception e) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(400, "请求体格式错误"));
    }

    @ExceptionHandler(MethodNotAllowedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(MethodNotAllowedException e) {
        log.warn("Method not allowed: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.fail(405, "请求方法不支持: " + e.getHttpMethod() + "，请检查接口定义"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleResponseStatus(ResponseStatusException e) {
        int status = e.getStatusCode().value();
        String message = switch (status) {
            // 未匹配路径（如 "No static resource ..."）统一为友好文案，避免泄漏框架内部信息
            case 404 -> "接口不存在";
            default -> e.getReason() == null || e.getReason().isBlank() ? "请求处理失败" : e.getReason();
        };
        return ResponseEntity.status(status).body(ApiResponse.fail(status, message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(500, "服务器内部错误"));
    }
}
