/**
 * NomadMark Desktop - Type Definitions
 *
 * 与 Rust Core FFI 和 Tauri 后端保持一致
 * 参考: 《架构设计书 v2.0》§3.1.4, §5.1
 */

// =============================================================================
// RenderCommand (与 Rust Core FFI 对齐)
// =============================================================================

export type RenderCommand =
  | DrawTextCommand
  | DrawLineCommand
  | FillRectCommand
  | DrawImageCommand;

export interface DrawTextCommand {
  type: 'DrawText';
  x: number;
  y: number;
  text: string;
  font: string;
  size: number;
  color: string;
}

export interface DrawLineCommand {
  type: 'DrawLine';
  x1: number;
  y1: number;
  x2: number;
  y2: number;
  width: number;
  color: string;
}

export interface FillRectCommand {
  type: 'FillRect';
  x: number;
  y: number;
  width: number;
  height: number;
  color: string;
}

export interface DrawImageCommand {
  type: 'DrawImage';
  x: number;
  y: number;
  width: number;
  height: number;
  data: string; // base64 编码
}

// =============================================================================
// 视口相关
// =============================================================================

export interface Viewport {
  x: number;
  y: number;
  width: number;
  height: number;
  scale_factor: number;
}

// =============================================================================
// 文档元数据
// =============================================================================

export interface DocumentMetadata {
  total_chars: number;
  total_lines: number;
  title: string;
}

export interface DocumentInfo {
  handle: number;
  path: string;
  total_chars: number;
  total_lines: number;
}

export interface RenderResult {
  commands: RenderCommand[];
  total_height: number;
}

// =============================================================================
// 目录
// =============================================================================

export interface TocEntry {
  level: number;
  title: string;
  byte_offset: number;
  line_number: number;
}

// =============================================================================
// 搜索
// =============================================================================

export interface SearchResult {
  line: number;
  start_column: number;
  end_column: number;
  context: string;
}

// =============================================================================
// 分屏模式 (参考 UI 文档 §6, §7)
// =============================================================================

export enum ViewMode {
  /** 纯编辑模式 */
  Edit = 'edit',
  /** 纯预览模式 */
  Preview = 'preview',
  /** 分屏模式 */
  Split = 'split',
}

export enum KeyboardType {
  /** 外接键盘 */
  External = 'external',
  /** 软键盘 */
  Soft = 'soft',
  /** 无键盘 */
  None = 'none',
}

export interface SplitConfig {
  /** 编辑区高度比例 (0-1) */
  editRatio: number;
  /** 预览区高度比例 (0-1) */
  previewRatio: number;
}

/**
 * 根据 UI 文档 §6, §7 获取分屏配置
 * - 外接键盘: 50:50 (edit:preview)
 * - 软键盘: 40:60 (edit:preview)
 */
export function getSplitConfig(keyboardType: KeyboardType): SplitConfig {
  switch (keyboardType) {
    case KeyboardType.External:
      return { editRatio: 0.5, previewRatio: 0.5 };
    case KeyboardType.Soft:
      return { editRatio: 0.4, previewRatio: 0.6 };
    default:
      return { editRatio: 0.5, previewRatio: 0.5 };
  }
}

// =============================================================================
// 工具类型
// =============================================================================

export interface Rect {
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface Point {
  x: number;
  y: number;
}
