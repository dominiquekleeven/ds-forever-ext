#!/usr/bin/env bash
# Build native prerequisites from pinned sources on the GitHub Linux runner.
set -euo pipefail
cd "$(dirname "$0")/.."
: "${ANDROID_HOME:?Android SDK is required}"
export ANDROID_NDK="$ANDROID_HOME/ndk/27.2.12479018"
mkdir -p .deps .bootstrap
dependency() {
    local name="$1" url="$2" revision="$3"
    if [ ! -d ".deps/$name/.git" ]; then
        git init ".deps/$name"
        git -C ".deps/$name" remote add origin "$url"
        git -C ".deps/$name" fetch --depth=1 origin "$revision"
        git -C ".deps/$name" checkout --detach FETCH_HEAD
    fi
    test "$(git -C ".deps/$name" rev-parse HEAD)" = "$revision"
    test -z "$(git -C ".deps/$name" status --porcelain)"
}
bash tools/fetch_components.sh
dependency CascLib https://github.com/ladislav-zezula/CascLib.git 38a34665624b8775bb875274b36191b21c38d97b
dependency recastnavigation https://github.com/recastnavigation/recastnavigation.git 6dc1667f580357e8a2154c28b7867bea7e8ad3a7
bash companion/tools/harvest/build_native.sh "$PWD/.deps/CascLib"
bash companion/tools/interiors_native/build_android.sh "$PWD/.deps/recastnavigation" "$PWD/.bootstrap/interiors-android"
bash companion/tools/whisper_native/build_android.sh "$PWD/.bootstrap/whisper-android"
bash tools/wow-session-bridge/build_dsf.sh

# The streaming source is already committed. Copy only its ignored static dependencies.
dependency moonlight-android https://github.com/moonlight-stream/moonlight-android.git b48494cb96bff23d8886c4775cc4f39a1075495d
src=.deps/moonlight-android/app/src/main/jni/moonlight-core
dest=dsforever/src/main/jni/prebuilt
for component in openssl opus; do
    mkdir -p "$dest/$component/arm64-v8a"
    cp -R "$src/$component/include" "$dest/$component/"
done
cp "$src/openssl/arm64-v8a/libcrypto.a" "$dest/openssl/arm64-v8a/"
cp "$src/opus/arm64-v8a/libopus.a" "$dest/opus/arm64-v8a/"
