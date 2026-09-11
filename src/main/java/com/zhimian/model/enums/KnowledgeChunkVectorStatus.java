package com.zhimian.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum KnowledgeChunkVectorStatus {
    PENDING(0),
    INDEXED(1),
    FAILED(2);

    private final int code;
}
