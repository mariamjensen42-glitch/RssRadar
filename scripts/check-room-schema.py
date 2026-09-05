#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
校验手写的 Room migration 建表语句与 Room 从 @Entity 生成的 schema 是否逐字符一致。

为什么需要这个脚本（项目踩过两次）：
1. 索引名写错 → 新装用户与升级用户的 schema 分叉，Room 校验时才报，错误信息极难定位。
2. **给只写了 Kotlin 默认值的字段加了 SQL DEFAULT** → Room 根本不生成那个 DEFAULT，
   `onValidateSchema` 判定不符，升级用户一开 App 就崩，而且本地 Kotlin 编译完全无感。

本工程禁止 gradle 编译，`check-kotlin.py` 又只做 Kotlin 编译（不跑 KSP），
所以这类问题在提交前没有任何自动化手段能拦住——只能事后拿 Room 生成的代码反向比对。
KSP 产物（`AppDatabase_Impl.kt`）就是 Room 眼中的唯一真相，拿它当基准最可靠。

用法：
    python scripts/check-room-schema.py

退出码 0 = 一致；1 = 发现差异（会把差异逐条打印出来）。
"""

import glob
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
DB_SRC = ROOT / "core" / "data" / "src" / "main" / "kotlin" / "com" / "cycling" / "rssradar" / "core" / "data" / "db"
IMPL_GLOB = str(
    ROOT / "core" / "data" / "build" / "generated" / "ksp" / "*" / "kotlin" /
    "com" / "cycling" / "rssradar" / "core" / "data" / "db" / "AppDatabase_Impl.kt"
)


def normalize(sql: str) -> str:
    """
    归一化到"只保留语义差异"的形态。

    刻意抹掉两类纯格式差异，否则会淹没真正的问题：
    - 反引号（Room 生成带、手写通常不带，SQLite 无差别）
    - 括号内侧空格（`( id ... )` 与 `(id ...)` 解析后完全等价）

    **保留**的才是要拦的东西：列名、类型、NOT NULL、DEFAULT 值、主键构成、索引列与顺序。
    Room 的 `onValidateSchema` 比的是 TableInfo（这些语义字段），不是 SQL 文本，
    所以这里跟着它的口径走才是准确的。
    """
    sql = sql.replace("`", "")
    sql = re.sub(r"\(\s+", "(", sql)
    sql = re.sub(r"\s+\)", ")", sql)
    sql = re.sub(r"\s+", " ", sql)
    return sql.strip().rstrip(";").strip()


def name_of(sql: str, kind: str) -> str:
    # 必须先去反引号：表名/索引名在 SQL 里是 `ai_tasks` 这种形态，
    # 不去的话 \w+ 匹配不到，所有键都会退化成 "?" 而互相覆盖。
    sql = sql.replace("`", "")
    if kind == "table":
        m = re.search(r"CREATE TABLE IF NOT EXISTS\s+(\w+)", sql)
    else:
        m = re.search(r"INDEX IF NOT EXISTS\s+(\w+)", sql)
    return m.group(1) if m else "?"


def parse_sql_object(sql: str, tables: dict, indexes: dict) -> None:
    flat = sql.replace("\n", " ").replace("`", "")
    upper = flat.upper()
    if "CREATE TABLE IF NOT EXISTS" in upper:
        if "ROOM_MASTER_TABLE" in upper:
            return
        tables[name_of(flat, "table")] = normalize(sql)
    elif "INDEX IF NOT EXISTS" in upper:
        indexes[name_of(flat, "index")] = normalize(sql)


def find_create_statements(text: str):
    '''
    按括号平衡切出完整的 CREATE 语句。

    不假设 execSQL("...") 前缀：三引号块、跨行字符串拼接、语句末尾带逗号
    这几种写法在前缀匹配下都会漏匹配或截断。按括号配平扫描则与调用形式无关，只认 SQL 本身。
    '''
    starts = [m.start() for m in re.finditer(
        r"CREATE\s+(?:UNIQUE\s+)?(?:TABLE|INDEX)\s+IF\s+NOT\s+EXISTS", text, re.I)]
    for start in starts:
        depth = 0
        end = None
        for i in range(start, len(text)):
            ch = text[i]
            if ch == "(":
                depth += 1
            elif ch == ")":
                depth -= 1
                if depth == 0:
                    end = i + 1
                    break
            elif ch == '"' and depth == 0:
                # 建索引语句没有括号，用字符串结束充当边界
                end = i
                break
        if end is None:
            continue
        yield text[start:end]


def preprocess(text: str) -> str:
    """
    合并 Kotlin 的相邻字符串拼接 `"a" +\n "b"` → `"ab"`。

    不合并的话，按引号截断会在第一段末尾就停下，拿到半条 CREATE 语句
    （项目里 MIGRATION_11_12 的索引就是这么写的，曾导致脚本误报）。
    三引号块里不会出现 `" + "` 这种形态，不受影响。
    """
    return re.sub(r'"\s*\+\s*\n?\s*"', '', text)


def parse_impl(path: str) -> tuple:
    """从 KSP 产物里取 Room 眼中的权威 schema。"""
    tables, indexes = {}, {}
    text = preprocess(pathlib.Path(path).read_text(encoding="utf-8"))
    for stmt in find_create_statements(text):
        parse_sql_object(stmt, tables, indexes)
    return tables, indexes


def parse_migrations() -> tuple:
    """从源码里取手写 migration 的建表与建索引语句。"""
    tables, indexes = {}, {}
    for src in sorted(DB_SRC.glob("*.kt")):
        text = preprocess(src.read_text(encoding="utf-8"))
        for stmt in find_create_statements(text):
            parse_sql_object(stmt, tables, indexes)
    return tables, indexes


def main() -> int:
    impls = sorted(glob.glob(IMPL_GLOB))
    if not impls:
        print("[跳过] 未找到 KSP 产物 AppDatabase_Impl.kt —— 需要先跑一次 gradle/ksp 才能校验。")
        return 0

    room_tables, room_indexes = parse_impl(impls[0])
    migration_tables, migration_indexes = parse_migrations()

    if not room_tables:
        print("[跳过] KSP 产物里没解析到建表语句，请检查脚本的解析正则。")
        return 0

    problems = []

    for name, hand_written in sorted(migration_tables.items()):
        if name not in room_tables:
            problems.append(("多余的表", name,
                             "migration 建了它，但 Room 的 @Entity 里没有对应实体", None))
            continue
        room_expected = room_tables[name]
        if hand_written != room_expected:
            problems.append(("建表语句不一致", name, room_expected, hand_written))

    for name, hand_written in sorted(migration_indexes.items()):
        if name not in room_indexes:
            problems.append(("多余的索引", name,
                             "migration 建了它，但 Room 的 @Entity 里没有对应索引", None))
            continue
        room_expected = room_indexes[name]
        if hand_written != room_expected:
            problems.append(("索引语句不一致", name, room_expected, hand_written))

    print(f"KSP 产物：{impls[0]}")
    print(f"比对范围：migration 建表 {len(migration_tables)} 张、索引 {len(migration_indexes)} 个")

    if not problems:
        print("结果：全部一致 ✓")
        return 0

    print(f"结果：发现 {len(problems)} 处差异 ✗\n")
    for kind, name, expected, actual in problems:
        print(f"── {kind}：{name}")
        if actual is None:
            print(f"   {expected}")
        else:
            print(f"   Room 期望：{expected}")
            print(f"   migration：{actual}")
            # 打印出来肉眼看一样却判定不等时，差异一定在不可见字符上——把码点打出来。
            for i, (e_ch, a_ch) in enumerate(zip(expected, actual)):
                if e_ch != a_ch:
                    print(f"   首个字符差异 @{i}：期望 U+{ord(e_ch):04X} {e_ch!r}"
                          f" / 实际 U+{ord(a_ch):04X} {a_ch!r}")
                    break
            else:
                if len(expected) != len(actual):
                    tail = expected[len(actual):] or actual[len(expected):]
                    print(f"   前缀相同但长度不同（{len(expected)} vs {len(actual)}），"
                          f"多出部分：{tail!r}")
                else:
                    print("   字符完全相同却判定不等（不应发生，请检查比较逻辑）")
            if expected and actual:
                e_parts, a_parts = expected.split(" "), actual.split(" ")
                diffs = [f"{i}:{e}!={a}" for i, (e, a) in enumerate(zip(e_parts, a_parts)) if e != a]
                if diffs:
                    print(f"   首个差异位置：{diffs[0]}  （共 {len(diffs)} 处 token 不同）")
                elif len(e_parts) != len(a_parts):
                    print(f"   长度不同：期望 {len(e_parts)} token，实际 {len(a_parts)} token")
        print()
    return 1


if __name__ == "__main__":
    sys.exit(main())
