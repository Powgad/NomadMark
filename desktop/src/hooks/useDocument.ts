/**
 * NomadMark Desktop - Document Management Hook
 *
 * 管理文档的打开、渲染、搜索等操作
 * 参考: 《架构设计书 v2.0》§5.1
 */

import { useState, useCallback, useRef } from 'react';
import { invoke } from '@tauri-apps/api/tauri';
import type {
  DocumentInfo,
  RenderCommand,
  RenderResult,
  TocEntry,
  SearchResult,
  Viewport,
} from '../types';

interface UseDocumentOptions {
  /** 默认 DPI 缩放 */
  defaultScale?: number;
}

interface DocumentState {
  /** 当前文档信息 */
  info: DocumentInfo | null;
  /** 是否正在加载 */
  loading: boolean;
  /** 错误信息 */
  error: string | null;
  /** 文档是否已修改 (未保存) */
  modified: boolean;
}

/**
 * 文档管理 Hook
 *
 * @example
 * ```tsx
 * const { info, loading, open, render, search, getToc, save } = useDocument();
 *
 * // 打开文件
 * await open('/path/to/file.md');
 *
 * // 渲染
 * const result = await render(viewport);
 *
 * // 搜索
 * const results = await search('keyword');
 * ```
 */
export function useDocument(options: UseDocumentOptions = {}) {
  const { defaultScale = 1.0 } = options;

  const [state, setState] = useState<DocumentState>({
    info: null,
    loading: false,
    error: null,
    modified: false,
  });

  // 用于取消请求的引用
  const abortControllerRef = useRef<AbortController | null>(null);

  /**
   * 打开文档
   */
  const open = useCallback(async (path: string): Promise<void> => {
    setState((prev) => ({ ...prev, loading: true, error: null }));

    try {
      const info: DocumentInfo = await invoke('open_file', { path });
      setState((prev) => ({
        ...prev,
        info,
        loading: false,
        modified: false,
      }));
    } catch (e) {
      setState((prev) => ({
        ...prev,
        loading: false,
        error: e as string,
      }));
      throw e;
    }
  }, []);

  /**
   * 渲染文档
   */
  const render = useCallback(
    async (viewport: Partial<Viewport>): Promise<RenderResult> => {
      if (!state.info) {
        throw new Error('No document open');
      }

      const fullViewport: Viewport = {
        x: viewport.x ?? 0,
        y: viewport.y ?? 0,
        width: viewport.width ?? 1200,
        height: viewport.height ?? 800,
        scale_factor: viewport.scale_factor ?? defaultScale,
      };

      try {
        const result: RenderResult = await invoke('render_document', {
          handle: state.info.handle,
          viewport: fullViewport,
        });
        return result;
      } catch (e) {
        console.error('Render failed:', e);
        return { commands: [], total_height: 0 };
      }
    },
    [state.info, defaultScale]
  );

  /**
   * 获取目录
   */
  const getToc = useCallback(async (): Promise<TocEntry[]> => {
    if (!state.info) return [];

    try {
      const toc: TocEntry[] = await invoke('get_toc', {
        handle: state.info.handle,
      });
      return toc;
    } catch (e) {
      console.error('Get TOC failed:', e);
      return [];
    }
  }, [state.info]);

  /**
   * 搜索文档
   */
  const search = useCallback(
    async (query: string): Promise<SearchResult[]> => {
      if (!state.info || !query) return [];

      try {
        const results: SearchResult[] = await invoke('search_document', {
          handle: state.info.handle,
          query,
        });
        return results;
      } catch (e) {
        console.error('Search failed:', e);
        return [];
      }
    },
    [state.info]
  );

  /**
   * 保存文档
   */
  const save = useCallback(async (): Promise<void> => {
    if (!state.info) return;

    setState((prev) => ({ ...prev, loading: true }));

    try {
      await invoke('save_document', { handle: state.info.handle });
      setState((prev) => ({ ...prev, loading: false, modified: false }));
    } catch (e) {
      setState((prev) => ({ ...prev, loading: false, error: e as string }));
      throw e;
    }
  }, [state.info]);

  /**
   * 撤销
   */
  const undo = useCallback(async (): Promise<boolean> => {
    if (!state.info) return false;

    try {
      const success: boolean = await invoke('undo', {
        handle: state.info.handle,
      });
      if (success) {
        setState((prev) => ({ ...prev, modified: true }));
      }
      return success;
    } catch (e) {
      console.error('Undo failed:', e);
      return false;
    }
  }, [state.info]);

  /**
   * 重做
   */
  const redo = useCallback(async (): Promise<boolean> => {
    if (!state.info) return false;

    try {
      const success: boolean = await invoke('redo', {
        handle: state.info.handle,
      });
      if (success) {
        setState((prev) => ({ ...prev, modified: true }));
      }
      return success;
    } catch (e) {
      console.error('Redo failed:', e);
      return false;
    }
  }, [state.info]);

  /**
   * 关闭文档
   */
  const close = useCallback(async (): Promise<void> => {
    if (!state.info) return;

    try {
      await invoke('close_document', { handle: state.info.handle });
    } catch (e) {
      console.error('Close failed:', e);
    }

    setState({
      info: null,
      loading: false,
      error: null,
      modified: false,
    });
  }, [state.info]);

  /**
   * 标记为已修改
   */
  const markModified = useCallback(() => {
    setState((prev) => ({ ...prev, modified: true }));
  }, []);

  return {
    // 状态
    info: state.info,
    loading: state.loading,
    error: state.error,
    modified: state.modified,

    // 操作
    open,
    render,
    getToc,
    search,
    save,
    undo,
    redo,
    close,
    markModified,
  };
}

/**
 * 分屏文档渲染 Hook
 *
 * 同时管理编辑区和预览区的渲染
 * 参考: UI 文档 §6, §7
 */
export interface SplitRenderResult {
  edit: RenderResult;
  preview: RenderResult;
}

export function useSplitDocument(options: UseDocumentOptions = {}) {
  const baseDocument = useDocument(options);

  /**
   * 同时渲染编辑区和预览区
   */
  const renderSplit = useCallback(
    async (
      editViewport: Partial<Viewport>,
      previewViewport: Partial<Viewport>
    ): Promise<SplitRenderResult> => {
      if (!baseDocument.info) {
        return {
          edit: { commands: [], total_height: 0 },
          preview: { commands: [], total_height: 0 },
        };
      }

      const [edit, preview] = await Promise.all([
        baseDocument.render(editViewport),
        baseDocument.render(previewViewport),
      ]);

      return { edit, preview };
    },
    [baseDocument]
  );

  return {
    ...baseDocument,
    renderSplit,
  };
}
