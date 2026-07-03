@echo off
REM NomadMark Build Script for Android (Windows)
REM
REM Prerequisites:
REM 1. Rust + Cargo installed
REM 2. Android NDK installed (r21e or later)
REM 3. cargo-ndk installed: cargo install cargo-ndk
REM 4. ANDROID_NDK_HOME environment variable set

setlocal enabledelayedexpansion

set PROJECT_ROOT=%~dp0
set CORE_DIR=%PROJECT_ROOT%core
set JNI_LIBS=%PROJECT_ROOT%android\app\src\main\jniLibs

echo ======================================
echo NomadMark Android Build Script
echo ======================================
echo.

REM Check prerequisites
echo Checking prerequisites...

where cargo >nul 2>nul
if errorlevel 1 (
    echo ❌ Rust/Cargo not found. Install from https://rustup.rs/
    exit /b 1
)

where cargo-ndk >nul 2>nul
if errorlevel 1 (
    echo ⚠️  cargo-ndk not found. Installing...
    cargo install cargo-ndk
)

if "%ANDROID_NDK_HOME%"=="" (
    echo ❌ ANDROID_NDK_HOME not set. Please set it to your NDK path.
    echo    Example: set ANDROID_NDK_HOME=C:\path\to\android-ndk
    exit /b 1
)

echo ✅ Prerequisites check passed
echo.

REM Build Rust Core
echo ======================================
echo Building Rust Core for Android...
echo ======================================

cd /d "%CORE_DIR%"

REM Build for arm64-v8a (64-bit, preferred)
echo Building for arm64-v8a...
cargo ndk -t arm64-v8a build --release

REM Build for armeabi-v7a (32-bit, legacy)
echo Building for armeabi-v7a...
cargo ndk -t armeabi-v7a build --release

echo ✅ Rust Core build complete
echo.

REM Copy .so files
echo ======================================
echo Copying .so files to jniLibs...
echo ======================================

if not exist "%JNI_LIBS%\arm64-v8a" mkdir "%JNI_LIBS%\arm64-v8a"
if not exist "%JNI_LIBS%\armeabi-v7a" mkdir "%JNI_LIBS%\armeabi-v7a"

REM arm64-v8a
copy "%CORE_DIR%\target\aarch64-linux-android\release\markdown_core.dll" ^
     "%JNI_LIBS%\arm64-v8a\libmarkdown_core.so" >nul
echo ✅ Copied arm64-v8a\libmarkdown_core.so

REM armeabi-v7a
copy "%CORE_DIR%\target\armv7-linux-androideabi\release\markdown_core.dll" ^
     "%JNI_LIBS%\armeabi-v7a\libmarkdown_core.so" >nul
echo ✅ Copied armeabi-v7a\libmarkdown_core.so

echo.
echo ======================================
echo Build Complete!
echo ======================================
echo.
echo Next steps:
echo 1. Open android\ directory in Android Studio
echo 2. Sync Gradle files
echo 3. Build APK: gradlew.bat assembleDebug
echo 4. Install on Supernote A6 X2 Nomad
echo.

pause
