# NomadMark PC 端开发任务安排

> **版本**: 2.0
> **日期**: 2026-08-04
> **平台**: Desktop (Tauri + React)
> **目标**: 完成跨平台桌面编辑器开发

---

## 目录

1. [项目概述](#项目概述)
2. [技术栈](#技术栈)
3. [开发阶段总览](#开发阶段总览)
4. [阶段一：基础架构搭建](#阶段一基础架构搭建)
5. [阶段二：核心编辑功能](#阶段二核心编辑功能)
6. [阶段三：UI/UX 完善](#阶段三uiux-完善)
7. [阶段四：高级功能](#阶段四高级功能)
8. [阶段五：打包发布](#阶段五打包发布)
9. [时间线汇总](#时间线汇总)
10. [验证清单](#验证清单)

---

## 项目概述

### 目标

为 NomadMark 项目开发功能完善的跨平台桌面编辑器，支持 Windows、macOS 和 Linux。

### 当前状态

```
Desktop 平台完成度: ~50%
✅ Tauri 项目框架已建立
✅ 基础 React 组件已创建
⚠️ FFI 集成待完善
⚠️ 编辑功能待实现
❌ Canvas 渲染未完成
❌ 文档管理待实现
```

---

## 技术栈

| 层级 | 技术 | 版本 | 用途 |
|------|------|------|------|
| **前端框架** | React | 18.2+ | UI 框架 |
| **类型系统** | TypeScript | 5.3+ | 类型安全 |
| **构建工具** | Vite | 5.0+ | 开发服务器/打包 |
| **桌面框架** | Tauri | 1.6 | 桌面应用封装 |
| **后端语言** | Rust | 1.70+ | Core 层 + Tauri 后端 |
| **核心库** | markdown_core | - | Markdown 处理核心 |
| **Markdown 渲染** | react-markdown | 9.0+ | 前端降级渲染 |
| **代码高亮** | react-syntax-highlighter | 15.0+ | 代码块高亮 |

---

## 开发阶段总览

```
┌─────────────────────────────────────────────────────────────────────┐
│                       PC 端开发里程碑                                 │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  [M1] 基础架构完成    → Tauri 项目可运行，基本路由正常               │
│                                                                      │
│  [M2] 核心编辑完成    → 可编辑/预览 Markdown 文档                    │
│                                                                      │
│  [M3] UI/UX 完善     → 工具栏、快捷键、主题支持                      │
│                                                                      │
│  [M4] 高级功能完成    → 搜索、替换、分屏、自动保存                    │
│                                                                      │
│  [M5] 打包发布        → 可生成各平台安装包                            │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

| 阶段 | 名称 | 任务数 | 预计工时 | 完成度 |
|------|------|--------|----------|--------|
| 阶段一 | 基础架构搭建 | 8 | 12h | 30% |
| 阶段二 | 核心编辑功能 | 7 | 28h | 0% |
| 阶段三 | UI/UX 完善 | 8 | 20h | 0% |
| 阶段四 | 高级功能 | 6 | 18h | 0% |
| 阶段五 | 打包发布 | 5 | 12h | 0% |
| **总计** | | **34** | **90h** | **~7%** |

---

## 阶段一：基础架构搭建

> **目标**: 搭建可运行的项目框架，建立前后端通信
> **预计工时**: 12 小时
> **依赖**: Core 层 (markdown_core) 可用

### 阶段概述

本阶段建立整个 Desktop 应用的基础架构，包括 Tauri 后端与 Rust Core 的 FFI 集成、React 前端组件结构、类型定义系统、状态管理 Hook 以及应用基础布局。

---

### 任务 1.1：项目初始化与依赖安装 (2h)

**状态**: ✅ 已完成

**描述**: 确保 Tauri 项目框架正确搭建，安装所有必需依赖

**文件**: `platforms/desktop/package.json`

**依赖清单**:

```json
{
  "dependencies": {
    "@tauri-apps/api": "^1.5.3",
    "react": "^18.2.0",
    "react-dom": "^18.2.0",
    "react-markdown": "^9.0.0",
    "react-syntax-highlighter": "^15.5.0",
    "remark-gfm": "^4.0.0",
    "remark-math": "^6.0.0",
    "rehype-katex": "^7.0.0",
    "katex": "^0.16.0"
  },
  "devDependencies": {
    "@tauri-apps/cli": "^1.5.10",
    "@types/react": "^18.2.47",
    "@types/react-dom": "^18.2.18",
    "@types/react-syntax-highlighter": "^15.5.0",
    "@vitejs/plugin-react": "^4.2.1",
    "typescript": "^5.3.3",
    "vite": "^5.0.11"
  }
}
```

**安装命令**:
```bash
cd platforms/desktop
npm install
```

**检查项**:
- [ ] package.json 配置正确
- [ ] tsconfig.json 配置正确
- [ ] vite.config.ts 配置正确
- [ ] tauri.conf.json 配置正确
- [ ] node_modules 安装完成

---

### 任务 1.2：Tauri 后端 FFI 集成 (3h)

**状态**: ⚠️ 部分完成

**描述**: 建立 Tauri 与 Rust Core 的 FFI 桥接，实现文档操作的 Tauri Command

**文件**: `platforms/desktop/src-tauri/src/main.rs`

**实现步骤**:

#### 步骤 1: 更新 Cargo.toml

```toml
# src-tauri/Cargo.toml

[package]
name = "nomadmark-desktop"
version = "0.1.0"
edition = "2021"

[build-dependencies]
tauri-build = { version = "1.5", features = [] }

[dependencies]
tauri = { version = "1.6", features = ["shell-open"] }
serde = { version = "1.0", features = ["derive"] }
serde_json = "1.0"

# 引用共享的 Core 库
markdown_core = { path = "../../../core" }

# 错误处理
thiserror = "1.0"

# 异步运行时
tokio = { version = "1.35", features = ["full"] }
```

#### 步骤 2: 实现后端主文件

```rust
// src-tauri/src/main.rs

use std::collections::HashMap;
use std::sync::{Arc, Mutex};
use std::sync::atomic::{AtomicU64, Ordering};
use tauri::State;

/// 应用全局状态
pub struct AppState {
    /// 文档存储: ID -> 文档内容
    documents: Arc<Mutex<HashMap<u64, String>>>,
    /// 下一个文档 ID
    next_id: AtomicU64,
    /// 当前打开的文档 ID
    current_document: AtomicU64,
}

impl AppState {
    pub fn new() -> Self {
        Self {
            documents: Arc::new(Mutex::new(HashMap::new())),
            next_id: AtomicU64::new(1),
            current_document: AtomicU64::new(0),
        }
    }
}

/// 创建新文档
#[tauri::command]
fn create_document(content: String, state: State<AppState>) -> Result<u64, String> {
    let id = state.next_id.fetch_add(1, Ordering::SeqCst);
    
    let mut docs = state.documents.lock()
        .map_err(|e| format!("Failed to acquire lock: {}", e))?;
    
    docs.insert(id, content);
    state.current_document.store(id, Ordering::SeqCst);
    
    Ok(id)
}

/// 从文件内容创建文档
#[tauri::command]
fn create_document_from_bytes(content: Vec<u8>, state: State<AppState>) -> Result<u64, String> {
    let content_str = String::from_utf8(content)
        .map_err(|e| format!("Invalid UTF-8: {}", e))?;
    
    create_document(content_str, state)
}

/// 更新文档内容
#[tauri::command]
fn update_document(id: u64, content: String, state: State<AppState>) -> Result<(), String> {
    let mut docs = state.documents.lock()
        .map_err(|e| format!("Failed to acquire lock: {}", e))?;
    
    if docs.contains_key(&id) {
        docs.insert(id, content);
        Ok(())
    } else {
        Err("Document not found".to_string())
    }
}

/// 获取文档内容
#[tauri::command]
fn get_document(id: u64, state: State<AppState>) -> Result<String, String> {
    let docs = state.documents.lock()
        .map_err(|e| format!("Failed to acquire lock: {}", e))?;
    
    docs.get(&id)
        .cloned()
        .ok_or_else(|| "Document not found".to_string())
}

/// 渲染 Markdown 为 HTML
#[tauri::command]
fn render_markdown(content: String) -> Result<String, String> {
    // 调用 Core 层渲染
    // 暂时使用前端降级方案
    Ok(markdown_core::render_to_html(&content)
        .unwrap_or_else(|_| String::new()))
}

/// 释放文档
#[tauri::command]
fn release_document(id: u64, state: State<AppState>) -> Result<(), String> {
    let mut docs = state.documents.lock()
        .map_err(|e| format!("Failed to acquire lock: {}", e))?;
    
    docs.remove(&id);
    
    // 如果释放的是当前文档，重置当前 ID
    if state.current_document.load(Ordering::SeqCst) == id {
        state.current_document.store(0, Ordering::SeqCst);
    }
    
    Ok(())
}

/// 读取文件内容
#[tauri::command]
async fn read_file(path: String) -> Result<String, String> {
    use std::fs;
    use std::io::Read;
    
    let mut file = fs::File::open(&path)
        .map_err(|e| format!("Failed to open file: {}", e))?;
    
    let mut content = String::new();
    file.read_to_string(&mut content)
        .map_err(|e| format!("Failed to read file: {}", e))?;
    
    Ok(content)
}

/// 写入文件内容
#[tauri::command]
async fn write_file(path: String, content: String) -> Result<(), String> {
    use std::fs;
    use std::io::Write;
    
    let mut file = fs::File::create(&path)
        .map_err(|e| format!("Failed to create file: {}", e))?;
    
    file.write_all(content.as_bytes())
        .map_err(|e| format!("Failed to write file: {}", e))?;
    
    Ok(())
}

fn main() {
    tauri::Builder::default()
        .manage(AppState::new())
        .invoke_handler(tauri::generate_handler![
            create_document,
            create_document_from_bytes,
            update_document,
            get_document,
            render_markdown,
            release_document,
            read_file,
            write_file,
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
```

**验证方法**:
```bash
npm run tauri:dev
# 确认应用可启动，无编译错误
```

**注意事项**:
1. 确保 `markdown_core` 路径正确
2. 处理好并发访问的线程安全问题
3. 所有 Tauri Command 必须返回 `Result` 类型

---

### 任务 1.3：React 基础组件结构 (2h)

**状态**: ⚠️ 部分完成

**描述**: 创建完整的 React 组件目录结构和基础组件文件

**文件结构**:

```
src/
├── App.tsx                 # 主应用组件
├── main.tsx               # 应用入口
├── index.css              # 全局样式
├── index.html             # HTML 模板
├── vite-env.d.ts          # Vite 类型声明
├── components/            # 组件目录
│   ├── Editor.tsx         # 编辑器组件
│   ├── Preview.tsx        # 预览组件
│   ├── Toolbar.tsx        # 工具栏组件
│   ├── SplitView.tsx      # 分屏组件
│   ├── StatusBar.tsx      # 状态栏组件
│   ├── Sidebar.tsx        # 侧边栏组件
│   └── common/
│       ├── Button.tsx     # 通用按钮
│       ├── Icon.tsx       # 图标组件
│       └── Modal.tsx      # 模态框
├── hooks/                 # 自定义 Hooks
│   ├── useDocument.ts     # 文档状态管理
│   ├── useCanvasRenderer.ts # Canvas 渲染
│   ├── useKeyboard.ts     # 键盘事件
│   └── useAutoSave.ts     # 自动保存
├── types/                 # 类型定义
│   ├── index.ts           # 导出所有类型
│   ├── document.ts        # 文档相关类型
│   ├── editor.ts          # 编辑器相关类型
│   └── config.ts          # 配置相关类型
├── utils/                 # 工具函数
│   ├── markdown.ts        # Markdown 工具
│   ├── file.ts            # 文件工具
│   └── debounce.ts        # 防抖函数
└── styles/                # 样式文件
    ├── themes.css         # 主题样式
    ├── editor.css         # 编辑器样式
    └── preview.css        # 预览样式
```

**组件骨架示例**:

```typescript
// src/components/Editor.tsx

import React from 'react';

interface EditorProps {
  content: string;
  onChange: (content: string) => void;
  readOnly?: boolean;
}

export const Editor: React.FC<EditorProps> = ({
  content,
  onChange,
  readOnly = false
}) => {
  return (
    <div className="editor">
      <textarea
        value={content}
        onChange={(e) => onChange(e.target.value)}
        readOnly={readOnly}
        spellCheck={false}
      />
    </div>
  );
};
```

---

### 任务 1.4：类型定义系统 (1.5h)

**状态**: ⚠️ 部分完成

**描述**: 建立完整的 TypeScript 类型定义系统

**文件**: `src/types/index.ts`

```typescript
// ==================== 文档相关类型 ====================

/**
 * 文档状态
 */
export interface Document {
  /** 文档唯一标识 */
  id: number;
  /** 文档内容 */
  content: string;
  /** 文件路径 (可能不存在，如新建文档) */
  filePath?: string;
  /** 是否已修改 */
  isModified: boolean;
  /** 最后保存时间 */
  lastSaved: Date | null;
  /** 创建时间 */
  createdAt: Date;
}

/**
 * 文档元数据
 */
export interface DocumentMetadata {
  /** 标题 (从第一个 # 标题提取) */
  title?: string;
  /** 字数统计 */
  wordCount: number;
  /** 字符数 (不含空格) */
  charCount: number;
  /** 总字符数 */
  charCountWithSpaces: number;
  /** 行数 */
  lineCount: number;
  /** 段落数 */
  paragraphCount: number;
}

// ==================== 编辑器相关类型 ====================

/**
 * 编辑器配置
 */
export interface EditorConfig {
  /** 字体大小 */
  fontSize: number;
  /** 行高 */
  lineHeight: number;
  /** 字体族 */
  fontFamily: string;
  /** Tab 宽度 */
  tabSize: number;
  /** 是否显示行号 */
  showLineNumbers: boolean;
  /** 是否启用自动换行 */
  wordWrap: boolean;
  /** 最大编辑器宽度 (像素) */
  maxWidth: number;
}

/**
 * 光标位置
 */
export interface CursorPosition {
  /** 行号 (从 0 开始) */
  line: number;
  /** 列号 (从 0 开始) */
  column: number;
}

/**
 * 文本选择范围
 */
export interface TextSelection {
  /** 起始位置 */
  start: CursorPosition;
  /** 结束位置 */
  end: CursorPosition;
}

// ==================== 预览相关类型 ====================

/**
 * 渲染配置
 */
export interface RenderConfig {
  /** 主题 */
  theme: 'light' | 'dark' | 'eink' | 'sepia';
  /** 是否启用代码高亮 */
  enableSyntaxHighlight: boolean;
  /** 是否启用数学公式渲染 */
  enableMath: boolean;
  /** 是否启用 GFM (GitHub Flavored Markdown) */
  enableGFM: boolean;
  /** 代码主题 */
  codeTheme: string;
}

/**
 * 渲染结果
 */
export interface RenderResult {
  /** HTML 内容 */
  html: string;
  /** 渲染耗时 (毫秒) */
  renderTime: number;
  /** 是否有错误 */
  hasError: boolean;
  /** 错误信息 */
  error?: string;
}

// ==================== 搜索相关类型 ====================

/**
 * 搜索选项
 */
export interface SearchOptions {
  /** 是否区分大小写 */
  caseSensitive: boolean;
  /** 是否使用正则表达式 */
  regex: boolean;
  /** 是否全词匹配 */
  wholeWord: boolean;
  /** 搜索范围 */
  scope: 'current' | 'all';
}

/**
 * 搜索结果
 */
export interface SearchResult {
  /** 匹配的起始位置 */
  start: number;
  /** 匹配的结束位置 */
  end: number;
  /** 匹配的文本 */
  text: string;
  /** 所在行号 */
  lineNumber: number;
  /** 所在行内容 */
  lineContent: string;
}

// ==================== 历史相关类型 ====================

/**
 * 历史记录条目
 */
export interface HistoryEntry {
  /** 操作内容 (完整文档内容或差异) */
  content: string;
  /** 操作时间戳 */
  timestamp: number;
  /** 操作描述 */
  description: string;
  /** 操作类型 */
  type: 'insert' | 'delete' | 'replace' | 'format';
}

/**
 * 撤销/重做栈状态
 */
export interface HistoryState {
  /** 撤销栈 */
  undoStack: HistoryEntry[];
  /** 重做栈 */
  redoStack: HistoryEntry[];
  /** 当前栈位置 */
  currentIndex: number;
}

// ==================== 应用相关类型 ====================

/**
 * 应用主题
 */
export type AppTheme = 'light' | 'dark' | 'eink' | 'sepia' | 'auto';

/**
 * 应用设置
 */
export interface AppSettings {
  /** 主题 */
  theme: AppTheme;
  /** 编辑器配置 */
  editor: EditorConfig;
  /** 渲染配置 */
  render: RenderConfig;
  /** 是否启用自动保存 */
  autoSave: boolean;
  /** 自动保存间隔 (毫秒) */
  autoSaveInterval: number;
  /** 最近文件列表 */
  recentFiles: string[];
  /** 窗口状态 */
  windowState: {
    width: number;
    height: number;
    isMaximized: boolean;
  };
}

/**
 * 应用状态
 */
export interface AppState {
  /** 当前文档 */
  currentDocument: Document | null;
  /** 应用设置 */
  settings: AppSettings;
  /** 是否正在加载 */
  isLoading: boolean;
  /** 加载进度 */
  loadingProgress: number;
  /** 错误信息 */
  error: string | null;
}
```

---

### 任务 1.5：文档状态管理 Hook (2h)

**状态**: ⚠️ 部分完成

**描述**: 创建完整的文档状态管理 Hook，支持文档的创建、更新、保存等操作

**文件**: `src/hooks/useDocument.ts`

```typescript
import { useState, useCallback, useEffect, useRef } from 'react';
import { invoke } from '@tauri-apps/api/tauri';
import type { Document, DocumentMetadata } from '../types';

/**
 * 文档状态管理 Hook
 */
export function useDocument() {
  const [document, setDocument] = useState<Document | null>(null);
  const [metadata, setMetadata] = useState<DocumentMetadata | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  
  // 用于防抖保存
  const saveTimeoutRef = useRef<NodeJS.Timeout>();

  /**
   * 计算文档元数据
   */
  const calculateMetadata = useCallback((content: string): DocumentMetadata => {
    const lines = content.split('\n');
    
    // 提取标题
    let title: string | undefined;
    const titleMatch = content.match(/^#\s+(.+)$/m);
    if (titleMatch) {
      title = titleMatch[1];
    }
    
    // 字数统计 (按空格或换行分隔)
    const words = content.trim().split(/\s+/).filter(w => w.length > 0);
    const wordCount = words.length;
    
    // 字符统计
    const charCount = content.replace(/\s/g, '').length;
    const charCountWithSpaces = content.length;
    
    // 段落数 (以空行分隔)
    const paragraphs = content.split(/\n\s*\n/).filter(p => p.trim().length > 0);
    
    return {
      title,
      wordCount,
      charCount,
      charCountWithSpaces,
      lineCount: lines.length,
      paragraphCount: paragraphs.length,
    };
  }, []);

  /**
   * 创建新文档
   */
  const createDocument = useCallback(async (content: string = '') => {
    setIsLoading(true);
    setError(null);
    
    try {
      const id = await invoke<number>('create_document', { content });
      
      const newDocument: Document = {
        id,
        content,
        isModified: false,
        lastSaved: null,
        createdAt: new Date(),
      };
      
      setDocument(newDocument);
      setMetadata(calculateMetadata(content));
    } catch (err) {
      setError(err as string);
      console.error('Failed to create document:', err);
    } finally {
      setIsLoading(false);
    }
  }, [calculateMetadata]);

  /**
   * 从文件创建文档
   */
  const loadFromFile = useCallback(async (filePath: string) => {
    setIsLoading(true);
    setError(null);
    
    try {
      const content = await invoke<string>('read_file', { path: filePath });
      const id = await invoke<number>('create_document_from_bytes', { 
        content: content.asBytes() 
      });
      
      const newDocument: Document = {
        id,
        content,
        filePath,
        isModified: false,
        lastSaved: new Date(),
        createdAt: new Date(),
      };
      
      setDocument(newDocument);
      setMetadata(calculateMetadata(content));
    } catch (err) {
      setError(err as string);
      console.error('Failed to load file:', err);
    } finally {
      setIsLoading(false);
    }
  }, [calculateMetadata]);

  /**
   * 更新文档内容
   */
  const updateContent = useCallback((newContent: string) => {
    if (!document) return;
    
    setDocument({
      ...document,
      content: newContent,
      isModified: true,
    });
    
    setMetadata(calculateMetadata(newContent));
  }, [document, calculateMetadata]);

  /**
   * 保存文档
   */
  const saveDocument = useCallback(async (filePath?: string) => {
    if (!document) return;
    
    const path = filePath || document.filePath;
    if (!path) {
      throw new Error('No file path specified');
    }
    
    setIsLoading(true);
    setError(null);
    
    try {
      await invoke('write_file', { 
        path, 
        content: document.content 
      });
      
      // 同步更新到 Core 层
      await invoke('update_document', {
        id: document.id,
        content: document.content,
      });
      
      setDocument({
        ...document,
        filePath: path,
        isModified: false,
        lastSaved: new Date(),
      });
    } catch (err) {
      setError(err as string);
      console.error('Failed to save document:', err);
      throw err;
    } finally {
      setIsLoading(false);
    }
  }, [document]);

  /**
   * 释放文档
   */
  const releaseDocument = useCallback(async () => {
    if (!document) return;
    
    try {
      await invoke('release_document', { id: document.id });
      setDocument(null);
      setMetadata(null);
    } catch (err) {
      console.error('Failed to release document:', err);
    }
  }, [document]);

  /**
   * 清理定时器
   */
  useEffect(() => {
    return () => {
      if (saveTimeoutRef.current) {
        clearTimeout(saveTimeoutRef.current);
      }
    };
  }, []);

  return {
    document,
    metadata,
    isLoading,
    error,
    createDocument,
    loadFromFile,
    updateContent,
    saveDocument,
    releaseDocument,
  };
}
```

---

### 任务 1.6：应用基础布局实现 (1.5h)

**状态**: ❌ 未开始

**描述**: 实现 App.tsx 主应用组件和基础布局结构

**文件**: `src/App.tsx`

```typescript
import React, { useEffect } from 'react';
import { useDocument } from './hooks/useDocument';
import { Toolbar } from './components/Toolbar';
import { SplitView } from './components/SplitView';
import { StatusBar } from './components/StatusBar';
import { Sidebar } from './components/Sidebar';
import { LoadingScreen } from './components/common/LoadingScreen';
import { ErrorScreen } from './components/common/ErrorScreen';
import './styles/themes.css';

function App() {
  const {
    document,
    metadata,
    isLoading,
    error,
    createDocument,
    updateContent,
    saveDocument,
  } = useDocument();

  // 初始化时创建默认文档
  useEffect(() => {
    createDocument('# Welcome to NomadMark\n\nA modern Markdown editor for desktop.\n\n## Features\n\n- **Live Preview**: See your changes in real-time\n- **Split View**: Edit and preview side by side\n- **Themes**: Light, Dark, E-ink, and Sepia themes\n- **Auto Save**: Never lose your work\n\nStart typing to begin...');
  }, [createDocument]);

  // 显示加载状态
  if (isLoading && !document) {
    return <LoadingScreen message="Initializing NomadMark..." />;
  }

  // 显示错误状态
  if (error) {
    return <ErrorScreen error={error} />;
  }

  // 等待文档加载
  if (!document) {
    return <LoadingScreen message="Loading document..." />;
  }

  const handleContentChange = (newContent: string) => {
    updateContent(newContent);
  };

  const handleSave = () => {
    saveDocument();
  };

  const handleNew = () => {
    createDocument();
  };

  return (
    <div className="app">
      {/* 顶部工具栏 */}
      <Toolbar
        onNew={handleNew}
        onSave={handleSave}
        canSave={document.isModified}
      />
      
      {/* 主内容区 */}
      <div className="app-main">
        {/* 左侧边栏 */}
        <Sidebar />
        
        {/* 分屏编辑器 */}
        <SplitView
          content={document.content}
          onContentChange={handleContentChange}
        />
      </div>
      
      {/* 底部状态栏 */}
      <StatusBar
        metadata={metadata}
        isModified={document.isModified}
        filePath={document.filePath}
      />
    </div>
  );
}

export default App;
```

---

### 任务 1.7：全局样式系统 (1h)

**状态**: ❌ 未开始

**描述**: 建立全局样式和 CSS 变量系统

**文件**: `src/styles/themes.css`

```css
/* ==================== CSS 变量定义 ==================== */

:root {
  /* 颜色系统 - Light Theme */
  --color-bg-primary: #ffffff;
  --color-bg-secondary: #f6f8fa;
  --color-bg-tertiary: #eaeef2;
  --color-text-primary: #24292f;
  --color-text-secondary: #57606a;
  --color-text-tertiary: #8b949e;
  --color-border: #d0d7de;
  --color-border-light: #e1e4e8;
  --color-accent: #0969da;
  --color-accent-hover: #0860ca;
  --color-success: #1a7f37;
  --color-warning: #9a6700;
  --color-error: #cf222e;
  
  /* 编辑器颜色 */
  --editor-bg: #ffffff;
  --editor-text: #24292f;
  --editor-selection: #b6e3ff;
  --editor-line-number: #8b949e;
  --editor-current-line: #f6f8fa;
  
  /* 预览颜色 */
  --preview-bg: #ffffff;
  --preview-text: #24292f;
  --preview-link: #0969da;
  --preview-code-bg: #f6f8fa;
  --preview-quote: #57606a;
  
  /* 尺寸 */
  --toolbar-height: 48px;
  --statusbar-height: 24px;
  --sidebar-width: 250px;
  --min-content-width: 300px;
  
  /* 字体 */
  --font-ui: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Noto Sans', Helvetica, Arial, sans-serif;
  --font-editor: 'Menlo', 'Monaco', 'Courier New', monospace;
  --font-preview: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Noto Sans', Helvetica, Arial, sans-serif;
  
  /* 圆角 */
  --radius-sm: 4px;
  --radius-md: 6px;
  --radius-lg: 8px;
  
  /* 阴影 */
  --shadow-sm: 0 1px 2px rgba(0, 0, 0, 0.05);
  --shadow-md: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
  --shadow-lg: 0 10px 15px -3px rgba(0, 0, 0, 0.1);
  
  /* 过渡 */
  --transition-fast: 150ms ease;
  --transition-normal: 200ms ease;
}

/* Dark Theme */
[data-theme="dark"] {
  --color-bg-primary: #0d1117;
  --color-bg-secondary: #161b22;
  --color-bg-tertiary: #21262d;
  --color-text-primary: #c9d1d9;
  --color-text-secondary: #8b949e;
  --color-text-tertiary: #6e7681;
  --color-border: #30363d;
  --color-border-light: #21262d;
  --color-accent: #58a6ff;
  --color-accent-hover: #79b8ff;
  --color-success: #3fb950;
  --color-warning: #d29922;
  --color-error: #f85149;
  
  --editor-bg: #0d1117;
  --editor-text: #c9d1d9;
  --editor-selection: #264f78;
  --editor-line-number: #6e7681;
  --editor-current-line: #161b22;
  
  --preview-bg: #0d1117;
  --preview-text: #c9d1d9;
  --preview-link: #58a6ff;
  --preview-code-bg: #161b22;
  --preview-quote: #8b949e;
}

/* E-ink Theme */
[data-theme="eink"] {
  --color-bg-primary: #ffffff;
  --color-bg-secondary: #f5f5f5;
  --color-bg-tertiary: #eaeaea;
  --color-text-primary: #000000;
  --color-text-secondary: #333333;
  --color-text-tertiary: #666666;
  --color-border: #cccccc;
  --color-border-light:dddddd;
  --color-accent: #000000;
  --color-accent-hover: #333333;
  --color-success: #000000;
  --color-warning: #000000;
  --color-error: #000000;
  
  --editor-bg: #ffffff;
  --editor-text: #000000;
  --editor-selection: #cccccc;
  --editor-line-number: #666666;
  --editor-current-line: #f5f5f5;
  
  --preview-bg: #ffffff;
  --preview-text: #000000;
  --preview-link: #000000;
  --preview-code-bg: #f5f5f5;
  --preview-quote: #333333;
}

/* Sepia Theme */
[data-theme="sepia"] {
  --color-bg-primary: #f4ecd8;
  --color-bg-secondary: #e8dcc8;
  --color-bg-tertiary: #d4c4a8;
  --color-text-primary: #5c4b37;
  --color-text-secondary: #736355;
  --color-text-tertiary: #8f7a68;
  --color-border: #d4c4a8;
  --color-border-light: #e0d0b8;
  --color-accent: #8b5a3c;
  --color-accent-hover: #734a30;
  --color-success: #5c8a4a;
  --color-warning: #8a6a2a;
  --color-error: #a53a3a;
  
  --editor-bg: #f4ecd8;
  --editor-text: #5c4b37;
  --editor-selection: #d4c4a8;
  --editor-line-number: #8f7a68;
  --editor-current-line: #e8dcc8;
  
  --preview-bg: #f4ecd8;
  --preview-text: #5c4b37;
  --preview-link: #8b5a3c;
  --preview-code-bg: #e8dcc8;
  --preview-quote: #736355;
}

/* ==================== 全局样式重置 ==================== */

* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: var(--font-ui);
  font-size: 14px;
  color: var(--color-text-primary);
  background-color: var(--color-bg-primary);
  overflow: hidden;
}

button {
  font-family: inherit;
  font-size: inherit;
  border: none;
  background: none;
  cursor: pointer;
}

input,
textarea,
select {
  font-family: inherit;
  font-size: inherit;
  border: none;
  outline: none;
}

/* ==================== 应用布局 ==================== */

.app {
  width: 100vw;
  height: 100vh;
  display: flex;
  flex-direction: column;
  background-color: var(--color-bg-primary);
}

.app-main {
  flex: 1;
  display: flex;
  overflow: hidden;
}

/* ==================== 滚动条样式 ==================== */

::-webkit-scrollbar {
  width: 12px;
  height: 12px;
}

::-webkit-scrollbar-track {
  background: var(--color-bg-secondary);
}

::-webkit-scrollbar-thumb {
  background: var(--color-border);
  border-radius: 6px;
}

::-webkit-scrollbar-thumb:hover {
  background: var(--color-text-tertiary);
}
```

---

**阶段一完成标准**:
- [ ] 应用可正常启动 (npm run tauri:dev)
- [ ] Tauri 命令可被前端调用并返回正确结果
- [ ] 基础组件目录结构建立完成
- [ ] TypeScript 类型定义完整且无错误
- [ ] 全局样式系统正常工作
- [ ] 基础布局正确显示

---

## 阶段二：核心编辑功能

> **目标**: 实现编辑和预览 Markdown 文档的核心功能
> **预计工时**: 28 小时
> **依赖**: 阶段一完成

### 阶段概述

本阶段实现 Markdown 编辑器的核心功能，包括富文本编辑器、Markdown 预览渲染、分屏视图、Canvas 渲染引擎、以及文件操作（打开、保存、另存为）。

---

### 任务 2.1：编辑器组件实现 (8h)

**状态**: ❌ 未开始

**描述**: 实现功能完整的编辑器组件，支持语法高亮、行号显示、代码编辑等功能

**文件**: `src/components/Editor.tsx`

**功能清单**:
- 文本输入和编辑
- 行号显示
- 代码折叠（可选）
- Tab 键处理
- 快捷键支持
- 撤销/重做集成
- 光标位置跟踪
- 文本选择处理

**实现**:

```typescript
import React, { useRef, useEffect, useState, useCallback } from 'react';
import type { EditorConfig, CursorPosition } from '../types';

interface EditorProps {
  content: string;
  onChange: (content: string) => void;
  config?: Partial<EditorConfig>;
  readOnly?: boolean;
  className?: string;
}

const DEFAULT_CONFIG: EditorConfig = {
  fontSize: 14,
  lineHeight: 1.6,
  fontFamily: "'Menlo', 'Monaco', 'Courier New', monospace",
  tabSize: 4,
  showLineNumbers: true,
  wordWrap: true,
  maxWidth: 900,
};

export const Editor: React.FC<EditorProps> = ({
  content,
  onChange,
  config = {},
  readOnly = false,
  className = '',
}) => {
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const lineNumbersRef = useRef<HTMLDivElement>(null);
  const [cursorPosition, setCursorPosition] = useState<CursorPosition>({ line: 0, column: 0 });
  const [selection, setSelection] = useState<string | null>(null);
  
  const finalConfig = { ...DEFAULT_CONFIG, ...config };

  /**
   * 处理内容变化
   */
  const handleChange = useCallback((e: React.ChangeEvent<HTMLTextAreaElement>) => {
    onChange(e.target.value);
    updateLineNumbers();
    updateCursorPosition();
  }, [onChange]);

  /**
   * 处理 Tab 键
   */
  const handleKeyDown = useCallback((e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Tab') {
      e.preventDefault();
      const textarea = textareaRef.current;
      if (!textarea) return;

      const start = textarea.selectionStart;
      const end = textarea.selectionEnd;
      const value = textarea.value;

      // 插入空格
      const spaces = ' '.repeat(finalConfig.tabSize);
      const newValue = value.substring(0, start) + spaces + value.substring(end);
      
      onChange(newValue);
      
      // 恢复光标位置
      setTimeout(() => {
        textarea.selectionStart = textarea.selectionEnd = start + finalConfig.tabSize;
      }, 0);
    }
    
    // 快捷键处理
    if (e.ctrlKey || e.metaKey) {
      switch (e.key.toLowerCase()) {
        case 's':
          e.preventDefault();
          // 触发保存
          break;
        case '/':
          e.preventDefault();
          // 触发注释/取消注释
          break;
      }
    }
  }, [finalConfig.tabSize, onChange]);

  /**
   * 更新光标位置
   */
  const updateCursorPosition = useCallback(() => {
    const textarea = textareaRef.current;
    if (!textarea) return;

    const text = textarea.value;
    const position = textarea.selectionStart;
    
    // 计算行号和列号
    const lines = text.substring(0, position).split('\n');
    const line = lines.length - 1;
    const column = lines[lines.length - 1].length;
    
    setCursorPosition({ line, column });
  }, []);

  /**
   * 更新行号
   */
  const updateLineNumbers = useCallback(() => {
    const textarea = textareaRef.current;
    const lineNumbers = lineNumbersRef.current;
    if (!textarea || !lineNumbers) return;

    const lines = textarea.value.split('\n');
    const lineNumbersHtml = lines
      .map((_, i) => `<div key="${i}" class="line-number">${i + 1}</div>`)
      .join('');
    
    lineNumbers.innerHTML = lineNumbersHtml;
  }, []);

  /**
   * 处理文本选择
   */
  const handleSelect = useCallback(() => {
    const textarea = textareaRef.current;
    if (!textarea) return;

    const start = textarea.selectionStart;
    const end = textarea.selectionEnd;
    
    if (start !== end) {
      setSelection(textarea.value.substring(start, end));
    } else {
      setSelection(null);
    }
  }, []);

  /**
   * 同步滚动
   */
  const handleScroll = useCallback(() => {
    const textarea = textareaRef.current;
    const lineNumbers = lineNumbersRef.current;
    if (!textarea || !lineNumbers) return;

    lineNumbers.scrollTop = textarea.scrollTop;
  }, []);

  // 初始化
  useEffect(() => {
    updateLineNumbers();
    updateCursorPosition();
    
    const textarea = textareaRef.current;
    if (textarea) {
      textarea.addEventListener('select', handleSelect);
      textarea.addEventListener('scroll', handleScroll);
      textarea.addEventListener('click', updateCursorPosition);
      textarea.addEventListener('keyup', updateCursorPosition);
    }
    
    return () => {
      if (textarea) {
        textarea.removeEventListener('select', handleSelect);
        textarea.removeEventListener('scroll', handleScroll);
        textarea.removeEventListener('click', updateCursorPosition);
        textarea.removeEventListener('keyup', updateCursorPosition);
      }
    };
  }, [content, updateLineNumbers, updateCursorPosition, handleSelect, handleScroll]);

  const editorStyle: React.CSSProperties = {
    fontSize: `${finalConfig.fontSize}px`,
    lineHeight: finalConfig.lineHeight,
    fontFamily: finalConfig.fontFamily,
    tabSize: finalConfig.tabSize,
    maxWidth: finalConfig.maxWidth ? `${finalConfig.maxWidth}px` : undefined,
  };

  return (
    <div className={`editor-container ${className}`}>
      {finalConfig.showLineNumbers && (
        <div ref={lineNumbersRef} className="editor-line-numbers" />
      )}
      <textarea
        ref={textareaRef}
        className="editor-textarea"
        value={content}
        onChange={handleChange}
        onKeyDown={handleKeyDown}
        readOnly={readOnly}
        spellCheck={false}
        style={editorStyle}
        placeholder="Start writing..."
      />
    </div>
  );
};
```

**配套样式** (`src/styles/editor.css`):

```css
.editor-container {
  display: flex;
  position: relative;
  height: 100%;
  background-color: var(--editor-bg);
  color: var(--editor-text);
}

.editor-line-numbers {
  width: 50px;
  padding: 10px 5px;
  text-align: right;
  background-color: var(--color-bg-secondary);
  color: var(--editor-line-number);
  font-family: var(--font-editor);
  font-size: 13px;
  line-height: 1.6;
  user-select: none;
  overflow: hidden;
  border-right: 1px solid var(--color-border);
}

.line-number {
  height: 22.4px; /* 与编辑器行高一致 */
  line-height: 22.4px;
}

.line-number::before {
  content: attr(data-line);
}

.editor-textarea {
  flex: 1;
  padding: 10px;
  border: none;
  outline: none;
  resize: none;
  background-color: transparent;
  color: inherit;
  font-family: inherit;
  line-height: inherit;
  white-space: pre;
  overflow: auto;
}

.editor-textarea::selection {
  background-color: var(--editor-selection);
}
```

---

### 任务 2.2：预览组件实现 (8h)

**状态**: ❌ 未开始

**描述**: 实现 Markdown 预览组件，支持 HTML 渲染、代码高亮、数学公式等功能

**文件**: `src/components/Preview.tsx`

**功能清单**:
- Markdown 转 HTML 渲染
- 代码语法高亮
- 数学公式渲染 (KaTeX)
- GFM 支持 (表格、删除线、任务列表)
- 图片显示
- 链接处理
- 样式定制

**实现**:

```typescript
import React, { useEffect, useState, useMemo } from 'react';
import ReactMarkdown from 'react-markdown';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { vscDarkPlus, vs } from 'react-syntax-highlighter/dist/esm/styles/prism';
import remarkGfm from 'remark-gfm';
import remarkMath from 'remark-math';
import rehypeKatex from 'rehype-katex';
import type { RenderConfig } from '../types';
import 'katex/dist/katex.min.css';

interface PreviewProps {
  content: string;
  config?: Partial<RenderConfig>;
  className?: string;
  onScroll?: (scrollTop: number) => void;
}

const DEFAULT_CONFIG: RenderConfig = {
  theme: 'light',
  enableSyntaxHighlight: true,
  enableMath: true,
  enableGFM: true,
  codeTheme: 'vs',
};

export const Preview: React.FC<PreviewProps> = ({
  content,
  config = {},
  className = '',
  onScroll,
}) => {
  const [renderTime, setRenderTime] = useState<number>(0);
  const previewRef = useRef<HTMLDivElement>(null);
  
  const finalConfig = { ...DEFAULT_CONFIG, ...config };
  const isDark = finalConfig.theme === 'dark';

  /**
   * 获取代码高亮主题
   */
  const getCodeTheme = () => {
    const themeMap: Record<string, any> = {
      dark: vscDarkPlus,
      light: vs,
    };
    return themeMap[finalConfig.codeTheme] || (isDark ? vscDarkPlus : vs);
  };

  /**
   * 自定义渲染器
   */
  const components = useMemo(() => ({
    // 代码块
    code({ node, inline, className, children, ...props }: any) {
      const match = /language-(\w+)/.exec(className || '');
      const language = match ? match[1] : '';
      
      if (!inline && finalConfig.enableSyntaxHighlight) {
        return (
          <SyntaxHighlighter
            style={getCodeTheme()}
            language={language}
            PreTag="div"
            className="code-block"
            {...props}
          >
            {String(children).replace(/\n$/, '')}
          </SyntaxHighlighter>
        );
      }
      
      return (
        <code className={className} {...props}>
          {children}
        </code>
      );
    },
    
    // 图片
    img({ src, alt, ...props }: any) {
      return (
        <img
          src={src}
          alt={alt}
          loading="lazy"
          {...props}
          onClick={(e) => {
            // 点击图片可以查看大图
            window.open(src, '_blank');
          }}
          style={{ maxWidth: '100%' }}
        />
      );
    },
    
    // 链接
    a({ href, children, ...props }: any) {
      return (
        <a
          href={href}
          target="_blank"
          rel="noopener noreferrer"
          {...props}
        >
          {children}
        </a>
      );
    },
    
    // 表格
    table({ children }: any) {
      return (
        <div className="table-wrapper">
          <table>{children}</table>
        </div>
      );
    },
  }), [finalConfig.enableSyntaxHighlight, finalConfig.codeTheme, isDark]);

  /**
   * remark/rehype 插件
   */
  const remarkPlugins = useMemo(() => {
    const plugins: any[] = [];
    if (finalConfig.enableGFM) plugins.push(remarkGfm);
    if (finalConfig.enableMath) plugins.push(remarkMath);
    return plugins;
  }, [finalConfig.enableGFM, finalConfig.enableMath]);

  const rehypePlugins = useMemo(() => {
    const plugins: any[] = [];
    if (finalConfig.enableMath) plugins.push(rehypeKatex);
    return plugins;
  }, [finalConfig.enableMath]);

  /**
   * 处理滚动事件
   */
  const handleScroll = useCallback((e: React.UIEvent<HTMLDivElement>) => {
    if (onScroll) {
      onScroll(e.currentTarget.scrollTop);
    }
  }, [onScroll]);

  // 渲染计时
  useEffect(() => {
    const start = performance.now();
    
    return () => {
      const end = performance.now();
      setRenderTime(end - start);
    };
  }, [content]);

  return (
    <div
      ref={previewRef}
      className={`preview-container ${className}`}
      data-theme={finalConfig.theme}
      onScroll={handleScroll}
    >
      <div className="markdown-body">
        <ReactMarkdown
          remarkPlugins={remarkPlugins}
          rehypePlugins={rehypePlugins}
          components={components}
        >
          {content}
        </ReactMarkdown>
      </div>
      
      {/* 调试信息 */}
      {process.env.NODE_ENV === 'development' && (
        <div className="render-info">
          Render time: {renderTime.toFixed(2)}ms
        </div>
      )}
    </div>
  );
};
```

**配套样式** (`src/styles/preview.css`):

```css
.preview-container {
  height: 100%;
  overflow: auto;
  padding: 20px;
  background-color: var(--preview-bg);
  color: var(--preview-text);
}

.markdown-body {
  max-width: 900px;
  margin: 0 auto;
  font-family: var(--font-preview);
  line-height: 1.6;
  font-size: 16px;
}

/* 标题 */
.markdown-body h1,
.markdown-body h2,
.markdown-body h3,
.markdown-body h4,
.markdown-body h5,
.markdown-body h6 {
  margin-top: 24px;
  margin-bottom: 16px;
  font-weight: 600;
  line-height: 1.25;
}

.markdown-body h1 {
  font-size: 2em;
  border-bottom: 1px solid var(--color-border);
  padding-bottom: 0.3em;
}

.markdown-body h2 {
  font-size: 1.5em;
  border-bottom: 1px solid var(--color-border);
  padding-bottom: 0.3em;
}

/* 段落 */
.markdown-body p {
  margin-top: 0;
  margin-bottom: 16px;
}

/* 列表 */
.markdown-body ul,
.markdown-body ol {
  padding-left: 2em;
  margin-top: 0;
  margin-bottom: 16px;
}

.markdown-body li {
  margin-top: 0.25em;
}

/* 引用 */
.markdown-body blockquote {
  margin: 0 0 16px 0;
  padding: 0 1em;
  color: var(--preview-quote);
  border-left: 0.25em solid var(--color-border);
}

.markdown-body blockquote p {
  margin-bottom: 0;
}

/* 代码 */
.markdown-body code {
  padding: 0.2em 0.4em;
  margin: 0;
  font-size: 85%;
  background-color: var(--preview-code-bg);
  border-radius: 6px;
  font-family: var(--font-editor);
}

.markdown-body pre {
  padding: 16px;
  overflow: auto;
  font-size: 85%;
  line-height: 1.45;
  background-color: var(--preview-code-bg);
  border-radius: 6px;
  margin-bottom: 16px;
}

.markdown-body pre code {
  padding: 0;
  background-color: transparent;
}

/* 表格 */
.table-wrapper {
  overflow-x: auto;
  margin-bottom: 16px;
}

.markdown-body table {
  border-collapse: collapse;
  width: 100%;
  margin-bottom: 16px;
}

.markdown-body table th,
.markdown-body table td {
  padding: 6px 13px;
  border: 1px solid var(--color-border);
}

.markdown-body table th {
  font-weight: 600;
  background-color: var(--color-bg-secondary);
}

/* 分隔线 */
.markdown-body hr {
  height: 0.25em;
  padding: 0;
  margin: 24px 0;
  background-color: var(--color-border);
  border: 0;
}

/* 链接 */
.markdown-body a {
  color: var(--preview-link);
  text-decoration: none;
}

.markdown-body a:hover {
  text-decoration: underline;
}

/* 图片 */
.markdown-body img {
  max-width: 100%;
  box-sizing: content-box;
  background-color: var(--color-bg-primary);
  cursor: pointer;
}

/* 任务列表 */
.markdown-body .task-list-item {
  list-style-type: none;
}

.markdown-body .task-list-item input {
  margin: 0 0.2em 0.25em -1.6em;
  vertical-align: middle;
}
```

---

### 任务 2.3：分屏组件实现 (5h)

**状态**: ⚠️ 部分完成 (useCanvasRenderer.ts 存在)

**描述**: 实现可调节的分屏视图，支持编辑/预览同步滚动

**文件**: `src/components/SplitView.tsx`

**功能清单**:
- 左右分屏布局
- 可拖动分隔条
- 编辑区与预览区同步滚动
- 比例调节
- 全屏切换
- 纯编辑/纯预览模式切换

**实现**:

```typescript
import React, { useState, useRef, useCallback, useEffect } from 'react';
import { Editor } from './Editor';
import { Preview } from './Preview';
import type { EditorConfig, RenderConfig } from '../types';

interface SplitViewProps {
  content: string;
  onContentChange: (content: string) => void;
  editorConfig?: Partial<EditorConfig>;
  renderConfig?: Partial<RenderConfig>;
}

type ViewMode = 'split' | 'editor' | 'preview';

export const SplitView: React.FC<SplitViewProps> = ({
  content,
  onContentChange,
  editorConfig,
  renderConfig,
}) => {
  const [viewMode, setViewMode] = useState<ViewMode>('split');
  const [splitRatio, setSplitRatio] = useState useState(0.5);
  const [isResizing, setIsResizing] = useState(false);
  
  const containerRef = useRef<HTMLDivElement>(null);
  const editorRef = useRef<HTMLDivElement>(null);
  const previewRef = useRef<HTMLDivElement>(null);

  /**
   * 切换视图模式
   */
  const toggleViewMode = useCallback(() => {
    setViewMode((prev) => {
      switch (prev) {
        case 'split': return 'editor';
        case 'editor': return 'preview';
        case 'preview': return 'split';
      }
    });
  }, []);

  /**
   * 开始拖动分隔条
   */
  const handleResizeStart = useCallback(() => {
    setIsResizing(true);
  }, []);

  /**
   * 结束拖动
   */
  const handleResizeEnd = useCallback(() => {
    setIsResizing(false);
  }, []);

  /**
   * 拖动中
   */
  const handleResizing = useCallback((e: MouseEvent) => {
    if (!isResizing || !containerRef.current) return;

    const rect = containerRef.current.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const ratio = Math.max(0.2, Math.min(0.8, x / rect.width));
    
    setSplitRatio(ratio);
  }, [isResizing]);

  /**
   * 编辑区滚动同步到预览区
   */
  const handleEditorScroll = useCallback((scrollTop: number, scrollHeight: number, clientHeight: number) => {
    if (!previewRef.current || viewMode !== 'split') return;

    const previewElement = previewRef.current.querySelector('.preview-container') as HTMLElement;
    if (!previewElement) return;

    // 计算滚动比例
    const scrollRatio = scrollTop / (scrollHeight - clientHeight);
    const previewScrollHeight = previewElement.scrollHeight;
    const previewClientHeight = previewElement.clientHeight;
    
    // 同步预览区滚动
    previewElement.scrollTop = scrollRatio * (previewScrollHeight - previewClientHeight);
  }, [viewMode]);

  /**
   * 预览区滚动同步到编辑区
   */
  const handlePreviewScroll = useCallback((scrollTop: number) => {
    if (!editorRef.current || viewMode !== 'split') return;

    const editorElement = editorRef.current.querySelector('.editor-textarea') as HTMLTextAreaElement;
    if (!editorElement) return;

    // 计算滚动比例
    const previewElement = previewRef.current?.querySelector('.preview-container') as HTMLElement;
    if (!previewElement) return;
    
    const scrollRatio = scrollTop / (previewElement.scrollHeight - previewElement.clientHeight);
    const editorScrollHeight = editorElement.scrollHeight;
    const editorClientHeight = editorElement.clientHeight;
    
    // 同步编辑区滚动
    editorElement.scrollTop = scrollRatio * (editorScrollHeight - editorClientHeight);
  }, [viewMode]);

  // 注册拖动事件
  useEffect(() => {
    if (isResizing) {
      document.addEventListener('mousemove', handleResizing);
      document.addEventListener('mouseup', handleResizeEnd);
      
      return () => {
        document.removeEventListener('mousemove', handleResizing);
        document.removeEventListener('mouseup', handleResizeEnd);
      };
    }
  }, [isResizing, handleResizing, handleResizeEnd]);

  const containerStyle: React.CSSProperties = {
    display: 'flex',
    height: '100%',
    position: 'relative',
  };

  const editorStyle: React.CSSProperties = {
    flex: viewMode === 'split' ? splitRatio : 1,
    display: viewMode === 'preview' ? 'none' : 'block',
    overflow: 'hidden',
  };

  const previewStyle: React.CSSProperties = {
    flex: viewMode === 'split' ? 1 - splitRatio : 1,
    display: viewMode === 'editor' ? 'none' : 'block',
    overflow: 'hidden',
  };

  const resizerStyle: React.CSSProperties = {
    width: '4px',
    cursor: 'col-resize',
    backgroundColor: 'var(--color-border)',
    display: viewMode === 'split' ? 'block' : 'none',
    userSelect: 'none',
  };

  return (
    <div className="split-view" style={containerStyle} ref={containerRef}>
      {/* 编辑区 */}
      <div className="split-editor" style={editorStyle} ref={editorRef}>
        <Editor
          content={content}
          onChange={onContentChange}
          config={editorConfig}
        />
      </div>
      
      {/* 分隔条 */}
      <div
        className="split-resizer"
        style={resizerStyle}
        onMouseDown={handleResizeStart}
      />
      
      {/* 预览区 */}
      <div className="split-preview" style={previewStyle} ref={previewRef}>
        <Preview
          content={content}
          config={renderConfig}
          onScroll={handlePreviewScroll}
        />
      </div>
      
      {/* 视图模式切换按钮 */}
      <button
        className="view-mode-toggle"
        onClick={toggleViewMode}
        title={`Current: ${viewMode} (Click to switch)`}
      >
        {viewMode === 'split' && '⇄'}
        {viewMode === 'editor' && '📝'}
        {viewMode === 'preview' && '👁️'}
      </button>
    </div>
  );
};
```

---

### 任务 2.4：Canvas 渲染实现 (4h)

**状态**: ⚠️ 部分完成 (useCanvasRenderer.ts 存在)

**描述**: 实现 Canvas 高性能渲染引擎，用于替代 DOM 渲染

**文件**: `src/hooks/useCanvasRenderer.ts`

**功能清单**:
- Canvas 初始化
- 高 DPI 支持
- 绘制指令处理
- 文本渲染
- 局部刷新
- 滚动优化

**实现**:

```typescript
import { useEffect, useRef, useCallback, useState } from 'react';

interface DrawCommand {
  type: 'text' | 'line' | 'rect' | 'image';
  x: number;
  y: number;
  width?: number;
  height?: number;
  text?: string;
  color?: string;
  fontSize?: number;
}

interface CanvasRendererConfig {
  width: number;
  height: number;
  devicePixelRatio: number;
  backgroundColor: string;
  textColor: string;
  fontSize: number;
  fontFamily: string;
  lineHeight: number;
}

interface UseCanvasRendererOptions {
  canvasRef: React.RefObject<HTMLCanvasElement>;
  config: Partial<CanvasRendererConfig>;
}

/**
 * Canvas 渲染 Hook
 */
export function useCanvasRenderer({ canvasRef, config: userConfig }: UseCanvasRendererOptions) {
  const [isReady, setIsReady] = useState(false);
  const ctxRef = useRef<CanvasRenderingContext2D | null>(null);
  const drawQueueRef = useRef<DrawCommand[]>([]);
  const renderRequestedRef = useRef(false);
  
  const config = useRef<CanvasRendererConfig>({
    width: 800,
    height: 600,
    devicePixelRatio: window.devicePixelRatio || 1,
    backgroundColor: '#ffffff',
    textColor: '#000000',
    fontSize: 14,
    fontFamily: 'Menlo, Monaco, monospace',
    lineHeight: 1.6,
    ...userConfig,
  });

  /**
   * 初始化 Canvas
   */
  const initCanvas = useCallback(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const { width, height, devicePixelRatio } = config.current;

    // 设置实际像素尺寸
    canvas.width = width * devicePixelRatio;
    canvas.height = height * devicePixelRatio;

    // 设置 CSS 尺寸
    canvas.style.width = `${width}px`;
    canvas.style.height = `${height}px`;

    // 缩放上下文
    ctx.scale(devicePixelRatio, devicePixelRatio);

    ctxRef.current = ctx;
    setIsReady(true);
  }, [canvasRef]);

  /**
   * 清空 Canvas
   */
  const clearCanvas = useCallback(() => {
    const ctx = ctxRef.current;
    if (!ctx) return;

    const { width, height, backgroundColor } = config.current;
    ctx.fillStyle = backgroundColor;
    ctx.fillRect(0, 0, width, height);
  }, []);

  /**
   * 绘制文本
   */
  const drawText = useCallback((
    text: string,
    x: number,
    y: number,
    options?: {
      color?: string;
      fontSize?: number;
      fontFamily?: string;
    }
  ) => {
    const ctx = ctxRef.current;
    if (!ctx) return;

    const { fontSize, fontFamily, textColor } = config.current;
    
    ctx.font = `${options?.fontSize || fontSize}px ${options?.fontFamily || fontFamily}`;
    ctx.fillStyle = options?.color || textColor;
    ctx.fillText(text, x, y);
  }, []);

  /**
   * 绘制线条
   */
  const drawLine = useCallback((
    x1: number,
    y1: number,
    x2: number,
    y2: number,
    options?: {
      color?: string;
      width?: number;
    }
  ) => {
    const ctx = ctxRef.current;
    if (!ctx) return;

    ctx.beginPath();
    ctx.moveTo(x1, y1);
    ctx.lineTo(x2, y2);
    ctx.strokeStyle = options?.color || config.current.textColor;
    ctx.lineWidth = options?.width || 1;
    ctx.stroke();
  }, []);

  /**
   * 绘制矩形
   */
  const drawRect = useCallback((
    x: number,
    y: number,
    width: number,
    height: number,
    options?: {
      fill?: string;
      stroke?: string;
    }
  ) => {
    const ctx = ctxRef.current;
    if (!ctx) return;

    if (options?.fill) {
      ctx.fillStyle = options.fill;
      ctx.fillRect(x, y, width, height);
    }
    
    if (options?.stroke) {
      ctx.strokeStyle = options.stroke;
      ctx.strokeRect(x, y, width, height);
    }
  }, []);

  /**
   * 执行绘制队列
   */
  const processDrawQueue = useCallback(() => {
    if (!isReady) return;
    
    clearCanvas();
    
    const commands = drawQueueRef.current;
    commands.forEach((cmd) => {
      switch (cmd.type) {
        case 'text':
          if (cmd.text) {
            drawText(cmd.text, cmd.x, cmd.y, {
              color: cmd.color,
              fontSize: cmd.fontSize,
            });
          }
          break;
        case 'line':
          drawLine(cmd.x, cmd.y, cmd.x + (cmd.width || 0), cmd.y + (cmd.height || 0), {
            color: cmd.color,
          });
          break;
        case 'rect':
          drawRect(cmd.x, cmd.y, cmd.width || 0, cmd.height || 0, {
            fill: cmd.color,
          });
          break;
      }
    });
    
    drawQueueRef.current = [];
    renderRequestedRef.current = false;
  }, [isReady, clearCanvas, drawText, drawLine, drawRect]);

  /**
   * 请求渲染
   */
  const requestRender = useCallback((commands: DrawCommand[]) => {
    drawQueueRef.current.push(...commands);
    
    if (!renderRequestedRef.current) {
      renderRequestedRef.current = true;
      requestAnimationFrame(processDrawQueue);
    }
  }, [processDrawQueue]);

  /**
   * 渲染 Markdown 文档
   */
  const renderDocument = useCallback((content: string) => {
    const commands: DrawCommand[] = [];
    const lines = content.split('\n');
    
    let y = 20;
    const { fontSize, lineHeight } = config.current;
    const lineHeightPx = fontSize * lineHeight;
    
    lines.forEach((line, index) => {
      // 行号
      commands.push({
        type: 'text',
        x: 10,
        y: y + fontSize,
        text: `${index + 1}`,
        color: '#8b949e',
        fontSize: 12,
      });
      
      // 内容
      commands.push({
        type: 'text',
        x: 50,
        y: y + fontSize,
        text: line || ' ',
      });
      
      y += lineHeightPx;
    });
    
    requestRender(commands);
  }, [requestRender]);

  // 初始化
  useEffect(() => {
    initCanvas();
  }, [initCanvas]);

  return {
    isReady,
    renderDocument,
    requestRender,
    clearCanvas,
    drawText,
    drawLine,
    drawRect,
  };
}
```

---

### 任务 2.5：文件操作实现 (3h)

**状态**: ❌ 未开始

**描述**: 实现完整的文件操作功能，包括打开、保存、另存为、最近文件等

**文件**: `src/hooks/useFileOperations.ts`

**功能清单**:
- 打开文件对话框
- 保存文件对话框
- 另存为功能
- 最近文件列表管理
- 文件类型过滤
- 文件编码检测

**实现**:

```typescript
import { useCallback, useState } from 'react';
import { invoke } from '@tauri-apps/api/tauri';
import { open, save } from '@tauri-apps/api/dialog';
import { writeTextFile, readTextFile, exists } from '@tauri-apps/api/fs';
import { join } from '@tauri-apps/api/path';

interface FileOperationResult {
  path: string;
  content: string;
}

interface RecentFile {
  path: string;
  lastOpened: number;
}

const RECENT_FILES_KEY = 'nomadmark_recent_files';
const MAX_RECENT_FILES = 10;

/**
 * 文件操作 Hook
 */
export function useFileOperations() {
  const [recentFiles, setRecentFiles] = useState<RecentFile[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  /**
   * 加载最近文件列表
   */
  const loadRecentFiles = useCallback(async () => {
    try {
      const stored = localStorage.getItem(RECENT_FILES_KEY);
      if (stored) {
        setRecentFiles(JSON.parse(stored));
      }
    } catch (err) {
      console.error('Failed to load recent files:', err);
    }
  }, []);

  /**
   * 保存最近文件列表
   */
  const saveRecentFiles = useCallback((files: RecentFile[]) => {
    localStorage.setItem(RECENT_FILES_KEY, JSON.stringify(files));
    setRecentFiles(files);
  }, []);

  /**
   * 添加到最近文件
   */
  const addToRecentFiles = useCallback((path: string) => {
    setRecentFiles((prev) => {
      const newFiles = prev.filter((f) => f.path !== path);
      newFiles.unshift({
        path,
        lastOpened: Date.now(),
      });
      
      // 限制数量
      const trimmed = newFiles.slice(0, MAX_RECENT_FILES);
      saveRecentFiles(trimmed);
      
      return trimmed;
    });
  }, [saveRecentFiles]);

  /**
   * 打开文件对话框
   */
  const openFile = useCallback(async (): Promise<FileOperationResult | null> => {
    setIsLoading(true);
    setError(null);
    
    try {
      const selected = await open({
        multiple: false,
        filters: [{
          name: 'Markdown',
          extensions: ['md', 'markdown', 'mdown', 'mkd', 'mkdn', 'mdtxt', 'mdtext', 'txt']
        }]
      });

      if (!selected || Array.isArray(selected)) {
        return null;
      }

      const content = await readTextFile(selected);
      
      addToRecentFiles(selected);
      
      return {
        path: selected,
        content,
      };
    } catch (err) {
      const errorMessage = err as string;
      setError(errorMessage);
      console.error('Failed to open file:', err);
      return null;
    } finally {
      setIsLoading(false);
    }
  }, [addToRecentFiles]);

  /**
   * 保存文件对话框
   */
  const saveFile = useCallback(async (
    content: string,
    defaultPath?: string
  ): Promise<string | null> => {
    setIsLoading(true);
    setError(null);
    
    try {
      const filePath = await save({
        defaultPath,
        filters: [{
          name: 'Markdown',
          extensions: ['md']
        }]
      });

      if (!filePath) {
        return null;
      }

      await writeTextFile(filePath, content);
      
      addToRecentFiles(filePath);
      
      return filePath;
    } catch (err) {
      const errorMessage = err as string;
      setError(errorMessage);
      console.error('Failed to save file:', err);
      return null;
    } finally {
      setIsLoading(false);
    }
  }, [addToRecentFiles]);

  /**
   * 直接保存文件 (已知路径)
   */
  const saveFileDirect = useCallback(async (
    path: string,
    content: string
  ): Promise<void> => {
    setIsLoading(true);
    setError(null);
    
    try {
      await writeTextFile(path, content);
      addToRecentFiles(path);
    } catch (err) {
      const errorMessage = err as string;
      setError(errorMessage);
      throw err;
    } finally {
      setIsLoading(false);
    }
  }, [addToRecentFiles]);

  /**
   * 清除最近文件
   */
  const clearRecentFiles = useCallback(() => {
    localStorage.removeItem(RECENT_FILES_KEY);
    setRecentFiles([]);
  }, []);

  /**
   * 从最近文件移除
   */
  const removeFromRecent = useCallback((path: string) => {
    setRecentFiles((prev) => {
      const newFiles = prev.filter((f) => f.path !== path);
      saveRecentFiles(newFiles);
      return newFiles;
    });
  }, [saveRecentFiles]);

  // 初始化时加载最近文件
  useEffect(() => {
    loadRecentFiles();
  }, [loadRecentFiles]);

  return {
    recentFiles,
    isLoading,
    error,
    openFile,
    saveFile,
    saveFileDirect,
    clearRecentFiles,
    removeFromRecent,
  };
}
```

---

**阶段二完成标准**:
- [ ] 编辑器可正常输入和编辑文本
- [ ] 行号正确显示
- [ ] Tab 键正确插入空格
- [ ] 预览可正确渲染 Markdown
- [ ] 代码块有语法高亮
- [ ] 分屏模式正常工作
- [ ] 分隔条可拖动调节
- [ ] 滚动同步正常
- [ ] 可打开文件
- [ ] 可保存文件
- [ ] 另存为功能正常
- [ ] 最近文件列表正确更新

---

## 阶段三：UI/UX 完善

> **目标**: 完善用户界面和交互体验
> **预计工时**: 20 小时
> **依赖**: 阶段二完成

### 阶段概述

本阶段完善用户界面和交互体验，包括工具栏、快捷键系统、主题系统、设置面板、状态栏和侧边栏等 UI 组件。

---

### 任务 3.1：工具栏组件实现 (5h)

**状态**: ❌ 未开始

**描述**: 实现功能完整的工具栏，包含文件操作、编辑操作、格式操作和视图操作

**文件**: `src/components/Toolbar.tsx`

**功能清单**:
- 文件操作组：新建、打开、保存、另存为
- 编辑操作组：撤销、重做、剪切、复制、粘贴
- 格式操作组：粗体、斜体、删除线、标题、链接、图片、代码、引用、列表、表格
- 视图操作组：分屏切换、全屏切换、主题切换

**实现**:

```typescript
import React, { useState } from 'react';
import { useHotkeys } from '../hooks/useKeyboard';

interface ToolbarProps {
  onNew: () => void;
  onOpen: () => void;
  onSave: () => void;
  onSaveAs?: () => void;
  onUndo: () => void;
  onRedo: () => void;
  onCut?: () => void;
  onCopy?: () => void;
  onPaste?: () => void;
  canSave?: boolean;
  canUndo?: boolean;
  canRedo?: boolean;
  onFormat?: (format: string) => void;
  onToggleView?: () => void;
  onToggleTheme?: () => void;
  currentTheme?: string;
}

export const Toolbar: React.FC<ToolbarProps> = ({
  onNew,
  onOpen,
  onSave,
  onSaveAs,
  onUndo,
  onRedo,
  onCut,
  onCopy,
  onPaste,
  canSave = true,
  canUndo = true,
  canRedo = false,
  onFormat,
  onToggleView,
  onToggleTheme,
  currentTheme = 'light',
}) => {
  const [isFormatMenuOpen, setIsFormatMenuOpen] = useState(false);

  /**
   * 格式化文本
   */
  const handleFormat = (format: string) => {
    if (onFormat) {
      onFormat(format);
    }
    setIsFormatMenuOpen(false);
  };

  return (
    <div className="toolbar">
      {/* 文件操作 */}
      <div className="toolbar-group" aria-label="File operations">
        <ToolbarButton
          icon="📄"
          tooltip="New (Ctrl+N)"
          onClick={onNew}
          shortcut="Ctrl+N"
        />
        <ToolbarButton
          icon="📂"
          tooltip="Open (Ctrl+O)"
          onClick={onOpen}
          shortcut="Ctrl+O"
        />
        <ToolbarButton
          icon="💾"
          tooltip="Save (Ctrl+S)"
          onClick={onSave}
          disabled={!canSave}
          shortcut="Ctrl+S"
        />
      </div>

      <div className="toolbar-divider" />

      {/* 编辑操作 */}
      <div className="toolbar-group" aria-label="Edit operations">
        <ToolbarButton
          icon="↶"
          tooltip="Undo (Ctrl+Z)"
          onClick={onUndo}
          disabled={!canUndo}
          shortcut="Ctrl+Z"
        />
        <ToolbarButton
          icon="↷"
          tooltip="Redo (Ctrl+Y)"
          onClick={onRedo}
          disabled={!canRedo}
          shortcut="Ctrl+Y"
        />
      </div>

      <div className="toolbar-divider" />

      {/* 格式操作 */}
      <div className="toolbar-group" aria-label="Format operations">
        <ToolbarButton
          icon={<b>B</b>}
          tooltip="Bold (Ctrl+B)"
          onClick={() => handleFormat('bold')}
          shortcut="Ctrl+B"
        />
        <ToolbarButton
          icon={<i>I</i>}
          tooltip="Italic (Ctrl+I)"
          onClick={() => handleFormat('italic')}
          shortcut="Ctrl+I"
        />
        <ToolbarButton
          icon={<s>S</s>}
          tooltip="Strikethrough"
          onClick={() => handleFormat('strikethrough')}
        />
        <ToolbarButton
          icon="H"
          tooltip="Heading (Ctrl+H)"
          onClick={() => handleFormat('heading')}
        />
        <ToolbarDropdown
          icon="···"
          tooltip="More formats"
          isOpen={isFormatMenuOpen}
          onToggle={() => setIsFormatMenuOpen(!isFormatMenuOpen)}
        >
          <ToolbarMenuItem onClick={() => handleFormat('link')}>
            🔗 Link
          </ToolbarMenuItem>
          <ToolbarMenuItem onClick={() => handleFormat('image')}>
            🖼️ Image
          </ToolbarMenuItem>
          <ToolbarMenuItem onClick={() => handleFormat('code')}>
            &lt;/&gt; Code
          </ToolbarMenuItem>
          <ToolbarMenuItem onClick={() => handleFormat('quote')}>
            💬 Quote
          </ToolbarMenuItem>
          <ToolbarMenuItem onClick={() => handleFormat('bulletList')}>
            • Bullet List
          </ToolbarMenuItem>
          <ToolbarMenuItem onClick={() => handleFormat('numberedList')}>
            1. Numbered List
          </ToolbarMenuItem>
          <ToolbarMenuItem onClick={() => handleFormat('taskList')}>
            ☑ Task List
          </ToolbarMenuItem>
          <ToolbarMenuItem onClick={() => handleFormat('table')}>
            ▦ Table
          </ToolbarMenuItem>
        </ToolbarDropdown>
      </div>

      <div className="toolbar-divider" />

      {/* 视图操作 */}
      <div className="toolbar-group" aria-label="View operations">
        <ToolbarButton
          icon="⇄"
          tooltip="Toggle view (Ctrl+\\)"
          onClick={onToggleView}
          shortcut="Ctrl+\\"
        />
        <ToolbarButton
          icon="🎨"
          tooltip="Change theme"
          onClick={onToggleTheme}
        />
        <ToolbarButton
          icon="⛶"
          tooltip="Fullscreen (F11)"
          onClick={() => {
            if (document.fullscreenElement) {
              document.exitFullscreen();
            } else {
              document.documentElement.requestFullscreen();
            }
          }}
          shortcut="F11"
        />
      </div>
    </div>
  );
};

/**
 * 工具栏按钮组件
 */
interface ToolbarButtonProps {
  icon: React.ReactNode;
  tooltip: string;
  onClick: () => void;
  disabled?: boolean;
  shortcut?: string;
  className?: string;
}

const ToolbarButton: React.FC<ToolbarButtonProps> = ({
  icon,
  tooltip,
  onClick,
  disabled = false,
  shortcut,
  className = '',
}) => {
  return (
    <button
      className={`toolbar-button ${className}`}
      onClick={onClick}
      disabled={disabled}
      title={shortcut ? `${tooltip} (${shortcut})` : tooltip}
      aria-label={tooltip}
    >
      {typeof icon === 'string' ? <span className="toolbar-icon">{icon}</span> : icon}
      {shortcut && <span className="toolbar-shortcut">{shortcut}</span>}
    </button>
  );
};

/**
 * 工具栏下拉菜单组件
 */
interface ToolbarDropdownProps {
  icon: React.ReactNode;
  tooltip: string;
  isOpen: boolean;
  onToggle: () => void;
  children: React.ReactNode;
}

const ToolbarDropdown: React.FC<ToolbarDropdownProps> = ({
  icon,
  tooltip,
  isOpen,
  onToggle,
  children,
}) => {
  return (
    <div className="toolbar-dropdown">
      <button
        className="toolbar-button"
        onClick={onToggle}
        title={tooltip}
        aria-expanded={isOpen}
      >
        {icon}
        <span className="dropdown-arrow">▼</span>
      </button>
      {isOpen && (
        <div className="toolbar-dropdown-menu">
          {children}
        </div>
      )}
    </div>
  );
};

/**
 * 工具栏菜单项组件
 */
interface ToolbarMenuItemProps {
  onClick: () => void;
  children: React.ReactNode;
  shortcut?: string;
}

const ToolbarMenuItem: React.FC<ToolbarMenuItemProps> = ({
  onClick,
  children,
  shortcut,
}) => {
  return (
    <button
      className="toolbar-menu-item"
      onClick={onClick}
    >
      <span className="menu-item-content">{children}</span>
      {shortcut && <span className="menu-item-shortcut">{shortcut}</span>}
    </button>
  );
};
```

**配套样式** (`src/styles/toolbar.css`):

```css
.toolbar {
  display: flex;
  align-items: center;
  height: var(--toolbar-height);
  padding: 0 12px;
  background-color: var(--color-bg-secondary);
  border-bottom: 1px solid var(--color-border);
  gap: 4px;
}

.toolbar-group {
  display: flex;
  align-items: center;
  gap: 2px;
}

.toolbar-divider {
  width: 1px;
  height: 24px;
  margin: 0 8px;
  background-color: var(--color-border);
}

.toolbar-button {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 36px;
  height: 32px;
  padding: 0 8px;
  border-radius: var(--radius-sm);
  color: var(--color-text-primary);
  background-color: transparent;
  transition: background-color var(--transition-fast);
}

.toolbar-button:hover:not(:disabled) {
  background-color: var(--color-bg-tertiary);
}

.toolbar-button:active:not(:disabled) {
  background-color: var(--color-border);
}

.toolbar-button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.toolbar-icon {
  font-size: 16px;
}

.toolbar-shortcut {
  margin-left: 4px;
  font-size: 11px;
  color: var(--color-text-tertiary);
}

.toolbar-dropdown {
  position: relative;
}

.toolbar-dropdown .dropdown-arrow {
  margin-left: 4px;
  font-size: 10px;
  color: var(--color-text-tertiary);
}

.toolbar-dropdown-menu {
  position: absolute;
  top: 100%;
  left: 0;
  margin-top: 4px;
  min-width: 200px;
  padding: 4px 0;
  background-color: var(--color-bg-primary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-lg);
  z-index: 1000;
}

.toolbar-menu-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 16px;
  color: var(--color-text-primary);
  background-color: transparent;
  text-align: left;
  transition: background-color var(--transition-fast);
}

.toolbar-menu-item:hover {
  background-color: var(--color-bg-secondary);
}

.menu-item-shortcut {
  font-size: 11px;
  color: var(--color-text-tertiary);
}
```

---

### 任务 3.2：快捷键系统实现 (4h)

**状态**: ❌ 未开始

**描述**: 实现全局快捷键系统，支持所有编辑器操作的快捷键

**文件**: `src/hooks/useKeyboard.ts`

**快捷键列表**:

| 快捷键 | 功能 | 类别 |
|--------|------|------|
| Ctrl+N | 新建文件 | 文件 |
| Ctrl+O | 打开文件 | 文件 |
| Ctrl+S | 保存文件 | 文件 |
| Ctrl+Shift+S | 另存为 | 文件 |
| Ctrl+Z | 撤销 | 编辑 |
| Ctrl+Y / Ctrl+Shift+Z | 重做 | 编辑 |
| Ctrl+X | 剪切 | 编辑 |
| Ctrl+C | 复制 | 编辑 |
| Ctrl+V | 粘贴 | 编辑 |
| Ctrl+A | 全选 | 编辑 |
| Ctrl+F | 查找 | 编辑 |
| Ctrl+H | 替换 | 编辑 |
| Ctrl+B | 粗体 | 格式 |
| Ctrl+I | 斜体 | 格式 |
| Ctrl+U | 下划线 | 格式 |
| Ctrl+K | 插入链接 | 格式 |
| Ctrl+Shift+K | 插入图片 | 格式 |
| Ctrl+Shift+X | 删除线 | 格式 |
| Ctrl+` | 代码块 | 格式 |
| Ctrl+Shift+` | 行内代码 | 格式 |
| Ctrl+L | 标题 | 格式 |
| Ctrl+Shift+; | 注释/取消注释 | 格式 |
| Ctrl+\ | 切换分屏 | 视图 |
| F11 | 全屏 | 视图 |
| Ctrl++ | 放大 | 视图 |
| Ctrl+- | 缩小 | 视图 |
| Ctrl+0 | 重置缩放 | 视图 |
| Escape | 关闭面板/退出全屏 | 系统 |
| Ctrl+, | 打开设置 | 系统 |

**实现**:

```typescript
import { useEffect, useCallback } from 'react';

type KeyboardHandler = (e: KeyboardEvent) => void;

interface KeyboardShortcut {
  key: string;
  ctrlKey?: boolean;
  shiftKey?: boolean;
  altKey?: boolean;
  metaKey?: boolean;
  handler: KeyboardHandler;
  description: string;
}

/**
 * 快捷键 Hook
 */
export function useKeyboard(shortcuts: KeyboardShortcut[]) {
  /**
   * 检查快捷键是否匹配
   */
  const matchesShortcut = useCallback((event: KeyboardEvent, shortcut: KeyboardShortcut): boolean => {
    const keyMap: Record<string, string> = {
      ' ': 'Space',
      'ctrl': 'Control',
      'esc': 'Escape',
      'plus': '+',
      'minus': '-',
    };
    
    const normalizedKey = keyMap[event.key.toLowerCase()] || event.key;
    const normalizedShortcutKey = keyMap[shortcut.key.toLowerCase()] || shortcut.key;
    
    return (
      normalizedKey === normalizedShortcutKey &&
      !!shortcut.ctrlKey === event.ctrlKey &&
      !!shortcut.shiftKey === event.shiftKey &&
      !!shortcut.altKey === event.altKey &&
      !!shortcut.metaKey === event.metaKey
    );
  }, []);

  /**
   * 处理键盘事件
   */
  const handleKeyDown = useCallback((event: KeyboardEvent) => {
    // 忽略在输入框中的事件
    const target = event.target as HTMLElement;
    if (
      target.tagName === 'INPUT' ||
      target.tagName === 'TEXTAREA' ||
      target.isContentEditable
    ) {
      return;
    }

    // 查找匹配的快捷键
    for (const shortcut of shortcuts) {
      if (matchesShortcut(event, shortcut)) {
        event.preventDefault();
        event.stopPropagation();
        shortcut.handler(event);
        return;
      }
    }
  }, [shortcuts, matchesShortcut]);

  useEffect(() => {
    document.addEventListener('keydown', handleKeyDown);
    
    return () => {
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [handleKeyDown]);
}

/**
 * 预定义的编辑器快捷键
 */
export function useEditorShortcuts(handlers: {
  onNew?: () => void;
  onOpen?: () => void;
  onSave?: () => void;
  onSaveAs?: () => void;
  onUndo?: () => void;
  onRedo?: () => void;
  onCut?: () => void;
  onCopy?: () => void;
  onPaste?: () => void;
  onFind?: () => void;
  onReplace?: () => void;
  onBold?: () => void;
  onItalic?: () => void;
  onUnderline?: () => void;
  onStrikethrough?: () => void;
  onHeading?: () => void;
  onLink?: () => void;
  onImage?: () => void;
  onCode?: () => void;
  onInlineCode?: () => void;
  onQuote?: () => void;
  onBulletList?: () => void;
  onNumberedList?: () => void;
  onTaskList?: () => void;
  onTable?: () => void;
  onToggleView?: () => void;
  onToggleTheme?: () => void;
  onZoomIn?: () => void;
  onZoomOut?: () => void;
  onResetZoom?: () => void;
  onFullscreen?: () => void;
  onSettings?: () => void;
}) {
  const shortcuts: KeyboardShortcut[] = [
    // 文件操作
    { key: 'n', ctrlKey: true, handler: handlers.onNew || (() => {}), description: 'New' },
    { key: 'o', ctrlKey: true, handler: handlers.onOpen || (() => {}), description: 'Open' },
    { key: 's', ctrlKey: true, handler: handlers.onSave || (() => {}), description: 'Save' },
    { key: 's', ctrlKey: true, shiftKey: true, handler: handlers.onSaveAs || (() => {}), description: 'Save As' },
    
    // 编辑操作
    { key: 'z', ctrlKey: true, handler: handlers.onUndo || (() => {}), description: 'Undo' },
    { key: 'y', ctrlKey: true, handler: handlers.onRedo || (() => {}), description: 'Redo' },
    { key: 'z', ctrlKey: true, shiftKey: true, handler: handlers.onRedo || (() => {}), description: 'Redo (Alt)' },
    { key: 'x', ctrlKey: true, handler: handlers.onCut || (() => {}), description: 'Cut' },
    { key: 'c', ctrlKey: true, handler: handlers.onCopy || (() => {}), description: 'Copy' },
    { key: 'v', ctrlKey: true, handler: handlers.onPaste || (() => {}), description: 'Paste' },
    { key: 'f', ctrlKey: true, handler: handlers.onFind || (() => {}), description: 'Find' },
    { key: 'h', ctrlKey: true, handler: handlers.onReplace || (() => {}), description: 'Replace' },
    
    // 格式操作
    { key: 'b', ctrlKey: true, handler: handlers.onBold || (() => {}), description: 'Bold' },
    { key: 'i', ctrlKey: true, handler: handlers.onItalic || (() => {}), description: 'Italic' },
    { key: 'u', ctrlKey: true, handler: handlers.onUnderline || (() => {}), description: 'Underline' },
    { key: 'x', ctrlKey: true, shiftKey: true, handler: handlers.onStrikethrough || (() => {}), description: 'Strikethrough' },
    { key: 'l', ctrlKey: true, handler: handlers.onHeading || (() => {}), description: 'Heading' },
    { key: 'k', ctrlKey: true, handler: handlers.onLink || (() => {}), description: 'Link' },
    { key: 'k', ctrlKey: true, shiftKey: true, handler: handlers.onImage || (() => {}), description: 'Image' },
    { key: '`', ctrlKey: true, shiftKey: true, handler: handlers.onInlineCode || (() => {}), description: 'Inline Code' },
    { key: '`', ctrlKey: true, handler: handlers.onCode || (() => {}), description: 'Code Block' },
    
    // 视图操作
    { key: '\\', ctrlKey: true, handler: handlers.onToggleView || (() => {}), description: 'Toggle View' },
    { key: 'F11', handler: handlers.onFullscreen || (() => {}), description: 'Fullscreen' },
    { key: '=', ctrlKey: true, handler: handlers.onZoomIn || (() => {}), description: 'Zoom In' },
    { key: '-', ctrlKey: true, handler: handlers.onZoomOut || (() => {}), description: 'Zoom Out' },
    { key: '0', ctrlKey: true, handler: handlers.onResetZoom || (() => {}), description: 'Reset Zoom' },
    
    // 系统操作
    { key: 'Escape', handler: handlers.onToggleTheme || (() => {}), description: 'Close/Exit' },
    { key: ',', ctrlKey: true, handler: handlers.onSettings || (() => {}), description: 'Settings' },
  ];

  useKeyboard(shortcuts);
}
```

---

### 任务 3.3：主题系统实现 (4h)

**状态**: ❌ 未开始

**描述**: 实现完整的主题系统，支持多主题切换和自定义

**文件**: `src/hooks/useTheme.ts`, `src/styles/themes.css`

**主题列表**:
- Light Theme (默认)
- Dark Theme
- E-ink Theme (适合墨水屏显示器)
- Sepia Theme (护眼)

**实现**:

```typescript
import { useState, useEffect, useCallback } from 'react';

type Theme = 'light' | 'dark' | 'eink' | 'sepia' | 'auto';

const THEME_KEY = 'nomadmark_theme';
const DEFAULT_THEME: Theme = 'light';

/**
 * 主题 Hook
 */
export function useTheme() {
  const [theme, setThemeState] = useState<Theme>(() => {
    // 从 localStorage 读取
    const stored = localStorage.getItem(THEME_KEY);
    if (stored && isValidTheme(stored)) {
      return stored as Theme;
    }
    
    // 检测系统偏好
    if (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) {
      return 'dark';
    }
    
    return DEFAULT_THEME;
  });

  /**
   * 获取实际应用的主题
   */
  const getAppliedTheme = useCallback((): Exclude<Theme, 'auto'> => {
    if (theme === 'auto') {
      return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
    }
    return theme;
  }, [theme]);

  /**
   * 设置主题
   */
  const setTheme = useCallback((newTheme: Theme) => {
    setThemeState(newTheme);
    localStorage.setItem(THEME_KEY, newTheme);
  }, []);

  /**
   * 切换主题
   */
  const toggleTheme = useCallback(() => {
    const currentTheme = getAppliedTheme();
    const themeOrder: Exclude<Theme, 'auto'>[] = ['light', 'dark', 'eink', 'sepia'];
    const currentIndex = themeOrder.indexOf(currentTheme);
    const nextTheme = themeOrder[(currentIndex + 1) % themeOrder.length];
    setTheme(nextTheme);
  }, [getAppliedTheme, setTheme]);

  // 应用主题到 DOM
  useEffect(() => {
    const appliedTheme = getAppliedTheme();
    document.documentElement.setAttribute('data-theme', appliedTheme);
    
    // 更新 meta theme-color
    const themeColorMap: Record<Exclude<Theme, 'auto'>, string> = {
      light: '#ffffff',
      dark: '#0d1117',
      eink: '#ffffff',
      sepia: '#f4ecd8',
    };
    
    let metaThemeColor = document.querySelector('meta[name="theme-color"]');
    if (!metaThemeColor) {
      metaThemeColor = document.createElement('meta');
      metaThemeColor.setAttribute('name', 'theme-color');
      document.head.appendChild(metaThemeColor);
    }
    metaThemeColor.setAttribute('content', themeColorMap[appliedTheme]);
  }, [getAppliedTheme]);

  // 监听系统主题变化
  useEffect(() => {
    if (theme !== 'auto') return;
    
    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
    const handleChange = () => {
      const appliedTheme = getAppliedTheme();
      document.documentElement.setAttribute('data-theme', appliedTheme);
    };
    
    mediaQuery.addEventListener('change', handleChange);
    return () => mediaQuery.removeEventListener('change', handleChange);
  }, [theme, getAppliedTheme]);

  return {
    theme,
    appliedTheme: getAppliedTheme(),
    setTheme,
    toggleTheme,
  };
}

/**
 * 验证主题值是否有效
 */
function isValidTheme(value: string): boolean {
  return ['light', 'dark', 'eink', 'sepia', 'auto'].includes(value);
}
```

**增强的主题样式** (在现有 themes.css 基础上添加):

```css
/* 主题切换动画 */
* {
  transition: background-color var(--transition-normal),
              color var(--transition-normal),
              border-color var(--transition-normal);
}

/* 主题特定组件样式 */

/* 代码块在暗色主题下的特殊处理 */
[data-theme="dark"] .code-block,
[data-theme="eink"] .code-block {
  background-color: #161b22 !important;
}

/* Sepia 主题的链接样式 */
[data-theme="sepia"] a {
  color: #8b5a3c;
  text-decoration: underline;
}

/* E-ink 主题的高对比度 */
[data-theme="eink"] button:hover {
  background-color: #e0e0e0;
}

[data-theme="eink"] .toolbar-button:active {
  background-color: #cccccc;
}
```

---

### 任务 3.4：设置面板实现 (3.5h)

**状态**: ❌ 未开始

**描述**: 实现设置面板，支持应用各项配置

**文件**: `src/components/Settings.tsx`

**设置项**:
- 主题选择
- 字体大小
- 行高
- 编辑器宽度
- Tab 大小
- 自动保存
- 自动保存间隔
- 默认文件编码
- 是否显示行号
- 是否启用代码折叠

**实现**:

```typescript
import React, { useState, useEffect } from 'react';
import { useTheme } from '../hooks/useTheme';
import { useSettings } from '../hooks/useSettings';
import type { AppSettings } from '../types';

interface SettingsProps {
  isOpen: boolean;
  onClose: () => void;
}

export const Settings: React.FC<SettingsProps> = ({ isOpen, onClose }) => {
  const { theme, setTheme } = useTheme();
  const { settings, updateSettings, resetSettings } = useSettings();
  const [localSettings, setLocalSettings] = useState(settings);

  useEffect(() => {
    setLocalSettings(settings);
  }, [settings]);

  /**
   * 保存设置
   */
  const handleSave = () => {
    updateSettings(localSettings);
    onClose();
  };

  /**
   * 重置设置
   */
  const handleReset = () => {
    if (confirm('Are you sure you want to reset all settings to default?')) {
      resetSettings();
      onClose();
    }
  };

  if (!isOpen) return null;

  return (
    <div className="settings-overlay" onClick={onClose}>
      <div className="settings-panel" onClick={(e) => e.stopPropagation()}>
        {/* 标题栏 */}
        <div className="settings-header">
          <h2>Settings</h2>
          <button className="close-button" onClick={onClose}>×</button>
        </div>

        {/* 设置内容 */}
        <div className="settings-content">
          {/* 外观设置 */}
          <section className="settings-section">
            <h3>Appearance</h3>
            
            <div className="setting-item">
              <label>Theme</label>
              <select
                value={theme}
                onChange={(e) => setTheme(e.target.value as any)}
              >
                <option value="light">Light</option>
                <option value="dark">Dark</option>
                <option value="eink">E-ink</option>
                <option value="sepia">Sepia</option>
                <option value="auto">Auto (System)</option>
              </select>
            </div>

            <div className="setting-item">
              <label>Font Size</label>
              <div className="setting-control">
                <input
                  type="range"
                  min={10}
                  max={24}
                  value={localSettings.editor.fontSize}
                  onChange={(e) => setLocalSettings({
                    ...localSettings,
                    editor: {
                      ...localSettings.editor,
                      fontSize: Number(e.target.value),
                    },
                  })}
                />
                <span className="setting-value">{localSettings.editor.fontSize}px</span>
              </div>
            </div>

            <div className="setting-item">
              <label>Line Height</label>
              <div className="setting-control">
                <input
                  type="range"
                  min={1.0}
                  max={2.5}
                  step={0.1}
                  value={localSettings.editor.lineHeight}
                  onChange={(e) => setLocalSettings({
                    ...localSettings,
                    editor: {
                      ...localSettings.editor,
                      lineHeight: Number(e.target.value),
                    },
                  })}
                />
                <span className="setting-value">{localSettings.editor.lineHeight}</span>
              </div>
            </div>

            <div className="setting-item">
              <label>Max Editor Width</label>
              <div className="setting-control">
                <input
                  type="range"
                  min={500}
                  max={1200}
                  step={50}
                  value={localSettings.editor.maxWidth}
                  onChange={(e) => setLocalSettings({
                    ...localSettings,
                    editor: {
                      ...localSettings.editor,
                      maxWidth: Number(e.target.value),
                    },
                  })}
                />
                <span className="setting-value">{localSettings.editor.maxWidth}px</span>
              </div>
            </div>
          </section>

          {/* 编辑器设置 */}
          <section className="settings-section">
            <h3>Editor</h3>
            
            <div className="setting-item">
              <label>Show Line Numbers</label>
              <input
                type="checkbox"
                checked={localSettings.editor.showLineNumbers}
                onChange={(e) => setLocalSettings({
                  ...localSettings,
                  editor: {
                    ...localSettings.editor,
                    showLineNumbers: e.target.checked,
                  },
                })}
              />
            </div>

            <div className="setting-item">
              <label>Word Wrap</label>
              <input
                type="checkbox"
                checked={localSettings.editor.wordWrap}
                onChange={(e) => setLocalSettings({
                  ...localSettings,
                  editor: {
                    ...localSettings.editor,
                    wordWrap: e.target.checked,
                  },
                })}
              />
            </div>

            <div className="setting-item">
              <label>Tab Size</label>
              <select
                value={localSettings.editor.tabSize}
                onChange={(e) => setLocalSettings({
                  ...localSettings,
                  editor: {
                    ...localSettings.editor,
                    tabSize: Number(e.target.value),
                  },
                })}
              >
                <option value={2}>2 spaces</option>
                <option value={4}>4 spaces</option>
                <option value={8}>8 spaces</option>
              </select>
            </div>
          </section>

          {/* 文件设置 */}
          <section className="settings-section">
            <h3>File</h3>
            
            <div className="setting-item">
              <label>Auto Save</label>
              <input
                type="checkbox"
                checked={localSettings.autoSave}
                onChange={(e) => setLocalSettings({
                  ...localSettings,
                  autoSave: e.target.checked,
                })}
              />
            </div>

            <div className="setting-item">
              <label>Auto Save Interval</label>
              <select
                value={localSettings.autoSaveInterval / 1000}
                onChange={(e) => setLocalSettings({
                  ...localSettings,
                  autoSaveInterval: Number(e.target.value) * 1000,
                })}
                disabled={!localSettings.autoSave}
              >
                <option value={10}>10 seconds</option>
                <option value={30}>30 seconds</option>
                <option value={60}>1 minute</option>
                <option value={300}>5 minutes</option>
              </select>
            </div>
          </section>
        </div>

        {/* 底部按钮 */}
        <div className="settings-footer">
          <button className="button-secondary" onClick={handleReset}>
            Reset to Default
          </button>
          <div className="footer-actions">
            <button className="button-secondary" onClick={onClose}>
              Cancel
            </button>
            <button className="button-primary" onClick={handleSave}>
              Save
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
```

**配套样式**:

```css
.settings-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
}

.settings-panel {
  width: 600px;
  max-height: 80vh;
  background-color: var(--color-bg-primary);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.settings-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid var(--color-border);
}

.settings-header h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.close-button {
  width: 32px;
  height: 32px;
  font-size: 20px;
  color: var(--color-text-secondary);
  background-color: transparent;
  border-radius: var(--radius-sm);
}

.close-button:hover {
  background-color: var(--color-bg-tertiary);
}

.settings-content {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}

.settings-section {
  margin-bottom: 32px;
}

.settings-section h3 {
  margin: 0 0 16px 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.setting-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 0;
  border-bottom: 1px solid var(--color-border-light);
}

.setting-item:last-child {
  border-bottom: none;
}

.setting-item label {
  font-size: 14px;
  color: var(--color-text-primary);
}

.setting-control {
  display: flex;
  align-items: center;
  gap: 12px;
}

.setting-value {
  min-width: 50px;
  text-align: right;
  font-size: 13px;
  color: var(--color-text-secondary);
}

.settings-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-top: 1px solid var(--color-border);
}

.footer-actions {
  display: flex;
  gap: 8px;
}

.button-primary,
.button-secondary {
  padding: 8px 16px;
  border-radius: var(--radius-sm);
  font-size: 14px;
  font-weight: 500;
  transition: background-color var(--transition-fast);
}

.button-primary {
  color: #ffffff;
  background-color: var(--color-accent);
}

.button-primary:hover {
  background-color: var(--color-accent-hover);
}

.button-secondary {
  color: var(--color-text-primary);
  background-color: var(--color-bg-secondary);
}

.button-secondary:hover {
  background-color: var(--color-bg-tertiary);
}
```

---

### 任务 3.5：状态栏组件实现 (2h)

**状态**: ❌ 未开始

**描述**: 实现底部状态栏，显示文档信息和应用状态

**文件**: `src/components/StatusBar.tsx`

**显示信息**:
- 行号:列号
- 字数统计
- 文件编码
- 文件类型
- 修改状态
- 当前主题

**实现**:

```typescript
import React from 'react';
import type { DocumentMetadata } from '../types';

interface StatusBarProps {
  metadata?: DocumentMetadata | null;
  isModified?: boolean;
  filePath?: string;
  cursorPosition?: { line: number; column: number };
  currentTheme?: string;
}

export const StatusBar: React.FC<StatusBarProps> = ({
  metadata,
  isModified = false,
  filePath,
  cursorPosition,
  currentTheme = 'light',
}) => {
  const fileName = filePath ? filePath.split(/[/\\]/).pop() : 'Untitled';
  const extension = fileName ? fileName.split('.').pop() : 'md';
  
  return (
    <div className="statusbar">
      {/* 左侧：文件信息 */}
      <div className="statusbar-left">
        <span className="statusbar-item">
          {fileName}
        </span>
        {isModified && (
          <span className="statusbar-item modified">●</span>
        )}
      </div>

      {/* 中间：统计信息 */}
      <div className="statusbar-center">
        {cursorPosition && (
          <span className="statusbar-item">
            Ln {cursorPosition.line + 1}, Col {cursorPosition.column + 1}
          </span>
        )}
        {metadata && (
          <>
            <span className="statusbar-separator">|</span>
            <span className="statusbar-item">
              {metadata.wordCount} words
            </span>
            <span className="statusbar-item">
              {metadata.charCount} chars
            </span>
            <span className="statusbar-item">
              {metadata.lineCount} lines
            </span>
          </>
        )}
      </div>

      {/* 右侧：状态信息 */}
      <div className="statusbar-right">
        <span className="statusbar-item">
          {extension?.toUpperCase()}
        </span>
        <span className="statusbar-item">
          UTF-8
        </span>
        <span className="statusbar-item">
          {currentTheme}
        </span>
      </div>
    </div>
  );
};
```

**配套样式**:

```css
.statusbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: var(--statusbar-height);
  padding: 0 12px;
  background-color: var(--color-bg-secondary);
  border-top: 1px solid var(--color-border);
  font-size: 12px;
  color: var(--color-text-secondary);
}

.statusbar-left,
.statusbar-center,
.statusbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.statusbar-center {
  flex: 1;
  justify-content: center;
}

.statusbar-item {
  padding: 2px 4px;
  white-space: nowrap;
}

.statusbar-separator {
  color: var(--color-border);
}

.statusbar-item.modified {
  color: var(--color-warning);
  animation: pulse 2s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}
```

---

### 任务 3.6：侧边栏组件实现 (1.5h)

**状态**: ❌ 未开始

**描述**: 实现可折叠的侧边栏，支持文件树和目录导航

**文件**: `src/components/Sidebar.tsx`

**功能**:
- 文件树视图
- 最近文件列表
- 目录 (TOC) 视图
- 折叠/展开

**实现**:

```typescript
import React, { useState } from 'react';

type SidebarTab = 'files' | 'toc' | 'search';

interface SidebarProps {
  className?: string;
}

export const Sidebar: React.FC<SidebarProps> = ({ className = '' }) => {
  const [activeTab, setActiveTab] = useState<SidebarTab>('files');
  const [isCollapsed, setIsCollapsed] = useState(false);

  return (
    <div className={`sidebar ${isCollapsed ? 'collapsed' : ''} ${className}`}>
      {/* 侧边栏头部 */}
      <div className="sidebar-header">
        {!isCollapsed && (
          <div className="sidebar-tabs">
            <SidebarTabButton
              active={activeTab === 'files'}
              onClick={() => setActiveTab('files')}
              icon="📁"
              label="Files"
            />
            <SidebarTabButton
              active={activeTab === 'toc'}
              onClick={() => setActiveTab('toc')}
              icon="📑"
              label="TOC"
            />
          </div>
        )}
        <button
          className="sidebar-toggle"
          onClick={() => setIsCollapsed(!isCollapsed)}
        >
          {isCollapsed ? '▶' : '◀'}
        </button>
      </div>

      {/* 侧边栏内容 */}
      {!isCollapsed && (
        <div className="sidebar-content">
          {activeTab === 'files' && <FileTree />}
          {activeTab === 'toc' && <TableOfContents />}
        </div>
      )}
    </div>
  );
};

interface SidebarTabButtonProps {
  active: boolean;
  onClick: () => void;
  icon: string;
  label: string;
}

const SidebarTabButton: React.FC<SidebarTabButtonProps> = ({
  active,
  onClick,
  icon,
  label,
}) => {
  return (
    <button
      className={`sidebar-tab ${active ? 'active' : ''}`}
      onClick={onClick}
      title={label}
    >
      <span className="tab-icon">{icon}</span>
      <span className="tab-label">{label}</span>
    </button>
  );
};

/**
 * 文件树组件
 */
const FileTree: React.FC = () => {
  // 文件树实现...
  return (
    <div className="sidebar-panel">
      <p className="empty-state">No files open</p>
    </div>
  );
};

/**
 * 目录组件
 */
const TableOfContents: React.FC = () => {
  // 目录实现...
  return (
    <div className="sidebar-panel">
      <p className="empty-state">No headings found</p>
    </div>
  );
};
```

---

**阶段三完成标准**:
- [ ] 工具栏所有按钮正常工作
- [ ] 工具栏下拉菜单正常显示和操作
- [ ] 所有快捷键正常响应
- [ ] 主题切换正常且过渡动画流畅
- [ ] 设置面板可打开和关闭
- [ ] 设置修改可保存
- [ ] 状态栏正确显示信息
- [ ] 侧边栏可折叠和展开
- [ ] 侧边栏标签页切换正常

---

## 阶段四：高级功能

> **目标**: 实现搜索、替换、目录、自动保存等高级功能
> **预计工时**: 18 小时
> **依赖**: 阶段三完成

### 阶段概述

本阶段实现编辑器的高级功能，包括搜索与替换、目录导航、自动保存、导出功能等。

---

### 任务 4.1：搜索功能实现 (5h)

**状态**: ❌ 未开始

**文件**: `src/components/SearchPanel.tsx`

**功能清单**:
- 搜索输入框
- 搜索选项 (大小写、全词、正则)
- 结果列表
- 高亮显示
- 上一个/下一个导航
- 快捷键 Ctrl+F

**实现**:

```typescript
import React, { useState, useEffect, useCallback } from 'react';
import type { SearchOptions, SearchResult } from '../types';

interface SearchPanelProps {
  content: string;
  onJumpTo?: (position: number) => void;
  onClose?: () => void;
}

export const SearchPanel: React.FC<SearchPanelProps> = ({
  content,
  onJumpTo,
  onClose,
}) => {
  const [query, setQuery] = useState('');
  const [options, setOptions] = useState<SearchOptions>({
    caseSensitive: false,
    regex: false,
    wholeWord: false,
    scope: 'current',
  });
  const [results, setResults] = useState<SearchResult[]>([]);
  const [currentResultIndex, setCurrentResultIndex] = useState(0);
  const [isError, setIsError] = useState(false);

  /**
   * 执行搜索
   */
  const search = useCallback(() => {
    if (!query) {
      setResults([]);
      setCurrentResultIndex(0);
      return;
    }

    try {
      const searchResults: SearchResult[] = [];
      const lines = content.split('\n');
      let totalPosition = 0;

      for (let lineIndex = 0; lineIndex < lines.length; lineIndex++) {
        const line = lines[lineIndex];
        let searchContent = line;
        let searchQuery = query;

        // 处理大小写
        if (!options.caseSensitive) {
          searchContent = line.toLowerCase();
          searchQuery = query.toLowerCase();
        }

        // 构建正则表达式
        let regex: RegExp;
        if (options.regex) {
          try {
            regex = new RegExp(searchQuery, options.caseSensitive ? 'g' : 'gi');
          } catch (e) {
            setIsError(true);
            return;
          }
        } else {
          const escapedQuery = searchQuery.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
          const flags = options.caseSensitive ? 'g' : 'gi';
          regex = new RegExp(escapedQuery, flags);
        }

        // 查找匹配
        let match;
        while ((match = regex.exec(line)) !== null) {
          // 全词匹配检查
          if (options.wholeWord) {
            const before = line.charAt(match.index - 1);
            const after = line.charAt(match.index + match[0].length);
            
            const isWordBoundary = (char: string) => 
              !char || /\s/.test(char) || /[^\w]/.test(char);
            
            if (!isWordBoundary(before) || !isWordBoundary(after)) {
              continue;
            }
          }

          searchResults.push({
            start: totalPosition + match.index,
            end: totalPosition + match.index + match[0].length,
            text: match[0],
            lineNumber: lineIndex,
            lineContent: line,
          });
        }

        totalPosition += line.length + 1; // +1 for newline
      }

      setResults(searchResults);
      setCurrentResultIndex(0);
      setIsError(false);
    } catch (e) {
      setIsError(true);
    }
  }, [query, content, options]);

  /**
   * 跳转到上一个结果
   */
  const goToPrevious = useCallback(() => {
    if (results.length === 0) return;
    setCurrentResultIndex((prev) => {
      const newIndex = prev <= 0 ? results.length - 1 : prev - 1;
      const result = results[newIndex];
      if (onJumpTo) {
        onJumpTo(result.start);
      }
      return newIndex;
    });
  }, [results, onJumpTo]);

  /**
   * 跳转到下一个结果
   */
  const goToNext = useCallback(() => {
    if (results.length === 0) return;
    setCurrentResultIndex((prev) => {
      const newIndex = (prev + 1) % results.length;
      const result = results[newIndex];
      if (onJumpTo) {
        onJumpTo(result.start);
      }
      return newIndex;
    });
  }, [results, onJumpTo]);

  // 搜索防抖
  useEffect(() => {
    const timeout = setTimeout(() => {
      search();
    }, 200);

    return () => clearTimeout(timeout);
  }, [search]);

  const resultText = results.length === 0
    ? 'No results'
    : `${currentResultIndex + 1} of ${results.length}`;

  return (
    <div className="search-panel">
      <div className="search-input-group">
        <input
          type="text"
          className="search-input"
          placeholder="Search..."
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              e.shiftKey ? goToPrevious() : goToNext();
            }
          }}
        />
        {query && (
          <button
            className="search-clear"
            onClick={() => setQuery('')}
            title="Clear"
          >
            ×
          </button>
        )}
      </div>

      <div className="search-options">
        <label className="search-option">
          <input
            type="checkbox"
            checked={options.caseSensitive}
            onChange={(e) => setOptions({ ...options, caseSensitive: e.target.checked })}
          />
          Case
        </label>
        <label className="search-option">
          <input
            type="checkbox"
            checked={options.wholeWord}
            onChange={(e) => setOptions({ ...options, wholeWord: e.target.checked })}
          />
          Word
        </label>
        <label className="search-option">
          <input
            type="checkbox"
            checked={options.regex}
            onChange={(e) => setOptions({ ...options, regex: e.target.checked })}
          />
          Regex
        </label>
      </div>

      <div className="search-results-info">
        {isError ? (
          <span className="search-error">Invalid regex</span>
        ) : (
          <span className="search-count">{resultText}</span>
        )}
      </div>

      <div className="search-navigation">
        <button
          className="search-nav-button"
          onClick={goToPrevious}
          disabled={results.length === 0}
          title="Previous (Shift+Enter)"
        >
          ▲
        </button>
        <button
          className="search-nav-button"
          onClick={goToNext}
          disabled={results.length === 0}
          title="Next (Enter)"
        >
          ▼
        </button>
      </div>

      {onClose && (
        <button className="search-close" onClick={onClose} title="Close (Esc)">
          ×
        </button>
      )}
    </div>
  );
};
```

---

### 任务 4.2：替换功能实现 (3h)

**状态**: ❌ 未开始

**文件**: `src/components/ReplacePanel.tsx`

**功能清单**:
- 替换输入框
- 替换单个
- 替换全部
- 替换确认对话框
- 快捷键 Ctrl+H

**实现**:

```typescript
import React, { useState, useCallback } from 'react';
import { SearchPanel } from './SearchPanel';
import type { SearchOptions } from '../types';

interface ReplacePanelProps extends SearchPanelProps {
  onReplace?: (start: number, end: number, text: string) => void;
  onReplaceAll?: (replacements: Array<{ start: number; end: number; text: string }>) => void;
}

export const ReplacePanel: React.FC<ReplacePanelProps> = ({
  content,
  onJumpTo,
  onClose,
  onReplace,
  onReplaceAll,
}) => {
  const [query, setQuery] = useState('');
  const [replacement, setReplacement] = useState('');
  const [options, setOptions] = useState<SearchOptions>({
    caseSensitive: false,
    regex: false,
    wholeWord: false,
    scope: 'current',
  });

  /**
   * 执行替换操作
   */
  const executeReplace = useCallback((replaceAll: boolean) => {
    // 实现替换逻辑...
    if (replaceAll) {
      // 替换全部
    } else {
      // 替换当前匹配项
    }
  }, [query, replacement, options]);

  const handleReplace = () => {
    executeReplace(false);
  };

  const handleReplaceAll = () => {
    if (window.confirm(`Replace all occurrences of "${query}" with "${replacement}"?`)) {
      executeReplace(true);
    }
  };

  return (
    <div className="replace-panel">
      {/* 搜索部分 */}
      <SearchPanel
        content={content}
        onJumpTo={onJumpTo}
        onClose={undefined}
      />

      {/* 替换输入 */}
      <div className="replace-input-group">
        <input
          type="text"
          className="replace-input"
          placeholder="Replace with..."
          value={replacement}
          onChange={(e) => setReplacement(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              e.shiftKey ? handleReplaceAll() : handleReplace();
            }
          }}
        />
      </div>

      {/* 替换按钮 */}
      <div className="replace-actions">
        <button
          className="replace-button"
          onClick={handleReplace}
          disabled={!query}
          title="Replace (Enter)"
        >
          Replace
        </button>
        <button
          className="replace-button replace-all"
          onClick={handleReplaceAll}
          disabled={!query}
          title="Replace All (Shift+Enter)"
        >
          Replace All
        </button>
      </div>
    </div>
  );
};
```

---

### 任务 4.3：目录 (TOC) 功能实现 (4h)

**状态**: ❌ 未开始

**文件**: `src/components/TableOfContents.tsx`

**功能清单**:
- 提取标题结构
- 层级显示
- 点击跳转
- 高亮当前位置
- 自动滚动跟随

**实现**:

```typescript
import React, { useState, useEffect, useCallback } from 'react';

interface TocEntry {
  level: number;
  text: string;
  line: number;
  id: string;
  children?: TocEntry[];
}

interface TableOfContentsProps {
  content: string;
  onNavigate?: (line: number) => void;
  currentLine?: number;
}

export const TableOfContents: React.FC<TableOfContentsProps> = ({
  content,
  onNavigate,
  currentLine,
}) => {
  const [toc, setToc] = useState<TocEntry[]>([]);

  /**
   * 解析 Markdown 提取标题
   */
  useEffect(() => {
    const entries: TocEntry[] = [];
    const lines = content.split('\n');

    lines.forEach((line, index) => {
      const match = line.match(/^(#{1,6})\s+(.+)$/);
      if (match) {
        const level = match[1].length;
        const text = match[2];
        const id = `heading-${index}`;

        entries.push({
          level,
          text,
          line: index,
          id,
        });
      }
    });

    setToc(buildTocTree(entries));
  }, [content]);

  /**
   * 构建层级树结构
   */
  const buildTocTree = (entries: TocEntry[]): TocEntry[] => {
    const result: TocEntry[] = [];
    const stack: TocEntry[] = [];

    entries.forEach((entry) => {
      const node = { ...entry };

      while (stack.length > 0 && stack[stack.length - 1].level >= node.level) {
        stack.pop();
      }

      if (stack.length === 0) {
        result.push(node);
      } else {
        const parent = stack[stack.length - 1];
        if (!parent.children) {
          parent.children = [];
        }
        parent.children.push(node);
      }

      stack.push(node);
    });

    return result;
  };

  /**
   * 处理点击导航
   */
  const handleClick = useCallback((entry: TocEntry) => {
    if (onNavigate) {
      onNavigate(entry.line);
    }
  }, [onNavigate]);

  /**
   * 渲染目录项
   */
  const renderTocItem = (entry: TocEntry, depth: number = 0): React.ReactNode => {
    const isActive = currentLine === entry.line;
    const paddingLeft = `${depth * 16 + 8}px`;

    return (
      <div key={entry.id}>
        <div
          className={`toc-item ${isActive ? 'active' : ''}`}
          style={{ paddingLeft }}
          onClick={() => handleClick(entry)}
        >
          <span className="toc-indicator">H{entry.level}</span>
          <span className="toc-text">{entry.text}</span>
        </div>
        {entry.children && entry.children.map((child) => renderTocItem(child, depth + 1))}
      </div>
    );
  };

  if (toc.length === 0) {
    return (
      <div className="toc-empty">
        <p>No headings found</p>
      </div>
    );
  }

  return (
    <div className="table-of-contents">
      <div className="toc-header">Table of Contents</div>
      <div className="toc-list">
        {toc.map((entry) => renderTocItem(entry))}
      </div>
    </div>
  );
};
```

---

### 任务 4.4：自动保存功能实现 (3h)

**状态**: ❌ 未开始

**文件**: `src/hooks/useAutoSave.ts`

**功能清单**:
- 定时自动保存
- 修改后延迟保存
- 保存状态指示
- 恢复未保存内容

**实现**:

```typescript
import { useEffect, useRef, useCallback, useState } from 'react';

interface UseAutoSaveOptions {
  content: string;
  onSave: (content: string) => void | Promise<void>;
  interval?: number; // 定时保存间隔 (毫秒)
  debounce?: number; // 防抖延迟 (毫秒)
  enabled?: boolean;
}

interface AutoSaveState {
  isSaving: boolean;
  lastSaved: Date | null;
  lastSavedContent: string;
  hasUnsavedChanges: boolean;
}

/**
 * 自动保存 Hook
 */
export function useAutoSave({
  content,
  onSave,
  interval = 30000, // 默认 30 秒
  debounce = 2000, // 默认 2 秒
  enabled = true,
}: UseAutoSaveOptions) {
  const [state, setState] = useState<AutoSaveState>({
    isSaving: false,
    lastSaved: null,
    lastSavedContent: '',
    hasUnsavedChanges: false,
  });

  const saveTimeoutRef = useRef<NodeJS.Timeout>();
  const intervalRef = useRef<NodeJS.Timeout>();

  /**
   * 执行保存
   */
  const executeSave = useCallback(async () => {
    if (!enabled || state.isSaving) return;

    setState((prev) => ({ ...prev, isSaving: true }));

    try {
      await onSave(content);
      
      setState({
        isSaving: false,
        lastSaved: new Date(),
        lastSavedContent: content,
        hasUnsavedChanges: false,
      });
    } catch (error) {
      console.error('Auto-save failed:', error);
      setState((prev) => ({ ...prev, isSaving: false }));
    }
  }, [content, onSave, enabled, state.isSaving]);

  /**
   * 手动触发保存
   */
  const save = useCallback(() => {
    if (saveTimeoutRef.current) {
      clearTimeout(saveTimeoutRef.current);
    }
    executeSave();
  }, [executeSave]);

  /**
   * 检查是否有未保存的更改
   */
  const checkUnsavedChanges = useCallback(() => {
    const hasChanges = content !== state.lastSavedContent;
    setState((prev) => ({ ...prev, hasUnsavedChanges: hasChanges }));
    return hasChanges;
  }, [content, state.lastSavedContent]);

  // 内容变化时设置防抖保存
  useEffect(() => {
    if (!enabled) return;

    // 清除之前的定时器
    if (saveTimeoutRef.current) {
      clearTimeout(saveTimeoutRef.current);
    }

    // 检测内容变化
    const hasChanges = content !== state.lastSavedContent;
    if (hasChanges) {
      setState((prev) => ({ ...prev, hasUnsavedChanges: true }));
      
      // 设置防抖保存
      saveTimeoutRef.current = setTimeout(() => {
        executeSave();
      }, debounce);
    }

    return () => {
      if (saveTimeoutRef.current) {
        clearTimeout(saveTimeoutRef.current);
      }
    };
  }, [content, state.lastSavedContent, enabled, debounce, executeSave]);

  // 定时保存
  useEffect(() => {
    if (!enabled || interval <= 0) return;

    intervalRef.current = setInterval(() => {
      const hasChanges = content !== state.lastSavedContent;
      if (hasChanges) {
        executeSave();
      }
    }, interval);

    return () => {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
      }
    };
  }, [content, state.lastSavedContent, enabled, interval, executeSave]);

  // 组件卸载时保存
  useEffect(() => {
    return () => {
      const hasChanges = content !== state.lastSavedContent;
      if (hasChanges && enabled) {
        executeSave();
      }
    };
  }, [content, state.lastSavedContent, enabled, executeSave]);

  return {
    ...state,
    save,
    checkUnsavedChanges,
  };
}
```

---

### 任务 4.5：导出功能实现 (3h)

**状态**: ❌ 未开始

**文件**: `src/hooks/useExport.ts`

**支持格式**:
- HTML
- PDF (使用 print API)
- 纯文本
- 图片 (PNG)

**实现**:

```typescript
import { useCallback } from 'react';
import { save } from '@tauri-apps/api/dialog';
import { writeTextFile } from '@tauri-apps/api/fs';

type ExportFormat = 'html' | 'pdf' | 'txt' | 'png';

interface ExportOptions {
  title?: string;
  includeStyles?: boolean;
}

/**
 * 导出功能 Hook
 */
export function useExport() {
  /**
   * 导出为 HTML
   */
  const exportToHtml = useCallback(async (
    markdown: string,
    htmlContent: string,
    options?: ExportOptions
  ) => {
    const filePath = await save({
      filters: [{
        name: 'HTML',
        extensions: ['html']
      }]
    });

    if (!filePath) return;

    const fullHtml = `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>${options?.title || 'Document'}</title>
  <style>
    body {
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
      line-height: 1.6;
      max-width: 800px;
      margin: 0 auto;
      padding: 20px;
      color: #333;
    }
    /* 添加更多样式... */
  </style>
</head>
<body>
  ${htmlContent}
</body>
</html>`;

    await writeTextFile(filePath, fullHtml);
  }, []);

  /**
   * 导出为 PDF
   */
  const exportToPdf = useCallback(async (
    htmlContent: string,
    options?: ExportOptions
  ) => {
    // 创建打印窗口
    const printWindow = window.open('', '_blank');
    if (!printWindow) {
      throw new Error('Failed to open print window');
    }

    const fullHtml = `<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8">
  <title>${options?.title || 'Document'}</title>
  <style>
    @media print {
      body { margin: 0; }
      @page { margin: 2cm; }
    }
  </style>
</head>
<body>
  ${htmlContent}
  <script>
    window.onload = function() {
      window.print();
      window.onafterprint = function() {
        window.close();
      };
    };
  </script>
</body>
</html>`;

    printWindow.document.write(fullHtml);
    printWindow.document.close();
  }, []);

  /**
   * 导出为纯文本
   */
  const exportToText = useCallback(async (markdown: string) => {
    const filePath = await save({
      filters: [{
        name: 'Text',
        extensions: ['txt']
      }]
    });

    if (!filePath) return;

    await writeTextFile(filePath, markdown);
  }, []);

  /**
   * 导出为图片 (需要 html2canvas)
   */
  const exportToPng = useCallback(async (element: HTMLElement) => {
    // 动态导入 html2canvas
    const html2canvas = (await import('html2canvas')).default;
    
    const canvas = await html2canvas(element, {
      scale: 2, // Retina 支持
      backgroundColor: '#ffffff',
    });

    const blob = await new Promise<Blob>((resolve) => {
      canvas.toBlob((blob) => resolve(blob!), 'image/png');
    });

    // 使用 Tauri API 保存文件
    const filePath = await save({
      filters: [{
        name: 'PNG Image',
        extensions: ['png']
      }]
    });

    if (!filePath || !blob) return;

    // 转换 Blob 为 ArrayBuffer 并写入
    const arrayBuffer = await blob.arrayBuffer();
    const uint8Array = new Uint8Array(arrayBuffer);
    
    // 需要使用 Tauri 的二进制文件写入 API
    // 这里简化处理，实际需要使用适当的方法
    console.warn('PNG export requires additional implementation');
  }, []);

  return {
    exportToHtml,
    exportToPdf,
    exportToText,
    exportToPng,
  };
}
```

---

**阶段四完成标准**:
- [ ] 搜索功能正常，可定位到所有匹配项
- [ ] 搜索选项 (大小写、全词、正则) 正常工作
- [ ] 替换功能正常，可替换单个或全部
- [ ] 目录正确显示所有标题
- [ ] 点击目录可跳转到对应位置
- [ ] 自动保存正常工作
- [ ] 自动保存状态正确显示
- [ ] 导出为 HTML 正常
- [ ] 导出为 PDF 正常
- [ ] 导出为纯文本正常

---

## 阶段五：打包发布

> **目标**: 生成各平台安装包并发布
> **预计工时**: 12 小时
> **依赖**: 阶段四完成

### 阶段概述

本阶段完成应用的打包发布准备工作，包括图标资源、打包配置、构建测试和发布流程。

---

### 任务 5.1：图标和资源准备 (2h)

**状态**: ❌ 未开始

**文件**: `src-tauri/icons/`

**要求**:

#### Windows (.ico)
需要包含以下尺寸：
- 16x16
- 32x32
- 48x48
- 256x256

#### macOS (.icns)
需要包含以下尺寸：
- 16x16
- 32x32
- 64x64 (Retina)
- 128x128
- 256x256 (Retina)
- 512x512
- 1024x1024 (Retina)

#### Linux (.png)
- 512x512 (用于 AppImage)
- 64x64 (用于 deb 包)
- 128x128 (用于 deb 包)

**图标生成步骤**:

1. 设计原始图标 (1024x1024 PNG)
2. 使用在线工具转换：
   - https://icoconvert.com/ (生成 .ico)
   - https://cloudconvert.com/png-to-icns (生成 .icns)
3. 放置到对应目录：
   ```
   src-tauri/icons/
   ├── 32x32.png
   ├── 128x128.png
   ├── 128x128@2x.png
   ├── icon.icns
   ├── icon.ico
   └── icon.png
   ```

---

### 任务 5.2：打包配置完善 (2h)

**状态**: ⚠️ 部分完成 (tauri.conf.json 存在)

**文件**: `src-tauri/tauri.conf.json`

**完整配置**:

```json
{
  "$schema": "https://schema.tauri.app/config/1.6.0",
  "build": {
    "beforeBuildCommand": "npm run build",
    "beforeDevCommand": "npm run dev",
    "devPath": "http://localhost:1420",
    "distDir": "../dist"
  },
  "package": {
    "productName": "NomadMark",
    "version": "0.1.0"
  },
  "tauri": {
    "allowlist": {
      "all": false,
      "shell": {
        "all": false,
        "open": true
      },
      "dialog": {
        "all": false,
        "open": true,
        "save": true
      },
      "fs": {
        "all": false,
        "readFile": true,
        "writeFile": true,
        "readDir": true,
        "scope": ["**"]
      },
      "path": {
        "all": true
      }
    },
    "bundle": {
      "active": true,
      "category": "Developer Tool",
      "copyright": "Copyright © 2024 NomadMark Team",
      "deb": {
        "depends": []
      },
      "externalBin": [],
      "icon": [
        "icons/32x32.png",
        "icons/128x128.png",
        "icons/128x128@2x.png",
        "icons/icon.icns",
        "icons/icon.ico"
      ],
      "identifier": "com.nomadmark.desktop",
      "longDescription": "A modern Markdown editor for desktop with live preview, syntax highlighting, and cross-platform support.",
      "macOS": {
        "entitlements": null,
        "exceptionDomain": "",
        "frameworks": [],
        "providerShortName": null,
        "signingIdentity": null
      },
      "resources": [],
      "shortDescription": "Cross-platform Markdown Editor",
      "targets": "all",
      "windows": {
        "certificateThumbprint": null,
        "digestAlgorithm": "sha256",
        "timestampUrl": "",
        "wix": {
          "language": "en-US"
        },
        "webviewInstallMode": {
          "type": "embedBootstrapper"
        }
      }
    },
    "security": {
      "csp": null
    },
    "updater": {
      "active": false
    },
    "windows": [
      {
        "fullscreen": false,
        "height": 900,
        "resizable": true,
        "title": "NomadMark",
        "width": 1200,
        "minWidth": 800,
        "minHeight": 600,
        "theme": "system"
      }
    ]
  }
}
```

---

### 任务 5.3：构建测试 (4h)

**状态**: ❌ 未开始

**测试矩阵**:

#### Windows (x64)
```bash
cd platforms/desktop
npm run tauri:build -- --target x86_64-pc-windows-msvc
```

**输出**: `src-tauri/target/x86_64-pc-windows-msvc/release/bundle/nsis/NomadMark_0.1.0_x64-setup.exe`

**测试清单**:
- [ ] 安装程序正常运行
- [ ] 应用正确安装
- [ ] 应用可正常启动
- [ ] 核心功能测试通过
- [ ] 卸载程序正常工作

#### macOS (Intel + Apple Silicon)
```bash
# Intel
npm run tauri:build -- --target x86_64-apple-darwin

# Apple Silicon
npm run tauri:build -- --target aarch64-apple-darwin

# Universal (需要额外步骤)
npm run tauri:build -- --target universal-apple-darwin
```

**输出**: `src-tauri/target/*/release/bundle/dmg/NomadMark_0.1.0_*.dmg`

**测试清单**:
- [ ] DMG 可正常挂载
- [ ] 应用可拖拽到 Applications
- [ ] 应用可正常启动
- [ ] 核心功能测试通过
- [ ] 右键菜单正常

#### Linux (AppImage + deb)
```bash
# AppImage
npm run tauri:build -- --target x86_64-unknown-linux-gnu

# deb (需要在 Debian/Ubuntu 上构建)
npm run tauri:build -- --target x86_64-unknown-linux-gnu
```

**输出**: 
- AppImage: `src-tauri/target/x86_64-unknown-linux-gnu/release/bundle/appimage/NomadMark_0.1.0_amd64.AppImage`
- deb: `src-tauri/target/x86_64-unknown-linux-gnu/release/bundle/deb/nomadmark_0.1.0_amd64.deb`

**测试清单**:
- [ ] AppImage 可执行权限正确
- [ ] AppImage 可直接运行
- [ ] deb 包可正常安装
- [ ] 应用在应用程序菜单中显示
- [ ] 核心功能测试通过

---

### 任务 5.4：发布准备 (4h)

**状态**: ❌ 未开始

**任务清单**:

#### 1. 生成各平台安装包
```bash
# Windows
npm run tauri:build -- --target x86_64-pc-windows-msvc

# macOS
npm run tauri:build -- --target universal-apple-darwin

# Linux
npm run tauri:build -- --target x86_64-unknown-linux-gnu
```

#### 2. 准备发布说明

创建 `RELEASE_NOTES.md`:

```markdown
# NomadMark v0.1.0 Release Notes

## What's New

- Initial release of NomadMark Desktop
- Live Markdown preview with syntax highlighting
- Support for Windows, macOS, and Linux
- Multiple themes: Light, Dark, E-ink, Sepia
- Auto-save functionality
- Search and replace support
- Table of Contents navigation
- Export to HTML, PDF, and plain text

## System Requirements

### Windows
- Windows 10 or later
- x64 architecture

### macOS
- macOS 10.15 (Catalina) or later
- Intel or Apple Silicon

### Linux
- Any modern distribution
- x64 architecture

## Installation

### Windows
Download the `.exe` installer and run it.

### macOS
Download the `.dmg` file, open it, and drag NomadMark to your Applications folder.

### Linux
Download the `.AppImage` file, make it executable, and run it:
```bash
chmod +x NomadMark_0.1.0_amd64.AppImage
./NomadMark_0.1.0_amd64.AppImage
```

## Known Issues

- Currently only supports English interface
- Large files (>10MB) may have performance issues

## Next Release

Planned features for v0.2.0:
- Multi-language support
- Cloud storage integration
- Plugin system
```

#### 3. 创建 GitHub Release

使用 GitHub CLI 或网页界面：

```bash
# 使用 gh CLI
gh release create v0.1.0 \
  src-tauri/target/*/release/bundle/*/*.{exe,dmg,AppImage,deb} \
  --notes "RELEASE_NOTES.md" \
  --title "NomadMark v0.1.0"
```

#### 4. 更新网站/文档 (如果有)

- 更新下载页面
- 更新用户文档
- 添加版本更新日志

---

**阶段五完成标准**:
- [ ] 所有平台安装包可正常生成
- [ ] Windows 安装包测试通过
- [ ] macOS 安装包测试通过
- [ ] Linux AppImage 测试通过
- [ ] 发布说明准备完成
- [ ] GitHub Release 创建完成
- [ ] 下载链接可用

---

## 时间线汇总

### 甘特图

```
任务                W1  W2  W3  W4
─────────────────────────────────
阶段一 基础架构     ████
阶段二 核心编辑         ████████████████
阶段三 UI/UX 完善                  ████████████████████
阶段四 高级功能                               ████████████████████
阶段五 打包发布                                           ████████
```

### 详细时间表

| 周次 | 阶段 | 任务 | 工时 | 完成日期 |
|------|------|------|------|----------|
| W1-D1-2 | 阶段一 | 基础架构搭建 | 12h | Day 2 |
| W1-D3-5 | 阶段二 | 编辑器组件 | 8h | Day 5 |
| W2-D1-2 | 阶段二 | 预览组件 | 8h | Day 7 |
| W2-D3 | 阶段二 | 分屏组件 | 5h | Day 8 |
| W2-D4 | 阶段二 | Canvas 渲染 | 4h | Day 9 |
| W2-D5 | 阶段二 | 文件操作 | 3h | Day 10 |
| W3-D1 | 阶段三 | 工具栏组件 | 5h | Day 11 |
| W3-D2 | 阶段三 | 快捷键系统 | 4h | Day 12 |
| W3-D3 | 阶段三 | 主题系统 | 4h | Day 13 |
| W3-D4 | 阶段三 | 设置面板 | 3.5h | Day 14 |
| W3-D5 | 阶段三 | 状态栏 + 侧边栏 | 3.5h | Day 15 |
| W4-D1-2 | 阶段四 | 搜索功能 | 5h | Day 17 |
| W4-D3 | 阶段四 | 替换功能 | 3h | Day 18 |
| W4-D3 | 阶段四 | 目录功能 | 4h | Day 19 |
| W4-D4 | 阶段四 | 自动保存 | 3h | Day 20 |
| W4-D4 | 阶段四 | 导出功能 | 3h | Day 21 |
| W4-D5 | 阶段五 | 图标资源 | 2h | Day 22 |
| W4-D5 | 阶段五 | 打包配置 | 2h | Day 22 |
| W5-D1-2 | 阶段五 | 构建测试 | 4h | Day 24 |
| W5-D3 | 阶段五 | 发布准备 | 4h | Day 25 |

**总计**: 90 小时，约 **11-12 个工作日** 或 **2.5 周**

---

## 验证清单

### 功能验证

#### 基础功能
- [ ] 应用可正常启动和关闭
- [ ] 可创建新文档
- [ ] 可打开现有文档
- [ ] 可保存文档
- [ ] 可另存为
- [ ] 文件关联正常 (可选)

#### 编辑功能
- [ ] 文本输入正常
- [ ] 撤销/重做正常
- [ ] 剪切/复制/粘贴正常
- [ ] 查找/替换正常
- [ ] 快捷键响应正常
- [ ] Tab 键插入空格
- [ ] 行号正确显示

#### 预览功能
- [ ] Markdown 正确渲染
- [ ] 代码高亮正常
- [ ] 图片正确显示
- [ ] 表格正确渲染
- [ ] 链接可点击
- [ ] 数学公式正确渲染 (如启用)

#### 界面功能
- [ ] 工具栏按钮正常
- [ ] 工具栏菜单正常显示
- [ ] 主题切换正常
- [ ] 分屏模式正常
- [ ] 分隔条可拖动
- [ ] 全屏模式正常
- [ ] 设置可保存和读取
- [ ] 状态栏信息正确
- [ ] 侧边栏可折叠

#### 高级功能
- [ ] 搜索功能正常
- [ ] 搜索结果高亮显示
- [ ] 替换功能正常
- [ ] 目录正确显示
- [ ] 目录点击可跳转
- [ ] 自动保存正常
- [ ] 自动保存状态正确显示
- [ ] 导出 HTML 正常
- [ ] 导出 PDF 正常
- [ ] 导出纯文本正常

### 平台验证

#### Windows
- [ ] 安装程序 (EXE) 正常
- [ ] 应用启动正常
- [ ] 窗口大小调整正常
- [ ] 功能测试通过
- [ ] 卸载正常
- [ ] 文件关联正常 (可选)

#### macOS
- [ ] DMG 安装正常
- [ ] 应用启动正常
- [ ] 菜单栏显示正常
- [ ] 窗口控制正常
- [ ] 功能测试通过
- [ ] 代码签名 (如需要)
- [ ] 公钥分发 (如需要)

#### Linux
- [ ] AppImage 正常
- [ ] deb 包正常 (Ubuntu/Debian)
- [ ] 应用在菜单中显示
- [ ] 功能测试通过
- [ ] 文件关联正常 (可选)

### 性能验证

- [ ] 启动时间 < 3 秒
- [ ] 编辑 1000 字文档流畅
- [ ] 预览渲染时间 < 500ms
- [ ] 内存占用 < 200MB (空闲)
- [ ] 打开 1MB 文档可接受

---

## 依赖关系

```
┌─────────────────────────────────────────────────────────────────┐
│                        依赖关系图                                │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Rust Core (markdown_core)                                      │
│         │                                                        │
│         ▼                                                        │
│  ┌───────────────────────────────────────┐                      │
│  │ 阶段一: 基础架构 (12h)               │                      │
│  │  • Tauri FFI 集成                     │                      │
│  │  • React 组件结构                     │                      │
│  │  • 类型定义系统                        │                      │
│  │  • 状态管理 Hook                      │                      │
│  │  • 全局样式系统                        │                      │
│  └───────────────────────────────────────┘                      │
│         │                                                        │
│         ▼                                                        │
│  ┌───────────────────────────────────────┐                      │
│  │ 阶段二: 核心编辑 (28h)               │                      │
│  │  • 编辑器组件 (8h)                    │                      │
│  │  • 预览组件 (8h)                      │                      │
│  │  • 分屏组件 (5h)                      │                      │
│  │  • Canvas 渲染 (4h)                   │                      │
│  │  • 文件操作 (3h)                      │                      │
│  └───────────────────────────────────────┘                      │
│         │                                                        │
│         ▼                                                        │
│  ┌───────────────────────────────────────┐                      │
│  │ 阶段三: UI/UX (20h)                  │                      │
│  │  • 工具栏组件 (5h)                    │                      │
│  │  • 快捷键系统 (4h)                    │                      │
│  │  • 主题系统 (4h)                      │                      │
│  │  • 设置面板 (3.5h)                    │                      │
│  │  • 状态栏 + 侧边栏 (3.5h)             │                      │
│  └───────────────────────────────────────┘                      │
│         │                                                        │
│         ▼                                                        │
│  ┌───────────────────────────────────────┐                      │
│  │ 阶段四: 高级功能 (18h)               │                      │
│  │  • 搜索功能 (5h)                      │                      │
│  │  • 替换功能 (3h)                      │                      │
│  │  • 目录功能 (4h)                      │                      │
│  │  • 自动保存 (3h)                      │                      │
│  │  • 导出功能 (3h)                      │                      │
│  └───────────────────────────────────────┘                      │
│         │                                                        │
│         ▼                                                        │
│  ┌───────────────────────────────────────┐                      │
│  │ 阶段五: 打包发布 (12h)               │                      │
│  │  • 图标资源 (2h)                      │                      │
│  │  • 打包配置 (2h)                      │                      │
│  │  • 构建测试 (4h)                      │                      │
│  │  • 发布准备 (4h)                      │                      │
│  └───────────────────────────────────────┘                      │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 风险与缓解

| 风险 | 影响 | 概率 | 缓解措施 |
|------|------|------|----------|
| Tauri API 变化 | 中 | 低 | 锁定版本 1.6.x，关注更新日志 |
| Rust Core 未完成 | 高 | 中 | 使用前端降级方案 (react-markdown) |
| 跨平台兼容性问题 | 中 | 中 | 提前在各目标平台测试 |
| 打包签名问题 | 中 | 低 | 提前准备证书，测试签名流程 |
| 性能问题 (大文件) | 中 | 中 | 实现虚拟滚动，限制渲染范围 |
| WebView2 缺失 (Windows) | 高 | 中 | 打包时嵌入 WebView2 Runtime |
| macOS 代码签名 | 高 | 低 | 准备 Apple Developer 账号 |

---

## 参考资料

### 官方文档

- [Tauri 官方文档](https://tauri.app/)
- [React 文档](https://react.dev/)
- [Vite 文档](https://vitejs.dev/)
- [Rust 文档](https://doc.rust-lang.org/)
- [react-markdown](https://github.com/remarkjs/react-markdown)
- [react-syntax-highlighter](https://github.com/react-syntax-highlighter/react-syntax-highlighter)

### 项目文档

- [主路线图](../roadmap/roadmap.md)
- [架构设计](../architecture/README.md)
- [缺失功能分析](../guides/missing-features.md)
- [Android 构建指南](../guides/android-build-setup.md)
- [Desktop 快速开始](../guides/desktop-quickstart.md)

---

## 开发环境要求

### 通用
- Node.js 18+
- npm 9+
- Git

### Windows
- Windows 10+
- Visual Studio Build Tools (或 Visual Studio 2019+)
- WebView2 Runtime (通常已预装)
- Rust 1.70+

### macOS
- macOS 10.15+
- Xcode Command Line Tools
- Rust 1.70+

### Linux
- 任何现代发行版
- WebView(GTK) 开发包
- Rust 1.70+

### 安装开发依赖

```bash
# 安装 Rust
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh

# 安装 Node.js (Windows)
# 从 https://nodejs.org/ 下载安装

# 安装 Node.js (macOS)
brew install node

# 安装 Node.js (Linux)
sudo apt install nodejs npm  # Ubuntu/Debian
```

---

*文档生成时间: 2026-08-04*
*版本: 2.0*
*预计完成时间: 约 2.5 周 (90 工作小时)*
