package com.zhimian.common;

import lombok.Data;

/**
 * 统一返回体
 * 约定:HTTP 状态码恒为 200,业务成败由 code 表达(0 为成功)
 */
@Data
public class Result<T> {

    private int code;
    private String message;
    private T data;

    private Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), data);
    }

    public static Result<Void> success() {
        return success(null);
    }

    public static <T> Result<T> error(ErrorCode errorCode) {
        return new Result<>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    /**
     * 使用错误码但覆盖提示语(如参数校验的具体失败原因)
     */
    public static <T> Result<T> error(ErrorCode errorCode, String message) {
        return new Result<>(errorCode.getCode(), message, null);
    }

    /**
     * 直接指定 code 与提示语(供全局异常处理器转译 BusinessException 使用)
     */
    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }
}
