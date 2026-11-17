#!/usr/bin/env python3
"""站点字体构建：把 App 打包的思源宋体静态实例切成网页用到的字符子集。

用法：python3 server/webtools/make_web_fonts.py
产物：server/web/assets/fonts/noto-serif-{400,500,600}.woff
改落地页或社务页文案（server/web 下的 HTML）之后重跑本脚本；
社务页先跑 build_notes.py 生成，或直接跑 build_web.sh。

关于格式：本机 fontTools 缺 brotli 绑定、pip 在大陆又拉不到包，
所以出 woff（zlib）而不是 woff2——浏览器全兼容，这点字数的体积差可忽略。
"""
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
FONT_SRC = str(ROOT / "app/src/main/res/font/noto_serif_{weight}.ttf")
WEB = ROOT / "server/web"
OUT_DIR = WEB / "assets/fonts"
WEIGHTS = (400, 500, 600)

# 送审中的候选文案一并切进去，用户换稿后不必重建字体
CANDIDATES = """
建造于人间之里外的飘渺存在 —— 高自由度论坛，由东方有栖神社开发组研制
人间之里外，一座属于您的线上神社
下载安卓客户端 下载 APK 安装有栖神社 GitHub 仓库 查看开源代码 源码仓库 当前版本
切换明暗
复制群号 已复制 请手动复制 技术栈 联系与社群 用户群
搜索 搜索文档 关闭 加载中… 搜索索引加载失败。 输入关键词搜索文档。 没有找到匹配的页面。
"""


def page_chars() -> set:
    chars = set()
    pages = sorted(WEB.rglob("*.html"))
    if not pages:
        raise SystemExit(f"{WEB} 下没有 HTML 页面")
    for path in pages:
        html = path.read_text(encoding="utf-8")
        html = re.sub(r"<style.*?</style>", " ", html, flags=re.S)
        html = re.sub(r"<script.*?</script>", " ", html, flags=re.S)
        html = re.sub(r"<!--.*?-->", " ", html, flags=re.S)
        html = re.sub(r"<[^>]+>", " ", html)
        chars |= set(html)
    return chars


def main() -> None:
    chars = page_chars() | set(CANDIDATES)
    chars |= {chr(c) for c in range(0x20, 0x7F)}  # 可打印 ASCII：版本号/日期微调免重切
    text = "".join(sorted(chars))
    text_file = Path("/tmp/arisu_web_font_chars.txt")
    text_file.write_text(text, encoding="utf-8")

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for weight in WEIGHTS:
        dst = OUT_DIR / f"noto-serif-{weight}.woff"
        subprocess.run(
            ["pyftsubset", FONT_SRC.format(weight=weight),
             f"--text-file={text_file}", "--flavor=woff", "--no-hinting",
             f"--output-file={dst}"],
            check=True,
        )
        print(f"{dst.name}: {len(text)} 字符 → {dst.stat().st_size} 字节")


if __name__ == "__main__":
    main()
