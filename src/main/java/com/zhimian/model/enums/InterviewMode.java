package com.zhimian.model.enums;

public enum InterviewMode {
    DIRECTION("direction"),
    SCENARIO("scenario");

    private final String code;

    InterviewMode(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static InterviewMode fromCode(String code) {
        for (InterviewMode mode : values()) {
            if (mode.code.equals(code)) {
                return mode;
            }
        }
        return null;
    }
}
