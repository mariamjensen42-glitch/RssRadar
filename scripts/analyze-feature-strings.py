#!/usr/bin/env python3
"""资源归属分析：给定一批「即将搬进 feature 的文件」，把它们的 android 资源引用分类。

为什么需要：feature 模块摸不到 app 的 res（nonTransitiveRClass），所以搬迁前必须知道
哪些文案是这批文件独占、哪些还有别人在用。判据来自 SKILL 的「被依赖 >=2 次即非私有物」。

用法：
    python scripts/analyze-feature-strings.py <文件相对路径...>

输出：
    1. 独占资源 —— 引用者全在给定集合内，应随 feature 搬进它的 res（并从 app res 删除）
    2. 共享资源 —— 还有集合外的文件在用：app res 留副本（跨模块同名资源是 app 覆盖 library）
    3. 两语言原文 —— 便于直接贴进 feature res
    4. labelRes 调用点 —— **字面量扫描的盲区**：`Enum.labelRes()` 把 R.string 藏在扩展函数里，
       本脚本看不见。这一节列出每个调用 labelRes() 的文件及其枚举 import，
       用来判断某个枚举文案到底只有搬迁集合在用，还是别处也在用（决定归 feature 还是沉 core:ui）。

⚠️ 第 4 节必须人工过一遍。实测踩过：`ListViewMode.labelRes` 被 feed 顶栏与设置页共用，
   只跑字面量扫描会把它误判成「设置独占」，直到编译报错才暴露。
"""
from __future__ import annotations

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC_DIRS = ['app/src', 'core', 'feature']
REF = re.compile(r'\bR\.(string|plurals|array|drawable|mipmap|raw|xml)\.([A-Za-z0-9_]+)')


def iter_kotlin():
    for base in SRC_DIRS:
        base_dir = ROOT / base
        if not base_dir.is_dir():
            continue
        for path in base_dir.rglob('*.kt'):
            if 'build' in path.parts:
                continue
            yield path


def resource_value(kind: str, name: str) -> dict[str, str]:
    out = {}
    for lang in ('values', 'values-en'):
        f = ROOT / 'app/src/main/res' / lang / 'strings.xml'
        if not f.is_file():
            continue
        text = f.read_text(encoding='utf-8', errors='replace')
        m = re.search(rf'<{kind}\s+name="{re.escape(name)}"[^>]*>(.*?)</{kind}>', text, re.S)
        out[lang] = m.group(1) if m else None
    return out


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    moving = {str(pathlib.PurePath(p).as_posix()) for p in sys.argv[1:]}

    users: dict[tuple[str, str], set[str]] = {}
    for path in sorted(iter_kotlin()):
        rel = path.relative_to(ROOT).as_posix()
        text = path.read_text(encoding='utf-8', errors='replace')
        for kind, name in set(REF.findall(text)):
            users.setdefault((kind, name), set()).add(rel)

    keys = sorted({k for k, u in users.items() if u & moving})
    exclusive, shared = [], []
    for k in keys:
        (exclusive if users[k] <= moving else shared).append(k)

    print(f'被 {len(moving)} 个搬迁文件引用的资源共 {len(keys)} 项'
          f'（独占 {len(exclusive)} / 共享 {len(shared)}）\n')

    print('== 独占（应搬进 feature res，并从 app res 删除）==')
    for kind, name in exclusive:
        vals = resource_value(kind, name)
        print(f'  <{kind} name="{name}">{vals.get("values")}</{kind}>')
        print(f'       en: {vals.get("values-en")}')

    print('\n== 共享（app res 留副本；若属通用词则考虑下沉 core:ui）==')
    for kind, name in shared:
        others = sorted(users[(kind, name)] - moving)
        short = ', '.join(o.replace('app/src/main/java/com/cycling/rssradar/', '') for o in others[:4])
        more = f' …共{len(others)}个' if len(others) > 4 else ''
        print(f'  {kind}/{name}  <- {short}{more}')

    enum_ref = re.compile(r'^import com\.cycling\.rssradar\.core\.(?:model|domain)[\w.]*\.(\w+)$', re.M)
    print('\n== labelRes 调用点（枚举间接引用，字面量扫描的盲区；需人工判断归属）==')
    for path in sorted(iter_kotlin()):
        rel = path.relative_to(ROOT).as_posix()
        text = path.read_text(encoding='utf-8', errors='replace')
        if 'labelRes()' not in text:
            continue
        enums = sorted(set(enum_ref.findall(text)))
        mark = 'MOVING' if rel in moving else '      '
        print(f'  [{mark}] {rel.replace("app/src/main/java/com/cycling/rssradar/", "")}'
              f'  enums={enums if enums else "-"}')

    return 0


if __name__ == '__main__':
    sys.exit(main())
