package com.cycling.rssradar.core.data.ai

/**
 * AI 智能功能的唯一注册表：35 项功能各占一个枚举项。
 *
 * 加一项新功能的成本被压到最低——在这里加一行枚举常量（拿到 dbValue），
 * 再在 [AiPrompts] 加一个 prompt、在 [AiFeatureRunner] 加一个分支。
 * 产物落 `ai_artifacts` 的 (subjectKind, subjectId, kind) 三元组，**不需要 schema 迁移**，
 * 这是本表刻意不做外键、改由每日任务清理孤儿换来的（见 AiSchema.kt 注释）。
 *
 * 三项不变量：
 * 1. `dbValue` 一旦发布**永不复用、永不重排**——老版本写进 ai_artifacts 的 kind 靠它解释。
 * 2. `trigger` 决定任务队列的行为，不是文档装饰：[AiTaskPlanner] 按它分流。
 * 3. `defaultEnabled` 按**触发方式**划分，不是一律保守关：
 *    - MANUAL / REALTIME / ON_DEMAND（用户点一下才跑）→ 默认**开**。
 *      不点就不花钱，默认关掉只会让用户以为功能没做，白等一次「去设置里打开」。
 *    - BATCH（后台自动跑）→ 默认**关**。
 *      它会在每日任务里自动消耗额度，属于"要不要花钱"的决策，交给用户显式开启。
 *    这条划分让「打开就能用」和「不会静默扣费」同时成立。
 */
enum class AiFeature(
    /** 落库与任务队列使用的稳定整数标识，从 1 起连续编号。 */
    val dbValue: Int,
    /** 功能归属的三大分组，设置页据此分节。 */
    val category: AiCategory,
    /** 产物挂在哪一类主体上，决定 subjectKind 与清理归属。 */
    val scope: AiScope,
    /** 触发方式。 */
    val trigger: AiTrigger,
    /** 首次安装后的默认开关状态。 */
    val defaultEnabled: Boolean,
    /** 是否需要调用大模型。false 的项是纯本地能力（用量看板、任务队列、提示词管理）。 */
    val needsLlm: Boolean,
    /** 功能名，设置页与入口按钮直接用。 */
    val label: String,
    /** 一句话说明"它做什么"。 */
    val summary: String,
    /** 交互入口在哪，用户怎么触发。 */
    val entry: String,
    /** 结果展示在哪里、以什么形态。 */
    val presentation: String,
) {
    // ── 内容处理类（15 项） ────────────────────────────────────────────────

    SUMMARY(
        dbValue = 1,
        category = AiCategory.CONTENT,
        scope = AiScope.ARTICLE,
        trigger = AiTrigger.ON_DEMAND,
        defaultEnabled = true,
        needsLlm = true,
        label = "AI 摘要",
        summary = "基于正文生成一段结论 + 2~4 条要点，支持为单个订阅源单独配置摘要提示词。",
        entry = "阅读页顶栏 Sparkles 按钮 / 正文区摘要卡；订阅源操作页可配该源专属提示词。",
        presentation = "阅读页标题下常驻摘要卡，生成后持久保存，刷新永不覆盖。",
    ),
    TRANSLATE(
        dbValue = 2,
        category = AiCategory.CONTENT,
        scope = AiScope.ARTICLE,
        trigger = AiTrigger.MANUAL,
        defaultEnabled = true,
        needsLlm = true,
        label = "文章翻译",
        summary = "把外文正文整体译为简体中文，保留 HTML 结构，可随时切回原文。",
        entry = "阅读页顶栏翻译开关；重译走底部操作条。",
        presentation = "替换式译文，会话内 LRU 缓存，不落库。",
    ),
    CLASSIFY(
        dbValue = 3,
        category = AiCategory.CONTENT,
        scope = AiScope.ARTICLE,
        trigger = AiTrigger.BATCH,
        defaultEnabled = false,
        needsLlm = true,
        label = "智能分类",
        summary = "判定文章所属话题（科技/财经/开发/设计等），用于话题星系与信息聚合。",
        entry = "批处理：每日任务扫描未分类的新文章；也可在阅读页手动重判。",
        presentation = "阅读页摘要卡下方的话题 chip；话题星系页按此聚合。",
    ),
    TAGS(
        dbValue = 4,
        category = AiCategory.CONTENT,
        scope = AiScope.ARTICLE,
        trigger = AiTrigger.BATCH,
        defaultEnabled = false,
        needsLlm = true,
        label = "自动标签",
        summary = "抽取 3~6 个细粒度主题标签，比话题分类更具体、可跨话题。",
        entry = "批处理：每日任务；阅读页摘要卡下的「重新生成标签」。",
        presentation = "阅读页标签 chip 组，长按可复制到剪贴板。",
    ),
    SENTIMENT(
        dbValue = 5,
        category = AiCategory.CONTENT,
        scope = AiScope.ARTICLE,
        trigger = AiTrigger.BATCH,
        defaultEnabled = false,
        needsLlm = true,
        label = "情感分析",
        summary = "判断报道情绪倾向（正面/中性/负面）与强度，帮助识别带节奏的内容。",
        entry = "批处理：每日任务；也可在阅读页手动分析。",
        presentation = "列表卡片右侧的情绪小色条 + 阅读页情绪标签（含强度值）。",
    ),
    KEYWORDS(
        dbValue = 6,
        category = AiCategory.CONTENT,
        scope = AiScope.ARTICLE,
        trigger = AiTrigger.BATCH,
        defaultEnabled = false,
        needsLlm = true,
        label = "关键词提取",
        summary = "抽取 5~8 个核心关键词（专有名词优先），与画像词表同源可复用。",
        entry = "批处理：每日任务；阅读页摘要卡下的关键词行。",
        presentation = "阅读页关键词 chip 行；点击即作为搜索词跳转搜索页。",
    ),
    OPINION(
        dbValue = 7,
        category = AiCategory.CONTENT,
        scope = AiScope.ARTICLE,
        trigger = AiTrigger.MANUAL,
        defaultEnabled = true,
        needsLlm = true,
        label = "观点总结",
        summary = "提炼文章的核心论点与支撑论据，区分「作者观点」与「引用事实」。",
        entry = "阅读页底部操作条「观点」按钮。",
        presentation = "底部弹出的观点卡片：论点列表 + 依据摘要，可折叠。",
    ),
    QA(
        dbValue = 8,
        category = AiCategory.CONTENT,
        scope = AiScope.ARTICLE,
        trigger = AiTrigger.REALTIME,
        defaultEnabled = true,
        needsLlm = true,
        label = "文章问答与深度解析",
        summary = "针对当前文章提问，模型只依据正文作答，无依据时明说「文中未提及」。",
        entry = "阅读页底部「问这篇文章」输入框，支持一键追问（解释/影响/时间线）。",
        presentation = "底部问答抽屉：多轮对话，每条回答带「依据段落」引用，不持久化。",
    ),
    FULLTEXT(
        dbValue = 9,
        category = AiCategory.CONTENT,
        scope = AiScope.ARTICLE,
        trigger = AiTrigger.MANUAL,
        defaultEnabled = true,
        needsLlm = true,
        label = "自动提取全文",
        summary = "规则提取器失败时，用模型从原始 HTML 中还原正文，弥补 ADR-0012 的漏抓。",
        entry = "阅读页正文不完整提示卡上的「用 AI 提取」。",
        presentation = "成功则替换正文并清除不完整标记，失败如实提示不写脏数据。",
    ),
    DEDUPE(
        dbValue = 10,
        category = AiCategory.CONTENT,
        scope = AiScope.ARTICLE,
        trigger = AiTrigger.BATCH,
        defaultEnabled = false,
        needsLlm = true,
        label = "智能去重",
        summary = "识别同一事件的多源转载，给出主篇与相似组，避免重复读同一条新闻。",
        entry = "批处理：每日任务；AI 功能总览页展开后可「立即运行」。",
        presentation = "「AI 结果」页按功能筛选查看，文章级产物可跳原文；专属列表入口待做。",
    ),
    QUALITY(
        dbValue = 11,
        category = AiCategory.CONTENT,
        scope = AiScope.ARTICLE,
        trigger = AiTrigger.BATCH,
        defaultEnabled = false,
        needsLlm = true,
        label = "文章质量分析",
        summary = "从信息密度、原创性、标题党程度、证据充分性四个维度给出 0~100 评分。",
        entry = "批处理：每日任务；阅读页「质量」按钮可单篇重算。",
        presentation = "阅读页质量卡：总分 + 四维条形图 + 一句短板说明。",
    ),
    NOISE(
        dbValue = 12,
        category = AiCategory.CONTENT,
        scope = AiScope.ARTICLE,
        trigger = AiTrigger.BATCH,
        defaultEnabled = false,
        needsLlm = true,
        label = "智能降噪与内容评分",
        summary = "识别广告、水文、AI 批量生成与情绪煽动，输出降噪建议与信息价值分。",
        entry = "批处理：每日任务；阅读页「降噪」按钮。",
        presentation = "列表卡片右上角的降噪角标（噪声高则灰显）；阅读页降噪说明卡。",
    ),
    OUTLINE(
        dbValue = 13,
        category = AiCategory.CONTENT,
        scope = AiScope.ARTICLE,
        trigger = AiTrigger.MANUAL,
        defaultEnabled = true,
        needsLlm = true,
        label = "长文精读结构化",
        summary = "把长文拆成带小标题的层级大纲，可按节跳转，附一句全篇主旨。",
        entry = "阅读页底部「大纲」按钮；正文字数超阈值时主动提示。",
        presentation = "底部大纲抽屉：层级列表，点击滚动到对应段落。",
    ),
    CREDIBILITY(
        dbValue = 14,
        category = AiCategory.CONTENT,
        scope = AiScope.ARTICLE,
        trigger = AiTrigger.MANUAL,
        defaultEnabled = true,
        needsLlm = true,
        label = "信源可信度评估",
        summary = "评估信源类型、证据链与断言强度，给出可信度档位与需要留意的信号。",
        entry = "阅读页底部「可信度」按钮。",
        presentation = "可信度卡：档位 label + 依据列举 + 「存疑点」提示，绝不编造事实核查结论。",
    ),
    GLOSSARY(
        dbValue = 15,
        category = AiCategory.CONTENT,
        scope = AiScope.ARTICLE,
        trigger = AiTrigger.REALTIME,
        defaultEnabled = true,
        needsLlm = true,
        label = "划词解释",
        summary = "选中正文里的术语、缩写或专有名词，给出贴合上下文的一句话解释。",
        entry = "阅读页长按选中文本后的「解释」菜单项。",
        presentation = "底部释义条：一句解释 + 在本文中的含义，不持久化。",
    ),

    // ── 推荐发现类（10 项） ────────────────────────────────────────────────

    PERSONAL_FEED(
        dbValue = 16,
        category = AiCategory.DISCOVERY,
        scope = AiScope.GLOBAL,
        trigger = AiTrigger.ON_DEMAND,
        defaultEnabled = true,
        needsLlm = false,
        label = "个性化内容推荐",
        summary = "推荐流底座：按新鲜度、源亲和度、内容相似度打分并打散（ADR-0013）。",
        entry = "信息流「推荐」标签页；设置页可关闭该 tab。",
        presentation = "推荐 tab 的文章流，带「减少此类」负反馈与撤销。",
    ),
    FEED_RECOMMEND(
        dbValue = 17,
        category = AiCategory.DISCOVERY,
        scope = AiScope.GLOBAL,
        trigger = AiTrigger.BATCH,
        defaultEnabled = false,
        needsLlm = true,
        label = "智能订阅源推荐",
        summary = "基于兴趣画像与已订阅源，推荐值得新增的 RSSHub 路由或站点。",
        entry = "批处理：每日任务；AI 功能总览页展开后可「立即运行」。",
        presentation = "「AI 结果」页按功能筛选查看；专属「猜你想订」入口待做。",
    ),
    DISCOVER(
        dbValue = 18,
        category = AiCategory.DISCOVERY,
        scope = AiScope.GLOBAL,
        trigger = AiTrigger.ON_DEMAND,
        defaultEnabled = false,
        needsLlm = true,
        label = "发现模式",
        summary = "一次给一批跨领域的陌生话题文章，专门用于探索而非收敛。",
        entry = "专属「发现」入口尚未实现；开启后待专属页接入。",
        presentation = "专属探索流尚未实现；有产物时会出现在「AI 结果」页。",
    ),
    TOPIC_GALAXY(
        dbValue = 19,
        category = AiCategory.DISCOVERY,
        scope = AiScope.GLOBAL,
        trigger = AiTrigger.ON_DEMAND,
        defaultEnabled = false,
        needsLlm = false,
        label = "话题星系浏览",
        summary = "把已分类文章聚成话题簇，按热度与亲疏排布，看清自己的阅读版图。",
        entry = "专属「话题星系」入口尚未实现；依赖智能分类产物。",
        presentation = "专属星系页尚未实现；有产物时会出现在「AI 结果」页。",
    ),
    BUBBLE_BREAK(
        dbValue = 20,
        category = AiCategory.DISCOVERY,
        scope = AiScope.GLOBAL,
        trigger = AiTrigger.BATCH,
        defaultEnabled = false,
        needsLlm = true,
        label = "信息茧房破壁",
        summary = "找出画像覆盖不到的盲区话题，主动推几篇对立视角或陌生领域的文章。",
        entry = "批处理：每日任务；AI 功能总览页展开后可「立即运行」。",
        presentation = "「AI 结果」页按功能筛选查看；信息流「换个视角」卡片待做。",
    ),
    RELATED(
        dbValue = 21,
        category = AiCategory.DISCOVERY,
        scope = AiScope.ARTICLE,
        trigger = AiTrigger.ON_DEMAND,
        defaultEnabled = false,
        needsLlm = false,
        label = "文章关联推荐",
        summary = "读某篇时给出内容相关的其他文章（跨源），复用推荐引擎的内容相似度。",
        entry = "阅读页底部「相关阅读」横滑条；纯本地相似度，不调模型、不耗额度。",
        presentation = "阅读页底部横滑卡片：标题 + 来源，点击跳转原文；无候选时不显示。",
    ),
    AGGREGATE(
        dbValue = 22,
        category = AiCategory.DISCOVERY,
        scope = AiScope.GLOBAL,
        trigger = AiTrigger.BATCH,
        defaultEnabled = false,
        needsLlm = true,
        label = "智能信息聚合",
        summary = "把同一话题下多篇文章合成一份综述，保留分歧而不是抹平。",
        entry = "批处理：每日任务；AI 功能总览页展开后可「立即运行」。",
        presentation = "「AI 结果」页按功能筛选查看；专属综述页待做。",
    ),
    INTEREST_RANK(
        dbValue = 23,
        category = AiCategory.DISCOVERY,
        scope = AiScope.GLOBAL,
        trigger = AiTrigger.BATCH,
        defaultEnabled = false,
        needsLlm = true,
        label = "个人兴趣排序",
        summary = "用模型给画像词表做可读化命名与归并，产出「你最关心什么」的排序。",
        entry = "批处理：每日任务；AI 功能总览页展开后可「立即运行」。",
        presentation = "「AI 结果」页按功能筛选查看；兴趣画像页集成待做。",
    ),
    EVENT_MERGE(
        dbValue = 24,
        category = AiCategory.DISCOVERY,
        scope = AiScope.ARTICLE,
        trigger = AiTrigger.BATCH,
        defaultEnabled = false,
        needsLlm = true,
        label = "同源事件合并阅读",
        summary = "同一事件的多篇报道合成一条时间线，看清事件演进与各源口径差异。",
        entry = "批处理：每日任务；AI 功能总览页展开后可「立即运行」。",
        presentation = "「AI 结果」页按功能筛选查看；专属时间线页待做。",
    ),
    COLD_START(
        dbValue = 25,
        category = AiCategory.DISCOVERY,
        scope = AiScope.GLOBAL,
        trigger = AiTrigger.MANUAL,
        defaultEnabled = false,
        needsLlm = true,
        label = "兴趣冷启动引导",
        summary = "新用户勾选几个关注领域即可生成初始画像，跳过「推荐流空转」阶段。",
        entry = "专属引导卡尚未实现。",
        presentation = "专属勾选页尚未实现；有产物时会出现在「AI 结果」页。",
    ),

    // ── 辅助推送类（10 项） ────────────────────────────────────────────────

    DAILY_BRIEF(
        dbValue = 26,
        category = AiCategory.ASSIST,
        scope = AiScope.GLOBAL,
        trigger = AiTrigger.BATCH,
        defaultEnabled = false,
        needsLlm = true,
        label = "AI 每日简报",
        summary = "把当天新文章压缩成一份简报：几条要闻 + 与你相关的部分 + 可跳过清单。",
        entry = "批处理：每日任务；AI 功能总览页展开后可「立即运行」。",
        presentation = "「AI 结果」页按功能筛选查看；专属简报页与通知跳转待做。",
    ),
    SHARE_COPY(
        dbValue = 27,
        category = AiCategory.ASSIST,
        scope = AiScope.ARTICLE,
        trigger = AiTrigger.MANUAL,
        defaultEnabled = true,
        needsLlm = true,
        label = "生成分享文案",
        summary = "为一篇文章生成适合不同平台的分享文案（短评/长推/要点体）。",
        entry = "阅读页顶栏分享按钮里的「AI 生成文案」。",
        presentation = "文案选择弹层：三种风格卡片，点选即复制并唤起系统分享。",
    ),
    SMART_NOTIFY(
        dbValue = 28,
        category = AiCategory.ASSIST,
        scope = AiScope.ARTICLE,
        trigger = AiTrigger.BATCH,
        defaultEnabled = false,
        needsLlm = true,
        label = "智能通知",
        summary = "新文章先过一遍重要度判定，只推送值得打断你的那些，其余静默。",
        entry = "批处理：自动同步后判定；AI 功能总览页展开后可「立即运行」。",
        presentation = "「AI 结果」页按功能筛选查看；通知栏集成待做。",
    ),
    FEED_HEALTH(
        dbValue = 29,
        category = AiCategory.ASSIST,
        scope = AiScope.FEED,
        trigger = AiTrigger.BATCH,
        defaultEnabled = false,
        needsLlm = true,
        label = "订阅源健康监控",
        summary = "结合抓取日志与更新频率，诊断订阅源是否失效、降频或内容质量下滑。",
        entry = "批处理：每日任务；AI 功能总览页展开后可「立即运行」。",
        presentation = "「AI 结果」页按功能筛选查看（订阅源级产物可跳该源）；专属报告页待做。",
    ),
    HABIT(
        dbValue = 30,
        category = AiCategory.ASSIST,
        scope = AiScope.GLOBAL,
        trigger = AiTrigger.BATCH,
        defaultEnabled = false,
        needsLlm = true,
        label = "阅读习惯分析",
        summary = "分析活跃时段、阅读时长分布、订阅源集中度，指出信息摄入的偏食点。",
        entry = "每日任务；「我的」页的「阅读习惯」入口。",
        presentation = "习惯报告页：时段热力 + 源集中度 + 三条可执行的观察结论。",
    ),
    DAILY_REPORT(
        dbValue = 31,
        category = AiCategory.ASSIST,
        scope = AiScope.GLOBAL,
        trigger = AiTrigger.BATCH,
        defaultEnabled = false,
        needsLlm = true,
        label = "每日阅读报告",
        summary = "汇总当天读了什么、花了多久、错过了什么，给一句中肯的总结。",
        entry = "批处理：每日任务；AI 功能总览页展开后可「立即运行」。",
        presentation = "「AI 结果」页按功能筛选查看；专属报告页与次日通知待做。",
    ),
    FILTER_RULE(
        dbValue = 32,
        category = AiCategory.ASSIST,
        scope = AiScope.FEED,
        trigger = AiTrigger.MANUAL,
        defaultEnabled = false,
        needsLlm = true,
        label = "智能过滤规则生成",
        summary = "用自然语言描述「不想看到什么」，生成关键词过滤规则供确认后启用。",
        entry = "专属「过滤规则」入口尚未实现。",
        presentation = "规则确认页尚未实现；有产物时会出现在「AI 结果」页。",
    ),
    USAGE(
        dbValue = 33,
        category = AiCategory.ASSIST,
        scope = AiScope.GLOBAL,
        trigger = AiTrigger.MANUAL,
        defaultEnabled = true,
        needsLlm = false,
        label = "AI 用量看板",
        summary = "统计今日/累计调用次数、输入输出字数、失败率与预估费用。",
        entry = "设置 → AI 与诊断 → 「AI 用量」入口。",
        presentation = "用量页：数字卡 + 按功能的调用排行 + 日预算进度条。",
    ),
    TASK_QUEUE(
        dbValue = 34,
        category = AiCategory.ASSIST,
        scope = AiScope.GLOBAL,
        trigger = AiTrigger.MANUAL,
        defaultEnabled = true,
        needsLlm = false,
        label = "AI 任务队列",
        summary = "所有后台 AI 任务的排队、限速、重试与失败留痕，可手动清空与重跑。",
        entry = "设置 → AI 与诊断 → 「AI 任务队列」入口。",
        presentation = "队列页：按状态分组的任务列表 + 失败原因 + 重试/清空操作。",
    ),
    PROMPT_TEMPLATE(
        dbValue = 35,
        category = AiCategory.ASSIST,
        scope = AiScope.GLOBAL,
        trigger = AiTrigger.MANUAL,
        defaultEnabled = true,
        needsLlm = false,
        label = "提示词模板管理",
        summary = "集中管理全局提示词与各订阅源的摘要提示词覆盖，支持预览与恢复默认。",
        entry = "专属「提示词」管理页尚未实现；订阅源操作页的单源摘要提示词已可用。",
        presentation = "专属模板页尚未实现。",
    ),
    ;

    companion object {
        /**
         * 默认开启的功能集合。
         *
         * 刻意做成「逐项读 key、缺 key 回落 [defaultEnabled]」而不是持久化整个集合——
         * 后者的集合快照会**冻住**升级时刻：新版本新增一个默认开的功能，
         * 老用户因为本地已存了旧快照而永远拿不到它。逐项读 key 则让新功能自动按其默认值生效。
         */
        val DEFAULT_ENABLED: Set<AiFeature> = entries.filter { it.defaultEnabled }.toSet()

        /** dbValue → 枚举。查不到返回 null（老版本产物或未来功能），调用方按未知处理而不是崩。 */
        fun fromDbValue(value: Int): AiFeature? = entries.firstOrNull { it.dbValue == value }

        /** 按分组取全部功能，设置页分节用。 */
        fun ofCategory(category: AiCategory): List<AiFeature> = entries.filter { it.category == category }

        /** 会调用大模型的那些——预算与限流只拦这些。 */
        val LLM_FEATURES: List<AiFeature> = entries.filter { it.needsLlm }

        /** 走后台批处理的那些——[AiTaskPlanner] 按这个入队。 */
        val BATCH_FEATURES: List<AiFeature> = entries.filter { it.trigger == AiTrigger.BATCH }
    }
}

/** 三大功能分组，与需求文档的内容处理 / 推荐发现 / 辅助推送一一对应。 */
enum class AiCategory(val label: String, val description: String) {
    CONTENT("内容处理", "对单篇文章做理解、提炼与加工。"),
    DISCOVERY("推荐发现", "帮你找到该读的、该订的、以及你还没看到的。"),
    ASSIST("辅助推送", "总结、提醒与控制成本，让 AI 用得起。"),
}

/** 产物挂在什么主体上，决定 ai_artifacts 的 subjectKind 与孤儿清理方式。 */
enum class AiScope(val dbValue: Int, val label: String) {
    /** 文章级：随文章一起归档清理。 */
    ARTICLE(dbValue = 0, label = "文章"),

    /** 订阅源级：随订阅源删除而清理。 */
    FEED(dbValue = 1, label = "订阅源"),

    /** 全局级：按时间滚动清理（简报、报告、画像解读）。 */
    GLOBAL(dbValue = 2, label = "全局"),
    ;

    companion object {
        fun fromDbValue(value: Int): AiScope? = entries.firstOrNull { it.dbValue == value }
    }
}

/**
 * 触发方式。这不是文档字段——[AiTaskPlanner] 与 UI 都按它分流：
 * - MANUAL：只有用户显式点按钮才跑，不进队列。
 * - ON_DEMAND：进入时若有产物直接用，没有才跑；跑完持久化（摘要、关联推荐）。
 * - BATCH：不实时跑，由每日任务批量入队，受并发与日预算双重限制。
 * - REALTIME：每次都实时调，结果不落库（问答、划词解释）。
 */
enum class AiTrigger(val label: String, val description: String) {
    MANUAL("手动触发", "用户点按钮才执行，不占用后台额度。"),
    ON_DEMAND("按需生成", "有产物直接用，没有才生成，生成后持久保存。"),
    BATCH("后台批处理", "每日任务批量执行，受并发与日预算限制。"),
    REALTIME("实时交互", "每次都实时调用，结果不落库。"),
}
