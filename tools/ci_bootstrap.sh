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
# Match upstream's deterministic runtime recipe; release packaging checks the trimmed hash.
python3 -m venv .bootstrap/runtime-python
.bootstrap/runtime-python/bin/pip install 'zstandard==0.25.0'
.bootstrap/runtime-python/bin/python tools/test_trim_proton_i386.py
.bootstrap/runtime-python/bin/python tools/trim_proton_i386.py app/src/main/assets/bundled_components/proton-11.0-90624-arm64ec.wcp
mkdir -p .bootstrap/dns
ndk_bin="$ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
"$ndk_bin/aarch64-linux-android26-clang" -shared -O2 -fPIC -Wl,-z,max-page-size=16384 \
    -o .bootstrap/dns/libgamenative_dns_v4mapped.so app/src/main/cpp/gamenative_dns_v4mapped.c -ldl
"$ndk_bin/llvm-strip" --strip-unneeded .bootstrap/dns/libgamenative_dns_v4mapped.so
cp .bootstrap/dns/libgamenative_dns_v4mapped.so app/src/main/jniLibs/arm64-v8a/libgamenative_dns_v4mapped.so
sh tools/dns-shim/test.sh
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
