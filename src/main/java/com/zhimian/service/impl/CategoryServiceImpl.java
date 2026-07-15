package com.zhimian.service.impl;

import com.zhimian.common.ErrorCode;
import com.zhimian.exception.BusinessException;
import com.zhimian.mapper.CategoryMapper;
import com.zhimian.model.dto.CategoryAddDTO;
import com.zhimian.model.entity.Category;
import com.zhimian.model.vo.CategoryVO;
import com.zhimian.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {
    private final CategoryMapper  categoryMapper;

    @Override
    public List<CategoryVO> getTree() {
        List<Category> all = categoryMapper.selectAll();
        // 按 parentId 分组,一次遍历 O(n) 把"父 id -> 子分类列表"建好
        // 如果对每个顶级分类都单独发一条"查它的子分类"的 SQL,就是 N+1 查询;
        // 或者用双重循环给每个顶级分类去 all 里找子分类,是 O(n²)——数据量小(几十条)看不出差别,
        // 但要知道这是错误的写法,数据量一大就会退化得很明显
        Map<Long,List<Category>> childrenMap = new HashMap<>();
        List<Category> topLevel = new ArrayList<>();
        for(Category c : all){
            if(c.getParentId() == 0){
                topLevel.add(c);
            }else {
                childrenMap.computeIfAbsent(c.getParentId(),k->new ArrayList<>()).add(c);
            }
        }
        List<CategoryVO> tree = new ArrayList<>();
        for(Category top : topLevel){
            CategoryVO vo = new CategoryVO();
            BeanUtils.copyProperties(top,vo);

            List<CategoryVO> childVOs = new ArrayList<>();
            for(Category child : childrenMap.getOrDefault(top.getId(),List.of())){
                CategoryVO childVO = new CategoryVO();
                BeanUtils.copyProperties(child,childVO);
                childVOs.add(childVO);
            }
            vo.setChildren(childVOs);
            tree.add(vo);
        }
        return tree;
    }

    @Override
    public Long addCategory(CategoryAddDTO dto) {
        Category category = new Category();
        category.setName(dto.getName());
        category.setParentId(dto.getParentId());
        try {
            categoryMapper.insert(category);
        }catch (DuplicateKeyException e ){
            throw new BusinessException(ErrorCode.CONFLICT,"分类名已存在");
        }
        return category.getId();
    }
}
