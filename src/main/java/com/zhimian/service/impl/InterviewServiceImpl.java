package com.zhimian.service.impl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhimian.ai.AiQuestionCollectDispatcher;
import com.zhimian.ai.InterviewContextManager;
import com.zhimian.common.ErrorCode;
import com.zhimian.common.PageResult;
import com.zhimian.common.UserContext;
import com.zhimian.exception.BusinessException;
import com.zhimian.mapper.InterviewMessageMapper;
import com.zhimian.mapper.InterviewReportMapper;
import com.zhimian.mapper.InterviewSessionMapper;
import com.zhimian.mapper.InterviewTurnMapper;
import com.zhimian.model.dto.InterviewAnswerEvaluation;
import com.zhimian.model.dto.ChatMessageDTO;
import com.zhimian.model.dto.CreateInterviewDTO;
import com.zhimian.model.dto.InterviewReportResult;
import com.zhimian.model.dto.InterviewSessionQueryDTO;
import com.zhimian.model.entity.InterviewMessage;
import com.zhimian.model.entity.InterviewReport;
import com.zhimian.model.entity.InterviewSession;
import com.zhimian.model.entity.InterviewTurn;
import com.zhimian.model.enums.InterviewMode;
import com.zhimian.model.interview.InterviewPlan;
import com.zhimian.model.interview.InterviewPlanItem;
import com.zhimian.model.vo.*;
import com.zhimian.rag.interview.InterviewAnswerEvaluationService;
import com.zhimian.rag.interview.InterviewEvaluationResult;
import com.zhimian.ratelimit.RateLimiter;
import com.zhimian.redis.RedisLockManager;
import com.zhimian.service.InterviewPersistenceService;
import com.zhimian.service.InterviewExpirationService;
import com.zhimian.service.InterviewPlanService;
import com.zhimian.service.InterviewService;
import com.zhimian.service.InterviewTurnService;
import com.zhimian.util.AiQuestionExtractor;
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
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import java.util.concurrent.Executor;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewServiceImpl implements InterviewService {
    private static final int MAX_ACTIVE_SESSIONS = 3;
    private static final int DEFAULT_TARGET_QUESTION_COUNT = 8;
    private static final String CREATE_LOCK_PREFIX = "zhimian:interview:create-lock:";
    private static final String CHAT_LOCK_PREFIX = "zhimian:interview:chat-lock:";
    private static final String REPORT_LOCK_PREFIX = "zhimian:interview:report-lock:";
    private static final Duration CREATE_LOCK_TTL = Duration.ofSeconds(30);
    private static final Duration CHAT_LOCK_TTL = Duration.ofMinutes(15);
    private static final Duration REPORT_LOCK_TTL = Duration.ofMinutes(20);
    private static final String RATE_PREFIX = "zhimian:interview:rate:";
    private static final long RATE_WINDOW_SECONDS = 10;
    private final RateLimiter rateLimiter;
    private final InterviewMessageMapper  interviewMessageMapper;
    private final InterviewSessionMapper interviewSessionMapper;
    private final ChatClient chatClient;
    private final InterviewContextManager contextManager;
    private final InterviewReportMapper interviewReportMapper;
    private final InterviewTurnMapper interviewTurnMapper;
    private final InterviewTurnService interviewTurnService;
    private final ObjectMapper objectMapper;
    private final AiQuestionExtractor aiQuestionExtractor;
    private final AiQuestionCollectDispatcher questionCollectDispatcher;
    private final RedisLockManager redisLockManager;
    private final InterviewPersistenceService persistenceService;
    private final InterviewPlanService interviewPlanService;
    private final InterviewExpirationService interviewExpirationService;
    private final InterviewAnswerEvaluationService answerEvaluationService;
    @Qualifier("reportExecutor")
    private final Executor reportExecutor;

    @Override
    public InterviewSessionVO createInterview(CreateInterviewDTO dto) {
        InterviewPlan plan = interviewPlanService.build(dto);
        Long userId = UserContext.getUserId();
        String createLockKey = CREATE_LOCK_PREFIX + userId;
        String createLockToken = redisLockManager.tryLock(createLockKey, CREATE_LOCK_TTL);
        if (createLockToken == null) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "正在创建面试，请勿重复提交");
        }
        try {
            interviewExpirationService.expireInactiveForUser(userId);
            long activeCount = interviewSessionMapper.countInProgress(userId);
            if(activeCount >=MAX_ACTIVE_SESSIONS){
                throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS,"面试中的会话达到上限， 若想继续，请关闭先前对话");
            }

            InterviewSession session = new InterviewSession();
            session.setUserId(userId);
            session.setDirection(plan.mode().equals(InterviewMode.DIRECTION.getCode())
                    ? plan.code() : "scenario");
            session.setMode(plan.mode());
            session.setScenarioCode(plan.mode().equals(InterviewMode.SCENARIO.getCode()) ? plan.code() : null);
            session.setPlanJson(interviewPlanService.serialize(plan));
            session.setTitle(plan.title() + " . " + LocalDate.now());
            session.setStatus(0);
            session.setTargetQuestionCount(dto.getTargetQuestionCount() == null
                    ? DEFAULT_TARGET_QUESTION_COUNT : dto.getTargetQuestionCount());
            session.setAnsweredQuestionCount(0);

            String systemPrompt = buildSystemPrompt(plan);
            InterviewPlanItem openingItem = interviewPlanService.itemForRound(plan, 1);
            String openingQuestion = openingItem.questionText();
            String openingMessage = buildOpeningMessage(plan, openingItem);
            InterviewPersistenceService.CreatedInterview created = persistenceService.create(
                    session, systemPrompt, openingMessage, openingQuestion
            );
            evictContextQuietly(session.getId());
            questionCollectDispatcher.dispatch(
                    session.getId(),
                    created.assistantMessage().getId(),
                    session.getDirection(),
                    openingQuestion
            );

            InterviewSessionVO vo = new InterviewSessionVO();
            vo.setId(session.getId());
            vo.setDirection(session.getDirection());
            vo.setMode(session.getMode());
            vo.setScenarioCode(session.getScenarioCode());
            vo.setTitle(session.getTitle());
            vo.setOpeningMessage(openingMessage);
            vo.setTargetQuestionCount(session.getTargetQuestionCount());
            vo.setAnsweredQuestionCount(0);
            return vo;
        } finally {
            redisLockManager.unlock(createLockKey, createLockToken);
        }
    }

    private String buildOpeningMessage(InterviewPlan plan, InterviewPlanItem item) {
        String[] templates = {
                "你好，今天我们进行一场%s。整场会围绕%s展开，我会逐步覆盖不同模块。\n\n【下一题】\n%s",
                "欢迎进入%s。今天不预设固定路线，我会根据本场计划依次考察%s。\n\n【下一题】\n%s",
                "我们开始今天的%s。先从%s切入，后面会结合工程场景继续深入。\n\n【下一题】\n%s"
        };
        int index = Math.floorMod(java.util.UUID.randomUUID().hashCode(), templates.length);
        return templates[index].formatted(plan.title(), item.moduleName(), item.questionText());
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
        String lockKey = CHAT_LOCK_PREFIX+sessionId;
        String lockToken = redisLockManager.tryLock(lockKey, CHAT_LOCK_TTL);
        if(lockToken == null){
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS,"上一轮对话还在处理中，请稍后");
        }

        try {
            if (interviewExpirationService.expireWhileHoldingChatLock(sessionId)) {
                throw new BusinessException(
                        ErrorCode.CONFLICT, "面试已因长时间未操作自动结束，请重新开始"
                );
            }
            InterviewTurn currentTurn = ensureWaitingTurn(session);
            InterviewPlan plan = interviewPlanService.load(session);
            InterviewPlanItem nextItem = interviewPlanService.itemForRound(
                    plan, currentTurn.getRoundNo() + 1
            );
            int targetCount = targetQuestionCount(session);
            int answeredCount = answeredQuestionCount(session);
            boolean finalRound = answeredCount + 1 >= targetCount;
            List<InterviewMessage> history = contextManager.load(sessionId);
            List<Message> springAiMessages = toSpringAiMessages(history);
            if (finalRound) {
                springAiMessages.add(new SystemMessage(buildFinalRoundInstruction()));
            } else if (nextItem != null) {
                springAiMessages.add(new SystemMessage(buildNextQuestionInstruction(nextItem)));
            }
            springAiMessages.add(new UserMessage(dto.getContent()));

            // 有限超时:模型流卡死(既不吐字也不报错)时,到点触发 onTimeout 自救(dispose+放锁),
            // 而不是干等到锁 TTL 过期后被别的请求开出第二条流
            SseEmitter emitter = new SseEmitter(TimeUnit.MINUTES.toMillis(5));
            StringBuilder fullReply = new StringBuilder();
            String userContent = dto.getContent();
            AtomicInteger streamState = new AtomicInteger(0); // 0=流式中 1=完成处理中 2=已取消

            Disposable subscription = chatClient.prompt(new Prompt(springAiMessages))
                    .stream()
                    .content()
                    .doOnNext(fullReply::append)
                    .subscribe(
                            delta -> sendEvent(emitter, Map.of("type", "delta", "content", delta)),
                            error -> {
                                if (streamState.compareAndSet(0, 2)) {
                                    onStreamError(emitter, sessionId, lockKey, lockToken, error);
                                }
                            },
                            () -> {
                                if (streamState.compareAndSet(0, 1)) {
                                    onStreamComplete(
                                            emitter,
                                            sessionId,
                                            lockKey,
                                            lockToken,
                                            userContent,
                                            fullReply.toString(),
                                            session.getDirection(),
                                            currentTurn,
                                            plan,
                                            finalRound,
                                            targetCount
                                    );
                                }
                            }
                    );

            emitter.onTimeout(() -> {
                log.warn("SSE超时:sessionId={}",sessionId);
                if (streamState.compareAndSet(0, 2)) {
                    subscription.dispose();
                    sendEvent(emitter, Map.of("type", "error", "message", "AI响应超时，请重试"));
                    emitter.complete();
                    redisLockManager.unlock(lockKey, lockToken);
                }
            });
            emitter.onError(throwable -> {
                log.warn("SSE 连接异常:sessionId={}",sessionId,throwable);
                if (streamState.compareAndSet(0, 2)) {
                    subscription.dispose();
                    redisLockManager.unlock(lockKey, lockToken);
                }
            });

            return emitter;
        } catch (RuntimeException e) {
            // 锁已经拿到了,但订阅模型流之前的这几步(取历史、落库、消息角色转换……)只要有一步抛异常,
            // 后面负责释放锁的几条路径(onStreamComplete/onStreamError/onTimeout/onError)都不会被触发,
            // 这里必须自己把锁放掉,不然要等 TTL 到期,这场会话在此期间没法再发消息
            redisLockManager.unlock(lockKey, lockToken);
            throw e;
        }
    }

    @Override
    public PageResult<InterviewSessionListVO> listSessions(InterviewSessionQueryDTO dto) {
        Long userId = UserContext.getUserId();
        interviewExpirationService.expireInactiveForUser(userId);
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
        if (session.getStatus() == 0 && interviewExpirationService.expireIfInactive(sessionId)) {
            session = mustFindOwnSession(sessionId);
        }
        return buildSessionDetail(session);
    }

    @Override
    public InterviewSessionDetailVO getCurrentSession() {
        Long userId = UserContext.getUserId();
        interviewExpirationService.expireInactiveForUser(userId);
        InterviewSession session = interviewSessionMapper.selectLatestInProgressByUserId(userId);
        return session == null ? null : buildSessionDetail(session);
    }

    private InterviewSessionDetailVO buildSessionDetail(InterviewSession session) {
        Long sessionId = session.getId();
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

        if (InterviewExpirationService.FINISH_REASON.equals(session.getFinishReason())) {
            throw new BusinessException(
                    ErrorCode.CONFLICT, "面试已因长时间未操作自动结束，不生成评价报告"
            );
        }

        InterviewReport existing = interviewReportMapper.selectBySessionId(sessionId);
        if (existing != null && Integer.valueOf(1).equals(existing.getStatus())) {
            interviewSessionMapper.markReportReady(sessionId);
            return buildReportStatus(existing);
        }

        String chatLockKey = CHAT_LOCK_PREFIX + sessionId;
        String chatLockToken = redisLockManager.tryLock(chatLockKey, CHAT_LOCK_TTL);
        if (chatLockToken == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前回答正在处理中，请稍后再结束面试");
        }
        try {
            if (session.getStatus() == 0
                    && interviewExpirationService.expireWhileHoldingChatLock(sessionId)) {
                throw new BusinessException(
                        ErrorCode.CONFLICT, "面试已因长时间未操作自动结束，不生成评价报告"
                );
            }
            if (answeredQuestionCount(session) == 0) {
                throw new BusinessException(ErrorCode.CONFLICT, "至少回答一道题后才能结束面试");
            }
            if (session.getStatus() == 0) {
                persistenceService.finishByUser(sessionId);
            }
        } finally {
            redisLockManager.unlock(chatLockKey, chatLockToken);
        }
        startReportGeneration(sessionId);
        InterviewReport report = interviewReportMapper.selectBySessionId(sessionId);
        return report == null
                ? buildGeneratingStatus("报告正在生成中，请稍候")
                : buildReportStatus(report);
    }

    private void startReportGeneration(Long sessionId) {
        String reportLockKey = REPORT_LOCK_PREFIX + sessionId;
        String reportLockToken = redisLockManager.tryLock(reportLockKey, REPORT_LOCK_TTL);
        if (reportLockToken == null) {
            return;
        }
        try {
            InterviewReport existing = interviewReportMapper.selectBySessionId(sessionId);
            if (existing != null && Integer.valueOf(1).equals(existing.getStatus())) {
                interviewSessionMapper.markReportReady(sessionId);
                redisLockManager.unlock(reportLockKey, reportLockToken);
                return;
            }
            interviewReportMapper.insertGenerating(sessionId);
            CompletableFuture.runAsync(
                    () -> generateReportJob(sessionId, reportLockKey, reportLockToken), reportExecutor
            );
        } catch (RuntimeException e) {
            redisLockManager.unlock(reportLockKey, reportLockToken);
            throw e;
        }
    }

    private void generateReportJob(Long sessionId, String reportLockKey, String reportLockToken) {
        try {
            InterviewReport report = interviewReportMapper.selectBySessionId(sessionId);
            if(report != null && Integer.valueOf(1).equals(report.getStatus())){
                interviewSessionMapper.markReportReady(sessionId);
                return;
            }
            List<InterviewTurn> turns = interviewTurnMapper.selectAnsweredBySessionId(sessionId);
            String transcript = turns.isEmpty()
                    ? buildLegacyCompletedTranscript(interviewMessageMapper.selectBySessionId(sessionId))
                    : buildTurnTranscript(turns);
            if (!org.springframework.util.StringUtils.hasText(transcript)) {
                throw new IllegalStateException("没有可用于评分的完整问答");
            }
            InterviewReportResult result = generateReportWithRetry(transcript);
            boolean everyTurnScored = !turns.isEmpty()
                    && turns.stream().allMatch(turn -> turn.getScore() != null);
            Double averageScore = everyTurnScored
                    ? interviewTurnMapper.averageScoreBySessionId(sessionId) : null;
            if (averageScore != null) {
                result = new InterviewReportResult(
                        (int) Math.round(averageScore),
                        result.highlights(),
                        result.weaknesses(),
                        result.summary()
                );
            }
            int markedSuccess = interviewReportMapper.markSuccess(
                    sessionId,
                    result.score(),
                    serializeReportContent(result)
            );
            InterviewReport storedReport = markedSuccess == 1
                    ? null : interviewReportMapper.selectBySessionId(sessionId);
            if (markedSuccess == 1
                    || (storedReport != null && Integer.valueOf(1).equals(storedReport.getStatus()))) {
                interviewSessionMapper.markReportReady(sessionId);
            }
        }catch (Exception e){
            log.error("异步生成评价报告失败:sessionId={}",sessionId,e);
            interviewReportMapper.markFailed(sessionId,"评价报告生成失败,请稍后再试");
        }finally {
            redisLockManager.unlock(reportLockKey, reportLockToken);
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
        InterviewSession session = mustFindOwnSession(sessionId);
        if (session.getStatus() == 0 && interviewExpirationService.expireIfInactive(sessionId)) {
            session = mustFindOwnSession(sessionId);
        }
        if (InterviewExpirationService.FINISH_REASON.equals(session.getFinishReason())) {
            InterviewReportStatusVO unavailable = new InterviewReportStatusVO();
            unavailable.setStatus(2);
            unavailable.setMessage("面试已因长时间未操作自动结束，不生成评价报告");
            return unavailable;
        }
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
                        || result.score() < 0
                        || result.score() > 100
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
            1. score:建议总分,0~100 的整数。系统存在单题评分时会使用单题平均分覆盖此值。
            2. highlights:候选人表现好的地方,2~4 条,每条一句话,要具体(引用候选人实际提到的技术点),不说空泛的场面话。
            3. weaknesses:回答中不足或值得继续深挖的地方,2~4 条,同样要具体。
            4. summary:1~2 句话的总体评价,像面试官写给 HR 的结论。
            只依据记录里已经回答的题目打分和点评,不要编造候选人没有说过的内容，
            也不要因为用户主动结束后没有继续回答尚未出现的新题而扣分。
            """;
    }

    private String buildTurnTranscript(List<InterviewTurn> turns) {
        StringBuilder sb = new StringBuilder();
        for (InterviewTurn turn : turns) {
            sb.append("第").append(turn.getRoundNo()).append("题\n")
                    .append("面试官:").append(turn.getQuestionText()).append("\n")
                    .append("候选人:").append(turn.getAnswerText()).append("\n");
            if (turn.getScore() != null) {
                sb.append("单题评分:").append(turn.getScore()).append("/100\n");
            }
            if (org.springframework.util.StringUtils.hasText(turn.getEvaluation())) {
                sb.append("单题评价:").append(turn.getEvaluation()).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String buildLegacyCompletedTranscript(List<InterviewMessage> history) {
        StringBuilder sb = new StringBuilder();
        String pendingQuestion = null;
        int roundNo = 0;
        for (InterviewMessage message : history) {
            if ("assistant".equals(message.getRole())) {
                pendingQuestion = aiQuestionExtractor.extract(message.getContent()).orElse(null);
            } else if ("user".equals(message.getRole()) && pendingQuestion != null) {
                roundNo++;
                sb.append("第").append(roundNo).append("题\n")
                        .append("面试官:").append(pendingQuestion).append("\n")
                        .append("候选人:").append(message.getContent()).append("\n\n");
                pendingQuestion = null;
            }
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
                               String lockToken, Throwable error) {
        log.error("AI 流式调用失败", error);
        try {
            sendEvent(emitter, Map.of("type", "error", "message", "AI服务异常，请稍后重试"));
            emitter.completeWithError(error);
        } finally {
            redisLockManager.unlock(lockKey, lockToken);
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

    private void onStreamComplete(SseEmitter emitter,
                                  Long sessionId,
                                  String lockKey,
                                  String lockToken,
                                  String userContent,
                                  String fullReply,
                                  String direction,
                                  InterviewTurn currentTurn,
                                  InterviewPlan plan,
                                  boolean finalRound,
                                  int targetCount) {
        try {
            String replyToPersist = fullReply;
            InterviewPlanItem nextItem = interviewPlanService.itemForRound(
                    plan, currentTurn.getRoundNo() + 1
            );
            Optional<String> nextQuestion = finalRound || nextItem == null
                    ? Optional.empty()
                    : Optional.of(nextItem.questionText());
            if (!finalRound && nextQuestion.isPresent()
                    && aiQuestionExtractor.extract(fullReply).isEmpty()) {
                String suffix = "\n\n【下一题】\n" + nextQuestion.get();
                replyToPersist += suffix;
                sendEvent(emitter, Map.of("type", "delta", "content", suffix));
            }

            InterviewAnswerEvaluation evaluation = null;
            String evaluationFailureReason = null;
            try {
                InterviewPlanItem currentItem = interviewPlanService.itemForRound(
                        plan,
                        currentTurn.getRoundNo()
                );
                InterviewEvaluationResult evaluationResult = answerEvaluationService.evaluate(
                        currentTurn.getQuestionText(),
                        userContent,
                        currentItem
                );
                evaluation = evaluationResult.evaluation();
            } catch (Exception evaluationError) {
                log.error("面试单题评分失败:sessionId={},round={}",
                        sessionId, currentTurn.getRoundNo(), evaluationError);
                evaluationFailureReason = "AI评分失败，生成报告时将整体评价";
            }

            InterviewPersistenceService.CompletedRound completed = persistenceService.completeRound(
                    sessionId,
                    currentTurn,
                    userContent,
                    replyToPersist,
                    evaluation,
                    evaluationFailureReason,
                    nextQuestion.orElse(null),
                    finalRound
            );
            InterviewMessage assistantMsg = completed.assistantMessage();
            evictContextQuietly(sessionId);

            if (finalRound) {
                startReportGeneration(sessionId);
            } else {
                String question = nextQuestion.orElseThrow();
                questionCollectDispatcher.dispatch(
                        sessionId,
                        assistantMsg.getId(),
                        direction,
                        question
                );
            }
            sendEvent(emitter, Map.of(
                    "type", "done",
                    "messageId", assistantMsg.getId(),
                    "finished", finalRound,
                    "answeredCount", currentTurn.getRoundNo(),
                    "targetCount", targetCount
            ));
            emitter.complete();
        } catch (Exception e) {
            // 落库失败也要把流关掉并告诉前端,否则 emitter 既不 complete 也不发 error,前端 reader 永远挂着
            log.error("流式完成后落库失败:sessionId={}", sessionId, e);
            sendEvent(emitter, Map.of("type", "error", "message", "回复保存失败，请重试"));
            emitter.completeWithError(e);
        } finally {
            redisLockManager.unlock(lockKey, lockToken);
        }
    }

    private void evictContextQuietly(Long sessionId) {
        try {
            contextManager.evict(sessionId);
        } catch (RuntimeException e) {
            log.warn("清理面试上下文缓存失败:sessionId={}", sessionId, e);
        }
    }

    private InterviewSession mustFindOwnSession(Long sessionId) {
        InterviewSession session = interviewSessionMapper.selectById(sessionId);
        if(session == null || !session.getUserId().equals(UserContext.getUserId())){
            throw new BusinessException(ErrorCode.NOT_FOUND,"面试会话不存在");
        }
        return session;
    }

    private InterviewTurn ensureWaitingTurn(InterviewSession session) {
        InterviewTurn turn = interviewTurnMapper.selectWaitingBySessionId(session.getId());
        if (turn != null) {
            return turn;
        }

        // 兼容迁移前已经创建、但尚未生成 interview_turn 的进行中会话。
        InterviewMessage lastAssistant = interviewMessageMapper.selectLastAssistantBySessionId(session.getId());
        String question = lastAssistant == null
                ? null
                : aiQuestionExtractor.extract(lastAssistant.getContent()).orElse(null);
        if (!org.springframework.util.StringUtils.hasText(question)) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前会话缺少待回答题目，请重新开始面试");
        }
        return interviewTurnService.createWaitingTurn(
                session.getId(),
                session.getUserId(),
                answeredQuestionCount(session) + 1,
                question
        );
    }

    private int targetQuestionCount(InterviewSession session) {
        return session.getTargetQuestionCount() == null
                ? DEFAULT_TARGET_QUESTION_COUNT : session.getTargetQuestionCount();
    }

    private int answeredQuestionCount(InterviewSession session) {
        return session.getAnsweredQuestionCount() == null ? 0 : session.getAnsweredQuestionCount();
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

    private String buildFinalRoundInstruction() {
        return """
                本轮是这场面试的最后一道题。请先简短点评候选人的本次回答，
                然后以【面试结束】收尾。严禁再输出【下一题】，也不要再提出任何新问题。
                """;
    }

    private String buildSystemPrompt(InterviewPlan plan) {
        return """
                你是一位经验丰富的 Java 后端资深面试官,正在进行一场「%s」。

                本场面试的总体考察范围是:%s。

                面试策略(重要):
                1. 每次只问一个问题,不要一次性抛出多个问题。
                2. 以本场计划指定的模块为准,不要自行把整场面试拉回某一个熟悉方向。
                3. 对同一个知识点最多追问一次,然后切换到计划中的下一个模块。
                4. 当前回合的问题必须围绕系统提供的目标模块,不要被候选人的某个细节带偏。
                5. 点评要简短、具体,不要用固定套话,也不要每次都使用完全相同的句式。
                6. 语气专业、简洁,像真实面试官一样自然对话,不要用"好的,我们开始吧"这类机械化开场白,也不要每句话都用 Markdown 列表排版。
                7. 每次回复可以先简短点评候选人的回答,但最后必须使用下面的固定格式提出下一题:

                【下一题】
                这里写一个完整、脱离上下文也能理解的问题

                8. "【下一题】"后只能出现一道题,题目必须独立完整。不要使用"这里""刚才这个方案""那为什么"等依赖上文的指代。
                """.formatted(plan.title(), plan.focus());
    }

    private String buildNextQuestionInstruction(InterviewPlanItem item) {
        return """
                下一题由后端面试计划控制,请不要自行切换主题。
                目标模块: %s
                本轮考察能力: %s
                问题类型: %s
                难度: %d/3
                请在点评本轮回答后,严格使用下面这道题作为【下一题】,不要改写、追加第二道题或继续追问:

                【下一题】
                %s
                """.formatted(
                item.moduleName(), item.skill(), item.questionType(), item.difficulty(), item.questionText()
        );
    }
}
