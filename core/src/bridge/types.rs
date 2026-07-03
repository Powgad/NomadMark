// =============================================================================
// Shared Types for FFI (C ABI Compatible)
// =============================================================================
//
// All types in this module MUST be #[repr(C)] for compatibility with:
// - Kotlin (JNI)
// - Swift (C-interop)
// - Tauri (direct Rust call, no FFI needed)
//
// Critical Rules:
// 1. Use #[repr(C)] on all exported structs
// 2. Use only C-compatible types (i32, u32, f32, pointers)
// 3. String handling: use *const c_char + length, not Rust String
// 4. Array handling: use *const T + length
// =============================================================================


// Screen constants for Supernote A6 X2 Nomad
pub const SCREEN_WIDTH: u16 = 1404;
pub const SCREEN_HEIGHT: u16 = 1872;
pub const DPI: f32 = 300.0;

// =============================================================================
// Quantized Coordinate System (Memory Optimized)
// =============================================================================

/// Quantized rectangle using u16 coordinates.
///
/// Supernote screen is 1404x1872, which fits in u16 (max 65535).
/// This reduces memory usage compared to f32 (4 bytes -> 2 bytes per coordinate).
#[repr(C)]
#[derive(Clone, Copy, Debug, Default, PartialEq)]
pub struct QuantizedRect {
    pub x: u16,
    pub y: u16,
    pub width: u16,
    pub height: u16,
}

impl QuantizedRect {
    /// Convert from float coordinates
    #[inline]
    pub fn from_float(x: f32, y: f32, width: f32, height: f32) -> Self {
        Self {
            x: x.round().min(u16::MAX as f32) as u16,
            y: y.round().min(u16::MAX as f32) as u16,
            width: width.round().max(0.0).min(u16::MAX as f32) as u16,
            height: height.round().max(0.0).min(u16::MAX as f32) as u16,
        }
    }

    /// Check if this rectangle intersects with another
    #[inline]
    pub fn intersects(&self, other: &QuantizedRect) -> bool {
        self.x < other.x + other.width
            && self.x + self.width > other.x
            && self.y < other.y + other.height
            && self.y + self.height > other.y
    }

    /// Union with another rectangle (bounding box)
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

    /// Area in pixels
    #[inline]
    pub fn area(&self) -> u32 {
        (self.width as u32) * (self.height as u32)
    }
}

// =============================================================================
// Color (RGBA)
// =============================================================================

/// 32-bit RGBA color (0-255 per channel)
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
// Font Specification
// =============================================================================

/// Font identifier (matches font cache keys)
#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub enum FontFamily {
    Sans,
    Serif,
    Mono,
    CJK,
}

/// Font specification for rendering
#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct FontSpec {
    pub family: FontFamily,
    pub size_pt: f32,
    pub bold: bool,
    pub italic: bool,
}

// =============================================================================
// Document Metadata
// =============================================================================

/// Table of contents entry
#[repr(C)]
#[derive(Clone, Debug)]
pub struct TocEntry {
    /// Heading level (1-6)
    pub level: u8,
    /// Byte offset in source file
    pub byte_offset: usize,
    /// Line number (0-based)
    pub line_number: usize,
    /// Title text (UTF-8)
    pub title_len: usize,
    pub title_ptr: *const u8,
}

// Safety: This type is only passed across FFI boundary with careful lifetime management
unsafe impl Send for TocEntry {}
unsafe impl Sync for TocEntry {}

/// Document metadata
#[repr(C)]
#[derive(Clone, Debug)]
pub struct DocumentMetadata {
    /// Total character count
    pub total_chars: usize,
    /// Total line count
    pub total_lines: usize,
    /// Number of TOC entries
    pub toc_count: usize,
    /// Pointer to TOC array
    pub toc_ptr: *const TocEntry,
    /// Last modification offset (for incremental parsing)
    pub last_modified_offset: usize,
}

// =============================================================================
// Render Commands (Platform Agnostic)
// =============================================================================

/// Render command type
#[repr(C)]
#[derive(Clone, Copy)]
pub enum RenderCommandType {
    /// Draw text at position
    DrawText = 0,
    /// Fill rectangle (backgrounds, highlights)
    FillRect = 1,
    /// Draw line (borders, underlines)
    DrawLine = 2,
    /// Draw image
    DrawImage = 3,
}

/// Single render command (opaque pointer)
///
/// The actual data is stored in native memory to avoid serialization overhead.
/// Accessors provided via FFI functions.
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
// Search Result
// =============================================================================

/// Search result entry
#[repr(C)]
#[derive(Clone, Debug)]
pub struct SearchResult {
    /// Match start position (byte offset)
    pub start: usize,
    /// Match end position (byte offset)
    pub end: usize,
    /// Line number (0-based)
    pub line_number: usize,
}

// =============================================================================
// Memory Management Helpers
// =============================================================================

/// Wrapper for FFI-returned strings
///
/// MUST be freed with md_free_string
#[repr(C)]
pub struct FfiString {
    pub ptr: *const u8,
    pub len: usize,
}

/// Wrapper for FFI-returned arrays
///
/// MUST be freed with md_free
#[repr(C)]
pub struct FfiArray<T> {
    pub ptr: *const T,
    pub len: usize,
}

// Null pointer constant
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
