/**
 * :core:domain — 领域逻辑层（纯 Kotlin）。
 *
 * 承载业务规则与可复用逻辑：
 * - UseCase / Interactor（多 ViewModel 复用或逻辑复杂到值得单独测试时才建，透传不建）
 * - 纯业务规则计算（如抓取重试策略、内容完整性判定）
 * - Repository 接口（如有需要，实现留在 :app 的 data 层）
 *
 * 约定：
 * - 允许依赖 kotlinx-coroutines-core（suspend / Flow）
 * - 禁止 import 任何 androidx.* / android.* / Room / Compose 类型
 * - suspend 函数 main-safe；对外只暴露不可变类型
 * - 上游：:core:model；下游：:app
 */
package com.cycling.rssradar.core.domain
