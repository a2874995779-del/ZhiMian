package com.zhimian.service;

import com.zhimian.model.dto.CategoryAddDTO;
import com.zhimian.model.vo.CategoryVO;
import jakarta.validation.Valid;

import java.util.List;

public interface CategoryService {
    List<CategoryVO> getTree();

    Long addCategory(@Valid CategoryAddDTO dto);
}
