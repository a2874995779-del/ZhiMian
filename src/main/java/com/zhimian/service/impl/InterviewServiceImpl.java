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
import com.zhimian.ratelimit.RateLimiter;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import java.util.concurrent.Executor;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewServiceImpl implements InterviewService {
    private static final int MAX_ACTIVE_SESSIONS = 3;
    private static final String LOCK_PREFIX = "zhimian:interview:lock:";
    private static final long LOCK_TTL_SECONDS = 300;
    private static final String RATE_PREFIX = "zhimian:interview:rate:";
    private static final long RATE_WINDOW_SECONDS = 10;
    private final RateLimiter rateLimiter;
    private final InterviewMessageMapper  interviewMessageMapper;
    private final InterviewSessionMapper interviewSessionMapper;
    private final ChatClient chatClient;
    private final StringRedisTemplate  stringRedisTemplate;
    private final InterviewContextManager contextManager;
    private final InterviewReportMapper interviewReportMapper;
    private final ObjectMapper objectMapper;
    @Qualifier("reportExecutor")
    private final Executor reportExecutor;

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
        String openingMessage = buildFastOpeningMessage(direction);
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

    private String buildFastOpeningMessage(InterviewDirection direction) {
        return "你好，我是本场 " + direction.getLabel()
                + " 方向的面试官。我们直接开始，先请你用自己的话回答: "
                + firstQuestion(direction);
    }

    private String firstQuestion(InterviewDirection direction) {
        return switch (direction.getCode()) {
            case "java_concurrency" -> "线程池的核心参数有哪些？为什么不推荐直接使用 Executors 创建线程池？";
            case "jvm" -> "你能从运行时数据区开始，整体讲一下 JVM 的内存模型吗？";
            case "mysql" -> "MySQL 的 B+ 树索引为什么适合范围查询？";
            case "redis" -> "Redis 常见的数据结构有哪些？你在项目里会怎么选择？";
            case "system_design" -> "如果让你设计一个高并发排行榜服务，你会怎么设计读写链路？";
            default -> "请介绍一个你最熟悉的技术点，并说明它在项目中的使用场景。";
        };
    }

    @Override
    public SseEmitter chat(Long sessionId, ChatMessageDTO dto) {
        //判断会话是否存在
        InterviewSession session = mustFindOwnSession(sessionId);
        if(session.getStatus() !=0){
            throw new BusinessException(ErrorCode.CONFLICT,"会话已结束，无法继续对话");
        }
        // —— 本次任务新增:每用户对话频率限制 ——
        // userId 在这里(还是处理 HTTP 请求的原始线程)取,ThreadLocal 里还有值;
        // 这一步只是同步的入口拦截,不涉及后面的异步回调,不存在跨线程读 ThreadLocal 的问题

        Long userId = UserContext.getUserId();
        if(!rateLimiter.tryAcquire(RATE_PREFIX+userId,RATE_WINDOW_SECONDS)){
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS,"操作太频繁请稍后再试");
        }
        String lockKey = LOCK_PREFIX+sessionId;
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey,"1",LOCK_TTL_SECONDS,TimeUnit.SECONDS);
        if(!Boolean.TRUE.equals(locked)){
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS,"上一轮对话还在处理中，请稍后");
        }

        try {
            List<InterviewMessage> history = contextManager.load(sessionId);
            List<Message> springAiMessages = toSpringAiMessages(history);
            springAiMessages.add(new UserMessage(dto.getContent()));

            // 有限超时:模型流卡死(既不吐字也不报错)时,到点触发 onTimeout 自救(dispose+放锁),
            // 而不是干等到锁 300s TTL 过期后被别的请求开出第二条流
            SseEmitter emitter = new SseEmitter(TimeUnit.MINUTES.toMillis(5));
            StringBuilder fullReply = new StringBuilder();
            String userContent = dto.getContent();

            Disposable subscription = chatClient.prompt(new Prompt(springAiMessages))
                    .stream()
                    .content()
                    .doOnNext(fullReply::append)
                    .subscribe(
                            delta -> sendEvent(emitter, Map.of("type", "delta", "content", delta)),
                            // fullReply.toString() 在 lambda 里求值:失败时拿到的是"到目前吐出的半截",成功时是全文
                            error -> onStreamError(emitter, sessionId, lockKey, userContent, fullReply.toString(), error),
                            () -> onStreamComplete(emitter, sessionId, lockKey, userContent, fullReply.toString())
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
            // 这里必须自己把锁放掉,不然要等 300 秒 TTL 到期,这场会话在此期间没法再发消息
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
    public InterviewReportStatusVO finishInterview(Long sessionId) {
        InterviewSession session = mustFindOwnSession(sessionId);

        InterviewReport existing = interviewReportMapper.selectBySessionId(sessionId);
        if (existing != null && Integer.valueOf(1).equals(existing.getStatus())) {
            interviewSessionMapper.markReportReady(sessionId);
            return buildReportStatus(existing);
        }

        String lockKey = LOCK_PREFIX + sessionId;
        Boolean locked = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", LOCK_TTL_SECONDS, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(locked)) {
            return buildGeneratingStatus("报告正在生成中，请稍候");
        }

        try {
            existing = interviewReportMapper.selectBySessionId(sessionId);
            if (existing != null && Integer.valueOf(1).equals(existing.getStatus())) {
                interviewSessionMapper.markReportReady(sessionId);
                return buildReportStatus(existing);
            }
            if (session.getStatus() == 0) {
                interviewSessionMapper.endSession(sessionId);
            }
            interviewReportMapper.insertGenerating(sessionId);
            CompletableFuture.runAsync(() -> generateReportJob(sessionId, lockKey), reportExecutor);
            return buildGeneratingStatus("报告正在生成中，请稍候");
        } catch (RuntimeException e) {
            stringRedisTemplate.delete(lockKey);
            throw e;
        }
    }

    private void generateReportJob(Long sessionId, String lockKey) {
        try {
            InterviewReport report = interviewReportMapper.selectBySessionId(sessionId);
            if(report != null && Integer.valueOf(1).equals(report.getStatus())){
                interviewSessionMapper.markReportReady(sessionId);
                return;
            }
            List<InterviewMessage> history = interviewMessageMapper.selectBySessionId(sessionId);
            InterviewReportResult result = generateReportWithRetry(buildTranscript(history));
            interviewReportMapper.markSuccess(
                    sessionId,
                    result.score(),
                    serializeReportContent(result)
            );
            interviewSessionMapper.markReportReady(sessionId);
        }catch (Exception e){
            log.error("异步生成评价报告失败:sessionId={}",sessionId,e);
            interviewReportMapper.markFailed(sessionId,"评价报告生成失败,请稍后再试");
        }finally {
            stringRedisTemplate.delete(lockKey);
        }
    }

    private InterviewReportStatusVO buildGeneratingStatus(String message) {
        InterviewReportStatusVO vo = new InterviewReportStatusVO();
        vo.setStatus(0);
        vo.setMessage(message);
        return vo;
    }

    private InterviewReportStatusVO buildReportStatus(InterviewReport report) {
        InterviewReportStatusVO vo = new InterviewReportStatusVO();
        vo.setStatus(report.getStatus());
        if(Integer.valueOf(1).equals(report.getStatus())){
            vo.setReport(toReportVO(report));
            vo.setMessage("报告已生成");
        }else if(Integer.valueOf(2).equals(report.getStatus())){
            vo.setMessage(report.getErrorMessage() == null? "报告生成失败,请重试" : report.getErrorMessage());
        }else {
            vo.setMessage("报告正在生成中，请稍后");
        }
        return vo;
    }

    @Override
    public InterviewReportStatusVO getReportStatus(Long sessionId) {
        mustFindOwnSession(sessionId);
        InterviewReport report = interviewReportMapper.selectBySessionId(sessionId);
        if(report == null){
            return buildGeneratingStatus("报告尚未开始生成");
        }
        return buildReportStatus(report);
    }

    private InterviewReportResult generateReportWithRetry(String transcript) {
        for(int attempt = 1;attempt <=2;attempt++){
            try{
                InterviewReportResult result = chatClient
                        .prompt()
                        .system(buildJudgePrompt())
                        .user(transcript)
                        .call()
                        .entity(InterviewReportResult.class);
                // 模型可能返回缺字段的 JSON:score 为 null 撞库里 NOT NULL,
                // highlights/weaknesses/summary 为 null 会让 serializeReportContent 的 Map.of 抛 NPE。
                // 四个字段任一为空都当成"这次没解析出合法报告",抛出去触发重试,而不是等落库才 500。
                if(result == null
                        || result.score() == null
                        || result.highlights() == null
                        || result.weaknesses() == null
                        || result.summary() == null
                ){
                    throw new IllegalStateException("模型返回的报告字段不完整");
                }
                return result;
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
        if(report == null || report.getContent() == null){
            throw new BusinessException(ErrorCode.CONFLICT,"报告还未生成成功");
        }
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
    private void onStreamError(SseEmitter emitter, Long sessionId, String lockKey,
                               String userContent, String fullReply, Throwable error) {
        log.error("AI 流式调用失败", error);
        try {
            // 中途失败但已吐了半截:前端会保留这半截气泡,后端也要把「用户这轮 + 半截回复」成对存下来,
            // 保持"前端看到的 = 后端记得的";一个字都没吐(fullReply 空)就什么都不写,前端也会删空气泡、标失败,两边一致。
            if (!fullReply.isEmpty()) {
                persistTurn(sessionId, userContent, fullReply);
            }
        } catch (Exception e) {
            log.error("流式失败后落库也失败:sessionId={}", sessionId, e);
        } finally {
            sendEvent(emitter, Map.of("type", "error", "message", "AI服务异常，请稍后重试"));
            emitter.completeWithError(error);
            stringRedisTemplate.delete(lockKey);
        }
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

    private void onStreamComplete(SseEmitter emitter, Long sessionId, String lockKey, String userContent, String fullReply) {
        try {
            InterviewMessage assistantMsg = persistTurn(sessionId, userContent, fullReply);
            sendEvent(emitter, Map.of("type", "done", "messageId", assistantMsg.getId()));
            emitter.complete();
        } catch (Exception e) {
            // 落库失败也要把流关掉并告诉前端,否则 emitter 既不 complete 也不发 error,前端 reader 永远挂着
            log.error("流式完成后落库失败:sessionId={}", sessionId, e);
            sendEvent(emitter, Map.of("type", "error", "message", "回复保存失败，请重试"));
            emitter.completeWithError(e);
        } finally {
            stringRedisTemplate.delete(lockKey);
        }
    }

    // 把「用户提问 + AI 回复」成对落库 + 进上下文。user 先 insert、assistant 后 insert,id 自增保证历史顺序。
    private InterviewMessage persistTurn(Long sessionId, String userContent, String reply) {
        InterviewMessage userMsg = buildMessage(sessionId, "user", userContent);
        interviewMessageMapper.insert(userMsg);
        contextManager.append(sessionId, userMsg);

        InterviewMessage assistantMsg = buildMessage(sessionId, "assistant", reply);
        interviewMessageMapper.insert(assistantMsg);
        contextManager.append(sessionId, assistantMsg);
        return assistantMsg;
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

                这场面试需要考察以下几个维度:%s。

                面试策略(重要):
                1. 每次只问一个问题,不要一次性抛出多个问题。
                2. 以广度为主、深度为辅:目标是在有限轮次里尽量覆盖上面列出的多个维度,系统地考察候选人的知识面,而不是抓着某一个点一直往深里追问。
                3. 控制追问:对同一个知识点最多追问一次。无论这一点答得好不好,追问一次之后就要主动切换到一个还没考察过的维度,不要顺着候选人上一句话无限深挖。
                4. 根据回答质量灵活调整:答得好,简短肯定一句就切换到新维度;答得含糊或答不上来,可以给一点提示或换个角度再问一次,仍答不上就换维度,别在一个点上僵持。
                5. 心里记着已经问过哪些维度,有意识地让问题覆盖面铺开;等上面这些维度大多覆盖到了,再挑候选人答得最好或最薄弱的点做适度深入。
                6. 语气专业、简洁,像真实面试官一样自然对话,不要用"好的,我们开始吧"这类机械化开场白,也不要每句话都用 Markdown 列表排版。
                """.formatted(direction.getLabel(), direction.getFocus());
    }
}
