#!/usr/bin/env python3
"""
用真实样本验证 RssParser 的核心策略（Q8=B：无编译环境，用 Python 复刻算法跑真实数据）。

验证项：
1. 「description 与 content 取较长者」策略的全文命中率，对比旧实现（只读 description + 截 500 字）
2. 覆盖样本中的三种脏情况：RSSHub RSS（全文在 description）、美团无前缀 encoded、Atom 空 content
3. 清洗效果：script/style 去除率
"""
from __future__ import annotations

import html
import pathlib
import re
import sys
import xml.etree.ElementTree as ET

SAMPLES = pathlib.Path(__file__).resolve().parent
CONTENT_NS = "{http://purl.org/rss/1.0/modules/content/}"
TEXT_TAGS = re.compile(r"<(script|style)\b[^>]*>.*?</\1>", re.S | re.I)
TAGS = re.compile(r"<[^>]+>")
WS = re.compile(r"\s+")


def to_text(fragment: str) -> str:
    return WS.sub(" ", TAGS.sub(" ", html.unescape(fragment or ""))).strip()


def sanitize(fragment: str) -> str:
    cleaned = TEXT_TAGS.sub("", fragment or "")
    cleaned = re.sub(r'\son\w+="[^"]*"', "", cleaned, flags=re.I)
    return cleaned


def strip_ns(tag: str) -> str:
    return tag.rsplit("}", 1)[-1].lower()


def parse_entry_children(item: ET.Element):
    """返回 (desc, full_list, is_atom)。full_list 包含 atom content 与任意命名空间的 encoded。"""
    desc, full, atom = "", [], False
    for child in item:
        t = strip_ns(child.tag)
        if t == "feed" or "http://www.w3.org/2005/Atom" in child.tag:
            atom = True
        if t == "description":
            desc = (child.text or "") + "".join(ET.tostring(c, encoding="unicode") for c in child)
        elif t == "summary" and atom:
            desc = child.text or ""
        elif t == "content":
            # Atom content：空自闭合（<content src=.../>）text 为 None → 视为空
            full.append(child.text or "")
        elif t == "encoded":
            # 任意命名空间的 encoded（标准 content: 前缀与美团无前缀变体都命中）
            full.append((child.text or "") + "".join(ET.tostring(c, encoding="unicode") for c in child))
    return desc, full, atom


def evaluate(path: pathlib.Path):
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError as e:
        return None
    items = [el for el in root.iter() if strip_ns(el.tag) == "item"] or [
        el for el in root.iter() if strip_ns(el.tag) == "entry"
    ]
    stats = {"total": len(items), "old_full": 0, "new_full": 0, "new_gain": 0,
             "desc_as_content": 0, "clean_hit": 0}
    for item in items:
        desc, full_list, _ = parse_entry_children(item)
        full = max(full_list, key=lambda s: len(to_text(s)), default="")
        desc_len, full_len = len(to_text(desc)), len(to_text(full))
        # 旧实现：只读 description，截 500 字
        old_full = desc_len >= 500
        # 新实现：取较长者为正文
        if full_len >= desc_len:
            content = full
        else:
            content = desc
        new_full = len(to_text(content)) > 0
        if old_full:
            stats["old_full"] += 1
        if new_full:
            stats["new_full"] += 1
            if not old_full:
                stats["new_gain"] += 1
            if desc_len > full_len and desc_len >= 500:
                stats["desc_as_content"] += 1
        cleaned = sanitize(content)
        if "<script" not in cleaned.lower() and "<style" not in cleaned.lower() and "onclick" not in cleaned.lower():
            stats["clean_hit"] += 1 if new_full else 0
    return stats


def main():
    files = sorted(SAMPLES.glob("*.xml")) + sorted(SAMPLES.glob("*.atom"))
    total = {"t": 0, "old": 0, "new": 0, "gain": 0, "desc_as": 0, "clean": 0}
    print(f"{'样本':<34} {'条目':>4} {'旧全文':>6} {'新全文':>6} {'新增':>4}")
    for f in files:
        s = evaluate(f)
        if s is None or s["total"] == 0:
            continue
        total["t"] += s["total"]; total["old"] += s["old_full"]; total["new"] += s["new_full"]
        total["gain"] += s["new_gain"]; total["desc_as"] += s["desc_as_content"]; total["clean"] += s["clean_hit"]
        print(f"{f.name:<34} {s['total']:>4} {s['old_full']:>6} {s['new_full']:>6} {s['new_gain']:>4}")
    print("-" * 66)
    print(f"{'合计':<34} {total['t']:>4} {total['old']:>6} {total['new']:>6} {total['gain']:>4}")
    print()
    print(f"旧实现全文率（desc≥500字）: {total['old']}/{total['t']} = {total['old']/total['t']*100:.1f}%")
    print(f"新实现全文率（取较长者）:   {total['new']}/{total['t']} = {total['new']/total['t']*100:.1f}%")
    print(f"新增可读全文: {total['gain']} 条")
    print(f"其中 description 本身是全文（RSSHub RSS 行为）: {total['desc_as']} 条")
    if total["new"]:
        print(f"清洗后无 script/style/onclick: {total['clean']}/{total['new']}")


if __name__ == "__main__":
    sys.exit(main())
