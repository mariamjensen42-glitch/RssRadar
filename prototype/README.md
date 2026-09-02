# 原型（throwaway，不是生产代码）

## 已定案：B — 信息流优先 + 底部抽屉

**问题**：以 RSSHub 为核心的阅读器，路由的「发现 + 参数构建」该占主屏，还是藏在信息流背后？

**结论：B**。日常动作是读，加源是低频动作，不该占掉主屏；但 RSSHub 仍是一级入口（FAB），
不是塞进二级菜单。A 把低频动作摆在首屏，C 的自动解析靠维护平台词表、认不出时体验断崖。

**B 已落地**（不是本目录的东西，在正式代码里）：
- `app/.../data/RssHubRoute.kt` — 内置路由表 + buildUrl
- `app/.../ui/AddSubscriptionSheet.kt` — ModalBottomSheet 两阶段抽屉
- `app/.../ui/FeedListScreen.kt` — 右下 FAB

**A、C 只作对照，不要照抄进主分支。** 决策过程与范围见 issue #2。

## 文件

| 文件 | 说明 |
|---|---|
| `rsshub-home-prototype.html` | 三变体原型，单文件双击即开。`?variant=A|B|C`，← → 键切换 |
| `check-symbols.py` | 替代 gradle 的静态校验（见下） |
| `.lucide-truth.txt`、`.compose-symbols.txt` | 校验用的符号缓存，删掉会自动重建 |

## check-symbols.py

项目禁止用 gradle 编译，所以图标 / API 只能交叉验证。这个脚本做三件事：

1. **lucide 图标**：代码里 `Lucide.Xxx` 与 lucide import，逐个比对 aar 里的真值表（1665 个图标）。
   专治「用了旧命名」——lucide 只认新名，`CheckCircle2` 之类的旧名不存在。
2. **compose import 简单名**：和 aar 里的真实符号比对，抓「成员扩展当成顶层函数 import」。
   例如 `Modifier.weight` 是 `ColumnScope` 的成员扩展，顶层根本没有
   `androidx.compose.foundation.layout.weight`，import 了就编译失败。
3. **theme 颜色**：`ui/theme` 里没定义过的颜色引用会报错。

```
python prototype/check-symbols.py
```

## 待办

当前工作目录不是 git 仓库（无 `.git`）。等仓库就绪，把整个 `prototype/` 推到 throwaway 分支
`prototype/rsshub-home-variants`，主分支不留变体代码。
