# Kotlin 块注释可嵌套：用深度计数扫描未闭合注释
import sys

path = sys.argv[1]
data = open(path, 'rb').read().decode('utf-8')
lines = data.split('\n')
depth = 0
for i, line in enumerate(lines, 1):
    j = 0
    n = len(line)
    while j < n:
        c = line[j]
        nxt = line[j + 1] if j + 1 < n else ''
        if depth > 0:
            if c == '*' and nxt == '/':
                depth -= 1
                j += 2
                continue
            # 注意：注释内的 /* 会开启嵌套！
            if c == '/' and nxt == '*':
                depth += 1
                j += 2
                continue
        else:
            if c == '/' and nxt == '/':
                break  # 行注释
            if c == '/' and nxt == '*':
                depth += 1
                j += 2
                continue
            if c == '"' or c == "'":
                q = c
                j += 1
                while j < n:
                    if line[j] == '\\':
                        j += 2
                        continue
                    if line[j] == q:
                        break
                    j += 1
        j += 1
    if depth > 0:
        print(f'line {i}: depth={depth} -> {line.strip()[:70]}')
print('FINAL depth:', depth)
