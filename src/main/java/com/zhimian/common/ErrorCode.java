package com.zhimian.common;

import lombok.Getter;

/**
 * 统一错误码
 * 规范:五位数字,前三位对齐 HTTP 语义,后两位业务细分(见开发文档 4.2 节)
 */
@Getter
public enum ErrorCode {

    SUCCESS(0, "ok"),

    PARAMS_ERROR(40000, "请求参数错误"),
    NOT_LOGIN(40100, "未登录"),
    TOKEN_EXPIRED(40101, "登录已过期"),
    NO_AUTH(40300, "无权限"),
    NOT_FOUND(40400, "资源不存在"),
    CONFLICT(40900, "资源冲突"),
    TOO_MANY_REQUESTS(42900, "请求过于频繁"),

    SYSTEM_ERROR(50000, "系统内部异常"),
    AI_SERVICE_ERROR(50001, "AI 服务调用失败");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
