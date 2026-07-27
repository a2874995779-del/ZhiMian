package com.zhimian.service.impl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhimian.ai.InterviewContextManager;
import com.zhimian.common.ErrorCode;
import com.zhimian.common.PageResult;
import com.zhimian.common.UserContext;
import com.zhimian.exception.BusinessException;
import com.zhimian.mapper.InterviewMessageMapper;
import com.zhimian.mapper.InterviewReportMapper;
import com.zhimian.mapper.InterviewSessionMapper;
import com.zhimian.model.dto.ChatMessageDTO;
import com.zhimian.model.dto.CreateInterviewDTO;
import com.zhimian.model.dto.InterviewReportResult;
import com.zhimian.model.dto.InterviewSessionQueryDTO;
import com.zhimian.model.entity.InterviewMessage;
import com.zhimian.model.entity.InterviewReport;
import com.zhimian.model.entity.InterviewSession;
import com.zhimian.model.enums.InterviewDirection;
import com.zhimian.model.vo.*;
import com.zhimian.service.InterviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewServiceImpl implements InterviewService {
    private static final int MAX_ACTIVE_SESSIONS = 3;
    private static final String LOCK_PREFIX = "zhimian:interview:lock:";
    private static final long LOCK_TTL_SECONDS = 60;
    private final InterviewMessageMapper  interviewMessageMapper;
    private final InterviewSessionMapper interviewSessionMapper;
    private final ChatClient chatClient;
    private final StringRedisTemplate  stringRedisTemplate;
    private final InterviewContextManager contextManager;
    private final InterviewReportMapper interviewReportMapper;
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
        contextManager.append(session.getId(),systemMsg);
        contextManager.append(session.getId(),assistantMsg);

        InterviewSessionVO vo = new InterviewSessionVO();
        vo.setId(session.getId());
        vo.setDirection(direction.getCode());
        vo.setOpeningMessage(openingMessage);
        return vo;
    }

    @Override
    public SseEmitter chat(Long sessionId, ChatMessageDTO dto) {
        //判断会话是否存在
        InterviewSession session = mustFindOwnSession(sessionId);
        if(session.getStatus() !=0){
            throw new BusinessException(ErrorCode.CONFLICT,"会话已结束，无法继续对话");
        }

        String lockKey = LOCK_PREFIX+sessionId;
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey,"1",LOCK_TTL_SECONDS,TimeUnit.SECONDS);
        if(!Boolean.TRUE.equals(locked)){
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS,"上一轮对话还在处理中，请稍后");
        }

        try {
            List<InterviewMessage> history = contextManager.load(sessionId);

            InterviewMessage userMsg = buildMessage(sessionId,"user",dto.getContent());
            interviewMessageMapper.insert(userMsg);
            contextManager.append(sessionId,userMsg);
            List<Message> springAiMessages = toSpringAiMessages(history);
            springAiMessages.add(new UserMessage(dto.getContent()));

            SseEmitter emitter = new SseEmitter(0L);
            StringBuilder fullReply = new StringBuilder();

            Disposable subscription = chatClient.prompt(new Prompt(springAiMessages))
                    .stream()
                    .content()
                    .doOnNext(fullReply::append)
                    .subscribe(
                            delta -> sendEvent(emitter, Map.of("type", "delta", "content", delta)),
                            error -> onStreamError(emitter, lockKey, error),
                            () -> onStreamComplete(emitter, sessionId, lockKey, fullReply.toString())
                    );

            emitter.onTimeout(() -> {
                log.warn("SSE超时:sessionId={}",sessionId);
                subscription.dispose();
                stringRedisTemplate.delete(lockKey);
            });
            emitter.onError(throwable -> {
                log.warn("SSE 连接异常:sessionId={}",sessionId,throwable);
                subscription.dispose();
                stringRedisTemplate.delete(lockKey);
            });

            return emitter;
        } catch (RuntimeException e) {
            // 锁已经拿到了,但订阅模型流之前的这几步(取历史、落库、消息角色转换……)只要有一步抛异常,
            // 后面负责释放锁的几条路径(onStreamComplete/onStreamError/onTimeout/onError)都不会被触发,
            // 这里必须自己把锁放掉,不然要等 60 秒 TTL 到期,这场会话在此期间没法再发消息
            stringRedisTemplate.delete(lockKey);
            throw e;
        }
    }

    @Override
    public PageResult<InterviewSessionListVO> listSessions(InterviewSessionQueryDTO dto) {
        Long userId = UserContext.getUserId();
        long total = interviewSessionMapper.countByUserId(userId);
        if(total==0){
            return PageResult.of(List.of(),0,dto.getPageNum(),dto.getPageSize());
        }
        List<InterviewSession> records = interviewSessionMapper.selectPageByUserId(userId,dto.getOffset(),dto.getPageSize());
        List<InterviewSessionListVO> voList = new ArrayList<>();
        for(InterviewSession s : records){
            InterviewSessionListVO vo = new InterviewSessionListVO();
            BeanUtils.copyProperties(s,vo);
            voList.add(vo);
        }
        return PageResult.of(voList,total,dto.getPageNum(),dto.getPageSize());
    }

    @Override
    public InterviewSessionDetailVO getSessionDetail(Long sessionId) {
        InterviewSession session = mustFindOwnSession(sessionId);
        List<InterviewMessage> history =interviewMessageMapper.selectBySessionId(sessionId);

        List<InterviewMessageVO> messageVOList = new ArrayList<>();
        for(InterviewMessage m :  history){
            if("system".equals(m.getRole())){
                continue;
            }
            InterviewMessageVO vo = new InterviewMessageVO();
            BeanUtils.copyProperties(m,vo);
            messageVOList.add(vo);
        }
        InterviewSessionDetailVO detail = new InterviewSessionDetailVO();
        BeanUtils.copyProperties(session,detail);
        detail.setMessages(messageVOList);
        return detail;
    }

    @Override
    public InterviewReportVO finishInterview(Long sessionId) {
        InterviewSession session = mustFindOwnSession(sessionId);
        // 幂等短路:报告已经生成过,直接返回已有的,不再调模型(放在抢锁之前,不用为它付一次 Redis 往返)
        if(session.getStatus() == 2){
            return toReportVO(interviewReportMapper.selectBySessionId(sessionId));
        }
        // 和 chat 用同一把会话锁:结束面试和继续对话不能同时进行,
        // 不然 finish 拼对话记录时可能刚好错过 chat 还没落库的最后一条回复
        String lockKey = LOCK_PREFIX + sessionId;
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey,"1",LOCK_TTL_SECONDS,TimeUnit.SECONDS);
        if(!Boolean.TRUE.equals(locked)){
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS,"会话正在处理中，请稍后再试");
        }
        try {
            // 先把"已结束"落实(0→1),再调模型——模型调用失败状态也停在 1,不会出现"点了结束还能聊"
            if(session.getStatus() == 0){
                interviewSessionMapper.updateStatus(sessionId,1);
            }
            List<InterviewMessage> history = interviewMessageMapper.selectBySessionId(sessionId);
            InterviewReportResult result = generateReportWithRetry(buildTranscript(history));

            InterviewReport report = new InterviewReport();
            report.setSessionId(sessionId);
            report.setScore(result.score());
            report.setContent(serializeReportContent(result));
            interviewReportMapper.insert(report);
            interviewSessionMapper.updateStatus(sessionId,2);

            return toReportVO(report);
        }finally {
            stringRedisTemplate.delete(lockKey);
        }


    }

    private InterviewReportResult generateReportWithRetry(String transcript) {
        for(int attempt = 1;attempt <=2;attempt++){
            try{
                return chatClient.prompt()
                        .system(buildJudgePrompt())
                        .user(transcript)
                        .call()
                        .entity(InterviewReportResult.class);
            }catch (Exception e){
                log.warn("评价报告生成第{}次尝试失败",attempt,e);
            }
        }
        throw new BusinessException(ErrorCode.AI_SERVICE_ERROR,"评价报告生成失败，请稍后再试");
    }

    private String buildJudgePrompt() {
        return """
            你是一位资深技术面试评委,下面会给你一场完整的模拟面试问答记录(面试官提问与候选人回答交替出现)。
            请基于候选人的整体表现给出结构化评价:
            1. score:总分,0~100 的整数,综合回答的准确性、深度、表达清晰度。
            2. highlights:候选人表现好的地方,2~4 条,每条一句话,要具体(引用候选人实际提到的技术点),不说空泛的场面话。
            3. weaknesses:回答中不足或值得继续深挖的地方,2~4 条,同样要具体。
            4. summary:1~2 句话的总体评价,像面试官写给 HR 的结论。
            只依据对话记录里实际出现的内容打分和点评,不要编造候选人没有说过的内容。
            """;
    }

    private String buildTranscript(List<InterviewMessage> history) {
        StringBuilder sb = new StringBuilder();
        for(InterviewMessage m : history){
            if("system".equals(m.getRole())){
                continue;
            }
            String speaker = "assistant".equals(m.getRole()) ? "面试官" : "候选人";
            sb.append(speaker).append(":").append(m.getContent()).append("\n\n");
        }
        return sb.toString();
    }

    // highlights/weaknesses/summary 打包成一段 JSON 存进 content 列(score 已经单独成列,不放进 JSON)
    private String serializeReportContent(InterviewReportResult result) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "highlights", result.highlights(),
                    "weaknesses", result.weaknesses(),
                    "summary", result.summary()
            ));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private InterviewReportVO toReportVO(InterviewReport report){
        InterviewReportVO vo = new InterviewReportVO();
        vo.setScore(report.getScore());
        vo.setCreateTime(report.getCreateTime());
        try {
            Map<String, Object> content = objectMapper.readValue(report.getContent(), new TypeReference<>() {});
            vo.setHighlights((List<String>) content.get("highlights"));
            vo.setWeaknesses((List<String>) content.get("weaknesses"));
            vo.setSummary((String) content.get("summary"));
        }catch (JsonProcessingException e){
            throw new RuntimeException(e);
        }
        return vo;
    }
    private void onStreamError(SseEmitter emitter, String lockKey, Throwable error) {
        log.error("AI 流失调用失败",error);
        sendEvent(emitter,Map.of("type","error","message","AI服务异常，请稍后重试"));
        emitter.completeWithError(error);
        stringRedisTemplate.delete(lockKey);
    }

    private void sendEvent(SseEmitter emitter, Map<String, Object> payload) {
        try {
            emitter.send(SseEmitter.event().data(payload, MediaType.APPLICATION_JSON));
        }catch(IOException e){
            // 客户端大概率已经断开连接,这条消息送不到了。发送失败本身不需要再抛出去处理,
            // 真正的清理(释放锁、停止订阅)交给上面注册的 onError/onTimeout 回调
            log.warn("SSE 事件发送失败，客户端可能已断开");
        }
    }

    private void onStreamComplete(SseEmitter emitter, Long sessionId, String lockKey, String fullReply) {
        try {
            InterviewMessage assistantMsg = buildMessage(sessionId,"assistant",fullReply);
            interviewMessageMapper.insert(assistantMsg);
            contextManager.append(sessionId,assistantMsg);
            sendEvent(emitter,Map.of("type","done","messageId",assistantMsg.getId()));
            emitter.complete();
        }finally {
            stringRedisTemplate.delete(lockKey);
        }
    }

    private InterviewSession mustFindOwnSession(Long sessionId) {
        InterviewSession session = interviewSessionMapper.selectById(sessionId);
        if(session == null || !session.getUserId().equals(UserContext.getUserId())){
            throw new BusinessException(ErrorCode.NOT_FOUND,"面试会话不存在");
        }
        return session;
    }

    private List<Message> toSpringAiMessages(List<InterviewMessage> history) {
        List<Message> messages = new ArrayList<>();
        for(InterviewMessage m : history){
            switch (m.getRole()){
                case "system" -> messages.add(new SystemMessage(m.getContent()));
                case "user" -> messages.add(new UserMessage(m.getContent()));
                case "assistant" -> messages.add(new AssistantMessage(m.getContent()));
                default -> throw new IllegalStateException("未知的消息角色:"+m.getRole());
            }
        }
        return messages;
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
