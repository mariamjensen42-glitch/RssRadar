package com.cycling.rssradar.core.model

/**
 * AI 智能功能的唯一注册表：16 项功能各占一个枚举项。
 *
 * 加一项新功能的成本被压到最低——在这里加一行枚举常量（拿到 dbValue），
 * 在 AiPayloads 加载荷、AiParsers 加解析函数，再到 [AiFeatureSpecs] 登记一行
 * （prompt 构建 + 解析 + 空壳判定 + id 收口），app 侧 AiArticleSheet 的渲染注册表补一行。
 * 产物落 `ai_artifacts` 的 (subjectKind, subjectId, kind) 三元组，**不需要 schema 迁移**，
 * 这是本表刻意不做外键、改由每日任务清理孤儿换来的（见 AiArtifactSchema.kt 注释）。
 *
 * 本文件是本仓库少数**不能再拆**的文件之一：Kotlin 枚举项不能分文件。
 * 每项只留**程序要读的标志位**：dbValue / 分组 / 主体 / 触发 / 默认值 / 是否调模型 /
 * 是否给开关 / 功能名。说明性文案（做什么、入口在哪、呈现是什么）不在这里——
 * 它们曾经作为字符串字段写在这个文件里，但全仓没有任何读取点，
 * 界面走的是 `AiFeatureTexts` 的 `summaryRes()` / `entryRes()` / `presentationRes()`
 * 三张穷尽表 → `values/strings.xml` + `values-en/strings.xml`；
 * 同一句话存两份的下场是改一处忘一处，而且没有任何东西会发现。
 * 同级的 [AiCategory]、[AiScope]、[AiTrigger] 已各自独立成文件。
 * 新增功能请只往这里加枚举项，别的逻辑（spec / 载荷 / 解析 / 文案）都在别的文件。
 *
 * 四条不变量：
 * 1. `dbValue` 一旦发布**永不复用、永不重排**——老版本写进 ai_artifacts 的 kind 靠它解释。
 *    删掉的功能会留下空号（如 4 / 11），这正是它没被复用过的证据，不是待填的坑。
 * 2. `trigger` 决定任务队列的行为，不是文档装饰：[AiTaskPlanner] 按它分流。
 * 3. `defaultEnabled` 按**触发方式**划分，不是一律保守关：
 *    - MANUAL / REALTIME / ON_DEMAND（用户点一下才跑）→ 默认**开**。
 *      不点就不花钱，默认关掉只会让用户以为功能没做，白等一次「去设置里打开」。
 *    - BATCH（后台自动跑）→ 默认**关**。
 *      它会在每日任务里自动消耗额度，属于"要不要花钱"的决策，交给用户显式开启。
 *    这条划分让「打开就能用」和「不会静默扣费」同时成立。
 * 4. `configurable = false` 的项不进「功能开关」列表——它们的 `isEnabled` 全仓无人读取，
 *    拨动开关不改变任何行为。两种情形：设置入口（用量看板 / 任务队列 / 提示词管理），
 *    以及开关另有归属的本地能力（推荐流的开关在信息流的 tab 里）。
 */
enum class AiFeature(
    /** 落库与任务队列使用的稳定整数标识；删掉的功能留下的空号作废，不复用也不重排。 */
    val dbValue: Int,
    /** 功能归属的三大分组，设置页据此分节。 */
    val category: AiCategory,
    /** 产物挂在哪一类主体上，决定 subjectKind 与清理归属。 */
    val scope: AiScope,
    /** 触发方式。 */
    val trigger: AiTrigger,
    /** 首次安装后的默认开关状态。 */
    val defaultEnabled: Boolean,
    /** 是否需要调用大模型。false 的项是纯本地能力（推荐流 / 相关阅读 / 用量看板 / 任务队列 / 提示词管理）。 */
    val needsLlm: Boolean,
    /**
     * 是否在「功能开关」页给开关。false 的项不进那张列表，因为拨动它不改变任何行为。
     *
     * 两种情形：设置入口（用量看板 / 任务队列 / 提示词管理），
     * 以及开关另有归属的本地能力（推荐流的开关在信息流的 tab 里）。
     * 把它们混在 12 个开关里只会让用户问"用量看板要不要开"，并让"开关"这个词失去意义。
     */
    val configurable: Boolean = true,
    /**
     * 功能名。**只给日志与任务标题用**（跳过原因、队列条目名）——
     * 界面上显示的名称/说明/入口/呈现一律走 `AiFeatureTexts` 的资源映射，
     * 那样才有中英两份且能被穷尽 when 守住。
     */
    val label: String,
) {
    // ── 内容处理类（7 项） ────────────────────────────────────────────────

    SUMMARY(
        dbValue = 1,
        category = AiCategory.CONTENT,
        scope = AiScope.ARTICLE,
        trigger = AiTrigger.ON_DEMAND,
        defaultEnabled = true,
        needsLlm = true,
        label = "AI 摘要",
    ),
    TRANSLATE(
        dbValue = 2,
        category = AiCategory.CONTENT,
        scope = AiScope.ARTICLE,
        trigger = AiTrigger.MANUAL,
        defaultEnabled = true,
        needsLlm = true,
        label = "文章翻译",
    ),
    KEYWORDS(
        dbValue = 6,
        category = AiCategory.CONTENT,
        scope = AiScope.ARTICLE,
        trigger = AiTrigger.BATCH,
        defaultEnabled = false,
        needsLlm = true,
        label = "关键词提取",
    ),
    QA(
        dbValue = 8,
        category = AiCategory.CONTENT,
        scope = AiScope.ARTICLE,
        trigger = AiTrigger.REALTIME,
        defaultEnabled = true,
        needsLlm = true,
        label = "文章问答与深度解析",
    ),
    NOISE(
        dbValue = 12,
        category = AiCategory.CONTENT,
        scope = AiScope.ARTICLE,
        trigger = AiTrigger.BATCH,
        defaultEnabled = false,
        needsLlm = true,
        label = "智能降噪与内容评分",
    ),
    OUTLINE(
        dbValue = 13,
        category = AiCategory.CONTENT,
        scope = AiScope.ARTICLE,
        trigger = AiTrigger.MANUAL,
        defaultEnabled = true,
        needsLlm = true,
        label = "长文精读结构化",
    ),
    GLOSSARY(
        dbValue = 15,
        category = AiCategory.CONTENT,
        scope = AiScope.ARTICLE,
        trigger = AiTrigger.REALTIME,
        defaultEnabled = true,
        needsLlm = true,
        label = "划词解释",
    ),

    // ── 推荐发现类（3 项） ────────────────────────────────────────────────

    PERSONAL_FEED(
        dbValue = 16,
        category = AiCategory.DISCOVERY,
        scope = AiScope.GLOBAL,
        trigger = AiTrigger.ON_DEMAND,
        defaultEnabled = true,
        needsLlm = false,
        configurable = false,
        label = "个性化内容推荐",
    ),
    FEED_RECOMMEND(
        dbValue = 17,
        category = AiCategory.DISCOVERY,
        scope = AiScope.GLOBAL,
        trigger = AiTrigger.BATCH,
        defaultEnabled = false,
        needsLlm = true,
        label = "智能订阅源推荐",
    ),
    RELATED(
        dbValue = 21,
        category = AiCategory.DISCOVERY,
        scope = AiScope.ARTICLE,
        trigger = AiTrigger.ON_DEMAND,
        defaultEnabled = false,
        needsLlm = false,
        label = "文章关联推荐",
    ),
    // ── 辅助推送类（6 项） ────────────────────────────────────────────────

    SHARE_COPY(
        dbValue = 27,
        category = AiCategory.ASSIST,
        scope = AiScope.ARTICLE,
        trigger = AiTrigger.MANUAL,
        defaultEnabled = true,
        needsLlm = true,
        label = "生成分享文案",
    ),
    FEED_HEALTH(
        dbValue = 29,
        category = AiCategory.ASSIST,
        scope = AiScope.FEED,
        trigger = AiTrigger.BATCH,
        defaultEnabled = false,
        needsLlm = true,
        label = "订阅源健康监控",
    ),
    FILTER_RULE(
        dbValue = 32,
        category = AiCategory.ASSIST,
        // 规则作用于整个订阅列表，不挂在某个源上——挂在源上会顺带给产物一个
        // 「打开订阅源」按钮，而它并不属于任何一个源（subjectId 只能是 0）。
        scope = AiScope.GLOBAL,
        trigger = AiTrigger.MANUAL,
        defaultEnabled = false,
        needsLlm = true,
        label = "智能过滤规则生成",
    ),
    USAGE(
        dbValue = 33,
        category = AiCategory.ASSIST,
        scope = AiScope.GLOBAL,
        trigger = AiTrigger.MANUAL,
        defaultEnabled = true,
        needsLlm = false,
        configurable = false,
        label = "AI 用量看板",
    ),
    TASK_QUEUE(
        dbValue = 34,
        category = AiCategory.ASSIST,
        scope = AiScope.GLOBAL,
        trigger = AiTrigger.MANUAL,
        defaultEnabled = true,
        needsLlm = false,
        configurable = false,
        label = "AI 任务队列",
    ),
    PROMPT_TEMPLATE(
        dbValue = 35,
        category = AiCategory.ASSIST,
        scope = AiScope.GLOBAL,
        trigger = AiTrigger.MANUAL,
        defaultEnabled = true,
        needsLlm = false,
        configurable = false,
        label = "提示词模板管理",
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

        /** 按分组取全部功能（含设置入口），后台任务的按分组清理用这个。 */
        fun ofCategory(category: AiCategory): List<AiFeature> = entries.filter { it.category == category }

        /**
         * 按分组取**可开关**的功能——设置页的分组列表只认这个。
         *
         * 与 [ofCategory] 分开是刻意的：清理类逻辑要覆盖整组（包括设置入口），
         * 而开关列表只该出现"拨动它会有行为变化"的那些。
         * 谁要是拿 [ofCategory] 去渲染开关，就会把用量看板也变成一个无意义的开关。
         */
        fun configurableOfCategory(category: AiCategory): List<AiFeature> =
            entries.filter { it.category == category && it.configurable }

        /** 会调用大模型的那些——预算与限流只拦这些。 */
        val LLM_FEATURES: List<AiFeature> = entries.filter { it.needsLlm }

        /** 走后台批处理的那些——[AiTaskPlanner] 按这个入队。 */
        val BATCH_FEATURES: List<AiFeature> = entries.filter { it.trigger == AiTrigger.BATCH }
    }
}
