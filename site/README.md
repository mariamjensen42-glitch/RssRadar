# RssRadar 产品介绍页

本目录是 RssRadar 的产品介绍站点源码，由 `.github/workflows/pages.yml`
在 `dev` 分支 `site/**` 变更时自动部署到 GitHub Pages：

> https://mariamjensen42-glitch.github.io/RssRadar/

## 本地预览

直接用任意静态服务器：

```bash
cd site
python -m http.server 8000
# 打开 http://localhost:8000
```

## 文件

- `index.html` — 单页结构
- `styles.css` — 暗色主题，主色取自应用图标
- `script.js`  — 平滑锚点滚动
- `assets/icon.png` — 应用图标（来自 `design/`）

## 内容事实原则

页面中所有数字（命名空间、路由数、并发上限、版本号、技术栈版本等）
均来自仓库实际数据，未做虚构。
