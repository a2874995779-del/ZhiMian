package com.zhimian.controller;

import com.zhimian.annotation.Public;
import com.zhimian.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {
    @Public
    @GetMapping
    public Result<String> health() {
        return Result.success("UP");
    }
}
