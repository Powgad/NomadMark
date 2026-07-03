/**
 * NomadMark Desktop - Canvas Rendering Hook
 *
 * 将 Rust Core 返回的 RenderCommand 转换为 Canvas 绘制指令
 * 参考: 《架构设计书 v2.0》§3.1.3, §5.1
 */

import { useEffect, useRef, useCallback } from 'react';
import type { RenderCommand } from '../types';

interface UseCanvasRendererOptions {
  /** DPI 缩放因子 (默认 1.0) */
  scale?: number;
  /** 背景颜色 (默认白色) */
  backgroundColor?: string;
}

interface CanvasRendererContext {
  ctx: CanvasRenderingContext2D;
  scale: number;
  backgroundColor: string;
}

/**
 * 渲染单个绘制指令
 */
function renderCommand(
  ctx: CanvasRenderingContext2D,
  cmd: RenderCommand,
  scale: number
): void {
  ctx.save();

  switch (cmd.type) {
    case 'DrawText': {
      const { x, y, text, font, size, color } = cmd;
      ctx.fillStyle = color;
      ctx.font = `${size * scale}px ${font}`;
      ctx.textBaseline = 'top';
      ctx.fillText(text, x * scale, y * scale);
      break;
    }

    case 'DrawLine': {
      const { x1, y1, x2, y2, width, color } = cmd;
      ctx.strokeStyle = color;
      ctx.lineWidth = width * scale;
      ctx.beginPath();
      ctx.moveTo(x1 * scale, y1 * scale);
      ctx.lineTo(x2 * scale, y2 * scale);
      ctx.stroke();
      break;
    }

    case 'FillRect': {
      const { x, y, width, height, color } = cmd;
      ctx.fillStyle = color;
      ctx.fillRect(x * scale, y * scale, width * scale, height * scale);
      break;
    }

    case 'DrawImage': {
      const { x, y, width, height, data } = cmd;
      const img = new Image();
      img.onload = () => {
        ctx.drawImage(img, x * scale, y * scale, width * scale, height * scale);
      };
      img.src = `data:image/png;base64,${data}`;
      break;
    }
  }

  ctx.restore();
}

/**
 * 批量渲染绘制指令
 */
function renderCommands(
  ctx: CanvasRenderingContext2D,
  commands: RenderCommand[],
  scale: number,
  backgroundColor: string
): void {
  // 清空画布
  const { width, height } = ctx.canvas;
  ctx.fillStyle = backgroundColor;
  ctx.fillRect(0, 0, width, height);

  // 渲染所有指令
  for (const cmd of commands) {
    renderCommand(ctx, cmd, scale);
  }
}

/**
 * Canvas 渲染 Hook
 *
 * @example
 * ```tsx
 * const canvasRef = useRef<HTMLCanvasElement>(null);
 * const { render } = useCanvasRenderer(canvasRef);
 *
 * useEffect(() => {
 *   render(commands);
 * }, [commands, render]);
 * ```
 */
export function useCanvasRenderer(
  canvasRef: React.RefObject<HTMLCanvasElement>,
  options: UseCanvasRendererOptions = {}
) {
  const { scale = 1.0, backgroundColor = '#FFFFFF' } = options;

  // 存储渲染上下文
  const contextRef = useRef<CanvasRendererContext | null>(null);

  /**
   * 初始化 Canvas 上下文
   */
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const ctx = canvas.getContext('2d', { alpha: false });
    if (!ctx) return;

    contextRef.current = { ctx, scale, backgroundColor };

    // 设置 Canvas 尺寸 (考虑 DPI)
    const dpr = window.devicePixelRatio || 1;
    const rect = canvas.getBoundingClientRect();

    canvas.width = rect.width * dpr;
    canvas.height = rect.height * dpr;

    ctx.scale(dpr, dpr);

    // 初始清空
    ctx.fillStyle = backgroundColor;
    ctx.fillRect(0, 0, rect.width, rect.height);
  }, [canvasRef, scale, backgroundColor]);

  /**
   * 渲染指令到 Canvas
   */
  const render = useCallback((commands: RenderCommand[]) => {
    const context = contextRef.current;
    if (!context) return;

    renderCommands(context.ctx, commands, context.scale, context.backgroundColor);
  }, []);

  /**
   * 清空 Canvas
   */
  const clear = useCallback(() => {
    const context = contextRef.current;
    if (!context) return;

    const { width, height } = context.ctx.canvas;
    context.ctx.fillStyle = context.backgroundColor;
    context.ctx.fillRect(0, 0, width, height);
  }, []);

  /**
   * 获取 Canvas 尺寸
   */
  const getSize = useCallback((): { width: number; height: number } => {
    const canvas = canvasRef.current;
    if (!canvas) return { width: 0, height: 0 };

    const rect = canvas.getBoundingClientRect();
    return { width: rect.width, height: rect.height };
  }, [canvasRef]);

  return { render, clear, getSize };
}

/**
 * 脏矩形渲染器 (用于局部刷新)
 *
 * 仅重绘指定区域，减少闪烁
 * 参考: 《架构设计书 v2.0》§4.2
 */
export interface DirtyRect {
  x: number;
  y: number;
  width: number;
  height: number;
}

export function useDirtyRectRenderer(
  canvasRef: React.RefObject<HTMLCanvasElement>,
  options: UseCanvasRendererOptions = {}
) {
  const baseRenderer = useCanvasRenderer(canvasRef, options);
  const lastRenderRef = useRef<Map<string, RenderCommand[]>>(new Map());

  /**
   * 合并相交的脏矩形
   */
  const mergeDirtyRects = useCallback((rects: DirtyRect[]): DirtyRect[] => {
    if (rects.length === 0) return [];

    const merged: DirtyRect[] = [...rects];
    let changed = true;

    while (changed && merged.length > 1) {
      changed = false;
      for (let i = 0; i < merged.length - 1; i++) {
        for (let j = i + 1; j < merged.length; j++) {
          if (intersects(merged[i], merged[j])) {
            merged.splice(j, 1);
            merged.splice(i, 1);
            merged.push(union(merged[i], merged[j]));
            changed = true;
            break;
          }
        }
        if (changed) break;
      }
    }

    return merged;
  }, []);

  /**
   * 渲染指定区域的指令
   */
  const renderDirty = useCallback(
    (regionId: string, commands: RenderCommand[]) => {
      // 缓存本次渲染
      lastRenderRef.current.set(regionId, commands);

      // 如果脏矩形超过 Canvas 1/3，使用全量渲染
      const size = baseRenderer.getSize();
      const totalArea = size.width * size.height;

      const dirtyRects: DirtyRect[] = commands
        .filter((cmd) => cmd.type === 'FillRect')
        .map((cmd) => {
          const rectCmd = cmd as Extract<RenderCommand, { type: 'FillRect' }>;
          return {
            x: rectCmd.x,
            y: rectCmd.y,
            width: rectCmd.width,
            height: rectCmd.height,
          };
        });

      const dirtyArea = dirtyRects.reduce(
        (sum, rect) => sum + rect.width * rect.height,
        0
      );

      if (dirtyArea > totalArea / 3) {
        // 全量渲染
        baseRenderer.render(commands);
      } else {
        // 局部渲染
        for (const rect of dirtyRects) {
          baseRenderer.render(commands.filter((cmd) =>
            isCommandInRect(cmd, rect)
          ));
        }
      }
    },
    [baseRenderer]
  );

  return { ...baseRenderer, renderDirty, mergeDirtyRects };
}

// =============================================================================
// 辅助函数
// =============================================================================

function intersects(a: DirtyRect, b: DirtyRect): boolean {
  return !(
    a.x + a.width < b.x ||
    b.x + b.width < a.x ||
    a.y + a.height < b.y ||
    b.y + b.height < a.y
  );
}

function union(a: DirtyRect, b: DirtyRect): DirtyRect {
  const x = Math.min(a.x, b.x);
  const y = Math.min(a.y, b.y);
  const width = Math.max(a.x + a.width, b.x + b.width) - x;
  const height = Math.max(a.y + a.height, b.y + b.height) - y;
  return { x, y, width, height };
}

function isCommandInRect(cmd: RenderCommand, rect: DirtyRect): boolean {
  switch (cmd.type) {
    case 'DrawText':
    case 'DrawImage':
      return (
        cmd.x >= rect.x &&
        cmd.x < rect.x + rect.width &&
        cmd.y >= rect.y &&
        cmd.y < rect.y + rect.height
      );
    case 'FillRect':
      return (
        cmd.x >= rect.x &&
        cmd.x + cmd.width <= rect.x + rect.width &&
        cmd.y >= rect.y &&
        cmd.y + cmd.height <= rect.y + rect.height
      );
    case 'DrawLine':
      return (
        cmd.x1 >= rect.x &&
        cmd.x1 < rect.x + rect.width &&
        cmd.y1 >= rect.y &&
        cmd.y1 < rect.y + rect.height
      );
  }
}
