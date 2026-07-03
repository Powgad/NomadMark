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

### 阶段零：重构前准备（⭐ 新增）

**目标**: 确保重构安全可回滚，建立现状基准

#### 0.1 创建备份分支

```bash
# 创建重构前的备份分支
git branch backup-before-refactor-$(date +%Y%m%d)

# 确认分支创建成功
git branch | grep backup
```

#### 0.2 现状快照

记录当前项目状态，用于回滚对比：

```bash
#!/bin/bash
# scripts/snapshot-pre-refactor.sh

SNAPSHOT_DIR="pre-refactor-snapshot-$(date +%Y%m%d)"
mkdir -p "$SNAPSHOT_DIR"

# 记录目录结构
tree -L 3 -I 'target|node_modules|.git' > "$SNAPSHOT_DIR/directory-structure.txt"

# 记录 Cargo 依赖
cd core && cargo tree > "../$SNAPSHOT_DIR/cargo-deps.txt" && cd ..

# 记录 npm 依赖（如果存在）
if [ -f "desktop/package.json" ]; then
    cd desktop && npm list --depth=0 > "../$SNAPSHOT_DIR/npm-deps.txt" && cd ..
fi

# 记录 Gradle 配置
cp android/build.gradle "$SNAPSHOT_DIR/"
cp android/app/build.gradle "$SNAPSHOT_DIR/"

# 记录 Git 状态
git status > "$SNAPSHOT_DIR/git-status.txt"
git log --oneline -10 > "$SNAPSHOT_DIR/git-recent-commits.txt"

echo "✅ 快照已保存到: $SNAPSHOT_DIR"
```

#### 0.3 功能验证

确保当前版本可正常构建和运行：

```bash
# 验证 Core 构建
cd core && cargo build --release && cargo test && cd ..

# 验证 Android 构建（如果有环境）
cd android && ./gradlew assembleDebug && cd ..

# 验证 Desktop 构建（如果有环境）
cd desktop && npm run tauri:build && cd ..

echo "✅ 当前版本构建验证通过"
```

#### 0.4 依赖清单

记录所有关键依赖的版本：

```bash
# 创建依赖清单
cat > PRE_REFACTOR_DEPENDENCIES.md << 'EOF'
# 重构前依赖清单

## Rust Core
- pulldown-cmark: $(grep pulldown-cmark core/Cargo.toml)
- rustybuzz: $(grep rustybuzz core/Cargo.toml)
- tantivy: $(grep tantivy core/Cargo.toml)

## Android
- Gradle: $(grep gradle android/build.gradle | head -1)
- compileSdk: $(grep compileSdk android/app/build.gradle)
- targetSdk: $(grep targetSdk android/app/build.gradle)

## Desktop
- Node.js: $(node --version)
- npm: $(npm --version)
- Tauri: $(grep tauri desktop/src-tauri/Cargo.toml)

## 构建工具
- Rust: $(rustc --version)
- Cargo: $(cargo --version)
EOF
```

---

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

### D. 改进建议与补充

本文档在实施过程中发现的改进建议，按优先级分类。

#### D.1 高优先级改进 🔴

##### D.1.1 根目录图片文件处理

**问题**: 当前根目录有大量设计稿和截图文件（约60个PNG），未明确处理方式。

**改进方案**: 在阶段二增加根目录清理步骤：

```bash
# 阶段 2.5：根目录清理（插入到文档迁移之后）

# 创建设计稿目录
mkdir -p docs/assets/designs
mkdir -p docs/assets/screenshots

# 移动设计稿
git mv *.png docs/assets/designs/ 2>/dev/null || true

# 移动竖屏布局设计稿
git mv "Mark down编辑器竖屏页面布局_*.png" docs/assets/designs/

# 确认文件移动正确后，更新文档中的引用路径
```

##### D.1.2 Android 多架构构建策略

**问题**: 缺少多架构同时编译的具体方案。

**改进方案**: 在阶段四增加 `cargo-ndk` 工具集成：

```bash
# 安装 cargo-ndk
cargo install cargo-ndk

# 构建 Android 所有架构
cargo ndk --target arm64-v8a --platform 28 build --release
cargo ndk --target armeabi-v7a --platform 28 build --release
cargo ndk --target x86_64 --platform 28 build --release

# 输出位置
# target/aarch64-linux-android/release/libmarkdown_core.so
# target/armv7-linux-androideabi/release/libmarkdown_core.so
# target/x86_64-linux-android/release/libmarkdown_core.so
```

对应的 `scripts/build/build-android.sh` 更新：

```bash
#!/bin/bash
# 使用 cargo-ndk 构建
cargo ndk --target arm64-v8a --platform 28 build --release
cargo ndk --target armeabi-v7a --platform 28 build --release

# 复制到 jniLibs
mkdir -p platforms/android/app/src/main/jniLibs/arm64-v8a
mkdir -p platforms/android/app/src/main/jniLibs/armeabi-v7a

cp target/aarch64-linux-android/release/libmarkdown_core.so \
   platforms/android/app/src/main/jniLibs/arm64-v8a/
cp target/armv7-linux-androideabi/release/libmarkdown_core.so \
   platforms/android/app/src/main/jniLibs/armeabi-v7a/
```

##### D.1.3 Windows 构建脚本兼容性

**问题**: 现有脚本都是 bash 格式，用户在 Windows 上运行困难。

**改进方案**: 提供跨平台构建方案，推荐使用 `just`：

```makefile
# justfile - 根目录

# 默认任务：显示帮助
default:
    @just --list

# 构建 Android
build-android:
    cd core && cargo build --release --target aarch64-linux-android
    cd platforms/android && ./gradlew assembleDebug

# 构建 Desktop
build-desktop:
    cd platforms/desktop && npm run tauri:build

# 构建所有
build-all: build-android build-desktop

# 运行测试
test:
    cargo test --workspace

# 代码检查
lint:
    cargo clippy --workspace
    cargo fmt --check
```

安装命令：
```powershell
# Windows (PowerShell)
winget install just.command

# 或手动下载
# https://github.com/casey/just/releases
```

##### D.1.4 根目录剩余文件处理

**问题**: 根目录仍有大量文档和脚本文件未处理，影响项目整洁度。

**当前状态**：
```
根目录剩余文件：
├── build-android.sh / .bat     # 构建脚本
├── 14 个 MD 文档                # 技术文档
└── ratta-android-apk-check.md   # 临时文档
```

**改进方案**: 在阶段二增加根目录完全清理步骤：

```bash
# 阶段 2.6：根目录完全清理

# 1. 移动构建脚本
git mv build-android.sh scripts/build/
git mv build-android.bat scripts/build/

# 2. 移动技术文档到 docs/ 对应子目录
git mv DEPLOYMENT_GUIDE.md docs/guides/deployment.md
git mv IMPLEMENTATION_GUIDE.md docs/guides/implementation.md
git mv TESTING_CHECKLIST.md docs/guides/testing.md
git mv MISSING_FEATURES_ANALYSIS.md docs/guides/missing-features.md

# 3. 移动报告文档
git mv PROJECT_PROGRESS.md docs/reports/progress.md
git mv MARKDOWN_RENDERING_STATUS.md docs/reports/markdown-status.md
git mv ratta-android-apk-check.md docs/reports/ratta-android-check.md

# 4. 移动架构文档（已在 2.3 处理，这里确认）
git mv PROJECT_STRUCTURE.md docs/architecture/structure.md
git mv "《架构设计书 v2.0》.md" docs/architecture/design-v2.md
git mv "详细设计文档.md" docs/architecture/detailed-design.md
git mv "《UI交互文档》.md" docs/features/ui-interactions.md

# 5. 移动路线图文档
git mv DEVELOPMENT_ROADMAP.md docs/roadmap/roadmap.md
git mv ROADMAP_ANALYSIS.md docs/roadmap/analysis.md

# 6. 移动功能文档
git mv MARKDOWN_RENDERING_FEATURES.md docs/features/markdown-rendering.md

# 7. 处理 SKILL.md（项目技能说明，可移至 docs/ 或保留在根目录）
git mv SKILL.md docs/skills.md  # 或保留在根目录作为项目入口
```

##### D.1.5 Git 配置文件更新

**问题**: 重构后目录结构变化，`.gitignore` 需要更新，且缺少 `.gitattributes`。

**改进方案**: 在阶段二最后更新 Git 配置。

**更新 `.gitignore`**：

```bash
# .gitignore 更新内容

# ========== 平台特定 ==========
# Android
platforms/android/.gradle/
platforms/android/.idea/
platforms/android/.vscode/
platforms/android/captures/
platforms/android/app/build/
platforms/android/local.properties
platforms/android/*.iml

# Desktop (Tauri)
platforms/desktop/src-tauri/target/
platforms/desktop/node_modules/
platforms/desktop/dist/
platforms/desktop/dist-ssr/
platforms/desktop/.tauri/

# iOS
platforms/ios/Pods/
platforms/ios/*.xcworkspace
platforms/ios/DerivedData/

# ========== 统一构建输出 ==========
build/
target/
*.apk
*.aab
*.ipa
*.dmg
*.exe

# ========== 但保留重要文件 ==========
!platforms/android/app/src/main/jniLibs/*.so

# ========== 开发工具 ==========
.vscode/
.idea/
*.swp
*.swo
*~

# ========== 系统文件 ==========
.DS_Store
Thumbs.db
desktop.ini
```

**创建 `.gitattributes`**：

```bash
# .gitattributes - 统一行尾符和文件处理

# 默认文本文件使用 LF 换行符
* text=auto eol=lf

# Windows 批处理文件使用 CRLF
*.bat text eol=crlf
*.cmd text eol=crlf

# Shell 脚本使用 LF
*.sh text eol=lf

# Markdown 文件使用 LF
*.md text eol=lf

# 二进制文件
*.png binary
*.jpg binary
*.jpeg binary
*.gif binary
*.ico binary
*.pdf binary
*.so binary
*.a binary
*.dll binary
*.exe binary
*.apk binary
*.ipa binary

# Android APK
*.apk binary diff

# 特殊处理：确保某些文件不会被行尾符转换
git-lfs-filter-process(1) binary
```

---

#### D.2 中优先级改进 🟡

##### D.2.1 增量迁移策略

**问题**: 一次性大规模迁移风险较高。

**改进方案**: 将阶段二拆分为增量验证步骤：

```
阶段二修订：重组目录结构（增量式）

2.1 目录迁移（第一波：仅 Android）
    └── 验证 Android 构建

2.2 目录迁移（第二波：Desktop）
    └── 验证 Desktop 构建

2.3 文档迁移
    └── 验证文档链接

2.4 其他目录迁移（resources, tools, scripts）
    └── 最终验证
```

##### D.2.2 本地开发环境配置

**问题**: 重构后开发者需要重新配置 IDE 环境。

**改进方案**: 新增配置文件和文档。

**创建 `.vscode/settings.json`**：

```json
{
  "rust-analyzer.linkedProjects": [
    "Cargo.toml",
    "core/Cargo.toml",
    "platforms/desktop/src-tauri/Cargo.toml"
  ],
  "rust-analyzer.cargo.features": "all",
  "files.exclude": {
    "**/.git": true,
    "**/target": true,
    "**/node_modules": true
  }
}
```

**创建 `.vscode/extensions.json`**：

```json
{
  "recommendations": [
    "rust-lang.rust-analyzer",
    "tauri-apps.tauri-vscode",
    "vadimcn.vscode-lldb",
    "dbaeumer.vscode-eslint",
    "esbenp.prettier-vscode"
  ]
}
```

**新增文档 `docs/guides/setup-dev-env.md`**：

```markdown
# 开发环境设置指南

## VS Code 配置

1. 安装推荐的扩展（打开项目时会提示）
2. 确保工作区设置正确

## Android Studio 配置

1. 打开 `platforms/android` 目录
2. 等待 Gradle 同步完成
3. 确认 SDK 路径配置正确

## 构建验证

```bash
# 验证 Core 构建
cargo build -p markdown_core

# 验证 Android 构建
cd platforms/android && ./gradlew assembleDebug

# 验证 Desktop 构建
cd platforms/desktop && npm run tauri:build
```
```

##### D.2.3 依赖版本管理修复

**问题**: `workspace.dependencies` 不支持 `optional` 属性。

**修正方案**：更新根 `Cargo.toml` 配置：

```toml
# 错误写法（会报错）
[workspace.dependencies]
jni = { version = "0.21", optional = true }  # ❌

# 正确写法
[workspace.dependencies]
jni = "0.21"  # ✅

# 在 core/Cargo.toml 中声明 optional
[target.'cfg(target_os = "android")'.dependencies]
jni = { workspace = true, optional = true }
```

##### D.2.4 项目配置文件完善

**问题**: 缺少统一的项目配置文件，影响代码质量和团队协作。

**改进方案**: 在阶段六补充完整的项目配置文件。

**创建/更新 `.editorconfig`**：

```ini
# .editorconfig - 项目根目录
root = true

# ========== 默认设置 ==========
[*]
charset = utf-8
end_of_line = lf
insert_final_newline = true
trim_trailing_whitespace = true

# ========== Kotlin/Java ==========
[*.{kt,kts,java}]
indent_style = space
indent_size = 4
continuation_indent_size = 4
max_line_length = 120

# ========== Rust ==========
[*.{rs,toml}]
indent_style = tab
indent_size = 4
max_line_length = 100

# ========== TypeScript/JavaScript/JSON ==========
[*.{tsx,ts,jsx,js,json}]
indent_style = space
indent_size = 2

# ========== Markdown ==========
[*.md]
indent_style = space
indent_size = 2
trim_trailing_whitespace = false  # Markdown 允许行尾空格

# ========== Shell 脚本 ==========
[*.{sh,bash}]
indent_style = space
indent_size = 2
end_of_line = lf

# ========== YAML ==========
[*.{yml,yaml}]
indent_style = space
indent_size = 2

# ========== Gradle ==========
[*.gradle]
indent_style = space
indent_size = 4
```

**创建 `CODEOWNERS`**：

```
# .github/CODEOWNERS - 代码所有者配置

# ========== 核心代码 ==========
# 所有 Core 相关变更需要核心团队审核
core/ @nomadmark/core-team

# ========== 平台代码 ==========
# Android 平台
platforms/android/ @nomadmark/android-team

# Desktop 平台
platforms/desktop/ @nomadmark/desktop-team

# iOS 平台（开发中）
platforms/ios/ @nomadmark/ios-team

# ========== 构建和 CI ==========
# CI/CD 配置变更需要维护者审核
.github/ @nomadmark/maintainers
scripts/ @nomadmark/maintainers

# ========== 文档 ==========
# 文档变更任何人都可以审核
docs/ @nomadmark/docs-team @nomadmark/maintainers
*.md @nomadmark/docs-team

# ========== 紧急修复 ==========
# 安全相关需要所有维护者审核
SECURITY.md @nomadmark/maintainers
```

**创建/更新 `.rustfmt.toml`**：

```toml
# .rustfmt.toml - Rust 格式化配置

edition = "2021"
max_width = 100
hard_tabs = true
tab_spaces = 4

# 导入分组
imports_granularity = "Crate"
group_imports = "StdExternalCrate"
reorder_imports = true

# 代码风格
use_field_init_shorthand = true
use_try_shorthand = true
format_code_in_doc_comments = true
format_strings = true

# 注释
wrap_comments = true
comment_width = 80
normalize_comments = true

# 链式调用
chain_width = 60
single_line_if_else_max_width = 50

# 其他
merge_derives = true
remove_nested_parens = true
struct_lit_single_line = false
```

**创建/更新 `.clippy.toml`**：

```toml
# .clippy.toml - Clippy 配置

# 允许的复杂度阈值
cognitive-complexity-threshold = 30
type-complexity-threshold = 250

# 文档要求
missing-docs-in-private-items = false  # 私有项不需要文档

# 性能相关
too-many-arguments-threshold = 7
```

---

#### D.3 低优先级改进 🟢

##### D.3.1 CI/CD 成本优化

**改进方案**: 增加条件触发，避免不必要的构建：

```yaml
# .github/workflows/build-android.yml
on:
  push:
    branches: [main, develop]
    paths:
      - 'core/**'
      - 'platforms/android/**'
      - '.github/workflows/build-android.yml'
  pull_request:
    paths:
      - 'core/**'
      - 'platforms/android/**'
```

##### D.3.2 文档索引导航

**改进方案**: 创建 `docs/index.md`：

```markdown
# NomadMark 文档中心

## 📚 快速导航

### 架构文档
- [项目结构](architecture/structure.md)
- [架构设计 v2.0](architecture/design-v2.md)
- [详细设计](architecture/detailed-design.md)

### 开发指南
- [快速开始](guides/getting-started.md)
- [实现指南](guides/implementation.md)
- [部署指南](guides/deployment.md)
- [测试指南](guides/testing.md)

### API 文档
- [Core FFI API](api/core-ffi.md)
- [Android JNI API](api/jni-api.md)
- [Desktop Tauri API](api/tauri-commands.md)

### 报告
- [项目进度](reports/progress.md)
- [验证报告](reports/validation.md)

---

## 🎯 按角色查看

### 新开发者
1. 阅读 [快速开始](guides/getting-started.md)
2. 查看 [项目结构](architecture/structure.md)
3. 参考 [贡献指南](../CONTRIBUTING.md)

### 平台开发者
- **Android**: [JNI API](api/jni-api.md)
- **Desktop**: [Tauri API](api/tauri-commands.md)
- **iOS**: (开发中)

### Core 开发者
- [Core FFI API](api/core-ffi.md)
- [架构设计 v2.0](architecture/design-v2.md)
```

##### D.3.3 发布流程完善

**问题**: 阶段五提到 `release.yml`，但缺少完整的发布流程说明。

**改进方案**: 补充详细的发布流程文档。

**创建 `docs/guides/release-process.md`**：

```markdown
# 发布流程指南

## 版本管理

### 语义化版本

- **格式**: `MAJOR.MINOR.PATCH`
  - MAJOR: 破坏性变更
  - MINOR: 新功能，向后兼容
  - PATCH: Bug 修复

### 版本号示例

- `0.1.0` - 初始发布
- `0.2.0` - 添加搜索功能
- `0.2.1` - 修复搜索 bug
- `1.0.0` - 稳定版本，API 稳定

## 发布前检查清单

- [ ] 所有测试通过
- [ ] 更新 CHANGELOG.md
- [ ] 更新版本号（所有平台）
- [ ] 运行完整测试套件
- [ ] 检查依赖安全性
- [ ] 在发布分支上测试

## 发布步骤

### 1. 准备发布

\`\`\`bash
# 创建发布分支
git checkout -b release/v0.2.0

# 更新版本号
# core/Cargo.toml
# platforms/desktop/src-tauri/Cargo.toml
# platforms/android/app/build.gradle

# 更新 CHANGELOG.md
\`\`\`

### 2. 构建所有平台

\`\`\`bash
# 构建所有平台
./scripts/build/build-all.sh

# 运行测试
cargo test --workspace
cd platforms/android && ./gradlew test
cd platforms/desktop && npm test
\`\`\`

### 3. 创建 GitHub Release

\`\`\`bash
# 合并到主分支
git checkout main
git merge release/v0.2.0

# 创建标签
git tag -a v0.2.0 -m "Release v0.2.0"

# 推送标签
git push origin main --tags
\`\`\`

然后在 GitHub 创建 Release：
- 上传构建产物
- 使用 CHANGELOG.md 内容作为 Release Notes
- 检查自动构建的产物

### 4. 平台发布

**Android**:
- 上传到 Google Play Console
- 或发布 F-Droid 版本
- 更新 GitHub Release 中的 APK

**Desktop**:
- 发布到 GitHub Releases
- Windows: 代码签名
- macOS: 公证和分发
- Linux: AppImage

**iOS** (开发中):
- 上传到 App Store Connect

## 紧急发布流程

对于关键 bug 修复：

1. 在主分支直接修复
2. 创建补丁版本（如 0.2.1）
3. 跳过常规测试流程，只运行回归测试
4. 快速发布

## 回滚策略

如果发布出现问题：

1. 从 GitHub 删除 Release
2. 创建回滚标签（如 v0.2.0-rollback）
3. 重新构建上一版本
4. 重新发布
```

**更新 `scripts/release/tag.sh`**：

```bash
#!/bin/bash
# scripts/release/tag.sh

set -e

VERSION=$1

if [ -z "$VERSION" ]; then
    echo "Usage: $0 <version>"
    echo "Example: $0 0.2.0"
    exit 1
fi

echo "🏷️  准备发布 v$VERSION"

# 更新 Core 版本
sed -i "s/^version = .*/version = \"$VERSION\"/" core/Cargo.toml

# 更新 Desktop 版本
sed -i "s/\"version\": .*/\"version\": \"$VERSION\"/" desktop/package.json
sed -i "s/^version = .*/version = \"$VERSION\"/" desktop/src-tauri/Cargo.toml

# 更新 Android 版本
# versionCode 需要递增
sed -i "s/versionName .*/versionName \"$VERSION\"/" platforms/android/app/build.gradle

# 提交版本更改
git add -A
git commit -m "chore: bump version to $VERSION"

# 创建标签
git tag -a "v$VERSION" -m "Release v$VERSION"

echo "✅ 版本 $VERSION 已标记"
echo "📝 请更新 CHANGELOG.md 后推送: git push --tags"
```

##### D.3.4 安全策略配置

**问题**: 缺少安全漏洞报告流程和依赖审计机制。

**改进方案**: 创建安全策略文档和依赖审计流程。

**创建 `SECURITY.md`**：

```markdown
# 安全策略

## 报告安全漏洞

如果您发现安全漏洞，请**不要**公开创建 Issue。

### 报告流程

1. **发送邮件** 至 security@nomadmark.com
2. 使用邮件主题: `[Security] 漏洞描述`
3. 包含以下信息:
   - 漏洞描述
   - 影响范围
   - 复现步骤
   - 建议的修复方案

### 响应时间

- **确认收到**: 48 小时内
- **详细评估**: 7 天内
- **修复时间**: 视严重程度而定

## 安全最佳实践

### 开发者

- 使用强密码和双因素认证
- 定期更新依赖
- 不在代码中硬编码密钥
- 使用环境变量存储敏感信息

### 用户

- 保持应用更新
- 只从官方来源下载
- 验证下载文件的签名

## 已知安全问题

当前没有已知的安全漏洞。历史安全问题将在修复后在此记录。

## 致谢

感谢所有负责任地报告安全问题的研究人员。
```

**创建 `scripts/audit.sh`**：

```bash
#!/bin/bash
# scripts/audit.sh - 依赖审计脚本

set -e

echo "🔒 开始安全审计..."

# 1. Rust 依赖审计
echo "📦 检查 Rust 依赖..."
if command -v cargo-audit &> /dev/null; then
    cd core
    cargo audit
    cd ..
else
    echo "⚠️  cargo-audit 未安装，跳过 Rust 审计"
    echo "   安装: cargo install cargo-audit"
fi

# 2. npm 依赖审计
echo "📦 检查 npm 依赖..."
if [ -f "desktop/package.json" ]; then
    cd desktop
    npm audit
    cd ..
fi

# 3. 检查敏感信息
echo "🔍 检查敏感信息泄露..."
SECRETS_PATTERN="(password|secret|api_key|token|private_key)"
if grep -ri "$SECRETS_PATTERN" --include="*.rs" --include="*.kt" --include="*.ts" core/ platforms/ 2>/dev/null; then
    echo "⚠️  发现可能的敏感信息，请检查"
fi

# 4. 检查许可证兼容性
echo "📄 检查许可证..."
cd core
if command -v cargo-deny &> /dev/null; then
    cargo deny check licenses
else
    echo "⚠️  cargo-deny 未安装，跳过许可证检查"
    echo "   安装: cargo install cargo-deny"
fi
cd ..

echo "✅ 安全审计完成"
```

**创建 `deny.toml`（许可证配置）**：

```toml
# deny.toml - Rust 依赖许可证配置

[licenses]
# 许可证白名单
allow = [
    "MIT",
    "Apache-2.0",
    "Apache-2.0 WITH LLVM-exception",
    "BSD-2-Clause",
    "BSD-3-Clause",
    "ISC",
    "Unicode-DFS-2016"
]

# 需要澄清的许可证
clarify = [
    "GPL-3.0",
    "GPL-2.0"
]

# 拒绝的许可证
deny = [
    "GPL-3.0",
    "AGPL-3.0"
]

[bans]
# 禁止重复的多个版本
multiple-versions = "warn"

# 禁止特定 crate
deny = [
    # 有安全问题的 crate
    { name = "openssl-sys", version = "<0.9.60" },
]

[sources]
# 只允许官方源
allow = [
    "https://github.com/rust-lang/crates.io-index"
]

# 禁止 Git 依赖（除非明确允许）
allow-git = false
```

---

#### D.4 补充验证清单

在原有验证清单基础上，增加以下检查项：

```bash
# 完整验证脚本 (scripts/verify-refactoring.sh)

#!/bin/bash
set -e

echo "🔍 验证重构结果..."

# 1. Rust 编译验证
echo "📦 检查 Cargo Workspace..."
cargo check --workspace

echo "🧪 运行所有测试..."
cargo test --workspace

echo "🔍 Clippy 检查..."
cargo clippy --workspace -- -D warnings

# 2. Android 验证
echo "📱 检查 Android 构建..."
cd platforms/android
./gradlew clean assembleDebug
cd ../..

# 3. Desktop 验证
echo "💻 检查 Desktop 构建..."
cd platforms/desktop
npm ci
npm run tauri:build
cd ../..

# 4. 文档链接检查
echo "📄 检查文档链接..."
# 可以使用 markdown-link-check 工具

# 5. 文件结构验证
echo "📂 验证目录结构..."
required_dirs=(
    "core"
    "platforms/android"
    "platforms/desktop"
    "docs/architecture"
    "docs/api"
    "docs/guides"
    "scripts/build"
    "scripts/dev"
)

for dir in "${required_dirs[@]}"; do
    if [ ! -d "$dir" ]; then
        echo "❌ 缺少目录: $dir"
        exit 1
    fi
done

echo "✅ 所有验证通过！"
```

---

#### D.5 回滚补充

在回滚计划中增加更详细的步骤：

```bash
# 详细回滚脚本 (scripts/rollback-refactor.sh)

#!/bin/bash
BACKUP_BRANCH="backup-before-refactor-${USER}-$(date +%Y%m%d)"

echo "🔄 开始回滚重构..."

# 方案选择
echo "选择回滚方案："
echo "1. Git 回滚（推荐）"
echo "2. 恢复备份文件"
read -p "请选择 (1/2): " choice

if [ "$choice" = "1" ]; then
    # Git 回滚
    echo "📝 检查是否有未提交的更改..."
    if [ -n "$(git status --porcelain)" ]; then
        echo "⚠️  有未提交的更改，请先处理"
        git status
        exit 1
    fi

    echo "🔄 回滚到重构前的提交..."
    # 假设重构开始时的提交哈希是 REFACTOR_START
    git revert REFACTOR_START..HEAD --no-commit

    echo "📝 请检查更改，确认后提交"
    git status

elif [ "$choice" = "2" ]; then
    # 恢复备份
    if [ ! -d "../NomadMark-backup" ]; then
        echo "❌ 备份目录不存在"
        exit 1
    fi

    echo "📂 从备份恢复..."
    rm -rf core platforms docs resources scripts tools
    cp -r ../NomadMark-backup/{core,platforms,docs,resources,scripts,tools} .

    echo "📝 恢复根目录文件..."
    cp ../NomadMark-backup/{Cargo.toml,Cargo.lock,.gitignore,.rustfmt.toml} .

    echo "✅ 恢复完成，请验证构建"
else
    echo "❌ 无效选择"
    exit 1
fi
```

---

**文档状态**: 📝 待执行
**维护者**: NomadMark Team
**最后更新**: 2026-07-03
