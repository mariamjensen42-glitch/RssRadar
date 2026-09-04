#!/usr/bin/env python3
"""
RssRadar 静态符号校验（替代 gradle 编译，用于「禁止用 gradle」的约束下做交叉检查）。

检查项：
1. lucide 图标：代码里 Lucide.Xxx 与 import com.composables.icons.lucide.Xxx
   必须存在于 icons-lucide-android aar 的真值表。
2. androidx.compose.* 的 import：简单名必须能在 compose aar 的 classes.jar 里找到
   （顶层类 / *Kt 顶层函数 / object 成员）。用来抓「成员扩展被当成顶层函数 import」
   这类错误 —— 例如 Modifier.weight 是 ColumnScope 的成员扩展，顶层并不存在
   androidx.compose.foundation.layout.weight。
3. theme 颜色：ui/theme 里的 val 与代码里的引用对得上。

用法：
    python prototype/check-symbols.py
"""
from __future__ import annotations

import pathlib
import re
import sys
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
AAR_CACHE = pathlib.Path(r"E:\AndroidDev\Gradle\caches\modules-2\files-2.1")

LUCIDE_AAR = (
    AAR_CACHE
    / "com.composables"
    / "icons-lucide-android"
    / "2.2.1"
)
COMPOSE_AARS = [
    "androidx.compose.material3/material3-android/1.4.0",
    "androidx.compose.foundation/foundation-android/1.10.4",
    "androidx.compose.foundation/foundation-layout-android/1.10.4",
    "androidx.compose.ui/ui-android/1.10.4",
    "androidx.compose.ui/ui-unit-android/1.10.4",
    "androidx.compose.ui/ui-text-android/1.10.4",
    "androidx.compose.ui/ui-geometry-android/1.10.4",
    "androidx.compose.ui/ui-graphics-android/1.10.4",
    "androidx.compose.ui/ui-util-android/1.10.4",
    "androidx.compose.runtime/runtime-android/1.10.4",
    "androidx.compose.runtime/runtime-saveable-android/1.10.4",
    "androidx.compose.animation/animation-android/1.10.4",
    "androidx.compose.animation/animation-core-android/1.10.4",
]


def find_aar(rel: str) -> pathlib.Path | None:
    d = AAR_CACHE / pathlib.Path(rel)
    if not d.exists():
        return None
    hits = sorted(d.rglob("*.aar"))
    return hits[-1] if hits else None


def lucide_truth() -> set[str]:
    d = LUCIDE_AAR
    if not d.exists():
        return set()
    aar = sorted(d.rglob("*.aar"))[-1]
    with zipfile.ZipFile(aar) as z:
        names = [
            n for n in z.namelist()
            if n.startswith("res/drawable/") and n.endswith(".xml")
        ]
    out = set()
    for n in names:
        stem = pathlib.Path(n).stem.replace("lucide_ic_", "")
        out.add("".join(w.capitalize() for w in stem.split("_")))
    return out


IDENT = re.compile(r"^[A-Za-z_][A-Za-z0-9_]{0,60}$")

CACHE_FILE = pathlib.Path(__file__).resolve().parent / ".compose-symbols.txt"


def class_utf8_constants(data: bytes) -> list[str]:
    """解析 .class 常量池里的所有 Utf8 项（含类名、方法名、字段名）。"""
    if data[:4] != b"\xca\xfe\xba\xbe":
        return []
    i = 8
    count = int.from_bytes(data[i:i + 2], "big")
    i += 2
    out: list[str] = []
    idx = 1
    while idx < count and i < len(data):
        tag = data[i]
        i += 1
        if tag == 1:  # Utf8
            ln = int.from_bytes(data[i:i + 2], "big")
            i += 2
            out.append(data[i:i + ln].decode("utf-8", "ignore"))
            i += ln
        elif tag in (7, 8, 16, 19, 20):  # Class, String, MethodType, Module, Package
            i += 2
        elif tag == 15:  # MethodHandle
            i += 3
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
            i += 4
        elif tag in (5, 6):  # Long, Double：占两个常量池槽
            i += 8
            idx += 1
        else:
            break
        idx += 1
    return out


def compose_symbols() -> set[str]:
    """
    收集 compose aar 里所有可 import 的简单符号名：
      - 类名（ModalBottomSheet、LazyColumn …）
      - *Kt 文件类名去尾（FooKt -> Foo）
      - class 常量池里的标识符（顶层函数 dp / fillMaxWidth / remember …）
    结果缓存到 .compose-symbols.txt，避免每次重复解析。
    """
    if CACHE_FILE.exists():
        return set(CACHE_FILE.read_text(encoding="utf-8").split())

    syms: set[str] = set()
    for rel in COMPOSE_AARS:
        aar = find_aar(rel)
        if not aar:
            print(f"  [warn] 未找到 aar: {rel}")
            continue
        with zipfile.ZipFile(aar) as z:
            jar_data = z.read("classes.jar")
        with zipfile.ZipFile(__import__("io").BytesIO(jar_data)) as j:
            for name in j.namelist():
                if not name.endswith(".class"):
                    continue
                cls = name[:-6].replace("/", ".")
                simple = cls.rsplit(".", 1)[-1].split("$")[0]
                syms.add(simple)
                if simple.endswith("Kt"):
                    syms.add(simple[:-2])
                for s in class_utf8_constants(j.read(name)):
                    if IDENT.match(s):
                        syms.add(s)

    CACHE_FILE.write_text("\n".join(sorted(syms)), encoding="utf-8")
    return syms


def methods_of(aar: pathlib.Path, class_pattern: str) -> set[str]:
    """用 javap 取出某个类的方法名（去掉 name mangling 后缀）。给未来扩展留口子。"""
    import io
    import subprocess

    javap = pathlib.Path(r"C:\Program Files\Java\jdk-21\bin\javap.exe")
    if not javap.exists():
        return set()
    with zipfile.ZipFile(aar) as z:
        jar = io.BytesIO(z.read("classes.jar"))
    tmp = pathlib.Path(__import__("tempfile").gettempdir()) / "_chk.jar"
    tmp.write_bytes(jar.getvalue())
    res = subprocess.run(
        [str(javap), "-classpath", str(tmp), class_pattern],
        capture_output=True, text=True, errors="ignore",
    )
    out = set()
    for line in res.stdout.splitlines():
        m = re.search(r"\s([\w$]+)[-(]", line)
        if m:
            out.add(m.group(1).split("-")[0])
    return out


def main() -> int:
    kt_files = sorted((ROOT / "app/src/main/java").rglob("*.kt"))
    problems: list[str] = []

    # ---- 1. lucide ----
    truth = lucide_truth()
    if not truth:
        print("[skip] lucide aar 未找到，跳过图标校验")
    else:
        for f in kt_files:
            text = f.read_text(encoding="utf-8")
            for m in re.finditer(r"\bLucide\.(\w+)", text):
                if m.group(1) not in truth:
                    problems.append(f"图标不存在 Lucide.{m.group(1)}  ({f.relative_to(ROOT)})")
            for m in re.finditer(r"import com\.composables\.icons\.lucide\.(\w+)", text):
                name = m.group(1)
                if name != "Lucide" and name not in truth:
                    problems.append(f"import 图标不存在 {name}  ({f.relative_to(ROOT)})")
        print(f"[lucide] 真值表 {len(truth)} 个图标，校验 {len(kt_files)} 个文件")

    # ---- 2. compose import 简单名 ----
    syms = compose_symbols()
    # *Scope 的成员扩展：顶层并不存在同名函数，直接 import 会编译失败
    member_extensions = {"weight", "align", "alignBy", "alignByBaseline", "matchParentSize"}
    checked = 0
    for f in kt_files:
        text = f.read_text(encoding="utf-8")
        for m in re.finditer(r"import (androidx\.compose\.[\w.]+)\.(\w+)", text):
            fq, simple = m.group(1), m.group(2)
            checked += 1
            if simple in member_extensions:
                problems.append(
                    f"顶层不存在 {fq}.{simple}（它是 *Scope 的成员扩展，直接 import 会编译失败）"
                    f"  ({f.relative_to(ROOT)})"
                )
                continue
            if syms and simple not in syms:
                problems.append(
                    f"compose 符号未找到 {fq}.{simple}  ({f.relative_to(ROOT)})"
                )
    print(f"[compose] 符号表 {len(syms)} 个，校验 {checked} 条 import")

    # ---- 3. theme 颜色引用 ----
    theme_dir = ROOT / "app/src/main/java/com/cycling/rssradar/ui/theme"
    defined = set()
    for f in theme_dir.glob("*.kt"):
        text = f.read_text(encoding="utf-8")
        # 兼容两种定义形式：`val X = Color(...)` 和 getter 代理 `val X: Color get() = ...`
        defined |= set(re.findall(r"^val (\w+)\s*[:=]", text, re.M))
        defined |= set(re.findall(r"^fun (\w+)\s*[(<]", text, re.M))
    for f in kt_files:
        if f.parent == theme_dir:
            continue
        text = f.read_text(encoding="utf-8")
        for m in re.finditer(r"import com\.cycling\.rssradar\.ui\.theme\.(\w+)", text):
            if m.group(1) not in defined:
                problems.append(
                    f"theme 颜色未定义 {m.group(1)}  ({f.relative_to(ROOT)})"
                )
    print(f"[theme] 已定义 {len(defined)} 个颜色，校验引用")

    print()
    if problems:
        print(f"发现 {len(problems)} 个问题：")
        for p in problems:
            print("  -", p)
        return 1
    print("全部通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
