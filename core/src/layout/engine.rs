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
use crate::syntax::CodeHighlighter;
use lru::LruCache;
use std::collections::HashMap;
use std::num::NonZeroUsize;

// Android 日志函数
#[cfg(target_os = "android")]
fn android_log(tag: &str, msg: &str) {
    use std::ffi::CString;
    unsafe {
        let tag_cstr = CString::new(tag).unwrap();
        let msg_cstr = CString::new(msg).unwrap();
        let fmt = CString::new("%s").unwrap();
        extern "C" {
            fn __android_log_print(priority: std::os::raw::c_int, tag: *const std::os::raw::c_char, fmt: *const std::os::raw::c_char, ...) -> std::os::raw::c_int;
        }
        __android_log_print(3, tag_cstr.as_ptr(), fmt.as_ptr(), msg_cstr.as_ptr());
    }
}

#[cfg(not(target_os = "android"))]
fn android_log(tag: &str, msg: &str) {
    eprintln!("[{}] {}", tag, msg);
}

macro_rules! layout_log {
    ($($arg:tt)*) => {
        android_log("NomadMark", &format!($($arg)*))
    };
}

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

/// 字体指标缓存键
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub struct FontKey {
    pub family: FontFamily,
    pub size_pt: u8,
    pub bold: bool,
    pub italic: bool,
}

// -----------------------------------------------------------------------------
// 字形缓存（三层）
// -----------------------------------------------------------------------------

/// 字形缓存系统
pub struct GlyphCacheSystem {
    /// L1: RAM 缓存（热数据，约 2MB）
    l1_ram: LruCache<GlyphKey, GlyphBitmap>,
    /// L2: 预渲染字形指标（温数据）
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
    pub data: Vec<u8>,  // 灰度位图
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

    /// 获取字形位图（带 L1 缓存）
    pub fn get_glyph(&mut self, key: &GlyphKey) -> Option<&GlyphBitmap> {
        self.l1_ram.get(key)
    }

    /// 获取字形指标（从 L2）
    pub fn get_metrics(&self, key: &GlyphKey) -> Option<&GlyphMetrics> {
        self.l2_metrics.get(key)
    }

    /// 预加载常用 CJK 字符（启动时调用）
    pub fn preload_cjk_common(&mut self) {
        // 前 500 个常用 CJK 字符
        const CJK_COMMON: &str = "的一是在不了有和人这中大为上个国我以要他时来用们生到作地于出就分对成会可主发表还年能动同工也能下过子说产种面而方后多定行学法所民得经十三之进着等部度家电力里如水化高自二理起小物现实加量都两体制机当使点从业本去把性好应开它合还因由其些然前外天政四日那社义事平形相全表间样与关各重新线内数正心反你明看原又么利比或但质气第向道命此变条只没结解问意建月公无系军很情者最立代想已通并提直题党程展五果料象员革位入常文总次品式活设及管特件长求老头基资边流路级少图山统接知较将组见计别她手角期根论运农指几九区强放决西被干做必战先回则任取据处队南给色光门即保治北造百规热领七海口东导器压志世金增争济阶油思术极交受联什认六共权收证改清己美再采转更单风切打白教速花带安场身车例真务具万每目至达走积示议声报斗完类八离华名确才科张信马节话米整空元况今据温虫么书";

        let font = FontKey {
            family: FontFamily::CJK,
            size_pt: 14,
            bold: false,
            italic: false,
        };

        // 预填充指标缓存
        for ch in CJK_COMMON.chars() {
            let key = GlyphKey { char: ch, font };
            // 在实际实现中，将通过 FreeType 进行光栅化
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
// 布局引擎
// -----------------------------------------------------------------------------

/// 主布局引擎
pub struct Layouter {
    /// 配置
    config: LayoutConfig,
    /// 字体指标缓存
    font_metrics: LruCache<FontKey, FontMetrics>,
    /// 字形缓存
    glyph_cache: GlyphCacheSystem,
    /// 当前光标位置
    cursor_x: f32,
    cursor_y: f32,
    /// 当前行高
    current_line_height: f32,
}

impl Layouter {
    /// 为 Supernote 创建新的布局器
    pub fn for_supernote() -> Self {
        let mut layouter = Self {
            config: LayoutConfig::for_supernote(),
            font_metrics: LruCache::new(NonZeroUsize::new(1024).unwrap()),
            glyph_cache: GlyphCacheSystem::new(),
            cursor_x: 0.0,
            cursor_y: 0.0,
            current_line_height: 0.0,
        };

        // 预加载常用 CJK 字形
        layouter.glyph_cache.preload_cjk_common();

        layouter
    }

    /// 将光标重置到内容区域起始位置
    fn reset_cursor(&mut self) {
        self.cursor_x = self.config.margin_left;
        self.cursor_y = self.config.margin_top;
        self.current_line_height = self.config.pt_to_px(14.0) * self.config.line_spacing;
    }

    /// 移动到下一行
    fn new_line(&mut self) {
        self.cursor_x = self.config.margin_left;
        self.cursor_y += self.current_line_height;
    }

    /// 获取字体指标（带缓存）
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

        // 计算指标（简化版 - 实际实现使用字体表）
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

    /// 布局单个块节点
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
            BlockNode::Blockquote { level, children } => {
                self.layout_blockquote(*level, children, &mut result);
            }
            BlockNode::ThematicBreak => {
                self.layout_thematic_break(&mut result);
            }
            BlockNode::MathBlock { latex } => {
                self.layout_math_block(latex, &mut result);
            }
            BlockNode::Callout { kind, title, children } => {
                self.layout_callout(kind, title.as_deref(), children, &mut result);
            }
            BlockNode::TableOfContents => {
                self.layout_toc(&mut result);
            }
            _ => {
                // 其他块类型的占位符
            }
        }

        result
    }

    /// 布局标题
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

        // 绘制标题背景
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

        // 布局内联内容
        self.layout_inline(children, font, Color::BLACK, result);

        self.new_line();
        self.cursor_y += self.config.paragraph_spacing;
    }

    /// 布局段落
    fn layout_paragraph(&mut self, children: &[InlineNode], result: &mut RenderResult) {
        let font = FontSpec {
            family: FontFamily::Sans,
            size_pt: 14.0,
            bold: false,
            italic: false,
        };

        let metrics = self.get_font_metrics(font);
        self.current_line_height = metrics.line_height;

        layout_log!("  📝 Paragraph BEFORE: cursor_y={}", self.cursor_y);
        self.layout_inline(children, font, Color::BLACK, result);
        self.new_line();
        self.cursor_y += self.config.paragraph_spacing;
        layout_log!("  📝 Paragraph AFTER: cursor_y={}", self.cursor_y);
    }

    /// 布局内联节点
    fn layout_inline(&mut self, nodes: &[InlineNode], font: FontSpec, color: Color, result: &mut RenderResult) {
        let metrics = self.get_font_metrics(font);
        let content_width = self.config.content_width();

        for node in nodes {
            match node {
                InlineNode::Text(text) => {
                    // 简单的自动换行
                    let words: Vec<&str> = text.split_whitespace().collect();
                    for (i, word) in words.iter().enumerate() {
                        let word_width = word.len() as f32 * metrics.avg_char_width;

                        // 检查是否需要换行
                        if self.cursor_x + word_width > self.config.margin_left + content_width {
                            self.new_line();
                        }

                        // 绘制文本
                        result.push(RenderCommand::draw_text(
                            self.cursor_x,
                            self.cursor_y + metrics.ascent,
                            word,
                            font,
                            color,
                        ));

                        self.cursor_x += word_width;

                        // 在单词之间添加空格
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

                    // 绘制代码背景
                    let text_width = code.len() as f32 * metrics.avg_char_width;
                    result.push(RenderCommand::fill_rect(
                        self.cursor_x,
                        self.cursor_y,
                        text_width + 8.0,
                        self.current_line_height,
                        Color::rgb(240, 240, 240),
                    ));

                    // 绘制代码文本
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
                    // 渲染为空格
                    self.cursor_x += metrics.avg_char_width * 0.3;
                }
                InlineNode::HardBreak => {
                    self.new_line();
                }
                InlineNode::Math { display_mode, latex } => {
                    // 布局行内数学公式
                    let math_font = FontSpec {
                        family: FontFamily::Sans,
                        size_pt: font.size_pt,
                        bold: false,
                        italic: true,  // 数学公式使用斜体
                    };

                    // 简化实现：显示为占位符
                    let placeholder = format!("${}$", latex);
                    let text_width = placeholder.len() as f32 * metrics.avg_char_width;

                    // 绘制数学公式背景（浅蓝色）
                    let math_height = if *display_mode {
                        self.current_line_height * 1.5  // 块级显示更高
                    } else {
                        self.current_line_height
                    };

                    result.push(RenderCommand::fill_rect(
                        self.cursor_x,
                        self.cursor_y,
                        text_width + 8.0,
                        math_height,
                        Color::rgb(240, 240, 240),  // 浅灰色背景（墨水屏友好）
                    ));

                    // 绘制占位符文本
                    result.push(RenderCommand::draw_text(
                        self.cursor_x + 4.0,
                        self.cursor_y + if *display_mode {
                            math_height / 2.0 - metrics.ascent / 2.0
                        } else {
                            metrics.ascent
                        },
                        &placeholder,
                        math_font,
                        Color::rgb(0, 0, 0),  // 黑色文字（墨水屏高对比度）
                    ));

                    self.cursor_x += text_width + 8.0;
                }
                _ => {
                    // 其他内联节点尚未实现
                }
            }
        }
    }

    /// 布局代码块（支持语法高亮）
    fn layout_code_block(&mut self, language: &Option<String>, content: &str, result: &mut RenderResult) {
        let font = FontSpec {
            family: FontFamily::Mono,
            size_pt: 12.0,
            bold: false,
            italic: false,
        };

        let metrics = self.get_font_metrics(font);
        self.current_line_height = metrics.line_height;

        // 绘制背景
        let line_count = content.lines().count() as f32;
        let block_height = line_count * self.current_line_height;

        // E-ink 友好的浅灰背景
        let bg_color = Color::rgb(245, 245, 245);
        result.push(RenderCommand::fill_rect(
            self.config.margin_left,
            self.cursor_y,
            self.config.content_width(),
            block_height,
            bg_color,
        ));

        // 使用语法高亮器
        let highlighter = CodeHighlighter::new();
        let theme = highlighter.theme();

        // 获取高亮 token
        let lang_str = language.as_deref();
        let tokens = highlighter.highlight(content, lang_str);

        // 逐行渲染高亮代码
        let mut char_offset = 0;
        let base_x = self.cursor_x + 8.0;
        let mut y = self.cursor_y + metrics.ascent;

        for line in content.lines() {
            let mut x = base_x;

            // 渲染该行的每个 token
            for token in &tokens {
                // 只处理当前行的 token
                if token.offset < char_offset || token.offset >= char_offset + line.len() {
                    continue;
                }

                // 获取 token 颜色
                let color = theme.color_for_token(token.token_type.color_name());

                // 渲染 token 文本
                if !token.text.is_empty() {
                    result.push(RenderCommand::draw_text(
                        x,
                        y,
                        &token.text,
                        font,
                        color,
                    ));

                    // 计算下一个 token 的 x 位置（简化：使用字符数）
                    // 在实际实现中，应该使用字形度量
                    x += token.text.len() as f32 * font.size_pt * 0.6;
                }
            }

            // 移动到下一行
            y += self.current_line_height;
            char_offset += line.len() + 1; // +1 for newline
        }

        // 更新光标位置
        self.cursor_y += block_height + self.config.paragraph_spacing;
    }

    /// 布局列表
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
            self.cursor_x = self.config.margin_left + 20.0;  // 缩进

            // 绘制标记
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

            // 布局内容
            for block in &item.content {
                self.layout_block(block);
            }
        }
    }

    /// 布局引用块
    fn layout_blockquote(&mut self, level: u8, children: &[BlockNode], result: &mut RenderResult) {
        layout_log!("  💬 Blockquote level={} BEFORE: cursor_y={}", level, self.cursor_y);

        // 计算缩进（每级 12px）
        let indent = self.config.margin_left + (level as f32 * 12.0);

        // 绘制背景
        let bg_height = self.estimate_block_height(&BlockNode::Blockquote {
            level,
            children: children.to_vec(),
        });

        result.push(RenderCommand::fill_rect(
            indent,
            self.cursor_y,
            self.config.content_width() - (level as f32 * 12.0),
            bg_height,
            Color::rgb(245, 245, 245),  // E-ink 友好的浅灰背景
        ));

        // 绘制左边框（2px）
        result.push(RenderCommand::fill_rect(
            indent,
            self.cursor_y,
            2.0,
            bg_height,
            Color::rgb(204, 204, 204),  // 边框颜色
        ));

        // 保存原始 cursor_x，设置新的缩进
        let original_x = self.cursor_x;
        self.cursor_x = indent + 12.0;  // 左内边距

        // 递归布局子块
        for child in children {
            let child_result = self.layout_block(child);
            result.commands.extend(child_result.commands);
            result.dirty_rects.extend(child_result.dirty_rects);
        }

        // 恢复原始 cursor_x
        self.cursor_x = original_x;
        self.cursor_y += bg_height + 8.0;  // 下边距

        layout_log!("  💬 Blockquote AFTER: cursor_y={}", self.cursor_y);
    }

    /// 布局 Callout 提示块
    fn layout_callout(&mut self, kind: &crate::parser::CalloutKind, title: Option<&str>, _children: &[BlockNode], result: &mut RenderResult) {
        layout_log!("  📋 Callout kind={:?} BEFORE: cursor_y={}", kind, self.cursor_y);

        let font = FontSpec {
            family: FontFamily::Sans,
            size_pt: 14.0,
            bold: true,
            italic: false,
        };

        let body_font = FontSpec {
            family: FontFamily::Sans,
            size_pt: 14.0,
            bold: false,
            italic: false,
        };

        let metrics = self.get_font_metrics(font);
        let body_metrics = self.get_font_metrics(body_font);

        // 估算高度（简化：标题 + 2行内容）
        let title_height = metrics.line_height;
        let content_height = body_metrics.line_height * 2.0;
        let total_height = title_height + content_height + 16.0; // + padding

        // 获取 Callout 类型的颜色
        let border_color = kind.border_color();

        // 绘制背景（浅灰）
        result.push(RenderCommand::fill_rect(
            self.config.margin_left,
            self.cursor_y,
            self.config.content_width(),
            total_height,
            Color::rgb(250, 250, 250),
        ));

        // 绘制左边框（4px）
        result.push(RenderCommand::fill_rect(
            self.config.margin_left,
            self.cursor_y,
            4.0,
            total_height,
            Color::rgb(border_color.0, border_color.1, border_color.2),
        ));

        // 绘制图标和标题
        let icon = kind.icon();
        let title_text = title.unwrap_or(kind.default_title());

        // 图标
        let icon_x = self.config.margin_left + 12.0;
        let icon_y = self.cursor_y + metrics.ascent;
        result.push(RenderCommand::draw_text(
            icon_x,
            icon_y,
            icon,
            font,
            Color::rgb(60, 60, 60),
        ));

        // 标题
        let title_x = icon_x + 24.0;
        let title_y = self.cursor_y + metrics.ascent;
        result.push(RenderCommand::draw_text(
            title_x,
            title_y,
            title_text,
            font,
            Color::rgb(60, 60, 60),
        ));

        // 移动光标
        self.cursor_y += total_height + self.config.paragraph_spacing;

        layout_log!("  📋 Callout AFTER: cursor_y={}", self.cursor_y);
    }

    /// 布局目录（TOC）
    fn layout_toc(&mut self, result: &mut RenderResult) {
        layout_log!("  📑 TOC BEFORE: cursor_y={}", self.cursor_y);

        let font = FontSpec {
            family: FontFamily::Sans,
            size_pt: 14.0,
            bold: false,
            italic: false,
        };

        let metrics = self.get_font_metrics(font);

        // 绘制目录标题
        let title_font = FontSpec {
            family: FontFamily::Sans,
            size_pt: 16.0,
            bold: true,
            italic: false,
        };

        let title_metrics = self.get_font_metrics(title_font);
        result.push(RenderCommand::draw_text(
            self.config.margin_left,
            self.cursor_y + title_metrics.ascent,
            "目录",
            title_font,
            Color::rgb(0, 0, 0),
        ));

        self.cursor_y += title_metrics.line_height + 8.0;

        // 绘制背景
        result.push(RenderCommand::fill_rect(
            self.config.margin_left,
            self.cursor_y,
            self.config.content_width(),
            metrics.line_height * 3.0, // 简化：显示3个条目
            Color::rgb(245, 245, 245),
        ));

        // 绘制示例条目（简化实现）
        let toc_items = vec![
            ("  • 第一章", Color::rgb(60, 60, 60)),
            ("    • 1.1 小节", Color::rgb(100, 100, 100)),
            ("  • 第二章", Color::rgb(60, 60, 60)),
        ];

        for (item, color) in toc_items {
            result.push(RenderCommand::draw_text(
                self.config.margin_left + 8.0,
                self.cursor_y + metrics.ascent,
                item,
                font,
                color,
            ));
            self.cursor_y += metrics.line_height;
        }

        self.cursor_y += self.config.paragraph_spacing;

        layout_log!("  📑 TOC AFTER: cursor_y={}", self.cursor_y);
    }

    /// 布局分割线
    fn layout_thematic_break(&mut self, result: &mut RenderResult) {
        layout_log!("  ➖ ThematicBreak BEFORE: cursor_y={}", self.cursor_y);

        let line_y = self.cursor_y + self.current_line_height / 2.0;

        // 绘制分割线（1px 实线）
        result.push(RenderCommand::draw_line(
            self.config.margin_left,
            line_y,
            self.config.margin_left + self.config.content_width(),
            line_y,
            1.0,
            Color::rgb(221, 221, 221),  // 分割线颜色
        ));

        // 上下边距各 16px
        self.cursor_y += self.current_line_height + 16.0;

        layout_log!("  ➖ ThematicBreak AFTER: cursor_y={}", self.cursor_y);
    }

    /// 布局数学公式块
    fn layout_math_block(&mut self, latex: &str, result: &mut RenderResult) {
        layout_log!("  📐 MathBlock BEFORE: cursor_y={}, latex={}", self.cursor_y, latex);

        // 简化实现：显示为占位符文本
        // 完整实现需要集成 LaTeX 渲染引擎
        let placeholder = format!("[公式: {}]", latex);
        let font_size = 18.0;  // 块级公式字体（原 14.0 → 16.0 → 18.0）

        // 计算高度（数学公式通常比普通文本高）
        let formula_height = font_size * self.config.line_spacing * 2.0; // 2倍行高

        // 绘制占位符背景（浅灰色，墨水屏友好）
        result.push(RenderCommand::fill_rect(
            self.config.margin_left,
            self.cursor_y,
            self.config.content_width(),
            formula_height,
            Color::rgb(240, 240, 240),  // 浅灰色背景（墨水屏友好）
        ));

        // 绘制占位符文本
        result.push(RenderCommand::draw_text(
            self.config.margin_left + 8.0,
            self.cursor_y + formula_height / 2.0,
            &placeholder,
            FontSpec {
                family: FontFamily::Sans,
                size_pt: font_size,
                bold: false,
                italic: true,  // 斜体表示数学公式
            },
            Color::rgb(0, 0, 0),  // 黑色文字（墨水屏高对比度）
        ));

        self.cursor_y += formula_height + 8.0;  // 下边距

        layout_log!("  📐 MathBlock AFTER: cursor_y={}", self.cursor_y);
    }

/// 仅布局可见范围
    pub fn layout_visible_range(
        &mut self,
        blocks: &[BlockNode],
        start_y: f32,
        end_y: f32,
    ) -> RenderResult {
        let mut result = RenderResult::new();
        self.reset_cursor();

        // 跳过可见范围之前的块
        for block in blocks {
            let block_height = self.estimate_block_height(block);

            if self.cursor_y + block_height < start_y {
                self.cursor_y += block_height;
                continue;
            }

            if self.cursor_y > end_y {
                break;
            }

            // 布局此块
            let block_result = self.layout_block(block);
            result.commands.extend(block_result.commands);
            result.dirty_rects.extend(block_result.dirty_rects);
        }

        result.total_height = self.cursor_y + self.config.margin_bottom;
        result.merge_dirty_rects();

        result
    }

    /// 估计块高度（无需完整布局）
    fn estimate_block_height(&self, _block: &BlockNode) -> f32 {
        self.current_line_height * 2.0  // 简化版
    }
}

// -----------------------------------------------------------------------------
// 公共 API
// -----------------------------------------------------------------------------

/// 为 Supernote 创建配置好的布局器
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
        // 应该至少生成了一个文本命令
        assert!(!result.commands.is_empty());
    }
}
