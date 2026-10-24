#!/bin/bash
# ============================================================
#  打包 Magisk 模块 zip
#  用法：bash tools/build-module-zip.sh
#  产物：dist/haptic-boost-v<版本>.zip
# ============================================================
set -e

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

VERSION="$(grep '^version=' module/module.prop | cut -d= -f2)"
OUT="dist/haptic-boost-v${VERSION}.zip"

# 来电播放器位于配套 APK 中，模块必须包含 APK。发布签名优先。
if [ -f "dist/HapticBoost-${VERSION}-release.apk" ]; then
  APK="dist/HapticBoost-${VERSION}-release.apk"
else
  APK="$(ls "dist/HapticBoost-${VERSION}"-debug.apk 2>/dev/null | head -1 || true)"
fi
if [ -z "$APK" ]; then
  echo "缺少配套 APK：请先构建 App，来电增强需要其中的播放器。" >&2
  exit 1
fi

# 1) 准备临时目录
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

cp -a module/. "$TMP"/

if [ -n "$APK" ]; then
  cp "$APK" "$TMP/HapticBoost.apk"
  echo "  + 内嵌 App: $(basename "$APK")"
else
  echo "  ! 未找到 APK，将打包「不含 App」的模块"
fi

# 2) 用 python 打包（可精确控制可执行位；不依赖 zip 命令）
python3 - "$TMP" "$OUT" <<'PY'
import stat
import sys
import os
import zipfile

src, out = sys.argv[1], sys.argv[2]
os.makedirs(os.path.dirname(out), exist_ok=True)

with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as z:
    files = []
    for root, _, names in os.walk(src):
        for name in names:
            path = os.path.join(root, name)
            archive_name = os.path.relpath(path, src).replace(os.sep, '/')
            files.append((archive_name, path))

    for archive_name, path in sorted(files):
        info = zipfile.ZipInfo(archive_name)
        # Emit a conventional Unix ZIP entry. Some older Magisk/Kitsune
        # extractors reject Windows-origin entries carrying Unix mode bits.
        info.create_system = 3
        info.compress_type = zipfile.ZIP_DEFLATED
        # 关键：.sh 记成 0755，其余 0644
        mode = 0o755 if archive_name.endswith('.sh') or archive_name.endswith('/update-binary') else 0o644
        info.external_attr = (stat.S_IFREG | mode) << 16
        with open(path, 'rb') as f:
            z.writestr(info, f.read())

print("  = 已生成:", out, f"({os.path.getsize(out)} bytes)")
with zipfile.ZipFile(out) as z:
    for i in z.infolist():
        print(f"    {oct(i.external_attr >> 16)[-4:]}  {i.filename:28s} {i.file_size:>7d}")
PY

echo "✅ 完成：$OUT"
echo "   刷入方式：面具 → 模块 → 从本地安装 → 选择该 zip → 重启"
