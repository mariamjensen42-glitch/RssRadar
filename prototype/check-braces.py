import re, sys
files = [
    'ui/feed/FeedListScreen.kt', 'ui/search/SearchScreen.kt',
    'ui/components/ArticleContextMenu.kt', 'ui/feed/FeedListViewModel.kt',
    'ui/search/SearchViewModel.kt', 'data/db/AppDatabase.kt',
    'data/FeedRepository.kt',
]
ok = True
for f in files:
    s = open(f, encoding='utf-8').read()
    s = re.sub(r'"""(.*?)"""', lambda m: re.sub(r'[{}()]', 'x', m.group(1)), s, flags=re.S)
    # remove line comments only outside strings (strings already brace-stripped)
    s = re.sub(r'//.*', '', s)
    s = re.sub(r'/\*.*?\*/', '', s, flags=re.S)
    b = s.count('{') - s.count('}')
    p = s.count('(') - s.count(')')
    print(f, 'braces', b, 'parens', p)
    if b or p:
        ok = False
print('BALANCED' if ok else 'UNBALANCED')
