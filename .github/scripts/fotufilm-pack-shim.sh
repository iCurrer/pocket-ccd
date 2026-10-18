#!/usr/bin/env bash
# 让上游 tools/verify-android-aot.sh 能在 Linux 上跑起来的最小替身。
#
# 背景（已核实，不是猜测）：
#   上游 Sources/fotufilm/main.swift 的 `--dump-wasm-pack` 实现位于
#   `#if canImport(ImageIO)`（第 757–1485 行）之内。Linux 上没有 ImageIO，
#   整段被替换成 fail("Image I/O requires macOS (ImageIO)")。
#   上游自己的 CI（.github/workflows/ci.yml @ 2d07c48）也只用 macos-26。
#
#   但 pack 本身是纯参数序列化（FSWP 头 + 配置 floats + 若干 LUT + 尺寸阶梯），
#   与平台无关。所以把 macOS 上真实 CLI 产出的同名 pack 复制过去，
#   喂给 verify-android-aot.sh 的 AOT/JIT 对比，语义完全等价。
#
# 用法：本脚本只被 verify-android-aot.sh 调用，不是通用工具。
#   环境变量 FOTUFILM_PACK_DIR 指向 macOS job 下载下来的 pack 目录。
set -euo pipefail

OUT=""
STOCK=""
SIZE=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dump-wasm-pack) OUT="${2:-}"; shift 2 || shift ;;
    --stock)          STOCK="${2:-}"; shift 2 || shift ;;
    --pack-size)      SIZE="${2:-}"; shift 2 || shift ;;
    --list-stocks)
      # 上游 --list-stocks 在 Linux 上是可用的（在 ImageIO 门槛之前就 exit(0)），
      # 但万一有人拿本脚本当 CLI 用，这里给出可读的结果而不是静默失败。
      echo "shim: --list-stocks is not implemented; use the real CLI" >&2
      exit 2
      ;;
    *) shift ;;
  esac
done

if [[ -z "$OUT" || -z "$STOCK" || -z "$SIZE" ]]; then
  echo "shim: need --dump-wasm-pack <out> --stock <id> --pack-size <WxH>" >&2
  echo "shim: got out='$OUT' stock='$STOCK' size='$SIZE'" >&2
  exit 2
fi

PACK_DIR="${FOTUFILM_PACK_DIR:?set FOTUFILM_PACK_DIR to the directory holding the macOS-dumped packs}"
SRC="$PACK_DIR/${STOCK}__${SIZE}.fswp"

if [[ ! -s "$SRC" ]]; then
  echo "shim: no prebuilt pack for $STOCK @ $SIZE" >&2
  echo "shim: expected $SRC" >&2
  echo "shim: available:" >&2
  ls -1 "$PACK_DIR" >&2 || true
  exit 1
fi

mkdir -p "$(dirname "$OUT")"
cp "$SRC" "$OUT"

# 明文记录来源，避免以后有人误以为这个 pack 是 Linux 现算的
echo "shim: ${STOCK} @ ${SIZE} <- $SRC ($(wc -c < "$SRC") bytes)"
