/**
 * NomadMark Desktop - Split Screen View Component
 *
 * 分屏布局组件
 * 参考: UI 文档 §6 (外接键盘), §7 (软键盘)
 *
 * 布局规则:
 * - 外接键盘: 编辑区:预览区 = 50:50
 * - 软键盘: 编辑区:预览区 = 40:60
 */

import React, { useRef, useEffect, useCallback } from 'react';
import { useCanvasRenderer } from '../hooks/useCanvasRenderer';
import { useDocument } from '../hooks/useDocument';
import {
  ViewMode,
  KeyboardType,
  type Rect,
} from '../types';

interface SplitViewProps {
  /** 视图模式 */
  viewMode: ViewMode;
  /** 键盘类型 (影响分屏比例) */
  keyboardType: KeyboardType;
  /** 容器尺寸 */
  containerSize: Rect;
  /** 滚动位置 */
  scrollPosition: { edit: number; preview: number };
  /** 滚动位置变化回调 */
  onScrollChange?: (position: { edit: number; preview: number }) => void;
  /** 分隔条位置 (0-1, 仅在分屏模式有效) */
  dividerPosition?: number;
  /** 分隔条拖动回调 */
  onDividerChange?: (position: number) => void;
}

/**
 * 分屏视图组件
 */
export const SplitView: React.FC<SplitViewProps> = ({
  viewMode,
  keyboardType,
  containerSize,
  scrollPosition,
  onScrollChange,
  dividerPosition = 0.5,
  onDividerChange,
}) => {
  const editCanvasRef = useRef<HTMLCanvasElement>(null);
  const previewCanvasRef = useRef<HTMLCanvasElement>(null);
  const dividerRef = useRef<HTMLDivElement>(null);

  const editRenderer = useCanvasRenderer(editCanvasRef);
  const previewRenderer = useCanvasRenderer(previewCanvasRef);
  const { render, info } = useDocument();

  /**
   * 计算区域尺寸
   */
  const calculateRegions = useCallback((): {
    edit: Rect;
    preview: Rect;
    divider: Rect;
  } => {
    const { width, height } = containerSize;

    if (viewMode === ViewMode.Edit) {
      // 纯编辑模式: 全屏编辑区
      return {
        edit: { x: 0, y: 0, width, height },
        preview: { x: 0, y: 0, width: 0, height: 0 },
        divider: { x: 0, y: 0, width: 0, height: 0 },
      };
    }

    if (viewMode === ViewMode.Preview) {
      // 纯预览模式: 全屏预览区
      return {
        edit: { x: 0, y: 0, width: 0, height: 0 },
        preview: { x: 0, y: 0, width, height },
        divider: { x: 0, y: 0, width: 0, height: 0 },
      };
    }

    // 分屏模式
    const dividerHeight = 4;
    const dividerY = height * dividerPosition - dividerHeight / 2;

    // 编辑区 (下方)
    const editHeight = height - (dividerY + dividerHeight);

    // 预览区 (上方)
    const previewHeight = dividerY;

    return {
      edit: { x: 0, y: dividerY + dividerHeight, width, height: editHeight },
      preview: { x: 0, y: 0, width, height: previewHeight },
      divider: { x: 0, y: dividerY, width, height: dividerHeight },
    };
  }, [containerSize, viewMode, dividerPosition]);

  /**
   * 渲染编辑区
   */
  useEffect(() => {
    if (!info || viewMode === ViewMode.Preview) return;

    const regions = calculateRegions();
    if (regions.edit.width === 0 || regions.edit.height === 0) return;

    const viewport = {
      x: 0,
      y: scrollPosition.edit,
      width: regions.edit.width,
      height: regions.edit.height,
      scale_factor: window.devicePixelRatio || 1,
    };

    render(viewport).then((result) => {
      editRenderer.render(result.commands);
    });
  }, [info, viewMode, scrollPosition.edit, calculateRegions, render, editRenderer]);

  /**
   * 渲染预览区
   */
  useEffect(() => {
    if (!info || viewMode === ViewMode.Edit) return;

    const regions = calculateRegions();
    if (regions.preview.width === 0 || regions.preview.height === 0) return;

    const viewport = {
      x: 0,
      y: scrollPosition.preview,
      width: regions.preview.width,
      height: regions.preview.height,
      scale_factor: window.devicePixelRatio || 1,
    };

    render(viewport).then((result) => {
      previewRenderer.render(result.commands);
    });
  }, [
    info,
    viewMode,
    scrollPosition.preview,
    calculateRegions,
    render,
    previewRenderer,
  ]);

  /**
   * 处理分隔条拖动
   */
  const handleDividerDragStart = useCallback(() => {
    const container = editCanvasRef.current?.parentElement;
    if (!container) return;

    const handleMouseMove = (e: MouseEvent) => {
      const rect = container.getBoundingClientRect();
      const relativeY = e.clientY - rect.top;
      const newPosition = Math.max(0.1, Math.min(0.9, relativeY / rect.height));
      onDividerChange?.(newPosition);
    };

    const handleMouseUp = () => {
      document.removeEventListener('mousemove', handleMouseMove);
      document.removeEventListener('mouseup', handleMouseUp);
    };

    document.addEventListener('mousemove', handleMouseMove);
    document.addEventListener('mouseup', handleMouseUp);
  }, [onDividerChange]);

  /**
   * 处理滚轮事件
   */
  const handleWheel = useCallback(
    (e: React.WheelEvent, region: 'edit' | 'preview') => {
      e.preventDefault();

      const delta = e.deltaY;
      const newScroll = {
        ...scrollPosition,
        [region]: Math.max(0, scrollPosition[region] + delta),
      };

      onScrollChange?.(newScroll);
    },
    [scrollPosition, onScrollChange]
  );

  const regions = calculateRegions();

  return (
    <div className="split-view" style={{ position: 'relative', width: containerSize.width, height: containerSize.height, overflow: 'hidden' }}>
      {/* 预览区 (上方) */}
      {viewMode !== ViewMode.Edit && regions.preview.width > 0 && (
        <canvas
          ref={previewCanvasRef}
          className="preview-canvas"
          style={{
            position: 'absolute',
            left: regions.preview.x,
            top: regions.preview.y,
            width: regions.preview.width,
            height: regions.preview.height,
            backgroundColor: '#FFFFFF',
          }}
          onWheel={(e) => handleWheel(e, 'preview')}
        />
      )}

      {/* 分隔条 */}
      {viewMode === ViewMode.Split && regions.divider.width > 0 && (
        <div
          ref={dividerRef}
          className="split-divider"
          style={{
            position: 'absolute',
            left: regions.divider.x,
            top: regions.divider.y,
            width: regions.divider.width,
            height: regions.divider.height,
            backgroundColor: '#CCCCCC',
            cursor: 'row-resize',
            zIndex: 10,
          }}
          onMouseDown={handleDividerDragStart}
        />
      )}

      {/* 编辑区 (下方) */}
      {viewMode !== ViewMode.Preview && regions.edit.width > 0 && (
        <canvas
          ref={editCanvasRef}
          className="edit-canvas"
          style={{
            position: 'absolute',
            left: regions.edit.x,
            top: regions.edit.y,
            width: regions.edit.width,
            height: regions.edit.height,
            backgroundColor: '#FFFFFF',
          }}
          onWheel={(e) => handleWheel(e, 'edit')}
        />
      )}

      {/* 键盘标识 (UI 文档要求) */}
      {viewMode === ViewMode.Edit && (
        <div
          className="keyboard-indicator"
          style={{
            position: 'absolute',
            bottom: 0,
            left: 0,
            right: 0,
            height: 24,
            backgroundColor: '#F0F0F0',
            borderTop: '1px solid #CCCCCC',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: 12,
            color: '#666666',
          }}
        >
          {keyboardType === KeyboardType.External
            ? '⌨️ 外接键盘'
            : keyboardType === KeyboardType.Soft
            ? '📱 软键盘'
            : ''}
        </div>
      )}
    </div>
  );
};

export default SplitView;
