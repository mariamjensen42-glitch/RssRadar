"""把 analysis.json 渲染成 REPORT.md 的数据明细（打印到 stdout，供人工汇总）。"""

import json
import os

BASE = os.path.dirname(os.path.abspath(__file__))
data = json.load(open(os.path.join(BASE, "analysis.json"), encoding="utf-8"))

LABEL = {
    "plain_ruanyifeng_atom.xml": "阮一峰的网络日志（Atom）",
    "plain_sspai_feed.xml": "少数派官方 feed",
    "plain_oschina_news.xml": "开源中国资讯",
    "upstream_github_kotlin_releases.atom": "GitHub Kotlin releases（RSSHub 上游）",
    "upstream_hackernews_frontpage.xml": "HN Front Page（RSSHub 上游）",
    "cn_ithome.xml": "IT之家",
    "cn_ifanr.xml": "爱范儿",
    "cn_solidot.xml": "Solidot",
    "cn_infoq.xml": "InfoQ 中国",
    "cn_meituan_tech.xml": "美团技术团队",
    "cn_geekpark.xml": "极客公园",
    "blog_coolshell.xml": "酷壳 CoolShell",
    "blog_codingnow.xml": "云风的 BLOG",
    "blog_appinn.xml": "小众软件",
    "en_rustlang_blog.xml": "Rust Blog (EN)",
}


def s(v):
    return "-" if v is None else f"{v:,.0f}"


for r in data:
    if r.get("error"):
        print(f"### {r['file']}: {r['error']}")
        continue
    n = r["items"]
    dr, dt = r["desc_raw"], r["desc_text"]
    fr, ft = r["full_raw"], r["full_text"]
    print("=" * 100)
    print(f"{r['file']}   [{LABEL.get(r['file'], '')}]")
    print(f"  格式={r['format']}  条目={n}  文件={r['bytes']:,}B")
    print(f"  专用全文字段: {dict(r['full_fields'])}  有正文的条目={r['full_present']}/{n} "
          f"({(r['full_present']/n*100):.1f}%)  外链空content={r['full_empty_outofline']}")
    print(f"  description/summary 原始HTML字符数: n={len(dr)} min={s(min(dr) if dr else None)} "
          f"中位={s(sorted(dr)[len(dr)//2] if dr else None)} max={s(max(dr) if dr else None)}")
    if dt:
        sdt = sorted(dt)
        print(f"  description/summary 纯文本字符数: min={sdt[0]:,} 中位={sdt[len(sdt)//2]:,} max={sdt[-1]:,}  "
              f">=500字的条目={sum(1 for x in dt if x>=500)}/{len(dt)}")
    if fr:
        sfr, sft = sorted(fr), sorted(ft)
        print(f"  全文字段 原始HTML: min={sfr[0]:,} 中位={sfr[len(sfr)//2]:,} max={sfr[-1]:,}")
        print(f"  全文字段 纯文本  : min={sft[0]:,} 中位={sft[len(sft)//2]:,} max={sft[-1]:,}")
    print(f"  图片: media:thumbnail={r['media_thumbnail']} media:content={r['media_content']} "
          f"enclosure={r['enclosure']} itunes:image={r['itunes_image']} "
          f"正文含<img>的条目={r['img_in_content']} 摘要含<img>={r['img_in_desc']}")
    print(f"  清洗相关: 含<script>的条目={r['script_items']} 含<iframe>={r['iframe_items']} "
          f"含内联style={r['inline_style_items']}")
    print(f"  日期字段: {dict(r['date_fields'])}")
    print(f"  日期格式: {dict(r['dates'] and __import__('collections').Counter() or {})}"
          if False else f"  日期样例: {r['dates'][:2]}")
    top = sorted(r["tag_counter"].items(), key=lambda kv: -kv[1])[:12]
    print(f"  正文 HTML 标签 Top12: {', '.join(f'{k}({v})' for k, v in top)}")
print("=" * 100)
