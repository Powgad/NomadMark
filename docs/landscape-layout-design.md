# NomadMark 横屏布局设计方案

## 1. 设计目标

为 NomadMark 应用设计一套适合横屏使用的布局，保持功能完整性的同时优化横屏体验。

## 2. 设计原则

1. **保持 UI 一致性**：横竖屏使用一致的布局结构，减少用户认知负担
2. **保持功能一致**：所有竖屏功能在横屏下同样可用
3. **优化空间利用**：利用横屏宽度优势，弥补高度不足
4. **最大化复用**：复用现有布局和代码，减少维护成本
5. **渐进式实现**：分阶段实现，优先核心功能

## 3. 布局结构调整

### 3.1 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                        顶部工具栏 (单行设计，保持一致)           │
├─────────────────────────────────────────────────────────────┤
│  主内容区域 (横屏优化：左右分屏替代上下分屏)                  │
└─────────────────────────────────────────────────────────────┘
```

### 3.2 顶部工具栏：保持单行设计

**设计原则**：横竖屏保持一致的 UI 布局，横屏宽度更大，单行布局反而更宽松

**横屏方案**（与竖屏完全一致）：

```
┌─────────────────────────────────────────────────────────────┐
│[返回][目录][打开]|  文件名.md  |[预览][修订][搜索][分屏][撤销][重做][快捷栏][设置][保存]|
└─────────────────────────────────────────────────────────────┘
```

**优势**：
- 保持 UI 一致性，用户切换方向时无需重新适应
- 横屏宽度增加，按钮间距更宽松，显示效果更好
- 无需修改工具栏相关代码，最大化复用
- 节省竖直空间（横屏高度有限）

**高度保持**：`56dp`（与竖屏一致）

### 3.3 主内容区域

#### 3.3.1 编辑模式 / 预览模式

保持不变，内容区域宽度增加，显示更多内容列数。

#### 3.3.2 分屏模式：左右分屏

**现状问题**：竖屏下是上下 6:4 分配

**横屏方案**：左右 1:1 分配

```
┌─────────────────────────────────────────────────────────────┐
│                    预览区 (左, 50%)                          │
│                                                              │
│                     [竖分隔线]                                │
│                                                              │
│                    编辑区 (右, 50%)                          │
└─────────────────────────────────────────────────────────────┘
```

**布局结构**：
```xml
<LinearLayout
    android:orientation="horizontal"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <!-- 左侧预览区 -->
    <ObservableScrollView
        android:layout_width="0dp"
        android:layout_height="match_parent"
        android:layout_weight="1">

        <TextView ... />
    </ObservableScrollView>

    <!-- 分隔线 -->
    <View
        android:layout_width="2dp"
        android:layout_height="match_parent"
        android:background="#000000" />

    <!-- 右侧编辑区 -->
    <ObservableScrollView
        android:layout_width="0dp"
        android:layout_height="match_parent"
        android:layout_weight="1">

        <EditText ... />
    </ObservableScrollView>
</LinearLayout>
```

### 3.4 搜索栏

保持横向布局不变，横屏宽度增加时更宽松。

### 3.5 目录侧边栏

**现状**：竖屏下占 2/3 宽度

**横屏方案**：调整为 1/3 宽度

```
┌─────────────────────────────────────────────────────────────┐
│   目录列表 (1/3)   │            空白区域 (2/3)              │
└─────────────────────────────────────────────────────────────┘
```

**布局结构**：
```xml
<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="horizontal">

    <!-- 目录列表 (占 1/3) -->
    <ListView
        android:id="@+id/toc_list"
        android:layout_width="0dp"
        android:layout_height="match_parent"
        android:layout_weight="1"
        android:background="#FFFFFF"
        android:padding="8dp" />

    <!-- 竖分隔线 -->
    <View
        android:layout_width="2dp"
        android:layout_height="match_parent"
        android:background="#000000" />

    <!-- 空白区域 (占 2/3，点击关闭目录) -->
    <View
        android:id="@+id/toc_close_area"
        android:layout_width="0dp"
        android:layout_height="match_parent"
        android:layout_weight="2"
        android:background="#AAFFFFFF" />
</LinearLayout>
```

**说明**：横屏宽度充足，1/3 足够显示目录标题，同时留出更大空白区域方便点击关闭。

### 3.6 底部快捷栏

**现状**：HorizontalScrollView 横向滚动（竖屏下宽度有限，需要滚动）

**横屏方案**：改为 LinearLayout，按钮铺满宽度显示

```
┌─────────────────────────────────────────────────────────────┐
│[**B**][*I*][H+][H-][`代码`][```][链接][图片][- 列表][>引用][表格][---][∑]|
└─────────────────────────────────────────────────────────────┘
```

**布局结构**（横屏专用）：
```xml
<!-- 横屏：普通 LinearLayout，按钮铺满 -->
<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="@dimen/bottom_toolbar_height"
    android:orientation="horizontal"
    android:background="#F0F0F0"
    android:paddingStart="8dp"
    android:paddingEnd="8dp">

    <!-- 按钮平均分配空间 -->
    <Button style="@style/ToolbarButton" android:layout_weight="1" />
    <Button style="@style/ToolbarButton" android:layout_weight="1" />
    <!-- ... 其他按钮 ... -->
</LinearLayout>
```

**竖屏布局保持不变**：继续使用 HorizontalScrollView

**优势**：
- 横屏宽度充足，所有按钮一次显示，无需滚动
- 按钮等宽分布，视觉效果更整齐
- 提升操作效率

## 4. 尺寸调整

### 4.1 新建 `values-land/dimens.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- 工具栏高度保持与竖屏一致 -->
    <dimen name="toolbar_height">56dp</dimen>
    <dimen name="bottom_toolbar_height">48dp</dimen>
    <dimen name="keyboard_indicator_height">40dp</dimen>

    <!-- 横屏内容区域内边距 -->
    <dimen name="content_padding_horizontal">24dp</dimen>
    <dimen name="content_padding_vertical">16dp</dimen>

    <!-- 按钮尺寸 -->
    <dimen name="toolbar_button_size">48dp</dimen>
    <dimen name="toolbar_button_padding">8dp</dimen>

    <!-- 间距 -->
    <dimen name="margin_small">4dp</dimen>
    <dimen name="margin_medium">8dp</dimen>
    <dimen name="margin_large">16dp</dimen>

    <!-- 文字大小 -->
    <dimen name="text_size_small">12sp</dimen>
    <dimen name="text_size_normal">14sp</dimen>
    <dimen name="text_size_medium">16sp</dimen>
    <dimen name="text_size_large">18sp</dimen>
    <dimen name="text_size_xlarge">24sp</dimen>
</resources>
```

**说明**：由于工具栏保持单行设计，大部分尺寸与竖屏保持一致，主要调整内容区内边距以利用横屏宽度优势。

### 4.2 字体大小

横屏时内容宽度增加，可以考虑：
- 正文保持 `16sp`
- 表格、代码块可能显示更多列
- 标题可适当增大以充分利用空间

## 5. 实现方案

### 5.1 目录结构

```
platforms/android/app/src/main/res/
├── layout/
│   └── activity_editor.xml          (竖屏布局，现有)
└── layout-land/
    └── activity_editor.xml          (横屏布局，新建)
```

### 5.2 实现步骤

#### 第一阶段：基础横屏布局

1. **创建 `layout-land/activity_editor.xml`**
   - 复制竖屏布局
   - 保持顶部工具栏单行设计（不变）
   - 调整分屏层为左右分屏（核心改动）

2. **创建 `values-land/dimens.xml`**
   - 定义横屏专用尺寸
   - 大部分尺寸与竖屏保持一致

3. **测试基础功能**
   - 旋转设备测试布局显示
   - 验证各功能在横屏下正常工作

#### 第二阶段：细节优化

1. **优化目录侧边栏宽度**
2. **优化底部快捷栏布局**
3. **调整内边距和间距**

#### 第三阶段：特殊场景处理

1. **乐谱渲染横屏适配**
2. **搜索栏横屏优化**
3. **键盘弹出时的布局调整**

### 5.3 代码调整

#### MarkdownEditorActivity.kt

```kotlin
// 无需特殊处理，因为 configChanges 已经设置
// 如果需要根据方向做特殊处理：

private fun isLandscape(): Boolean {
    return resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
}

// 在需要的地方根据方向调整逻辑
if (isLandscape()) {
    // 横屏特定逻辑
} else {
    // 竖屏特定逻辑
}
```

## 6. 兼容性考虑

### 6.1 Android 版本

- Android API 21+（5.0+）完全支持 layout-land

### 6.2 Supernote 设备

需要确认：
- [ ] 设备是否允许横屏旋转
- [ ] 墨水屏横屏刷新率是否影响体验
- [ ] 键盘在横屏下的位置和大小

### 6.3 状态保持

由于 `configChanges` 已设置，旋转时：
- Activity 不会重建
- 所有状态（文本、光标位置、模式等）自动保持
- 系统会自动应用对应的布局文件

## 7. 测试清单

- [ ] 横竖屏旋转时布局正常切换
- [ ] 编辑模式横屏显示正常
- [ ] 预览模式横屏显示正常
- [ ] 分屏模式横屏左右分屏正常
- [ ] 目录侧边栏横屏显示正常
- [ ] 搜索功能横屏正常
- [ ] 底部快捷栏横屏显示正常
- [ ] 乐谱渲染横屏显示正常
- [ ] 撤销/重做横屏正常
- [ ] 保存功能横屏正常
- [ ] 文本编辑输入横屏正常
- [ ] 外接键盘横屏正常

## 8. 工作量估算

| 阶段 | 工作内容 | 预计时间 |
|------|----------|----------|
| 第一阶段 | 创建横屏布局文件（主要调整分屏层） | 0.5 天 |
| 第二阶段 | 细节优化、测试调整 | 0.5 天 |
| **总计** | | **0.5-1 天** |

**说明**：由于顶部工具栏和底部快捷栏保持单行设计，最大化复用了现有布局和代码，主要改动仅为分屏模式的左右分屏调整，工作量大幅减少。

## 9. 附录

### 9.1 参考 Supernote 设备规格

- A6 X2 Nomad: 1872 x 1404 像素
- 屏幕比例：竖屏为主，支持旋转

### 9.2 相关文件

- 竖屏布局：`platforms/android/app/src/main/res/layout/activity_editor.xml`
- 横屏布局：`platforms/android/app/src/main/res/layout-land/activity_editor.xml`（待创建）
- 尺寸定义：`platforms/android/app/src/main/res/values/dimens.xml`
- 横屏尺寸：`platforms/android/app/src/main/res/values-land/dimens.xml`（待创建）
