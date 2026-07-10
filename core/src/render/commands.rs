// =============================================================================
// 渲染命令（平台无关）
// =============================================================================
//
// 这些命令由 Layouter 生成，由平台特定的渲染器
// （Android Canvas、iOS Core Graphics、Web Canvas2D）使用。
// =============================================================================

use crate::bridge::types::{Color, FontSpec, QuantizedRect};

// -----------------------------------------------------------------------------
// 渲染命令类型
// -----------------------------------------------------------------------------

/// 渲染命令类型区分符
#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum RenderCommandType {
    DrawText = 0,
    FillRect = 1,
    DrawLine = 2,
    DrawImage = 3,
}

/// 单个渲染命令
///
/// 使用 #[repr(C)] 以实现 FFI 兼容性。
/// 命令存储在连续缓冲区中以提高缓存效率。
#[repr(C)]
#[derive(Clone, Copy)]
pub struct RenderCommand {
    /// 命令类型
    pub cmd_type: RenderCommandType,
    /// 位置和尺寸
    pub x: f32,
    pub y: f32,
    pub width: f32,
    pub height: f32,
    /// 颜色（用于所有命令类型）
    pub color: Color,
    /// 联合数据（根据 cmd_type 解释）
    pub data: RenderCommandData,
}

/// 命令特定数据（24 字节 = 3 x u64）
#[repr(C)]
#[derive(Clone, Copy)]
pub union RenderCommandData {
    /// 用于 DrawText：文本指针、长度、字体规格
    pub text: TextData,
    /// 用于 DrawLine：线条数据
    pub line: LineData,
    /// 用于 DrawImage：图像数据
    pub image: ImageData,
    /// 用于其他用途的原始字节
    pub raw: [u8; 24],
}

unsafe impl Send for RenderCommandData {}
unsafe impl Sync for RenderCommandData {}

// -----------------------------------------------------------------------------
// 文本命令数据
// -----------------------------------------------------------------------------

#[repr(C)]
#[derive(Clone, Copy)]
pub struct TextData {
    /// UTF-8 文本指针（由渲染器拥有）
    pub text_ptr: u64,
    /// 文本字节长度
    pub text_len: u32,
    /// 字体家族
    pub font_family: u8,
    /// 字体大小（点）
    pub font_size_pt: u8,
    /// 字体粗体
    pub font_bold: u8,
    /// 字体斜体
    pub font_italic: u8,
    /// 填充（对齐到 24 字节）
    pub _pad: [u8; 8],
}

impl From<&crate::bridge::types::FontSpec> for TextData {
    fn from(spec: &crate::bridge::types::FontSpec) -> Self {
        Self {
            text_ptr: 0,
            text_len: 0,
            font_family: spec.family as u8,
            font_size_pt: spec.size_pt as u8,
            font_bold: spec.bold as u8,
            font_italic: spec.italic as u8,
            _pad: [0; 8],
        }
    }
}

// -----------------------------------------------------------------------------
// 线条命令数据
// -----------------------------------------------------------------------------

#[repr(C)]
#[derive(Clone, Copy)]
pub struct LineData {
    /// 线条宽度（像素）
    pub width: f32,
    /// 起点坐标（x1, y1）
    pub x1: f32,
    pub y1: f32,
    /// 终点坐标（x2, y2）
    pub x2: f32,
    pub y2: f32,
}

// -----------------------------------------------------------------------------
// 图像命令数据
// -----------------------------------------------------------------------------

#[repr(C)]
#[derive(Clone, Copy)]
pub struct ImageData {
    /// 图像数据指针
    pub data_ptr: u64,
    /// 数据长度
    pub data_len: u32,
    pub _pad: u32,
    /// 图像格式（0 = PNG，1 = JPEG，2 = RGBA）
    pub format: u32,
}

// -----------------------------------------------------------------------------
// 渲染结果（包含脏矩形的捆绑包）
// -----------------------------------------------------------------------------

/// 渲染操作的结果
///
/// 包含渲染命令和用于局部刷新的脏矩形。
#[repr(C)]
#[derive(Clone)]
pub struct RenderResult {
    /// 渲染命令
    pub commands: Vec<RenderCommand>,
    /// 脏矩形（用于局部刷新优化）
    pub dirty_rects: Vec<QuantizedRect>,
    /// 文档总高度（用于滚动条计算）
    pub total_height: f32,
}

impl RenderResult {
    pub fn new() -> Self {
        Self {
            commands: Vec::new(),
            dirty_rects: Vec::new(),
            total_height: 0.0,
        }
    }

    /// 添加命令并将其区域标记为脏
    pub fn push(&mut self, cmd: RenderCommand) {
        let rect = QuantizedRect::from_float(cmd.x, cmd.y, cmd.width, cmd.height);
        self.dirty_rects.push(rect);
        self.commands.push(cmd);
    }

    /// 合并脏矩形以减少刷新调用
    pub fn merge_dirty_rects(&mut self) {
        if self.dirty_rects.len() <= 1 {
            return;
        }

        let mut merged = Vec::new();
        let mut current = self.dirty_rects[0];

        for &rect in &self.dirty_rects[1..] {
            if current.intersects(&rect) || rect.adjacent_to(&current) {
                current = current.union(&rect);
            } else {
                merged.push(current);
                current = rect;
            }
        }
        merged.push(current);

        self.dirty_rects = merged;
    }

    /// 计算总脏区域（用于决定全局刷新还是局部刷新）
    pub fn total_dirty_area(&self) -> u32 {
        self.dirty_rects.iter().map(|r| r.area()).sum()
    }
}

impl QuantizedRect {
    /// 检查两个矩形是否相邻（接触或相距 4 像素以内）
    fn adjacent_to(&self, other: &Self) -> bool {
        const GAP: i16 = 4;
        let dx = (self.x as i16 - other.x as i16).abs();
        let dy = (self.y as i16 - other.y as i16).abs();
        dx <= GAP || dy <= GAP
    }
}

// -----------------------------------------------------------------------------
// 命令构建器
// -----------------------------------------------------------------------------

impl RenderCommand {
    /// 创建 DrawText 命令
    pub fn draw_text(x: f32, y: f32, text: &str, font: FontSpec, color: Color) -> Self {
        // 分配文本内容到堆上，并通过 FFI 传递指针
        // 注意：调用者负责释放此内存
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

        let text_data = {
            let mut td = TextData::from(&font);
            td.text_ptr = text_ptr;
            td.text_len = text_len as u32;
            td
        };

        Self {
            cmd_type: RenderCommandType::DrawText,
            x,
            y,
            width: 0.0,  // 将由 layout器 计算
            height: 0.0,
            color,
            data: RenderCommandData {
                text: text_data,
            },
        }
    }

    /// 创建 FillRect 命令
    pub fn fill_rect(x: f32, y: f32, width: f32, height: f32, color: Color) -> Self {
        Self {
            cmd_type: RenderCommandType::FillRect,
            x, y, width, height,
            color,
            data: RenderCommandData { raw: [0; 24] },
        }
    }

    /// 创建 DrawLine 命令
    pub fn draw_line(x1: f32, y1: f32, x2: f32, y2: f32, width: f32, color: Color) -> Self {
        Self {
            cmd_type: RenderCommandType::DrawLine,
            x: x1.min(x2),
            y: y1.min(y2),
            width: (x2 - x1).abs() + width,
            height: (y2 - y1).abs() + width,
            color,
            data: RenderCommandData {
                line: LineData { x1, y1, x2, y2, width },
            },
        }
    }

    /// 创建 DrawImage 命令
    pub fn draw_image(x: f32, y: f32, width: f32, height: f32, data_ptr: u64, data_len: u32, format: u32) -> Self {
        Self {
            cmd_type: RenderCommandType::DrawImage,
            x, y, width, height,
            color: Color::WHITE,
            data: RenderCommandData {
                image: ImageData { data_ptr, data_len, _pad: 0, format },
            },
        }
    }
}

// -----------------------------------------------------------------------------
// FFI 辅助函数
// ----------------------------------------------------------------------------/

/// 将命令转换为 FFI 兼容数组
///
/// 返回 (ptr, len) 元组用于跨 FFI 边界传递。
pub fn commands_to_ffi(commands: &mut Vec<RenderCommand>) -> (*const RenderCommand, usize) {
    (commands.as_ptr(), commands.len())
}

/// 将脏矩形转换为 FFI 兼容的扁平数组
///
/// 返回 [x, y, w, h, x, y, w, h, ...] 格式的 [i32; 4 * n]
pub fn dirty_rects_to_ffi(rects: &[QuantizedRect]) -> Vec<i32> {
    let mut result = Vec::with_capacity(rects.len() * 4);
    for rect in rects {
        result.push(rect.x as i32);
        result.push(rect.y as i32);
        result.push(rect.width as i32);
        result.push(rect.height as i32);
    }
    result
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::bridge::types::FontFamily;

    #[test]
    fn test_render_command_draw_text() {
        let cmd = RenderCommand::draw_text(
            10.0, 20.0, "Hello", FontSpec {
                family: FontFamily::Sans,
                size_pt: 14.0,
                bold: false,
                italic: false,
            },
            Color::BLACK
        );

        assert_eq!(cmd.cmd_type, RenderCommandType::DrawText);
        assert_eq!(cmd.x, 10.0);
        assert_eq!(cmd.y, 20.0);
    }

    #[test]
    fn test_render_command_fill_rect() {
        let cmd = RenderCommand::fill_rect(5.0, 10.0, 100.0, 200.0, Color::WHITE);

        assert_eq!(cmd.cmd_type, RenderCommandType::FillRect);
        assert_eq!(cmd.width, 100.0);
        assert_eq!(cmd.height, 200.0);
    }

    #[test]
    fn test_dirty_rects_to_ffi() {
        let rects = vec![
            QuantizedRect { x: 10, y: 20, width: 100, height: 200 },
            QuantizedRect { x: 5, y: 15, width: 50, height: 100 },
        ];

        let ffi = dirty_rects_to_ffi(&rects);
        assert_eq!(ffi.len(), 8);
        assert_eq!(ffi[0], 10);
        assert_eq!(ffi[1], 20);
        assert_eq!(ffi[2], 100);
        assert_eq!(ffi[3], 200);
    }
}
