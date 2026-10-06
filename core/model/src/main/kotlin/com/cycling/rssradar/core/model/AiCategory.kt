package com.cycling.rssradar.core.model



/** 三大功能分组，与需求文档的内容处理 / 推荐发现 / 辅助推送一一对应。 */
enum class AiCategory(val label: String, val description: String) {
    CONTENT("内容处理", "对单篇文章做理解、提炼与加工。"),
    DISCOVERY("推荐发现", "帮你找到该读的、该订的、以及你还没看到的。"),
    ASSIST("辅助推送", "总结、提醒与控制成本，让 AI 用得起。"),
}
