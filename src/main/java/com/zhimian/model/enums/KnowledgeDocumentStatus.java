package com.zhimian.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum KnowledgeDocumentStatus {

    PENDING(0),
    PROCESSING(1),
    COMPLETED(2),
    FAILED(3),
    DELETING(4);
    private final int code;

}
