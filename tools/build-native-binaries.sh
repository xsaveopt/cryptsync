#!/usr/bin/env bash
set -euo pipefail

RESTIC_VERSION="${RESTIC_VERSION:-v0.19.0}"
RCLONE_VERSION="${RCLONE_VERSION:-v1.74.3}"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JNI_DIR="$ROOT_DIR/app/src/main/jniLibs"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

export GOBIN=""
export GOPATH="$WORK_DIR/gopath"
export GOFLAGS="-trimpath"
export CGO_ENABLED=0

ABI_PAIRS="arm64-v8a:arm64"

build() {
  local module="$1"
  local out_name="$2"
  local abi="$3"
  local goarch="$4"
  local dest="$JNI_DIR/$abi"
  mkdir -p "$dest"
  echo ">> building $out_name ($abi / android-$goarch)"
  GOOS=android GOARCH="$goarch" go install -buildmode=pie -ldflags "-s -w" "$module"
  local built="$GOPATH/bin/android_$goarch"
  local src
  src="$(find "$built" -maxdepth 1 -type f | head -1)"
  cp "$src" "$dest/$out_name"
  chmod +x "$dest/$out_name"
}

for pair in $ABI_PAIRS; do
  abi="${pair%%:*}"
  goarch="${pair##*:}"
  build "github.com/restic/restic/cmd/restic@$RESTIC_VERSION" "librestic.so" "$abi" "$goarch"
  build "github.com/rclone/rclone@$RCLONE_VERSION" "librclone.so" "$abi" "$goarch"
done

echo ">> checksums"
( cd "$JNI_DIR" && find . -name "lib*.so" -type f -print0 | sort -z | xargs -0 shasum -a 256 ) > "$ROOT_DIR/tools/native-binaries.sha256"
cat "$ROOT_DIR/tools/native-binaries.sha256"
echo ">> done"
