#!/usr/bin/env python3
"""
验证 readability 提取算法在真实中文网页上的成功率（issue #13 的实现前置）。

方法：从已抓的 feed 样本中取文章 URL，抓取网页 HTML，用 readability-lxml
（Mozilla Readability 算法的 Python 实现，与 readability4j 同源同逻辑）提取正文，
统计成功率。readability4j 是该算法的 Kotlin 移植，行为等价。
"""
from __future__ import annotations

import html
import pathlib
import re
import sys
import xml.etree.ElementTree as ET

import requests
from readability import Document

SAMPLES = pathlib.Path(__file__).resolve().parent
SOURCES = ["cn_ithome.xml", "cn_geekpark.xml", "blog_coolshell.xml", "plain_sspai_feed.xml"]
PER_SOURCE = 2
UA = {"User-Agent": "Mozilla/5.0 (Android) RssRadar/1.0"}
TAGS = re.compile(r"<[^>]+>")
WS = re.compile(r"\s+")


def links_from(path: pathlib.Path, n: int) -> list[str]:
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError:
        return []
    out = []
    for el in root.iter():
        t = el.tag.rsplit("}", 1)[-1].lower()
        if t in ("link", "guid") and (el.text or "").strip().startswith("http"):
            url = el.text.strip()
            if url not in out:
                out.append(url)
        if len(out) >= n:
            break
    return out


def main():
    total = ok = fail_fetch = fail_extract = 0
    print(f"{'源':<28} {'状态':<10} {'网页大小':>8} {'提取正文字数':>10}")
    print("-" * 64)
    for src in SOURCES:
        for url in links_from(SAMPLES / src, PER_SOURCE):
            total += 1
            try:
                resp = requests.get(url, headers=UA, timeout=20)
                resp.raise_for_status()
                raw = resp.text
            except Exception:
                fail_fetch += 1
                print(f"{src:<28} {'抓取失败':<10}")
                continue
            try:
                doc = Document(raw)
                summary_html = doc.summary(html_partial=True)
                text = WS.sub(" ", TAGS.sub(" ", html.unescape(summary_html))).strip()
                n = len(text)
                if n >= 200:
                    ok += 1
                    status = "成功"
                else:
                    fail_extract += 1
                    status = "提取过短"
                print(f"{src:<28} {status:<10} {len(raw):>8} {n:>10}")
            except Exception:
                fail_extract += 1
                print(f"{src:<28} {'提取异常':<10}")
    print("-" * 64)
    print(f"合计 {total}：成功 {ok}，抓取失败 {fail_fetch}，提取失败/过短 {fail_extract}")
    fetchable = total - fail_fetch
    if fetchable:
        print(f"抓取成功条件下的提取成功率: {ok}/{fetchable} = {ok/fetchable*100:.0f}%")


if __name__ == "__main__":
    sys.exit(main())
