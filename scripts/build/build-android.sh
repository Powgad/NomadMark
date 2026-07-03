#!/bin/bash
# NomadMark Build Script for Android
#
# Prerequisites:
# 1. Rust + Cargo installed
# 2. Android NDK installed (r21e or later)
# 3. cargo-ndk installed: cargo install cargo-ndk
# 4. ANDROID_NDK_HOME environment variable set

set -e

# Script is in scripts/build/, so project root is two levels up
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$(dirname "$SCRIPT_DIR")")"
CORE_DIR="$PROJECT_ROOT/core"
JNI_LIBS="$PROJECT_ROOT/platforms/android/app/src/main/jniLibs"

echo "======================================"
echo "NomadMark Android Build Script"
echo "======================================"
echo ""

# Check prerequisites
echo "Checking prerequisites..."

if ! command -v cargo &> /dev/null; then
    echo "❌ Rust/Cargo not found. Install from https://rustup.rs/"
    exit 1
fi

if ! command -v cargo-ndk &> /dev/null; then
    echo "⚠️  cargo-ndk not found. Installing..."
    cargo install cargo-ndk
fi

if [ -z "$ANDROID_NDK_HOME" ]; then
    echo "❌ ANDROID_NDK_HOME not set. Please set it to your NDK path."
    echo "   Example: export ANDROID_NDK_HOME=/path/to/android-ndk"
    exit 1
fi

echo "✅ Prerequisites check passed"
echo ""

# Build Rust Core
echo "======================================"
echo "Building Rust Core for Android..."
echo "======================================"

cd "$CORE_DIR"

# Build for arm64-v8a (64-bit, preferred)
echo "Building for arm64-v8a..."
cargo ndk -t arm64-v8a build --release

# Build for armeabi-v7a (32-bit, legacy)
echo "Building for armeabi-v7a..."
cargo ndk -t armeabi-v7a build --release

echo "✅ Rust Core build complete"
echo ""

# Copy .so files
echo "======================================"
echo "Copying .so files to jniLibs..."
echo "======================================"

# arm64-v8a
cp "$CORE_DIR/target/aarch64-linux-android/release/libmarkdown_core.so" \
   "$JNI_LIBS/arm64-v8a/libmarkdown_core.so"
echo "✅ Copied arm64-v8a/libmarkdown_core.so"

# armeabi-v7a
cp "$CORE_DIR/target/armv7-linux-androideabi/release/libmarkdown_core.so" \
   "$JNI_LIBS/armeabi-v7a/libmarkdown_core.so"
echo "✅ Copied armeabi-v7a/libmarkdown_core.so"

echo ""
echo "======================================"
echo "Build Complete!"
echo "======================================"
echo ""
echo "Next steps:"
echo "1. Open android/ directory in Android Studio"
echo "2. Sync Gradle files"
echo "3. Build APK: ./gradlew assembleDebug"
echo "4. Install on Supernote A6 X2 Nomad"
echo ""

# Clean unused build artifacts (保留 release .so 供调试)
echo "======================================"
echo "Cleaning unused build artifacts..."
echo "======================================"
cd "$CORE_DIR"
# 只删除 debug 构建产物，保留 release
rm -rf target/debug
rm -rf target/*-debug-*
echo "✅ Cleaned debug artifacts (release kept for reference)"
echo ""
