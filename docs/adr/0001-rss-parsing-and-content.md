# RSS 解析用 Rome 2.1.0 + Jsoup，正文按需抓取用 readability4j + 文件缓存

真实 feed 比想象中脏：15 源 / 303 条实测显示，专用全文字段（content:encoded / Atom content）只覆盖 30.7%，
RSSHub 的 RSS 渲染器把全文塞在 description 里、Atom 渲染器总是输出可为空的 content，还有站点用无前缀的
`<encoded xmlns>` 变体、三种日期后缀混用、甚至 10/10 条目完全没有日期。同时本项目处于无法用 gradle 编译
验证的环境，手写解析器的每一条分支都跑不起来。因此决定：**保留 Rome 2.1.0 做 feed 结构解析**（被 ReadYou
App 在 Android 上生产验证），**引入 Jsoup 做清洗与配合正文提取**；正文获取采用 **feed 字段优先、打开详情时
按需抓原文**的策略，提取算法用 **readability4j**（同为 ReadYou 验证过的方案），抓取结果存文件缓存
（`cacheDir` + SHA-256(link)），feed 自带正文才进数据库列。

## Considered Options

- **手写 XmlPullParser + Jsoup**（曾被推荐）：省掉 Rome+jdom2 约 2MB 体积、行为完全可控，但在"不能编译
  验证"的处境下风险不可控，且真实数据的脏度（无前缀命名空间变体、缺失日期）正是成熟库的价值区间。被
  样本数据推翻。
- **仅用 feed 自带字段、不抓原文**：57% 的条目能直读全文，剩余 43% 只有摘要——对"阅读"场景不可接受。
- **订阅时预抓全部正文**：流量与电量成本不可控，且无法预知用户会读哪篇。

## Consequences

- 全文判定不能依赖专用字段名：取 description 与 content 中**文本较长者**。
- 缩略图按 enclosure → media:* → 正文首个 `<img>` 三级取，不为缩略图单独发请求；取不到就不显示。
- 清洗必须覆盖 `<script>`、内联 style、`<iframe>`（实测命中率分别 100%/46%/罕见）。
- 阅读时长按正文纯文本长度计算，取不到就不显示——不虚构。
- 依据样本：`prototype/samples/REPORT.md`（15 源 303 条统计）、`prototype/samples/READYOU-NOTES.md`
  （ReadYou 实现调查）。
