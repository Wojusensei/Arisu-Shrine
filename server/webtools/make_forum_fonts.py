#!/usr/bin/env python3
"""网页端字体构建：把 App 打包的四字重思源宋体切成网页端子集（按需分档）。

与 make_web_fonts.py（落地页，按页面字符集）不同：论坛正文是用户写的，
字符不可预知，所以固定切 GB2312 全表（7445 字，覆盖绝大多数正文）。

分档策略（2026-11，首屏体积）：
  * common 档＝ASCII ＋ GB2312 一级字（3755）＋ 符号区（682）＋ 少量表外常用字。
    正文 99.9% 的字符都在这里，首屏只下载这一档。
  * rare 档＝GB2312 二级字（3008，生僻字）－ common。
    由 app.css 里 rare @font-face 的 unicode-range 精确声明；浏览器只有
    页面真的出现二级字时才会去取，且只取用到的那个字重。
  * 两档并集 == 旧版单档的字符集（脚本里 assert 校验，覆盖不会缩水）。
  * 单字重两档相加 ≈ 旧单档的 1.02 倍；单字重 worst case 与旧版持平，
    但常规首屏从「四字重全量」降为「四字重 common」。

用法：python3 server/webtools/make_forum_fonts.py
产物：server/internal/web/app-assets/fonts/noto-serif-{400,500,600,700}-{common,rare}.woff

格式沿用 make_web_fonts.py 的口径：本机 fontTools 缺 brotli，出 woff（zlib）。
若日后装了 brotli，可把 FLAVOR 改 woff2 并同步改 app.css 的 format()/文件名，
体积还能再降约 40%（common 400 实测 1.07MB → 0.78MB）。

改完字符集必须同步 app.css 的 unicode-range：本脚本会校验 app.css 里 rare
unicode-range 与实际切出的字集是否一致，不一致时打印醒目警告。
"""
import io
import re
import string
import sys
from pathlib import Path

from fontTools.subset import Options, Subsetter, load_font, save_font

ROOT = Path(__file__).resolve().parents[2]
FONT_SRC = str(ROOT / "app/src/main/res/font/noto_serif_{weight}.ttf")
OUT_DIR = ROOT / "server/internal/web/app-assets/fonts"
CSS = ROOT / "server/internal/web/app-assets/app.css"
WEB = ROOT / "server/internal/web"
WEIGHTS = (400, 500, 600, 700)
FLAVOR = "woff"

# GB2312 编码分区：B0-D7 一级汉字（常用，3755）、D8-F7 二级汉字（生僻，3008），
# 其余行（A1-A9）是符号区。分档只按行号切，不做频率猜测。
LEVEL1_ROWS = range(0xB0, 0xD8)
LEVEL2_ROWS = range(0xD8, 0xF8)

# 表外常用字：〇（数字零）、噸/邨（社内用语）、円（赛钱单位，安卓端有字形）、
# 日式标点与常用符号。「」『』·—… 在 GB2312 符号区内已有，这里只是显式列出便于检索。
COMMON_EXTRA = "〇噸邨円「」『』·—…"


def gb2312_tiers() -> tuple[set[str], set[str], set[str]]:
    """返回（一级汉字, 二级汉字, 符号区字）。空位跳过。"""
    level1: set[str] = set()
    level2: set[str] = set()
    symbols: set[str] = set()
    for hi in range(0xA1, 0xF8):
        for lo in range(0xA1, 0xFF):
            try:
                ch = bytes((hi, lo)).decode("gb2312")
            except UnicodeDecodeError:
                continue  # GB2312 空位
            if hi in LEVEL1_ROWS:
                level1.add(ch)
            elif hi in LEVEL2_ROWS:
                level2.add(ch)
            else:
                symbols.add(ch)
    return level1, level2, symbols


def ui_chars() -> set[str]:
    """扫模板/前端脚本/Go 文案里的非 ASCII 字符。

    这些字出现在页面上就意味着对应字重会被下载；如果某个界面字落在 rare 档，
    打开注册页/设置页都会多拉一整份 rare，白白多 630KB。所以界面文案里的字
    一律并进 common（它们数量很小，几十个）。字符串里的 emoji 等字体没有的
    字符由调用方按 cmap 过滤。
    """
    chars: set[str] = set()
    for path in list(WEB.glob("templates/*.html")) + list(WEB.glob("app-assets/*.js")):
        chars |= {c for c in path.read_text(encoding="utf-8") if ord(c) >= 0x80 and not c.isspace()}
    for path in WEB.glob("*.go"):
        text = path.read_text(encoding="utf-8")
        text = re.sub(r"/\*.*?\*/", " ", text, flags=re.S)
        text = re.sub(r"//[^\n]*", " ", text)
        chars |= {c for c in text if ord(c) >= 0x80 and not c.isspace()}
    return chars


def forum_charsets(ui: set[str]) -> tuple[set[str], set[str]]:
    """按首屏优先级拆成（common, rare）。两档互斥，并集是旧版全表 + 界面字。"""
    ascii_chars = set(string.printable.strip())
    level1, level2, symbols = gb2312_tiers()
    common = ascii_chars | level1 | symbols | set(COMMON_EXTRA) | ui
    rare = level2 - common
    # 不变量：一级字必须全在 common（正文常用字不允许回落系统字体）
    assert level1 <= common, "GB2312 一级字有遗漏，正文会回退"
    assert not (common & rare), "common/rare 有交集，unicode-range 会互相抢字"
    # 并集必须覆盖旧版全表（7445 + ASCII + 表外补充），覆盖不许缩水
    legacy = ascii_chars | level1 | level2 | symbols | set(COMMON_EXTRA)
    assert legacy <= common | rare, "两档并集比旧版单档小，覆盖缩水"
    return common, rare


def unicode_range(chars: set[str]) -> str:
    """把字集压成 CSS unicode-range（精确区间，不膨胀、不误伤 common）。"""
    cps = sorted(ord(c) for c in chars)
    ranges: list[tuple[int, int]] = []
    start = prev = cps[0]
    for cp in cps[1:]:
        if cp == prev + 1:
            prev = cp
            continue
        ranges.append((start, prev))
        start = prev = cp
    ranges.append((start, prev))
    parts = []
    for a, b in ranges:
        parts.append(f"U+{a:X}" if a == b else f"U+{a:X}-{b:X}")
    return ",".join(parts)


def build(weight: int, chars: set[str], tier: str) -> tuple[Path, int]:
    opts = Options()
    opts.flavor = FLAVOR
    font = load_font(FONT_SRC.format(weight=weight), opts)
    ss = Subsetter(options=opts)
    ss.populate(text="".join(sorted(chars)))
    ss.subset(font)
    buf = io.BytesIO()
    save_font(font, buf, opts)
    out = OUT_DIR / f"noto-serif-{weight}-{tier}.{FLAVOR}"
    out.write_bytes(buf.getbuffer())
    return out, buf.getbuffer().nbytes


def css_unicode_range(chars: set[str], width: int = 100) -> str:
    """生成可直接粘进 CSS 的 unicode-range：逗号分隔、按 width 折行。

    折行只在逗号后断（行的最后一个 token 后面补回逗号），否则换行把两个
    token 粘成一个非法值——CSS 遇到非法 unicode-range 会退回「全范围」，
    等于这一档无条件加载，分档白做。返回值由 check_css 自校验。
    """
    parts = unicode_range(chars).split(",")
    lines: list[str] = []
    cur: list[str] = []
    for part in parts:
        if cur and sum(len(x) + 1 for x in cur) + len(part) > width:
            lines.append(",".join(cur))
            cur = [part]
        else:
            cur.append(part)
    lines.append(",".join(cur))
    return ",\n    ".join(lines)


def check_css(range_text: str) -> None:
    """校验 app.css 里 rare @font-face 的 unicode-range 与本次切出的字集一致。

    只做只读校验：多工人并行改 app.css，脚本不自动改它，但漂移必须吼出来。
    """
    if not CSS.exists():
        return
    css = CSS.read_text(encoding="utf-8")
    found = re.findall(r"unicode-range:\s*([^;}]+)", css)
    if not found:
        print("!! app.css 里没有 unicode-range：rare 档会变成常驻下载", file=sys.stderr)
        return
    expected = range_text.replace("\n", "").replace(" ", "")
    for value in found:
        # 逐个 token 校验格式：非法 token 会让整条 descriptor 失效（退回全范围）
        tokens = [t.strip() for t in value.replace("\n", " ").split(",") if t.strip()]
        bad = [t for t in tokens if not re.fullmatch(r"U\+[0-9A-Fa-f]+(-[0-9A-Fa-f]+)?", t)]
        if bad:
            print(f"!! app.css 的 unicode-range 含非法 token {bad[:3]}：该 @font-face 会退回全范围", file=sys.stderr)
            return
        if value.replace("\n", "").replace(" ", "") == expected:
            return
    print(
        "!! app.css 的 rare unicode-range 与本脚本切出的字集不一致。\n"
        "   请把 css_unicode_range() 的输出替换进 app.css 的四个 rare @font-face（400/500/600/700）。",
        file=sys.stderr,
    )


def main() -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    common, rare = forum_charsets(ui_chars())
    range_text = css_unicode_range(rare)

    total = 0
    for weight in WEIGHTS:
        row = []
        for tier, chars in (("common", common), ("rare", rare)):
            out, size = build(weight, chars, tier)
            total += size
            row.append(f"{out.name}: {len(chars)} 字 {size} 字节")
        print(" | ".join(row))
    print(f"合计 {total} 字节（{total / 1024 / 1024:.2f} MiB）")
    print(f"rare unicode-range: {len(range_text)} 字节，{range_text.count(',') + 1} 个区间")

    check_css(range_text)


if __name__ == "__main__":
    main()
