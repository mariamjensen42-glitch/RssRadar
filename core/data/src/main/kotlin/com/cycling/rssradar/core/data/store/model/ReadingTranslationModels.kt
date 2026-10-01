package com.cycling.rssradar.core.data.store.model

/** 译文显示模式：纯译文（替换式）或双语对照。 */
enum class TranslationViewMode { TRANSLATION_ONLY, BILINGUAL }

/** 双语对照的排布：上下堆叠或左右并排。 */
enum class BilingualLayout { STACKED, SIDE_BY_SIDE }

/**
 * 译文显示偏好（翻译功能 v2）：显示模式 + 双语排布。
 * 与翻译过程状态（VM 的 TranslationState）分离——过程态随文章生灭，
 * 这里是用户级环境偏好。
 *
 * 默认纯译文 + 上下：替换式翻译是既有行为，老用户升级后视觉不变。
 */
data class TranslationDisplayState(
    val viewMode: TranslationViewMode = TranslationViewMode.TRANSLATION_ONLY,
    val bilingualLayout: BilingualLayout = BilingualLayout.STACKED,
)

/**
 * **阅读偏好**（Reading preferences）：阅读页一整套只影响呈现、不影响内容的
 * 用户级设置——排版四项（字号/行距/边距/字体族）、图片两项、正文渲染器、译文显示方式。
 *
 * 四项原先各是一个独立 Store（ReadingStyleStore / ReadingImageStore /
 * ReadingRendererStore / TranslationDisplayStore），每个都要重复一套
 * 「Hilt provides → EntryPoint → CompositionLocal 声明 → 取值 → collect → provides
 * → ViewModel 构造 → ViewModel 暴露 → ViewModel 写方法」九点接线。合成一份 state
 * 后接线只剩一条，四项降为本模块的内部缝。
 *
 * _Avoid_: 阅读设置、排版设置、显示偏好
 */
data class ReadingPrefs(
    val style: ReadingStyleState = ReadingStyleState(),
    val image: ReadingImageState = ReadingImageState(),
    val renderer: ReadingRenderer = ReadingRenderer.NATIVE,
    val translation: TranslationDisplayState = TranslationDisplayState(),
    /**
     * 沉浸阅读（issue #93）：开则阅读页做显示层降噪——原生路剥掉分享/推荐/导航等
     * 杂乱块（带正文安全网），WebView 路注入降噪 CSS。默认开：只删明显噪声，
     * 误伤有安全网兜底；不想要极简以外的行为时用户可关。
     */
    val immersive: Boolean = true,
    /**
     * 滚动时自动隐藏顶栏/底栏（ReadYou 差距表第 22 项）。
     *
     * **与 [immersive] 是两件事**：那是内容降噪，这是收起工具栏腾阅读空间。
     * 默认关：这是「我的界面会动」的强感知改动，且少数人会把突然消失的底栏当成 bug。
     */
    val autoHideBars: Boolean = false,
    /**
     * 阅读主题（差距表第 16 项）：阅读页专属配色，四档。默认 [ReadingTheme.FOLLOW]，
     * 老用户升级后阅读页长相不变。
     */
    val readingTheme: ReadingTheme = ReadingTheme.FOLLOW,
    /**
     * 顶部下拉看上一篇 / 底部上拉看下一篇（ReadYou 差距表第 23 项）。
     *
     * 默认关：手势换掉正在读的东西是强感知改动，且和正常滚动共用一套手势——
     * 不想要的人会觉得文章自己在跳。只在整页滚动模式生效（视口模式由 WebView
     * 内部滚动，Compose 拿不到越界量）。
     */
    val pullToSwitchArticle: Boolean = false,
)
