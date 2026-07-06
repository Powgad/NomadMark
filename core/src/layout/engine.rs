// =============================================================================
// 布局引擎（300 DPI 优化）
// =============================================================================
//
// 职责：
// 1. 计算 Markdown 块的布局
// 2. 生成渲染命令
// 3. 管理字形缓存（L1: RAM，L2: mmap）
// 4. 跟踪脏矩形
//
// 性能目标：
// - 单屏渲染：<50ms
// - 布局缓存命中：每块 <1ms
// =============================================================================

use crate::bridge::types::{QuantizedRect, Color, FontSpec, FontFamily, SCREEN_WIDTH, SCREEN_HEIGHT};
use crate::parser::ast::{BlockNode, InlineNode};
use crate::render::commands::{RenderCommand, RenderResult};
use lru::LruCache;
use std::collections::HashMap;
use std::num::NonZeroUsize;

// -----------------------------------------------------------------------------
// 布局配置
// -----------------------------------------------------------------------------

/// 布局引擎配置
#[derive(Clone, Copy)]
pub struct LayoutConfig {
    /// 设备 DPI（Supernote 默认为 300）
    pub dpi: f32,
    /// 屏幕宽度（像素）
    pub screen_width: u16,
    /// 屏幕高度（像素）
    pub screen_height: u16,
    /// 左边距（像素）
    pub margin_left: f32,
    /// 右边距（像素）
    pub margin_right: f32,
    /// 上边距（像素）
    pub margin_top: f32,
    /// 下边距（像素）
    pub margin_bottom: f32,
    /// 行间距倍数
    pub line_spacing: f32,
    /// 段落间距
    pub paragraph_spacing: f32,
}

impl LayoutConfig {
    /// Supernote A6 X2 Nomad 的默认配置
    pub fn for_supernote() -> Self {
        Self {
            dpi: 300.0,
            screen_width: SCREEN_WIDTH,
            screen_height: SCREEN_HEIGHT,
            margin_left: 40.0,
            margin_right: 40.0,
            margin_top: 40.0,
            margin_bottom: 40.0,
            line_spacing: 1.5,
            paragraph_spacing: 20.0,
        }
    }

    /// 将点转换为像素（基于当前 DPI）
    #[inline]
    pub fn pt_to_px(&self, pt: f32) -> f32 {
        pt * (self.dpi / 72.0)
    }

    /// 可用内容宽度
    #[inline]
    pub fn content_width(&self) -> f32 {
        (self.screen_width as f32) - self.margin_left - self.margin_right
    }
}

// -----------------------------------------------------------------------------
// 字体指标缓存
// -----------------------------------------------------------------------------

/// 字体指标（按字体/大小组合缓存）
#[derive(Clone, Copy, Debug)]
pub struct FontMetrics {
    /// 从基线到顶部的高度
    pub ascent: f32,
    /// 从基线到底部的高度
    pub descent: f32,
    /// 行高（ascent + descent + leading）
    pub line_height: f32,
    /// 平均字符宽度
    pub avg_char_width: f32,
}

/// 字体规格缓存键
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub struct FontKey {
    pub family: FontFamily,
    pub size_pt: u8,
    pub bold: bool,
    pub italic: bool,
}

// -----------------------------------------------------------------------------
// Glyph Cache (Three-tier)
// -----------------------------------------------------------------------------

/// 字形缓存系统
pub struct GlyphCacheSystem {
    /// L1: RAM 缓存（热数据，约 2MB）
    l1_ram: LruCache<GlyphKey, GlyphBitmap>,
    /// L2: 预渲染的字形规格（温数据）
    l2_metrics: HashMap<GlyphKey, GlyphMetrics>,
}

#[derive(Clone, Debug, PartialEq, Eq, Hash)]
pub struct GlyphKey {
    pub char: char,
    pub font: FontKey,
}

#[derive(Clone, Debug)]
pub struct GlyphBitmap {
    pub width: u8,
    pub height: u8,
    pub data: Vec<u8>,  // Grayscale bitmap
}

#[derive(Clone, Copy, Debug)]
pub struct GlyphMetrics {
    pub advance: f32,
    pub bearing_x: f32,
    pub bearing_y: f32,
}

impl GlyphCacheSystem {
    pub fn new() -> Self {
        Self {
            l1_ram: LruCache::new(NonZeroUsize::new(4096).unwrap()),
            l2_metrics: HashMap::new(),
        }
    }

    /// Get glyph bitmap (with L1 caching)
    pub fn get_glyph(&mut self, key: &GlyphKey) -> Option<&GlyphBitmap> {
        self.l1_ram.get(key)
    }

    /// Get glyph metrics (from L2)
    pub fn get_metrics(&self, key: &GlyphKey) -> Option<&GlyphMetrics> {
        self.l2_metrics.get(key)
    }

    /// Preload common CJK characters (call at startup)
    pub fn preload_cjk_common(&mut self) {
        // Top 500 common CJK characters
        const CJK_COMMON: &str = "的一是在不了有和人这中大为上个国我以要他时来用们生到作地于出就分对成会可主发表还年能动同工也能下过子说产种面而方后多定行学法所民得经十三之进着等部度家电力里如水化高自二理起小物现实加量都两体制机当使点从业本去把性好应开它合还因由其些然前外天政四日那社义事平形相全表间样与关各重新线内数正心反你明看原又么利比或但质气第向道命此变条只没结解问意建月公无系军很情者最立代想已通并提直题党程展五果料象员革位入常文总次品式活设及管特件长求老头基资边流路级少图山统接知较将组见计别她手角期根论运农指几九区强放决西被干做必战先回则任取据处队南给色光门即保治北造百规热领七海口东导器压志世金增争济阶油思术极交受联什认六共权收证改清己美再采转更单风切打白教速花带安场身车例真务具万每目至达走积示议声报斗完类八离华名确才科张信马节话米整空元况今据温虫么书";

        let font = FontKey {
            family: FontFamily::CJK,
            size_pt: 14,
            bold: false,
            italic: false,
        };

        // Pre-populate metrics cache
        for ch in CJK_COMMON.chars() {
            let key = GlyphKey { char: ch, font };
            // In real implementation, would rasterize via FreeType
            self.l2_metrics.insert(key, GlyphMetrics {
                advance: font.size_pt as f32,
                bearing_x: 0.0,
                bearing_y: font.size_pt as f32,
            });
        }
    }
}

impl Default for GlyphCacheSystem {
    fn default() -> Self {
        Self::new()
    }
}

// -----------------------------------------------------------------------------
// Layout Engine
// ----------------------------------------------------------------------------/

/// 主布局引擎
pub struct Layouter {
    /// Configuration
    config: LayoutConfig,
    /// Font metrics cache
    font_metrics: LruCache<FontKey, FontMetrics>,
    /// Glyph cache
    glyph_cache: GlyphCacheSystem,
    /// Current cursor position
    cursor_x: f32,
    cursor_y: f32,
    /// Current line height
    current_line_height: f32,
}

impl Layouter {
    /// Create new layouter for Supernote
    pub fn for_supernote() -> Self {
        let mut layouter = Self {
            config: LayoutConfig::for_supernote(),
            font_metrics: LruCache::new(NonZeroUsize::new(1024).unwrap()),
            glyph_cache: GlyphCacheSystem::new(),
            cursor_x: 0.0,
            cursor_y: 0.0,
            current_line_height: 0.0,
        };

        // Preload common CJK glyphs
        layouter.glyph_cache.preload_cjk_common();

        layouter
    }

    /// Reset cursor to start of content area
    fn reset_cursor(&mut self) {
        self.cursor_x = self.config.margin_left;
        self.cursor_y = self.config.margin_top;
        self.current_line_height = self.config.pt_to_px(14.0) * self.config.line_spacing;
    }

    /// Move to next line
    fn new_line(&mut self) {
        self.cursor_x = self.config.margin_left;
        self.cursor_y += self.current_line_height;
    }

    /// Get font metrics (with caching)
    fn get_font_metrics(&mut self, font: FontSpec) -> FontMetrics {
        let key = FontKey {
            family: font.family,
            size_pt: font.size_pt as u8,
            bold: font.bold,
            italic: font.italic,
        };

        if let Some(&metrics) = self.font_metrics.get(&key) {
            return metrics;
        }

        // Compute metrics (simplified - real implementation uses font tables)
        let size_px = self.config.pt_to_px(font.size_pt);
        let metrics = FontMetrics {
            ascent: size_px * 0.8,
            descent: size_px * 0.2,
            line_height: size_px * self.config.line_spacing,
            avg_char_width: size_px * 0.5,
        };

        self.font_metrics.put(key, metrics);
        metrics
    }

    /// Layout a single block node
    pub fn layout_block(&mut self, node: &BlockNode) -> RenderResult {
        let mut result = RenderResult::new();

        match node {
            BlockNode::Heading { level, children } => {
                self.layout_heading(*level, children, &mut result);
            }
            BlockNode::Paragraph { children } => {
                self.layout_paragraph(children, &mut result);
            }
            BlockNode::CodeBlock { language, content } => {
                self.layout_code_block(language, content, &mut result);
            }
            BlockNode::List { ordered, start_number, items } => {
                self.layout_list(*ordered, *start_number, items, &mut result);
            }
            _ => {
                // Placeholder for other block types
            }
        }

        result
    }

    /// Layout heading
    fn layout_heading(&mut self, level: u8, children: &[InlineNode], result: &mut RenderResult) {
        let font_size = match level {
            1 => 24.0,
            2 => 20.0,
            3 => 18.0,
            4 => 16.0,
            _ => 14.0,
        };

        let font = FontSpec {
            family: FontFamily::Sans,
            size_pt: font_size,
            bold: true,
            italic: false,
        };

        let metrics = self.get_font_metrics(font);
        self.current_line_height = metrics.line_height;

        // Draw heading background
        let bg_rect = QuantizedRect::from_float(
            self.config.margin_left,
            self.cursor_y,
            self.config.content_width(),
            self.current_line_height,
        );
        result.push(RenderCommand::fill_rect(
            bg_rect.x as f32,
            bg_rect.y as f32,
            bg_rect.width as f32,
            bg_rect.height as f32,
            Color::WHITE,
        ));

        // Layout inline content
        self.layout_inline(children, font, Color::BLACK, result);

        self.new_line();
        self.cursor_y += self.config.paragraph_spacing;
    }

    /// Layout paragraph
    fn layout_paragraph(&mut self, children: &[InlineNode], result: &mut RenderResult) {
        let font = FontSpec {
            family: FontFamily::Sans,
            size_pt: 14.0,
            bold: false,
            italic: false,
        };

        let metrics = self.get_font_metrics(font);
        self.current_line_height = metrics.line_height;

        self.layout_inline(children, font, Color::BLACK, result);
        self.new_line();
        self.cursor_y += self.config.paragraph_spacing;
    }

    /// Layout inline nodes
    fn layout_inline(&mut self, nodes: &[InlineNode], font: FontSpec, color: Color, result: &mut RenderResult) {
        let metrics = self.get_font_metrics(font);
        let content_width = self.config.content_width();

        for node in nodes {
            match node {
                InlineNode::Text(text) => {
                    // Simple word wrapping
                    let words: Vec<&str> = text.split_whitespace().collect();
                    for (i, word) in words.iter().enumerate() {
                        let word_width = word.len() as f32 * metrics.avg_char_width;

                        // Check if need to wrap
                        if self.cursor_x + word_width > self.config.margin_left + content_width {
                            self.new_line();
                        }

                        // Draw text
                        result.push(RenderCommand::draw_text(
                            self.cursor_x,
                            self.cursor_y + metrics.ascent,
                            word,
                            font,
                            color,
                        ));

                        self.cursor_x += word_width;

                        // Add space between words
                        if i < words.len() - 1 {
                            self.cursor_x += metrics.avg_char_width * 0.3;
                        }
                    }
                }
                InlineNode::Emphasis { children, level } => {
                    let emphasis_font = FontSpec {
                        size_pt: font.size_pt,
                        bold: font.bold,
                        italic: *level >= 1,
                        ..font
                    };
                    self.layout_inline(children, emphasis_font, color, result);
                }
                InlineNode::Strong { children } => {
                    let strong_font = FontSpec {
                        size_pt: font.size_pt,
                        bold: true,
                        italic: font.italic,
                        ..font
                    };
                    self.layout_inline(children, strong_font, color, result);
                }
                InlineNode::Code(code) => {
                    let code_font = FontSpec {
                        family: FontFamily::Mono,
                        size_pt: 13.0,
                        bold: false,
                        italic: false,
                    };

                    // Draw code background
                    let text_width = code.len() as f32 * metrics.avg_char_width;
                    result.push(RenderCommand::fill_rect(
                        self.cursor_x,
                        self.cursor_y,
                        text_width + 8.0,
                        self.current_line_height,
                        Color::rgb(240, 240, 240),
                    ));

                    // Draw code text
                    result.push(RenderCommand::draw_text(
                        self.cursor_x + 4.0,
                        self.cursor_y + metrics.ascent,
                        code,
                        code_font,
                        Color::rgb(60, 60, 60),
                    ));

                    self.cursor_x += text_width + 8.0;
                }
                InlineNode::SoftBreak => {
                    // Render as space
                    self.cursor_x += metrics.avg_char_width * 0.3;
                }
                InlineNode::HardBreak => {
                    self.new_line();
                }
                _ => {
                    // Other inline nodes not implemented yet
                }
            }
        }
    }

    /// Layout code block
    fn layout_code_block(&mut self, _language: &Option<String>, content: &str, result: &mut RenderResult) {
        let font = FontSpec {
            family: FontFamily::Mono,
            size_pt: 12.0,
            bold: false,
            italic: false,
        };

        let metrics = self.get_font_metrics(font);
        self.current_line_height = metrics.line_height;

        // Draw background
        let line_count = content.lines().count() as f32;
        let block_height = line_count * self.current_line_height;

        result.push(RenderCommand::fill_rect(
            self.config.margin_left,
            self.cursor_y,
            self.config.content_width(),
            block_height,
            Color::rgb(245, 245, 245),
        ));

        // Draw each line
        for line in content.lines() {
            result.push(RenderCommand::draw_text(
                self.cursor_x + 8.0,
                self.cursor_y + metrics.ascent,
                line,
                font,
                Color::rgb(40, 40, 40),
            ));
            self.new_line();
        }

        self.cursor_y += self.config.paragraph_spacing;
    }

    /// Layout list
    fn layout_list(&mut self, _ordered: bool, start_number: Option<usize>, items: &[crate::parser::ast::ListItem], result: &mut RenderResult) {
        let font = FontSpec {
            family: FontFamily::Sans,
            size_pt: 14.0,
            bold: false,
            italic: false,
        };

        let metrics = self.get_font_metrics(font);
        self.current_line_height = metrics.line_height;

        for (i, item) in items.iter().enumerate() {
            self.cursor_x = self.config.margin_left + 20.0;  // Indent

            // Draw marker
            let marker = match start_number {
                Some(start) => format!("{}.", start + i),
                None => "•".to_string(),
            };

            result.push(RenderCommand::draw_text(
                self.cursor_x - 20.0,
                self.cursor_y + metrics.ascent,
                &marker,
                font,
                Color::BLACK,
            ));

            // Layout content
            for block in &item.content {
                self.layout_block(block);
            }
        }
    }

    /// Layout visible range only
    pub fn layout_visible_range(
        &mut self,
        blocks: &[BlockNode],
        start_y: f32,
        end_y: f32,
    ) -> RenderResult {
        let mut result = RenderResult::new();
        self.reset_cursor();

        // Skip blocks before visible range
        for block in blocks {
            let block_height = self.estimate_block_height(block);

            if self.cursor_y + block_height < start_y {
                self.cursor_y += block_height;
                continue;
            }

            if self.cursor_y > end_y {
                break;
            }

            // Layout this block
            let block_result = self.layout_block(block);
            result.commands.extend(block_result.commands);
            result.dirty_rects.extend(block_result.dirty_rects);
        }

        result.total_height = self.cursor_y + self.config.margin_bottom;
        result.merge_dirty_rects();

        result
    }

    /// Estimate block height without full layout
    fn estimate_block_height(&self, _block: &BlockNode) -> f32 {
        self.current_line_height * 2.0  // Simplified
    }
}

// -----------------------------------------------------------------------------
// Public API
// ----------------------------------------------------------------------------/

/// 创建为 Supernote 配置的布局器
pub fn create_supernote_layouter() -> Layouter {
    Layouter::for_supernote()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_layout_config() {
        let config = LayoutConfig::for_supernote();
        assert_eq!(config.dpi, 300.0);
        assert_eq!(config.screen_width, 1404);
        assert_eq!(config.screen_height, 1872);
    }

    #[test]
    fn test_pt_to_px() {
        let config = LayoutConfig::for_supernote();
        let px = config.pt_to_px(12.0);
        assert!((px - 50.0).abs() < 1.0);  // 12pt * (300/72) ≈ 50px
    }

    #[test]
    fn test_layout_paragraph() {
        let mut layouter = Layouter::for_supernote();
        let nodes = vec![
            InlineNode::Text("Hello world".to_string()),
        ];

        let mut result = RenderResult::new();
        layouter.layout_paragraph(&nodes, &mut result);
        // Should have generated at least one text command
        assert!(!result.commands.is_empty());
    }
}
