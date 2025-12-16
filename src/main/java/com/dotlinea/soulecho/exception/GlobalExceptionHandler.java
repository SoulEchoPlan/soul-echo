package com.dotlinea.soulecho.exception;

import com.dotlinea.soulecho.dto.ApiResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * <p>
 * 统一处理应用中的异常，返回标准化的 ApiResponse 格式
 * </p>
 *
 * @author fanfan187
 * @version v1.0.0
 * @since v1.0.0
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        logger.warn("业务异常: code={}, message={}", ex.getErrorCode(), ex.getMessage());

        ApiResponse<Void> response = ApiResponse.error(ex.getErrorCode(), ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * 处理资源未找到异常
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(ResourceNotFoundException ex) {
        logger.warn("资源未找到: {}", ex.getMessage());

        ApiResponse<Void> response = ApiResponse.error(
                ErrorCode.CHARACTER_NOT_FOUND.getCode(),
                ex.getMessage()
        );
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    /**
     * 处理参数校验异常（@Valid 触发）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException ex) {
        String errorMessage = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));

        logger.warn("参数校验失败: {}", errorMessage);

        ApiResponse<Void> response = ApiResponse.error(ErrorCode.PARAM_INVALID, errorMessage);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * 处理约束违反异常（@Validated 触发）
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        String errorMessage = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));

        logger.warn("约束违反: {}", errorMessage);

        ApiResponse<Void> response = ApiResponse.error(ErrorCode.PARAM_INVALID, errorMessage);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * 处理请求参数缺失异常
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException ex) {
        String errorMessage = "缺少必填参数: " + ex.getParameterName();
        logger.warn("参数缺失: {}", errorMessage);

        ApiResponse<Void> response = ApiResponse.error(ErrorCode.PARAM_MISSING, errorMessage);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * 处理文件大小超限异常
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        logger.warn("文件大小超限: {}", ex.getMessage());

        ApiResponse<Void> response = ApiResponse.error(ErrorCode.KNOWLEDGE_FILE_TOO_LARGE);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * 处理非法参数异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        logger.warn("非法参数: {}", ex.getMessage());

        ApiResponse<Void> response = ApiResponse.error(ErrorCode.PARAM_INVALID, ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * 处理通用异常（兜底）
     * <p>
     * 注意：对外返回脱敏信息，完整错误信息只记录日志
     * </p>
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        // 记录完整异常堆栈到日志
        logger.error("系统内部错误", ex);

        // 返回脱敏信息给客户端
        ApiResponse<Void> response = ApiResponse.error(ErrorCode.SYSTEM_ERROR);
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}