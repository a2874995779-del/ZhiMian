package com.zhimian.mapper;

import com.zhimian.model.entity.Tag;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TagMapper {
    void insert(Tag tag);

    Tag[] selectAll();
}
