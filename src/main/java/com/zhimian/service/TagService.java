package com.zhimian.service;

import com.zhimian.model.dto.TagAddDTO;
import com.zhimian.model.vo.TagVO;
import jakarta.validation.Valid;

import java.util.List;

public interface TagService {
    List<TagVO> listAll();

    Long addTag(@Valid TagAddDTO dto);
}
