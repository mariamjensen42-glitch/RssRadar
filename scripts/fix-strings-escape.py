# -*- coding: utf-8 -*-
"""strings.xml 撇号/双引号转义修复（ADR-0017）。

AAPT 规则：非引号包裹的 string 里 ' 必须写作 \'，" 必须写作 \"。
此前三批迁移脚本生成的英文文案含裸撇号（Can't / RssRadar's），
AGP mergeDebugResources 报 Invalid unicode escape sequence。
"""
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parents[1]

BS = chr(92)  # 反斜杠，避免脚本自身被转义问题波及
APOS = BS + "'"
QUOTE = BS + '"'

for loc in ("values", "values-en"):
    p = ROOT / f"app/src/main/res/{loc}/strings.xml"
    out = []
    fixed = 0
    for line in p.read_text(encoding="utf-8").splitlines(keepends=True):
        m = re.match(r'(\s*<string name="[^"]+">)(.*?)(</string>)(\s*)$', line)
        if m:
            head, body, tail, nl = m.groups()
            new = re.sub("(?<!%s)'|(?<!%s)\"" % (re.escape(BS), re.escape(BS)),
                         lambda mm: APOS if mm.group(0) == "'" else QUOTE, body)
            if new != body:
                fixed += 1
            out.append(head + new + tail + nl)
        else:
            out.append(line)
    p.write_text("".join(out), encoding="utf-8")
    text = "".join(out)
    print(loc, "lines fixed:", fixed, "| escape pairs:", text.count(APOS), text.count(QUOTE))
