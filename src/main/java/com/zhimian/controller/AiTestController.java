package com.zhimian.controller;

import com.zhimian.annotation.RequireAdmin;
import com.zhimian.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AiTestController {

    private final ChatClient chatClient;

    @RequireAdmin
    @GetMapping("/api/ai/ping")
    public Result<String> ping(@RequestParam String message){
        String reply = chatClient.prompt()
                .user(message)
                .call()
                .content();
        return Result.success(reply);
    }
}
