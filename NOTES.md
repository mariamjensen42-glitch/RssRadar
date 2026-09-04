# NOTES

## 项目术语（RssRadar）
- core:* 系列：core:model（领域数据结构，纯 JVM）→ core:domain（纯逻辑，纯 JVM）→ core:data（Android library，Room/ROME/store/通知/AI）→ 计划中 core:ui（Android library，Compose 主题/通用组件/图片封装）。
- RssRadarPalette：旧的全局可变色板单例（mutableStateOf + 顶层 getter 代理 + applyPalette）。core:ui 落地时重构为 RadarColors（immutable data class）+ LocalRadarColors（CompositionLocal）+ radarColors() 读取入口。
- 通用组件判定标准：零业务依赖（import 不含 core.data/di/sync、不含业务文案）。OptionPickerSheet/ArticleContextMenu/BottomTabBar 判业务，留 app。
- 主题装配：CompositionLocalRoot.kt 留在 app——它用 Hilt EntryPoint 拉 ReadingPrefs（core:data），是数据层进 UI 的唯一接线点。
- 图片：coil，调用点 4 处；统一 RadarImage 封装后 coil import 限定在 core:ui 内。
- 图标：lucide 线性描边风格，禁 Filled（用户硬性偏好）。

## 用户偏好（工程侧）
- 决策快：一轮给推荐项，用户大多照单全收；但「重构正统性」类选项会主动选激进方案（如 palette 重构为 CompositionLocal）。
- 无 gradle 环境，验证靠 scripts/check-kotlin.py（--files 要带全五模块源码）+ CI。
