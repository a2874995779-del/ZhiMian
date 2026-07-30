package com.zhimian.exception;

import com.zhimian.common.ErrorCode;
import com.zhimian.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

/**
 * 全局异常处理器:所有异常在这里统一转成 Result 格式
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常:业务层主动抛出,message 可直接展示给用户
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 参数校验异常:@Valid 校验失败时抛出,取第一条错误信息返回
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidException(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError == null
                ? ErrorCode.PARAMS_ERROR.getMessage()
                : fieldError.getField() + " " + fieldError.getDefaultMessage();
        return Result.error(ErrorCode.PARAMS_ERROR, message);
    }

    /**
     * 请求体解析失败(JSON 格式错误、类型不匹配等)
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleNotReadableException(HttpMessageNotReadableException e) {
        return Result.error(ErrorCode.PARAMS_ERROR, "请求体格式错误");
    }

    /**
     * 兜底:未预期的异常,打全栈日志,对外只暴露笼统信息(不泄露内部细节)
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.error(ErrorCode.SYSTEM_ERROR);
    }

    @ExceptionHandler({NonTransientAiException.class, TransientAiException.class, RestClientException.class})
    public Result<Void> handleAiException(Exception e){
        log.error("AI服务调用失败",e);
        return Result.error(ErrorCode.AI_SERVICE_ERROR);
    }
}
