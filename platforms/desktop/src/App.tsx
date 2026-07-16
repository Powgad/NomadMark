/**
 * NomadMark Desktop - Main Application
 *
 * 主应用组件
 * 参考: 《架构设计书 v2.0》§5.1, UI 文档
 */

import React, { useState, useCallback, useEffect, useRef } from 'react';
import SplitView from './components/SplitView';
import { useDocument } from './hooks/useDocument';
import { ViewMode, KeyboardType, type Rect } from './types';
import { open } from '@tauri-apps/api/dialog';

// =============================================================================
// 工具栏图标 (使用 Unicode 模拟)
// =============================================================================

const Icons = {
  // 预览切换 (UI 文档: 2.13 <-> 2.15)
  preview: '👁️',
  previewActive: '📄',

  // 分屏切换 (UI 文档: 2.20 <-> 2.22)
  split: '🔲',
  splitActive: '⊞',

  // 修订 (UI 文档: 2.17)
  revision: '✏️',

  // 目录 (UI 文档: 2.9.0)
  toc: '📑',

  // 搜索 (UI 文档: 2.19)
  search: '🔍',

  // 快捷键设置 (UI 文档: 2.8.0)
  settings: '⚙️',

  // 保存 (UI 文档: 359523fe...)
  save: '💾',

  // 快捷工具栏切换 (UI 文档: 2.14)
  quickBar: '🔧',
};

// =============================================================================
// 工具栏组件
// =============================================================================

interface ToolbarProps {
  viewMode: ViewMode;
  isPreviewActive: boolean;
  isSplitActive: boolean;
  isRevisionActive: boolean;
  isQuickBarVisible: boolean;
  documentModified: boolean;
  onTogglePreview: () => void;
  onToggleSplit: () => void;
  onToggleRevision: () => void;
  onToggleQuickBar: () => void;
  onOpenFile: () => void;
  onSave: () => void;
  onShowToc: () => void;
  onShowSearch: () => void;
  onShowSettings: () => void;
}

const Toolbar: React.FC<ToolbarProps> = ({
  isPreviewActive,
  isSplitActive,
  isRevisionActive,
  isQuickBarVisible,
  documentModified,
  onTogglePreview,
  onToggleSplit,
  onToggleRevision,
  onToggleQuickBar,
  onOpenFile,
  onSave,
  onShowToc,
  onShowSearch,
  onShowSettings,
}) => {
  return (
    <div
      className="toolbar"
      style={{
        display: 'flex',
        alignItems: 'center',
        height: 48,
        padding: '0 12px',
        backgroundColor: '#F5F5F5',
        borderBottom: '1px solid #DDDDDD',
        gap: 8,
      }}
    >
      {/* 打开文件 */}
      <button
        onClick={onOpenFile}
        style={buttonStyle}
        title="打开文件"
      >
        📂
      </button>

      {/* 保存 */}
      <button
        onClick={onSave}
        style={documentModified ? { ...buttonStyle, color: '#0066CC' } : { ...buttonStyle, opacity: 0.5 }}
        disabled={!documentModified}
        title="保存"
      >
        {Icons.save}
      </button>

      <div style={{ width: 1, height: 24, backgroundColor: '#DDDDDD' }} />

      {/* 预览切换 */}
      <button
        onClick={onTogglePreview}
        style={buttonStyle}
        title={isPreviewActive ? '关闭预览' : '开启预览'}
      >
        {isPreviewActive ? Icons.previewActive : Icons.preview}
      </button>

      {/* 分屏切换 */}
      <button
        onClick={onToggleSplit}
        style={buttonStyle}
        title={isSplitActive ? '关闭分屏' : '开启分屏'}
      >
        {isSplitActive ? Icons.splitActive : Icons.split}
      </button>

      {/* 修订 */}
      <button
        onClick={onToggleRevision}
        style={isRevisionActive ? { ...buttonStyle, backgroundColor: '#E0E0E0' } : buttonStyle}
        title={isRevisionActive ? '关闭修订' : '开启修订'}
      >
        {Icons.revision}
      </button>

      <div style={{ width: 1, height: 24, backgroundColor: '#DDDDDD' }} />

      {/* 目录 */}
      <button onClick={onShowToc} style={buttonStyle} title="目录">
        {Icons.toc}
      </button>

      {/* 搜索 */}
      <button onClick={onShowSearch} style={buttonStyle} title="搜索">
        {Icons.search}
      </button>

      {/* 快捷工具栏 */}
      <button
        onClick={onToggleQuickBar}
        style={isQuickBarVisible ? { ...buttonStyle, backgroundColor: '#E0E0E0' } : buttonStyle}
        title={isQuickBarVisible ? '隐藏快捷工具栏' : '显示快捷工具栏'}
      >
        {Icons.quickBar}
      </button>

      <div style={{ flex: 1 }} />

      {/* 设置 */}
      <button onClick={onShowSettings} style={buttonStyle} title="设置">
        {Icons.settings}
      </button>
    </div>
  );
};

const buttonStyle: React.CSSProperties = {
  width: 36,
  height: 36,
  border: 'none',
  borderRadius: 4,
  backgroundColor: 'transparent',
  cursor: 'pointer',
  fontSize: 18,
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  transition: 'background-color 0.15s',
};

// =============================================================================
// 快捷工具栏组件
// =============================================================================

interface QuickBarProps {
  visible: boolean;
  onInsert: (type: string) => void;
}

const QuickBar: React.FC<QuickBarProps> = ({ visible, onInsert }) => {
  if (!visible) return null;

  const buttons = [
    { label: 'H1', type: 'heading1', title: '一级标题' },
    { label: 'H2', type: 'heading2', title: '二级标题' },
    { label: 'B', type: 'bold', title: '粗体' },
    { label: 'I', type: 'italic', title: '斜体' },
    { label: '~~', type: 'strikethrough', title: '删除线' },
    { label: '`', type: 'code', title: '行内代码' },
    { label: '>', type: 'quote', title: '引用' },
    { label: '•', type: 'list', title: '列表' },
    { label: '[]', type: 'checkbox', title: '复选框' },
    { label: '🔗', type: 'link', title: '链接' },
    { label: '🖼️', type: 'image', title: '图片' },
    { label: '---', type: 'hr', title: '分隔线' },
  ];

  return (
    <div
      className="quick-bar"
      style={{
        display: 'flex',
        flexWrap: 'wrap',
        gap: 4,
        padding: 8,
        backgroundColor: '#F0F0F0',
        borderBottom: '1px solid #DDDDDD',
      }}
    >
      {buttons.map((btn) => (
        <button
          key={btn.type}
          onClick={() => onInsert(btn.type)}
          style={quickButtonStyle}
          title={btn.title}
        >
          {btn.label}
        </button>
      ))}
    </div>
  );
};

const quickButtonStyle: React.CSSProperties = {
  ...buttonStyle,
  width: 40,
  height: 32,
  fontSize: 14,
  backgroundColor: '#FFFFFF',
  border: '1px solid #CCCCCC',
};

// =============================================================================
// 主应用
// =============================================================================

function App() {
  // 文档状态
  const { info, modified, open: openDoc, save } = useDocument();

  // 视图状态
  const [viewMode, setViewMode] = useState<ViewMode>(ViewMode.Edit);
  const [keyboardType, setKeyboardType] = useState<KeyboardType>(KeyboardType.None);
  const [isPreviewActive, setIsPreviewActive] = useState(false);
  const [isSplitActive, setIsSplitActive] = useState(false);
  const [isRevisionActive, setIsRevisionActive] = useState(false);
  const [isQuickBarVisible, setIsQuickBarVisible] = useState(false);
  const [dividerPosition, setDividerPosition] = useState(0.5);

  // 滚动状态
  const [scrollPosition, setScrollPosition] = useState({ edit: 0, preview: 0 });

  // 容器尺寸
  const [containerSize, setContainerSize] = useState<Rect>({
    x: 0,
    y: 0,
    width: 1200,
    height: 800,
  });
  const containerRef = useRef<HTMLDivElement>(null);

  /**
   * 检测键盘类型
   */
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'F11') {
        // F11 用于软键盘切换 (模拟)
        setKeyboardType((prev) =>
          prev === KeyboardType.Soft ? KeyboardType.None : KeyboardType.Soft
        );
      }
    };

    // 简单检测: 如果有物理键盘事件，认为是外接键盘
    const keyboardHandler = () => {
      if (keyboardType === KeyboardType.None) {
        setKeyboardType(KeyboardType.External);
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    window.addEventListener('keydown', keyboardHandler, { once: true });

    return () => {
      window.removeEventListener('keydown', handleKeyDown);
      window.removeEventListener('keydown', keyboardHandler);
    };
  }, [keyboardType]);

  /**
   * 更新容器尺寸
   */
  useEffect(() => {
    const updateSize = () => {
      if (containerRef.current) {
        const rect = containerRef.current.getBoundingClientRect();
        setContainerSize({
          x: rect.left,
          y: rect.top,
          width: rect.width,
          height: rect.height,
        });
      }
    };

    updateSize();
    window.addEventListener('resize', updateSize);
    return () => window.removeEventListener('resize', updateSize);
  }, []);

  /**
   * 打开文件
   */
  const handleOpenFile = useCallback(async () => {
    try {
      const selected = await open({
        multiple: false,
        filters: [
          {
            name: 'Markdown',
            extensions: ['md', 'markdown', 'txt'],
          },
        ],
      });

      if (selected && typeof selected === 'string') {
        await openDoc(selected);
      }
    } catch (e) {
      console.error('Failed to open file:', e);
    }
  }, [openDoc]);

  /**
   * 保存文件
   */
  const handleSave = useCallback(async () => {
    try {
      await save();
    } catch (e) {
      console.error('Failed to save:', e);
    }
  }, [save]);

  /**
   * 切换预览
   */
  const handleTogglePreview = useCallback(() => {
    setIsPreviewActive((prev) => !prev);
    setIsSplitActive(false);

    setViewMode((prev) =>
      prev === ViewMode.Preview ? ViewMode.Edit : ViewMode.Preview
    );
  }, []);

  /**
   * 切换分屏
   */
  const handleToggleSplit = useCallback(() => {
    setIsSplitActive((prev) => !prev);
    setIsPreviewActive(false);
    setViewMode(ViewMode.Split);
  }, []);

  /**
   * 切换修订
   */
  const handleToggleRevision = useCallback(() => {
    setIsRevisionActive((prev) => !prev);
  }, []);

  /**
   * 切换快捷工具栏
   */
  const handleToggleQuickBar = useCallback(() => {
    setIsQuickBarVisible((prev) => !prev);
  }, []);

  /**
   * 插入 Markdown 元素
   */
  const handleInsert = useCallback((type: string) => {
    // TODO: 实现插入逻辑
    console.log('Insert:', type);
  }, []);

  return (
    <div
      className="app"
      style={{
        display: 'flex',
        flexDirection: 'column',
        height: '100vh',
        overflow: 'hidden',
        fontFamily: 'NotoSansCJK, sans-serif',
      }}
    >
      {/* 工具栏 */}
      <Toolbar
        viewMode={viewMode}
        isPreviewActive={isPreviewActive}
        isSplitActive={isSplitActive}
        isRevisionActive={isRevisionActive}
        isQuickBarVisible={isQuickBarVisible}
        documentModified={modified}
        onTogglePreview={handleTogglePreview}
        onToggleSplit={handleToggleSplit}
        onToggleRevision={handleToggleRevision}
        onToggleQuickBar={handleToggleQuickBar}
        onOpenFile={handleOpenFile}
        onSave={handleSave}
        onShowToc={() => console.log('Show TOC')}
        onShowSearch={() => console.log('Show Search')}
        onShowSettings={() => console.log('Show Settings')}
      />

      {/* 快捷工具栏 */}
      <QuickBar
        visible={isQuickBarVisible}
        onInsert={handleInsert}
      />

      {/* 主内容区 */}
      <div
        ref={containerRef}
        className="content-area"
        style={{ flex: 1, position: 'relative', overflow: 'hidden' }}
      >
        {info ? (
          <SplitView
            viewMode={viewMode}
            keyboardType={keyboardType}
            containerSize={containerSize}
            scrollPosition={scrollPosition}
            onScrollChange={setScrollPosition}
            dividerPosition={dividerPosition}
            onDividerChange={setDividerPosition}
          />
        ) : (
          <div
            style={{
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              justifyContent: 'center',
              height: '100%',
              color: '#999999',
            }}
          >
            <p style={{ fontSize: 18, marginBottom: 16 }}>📝 NomadMark Desktop</p>
            <p>点击工具栏的 📂 图标打开 Markdown 文件</p>
          </div>
        )}
      </div>
    </div>
  );
}

export default App;
