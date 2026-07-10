// =============================================================================
// 渲染模块
// =============================================================================

pub mod commands;

pub use commands::{
    RenderCommand, RenderCommandType, RenderResult, RenderCommandData,
    TextData, LineData, ImageData,
    commands_to_ffi, dirty_rects_to_ffi,
};
