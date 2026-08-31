"""统计 RSS/Atom 样本：格式、条目数、全文字段覆盖率、长度分布、图片字段、日期格式、HTML 标签。"""

import glob
import json
import os
import re
import statistics
from collections import Counter
from html.parser import HTMLParser

from lxml import etree

BASE = os.path.dirname(os.path.abspath(__file__))

NS = {
    "content": "http://purl.org/rss/1.0/modules/content/",
    "atom": "http://www.w3.org/2005/Atom",
    "dc": "http://purl.org/dc/elements/1.1/",
    "media": "http://search.yahoo.com/mrss/",
    "rdf": "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
    "rss10": "http://purl.org/rss/1.0/",
    "itunes": "http://www.itunes.com/dtds/podcast-1.0.dtd",
}

IMG_RE = re.compile(r"<img\b", re.I)
TAG_RE = re.compile(r"<([a-zA-Z][a-zA-Z0-9]*)")
STYLE_ATTR_RE = re.compile(r"\sstyle\s*=", re.I)
SCRIPT_RE = re.compile(r"<script\b", re.I)
IFRAME_RE = re.compile(r"<iframe\b", re.I)


class TagCounter(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.tags = Counter()
        self.has_style_attr = 0
        self.text_len = 0
        self._skip_depth = 0
        self._skip_tag = None

    def handle_starttag(self, tag, attrs):
        self.tags[tag] += 1
        if any(k == "style" for k, _ in attrs):
            self.has_style_attr += 1
        if tag in ("script", "style") and self._skip_depth == 0:
            self._skip_tag, self._skip_depth = tag, 1
        elif self._skip_depth and tag == self._skip_tag:
            self._skip_depth += 1

    def handle_endtag(self, tag):
        if self._skip_depth and tag == self._skip_tag:
            self._skip_depth -= 1

    def handle_data(self, data):
        if not self._skip_depth:
            self.text_len += len(data)


def analyze_html(html: str):
    if not html:
        return None
    c = TagCounter()
    try:
        c.feed(html)
        c.close()
    except Exception:
        pass
    return {
        "tags": c.tags,
        "text_len": c.text_len,
        "img_count": len(IMG_RE.findall(html)),
        "script_count": len(SCRIPT_RE.findall(html)),
        "iframe_count": len(IFRAME_RE.findall(html)),
        "inline_style": len(STYLE_ATTR_RE.findall(html)),
    }


def full_text(el):
    """元素内全部文本（含子元素），lxml 下 etree.tostring 更快但这里要纯文本。"""
    return "".join(el.itertext()) if el is not None else ""


def inner_html(el):
    """取元素内部 HTML（不含自身标签）。"""
    if el is None:
        return ""
    parts = [el.text or ""]
    for child in el:
        parts.append(etree.tostring(child, encoding="unicode", with_tail=True))
    return "".join(parts)


def detect_date_format(samples):
    """返回 {格式: 数量}"""
    out = Counter()
    for s in samples:
        s = (s or "").strip()
        if not s:
            out["(空)"] += 1
        elif re.match(r"^\d{4}-\d{2}-\d{2}T", s):
            out["ISO 8601"] += 1
        elif re.match(r"^(Mon|Tue|Wed|Thu|Fri|Sat|Sun),", s, re.I) or re.search(
            r"\d{1,2} [A-Z][a-z]{2} \d{4}", s
        ):
            out["RFC 822/1123"] += 1
        elif re.match(r"^\d{4}-\d{2}-\d{2}[ T]", s):
            out["类 ISO（无 T/无时区）"] += 1
        else:
            out[f"其他: {s[:32]}"] += 1
    return out


def stats(values):
    if not values:
        return None
    v = sorted(values)
    n = len(v)
    mid = n // 2
    median = v[mid] if n % 2 else (v[mid - 1] + v[mid]) / 2
    return {
        "n": n,
        "min": v[0],
        "median": median,
        "mean": round(statistics.fmean(v), 1),
        "max": v[-1],
    }


def analyze(path):
    with open(path, "rb") as f:
        raw = f.read()
    parser = etree.XMLParser(recover=True, huge_tree=True, resolve_entities=False)
    try:
        root = etree.fromstring(raw, parser=parser)
    except Exception as e:
        return {"file": os.path.basename(path), "error": f"XML 解析失败: {e}"}

    tag = etree.QName(root).localname if not str(root.tag).startswith("{") else etree.QName(root).localname
    root_ns = etree.QName(root).namespace or ""

    if root_ns == NS["atom"]:
        fmt, items = "Atom 1.0", root.findall(".//atom:entry", NS)
    elif root_ns == NS["rss10"] or tag == "RDF":
        fmt, items = "RSS 1.0 (RDF)", root.findall(".//rss10:item", NS)
    elif tag == "rss":
        ver = root.get("version", "?")
        fmt = f"RSS {ver}"
        items = root.findall(".//item")
    else:
        return {"file": os.path.basename(path), "error": f"未知根节点 <{root.tag}>"}

    rec = {
        "file": os.path.basename(path),
        "bytes": len(raw),
        "format": fmt,
        "items": len(items),
        "desc_raw": [], "desc_text": [], "desc_present": 0,
        "full_raw": [], "full_text": [], "full_present": 0, "full_empty_outofline": 0,
        "full_fields": Counter(),
        "media_thumbnail": 0, "media_content": 0, "enclosure": 0,
        "itunes_image": 0, "img_in_content": 0, "img_in_desc": 0,
        "script_items": 0, "iframe_items": 0, "inline_style_items": 0,
        "tag_counter": Counter(), "dates": [], "date_fields": Counter(),
        "desc_missing": 0,
    }

    for it in items:
        # ---- 摘要字段 ----
        desc = None
        for xp in ("description", "atom:summary", "dc:description", "rss10:description"):
            found = it.xpath(xp, namespaces=NS)
            if found:
                desc = inner_html(found[0])
                break
        if desc is not None and desc.strip():
            rec["desc_present"] += 1
            rec["desc_raw"].append(len(desc))
            rec["desc_text"].append(analyze_html(desc)["text_len"])
            if IMG_RE.search(desc):
                rec["img_in_desc"] += 1
        else:
            rec["desc_missing"] += 1

        # ---- 全文字段 ----
        full_html = None
        hits = []
        for xp, label in (
            ("content:encoded", "content:encoded"),
            ("atom:content", "atom:content"),
            ("content:items", "content:items (RDF)"),
        ):
            for el in it.xpath(xp, namespaces=NS):
                hits.append(label)
                html = inner_html(el)
                # RSSHub Atom 回退：<content src="..." type="text/html"/> 无正文
                if not html.strip() and el.get("src"):
                    rec["full_empty_outofline"] += 1
                elif full_html is None or len(html) > len(full_html):
                    full_html = html
        # 无任何命名空间的 <content>
        if full_html is None:
            for el in it.xpath("content"):
                hits.append("content (无命名空间)")
                full_html = inner_html(el)

        for h in hits:
            rec["full_fields"][h] += 1
        if full_html is not None and full_html.strip():
            rec["full_present"] += 1
            rec["full_raw"].append(len(full_html))
            rec["full_text"].append(analyze_html(full_html)["text_len"])
            if IMG_RE.search(full_html):
                rec["img_in_content"] += 1
            if SCRIPT_RE.search(full_html):
                rec["script_items"] += 1
            if IFRAME_RE.search(full_html):
                rec["iframe_items"] += 1
            if STYLE_ATTR_RE.search(full_html):
                rec["inline_style_items"] += 1

        # ---- 图片字段 ----
        if it.xpath("media:thumbnail", namespaces=NS):
            rec["media_thumbnail"] += 1
        if it.xpath("media:content", namespaces=NS):
            rec["media_content"] += 1
        if it.xpath("enclosure", namespaces=NS):
            rec["enclosure"] += 1
        if it.xpath("itunes:image", namespaces=NS):
            rec["itunes_image"] += 1

        # ---- 日期 ----
        for xp in ("pubDate", "atom:published", "atom:updated", "dc:date", "published", "updated"):
            for el in it.xpath(xp, namespaces=NS):
                rec["dates"].append((el.text or "").strip())
                rec["date_fields"][xp.split(":")[-1]] += 1
                break
            else:
                continue
            break

        # ---- 内容 HTML 标签频次 ----
        blob = (full_html or "")
        if blob.strip():
            rec["tag_counter"].update(analyze_html(blob)["tags"])

    return rec


def pct(a, b):
    return f"{(a / b * 100):.1f}%" if b else "n/a"


def main():
    files = sorted(
        p for p in glob.glob(os.path.join(BASE, "*.xml")) + glob.glob(os.path.join(BASE, "*.atom"))
        if os.path.basename(p) not in ()
    )
    results = []
    for p in files:
        r = analyze(p)
        results.append(r)
        if r.get("error"):
            print(f"!! {r['file']}: {r['error']}")
            continue
        n = r["items"]
        d = stats(r["desc_raw"])
        f = stats(r["full_raw"])
        ge500 = sum(1 for x in r["desc_raw"] if x >= 500)
        ge450 = sum(1 for x in r["desc_raw"] if x >= 450)
        print(
            f"{r['file']:38s} {r['format']:14s} items={n:>3d} "
            f"全文={r['full_present']}/{n} ({pct(r['full_present'], n)}) "
            f"desc中位={d['median'] if d else '-':>6} "
            f"desc>=500={ge500}/{n} ({pct(ge500, n)})"
        )

    with open(os.path.join(BASE, "analysis.json"), "w", encoding="utf-8") as fp:
        json.dump(
            [{k: (dict(v) if isinstance(v, Counter) else v) for k, v in r.items()} for r in results],
            fp, ensure_ascii=False, indent=2,
        )
    print(f"\n已分析 {len(results)} 个文件，明细见 analysis.json")


if __name__ == "__main__":
    main()
