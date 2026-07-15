package com.zhimian.service.impl;

import com.zhimian.common.ErrorCode;
import com.zhimian.exception.BusinessException;
import com.zhimian.mapper.TagMapper;
import com.zhimian.model.dto.TagAddDTO;
import com.zhimian.model.entity.Tag;
import com.zhimian.model.vo.TagVO;
import com.zhimian.service.TagService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TagServiceImpl implements TagService {

    private final TagMapper tagMapper;

    @Override
    public List<TagVO> listAll() {
        List<TagVO> result = new ArrayList<>();
        for(Tag tag : tagMapper.selectAll()){
            TagVO vo = new TagVO();
            BeanUtils.copyProperties(tag,vo);
            result.add(vo);
        }
        return result;
    }

    @Override
    public Long addTag(TagAddDTO dto) {
        Tag tag = new Tag();
        tag.setName(dto.getName());
        try {
            tagMapper.insert(tag);
        }catch (DuplicateKeyException e){
            throw new BusinessException(ErrorCode.CONFLICT,"标签已存在");
        }
        return tag.getId();
    }
}
