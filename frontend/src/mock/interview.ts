import type { InterviewStage } from '../types/interview'

export const interviewStages: InterviewStage[] = [
  {
    id: 1,
    name: '自我介绍',
    question: '请用两分钟做一个自我介绍,重点讲讲你最有技术含量的一个项目,以及你在其中解决的最难的问题。',
    hint: '建议结构:一句话定位 → 项目背景 → 你的具体职责 → 一个有冲突感的技术难点 → 量化的结果。',
    report: [
      { name: '表达清晰度', score: 86, comment: '结构完整,重点突出,略有语速偏快的倾向' },
      { name: '重点提炼', score: 78, comment: '项目亮点明确,但量化结果还可以更具体' },
      { name: '时间控制', score: 92, comment: '两分钟内完成,节奏把控到位' },
      { name: '感染力', score: 74, comment: '陈述偏平铺,可以在难点处制造更多张力' },
    ],
  },
  {
    id: 2,
    name: '并发编程',
    question: '你的项目里用了线程池做浏览量异步回写,说说 ThreadPoolExecutor 的核心参数,以及队列打满之后会发生什么。',
    hint: '按"参数 → 运行机制 → 拒绝策略的取舍"展开,最好能结合你自己项目里的参数选择讲。',
    report: [
      { name: '知识深度', score: 82, comment: '七个参数完整,拒绝策略讲清了 CallerRunsPolicy 的背压效果' },
      { name: '逻辑严密性', score: 88, comment: '运行机制推演正确,队列和最大线程数的触发顺序无误' },
      { name: '结合实践', score: 90, comment: '能落到自己项目的真实参数,这是加分项' },
      { name: '表达清晰度', score: 80, comment: '中段有一处术语混用(核心线程/活跃线程)' },
    ],
  },
  {
    id: 3,
    name: 'Redis 缓存',
    question: '缓存穿透、雪崩、击穿分别是什么?你的项目里是怎么处理的?追问:互斥锁方案里,释放锁时要注意什么?',
    hint: '三个问题先给一句话定义再给解法;追问考察的是"误删别人的锁"和 Lua 原子释放。',
    report: [
      { name: '知识深度', score: 91, comment: '三件套定义准确,追问的锁归属校验也答上来了' },
      { name: '逻辑严密性', score: 85, comment: '击穿和穿透的边界区分清晰' },
      { name: '结合实践', score: 88, comment: '空值缓存 TTL 和正常缓存 TTL 的差异讲得具体' },
      { name: '表达清晰度', score: 84, comment: '答案结构好,先定义后方案' },
    ],
  },
  {
    id: 4,
    name: '系统设计提问',
    question: '设计一个支撑十万 QPS 的答题排行榜服务:数据结构选型、读写路径、以及 Redis 挂掉之后怎么恢复。',
    hint: '考察 ZSet 的复杂度分析、ZINCRBY 原子性、流水表作为事实来源的兜底思路。',
    report: [
      { name: '架构完整性', score: 79, comment: '读写路径清晰,但没主动提到热点 key 的倾斜问题' },
      { name: '知识深度', score: 84, comment: '跳表复杂度和 ZINCRBY 原子性分析到位' },
      { name: '容灾思路', score: 76, comment: '答出了流水表重建,恢复期间的降级策略可以再展开' },
      { name: '表达清晰度', score: 82, comment: '先画整体再讲细节,顺序合理' },
    ],
  },
  {
    id: 5,
    name: '反问环节',
    question: '面试的最后,你有什么想问面试官的问题吗?',
    hint: '避免只问薪资福利;问团队的技术挑战、代码评审文化、新人的成长路径,都是有信息量的反问。',
    report: [
      { name: '问题质量', score: 80, comment: '问了团队技术栈演进,是有信息量的问题' },
      { name: '沟通姿态', score: 88, comment: '自然、不卑不亢' },
      { name: '收尾完整度', score: 85, comment: '有礼貌地收束了整场对话' },
      { name: '主动性', score: 72, comment: '可以再追问一层,展示你对答案的思考' },
    ],
  },
]
