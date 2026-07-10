# Rust Core Rendering Troubleshooting

## 问题 1: Rust Core 渲染空白页

### 症状
```
Rust Core 渲染后显示空白页，没有任何文本内容
```

### 原因
Rust 端 `RenderCommand::draw_text` 函数中，`text_ptr` 被显式设置为 0，导致 Kotlin 端无法读取任何文本内容。

### 详细分析

#### Rust 端问题代码（修复前）：
```rust
pub fn draw_text(x: f32, y: f32, text: &str, font: FontSpec, color: Color) -> Self {
    Self {
        cmd_type: RenderCommandType::DrawText,
        x,
        y,
        width: 0.0,
        height: 0.0,
        color,
        data: RenderCommandData {
            text: {
                let mut text_data = TextData::from(&font);
                text_data.text_ptr = 0;  // ❌ 问题在这里！总是设置为 0
                text_data.text_len = text.len() as u32;
                text_data
            },
        },
    }
}
```

#### Kotlin 端处理：
```kotlin
val textPtr = this.long   // 读取 text_ptr（总是 0）
val textLen = this.int    // 读取 text_len
if (textPtr != 0L && textLen > 0) {
    val textBytes = MarkdownCore.nativeReadBytes(textPtr, textLen)
    // 从不执行，因为 textPtr 总是 0
}
```

### 解决方法

#### 1. 修改 Rust 端 `RenderCommand::draw_text` 函数
```rust
pub fn draw_text(x: f32, y: f32, text: &str, font: FontSpec, color: Color) -> Self {
    // 分配文本内容到堆上，并通过 FFI 传递指针
    let text_bytes = text.as_bytes();
    let text_len = text_bytes.len();

    // 分配内存并复制文本内容
    let text_ptr = if text_len > 0 {
        let layout = std::alloc::Layout::array::<u8>(text_len).unwrap();
        unsafe {
            let ptr = std::alloc::alloc(layout);
            if !ptr.is_null() {
                std::ptr::copy_nonoverlapping(text_bytes.as_ptr(), ptr, text_len);
                ptr as u64
            } else {
                0
            }
        }
    } else {
        0
    };

    Self {
        cmd_type: RenderCommandType::DrawText,
        x,
        y,
        width: 0.0,
        height: 0.0,
        color,
        data: RenderCommandData {
            text: {
                let mut text_data = TextData::from(&font);
                text_data.text_ptr = text_ptr;  // ✅ 设置实际指针
                text_data.text_len = text_len as u32;
                text_data
            },
        },
    }
}
```

#### 2. 更新 `md_free_commands` 函数
需要释放分配的文本内容：
```rust
pub extern "C" fn md_free_commands(ptr: *mut RenderCommand, len: usize) {
    if ptr.is_null() || len == 0 {
        return;
    }

    unsafe {
        // 首先释放每个命令中的文本内容
        let commands = std::slice::from_raw_parts_mut(ptr, len);
        for cmd in commands.iter() {
            if cmd.cmd_type == render::commands::RenderCommandType::DrawText {
                let text_data = cmd.data.text;
                if text_data.text_ptr != 0 && text_data.text_len > 0 {
                    // 释放文本内容
                    let layout = std::alloc::Layout::array::<u8>(text_data.text_len as usize).unwrap();
                    std::alloc::dealloc(text_data.text_ptr as *mut u8, layout);
                }
            }
        }

        // 然后释放命令数组本身
        let _ = Box::from_raw(commands);
    }
}
```

#### 3. 修正 Kotlin 端字段读取顺序
```kotlin
when (cmdType) {
    0 -> {  // CMD_DRAW_TEXT
        val textPtr = this.long   // text_ptr @ offset 24-31
        val textLen = this.int    // text_len @ offset 32-35
        // ...
    }
}
```

### 验证方法
1. 重新编译 Rust Core：
   ```bash
   cd core
   cargo ndk -t arm64-v8a -o "../platforms/android/app/src/main/jniLibs" build --release
   ```
2. 编译并运行 APK
3. 打开 Markdown 文件，确认文本正确显示
4. 检查日志确认没有 "Failed to read text" 错误

### 预防措施
1. **内存管理**：在 FFI 边界分配内存时，必须确保对应的释放函数
2. **文档化**：在代码中注释内存布局和生命周期
3. **测试**：添加测试验证文本指针的正确性

---

## 问题 2: "Rust Core 渲染异常：null"

### 症状
```
Rust Core 渲染异常: null
```

### 原因
Kotlin 端的 `RenderCommand` 结构大小与 Rust 端不匹配，导致内存读取越界。

### 详细分析

#### Rust 端结构（64位系统）：
```rust
pub struct RenderCommand {
    pub cmd_type: RenderCommandType,  // 4 bytes (i32 enum)
    pub x: f32,                        // 4 bytes
    pub y: f32,                        // 4 bytes
    pub width: f32,                    // 4 bytes
    pub height: f32,                   // 4 bytes
    pub color: Color,                  // 4 bytes (RGBA)
    pub data_len: usize,               // 8 bytes
    pub data_ptr: *const u8,           // 8 bytes
}
// 总计：40 bytes
```

#### Kotlin 端错误（修复前）：
```kotlin
val commandSize = 48  // ❌ 错误！应该是 40
val dataSize = commandsCount * commandSize  // 读取超出有效内存范围
```

### 解决方法

#### 1. 修正命令大小
将 `commandSize` 从 48 改为 40：
```kotlin
val commandSize = 40  // 每个 RenderCommand 40 字节（64位系统）
```

#### 2. 修正字段偏移
`data_len` 和 `data_ptr` 都是 8 字节（64位系统）：
```kotlin
val dataLen = this.long  // data_len: usize (8 bytes)
val dataPtr = this.long  // data_ptr: *const u8 (8 bytes)
```

#### 3. 添加边界检查
```kotlin
if (dataSize <= 0 || commandsPtr == 0L) {
    return SpannableString.valueOf("")
}

val bytes = MarkdownCore.nativeReadBytes(commandsPtr, dataSize)
if (bytes == null || bytes.isEmpty()) {
    Log.e("MarkdownEditorActivity", "nativeReadBytes returned null or empty")
    return SpannableString.valueOf("")
}
```

### 验证方法
1. 编译并运行应用
2. 打开包含中文的 Markdown 文件
3. 检查日志确认没有 "nativeReadBytes returned null" 错误
4. 确认文本正确显示

### 预防措施
1. **保持 FFI 结构同步**：修改 Rust `RenderCommand` 结构时，必须同步更新 Kotlin 解析代码
2. **使用常量定义大小**：
   ```kotlin
   companion object {
       private const val RENDER_COMMAND_SIZE = 40
   }
   ```
3. **添加断言检查**：在调试模式下验证结构大小
4. **文档化内存布局**：在代码中注释每个字段的大小和偏移

### 跨平台注意事项
- **32位系统**：`usize` 和指针大小为 4 字节，结构总大小为 32 字节
- **64位系统**：`usize` 和指针大小为 8 字节，结构总大小为 40 字节

当前实现仅针对 64 位 Android 系统。

---

## 问题 2: nativeReadBytes 返回 null

### 症状
```
java.lang.NullPointerException: Attempt to invoke virtual method 'int java.nio.ByteBuffer.capacity()' on a null object reference
```

### 可能原因
1. `ptr` 参数为 0 但 `size > 0`
2. `size` 参数为负数
3. 指针指向的内存已释放
4. 内存分配失败

### 解决方法
在 Kotlin 端添加边界检查：
```kotlin
if (dataSize <= 0 || commandsPtr == 0L) {
    return SpannableString.valueOf("")
}

try {
    val bytes = MarkdownCore.nativeReadBytes(commandsPtr, dataSize)
    if (bytes == null || bytes.isEmpty()) {
        Log.e(TAG, "nativeReadBytes returned null or empty")
        return SpannableString.valueOf("")
    }
    // 处理 bytes...
} catch (e: Exception) {
    Log.e(TAG, "Error reading bytes", e)
    return SpannableString.valueOf("")
}
```

---

## 调试技巧

### 1. 添加详细日志
```kotlin
Log.d("MarkdownEditorActivity", "Rust Core rendered: commandsPtr=$commandsPtr, count=$commandsCount, dataSize=$dataSize")
```

### 2. 验证命令内容
```kotlin
java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.nativeOrder()).apply {
    repeat(commandsCount) {
        val cmdType = this.int
        Log.d("MarkdownEditorActivity", "Command $it: type=$cmdType")
        // ...
    }
}
```

### 3. 检查 Rust 端输出
在 `jni.rs` 中添加日志：
```rust
log::info!("nativeLoadRange: handle={}, start_line={}, count={}, result={}", 
           handle, start_line, count, result);
```

---

## 相关文件
- Kotlin: [MarkdownEditorActivity.kt](../../platforms/android/app/src/main/java/com/editor/nomadmark/MarkdownEditorActivity.kt)
- Kotlin: [MarkdownCore.kt](../../platforms/android/app/src/main/java/com/editor/nomadmark/MarkdownCore.kt)
- Rust: [bridge/jni.rs](../../core/src/bridge/jni.rs)
- Rust: [bridge/types.rs](../../core/src/bridge/types.rs)