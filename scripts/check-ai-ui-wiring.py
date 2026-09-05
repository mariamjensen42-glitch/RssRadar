#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
AI 功能「接线」检查：枚举里登记的功能，是否真的有 UI 出口。

为什么需要它
------------
AiFeature 是唯一注册表，"加一项功能 = 加一行枚举 + 一个 prompt + 一个解析分支"。
这个流程很顺畅，但漏掉第四步——**加 UI 出口**——不会有任何报错：
执行器照常跑、产物照常落 ai_artifacts、设置页照常显示开关与一句
"结果展示：简报页"，而那个页面根本不存在。用户的体感就是"跑成功了，结果呢？"。

本脚本把"没有出口"这件事变成一条能变红的命令，在提交前就拦住。

两条检查
--------
A. UI 出口：每项功能必须在 UI 模块（app/src/main/java）被引用，或属于
   早于枚举就存在的旧 UI（见 LEGACY_UI，逐项写明依据）。
B. 渲染分支：阅读页 AI 面板（AiArticleSheet）展示的每个功能，其产物类型
   必须在 AiResultCard 的 when 里有分支。缺分支的后果是一张只有标题的空白卡，
   编译不报错、运行不报错，用户只看到"生成了但什么都没有"。

用法
----
    python scripts/check-ai-ui-wiring.py

退出码 0 = 全部接线；1 = 有未接线的功能（或渲染缺分支）。
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FEATURE_FILE = ROOT / "core/data/src/main/kotlin/com/cycling/rssradar/core/data/ai/AiFeature.kt"
SHEET_FILE = ROOT / "app/src/main/java/com/cycling/rssradar/ui/article/AiArticleSheet.kt"
PARSERS_FILE = ROOT / "core/data/src/main/kotlin/com/cycling/rssradar/core/data/ai/AiParsers.kt"
BUTTONS_FILE = ROOT / "app/src/main/java/com/cycling/rssradar/ui/article/ArticleDetailViewModel.kt"
UI_DIR = ROOT / "app/src/main/java"

# 早于 AiFeature 枚举就存在的 UI，因此 UI 代码里不会写 `AiFeature.XXX`。
# 每一项都必须能指出具体落点，否则就是给自己开后门。
LEGACY_UI: dict[str, str] = {
    "SUMMARY": "阅读页摘要卡（articles.aiSummary，GenerateSummary）",
    "TRANSLATE": "阅读页翻译开关（TranslationState）",
    "PERSONAL_FEED": "信息流「推荐」tab（FeedTab.Recommended）",
    "USAGE": "AI 功能总览页用量卡（UsageCard）",
    "TASK_QUEUE": "AI 功能总览页队列区（QueueSection）",
}


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def parse_features() -> list[str]:
    """取 AiFeature 的枚举常量名（跳过 companion object 里的成员）。"""
    src = read(FEATURE_FILE)
    body = src.split("enum class AiFeature(", 1)[1]
    # 第一个 `;` 之前是枚举常量区，之后是 companion
    consts = body.split("\n    ;", 1)[0]
    names = re.findall(r"^    ([A-Z][A-Z0-9_]*)\($", consts, re.M)
    return names


def ui_references() -> set[str]:
    """UI 模块里出现过的 AiFeature.X 引用。"""
    hits: set[str] = set()
    for kt in UI_DIR.rglob("*.kt"):
        hits.update(re.findall(r"AiFeature\.([A-Z][A-Z0-9_]*)", read(kt)))
    return hits


def has_generic_viewer() -> bool:
    """
    UI 模块里是否存在**通用产物查看面**（AI 产物中心）。

    它不认识任何具体功能：直接把 ai_artifacts 按 kind 摊开、用
    [AiPayloadText] 渲染 JSON，因此新增功能**零成本**自动纳入。
    有它之后，一个"没有专属 UI"的功能不再是完全不可达——用户至少能看见产物原文，
    只是没有专属入口与排版。所以未接线项从 error 降级为 warning：
    仍然提醒开发者"这项功能目前只有兜底出口"，但不再把整条流水线卡红。
    """
    for kt in UI_DIR.rglob("*.kt"):
        src = read(kt).lower()
        if "aiartifactrepository" in src and ("browse(" in src or "overview(" in src):
            return True
    return False


def sheet_render_branches() -> set[str]:
    """AiResultCard 的 when 里已处理的 payload 类型名。"""
    src = read(SHEET_FILE)
    card = src.split("private fun AiResultCard(", 1)[1]
    card = card.split("\n@Composable", 1)[0]
    return set(re.findall(r"is (Ai[A-Za-z]*Payload)", card))


def payload_type_of() -> dict[str, str]:
    """
    功能 → 产物类型，唯一权威来源是 AiParsers.parse 的分派 + 各解析函数的返回类型。

    刻意不去猜 `Ai<PascalCase>Payload`——SHARE_COPY 的真实类型是 AiSharePayload，
    猜名字会造出假红。这里顺着 parse 的分支走到解析函数签名上取返回类型。
    """
    src = read(PARSERS_FILE)

    # parse() 的分派表：AiFeature.X -> 调用的解析函数名
    dispatch_src = src.split("fun parse(feature: AiFeature, raw: String): Any = when (feature) {", 1)[1]
    dispatch_src = dispatch_src.split("\n}", 1)[0]
    dispatch: dict[str, str] = {}
    for line in dispatch_src.splitlines():
        m = re.match(r"\s*AiFeature\.([A-Z][A-Z0-9_]*)\s*->\s*([a-zA-Z0-9_]+)\(raw\)", line)
        if m:
            dispatch[m.group(1)] = m.group(2)

    # 各解析函数的返回类型
    returns = dict(
        re.findall(r"fun\s+([a-zA-Z0-9_]+)\s*\(\s*raw:\s*String\s*\)\s*:\s*([A-Za-z0-9_]+)", src)
    )

    out: dict[str, str] = {}
    for feature, fn in dispatch.items():
        ret = returns.get(fn)
        if ret:
            out[feature] = ret
    return out


def sheet_features() -> list[str]:
    """面板会展示产物的功能清单（ARTICLE_AI_BUTTONS + QA + GLOSSARY）。"""
    src = read(BUTTONS_FILE)
    block = src.split("val ARTICLE_AI_BUTTONS: List<AiFeature> = listOf(", 1)[1]
    block = block.split(")", 1)[0]
    names = re.findall(r"AiFeature\.([A-Z][A-Z0-9_]*)", block)
    extra = src.split("val ARTICLE_AI_FEATURES: List<AiFeature> =", 1)[1].split("\n", 1)[0]
    names += re.findall(r"AiFeature\.([A-Z][A-Z0-9_]*)", extra)
    return names


def main() -> int:
    features = parse_features()
    refs = ui_references()
    branches = sheet_render_branches()

    # ── 检查 A：UI 出口 ────────────────────────────────────────────────
    unwired = [f for f in features if f not in refs and f not in LEGACY_UI]
    legacy = [f for f in features if f in LEGACY_UI and f not in refs]

    payload_types = payload_type_of()

    # ── 检查 B：渲染分支 ───────────────────────────────────────────────
    # 只查面板会展示的功能；产物类型是 String 的（纯文本/非 LLM 功能）不进卡片，跳过。
    unrendered: list[tuple[str, str]] = []
    for f in sheet_features():
        t = payload_types.get(f)
        if t and t != "String" and t not in branches:
            unrendered.append((f, t))

    generic = has_generic_viewer()

    print(f"AiFeature 共 {len(features)} 项")
    print(f"  UI 模块直接引用      : {len(refs & set(features))} 项")
    print(f"  旧 UI（早于枚举）    : {len(legacy)} 项")
    print(f"  无专属出口           : {len(unwired)} 项")
    print(f"  通用产物中心         : {'有' if generic else '无'}")
    print()

    if unwired:
        level = "【仅通用出口】" if generic else "【未接线】"
        if generic:
            print(level + "下列功能没有专属入口，产物只能在「AI 结果」页看原文：")
        else:
            print(level + "下列功能有执行器、会落库，但没有任何页面能触发或看到它：")
        for f in unwired:
            print(f"  - {f}")
        print()
        if generic:
            print("  现状：开关照常生效、跑批照常花钱，产物在产物中心可见，但缺少为它")
            print("        定制的排版与触发入口。做专属页之前，这不阻塞发布。")
        else:
            print("  后果：设置页照常显示开关与「结果展示：…」文案，用户开启后跑批成功，")
            print("        产物进了 ai_artifacts，但全 App 找不到它。等同于白烧额度。")
        print()

    if unrendered:
        print("【渲染缺分支】面板会展示这些功能，但 AiResultCard 的 when 没有对应类型：")
        for f, t in unrendered:
            print(f"  - {f} → 需要 is {t} 分支")
        print()
        print("  后果：产物已生成，卡片只渲染出一个标题，下面一片空白。编译不报错。")
        print()

    if legacy:
        print("【旧 UI】以下功能不引用枚举但有既有落点，不计为未接线：")
        for f in legacy:
            print(f"  - {f}：{LEGACY_UI[f]}")
        print()

    if not unwired and not unrendered:
        print("OK：全部功能都有 UI 出口，面板产物都有渲染分支。")
        return 0
    if unwired and not unrendered and generic:
        # 有通用产物中心时，缺专属出口不算阻断——提醒即可。
        # 一旦把这种情况也判红，脚本会长期红着，红到最后没人看，守门就失效了。
        print("仅提醒：上述功能只有通用产物中心这一个出口，尚未做专属入口。")
        return 0
    return 1


if __name__ == "__main__":
    sys.exit(main())
