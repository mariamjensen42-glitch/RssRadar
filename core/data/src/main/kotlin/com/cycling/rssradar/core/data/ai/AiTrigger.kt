package com.cycling.rssradar.core.data.ai



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
