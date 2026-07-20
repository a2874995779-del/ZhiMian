package com.zhimian.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhimian.common.ErrorCode;
import com.zhimian.common.UserContext;
import com.zhimian.exception.BusinessException;
import com.zhimian.mapper.InterviewMessageMapper;
import com.zhimian.mapper.InterviewSessionMapper;
import com.zhimian.model.dto.CreateInterviewDTO;
import com.zhimian.model.entity.InterviewMessage;
import com.zhimian.model.entity.InterviewSession;
import com.zhimian.model.enums.InterviewDirection;
import com.zhimian.model.vo.InterviewSessionVO;
import com.zhimian.service.InterviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class InterviewServiceImpl implements InterviewService {
    private static final int MAX_ACTIVE_SESSIONS = 3;
    private static final String CTX_KEY_PREFIX = "zhimian:interview:ctx:";
    private static final long CTX_TTL_HOURS = 2;

    private final InterviewMessageMapper  interviewMessageMapper;
    private final InterviewSessionMapper interviewSessionMapper;
    private final ChatClient chatClient;
    private final StringRedisTemplate  stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public InterviewSessionVO createInterview(CreateInterviewDTO dto) {
        InterviewDirection direction = InterviewDirection.fromCode(dto.getDirection());
        if(direction == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"暂不支持该面试方向");
        }
        Long userId = UserContext.getUserId();
        long activeCount = interviewSessionMapper.countInProgress(userId);
        if(activeCount >=MAX_ACTIVE_SESSIONS){
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS,"面试中的会话达到上限，若想继续，请关闭先前对话");
        }

        InterviewSession session = new InterviewSession();
        session.setUserId(userId);
        session.setDirection(direction.getCode());
        session.setTitle(direction.getLabel() + " . "+ LocalDate.now());
        session.setStatus(0);
        interviewSessionMapper.insert(session);
        // 到这里,插库已经完成。下面调用 AI 是一次不可控耗时的外部请求,
        // 整个方法没有加 @Transactional,就是不想让数据库连接陪着这次外部调用一起等

        String systemPrompt = buildSystemPrompt(direction);
        String openingMessage;
        try {
            openingMessage = chatClient.prompt()
                    .system(systemPrompt)
                    .user("请开始这场模拟面试：先用一两句话做简短的自我介绍，然后直接提出第一个问题")
                    .call()
                    .content();
        }catch (Exception e){
            // session 已经建好了,这里选择不做任何补偿(不删 session、不标记失败状态)。
            // 这次任务的验收范围到这里就够了:知道"为什么不能在事务里调 AI"是重点,
            // "开场白生成失败要不要提供重试接口"留给后面任务或者你自己课后思考
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR,"开场白生成失败，请稍后再试");
        }

        InterviewMessage systemMsg = buildMessage(session.getId(),"system",systemPrompt);
        InterviewMessage assistantMsg = buildMessage(session.getId(),"assistant",openingMessage);
        interviewMessageMapper.insert(systemMsg);
        interviewMessageMapper.insert(assistantMsg);
        appendToContext(session.getId(),systemMsg);
        appendToContext(session.getId(),assistantMsg);

        InterviewSessionVO vo = new InterviewSessionVO();
        vo.setId(session.getId());
        vo.setDirection(direction.getCode());
        vo.setOpeningMessage(openingMessage);
        return vo;
    }

    private void appendToContext(Long sessionId, InterviewMessage message) {
        String key = CTX_KEY_PREFIX +sessionId;
        stringRedisTemplate.opsForList().rightPush(key,serialize(message));
        stringRedisTemplate.expire(key, CTX_TTL_HOURS, TimeUnit.HOURS);
    }

    private String serialize(InterviewMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        }catch (JsonProcessingException e){
            throw new RuntimeException(e);
        }
    }

    private InterviewMessage buildMessage(Long sessionId, String role, String content) {
        InterviewMessage message = new InterviewMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        return message;
    }

    private String buildSystemPrompt(InterviewDirection direction) {
        return """
                你是一位经验丰富的 Java 后端资深面试官,正在对候选人进行一场专注于「%s」方向的技术面试。
                                面试风格要求:
                                1. 每次只问一个问题,不要一次性抛出多个问题。
                                2. 根据候选人上一轮回答的质量动态调整下一个问题的深度——回答得好就继续追问细节,回答得含糊就换个角度重新问或给出提示。
                                3. 语气专业、简洁,像真实面试官一样自然对话,不要用"好的,我们开始吧"这类机械化开场白,也不要每句话都用 Markdown 列表排版。
                                4. 这场面试重点考察方向:%s。
                """.formatted(direction.getLabel(),direction.getFocus());
    }
}
