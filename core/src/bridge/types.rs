// =============================================================================
// FFI 共享类型 (C ABI 兼容)
// =============================================================================
//
// 本模块中的所有类型必须是 #[repr(C)] 以兼容：
// - Kotlin (JNI)
// - Swift (C-interop)
// - Tauri (直接 Rust 调用，无需 FFI)
//
// 关键规则：
// 1. 在所有导出的结构体上使用 #[repr(C)]
// 2. 只使用 C 兼容类型（i32, u32, f32, 指针）
// 3. 字符串处理：使用 *const c_char + length，而非 Rust String
// 4. 数组处理：使用 *const T + length
// =============================================================================


// Supernote A6 X2 Nomad 屏幕常量
pub const SCREEN_WIDTH: u16 = 1404;
pub const SCREEN_HEIGHT: u16 = 1872;
pub const DPI: f32 = 300.0;

// =============================================================================
// 量化坐标系统（内存优化）
// =============================================================================

/// 使用 u16 坐标的量化矩形。
///
/// Supernote 屏幕为 1404x1872，适合 u16（最大 65535）。
/// 与 f32 相比，这减少了内存使用（4 字节 -> 每坐标 2 字节）。
#[repr(C)]
#[derive(Clone, Copy, Debug, Default, PartialEq)]
pub struct QuantizedRect {
    pub x: u16,
    pub y: u16,
    pub width: u16,
    pub height: u16,
}

impl QuantizedRect {
    /// 从浮点坐标转换
    #[inline]
    pub fn from_float(x: f32, y: f32, width: f32, height: f32) -> Self {
        Self {
            x: x.round().min(u16::MAX as f32) as u16,
            y: y.round().min(u16::MAX as f32) as u16,
            width: width.round().max(0.0).min(u16::MAX as f32) as u16,
            height: height.round().max(0.0).min(u16::MAX as f32) as u16,
        }
    }

    /// 检查此矩形是否与另一个矩形相交
    #[inline]
    pub fn intersects(&self, other: &QuantizedRect) -> bool {
        self.x < other.x + other.width
            && self.x + self.width > other.x
            && self.y < other.y + other.height
            && self.y + self.height > other.y
    }

    /// 与另一个矩形的并集（边界框）
    pub fn union(&self, other: &QuantizedRect) -> QuantizedRect {
        let x1 = self.x.min(other.x);
        let y1 = self.y.min(other.y);
        let x2 = (self.x + self.width).max(other.x + other.width);
        let y2 = (self.y + self.height).max(other.y + other.height);

        QuantizedRect {
            x: x1,
            y: y1,
            width: x2 - x1,
            height: y2 - y1,
        }
    }

    /// 面积（像素）
    #[inline]
    pub fn area(&self) -> u32 {
        (self.width as u32) * (self.height as u32)
    }
}

// =============================================================================
// 颜色 (RGBA)
// =============================================================================

/// 32 位 RGBA 颜色（每通道 0-255）
#[repr(C)]
#[derive(Clone, Copy, Debug, Default, PartialEq)]
pub struct Color {
    pub r: u8,
    pub g: u8,
    pub b: u8,
    pub a: u8,
}

impl Color {
    pub const BLACK: Self = Color { r: 0, g: 0, b: 0, a: 255 };
    pub const WHITE: Self = Color { r: 255, g: 255, b: 255, a: 255 };
    pub const GRAY: Self = Color { r: 128, g: 128, b: 128, a: 255 };

    pub fn rgb(r: u8, g: u8, b: u8) -> Self {
        Self { r, g, b, a: 255 }
    }
}

// =============================================================================
// 字体规格
// =============================================================================

/// 字体标识符（匹配字体缓存键）
#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub enum FontFamily {
    Sans,
    Serif,
    Mono,
    CJK,
}

/// 用于渲染的字体规格
#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct FontSpec {
    pub family: FontFamily,
    pub size_pt: f32,
    pub bold: bool,
    pub italic: bool,
}

// =============================================================================
// 文档元数据
// =============================================================================

/// 目录条目
#[repr(C)]
#[derive(Clone, Debug)]
pub struct TocEntry {
    /// 标题级别（1-6）
    pub level: u8,
    /// 源文件中的字节偏移
    pub byte_offset: usize,
    /// 行号（从 0 开始）
    pub line_number: usize,
    /// 标题文本（UTF-8）
    pub title_len: usize,
    pub title_ptr: *const u8,
}

// 安全性：此类型仅在仔细管理生命周期的情况下通过 FFI 边界传递
unsafe impl Send for TocEntry {}
unsafe impl Sync for TocEntry {}

/// 文档元数据
#[repr(C)]
#[derive(Clone, Debug)]
pub struct DocumentMetadata {
    /// 总字符数
    pub total_chars: usize,
    /// 总行数
    pub total_lines: usize,
    /// 目录条目数
    pub toc_count: usize,
    /// 指向目录数组的指针
    pub toc_ptr: *const TocEntry,
    /// 最后修改偏移（用于增量解析）
    pub last_modified_offset: usize,
}

// =============================================================================
// 渲染命令（平台无关）
// =============================================================================

/// 渲染命令类型
#[repr(C)]
#[derive(Clone, Copy)]
pub enum RenderCommandType {
    /// 在位置绘制文本
    DrawText = 0,
    /// 填充矩形（背景、高亮）
    FillRect = 1,
    /// 绘制线条（边框、下划线）
    DrawLine = 2,
    /// 绘制图像
    DrawImage = 3,
}

/// 单个渲染命令（不透明指针）
///
/// 实际数据存储在本机内存中，避免序列化开销。
/// 通过 FFI 函数提供访问器。
#[repr(C)]
#[derive(Clone, Copy)]
pub struct RenderCommand {
    pub cmd_type: RenderCommandType,
    pub x: f32,
    pub y: f32,
    pub width: f32,
    pub height: f32,
    pub color: Color,
    pub data_len: usize,
    pub data_ptr: *const u8,
}

// =============================================================================
// 搜索结果
// =============================================================================

/// 搜索结果条目
#[repr(C)]
#[derive(Clone, Debug)]
pub struct SearchResult {
    /// 匹配起始位置（字节偏移）
    pub start: usize,
    /// 匹配结束位置（字节偏移）
    pub end: usize,
    /// 行号（从 0 开始）
    pub line_number: usize,
}

// =============================================================================
// 内存管理辅助
// =============================================================================

/// FFI 返回字符串的包装器
///
/// 必须使用 md_free_string 释放
#[repr(C)]
pub struct FfiString {
    pub ptr: *const u8,
    pub len: usize,
}

/// FFI 返回数组的包装器
///
/// 必须使用 md_free 释放
#[repr(C)]
pub struct FfiArray<T> {
    pub ptr: *const T,
    pub len: usize,
}

// 空指针常量
pub const NULL_PTR: *const u8 = std::ptr::null();

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_quantized_rect() {
        let rect = QuantizedRect::from_float(10.5, 20.3, 100.7, 200.9);
        assert_eq!(rect.x, 11);
        assert_eq!(rect.y, 20);
        assert_eq!(rect.width, 101);
        assert_eq!(rect.height, 201);
    }

    #[test]
    fn test_rect_intersection() {
        let a = QuantizedRect { x: 0, y: 0, width: 100, height: 100 };
        let b = QuantizedRect { x: 50, y: 50, width: 100, height: 100 };
        assert!(a.intersects(&b));

        let c = QuantizedRect { x: 150, y: 150, width: 100, height: 100 };
        assert!(!a.intersects(&c));
    }

    #[test]
    fn test_rect_union() {
        let a = QuantizedRect { x: 0, y: 0, width: 100, height: 100 };
        let b = QuantizedRect { x: 50, y: 50, width: 100, height: 100 };
        let union = a.union(&b);
        assert_eq!(union, QuantizedRect { x: 0, y: 0, width: 150, height: 150 });
    }

    #[test]
    fn test_color() {
        assert_eq!(Color::BLACK, Color::rgb(0, 0, 0));
        assert_eq!(Color::WHITE, Color::rgb(255, 255, 255));
    }
}
