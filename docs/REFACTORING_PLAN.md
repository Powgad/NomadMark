# NomadMark 项目架构重构方案

**文档版本**: 1.0  
**制定日期**: 2026-07-03  
**目标架构**: Cargo Workspace + 分层架构  
**预计工期**: 6-9 个工作日

---

## 目录

1. [重构目标](#重构目标)
2. [问题诊断](#问题诊断)
3. [目标架构](#目标架构)
4. [迁移计划](#迁移计划)
5. [实施步骤](#实施步骤)
6. [验证清单](#验证清单)
7. [风险控制](#风险控制)

---

## 重构目标

### 核心目标
1. ✅ 建立 Cargo Workspace 统一管理
2. ✅ 重组目录结构，提升可维护性
3. ✅ 统一构建流程，简化开发
4. ✅ 优化编译效率，支持增量构建
5. ✅ 规范文档组织，便于查找

### 非目标
- ❌ 不改变功能实现
- ❌ 不调整模块边界
- ❌ 不重写核心逻辑

---

## 问题诊断

### 当前问题

| 类别 | 问题 | 影响 |
|------|------|------|
| **架构** | 无 Workspace 结构 | 依赖分散，无法统一管理 |
| **构建** | Core 为各平台独立构建 | 重复编译，效率低下 |
| **目录** | 根目录文件混杂 | 难以导航，不够专业 |
| **文档** | 文档散落各处 | 维护困难，查找不便 |
| **路径** | 相对路径混乱 | 跨包引用复杂 |
| **版本** | 无统一版本控制 | 发布协调困难 |

### 技术债务

```
优先级 1 (高):
├── 无 Cargo Workspace - 阻碍长期发展
└── 构建流程割裂 - 降低开发效率

优先级 2 (中):
├── 目录结构混乱 - 影响可读性
└── 文档分散 - 增加维护成本

优先级 3 (低):
├── 缺少代码规范工具
└── 无统一 CI/CD 流程
```

---

## 目标架构

### 目录结构

```
NomadMark/
│
├── Cargo.toml                  # ★ Workspace Root 配置
├── Cargo.lock                  # 统一依赖锁文件
│
├── core/                       # ★ 核心库 (Workspace Member)
│   ├── Cargo.toml              # 继承 workspace 版本
│   ├── src/
│   │   ├── lib.rs              # 主入口，FFI 导出
│   │   ├── parser/             # Markdown 解析
│   │   ├── layout/             # 布局引擎
│   │   ├── render/             # 渲染命令
│   │   ├── bridge/             # FFI 桥接层
│   │   │   ├── mod.rs
│   │   │   ├── types.rs        # C ABI 类型
│   │   │   └── jni.rs          # Android JNI
│   │   ├── insert.rs           # 语法插入
│   │   ├── history/            # 撤销/重做
│   │   ├── search/             # 全文搜索
│   │   └── replace/            # 文本替换
│   ├── benches/                # 性能基准测试
│   ├── tests/                  # 单元测试
│   └── examples/               # 示例代码
│
├── libs/                       # ★ 共享 Rust 库 (可选扩展)
│   ├── ffi/                    # FFI 绑定生成工具
│   │   ├── android/            # JNI 绑定代码
│   │   ├── ios/                # Swift 绑定代码
│   │   └── desktop/            # Tauri 命令定义
│   └── common/                 # 共享类型和工具
│       ├── Cargo.toml
│       └── src/
│
├── platforms/                  # ★ 平台目录
│   │
│   ├── android/                # Android 平台
│   │   ├── .gradle/            # Gradle 缓存
│   │   ├── .idea/              # Android Studio 配置
│   │   ├── app/
│   │   │   ├── src/
│   │   │   │   └── main/
│   │   │   │       ├── java/com/editor/nomadmark/
│   │   │   │       │   ├── MainActivity.kt
│   │   │   │       │   ├── MarkdownEditorActivity.kt
│   │   │   │       │   ├── MarkdownEditorView.kt
│   │   │   │       │   ├── MarkdownCore.kt         # FFI 桥接
│   │   │   │       │   ├── GestureEditor.kt
│   │   │   │       │   ├── GestureRecognizer.kt
│   │   │   │       │   ├── GestureOverlayView.kt
│   │   │   │       │   ├── search/
│   │   │   │       │   │   ├── SearchPanel.kt
│   │   │   │       │   │   └── SearchHighlightPainter.kt
│   │   │   │       │   ├── toc/
│   │   │   │       │   │   ├── TocPanel.kt
│   │   │   │       │   │   └── TocAdapter.kt
│   │   │   │       │   ├── FileOperationHelper.kt
│   │   │   │       │   ├── ScrollSyncManager.kt
│   │   │   │       │   ├── KeyboardDetector.kt
│   │   │   │       │   └── EinkRefreshController.kt
│   │   │   │       ├── jniLibs/                 # 自动构建输出
│   │   │   │       │   ├── arm64-v8a/
│   │   │   │       │   │   └── libmarkdown_core.so
│   │   │   │       │   └── armeabi-v7a/
│   │   │   │       │       └── libmarkdown_core.so
│   │   │   │       ├── res/                     # Android 资源
│   │   │   │       └── AndroidManifest.xml
│   │   │   ├── build.gradle
│   │   │   └── proguard-rules.pro
│   │   ├── build.gradle          # 项目级配置
│   │   ├── settings.gradle
│   │   ├── gradle.properties
│   │   ├── gradlew/
│   │   ├── gradlew.bat
│   │   └── build.rs              # ★ 调用 Core 构建
│   │
│   ├── desktop/                # Desktop 平台 (Tauri)
│   │   ├── src/                 # React UI
│   │   │   ├── App.tsx
│   │   │   ├── main.tsx
│   │   │   ├── components/
│   │   │   │   ├── SplitView.tsx
│   │   │   │   └── Toolbar.tsx
│   │   │   ├── hooks/
│   │   │   │   ├── useDocument.ts
│   │   │   │   └── useCanvasRenderer.ts
│   │   │   └── types/
│   │   │       └── index.ts
│   │   ├── src-tauri/           # Rust 后端
│   │   │   ├── Cargo.toml       # Workspace Member
│   │   │   ├── src/
│   │   │   │   └── main.rs
│   │   │   ├── tauri.conf.json
│   │   │   └── build.rs
│   │   ├── package.json
│   │   ├── vite.config.ts
│   │   └── tsconfig.json
│   │
│   └── ios/                    # iOS 平台
│       ├── NomadMark/          # iOS App
│       │   ├── Sources/
│       │   │   ├── AppDelegate.swift
│       │   │   ├── SceneDelegate.swift
│       │   │   ├── MarkdownEditorViewController.swift
│       │   │   ├── MarkdownView.swift
│       │   │   └── Core/
│       │   │       └── MarkdownCoreBridge.swift    # FFI 桥接
│       │   ├── Resources/
│       │   │   ├── Assets.xcassets
│       │   │   └── Info.plist
│       │   └── NomadMark.xcodeproj
│       └── NomadMarkCore/      # Rust 静态库
│           ├── Cargo.toml      # Workspace Member
│           ├── src/
│           │   └── lib.rs      # iOS 特定导出
│           └── build.rs
│
├── resources/                  # ★ 共享资源
│   ├── fonts/                  # 字体文件
│   │   ├── NotoSans/
│   │   ├── NotoSerif/
│   │   ├── NotoMono/
│   │   └── CJK/
│   ├── themes/                 # 主题配置
│   │   ├── light.json
│   │   ├── dark.json
│   │   └── e-ink.json
│   ├── icons/                  # 图标资源
│   │   ├── app/
│   │   ├── ui/
│   │   └── file-associations/
│   └── specs/                 # 设计规范
│       ├── typography.md
│       ├── colors.md
│       └── components.md
│
├── tools/                      # ★ 开发工具
│   ├── codegen/                # 代码生成工具
│   │   ├── ffi-bindgen/        # FFI 绑定生成
│   │   └── openapi-generator/  # API 文档生成
│   ├── lint/                   # 代码检查工具
│   │   ├── rust-clippy/
│   │   ├── kotlin-ktlint/
│   │   └── typescript-eslint/
│   └── scripts/                # 辅助脚本
│       ├── format-code.sh
│       └── check-style.sh
│
├── scripts/                    # ★ 构建脚本
│   ├── build/                  # 构建脚本
│   │   ├── build-core.sh
│   │   ├── build-android.sh
│   │   ├── build-desktop.sh
│   │   ├── build-ios.sh
│   │   └── build-all.sh
│   ├── dev/                    # 开发脚本
│   │   ├── run-android.sh
│   │   ├── run-desktop.sh
│   │   └── watch-core.sh
│   └── release/                # 发布脚本
│       ├── tag.sh
│       ├── changelog.sh
│       └── publish.sh
│
├── tests/                      # ★ 集成测试
│   ├── e2e/                    # 端到端测试
│   │   ├── android/
│   │   ├── desktop/
│   │   └── cross-platform/
│   ├── performance/            # 性能测试
│   │   ├── render-bench.rs
│   │   └── memory-profile.rs
│   ├── fixtures/               # 测试数据
│   │   ├── markdown/
│   │   └── expected/
│   └── README.md
│
├── docs/                       # ★ 文档目录
│   ├── architecture/           # 架构文档
│   │   ├── structure.md       # 目录结构
│   │   ├── design-v2.md       # 架构设计 v2.0
│   │   ├── detailed-design.md # 详细设计
│   │   └── refactoring.md     # 本重构方案
│   ├── api/                    # API 文档
│   │   ├── core-ffi.md        # Core FFI API
│   │   ├── jni-api.md         # Android JNI API
│   │   └── tauri-commands.md  # Desktop Tauri API
│   ├── guides/                 # 开发指南
│   │   ├── getting-started.md # 快速开始
│   │   ├── implementation.md  # 实现指南
│   │   ├── missing-features.md# 缺失功能
│   │   ├── deployment.md      # 部署指南
│   │   └── testing.md         # 测试指南
│   ├── reports/                # 报告文档
│   │   ├── validation.md      # 验证报告
│   │   ├── markdown-status.md # Markdown 渲染状态
│   │   └── progress.md        # 项目进度
│   ├── roadmap/                # 路线图
│   │   ├── roadmap.md         # 开发路线
│   │   └── analysis.md        # 路线分析
│   ├── features/               # 功能文档
│   │   ├── markdown-rendering.md
│   │   └── e-ink-optimization.md
│   ├── assets/                 # 文档资源
│   │   ├── diagrams/
│   │   └── screenshots/
│   └── index.md                # 文档索引
│
├── .github/                    # GitHub 配置
│   ├── workflows/              # CI/CD 工作流
│   │   ├── build-core.yml
│   │   ├── build-android.yml
│   │   ├── build-desktop.yml
│   │   ├── test.yml
│   │   ├── lint.yml
│   │   └── release.yml
│   ├── ISSUE_TEMPLATE/
│   │   ├── bug.yml
│   │   └── feature.yml
│   └── PULL_REQUEST_TEMPLATE.md
│
├── README.md                   # 项目主页
├── CONTRIBUTING.md             # 贡献指南
├── LICENSE                      # 许可证
├── CHANGELOG.md                # 变更日志
├── CODE_OF_CONDUCT.md          # 行为准则
├── .gitignore
├── .editorconfig
├── .rustfmt.toml               # Rust 格式化配置
├── .clippy.toml                # Clippy 配置
└── package.json                # 根级 package (脚本别名)
```

---

## 迁移计划

### 总体时间线

```
Week 1:
├── Day 1-2: 阶段一 - 建立 Workspace
├── Day 3-4: 阶段二 - 重组目录结构  
└── Day 5:   阶段三 - 修复路径引用

Week 2:
├── Day 1-2: 阶段四 - 构建集成
├── Day 3-4: 阶段五 - CI/CD 配置
└── Day 5:   阶段六 - 文档更新与验证
```

### 依赖关系

```
阶段一 (Workspace)
    │
    ▼
阶段二 (目录重组)
    │
    ▼
阶段三 (路径修复)
    │
    ├─────────────────┐
    ▼                 ▼
阶段四 (构建集成)  阶段六 (文档)
    │
    ▼
阶段五 (CI/CD)
```

---

## 实施步骤

### 阶段一：建立 Cargo Workspace

**目标**: 创建 Workspace 根配置，建立统一依赖管理

#### 1.1 创建根 Cargo.toml

```toml
# NomadMark/Cargo.toml

[workspace]
resolver = "2"
members = [
    "core",
    "platforms/desktop/src-tauri",
    "platforms/ios/NomadMarkCore",
]

# 可选成员 (开发中)
#members = [
#    "libs/common",
#    "libs/ffi/android",
#]

[workspace.package]
version = "0.1.0"
authors = ["NomadMark Team"]
license = "MIT"
repository = "https://github.com/nomadmark/nomadmark"
edition = "2021"

[workspace.dependencies]
# 统一管理依赖版本
pulldown-cmark = "0.9"
rustybuzz = "0.7"
font-types = "0.1"
lru = "0.11"
rayon = "1.7"
tantivy = "0.19"
regex = { version = "1", optional = true }
thiserror = "1.0"
memmap2 = "0.9"
libc = "0.2"

# JNI (Android only)
jni = { version = "0.21", optional = true }

# Desktop dependencies
serde = { version = "1.0", features = ["derive"] }
serde_json = "1.0"
tokio = { version = "1.35", features = ["full"] }
tauri = { version = "1.6", features = ["shell-open"] }
notify = "6.1"

[workspace.metadata.platform]
android-min-sdk = 28
android-target-sdk = 34
android-compile-sdk = 34

[profile.release]
opt-level = 3
lto = true
codegen-units = 1
strip = false
panic = "abort"

[profile.dev]
opt-level = 0

# 优化依赖编译
[profile.dev.package."*"]
opt-level = 3

# Android 优化配置
[profile.android]
inherits = "release"
opt-level = "z"
strip = "panic"

[profile.android.package."*"]
opt-level = "z"
```

#### 1.2 更新 core/Cargo.toml

```toml
# core/Cargo.toml

[package]
name = "markdown_core"
version.workspace = true
authors.workspace = true
license.workspace = true
repository.workspace = true
edition.workspace = true

[lib]
crate-type = ["cdylib", "staticlib"]

[dependencies]
pulldown-cmark.workspace = true
rustybuzz.workspace = true
font-types.workspace = true
lru.workspace = true
rayon.workspace = true
tantivy.workspace = true
regex = { workspace = true, optional = true }
thiserror.workspace = true
memmap2.workspace = true
libc.workspace = true

[target.'cfg(target_os = "android")'.dependencies]
jni.workspace = true

[features]
android = ["jni"]
jni = ["dep:jni"]
regex-search = ["regex"]
```

#### 1.3 验证编译

```bash
# 验证 Workspace 是否正常
cargo check --workspace

# 验证 Core 编译
cargo build -p markdown_core
```

---

### 阶段二：重组目录结构

**目标**: 创建 platforms/ 目录，迁移平台代码

#### 2.1 目录迁移

```bash
# 使用 git mv 保留历史

# 创建 platforms 目录
mkdir -p platforms

# 迁移 Android
git mv android platforms/android

# 迁移 Desktop
git mv desktop platforms/desktop

# 迁移 iOS
git mv ios platforms/ios
```

#### 2.2 创建新目录

```bash
# 创建共享资源目录
git mv common resources

# 创建文档子目录
mkdir -p docs/{architecture,api,guides,reports,roadmap,features,assets}

# 创建工具目录
mkdir -p tools/{codegen,lint,scripts}

# 创建脚本目录
mkdir -p scripts/{build,dev,release}

# 创建测试目录
mkdir -p tests/{e2e,performance,fixtures}
```

#### 2.3 文档迁移

```bash
# 架构文档
git mv PROJECT_STRUCTURE_ANALYSIS.md docs/architecture/structure.md
git mv "《架构设计书 v2.0》.md" docs/architecture/design-v2.md
git mv 详细设计文档.md docs/architecture/detailed-design.md

# 开发指南
git mv IMPLEMENTATION_GUIDE.md docs/guides/implementation.md
git mv MISSING_FEATURES_ANALYSIS.md docs/guides/missing-features.md
git mv DEPLOYMENT_GUIDE.md docs/guides/deployment.md
git mv TESTING_CHECKLIST.md docs/guides/testing.md

# 报告
git mv VALIDATION_REPORT.md docs/reports/validation.md
git mv MARKDOWN_RENDERING_STATUS.md docs/reports/markdown-status.md
git mv PROJECT_PROGRESS.md docs/reports/progress.md

# 路线图
git mv ROADMAP_ANALYSIS.md docs/roadmap/analysis.md
git mv DEVELOPMENT_ROADMAP.md docs/roadmap/roadmap.md

# 功能
git mv MARKDOWN_RENDERING_FEATURES.md docs/features/markdown-rendering.md
```

#### 2.4 脚本迁移

```bash
# 迁移构建脚本
git mv build-android.sh scripts/build/
git mv build-android.bat scripts/build/
```

---

### 阶段三：修复路径引用

**目标**: 更新所有配置文件中的路径引用

#### 3.1 Android 路径修复

```toml
# platforms/android/build.gradle

// 更新 JNI 配置
android {
    sourceSets {
        main {
            jniLibs.srcDirs = ['src/main/jniLibs']
        }
    }
}
```

```gradle
# platforms/android/app/build.gradle

// 添加构建任务：自动编译 Rust
android {
    // ...
}

// 自动构建 Rust Core
tasks.register('buildRustCore') {
    doLast {
        exec {
            workingDir '../../core'
            if (System.getProperty('os.name').toLowerCase().contains('windows')) {
                commandLine 'cmd', '/c', 'cargo', 'build', '--release'
            } else {
                commandLine 'cargo', 'build', '--release'
            }
        }
        // 复制 .so 文件到 jniLibs
        copyNativeLibs()
    }
}

// 确保 Rust 在 Java 编译前构建
tasks.named('preBuild').configure {
    dependsOn 'buildRustCore'
}

def copyNativeLibs() {
    // 复制逻辑
}
```

#### 3.2 Desktop 路径修复

```toml
# platforms/desktop/src-tauri/Cargo.toml

[dependencies]
# 更新 Core 路径
markdown_core = { path = "../../../core" }

# 如果创建 libs/common
# nomadmark_common = { path = "../../../libs/common" }
```

```json
// platforms/desktop/src-tauri/tauri.conf.json

// 更新构建路径
"build": {
    "beforeBuildCommand": "cd ../.. && npm run build:core",
    "beforeDevCommand": "cd ../.. && npm run dev:core",
    // ...
}
```

#### 3.3 iOS 路径设置

```toml
# platforms/ios/NomadMarkCore/Cargo.toml

[package]
name = "nomadmark-ios"
version.workspace = true
edition.workspace = true

[lib]
crate-type = ["staticlib"]

[dependencies]
markdown_core = { path = "../../../core" }
```

---

### 阶段四：构建集成

**目标**: 统一构建流程，实现自动化

#### 4.1 创建构建脚本

```bash
#!/bin/bash
# scripts/build/build-core.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$(dirname "$SCRIPT_DIR")")"

echo "Building NomadMark Core..."
cd "$PROJECT_ROOT/core"

# 检测平台
if [[ "$OSTYPE" == "darwin"* ]]; then
    cargo build --release --target aarch64-apple-ios
    cargo build --release --target x86_64-apple-ios
elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
    cargo build --release
    # Android targets
    cargo build --release --target aarch64-linux-android
    cargo build --release --target armv7-linux-androideabi
fi

echo "Core build complete!"
```

```bash
#!/bin/bash
# scripts/build/build-android.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$(dirname "$SCRIPT_DIR")")"

# 先构建 Core
"$SCRIPT_DIR/build-core.sh"

# 构建 Android APK
cd "$PROJECT_ROOT/platforms/android"
./gradlew assembleDebug

# 复制 APK
cp app/build/outputs/apk/debug/app-debug.apk "$PROJECT_ROOT/build/"
```

```bash
#!/bin/bash
# scripts/build/build-all.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$(dirname "$SCRIPT_DIR")")"

echo "Building all NomadMark platforms..."

# Core
"$SCRIPT_DIR/build-core.sh"

# Android
"$SCRIPT_DIR/build-android.sh"

# Desktop
"$SCRIPT_DIR/build-desktop.sh"

# iOS (需要 macOS)
if [[ "$OSTYPE" == "darwin"* ]]; then
    "$SCRIPT_DIR/build-ios.sh"
fi

echo "All builds complete!"
```

#### 4.2 创建 Android build.rs

```rust
// platforms/android/build.rs

fn main() {
    println!("cargo:rerun-if-changed=../../core/Cargo.toml");
    
    // 构建目标
    let target = std::env::var("TARGET").unwrap();
    
    if target.contains("android") {
        // 触发 Core 构建
        let core_dir = std::path::PathBuf::from("../../core");
        let status = std::process::Command::new("cargo")
            .current_dir(&core_dir)
            .args(["build", "--release"])
            .status()
            .expect("Failed to build core");
        
        if !status.success() {
            panic!("Core build failed");
        }
        
        // 复制 .so 文件
        copy_native_libs(&core_dir);
    }
}

fn copy_native_libs(core_dir: &std::path::Path) {
    use std::fs;
    
    let src_dir = core_dir.join("target/release");
    let dst_dir = std::path::PathBuf::from("app/src/main/jniLibs");
    
    fs::create_dir_all(&dst_dir).ok();
    
    // 复制 libmarkdown_core.so
    // ...
}
```

---

### 阶段五：CI/CD 配置

**目标**: 设置 GitHub Actions 自动化流程

#### 5.1 Core 构建工作流

```yaml
# .github/workflows/build-core.yml

name: Build Core

on:
  push:
    branches: [main, develop]
    paths:
      - 'core/**'
      - 'Cargo.toml'
      - 'Cargo.lock'
  pull_request:
    paths:
      - 'core/**'
      - 'Cargo.toml'
      - 'Cargo.lock'

jobs:
  build:
    name: Build on ${{ matrix.os }}
    runs-on: ${{ matrix.os }}
    strategy:
      matrix:
        os: [ubuntu-latest, macos-latest, windows-latest]
        include:
          - os: ubuntu-latest
            target: x86_64-unknown-linux-gnu
          - os: macos-latest
            target: x86_64-apple-darwin
          - os: windows-latest
            target: x86_64-pc-windows-msvc
    
    steps:
      - uses: actions/checkout@v4
      
      - uses: actions-rs/toolchain@v1
        with:
          profile: release
          toolchain: stable
      
      - name: Build Core
        run: cargo build --release -p markdown_core
      
      - name: Run Tests
        run: cargo test -p markdown_core
      
      - name: Upload Artifacts
        uses: actions/upload-artifact@v3
        with:
          name: core-${{ matrix.target }}
          path: |
            target/release/libmarkdown_core.so
            target/release/libmarkdown_core.a
            target/release/libmarkdown_core.dll
            target/release/markdown_core.lib
```

#### 5.2 Android 构建工作流

```yaml
# .github/workflows/build-android.yml

name: Build Android

on:
  push:
    branches: [main, develop]
    paths:
      - 'core/**'
      - 'platforms/android/**'
  pull_request:
    paths:
      - 'core/**'
      - 'platforms/android/**'

jobs:
  build:
    runs-on: ubuntu-latest
    
    steps:
      - uses: actions/checkout@v4
      
      - uses: actions-rs/toolchain@v1
        with:
          toolchain: stable
          target: aarch64-linux-android
          override: true
      
      - name: Install NDK
        run: |
          sudo apt-get update
          sudo apt-get install -y ndk-build
      
      - name: Setup Gradle
        uses: gradle/gradle-build-action@v2
      
      - name: Build Rust Core
        run: |
          cd core
          cargo build --release --target aarch64-linux-android
      
      - name: Build Android APK
        run: |
          cd platforms/android
          ./gradlew assembleDebug
      
      - name: Upload APK
        uses: actions/upload-artifact@v3
        with:
          name: android-apk
          path: platforms/android/app/build/outputs/apk/debug/app-debug.apk
```

#### 5.3 Desktop 构建工作流

```yaml
# .github/workflows/build-desktop.yml

name: Build Desktop

on:
  push:
    branches: [main, develop]
    paths:
      - 'core/**'
      - 'platforms/desktop/**'
  pull_request:
    paths:
      - 'core/**'
      - 'platforms/desktop/**'

jobs:
  build:
    strategy:
      matrix:
        os: [ubuntu-latest, macos-latest, windows-latest]
        include:
          - os: ubuntu-latest
            artifact: NomadMark-linux.appimage
          - os: macos-latest
            artifact: NomadMark-macos.dmg
          - os: windows-latest
            artifact: NomadMark-windows.exe
    
    runs-on: ${{ matrix.os }}
    
    steps:
      - uses: actions/checkout@v4
      
      - uses: actions-rs/toolchain@v1
        with:
          toolchain: stable
      
      - uses: actions/setup-node@v3
        with:
          node-version: '18'
      
      - name: Install Dependencies
        run: |
          cd platforms/desktop
          npm ci
      
      - name: Build
        run: |
          cd platforms/desktop
          npm run tauri:build
      
      - name: Upload Artifacts
        uses: actions/upload-artifact@v3
        with:
          name: desktop-${{ matrix.os }}
          path: platforms/desktop/src-tauri/target/release/bundle/*
```

#### 5.4 测试工作流

```yaml
# .github/workflows/test.yml

name: Test

on:
  push:
    branches: [main, develop]
  pull_request:

jobs:
  rust-tests:
    name: Rust Tests
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions-rs/toolchain@v1
      - name: Run Tests
        run: cargo test --workspace
      
  kotlin-tests:
    name: Kotlin Tests
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Run Tests
        run: |
          cd platforms/android
          ./gradlew test
  
  typescript-tests:
    name: TypeScript Tests
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v3
      - name: Run Tests
        run: |
          cd platforms/desktop
          npm ci
          npm test
```

---

### 阶段六：文档更新

**目标**: 更新所有文档以反映新结构

#### 6.1 创建根 README.md

```markdown
# NomadMark

<div align="center">

**专为 E-ink 设备优化的跨平台 Markdown 编辑器**

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Build Status](https://github.com/nomadmark/nomadmark/workflows/build/badge.svg)](https://github.com/nomadmark/nomadmark/actions)
[![Platform](https://img.shields.io/badge/platform-Android%20%7C%20Desktop%20%7C%20iOS-lightgrey.svg)](https://github.com/nomadmark/nomadmark)

[English](./README_EN.md) | 简体中文

</div>

## 特性

- 📝 **完整 Markdown 支持** - GFM (GitHub Flavored Markdown) + 扩展语法
- 🚄 **大文件支持** - 流式解析，支持 GB 级文件
- 🔍 **全文搜索** - 支持正则表达式和替换
- ↩️ **撤销/重做** - 完整的历史记录管理
- 📱 **E-ink 优化** - 局部刷新、A2 模式、延迟刷新
- ✏️ **手写识别** - DELETE、INSERT、SELECT 手势
- 📑 **目录导航** - 可折叠的树形目录
- 🖥️ **跨平台** - Android、Desktop (Windows/macOS/Linux)、iOS (开发中)

## 快速开始

### 前置要求

- Rust 1.70+
- Node.js 18+ (Desktop 平台)
- Android SDK 34+ (Android 平台)
- Xcode 14+ (iOS 平台)

### 构建项目

```bash
# 克隆仓库
git clone https://github.com/nomadmark/nomadmark.git
cd nomadmark

# 构建所有平台
./scripts/build/build-all.sh

# 或单独构建
./scripts/build/build-android.sh   # Android APK
./scripts/build/build-desktop.sh   # Desktop 应用
./scripts/build/build-ios.sh       # iOS 应用 (需 macOS)
```

### 开发模式

```bash
# Android
./scripts/dev/run-android.sh

# Desktop
./scripts/dev/run-desktop.sh

# iOS (需 macOS)
./scripts/dev/run-ios.sh
```

## 项目结构

```
NomadMark/
├── core/           # 共享 Rust 核心库
├── platforms/      # 平台实现
│   ├── android/
│   ├── desktop/
│   └── ios/
├── resources/      # 共享资源
├── docs/           # 项目文档
└── scripts/        # 构建脚本
```

详细结构请参阅 [架构文档](./docs/architecture/structure.md)。

## 文档

- [架构设计](./docs/architecture/design-v2.md)
- [API 文档](./docs/api/)
- [开发指南](./docs/guides/)
- [变更日志](./CHANGELOG.md)

## 贡献

欢迎贡献！请参阅 [贡献指南](./CONTRIBUTING.md)。

## 许可证

[MIT License](./LICENSE)
```

#### 6.2 创建 CONTRIBUTING.md

```markdown
# 贡献指南

感谢您对 NomadMark 的关注！

## 开发环境设置

### Rust 工具链

```bash
# 安装 Rust
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh

# 安装组件
rustup component add clippy rustfmt
```

### Android Studio

1. 打开 `platforms/android` 目录
2. 等待 Gradle 同步完成
3. 运行 `./gradlew assembleDebug`

### VS Code

推荐安装以下扩展：
- Rust Analyzer
- CodeLLDB
- ES Lint
- Prettier

## 代码风格

### Rust

```bash
# 格式化代码
cargo fmt

# 检查代码
cargo clippy
```

### Kotlin

```bash
# 使用 ktlint
./gradlew ktlintCheck
./gradlew ktlintFormat
```

### TypeScript

```bash
# 使用 eslint 和 prettier
npm run lint
npm run format
```

## 提交规范

使用 [Conventional Commits](https://www.conventionalcommits.org/) 格式：

- `feat:` 新功能
- `fix:` 修复 bug
- `docs:` 文档更新
- `style:` 代码格式调整
- `refactor:` 重构
- `test:` 测试相关
- `chore:` 构建/工具链更新

示例：
```
feat(android): add gesture recognition for DELETE
fix(core): correct memory leak in streaming parser
docs(api): update FFI documentation
```

## Pull Request 流程

1. Fork 仓库
2. 创建特性分支 (`git checkout -b feature/amazing-feature`)
3. 提交更改 (`git commit -m 'feat: add amazing feature'`)
4. 推送到分支 (`git push origin feature/amazing-feature`)
5. 创建 Pull Request

## 行为准则

请参阅 [CODE_OF_CONDUCT.md](./CODE_OF_CONDUCT.md)。
```

---

## 验证清单

### 编译验证

- [ ] Core 层编译成功 (`cargo build --release -p markdown_core`)
- [ ] Android APK 构建成功 (`./gradlew assembleDebug`)
- [ ] Desktop 应用构建成功 (`npm run tauri:build`)
- [ ] 所有单元测试通过 (`cargo test --workspace`)

### 功能验证

- [ ] Android 应用启动正常
- [ ] 文件打开/保存功能正常
- [ ] 搜索功能正常
- [ ] 撤销/重做功能正常
- [ ] 预览渲染正常
- [ ] 手写识别正常 (Android)
- [ ] 分屏模式正常 (Desktop)

### 文档验证

- [ ] README.md 更新完成
- [ ] CONTRIBUTING.md 创建完成
- [ ] 架构文档路径更新
- [ ] API 文档路径更新
- [ ] CI/CD 工作流配置完成

### Git 历史验证

- [ ] 所有迁移使用 `git mv`
- [ ] 文件历史完整保留
- [ ] `.gitignore` 正确配置
- [ ] 子模块（如有）正常

---

## 风险控制

### 风险识别

| 风险 | 概率 | 影响 | 缓解措施 |
|------|------|------|----------|
| 路径配置错误 | 中 | 高 | 渐进迁移，每步验证 |
| 构建失败 | 中 | 高 | 保留原结构备份 |
| Git 历史丢失 | 低 | 中 | 使用 git mv |
| CI/CD 延误 | 低 | 低 | 提前配置测试 |

### 回滚计划

```bash
# 如果重构失败，执行回滚

# 方案一：Git 回滚
git revert <commit-hash>

# 方案二：恢复备份
cp -r ../NomadMark-backup/* .

# 方案三：分支恢复
git checkout backup-branch
```

### 备份策略

```bash
# 重构前创建备份分支
git branch backup-before-refactor

# 或创建完整备份
cp -r . ../NomadMark-backup
```

---

## 附录

### A. 路径对照表

| 原路径 | 新路径 |
|--------|--------|
| `core/` | `core/` (不变，仅加入 workspace) |
| `android/` | `platforms/android/` |
| `desktop/` | `platforms/desktop/` |
| `ios/` | `platforms/ios/` |
| `common/` | `resources/` |
| `PROJECT_STRUCTURE_ANALYSIS.md` | `docs/architecture/structure.md` |
| `IMPLEMENTATION_GUIDE.md` | `docs/guides/implementation.md` |

### B. 命令速查

```bash
# 重构相关命令
./scripts/build/build-all.sh          # 构建所有平台
cargo build --workspace              # 编译 workspace
cargo test --workspace               # 测试 workspace
cargo update -p markdown_core       # 更新 Core 依赖

# Android 相关
cd platforms/android
./gradlew clean
./gradlew assembleDebug
./gradlew test

# Desktop 相关
cd platforms/desktop
npm install
npm run tauri:dev
npm run tauri:build
```

### C. 常见问题

**Q: Cargo Workspace 如何管理依赖版本？**  
A: 在根 Cargo.toml 的 `[workspace.dependencies]` 中统一定义，成员使用 `xxx.workspace = true` 引用。

**Q: Android 如何自动构建 Rust Core？**  
A: 通过 build.rs 或 Gradle 任务在构建前调用 `cargo build`。

**Q: 如何调试路径问题？**  
A: 使用 `cargo build -vv` 查看详细构建信息，或使用 `print!` 输出路径。

---

**文档状态**: 📝 待执行  
**维护者**: NomadMark Team  
**最后更新**: 2026-07-03
