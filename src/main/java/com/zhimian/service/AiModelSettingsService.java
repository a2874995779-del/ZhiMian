package com.zhimian.service;

import com.zhimian.model.dto.AiModelSettingsUpdateDTO;
import com.zhimian.model.vo.AiModelSettingsVO;

public interface AiModelSettingsService {
    AiModelSettingsVO get(Long userId);

    AiModelSettingsVO save(Long userId, AiModelSettingsUpdateDTO dto);

    void test(Long userId);

    void clear(Long userId);
}
