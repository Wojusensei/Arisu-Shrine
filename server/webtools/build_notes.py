#!/usr/bin/env python3
"""文档站构建：把 notes/*.md 生成为 server/web/notes/ 下的静态 HTML。

用法：python3 server/webtools/build_notes.py
随后跑 make_web_fonts.py 重建字体子集，或直接跑 build_web.sh。

页面结构照官方文档的读法组织：左侧分节导航、面包屑、页内目录、上下页与相关页面。
正文只支持项目用得到的 Markdown 子集：二到四级标题、段落、无序/有序列表、
引用、围栏代码块、分隔线、管道表格、note/warning/tip/download 指令，以及行内
code/粗体/斜体/链接。所有文本先做 HTML 转义，不接收原始 HTML。
"""
from __future__ import annotations

import html
import json
import re
import shutil
from dataclasses import dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
WEB = ROOT / "server/web"
SRC = ROOT / "server/webtools/notes"
NOTES = WEB / "notes"
SITE = "有栖神社"
DOCS_TITLE = "有栖神社文档"
BASE_URL = "https://arisushrine.com"
GITHUB = "https://github.com/Wojusensei/Arisu-Shrine"
# 样式与字体分别版本化：只改样式时不必让浏览器重拉字体
ASSET_VERSION = "20261034"
FONT_VERSION = "20261032"
DOCS_UPDATED = "2026-10-27"


@dataclass(frozen=True)
class Section:
    key: str
    title: str
    summary: str
    order: int


SECTIONS = [
    Section("start", "开始使用", "安装、注册与界面导航。", 10),
    Section("guide", "使用指南", "茶话、祝部、游会、赛钱与客房。", 20),
    Section("account", "账号与安全", "设置、登录设备、举报与注销。", 30),
    Section("release", "版本与下载", "下载、安装要求与版本记录。", 40),
    Section("project", "项目", "技术栈、联系与社群。", 50),
    Section("about", "关于", "缘起与常见问题。", 60),
]
SECTION_BY_KEY = {s.key: s for s in SECTIONS}

SVG = 'viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"'
ICONS = {
    "book": f'<svg {SVG}><path d="M4 4.5A2.5 2.5 0 0 1 6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5V4.5Z"/><path d="M4 17.5h16"/></svg>',
    "compass": f'<svg {SVG}><circle cx="12" cy="12" r="9"/><path d="m15.5 8.5-2.1 5-5 2.1 2.1-5 5-2.1Z"/></svg>',
    "chat": f'<svg {SVG}><path d="M20 11.5a7.5 7.5 0 0 1-7.5 7.5H7l-3 2v-3.5A7.5 7.5 0 0 1 11.5 4h1A7.5 7.5 0 0 1 20 11.5Z"/></svg>',
    "lock": f'<svg {SVG}><rect x="5" y="10" width="14" height="10" rx="2"/><path d="M8 10V7a4 4 0 0 1 8 0v3"/></svg>',
    "download": f'<svg {SVG}><path d="M12 3v11"/><path d="m7.5 10 4.5 4.5L16.5 10"/><path d="M5 20h14"/></svg>',
    "code": f'<svg {SVG}><path d="m8 8-4 4 4 4"/><path d="m16 8 4 4-4 4"/><path d="m13.5 5-3 14"/></svg>',
    "torii": '<svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M0.8 3.7Q12 1.3 23.2 3.7L23.2 5.9Q12 3.5 0.8 5.9ZM2.7 6.1L21.3 6.1L21.3 7.9L2.7 7.9ZM10.7 7.9L13.3 7.9L13.3 10.7L10.7 10.7ZM3.5 10.7L20.5 10.7L20.5 12.5L3.5 12.5ZM5.7 7.9L8 7.9L8 22.2L5.7 22.2ZM16 7.9L18.3 7.9L18.3 22.2L16 22.2Z"/></svg>',
    "home": f'<svg {SVG}><path d="m3 10 9-7 9 7"/><path d="M5 9v11h14V9"/></svg>',
    "menu": f'<svg {SVG}><path d="M4 6h16M4 12h16M4 18h16"/></svg>',
    "list": f'<svg {SVG}><path d="M9 6h11M9 12h11M9 18h11"/><circle cx="4.5" cy="6" r=".9"/><circle cx="4.5" cy="12" r=".9"/><circle cx="4.5" cy="18" r=".9"/></svg>',
    "copy": f'<svg {SVG}><rect x="9" y="9" width="10" height="11" rx="2"/><path d="M15 9V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h3"/></svg>',
    "user": f'<svg {SVG}><circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/></svg>',
    "users": f'<svg {SVG}><circle cx="9" cy="8" r="3.5"/><path d="M2.5 20a6.5 6.5 0 0 1 13 0"/><path d="M16 5a3.5 3.5 0 0 1 0 7"/><path d="M17 14a6.5 6.5 0 0 1 4.5 6"/></svg>',
    "question": f'<svg {SVG}><circle cx="12" cy="12" r="9"/><path d="M9.5 9a2.5 2.5 0 1 1 4 2c-.9.7-1.5 1.2-1.5 2.5"/><path d="M12 17h.01"/></svg>',
    "history": f'<svg {SVG}><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></svg>',
    "search": f'<svg {SVG}><circle cx="11" cy="11" r="7"/><path d="m16.5 16.5 4 4"/></svg>',
    "arrow": f'<svg {SVG}><path d="M4 12h15"/><path d="m13 6 6 6-6 6"/></svg>',
    "chevron-left": f'<svg {SVG}><path d="m14 6-6 6 6 6"/></svg>',
    "chevron-right": f'<svg {SVG}><path d="m10 6 6 6-6 6"/></svg>',
}
SECTION_ICONS = {
    "start": "compass",
    "guide": "chat",
    "account": "lock",
    "release": "download",
    "project": "code",
    "about": "torii",
}


def icon(name: str) -> str:
    return ICONS[name]



@dataclass
class Heading:
    level: int
    text: str
    anchor: str


@dataclass
class Page:
    slug: str
    title: str
    summary: str
    section: str
    order: int
    body: str
    related: list[str]
    date: str | None = None
    html: str = ""
    headings: list[Heading] = field(default_factory=list)


@dataclass
class Release:
    version: str
    size: str
    apk_url: str
    date: str
    android: str = "Android 8.0"


THEME_PREFLIGHT = """<script>
// 主题在首帧之前定下来：点过切换就记住，否则跟随系统，避免闪错主题
try {
  var savedTheme = localStorage.getItem("arisu-web-theme");
  if (savedTheme === "light" || savedTheme === "dark") {
    document.documentElement.dataset.theme = savedTheme;
  }
  var darkNow = document.documentElement.dataset.theme
    ? document.documentElement.dataset.theme === "dark"
    : matchMedia("(prefers-color-scheme: dark)").matches;
  if (darkNow) document.querySelector('meta[name="theme-color"]').content = "#0A0E14";
} catch (e) {}
</script>"""

THEME_SCRIPT = """<script>
// 明暗切换：与落地页共用同一个 localStorage 键，来回走不丢选择
(function () {
  var root = document.documentElement;
  function effective() {
    return root.dataset.theme || (matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light");
  }
  document.getElementById("themeBtn").addEventListener("click", function () {
    var next = effective() === "dark" ? "light" : "dark";
    try { localStorage.setItem("arisu-web-theme", next); } catch (e) {}
    root.dataset.theme = next;
    document.querySelector('meta[name="theme-color"]').content = next === "dark" ? "#0A0E14" : "#EDE8E1";
  });
})();
</script>"""

TOC_SCRIPT = """<script>
// 页内目录跟随滚动高亮当前小节；手机端点一条后收起抽屉
(function () {
  document.querySelectorAll(".mobile-toc a").forEach(function (a) {
    a.addEventListener("click", function () {
      var d = a.closest("details");
      if (d) d.open = false;
    });
  });
  var links = Array.prototype.slice.call(document.querySelectorAll(".toc a"));
  if (!links.length || !("IntersectionObserver" in window)) return;
  var byId = {};
  links.forEach(function (a) { byId[a.getAttribute("href").slice(1)] = a; });
  links[0].classList.add("active");
  var headings = Object.keys(byId).map(function (id) { return document.getElementById(id); }).filter(Boolean);
  var observer = new IntersectionObserver(function (entries) {
    entries.forEach(function (entry) {
      if (!entry.isIntersecting) return;
      links.forEach(function (a) { a.classList.remove("active"); });
      var active = byId[entry.target.id];
      if (active) active.classList.add("active");
    });
  }, { rootMargin: "0px 0px -70% 0px", threshold: 0 });
  headings.forEach(function (h) { observer.observe(h); });
})();
</script>"""

COPY_SCRIPT = """<script>
// 复制群号；剪贴板不可用时提示手动复制
(function () {
  document.querySelectorAll("[data-copy]").forEach(function (button) {
    button.addEventListener("click", function () {
      var text = button.getAttribute("data-copy") || "";
      var label = button.querySelector(".qq-copy-label") || button;
      if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(text).then(function () {
          label.textContent = "已复制";
        }).catch(function () {
          label.textContent = "请手动复制";
        });
      } else {
        label.textContent = "请手动复制";
      }
      setTimeout(function () { label.textContent = "复制群号"; }, 1600);
    });
  });
})();
</script>"""


def parse_page(path: Path) -> Page:
    text = path.read_text(encoding="utf-8")
    lines = text.splitlines()
    if not lines or lines[0].strip() != "---":
        raise SystemExit(f"{path.name}: 缺少 frontmatter")
    try:
        end = lines.index("---", 1)
    except ValueError:
        raise SystemExit(f"{path.name}: frontmatter 没有收尾的 ---")

    meta: dict[str, str] = {}
    for line in lines[1:end]:
        if not line.strip():
            continue
        if ":" not in line:
            raise SystemExit(f"{path.name}: frontmatter 行缺少冒号：{line}")
        key, value = line.split(":", 1)
        meta[key.strip()] = value.strip()

    for key in ("slug", "title", "summary", "section", "order"):
        if not meta.get(key):
            raise SystemExit(f"{path.name}: frontmatter 缺少 {key}")

    if not re.fullmatch(r"[a-z0-9-]+", meta["slug"]):
        raise SystemExit(f"{path.name}: slug 只允许小写字母、数字与连字符")
    if meta["section"] not in SECTION_BY_KEY:
        raise SystemExit(f"{path.name}: 未知 section {meta['section']}")
    if meta.get("date") and not re.fullmatch(r"\d{4}-\d{2}-\d{2}", meta["date"]):
        raise SystemExit(f"{path.name}: date 需要 YYYY-MM-DD")

    try:
        order = int(meta["order"])
    except ValueError:
        raise SystemExit(f"{path.name}: order 需要整数")

    related = [x.strip() for x in meta.get("related", "").split(",") if x.strip()]

    return Page(
        slug=meta["slug"],
        title=meta["title"],
        summary=meta["summary"],
        section=meta["section"],
        order=order,
        body="\n".join(lines[end + 1:]).strip() + "\n",
        related=related,
        date=meta.get("date"),
    )


def apply_placeholders(text: str, release: Release) -> str:
    values = {
        "version": release.version,
        "release_date": release.date,
        "apk_size": release.size,
        "apk_url": release.apk_url,
        "android": release.android,
    }
    for key, value in values.items():
        text = text.replace("{{" + key + "}}", value)
    if re.search(r"\{\{[a-z_]+\}\}", text):
        raise SystemExit("正文里还有没替换的占位符")
    return text


def release_info(pages: list[Page]) -> Release:
    landing = (WEB / "index.html").read_text(encoding="utf-8")
    apk = re.search(r'href="(/uploads/arisu-[^"]+\.apk)"', landing)
    ver = re.search(r"当前版本\s+(v[0-9][0-9.]*)\s*·\s*([0-9]+\s*MB)", landing)
    if not apk or not ver:
        raise SystemExit("index.html 里找不到当前版本或 APK 地址，先同步落地页")
    changelog = next((p for p in pages if p.slug == "changelog"), None)
    return Release(
        version=ver.group(1),
        size=ver.group(2),
        apk_url=apk.group(1),
        date=changelog.date if changelog and changelog.date else DOCS_UPDATED,
    )


def split_row(line: str) -> list[str]:
    line = line.strip()
    if line.startswith("|"):
        line = line[1:]
    if line.endswith("|"):
        line = line[:-1]
    return [cell.strip() for cell in line.split("|")]


def render_table(header: list[str], rows: list[list[str]]) -> str:
    th = "".join(f"<th>{render_inline(c)}</th>" for c in header)
    body = "".join(
        "<tr>" + "".join(f"<td>{render_inline(c)}</td>" for c in row) + "</tr>"
        for row in rows
    )
    return (
        '<div class="table-wrap"><table><thead><tr>'
        + th
        + "</tr></thead><tbody>"
        + body
        + "</tbody></table></div>"
    )


def plain_text(value: str) -> str:
    value = re.sub(r"`([^`]+)`", r"\1", value)
    value = re.sub(r"\[([^\]]+)\]\([^)]+\)", r"\1", value)
    value = value.replace("**", "").replace("*", "")
    return html.unescape(value).strip()


def safe_url(url: str) -> bool:
    raw = html.unescape(url).strip()
    return raw.startswith(("/", "#")) or re.match(r"^(https?://|mailto:)", raw, re.I) is not None


def render_inline(text: str) -> str:
    text = html.escape(text, quote=True)

    codes: list[str] = []

    def stash(m: re.Match[str]) -> str:
        codes.append(m.group(1))
        return f"\x00{len(codes) - 1}\x00"

    text = re.sub(r"`([^`]+)`", stash, text)

    def link(m: re.Match[str]) -> str:
        label, url = m.group(1), m.group(2)
        if not safe_url(url):
            return m.group(0)
        external = re.match(r"^https?://", html.unescape(url), re.I) is not None
        attrs = ' target="_blank" rel="noopener"' if external else ""
        return f'<a href="{url}"{attrs}>{label}</a>'

    text = re.sub(r"\[([^\]]+)\]\(([^)\s]+)\)", link, text)
    text = re.sub(r"\*\*([^*]+)\*\*", r"<strong>\1</strong>", text)
    text = re.sub(r"(?<!\*)\*([^*]+)\*(?!\*)", r"<em>\1</em>", text)

    def restore(m: re.Match[str]) -> str:
        return f"<code>{codes[int(m.group(1))]}</code>"

    return re.sub(r"\x00(\d+)\x00", restore, text)


def download_button(release: Release) -> str:
    return (
        f'<a class="download-button" href="{html.escape(release.apk_url)}" download>'
        f'<span class="download-line">{icon("download")}<span class="download-title">下载安卓客户端</span></span>'
        f'<span class="download-meta">{html.escape(release.version)} · {html.escape(release.size)}</span>'
        "</a>"
    )


def parse_changelog(body: str) -> list[dict[str, str]]:
    entries: list[dict[str, str]] = []
    for line in body.splitlines():
        s = line.strip()
        if not s.startswith("|"):
            continue
        cells = split_row(s)
        if len(cells) < 4:
            continue
        if cells[0] == "版本" or set(cells[0]) <= set("-: "):
            continue
        entries.append(
            {
                "version": cells[0],
                "date": cells[1],
                "type": cells[2],
                "note": cells[3],
            }
        )
    return entries


def render_markdown(
    md: str,
    release: Release,
    releases: list[dict[str, str]],
    *,
    with_headings: bool = True,
) -> tuple[str, list[Heading]]:
    lines = md.splitlines()
    out: list[str] = []
    headings: list[Heading] = []
    heading_no = 0
    i = 0

    while i < len(lines):
        line = lines[i]
        s = line.strip()
        if not s:
            i += 1
            continue

        if s.startswith(":::"):
            kind = s[3:].strip().lower()
            if kind == "download":
                out.append(download_button(release))
                i += 1
                if i < len(lines) and lines[i].strip() == ":::":
                    i += 1
                continue
            if kind.startswith("qq "):
                number = kind.split(None, 1)[1].strip()
                if not re.fullmatch(r"\d{5,12}", number):
                    raise SystemExit("qq 指令需要 5–12 位群号")
                out.append(
                    '<div class="qq-card"><div>'
                    '<span class="qq-label">QQ 群</span>'
                    f'<span class="qq-number">{number}</span>'
                    "</div>"
                    f'<button class="qq-copy" type="button" data-copy="{number}">{icon("copy")}<span class="qq-copy-label">复制群号</span></button>'
                    "</div>"
                )
                i += 1
                if i < len(lines) and lines[i].strip() == ":::":
                    i += 1
                continue
            if kind.startswith("releases"):
                match = re.fullmatch(r"releases(?:\s+(\d+))?", kind)
                count = int(match.group(1)) if match and match.group(1) else 3
                parts = []
                for entry in releases[:count]:
                    note = entry["note"]
                    label = note if note.startswith(entry["type"]) else f'{entry["type"]}：{note}'
                    parts.append(
                        f'<h3>{render_inline(entry["version"] + " · " + entry["date"])}</h3>'
                        f"<ul><li>{render_inline(label)}</li></ul>"
                    )
                out.append("".join(parts))
                i += 1
                if i < len(lines) and lines[i].strip() == ":::":
                    i += 1
                continue
            if kind in ("note", "warning", "tip"):
                i += 1
                buf: list[str] = []
                while i < len(lines) and lines[i].strip() != ":::":
                    buf.append(lines[i])
                    i += 1
                if i < len(lines):
                    i += 1
                inner, _ = render_markdown(
                    "\n".join(buf), release, releases, with_headings=False
                )
                title = {"note": "注意", "warning": "警告", "tip": "提示"}[kind]
                out.append(
                    f'<div class="admonition {kind}">'
                    f'<p class="admonition-title">{title}</p>{inner}</div>'
                )
                continue
            raise SystemExit(f"未知指令：{kind}")

        if s.startswith("```"):
            lang = s[3:].strip()
            i += 1
            buf = []
            while i < len(lines) and not lines[i].strip().startswith("```"):
                buf.append(lines[i])
                i += 1
            if i < len(lines):
                i += 1
            code = "\n".join(buf)
            cls = f' class="language-{html.escape(lang)}"' if lang else ""
            out.append(f"<pre><code{cls}>{html.escape(code)}</code></pre>")
            continue

        if s.startswith("|") and i + 1 < len(lines):
            sep = lines[i + 1].strip()
            if re.fullmatch(r"\|[\s:|-]+\|", sep):
                header = split_row(s)
                i += 2
                rows: list[list[str]] = []
                while i < len(lines) and lines[i].strip().startswith("|"):
                    rows.append(split_row(lines[i].strip()))
                    i += 1
                out.append(render_table(header, rows))
                continue

        m = re.match(r"^(#{2,4})\s+(.*)$", s)
        if m:
            level = len(m.group(1))
            raw = m.group(2)
            attrs = ""
            if with_headings:
                heading_no += 1
                anchor = f"s{heading_no}"
                headings.append(Heading(level, plain_text(raw), anchor))
                attrs = f' id="{anchor}"'
            out.append(f"<h{level}{attrs}>{render_inline(raw)}</h{level}>")
            i += 1
            continue

        if s.startswith("# "):
            raise SystemExit("正文不要使用一级标题，页面标题由 frontmatter 提供")

        if re.fullmatch(r"-{3,}", s):
            out.append("<hr>")
            i += 1
            continue

        if s.startswith(">"):
            buf = []
            while i < len(lines) and lines[i].strip().startswith(">"):
                buf.append(lines[i].strip()[1:].strip())
                i += 1
            out.append("<blockquote><p>" + render_inline("".join(buf)) + "</p></blockquote>")
            continue

        if re.match(r"^-\s+", s):
            items = []
            while i < len(lines) and re.match(r"^-\s+", lines[i].strip()):
                items.append(re.sub(r"^-\s+", "", lines[i].strip()))
                i += 1
            out.append("<ul>" + "".join(f"<li>{render_inline(x)}</li>" for x in items) + "</ul>")
            continue

        if re.match(r"^\d+\.\s+", s):
            items = []
            while i < len(lines) and re.match(r"^\d+\.\s+", lines[i].strip()):
                items.append(re.sub(r"^\d+\.\s+", "", lines[i].strip()))
                i += 1
            out.append("<ol>" + "".join(f"<li>{render_inline(x)}</li>" for x in items) + "</ol>")
            continue

        buf = [s]
        i += 1
        while i < len(lines) and lines[i].strip() and not is_block_start(lines[i]):
            buf.append(lines[i].strip())
            i += 1
        out.append("<p>" + render_inline("".join(buf)) + "</p>")

    return "\n".join(out), headings


def is_block_start(line: str) -> bool:
    s = line.strip()
    return bool(
        s.startswith(":::")
        or s.startswith("```")
        or s.startswith(">")
        or s.startswith("|")
        or re.match(r"^#{2,4}\s+", s)
        or re.match(r"^-\s+", s)
        or re.match(r"^\d+\.\s+", s)
        or re.fullmatch(r"-{3,}", s)
    )


def nav_html(pages_by_section: dict[str, list[Page]], current_slug: str | None) -> str:
    parts = []
    home = icon("home")
    if current_slug is None:
        parts.append(f'<a class="nav-home current" aria-current="page" href="/notes/">{home}文档首页</a>')
    else:
        parts.append(f'<a class="nav-home" href="/notes/">{home}文档首页</a>')
    for section in SECTIONS:
        first = pages_by_section[section.key][0]
        parts.append(
            f'<div class="nav-section"><a class="nav-section-title" href="/notes/{first.slug}/">{icon(SECTION_ICONS[section.key])}{html.escape(section.title)}</a><ul>'
        )
        for page in pages_by_section[section.key]:
            if page.slug == current_slug:
                parts.append(
                    f'<li><a class="current" aria-current="page" href="/notes/{page.slug}/">{html.escape(page.title)}</a></li>'
                )
            else:
                parts.append(f'<li><a href="/notes/{page.slug}/">{html.escape(page.title)}</a></li>')
        parts.append("</ul></div>")
    return "".join(parts)


def crumbs_html(page: Page) -> str:
    section = SECTION_BY_KEY[page.section]
    return (
        '<nav class="crumbs" aria-label="面包屑">'
        '<a href="/notes/">文档</a><span class="sep">/</span>'
        f'<a href="/notes/#{section.key}">{html.escape(section.title)}</a><span class="sep">/</span>'
        f'<span>{html.escape(page.title)}</span>'
        "</nav>"
    )


def toc_items(headings: list[Heading]) -> str:
    items = []
    for heading in headings:
        if heading.level not in (2, 3):
            continue
        cls = ' class="toc-sub"' if heading.level == 3 else ""
        items.append(
            f'<li{cls}><a href="#{heading.anchor}">{html.escape(heading.text)}</a></li>'
        )
    return "".join(items)


def toc_html(headings: list[Heading]) -> str:
    items = toc_items(headings)
    if not items:
        return ""
    return (
        '<nav class="toc" aria-label="本页目录">'
        '<p class="toc-title">本页目录</p><ul>' + items + "</ul></nav>"
    )


def mobile_toc_html(headings: list[Heading]) -> str:
    items = toc_items(headings)
    if not items:
        return ""
    return f'<details class="mobile-toc"><summary>{icon("list")}本页目录</summary><ul>{items}</ul></details>'


def related_html(page: Page, page_by_slug: dict[str, Page]) -> str:
    links = []
    for slug in page.related:
        target = page_by_slug.get(slug)
        if target:
            links.append(
                f'<li><a href="/notes/{target.slug}/">{html.escape(target.title)}</a></li>'
            )
    if not links:
        return ""
    return (
        '<div class="related"><p class="related-title">参见</p><ul>'
        + "".join(links)
        + "</ul></div>"
    )


def pager_html(prev: Page | None, nxt: Page | None) -> str:
    parts = []
    if prev:
        parts.append(
            f'<a class="prev" href="/notes/{prev.slug}/"><span>{icon("chevron-left")}上一页</span>'
            f"<strong>{html.escape(prev.title)}</strong></a>"
        )
    if nxt:
        parts.append(
            f'<a class="next" href="/notes/{nxt.slug}/"><span>下一页{icon("chevron-right")}</span>'
            f"<strong>{html.escape(nxt.title)}</strong></a>"
        )
    if not parts:
        return ""
    return '<nav class="pager" aria-label="翻页">' + "".join(parts) + "</nav>"


def header(active: str) -> str:
    docs = ' class="current" aria-current="page"' if active == "docs" else ""
    download = ' class="current" aria-current="page"' if active == "download" else ""
    return f"""<header class="site-head"><div class="site-head-inner">
  <button class="menu-btn" id="menuBtn" type="button" aria-label="文档目录" aria-expanded="false">{icon("menu")}</button>
  <a class="brand" href="/" aria-label="有栖神社">
    <img src="/assets/logo_red.png" width="30" height="30" alt="">
    <span>有栖神社</span>
  </a>
  <nav class="site-nav" aria-label="站点导航">
    <a{docs} href="/notes/">文档</a>
    <a{download} href="/notes/download/">下载</a>
  </nav>
  <div class="site-tools">
    <button class="search-trigger" id="searchBtn" type="button" aria-label="搜索文档">{icon("search")}<span class="search-label">搜索</span><kbd>/</kbd></button>
    <button class="theme" id="themeBtn" type="button" aria-label="切换明暗">
      <svg class="ic-moon" viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79Z"/></svg>
      <svg class="ic-sun" viewBox="0 0 24 24" aria-hidden="true"><g fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"><circle cx="12" cy="12" r="4.2"/><path d="M12 2.8v2.2M12 19v2.2M2.8 12H5M19 12h2.2M5.5 5.5l1.6 1.6M16.9 16.9l1.6 1.6M18.5 5.5l-1.6 1.6M7.1 16.9l-1.6 1.6"/></g></svg>
    </button>
    <a class="github" href="{GITHUB}" target="_blank" rel="noopener" aria-label="GitHub 仓库" title="GitHub 仓库">
      <svg viewBox="0 0 16 16" aria-hidden="true"><path d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27s1.36.09 2 .27c1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.01 8.01 0 0 0 16 8c0-4.42-3.58-8-8-8Z"/></svg>
    </a>
  </div>
</div></header>"""



SEARCH_HTML = f"""<dialog class="search-dialog" id="searchDialog" aria-label="搜索文档">
  <form method="dialog" class="search-head">
    {icon("search")}
    <input id="searchInput" type="search" placeholder="搜索文档" autocomplete="off" enterkeyhint="search">
    <button class="search-close" value="cancel" aria-label="关闭">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" aria-hidden="true"><path d="m6 6 12 12M18 6 6 18"/></svg>
    </button>
  </form>
  <div class="search-results" id="searchResults"></div>
</dialog>"""

SEARCH_SCRIPT = """<script>
// 文档搜索：首次打开时加载静态索引，结果用 DOM 节点构建
(function () {
  var button = document.getElementById("searchBtn");
  var dialog = document.getElementById("searchDialog");
  if (!button || !dialog) return;
  var input = document.getElementById("searchInput");
  var results = document.getElementById("searchResults");
  var index = null;
  var loading = false;

  function message(text) {
    results.textContent = "";
    var note = document.createElement("p");
    note.className = "search-note";
    note.textContent = text;
    results.appendChild(note);
  }

  function render(query) {
    var q = (query || "").trim().toLowerCase();
    results.textContent = "";
    if (!index) return;
    if (!q) { message("输入关键词搜索文档。"); return; }
    var hits = [];
    index.forEach(function (page) {
      var title = page.title.toLowerCase();
      var summary = page.summary.toLowerCase();
      var section = page.section.toLowerCase();
      var score = 0;
      if (title === q) score = 5;
      else if (title.indexOf(q) >= 0) score = 4;
      else if (summary.indexOf(q) >= 0) score = 2;
      else if (section.indexOf(q) >= 0) score = 1;
      if (score) hits.push({ page: page, heading: null, score: score });
      (page.headings || []).forEach(function (heading) {
        if (heading.text.toLowerCase().indexOf(q) >= 0) {
          hits.push({ page: page, heading: heading, score: 3 });
        }
      });
    });
    hits.sort(function (a, b) {
      return b.score - a.score || a.page.title.localeCompare(b.page.title, "zh");
    });
    hits = hits.slice(0, 12);
    if (!hits.length) { message("没有找到匹配的页面。"); return; }
    hits.forEach(function (hit) {
      var link = document.createElement("a");
      link.className = "search-result";
      link.href = hit.page.url + (hit.heading ? "#" + hit.heading.anchor : "");
      var title = document.createElement("strong");
      title.textContent = hit.heading ? hit.heading.text : hit.page.title;
      var meta = document.createElement("span");
      meta.textContent = hit.page.section + " / " + hit.page.title;
      link.appendChild(title);
      link.appendChild(meta);
      results.appendChild(link);
    });
  }

  function load() {
    if (index || loading) return;
    loading = true;
    message("加载中…");
    fetch("/notes/search.json?v=__ASSET_VERSION__")
      .then(function (response) { return response.json(); })
      .then(function (data) { index = data; loading = false; render(input.value); })
      .catch(function () { loading = false; message("搜索索引加载失败。"); });
  }

  function open() {
    if (!dialog.open) {
      if (typeof dialog.showModal === "function") dialog.showModal();
      else dialog.setAttribute("open", "");
    }
    input.focus();
    load();
  }

  button.addEventListener("click", open);
  input.addEventListener("input", function () { render(input.value); });
  document.addEventListener("keydown", function (event) {
    var tag = document.activeElement && document.activeElement.tagName;
    if (event.key === "/" && tag !== "INPUT" && tag !== "TEXTAREA") {
      event.preventDefault();
      open();
    }
  });
  dialog.addEventListener("click", function (event) {
    if (event.target === dialog) dialog.close();
  });
})();
</script>"""

NAV_SCRIPT = """<script>
// 手机端页头菜单：打开/关闭文档目录抽屉
(function () {
  var button = document.getElementById("menuBtn");
  var nav = document.querySelector(".mobile-nav");
  if (!button || !nav) return;
  button.addEventListener("click", function () { nav.open = !nav.open; });
  nav.addEventListener("toggle", function () {
    button.setAttribute("aria-expanded", nav.open ? "true" : "false");
  });
})();
</script>"""


def footer(release: Release) -> str:
    return f"""<footer class="site-foot">
  <span>{DOCS_TITLE} · 适用于客户端 {html.escape(release.version)} · 更新于 {DOCS_UPDATED}</span>
  <span class="foot-links">
    <a href="/notes/download/">下载客户端</a>
    <a href="/notes/contact/">用户群</a>
    <a href="{GITHUB}" target="_blank" rel="noopener">GitHub 仓库</a>
  </span>
</footer>"""


def page_shell(
    *,
    title: str,
    description: str,
    canonical: str,
    og_type: str,
    content: str,
    nav: str,
    toc: str,
    release: Release,
    active: str,
    toc_script: bool = False,
) -> str:
    shell_class = "docs-shell" if toc else "docs-shell no-toc"
    return f"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{html.escape(title)}</title>
<meta name="description" content="{html.escape(description)}">
<meta property="og:type" content="{og_type}">
<meta property="og:site_name" content="{SITE}">
<meta property="og:title" content="{html.escape(title)}">
<meta property="og:description" content="{html.escape(description)}">
<meta property="og:url" content="{canonical}">
<meta property="og:image" content="{BASE_URL}/assets/logo_red.png">
<meta name="theme-color" content="#EDE8E1">
<link rel="canonical" href="{canonical}">
<link rel="icon" type="image/png" href="/assets/logo_red.png">
<link rel="apple-touch-icon" href="/assets/logo_red.png">
<link rel="preload" href="/assets/fonts/noto-serif-400.woff?v={FONT_VERSION}" as="font" type="font/woff" crossorigin>
{THEME_PREFLIGHT}
<link rel="stylesheet" href="/assets/notes.css?v={ASSET_VERSION}">
</head>
<body>
{header(active)}
<div class="{shell_class}">
  <details class="mobile-nav"><summary>{icon("menu")}文档目录</summary><nav aria-label="文档目录">{nav}</nav></details>
  <aside class="sidebar"><nav aria-label="文档目录">{nav}</nav></aside>
  <main class="doc">
{content}
  </main>
  {toc}
</div>
{footer(release)}
{SEARCH_HTML}
{THEME_SCRIPT}
{TOC_SCRIPT if toc_script else ""}
{COPY_SCRIPT if "data-copy" in content else ""}
{NAV_SCRIPT}
{SEARCH_SCRIPT.replace("__ASSET_VERSION__", ASSET_VERSION)}
</body>
</html>
"""


def render_index(pages_by_section: dict[str, list[Page]], release: Release, nav: str) -> str:
    blocks = []
    for section in SECTIONS:
        first = pages_by_section[section.key][0]
        section_pages = pages_by_section[section.key]
        last_row_start = ((len(section_pages) - 1) // 2) * 2
        parts = []
        for index, p in enumerate(section_pages):
            cls = "index-item last-row" if index >= last_row_start else "index-item"
            parts.append(
                f'<a class="{cls}" href="/notes/{p.slug}/">'
                f'<span class="index-item-text"><strong>{html.escape(p.title)}</strong>'
                f'<span>{html.escape(p.summary)}</span></span>'
                f'<span class="go-badge" aria-hidden="true">{icon("arrow")}</span></a>'
            )
        items = "".join(parts)
        blocks.append(
            f'<section class="index-section" id="{section.key}">'
            f'<a class="index-section-title" href="/notes/{first.slug}/">{icon(SECTION_ICONS[section.key])}<div class="index-section-text"><h2>{html.escape(section.title)}</h2><span class="index-section-summary">{html.escape(section.summary)}</span></div><span class="go-badge" aria-hidden="true">{icon("arrow")}</span></a>'
            f'<div class="index-list">{items}</div></section>'
        )
    content = f"""<div class="doc-title">{icon("book")}<h1>{DOCS_TITLE}</h1></div>
<p class="deck">安装、注册、功能与账号管理。适用于客户端 {html.escape(release.version)}。<br><span class="conventions">文中 <code>代码</code> 表示按钮、标签或输入值；路径用 <code>→</code> 连接。</span></p>
<a class="download-callout" href="/notes/download/">
  <span class="kicker">当前稳定版</span>
  <span class="title">{icon("download")}下载客户端</span>
  <span class="meta">{html.escape(release.version)} · {html.escape(release.size)} · {html.escape(release.android)} 及以上</span>
</a>
{"".join(blocks)}"""
    return page_shell(
        title=DOCS_TITLE,
        description="有栖神社 Android 客户端文档：安装、注册、功能与账号管理。",
        canonical=f"{BASE_URL}/notes/",
        og_type="website",
        content=content,
        nav=nav,
        toc="",
        release=release,
        active="docs",
    )


def render_page(
    page: Page,
    page_by_slug: dict[str, Page],
    prev: Page | None,
    nxt: Page | None,
    release: Release,
    nav: str,
) -> str:
    toc = toc_html(page.headings)
    content = f"""{crumbs_html(page)}
<div class="doc-title">{icon(SECTION_ICONS[page.section])}<h1>{html.escape(page.title)}</h1></div>
<p class="deck">{html.escape(page.summary)}</p>
{mobile_toc_html(page.headings)}
<article class="doc-body">
{page.html}
</article>
{related_html(page, page_by_slug)}
{pager_html(prev, nxt)}"""
    return page_shell(
        title=f"{page.title} — {DOCS_TITLE}",
        description=page.summary,
        canonical=f"{BASE_URL}/notes/{page.slug}/",
        og_type="article",
        content=content,
        nav=nav,
        toc=toc,
        release=release,
        active="download" if page.slug == "download" else "docs",
        toc_script=bool(toc),
    )


def legacy_redirect(target: str) -> str:
    return f"""<!DOCTYPE html>
<html lang="zh-CN"><head><meta charset="utf-8">
<meta http-equiv="refresh" content="0; url={target}">
<link rel="canonical" href="{BASE_URL}{target}">
<title>页面已移动 — {DOCS_TITLE}</title></head>
<body><h1>页面已移动</h1><p><a href="{target}">前往文档首页</a></p></body></html>
"""


def main() -> None:
    sources = sorted(SRC.glob("*.md"))
    if not sources:
        raise SystemExit(f"{SRC} 下没有 Markdown 源文件")

    pages = [parse_page(path) for path in sources]
    slugs = [p.slug for p in pages]
    if len(slugs) != len(set(slugs)):
        raise SystemExit("slug 有重复")

    release = release_info(pages)
    changelog_page = next((p for p in pages if p.slug == "changelog"), None)
    if not changelog_page:
        raise SystemExit("缺少 changelog 页面，下载页无法生成更新公告")
    releases = parse_changelog(changelog_page.body)

    for page in pages:
        body = apply_placeholders(page.body, release)
        page.html, page.headings = render_markdown(body, release, releases)

    page_by_slug = {p.slug: p for p in pages}
    for page in pages:
        for slug in page.related:
            if slug not in page_by_slug:
                raise SystemExit(f"{page.slug}: related 指向不存在的页面 {slug}")

    pages.sort(key=lambda p: (SECTION_BY_KEY[p.section].order, p.order))
    pages_by_section: dict[str, list[Page]] = {s.key: [] for s in SECTIONS}
    for page in pages:
        pages_by_section[page.section].append(page)

    if NOTES.exists():
        shutil.rmtree(NOTES)
    NOTES.mkdir(parents=True)

    (NOTES / "index.html").write_text(
        render_index(pages_by_section, release, nav_html(pages_by_section, None)),
        encoding="utf-8",
    )

    for index, page in enumerate(pages):
        prev = pages[index - 1] if index > 0 else None
        nxt = pages[index + 1] if index + 1 < len(pages) else None
        nav = nav_html(pages_by_section, page.slug)
        target = NOTES / page.slug
        target.mkdir()
        (target / "index.html").write_text(
            render_page(page, page_by_slug, prev, nxt, release, nav),
            encoding="utf-8",
        )

    (NOTES / "guide").mkdir()
    (NOTES / "guide" / "index.html").write_text(legacy_redirect("/notes/"), encoding="utf-8")

    search = [
        {
            "title": page.title,
            "url": f"/notes/{page.slug}/",
            "section": SECTION_BY_KEY[page.section].title,
            "summary": page.summary,
            "headings": [
                {"text": heading.text, "anchor": heading.anchor}
                for heading in page.headings
                if heading.level in (2, 3)
            ],
        }
        for page in pages
    ]
    (NOTES / "search.json").write_text(
        json.dumps(search, ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8",
    )

    print(f"{len(pages)} 页 → {NOTES.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
