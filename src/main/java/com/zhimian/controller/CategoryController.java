package com.zhimian.controller;

import com.zhimian.annotation.Public;
import com.zhimian.annotation.RequireAdmin;
import com.zhimian.common.Result;
import com.zhimian.model.dto.CategoryAddDTO;
import com.zhimian.model.vo.CategoryVO;
import com.zhimian.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {
    private final CategoryService categoryService;


    @Public
    @GetMapping
    public Result<List<CategoryVO>> tree() {
        return Result.success(categoryService.getTree());
    }

    @RequireAdmin
    @PostMapping
    public Result<Long> add(@Valid @RequestBody CategoryAddDTO  dto) {
        return Result.success(categoryService.addCategory(dto));
    }
}
