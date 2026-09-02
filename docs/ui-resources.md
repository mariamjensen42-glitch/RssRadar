# RssRadar UI 资源白名单

> 基于 Awesome Android UI 方法论整理：按「用途」归类，每类只保留已锁定的主流选择。
> 选型维度：Compose 原生兼容性、库活跃度、依赖体积。新增 UI 依赖前先查本表。
> 最后更新：2026-08-30

## 一、组件白名单（按用途）

| 用途 | 选型 | 版本 | 来源 / 备注 |
|---|---|---|---|
| UI 框架 / 主题 | Jetpack Compose BOM + Material 3 | 2026.02.01 | Compose 原生，零 View 体系；主题走 `RssRadarPalette` + M3 |
| 图标 | lucide（`com.composables:icons-lucide-cmp`） | 2.2.1 | 线性描边，1665 图标；可用符号真值表见 `prototype/check-symbols.py` |
| 远程图片 | Coil 3 + `coil-network-okhttp` | 3.3.0 | v3 **必须**显式加 OkHttp 引擎，否则网络图空白 |
| 文章正文(HTML) | Android `WebView`（`android.webkit.WebView`） | 系统 | `ArticleDetailScreen` 用 `AndroidView` 包 WebView 渲染净化 HTML |
| 导航 | navigation-compose（类型安全路由） | 2.10.0 | `bottomSheet` DSL 已删除，改用 `composable` + `ModalBottomSheet` |
| 弹窗 / 底部抽屉 | Material 3 `ModalBottomSheet` / `AlertDialog` | 自带 | 零新依赖 |
| 下拉刷新 | 手写 `PullToRefresh` | — | 无第三方库 |
| 分页 | 手写 OFFSET 分页（PAGE_SIZE=30） | — | 无 Paging 库 |
| 状态 / DI | ViewModel + Hilt + runtime-saveable | — | — |

## 二、选型结论（保持，勿动）

- **Compose 原生优先**，拒绝 View 体系混用。
- **图标集已锁 lucide**。⚠️ 与全局 feather 偏好冲突：本项目已落地 lucide，新图标只在 lucide 取；
  切 feather 是一次性全量替换，不是增量。两套禁止混用。
- 弹窗 / 抽屉 / 刷新 / 分页全部手写或系统组件，**不引第三方库**——体量小、依赖干净。

## 三、已知缺口（按需再定，勿过早引入）

1. 文章内图片画廊 / 缩放：`WebView` 内可点，原生 `HorizontalPager` + 缩放缺。
2. 骨架屏 / shimmer：封面现用 `Surface2` 灰块占位，列表无加载动画。
3. 富文本离线正文：`WebView` 已够，勿提前换 compose-richtext。
4. 系统分享：现只 `context.openUrl`，未接 `ACTION_SEND`。

## 四、避坑清单（本项目已踩）

- Coil 3 必须加 `coil-network-okhttp`，否则网络图全空白（封面空白根因）。
- `SubcomposeAsyncImage` 默认加载态不绘制，必须显式 `loading`/`error` 占位，否则透明空洞。
- nav 2.10 删了 `bottomSheet` DSL → 用 `composable` + `ModalBottomSheet` 替代，行为等价（进栈、预测性返回）。
- 跨文件私有扩展不可访问（如 `withoutScheme`）→ 统一用标准库 `removePrefix`。
- 通用三忌：别混图标集、别为酷炫动效引大库拖慢包体、别直接搬 demo 上生产不改边界。
