# Spring AI 模块知识总结:从「调一次模型」到「打字机效果的流式面试」

> 这是 AI 模块(任务 2.1~2.4)的**完整重新整理版**,取代之前分阶段写的旧版本。之前旧版只讲到任务 2.3(多轮对话),这次任务 2.4(SSE 流式输出)你已经自己写完了,所以把整个模块从头到尾重新捋一遍,一篇文档能看到完整链路,不用再跳着看好几篇。
> 全部代码对应项目里**现在真实跑着的版本**,不是简化过的教学代码。

---

## 零、整个模块四个任务分别做了什么

在看代码之前,先建立一张全局地图,知道自己现在走到哪一步、每一步解决的是什么问题:

| 任务 | 解决的问题 | 一句话概括 |
|------|------|------|
| **2.1 Spring AI 接入打通** | 项目怎么"连上"一个大模型 | 配好 `ChatClient`,能调通一次最基础的问答 |
| **2.2 创建面试会话 + 面试官 Prompt 设计** | 怎么让模型"扮演"一个面试官,聊出开场白 | 用 system prompt 定人设,插库 + 调模型生成第一句话 |
| **2.3 多轮对话上下文管理 + Token 窗口** | 模型本身不记事,怎么让它"看起来"记得住 | 每轮把裁剪过的历史消息重新发一遍,MySQL+Redis 双写 |
| **2.4 SSE 流式输出** | 用户体验:等 5~10 秒 vs 像打字机一样实时看到文字 | 把"等模型说完再一次性返回"换成"模型吐一点就发一点" |

这四个任务不是四段孤立的代码,是**同一条链路层层加码**:2.1 打通"能不能调通",2.2 加上"角色扮演",2.3 加上"记忆",2.4 加上"实时性"。读下面每一节的时候,注意它是在前一节的基础上改了哪一小块,而不是推倒重写。

---

## 一、先搞懂几个大模型的基础概念

在看代码之前,有三个概念必须先搞清楚,不然后面的设计选择会看不懂"为什么要这样做"。

### 1. 三种角色:system / user / assistant

调用大模型的对话接口时,你发送的不是一句话,而是一个**消息列表**,每条消息都有一个"角色":

| 角色 | 作用 |
|------|------|
| `system` | 给模型定人设、定规则,类似"演员拿到的角色说明书"。用户看不到这条消息,但它决定了模型全程怎么"演" |
| `user` | 用户说的话 |
| `assistant` | 模型自己说过的话 |

你项目里的"面试官人设"(资深 Java 面试官、每次只问一个问题……)就是一条 `system` 消息。这条消息**不是"发一次就完事"**,而是**每一轮对话都要原封不动地带上**——因为模型本身不记得自己是谁,每次调用都要重新告诉它。

### 2. Prompt Engineering:写 prompt 本质是在写"给模型的需求文档"

`system prompt` 这种大段文字不是随便写的,它其实和你平时写的接口文档、需求文档是一回事——**越明确、越无歧义,模型的输出就越可控**。看这次项目里实际写的这段(`InterviewServiceImpl.buildSystemPrompt`):

```java
private String buildSystemPrompt(InterviewDirection direction) {
    return """
            你是一位经验丰富的 Java 后端资深面试官,正在对候选人进行一场专注于「%s」方向的技术面试。

            面试风格要求:
            1. 每次只问一个问题,不要一次性抛出多个问题。
            2. 根据候选人上一轮回答的质量动态调整下一个问题的深度——回答得好就继续追问细节,回答得含糊就换个角度重新问或给出提示。
            3. 语气专业、简洁,像真实面试官一样自然对话,不要用"好的,我们开始吧"这类机械化开场白,也不要每句话都用 Markdown 列表排版。
            4. 这场面试重点考察方向:%s。
            """.formatted(direction.getLabel(), direction.getFocus());
}
```

拆开看这四条"要求"分别在防什么:

- **第 1 条("每次只问一个问题")**:防止模型一股脑把五个问题都抛出来——大模型有个天然倾向,越"热心"越容易一次性把能说的都说了,不加约束的话面试体验会变成"考卷"而不是"对话"。
- **第 2 条("根据回答质量调整深度")**:这是让面试"看起来聪明"的关键一句,没有这句,模型大概率会按照一个固定的问题清单机械往下问,不会真的"听"你回答了什么。
- **第 3 条(语气 + 不用机械开场白 + 不用 Markdown 列表)**:纯粹是体验问题。默认情况下,模型很喜欢用"好的,我们开始吧!"这种客服式开场,也很喜欢把什么都列成 1234 的列表——这在"模拟对话"场景里显得很假,需要显式压制。
- **第 4 条(`%s` 占位的考察方向)**:这是**动态**部分,从 `InterviewDirection` 枚举取,五个方向(Java 并发/JVM/MySQL/Redis/系统设计)复用同一套 prompt 模板,只换这一句,不用为每个方向复制一份 prompt。

**这就是"Prompt Engineering"这个词在工程项目里的真实含义**:不是玄学、不是"念咒语",而是像写单元测试一样,把你想要的行为、不想要的行为都显式写清楚,然后跑真实案例去验证效果,不断调整措辞。

### 3. OpenAI 协议:为什么"硅基流动的 DeepSeek"能用"OpenAI 的接口格式"调用

这是很多人第一次遇到会懵的地方:项目里引入的依赖叫 `spring-ai-starter-model-openai`,配的是硅基流动的 key,调的模型是 DeepSeek——三个"OpenAI"看起来毫不相关的东西怎么能凑一块?

真相是:**OpenAI 定义的 Chat Completions 接口格式(请求体长什么样、返回体长什么样),已经事实上成了整个行业的通用标准**。几乎所有大模型服务商(硅基流动这类聚合平台、国内各家云厂商、甚至企业自己部署的开源模型网关)都会**照抄这套接口格式**对外提供服务——因为这样一来,全世界基于"OpenAI 协议"写的 SDK、工具链(包括 Spring AI 的这个 openai starter)都能直接拿来用,不用为每一家单独写一套适配代码。

所以"调用硅基流动的 DeepSeek"这件事,本质是:**用一个通用的"OpenAI 协议客户端",把请求地址(`base-url`)从 `api.openai.com` 换成硅基流动的地址,把模型名(`model`)换成硅基流动平台上的 DeepSeek 型号**,别的什么都不用变。这也是为什么项目最终选了 `spring-ai-starter-model-openai` 而不是 `spring-ai-starter-model-deepseek`——后者内部把地址锁死指向了 DeepSeek 官方,没法这样灵活换目标。

---

## 二、Spring AI 的分层抽象:你在跟谁打交道

Spring AI 不是一个"调 HTTP 接口的工具类",它是分层设计的,理解这个分层能帮你理解代码里为什么会出现 `ChatClient.Builder` 这种"半成品"式的对象:

```
Model 层(最底层)
  ↑ 具体和某个厂商的 HTTP 接口打交道,这次是 OpenAiChatModel
ChatClient 层(业务代码直接用的门面)
  ↑ 提供 .prompt().system(...).user(...).call().content() 这种链式 API
  ↑ 同一个门面也提供 .stream() 版本(第八节会讲),同步/流式只差这一步调用
Advisor 层(可插拔的拦截器,这个项目暂时没用到)
  ↑ 请求发出去之前、回复回来之后,可以插入自定义逻辑,比如上下文裁剪——
    Spring AI 官方提供了现成的 Advisor 做这件事,这个项目选择自己手写 InterviewContextManager
```

**为什么不直接用 `RestTemplate` 发 HTTP 请求?** 因为业务代码不应该关心"这次请求的 JSON 长什么样、认证头怎么加、流式响应怎么解析"这些细节——这些交给 `Model` 层去适配不同厂商;业务代码只管拿着 `ChatClient` 说"帮我问一句话",这是**关注点分离**。以后如果要从"硅基流动的 DeepSeek"换成别的同样兼容 OpenAI 协议的厂商,理论上只用改配置文件里的 `base-url`/`model`/`api-key`,业务代码一行都不用动。

`AiConfig.java` 把这个"半成品" `ChatClient.Builder` 组装成能用的 `ChatClient`:

```java
@Bean
public ChatClient chatClient(ChatClient.Builder builder){
    return builder.build();
}
```

`builder` 这个参数是 Spring Boot 的自动配置机制(引入 `spring-ai-starter-model-openai` 后,只要配好 `api-key` 就自动往容器里注册)递给你的,你只用 `.build()` 一下变成可以直接用的 `ChatClient`,注册成 bean 供全项目复用。

---

## 三、`InterviewDirection`:用枚举承载"业务知识",不只是用来做校验

```java
public enum InterviewDirection {
    JAVA_CONCURRENCY("java_concurrency", "Java 并发", "线程池、锁机制、CAS、AQS、并发容器、线程协作工具"),
    JVM("jvm", "JVM", "内存区域划分、垃圾回收算法与收集器、类加载机制、常见调优场景"),
    MYSQL("mysql", "MySQL", "索引原理、事务隔离级别与 MVCC、锁机制、SQL 优化与索引失效场景"),
    REDIS("redis", "Redis", "常用数据结构与使用场景、持久化机制、缓存穿透雪崩击穿、分布式锁"),
    SYSTEM_DESIGN("system_design", "系统设计", "高并发架构设计思路、限流降级、缓存与数据库一致性、常见系统的设计权衡");

    private final String code;    // 对外的接口值,比如 "java_concurrency"
    private final String label;   // 给人看的中文名,比如 "Java 并发"
    private final String focus;   // 拼进 system prompt 的"考察重点"素材

    public static InterviewDirection fromCode(String code) {
        for (InterviewDirection d : values()) {
            if (d.code.equals(code)) {
                return d;
            }
        }
        return null;
    }
}
```

这个枚举做了两件事,很多人第一次写枚举只会想到第一件:

1. **校验**:`fromCode` 把一个字符串对应到合法值,对不上就返回 `null`,调用方据此判断"这是不是一个非法方向"。
2. **承载业务素材**:`focus` 字段直接被拼进 `buildSystemPrompt` 里。**枚举实例本身就是一份"配置数据",不需要另外写一个 `switch` 语句或者 `Map<String, String>` 去对应每个方向该说什么**——值和与这个值强相关的数据可以绑在一起。

---

## 四、`createInterview` 全链路逐行拆解(任务 2.1/2.2)

```java
@Override
public InterviewSessionVO createInterview(CreateInterviewDTO dto) {
    InterviewDirection direction = InterviewDirection.fromCode(dto.getDirection());
    if (direction == null) {
        throw new BusinessException(ErrorCode.PARAMS_ERROR, "暂不支持该面试方向");
    }
```

**第一步:校验方向合法**。注意用的是 `InterviewDirection.fromCode`,不是 Bean Validation(`@NotBlank`)——两者分工不同:`@NotBlank` 管"这个字符串是不是空的"这种**结构性**校验;"是不是我定义的五个合法值之一"这种需要结合**业务枚举**去比对的校验,放在 Service 层手写判断。

```java
    Long userId = UserContext.getUserId();
    long activeCount = interviewSessionMapper.countInProgress(userId);
    if (activeCount >= MAX_ACTIVE_SESSIONS) {
        throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "面试中的会话达到上限，若想继续，请关闭先前对话");
    }
```

**第二步:限制并发会话数**。每一次调用大模型都是**真金白银的成本**,不限制的话一个用户可以无限开新会话,每个会话都要占着一份对话历史,以后每一轮追问都要花钱调模型。限制"进行中"的会话数上限(这里是 3),是一个朴素但必要的成本控制手段。

```java
    InterviewSession session = new InterviewSession();
    session.setUserId(userId);
    session.setDirection(direction.getCode());
    session.setTitle(direction.getLabel() + " . " + LocalDate.now());
    session.setStatus(0);
    interviewSessionMapper.insert(session);
    // 到这里,插库已经完成。下面调用 AI 是一次不可控耗时的外部请求,
    // 整个方法没有加 @Transactional,就是不想让数据库连接陪着这次外部调用一起等
```

**第三步:先把会话记录插库,而且这一步之后不包事务**。数据库事务的本质是"占用一条数据库连接,直到提交或回滚",事务开得越久,连接被占用的时间就越长。调用大模型是一次跨网络的外部请求,快则一两秒,慢则可能更久——**如果把这次调用也塞进事务里,相当于让一条数据库连接白白空转等待几秒钟什么正事都不干**。高并发场景下,连接池会被这种"陪等"的事务迅速占满——这就是**长事务陷阱**,任何"调第三方接口 + 写库"的组合都要留意这一点。

正确做法:**该插库的先插库,外部调用留在事务之外**。那 AI 调用失败了怎么办?——这次的选择是"允许这个会话就这样晾在那里,没有开场白",不做自动删除或回滚,失败补偿留给后续任务迭代。

```java
    String systemPrompt = buildSystemPrompt(direction);
    String openingMessage;
    try {
        openingMessage = chatClient.prompt()
                .system(systemPrompt)
                .user("请开始这场模拟面试：先用一两句话做简短的自我介绍，然后直接提出第一个问题")
                .call()
                .content();
    } catch (Exception e) {
        throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "开场白生成失败，请稍后再试");
    }
```

**第四步:真正调用模型生成开场白**。注意"请提出第一个问题"这句写在 **user** 消息里,不是 system prompt——system prompt 之后**每一轮**对话都要原样复用,如果把"提出第一个问题"这种一次性指令混进去,模型每一轮都会误以为自己"刚开始",容易在后续追问里重复自我介绍。**一次性的指令放 user 消息,长期有效的人设规则放 system 消息**。

`.call()` 是同步调用(等模型把完整回复生成完才返回),`.content()` 直接拿到回复的纯文本——这是 2.1/2.2/2.3 阶段用的调用方式,第八节会讲它和 2.4 的 `.stream()` 差在哪。外部调用包一层 `try-catch`,失败转成统一的 `AI_SERVICE_ERROR`,不让裸异常抛成 500。

```java
    InterviewMessage systemMsg = buildMessage(session.getId(), "system", systemPrompt);
    InterviewMessage assistantMsg = buildMessage(session.getId(), "assistant", openingMessage);
    interviewMessageMapper.insert(systemMsg);
    interviewMessageMapper.insert(assistantMsg);
    contextManager.append(session.getId(), systemMsg);
    contextManager.append(session.getId(), assistantMsg);
```

**第五步:两条消息(system + assistant 开场白)同时写进 MySQL 和 Redis**——这是下一节要讲的双写设计。

---

## 五、为什么消息要同时存 MySQL 又存 Redis(双写)

**两个存储各自的职责不一样,不是重复劳动:**

- **MySQL(`interview_message` 表)是事实来源(source of truth)**:完整保留这场面试的每一条消息,永久存在,用于"查看历史会话详情"这种低频、要求完整的场景,也是万一 Redis 数据丢失时用来"重建现场"的底本。
- **Redis(`zhimian:interview:ctx:{sessionId}` 这个 List)是"当前对话要用的热数据"**:每一轮新提问都要把最近的对话历史重新发给模型——这个操作**高频**(每问一句就要读一次),如果每次都去 MySQL 拉全部历史再拼装,对数据库是不必要的压力。

这和**任务 1.8/1.9(题目详情缓存)**里学过的"数据库是事实来源,Redis 是加速读的缓存"是**同一个设计思想的第二次应用**。

**用的数据结构是 `List`,不是 `String`(题目详情缓存)也不是 `ZSet`(排行榜)**——对话历史天然是"有先后顺序的一串消息",`List` 的 `rightPush`(对应 `RPUSH`,插到队尾)刚好能表达"新消息追加在最后"这个语义,取的时候用 `LRANGE key 0 -1` 就能按顺序拿到完整列表。**选数据结构的第一原则是:这份数据本身的"形状"是什么样的,就选最贴近这个形状的结构。**

**为什么 TTL 每次追加都要重新设置("续期")?**——只要用户还在继续这场对话,这个上下文就不该过期。如果只在会话创建那一刻设置 2 小时 TTL,用户如果聊了 2 个多小时,上下文会在对话进行到一半时突然消失。所以每追加一条消息就把 TTL 打回 2 小时,只要对话还在继续,它就永远不会到期——真正的过期只会发生在"用户离开、超过 2 小时没有任何新消息"的场景。

---

## 六、`InterviewContextManager`:让 AI 真正"记住"你们聊过什么(任务 2.3)

到 2.2 为止,只做到了"创建会话 + 生成第一句开场白",`append` 只管往 Redis 里写,从来没有"读出来拼给模型"这一步——**AI 压根还没有真正"记忆"能力**。2.3 要补的,就是"读"这一半:用户追加一句回答,系统要把**完整的历史对话**重新发给模型,AI 才能接着往下问,而不是每次都从零开始。

背后是两个硬约束(第一节讲过):大模型**无状态**,历史必须自己维护并重新发送;大模型有**上下文窗口上限**、按 **token** 计费,历史不能无限增长。这两个约束合起来,就是这个类要解决的问题。

### 为什么不直接用 Spring AI 的 Advisor,要自己手写一个类

1. **裁剪规则是业务特有的**:"system prompt 永远保留,其余按 token 预算从最新往回裁剪"是这个项目自己的业务规则,不是通用组件能开箱即用的默认行为。
2. **存储介质是自定义的双层结构**:历史要读 Redis(热数据)、未命中要回源 MySQL(事实来源)再回填——这套"缓存 + 兜底重建"的逻辑本来就是你从任务 1.9 一路写下来的自定义能力。
3. **学习阶段,先理解原理更重要**:自己写一遍,才真正理解"多轮对话的记忆"背后发生了什么——以后被问到"LLM 应用怎么做上下文管理",能讲出实现细节,而不是只会说"用了框架自带的"。

### `append`:写入 Redis 并续期

```java
public void append(Long sessionId, InterviewMessage message){
    String key = ctxKey(sessionId);
    redisTemplate.opsForList().rightPush(key,serialize(message));
    redisTemplate.expire(key,CTX_TTL_HOURS, TimeUnit.HOURS);
}
```

`createInterview`(存开场白)和 `chat`(存每一轮问答)共用这一份实现,不用各写一遍——这和你在任务 1.9 把"补分类名+标签"抽成 `enrichToListVO` 是同一个道理:第二处要用到同一段逻辑,就该抽出来。

### `load`:读取历史,Redis 未命中就从 MySQL 重建

```java
public List<InterviewMessage> load(Long sessionId){
    String key = ctxKey(sessionId);
    List<String> raw = redisTemplate.opsForList().range(key,0,-1);

    List<InterviewMessage> messages;
    if(raw == null || raw.isEmpty()){
        log.info("会话{}的Redis上下文未命中，从MySQL重建",sessionId);
        messages = interviewMessageMapper.selectBySessionId(sessionId);
        rebuildRedis(sessionId,messages);
    }else {
        messages = raw.stream().map(this::deserialize).toList();
    }
    return trim(messages);
}
```

这是你在这个项目里第三次遇到"缓存未命中就回源重建"这个模式了(第一次是任务 1.9 的详情缓存穿透防护,第二次是任务 1.10 排行榜)——套路都一样:**先查缓存,查不到就查数据库(事实来源),查到之后回填缓存,再返回结果**。这次的"未命中"场景具体是:会话很久没人说话、Redis 里 2 小时的 TTL 到期了;或者 Redis 服务重启、数据丢了;又或者是自测时故意 `redis-cli DEL` 清掉的。**只要 Redis 里读不到,就必须能从 MySQL 把现场完整地恢复出来**,不然用户会突然发现 AI"失忆"了。

`rebuildRedis` 把从 MySQL 查出来的历史重新写回 Redis——**只查库不回填,等于缓存白建了**。

### `trim`:按 token 预算裁剪,这是最容易写反的一步

```java
private List<InterviewMessage> trim(List<InterviewMessage> messages){
    InterviewMessage systemMsg = messages.stream()
            .filter(m->"system".equals(m.getRole()))
            .findFirst()
            .orElse(null);
    List<InterviewMessage> others = messages.stream()
            .filter(m->!"system".equals(m.getRole()))
            .toList();
    int used = systemMsg !=null ? estimateTokens(systemMsg.getContent()) : 0 ;
    LinkedList<InterviewMessage> kept = new LinkedList<>();
    for(int i =others.size()-1;i>=0;i--){
        InterviewMessage m = others.get(i);
        int tokens =estimateTokens(m.getContent());
        if(used + tokens > TOKEN_BUDGET){
            break;
        }
        kept.addFirst(m);
        used += tokens;
    }

    List<InterviewMessage> result = new ArrayList<>();
    if(systemMsg !=null){
        result.add(systemMsg);
    }
    result.addAll(kept);
    return result;
}

private int estimateTokens(String text) {
    return text.length() / 2;   // 粗算:中文字符数/2 估一个数量级,不追求精确
}
```

**Token 怎么估**:Token 是大模型处理文本的最小单位,不完全等于"一个字",英文大概 4 个字符≈1 个 token,中文大概 1~2 个字≈1 个 token。这次不追求精确分词,用"字符数 / 2"粗算一个数量级就够——**工程上常见的取舍:先用便宜的方法解决 80% 的问题**。

**裁剪的核心设计,拆成两步看:**

1. **先按 `role` 把 system 消息单独摘出来,永远保留,不参与裁剪**——不是假设它固定在列表第 0 位,而是显式按角色过滤,不受存储顺序影响。
2. **剩下的消息,从最后一条往前(倒序)累加 token 数,一旦快超预算就停手**。这一步的方向**极其容易写反**:如果不小心写成正序遍历、从预算里扣,留下的会是**最旧的**几条对话,丢掉的反而是**刚刚说过的话**——AI 会记得你老早以前说了什么,却把你刚才的回答忘得干干净净,体验完全是反的。倒序遍历、用 `LinkedList.addFirst` 把新决定保留的消息插到已保留列表最前面,遍历完之后天然就是正序的。

**上下文管理这类"看起来简单"的逻辑,最容易在"方向"这种细节上出错,而且一旦写反,不会报任何错误,只会在体验上悄悄变差,不容易第一时间发现。**

### `toSpringAiMessages`:把自己的实体转成 Spring AI 认识的类型

```java
private List<Message> toSpringAiMessages(List<InterviewMessage> history) {
    List<Message> messages = new ArrayList<>();
    for (InterviewMessage m : history) {
        switch (m.getRole()) {
            case "system" -> messages.add(new SystemMessage(m.getContent()));
            case "user" -> messages.add(new UserMessage(m.getContent()));
            case "assistant" -> messages.add(new AssistantMessage(m.getContent()));
            default -> throw new IllegalStateException("未知的消息角色: " + m.getRole());
        }
    }
    return messages;
}
```

`InterviewMessage` 是你自己的数据库实体,Spring AI 只认自己定义的 `Message` 接口,具体要用哪个实现类取决于这条消息的角色。`SystemMessage`/`UserMessage`/`AssistantMessage` 都实现了 `Message` 接口,所以能放进同一个 `List<Message>`。转换完,连同这一轮新的用户消息一起打包成 `new Prompt(springAiMessages)` 交给 `chatClient`——**这才是"多轮对话"的真正实现方式:不是模型自己记得,而是每次调用你都把完整的、裁剪过的历史,重新摆在它面前**。

---

## 七、从"等 5 秒看到结果"到"打字机效果":为什么要流式(任务 2.4)

前六节讲完,链路已经能创建会话、多轮追问、记得住上下文——但 2.3 版本的 `chat` 方法用的是这样一行:

```java
String reply = chatClient.prompt(new Prompt(springAiMessages)).call().content();
```

`.call()` 是**同步阻塞调用**:服务端要等模型把这一整段回复(可能几百字)**完整生成完**,才把整段文字一次性返回给前端。这几秒到十几秒里,用户只能盯着一个转圈的 loading——而 ChatGPT、豆包这些产品你用的时候,文字是**一个字一个字往外蹦**的,你几乎感觉不到等待。**内容生成的总耗时其实没有变化,流式解决的是"用户能不能实时看到进度"这件事。**

### SSE vs WebSocket vs 轮询,先分清楚为什么选它

| 方案 | 方向 | 协议 | 特点 |
|------|------|------|------|
| **轮询(polling)** | 单向(客户端主动问) | 普通 HTTP | 客户端每隔几百毫秒发一次请求"有新内容吗",简单粗暴,延迟高、浪费请求 |
| **WebSocket** | 双向 | 独立协议(`ws://`),握手时从 HTTP 升级 | 全双工,客户端服务端都能随时发消息,适合聊天室、协作编辑这种双向高频场景,但握手复杂、服务端要维护连接状态 |
| **SSE(Server-Sent Events)** | 单向(只能服务端推给客户端) | 就是普通 HTTP,`Content-Type: text/event-stream` | 建立一次连接,服务端持续往外写数据,客户端只能收不能通过这条连接发 |

**这次场景为什么选 SSE 不选 WebSocket**:AI 对话在这个接口里的通信模式是"一问一答",用户发一次 `chat` 请求,服务端流式吐出这一轮的完整回复,吐完这次交互就结束了——**天然是单向的**。WebSocket 的双向能力在这里用不上,反而要多维护一层协议升级和连接生命周期管理。SSE 建立在普通 HTTP 之上,复用你已经很熟悉的一切(拦截器、`@RequestBody` 校验……)。

**SSE 还有一个隐藏优点**:它是纯文本协议,断线后浏览器的 `EventSource` API 会自动重连(这次是项目自己写调用测试,重连了解即可)。

---

## 八、`SseEmitter` 背后的机制:为什么它不会拖垮线程池

这是任务 2.4 被标"核心难点"的原因,值得多花篇幅讲清楚原理,不然代码背下来也不知道为什么这么写。

### 一个普通 Controller 方法为什么没法"边算边发"

正常情况下,一个 `@RestController` 方法是这样运作的:Tomcat 分配一个工作线程处理这个请求 → 你的方法执行 → `return` 一个对象 → Spring 把它序列化成 JSON 写进响应 → **这个工作线程才被释放**,可以去处理别的请求。整个过程中,这个线程从头到尾都被这一个请求占着。

如果直接在这个模型上硬做"流式",意味着这个工作线程要一直卡在这个方法里,每生成一小段文字就手动往响应流里写一点——**这个线程会被占用好几秒甚至更久,期间完全没法处理别的请求**。Tomcat 的工作线程数是有限的(默认 200 个左右),如果同时有几十个用户在流式对话,线程池很快就会被占满,新请求全部排队甚至超时。

### `SseEmitter` 解决的正是这个问题:Servlet 3 异步机制

`SseEmitter` 的原理是:**Controller 方法立刻 `return` 一个 `SseEmitter` 对象,Tomcat 工作线程随即被释放去处理别的请求**;真正往响应里写数据这件事,交给**另一个线程**(Spring AI 内部的响应式调度线程)在后台异步完成,写一点发一点,直到调用 `emitter.complete()` 才真正关闭这次 HTTP 响应。

这背后依赖的是 Servlet 3.0 引入的**异步请求处理(Async Servlet)**能力:一个请求可以先被 Tomcat "挂起"(`SseEmitter` 内部帮你调用了 `request.startAsync()`),工作线程归还给线程池;等异步任务真正有数据要写的时候,再通过 `AsyncContext` 把数据写回这个还没关闭的连接。**"不占用 Tomcat 工作线程死等"是 `SseEmitter` 相对于"手写阻塞式流式输出"的核心优势**,也是一个常见的面试考点。

### 响应式流的背压(了解级)

Spring AI 的 `.stream()` 返回的是一个 `Flux<String>`(Reactor 库的响应式流类型)。响应式流有一个叫"背压(backpressure)"的概念:如果生产数据的速度(模型吐字的速度)比消费数据的速度(网络往客户端发送的速度)快,消费方可以"告诉"生产方"慢一点,我处理不过来了",避免内存里堆积过多还没处理的数据。**这次不需要手动处理背压**(Spring AI 和 Reactor 内部已经处理好了),知道这个词是干什么的就够。

---

## 九、项目现在的流式 `chat` 方法逐段拆解

先约定好这次的 SSE 事件格式(不是标准 SSE 用 `event:` 字段区分类型,而是把 `type` 塞进 `data` 的 JSON 里):

```
data: {"type":"delta","content":"嗯,"}

data: {"type":"delta","content":"那么它能保证原子性吗?"}

data: {"type":"done","messageId":88}
```

`type=delta` 是增量文本片段,`type=done` 标记这一轮结束、带上落库后的消息 id,`type=error` 携带错误信息。

**为什么错误也要通过一个 SSE 事件下发,而不是让接口返回 HTTP 500?**——因为这条 HTTP 连接在生成 `SseEmitter` 那一刻起,响应头(包括状态码)就已经确定是 `200 text/event-stream` 了,**SSE 协议本身不支持流已经开始之后再改状态码**。所以但凡在流式过程中出了错,唯一能告诉客户端"出错了"的办法就是**再发一个 `data` 事件,内容里注明这是个错误**,客户端拿到之后自己判断该怎么处理。这是 SSE 协议本身的固有约束。

现在看 `InterviewServiceImpl.chat` 的完整实现:

```java
@Override
public SseEmitter chat(Long sessionId, ChatMessageDTO dto) {
    InterviewSession session = mustFindSession(sessionId);
    if(session.getStatus() !=0){
        throw new BusinessException(ErrorCode.CONFLICT,"会话已结束，无法继续对话");
    }

    String lockKey = LOCK_PREFIX+sessionId;
    Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey,"1",LOCK_TTL_SECONDS,TimeUnit.SECONDS);
    if(!Boolean.TRUE.equals(locked)){
        throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS,"上一轮对话还在处理中，请稍后");
    }

    List<InterviewMessage> history = contextManager.load(sessionId);
    InterviewMessage userMsg = buildMessage(sessionId,"user",dto.getContent());
    interviewMessageMapper.insert(userMsg);
    contextManager.append(sessionId,userMsg);
    List<Message> springAiMessages = toSpringAiMessages(history);
    springAiMessages.add(new UserMessage(dto.getContent()));

    SseEmitter emitter = new SseEmitter(0L);   // 0L = 不超时,长回答不会被提前掐断
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
        subscription.dispose();
        stringRedisTemplate.delete(lockKey);
    });
    emitter.onError(throwable -> {
        subscription.dispose();
        stringRedisTemplate.delete(lockKey);
    });

    return emitter;
}
```

### 前半段还是同步的,只有最后一步"异步"了起来

**为什么方法一开始的部分(校验会话、抢锁、读历史、落用户消息)全是同步代码,到 `chatClient.prompt(...).stream()...subscribe(...)` 这一行才"异步"起来?** 这些前置步骤本身都很快,没有必要也异步化。**真正耗时的只有"等模型把回复生成完"这一步**,所以只有这一步需要用响应式的方式处理。`subscribe(...)` 这一行本身**不会阻塞**——它把"收到一段文字该怎么办、遇到错误该怎么办、正常结束该怎么办"这三个逻辑注册好之后,方法立刻继续往下走(注册 `onTimeout`/`onError`,然后 `return emitter`),不会卡在这里等模型说完。真正"消费"模型输出这件事,是在**另一个线程**上,随着模型不断吐出新内容,持续触发 `subscribe` 里的第一个回调。

### `.subscribe(onNext, onError, onComplete)` 三个回调分别对应什么

- **第一个(`onNext`,`delta -> sendEvent(...)`)**:**每收到一小段增量文本就触发一次**,直接包成 `type=delta` 的事件发给客户端,同时这段文字也通过 `doOnNext(fullReply::append)` 被记录进 `fullReply`——**流式发给客户端和攒完整内容落库,是两件并行发生的事,不冲突**。
- **第二个(`onError`)**:这一轮 AI 调用本身失败了(网络问题、API 报错),对应 `onStreamError`。
- **第三个(`onComplete`)**:模型正常把这一轮回复说完了,对应 `onStreamComplete`——**这时候 `fullReply` 里已经攒好了完整的回复文本**,在这里把 assistant 消息落库、追加进上下文、发 `done` 事件、真正关闭连接(`emitter.complete()`)。

### 为什么 `onTimeout`/`onError` 里要调用 `subscription.dispose()`

想象客户端中途断开连接(用户关掉了浏览器标签页,或者自测时按了 Ctrl+C)——这条 HTTP 连接没了,但 `chatClient.prompt(...).stream()` 这个响应式流**默认不会自动知道"客户端已经不在了",它会继续跑,继续从模型那里消费 token、生成内容**,只是这些内容再也发不出去了(`emitter.send()` 会抛 `IOException`,被 `sendEvent` 悄悄吃掉)。**这是白白浪费模型调用的成本**——`Disposable`(`subscribe(...)` 的返回值)就是用来手动"取消订阅"的句柄,调用它的 `dispose()`,会让这条响应式流停止继续拉取模型的输出,及时止损。`SseEmitter` 提供的 `onTimeout`/`onError` 回调,正是**给你一个"连接生命周期结束"的信号**,是清理资源(取消订阅、释放锁)的正确挂载点。

### 为什么锁的释放要在好几个地方各写一遍

`onStreamComplete`、`onStreamError`、`onTimeout`、`onError` 里都有 `stringRedisTemplate.delete(lockKey)`——这几个回调对应的是**互斥的不同结束路径**(正常完成、AI 调用出错、SSE 超时、SSE 连接异常),任意一条路径走完,这次对话都算"结束"了,锁都应该被释放。`DEL` 命令本身是幂等的,就算极端情况下两条路径都触发、锁被删了两次,也不会有副作用——**这里宁可"可能重复释放"也不能有任何一条路径"忘了释放"**(那样锁会一直占到 60 秒 TTL 到期才自动解开,期间这个会话没法再对话)。

### Controller 为什么不再包一层 `Result<...>`

```java
@PostMapping("/{id}/chat")
public SseEmitter chat(@PathVariable Long id, @Valid @RequestBody ChatMessageDTO dto){
    return interviewService.chat(id,dto);
}
```

原来返回 `Result<ChatReplyVO>` 的写法不再适用了——**SSE 响应不走"统一返回体包一层 `{code,message,data}`"这套约定**,`SseEmitter` 是 Spring MVC 专门为流式响应设计的返回类型,框架看到方法返回它,就知道要走异步 SSE 这条路径,不会再尝试用 Jackson 把它序列化成 JSON。

### 一个还悬着的坑:拦截器会不会被 SSE 的异步分发触发两次

`SseEmitter` 底层依赖 Servlet 3 的异步请求处理——一个请求会先经历"主线程处理到 `return emitter` 为止"这一次分发(`DispatcherType.REQUEST`),之后随着 `emitter.complete()`/`completeWithError()` 被调用,Servlet 容器会**再触发一次分发**,把这次异步处理的"收尾"工作走一遍(`DispatcherType.ASYNC`)。**如果 Spring MVC 让拦截器在每一次分发里都执行一遍**,`JwtInterceptor.preHandle` 就可能在同一个请求上被调用**两次**——第二次这种"异步收尾分发"压根不需要再校验一次登录。

**目前项目里的 `JwtInterceptor` 还没有针对 `DispatcherType.ASYNC` 做任何特殊处理**,这个现象是否真实发生、要不要处理,还没验证。验证方式:在 `preHandle` 开头加一行日志打印 `request.getDispatcherType()`,跑一次 SSE 请求,看控制台是不是打出了两次日志、第二次的 `DispatcherType` 是不是 `ASYNC`。如果确认存在,处理方式是在 `preHandle` 一开始放行异步分发:

```java
if (request.getDispatcherType() == DispatcherType.ASYNC) {
    return true;   // 登录态已经在第一次分发里校验过了
}
```

**这是当前 AI 模块里唯一一个"文档提到了、但代码里还没验证/处理"的点**,留在这里作为待办,不是这篇文档漏讲。

---

## 十、这次全链路踩过的真坑合集

下面这些问题是这次任务代码在 review 时真实发现过的,不是编出来的反面教材——**你自己写代码踩过的坑,比任何人讲的"最佳实践"都记得牢**。

### 坑 1:DTO 字段名打错字,接口直接不可用

最初 `CreateInterviewDTO` 的字段被打成了 `diirection`(多了一个 i)。**这个错误不会报编译错误,项目照样能启动**,问题出在运行时:Jackson 处理 `@RequestBody` 时靠"字段名"去匹配 JSON 里的 key,文档约定的请求体是 `{"direction": ...}`,字段名对不上,反序列化结果永远是 `null`,`@NotBlank` 校验必然失败——**从报错信息上看完全像是"调用方传参有问题",但真正的病灶在服务端的字段名打错了**。字段名这种和外部契约强绑定的地方,写完要对照接口文档逐字核对。

### 坑 2:自己注入自己,项目启动不起来

`InterviewServiceImpl` 构造器里多出过 `private final InterviewService interviewService;`——自己就是这个接口唯一的实现类,相当于说"造我自己之前请先给我一个我自己",这是一个逻辑死结。**构造器注入的循环依赖无解**(不像字段/setter 注入能靠"先给一个早期引用"绕过),Spring 启动时直接抛 `BeanCurrentlyInCreationException`。这类坑通常是手滑把某个字段的类型敲成了当前类自己实现的接口,而且**从来没被真正用到**——一个从没在方法体里出现过的字段,本身就是排查信号。

### 坑 3:两个 Redis 模板拿混了,数据存成了"乱码"

最初同时注入了 `StringRedisTemplate` 和 `RedisTemplate<Object, Object>`,`append` 实际调用的是后者。项目没有自定义 `RedisConfig`,这个 `RedisTemplate<Object, Object>` 默认走 **JDK 原生序列化**,不是存文本用的 `StringRedisSerializer`。代码里手动把消息转成 JSON 字符串,又被这个模板的 JDK 序列化器包了一层——**存进 Redis 的不再是干净的 JSON 文本,而是一段带 Java 序列化协议头部字节的二进制数据**,`redis-cli LRANGE` 看到的是乱码,后续用 `StringRedisTemplate` 读会直接解析失败。**统一用 `StringRedisTemplate`** 之后问题消失——这是项目从任务 1.8 起建立的约定,同一类数据的存取方式要保持一致。

### 坑 4:接口路径和文档对不上

`@RequestMapping("/api/interview")` 少了一个 `s`,和项目里其他资源路径(`/api/questions`、`/api/categories`)的复数形式约定不一致。接口路径这种纯字符串约定没有编译器兜底,只能对照文档手动核对,自测时最好直接照抄文档里的 curl 命令。

### 坑 5:MyBatis XML 语句 id 写成了类名,方法一调就绑定不上

`InterviewMessageMapper.selectBySessionId` 对应的 XML 最初把 `<select>` 的 `id` 属性写成了结果类名(`id="com.zhimian.model.entity.InterviewMessage"`),`resultType` 反而漏掉了。**MyBatis 靠 `id` 属性去匹配接口方法名**,这条语句既没有名字能被 `selectBySessionId` 找到,也没告诉 MyBatis 结果怎么映射。**这类错误编译期完全查不出来**——XML 是运行时才被解析绑定的,真正暴露问题是在 `InterviewContextManager.load()` 第一次真的需要"Redis 未命中、从 MySQL 重建"的时候,抛 `BindingException: Invalid bound statement (not found)`。**平时如果一直是 Redis 缓存命中的路径,这个坑会一直潜伏着不发作**——这也是为什么自测清单里专门要有"手动删掉 Redis key 再测一次"这一步,不这么测,这类"冷路径"上的 bug 很容易被漏掉。

### 坑 6:重构挪走了逻辑,常量和字段却忘了一起挪走

`append`/`serialize` 这两个方法从 `InterviewServiceImpl` 搬进 `InterviewContextManager` 之后,`InterviewServiceImpl` 里原来配合它们用的 `CTX_KEY_PREFIX`/`CTX_TTL_HOURS`/`ObjectMapper objectMapper` 没有跟着删掉。这几行不会导致编译失败或运行时错误,纯粹是"死代码",但会误导后来者以为这个类还在自己管理序列化。**重构挪走一段逻辑的时候,要顺手检查这段逻辑专属的字段、常量、import 是不是也该跟着一起清理。**

### 坑 7(任务 2.4 新增,待你自己验证):拦截器可能被异步分发二次触发

见第九节最后一部分——`JwtInterceptor` 目前没有处理 `DispatcherType.ASYNC`,不确定这个二次触发现象在当前 Spring Boot 版本下是否真实存在,需要加日志验证后再决定要不要处理。

### 坑 8(顺手提一句,不是这次任务的重点):`ChatReplyVO` 已经不再是 `chat` 接口的返回类型了

`chat` 方法的返回值从 `ChatReplyVO`(非流式的完整回复)换成了 `SseEmitter` 之后,`InterviewController`/`InterviewService`/`InterviewServiceImpl` 里原来 `import com.zhimian.model.vo.ChatReplyVO;` 这一行现在都用不上了,是切换到流式之后遗留的死 import——和坑 6 是同一类问题(重构后遗留痕迹没清理),不影响功能,但下次顺手清一下。

---

## 十一、自测清单(任务 2.1~2.4 合并版)

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"你的账号","password":"你的密码"}' | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
```

### ① 创建会话(2.1/2.2)

```bash
curl -s -X POST http://localhost:8080/api/interviews \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"direction":"java_concurrency"}'
```

期望返回 `code:0`,`data.openingMessage` 是一段自然语言;查库确认两条消息落地(`SELECT * FROM interview_message ORDER BY id DESC LIMIT 2;`);查 Redis 确认存的是可读 JSON,不是乱码(`redis-cli LRANGE zhimian:interview:ctx:<会话id> 0 -1`)。

### ② 多轮对话与上下文裁剪(2.3)

- **连续对话验证记忆**:连续调几次 `chat`,最后一轮故意问"你还记得我刚才说过什么吗",回复应该能提到早期真实说过的内容。
- **调小 `TOKEN_BUDGET`(比如改成 `50`)验证裁剪方向**:重复上一步,盯着控制台日志确认裁剪确实发生,再问"我叫什么名字"——期望 AI **答不上来**(第一轮已被裁掉),但**面试官人设不能丢**(system prompt 始终保留)。**测完记得改回去**。
- **手动清空 Redis 上下文,验证从 MySQL 重建**:`redis-cli DEL zhimian:interview:ctx:<id>` 后再发一次 `chat`,期望 AI 依然记得之前聊过的内容,重建后再查一次 Redis 应该又能看到完整历史。**这一步是坑 5 那个 XML 绑定错误唯一会暴露出来的路径。**
- **并发锁验证**:同一个 sessionId 并发发两次 `chat`,期望一次成功、一次返回 `code=42900`。

### ③ 流式输出(2.4)

```bash
SESSION_ID=<上面创建会话返回的 id>
```

- **用 `curl -N` 观察逐段吐出的效果**(`-N` 关掉 curl 自己的输出缓冲,不然感觉不到"流式"):
  ```bash
  curl -N -X POST http://localhost:8080/api/interviews/$SESSION_ID/chat \
    -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
    -d '{"content":"缓存穿透具体是指什么场景?"}'
  ```
  期望看到一连串 `data: {"type":"delta",...}` **陆续**打印出来,最后一行是 `data: {"type":"done","messageId":...}`。
- **验证消息落库完整**:查最新一条 `assistant` 消息,`content` 应该和终端看到的所有 `delta` 拼起来的内容一致,不是被截断的片段。
- **中途断开,验证服务端正常清理**:发一次请求,看到吐出几个 `delta` 后手动 Ctrl+C,检查控制台是否有 `onError`/`onTimeout` 日志,再 `redis-cli EXISTS zhimian:interview:lock:<id>` 应该是 `0`(锁被正常释放,不是死等 60 秒 TTL)。
- **验证 AI 报错走 `error` 事件**:临时改错 `api-key`,重启后再发一次请求,期望看到 `data: {"type":"error",...}`,而不是连接裸断或服务端裸抛堆栈。**测完记得把 `api-key` 改回来**。

---

## 十二、后面怎么继续(阶段二剩下的任务)

到任务 2.4 为止,这场"模拟面试"已经能**创建会话、生成开场白、多轮追问且记得住上下文、并且是打字机式的实时输出**——核心对话体验已经完整。接下来:

| 任务 | 要解决的问题 | 和已完成部分的关系 |
|------|------|------|
| **2.5 消息持久化与会话查询** | "我的会话列表"怎么查、越权访问怎么防 | 复用现在的 `InterviewMessage`/`InterviewSession` 表,重点补上"只能看自己的会话"这个安全校验——`mustFindSession` 目前只查了"存不存在",还没查"是不是当前用户自己的" |
| **2.6 结束面试 + 结构化评价报告** | 面试结束后,怎么让模型输出一份"打分 + 亮点 + 薄弱点"的结构化报告 | "system prompt 定角色"会再用一次,只是角色从"面试官"换成"评委",还要学 Spring AI 怎么强制模型输出合法 JSON |
| **2.7 AI 调用的健壮性** | 频率限制、超时、失败重试、模型降级 | 现在的 `MAX_ACTIVE_SESSIONS` 限流、互斥锁、`AI_SERVICE_ERROR` 错误处理只是简化版,2.7 会系统性补全 |

**另外一个现实中的断层,不属于后端任务范围,但要知道它存在**:前端的 `chatInterview`(`frontend/src/api/interview.ts`)目前还是按"发一个 POST、等一个 JSON `ChatReplyVO`"写的,而后端 `chat` 接口现在返回的是 `text/event-stream` 的 SSE 流,两边协议已经不匹配了——前端要跟着后端这次的切换换成用 `EventSource`(或者 `fetch` + `ReadableStream`)去消费 `delta`/`done`/`error` 三种事件,这块等你确认要推进的时候可以再找我对接。

---

## 十三、面试自测(合上代码,自己能不能讲清楚)

1. 为什么大模型的多轮对话需要每次都把历史消息重新发一遍?它和普通 Web 接口"服务端保存 session"有什么本质区别?
2. system prompt 和 user 消息分别应该放什么内容?"提出第一个问题"这句话为什么不能写进 system prompt?
3. 为什么可以用"OpenAI 协议的客户端"去调用硅基流动的 DeepSeek 模型?这说明了什么行业现象?
4. "插库 + 调 AI 生成开场白"这两步为什么不能包在同一个事务里?如果被问到"那 AI 调用失败了数据怎么办",你会怎么回答?
5. 面试消息为什么要同时存 MySQL 和 Redis?分别承担什么职责?
6. Token 预算裁剪为什么要"从最新往回"数,而不是"从最旧往前"数?写反了会有什么体验上的后果?为什么这种错误不会被编译器或者启动检查发现?
7. `InterviewContextManager` 里"Redis 未命中就查 MySQL、查到之后要回填 Redis"这套逻辑,你在这个项目里已经是第几次实现同样的模式了?分别是哪几个场景?
8. `interview:lock:{sessionId}` 这把锁和任务 1.9 详情缓存的击穿防护锁,思路上有什么共同点?锁的粒度分别是什么?
9. `SseEmitter` 为什么能做到"不占用 Tomcat 工作线程死等"?这背后依赖的是什么机制?
10. 如果客户端在流式响应进行到一半时断开连接,不调用 `subscription.dispose()` 会有什么后果?
11. SSE 报错为什么不能通过修改 HTTP 状态码来告诉客户端,只能通过一个 `data` 事件?这说明了 SSE 协议本身的什么约束?
12. `chat` 接口用了响应式的 `Flux`(通过 `.stream()`),但项目其他接口都是同步阻塞的 Spring MVC 写法,两种编程模型混用会不会有问题?你怎么理解这种"混合"架构?
13. `JwtInterceptor` 为什么可能要专门处理 `DispatcherType.ASYNC` 这种分发?如果不处理,最坏情况下会发生什么?你会怎么验证这个现象在你的项目里是否真实存在?
