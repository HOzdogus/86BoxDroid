#!/usr/bin/env bash
# Builds the native side of 86BoxDroid (SDL3 + 86Box as libmain.so) for
# arm64 Android and copies the results into the Gradle project.
#
# Needs: ANDROID_NDK_HOME (NDK r26+), VCPKG_ROOT (a vcpkg checkout),
#        cmake, ninja, pkg-config.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"
WORK="${WORK:-$ROOT/build-android}"
SDL_TAG="${SDL_TAG:-release-3.4.18}"
API=26
ABI=arm64-v8a
TRIPLET=arm64-android-86box
NDK="${ANDROID_NDK_HOME:?set ANDROID_NDK_HOME}"
VCPKG="${VCPKG_ROOT:?set VCPKG_ROOT}"
NDK_TC="$NDK/build/cmake/android.toolchain.cmake"

mkdir -p "$WORK"

echo "::group::vcpkg dependencies"
export ANDROID_NDK_HOME="$NDK"
"$VCPKG/vcpkg" install --classic \
    --triplet "$TRIPLET" \
    --overlay-triplets "$HERE/triplets" \
    --clean-after-build \
    freetype libpng zlib zstd libslirp openal-soft "libsndfile[core]"
echo "::endgroup::"

echo "::group::SDL3"
if [ ! -d "$WORK/SDL" ]; then
    git clone --depth 1 -b "$SDL_TAG" https://github.com/libsdl-org/SDL.git "$WORK/SDL"
fi
cmake -S "$WORK/SDL" -B "$WORK/sdl-build" -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE="$NDK_TC" \
    -DANDROID_ABI=$ABI -DANDROID_PLATFORM=$API -DANDROID_STL=c++_shared \
    -DCMAKE_BUILD_TYPE=Release \
    -DSDL_SHARED=ON -DSDL_STATIC=OFF -DSDL_TEST_LIBRARY=OFF -DSDL_TESTS=OFF \
    -DCMAKE_INSTALL_PREFIX="$WORK/sdl-prefix"
cmake --build "$WORK/sdl-build" -j"$(nproc)"
cmake --install "$WORK/sdl-build"
echo "::endgroup::"

echo "::group::86Box"
cmake -S "$ROOT" -B "$WORK/86box-build" -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE="$VCPKG/scripts/buildsystems/vcpkg.cmake" \
    -DVCPKG_CHAINLOAD_TOOLCHAIN_FILE="$NDK_TC" \
    -DVCPKG_TARGET_TRIPLET=$TRIPLET \
    -DVCPKG_OVERLAY_TRIPLETS="$HERE/triplets" \
    -DVCPKG_MANIFEST_MODE=OFF \
    -DANDROID_ABI=$ABI -DANDROID_PLATFORM=$API -DANDROID_STL=c++_shared \
    -DCMAKE_BUILD_TYPE=Release -DRELEASE=ON \
    -DCMAKE_PREFIX_PATH="$WORK/sdl-prefix" \
    -DCMAKE_FIND_ROOT_PATH="$WORK/sdl-prefix" \
    -DPKG_CONFIG_ARGN=--static \
    -DQT=OFF -DSTATIC_BUILD=OFF -DDYNAREC=ON \
    -DOPENAL=ON -DRTMIDI=OFF -DFLUIDSYNTH=OFF -DMUNT=OFF -DSOUNDCANVAS=OFF \
    -DDISCORD=OFF -DVNC=OFF -DMINITRACE=OFF
cmake --build "$WORK/86box-build" -j"$(nproc)"
echo "::endgroup::"

echo "::group::Copy into Gradle project"
JNI="$HERE/app/src/main/jniLibs/$ABI"
mkdir -p "$JNI"
cp "$WORK/sdl-prefix/lib/libSDL3.so" "$JNI/"
find "$WORK/86box-build" -name libmain.so -exec cp {} "$JNI/" \;
cp "$NDK/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so" "$JNI/"
JAVA_DST="$HERE/app/src/main/java/org/libsdl/app"
mkdir -p "$JAVA_DST"
cp "$WORK/SDL/android-project/app/src/main/java/org/libsdl/app/"*.java "$JAVA_DST/"
ls -la "$JNI"
echo "::endgroup::"
