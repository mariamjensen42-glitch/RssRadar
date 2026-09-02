# RSS / Atom 样本实测报告

> 调查目的：判断 feed 自带的全文字段有多普遍，从而决定解析层是否需要"抓原文网页取正文"。
> 调查时间：2026-08-29。所有数字来自对本目录 `.xml` / `.atom` 原始样本的脚本统计（`analyze.py` → `analysis.json`），非估计值。
> 本次**没有修改任何 App 源码**。

---

## 0. 结论速览（TL;DR）

1. **RSSHub 公共实例（rsshub.app）从本机完全不可达**（连接超时，非限流）。因此 RSSHub 部分改用**其 GitHub 源码**（`DIYgod/RSSHub` master，见 `rsshub_src/`）作为事实依据。
2. **RSSHub 的默认 RSS 2.0 渲染器从不输出 `content:encoded`**（源码 `lib/views/rss.tsx`）。全文一律放在 `<description>` 里。**如果解析器只认 `content:encoded` 判断"是否有全文"，RSSHub 的 RSS 输出会被 100% 误判为无全文。**
3. **RSSHub 的 Atom 渲染器总是输出 `<content>`**（源码 `lib/views/atom.tsx`），但当正文为空时会退化成 `<content src="..." type="text/html"/>`。**如果解析器只认"有没有 `<content>` 元素"，RSSHub 的 Atom 输出会被误判为 100% 有全文。**
4. 实测 15 个真实源 / 303 条目：**9 个源（60.0%）、173 条（57.1%）能直接读到完整正文**；6 个源（40.0%）、130 条（42.9%）只有摘要、占位符或纯链接。
5. **全文字段只有 8/15 源（53.3%）、93/303 条（30.7%）在用**。另外 2 个源（IT之家、极客公园，共 90 条）**把整篇正文塞进了 `<description>`**——这是最容易被漏掉的一类。
6. **现有 500 字截断必须改**：全文字段里 88.2% 的条目纯文本 ≥ 500 字、77.4% 甚至 ≥ 1,500 字；`<description>` 里有 74 条（24.4%）纯文本 ≥ 500 字，其中极客公园 30/30、IT之家 35/60 全是正文，会被砍掉。
7. **图片几乎全靠正文 HTML 里的 `<img>`**：15 个源里 `media:thumbnail` / `media:content` 使用率 **0%**，`enclosure` 只有 1 个源（6.7%）。别把缩略图方案押在 `media:*` 上。

---

## 1. 抓取结果

### 1.1 失败的源（原因说明）

| 目标 | 结果 | 原因 |
|---|---|---|
| `https://rsshub.app/zhihu/daily` | ❌ | TCP 连接超时（20s） |
| `https://rsshub.app/sspai/index` | ❌ | TCP 连接超时（20s） |
| `https://rsshub.app/github/repos/JetBrains/kotlin/releases` | ❌ | TCP 连接超时（20s） |
| `https://rsshub.app/hackernews/front` | ❌ | TCP 连接超时（20s） |
| `https://rsshub.rssforever.com/hackernews/front`（官方镜像） | ❌ | 连接超时（15s） |
| `https://docs.rsshub.app/`（连通性对照） | ❌ | 连接超时 |
| `https://github.com/DIYgod/RSSHub`（连通性对照） | ✅ 200 | —— |
| `https://www.36kr.com/feed` | ❌ | 返回反爬 HTML 页（根结点是 `<html>`，已删除） |
| `https://www.huxiu.com/rss/0.xml` | ❌ | 读取超时（20s） |
| `https://linux.cn/feed/` | ❌ | DNS 解析失败 |
| `https://www.reddit.com/r/androiddev/.rss` | ❌ | 连接超时 |
| `https://www.yinwang.org/atom.xml` | ❌ | 返回 Vite SPA 首页 HTML，非 feed（已删除） |

**判定**：本机网络可正常访问 GitHub / 阮一峰 / 少数派 / IT之家 等，但 `rsshub.app` 与其官方文档站、官方镜像均超时 → 是网络层面不可达，不是限流或 502。4 个 RSSHub 路由**一个都没抓到**。

### 1.2 成功抓取的 15 个源

为弥补 RSSHub 缺失，额外引入了 2 个**"上游等价源"**——它们是对应 RSSHub 路由的数据来源，可以反推 RSSHub 会输出什么：

- `upstream_github_kotlin_releases.atom` ← `https://github.com/JetBrains/kotlin/releases.atom`（对应 `/github/repos/.../releases`）
- `upstream_hackernews_frontpage.xml` ← `https://hnrss.org/frontpage`（对应 `/hackernews/front`）

| 文件 | 源 | 类型 |
|---|---|---|
| `plain_ruanyifeng_atom.xml` | 阮一峰的网络日志 | 中文个人博客 |
| `plain_sspai_feed.xml` | 少数派官方 feed | 中文新媒体 |
| `plain_oschina_news.xml` | 开源中国资讯 | 中文 IT 资讯 |
| `cn_ithome.xml` | IT之家 | 中文 IT 资讯 |
| `cn_ifanr.xml` | 爱范儿 | 中文科技媒体 |
| `cn_solidot.xml` | Solidot | 中文科技资讯 |
| `cn_infoq.xml` | InfoQ 中国 | 中文技术媒体 |
| `cn_meituan_tech.xml` | 美团技术团队 | 企业技术博客 |
| `cn_geekpark.xml` | 极客公园 | 中文科技媒体 |
| `blog_coolshell.xml` | 酷壳 CoolShell | 中文个人博客 |
| `blog_codingnow.xml` | 云风的 BLOG | 中文个人博客 |
| `blog_appinn.xml` | 小众软件 | 中文软件博客 |
| `en_rustlang_blog.xml` | Rust Blog | 英文对照 |
| `upstream_github_kotlin_releases.atom` | GitHub Kotlin releases | RSSHub 上游 |
| `upstream_hackernews_frontpage.xml` | HN Front Page | RSSHub 上游 |

---

## 2. 汇总表

### 2.1 主表：格式、条目数、全文覆盖

| 源 | 格式 | 条目 | 专用全文字段 | 有正文的条目 | 覆盖率 | 正文实际落在哪 | 能否直读全文 |
|---|---|---:|---|---:|---:|---|---|
| 阮一峰的网络日志 | Atom 1.0 | 3 | `atom:content` | 3 | **100%** | `<content type="html">` | ✅ 全文 |
| 少数派官方 feed | RSS 2.0 | 10 | 无 | 0 | 0% | `<description>`（中位 108 字） | ❌ 仅摘要 |
| 开源中国资讯 | RSS 2.0 | 50 | 无 | 0 | 0% | `<description>`（中位 185 字） | ❌ 仅摘要 |
| IT之家 | RSS 2.0 | 60 | 无 | 0 | 0% | `<description>`（中位 535 字，含 `<img>`） | ✅ **全文藏在 description** |
| 爱范儿 | RSS 2.0 | 20 | `content:encoded` | 20 | **100%** | `content:encoded` | ✅ 全文 |
| Solidot | RSS 2.0 | 20 | 无 | 0 | 0% | `<description>`（中位 299 字） | ❌ 仅摘要 |
| InfoQ 中国 | RSS 2.0 | 20 | 无 | 0 | 0% | `<description>`（纯文本仅 **7 字**，就是个"点击查看原文"链接） | ❌ 几乎无内容 |
| 美团技术团队 | RSS 2.0 | 10 | `content:encoded`（**无前缀**） | 10 | **100%** | `<encoded xmlns="http://purl.org/rss/1.0/modules/content/">` | ✅ 全文 |
| 极客公园 | RSS 2.0 | 30 | 无 | 0 | 0% | `<description>`（中位 4,252 字，最大 8,050） | ✅ **全文藏在 description** |
| 酷壳 CoolShell | RSS 2.0 | 15 | `content:encoded` | 15 | **100%** | `content:encoded` | ✅ 全文 |
| 云风的 BLOG | Atom 1.0 | 15 | `atom:content` | 15 | **100%** | `<content type="html">` | ✅ 全文 |
| 小众软件 | RSS 2.0 | 10 | `content:encoded` | 10 | **100%** | `content:encoded` | ✅ 全文 |
| Rust Blog (EN) | Atom 1.0 | 10 | `atom:content` | 10 | **100%** | `<content type="html">` | ✅ 全文 |
| GitHub Kotlin releases（上游） | Atom 1.0 | 10 | `atom:content` | 10 | 100% ⚠️ | `<content>` 但**只有 67 字符 HTML / 60 字文本**（仅 release 标题链接） | ❌ 占位符，非正文 |
| HN Front Page（上游） | RSS 2.0 | 20 | 无 | 0 | 0% | `<description>`（中位 163 字，是"Comments \| Source"两个链接） | ❌ 仅链接 |
| **合计** | | **303** | **8/15 源有** | **93** | **30.7%** | | **9/15 源、173/303 条（57.1%）** |

⚠️ 注意 GitHub releases 这一行：它有 `<content>` 元素、覆盖率显示 100%，但内容只有 60 个字。**"有 `<content>` 元素" ≠ "有全文"**，判定时必须看内容长度，不能只看元素存在与否。

### 2.2 字符数分布

**`description` / `summary` 字段（纯文本，已剥离 HTML 标签）**

| 源 | 条目 | 最小 | 中位 | 最大 | 纯文本 ≥ 500 字的条目 |
|---|---:|---:|---:|---:|---:|
| 极客公园 | 30 | 2,297 | **4,252** | 8,050 | **30 / 30（100%）** |
| IT之家 | 60 | 267 | **535** | 1,717 | **35 / 60（58.3%）** |
| 云风的 BLOG | 15 | 136 | 598 | 1,069 | 9 / 15（60.0%） |
| Solidot | 20 | 209 | 299 | 493 | 0 / 20（0%） |
| HN Front Page（上游） | 20 | 126 | 163 | 352 | 0 / 20（0%） |
| 开源中国资讯 | 50 | 155 | 185 | 247 | 0 / 50（0%） |
| 少数派官方 feed | 10 | 21 | 108 | 108 | 0 / 10（0%） |
| 酷壳 CoolShell | 15 | 155 | 161 | 172 | 0 / 15（0%） |
| 爱范儿 | 20 | 54 | 65 | 142 | 0 / 20（0%） |
| 美团技术团队 | 10 | 90 | 114 | 170 | 0 / 10（0%） |
| 小众软件 | 10 | 145 | 151 | 151 | 0 / 10（0%） |
| InfoQ 中国 | 20 | 7 | 7 | 7 | 0 / 20（0%） |
| 阮一峰的网络日志 | 3 | 24 | 24 | 24 | 0 / 3（0%） |
| Rust Blog (EN) | 10 | — | — | — | 无 `summary` 字段 |
| GitHub Kotlin releases | 10 | — | — | — | 无 `summary` 字段 |
| **合计** | **303** | | | | **74 / 303（24.4%）** |

> **对 500 字截断的直接影响**：74 条 `description` 会被现有解析器截断。其中极客公园 30 条（100%）、IT之家 35 条（58.3%）**本来就是完整正文**，截 500 字等于把正文砍成摘要。另外 IT之家 `description` 原始 HTML 中位 1,542 字符、最大 6,066，极客公园中位 8,009、最大 108,879——如果按 HTML 字符串截断，还会切出**未闭合的标签**，产生 HTML 碎片。

**全文字段（`content:encoded` / Atom `<content>`）**

| 源 | 条目 | 最小 | 中位 | 最大 |
|---|---:|---:|---:|---:|
| Rust Blog (EN) | 10 | 748 | **6,405** | 25,681 |
| 阮一峰的网络日志 | 3 | 5,400 | **5,491** | 5,628 |
| 爱范儿 | 20 | 1,939 | **4,364** | 12,733 |
| 酷壳 CoolShell | 15 | 2,317 | **3,814** | 11,002 |
| 美团技术团队 | 10 | 718 | **3,319** | 19,920 |
| 云风的 BLOG | 15 | 1,054 | **3,263** | 11,515 |
| 小众软件 | 10 | 404 | **1,313** | 3,739 |
| GitHub Kotlin releases | 10 | 60 | 60 | 60 |
| **全部合并（93 条）** | 93 | **60** | **3,359** | **25,681** |

93 条全文中：
- 纯文本 **≥ 1,500 字：72 条（77.4%）**
- 纯文本 **≥ 1,000 字：79 条（84.9%）**
- 纯文本 **≥ 500 字：82 条（88.2%）**
- 纯文本 < 500 字：**11 条（11.8%）**，其中 10 条是 GitHub releases 的 60 字占位符，只有 1 条是真正的短文（小众软件 404 字）

> **结论**：全文字段的中位长度是 3,359 字，是 500 字阈值的 **6.7 倍**。对全文字段做 500 字截断会毁掉 **88.2%** 的条目——这个截断只能用于摘要字段，绝不能用于全文字段。

### 2.3 图片字段

| 字段 | 使用的源数 | 条目数 | 说明 |
|---|---:|---:|---|
| `media:thumbnail` | **0 / 15（0%）** | 0 | 一个都没有 |
| `media:content` | **0 / 15（0%）** | 0 | 一个都没有 |
| `enclosure` | **1 / 15（6.7%）** | 10 | 仅美团技术团队，全部是 `type="image/png"` 封面图 |
| `itunes:image` | 0 / 15（0%） | 0 | —— |
| 正文含 `<img>` 标签的条目 | 7 源 | **63 / 93 全文条目（67.7%）** | 小众软件 10/10、酷壳 15/15、爱范儿 20/20、美团 10/10、阮一峰 3/3、Rust Blog 4/10、云风 1/15 |
| `description` 含 `<img>` 标签的条目 | 2 源 | **84 / 303（27.7%）** | IT之家 56/60、极客公园 28/30 |
| 内容 HTML 里 `og:image` 元信息 | 0 / 15（0%） | 0 | 没有任何 feed 在正文里嵌 `og:image` |

> **结论**：**缩略图只能从正文 HTML 的第一个 `<img>` 里抽取**，或者从 `enclosure`（仅 6.7% 的源有）。`media:*` 命名空间在这批中文源里出现率为 0，可以低优先级处理，但 RSSHub 的 `media:*` 是有输出的（`lib/views/rss.tsx` 支持 `item.media`，且路由层如 `/zhihu/daily` 会输出 `image` → `<enclosure url=... type="image/jpeg">`），所以还是得支持，只是别指望它。

### 2.4 日期字段

| 格式 | 源数 | 样例 | 出现的源 |
|---|---:|---|---|
| **RFC 822/1123**（RSS 2.0 的 `pubDate`） | 11 / 15 | `Sat, 29 Aug 2026 13:58:53 +0800`<br>`Sat, 29 Aug 2026 08:23:04 GMT`<br>`Sat, 29 Aug 2026 00:41:00 +0000` | 所有 RSS 2.0 源 |
| **ISO 8601**（Atom 的 `published` / `updated`） | 4 / 15 | `2026-08-20T12:01:10Z`<br>`2026-08-26T00:00:00+00:00`<br>`2026-08-29T01:15:26Z` | 所有 Atom 源 |
| **无条目级日期** | 1 / 15 | —— | **美团技术团队：10 条全部没有日期字段** |

细节：
- RFC 822 的时区后缀有 3 种写法混用：`GMT`（IT之家、InfoQ）、`+0000`（爱范儿、小众软件、HN）、`+0800`（极客公园、Solidot、开源中国、少数派）。**不能用单一 `SimpleDateFormat` 硬解，需要宽松 parser。**
- ISO 8601 也有 2 种：`Z` 结尾（阮一峰、云风、GitHub）和 `+00:00` 结尾（Rust Blog）。
- **美团技术团队是一个"无日期"的真实案例**（10/10 条目无 `pubDate`/`published`/`updated`/`dc:date`，只有频道级 `lastBuildDate`）。解析器必须容忍"条目无日期"，不能因此丢弃条目。
- GitHub releases Atom 只有 `<updated>`，**没有 `<published>`**——回退链里得包含 `updated`。

### 2.5 内容 HTML 的标签与清洗需求

**各源正文 Top 10 标签**（按出现次数）：

| 源 | Top 10 标签 |
|---|---|
| 小众软件 | `p(159)` `strong(139)` `li(131)` `a(72)` `td(67)` `img(48)` `figure(47)` `ul(42)` `h2(37)` `code(35)` |
| 云风的 BLOG | `p(415)` `a(39)` `hr(37)` `code(18)` `li(15)` `pre(5)` `blockquote(3)` `img(3)` `ol(3)` `ul(1)` |
| 酷壳 CoolShell | `p(453)` `li(390)` `a(381)` `code(251)` `strong(188)` `img(134)` `ul(86)` `div(61)` `h4(53)` `td(45)` |
| 爱范儿 | `p(1635)` `img(430)` `div(283)` `section(192)` `h3(147)` `strong(103)` `a(27)` `li(25)` `span(15)` `h2(12)` |
| 美团技术团队 | `p(553)` `strong(249)` `span(203)` `li(150)` `img(94)` `h3(58)` `a(53)` `ul(50)` `h2(48)` `code(24)` |
| Rust Blog (EN) | `span(565)` `a(230)` `p(229)` `code(172)` `li(61)` `h2(38)` `blockquote(38)` `div(30)` `strong(22)` `pre(15)` |
| 阮一峰的网络日志 | `p(485)` `a(154)` `img(98)` `h2(33)` `strong(16)` `li(10)` `div(6)` `ul(4)` `blockquote(4)` `h3(3)` |

**需要清洗的东西（实测计数）**：

| 脏东西 | 涉及条目 | 分布 |
|---|---:|---|
| `<script>` 标签 | **15 条** | 酷壳 CoolShell **15/15（100%）** |
| `<iframe>` 标签 | **2 条** | 酷壳 1、美团 1 |
| 内联 `style="..."` 属性 | **43 条** | 爱范儿 17、酷壳 15、小众软件 5、阮一峰 3、美团 2、Rust Blog 1 |
| `&nbsp;` / `style="text-align:center"` 排版残留 | —— | 极客公园（正文在 `description` 里，开头有 `<p style="text-align: center;">&nbsp;</p>`） |

> **结论**：`<script>` 只有 1/15 源在输出，但那个源是 **100% 命中**——不做清洗就是 15 条正文里全部注入脚本。内联 `style` 更普遍（43/93 条，46.2%），如果要自己控制排版就必须清掉，否则和 App 主题冲突。`<iframe>` 罕见（2 条）但存在。

---

## 3. RSSHub 专项（基于源码，非实测 XML）

由于 `rsshub.app` 不可达，这部分证据来自 `DIYgod/RSSHub` master 分支的源码（已下载到 `rsshub_src/`）。以下引用的行号对应下载时的 master 快照。

### 3.1 渲染器层面（决定"全文字段叫什么名字"）

**`lib/views/rss.tsx`（默认 RSS 2.0 输出）**——第 38-57 行的 `<item>` 模板只输出这些子元素：

```
title, description, link, guid, pubDate, author,
enclosure(来自 item.image 或 item.enclosure_url), itunes:image,
itunes:duration, category, media:*
```

**没有 `content:encoded`。** 全文只能从 `item.description` 走 `<description>` 出去。

**`lib/views/atom.tsx`（Atom 输出）**——第 23 行：

```tsx
{item.description ? <content type="html">{item.description}</content>
                  : item.content?.text ? <content type="text">{item.content.text}</content>
                  : <content src={item.link} type="text/html" />}
```

最后一个分支会输出一个**空的、只有 `src` 属性的 `<content>`**。

**这两条合起来意味着：**
- 用"有没有 `content:encoded`"判全文 → RSSHub RSS 输出 **0% 命中**（即使有全文）
- 用"有没有 `<content>` 元素"判全文 → RSSHub Atom 输出 **100% 命中**（即使完全没正文）
- **正确做法：把 RSS 的 `<description>` 和 Atom 的 `<content>` 同等对待，用内容长度 + 是否只有链接来判定。**

### 3.2 具体路由层面（决定"到底有没有全文"）

| 路由 | 源码位置 | `description` 是什么 | 带全文？ |
|---|---|---|---|
| `/zhihu/daily` | `lib/routes/zhihu/daily.ts:55` | `description: storyJson.body` —— 知乎日报 API 返回的**完整正文 HTML**；另有 `image` → `<enclosure>` | ✅ **带全文** |
| `/sspai/index` | `lib/routes/sspai/index.ts:41-54` | banner `<img>` + `body_extends` 各小节 + `articleData.body` **拼接成完整正文** | ✅ **带全文** |
| `/hackernews/front` | `lib/routes/hackernews/index.ts:97,102` | `description: ''` 或 `<a>Comments on Hacker News</a> \| <a>Source</a>`；只有 `?type=comments` 才抓评论全文 | ❌ **不带全文**（默认只给链接） |
| `/github/repos/:user/:type?/:sort?` | `lib/routes/github/repos.ts:83` | `description: item.description \|\| 'No description'` —— **仓库的一句话简介**（几十字符） | ❌ **不带全文** |

**关于 `/github/repos/JetBrains/kotlin/releases` 的额外发现**：
`lib/routes/github/` 目录下的文件是 `activity, advisor, branches, comments, contributors, discussions, eventapi, file, follower, gist, issue, namespace, notifications, org-event, private-feed, pulls, pulse, repo-event, repos, search, star, starred-repos, topic, trending, user-event, wiki` —— **没有 releases 路由**。

该 URL 会命中 `path: '/repos/:user/:type?/:sort?'`，被解析成 `user=JetBrains, type=kotlin, sort=releases`，返回的其实是 **JetBrains 的仓库列表**，而不是 Kotlin 的 release notes。**这个 URL 从一开始就不是你想要的东西**，选型时别拿它当"RSSHub 技术类路由"的样本。

**上游等价源的实测佐证**：
- `hnrss.org/frontpage`（HN 官方 RSS，即 RSSHub `/hackernews/front` 的数据源）实测 20 条，`description` 纯文本中位 **163 字**，内容是"Comments | Source"链接 —— **与源码判断一致：无正文**。
- `github.com/JetBrains/kotlin/releases.atom`（GitHub 官方 releases atom）实测 10 条，`<content>` 只有 **60 字**，就是 release 标题 + 链接 —— **GitHub 自己就不在 feed 里放 release notes 正文**。

### 3.3 一个鲜明的对照

| | 少数派**官方** feed | RSSHub `/sspai/index` |
|---|---|---|
| 条目数 | 10 | 10（源码 `limit=10`） |
| `description` 纯文本中位 | **108 字**（摘要） | **完整正文**（源码：`banner <img>` + `body_extends` + `body`） |
| 能否直读全文 | ❌ | ✅ |

**这就是 RSSHub 的核心价值**：它把"只给摘要的源"变成了"给全文的源"。同样一个少数派，官方 feed 只能看 108 字摘要，RSSHub 路由能给整篇。

---

## 4. 最终结论

### Q1：RSSHub 的路由输出到底带不带全文？

**分路由，差别极大（4 个路由实测源码：2 带、2 不带）：**

- **带全文**：`/zhihu/daily`（知乎日报 API 的完整 `body`）、`/sspai/index`（banner + 各小节 + 正文拼接）。
- **不带全文**：`/hackernews/front`（默认只有链接，`description` 为空）、`/github/repos/:user/...`（只有仓库一句话简介）。

**但更关键的发现是"带了你也拿不到"**：RSSHub 的 RSS 2.0 渲染器**根本不输出 `content:encoded`**，全文全在 `<description>` 里；而 Atom 渲染器**总是输出 `<content>`**，没正文时给一个空的 `<content src=.../>`。

> **对解析层的要求**：不能只认 `content:encoded`，也不能只认 `<content>` 元素存在。必须把 **RSS 的 `<description>`、Atom 的 `<content>`、Atom 的 `<summary>`** 三条路都打通，然后用**内容长度**（建议阈值：纯文本 ≥ 800 字视为全文）来判断，而不是用"字段是否存在"来判断。

### Q2：常规中文源呢？

**15 个源 / 303 条实测：**

| 分类 | 源数 | 占比 | 条目数 | 占比 |
|---|---:|---:|---:|---:|
| 用专用全文字段（`content:encoded` / Atom `<content>`） | 7 | 46.7% | 83 | 27.4% |
| 无专用字段但把全文塞进 `<description>` | 2 | 13.3% | 90 | 29.7% |
| **小计：能直读全文** | **9** | **60.0%** | **173** | **57.1%** |
| 只有摘要，需抓原文 | 4 | 26.7% | 100 | 33.0% |
| 只有链接或空占位符，需抓原文 | 2 | 13.3% | 30 | 9.9% |
| **小计：需抓原文** | **6** | **40.0%** | **130** | **42.9%** |

（本表把 GitHub Kotlin releases 的 10 条空占位符 `<content>` 归入"需抓原文"，因为它有 `<content>` 元素但只有 60 字，不算全文。）

**规律（可用于预判未知源）：**
- **个人博客 / 企业技术博客（WordPress、Hexo、Hugo 类）→ 几乎 100% 带全文**。实测 5 个（阮一峰、酷壳、云风、小众软件、美团技术）**全部 100% 覆盖**。
- **中文 IT 资讯聚合站 → 一半一半**。IT之家、极客公园给全文（但塞在 `<description>` 里），开源中国、Solidot、InfoQ 只给摘要。
- **英文源（Rust Blog）→ 带全文**（`atom:content`，中位 6,405 字）。
- **链接聚合型（HN、GitHub releases）→ 天然没有正文**，这类源再怎么解析 feed 都拿不到正文。

### Q3：对解析层的具体建议（基于以上数据）

1. **别对全文字段做 500 字截断**。全文纯文本中位 3,359 字，88.2%（82/93）≥ 500 字、77.4%（72/93）≥ 1,500 字。截断只用于列表摘要。
2. **`description` 也要当全文候选**。IT之家 60 条、极客公园 30 条共 90 条（占全部条目 29.7%）的正文就在 `<description>` 里，只认 `content:encoded` 会全部漏掉。
3. **若按字符数截断 `description`，必须在 HTML 边界上截**（标签闭合），否则极客公园（中位 8,009 字符 HTML）和 IT之家（中位 1,542）会切出 HTML 碎片。
4. **`<encoded>` 无命名空间前缀的变体必须支持**。美团技术团队用的就是 `<encoded xmlns="http://purl.org/rss/1.0/modules/content/">`（无 `content:` 前缀），按字符串匹配 `content:encoded` 会漏。
5. **日期解析要宽松**：RFC 822 的 `GMT` / `+0000` / `+0800` 三种后缀 + ISO 8601 的 `Z` / `+00:00` 两种，且**必须容忍条目完全没有日期**（美团技术团队 10/10 无日期）。回退链：`pubDate` → `published` → `updated` → `dc:date` → 无。
6. **必须做 HTML 清洗**：`<script>`（酷壳 100% 命中）、内联 `style`（43/93 条，46.2%）、`<iframe>`（2 条）。
7. **缩略图从正文第一个 `<img>` 抽**。`media:*` 在这批源里 0% 使用，`enclosure` 只有 6.7%。
8. **仍需要"抓原文取正文"作为兜底**，但只对 **40.0% 的源 / 42.9% 的条目**生效（开源中国、Solidot、InfoQ、少数派官方、HN、GitHub releases）。这条路径值得做，但不该是默认路径。

---

## 5. 复现方式

```bash
cd prototype/samples
python fetch_samples.py    # 第一批（含 4 个 RSSHub 路由，本次全部超时）
python fetch_more.py       # 第二批：常规中文源 + 2 个 RSSHub 上游等价源
python analyze.py          # 解析所有 .xml/.atom → analysis.json
python summarize.py        # 打印逐源明细
```

原始数据、抓取结果（HTTP 状态 / 字节数 / 耗时）分别保存在 `fetch_results.json`、`fetch_results_2.json`；RSSHub 源码证据保存在 `rsshub_src/`。

**已知局限**：
1. 4 个 RSSHub 路由未能实测（网络不可达），第 3 节的结论源自源码而非真实 XML。若后续网络可达，应补抓一次验证。
2. 样本量 15 源 / 303 条，其中 Atom 源只有 4 个（27 条），Atom 相关的比例置信度低于 RSS 2.0。
3. 所有样本为 2026-08-29 单日快照，源的输出格式可能随时变化。
