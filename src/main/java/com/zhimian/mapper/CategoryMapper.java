package com.zhimian.mapper;

import com.zhimian.model.entity.Category;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CategoryMapper {
    void insert(Category category);

    List<Category> selectAll();
}
