---
name: supernote-device-dev
description: 适用于任意在 Supernote/Ratta 墨水屏设备上运行的 Android 应用。E-Ink 防闪烁 UI、厂商系统签名与笔记库手写服务、drawpath 物理坐标与不可写区域、Scoped Storage 与 SAF。调试 UI 闪烁、配置签名、对接手写 SDK、修复坐标错位或 Media is read-only 时使用。
---

# Supernote 墨水屏设备开发指南

> **范围**：与具体业务应用无关；下列 API/类名（如 `RecognResultData`、`sendDisableAreaInfo`）以你实际集成的 **Ratta 笔记库 / 手写 SDK** 文档为准，不同版本可能略有差异。

## 速查（给 Agent）

| 主题 | 一句话 |
|------|--------|
| 工具栏按钮 | 用 `ImageView`，禁用 `ImageButton` / ripple / `selectableItemBackground` |
| 系统手写 | 厂商 `*.jks` + `sharedUserId` + `signingConfigs` 三者齐全；密钥**勿提交、勿写进文档明文** |
| drawpath 不可写区 | Rect 用 **物理像素**；优先 `WindowManager.getRealSize()`，勿**仅依赖**型号表/`Build` 推断分辨率 |
| 删除手势错位 | 边界框 vs `key_point` 坐标系不一致时做缩放修正（见 §4） |
| 保存失败 | **优先**经 Supernote **自带文件管理（inbox）** 打开得 `file://`；否则用 `ACTION_OPEN_DOCUMENT`（避免 `ACTION_GET_CONTENT` + 第三方管理器返回 MediaStore 只读 URI） |

**集成时自检**：手写 Binder 接口名、Rect 列表类型、识别结果字段名以当前 SDK 为准；下文用常见命名举例。

---

## 1. E-Ink 屏幕 UI 适配

### 核心原则

墨水屏每次像素变化易触发局部刷新与可见闪烁。**按压高亮、透明度变化、动画等视觉反馈应尽量避免**。

### ImageView vs ImageButton（关键差异）

| 特性 | ImageView | ImageButton |
|------|-----------|-------------|
| 按压状态反馈 | **无** | 有（内置 pressed state） |
| 默认背景 | 透明 | 带 padding 的默认 drawable |
| 墨水屏闪烁 | **相对安全** | 即使 `background="@null"` + `stateListAnimator="@null"` 仍可能闪烁 |
| 可点击性 | 需 `setOnClickListener` 后自动可点击 | 默认可点击 |

**结论：墨水屏上工具栏等高频点击控件应使用 `ImageView`，避免 `ImageButton`（易触发按压态闪烁）。**

```xml
<!-- ✅ 推荐：ImageView -->
<ImageView
    android:id="@+id/btn_save"
    android:layout_width="50dp"
    android:layout_height="50dp"
    android:src="@drawable/ic_txt_save"
    android:contentDescription="保存"
    android:scaleType="centerInside"
    android:padding="10dp" />

<!-- ❌ 不推荐：ImageButton 在墨水屏上易闪烁 -->
<ImageButton
    android:background="@null"
    android:stateListAnimator="@null"
    ... />
```

### 禁用动画与 Material 涟漪

| 属性 | 说明 |
|------|------|
| `?attr/selectableItemBackgroundBorderless` | **避免**（水波纹） |
| `?attr/selectableItemBackground` | **避免**（水波纹） |

### 按钮状态切换

用不同 drawable 表示启用/禁用，通过 `setImageResource()` 切换（避免依赖按压态 drawable）：

```java
public void setSaveEnabled(boolean enable) {
    saveEnabled = enable;
    if (btnSave != null) {
        btnSave.setImageResource(enable
            ? R.drawable.ic_txt_save
            : R.drawable.ic_txt_un_save);
    }
}
```

### 图标规格（对齐原生风格）

- 容器约 50dp，viewport 约 48dp，padding 约 10dp
- 描边线条、线宽约 `strokeWidth="4"`，颜色 `#000000`，透明填充
- `strokeLineCap` / `strokeLineJoin` 用 `round`

---

## 2. 系统签名配置

系统级能力（如 myScript 资源路径等）通常需要：

```
厂商平台密钥库（常见文件名 ratta.jks，以厂商交付为准）
+ AndroidManifest sharedUserId="android.uid.system"
+ Gradle signingConfigs
缺一项 → 可能无法获得预期系统权限（如访问设备上 myScript 等资源路径）
```

### 检查清单

| 检查项 | 位置 | 要求 |
|--------|------|------|
| 密钥库 | 如 `app/ratta.jks`（路径自定） | 厂商提供，**不入库** |
| 系统 UID | `AndroidManifest.xml` | `android:sharedUserId="android.uid.system"`（设定后勿随意改） |
| 签名 | `app/build.gradle` | debug / release 均指向同一厂商密钥（若需系统权限） |
| Git | `.gitignore` | `*.jks`、`keystore.properties` 等 |

### build.gradle 签名配置（勿写明文密码）

**禁止**在仓库或 Skill 中保存真实 `storePassword` / `keyPassword`。推荐本地 `keystore.properties`（已 gitignore）或环境变量：

```groovy
def keystoreProps = new Properties()
def keystorePropsFile = rootProject.file("keystore.properties")
if (keystorePropsFile.exists()) {
    keystoreProps.load(new FileInputStream(keystorePropsFile))
}

signingConfigs {
    release {
        storeFile file("ratta.jks") // 文件名以厂商交付为准
        storePassword keystoreProps.getProperty("storePassword")
        keyAlias keystoreProps.getProperty("keyAlias", "ratta")
        keyPassword keystoreProps.getProperty("keyPassword")
    }
    debug {
        // 与 release 使用同一厂商密钥时，debug 才能具备与 release 一致的系统权限行为
        storeFile file("ratta.jks")
        storePassword keystoreProps.getProperty("storePassword")
        keyAlias keystoreProps.getProperty("keyAlias", "ratta")
        keyPassword keystoreProps.getProperty("keyPassword")
    }
}

buildTypes {
    release { signingConfig signingConfigs.release }
    debug   { signingConfig signingConfigs.debug }
}
```

本地 `keystore.properties` 示例（**仅本机**，勿提交）：

```properties
storePassword=***
keyPassword=***
keyAlias=ratta
```

---

## 3. 设备特性参考

### 型号与屏幕（常见参考值）

| 型号 | 系统 | DPI | 分辨率 | 色彩 |
|------|------|-----|--------|------|
| A6X | API 26 | 226 | 1404×1872 | 灰阶 |
| A5X | API 26 | 300 | 1404×1872 | 灰阶 |
| A6X2 | API 30 | 300 | 1404×1872 | 灰阶 |
| A5X2 | API 30 | 300 | 1920×2560 | 灰阶 |

### 笔记库 / 手写

- Binder IPC（如 `drawpath` 相关服务）
- myScript 等资源路径常位于 `/sdcard/.data/dictionary/conf/` 等（以设备为准）
- 常见广播：`android.intent.action.penup`、`com.ratta.supernote.launcher.flashscreen`、`keyboard_show` / `keyboard_hide`

---

## 4. 手写坐标空间差异（设备兼容性）

**现象**：`RecognResultData` 的 `get_up_left_point` / `get_down_right_point`（边界框）与 `get_key_point`（关键点）在部分机型上处于不同坐标空间（例如约 1.36× 偏差）。

**影响**：用边界框做 `coordinateToOffset` 时可能删错行。

**检测**：比较 `(up_left + down_right) / 2` 与 `key_point`，偏差持续 > 20px 时考虑修正。

**修正思路**：

```java
double bboxMidX = (startX + endX) / 2.0;
double bboxMidY = (startY + endY) / 2.0;
if (Math.abs(bboxMidX - midX) > 20 || Math.abs(bboxMidY - midY) > 20) {
    double scaleX = bboxMidX / midX;
    double scaleY = bboxMidY / midY;
    startX = (int) (startX / scaleX);
    // 同样修正 startY, endX, endY, jniRect 等与 bbox 同空间的量
}
int y = midY; // key_point 的 Y 通常更可靠时可优先采用
```

`getLastTrailRect()` 的 jniRect 与 up_left/down_right 同空间时需一并缩放。

| 设备 | 边界框 vs key_point | 建议 |
|------|---------------------|------|
| A5X (API 26) | 常一致 | 一般无需修正 |
| A5X2 / A6X2 (API 30) | 可能不一致 | **运行时检测**；A6X2 上常见需修正 |

---

## 5. drawpath 不可写区域坐标系

### 坐标系

drawpath 侧常用**物理屏幕像素**，与 `WindowManager.getRealSize()` 等设备真实分辨率一致。

| 操作 | 说明 |
|------|------|
| `sendWritable(true)` | 常见大矩形覆盖全屏可写 |
| `sendWritable(false)` | 常见大矩形全屏不可写 |
| `sendDisableAreaInfo(rectList)` | **Rect 必须为物理像素** |

### 型号表 vs 真实分辨率不一致

**现象**：工具栏已设为不可写，但右侧仍可手写识别。

**根因示例**：用**型号表或 `Build` 推断**得到逻辑宽 1404，而物理宽为 1920，`sendDisableAreaInfo` 只盖住 0～1404，笔触在 1404～1920 漏检。

drawpath 侧日志可能出现类似：`disablse size 0, intersectDisable 0`（原文拼写如此，仅作检索用）。

**诊断**：对比手势服务 raw x/y 与日志里「屏幕尺寸」是否同范围。

**推荐**：凡与 drawpath 交互的宽高，**以运行时 `WindowManager.getRealSize()`（或当前方向下的物理像素）为准**；自建机型分辨率表仅作 `getRealSize` 失败时的兜底。

```java
int width, height;
try {
    WindowManager wm = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
    Point realSize = new Point();
    wm.getDefaultDisplay().getRealSize(realSize);
    width  = Math.min(realSize.x, realSize.y);
    height = Math.max(realSize.x, realSize.y);
} catch (Exception e) {
    width  = resolveDisplayWidthFallback();  // 自建机型表 / DisplayMetrics 等
    height = resolveDisplayHeightFallback();
}
disableRectList.add(new Rect(0, 0, width, toolbarHeightPx));
```

### 工具栏 / 悬浮层 Y

覆盖层或工具栏调用 `getLocationOnScreen(int[])` 得到的坐标，在与手势/raw 事件同一坐标系时，可直接用于 `sendDisableAreaInfo`（仍以 SDK 文档为准）。

### 型号表误判

部分 `Build` 字段仅匹配到泛化代号（如含 `NOMAD`）时，静态映射可能仍给 1404×1872，而真机为 1920×2560。**不要单独信任静态表覆盖 drawpath 矩形。**

---

## 6. 常见陷阱

| 陷阱 | 后果 | 处理 |
|------|------|------|
| `ImageButton` + 默认 pressed | 闪烁 | 改用 `ImageView`（§1） |
| `selectableItemBackground*` | 水波纹闪烁 | 不用或换静态背景 |
| 缺 `sharedUserId` / 签名 | 系统能力不可用 | 检查 manifest + signing（§2） |
| debug 未签厂商包 | 与 release 权限不一致 | debug 同步签名策略（§2） |
| 直接用 `up_left` 不做检测 | 部分机型删错位 | §4 |
| `content://` 用 `uri.getPath()` 当文件名 | 显示 `document:4078` | `OpenableColumns.DISPLAY_NAME`（§7） |
| MediaStore URI 非 SAF 打开 | Android 10+ `Media is read-only` | 引导 **inbox** 打开或 `ACTION_OPEN_DOCUMENT`（§7） |
| 保存依赖 `_data` | 废弃列、易失败 | 主路径 `openOutputStream(uri)`（§7） |

---

## 7. 文件 I/O 与 URI（Scoped Storage）

### 背景

设备横跨 API 26 与 API 30+，**Scoped Storage** 使 `content://` 写入行为依赖 URI 来源与授权方式。

### 优先：Supernote 自带文件管理（inbox）

在 Supernote 上，**最省事、最稳的打开方式**是让用户通过系统**自带的文件管理入口（用户侧常称为 inbox / 文档库）**打开文件，而不是第三方文件管理器或泛泛的「分享 / 打开方式」。

| 方式 | 典型 URI | Android 11 写入 |
|------|----------|-------------------|
| **自带文件管理（inbox）打开** | 常为 `file:///storage/emulated/0/...` | ✅ 路径在应用有权限时，按文件读写即可 |
| 第三方管理器 / `ACTION_GET_CONTENT` | 易为 `content://com.android.providers.media.documents/...` | ❌ 常见 **Media is read-only** |

**结论（产品 / 交互）**：

- 在帮助文案、首次引导、设置里明确写：**请用 Supernote 自带「文件 / 文档」打开本应用要编辑的文件**（即走 inbox 体系），可避免「能读不能存」的问题。
- 若你的应用支持「从外部打开」：在无法改 URI 类型时，至少提示用户：**改用 inbox 重新打开同一文件** 再保存。

**结论（开发）**：inbox 路径下 `file://` 与「经 SAF 正确授权的 `content://`」都属于低风险；问题集中在 **MediaStore 文档 URI 且无写授权**。

### URI 与写入（简表）

| Authority 示例 | 典型来源 | API 30+ 注意 |
|----------------|----------|----------------|
| `file://` | 自带管理器、真实路径 | 受分区与签名权限约束；inbox 常见此形态 |
| `com.android.externalstorage.documents` | SAF 外部存储 | 带持久授权时可写 |
| `com.android.providers.media.documents` | MediaStore | 非 SAF 打开时常 **read-only** |

### 根因链（典型）

用户用 **Google 文件等第三方管理器** 或 **`ACTION_GET_CONTENT`** 选文件 → 得到 `content://...media.documents/...` → 在 Android 11 上 `openOutputStream` 报 **Media is read-only**（非本应用创建的媒体项）。**同一文件若改由 inbox 打开，往往变为 `file://`，即可正常写回。**

### 方案优先级（推荐顺序）

1. **首选（产品路径）**：引导用户使用 **Supernote 自带文件管理（inbox）** 打开待编辑文件，获得 `file://` 或可写路径，避免落入 MediaStore 只读 URI。
2. **次选（应用内「打开文件」）**：使用 **`ACTION_OPEN_DOCUMENT`**，并带 `FLAG_GRANT_READ_URI_PERMISSION | FLAG_GRANT_WRITE_URI_PERMISSION | FLAG_GRANT_PERSISTABLE_URI_PERMISSION`，优于易返回临时 Media URI、且不一定可写的 **`ACTION_GET_CONTENT`**。
3. **避免默认依赖**：不要假设「任意系统文件选择器」返回的 `content://` 都可写；对只读 URI 应给出明确提示（含「请用 inbox 打开」）。

```java
Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
intent.addCategory(Intent.CATEGORY_OPENABLE);
intent.setType("*/*");
intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
startActivityForResult(intent, REQUEST_CODE_OPEN_FILE); // 新代码可改用 Activity Result API
```

### 推荐写入方式

对持有写权限的 `content://`，直接使用：

```java
try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
    out.write(content.getBytes(StandardCharsets.UTF_8));
    out.flush();
}
```

避免把「解析成绝对路径再 `FileOutputStream`」当作主路径（`_data` 不可靠且绕过 SAF）。

### 显示文件名

```java
private String getFileName(Uri uri) {
    if ("content".equals(uri.getScheme())) {
        try (Cursor c = getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                String name = c.getString(0);
                if (name != null && !name.isEmpty()) return name;
            }
        } catch (Exception ignored) { }
    }
    String path = uri.getPath();
    if (path != null) {
        int i = path.lastIndexOf('/');
        if (i >= 0 && i < path.length() - 1) return path.substring(i + 1);
    }
    return "未知文件";
}
```

### tryResolveRealPath

仅用于**必须**路径的场景（如展示、部分 Intent）；**保存以 ContentResolver + 原 URI 为主**。

### 排查清单

1. **是否经 inbox 打开**：`file://` 且可写 → 多为自带管理器路径；若用户从第三方管理器打开，先建议 **用 inbox 重开同一文件**。
2. 日志中的 URI：`file://` / `externalstorage.documents` 通常可写；`media.documents` 需确认是否 **`ACTION_OPEN_DOCUMENT`** 取得写授权。
3. 是否 `ACTION_OPEN_DOCUMENT` + 写权限 flag（应用内选文件时）。
4. 异常栈是否为 `openOutputStream` + read-only → Scoped Storage 拒绝 → 按上文优先级调整打开方式或引导 inbox。

---
