"""对账：用了 `hiltViewModel()` 的模块必须显式声明 hilt-navigation-compose。

为什么需要这个守卫（本项目踩过两次：feature/subscriptions、feature/addsubscription）：
`androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel` 由传递依赖
`androidx.hilt:hilt-lifecycle-viewmodel-compose` 提供，只声明 hilt-android + hilt-compiler 拿不到。
本地扁平 classpath（check-kotlin.py）恰好有那个 jar，**本地类型检查不会报**，
只有真 Gradle/AS 构建才暴露 `Unresolved reference 'hilt'` —— 症状出现在用户那侧，代价最高。

典型触发场景：给某个模块**新增/改造 Destination**（Destination 里按惯例用 hiltViewModel()），
但忘了同步 build.gradle.kts。改造前 VM 是外部传入的模块尤其容易漏。

用法：python scripts/check-hilt-compose-deps.py   （退出码 1 = 有模块缺依赖）
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
IMPORT = re.compile(
    r'^\s*import\s+androidx\.hilt\.lifecycle\.viewmodel\.compose\.hiltViewModel',
    re.M,
)
# 必须锚在行首：早期版本用 `'hilt.navigation.compose' in txt` 做子串判断，
# 结果被注释掉的 `// implementation(...)` 也算"已声明"，守卫静默空转（实测过）。
DECLARED = re.compile(
    r'^\s*implementation\(libs\.androidx\.hilt\.navigation\.compose\)',
    re.M,
)


def main() -> int:
    build_files = (
        list(ROOT.glob('feature/*/build.gradle.kts'))
        + list(ROOT.glob('core/*/build.gradle.kts'))
        + [ROOT / 'app/build.gradle.kts']
    )

    used_mods, declared_mods, missing = [], [], []
    for bg in sorted(build_files):
        if not bg.exists():
            continue
        module = bg.parent.relative_to(ROOT).as_posix()
        declared = bool(DECLARED.search(bg.read_text(encoding='utf-8')))

        used = False
        for kt in bg.parent.rglob('*.kt'):
            if '/build/' in kt.as_posix():
                continue
            if IMPORT.search(kt.read_text(encoding='utf-8', errors='ignore')):
                used = True
                break

        if used:
            used_mods.append(module)
        if declared:
            declared_mods.append(module)
        if used and not declared:
            missing.append(module)

    print('用了 hiltViewModel() 的模块：%d 个' % len(used_mods))
    for m in used_mods:
        mark = 'OK ' if m not in missing else 'MISSING'
        print('  %-8s %s' % (mark, m))
    print()
    print('声明了 hilt-navigation-compose 的模块：%d 个' % len(declared_mods))

    if missing:
        print()
        print('以下模块用了 hiltViewModel() 但没声明依赖，AS 构建会报 Unresolved reference \'hilt\'：')
        for m in missing:
            print('  - %s/build.gradle.kts  →  implementation(libs.androidx.hilt.navigation.compose)' % m)
        return 1
    print()
    print('全部对齐。')
    return 0


if __name__ == '__main__':
    sys.exit(main())
