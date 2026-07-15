package com.zhimian.exception;

import com.zhimian.common.ErrorCode;
import lombok.Getter;

/**
 * 业务异常:业务代码中主动抛出,由全局异常处理器统一转成 Result
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
    }
}
