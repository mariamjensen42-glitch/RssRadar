/* 更新日志渲染：读 changelog-data.js 的 RSSRADAR_RELEASES，按日期倒序输出。
 * 数据结构见 changelog-data.js 头注释；追加新版本只改数据文件，本文件不用动。 */
(function () {
  "use strict";

  /* type → 展示标签与配色（与站点主题一致，feature 用 accent 青色系） */
  var TYPE_META = {
    feature: { label: "新功能", cls: "cl-tag-feature" },
    improvement: { label: "功能优化", cls: "cl-tag-improvement" },
    fix: { label: "问题修复", cls: "cl-tag-fix" },
  };

  function el(tag, cls, text) {
    var node = document.createElement(tag);
    if (cls) node.className = cls;
    if (text != null) node.textContent = text;
    return node;
  }

  function render() {
    var root = document.getElementById("changelog");
    var releases = (window.RSSRADAR_RELEASES || []).slice()
      .sort(function (a, b) { return a.date < b.date ? 1 : -1; });
    if (!root) return;
    root.textContent = "";

    releases.forEach(function (rel) {
      var item = el("article", "cl-item");

      var head = el("div", "cl-head");
      var title = el("h3", "cl-version");
      title.appendChild(el("span", null, rel.version));
      if (rel === releases[0]) title.appendChild(el("span", "cl-latest", "最新"));
      head.appendChild(title);
      // <time> 语义化，datetime 属性方便机器读
      var time = el("time", "cl-date", rel.date);
      time.setAttribute("datetime", rel.date);
      head.appendChild(time);
      item.appendChild(head);

      (rel.sections || []).forEach(function (sec) {
        var meta = TYPE_META[sec.type] || { label: sec.type, cls: "cl-tag-improvement" };
        var group = el("div", "cl-group");
        var tag = el("span", "cl-tag " + meta.cls, meta.label);
        group.appendChild(tag);
        var ul = el("ul", "cl-items");
        (sec.items || []).forEach(function (text) {
          ul.appendChild(el("li", null, text));
        });
        group.appendChild(ul);
        item.appendChild(group);
      });

      root.appendChild(item);
    });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", render);
  } else {
    render();
  }
})();
