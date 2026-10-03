# RssRadar

Android RSS 阅读器（Jetpack Compose + Material 3，RSSHub 为核心数据源）。

不要编译，可以单元测试。4、不要代码注释。5、严禁本地自动构建APK。6、开发项目，都有合理的官方架构7、严禁静态检查


## UI 组件准则（最高优先级）

> **多使用自带的组件。M3 EXPRESSIVE。**

写自定义控件前，先确认 Material 3 Expressive 官方没有对应组件。官方组件清单、允许手写的三类例外、
以及「已有官方替代就移除第三方库」的具体规则见 **`docs/ui-resources.md` 四、组件准则**。

底线：别手搓官方已有控件。手写只允许在官方确实没有时（无障碍降级、品牌化外观、平台 IO 封装）。

## Agent skills

### Issue tracker

Issues live as local markdown files under `.scratch/` (not a remote tracker). See `docs/agents/issue-tracker.md`.

### Triage labels

Roles are recorded as a `Status:` line in each issue file; default five-role vocabulary. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: `CONTEXT.md` + `docs/adr/` at the repo root. See `docs/agents/domain.md`.
