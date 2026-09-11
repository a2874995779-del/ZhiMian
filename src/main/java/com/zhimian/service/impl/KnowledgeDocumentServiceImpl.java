package com.zhimian.service.impl;

import com.zhimian.common.ErrorCode;
import com.zhimian.exception.BusinessException;
import com.zhimian.mapper.KnowledgeDocumentMapper;
import com.zhimian.model.dto.KnowledgeDocumentCreateDTO;
import com.zhimian.model.entity.KnowledgeDocument;
import com.zhimian.model.enums.KnowledgeDocumentStatus;
import com.zhimian.service.KnowledgeDocumentService;
import com.zhimian.util.KnowledgeContentHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class KnowledgeDocumentServiceImpl implements KnowledgeDocumentService {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 500;
    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeContentHasher contentHasher;
    @Override
    public Long createPending(KnowledgeDocumentCreateDTO dto) {
        validateCreateDTO(dto);
        String normalizedContent = contentHasher.normalize(dto.getContent());
        String contentHash = contentHasher.sha256(normalizedContent);

        KnowledgeDocument existing = documentMapper.selectActiveByContentHash(contentHash);
        if(existing !=null){
            throw new BusinessException(ErrorCode.CONFLICT,"相同内容的知识文档已经存在了");
        }
        KnowledgeDocument document = new KnowledgeDocument();
        document.setTitle(dto.getTitle().strip());
        document.setOriginalFilename(dto.getOriginalFilename().strip());
        document.setSourceType(dto.getSourceType().strip().toUpperCase(Locale.ROOT));
        document.setContent(normalizedContent);
        document.setContentHash(contentHash);
        document.setStatus(KnowledgeDocumentStatus.PENDING.getCode());
        document.setChunkCount(0);
        document.setCreatedBy(dto.getCreatedBy());

        try {
            if(documentMapper.insert(document) != 1 || document.getId() == null){
                throw new IllegalStateException("知识文档创建失败");
            }
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.CONFLICT,"相同内容的知识文档已经存在了");
        }
        return document.getId();
    }

    @Override
    public KnowledgeDocument getById(Long id) {
        KnowledgeDocument document = documentMapper.selectById(id);
        if(document == null){
            throw new BusinessException(ErrorCode.NOT_FOUND,"知识文档不存在");
        }
        return document;
    }

    @Override
    public void startProcessing(Long id, String embeddingModel, int vectorDimension) {
        getById(id);
        if(!StringUtils.hasText(embeddingModel) || vectorDimension <= 0){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"Embedding模型和向量维度不能为空");
        }
        if(documentMapper.markProcessing(id,embeddingModel.strip(),vectorDimension) == 0){
            throw stateConflict();
        }
    }

    @Override
    public void complete(Long id, int chunkCount) {
        if(chunkCount <= 0){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"完成处理时chunk数量必须大于0");
        }
        if(documentMapper.markCompleted(id,chunkCount) == 0){
            throw stateConflict();
        }
    }

    @Override
    public void fail(Long id, String errorMessage) {
        String safeMessage = truncateErrorMessage(errorMessage);
        if(documentMapper.markFailed(id,safeMessage) == 0 ){
            throw stateConflict();
        }
    }


    @Override
    public void beginDelete(Long id) {
        getById(id);
        if(documentMapper.markDeleting(id) == 0){
            throw stateConflict();
        }
    }


    private void validateCreateDTO(KnowledgeDocumentCreateDTO dto) {
        if(dto == null
               || !StringUtils.hasText(dto.getTitle())
               || !StringUtils.hasText(dto.getOriginalFilename())
               || !StringUtils.hasText(dto.getSourceType())
                || !StringUtils.hasText(dto.getContent())
                || dto.getCreatedBy() == null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"知识文档参数不完整");
        }
        String sourceType = dto.getSourceType().strip().toUpperCase(Locale.ROOT);
        if(!sourceType.equals("MARKDOWN") && !sourceType.equals("TEXT")){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"当前只支持MARKDOWN和TEXT文档");
        }
    }

    private String truncateErrorMessage(String errorMessage) {
        if(!StringUtils.hasText(errorMessage)){
            return "未知处理异常";
        }
        String stripped = errorMessage.strip();
        return stripped.length() <= MAX_ERROR_MESSAGE_LENGTH
                ? stripped
                : stripped.substring(0,MAX_ERROR_MESSAGE_LENGTH);
    }

    private BusinessException stateConflict() {
        return new BusinessException(ErrorCode.CONFLICT,"知识文档状态已变化，请刷新后重试");
    }
}
