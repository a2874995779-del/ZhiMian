package com.zhimian.controller;

import com.zhimian.annotation.Public;
import com.zhimian.annotation.RequireAdmin;
import com.zhimian.common.Result;
import com.zhimian.model.dto.TagAddDTO;
import com.zhimian.model.vo.TagVO;
import com.zhimian.service.TagService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagController {
    private final TagService tagService;

    @Public
    @GetMapping
    public Result<List<TagVO>> list(){
        return Result.success(tagService.listAll());
    }

    @RequireAdmin
    @PostMapping
    public Result<Long> add(@Valid @RequestBody TagAddDTO dto){
        return Result.success(tagService.addTag(dto));
    }
}
