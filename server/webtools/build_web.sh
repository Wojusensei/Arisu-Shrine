#!/bin/sh
# 重建社务页与网页字体子集。改完 server/webtools/notes/ 下的文案后跑一次，
# 再把 server/web 同步到生产机的 /opt/arisu/server/web。
set -eu
cd "$(dirname "$0")/../.."
python3 server/webtools/build_notes.py
python3 server/webtools/make_web_fonts.py
