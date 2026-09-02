import re

files = [
    'ui/feed/FeedListScreen.kt', 'ui/search/SearchScreen.kt',
    'ui/components/ArticleContextMenu.kt', 'ui/feed/FeedListViewModel.kt',
    'ui/search/SearchViewModel.kt', 'data/db/AppDatabase.kt',
    'data/FeedRepository.kt',
]


def strip_noise(src: str) -> str:
    """
    一次扫描剥掉注释、字符串与字符字面量，只留代码本身。

    顺序不能反：字符串里的 `//`（URL 最常见）必须先按字符串吃掉，
    否则行注释规则会把 "https://x" 后半截当注释删掉，引号失配、计数全乱。
    纯字符计数（count('{') - count('}')）对这些一律误判。
    """
    out = []
    i, n = 0, len(src)
    while i < n:
        c = src[i]
        if c == '/' and i + 1 < n and src[i + 1] == '/':
            j = src.find('\n', i)
            i = n if j < 0 else j
            continue
        if c == '/' and i + 1 < n and src[i + 1] == '*':
            j = src.find('*/', i + 2)
            i = n if j < 0 else j + 2
            continue
        if src.startswith('"""', i):
            j = src.find('"""', i + 3)
            i = n if j < 0 else j + 3
            continue
        if c in '"\'':
            quote = c
            i += 1
            while i < n:
                if src[i] == '\\':
                    i += 2
                    continue
                if src[i] == quote:
                    i += 1
                    break
                if src[i] == '\n':  # 未闭合的引号（正则/格式化串里的边界），放弃本行
                    break
                i += 1
            continue
        out.append(c)
        i += 1
    return ''.join(out)


ok = True
for f in files:
    s = strip_noise(open(f, encoding='utf-8').read())
    b = s.count('{') - s.count('}')
    p = s.count('(') - s.count(')')
    print(f, 'braces', b, 'parens', p)
    if b or p:
        ok = False
print('BALANCED' if ok else 'UNBALANCED')
